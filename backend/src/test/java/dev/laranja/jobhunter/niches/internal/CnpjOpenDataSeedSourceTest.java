package dev.laranja.jobhunter.niches.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.laranja.jobhunter.companies.CompanyKind;
import dev.laranja.jobhunter.niches.NichesProperties;
import dev.laranja.jobhunter.niches.internal.CompanySeedSource.SeedRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CnpjOpenDataSeedSourceTest {

	@TempDir
	Path directory;

	@Test
	void keepsActiveItHeadquartersAboveTheSizeAndCapitalFloorAndNeverOpensPartners() throws IOException {
		// Layout da Receita: CNPJ básico; razão social; natureza; qualificação; capital social; porte; ente federativo
		zip("Empresas0.zip", "K3241.K03200Y0.D60913.EMPRECSV",
				row("12345678", "ALFA SOLUÇÕES EM TECNOLOGIA LTDA", "2062", "49", "500000,00", "05", ""),
				row("23456789", "BETA SOFTWARE LTDA", "2062", "49", "250000,00", "03", ""),
				// microempresa e capital baixo ficam de fora
				row("34567890", "JOAO DA SILVA 12345678900", "2135", "50", "1000000,00", "01", ""),
				row("45678901", "GAMA TI LTDA", "2062", "49", "5000,00", "05", ""),
				// CNAE de outro ramo e matriz baixada
				row("56789012", "DELTA PADARIA LTDA", "2062", "49", "900000,00", "05", ""),
				row("67890123", "EPSILON SISTEMAS LTDA", "2062", "49", "900000,00", "05", ""));
		zip("Estabelecimentos0.zip", "K3241.K03200Y0.D60913.ESTABELE",
				establishment("12345678", "0001", "1", "ALFA TECH", "02", "6204000", "SP"),
				// filial da Alfa com outro CNAE não muda nada
				establishment("12345678", "0002", "2", "", "02", "6201501", "RJ"),
				establishment("23456789", "0001", "1", "", "02", "6201501", "MG"),
				establishment("34567890", "0001", "1", "", "02", "6204000", "SP"),
				establishment("45678901", "0001", "1", "", "02", "6209100", "SP"),
				establishment("56789012", "0001", "1", "", "02", "1091102", "SP"),
				establishment("67890123", "0001", "1", "", "08", "6204000", "SP"));
		// O arquivo de sócios nem é um zip válido: se fosse aberto, a leitura falharia.
		Files.writeString(this.directory.resolve("Socios0.zip"), "CPF 123.456.789-00 MARIA SOCIA");

		NichesProperties properties = new NichesProperties(new NichesProperties.Bcb("http://bcb", false, "-"),
				new NichesProperties.Cnpj(this.directory.toString(), List.of("6204000", "6201501", "6209100"),
						new BigDecimal("100000")), "-");
		var source = new CnpjOpenDataSeedSource(properties);
		List<SeedRecord> records = new ArrayList<>();
		source.fetch(records::add);

		assertThat(source.unavailableReason()).isNull();
		assertThat(records).containsExactly(
				new SeedRecord("Alfa Tech", "12345678", "CNAE 6204-0/00", CompanyKind.CONSULTORIA_TI, null, "SP"),
				new SeedRecord("Beta Software Ltda", "23456789", "CNAE 6201-5/01", CompanyKind.CONSULTORIA_TI, null, "MG"));
		assertThat(records).noneMatch(record -> record.name().contains("Socia") || record.name().contains("Joao"));
	}

	@Test
	void parsesTheQuotedSemicolonLayout() {
		assertThat(CnpjOpenDataSeedSource.split("\"1\";\"A; B\";\"\";\"C \"\"D\"\"\""))
			.containsExactly("1", "A; B", "", "C \"D\"");
		assertThat(CnpjOpenDataSeedSource.capital("1.250.000,50")).isEqualByComparingTo("1250000.50");
		assertThat(CnpjOpenDataSeedSource.formatCnae("6209100")).isEqualTo("6209-1/00");
	}

	@Test
	void withoutTheDirectoryTheSourceIsUnavailable() {
		var source = new CnpjOpenDataSeedSource(new NichesProperties(new NichesProperties.Bcb("http://bcb", false, "-"),
				new NichesProperties.Cnpj(null, List.of("6204000"), BigDecimal.ZERO), "-"));

		assertThat(source.unavailableReason()).contains("JOBHUNTER_CNPJ_DIRECTORY");
	}

	@Test
	void namesFromPublicRegistriesBecomeReadable() {
		assertThat(NameFormatter.pretty("BANCO ITAÚ VEÍCULOS S.A.")).isEqualTo("Banco Itaú Veículos S.A.");
		assertThat(NameFormatter.pretty("XP INVESTIMENTOS CCTVM S/A")).isEqualTo("XP Investimentos CCTVM S/A");
		assertThat(NameFormatter.pretty("COOPERATIVA DE CRÉDITO SICOOB-CREDIP")).isEqualTo("Cooperativa de Crédito Sicoob-Credip");
		assertThat(NameFormatter.pretty("Nubank")).isEqualTo("Nubank");
		assertThat(NameFormatter.pretty("ABN AMRO CLEARING CORRETORA LTDA.")).isEqualTo("Abn Amro Clearing Corretora Ltda.");
		assertThat(NameFormatter.website("WWW.BANCOFIAT.COM.BR")).isEqualTo("https://www.bancofiat.com.br");
		assertThat(Set.of(NameFormatter.pretty("FFCRED SOCIEDADE DE CRÉDITO DIRETO S.A.."))).containsExactly(
				"Ffcred Sociedade de Crédito Direto S.A.");
	}

	private void zip(String name, String entry, String... lines) throws IOException {
		try (OutputStream file = Files.newOutputStream(this.directory.resolve(name));
				ZipOutputStream zip = new ZipOutputStream(file)) {
			zip.putNextEntry(new ZipEntry(entry));
			zip.write(String.join("\n", lines).getBytes(StandardCharsets.ISO_8859_1));
			zip.closeEntry();
		}
	}

	private static String row(String... fields) {
		return "\"" + String.join("\";\"", fields) + "\"";
	}

	/** Os 30 campos do estabelecimento; só matriz, nome fantasia, situação, CNAE e UF importam aqui. */
	private static String establishment(String root, String order, String headquarters, String tradeName, String status,
			String cnae, String state) {
		String[] fields = new String[30];
		java.util.Arrays.fill(fields, "");
		fields[0] = root;
		fields[1] = order;
		fields[2] = "00";
		fields[3] = headquarters;
		fields[4] = tradeName;
		fields[5] = status;
		fields[11] = cnae;
		fields[19] = state;
		fields[27] = "contato@example.com";
		return row(fields);
	}

}
