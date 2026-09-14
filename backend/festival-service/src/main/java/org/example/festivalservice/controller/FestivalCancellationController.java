package org.example.festivalservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.festivalservice.domain.festival.*;
import org.example.festivalservice.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController @RequiredArgsConstructor
public class FestivalCancellationController {
    private final FestivalRepository repository;
    @Value("${internal.auth-token:CHANGE_ME_IN_ENV}") private String token;
    public record Reason(String reason) { }
    public record Candidate(Long festivalId, Long hostUserId, Long initiatedBy, String reason) { }
    @GetMapping("/api/admin/festivals/cancellation-requests")
    public ApiResponse<?> requests(@RequestHeader("X-User-Role") String role) {
        if (!"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ApiResponse.success("취소 승인 요청", repository.findByFestivalStatus(FestivalStatus.CANCELLATION_PENDING).stream()
                .map(f -> Map.of("festivalId", f.getId(), "name", f.getName(), "reason", f.getCancelReason(),
                        "approved", f.getCancellationApprovedAt() != null)).toList());
    }
    @PostMapping("/api/host/festivals/{id}/cancellation-request") @Transactional
    public ApiResponse<?> request(@PathVariable Long id, @RequestHeader("X-User-Id") Long user,
                                 @RequestHeader("X-User-Role") String role, @RequestBody Reason reason) {
        if (!"HOST".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var f = get(id);
        if (!user.equals(f.getHostUserId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        f.requestCancellation(user, reason.reason());
        return ApiResponse.success("행사 취소 승인 대기", f.getFestivalStatus());
    }
    @PostMapping("/api/admin/festivals/{id}/approve-cancellation") @Transactional
    public ApiResponse<?> approve(@PathVariable Long id, @RequestHeader("X-User-Role") String role,
                                 @RequestHeader("X-User-Id") Long actor) {
        if (!"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var f = get(id); f.approveCancellation(actor);
        return ApiResponse.success("전액 환불 승인", f.getFestivalStatus());
    }
    @GetMapping("/internal/v1/festivals/refund-candidates")
    public List<Candidate> candidates(@RequestHeader("Authorization") String auth) {
        verify(auth);
        return repository.findByFestivalStatusAndCancellationApprovedAtIsNotNull(FestivalStatus.CANCELLATION_PENDING)
                .stream().map(f -> new Candidate(f.getId(), f.getHostUserId(), f.getCancelledByUserId(), f.getCancelReason())).toList();
    }
    @PostMapping("/internal/v1/festivals/{id}/complete-cancellation") @Transactional
    public void complete(@PathVariable Long id, @RequestHeader("Authorization") String auth) {
        verify(auth); get(id).completeCancellation();
    }
    private Festival get(Long id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
    private void verify(String auth) { if (!auth.equals("Bearer " + token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
}
