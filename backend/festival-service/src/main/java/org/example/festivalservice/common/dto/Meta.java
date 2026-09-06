package org.example.festivalservice.common.dto;

import org.springframework.data.domain.Page;

public record Meta(PageMeta pagination) {

    public static Meta of(Page<?> page) {
        return new Meta(PageMeta.from(page));
    }
}
