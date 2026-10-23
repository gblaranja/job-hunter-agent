package dev.laranja.jobhunter.shared.storage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.ContentDisposition;

/** Implementação sobre o AWS SDK v2; serve para RustFS no ambiente local e para S3 ou R2 em produção. */
class S3ObjectStorage implements ObjectStorage, DisposableBean {

	private final S3Client client;

	private final S3Presigner presigner;

	private final String bucket;

	S3ObjectStorage(StorageProperties properties) {
		var credentials = StaticCredentialsProvider
			.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
		var region = Region.of(properties.region());
		var s3Configuration = S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build();
		this.client = S3Client.builder()
			.endpointOverride(properties.endpoint())
			.region(region)
			.credentialsProvider(credentials)
			.serviceConfiguration(s3Configuration)
			// Servidores S3 compatíveis nem sempre aceitam os checksums que o SDK envia por padrão.
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
			.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
			.build();
		this.presigner = S3Presigner.builder()
			.endpointOverride(properties.presignEndpoint())
			.region(region)
			.credentialsProvider(credentials)
			.serviceConfiguration(s3Configuration)
			.build();
		this.bucket = properties.bucket();
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		this.client.putObject(request -> request.bucket(this.bucket).key(key).contentType(contentType),
				RequestBody.fromBytes(content));
	}

	@Override
	public void delete(String key) {
		this.client.deleteObject(request -> request.bucket(this.bucket).key(key));
	}

	@Override
	public URI presignedDownload(String key, String fileName, Duration ttl) {
		String disposition = ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString();
		var presigned = this.presigner.presignGetObject(request -> request.signatureDuration(ttl)
			.getObjectRequest(get -> get.bucket(this.bucket).key(key).responseContentDisposition(disposition)));
		return URI.create(presigned.url().toString());
	}

	void createBucketIfMissing() {
		try {
			this.client.headBucket(request -> request.bucket(this.bucket));
		}
		catch (S3Exception ex) {
			if (ex.statusCode() != 404) {
				throw ex;
			}
			this.client.createBucket(request -> request.bucket(this.bucket));
		}
	}

	@Override
	public void destroy() {
		this.presigner.close();
		this.client.close();
	}

}
