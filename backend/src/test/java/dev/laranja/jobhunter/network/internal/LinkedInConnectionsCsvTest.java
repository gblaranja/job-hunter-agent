package dev.laranja.jobhunter.network.internal;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import dev.laranja.jobhunter.network.ContactCommands.Connection;
import dev.laranja.jobhunter.shared.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkedInConnectionsCsvTest {

	@Test
	void skipsTheNotesBeforeTheHeaderAndReadsEachConnection() throws Exception {
		List<Connection> connections;
		try (InputStream input = getClass().getResourceAsStream("/fixtures/linkedin/Connections.csv")) {
			connections = LinkedInConnectionsCsv.parse(input);
		}

		assertThat(connections).hasSize(3);
		assertThat(connections.getFirst()).isEqualTo(new Connection("Ana Lima", "https://www.linkedin.com/in/ana-lima", null,
				"Banco Exemplo S.A.", "Engineering Manager", LocalDate.of(2024, 3, 15)));
		assertThat(connections.get(1).company()).isEqualTo("Fintech Exemplo, Pagamentos");
		assertThat(connections.get(1).email()).isEqualTo("bruno@example.com");
		assertThat(connections.get(2).company()).isNull();
	}

	@Test
	void acceptsAFileWithoutNotesAndWithByteOrderMark() {
		String csv = "﻿First Name,Last Name,URL,Email Address,Company,Position,Connected On\n"
				+ "Ana,Lima,https://www.linkedin.com/in/ana-lima,,Banco Exemplo,Gerente,2024-03-15\n";

		List<Connection> connections = LinkedInConnectionsCsv.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

		assertThat(connections).singleElement().extracting(Connection::connectedOn).isEqualTo(LocalDate.of(2024, 3, 15));
	}

	@Test
	void rejectsAFileThatIsNotTheConnectionsExport() {
		InputStream input = new ByteArrayInputStream("nome;empresa\nAna;Banco\n".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> LinkedInConnectionsCsv.parse(input)).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void normalizesProfileLinks() {
		assertThat(ProfileUrls.normalize("http://br.linkedin.com/in/Ana-Lima/?trk=x"))
			.isEqualTo("https://www.linkedin.com/in/ana-lima");
		assertThat(ProfileUrls.normalize(" ")).isNull();
	}

}
