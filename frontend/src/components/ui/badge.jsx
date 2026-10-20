import * as React from "react"
import { cva } from "class-variance-authority";

import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "stak-badge",
  {
    variants: {
      variant: {
        default:
          "stak-badge--primary",
        secondary:
          "stak-badge--secondary",
        destructive:
          "stak-badge--destructive",
        outline: "stak-badge--outlined",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

function Badge({
  className,
  variant,
  ...props
}) {
  return (<div className={cn(badgeVariants({ variant }), className)} {...props} />);
}

export { Badge, badgeVariants }
