import { useEffect } from "react"
import { errorMessage } from "@/api/client"
import { useCheckVersion, useSetNightly, useUpdateNow, useVersion, type Version } from "@/api/admin"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { PageHeader } from "@/components/ui/page-header"
import { BackLink, QueryState } from "@/components/ui/page-section"
import { dateTime } from "@/lib/time"

const MB = 1_048_576

export function VersionPage() {
  const version = useVersion()
  return (
    <>
      <BackLink to="/instellingen">Instellingen</BackLink>
      <PageHeader title="Versie" tabTitle="Versie">
        Welke build van de app draait en of er een nieuwere klaarstaat. Bij elke wijziging op GitHub wordt een nieuwe build klaargezet.
      </PageHeader>
      <QueryState query={version}>{(data) => <VersionDetails version={data} />}</QueryState>
    </>
  )
}

function VersionDetails({ version }: { version: Version }) {
  const check = useCheckVersion()
  const update = useUpdateNow()
  const nightly = useSetNightly()
  const downloading = version.phase === "DOWNLOADING"
  const restarting = version.phase === "RESTARTING"
  const error = check.error ?? update.error ?? nightly.error

  return (
    <>
      {error && <Alert variant="error">{errorMessage(error)}</Alert>}
      {downloading && (
        <Alert>
          <b>De nieuwe versie wordt gedownload</b>
          {version.percent !== null && `: ${version.percent}% (${Math.floor(version.downloadedBytes / MB)} van ${Math.floor(version.totalBytes / MB)} MB)`}. Dit kan op een trage verbinding een paar minuten
          duren. Daarna start de app vanzelf opnieuw; je kunt deze pagina openlaten.
        </Alert>
      )}
      {version.failure && !downloading && !restarting && (
        <Alert variant="error">
          <b>Bijwerken is niet gelukt.</b> {version.failure}
        </Alert>
      )}
      {restarting && <RestartNotice />}

      <Card>
        <h2 className="text-xl">Deze installatie</h2>
        <p>
          <b>{version.current.label}</b>
        </p>
        <p className="text-muted">{version.current.details}</p>
        {!version.runsAsService && <p className="text-muted">De app draait niet als Windows-service; bijwerken vanuit dit scherm is dan uitgezet.</p>}
      </Card>

      <Card>
        <h2 className="text-xl">Op GitHub</h2>
        {!version.latest && !version.checkError && <p className="text-muted">Nog niet gecontroleerd.</p>}
        {version.checkError && <p className="text-danger">{version.checkError}</p>}
        {version.latest && (
          <div>
            <p>
              <b>Build {version.latest.number}</b>
              {version.latest.publishedAt && <span className="text-muted"> · gebouwd {dateTime(version.latest.publishedAt)}</span>}
            </p>
            {version.newer ? (
              <p className="mt-1.5">
                <b>Er staat een nieuwere build klaar.</b>
              </p>
            ) : (
              <p className="mt-1.5 text-muted">Je draait de nieuwste build.</p>
            )}
          </div>
        )}
        {version.checkedAt && <p className="text-muted">Laatst gecontroleerd: {dateTime(version.checkedAt)}</p>}
        {version.busy.length > 0 && <Alert>Er loopt nu een programma of upload ({version.busy.join(", ")}). Bijwerken kan pas als die klaar is.</Alert>}
        <div className="flex flex-wrap gap-2.5">
          <Button variant="secondary" disabled={check.isPending} onClick={() => check.mutate(undefined)}>
            Controleer nu
          </Button>
          <Button
            disabled={!version.runsAsService || !version.newer || version.busy.length > 0 || downloading || restarting || update.isPending}
            onClick={() => {
              if (window.confirm("De app nu bijwerken? De app start opnieuw en is een halve minuut niet bereikbaar. Wie bezig is met een bevestiging of formulier, moet daarna opnieuw inloggen.")) update.mutate(undefined)
            }}
          >
            Nu bijwerken
          </Button>
        </div>
      </Card>

      <Card>
        <h2 className="text-xl">Elke nacht bijwerken</h2>
        <label className="flex items-center gap-2.5">
          <input type="checkbox" className="size-4.5 accent-primary" checked={version.nightly} disabled={nightly.isPending} onChange={(event) => nightly.mutate(event.target.checked)} /> Elke nacht om 00:00 bijwerken
          naar de nieuwste build
        </label>
        <p className="text-[13px] text-muted">
          Is er dan nog iemand bezig met een programma of upload, dan probeert de app het elke 10 minuten opnieuw tot 03:00. Daarna wacht hij tot de volgende nacht.
          {version.nightlyPending && <b> Er loopt nu een nachtelijke poging.</b>}
        </p>
      </Card>

      {version.events.length > 0 && (
        <Card className="gap-1">
          <h2 className="mb-2 text-xl">Recent</h2>
          {version.events.map((event) => (
            <p key={event} className="text-muted">
              {event}
            </p>
          ))}
        </Card>
      )}
    </>
  )
}

/** Wacht tot de app weg is en weer terug, en laadt dan het scherm opnieuw. */
function RestartNotice() {
  useEffect(() => {
    let down = false
    const timer = setInterval(() => {
      fetch("/actuator/health", { cache: "no-store" })
        .then((response) => {
          if (response.ok && down) window.location.reload()
        })
        .catch(() => {
          down = true
        })
    }, 2000)
    return () => clearInterval(timer)
  }, [])
  return (
    <Alert>
      <b>De app wordt opnieuw gestart.</b> Dit duurt ongeveer een halve minuut. Deze pagina laadt daarna vanzelf opnieuw; je moet dan opnieuw als beheerder inloggen.
    </Alert>
  )
}
