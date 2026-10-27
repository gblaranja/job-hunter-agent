/**
 * Regras puras da extensão (sem APIs do navegador), testadas com `node --test extension/test`.
 * Só a página aberta, por clique (D-03): nada aqui percorre listas nem abre outras páginas.
 */

/** Limites combinados com o POST /api/jobs/import/preview. */
export const MAX_JSON_LD = 1_000_000
export const MAX_TEXT = 50_000

/** Entidades HTML básicas: a Gupy publica o JSON-LD escapado dentro do script (`{&quot;@type&quot;...`). */
export function decodeEntities(text) {
  return text
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
}

function parseBlock(block) {
  try {
    return JSON.parse(block)
  } catch {
    try {
      return JSON.parse(decodeEntities(block))
    } catch {
      return undefined
    }
  }
}

/** Junta os blocos JSON-LD da página num array JSON em texto; blocos inválidos são ignorados. */
export function jsonLdArray(blocks) {
  const parsed = []
  for (const block of blocks ?? []) {
    const value = parseBlock(block)
    if (value !== undefined) parsed.push(value)
  }
  if (parsed.length === 0) return null
  const text = JSON.stringify(parsed)
  return text.length <= MAX_JSON_LD ? text : null
}

/** Corpo do preview: o link da página, o JSON-LD e o texto selecionado, quando houver. */
export function previewRequest({ url, jsonLdBlocks, selection }) {
  const text = (selection ?? '').trim()
  return {
    url,
    jsonLd: jsonLdArray(jsonLdBlocks),
    text: text ? text.slice(0, MAX_TEXT) : null,
  }
}

/** Corpo do POST /api/jobs (ManualJobCommand) a partir da prévia revisada. */
export function jobCommand(preview) {
  return {
    title: (preview.title ?? '').trim(),
    company: (preview.company ?? '').trim(),
    location: preview.location ?? null,
    workModel: preview.workModel ?? null,
    scope: preview.scope ?? null,
    salaryRange: preview.salaryRange ?? null,
    url: preview.url,
    description: preview.description ?? null,
  }
}

/** Origem do backend no formato das permissões de host (`https://exemplo.com/*`). */
export function originPattern(baseUrl) {
  const url = new URL(baseUrl)
  return `${url.protocol}//${url.host}/*`
}

/** Mensagem do ProblemDetail do backend, ou o status. */
export function problemMessage(status, body) {
  if (status === 401) return 'Token recusado. Gere outro em Configurações → Extensão do navegador.'
  return (body && (body.detail || body.title)) || `Erro ${status}.`
}
