package dev.laranja.jobhunter.shared.storage;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

	@Bean
	S3ObjectStorage objectStorage(StorageProperties properties) {
		return new S3ObjectStorage(properties);
	}

	@Bean
	ApplicationRunner createBucketIfMissing(StorageProperties properties, S3ObjectStorage storage) {
		return args -> {
			if (properties.createBucketIfMissing()) {
				storage.createBucketIfMissing();
			}
		};
	}

}
