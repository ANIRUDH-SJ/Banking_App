package com.netbanking.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Objects;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public PagedResponse {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    public static <T> PagedResponse<T> from(Page<T> page) {
        Objects.requireNonNull(page, "page");
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
