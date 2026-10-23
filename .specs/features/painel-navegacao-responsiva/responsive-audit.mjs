// Auditoria por largura (RESP-01): mede se alguma tela do painel rola na horizontal.
// Abre o Chrome headless pelo DevTools Protocol (WebSocket nativo do Node 24, sem dependências),
// entra com o login de desenvolvimento e compara scrollWidth com clientWidth em cada rota e largura.
// Sai com código 1 se alguma rota estourar.
//
// Pré-requisitos: backend em :8080 e `npm run dev` em :5173.
// Variáveis: BASE (http://localhost:5173), CHROME (google-chrome), JH_USER/JH_PASS (login de dev do README),
// WIDTHS (320,375,768,1024), ROUTES (as do RESP-01), SHOTS (pasta para capturas; desligado por padrão,
// porque as telas mostram dados reais da instalação).
import { spawn } from 'node:child_process'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const base = process.env.BASE ?? 'http://localhost:5173'
const widths = (process.env.WIDTHS ?? '320,375,768,1024').split(',').map(Number)
const routes = (process.env.ROUTES ?? '/inbox,/board,/applications,/responses,/jobs/new,/resumes,/companies,/insights').split(',')
const shots = process.env.SHOTS
const port = 9300 + Math.floor(Math.random() * 600)
const profile = mkdtempSync(join(tmpdir(), 'responsive-audit-'))
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const chrome = spawn(process.env.CHROME ?? 'google-chrome', [
  '--headless=new',
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${profile}`,
  '--no-first-run',
  'about:blank',
], { stdio: 'ignore' })

let socket
try {
  let target
  for (let attempt = 0; attempt < 50 && !target; attempt++) {
    await sleep(200)
    try {
      target = (await (await fetch(`http://127.0.0.1:${port}/json`)).json()).find((entry) => entry.type === 'page')
    } catch {
      // O Chrome ainda está subindo.
    }
  }
  if (!target) throw new Error('Chrome não respondeu no DevTools Protocol.')

  socket = new WebSocket(target.webSocketDebuggerUrl)
  await new Promise((resolve) => socket.addEventListener('open', resolve))
  let nextId = 0
  const pending = new Map()
  socket.addEventListener('message', (event) => {
    const message = JSON.parse(event.data)
    pending.get(message.id)?.(message)
    pending.delete(message.id)
  })
  const send = (method, params = {}) =>
    new Promise((resolve) => {
      const id = ++nextId
      pending.set(id, resolve)
      socket.send(JSON.stringify({ id, method, params }))
    })
  const evaluate = async (expression) =>
    (await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.result?.value

  await send('Page.enable')
  await send('Page.navigate', { url: `${base}/login` })
  await sleep(1500)
  const credentials = JSON.stringify({ username: process.env.JH_USER ?? 'admin', password: process.env.JH_PASS ?? 'jobhunter' })
  const loginStatus = await evaluate(`(async () => {
    await fetch('/api/auth/me')
    const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN=')) ?? ''
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-XSRF-TOKEN': decodeURIComponent(cookie.slice(11)) },
      body: new URLSearchParams(${credentials}),
    })
    return response.status
  })()`)
  if (loginStatus !== 200) throw new Error(`Login de desenvolvimento falhou (HTTP ${loginStatus}).`)

  if (shots) mkdirSync(shots, { recursive: true })
  const failures = []
  for (const width of widths) {
    await send('Emulation.setDeviceMetricsOverride', { width, height: 812, deviceScaleFactor: 1, mobile: width < 768 })
    for (const route of routes) {
      await send('Page.navigate', { url: base + route })
      await sleep(2500)
      const result = await evaluate(`(() => {
        const root = document.documentElement
        return { path: location.pathname, scrollWidth: root.scrollWidth, clientWidth: root.clientWidth }
      })()`)
      const overflow = result.path !== route || result.scrollWidth > result.clientWidth
      console.log(`${overflow ? 'FAIL' : 'ok  '} ${String(width).padStart(4)}px ${route.padEnd(14)} scrollWidth=${result.scrollWidth} clientWidth=${result.clientWidth}${result.path !== route ? ` (redirecionou para ${result.path})` : ''}`)
      if (overflow) failures.push(`${width}px ${route}`)
      if (shots) {
        const shot = await send('Page.captureScreenshot', { format: 'png' })
        writeFileSync(join(shots, `${width}${route.replaceAll('/', '_')}.png`), Buffer.from(shot.result.data, 'base64'))
      }
    }
  }
  console.log(failures.length === 0 ? '\nNenhuma rota rola na horizontal.' : `\n${failures.length} rota(s) com estouro: ${failures.join(', ')}`)
  process.exitCode = failures.length === 0 ? 0 : 1
} catch (error) {
  console.error(error.message)
  process.exitCode = 2
} finally {
  socket?.close()
  chrome.kill()
  await sleep(300)
  rmSync(profile, { recursive: true, force: true })
}
