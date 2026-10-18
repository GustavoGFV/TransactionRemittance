package com.inter.remittance.user.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> converter) {
		return new PageResponse<>(
				page.getContent().stream().map(converter).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}
