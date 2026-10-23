package dev.laranja.jobhunter.shared.storage;

import java.net.URI;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Conexão com o storage S3 compatível.
 *
 * @param endpoint endereço usado pelo backend
 * @param publicEndpoint endereço que o navegador enxerga, usado nas URLs pré-assinadas (no docker-compose é
 * diferente do interno)
 * @param createBucketIfMissing cria o bucket na subida; só para desenvolvimento e testes
 */
@Validated
@ConfigurationProperties("jobhunter.storage")
public record StorageProperties(@NotNull URI endpoint, URI publicEndpoint, @NotBlank String region,
		@NotBlank String accessKey, @NotBlank String secretKey, @NotBlank String bucket, boolean pathStyle,
		boolean createBucketIfMissing) {

	public URI presignEndpoint() {
		return (this.publicEndpoint != null) ? this.publicEndpoint : this.endpoint;
	}

}
