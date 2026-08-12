import * as React from 'react'
import { cn } from '@/lib/utils'

type AlertProps = React.HTMLAttributes<HTMLDivElement> & { variant?: 'default' | 'destructive' }

const Alert = React.forwardRef<HTMLDivElement, AlertProps>(({ className, variant = 'default', ...props }, ref) => (
  <div ref={ref} role="alert" className={cn('relative w-full rounded-lg border p-3 text-sm', variant === 'default' ? 'border-border bg-surface-raised text-foreground' : 'border-danger/30 bg-danger/10 text-danger', className)} {...props} />
))
Alert.displayName = 'Alert'
const AlertTitle = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLHeadingElement>>(({ className, ...props }, ref) => <h5 ref={ref} className={cn('mb-1 font-medium leading-none', className)} {...props} />)
AlertTitle.displayName = 'AlertTitle'
const AlertDescription = React.forwardRef<HTMLParagraphElement, React.HTMLAttributes<HTMLParagraphElement>>(({ className, ...props }, ref) => <div ref={ref} className={cn('text-sm leading-5 opacity-90', className)} {...props} />)
AlertDescription.displayName = 'AlertDescription'

export { Alert, AlertTitle, AlertDescription }
