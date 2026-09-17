package org.example.festivalservice.domain.festival;

/**
 * 페스티벌 전체에 적용되는 구역 배치 방식. 구역(=SEATED 티켓타입) 하나하나가 아니라
 * 페스티벌 단위로 하나만 정해진다 — 같은 행사 안에서 전면형/중앙형이 섞이지 않는다.
 */
public enum FestivalStageLayout {
    FRONT_STAGE,
    CENTER_STAGE
}