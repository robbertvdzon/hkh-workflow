import { useInfiniteQuery, useMutation, useQuery } from "@tanstack/react-query"
import { api, unwrap } from "./client"
import type { components } from "./schema"

export type Field = components["schemas"]["FieldResponse"]
export type RecentRecord = components["schemas"]["RecentRecordResponse"]
export type RecordScope = "mine" | "all" | "to-describe"
export type Values = Record<string, string[]>
export type ConflictRow = { field: string; label: string; mine: string; theirs: string }

const PAGE_SIZE = 20

export function recordsPath(collection: string) {
  return `/collectie/${encodeURIComponent(collection)}/records`
}

/** De pagina die de browser ingelogd naar ZCBS stuurt; hoort in een nieuw tabblad. */
export function openInZcbsUrl(collection: string, number: string) {
  return `/api/collections/${encodeURIComponent(collection)}/records/${encodeURIComponent(number)}/open-in-zcbs`
}

export function useRecentRecords(collection: string, scope: RecordScope) {
  return useInfiniteQuery({
    queryKey: ["records", collection, scope],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      unwrap(api.GET("/api/collections/{collection}/records", { params: { path: { collection }, query: { scope, offset: pageParam, limit: PAGE_SIZE } } })),
    getNextPageParam: (last, _pages, offset) => (last.more ? offset + PAGE_SIZE : undefined),
    // Na het opslaan of toevoegen moet een record meteen goed in de lijst staan.
    staleTime: 0,
  })
}

export function useNextToDescribe(collection: string, after: string | null) {
  return useQuery({
    queryKey: ["next-to-describe", collection, after],
    enabled: after !== null,
    queryFn: () => unwrap(api.GET("/api/collections/{collection}/records/next-to-describe", { params: { path: { collection }, query: { after: after ?? "" } } })),
  })
}

export function fetchMedia(collection: string, number: string) {
  return unwrap(api.GET("/api/collections/{collection}/records/{number}/media", { params: { path: { collection, number } } }))
}

/** Openen vergrendelt het record bij ZCBS. Daarom is dit eenmalig per bezoek aan het scherm: niet opnieuw als je terugkomt in het tabblad. */
export function useOpenRecord(collection: string, number: string) {
  return useQuery({
    queryKey: ["edit", collection, number],
    queryFn: () => unwrap(api.POST("/api/collections/{collection}/records/{number}/edit", { params: { path: { collection, number } } })),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
}

export function useCancelEdit(collection: string, number: string) {
  return useMutation({ mutationFn: () => unwrap(api.DELETE("/api/collections/{collection}/records/{number}/edit", { params: { path: { collection, number } } })) })
}

export function useSaveRecord(collection: string, number: string) {
  return useMutation({
    mutationFn: (body: { values: Values; onConflict?: "keep-mine" | "take-theirs" }) =>
      unwrap(api.PUT("/api/collections/{collection}/records/{number}", { params: { path: { collection, number } }, body })),
  })
}
