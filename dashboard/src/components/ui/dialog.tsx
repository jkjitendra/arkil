import * as React from 'react'
import { cn } from '@/lib/utils'
import { X } from 'lucide-react'

interface DialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  children: React.ReactNode
}

export function Dialog({ open, onOpenChange, children }: DialogProps) {
  // Close on escape key
  React.useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onOpenChange(false)
    }
    if (open) {
      document.addEventListener('keydown', handleEscape)
      // Store previous overflow value and restore on cleanup
      const prevOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
      return () => {
        document.removeEventListener('keydown', handleEscape)
        document.body.style.overflow = prevOverflow
      }
    }
    return () => {
      document.removeEventListener('keydown', handleEscape)
    }
  }, [open, onOpenChange])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/60 backdrop-blur-sm animate-fade-in"
        onClick={() => onOpenChange(false)}
      />
      {/* Content */}
      <div className="relative z-10 w-full max-w-lg mx-4 animate-fade-in">
        {children}
      </div>
    </div>
  )
}

interface DialogContentProps extends React.HTMLAttributes<HTMLDivElement> { }

export function DialogContent({ className, children, ...props }: DialogContentProps) {
  return (
    <div
      className={cn(
        'bg-card rounded-lg shadow-2xl border border-border p-6',
        className
      )}
      onClick={(e) => e.stopPropagation()}
      {...props}
    >
      {children}
    </div>
  )
}

interface DialogHeaderProps extends React.HTMLAttributes<HTMLDivElement> { }

export function DialogHeader({ className, ...props }: DialogHeaderProps) {
  return (
    <div
      className={cn('flex flex-col space-y-2 mb-4', className)}
      {...props}
    />
  )
}

interface DialogTitleProps extends React.HTMLAttributes<HTMLHeadingElement> { }

export function DialogTitle({ className, ...props }: DialogTitleProps) {
  return (
    <h2
      className={cn('text-lg font-semibold leading-none tracking-tight', className)}
      {...props}
    />
  )
}

interface DialogDescriptionProps extends React.HTMLAttributes<HTMLParagraphElement> { }

export function DialogDescription({ className, ...props }: DialogDescriptionProps) {
  return (
    <p
      className={cn('text-sm text-muted-foreground', className)}
      {...props}
    />
  )
}

interface DialogFooterProps extends React.HTMLAttributes<HTMLDivElement> { }

export function DialogFooter({ className, ...props }: DialogFooterProps) {
  return (
    <div
      className={cn('flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2 mt-6', className)}
      {...props}
    />
  )
}

interface DialogCloseProps {
  onClose: () => void
}

export function DialogClose({ onClose }: DialogCloseProps) {
  return (
    <button
      onClick={onClose}
      className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2"
    >
      <X className="h-4 w-4" />
      <span className="sr-only">Close</span>
    </button>
  )
}
