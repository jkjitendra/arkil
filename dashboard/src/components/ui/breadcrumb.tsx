import * as React from 'react'
import { ChevronRight } from 'lucide-react'
import { cn } from '@/lib/utils'

function Breadcrumb({ ...props }: React.ComponentProps<'nav'>) { return <nav aria-label="breadcrumb" {...props} /> }
function BreadcrumbList({ className, ...props }: React.ComponentProps<'ol'>) { return <ol className={cn('flex flex-wrap items-center gap-1.5 break-words text-sm text-foreground-muted', className)} {...props} /> }
function BreadcrumbItem({ className, ...props }: React.ComponentProps<'li'>) { return <li className={cn('inline-flex items-center gap-1.5', className)} {...props} /> }
function BreadcrumbLink({ className, ...props }: React.ComponentProps<'a'>) { return <a className={cn('transition-colors duration-150 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background', className)} {...props} /> }
function BreadcrumbSeparator({ children, className, ...props }: React.ComponentProps<'li'>) { return <li role="presentation" aria-hidden="true" className={cn('[&>svg]:size-3.5', className)} {...props}>{children ?? <ChevronRight />}</li> }
function BreadcrumbPage({ className, ...props }: React.ComponentProps<'span'>) { return <span aria-current="page" className={cn('font-medium text-foreground', className)} {...props} /> }

export { Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbSeparator, BreadcrumbPage }
