package org.example.authservice.common.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Meta {
    private PageMeta pagination;
}
