import type { ReactNode } from "react"
import { Link } from "react-router"

const base = "flex flex-col gap-1.5 rounded-xl border border-line bg-card px-5 py-4.5 no-underline"

type ChoiceProps = { title: string; children: ReactNode }

/** Een grote keuze die je kunt aanklikken. */
export function Choice({ to, title, children }: ChoiceProps & { to: string }) {
  return (
    <Link to={to} className={`${base} text-foreground hover:border-primary hover:bg-primary-soft`}>
      <b className="text-base font-semibold">{title}</b>
      <span className="text-[13px] text-muted">{children}</span>
    </Link>
  )
}

/** Dezelfde keuze, maar die kan nu niet. */
export function ChoiceOff({ title, children }: ChoiceProps) {
  return (
    <div aria-disabled="true" className={`${base} opacity-60`}>
      <b className="text-base font-semibold">{title}</b>
      <span className="text-[13px] text-muted">{children}</span>
    </div>
  )
}

export function Choices({ children }: { children: ReactNode }) {
  return <div className="grid max-w-3xl grid-cols-1 gap-3 sm:grid-cols-2">{children}</div>
}
