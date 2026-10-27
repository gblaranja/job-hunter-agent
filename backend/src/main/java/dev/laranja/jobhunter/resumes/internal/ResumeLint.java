package dev.laranja.jobhunter.resumes.internal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.laranja.jobhunter.resumes.ResumeLintWarning;
import org.jspecify.annotations.Nullable;

/**
 * Teste "o que o ATS lê", determinístico, sobre o texto extraído pelo Tika: contato, seções com nomes padrão,
 * períodos com ano em ordem decrescente, caracteres ilegíveis, pouco texto para o número de páginas e indício de
 * duas colunas ou tabela (o ATS lê na ordem do arquivo e mistura as colunas).
 */
final class ResumeLint {

	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

	private static final Pattern PHONE = Pattern.compile("(\\+?\\d{2}\\s?)?\\(?\\d{2}\\)?[\\s.-]?9?\\d{4}[\\s.-]?\\d{4}");

	/** "03/2018 - atual", "2015 a 2019", "jan 2018 – presente": o ano inicial. */
	private static final Pattern PERIOD = Pattern.compile("((?:19|20)\\d{2})\\s*(?:-|–|—|a|até|ate|to)\\s*"
			+ "(?:[a-z]{3,9}\\.?\\s*(?:de\\s*)?|\\d{1,2}\\s*/\\s*)?((?:19|20)\\d{2}|atual|atualmente|presente|hoje|o momento|current|present|now)");

	private static final Map<String, Pattern> SECTIONS = Map.of("Experiência",
			heading("experiencia|experiencias|experiencia profissional|historico profissional|experience|work experience"),
			"Formação", heading("formacao|formacao academica|educacao|escolaridade|education"), "Habilidades",
			heading("habilidades|competencias|skills|conhecimentos|tecnologias|competencias tecnicas"));

	private static final int MIN_CHARACTERS_PER_PAGE = 400;

	private ResumeLint() {
	}

	static List<ResumeLintWarning> check(@Nullable String text, @Nullable Integer pages) {
		List<ResumeLintWarning> warnings = new ArrayList<>();
		if (text == null || text.isBlank()) {
			warnings.add(new ResumeLintWarning("SEM_TEXTO",
					"O arquivo não tem texto legível (PDF escaneado ou imagem): o ATS não lê nada dele."));
			return warnings;
		}
		if (!EMAIL.matcher(text).find()) {
			warnings.add(new ResumeLintWarning("SEM_EMAIL", "Não achamos um e-mail no texto: o ATS não preenche o contato."));
		}
		if (!PHONE.matcher(text).find()) {
			warnings.add(new ResumeLintWarning("SEM_TELEFONE", "Não achamos um telefone no texto."));
		}
		String folded = fold(text);
		SECTIONS.entrySet()
			.stream()
			.sorted(Map.Entry.comparingByKey())
			.filter(section -> !section.getValue().matcher(folded).find())
			.forEach(section -> warnings.add(new ResumeLintWarning("SECAO_AUSENTE",
					"Seção \"" + section.getKey() + "\" com nome padrão não encontrada; o ATS procura títulos usuais.")));
		List<Integer> starts = new ArrayList<>();
		Matcher period = PERIOD.matcher(folded);
		while (period.find()) {
			starts.add(Integer.parseInt(period.group(1)));
		}
		if (starts.isEmpty()) {
			warnings.add(new ResumeLintWarning("SEM_DATAS",
					"Nenhum período com ano (ex.: 03/2021 – atual): o ATS não calcula seus anos de experiência."));
		}
		else if (!descending(starts)) {
			warnings.add(new ResumeLintWarning("DATAS_FORA_DE_ORDEM",
					"Os períodos não estão do mais recente para o mais antigo."));
		}
		long unreadable = text.chars().filter(ResumeLint::unreadable).count();
		if (unreadable > 0 && (double) unreadable / text.length() > 0.01) {
			warnings.add(new ResumeLintWarning("CARACTERES_ILEGIVEIS",
					"Parte do texto saiu com caracteres ilegíveis (fonte embutida sem mapeamento): exporte o PDF de novo."));
		}
		int compact = text.replaceAll("\\s+", "").length();
		if (pages != null && pages > 0 && compact / pages < MIN_CHARACTERS_PER_PAGE) {
			warnings.add(new ResumeLintWarning("TEXTO_CURTO", "Pouco texto para " + pages + " página(s): "
					+ "partes do currículo podem ser imagem e não são lidas."));
		}
		if (looksLikeColumns(text)) {
			warnings.add(new ResumeLintWarning("DUAS_COLUNAS", "Muitas linhas curtas e soltas: indício de duas colunas "
					+ "ou tabela, que o ATS pode ler fora de ordem. Prefira uma coluna."));
		}
		return warnings;
	}

	/** Mais da metade das linhas com até 3 palavras, num texto com linhas suficientes. */
	private static boolean looksLikeColumns(String text) {
		List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
		if (lines.size() < 15) {
			return false;
		}
		long shortLines = lines.stream().filter(line -> line.split("\\s+").length <= 3).count();
		return (double) shortLines / lines.size() > 0.55;
	}

	private static boolean descending(List<Integer> starts) {
		int inversions = 0;
		for (int i = 1; i < starts.size(); i++) {
			if (starts.get(i) > starts.get(i - 1)) {
				inversions++;
			}
		}
		return inversions == 0;
	}

	private static boolean unreadable(int c) {
		return c == '�' || (c >= 0xE000 && c <= 0xF8FF) || (Character.isISOControl(c) && c != '\n' && c != '\t');
	}

	private static Pattern heading(String names) {
		return Pattern.compile("(?m)^\\s*(?:" + names + ")\\s*:?\\s*$");
	}

	private static String fold(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
	}

}
