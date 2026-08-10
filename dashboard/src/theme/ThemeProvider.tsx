import { useEffect, useState } from 'react'
import { applyTheme, getStoredTheme, setStoredTheme, type ThemePreference } from './theme'
import { ThemeContext } from './ThemeContext'

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<ThemePreference>(getStoredTheme)

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  useEffect(() => {
    const listener = () => {
      if (getStoredTheme() === 'system') applyTheme('system')
    }
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', listener)
    return () => window.matchMedia('(prefers-color-scheme: dark)').removeEventListener('change', listener)
  }, [])

  const setTheme = (t: ThemePreference) => {
    setStoredTheme(t)
    setThemeState(t)
  }

  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      {children}
    </ThemeContext.Provider>
  )
}
