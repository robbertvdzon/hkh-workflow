import { cva, type VariantProps } from "class-variance-authority"
import { Slot } from "radix-ui"
import type { ComponentProps } from "react"
import { cn } from "@/lib/utils"

const buttonVariants = cva(
  "inline-flex cursor-pointer items-center justify-center gap-2 rounded-lg border border-transparent font-semibold no-underline disabled:cursor-not-allowed disabled:opacity-45",
  {
    variants: {
      variant: {
        primary: "bg-primary text-white hover:bg-primary-hover hover:text-white",
        secondary: "border-neutral-line bg-card text-foreground hover:bg-neutral",
        danger: "border-neutral-line bg-card text-danger hover:bg-danger-soft",
      },
      size: {
        default: "h-11 px-4.5 text-sm",
        small: "h-9 px-3 text-[13px]",
      },
    },
    defaultVariants: { variant: "primary", size: "default" },
  },
)

type ButtonProps = ComponentProps<"button"> & VariantProps<typeof buttonVariants> & { asChild?: boolean }

export function Button({ className, variant, size, asChild = false, ...props }: ButtonProps) {
  const Component = asChild ? Slot.Root : "button"
  return <Component className={cn(buttonVariants({ variant, size }), className)} {...props} />
}
