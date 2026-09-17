// org/example/reservationservice/reservation/controller/SeatController.java
package org.example.reservationservice.reservation.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.common.dto.ApiResponse;
import org.example.reservationservice.seat.dto.SeatResponse;
import org.example.reservationservice.seat.service.SeatGenerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 참가자용 좌석맵 조회 API — 인증 불필요(페스티벌 목록/상세 조회와 같은 공개 정책).
 */
@RestController
@RequestMapping("/api/festivals/{festivalId}/ticket-types/{ticketTypeId}/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatGenerationService seatGenerationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SeatResponse>>> listSeats(
            @PathVariable Long festivalId,
            @PathVariable Long ticketTypeId
    ) {
        return ResponseEntity.ok(ApiResponse.success("좌석 목록 조회",
                seatGenerationService.listSeats(festivalId, ticketTypeId)));
    }
}