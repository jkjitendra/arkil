import { createRootRoute, createRoute, createRouter, Link, Outlet } from '@tanstack/react-router'
import { Layout } from '@/components/Layout'
import { ProjectsPage } from './ProjectsPage'
import { ProjectDetailPage } from './ProjectDetailPage'
import { SettingsPage } from './SettingsPage'
import { CallbackPage } from './CallbackPage'
import { SilentRefreshPage } from './SilentRefreshPage'
import { LoginPage } from './LoginPage'
import { useAuth } from '@/lib/auth'

// Auth guard component
function AuthGuard({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="animate-spin h-8 w-8 border-4 border-primary border-t-transparent rounded-full" />
      </div>
    )
  }

  if (!isAuthenticated) {
    return <LoginPage />
  }

  return <>{children}</>
}

// ─────────────────────────────────────────────────────────────────
// Root route - just outlet, no auth
// ─────────────────────────────────────────────────────────────────

const rootRoute = createRootRoute({
  component: () => <Outlet />,
})

// ─────────────────────────────────────────────────────────────────
// PUBLIC ROUTES (no auth, no layout)
// ─────────────────────────────────────────────────────────────────

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: LoginPage,
})

const callbackRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/callback',
  component: CallbackPage,
})

const silentRefreshRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/silent-refresh',
  component: SilentRefreshPage,
})

// ─────────────────────────────────────────────────────────────────
// PROTECTED ROUTES (auth guard + layout, direct under root)
// ─────────────────────────────────────────────────────────────────

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: () => (
    <AuthGuard>
      <Layout>
        <ProjectsPage />
      </Layout>
    </AuthGuard>
  ),
})

const projectDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/projects/$projectId',
  component: () => (
    <AuthGuard>
      <Layout>
        <ProjectDetailPage />
      </Layout>
    </AuthGuard>
  ),
})

const keysRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/keys',
  component: () => (
    <AuthGuard>
      <Layout>
        <div className="space-y-4">
          <h1 className="text-3xl font-bold">API Keys</h1>
          <p className="text-muted-foreground">
            View and manage API keys across all your projects.
          </p>
          <p className="text-sm">
            Select a project from the <Link to="/" className="text-primary hover:underline">Projects</Link> page to manage its keys.
          </p>
        </div>
      </Layout>
    </AuthGuard>
  ),
})

const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings',
  component: () => (
    <AuthGuard>
      <Layout>
        <SettingsPage />
      </Layout>
    </AuthGuard>
  ),
})

const notFoundRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '*',
  component: () => (
    <AuthGuard>
      <Layout>
        <div className="flex flex-col items-center justify-center min-h-[60vh] text-center">
          <h1 className="text-6xl font-bold text-muted-foreground">404</h1>
          <p className="text-xl mt-4">Page not found</p>
          <Link to="/" className="text-primary hover:underline mt-4">
            Go back home
          </Link>
        </div>
      </Layout>
    </AuthGuard>
  ),
})

// ─────────────────────────────────────────────────────────────────
// BUILD ROUTER
// ─────────────────────────────────────────────────────────────────

const routeTree = rootRoute.addChildren([
  // Public routes
  loginRoute,
  callbackRoute,
  silentRefreshRoute,
  // Protected routes (each has its own auth guard + layout)
  indexRoute,
  projectDetailRoute,
  keysRoute,
  settingsRoute,
  notFoundRoute,
])

export const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
