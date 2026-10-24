package dev.laranja.jobhunter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Grava o contrato OpenAPI num arquivo sem subir a aplicação contra o banco de desenvolvimento, para gerar o schema
 * do frontend: {@code ./mvnw test -Dtest=OpenApiExportTests -Dopenapi.export=../frontend/openapi.json} e depois
 * {@code npx openapi-typescript openapi.json -o src/api/schema.d.ts}.
 */
@EnabledIfSystemProperty(named = "openapi.export", matches = ".+")
class OpenApiExportTests extends IntegrationTest {

	@Test
	void exportsTheContract() throws Exception {
		String contract = this.mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		Files.writeString(Path.of(System.getProperty("openapi.export")), contract);
	}

}
