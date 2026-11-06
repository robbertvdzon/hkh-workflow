import { type FormEvent, useState } from "react"
import { Navigate, useNavigate, useParams } from "react-router"
import type { components } from "@/api/schema"
import { ApiError, errorMessage } from "@/api/client"
import { type ConflictRow, type Field, openInZcbsUrl, recordsPath, useCancelEdit, useOpenRecord, useSaveRecord, type Values } from "@/api/records"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input, Select, Textarea } from "@/components/ui/input"
import { PageHeader } from "@/components/ui/page-header"
import { BackLink } from "@/components/ui/page-section"
import { Table, TableHead, TableRow } from "@/components/ui/table"

type Edit = components["schemas"]["EditResponse"]

/** Een record bewerken via het formulier van ZCBS. Het scherm openen vergrendelt het record voor anderen, zoals in ZCBS zelf. */
export function RecordPage() {
  const { collection = "", number = "" } = useParams()
  const open = useOpenRecord(collection, number)
  const back = recordsPath(collection)

  if (open.error instanceof ApiError && ["record-not-found", "invalid-number"].includes(open.error.code)) {
    return <Navigate to={back} replace state={{ error: open.error.message }} />
  }

  return (
    <>
      <BackLink to={back}>Terug naar Records aanpassen</BackLink>
      {open.data ? (
        <RecordForm key={open.data.number} collection={collection} edit={open.data} />
      ) : open.isError ? (
        <Blocked collection={collection} number={number} error={open.error} onRetry={() => open.refetch()} />
      ) : (
        <PageHeader title={`Record ${number}`} tabTitle="Record bewerken">
          Het record openen in ZCBS…
        </PageHeader>
      )}
    </>
  )
}

function Blocked({ collection, number, error, onRetry }: { collection: string; number: string; error: unknown; onRetry: () => void }) {
  const navigate = useNavigate()
  const cancel = useCancelEdit(collection, number)
  const code = error instanceof ApiError ? error.code : ""
  const leave = () => cancel.mutate(undefined, { onSettled: () => navigate(recordsPath(collection)) })
  return (
    <>
      <PageHeader title={`Record ${number}`} tabTitle="Record bewerken" />
      {code === "record-locked" ? (
        <Alert variant="warn" className="flex flex-col gap-2">
          <b>{errorMessage(error)}</b>
          <span>Probeer het over een paar minuten opnieuw, of doe dit record later.</span>
          <div className="flex flex-wrap gap-2">
            <Button size="small" onClick={onRetry}>
              Probeer opnieuw
            </Button>
            <Button size="small" variant="secondary" onClick={leave}>
              Annuleren
            </Button>
          </div>
        </Alert>
      ) : (
        <Alert variant="error" className="flex flex-col gap-2.5">
          <b>{code === "record-unsafe" ? "Dit record is niet veilig te bewerken in deze app." : "Er ging iets mis."}</b>
          <span className="whitespace-pre-line">{errorMessage(error)}</span>
          <div className="flex flex-wrap gap-2">
            <Button asChild size="small">
              <a href={openInZcbsUrl(collection, number)} target="_blank" rel="noopener">
                Open in ZCBS
              </a>
            </Button>
            <Button size="small" variant="secondary" onClick={leave}>
              Annuleren
            </Button>
          </div>
        </Alert>
      )}
    </>
  )
}

function valuesOf(fields: Field[]): Values {
  return Object.fromEntries(fields.map((field) => [field.name, field.values]))
}

