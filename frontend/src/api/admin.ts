import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api, unwrap } from "./client"
import type { components } from "./schema"

export type Settings = components["schemas"]["SettingsResponse"]
export type SettingsRequest = components["schemas"]["SettingsRequest"]
export type Version = components["schemas"]["VersionResponse"]

const settingsKey = ["settings"]
const sessionsKey = ["sessions"]
const versionKey = ["version"]
const logsKey = ["program-logs"]

export function useSettings() {
  return useQuery({ queryKey: settingsKey, queryFn: () => unwrap(api.GET("/api/settings")), refetchOnWindowFocus: false })
}

export function useSaveSettings() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: SettingsRequest) => unwrap(api.PUT("/api/settings", { body })),
    onSuccess: (settings) => client.setQueryData(settingsKey, settings),
  })
}

export function useFtpTest() {
  return useMutation({ mutationFn: () => unwrap(api.POST("/api/settings/ftp-test")) })
}

export function useSessions() {
  return useQuery({ queryKey: sessionsKey, queryFn: () => unwrap(api.GET("/api/sessions")) })
}

export function useStopFlow() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (path: { collection: string; user: string }) => unwrap(api.DELETE("/api/sessions/flows/{collection}/{user}", { params: { path } })),
    onSettled: () => client.invalidateQueries({ queryKey: sessionsKey }),
  })
}

/** Tijdens het downloaden en herstarten volgt het scherm de voortgang. */
export function useVersion() {
  return useQuery({
    queryKey: versionKey,
    queryFn: () => unwrap(api.GET("/api/version")),
    refetchInterval: (query) => (query.state.data?.phase === "DOWNLOADING" ? 2500 : false),
  })
}

function useVersionAction<T>(action: (input: T) => Promise<Version>) {
  const client = useQueryClient()
  return useMutation({ mutationFn: action, onSuccess: (version) => client.setQueryData(versionKey, version) })
}

export function useCheckVersion() {
  return useVersionAction(() => unwrap(api.POST("/api/version/check")))
}

export function useUpdateNow() {
  return useVersionAction(() => unwrap(api.POST("/api/version/update")))
}

export function useSetNightly() {
  return useVersionAction((enabled: boolean) => unwrap(api.PUT("/api/version/nightly", { body: { enabled } })))
}

export function useProgramLogs() {
  return useQuery({ queryKey: logsKey, queryFn: () => unwrap(api.GET("/api/program-logs")) })
}

export function useProgramLog(file: string | null) {
  return useQuery({
    queryKey: [...logsKey, file],
    enabled: file !== null,
    queryFn: () => unwrap(api.GET("/api/program-logs/{file}", { params: { path: { file: file ?? "" } }, parseAs: "text" })) as Promise<string>,
  })
}

export function useDeleteLog() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (file: string) => unwrap(api.DELETE("/api/program-logs/{file}", { params: { path: { file } } })),
    onSettled: () => client.invalidateQueries({ queryKey: logsKey }),
  })
}

export function useDeleteAllLogs() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.DELETE("/api/program-logs")),
    onSettled: () => client.invalidateQueries({ queryKey: logsKey }),
  })
}
