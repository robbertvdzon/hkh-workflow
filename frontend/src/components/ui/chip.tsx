import { cva, type VariantProps } from "class-variance-authority"
import type { ComponentProps } from "react"
import { cn } from "@/lib/utils"

const chipVariants = cva("inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.75 text-xs font-semibold whitespace-nowrap", {
  variants: {
    variant: {
      ok: "bg-ok-soft text-ok",
      busy: "bg-busy-soft text-busy",
      error: "bg-danger-soft text-danger",
      neutral: "bg-neutral text-foreground-soft",
    },
  },
  defaultVariants: { variant: "neutral" },
})

export function Chip({ className, variant, ...props }: ComponentProps<"span"> & VariantProps<typeof chipVariants>) {
  return <span className={cn(chipVariants({ variant }), className)} {...props} />
}
