package dev.laranja.jobhunter.shared.web;

import java.util.List;

/** Página de resultados com um formato estável para o frontend. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
