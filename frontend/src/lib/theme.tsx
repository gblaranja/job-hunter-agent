import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ThemeContext, type ThemePreference } from '@/lib/theme-context'

const STORAGE_KEY = 'job-hunter-theme'

function readThemePreference(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  } catch {
    // Prefer the system setting when storage is unavailable.
  }
  return 'system'
}

function systemPrefersDark() {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<ThemePreference>(readThemePreference)

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-color-scheme: dark)')
    const applyTheme = () => {
      const isDark = theme === 'dark' || (theme === 'system' && (media?.matches ?? systemPrefersDark()))
      document.documentElement.classList.toggle('dark', isDark)
      document.documentElement.style.colorScheme = isDark ? 'dark' : 'light'
    }

    applyTheme()
    if (theme !== 'system' || !media) return

    media.addEventListener('change', applyTheme)
    return () => media.removeEventListener('change', applyTheme)
  }, [theme])

  const value = useMemo(
    () => ({
      theme,
      setTheme: (nextTheme: ThemePreference) => {
        try {
          window.localStorage.setItem(STORAGE_KEY, nextTheme)
        } catch {
          // The in-memory preference still applies for this session.
        }
        setTheme(nextTheme)
      },
    }),
    [theme],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}