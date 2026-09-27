package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.UserLookupClient;
import org.example.festivalservice.common.UserLookupClient.UserSummary;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.tickettype.TicketTypeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 어드민 운영 현황 — 공개된 적 있는 페스티벌의 운영 상태(예정/진행 중/종료/취소)와 티켓 판매 현황을 조회한다.
 */
@Service
@RequiredArgsConstructor
public class FestivalOperationService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String HOST_ROLE = "HOST";

    //운영 현황 대상 — 공개된 적 있는 상태만(심사 대기·반려는 제외)
    private static final List<FestivalStatus> OPERATION_VISIBLE_STATUSES = List.of(
            FestivalStatus.PUBLISHED, FestivalStatus.CLOSED,
            FestivalStatus.CANCELLATION_PENDING, FestivalStatus.CANCELLED);

    private static final Set<String> OPERATION_STATUSES =
            Set.of("ALL", "SCHEDULED", "ONGOING", "CLOSED", "CANCELLED");

    //검색어로 찾은 주최자가 없을 때 IN 조건에 넣는 값 — 빈 IN () 문법 오류 방지
    private static final Long NO_MATCH_ID = -1L;

    private final FestivalRepository festivalRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final FestivalImageRepository festivalImageRepository;
    private final UserLookupClient userLookupClient;

    //"지금" 기준 타임존 — 서버 타임존과 무관하게 한국 시간으로 비교한다
    @Value("${app.timezone:Asia/Seoul}")
    private String appTimezone;

    public Page<FestivalOperationResponseDto> searchOperations(String role, String operationStatus,
                                                               String keyword, Pageable pageable) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        }

        // 모르는 값은 전체로 처리
        String status = "ALL";
        if (operationStatus != null && OPERATION_STATUSES.contains(operationStatus)) {
            status = operationStatus;
        }

        // 검색창이 비어 있으면 null로 바꿔서 "검색 조건 없음"으로 처리
        String searchKeyword = null;
        if (keyword != null && !keyword.isBlank()) {
            searchKeyword = keyword.trim();
        }

        // 주최자 닉네임·이메일로 찾은 id 목록 (없으면 아무것도 매칭되지 않는 값)
        List<Long> hostIds = List.of(NO_MATCH_ID);
        if (searchKeyword != null) {
            List<Long> foundHostIds = userLookupClient.searchIds(searchKeyword, HOST_ROLE);
            if (!foundHostIds.isEmpty()) {
                hostIds = foundHostIds;
            }
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of(appTimezone));

        Page<Festival> festivalPage = festivalRepository.searchOperationsForAdmin(
                searchKeyword, hostIds, OPERATION_VISIBLE_STATUSES, status, now, pageable);

        return toOperationResponsePage(festivalPage, now);
    }

    //응답 조립(내부 메서드) — 티켓 합계·대표 이미지·주최자를 페이지 단위로 한 번씩만 조회한다
    private Page<FestivalOperationResponseDto> toOperationResponsePage(Page<Festival> festivalPage,
                                                                       LocalDateTime now) {
        List<Long> festivalIds = new ArrayList<>();
        List<Long> hostUserIds = new ArrayList<>();
        for (Festival festival : festivalPage.getContent()) {
            festivalIds.add(festival.getId());
            if (festival.getHostUserId() != null) {
                hostUserIds.add(festival.getHostUserId());
            }
        }

        // 페스티벌 id → [전체 수량, 판매 수량]
        Map<Long, long[]> quantities = new HashMap<>();
        // 페스티벌 id → 대표 이미지 URL
        Map<Long, String> thumbnails = new HashMap<>();
        if (!festivalIds.isEmpty()) {
            for (Object[] row : ticketTypeRepository.sumQuantitiesByFestivalIds(festivalIds)) {
                Long festivalId = (Long) row[0];
                long total = row[1] == null ? 0L : ((Number) row[1]).longValue();
                long sold = row[2] == null ? 0L : ((Number) row[2]).longValue();
                quantities.put(festivalId, new long[]{total, sold});
            }
            for (FestivalImage image : festivalImageRepository.findByFestivalIdIn(festivalIds)) {
                if (image.getImageType() == FestivalImageType.THUMBNAIL) {
                    thumbnails.put(image.getFestival().getId(), image.getImageUrl());
                }
            }
        }

        Map<Long, UserSummary> hosts = userLookupClient.findByIds(hostUserIds);

        return festivalPage.map(festival -> {
            long[] quantity = quantities.getOrDefault(festival.getId(), new long[]{0L, 0L});
            long total = quantity[0];
            long sold = quantity[1];
            int saleRate = total == 0 ? 0 : (int) (sold * 100 / total);

            UserSummary host = hosts.get(festival.getHostUserId());

            return new FestivalOperationResponseDto(
                    festival.getId(),
                    festival.getName(),
                    festival.getHostUserId(),
                    host == null ? null : host.nickname(),
                    festival.getFestivalCategory(),
                    festival.getStartAt(),
                    festival.getEndAt(),
                    festival.getFestivalStatus(),
                    operationStatusOf(festival, now),
                    thumbnails.get(festival.getId()),
                    total,
                    sold,
                    saleRate
            );
        });
    }

    //운영 상태 계산(내부 메서드) — 쿼리의 필터 조건과 같은 기준
    private String operationStatusOf(Festival festival, LocalDateTime now) {
        FestivalStatus status = festival.getFestivalStatus();
        if (status == FestivalStatus.CANCELLATION_PENDING || status == FestivalStatus.CANCELLED) {
            return "CANCELLED";
        }
        if (status == FestivalStatus.CLOSED) {
            return "CLOSED";
        }
        // PUBLISHED — 날짜로 판단
        if (festival.getEndAt() != null && festival.getEndAt().isBefore(now)) {
            return "CLOSED";
        }
        if (festival.getStartAt() != null && festival.getStartAt().isAfter(now)) {
            return "SCHEDULED";
        }
        return "ONGOING";
    }
}