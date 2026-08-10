import { createContext, useContext } from 'react'
import type { ThemePreference } from './theme'

export const ThemeContext = createContext<{
  theme: ThemePreference
  setTheme: (theme: ThemePreference) => void
}>({ theme: 'system', setTheme: () => {} })

export const useTheme = () => useContext(ThemeContext)
