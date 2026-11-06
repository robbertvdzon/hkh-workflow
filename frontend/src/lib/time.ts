const ZONE = "Europe/Amsterdam"

/** Een moment uit de API als `19:40`. */
export function clockTime(moment: string) {
  return new Date(moment).toLocaleTimeString("nl-NL", { timeZone: ZONE, hour: "2-digit", minute: "2-digit" })
}

/** Een moment uit de API als `6 nov 2026 19:40`. */
export function dateTime(moment: string) {
  return new Date(moment).toLocaleString("nl-NL", { timeZone: ZONE, day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })
}
