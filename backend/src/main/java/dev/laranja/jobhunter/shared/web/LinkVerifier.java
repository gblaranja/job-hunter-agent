package dev.laranja.jobhunter.shared.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Verifica se a página de uma vaga ainda está no ar. Usa GET (muitos sites respondem mal a HEAD), segue
 * redirecionamentos e lê só o começo da página, procurando avisos de vaga encerrada. Páginas que exigem login ou
 * bloqueiam robôs (o LinkedIn responde 999) ficam como indeterminadas, para não marcar uma vaga como removida
 * por engano.
 */
@Component
public class LinkVerifier {

	private static final int MAX_BYTES = 300_000;

	private static final Pattern CLOSED = Pattern.compile("""
			vaga encerrada|vaga foi encerrada|esta vaga nao esta mais disponivel|vaga nao esta mais disponivel|\
			inscricoes encerradas|processo seletivo encerrado|no longer accepting applications|nao aceita mais candidaturas|\
			job is no longer available|this job has expired|position has been filled|job posting is closed""");

	private final HttpClient http;

	public LinkVerifier() {
		this(HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10))
			.build());
	}

	LinkVerifier(HttpClient http) {
		this.http = http;
	}

	public Result check(String url) {
		HttpRequest request;
		try {
			request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) JobHunterAgent/1.0")
				.header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
				.GET()
				.build();
		}
		catch (IllegalArgumentException ex) {
			return Result.INDETERMINADO;
		}
		try {
			HttpResponse<InputStream> response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			int status = response.statusCode();
			String body;
			try (InputStream input = response.body()) {
				body = new String(input.readNBytes(MAX_BYTES), StandardCharsets.UTF_8);
			}
			if (status == 404 || status == 410) {
				return Result.REMOVIDO;
			}
			if (status >= 200 && status < 300) {
				String finalUrl = response.uri().toString().toLowerCase(Locale.ROOT);
				if (finalUrl.contains("/login") || finalUrl.contains("authwall") || finalUrl.contains("signin")) {
					return Result.INDETERMINADO;
				}
				String text = Normalizer.normalize(body.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
					.replaceAll("\\p{M}+", "");
				return CLOSED.matcher(text).find() ? Result.REMOVIDO : Result.ATIVO;
			}
			return Result.INDETERMINADO;
		}
		catch (IOException ex) {
			return Result.INDETERMINADO;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return Result.INDETERMINADO;
		}
	}

	public enum Result {

		ATIVO, REMOVIDO, INDETERMINADO

	}

}
