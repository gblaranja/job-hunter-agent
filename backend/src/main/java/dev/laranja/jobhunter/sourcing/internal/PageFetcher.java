package dev.laranja.jobhunter.sourcing.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Baixa uma página informada pelo usuário sem abrir caminho para a rede interna (SSRF): só http e https, nenhum
 * endereço privado, de loopback, link-local ou multicast (também depois de cada redirecionamento), até 10 segundos e
 * 2 MB.
 */
@Component
class PageFetcher {

	static final int MAX_BYTES = 2 * 1024 * 1024;

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final int MAX_REDIRECTS = 4;

	private final HttpClient http = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NEVER)
		.connectTimeout(TIMEOUT)
		.build();

	private final boolean allowPrivateHosts;

	PageFetcher(SourcingProperties properties) {
		this.allowPrivateHosts = properties.jobImport().allowPrivateHosts();
	}

	/** O HTML da página; 400 para link inválido ou bloqueado, 422 quando a página não responde. */
	String fetch(String url) {
		URI uri = validate(url);
		for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
			HttpResponse<InputStream> response = send(uri);
			int status = response.statusCode();
			if (status >= 300 && status < 400) {
				Optional<String> location = response.headers().firstValue("Location");
				close(response);
				if (location.isEmpty()) {
					throw unprocessable("A página redirecionou sem dizer para onde.");
				}
				uri = validate(uri.resolve(location.get()).toString());
				continue;
			}
			if (status < 200 || status >= 300) {
				close(response);
				throw unprocessable("A página respondeu HTTP " + status + ".");
			}
			try (InputStream body = response.body()) {
				byte[] bytes = body.readNBytes(MAX_BYTES);
				return new String(bytes, charset(response));
			}
			catch (IOException ex) {
				throw unprocessable("Não consegui ler a página: " + ex.getMessage());
			}
		}
		throw unprocessable("A página redirecionou vezes demais.");
	}

	/** Só a forma do link (http ou https com host), sem resolver o endereço. */
	URI validateSyntax(String url) {
		URI uri;
		try {
			uri = URI.create(url.strip());
		}
		catch (IllegalArgumentException ex) {
			throw badRequest("Link inválido.");
		}
		String scheme = (uri.getScheme() == null) ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
			throw badRequest("Use um link http ou https.");
		}
		return uri;
	}

	URI validate(String url) {
		URI uri;
		try {
			uri = URI.create(url.strip());
		}
		catch (IllegalArgumentException ex) {
			throw badRequest("Link inválido.");
		}
		String scheme = (uri.getScheme() == null) ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
			throw badRequest("Use um link http ou https.");
		}
		if (!this.allowPrivateHosts) {
			try {
				for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
					if (isInternal(address)) {
						throw badRequest("Esse link aponta para a rede interna e não pode ser importado.");
					}
				}
			}
			catch (UnknownHostException ex) {
				throw badRequest("Não encontrei o endereço " + uri.getHost() + ".");
			}
		}
		return uri;
	}

	/** Loopback, rede privada (10/8, 172.16/12, 192.168/16, fc00::/7), CGNAT, link-local, multicast e 0.0.0.0. */
	static boolean isInternal(InetAddress address) {
		if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
				|| address.isAnyLocalAddress() || address.isMulticastAddress()) {
			return true;
		}
		byte[] bytes = address.getAddress();
		if (address instanceof Inet6Address) {
			return (bytes[0] & 0xfe) == 0xfc;
		}
		int first = bytes[0] & 0xff;
		int second = bytes[1] & 0xff;
		return (first == 100 && second >= 64 && second <= 127) || first == 0;
	}

	private HttpResponse<InputStream> send(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(TIMEOUT)
			.header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) JobHunterAgent/1.0")
			.header("Accept", "text/html,application/xhtml+xml")
			.header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
			.GET()
			.build();
		try {
			return this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
		}
		catch (IOException ex) {
			throw unprocessable("A página não respondeu: " + ex.getMessage());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw unprocessable("A busca da página foi interrompida.");
		}
	}

	private static Charset charset(HttpResponse<?> response) {
		return response.headers()
			.firstValue("Content-Type")
			.map(type -> type.toLowerCase(Locale.ROOT))
			.filter(type -> type.contains("charset="))
			.map(type -> type.substring(type.indexOf("charset=") + 8).replace("\"", "").strip())
			.flatMap(name -> {
				try {
					return Optional.of(Charset.forName(name));
				}
				catch (RuntimeException ex) {
					return Optional.empty();
				}
			})
			.orElse(StandardCharsets.UTF_8);
	}

	private static void close(HttpResponse<InputStream> response) {
		try {
			response.body().close();
		}
		catch (IOException ignored) {
			// nada a fazer
		}
	}

	static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	static ResponseStatusException unprocessable(String message) {
		return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message);
	}

}
