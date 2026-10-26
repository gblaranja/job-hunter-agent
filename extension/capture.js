/**
 * Injetada na aba aberta só depois do clique (permissão activeTab). Devolve os blocos JSON-LD e o texto
 * selecionado; não navega nem lê outras páginas.
 */
export function capturePage() {
  return {
    url: location.href,
    jsonLdBlocks: [...document.querySelectorAll('script[type="application/ld+json"]')].map((s) => s.textContent ?? ''),
    selection: String(window.getSelection() ?? ''),
  }
}
