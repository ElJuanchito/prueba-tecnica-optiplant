import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils.ts'

const badgeVariants = cva(
  'inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-orange-500 focus:ring-offset-2',
  {
    variants: {
      variant: {
        default:
          'border-transparent bg-orange-600 text-white shadow-2xs hover:bg-orange-700',
        secondary:
          'border-slate-200 bg-slate-100 text-slate-800 hover:bg-slate-200',
        destructive:
          'border-transparent bg-red-600 text-white shadow-2xs hover:bg-red-700',
        outline: 'text-slate-800 border-slate-300',
        success:
          'border-transparent bg-emerald-600 text-white shadow-2xs hover:bg-emerald-700',
        warning:
          'border-transparent bg-amber-500 text-white shadow-2xs hover:bg-amber-600',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
)

export interface BadgeProps
  extends
    React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

export { Badge, badgeVariants }
