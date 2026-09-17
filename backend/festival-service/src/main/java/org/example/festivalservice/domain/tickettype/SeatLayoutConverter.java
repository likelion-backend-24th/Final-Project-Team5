package org.example.festivalservice.domain.tickettype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * SeatLayout ↔ JSON 문자열 변환. MySQL/H2 둘 다 지원되는 평범한 TEXT 컬럼에 저장하기 위해
 * (JSON 네이티브 타입 대신) 문자열 변환 방식을 쓴다.
 */
@Converter
public class SeatLayoutConverter implements AttributeConverter<SeatLayout, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(SeatLayout attribute) {
        if (attribute == null) return null;
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("좌석 배치 정보를 JSON으로 변환할 수 없습니다.", e);
        }
    }

    @Override
    public SeatLayout convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            return OBJECT_MAPPER.readValue(dbData, SeatLayout.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("좌석 배치 정보를 읽을 수 없습니다.", e);
        }
    }
}