function RecordForm({ collection, edit }: { collection: string; edit: Edit }) {
  const navigate = useNavigate()
  const [fields, setFields] = useState(edit.fields)
  const [values, setValues] = useState(() => valuesOf(edit.fields))
  const [info, setInfo] = useState<string | null>(null)
  const save = useSaveRecord(collection, edit.number)
  const cancel = useCancelEdit(collection, edit.number)
  const problem = save.error instanceof ApiError ? save.error : null
  const conflicts = problem?.code === "edit-conflict" ? (problem.body.conflicts as ConflictRow[]) : null
  const blockers = problem?.code === "save-blocked" ? (problem.body.blockers as string[]) : null

  function submit(event?: FormEvent) {
    event?.preventDefault()
    setInfo(null)
    save.mutate({ values }, { onSuccess: afterSave })
  }

  function afterSave(result: { saved: boolean; unchanged: boolean }) {
    if (result.saved) navigate(`${recordsPath(collection)}?opgeslagen=${edit.number}`)
    else setInfo("Er is niets gewijzigd; er is niets opgeslagen.")
  }

  function keepMine() {
    save.mutate({ values, onConflict: "keep-mine" }, { onSuccess: afterSave })
  }

  function takeTheirs() {
    save.mutate(
      { values, onConflict: "take-theirs" },
      {
        onSuccess: (result) => {
          if (result.fields) {
            setFields(result.fields)
            setValues(valuesOf(result.fields))
          }
          setInfo("De velden die botsten zijn vervangen door de versie uit ZCBS. Controleer het formulier en sla dan op.")
        },
      },
    )
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4.5 pb-16">
      <div className="flex flex-wrap items-end justify-between gap-6">
        <PageHeader title={<>Record <span className="font-mono">{edit.number}</span></>} tabTitle="Record bewerken">
          {edit.fileName && <span className="font-mono">{edit.fileName}</span>}
          {edit.fileName && edit.openedAt && " · "}
          {edit.openedAt && `geopend om ${edit.openedAt}, voor anderen vergrendeld tot ${edit.lockedUntil}`}
        </PageHeader>
        <Button asChild variant="secondary" size="small">
          <a href={openInZcbsUrl(collection, edit.number)} target="_blank" rel="noopener">
            Open in ZCBS
          </a>
        </Button>
      </div>

      {info && <Alert>{info}</Alert>}
      {blockers && (
        <Alert variant="error" className="flex flex-col gap-1.5">
          <b>{errorMessage(save.error)}</b>
          {blockers.map((blocker) => (
            <span key={blocker}>{blocker}</span>
          ))}
        </Alert>
      )}
      {problem?.code === "record-locked" && (
        <Alert variant="warn" className="flex flex-col gap-2">
          <b>{problem.message}</b>
          <span>Probeer het over een paar minuten opnieuw, of doe dit record later.</span>
          <div>
            <Button size="small">Probeer opnieuw op te slaan</Button>
          </div>
        </Alert>
      )}
      {conflicts && (
        <Alert variant="warn" className="flex flex-col gap-2">
          <b>{errorMessage(save.error)}</b>
          <span>Deze velden zijn bij jullie allebei anders:</span>
          <Table className="text-foreground">
            <TableHead columns="200px 1fr 1fr">
              <span>Veld</span>
              <span>Jouw versie</span>
              <span>Nu in ZCBS</span>
            </TableHead>
            {conflicts.map((row) => (
              <TableRow key={row.field} columns="200px 1fr 1fr">
                <span>{row.label}</span>
                <span className="font-medium text-ok">{row.mine}</span>
                <span>{row.theirs}</span>
              </TableRow>
            ))}
          </Table>
          <div className="flex flex-wrap gap-2">
            <Button type="button" size="small" onClick={keepMine}>
              Mijn versie bewaren
            </Button>
            <Button type="button" size="small" variant="secondary" onClick={takeTheirs}>
              Die van ZCBS gebruiken
            </Button>
          </div>
        </Alert>
      )}
      {save.isError && !blockers && !conflicts && problem?.code !== "record-locked" && <Alert variant="error" className="whitespace-pre-line">{errorMessage(save.error)}</Alert>}

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        <RecordDocument edit={edit} />
        <Card>
          {fields.map((field) => (
            <FieldRow key={field.name} field={field} values={values[field.name] ?? []} onChange={(next) => setValues((current) => ({ ...current, [field.name]: next }))} />
          ))}
        </Card>
      </div>

      <div className="fixed right-0 bottom-0 left-0 flex items-center justify-between gap-6 border-t border-sidebar-line bg-card px-4 py-4 md:left-60 md:px-10">
        <Button type="button" variant="secondary" title="Dit record niet opslaan en terug naar Records aanpassen" onClick={() => cancel.mutate(undefined, { onSettled: () => navigate(recordsPath(collection)) })}>
          Annuleren
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? "Opslaan…" : "Opslaan"}
        </Button>
      </div>
    </form>
  )
}

