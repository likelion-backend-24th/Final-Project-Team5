package org.example.festivalservice.domain.festival;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.infrastructure.kakao.KakaoLocalClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 전용 — 좌표 없이 등록된(카카오맵 기능 이전 등록분 포함) 페스티벌의 locationDetail을 카카오
 * "키워드로 장소 검색"에 넣어 좌표를 채운다. 검색 결과의 지역이 DB의 region과 다르면(동명 장소 오매칭 방지)
 * 적용하지 않고 건너뛴다 — 잘못된 좌표를 채우는 것보다 비워 두는 쪽이 안전하다.
 */
@Service
@RequiredArgsConstructor
public class FestivalCoordinateBackfillService {

    private static final Logger log = LoggerFactory.getLogger(FestivalCoordinateBackfillService.class);
    private static final String ADMIN_ROLE = "ADMIN";

    private final FestivalRepository festivalRepository;
    private final KakaoLocalClient kakaoLocalClient;

    @Transactional
    public List<CoordinateBackfillResultDto> backfillMissingCoordinates(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        }

        List<Festival> targets = festivalRepository.findByLatitudeIsNull();
        List<CoordinateBackfillResultDto> results = new ArrayList<>();

        for (Festival festival : targets) {
            results.add(backfillOne(festival));
        }
        return results;
    }

    private CoordinateBackfillResultDto backfillOne(Festival festival) {
        if (festival.getLocationDetail() == null || festival.getLocationDetail().isBlank()) {
            return CoordinateBackfillResultDto.skipped(festival.getId(), festival.getName(), "상세주소 없음");
        }

        //지역명을 앞에 붙여 검색 범위를 좁힌다("서울 서울숲 야외무대") — 동명 장소가 다른 지역에 있어도
        //카카오가 이 지역에 가까운 결과를 우선 고르게 유도한다.
        String query = (FestivalRegionNames.shortLabel(festival.getRegion()) + " " + festival.getLocationDetail()).trim();
        Optional<KakaoLocalClient.KeywordResult> match = kakaoLocalClient.searchKeyword(query);
        if (match.isEmpty()) {
            return CoordinateBackfillResultDto.skipped(festival.getId(), festival.getName(), "검색 결과 없음");
        }

        KakaoLocalClient.KeywordResult result = match.get();
        String addressName = (result.roadAddressName() != null && !result.roadAddressName().isBlank())
                ? result.roadAddressName() : result.addressName();
        if (!FestivalRegionNames.matchesRegion(festival.getRegion(), addressName)) {
            log.info("좌표 백필 지역 불일치로 건너뜀: festivalId={}, region={}, 검색결과={}",
                    festival.getId(), festival.getRegion(), addressName);
            return CoordinateBackfillResultDto.skipped(
                    festival.getId(), festival.getName(), "지역 불일치(검색 결과: " + addressName + ")");
        }

        festival.updateCoordinates(result.latitude(), result.longitude());
        return CoordinateBackfillResultDto.applied(festival.getId(), festival.getName(), result.latitude(), result.longitude());
    }
}
