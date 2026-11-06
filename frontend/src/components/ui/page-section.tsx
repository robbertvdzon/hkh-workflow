import type { ReactNode } from "react"
import { Link } from "react-router"
import { errorMessage } from "@/api/client"
import { Alert } from "./alert"

export function BackLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="text-sm font-medium no-underline">
      ← {children}
    </Link>
  )
}

/** Laden en fouten van een query, zodat de rest van het scherm alleen de gegevens kent. */
export function QueryState<T>({ query, loading, children }: { query: { data: T | undefined; error: unknown }; loading?: string; children: (data: T) => ReactNode }) {
  if (query.data !== undefined) return children(query.data)
  if (query.error) return <Alert variant="error">{errorMessage(query.error)}</Alert>
  return <p className="text-[13px] text-muted">{loading ?? "Even laden…"}</p>
}
