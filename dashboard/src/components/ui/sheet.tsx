import * as React from 'react'
import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'

const Sheet = DialogPrimitive.Root
const SheetTrigger = DialogPrimitive.Trigger

const SheetContent = React.forwardRef<React.ElementRef<typeof DialogPrimitive.Content>, React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> & { side?: 'top' | 'right' | 'bottom' | 'left' }>(
  ({ className, children, side = 'right', ...props }, ref) => (
    <DialogPrimitive.Portal>
      <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/50 data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:animate-in data-[state=open]:fade-in-0" />
      <DialogPrimitive.Content
        ref={ref}
        className={cn(
          'fixed z-50 flex flex-col gap-4 border-border bg-surface p-6 text-foreground shadow-lg outline-none transition duration-200 ease-out',
          side === 'right' && 'inset-y-0 right-0 h-full w-[min(100%,24rem)] border-l data-[state=closed]:translate-x-full data-[state=open]:translate-x-0',
          side === 'left' && 'inset-y-0 left-0 h-full w-[min(100%,24rem)] border-r data-[state=closed]:-translate-x-full data-[state=open]:translate-x-0',
          side === 'top' && 'inset-x-0 top-0 w-full border-b data-[state=closed]:-translate-y-full data-[state=open]:translate-y-0',
          side === 'bottom' && 'inset-x-0 bottom-0 w-full border-t data-[state=closed]:translate-y-full data-[state=open]:translate-y-0',
          className,
        )}
        {...props}
      >
        {children}
        <DialogPrimitive.Close className="absolute right-4 top-4 rounded-lg p-1 text-foreground-muted transition-colors duration-150 hover:bg-surface-raised hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background">
          <X className="size-4" />
          <span className="sr-only">Close</span>
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  ),
)
SheetContent.displayName = DialogPrimitive.Content.displayName

const SheetHeader = ({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) => <div className={cn('flex flex-col gap-1.5 pr-7 text-left', className)} {...props} />
const SheetTitle = React.forwardRef<React.ElementRef<typeof DialogPrimitive.Title>, React.ComponentPropsWithoutRef<typeof DialogPrimitive.Title>>(
  ({ className, ...props }, ref) => <DialogPrimitive.Title ref={ref} className={cn('text-base font-semibold', className)} {...props} />,
)
SheetTitle.displayName = DialogPrimitive.Title.displayName
const SheetDescription = React.forwardRef<React.ElementRef<typeof DialogPrimitive.Description>, React.ComponentPropsWithoutRef<typeof DialogPrimitive.Description>>(
  ({ className, ...props }, ref) => <DialogPrimitive.Description ref={ref} className={cn('text-sm text-foreground-secondary', className)} {...props} />,
)
SheetDescription.displayName = DialogPrimitive.Description.displayName

export { Sheet, SheetTrigger, SheetContent, SheetHeader, SheetTitle, SheetDescription }
