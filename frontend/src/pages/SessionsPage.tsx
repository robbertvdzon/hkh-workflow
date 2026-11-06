import { errorMessage } from "@/api/client"
import { useSessions, useStopFlow } from "@/api/admin"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { PageHeader } from "@/components/ui/page-header"
import { BackLink, QueryState } from "@/components/ui/page-section"
import { Table, TableHead, TableRow } from "@/components/ui/table"
import { clockTime } from "@/lib/time"

const COLUMNS = "140px minmax(120px,1fr) 100px 110px 110px 120px"

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
                          if (window.confirm("Deze doorloop stoppen? Wat de gebruiker hier doet wordt gestopt en hij moet opnieuw beginnen.")) stop.mutate({ collection: flow.collection, user: flow.user })
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
              <h2 className="text-xl">Ingelogd bij ZCBS</h2>
              {data.users.length === 0 ? <p className="text-muted">Niemand.</p> : <p>{data.users.join(", ")}</p>}
            </Card>
          </>
        )}
      </QueryState>
    </>
  )
}
