import { useState } from "react"
import { errorMessage } from "@/api/client"
import { useDeleteAllLogs, useDeleteLog, useProgramLog, useProgramLogs } from "@/api/admin"
import type { components } from "@/api/schema"
import { Alert } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Chip } from "@/components/ui/chip"
import { PageHeader } from "@/components/ui/page-header"
import { BackLink, QueryState } from "@/components/ui/page-section"
import { Table, TableHead, TableRow } from "@/components/ui/table"
import { dateTime } from "@/lib/time"

type ProgramLog = components["schemas"]["ProgramLogResponse"]

const COLUMNS = "minmax(150px,auto) minmax(0,1.5fr) minmax(0,1fr) 70px minmax(130px,auto)"

export function ProgramLogsPage() {
  const logs = useProgramLogs()
  const remove = useDeleteLog()
  const removeAll = useDeleteAllLogs()
  const [shown, setShown] = useState<string | null>(null)
  const error = remove.error ?? removeAll.error
  return (
    <>
      <BackLink to="/instellingen">Instellingen</BackLink>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <PageHeader title="Logs" tabTitle="Logs">
          Elke start van een programma heeft een eigen logbestand met tijd sinds start per regel. Nieuwste boven; blijft staan na een herstart van de app.
        </PageHeader>
        {!!logs.data?.length && (
          <Button
            variant="danger"
            onClick={() => {
              if (window.confirm("Alle logbestanden verwijderen? Het log van een lopend programma blijft staan.")) removeAll.mutate(undefined, { onSuccess: () => setShown(null) })
            }}
          >
            Alle logs wissen
          </Button>
        )}
      </div>
      {error && <Alert variant="error">{errorMessage(error)}</Alert>}
      <QueryState query={logs}>
        {(data) => (
          <Table>
            <TableHead columns={COLUMNS}>
              <span>Gestart</span>
              <span>Programma</span>
              <span>Resultaat</span>
              <span>Duur</span>
              <span>Log</span>
            </TableHead>
            {data.length === 0 && (
              <TableRow columns="1fr">
                <span className="text-[13px] text-muted">Nog geen runs.</span>
              </TableRow>
            )}
            {data.map((log) => (
              <TableRow key={log.file} columns={COLUMNS}>
                <span className="font-mono">{log.startedAt ? dateTime(log.startedAt) : "–"}</span>
                <span>{log.label}</span>
                <span>
                  <Result log={log} />
                </span>
                <span className="font-mono">{log.duration ?? "–"}</span>
                <span className="flex flex-wrap gap-2">
                  <Button variant="secondary" size="small" onClick={() => setShown(log.file)}>
                    Toon
                  </Button>
                  {!log.running && (
                    <Button
                      variant="danger"
                      size="small"
                      title="Verwijder dit logbestand"
                      onClick={() => {
                        if (window.confirm("Dit logbestand verwijderen?")) remove.mutate(log.file, { onSuccess: () => setShown((current) => (current === log.file ? null : current)) })
                      }}
                    >
                      Wis
                    </Button>
                  )}
                </span>
              </TableRow>
            ))}
          </Table>
        )}
      </QueryState>
      {shown && <ShownLog file={shown} />}
    </>
  )
}

function Result({ log }: { log: ProgramLog }) {
  if (log.running) return <Chip variant="busy">Draait</Chip>
  if (log.stopped) return <Chip variant="error">Gestopt door gebruiker</Chip>
  if (log.exitCode === 0) return <Chip variant="ok">Klaar (exitcode 0)</Chip>
  if (log.exitCode !== null) return <Chip variant="error">Mislukt (exitcode {log.exitCode})</Chip>
  return (
    <Chip title="Geen EXIT-regel in het log: de app is gestopt terwijl het programma draaide">Onbekend</Chip>
  )
}

function ShownLog({ file }: { file: string }) {
  const log = useProgramLog(file)
  return (
    <div className="flex flex-col gap-1.5">
      <div className="text-[13px] text-muted">
        Log: <span className="font-mono">{file}</span>
      </div>
      <QueryState query={log}>{(text) => <pre className="h-105 overflow-auto rounded-[10px] bg-console px-4.5 py-4 font-mono text-[13px] leading-snug whitespace-pre-wrap text-console-ink">{text}</pre>}</QueryState>
    </div>
  )
}
