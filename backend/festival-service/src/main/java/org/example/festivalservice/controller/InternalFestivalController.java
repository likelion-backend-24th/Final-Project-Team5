package org.example.festivalservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.festival.FestivalCancellationService;
import org.example.festivalservice.domain.festival.FestivalErrorCode;
import org.example.festivalservice.domain.festival.RefundCandidateDto;
import org.example.festivalservice.domain.festival.SettlementContextDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * Gateway를 거치지 않는 내부 전용 API — payment-service의 정산·행사 환불 배치가 환경변수 Bearer Token으로만 호출한다.
 * 응답은 팀 공통 봉투(ApiResponse) 없이 그대로 내려준다(payment-service의 FestivalSettlementClient가 배열/객체로 바로 파싱한다).
 */
@RestController
@RequestMapping("/internal/v1/festivals")
@RequiredArgsConstructor
public class InternalFestivalController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FestivalCancellationService festivalCancellationService;

    @Value("${internal.auth-token:CHANGE_ME_IN_ENV}")
    private String internalAuthToken;

    //정산 배치가 페이지 단위(100건)로 훑는 정산 후보 — 종료 24시간이 지난 공개·종료·취소 페스티벌
    @GetMapping("/settlement-candidates")
    public List<SettlementContextDto> settlementCandidates(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "0") int page) {
        verifyInternalToken(authorization);
        return festivalCancellationService.settlementCandidates(page);
    }

    //정산 계산 직전에 페스티벌 하나의 주최자·이름·정산 가능 시각·상태를 다시 확인한다
    @GetMapping("/{id}/settlement-context")
    public SettlementContextDto settlementContext(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authorization) {
        verifyInternalToken(authorization);
        return festivalCancellationService.settlementContext(id);
    }

    //운영자가 승인한 행사 취소 목록 — 환불 배치가 이 목록의 결제를 전액 환불한다
    @GetMapping("/refund-candidates")
    public List<RefundCandidateDto> refundCandidates(@RequestHeader("Authorization") String authorization) {
        verifyInternalToken(authorization);
        return festivalCancellationService.refundCandidates();
    }

    //환불 배치가 모든 결제의 환불·예매 반영을 확인한 뒤 호출한다 — 이때 비로소 CANCELLED가 된다
    @PostMapping("/{id}/complete-cancellation")
    public void completeCancellation(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authorization) {
        verifyInternalToken(authorization);
        festivalCancellationService.completeCancellation(id);
    }

    //토큰 길이·접두어에 따른 비교 시간 차이로 값을 추측하지 못하도록 상수 시간 비교를 쓴다.
    private void verifyInternalToken(String authorization) {
        byte[] expected = (BEARER_PREFIX + internalAuthToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(FestivalErrorCode.INVALID_INTERNAL_TOKEN);
        }
    }
}
