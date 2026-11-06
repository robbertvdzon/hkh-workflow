import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api, unwrap } from "./client"
import type { components } from "./schema"

export type Session = components["schemas"]["SessionResponse"]

export const sessionKey = ["session"]

export function useSession() {
  return useQuery({ queryKey: sessionKey, queryFn: () => unwrap(api.GET("/api/session")) })
}

/** De sessie in een scherm binnen de layout: die toont het scherm pas als de sessie er is. */
export function useLoadedSession(): Session {
  const { data } = useSession()
  if (!data) throw new Error("De sessie is nog niet geladen.")
  return data
}

export function useLogin() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: { name: string; password: string }) => unwrap(api.POST("/api/session", { body })),
    onSuccess: (session) => client.setQueryData(sessionKey, session),
  })
}

export function useLogout() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.DELETE("/api/session")),
    onSuccess: async () => {
      // Wat van deze gebruiker was, hoort niet bij de volgende.
      client.removeQueries({ predicate: (query) => query.queryKey[0] !== sessionKey[0] })
      await client.invalidateQueries({ queryKey: sessionKey })
    },
  })
}

export function useAdminLogin() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (body: { password: string }) => unwrap(api.POST("/api/admin-session", { body })),
    onSuccess: () => client.invalidateQueries({ queryKey: sessionKey }),
  })
}

export function useAdminLogout() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: () => unwrap(api.DELETE("/api/admin-session")),
    onSuccess: () => client.invalidateQueries({ queryKey: sessionKey }),
  })
}
