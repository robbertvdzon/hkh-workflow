import type { ComponentProps } from "react"
import { cn } from "@/lib/utils"

export function Input({ className, ...props }: ComponentProps<"input">) {
  return <input className={cn("h-11 w-full rounded-lg border border-field bg-card px-3 text-sm text-foreground", className)} {...props} />
}
