package dev.laranja.jobhunter.jobs;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Vaga como chega de uma fonte de coleta, antes da normalização.
 *
 * @param externalId identificador da vaga na fonte (por exemplo, o id do LinkedIn); evita duplicatas mesmo quando
 * o título muda
 */
public record RawJob(JobOrigin origin, @Nullable String externalId, String title, String company,
		@Nullable String location, @Nullable WorkModel workModel, @Nullable String salaryRange, @Nullable String url,
		@Nullable String description) {

	/** Infere o modelo de trabalho pelo texto da localidade quando a fonte não informa. */
	public WorkModel resolvedWorkModel() {
		if (this.workModel != null) {
			return this.workModel;
		}
		String place = (this.location == null) ? "" : this.location.toLowerCase(Locale.ROOT);
		if (place.contains("remot") || place.contains("remote")) {
			return WorkModel.REMOTO;
		}
		if (place.contains("híbrid") || place.contains("hibrid") || place.contains("hybrid")) {
			return WorkModel.HIBRIDO;
		}
		if (place.contains("presencial") || place.contains("on-site") || place.contains("on site")) {
			return WorkModel.PRESENCIAL;
		}
		return WorkModel.NAO_INFORMADO;
	}

}
