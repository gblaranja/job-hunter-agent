/**
 * Abre numa aba nova uma URL que só fica pronta depois de uma requisição (as URLs pré-assinadas de
 * download). A aba é aberta ainda no clique, para o navegador não bloquear como popup.
 */
export async function openInNewTab(resolveUrl: () => Promise<{ url: string }>): Promise<void> {
  const tab = window.open('about:blank', '_blank')
  try {
    const { url } = await resolveUrl()
    if (tab) {
      tab.opener = null
      tab.location.href = url
    } else {
      window.location.assign(url)
    }
  } catch (error) {
    tab?.close()
    throw error
  }
}
