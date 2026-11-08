import { type MouseEvent, useRef, useState } from "react"
import { Link, useParams, useSearchParams } from "react-router"
import { errorMessage } from "@/api/client"
import {
  consoleLogUrl,
  type Flow,
  flowActions,
  resultUrl,
  sourceFileUrl,
  type Step,
  type StepName,
  useChecks,
  useChosenFiles,
  useEntries,
  useFileDetails,
  useFlow,
  useFlowAction,
  useLiveConsole,
  useNumbers,
  useResults,
  useSources,
  useSteps,
  useSummary,
} from "@/api/flow"
import { recordsPath } from "@/api/records"
import { useFileViewer } from "@/components/FileViewer"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Chip } from "@/components/ui/chip"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { PageHeader } from "@/components/ui/page-header"
import { BackLink, QueryState } from "@/components/ui/page-section"
import { Table, TableHead, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"

type Actions = ReturnType<typeof flowActions>
type Act = (action: (actions: Actions) => Promise<unknown>, info?: string | ((result: never) => string | null)) => void

const STEP_OF_PHASE: Record<number, StepName> = { 4: "pdf2zcbs", 5: "images", 6: "upload", 7: "backup" }
const PROGRAM_NAME: Record<string, string> = { pdf2zcbs: "pdf2zcbs.exe", images: "images.exe" }

/** "Nieuwe bestanden in ZCBS zetten": de scans kiezen, nummers geven, de twee programma's draaien, uploaden en de back-up. De fase komt van de server. */
export function NewFilesPage() {
  const { collection = "" } = useParams()
  const flow = useFlow(collection)
  const action = useFlowAction(collection)
  const [info, setInfo] = useState<string | null>(null)

  const act: Act = (run, message) => {
    setInfo(null)
    action.mutate(() => run(flowActions(collection)), {
      onSuccess: (result) => {
        const text = typeof message === "function" ? message(result as never) : message
        if (text) setInfo(text)
      },
    })
  }

  return (
    <>
      <BackLink to={`/collectie/${encodeURIComponent(collection)}`}>Start</BackLink>
      <div className="flex flex-wrap items-end justify-between gap-6">
        <PageHeader title="Nieuwe bestanden in ZCBS zetten" tabTitle="Nieuwe bestanden">
          Van de scans op K: naar het archief, stap voor stap. De gegevens (titel enzovoort) vul je op een andere dag in bij ZCBS records aanpassen.
        </PageHeader>
        {flow.data && flow.data.phase >= 2 && !flow.data.finished && <StartOver phase={flow.data.phase} act={act} />}
      </div>
      {action.isError && <Alert variant="error" className="whitespace-pre-line">{errorMessage(action.error)}</Alert>}
      {info && <Alert variant="ok">{info}</Alert>}
      <QueryState query={flow}>
        {(data) => (
          <>
            <PhaseStrip flow={data} />
            <CurrentPhase collection={collection} flow={data} act={act} busy={action.isPending} />
          </>
        )}
      </QueryState>
    </>
  )
}

function StartOver({ phase, act }: { phase: number; act: Act }) {
  const question = phase <= 3 ? "Opnieuw beginnen? Je keuze van bestanden wordt gewist; de bestanden op K: blijven staan." : "Opnieuw beginnen? Wat je hier hebt gedaan wordt gewist; bestanden op schijf blijven staan."
  return (
    <Button
      variant="secondary"
      size="small"
      title="Stopt een nog lopend programma en begint opnieuw"
      onClick={() => {
        if (window.confirm(question)) act((actions) => actions.reset())
      }}
    >
      Opnieuw beginnen
    </Button>
  )
}

function PhaseStrip({ flow }: { flow: Flow }) {
  return (
    <ol className="flex flex-wrap gap-2">
      {flow.phases.map((phase) => (
        <li
          key={phase.number}
          aria-current={phase.state === "now" ? "step" : undefined}
          className={cn(
            "inline-flex items-center gap-2 rounded-full border border-line bg-card py-1.25 pr-3 pl-1.5 text-[13px] text-muted",
            phase.state === "done" && "border-ok-soft bg-ok-soft text-ok",
            phase.state === "now" && "border-primary bg-primary-soft font-semibold text-primary",
          )}
        >
          <span
            className={cn(
              "flex size-5.5 items-center justify-center rounded-full border border-neutral-line text-xs font-semibold",
              phase.state === "done" && "border-ok bg-ok text-white",
              phase.state === "now" && "border-primary text-primary",
            )}
          >
            {phase.state === "done" ? "✓" : phase.number}
          </span>
          {phase.title}
          {phase.note && <span className="text-xs font-medium">{phase.note}</span>}
        </li>
      ))}
    </ol>
  )
}

type PhaseProps = { collection: string; flow: Flow; act: Act; busy: boolean }

function CurrentPhase(props: PhaseProps) {
  const { flow } = props
  if (flow.finished) return <Finished {...props} />
  if (flow.phase === 1) return <ChecksPhase {...props} />
  if (flow.phase === 2) return <PickPhase {...props} />
  if (flow.phase === 3) return <NumbersPhase {...props} />
  return <StepsPhase {...props} />
}

// ---- 1. Controleren ----

function ChecksPhase({ collection, act, busy }: PhaseProps) {
  const checks = useChecks(collection)
  return (
    <Card>
      <h2 className="text-xl">1. Controleer of alles klaarstaat</h2>
      <QueryState query={checks} loading="Controleren…">
        {(data) => (
          <>
            <ul className="flex flex-col gap-2">
              {data.checks.map((check) => (
                <li key={check.id} className="flex items-start gap-3">
                  <span className={cn("mt-px flex size-5.5 flex-none items-center justify-center rounded-full text-[13px] font-bold", check.ok ? "bg-ok-soft text-ok" : "bg-danger-soft text-danger")}>{check.ok ? "✓" : "✗"}</span>
                  <span>
                    <b>{check.label}</b>
                    {check.detail && <span className={cn("mt-0.5 block text-[13px] wrap-anywhere", check.ok ? "text-muted" : "text-danger")}>{check.detail}</span>}
                  </span>
                </li>
              ))}
            </ul>
            {data.leftovers.length > 0 && (
              <div className="flex flex-col gap-3.5 rounded-[10px] border border-busy bg-busy-soft p-4">
                <h3 className="font-semibold">Eerst opruimen: er staat nog werk van een eerdere keer</h3>
                <p className="text-muted">
                  pdf2zcbs.exe wist zijn map <span className="font-mono">output</span> bij de start, images.exe zou oude afbeeldingen opnieuw uploaden, en bestanden die je eerder hebt gekozen zouden ongemerkt meedoen. Zet ze
                  dus eerst opzij of verwijder ze.
                </p>
                <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm">
                  {data.leftovers.map((leftover) => (
                    <li key={leftover.area}>
                      <b>{leftover.label}</b>
                      <span className="block text-[13px] wrap-anywhere text-muted">
                        {leftover.files.length} {leftover.files.length === 1 ? "bestand" : "bestanden"}: {leftover.files.slice(0, 6).join(", ")}
                        {leftover.files.length > 6 && ` en ${leftover.files.length - 6} andere`}
                      </span>
                    </li>
                  ))}
                </ul>
                <div className="flex flex-wrap gap-2">
                  <Button disabled={busy || !data.leftoversTarget} onClick={() => act((a) => a.moveLeftovers(), (r: { message: string }) => r.message)}>
                    Verplaats ze naar een map met resten
                  </Button>
                  {data.leftovers.some((it) => it.area === "invoer") && (
                    <Button variant="secondary" disabled={busy} title="Bijvoorbeeld als je de app opnieuw hebt gestart en deze bestanden zelf hebt klaargezet" onClick={() => act((a) => a.acceptLeftovers(), "Je gekozen bestanden horen erbij.")}>
                      Mijn gekozen bestanden horen erbij
                    </Button>
                  )}
                  <Button
                    variant="danger"
                    disabled={busy}
                    onClick={() => {
                      if (window.confirm("Deze bestanden definitief verwijderen? Dit kan niet ongedaan worden gemaakt.")) act((a) => a.deleteLeftovers(), (r: { message: string }) => r.message)
                    }}
                  >
                    Verwijder ze
                  </Button>
                </div>
                {data.leftoversTarget && (
                  <p className="text-[13px] text-muted">
                    Verplaatsen zet ze in een submap met datum en tijd onder <span className="font-mono">{data.leftoversTarget}</span>, met dezelfde mapindeling, zodat je ze altijd kunt terugvinden. Alleen deze bestanden worden
                    aangeraakt.
                  </p>
                )}
              </div>
            )}
            <div>
              <Button disabled={busy || !data.allOk} onClick={() => act((a) => a.start())}>
                {data.allOk ? "Alles in orde: verder met bestanden kiezen" : "Los eerst de rode punten op"}
              </Button>
            </div>
          </>
        )}
      </QueryState>
    </Card>
  )
}

// ---- 2. Bestanden kiezen ----

const FILE_COLUMNS = "28px minmax(120px,1fr) 62px 110px 124px 130px 62px"

function PickPhase({ collection, act, busy }: PhaseProps) {
  const [params, setParams] = useSearchParams()
  const sources = useSources(collection)
  const chosen = useChosenFiles(collection)
  const source = params.get("bron") ?? sources.data?.[0]?.name ?? null
  const path = params.get("pad") ?? ""
  const entries = useEntries(collection, source, path)
  const chosenFiles = chosen.data ?? []

  function browse(nextSource: string, nextPath = "") {
    setParams(nextPath ? { bron: nextSource, pad: nextPath } : { bron: nextSource })
  }

  return (
    <>
      <Card>
        <h2 className="text-xl">2. Bestanden kiezen</h2>
        {sources.data?.length === 0 && <p className="text-[13px] text-busy">Er zijn geen mappen met scans ingesteld. Dat doet de beheerder bij Instellingen.</p>}
        {!!sources.data?.length && (
          <div className="flex flex-wrap items-center gap-2.5">
            <span className="text-[13px] text-muted">Scans van:</span>
            {sources.data.map((it) => (
              <Button key={it.name} size="small" variant={it.name === source ? "primary" : "secondary"} title={it.path} onClick={() => browse(it.name)}>
                {it.name}
              </Button>
            ))}
          </div>
        )}
        {source && (
          <QueryState query={entries} loading="De map lezen…">
            {(listing) => (
              <>
                <nav aria-label="Pad" className="flex flex-wrap items-center gap-2 text-sm">
                  <button className="cursor-pointer text-primary underline" onClick={() => browse(source)}>
                    {listing.source}
                  </button>
                  {listing.crumbs.map((crumb) => (
                    <span key={crumb.path} className="flex items-center gap-2">
                      <span className="text-muted">›</span>
                      <button className="cursor-pointer text-primary underline" onClick={() => browse(source, crumb.path)}>
                        {crumb.name}
                      </button>
                    </span>
                  ))}
                </nav>
                {listing.folders.length > 0 && (
                  <div className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-2">
                    {listing.folders.map((folder) => (
                      <button key={folder.path} className="flex cursor-pointer items-center gap-2 rounded-lg border border-line bg-card px-3 py-2.5 text-left text-sm hover:border-primary" onClick={() => browse(source, folder.path)}>
                        <span aria-hidden>📁</span>
                        {folder.name}
                      </button>
                    ))}
                  </div>
                )}
                {listing.files.length === 0 && <p className="text-[13px] text-muted">{listing.folders.length === 0 ? "Deze map is leeg." : "Geen pdf's of afbeeldingen in deze map; open een submap."}</p>}
                {listing.files.length > 0 && (
                  <FilePicker
                    key={`${source}/${path}`}
                    collection={collection}
                    source={source}
                    path={path}
                    files={listing.files}
                    canContinueEmpty={chosenFiles.length > 0}
                    busy={busy}
                    onChoose={(names) => act((a) => a.chooseFiles(source, path, names), (r: { skipped: string[] }) => (r.skipped.length ? `Overgeslagen: ${r.skipped.join(", ")}.` : null))}
                  />
                )}
              </>
            )}
          </QueryState>
        )}
      </Card>
      {chosenFiles.length > 0 && (
        <Alert>
          <b>{chosenFiles.length === 1 ? "1 bestand stond al klaar en doet mee:" : `${chosenFiles.length} bestanden stonden al klaar en doen mee:`}</b> <span className="font-mono">{chosenFiles.map((it) => it.name).join(", ")}</span>{" "}
          <Button variant="secondary" size="small" disabled={busy} onClick={() => act((a) => a.clearFiles())}>
            Haal ze weg
          </Button>
        </Alert>
      )}
    </>
  )
}

type SourceFile = { name: string; kind: string; sizeKb: number; modified: string; processed: { number: string; user: string; date: string } | null }

function FilePicker({ collection, source, path, files, canContinueEmpty, busy, onChoose }: { collection: string; source: string; path: string; files: SourceFile[]; canContinueEmpty: boolean; busy: boolean; onChoose: (names: string[]) => void }) {
  const viewer = useFileViewer()
  const details = useFileDetails(collection, source, path)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const last = useRef<string | null>(null)
  const available = files.filter((file) => !file.processed).map((file) => file.name)
  const allOn = available.length > 0 && available.every((name) => selected.has(name))

  /** Shift-klik kiest alles tussen twee bestanden ("X t/m Y"). */
  function toggle(event: MouseEvent<HTMLInputElement>, name: string) {
    const on = event.currentTarget.checked
    const from = last.current ? available.indexOf(last.current) : -1
    const to = available.indexOf(name)
    const names = event.shiftKey && from >= 0 && from !== to ? available.slice(Math.min(from, to), Math.max(from, to) + 1) : [name]
    last.current = name
    setSelected((current) => {
      const next = new Set(current)
      names.forEach((it) => (on ? next.add(it) : next.delete(it)))
      return next
    })
  }

  return (
    <div className="flex flex-col gap-3.5">
      <Table>
        <TableHead columns={FILE_COLUMNS}>
          <input type="checkbox" className="size-4.5 accent-primary" aria-label="Alles aanvinken" title="Alles aanvinken" checked={allOn} onChange={(event) => setSelected(new Set(event.target.checked ? available : []))} />
          <span>Bestand</span>
          <span>Grootte</span>
          <span>Afmeting / pagina's</span>
          <span>Gescand</span>
          <span>Status</span>
          <span />
        </TableHead>
        {files.map((file) => (
          <TableRow key={file.name} columns={FILE_COLUMNS} className={file.processed ? "opacity-60" : ""}>
            <input
              type="checkbox"
              className="size-4.5 accent-primary"
              aria-label={file.name}
              disabled={!!file.processed}
              checked={selected.has(file.name)}
              onClick={(event) => toggle(event, file.name)}
              readOnly
            />
            <span className="flex min-w-0 items-center gap-2.5">
              <span className="flex h-9.5 w-7.5 flex-none items-end justify-center rounded-sm border border-neutral-line bg-background pb-1 text-[9px] font-semibold text-danger">{extension(file.name)}</span>
              <span className="truncate font-mono" title={file.name}>
                {file.name}
              </span>
            </span>
            <span className="text-muted">{file.sizeKb} kB</span>
            <span className="text-muted">{details.data ? (details.data[file.name] ?? "–") : details.isError ? "?" : "…"}</span>
            <span className="text-muted">{file.modified}</span>
            {file.processed ? (
              <span>
                <Chip variant="ok" title={`Verwerkt door ${file.processed.user} op ${file.processed.date}`}>
                  Al verwerkt: {file.processed.number}
                </Chip>
              </span>
            ) : (
              <span className="text-muted">–</span>
            )}
            <Button variant="secondary" size="small" aria-label={`Open ${file.name}`} onClick={() => viewer({ url: sourceFileUrl(collection, source, path, file.name), title: file.name, kind: file.kind })}>
              Open
            </Button>
          </TableRow>
        ))}
      </Table>
      <p className="text-[13px] text-muted">
        Tip: vink het eerste bestand aan, houd Shift ingedrukt en klik op het laatste: dan staat alles ertussen ook aan ("X t/m Y"). Bestanden die al verwerkt zijn, kun je niet aanvinken. Een jpg of png gaat niet door pdf2zcbs.exe
        (geen tekst of pdf) maar rechtstreeks naar images.exe.{selected.size > 0 && ` ${selected.size} aangevinkt.`}
      </p>
      <div>
        <Button disabled={busy || (selected.size === 0 && !canContinueEmpty)} onClick={() => onChoose([...selected])}>
          Verder
        </Button>
      </div>
    </div>
  )
}

function extension(name: string) {
  return name.split(".").pop()?.toUpperCase().replace("JPEG", "JPG") ?? ""
}

// ---- 3. Nummers ----

const RANGE_COLUMNS = "minmax(0,1.4fr) 110px 150px 130px"

function NumbersPhase({ collection, act, busy }: PhaseProps) {
  const numbers = useNumbers(collection)
  return (
    <Card>
      <h2 className="text-xl">3. Nummers</h2>
      <QueryState query={numbers} loading="De vrije nummers ophalen uit ZCBS…">
        {(data) => <NumbersForm key={data.suggestion} numbers={data} act={act} busy={busy} />}
      </QueryState>
    </Card>
  )
}

function NumbersForm({ numbers, act, busy }: { numbers: { count: number; width: number; lastNumber: string | null; suggestion: string | null; ranges: { from: string; label: string; size: number | null; fits: boolean }[] }; act: Act; busy: boolean }) {
  const [start, setStart] = useState(numbers.suggestion ?? "")
  const input = useRef<HTMLInputElement>(null)
  const first = Number.parseInt(start, 10)
  const pad = (value: number) => String(value).padStart(numbers.width, "0")
  return (
    <>
      <p className="text-muted">
        Je hebt <b>{numbers.count}</b> {numbers.count === 1 ? "bestand" : "bestanden"} gekozen. Ze krijgen aaneengesloten recordnummers: kies een beginnummer uit de nummers die in ZCBS nog vrij zijn.
      </p>
      {numbers.lastNumber && (
        <p>
          Laatste record in ZCBS: <b className="font-mono">{numbers.lastNumber}</b>
        </p>
      )}
      <Table className="max-h-75">
        <TableHead columns={RANGE_COLUMNS}>
          <span>Vrije nummers</span>
          <span>Aantal</span>
          <span>Passen {numbers.count} bestanden?</span>
          <span />
        </TableHead>
        {numbers.ranges.map((range) => (
          <TableRow key={range.from} columns={RANGE_COLUMNS} className={range.fits ? "" : "opacity-55"}>
            <span className="font-mono">{range.label}</span>
            <span>{range.size ?? "onbeperkt"}</span>
            <span>
              <Chip variant={range.fits ? "ok" : "neutral"}>{range.fits ? "Ja, past" : "Te klein"}</Chip>
            </span>
            <span>
              {range.fits && (
                <Button
                  variant="secondary"
                  size="small"
                  onClick={() => {
                    setStart(range.from)
                    input.current?.focus()
                  }}
                >
                  Begin hier
                </Button>
              )}
            </span>
          </TableRow>
        ))}
      </Table>
      <p className="text-[13px] text-muted">Zo zijn de nummers ook te zien als je in ZCBS een nieuw record maakt. Een reeks bestanden moet in één vrije reeks passen; zijn er meer bestanden dan er ruimte is, dan weigert de app dat.</p>
      <form
        className="flex flex-col gap-3.5"
        onSubmit={(event) => {
          event.preventDefault()
          act((a) => a.assignNumbers(start))
        }}
      >
        <div className="flex max-w-65 flex-col gap-1.5">
          <Label htmlFor="start">Eerste recordnummer</Label>
          <Input ref={input} id="start" required className="font-mono" inputMode="numeric" value={start} onChange={(event) => setStart(event.target.value)} />
        </div>
        <p>
          De bestanden krijgen de nummers <b className="font-mono">{Number.isNaN(first) ? "?" : pad(first)}</b> t/m <b className="font-mono">{Number.isNaN(first) ? "?" : pad(first + numbers.count - 1)}</b>.
        </p>
        <p className="text-[13px] text-muted">De app geeft de bestanden het recordnummer als naam, op een kopie. De originelen blijven staan.</p>
        <div>
          <Button type="submit" disabled={busy}>
            Geef de bestanden deze nummers en ga verder
          </Button>
        </div>
      </form>
    </>
  )
}

// ---- 4 t/m 7. De stappen ----

function StepsPhase({ collection, flow, act, busy }: PhaseProps) {
  const steps = useSteps(collection, flow.running)
  const title = flow.phases[flow.phase - 1]?.title
  return (
    <>
      <h2 className="text-xl">
        {flow.phase}. {title}
      </h2>
      {flow.review ? (
        <Review collection={collection} step={STEP_OF_PHASE[flow.phase]!} steps={steps.data ?? []} act={act} busy={busy} />
      ) : (
        <>
          {flow.phase >= 6 && <FileList collection={collection} />}
          <QueryState query={steps}>{(data) => <CurrentStep collection={collection} flow={flow} step={data.find((it) => it.current) ?? null} act={act} busy={busy} />}</QueryState>
        </>
      )}
    </>
  )
}

function FileList({ collection }: { collection: string }) {
  const files = useChosenFiles(collection)
  return (
    <Table>
      <TableHead columns="minmax(0,1fr) 140px">
        <span>Bestand</span>
        <span>Nummer</span>
      </TableHead>
      {files.data?.map((file) => (
        <TableRow key={file.name} columns="minmax(0,1fr) 140px">
          <span className="truncate" title={file.name}>
            {file.name}
          </span>
          <span className="font-mono">{file.newName}</span>
        </TableRow>
      ))}
    </Table>
  )
}

function CurrentStep({ collection, flow, step, act, busy }: { collection: string; flow: Flow; step: Step | null; act: Act; busy: boolean }) {
  if (!step) return null
  const name = step.name as StepName
  const blocked = step.problems.length > 0
  const running = step.status === "RUNNING"
  return (
    <>
      <section className="flex flex-wrap items-start justify-between gap-5 rounded-xl border-2 border-primary bg-card px-6 py-5">
        <div className="flex min-w-0 flex-[1_1_380px] flex-col gap-1.5">
          <span className="text-lg font-semibold">{step.usesProgram ? (running ? `${step.label} draait` : `Start ${step.label}`) : step.question}</span>
          {step.status === "PENDING" && step.plan.length > 0 && (
            <details>
              <summary className="cursor-pointer text-[13px] text-muted">Wat gebeurt er?</summary>
              <ul className="mt-0.5 flex list-disc flex-col gap-0.5 pl-4.5 text-[13.5px] text-muted">
                {step.plan.map((line) => (
                  <li key={line}>{line}</li>
                ))}
              </ul>
            </details>
          )}
          {step.problems.length > 0 && (
            <ul className="mt-0.5 flex list-disc flex-col gap-0.5 pl-4.5 text-[13.5px] font-semibold text-danger">
              {step.problems.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          )}
          {step.warnings.map((warning) => (
            <Alert key={warning} className="mt-1">
              {warning}
            </Alert>
          ))}
          {step.status === "FAILED" && <span className="text-[13px] whitespace-pre-line text-danger">{step.message}</span>}
          {running && !step.usesProgram && <span className="text-[13px] text-muted">Bezig… even geduld, het scherm gaat vanzelf verder als het klaar is.</span>}
        </div>
        {step.status === "PENDING" &&
          (step.usesProgram ? (
            <div className="flex gap-2">
              <Button variant="secondary" disabled={busy || blocked} title="Jij beantwoordt de vragen hieronder; de app onthoudt je antwoorden voor de volgende keer" onClick={() => act((a) => a.run(name, "manual"))}>
                Handmatig uitvoeren
              </Button>
              <Button disabled={busy || blocked} title="De app geeft zelf de antwoorden die hij van de laatste handmatige keer onthield" onClick={() => act((a) => a.run(name, "auto"))}>
                Automatisch uitvoeren
              </Button>
            </div>
          ) : (
            <Button disabled={busy || blocked} onClick={() => act((a) => a.run(name, "auto"))}>
              Uitvoeren
            </Button>
          ))}
        {step.status === "FAILED" && (
          <div className="flex gap-2">
            {step.usesProgram && <LogButton collection={collection} program={step.label} />}
            <Button disabled={busy} onClick={() => act((a) => a.retry(name))}>
              Opnieuw
            </Button>
          </div>
        )}
      </section>
      {running && step.usesProgram && flow.running && <ProgramConsole collection={collection} act={act} />}
    </>
  )
}

/** Toont het log van de laatste run in een venster boven het scherm. */
function LogButton({ collection, program }: { collection: string; program: string }) {
  const viewer = useFileViewer()
  return (
    <Button variant="secondary" onClick={() => viewer({ url: consoleLogUrl(collection), title: `Log van ${program}`, kind: "text" })}>
      Bekijk log
    </Button>
  )
}

/** De uitvoer van het programma en de vragen ervan. Bij automatisch uitvoeren blijft dit dicht, behalve als de app een vraag niet herkent. */
function ProgramConsole({ collection, act }: { collection: string; act: Act }) {
  const console = useLiveConsole(collection)
  if (!console || !console.running) return <p className="text-[13px] text-muted">Bezig… even geduld, het scherm gaat vanzelf verder.</p>
  if (console.auto && !console.autoStuck) {
    return (
      <div className="flex items-center gap-2.5 text-[13px] text-muted">
        <Chip variant="busy">Bezig</Chip>
        <span>{console.label}</span>
        <span>Even geduld, het scherm gaat vanzelf verder.</span>
      </div>
    )
  }
  const answering = !console.auto || console.autoStuck
  const suggested = console.suggestion !== null && !console.auto
  return (
    <section className="flex flex-col gap-2.5">
      <div className="flex items-center justify-between">
        <span className="font-semibold">
          {console.label}
          {console.auto && " (automatisch)"}
        </span>
        <Chip variant="busy">Draait</Chip>
      </div>
      <div className="flex flex-wrap gap-3.5 text-[13px] text-muted">
        <span>Laatste uitvoer: {console.secondsSinceOutput} s geleden</span>
        <span>Invoerregels verstuurd: {console.inputsSent}</span>
        {console.auto && <span>Automatisch beantwoord: {console.autoAnswered}</span>}
        <a href={consoleLogUrl(collection)} target="_blank" rel="noopener">
          Volledig log
        </a>
      </div>
      {console.autoStuck && <Alert variant="error">Automatisch: deze vraag wordt door geen enkele regel herkend. Beantwoord hem hieronder handmatig.</Alert>}
      {console.suspiciousSilence && (
        <Alert>
          {console.label?.includes("pdf2zcbs") && <b>Bij pdf2zcbs.exe is dat normaal als pdf2zcbs.ini de vragen al beantwoordt: het programma toont dan niets en klaar is klaar. </b>}
          Er komt geen uitvoer (meer) terwijl het programma draait. Wacht het op een antwoord op een vraag die je niet ziet? Dan houdt het zijn uitvoer waarschijnlijk vast (buffering). Probeer blind een antwoord te sturen, of stop en
          start opnieuw met PERLIO=:unix.
        </Alert>
      )}
      {suggested && (
        <Alert>
          Voorgesteld antwoord op deze vraag: <b>{console.suggestion === "" ? "(alleen Enter)" : console.suggestion}</b>.{" "}
          {console.suggestion === "" ? "Dat is een lege regel, dus er staat niets in het invoerveld: druk op de gemarkeerde knop Enter om het te gebruiken, of typ iets anders." : "Het staat al in het invoerveld; druk op Enter of Verstuur om het te gebruiken, of typ iets anders."}
        </Alert>
      )}
      <ConsoleOutput text={console.output} />
      {answering && (
        <AnswerForm
          // Een nieuwe vraag begint met een schoon veld, met het voorgestelde antwoord erin.
          key={`${console.question}#${console.inputsSent}`}
          suggestion={suggested ? console.suggestion : null}
          act={act}
        />
      )}
    </section>
  )
}

function ConsoleOutput({ text }: { text: string }) {
  const scrollToEnd = (element: HTMLPreElement | null) => {
    if (element) element.scrollTop = element.scrollHeight
  }
  return (
    <pre ref={scrollToEnd} aria-label="Uitvoer van het programma" className="h-85 overflow-auto rounded-[10px] bg-console px-4.5 py-4 font-mono text-[13px] leading-snug whitespace-pre-wrap text-console-ink">
      {text || "(nog geen uitvoer)"}
    </pre>
  )
}

function AnswerForm({ suggestion, act }: { suggestion: string | null; act: Act }) {
  const [line, setLine] = useState(suggestion ?? "")
  const send = (answer: string) => {
    act((a) => a.answer(answer))
    setLine("")
  }
  return (
    <div className="flex flex-wrap items-center gap-2">
      <form
        className="flex grow gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          send(line)
        }}
      >
        <Input
          aria-label="Antwoord"
          autoComplete="off"
          autoFocus
          onFocus={(event) => event.target.select()}
          placeholder={suggestion === "" ? "Onthouden antwoord: alleen Enter (druk op Enter)" : "Typ een antwoord en druk op Enter"}
          value={line}
          onChange={(event) => setLine(event.target.value)}
        />
        <Button type="submit">Verstuur</Button>
      </form>
      <Button variant="secondary" className={suggestion === "" ? "ring-2 ring-primary" : ""} onClick={() => send("")}>
        Enter
      </Button>
      <Button variant="secondary" onClick={() => send("j")}>
        J
      </Button>
      <Button variant="secondary" onClick={() => send("n")}>
        N
      </Button>
      <Button variant="danger" onClick={() => act((a) => a.stop())}>
        Stop programma
      </Button>
    </div>
  )
}

