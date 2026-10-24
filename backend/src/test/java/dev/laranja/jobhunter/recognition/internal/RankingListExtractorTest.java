package dev.laranja.jobhunter.recognition.internal;

import dev.laranja.jobhunter.recognition.internal.RankingListExtractor.Entry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingListExtractorTest {

	@Test
	void readsOneCompanyPerLineWithoutTheNumbering() {
		String text = """
				GPTW TI 2026 — Grandes empresas
				1. Alfa Tecnologia
				2º - Beta Pagamentos
				3) Gama S.A.
				- Delta
				1. Alfa Tecnologia
				""";

		assertThat(RankingListExtractor.byLines(text)).containsExactly(new Entry("GPTW TI 2026 — Grandes empresas", null),
				new Entry("Alfa Tecnologia", 1), new Entry("Beta Pagamentos", 2), new Entry("Gama S.A.", 3),
				new Entry("Delta", null));
	}

	@Test
	void pageTextKeepsOneItemPerLine() {
		String html = "<html><body><nav>Menu</nav><ol><li>Alfa</li><li>Beta&nbsp;Pay</li></ol><script>x()</script></body></html>";

		assertThat(PageText.toText(html)).isEqualTo("Alfa\nBeta Pay");
	}

}
