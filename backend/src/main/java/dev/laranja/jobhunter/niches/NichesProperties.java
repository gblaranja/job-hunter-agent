package dev.laranja.jobhunter.niches;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Nichos, fit e as bases públicas de empresas.
 *
 * @param fitCron recálculo semanal da classificação e do fit (depois das métricas de domingo)
 */
@ConfigurationProperties("jobhunter.niches")
public record NichesProperties(@DefaultValue Bcb bcb, @DefaultValue Cnpj cnpj,
		@DefaultValue("0 0 23 * * SUN") String fitCron) {

	/**
	 * Relação de Instituições em Funcionamento do Banco Central (API OData de dados abertos).
	 *
	 * @param includeSingularCooperatives as ~700 cooperativas singulares; por padrão só centrais e confederações, onde
	 * ficam os times de tecnologia
	 */
	public record Bcb(@DefaultValue("https://olinda.bcb.gov.br/olinda/servico/Instituicoes_em_funcionamento/versao/v1/odata") String baseUrl,
			@DefaultValue("false") boolean includeSingularCooperatives, @DefaultValue("0 0 3 1 * *") String cron) {
	}

	/**
	 * Base de CNPJ da Receita Federal, baixada à mão (arquivos {@code Empresas*.zip} e {@code Estabelecimentos*.zip}
	 * de arquivos.receitafederal.gov.br) numa pasta. Os arquivos de sócios nunca são lidos.
	 *
	 * @param directory pasta com os zips; sem ela, a importação fica desligada
	 * @param cnaes CNAEs principais, só dígitos
	 * @param minCapital capital social mínimo, para separar consultorias reais de PJs de uma pessoa só
	 */
	public record Cnpj(@Nullable String directory, @DefaultValue({ "6204000", "6201501", "6209100" }) List<String> cnaes,
			@DefaultValue("100000") BigDecimal minCapital) {
	}

}
