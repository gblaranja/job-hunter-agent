import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { useCurrentUser, useLogin, useLoginProviders } from '@/api/queries'
import { LogoMark } from '@/components/brand/Logo'
import { BRAND } from '@/lib/brand'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Separator } from '@/components/ui/separator'

const googleErrors: Record<string, string> = {
  negado: 'Esta conta Google não tem acesso a esta instalação. Entre com a conta do dono ou com usuário e senha.',
  erro: 'Não foi possível concluir o login com Google. Tente de novo.',
  indisponivel: 'O login com Google não está configurado nesta instalação.',
}

export function LoginPage() {
  const me = useCurrentUser()
  const providers = useLoginProviders()
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const from = (location.state as { from?: string } | null)?.from ?? '/today'
  const googleError = googleErrors[params.get('google') ?? '']

  if (me.data) return <Navigate to={from} replace />

  function submit(event: FormEvent) {
    event.preventDefault()
    login.mutate({ username, password }, { onSuccess: () => navigate(from, { replace: true }) })
  }

  return (
    <main className="grid min-h-svh place-items-center bg-sidebar px-4">
      <div className="w-full max-w-sm">
        <Card>
          <CardHeader>
            <LogoMark className="mb-2 size-10" />
            <CardTitle className="font-display text-2xl tracking-tight">{BRAND.name}</CardTitle>
            <CardDescription>Entre para acompanhar suas vagas e candidaturas.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {googleError && (
              <Alert variant="destructive">
                <AlertDescription>{googleError}</AlertDescription>
              </Alert>
            )}
            {providers.data?.google && (
              <>
                <Button asChild variant="outline" className="w-full">
                  {/* Navegação completa: o backend redireciona para o Google e volta já com a sessão. */}
                  <a href="/api/auth/google">
                    <GoogleIcon /> Entrar com Google
                  </a>
                </Button>
                <div className="flex items-center gap-3 text-xs text-muted-foreground">
                  <Separator className="flex-1" /> ou com usuário e senha <Separator className="flex-1" />
                </div>
              </>
            )}
            <form onSubmit={submit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="username">Usuário</Label>
                <Input
                  id="username"
                  autoComplete="username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Senha</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                />
              </div>
              {login.error && (
                <Alert variant="destructive">
                  <AlertDescription>{login.error.message}</AlertDescription>
                </Alert>
              )}
              <Button type="submit" className="w-full" disabled={login.isPending}>
                Entrar
              </Button>
            </form>
          </CardContent>
        </Card>
        <p className="mt-4 text-center text-sm text-sidebar-foreground/70">
          <Link to="/" className="hover:text-sidebar-foreground hover:underline">
            Conheça o {BRAND.name}
          </Link>
        </p>
      </div>
    </main>
  )
}

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" className="size-4" aria-hidden>
      <path fill="#4285F4" d="M23.5 12.27c0-.85-.08-1.67-.22-2.45H12v4.64h6.45a5.52 5.52 0 0 1-2.4 3.62v3h3.88c2.27-2.09 3.57-5.17 3.57-8.81Z" />
      <path fill="#34A853" d="M12 24c3.24 0 5.96-1.07 7.94-2.92l-3.88-3c-1.08.72-2.45 1.15-4.06 1.15-3.12 0-5.77-2.11-6.71-4.95H1.29v3.1A12 12 0 0 0 12 24Z" />
      <path fill="#FBBC05" d="M5.29 14.28a7.2 7.2 0 0 1 0-4.56v-3.1H1.29a12 12 0 0 0 0 10.76l4-3.1Z" />
      <path fill="#EA4335" d="M12 4.77c1.76 0 3.34.61 4.59 1.8l3.44-3.44A11.97 11.97 0 0 0 12 0 12 12 0 0 0 1.29 6.62l4 3.1C6.23 6.88 8.88 4.77 12 4.77Z" />
    </svg>
  )
}
