package org.example.festivalservice.common.dto;

import org.springframework.data.domain.Page;

public record PageMeta(int page, int size, long totalItems, int totalPages, boolean hasNext, boolean hasPrev) {

    public static PageMeta from(Page<?> page) {
        return new PageMeta(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
