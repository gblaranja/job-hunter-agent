import { useContext } from 'react'
import { ThemeContext } from '@/lib/theme-context'

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme precisa ser usado dentro de ThemeProvider.')
  return context
}