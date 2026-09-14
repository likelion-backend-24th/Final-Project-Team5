package org.example.paymentservice.domain.settlement;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.dto.ApiResponse;
import org.example.paymentservice.common.dto.Meta;
import org.example.paymentservice.common.exception.ApiException;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;
import org.example.paymentservice.domain.settlement.dto.SettlementActor;
import org.example.paymentservice.domain.settlement.dto.SettlementCommandRequest;
import org.example.paymentservice.domain.settlement.dto.SettlementDetailResponse;
import org.example.paymentservice.domain.settlement.dto.SettlementFilter;
import org.example.paymentservice.domain.settlement.dto.SettlementResponse;
import org.example.paymentservice.domain.settlement.dto.SettlementSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/{audience:admin|host}/settlements")
public class SettlementController {

    private final SettlementService service;
    private final SettlementQueryService queries;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SettlementResponse>>> list(
        @PathVariable String audience,
        @RequestHeader("X-User-Id") Long user,
        @RequestHeader("X-User-Role") String role,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(required = false) SettlementStatus status,
        @RequestParam(required = false) Long festivalId,
        @RequestParam(required = false) Long hostUserId,
        @RequestParam(required = false) PaymentMethodCategory paymentMethod,
        @RequestParam(required = false) String festivalName,
        @RequestParam(required = false) String hostName,
        @RequestParam(defaultValue = "false") boolean testPayment,
        @RequestParam(defaultValue = "SETTLEMENT_AT") String dateBasis,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        var result = queries.list(
            new SettlementActor(user, role),
            "host".equals(audience),
            new SettlementFilter(
                from,
                to,
                status,
                festivalId,
                hostUserId,
                paymentMethod,
                testPayment,
                dateBasis,
                festivalName,
                hostName
            ),
            page,
            size
        );
        return ResponseEntity.ok(ApiResponse.success("정산 목록", result.getContent(), Meta.of(result)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SettlementSummaryResponse>> summary(
        @PathVariable String audience,
        @RequestHeader("X-User-Id") Long user,
        @RequestHeader("X-User-Role") String role,
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to,
        @RequestParam(required = false) SettlementStatus status,
        @RequestParam(required = false) Long festivalId,
        @RequestParam(required = false) Long hostUserId,
        @RequestParam(required = false) PaymentMethodCategory paymentMethod,
        @RequestParam(required = false) String festivalName,
        @RequestParam(required = false) String hostName,
        @RequestParam(defaultValue = "false") boolean testPayment,
        @RequestParam(defaultValue = "SETTLEMENT_AT") String dateBasis
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "정산 요약",
                queries.summary(
                    new SettlementActor(user, role),
                    "host".equals(audience),
                    new SettlementFilter(
                        from,
                        to,
                        status,
                        festivalId,
                        hostUserId,
                        paymentMethod,
                        testPayment,
                        dateBasis,
                        festivalName,
                        hostName
                    )
                )
            )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SettlementDetailResponse>> detail(
        @PathVariable String audience,
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long user,
        @RequestHeader("X-User-Role") String role
    ) {
        return ResponseEntity.ok(
            ApiResponse.success(
                "정산 상세",
                queries.detail(new SettlementActor(user, role), "host".equals(audience), id)
            )
        );
    }

    @PostMapping("/{id}/{action}")
    public ResponseEntity<ApiResponse<SettlementDetailResponse>> command(
        @PathVariable String audience,
        @PathVariable Long id,
        @PathVariable String action,
        @RequestHeader("X-User-Id") Long user,
        @RequestHeader("X-User-Role") String role,
        @RequestHeader("Idempotency-Key") String key,
        @RequestBody SettlementCommandRequest command
    ) {
        if (!"admin".equals(audience)) throw new ApiException(SettlementErrorCode.FORBIDDEN_ROLE);
        return ResponseEntity.ok(
            ApiResponse.success("정산 처리", service.command(new SettlementActor(user, role), id, action, key, command))
        );
    }
}
