package org.example.reservationservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.dto.ApiResponse;
import org.example.reservationservice.domain.seat.SeatGenerationService;
import org.example.reservationservice.domain.seat.SeatResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 참가자·비로그인 사용자가 좌석맵을 조회하는 공개 API. 페스티벌 목록/상세 조회와 같은 정책으로
 * 인증을 요구하지 않는다 — 좌석 선점(예매 신청)만 로그인이 필요하다.
 */
@RestController
@RequestMapping("/api/festivals/{festivalId}/ticket-types/{ticketTypeId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatGenerationService seatGenerationService;

    //특정 페스티벌·티켓타입의 전체 좌석을 구역/행/번호 순으로 조회한다
    @GetMapping
    public ResponseEntity<ApiResponse<List<SeatResponseDto>>> listSeats(
            @PathVariable Long festivalId,
            @PathVariable Long ticketTypeId
    ) {
        return ResponseEntity.ok(ApiResponse.success("좌석 목록 조회 성공",
                seatGenerationService.listSeats(festivalId, ticketTypeId)));
    }
}