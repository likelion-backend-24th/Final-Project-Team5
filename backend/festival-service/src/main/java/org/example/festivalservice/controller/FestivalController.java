package org.example.festivalservice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiResponse;
import org.example.festivalservice.common.Meta;
import org.example.festivalservice.domain.festival.FestivalResponseDto;
import org.example.festivalservice.domain.festival.FestivalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/festivals")
@RequiredArgsConstructor
public class FestivalController {
    private final FestivalService festivalService;

    //페스티벌 목록 페이징 조회, 인증 불필요
    @GetMapping
    public ResponseEntity<ApiResponse<List<FestivalResponseDto>>> listFestivals(Pageable pageable){
        Page<FestivalResponseDto> page = festivalService.listFestivals(pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), Meta.of(page)));
    }

    //페스티벌 상세 조회, 인증 불필요
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FestivalResponseDto>> getFestivalDetail(
            @PathVariable Long id){
        return ResponseEntity.ok(ApiResponse.success(festivalService.getFestivalDetail(id)));
    }
}
