import { cva, type VariantProps } from "class-variance-authority"
import type { ComponentProps } from "react"
import { cn } from "@/lib/utils"

const alertVariants = cva("rounded-[10px] px-4.5 py-3.5 text-sm", {
  variants: {
    variant: {
      info: "bg-primary-soft text-primary-ink",
      error: "bg-danger-soft text-danger",
      ok: "bg-ok-soft text-ok",
      warn: "bg-busy-soft text-busy",
    },
  },
  defaultVariants: { variant: "info" },
})

export function Alert({ className, variant, ...props }: ComponentProps<"div"> & VariantProps<typeof alertVariants>) {
  return <div role={variant === "error" ? "alert" : "status"} className={cn(alertVariants({ variant }), className)} {...props} />
}
