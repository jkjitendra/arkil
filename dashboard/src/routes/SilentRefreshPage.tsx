import { useEffect } from 'react'
import { userManager } from '@/lib/auth'

// Silent refresh page - loaded in hidden iframe for token renewal
export function SilentRefreshPage() {
  useEffect(() => {
    const handleCallback = async () => {
      try {
        await userManager.signinSilentCallback()
      } catch (error) {
        console.error('Silent refresh callback error:', error)
      }
    }
    handleCallback()
  }, [])

  // This page is loaded in an iframe - nothing to render
  return null
}
