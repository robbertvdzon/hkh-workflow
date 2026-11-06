import type { ComponentProps } from "react"
import { cn } from "@/lib/utils"

export function Input({ className, ...props }: ComponentProps<"input">) {
  return <input className={cn("h-11 w-full rounded-lg border border-field bg-card px-3 text-sm text-foreground", className)} {...props} />
}

export function Textarea({ className, ...props }: ComponentProps<"textarea">) {
  return <textarea className={cn("w-full rounded-lg border border-field bg-card px-3 py-2.5 text-sm text-foreground", className)} {...props} />
}

export function Select({ className, ...props }: ComponentProps<"select">) {
  return <select className={cn("w-full rounded-lg border border-field bg-card px-3 text-sm text-foreground", props.multiple ? "py-1.5" : "h-11", className)} {...props} />
}
