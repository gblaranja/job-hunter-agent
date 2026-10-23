package dev.laranja.jobhunter.shared.storage;

import java.net.URI;
import java.time.Duration;

/** Storage de objetos privado (S3 compatível). Os arquivos só saem por URL pré-assinada de curta duração. */
public interface ObjectStorage {

	void put(String key, byte[] content, String contentType);

	byte[] get(String key);

	void delete(String key);

	URI presignedDownload(String key, String fileName, Duration ttl);

}
