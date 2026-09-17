package org.example.festivalservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.common.dto.Meta;
import org.example.festivalservice.domain.festival.FestivalResponseDto;
import org.example.festivalservice.domain.festival.FestivalService;
import org.example.festivalservice.domain.festival.FestivalViewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/festivals")
@RequiredArgsConstructor
public class FestivalController {

    private static final Logger log = LoggerFactory.getLogger(FestivalController.class);

    private final FestivalService festivalService;
    private final FestivalViewService festivalViewService;

    //페스티벌 목록 페이징 조회, 인증 불필요. 인기순은 ?sort=viewCount,desc
    @GetMapping
    public ResponseEntity<ApiResponse<List<FestivalResponseDto>>> listFestivals(Pageable pageable){
        Page<FestivalResponseDto> page = festivalService.listFestivals(pageable);
        return ResponseEntity.ok(ApiResponse.success("페스티벌 목록 페이징 조회",page.getContent(), Meta.of(page)));
    }

    //페스티벌 상세 조회, 인증 불필요. 조회할 때마다 IP 기준으로 조회수를 집계한다(24시간에 1회)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FestivalResponseDto>> getFestivalDetail(
            @PathVariable Long id,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            HttpServletRequest request){
        FestivalResponseDto detail = festivalService.getFestivalDetail(id);
        try {
            festivalViewService.recordView(id, clientIp(forwardedFor, request));
        } catch (RuntimeException e) {
            //집계는 부가 기능이라 실패해도 상세 응답은 그대로 내려준다(동시 조회의 유니크 충돌 등)
            log.debug("조회수 집계 실패. festivalId={}", id, e);
        }
        return ResponseEntity.ok(ApiResponse.success("페스티벌 상세 조회", detail));
    }

    //nginx → gateway를 거치며 X-Forwarded-For가 "클라이언트, nginx" 순으로 쌓이므로 첫 값이 실제 클라이언트다
    private static String clientIp(String forwardedFor, HttpServletRequest request) {
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
