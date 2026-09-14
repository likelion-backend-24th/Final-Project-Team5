package org.example.festivalservice.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.festival.FestivalCancellationService;
import org.example.festivalservice.domain.festival.FestivalErrorCode;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.example.festivalservice.domain.festival.FestivalStatus;
import org.example.festivalservice.domain.festival.RefundCandidateDto;
import org.example.festivalservice.domain.festival.SettlementContextDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/festivals")
public class InternalFestivalController {

    private final FestivalRepository repository;
    private final FestivalCancellationService cancellations;

    @Value("${internal.auth-token:CHANGE_ME_IN_ENV}")
    private String token;

    @Value("${app.timezone}")
    private String timezone;

    @GetMapping("/settlement-candidates")
    public List<SettlementContextDto> candidates(
        @RequestHeader("Authorization") String auth,
        @RequestParam(defaultValue = "0") int page
    ) {
        verify(auth);
        if (page < 0) throw new ApiException(FestivalErrorCode.INVALID_SETTLEMENT_PAGE);
        return repository
            .findSettlementCandidates(
                LocalDateTime.now(ZoneId.of(timezone)).minusHours(24),
                List.of(FestivalStatus.PUBLISHED, FestivalStatus.CLOSED,
                        FestivalStatus.CANCELLATION_PENDING, FestivalStatus.CANCELLED),
                PageRequest.of(page, 100)
            )
            .stream()
            .map(f -> SettlementContextDto.from(f, ZoneId.of(timezone)))
            .toList();
    }

    @GetMapping("/{id}/settlement-context")
    public SettlementContextDto context(@PathVariable Long id, @RequestHeader("Authorization") String auth) {
        verify(auth);
        return SettlementContextDto.from(
            repository.findById(id).orElseThrow(() -> new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND)),
            ZoneId.of(timezone)
        );
    }

    @GetMapping("/refund-candidates")
    public List<RefundCandidateDto> refundCandidates(@RequestHeader("Authorization") String auth) {
        verify(auth);
        return cancellations.refundCandidates();
    }

    @PostMapping("/{id}/complete-cancellation")
    public void completeCancellation(@PathVariable Long id, @RequestHeader("Authorization") String auth) {
        verify(auth);
        cancellations.completeCancellation(id);
    }

    private void verify(String auth) {
        // 내부 토큰의 접두어 비교 시간 차이로 인증 값을 추측하지 못하도록 상수 시간으로 비교한다.
        if (
            !MessageDigest.isEqual(
                ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                auth.getBytes(StandardCharsets.UTF_8)
            )
        ) {
            throw new ApiException(FestivalErrorCode.INVALID_INTERNAL_TOKEN);
        }
    }
}
