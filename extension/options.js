import { originPattern } from './payload.js'

const $ = (id) => document.getElementById(id)

chrome.storage.local.get(['baseUrl', 'appUrl', 'token']).then((saved) => {
  $('baseUrl').value = saved.baseUrl ?? ''
  $('appUrl').value = saved.appUrl ?? ''
  $('token').value = saved.token ?? ''
})

$('form').addEventListener('submit', async (event) => {
  event.preventDefault()
  const baseUrl = $('baseUrl').value.trim()
  // Permissão só para o endereço do Job Hunter, pedida neste clique.
  const granted = await chrome.permissions.request({ origins: [originPattern(baseUrl)] })
  if (!granted) {
    $('message').textContent = 'Sem a permissão para o endereço da API, a extensão não consegue salvar.'
    return
  }
  await chrome.storage.local.set({ baseUrl, appUrl: $('appUrl').value.trim(), token: $('token').value.trim() })
  $('message').textContent = 'Salvo.'
})
