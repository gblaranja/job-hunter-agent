package dev.laranja.jobhunter;

import java.util.function.Supplier;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String STORAGE_ACCESS_KEY = "test-access";

	static final String STORAGE_SECRET_KEY = "test-secret-key";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	@Bean
	GenericContainer<?> objectStorageContainer() {
		return new GenericContainer<>(DockerImageName.parse("rustfs/rustfs:1.0.3"))
			.withEnv("RUSTFS_ACCESS_KEY", STORAGE_ACCESS_KEY)
			.withEnv("RUSTFS_SECRET_KEY", STORAGE_SECRET_KEY)
			.withExposedPorts(9000)
			.waitingFor(Wait.forHttp("/health").forPort(9000));
	}

	@Bean
	DynamicPropertyRegistrar objectStorageProperties(GenericContainer<?> objectStorageContainer) {
		return registry -> {
			Supplier<Object> endpoint = () -> "http://" + objectStorageContainer.getHost() + ":"
					+ objectStorageContainer.getMappedPort(9000);
			registry.add("jobhunter.storage.endpoint", endpoint);
			registry.add("jobhunter.storage.public-endpoint", endpoint);
			registry.add("jobhunter.storage.access-key", () -> STORAGE_ACCESS_KEY);
			registry.add("jobhunter.storage.secret-key", () -> STORAGE_SECRET_KEY);
			registry.add("jobhunter.storage.create-bucket-if-missing", () -> "true");
		};
	}

}
