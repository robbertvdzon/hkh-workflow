import { errorMessage } from "@/api/client"
import { useSessions, useStopFlow } from "@/api/admin"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { PageHeader } from "@/components/ui/page-header"
import { BackLink, QueryState } from "@/components/ui/page-section"
import { Table, TableHead, TableRow } from "@/components/ui/table"
import { clockTime, dateTime } from "@/lib/time"

const COLUMNS = "140px minmax(120px,1fr) 100px 110px 110px 120px"
const ABORTED_COLUMNS = "150px 120px 110px 150px minmax(160px,1fr) 100px"

export function SessionsPage() {
  const sessions = useSessions()
  const stop = useStopFlow()
  return (
    <>
      <BackLink to="/instellingen">Instellingen</BackLink>
      <PageHeader title="Sessies" tabTitle="Sessies">
        Wie er nu is ingelogd en wie bezig is met Nieuwe bestanden. Elke gebruiker heeft een eigen doorloop; meer mensen kunnen tegelijk in dezelfde collectie werken.
      </PageHeader>
      {stop.isError && <Alert variant="error">{errorMessage(stop.error)}</Alert>}
      <QueryState query={sessions}>
        {(data) => (
          <>
            <Card>
              <h2 className="text-xl">Bezig met nieuwe bestanden</h2>
              {data.flows.length === 0 ? (
                <p className="text-muted">Er werkt nu niemand aan nieuwe bestanden.</p>
              ) : (
                <Table>
                  <TableHead columns={COLUMNS}>
                    <span>Collectie</span>
                    <span>Gebruiker</span>
                    <span>Fase</span>
                    <span>Sinds</span>
                    <span>Laatst actief</span>
                    <span />
                  </TableHead>
                  {data.flows.map((flow) => (
                    <TableRow key={`${flow.collection}/${flow.user}`} columns={COLUMNS}>
                      <span>{flow.collection}</span>
                      <span>{flow.user || "(beheerder)"}</span>
                      <span>{flow.phase} van 7</span>
                      <span className="text-muted">{clockTime(flow.since)}</span>
                      <span className="text-muted">{clockTime(flow.lastActive)}</span>
                      <Button
                        variant="secondary"
                        size="small"
                        onClick={() => {
                          if (window.confirm("Deze doorloop stoppen? Wat de gebruiker hier doet wordt gestopt, zijn bestanden in de werkmappen worden verwijderd en hij moet opnieuw beginnen. De scans op K: blijven staan.")) stop.mutate({ collection: flow.collection, user: flow.user })
                        }}
                      >
                        Stoppen
                      </Button>
                    </TableRow>
                  ))}
                </Table>
              )}
            </Card>
            <Card>
              <h2 className="text-xl">Afgebroken nadat images.exe was gestart</h2>
              {data.aborted.length === 0 ? (
                <p className="text-muted">Geen. Hier komt een doorloop te staan als iemand opnieuw begint (of de beheerder de doorloop stopt) nadat images.exe is gestart.</p>
              ) : (
                <>
                  <p className="text-muted">
                    Bij deze doorlopen is opnieuw begonnen nadat images.exe was gestart. De records kunnen al in ZCBS staan en de afbeeldingen op de server, terwijl de upload van tekst en pdf of de back-up niet is gedaan. Kijk in
                    ZCBS wat er van deze nummers is geworden.
                  </p>
                  <Table>
                    <TableHead columns={ABORTED_COLUMNS}>
                      <span>Wanneer</span>
                      <span>Gebruiker</span>
                      <span>Collectie</span>
                      <span>Records</span>
                      <span>Hoe ver het was</span>
                      <span>Gestopt door</span>
                    </TableHead>
                    {data.aborted.map((flow) => (
                      <TableRow key={`${flow.at}/${flow.user}`} columns={ABORTED_COLUMNS}>
                        <span className="text-muted">{dateTime(flow.at)}</span>
                        <span>{flow.user || "(beheerder)"}</span>
                        <span>{flow.collection}</span>
                        <span className="font-mono">{flow.numbers}</span>
                        <span className="text-[13px] wrap-anywhere text-muted">{flow.steps}</span>
                        <span>{flow.by}</span>
                      </TableRow>
                    ))}
                  </Table>
                </>
              )}
            </Card>
            <Card>
              <h2 className="text-xl">Ingelogd bij ZCBS</h2>
              {data.users.length === 0 ? <p className="text-muted">Niemand.</p> : <p>{data.users.join(", ")}</p>}
            </Card>
          </>
        )}
      </QueryState>
    </>
  )
}
