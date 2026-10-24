package dev.laranja.jobhunter.niches.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.laranja.jobhunter.companies.CompanyKind;
import dev.laranja.jobhunter.niches.NichesProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Base mensal de CNPJ da Receita Federal (dados abertos), lida em streaming dos zips numa pasta local: CSV em latin1,
 * separado por ";". Entram as matrizes ativas com CNAE principal de TI, porte diferente de ME e capital social acima
 * do mínimo. Só os arquivos de empresas e de estabelecimentos são abertos; os de sócios nunca são lidos (LGPD), e dos
 * estabelecimentos só saem nome fantasia, CNAE e UF.
 */
@Component
class CnpjOpenDataSeedSource implements CompanySeedSource {

	private static final Logger log = LoggerFactory.getLogger(CnpjOpenDataSeedSource.class);

	private static final Pattern COMPANIES_FILE = Pattern.compile("(?i)empresas\\d*\\.zip");

	private static final Pattern ESTABLISHMENTS_FILE = Pattern.compile("(?i)estabelecimentos\\d*\\.zip");

	/** Porte "01" é microempresa; "00" (não informado), "03" (EPP) e "05" (demais) entram. */
	private static final String MICRO = "01";

	private static final String ACTIVE = "02";

	private static final String HEADQUARTERS = "1";

	private final NichesProperties.Cnpj properties;

	CnpjOpenDataSeedSource(NichesProperties properties) {
		this.properties = properties.cnpj();
	}

	@Override
	public String key() {
		return "cnpj";
	}

	@Override
	public String label() {
		return "Receita Federal: consultorias e software (CNAE de TI)";
	}

	@Override
	public @Nullable String unavailableReason() {
		if (this.properties.directory() == null || this.properties.directory().isBlank()) {
			return "Informe a pasta com os zips da base de CNPJ em JOBHUNTER_CNPJ_DIRECTORY.";
		}
		if (!Files.isDirectory(Path.of(this.properties.directory()))) {
			return "A pasta " + this.properties.directory() + " não existe.";
		}
		return null;
	}

	@Override
	public void fetch(Consumer<SeedRecord> sink) {
		Path directory = Path.of(this.properties.directory());
		read(files(directory, COMPANIES_FILE), files(directory, ESTABLISHMENTS_FILE), Set.copyOf(this.properties.cnaes()),
				this.properties.minCapital(), sink);
	}

	/**
	 * Três passadas, para caber em memória: as raízes elegíveis pelo porte e capital (um vetor de inteiros), as
	 * matrizes ativas de TI dessas raízes e, por fim, a razão social de cada uma.
	 */
	static void read(List<Path> companyFiles, List<Path> establishmentFiles, Set<String> cnaes, BigDecimal minCapital,
			Consumer<SeedRecord> sink) {
		IntSet eligible = new IntSet();
		forEachRow(companyFiles, row -> {
			if (row.length > 5 && !MICRO.equals(row[5]) && capital(row[4]).compareTo(minCapital) >= 0) {
				eligible.add(root(row[0]));
			}
		});
		eligible.seal();
		Map<Integer, Establishment> establishments = new HashMap<>();
		forEachRow(establishmentFiles, row -> {
			if (row.length > 19 && HEADQUARTERS.equals(row[3]) && ACTIVE.equals(row[5]) && cnaes.contains(row[11])
					&& eligible.contains(root(row[0]))) {
				establishments.put(root(row[0]), new Establishment(row[4].strip(), row[11], row[19].strip()));
			}
		});
		log.info("Base de CNPJ: {} raízes elegíveis, {} matrizes de TI.", eligible.size(), establishments.size());
		forEachRow(companyFiles, row -> {
			Establishment establishment = establishments.remove(root(row[0]));
			if (establishment == null) {
				return;
			}
			String name = establishment.tradeName().isBlank() ? row[1] : establishment.tradeName();
			sink.accept(new SeedRecord(NameFormatter.pretty(name), row[0].strip(), "CNAE " + formatCnae(establishment.cnae()),
					CompanyKind.CONSULTORIA_TI, null, establishment.state().isBlank() ? null : establishment.state()));
		});
	}

	static List<Path> files(Path directory, Pattern name) {
		try (Stream<Path> paths = Files.list(directory)) {
			return paths.filter(path -> name.matcher(path.getFileName().toString()).matches()).sorted().toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void forEachRow(List<Path> zips, Consumer<String[]> consumer) {
		for (Path zip : zips) {
			try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
				ZipEntry entry;
				while ((entry = input.getNextEntry()) != null) {
					if (entry.isDirectory()) {
						continue;
					}
					BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.ISO_8859_1));
					String line;
					while ((line = reader.readLine()) != null) {
						String[] row = split(line);
						if (row.length > 0 && row[0].length() == 8) {
							consumer.accept(row);
						}
					}
				}
			}
			catch (IOException ex) {
				throw new UncheckedIOException("Falha ao ler " + zip.getFileName(), ex);
			}
		}
	}

	/** Uma linha do CSV da Receita: campos entre aspas separados por ";". */
	static String[] split(String line) {
		List<String> fields = new ArrayList<>(32);
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '"') {
				if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				}
				else {
					quoted = !quoted;
				}
			}
			else if (c == ';' && !quoted) {
				fields.add(field.toString());
				field.setLength(0);
			}
			else {
				field.append(c);
			}
		}
		fields.add(field.toString());
		return fields.toArray(String[]::new);
	}

	static BigDecimal capital(String value) {
		try {
			return new BigDecimal(value.strip().replace(".", "").replace(',', '.'));
		}
		catch (NumberFormatException ex) {
			return BigDecimal.ZERO;
		}
	}

	private static int root(String cnpjRoot) {
		try {
			return Integer.parseInt(cnpjRoot.strip());
		}
		catch (NumberFormatException ex) {
			return -1;
		}
	}

	/** 6204000 vira 6204-0/00. */
	static String formatCnae(String cnae) {
		return (cnae.length() == 7)
				? String.format(Locale.ROOT, "%s-%s/%s", cnae.substring(0, 4), cnae.substring(4, 5), cnae.substring(5))
				: cnae;
	}

	private record Establishment(String tradeName, String cnae, String state) {
	}

	/** Conjunto de inteiros num vetor ordenado: milhões de raízes de CNPJ em poucas dezenas de MB. */
	static final class IntSet {

		private int[] values = new int[1024];

		private int size;

		void add(int value) {
			if (this.size == this.values.length) {
				this.values = Arrays.copyOf(this.values, this.values.length * 2);
			}
			this.values[this.size++] = value;
		}

		void seal() {
			Arrays.sort(this.values, 0, this.size);
		}

		boolean contains(int value) {
			return Arrays.binarySearch(this.values, 0, this.size, value) >= 0;
		}

		int size() {
			return this.size;
		}

	}

}
