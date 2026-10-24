package dev.laranja.jobhunter.recognition.internal;

import java.util.List;

/** Porta para a API de busca na web usada na verificação dos selos (Tavily; Brave e Serper no futuro). */
public interface RecognitionSearchClient {

	String provider();

	boolean enabled();

	List<SearchHit> search(SearchQuery query);

	/**
	 * @param includeDomains restringe a busca a estes domínios (a página oficial do GPTW, por exemplo); vazio busca
	 * em tudo
	 */
	record SearchQuery(String text, List<String> includeDomains) {
	}

	record SearchHit(String title, String url, String content) {
	}

}
