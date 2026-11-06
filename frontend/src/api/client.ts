import createClient from "openapi-fetch"
import type { paths } from "./schema"

/** Een fout van de API, altijd in dezelfde vorm: zie docs/api.md. [detail] is bedoeld om aan de gebruiker te tonen. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  /** De hele fout, voor wat er naast de melding in staat (bijvoorbeeld de `conflicts` bij `edit-conflict`). */
  readonly body: Record<string, unknown>

  constructor(status: number, code: string, detail: string, body: Record<string, unknown> = {}) {
    super(detail)
    this.status = status
    this.code = code
    this.body = body
  }
}

export const api = createClient<paths>()

type Result<T> = { data?: T; error?: unknown; response: Response }

/** Het antwoord van een aanroep, of een [ApiError] als het niet gelukt is. */
export async function unwrap<T>(call: Promise<Result<T>>): Promise<T> {
  const { data, error, response } = await call
  if (response.ok) return data as T
  const problem = (error ?? {}) as { code?: string; detail?: string }
  throw new ApiError(response.status, problem.code ?? "unknown", problem.detail ?? `De server gaf een fout (${response.status}).`, problem)
}

/** De tekst om aan de gebruiker te tonen. Alleen een [ApiError] heeft een melding die voor hem bedoeld is. */
export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : "De server is niet bereikbaar. Probeer het zo nog eens."
}
