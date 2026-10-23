import { createContext } from 'react'

export type ThemePreference = 'system' | 'light' | 'dark'

export const ThemeContext = createContext<{
  theme: ThemePreference
  setTheme: (theme: ThemePreference) => void
} | null>(null)