package dev.laranja.jobhunter.shared.storage;

import java.net.URI;
import java.time.Instant;

/** URL pré-assinada de download e o instante em que ela deixa de valer. */
public record DownloadLink(URI url, Instant expiresAt) {
}
