package dev.laranja.jobhunter.network.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.laranja.jobhunter.network.ContactCommands.Connection;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.Nullable;

/**
 * Lê o {@code Connections.csv} do export de dados do LinkedIn. O arquivo começa com algumas linhas de aviso
 * ("Notes:") antes do cabeçalho {@code First Name,Last Name,URL,Email Address,Company,Position,Connected On}; tudo
 * antes do cabeçalho é ignorado. Linhas sem link do perfil (contas apagadas) ficam de fora.
 */
public final class LinkedInConnectionsCsv {

	private static final String HEADER_START = "first name,last name,url";

	private static final DateTimeFormatter CONNECTED_ON = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private LinkedInConnectionsCsv() {
	}

	public static List<Connection> parse(InputStream input) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			StringBuilder content = new StringBuilder();
			boolean header = false;
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				String clean = line.replace("﻿", "");
				if (!header && clean.strip().toLowerCase(Locale.ROOT).startsWith(HEADER_START)) {
					header = true;
				}
				if (header) {
					content.append(clean).append('\n');
				}
			}
			if (!header) {
				throw new BusinessRuleException(
						"Não encontrei o cabeçalho do Connections.csv (First Name, Last Name, URL...). Envie o arquivo do export do LinkedIn.");
			}
			return read(content.toString());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static List<Connection> read(String csv) throws IOException {
		CSVFormat format = CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.setIgnoreEmptyLines(true)
			.setTrim(true)
			.setAllowMissingColumnNames(true)
			.get();
		List<Connection> connections = new ArrayList<>();
		for (CSVRecord record : format.parse(new StringReader(csv))) {
			String url = column(record, "URL");
			String name = ((nullToEmpty(column(record, "First Name")) + " " + nullToEmpty(column(record, "Last Name")))).strip();
			if (url == null || name.isEmpty()) {
				continue;
			}
			connections.add(new Connection(name, url, column(record, "Email Address"), column(record, "Company"),
					column(record, "Position"), date(column(record, "Connected On"))));
		}
		return connections;
	}

	private static @Nullable String column(CSVRecord record, String name) {
		if (!record.isMapped(name) || !record.isSet(name)) {
			return null;
		}
		String value = record.get(name);
		return (value == null || value.isBlank()) ? null : value.strip();
	}

	private static String nullToEmpty(@Nullable String value) {
		return (value == null) ? "" : value;
	}

	// "15 Mar 2024" no export em inglês; outros formatos ficam sem data.
	private static @Nullable LocalDate date(@Nullable String value) {
		if (value == null) {
			return null;
		}
		try {
			return LocalDate.parse(value, CONNECTED_ON);
		}
		catch (DateTimeParseException ex) {
			try {
				return LocalDate.parse(value);
			}
			catch (DateTimeParseException ignored) {
				return null;
			}
		}
	}

}
