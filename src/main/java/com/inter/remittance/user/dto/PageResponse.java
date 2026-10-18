package com.inter.remittance.user.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A page of results")
public record PageResponse<T>(
		@Schema(description = "Items of the current page") List<T> content,
		@Schema(description = "Zero-based page index", example = "0") int page,
		@Schema(description = "Requested page size", example = "20") int size,
		@Schema(description = "Total number of items matching the filters", example = "42") long totalElements,
		@Schema(description = "Total number of pages", example = "3") int totalPages) {

	public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> converter) {
		return new PageResponse<>(
				page.getContent().stream().map(converter).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}
