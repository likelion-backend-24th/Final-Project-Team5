package org.example.paymentservice.domain.settlement;

import lombok.RequiredArgsConstructor;
import org.example.paymentservice.common.dto.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
import org.example.paymentservice.domain.payment.PaymentMethodCategory;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/{audience:admin|host}/settlements")
public class SettlementController {
    private final SettlementService service;
    @GetMapping
    public ApiResponse<?> list(@PathVariable String audience, @RequestHeader("X-User-Id") Long user,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) SettlementStatus status, @RequestParam(required = false) Long festivalId,
            @RequestParam(required = false) Long hostUserId, @RequestParam(required = false) PaymentMethodCategory paymentMethod,
            @RequestParam(required = false) String festivalName, @RequestParam(required = false) String hostName,
            @RequestParam(defaultValue = "false") boolean testPayment,
            @RequestParam(defaultValue = "SETTLEMENT_AT") String dateBasis,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.list(new SettlementService.Actor(user, role), "host".equals(audience),
                new SettlementService.Filter(from, to, status, festivalId, hostUserId, paymentMethod, testPayment, dateBasis, festivalName, hostName), page, size);
        return ApiResponse.success("정산 목록", result.getContent(), Meta.of(result));
    }
    @GetMapping("/summary")
    public ApiResponse<?> summary(@PathVariable String audience, @RequestHeader("X-User-Id") Long user,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) SettlementStatus status, @RequestParam(required = false) Long festivalId,
            @RequestParam(required = false) Long hostUserId, @RequestParam(required = false) PaymentMethodCategory paymentMethod,
            @RequestParam(required = false) String festivalName, @RequestParam(required = false) String hostName,
            @RequestParam(defaultValue = "false") boolean testPayment,
            @RequestParam(defaultValue = "SETTLEMENT_AT") String dateBasis) {
        return ApiResponse.success("정산 요약", service.summary(new SettlementService.Actor(user, role), "host".equals(audience),
                new SettlementService.Filter(from, to, status, festivalId, hostUserId, paymentMethod, testPayment, dateBasis, festivalName, hostName)));
    }
    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable String audience, @PathVariable Long id,
            @RequestHeader("X-User-Id") Long user, @RequestHeader("X-User-Role") String role) {
        return ApiResponse.success("정산 상세", service.detail(new SettlementService.Actor(user, role), "host".equals(audience), id));
    }
    @PostMapping("/{id}/{action}")
    public ApiResponse<?> command(@PathVariable String audience, @PathVariable Long id, @PathVariable String action,
            @RequestHeader("X-User-Id") Long user, @RequestHeader("X-User-Role") String role,
            @RequestHeader("Idempotency-Key") String key, @RequestBody SettlementService.Command command) {
        if (!"admin".equals(audience)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        return ApiResponse.success("정산 처리", service.command(new SettlementService.Actor(user, role), id, action, key, command));
    }
}
