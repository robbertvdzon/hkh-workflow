import type { ComponentProps } from "react"
import { cn } from "@/lib/utils"

export function Card({ className, ...props }: ComponentProps<"section">) {
  return <section className={cn("flex flex-col gap-4 rounded-xl border border-line bg-card px-6 py-5", className)} {...props} />
}
