package org.example.reservationservice.infrastructure.festival.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** festival-service의 공개 API(/api/festivals/**)가 감싸서 내려주는 팀 공통 응답 봉투. data만 꺼내 쓴다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FestivalApiEnvelope<T>(boolean success, T data) {
}
