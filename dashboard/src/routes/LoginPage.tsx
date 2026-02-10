import { useAuth } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { LogIn } from 'lucide-react'

export function LoginPage() {
  const { login, isLoading } = useAuth()

  const handleLogin = async () => {
    try {
      await login()
    } catch (error) {
      console.error('Login error:', error)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background">
      <div className="text-center space-y-8 p-8">
        {/* Logo */}
        <div className="space-y-4">
          <div className="h-16 w-16 rounded-2xl bg-primary flex items-center justify-center mx-auto">
            <span className="text-primary-foreground font-bold text-2xl">A</span>
          </div>
          <h1 className="text-4xl font-bold tracking-tight">
            <span className="gradient-text">Arkil</span> Dashboard
          </h1>
          <p className="text-muted-foreground max-w-sm mx-auto">
            Manage your authentication projects, API keys, and settings
          </p>
        </div>

        {/* Login button */}
        <Button size="lg" onClick={handleLogin} disabled={isLoading} className="px-8">
          <LogIn className="h-5 w-5" />
          {isLoading ? 'Loading...' : 'Sign in with Arkil'}
        </Button>

        {/* Footer */}
        <p className="text-xs text-muted-foreground">
          You'll be redirected to the Arkil authentication server
        </p>
      </div>
    </div>
  )
}
