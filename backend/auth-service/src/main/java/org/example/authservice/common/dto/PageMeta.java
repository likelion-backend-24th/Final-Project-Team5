package org.example.authservice.common.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class PageMeta {
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrev;

    public static final PageMeta from(Page<?> page) {
        return PageMeta.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalItems(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrev(page.hasPrevious())
                .build();
    }
}
