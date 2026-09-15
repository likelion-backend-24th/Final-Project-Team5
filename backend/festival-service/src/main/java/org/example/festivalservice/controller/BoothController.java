package org.example.festivalservice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.booth.BoothResponseDto;
import org.example.festivalservice.domain.booth.BoothService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 관람자용 부스 조회 — 인증 불필요. 대기 신청 버튼(POST /api/booth-waitlists)만 로그인·티켓 보유가 필요하다. */
@RestController
@RequiredArgsConstructor
public class BoothController {

    private final BoothService boothService;

    //페스티벌 상세 화면에 딸린 부스 목록
    @GetMapping("/api/festivals/{festivalId}/booths")
    public ResponseEntity<ApiResponse<List<BoothResponseDto>>> listBoothsForFestival(@PathVariable Long festivalId) {
        return ResponseEntity.ok(ApiResponse.success("페스티벌 부스 목록 조회", boothService.listBoothsForFestival(festivalId)));
    }

    //부스 단건 상세 — 대기 신청 페이지와 동일 화면이 이 API로 정보를 채운다
    @GetMapping("/api/booths/{id}")
    public ResponseEntity<ApiResponse<BoothResponseDto>> getBoothDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("부스 상세 조회", boothService.getBoothDetail(id)));
    }
}
