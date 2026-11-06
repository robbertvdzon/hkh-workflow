import { type FormEvent, useState } from "react"
import { Link, useLocation, useNavigate, useParams, useSearchParams } from "react-router"
import { ApiError, errorMessage } from "@/api/client"
import { fetchMedia, openInZcbsUrl, type RecentRecord, recordsPath, type RecordScope, useNextToDescribe, useRecentRecords } from "@/api/records"
import { useLoadedSession } from "@/api/session"
import { useFileViewer } from "@/components/FileViewer"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { PageHeader } from "@/components/ui/page-header"
import { BackLink } from "@/components/ui/page-section"

const SCOPES: { scope: RecordScope; label: string; empty: string }[] = [
  { scope: "mine", label: "Alleen van mij", empty: "Je hebt nog geen records toegevoegd met deze app. Kies Alles om te zien wat anderen toevoegden." },
  { scope: "all", label: "Alles", empty: "Er zijn nog geen records toegevoegd met deze app." },
  { scope: "to-describe", label: "Nog te beschrijven", empty: "Alle recent toegevoegde records hebben een titel." },
]

const COLUMNS = "md:grid-cols-[64px_44px_94px_48px_76px_minmax(0,1fr)_300px] grid-cols-[64px_44px_minmax(0,1fr)]"

export function RecordsPage() {
  const session = useLoadedSession()
  const { collection = "" } = useParams()
  const location = useLocation()
  const [params] = useSearchParams()
  const saved = params.get("opgeslagen")
  const scope = SCOPES.find((it) => it.scope === params.get("toon")) ?? SCOPES[0]!
  const stateError = (location.state as { error?: string } | null)?.error

  return (
    <>
      <BackLink to={`/collectie/${encodeURIComponent(collection)}`}>Start</BackLink>
      <PageHeader title="Records aanpassen" tabTitle="ZCBS records aanpassen">
        Ingelogd bij ZCBS als <b>{session.user}</b>. Alles gaat via het formulier van ZCBS zelf, met teruglezen achteraf.
      </PageHeader>
      {stateError && <Alert variant="error">{stateError}</Alert>}
      {saved && <SavedNotice collection={collection} number={saved} />}
      <NumberForm collection={collection} />

      <section className="mt-5 max-w-[1180px]">
        <div className="mb-1 flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-xl">Recent toegevoegd</h2>
          <div className="flex flex-wrap gap-2">
            {SCOPES.map((it) => (
              <Button key={it.scope} asChild size="small" variant={it.scope === scope.scope ? "primary" : "secondary"}>
                <Link to={it.scope === "mine" ? "." : `?toon=${it.scope}`} replace>
                  {it.label}
                </Link>
              </Button>
            ))}
          </div>
        </div>
        <RecentList collection={collection} scope={scope.scope} empty={scope.empty} />
        <p className="mt-1.5 border-t border-line pt-2.5 text-[13px] text-muted">
          Records die met deze app zijn toegevoegd, nieuwste eerst, met wie en wanneer. Records die rechtstreeks in ZCBS zijn toegevoegd staan hier niet bij.
        </p>
      </section>
    </>
  )
}

function SavedNotice({ collection, number }: { collection: string; number: string }) {
  const next = useNextToDescribe(collection, number)
  return (
    <Alert variant="ok" className="flex flex-col gap-2.5">
      <span>Record {number} is opgeslagen en teruggecontroleerd. ZCBS stuurt zelf een e-mail met het record.</span>
      {next.isPending && <span className="text-[13px] text-muted">Het volgende record zoeken…</span>}
      {next.data?.number && (
        <div>
          <Button asChild size="small">
            <Link to={`${recordsPath(collection)}/${next.data.number}`}>Volgende om te beschrijven: {next.data.number}</Link>
          </Button>
        </div>
      )}
    </Alert>
  )
}

function NumberForm({ collection }: { collection: string }) {
  const [number, setNumber] = useState("")
  const [message, setMessage] = useState("")
  const navigate = useNavigate()
  const viewer = useFileViewer()

  /** Het ingevulde nummer, of null met een melding. */
  function validNumber(): string | null {
    const clean = number.trim()
    if (/^\d{1,9}$/.test(clean)) {
      setMessage("")
      return clean
    }
    setMessage(clean === "" ? "Vul een recordnummer in, bijvoorbeeld 11934." : "Vul één recordnummer in, bijvoorbeeld 11934.")
    return null
  }

  function edit(event: FormEvent) {
    event.preventDefault()
    const clean = validNumber()
    if (clean) navigate(`${recordsPath(collection)}/${clean}`)
  }

  async function view() {
    const clean = validNumber()
    if (!clean) return
    setMessage("Scan zoeken…")
    try {
      const media = await fetchMedia(collection, clean)
      setMessage("")
      viewer({ url: media.url, title: media.title ?? `Record ${clean}`, kind: media.kind })
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : "De scan kon niet worden opgehaald. Probeer het opnieuw.")
    }
  }

  function openZcbs() {
    const clean = validNumber()
    if (clean) window.open(openInZcbsUrl(collection, clean), "_blank")
  }

  return (
    <div>
      <form onSubmit={edit} className="flex max-w-[1180px] flex-wrap items-center gap-2.5">
        <Input
          aria-label="Recordnummer"
          className="h-12 min-w-55 flex-1 rounded-[10px] px-3.5 font-mono text-lg"
          inputMode="numeric"
          autoComplete="off"
          autoFocus
          placeholder="Recordnummer, bijvoorbeeld 11934"
          value={number}
          onChange={(event) => setNumber(event.target.value)}
        />
        <Button type="button" className="h-12" onClick={view} title="De pdf of afbeelding van dit record">
          Open
        </Button>
        <Button type="submit" className="h-12">
          Bewerk
        </Button>
        <Button type="button" className="h-12" onClick={openZcbs} title="Dit record in ZCBS zelf, met dezelfde inlog">
          Open in ZCBS
        </Button>
      </form>
      <p aria-live="polite" className="mt-1 min-h-5 text-[13px] text-danger">
        {message}
      </p>
    </div>
  )
}

