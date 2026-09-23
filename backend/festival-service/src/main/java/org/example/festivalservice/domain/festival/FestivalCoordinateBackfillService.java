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
 * 좌표 없이 등록된(카카오맵 기능 이전 등록분 포함) 페스티벌의 locationDetail을 카카오 "키워드로 장소 검색"에
 * 넣어 좌표를 채운다. 검색 결과의 지역이 DB의 region과 다르면(동명 장소 오매칭 방지) 적용하지 않고 건너뛴다
 * — 잘못된 좌표를 채우는 것보다 비워 두는 쪽이 안전하다.
 *
 * 두 가지 진입점이 있다:
 *  - {@link #backfillIfMissing}: 방문자가 상세 페이지를 볼 때(FestivalService.getFestivalDetail) 좌표가
 *    없으면 그 자리에서 한 번 채우는 지연 백필. 실패해도 절대 예외를 던지지 않는다 — 상세 조회가 이 부가
 *    기능 때문에 실패하면 안 되기 때문이다. 한 번 채워지면 다음부터는 latitude가 있으니 다시 호출 안 된다.
 *  - {@link #backfillMissingCoordinates}: 운영자가 남아있는 미채움 건을 한 번에 훑어보고 싶을 때 쓰는 수동
 *    실행 API. 지연 백필이 결국 방문 있는 페스티벌은 다 채우므로 평소엔 없어도 되지만, 방문이 뜸한 건을
 *    바로 확인하고 싶을 때 쓴다.
 */
@Service
@RequiredArgsConstructor
public class FestivalCoordinateBackfillService {

    private static final Logger log = LoggerFactory.getLogger(FestivalCoordinateBackfillService.class);
    private static final String ADMIN_ROLE = "ADMIN";

    private final FestivalRepository festivalRepository;
    private final KakaoLocalClient kakaoLocalClient;

    //상세 조회 경로에서 호출 — 좌표가 이미 있으면 아무 것도 안 하고, 실패해도 상세 조회 자체는 절대
    //막지 않는다(카카오 API 장애·검색 실패·DB 저장 실패 전부 로그만 남기고 삼킨다).
    @Transactional
    public void backfillIfMissing(Festival festival) {
        if (festival.getLatitude() != null || festival.getLongitude() != null) {
            return;
        }
        try {
            CoordinateBackfillResultDto result = backfillOne(festival);
            if (result.applied()) {
                festivalRepository.save(festival);
            }
        } catch (RuntimeException e) {
            log.warn("상세 조회 중 좌표 지연 백필 실패(무시하고 계속 진행). festivalId={}", festival.getId(), e);
        }
    }

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
