import { Hexagon } from 'lucide-react'
import type { ReactNode } from 'react'

export function ArkilBrand() {
  return (
    <div className="flex items-center gap-2.5">
      <span className="flex size-9 items-center justify-center rounded-lg border border-primary/25 bg-primary-subtle text-primary">
        <Hexagon className="size-5" strokeWidth={1.8} aria-hidden="true" />
      </span>
      <span className="text-lg font-semibold tracking-tight text-foreground">Arkil</span>
    </div>
  )
}

function IdentityVisualization() {
  return (
    <aside className="auth-visual relative hidden overflow-hidden bg-[#09090b] px-14 py-12 text-slate-100 lg:flex lg:flex-col lg:justify-between">
      <div className="auth-mesh absolute inset-0" aria-hidden="true" />
      <div className="auth-node auth-node-one absolute left-[22%] top-[22%] size-12" aria-hidden="true" />
      <div className="auth-node auth-node-two absolute right-[22%] top-[31%] size-7" aria-hidden="true" />
      <div className="auth-node auth-node-three absolute bottom-[24%] left-[38%] size-9" aria-hidden="true" />
      <div className="auth-node auth-node-four absolute bottom-[18%] right-[20%] size-5" aria-hidden="true" />
      <div className="relative z-10 flex items-center gap-2.5 text-sm font-medium text-slate-100">
        <span className="flex size-8 items-center justify-center rounded-lg border border-indigo-300/20 bg-indigo-400/10 text-indigo-200">
          <Hexagon className="size-4" strokeWidth={1.8} aria-hidden="true" />
        </span>
        Arkil
      </div>
      <div className="relative z-10 max-w-sm">
        <p className="text-sm font-medium text-indigo-200">Authentication, without the friction.</p>
        <p className="mt-2 text-sm leading-6 text-slate-400">
          Build secure identity flows your users trust, with the controls your team needs.
        </p>
      </div>
    </aside>
  )
}

export function AuthFrame({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-surface lg:grid lg:grid-cols-2">
      <main className="flex min-h-screen items-center justify-center px-5 py-10 sm:px-8">
        <div className="w-full max-w-[25rem]">{children}</div>
      </main>
      <IdentityVisualization />
    </div>
  )
}