function RecordDocument({ edit }: { edit: Edit }) {
  return (
    <div className="flex flex-col gap-3.5">
      {edit.media?.kind === "pdf" ? (
        <>
          <iframe src={edit.media.url} title="De pdf van dit record" className="h-[480px] w-full rounded-[10px] border border-line" />
          <a href={edit.media.url} target="_blank" rel="noopener" className="text-[13px]">
            Open de pdf in een apart venster
          </a>
        </>
      ) : edit.media ? (
        <>
          <a href={edit.media.url} target="_blank" rel="noopener" className="block rounded-[10px] border border-line bg-neutral p-2">
            <img src={edit.media.url} alt="De afbeelding van dit record" className="block max-w-full" />
          </a>
          <span className="text-[13px] text-muted">De pdf van dit record staat (nog) niet op de site; dit is de afbeelding.</span>
        </>
      ) : (
        <div className="flex h-85 flex-col items-center justify-center gap-2 rounded-[10px] border border-line bg-neutral p-4 text-center text-[13px] text-muted">
          <span>de pdf van dit record staat (nog) niet op de site</span>
          <span>archief/pdf/{edit.number}.pdf</span>
        </div>
      )}
      {edit.ocrText && (
        <Card className="gap-1.5 px-4 py-3.5">
          <b className="text-[13px]">Tekst uit het document (ocr)</b>
          <p className="text-[13.5px] text-muted">{edit.ocrText}</p>
        </Card>
      )}
    </div>
  )
}

function FieldRow({ field, values, onChange }: { field: Field; values: string[]; onChange: (values: string[]) => void }) {
  const single = values[0] ?? ""
  return (
    <div className="grid gap-x-3.5 gap-y-1.5 sm:grid-cols-[170px_minmax(0,1fr)]">
      <div className="text-[13.5px] font-medium text-foreground-soft sm:pt-3">
        {field.kind === "RADIO" ? <span id={`${field.name}-label`}>{field.label}</span> : <label htmlFor={field.name}>{field.label}</label>}
        {field.help && <Help text={field.help} />}
      </div>
      <div>
        {field.kind === "TEXT" && <Input id={field.name} value={single} onChange={(event) => onChange([event.target.value])} />}
        {field.kind === "TEXTAREA" && <Textarea id={field.name} className="min-h-21" value={single} onChange={(event) => onChange([event.target.value])} />}
        {field.kind === "SELECT" && (
          <Select id={field.name} value={single} onChange={(event) => onChange([event.target.value])}>
            {field.options.map((option) => (
              <option key={option}>{option}</option>
            ))}
          </Select>
        )}
        {field.kind === "MULTI" && (
          <>
            <Select id={field.name} multiple size={6} value={values} onChange={(event) => onChange([...event.target.selectedOptions].map((option) => option.value))}>
              {field.options.map((option) => (
                <option key={option}>{option}</option>
              ))}
            </Select>
            <div className="mt-1 text-[13px] text-muted">Houd Ctrl (of Cmd) ingedrukt om meer dan één te kiezen.</div>
          </>
        )}
        {field.kind === "RADIO" && (
          <div role="radiogroup" aria-labelledby={`${field.name}-label`} className="flex gap-4.5 pt-2.75 text-sm">
            {field.options.map((option) => (
              <label key={option} className="flex items-center gap-1.5">
                <input type="radio" name={field.name} className="accent-primary" checked={single === option} onChange={() => onChange([option])} />
                {field.name === "a21" ? (option === "0" ? "neen" : "ja") : option}
              </label>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

/** Het vraagteken bij een veld met de helptekst van ZCBS, bij aanwijzen of met Tab. */
function Help({ text }: { text: string }) {
  return (
    <span tabIndex={0} role="note" aria-label={text} className="group relative ml-1.5 inline-flex size-4.5 cursor-help items-center justify-center rounded-full border border-neutral-line align-middle text-xs font-semibold text-muted">
      ?
      <span className="absolute top-[-6px] left-6 z-10 hidden w-95 max-w-[60vw] rounded-md border border-line bg-card px-3 py-2.5 text-left text-[13px] leading-snug font-normal whitespace-pre-line text-foreground shadow-md group-hover:block group-focus:block">
        {text}
      </span>
    </span>
  )
}
