import type { ReactNode } from "react"

/** De kop van een scherm, en meteen de titel van het tabblad. */
export function PageHeader({ title, tabTitle, children }: { title: ReactNode; tabTitle: string; children?: ReactNode }) {
  return (
    <header>
      <title>{`${tabTitle} — ZcbsServer`}</title>
      <h1 className="text-[32px] tracking-tight">{title}</h1>
      {children && <p className="mt-1.5 text-muted">{children}</p>}
    </header>
  )
}