function RecentList({ collection, scope, empty }: { collection: string; scope: RecordScope; empty: string }) {
  const query = useRecentRecords(collection, scope)
  const rows = query.data?.pages.flatMap((page) => page.items) ?? []
  return (
    <>
      <div aria-hidden className={`hidden gap-x-3 border-b border-line px-1.5 pt-3.5 pb-1.5 text-[11.5px] font-semibold text-muted md:grid ${COLUMNS}`}>
        <span>Nr</span>
        <span>Type</span>
        <span>Datum</span>
        <span>Tijd</span>
        <span>Gebruiker</span>
        <span>Titel</span>
        <span />
      </div>
      {query.isPending && <p className="py-3.5 text-[13px] text-muted">Recente records ophalen…</p>}
      {query.isError && <p className="py-3.5 text-[13px] text-muted">{errorMessage(query.error)}</p>}
      {query.isSuccess && rows.length === 0 && <p className="py-3.5 text-[13px] text-muted">{empty}</p>}
      {rows.map((record) => (
        <RecordRow key={record.number} collection={collection} record={record} />
      ))}
      {query.hasNextPage && (
        <div className="border-t border-line pt-3 pb-1">
          <Button variant="secondary" size="small" disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>
            {query.isFetchingNextPage ? "Laden…" : "Meer laden"}
          </Button>
        </div>
      )}
    </>
  )
}

function RecordRow({ collection, record }: { collection: string; record: RecentRecord }) {
  const viewer = useFileViewer()
  const [date, time] = dateAndTime(record.addedAt)
  const title = record.toDescribe ? "Nog niet beschreven" : (record.title ?? "")
  const fileWord = record.media?.kind === "pdf" ? "pdf" : "afbeelding"
  return (
    <div className={`grid items-center gap-x-3 border-t border-line px-1.5 py-2 text-sm hover:bg-primary-soft ${COLUMNS}`}>
      <span className="font-mono">{record.number}</span>
      <span className="text-muted">{record.media ? (record.media.kind === "pdf" ? "pdf" : "jpg") : ""}</span>
      <span className="hidden whitespace-nowrap text-muted md:inline">{date}</span>
      <span className="hidden whitespace-nowrap text-muted md:inline">{time}</span>
      <span className="hidden truncate text-muted md:inline" title={record.addedBy}>
        {record.addedBy.slice(0, 8)}
      </span>
      <span className="flex min-w-0 items-center gap-2">
        <span className={`min-w-0 truncate ${record.toDescribe ? "text-muted" : ""}`}>{title}</span>
        {record.busyBy && <span className="rounded-md bg-busy-soft px-2 py-px text-[11.5px] whitespace-nowrap text-busy">{record.busyBy} heeft dit open</span>}
      </span>
      <span className="col-span-full grid grid-cols-[62px_80px_128px] gap-2 pt-1.5 md:col-span-1 md:pt-0">
        {record.media ? (
          <Button
            size="small"
            className="px-0"
            title={record.media.kind === "pdf" ? "Open de pdf van dit record" : "Open de afbeelding van dit record"}
            onClick={() => viewer({ url: record.media!.url, title: `${record.number} – ${record.toDescribe || !record.title ? fileWord : record.title}`, kind: record.media!.kind })}
          >
            Open
          </Button>
        ) : (
          <span />
        )}
        <Button asChild size="small" className="px-0">
          <Link to={`${recordsPath(collection)}/${record.number}`} aria-label={`Bewerk ${record.number}`}>
            Bewerk
          </Link>
        </Button>
        <Button asChild size="small" className="px-0">
          <a href={openInZcbsUrl(collection, record.number)} target="_blank" rel="noopener">
            Open in ZCBS
          </a>
        </Button>
      </span>
    </div>
  )
}

/** `2026-11-06T19:40:12` wordt `06-11-2026` en `19:40`. De tijd staat al in de tijdzone van de server. */
function dateAndTime(value: string): [string, string] {
  const [date = "", time = ""] = value.split("T")
  return [date.split("-").reverse().join("-"), time.slice(0, 5)]
}