// ---- Controleren wat een programma heeft gemaakt ----

function Review({ collection, step, steps, act, busy }: { collection: string; step: StepName; steps: Step[]; act: Act; busy: boolean }) {
  const results = useResults(collection, step)
  const viewer = useFileViewer()
  const images = step === "images"
  const columns = `76px minmax(40px,1fr) 62px 70px 124px ${images ? "84px " : ""}62px`
  const message = steps.find((it) => it.name === step)?.message
  const program = PROGRAM_NAME[step]
  return (
    <Card>
      <h3 className="font-semibold">Dit heeft {program} gemaakt</h3>
      {images && message && <Alert variant="ok" className="whitespace-pre-line">{message}</Alert>}
      <QueryState query={results}>
        {(data) => (
          <Table>
            <TableHead columns={columns}>
              <span>Nummer</span>
              <span>Bestand</span>
              <span>Soort</span>
              <span>Grootte</span>
              <span>Afmeting</span>
              {images && <span>Op de server</span>}
              <span />
            </TableHead>
            {data.map((result, index) => {
              const first = data[index - 1]?.number !== result.number
              return (
                <TableRow key={`${result.number}/${result.kind}`} columns={columns} className={first ? "border-t border-line" : ""}>
                  <span className="font-mono">{first ? result.number : ""}</span>
                  <span className="truncate text-muted" title={result.originalName ?? undefined}>
                    {first ? result.originalName : ""}
                  </span>
                  <span>{result.label}</span>
                  <span>{result.size}</span>
                  <span>
                    {result.resolution || "–"}
                    {result.note && <span className="text-[13px] text-busy"> {result.note}</span>}
                  </span>
                  {images && <span>{result.onServer === null ? <span className="text-muted">–</span> : <Chip variant={result.onServer ? "ok" : "error"}>{result.onServer ? "ja" : "nee"}</Chip>}</span>}
                  <Button
                    variant="secondary"
                    size="small"
                    aria-label={`Open ${result.number} ${result.label}`}
                    onClick={() => viewer({ url: resultUrl(collection, result.number, result.kind), title: `${result.number} – ${result.label}`, kind: result.kind === "pdf" ? "pdf" : result.kind === "tekst" ? "text" : "image" })}
                  >
                    Open
                  </Button>
                </TableRow>
              )
            })}
          </Table>
        )}
      </QueryState>
      <div className="flex flex-wrap gap-2">
        <Button disabled={busy} onClick={() => act((a) => a.approve(step))}>
          {images ? "Ja, goed: verder met de upload" : "Ja, goed: verder met images.exe"}
        </Button>
        <Button
          variant="secondary"
          disabled={busy}
          onClick={() => {
            if (!images || window.confirm("images.exe uploadt en maakt records aan. Opnieuw uitvoeren kan problemen geven. Weet je het zeker?")) act((a) => a.retry(step))
          }}
        >
          Niet goed: {program} opnieuw uitvoeren
        </Button>
        <LogButton collection={collection} program={program ?? step} />
      </div>
    </Card>
  )
}

