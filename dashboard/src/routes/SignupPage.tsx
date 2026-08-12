import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { AlertCircle, Check, CheckCircle2, Eye, EyeOff, UserPlus } from 'lucide-react'
import { AuthFrame, ArkilBrand } from '@/components/AuthFrame'
import { SocialLoginButtons } from '@/components/SocialLoginButtons'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { registerDeveloper } from '@/lib/api'

type FieldErrors = Partial<Record<'email' | 'password' | 'confirmPassword', string>>

export function SignupPage() {
  const [orgName, setOrgName] = useState('')
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [isSuccess, setIsSuccess] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)

    const nextFieldErrors: FieldErrors = {}
    if (!email.trim()) nextFieldErrors.email = 'Email is required'
    if (password.length < 8) nextFieldErrors.password = 'Password must be at least 8 characters'
    if (password !== confirmPassword) nextFieldErrors.confirmPassword = 'Passwords do not match'
    setFieldErrors(nextFieldErrors)

    if (Object.keys(nextFieldErrors).length > 0) return

    setIsLoading(true)
    try {
      await registerDeveloper({
        email,
        password,
        orgName: orgName.trim() || email.split('@')[0] + "'s Workspace",
        displayName: displayName || undefined,
      })
      setIsSuccess(true)
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'message' in err) {
        setError((err as { message: string }).message)
      } else {
        setError('Registration failed. Please try again.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  if (isSuccess) {
    return (
      <AuthFrame>
        <ArkilBrand />
        <div className="mt-14 text-center">
          <div className="mx-auto flex size-12 items-center justify-center rounded-full border border-success/25 bg-success/10 text-success">
            <CheckCircle2 className="size-6" aria-hidden="true" />
          </div>
          <h1 className="mt-5 text-2xl font-semibold tracking-tight text-foreground">Account created</h1>
          <p className="mt-2 text-sm leading-6 text-foreground-secondary">Your workspace is ready. Sign in to open the Arkil dashboard.</p>
          <Link to="/login" className="mt-7 inline-flex w-full">
            <Button size="lg" className="h-11 w-full">Sign in to Dashboard</Button>
          </Link>
        </div>
      </AuthFrame>
    )
  }

  const passwordIsValid = password.length >= 8

  return (
    <AuthFrame>
      <ArkilBrand />
      <div className="mt-8">
        <p className="text-sm font-medium text-foreground-secondary">Secure authentication infrastructure</p>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight text-foreground">Create your account</h1>
        <p className="mt-2 text-sm leading-6 text-foreground-secondary">Start building secure identity flows for your applications.</p>
      </div>

      <div className="mt-6">
        <SocialLoginButtons mode="signup" disabled={isLoading} />
      </div>

      <form onSubmit={handleSubmit} className="mt-5 space-y-4" noValidate>
        {error ? (
          <Alert variant="destructive">
            <AlertCircle className="absolute left-3 top-3.5 size-4" aria-hidden="true" />
            <AlertDescription className="pl-6">{error}</AlertDescription>
          </Alert>
        ) : null}

        <div className="space-y-1.5">
          <label htmlFor="orgName" className="text-sm font-medium text-foreground">Workspace name <span className="font-normal text-foreground-muted">(optional)</span></label>
          <Input id="orgName" placeholder="Acme Inc. or your name" value={orgName} onChange={(event) => setOrgName(event.target.value)} />
          <p className="text-xs leading-5 text-foreground-muted">Your team or personal workspace. You can change this later.</p>
        </div>

        <div className="space-y-1.5">
          <label htmlFor="email" className="text-sm font-medium text-foreground">Email</label>
          <Input id="email" type="email" placeholder="you@example.com" value={email} onChange={(event) => { setEmail(event.target.value); setFieldErrors((current) => ({ ...current, email: undefined })) }} data-invalid={Boolean(fieldErrors.email) || undefined} required autoFocus />
          {fieldErrors.email ? <p className="text-xs text-danger">{fieldErrors.email}</p> : null}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="displayName" className="text-sm font-medium text-foreground">Display name <span className="font-normal text-foreground-muted">(optional)</span></label>
          <Input id="displayName" placeholder="John Doe" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
        </div>

        <div className="space-y-1.5">
          <label htmlFor="password" className="text-sm font-medium text-foreground">Password</label>
          <div className="relative">
            <Input id="password" type={showPassword ? 'text' : 'password'} placeholder="Min. 8 characters" value={password} onChange={(event) => { setPassword(event.target.value); setFieldErrors((current) => ({ ...current, password: undefined })) }} data-invalid={Boolean(fieldErrors.password) || undefined} className="pr-10" required minLength={8} />
            <button type="button" onClick={() => setShowPassword((current) => !current)} className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-foreground-muted transition-colors duration-150 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background" aria-label={showPassword ? 'Hide password' : 'Show password'}>
              {showPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            </button>
          </div>
          <p className={passwordIsValid ? 'flex items-center gap-1.5 text-xs text-success' : 'flex items-center gap-1.5 text-xs text-foreground-muted'}>
            <Check className="size-3.5" aria-hidden="true" /> At least 8 characters
          </p>
          {fieldErrors.password ? <p className="text-xs text-danger">{fieldErrors.password}</p> : null}
        </div>

        <div className="space-y-1.5">
          <label htmlFor="confirmPassword" className="text-sm font-medium text-foreground">Confirm password</label>
          <div className="relative">
            <Input id="confirmPassword" type={showConfirmPassword ? 'text' : 'password'} placeholder="Confirm your password" value={confirmPassword} onChange={(event) => { setConfirmPassword(event.target.value); setFieldErrors((current) => ({ ...current, confirmPassword: undefined })) }} data-invalid={Boolean(fieldErrors.confirmPassword) || undefined} className="pr-10" required minLength={8} />
            <button type="button" onClick={() => setShowConfirmPassword((current) => !current)} className="absolute inset-y-0 right-0 flex w-10 items-center justify-center text-foreground-muted transition-colors duration-150 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background" aria-label={showConfirmPassword ? 'Hide confirmation password' : 'Show confirmation password'}>
              {showConfirmPassword ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
            </button>
          </div>
          {fieldErrors.confirmPassword ? <p className="text-xs text-danger">{fieldErrors.confirmPassword}</p> : null}
        </div>

        <Button type="submit" size="lg" className="h-11 w-full" disabled={isLoading}>
          <UserPlus className="size-4" />
          {isLoading ? 'Creating account…' : 'Create account'}
        </Button>
      </form>

      <p className="mt-7 text-center text-sm text-foreground-secondary">
        Already have an account? <Link to="/login" className="font-medium text-primary transition-colors duration-150 hover:text-primary-hover focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 focus-visible:ring-offset-2 focus-visible:ring-offset-background">Sign in</Link>
      </p>
    </AuthFrame>
  )
}
