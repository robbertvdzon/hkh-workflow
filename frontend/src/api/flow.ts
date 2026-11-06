import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { api, unwrap } from "./client"
import type { components } from "./schema"

export type Flow = components["schemas"]["FlowResponse"]
export type Step = components["schemas"]["StepResponse"]
export type Console = components["schemas"]["ConsoleResponse"]
export type StepName = "pdf2zcbs" | "images" | "upload" | "backup"

export function flowKey(collection: string) {
  return ["flow", collection]
}

function flowUrl(collection: string) {
  return `/api/collections/${encodeURIComponent(collection)}/flow`
}

export function collectionParams(collection: string) {
  return { params: { path: { collection } } }
}

/** Zolang er iets loopt, volgt het scherm de stand vanzelf. */
export function useFlow(collection: string) {
  return useQuery({
    queryKey: flowKey(collection),
    queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow", collectionParams(collection))),
    refetchInterval: (query) => (query.state.data?.running ? 1000 : false),
  })
}

export function useSteps(collection: string, running: boolean) {
  return useQuery({
    queryKey: [...flowKey(collection), "steps"],
    queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/steps", collectionParams(collection))),
    refetchInterval: running ? 1000 : false,
  })
}

export function useChecks(collection: string) {
  return useQuery({ queryKey: [...flowKey(collection), "checks"], queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/checks", collectionParams(collection))) })
}

export function useSources(collection: string) {
  return useQuery({ queryKey: [...flowKey(collection), "sources"], queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/sources", collectionParams(collection))) })
}

export function useEntries(collection: string, source: string | null, path: string) {
  return useQuery({
    queryKey: [...flowKey(collection), "entries", source, path],
    enabled: source !== null,
    queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/sources/{source}/entries", { params: { path: { collection, source: source ?? "" }, query: { path } } })),
  })
}

/** Het aantal pagina's of de afmeting per bestand; komt los binnen omdat het even duurt. */
export function useFileDetails(collection: string, source: string | null, path: string) {
  return useQuery({
    queryKey: [...flowKey(collection), "details", source, path],
    enabled: source !== null,
    queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/sources/{source}/details", { params: { path: { collection, source: source ?? "" }, query: { path } } })),
  })
}

export function sourceFileUrl(collection: string, source: string, path: string, name: string) {
  return `${flowUrl(collection)}/sources/${encodeURIComponent(source)}/file?path=${encodeURIComponent(path)}&name=${encodeURIComponent(name)}`
}

export function useChosenFiles(collection: string) {
  return useQuery({ queryKey: [...flowKey(collection), "files"], queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/files", collectionParams(collection))) })
}

export function useNumbers(collection: string) {
  return useQuery({ queryKey: [...flowKey(collection), "numbers"], queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/numbers", collectionParams(collection))) })
}

export function useResults(collection: string, step: StepName | null) {
  return useQuery({
    queryKey: [...flowKey(collection), "results", step],
    enabled: step !== null,
    queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/results", { params: { path: { collection }, query: { step: step ?? "" } } })),
  })
}

export function resultUrl(collection: string, number: string, kind: string) {
  return `${flowUrl(collection)}/results/${encodeURIComponent(number)}/${encodeURIComponent(kind)}`
}

export function useSummary(collection: string, enabled: boolean) {
  return useQuery({ queryKey: [...flowKey(collection), "summary"], enabled, queryFn: () => unwrap(api.GET("/api/collections/{collection}/flow/summary", collectionParams(collection))) })
}

export function consoleLogUrl(collection: string) {
  return `${flowUrl(collection)}/console/log`
}

/** Elke actie op de flow: daarna is alles wat we van de flow weten mogelijk veranderd. */
export function useFlowAction(collection: string) {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (action: () => Promise<unknown>) => action(),
    onSettled: () => client.invalidateQueries({ queryKey: flowKey(collection) }),
  })
}

export function flowActions(collection: string) {
  const params = collectionParams(collection)
  const step = (name: StepName) => ({ params: { path: { collection, step: name } } })
  return {
    reset: () => unwrap(api.DELETE("/api/collections/{collection}/flow", params)),
    start: () => unwrap(api.POST("/api/collections/{collection}/flow/start", params)),
    moveLeftovers: () => unwrap(api.POST("/api/collections/{collection}/flow/leftovers/move", params)),
    acceptLeftovers: () => unwrap(api.POST("/api/collections/{collection}/flow/leftovers/accept", params)),
    deleteLeftovers: () => unwrap(api.DELETE("/api/collections/{collection}/flow/leftovers", params)),
    chooseFiles: (source: string, path: string, names: string[]) => unwrap(api.PUT("/api/collections/{collection}/flow/files", { ...params, body: { source, path, names } })),
    clearFiles: () => unwrap(api.DELETE("/api/collections/{collection}/flow/files", params)),
    assignNumbers: (start: string) => unwrap(api.PUT("/api/collections/{collection}/flow/numbers", { ...params, body: { start } })),
    run: (name: StepName, answers: "auto" | "manual") => unwrap(api.POST("/api/collections/{collection}/flow/steps/{step}/run", { ...step(name), body: { answers } })),
    retry: (name: StepName) => unwrap(api.POST("/api/collections/{collection}/flow/steps/{step}/retry", step(name))),
    approve: (name: StepName) => unwrap(api.POST("/api/collections/{collection}/flow/steps/{step}/approve", step(name))),
    answer: (line: string) => unwrap(api.POST("/api/collections/{collection}/flow/console/input", { ...params, body: { line } })),
    stop: () => unwrap(api.POST("/api/collections/{collection}/flow/console/stop", params)),
  }
}

const RECONNECT_MILLIS = 2000

/** De console live, zolang het programma draait. De server sluit de stream als het klaar is; valt de verbinding weg, dan pakken we hem weer op. */
export function useLiveConsole(collection: string): Console | null {
  const [console, setConsole] = useState<Console | null>(null)
  useEffect(() => {
    let source: EventSource | null = null
    let retry: ReturnType<typeof setTimeout> | undefined
    function connect() {
      source = new EventSource(`${flowUrl(collection)}/console/events`)
      source.addEventListener("console", (event) => {
        const next = JSON.parse((event as MessageEvent<string>).data) as Console
        setConsole(next)
        if (!next.running) source?.close()
      })
      source.onerror = () => {
        source?.close()
        retry = setTimeout(connect, RECONNECT_MILLIS)
      }
    }
    connect()
    return () => {
      clearTimeout(retry)
      source?.close()
    }
  }, [collection])
  return console
}