// ---- Klaar ----

function Finished({ collection, act, busy }: PhaseProps) {
  const files = useChosenFiles(collection)
  const summary = useSummary(collection, true)
  const rows = files.data ?? []
  const columns = "150px 110px minmax(0,1fr) minmax(0,2fr)"
  return (
    <Card>
      <h2 className="text-xl">Klaar</h2>
      <p>
        <b>{rows.length}</b> {rows.length === 1 ? "bestand staat" : "bestanden staan"} nu op het archief, met de recordnummers <b className="font-mono">{rows[0]?.number}</b> t/m <b className="font-mono">{rows.at(-1)?.number}</b>. De
        100pct en de pdf's hebben een back-up en de werkmappen zijn opgeruimd. De nieuwe records staan onder <Link to={recordsPath(collection)}>Recent toegevoegd bij Records aanpassen</Link>.
      </p>
      <p className="text-muted">De gegevens (titel enzovoort) vul je op een andere dag in via ZCBS records aanpassen.</p>
      {!!summary.data?.length && (
        <Table>
          <TableHead columns={columns}>
            <span>Waar</span>
            <span>Soort</span>
            <span>Bestanden</span>
            <span>Map</span>
          </TableHead>
          {summary.data.map((line) => (
            <TableRow key={`${line.place}/${line.kind}`} columns={columns}>
              <span>{line.place.startsWith("Server") ? "Server" : line.place}</span>
              <span>{line.kind}</span>
              <span className="font-mono">{line.files}</span>
              <span className="font-mono wrap-anywhere">{line.folder}</span>
            </TableRow>
          ))}
        </Table>
      )}
      <p className="text-[13px] text-muted">De originelen op K: zijn niet aangeraakt.</p>
      <div>
        <Button disabled={busy} onClick={() => act((a) => a.reset())}>
          Nieuwe bestanden verwerken
        </Button>
      </div>
    </Card>
  )
}
