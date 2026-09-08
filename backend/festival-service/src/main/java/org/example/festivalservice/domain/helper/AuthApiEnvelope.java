package org.example.festivalservice.domain.helper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** auth-service의 내부 API가 감싸서 내려주는 팀 공통 응답 봉투. data만 꺼내 쓴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthApiEnvelope<T>(boolean success, T data) {
}
