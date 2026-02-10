import { createContext, useContext, useEffect, useState, useCallback, type ReactNode } from 'react'
import { UserManager, User, WebStorageStateStore } from 'oidc-client-ts'
import { authConfig } from './auth-config'

// Create UserManager instance
const userManager = new UserManager({
  ...authConfig,
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
})

interface AuthContextType {
  user: User | null
  isLoading: boolean
  isAuthenticated: boolean
  login: () => Promise<void>
  logout: () => Promise<void>
  getAccessToken: () => Promise<string | null>
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // Check for existing session on mount
  useEffect(() => {
    const loadUser = async () => {
      try {
        const currentUser = await userManager.getUser()
        if (currentUser && !currentUser.expired) {
          setUser(currentUser)
        }
      } catch (error) {
        console.error('Failed to load user:', error)
      } finally {
        setIsLoading(false)
      }
    }
    loadUser()

    // Listen for user changes
    const handleUserLoaded = (loadedUser: User) => {
      setUser(loadedUser)
    }
    const handleUserUnloaded = () => {
      setUser(null)
    }
    const handleSilentRenewError = (error: Error) => {
      console.error('Silent renew error:', error)
    }

    userManager.events.addUserLoaded(handleUserLoaded)
    userManager.events.addUserUnloaded(handleUserUnloaded)
    userManager.events.addSilentRenewError(handleSilentRenewError)

    return () => {
      userManager.events.removeUserLoaded(handleUserLoaded)
      userManager.events.removeUserUnloaded(handleUserUnloaded)
      userManager.events.removeSilentRenewError(handleSilentRenewError)
    }
  }, [])

  const login = useCallback(async () => {
    try {
      await userManager.signinRedirect()
    } catch (error) {
      console.error('Login failed:', error)
      throw error
    }
  }, [])

  const logout = useCallback(async () => {
    try {
      await userManager.signoutRedirect()
    } catch (error) {
      console.error('Logout failed:', error)
      // Clear local state even if redirect fails
      setUser(null)
      await userManager.removeUser()
    }
  }, [])

  const getAccessToken = useCallback(async (): Promise<string | null> => {
    try {
      const currentUser = await userManager.getUser()
      if (currentUser && !currentUser.expired) {
        return currentUser.access_token
      }
      // Try silent renew
      const renewedUser = await userManager.signinSilent()
      return renewedUser?.access_token || null
    } catch {
      return null
    }
  }, [])

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isAuthenticated: !!user && !user.expired,
        login,
        logout,
        getAccessToken,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}

// Export userManager for callback handling
export { userManager }
