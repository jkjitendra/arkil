import { Link } from '@tanstack/react-router'
import { ArrowRight, LogIn } from 'lucide-react'
import { AuthFrame, ArkilBrand } from '@/components/AuthFrame'
import { SocialLoginButtons } from '@/components/SocialLoginButtons'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/lib/auth'

export function LoginPage() {
  const { login, loginWithSocial, isLoading } = useAuth()

  const handleLogin = async () => {
    try {
      await login()
    } catch (error) {
      console.error('Login error:', error)
    }
  }

  const handleSocialLogin = async (provider: string) => {
    try {
      await loginWithSocial(provider)
    } catch (error) {
      console.error('Social login error:', error)
    }
  }

  return (
    <AuthFrame>
      <ArkilBrand />
      <div className="mt-10">
        <p className="text-sm font-medium text-foreground-secondary">Secure authentication infrastructure</p>
        <h1 className="mt-3 text-2xl font-semibold tracking-tight text-foreground">Welcome back</h1>
        <p className="mt-2 text-sm leading-6 text-foreground-secondary">Sign in to manage your authentication environments.</p>
      </div>

      <div className="mt-8 space-y-5">
        <SocialLoginButtons mode="signin" disabled={isLoading} onSocialLogin={handleSocialLogin} />
        <Button size="lg" onClick={handleLogin} disabled={isLoading} className="h-11 w-full">
          <LogIn className="size-4" />
          {isLoading ? 'Loading…' : 'Continue with Email'}
        </Button>
      </div>

      <p className="mt-8 text-center text-sm text-foreground-secondary">
        Don&apos;t have an account?{' '}
        <Link to="/signup" className="inline-flex items-center gap-1 font-medium text-primary transition-colors duration-150 hover:text-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background">
          Sign up <ArrowRight className="size-3.5" aria-hidden="true" />
        </Link>
      </p>
    </AuthFrame>
  )
}
