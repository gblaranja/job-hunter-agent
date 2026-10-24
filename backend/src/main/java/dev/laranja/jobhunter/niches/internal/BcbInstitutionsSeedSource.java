package dev.laranja.jobhunter.niches.internal;

import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.laranja.jobhunter.companies.CompanyKind;
import dev.laranja.jobhunter.niches.NichesProperties;
import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Relação de Instituições em Funcionamento no País, do Banco Central (API OData de dados abertos, atualizada
 * diariamente): bancos, sociedades (SCD, SEP, financeiras, instituições de pagamento, corretoras), cooperativas e
 * administradoras de consórcio. O tipo de autorização define o subsegmento.
 */
@Component
class BcbInstitutionsSeedSource implements CompanySeedSource {

	static final String COOPERATIVE = "Cooperativa de Crédito";

	static final String CONSORTIUM = "Administradora de Consórcio";

	private final RestClient client;

	private final NichesProperties.Bcb properties;

	BcbInstitutionsSeedSource(RestClient.Builder http, NichesProperties properties) {
		this.properties = properties.bcb();
		this.client = http.clone().baseUrl(this.properties.baseUrl()).build();
	}

	@Override
	public String key() {
		return "bcb";
	}

	@Override
	public String label() {
		return "Banco Central: instituições em funcionamento";
	}

	@Override
	public @Nullable String unavailableReason() {
		return null;
	}

	@Override
	public void fetch(Consumer<SeedRecord> sink) {
		for (Institution institution : list("SedesBancoComMultCE")) {
			emit(institution, institution.segment(), sink);
		}
		for (Institution institution : list("SedesSociedades")) {
			emit(institution, institution.segment(), sink);
		}
		for (Institution institution : list("SedesCooperativas")) {
			// as singulares são centenas de cooperativas pequenas; a tecnologia fica nas centrais e confederações
			if (this.properties.includeSingularCooperatives() || !"Singular".equalsIgnoreCase(institution.cooperativeClass())) {
				emit(institution, COOPERATIVE, sink);
			}
		}
		for (Institution institution : list("SedesConsorcios")) {
			emit(institution, CONSORTIUM, sink);
		}
	}

	private List<Institution> list(String entitySet) {
		ODataResponse response = this.client.get()
			.uri(builder -> builder.path("/{set}").queryParam("$format", "json").build(entitySet))
			.retrieve()
			.body(ODataResponse.class);
		return (response == null || response.value() == null) ? List.of() : response.value();
	}

	private static void emit(Institution institution, @Nullable String authorizationType, Consumer<SeedRecord> sink) {
		if (institution.cnpj() == null || institution.name() == null || authorizationType == null) {
			return;
		}
		String cnpj = institution.cnpj().replaceAll("\\D", "");
		if (cnpj.isEmpty() || cnpj.length() > 8) {
			return;
		}
		String location = (institution.city() != null)
				? NameFormatter.pretty(institution.city()) + ((institution.state() != null) ? "/" + institution.state() : "")
				: null;
		sink.accept(new SeedRecord(NameFormatter.pretty(institution.name()), "0".repeat(8 - cnpj.length()) + cnpj,
				authorizationType.strip(), CompanyKind.CONTRATANTE, NameFormatter.website(institution.website()),
				location));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ODataResponse(@Nullable List<Institution> value) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Institution(@JsonProperty("CNPJ") @Nullable String cnpj,
			@JsonProperty("NOME_INSTITUICAO") @Nullable String name,
			@JsonProperty("SEGMENTO") @Nullable String segment, @JsonProperty("CLASSE") @Nullable String cooperativeClass,
			@JsonProperty("MUNICIPIO") @Nullable String city, @JsonProperty("UF") @Nullable String state,
			@JsonProperty("SITIO_NA_INTERNET") @Nullable String website) {
	}

}
