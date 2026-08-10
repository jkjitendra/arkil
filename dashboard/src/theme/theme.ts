export type ThemePreference = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'arkil-theme'

export function getStoredTheme(): ThemePreference {
  return (localStorage.getItem(STORAGE_KEY) as ThemePreference) || 'system'
}

export function setStoredTheme(theme: ThemePreference) {
  localStorage.setItem(STORAGE_KEY, theme)
}

export function applyTheme(preference: ThemePreference) {
  const root = document.documentElement
  const systemDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  const resolved = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference
  root.setAttribute('data-theme', resolved)
}
