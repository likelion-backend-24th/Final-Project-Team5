package org.example.festivalservice.domain.festival;

import java.util.Map;
import java.util.Set;

/**
 * FestivalRegion ↔ 한글 지역명 매핑. 카카오 장소 검색 쿼리를 지역으로 좁히거나(짧은 이름),
 * 검색 결과 주소가 실제로 그 지역인지 검증할 때(신/구 명칭 다 허용) 쓴다.
 * 프론트 festivalApi.js의 FESTIVAL_REGIONS와 같은 값 세트를 유지해야 한다.
 */
final class FestivalRegionNames {

    private FestivalRegionNames() {
    }

    //검색 쿼리에 붙일 짧은 지역명 — 카카오 주소 문자열이 보통 이 형태로 시작한다.
    private static final Map<FestivalRegion, String> SHORT_LABEL = Map.ofEntries(
            Map.entry(FestivalRegion.SEOUL, "서울"),
            Map.entry(FestivalRegion.BUSAN, "부산"),
            Map.entry(FestivalRegion.DAEGU, "대구"),
            Map.entry(FestivalRegion.INCHEON, "인천"),
            Map.entry(FestivalRegion.GWANGJU, "광주"),
            Map.entry(FestivalRegion.DAEJEON, "대전"),
            Map.entry(FestivalRegion.ULSAN, "울산"),
            Map.entry(FestivalRegion.SEJONG, "세종"),
            Map.entry(FestivalRegion.GYEONGGI, "경기"),
            Map.entry(FestivalRegion.GANGWON, "강원"),
            Map.entry(FestivalRegion.CHUNGBUK, "충북"),
            Map.entry(FestivalRegion.CHUNGNAM, "충남"),
            Map.entry(FestivalRegion.JEONBUK, "전북"),
            Map.entry(FestivalRegion.JEONNAM, "전남"),
            Map.entry(FestivalRegion.GYEONGBUK, "경북"),
            Map.entry(FestivalRegion.GYEONGNAM, "경남"),
            Map.entry(FestivalRegion.JEJU, "제주"));

    //검색 결과 주소가 이 지역이 맞는지 검증할 때 쓰는 허용 명칭들 — 신/구 행정구역명을 다 받는다
    //(강원도→강원특별자치도, 전라북도→전북특별자치도 개편 반영).
    private static final Map<FestivalRegion, Set<String>> VERIFY_TOKENS = Map.ofEntries(
            Map.entry(FestivalRegion.SEOUL, Set.of("서울", "서울특별시")),
            Map.entry(FestivalRegion.BUSAN, Set.of("부산", "부산광역시")),
            Map.entry(FestivalRegion.DAEGU, Set.of("대구", "대구광역시")),
            Map.entry(FestivalRegion.INCHEON, Set.of("인천", "인천광역시")),
            Map.entry(FestivalRegion.GWANGJU, Set.of("광주", "광주광역시")),
            Map.entry(FestivalRegion.DAEJEON, Set.of("대전", "대전광역시")),
            Map.entry(FestivalRegion.ULSAN, Set.of("울산", "울산광역시")),
            Map.entry(FestivalRegion.SEJONG, Set.of("세종", "세종특별자치시")),
            Map.entry(FestivalRegion.GYEONGGI, Set.of("경기", "경기도")),
            Map.entry(FestivalRegion.GANGWON, Set.of("강원", "강원도", "강원특별자치도")),
            Map.entry(FestivalRegion.CHUNGBUK, Set.of("충북", "충청북도")),
            Map.entry(FestivalRegion.CHUNGNAM, Set.of("충남", "충청남도")),
            Map.entry(FestivalRegion.JEONBUK, Set.of("전북", "전라북도", "전북특별자치도")),
            Map.entry(FestivalRegion.JEONNAM, Set.of("전남", "전라남도")),
            Map.entry(FestivalRegion.GYEONGBUK, Set.of("경북", "경상북도")),
            Map.entry(FestivalRegion.GYEONGNAM, Set.of("경남", "경상남도")),
            Map.entry(FestivalRegion.JEJU, Set.of("제주", "제주도", "제주특별자치도")));

    static String shortLabel(FestivalRegion region) {
        return SHORT_LABEL.getOrDefault(region, "");
    }

    //addressName의 첫 토큰(시/도 부분)이 이 region의 허용 명칭 중 하나와 같은지 본다.
    static boolean matchesRegion(FestivalRegion region, String addressName) {
        if (region == null || addressName == null || addressName.isBlank()) return false;
        String firstToken = addressName.trim().split("\\s+")[0];
        return VERIFY_TOKENS.getOrDefault(region, Set.of()).contains(firstToken);
    }
}
