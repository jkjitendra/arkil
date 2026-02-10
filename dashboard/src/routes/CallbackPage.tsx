import { useEffect, useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { userManager } from '@/lib/auth'

// Handles the OIDC callback after login redirect
export function CallbackPage() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const handleCallback = async () => {
      try {
        // Complete the signin, this parses the authorization code from URL
        await userManager.signinRedirectCallback()
        // Redirect to home on success
        navigate({ to: '/' })
      } catch (err) {
        console.error('Callback error:', err)
        setError(err instanceof Error ? err.message : 'Authentication failed')
      }
    }
    handleCallback()
  }, [navigate])

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center space-y-4">
          <h1 className="text-2xl font-bold text-destructive">Authentication Error</h1>
          <p className="text-muted-foreground">{error}</p>
          <button
            onClick={() => navigate({ to: '/' })}
            className="text-primary hover:underline"
          >
            Go back home
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center space-y-4">
        <div className="animate-spin h-8 w-8 border-4 border-primary border-t-transparent rounded-full mx-auto" />
        <p className="text-muted-foreground">Completing sign in...</p>
      </div>
    </div>
  )
}
