import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

type EmptyStateProps = {
  icon: LucideIcon
  title: string
  description: string
  action?: React.ReactNode
  className?: string
}

function EmptyState({ icon: Icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div className={cn('flex min-h-48 flex-col items-center justify-center rounded-lg border border-dashed border-border bg-surface px-6 py-10 text-center', className)}>
      <div className="mb-3 flex size-10 items-center justify-center rounded-lg border border-border bg-surface-raised text-foreground-muted">
        <Icon className="size-5" aria-hidden="true" />
      </div>
      <h3 className="text-sm font-medium text-foreground">{title}</h3>
      <p className="mt-1 max-w-sm text-sm leading-5 text-foreground-secondary">{description}</p>
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}

export { EmptyState }
