import type { ComponentProps, CSSProperties } from "react"
import { cn } from "@/lib/utils"

/** Een tabel met vaste kolommen. Geef `columns` als CSS grid-template-columns aan de kop en de rijen. */
export function Table({ className, ...props }: ComponentProps<"div">) {
  return <div role="table" className={cn("overflow-auto rounded-xl border border-line bg-card", className)} {...props} />
}

type RowProps = ComponentProps<"div"> & { columns: string }

function style(columns: string): CSSProperties {
  return { gridTemplateColumns: columns }
}

export function TableHead({ className, columns, ...props }: RowProps) {
  return (
    <div
      role="row"
      style={style(columns)}
      className={cn("grid min-h-10 items-center gap-2.5 border-b border-line px-5 text-xs font-semibold tracking-wide text-muted uppercase", className)}
      {...props}
    />
  )
}

export function TableRow({ className, columns, ...props }: RowProps) {
  return <div role="row" style={style(columns)} className={cn("grid min-h-13 items-center gap-2.5 border-b border-line last:border-b-0 px-5 py-1.5", className)} {...props} />
}
