import * as React from 'react'
import { cn } from '@/lib/utils'

export type BadgeProps = React.HTMLAttributes<HTMLDivElement> & {
  variant?: 'default' | 'secondary' | 'outline' | 'destructive'
}

function Badge({ className, variant = 'default', ...props }: BadgeProps) {
  return <div className={cn('inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-medium leading-4', {
    'border-primary/20 bg-primary-subtle text-primary': variant === 'default',
    'border-transparent bg-surface-raised text-foreground-secondary': variant === 'secondary',
    'border-border bg-transparent text-foreground-secondary': variant === 'outline',
    'border-danger/20 bg-danger/10 text-danger': variant === 'destructive',
  }, className)} {...props} />
}

export { Badge }
