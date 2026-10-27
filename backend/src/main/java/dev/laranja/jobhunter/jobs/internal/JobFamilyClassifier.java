package dev.laranja.jobhunter.jobs.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.laranja.jobhunter.jobs.JobFamily;
import dev.laranja.jobhunter.shared.TextNormalizer;
import org.jspecify.annotations.Nullable;

/**
 * Família de cargo pelo título, em três passos:
 * <ol>
 * <li>sem nenhuma palavra de tecnologia e com uma de outra área ("vendas", "RH", "financeiro"), é
 * {@link JobFamily#FORA_DE_TI};</li>
 * <li>o papel escrito no título ("gerente", "full stack", "QA", "dados", "backend"...) decide, nesta ordem;</li>
 * <li>sem papel, decide a tecnologia ("React" é frontend, "Java" é backend; as duas juntas, full stack).</li>
 * </ol>
 * Título de tecnologia sem nada disso ("Engenheiro de Software") é {@link JobFamily#OUTRA}; sem nenhum sinal,
 * fica sem família, e uma vaga sem família nunca é descartada automaticamente.
 */
public final class JobFamilyClassifier {

	private static final Map<JobFamily, List<String>> ROLES = new LinkedHashMap<>();

	private static final Map<JobFamily, List<String>> TECHNOLOGIES = new LinkedHashMap<>();

	static {
		ROLES.put(JobFamily.GESTAO, List.of("gerente", "manager", "coordenador", "coordenadora", "head", "diretor",
				"diretora", "director", "cto", "cio", "vp", "vice president", "supervisor", "supervisora", "gestor",
				"gestora"));
		ROLES.put(JobFamily.FULLSTACK, List.of("full stack", "fullstack"));
		ROLES.put(JobFamily.QA, List.of("qa", "quality assurance", "tester", "testes", "teste", "test", "testing",
				"sdet", "quality engineer", "analista de qualidade"));
		ROLES.put(JobFamily.MOBILE, List.of("mobile", "android", "ios"));
		ROLES.put(JobFamily.FRONTEND, List.of("front end", "frontend", "ui engineer"));
		ROLES.put(JobFamily.BACKEND, List.of("back end", "backend"));
		ROLES.put(JobFamily.DEVOPS_SRE, List.of("devops", "dev ops", "devsecops", "sre", "site reliability",
				"platform engineer", "engenheiro de plataforma", "engenheira de plataforma", "infraestrutura",
				"infrastructure", "infra", "cloud engineer", "engenheiro cloud", "engenheiro de cloud", "observabilidade",
				"observability"));
		ROLES.put(JobFamily.SEGURANCA, List.of("seguranca", "security", "ciberseguranca", "cybersecurity", "appsec",
				"pentest", "pentester"));
		ROLES.put(JobFamily.DADOS, List.of("dados", "data", "machine learning", "ml", "mlops", "bi",
				"business intelligence", "analytics", "big data", "inteligencia artificial", "ia", "ai", "dba",
				"cientista"));

		TECHNOLOGIES.put(JobFamily.MOBILE, List.of("flutter", "react native", "swift", "kotlin multiplatform",
				"xamarin"));
		TECHNOLOGIES.put(JobFamily.FRONTEND, List.of("react", "reactjs", "react js", "angular", "vue", "vuejs",
				"vue js", "nextjs", "next js", "svelte"));
		TECHNOLOGIES.put(JobFamily.DADOS, List.of("databricks", "spark", "pyspark", "etl", "power bi", "llm"));
		TECHNOLOGIES.put(JobFamily.DEVOPS_SRE, List.of("kubernetes", "terraform", "cloud"));
		TECHNOLOGIES.put(JobFamily.BACKEND, List.of("java", "kotlin", "golang", "go", "python", "node", "nodejs",
				"node js", "net", "dotnet", "php", "ruby", "rails", "elixir", "scala", "spring", "spring boot",
				"springboot", "laravel", "django", "rust", "cobol", "api", "apis", "microsservicos", "microservices"));
	}

	/** Palavras de tecnologia sem família própria. */
	private static final List<String> GENERIC_TECH = List.of("desenvolvedor", "desenvolvedora", "desenvolvimento",
			"developer", "dev", "programador", "programadora", "software", "engineer", "engenheiro de software",
			"engenheira de software", "ti", "it", "tecnologia", "technology", "tech", "sistemas", "suporte", "support",
			"arquiteto", "arquiteta", "architect", "ux", "ui", "product designer", "scrum", "agile", "sap", "erp");

	/** Palavras de cargos de outras áreas. */
	private static final List<String> OUTSIDE_TECH = List.of("vendas", "vendedor", "vendedora", "comercial", "sales",
			"account executive", "account manager", "executivo de contas", "executiva de contas", "sdr", "bdr",
			"financeiro", "financeira", "finance", "financial", "contabil", "contador", "contadora", "accountant",
			"fiscal", "tributario", "rh", "recursos humanos", "human resources", "people partner", "recrutador",
			"recrutadora", "recruiter", "talent acquisition", "marketing", "juridico", "advogado", "advogada", "lawyer",
			"enfermeiro", "enfermeira", "medico", "medica", "motorista", "atendente", "atendimento", "customer success",
			"customer service", "administrativo", "administrativa", "auxiliar", "logistica", "estoquista", "operador",
			"operadora", "recepcionista", "secretaria", "compras", "comprador", "compradora", "professor", "professora",
			"teacher", "copywriter", "social media", "engenheiro civil", "engenheira civil", "mecanico", "eletricista");

	private JobFamilyClassifier() {
	}

	public static @Nullable JobFamily classify(@Nullable String title) {
		if (title == null || title.isBlank()) {
			return null;
		}
		String text = " " + TextNormalizer.normalize(title) + " ";
		boolean tech = containsAny(text, GENERIC_TECH) || ROLES.entrySet()
			.stream()
			.filter(entry -> entry.getKey() != JobFamily.GESTAO)
			.anyMatch(entry -> containsAny(text, entry.getValue()))
				|| TECHNOLOGIES.values().stream().anyMatch(terms -> containsAny(text, terms));
		if (!tech && containsAny(text, OUTSIDE_TECH)) {
			return JobFamily.FORA_DE_TI;
		}
		for (Map.Entry<JobFamily, List<String>> role : ROLES.entrySet()) {
			if (containsAny(text, role.getValue())) {
				return role.getKey();
			}
		}
		boolean frontend = containsAny(text, TECHNOLOGIES.get(JobFamily.FRONTEND));
		boolean backend = containsAny(text, TECHNOLOGIES.get(JobFamily.BACKEND));
		if (frontend && backend && !containsAny(text, TECHNOLOGIES.get(JobFamily.MOBILE))) {
			return JobFamily.FULLSTACK;
		}
		for (Map.Entry<JobFamily, List<String>> technology : TECHNOLOGIES.entrySet()) {
			if (containsAny(text, technology.getValue())) {
				return technology.getKey();
			}
		}
		return tech ? JobFamily.OUTRA : null;
	}

	private static boolean containsAny(String text, List<String> terms) {
		return terms.stream().anyMatch(term -> text.contains(" " + term + " "));
	}

}
