import { capturePage } from './capture.js'
import { jobCommand, previewRequest, problemMessage } from './payload.js'

const $ = (id) => document.getElementById(id)
let preview = null

function say(text, error = false) {
  $('message').textContent = text
  $('message').className = error ? 'error' : ''
}

async function settings() {
  const { baseUrl, appUrl, token } = await chrome.storage.local.get(['baseUrl', 'appUrl', 'token'])
  if (!baseUrl || !token) throw new Error('Configure o endereço do Job Hunter e o token primeiro.')
  return { baseUrl: baseUrl.replace(/\/$/, ''), appUrl: (appUrl || baseUrl).replace(/\/$/, ''), token }
}

async function call(path, body) {
  const { baseUrl, token } = await settings()
  const response = await fetch(baseUrl + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
  const json = await response.json().catch(() => null)
  if (!response.ok) throw new Error(problemMessage(response.status, json))
  return json
}

$('capture').addEventListener('click', async () => {
  try {
    say('Lendo a página…')
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true })
    const [{ result }] = await chrome.scripting.executeScript({ target: { tabId: tab.id }, func: capturePage })
    preview = await call('/api/jobs/import/preview', previewRequest(result))
    const { appUrl } = await settings()
    if (preview.existingJobId) {
      $('message').innerHTML = ''
      const link = Object.assign(document.createElement('a'), {
        href: `${appUrl}/jobs/${preview.existingJobId}`,
        target: '_blank',
        textContent: 'Esta vaga já está cadastrada: abrir',
      })
      $('message').append(link)
      return
    }
    $('title').value = preview.title ?? ''
    $('company').value = preview.company ?? ''
    $('location').value = preview.location ?? ''
    $('warnings').replaceChildren(
      ...(preview.warnings ?? []).map((w) => Object.assign(document.createElement('li'), { textContent: w })),
    )
    $('review').hidden = false
    say('Confira os campos e salve.')
  } catch (error) {
    say(error.message, true)
  }
})

$('review').addEventListener('submit', async (event) => {
  event.preventDefault()
  try {
    const job = await call(
      '/api/jobs',
      jobCommand({ ...preview, title: $('title').value, company: $('company').value, location: $('location').value || null }),
    )
    const { appUrl } = await settings()
    $('review').hidden = true
    $('message').replaceChildren(
      Object.assign(document.createElement('a'), { href: `${appUrl}/jobs/${job.id}`, target: '_blank', textContent: 'Vaga salva: abrir' }),
    )
  } catch (error) {
    say(error.message, true)
  }
})
