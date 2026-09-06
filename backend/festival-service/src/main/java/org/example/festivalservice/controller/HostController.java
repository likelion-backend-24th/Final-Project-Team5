package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.festival.FestivalRequestDto;
import org.example.festivalservice.domain.festival.FestivalResponseDto;
import org.example.festivalservice.domain.festival.FestivalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/host/festivals")
@RequiredArgsConstructor
public class HostController {
    private final FestivalService festivalService;

    //승인된 주최자가 새 페스티벌(및 티켓 종류)을 등록한다
    @PostMapping
    public ResponseEntity<ApiResponse<FestivalResponseDto>> createFestival(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody FestivalRequestDto dto
            ){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("새 페스티벌/티켓 종류 등록",festivalService.createFestival(userId,role,dto)));
    }

    //주최자가 본인이 등록한 페스티벌 목록을 조회한다
    @GetMapping
    public ResponseEntity<ApiResponse<List<FestivalResponseDto>>> listMyFestivals(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ){
        return ResponseEntity.ok(ApiResponse.success("본인이 등록한 페스티벌 목록 조회",festivalService.listMyFestivals(userId,role)));
    }

    //주최자가 본인 페스티벌의 상세 정보를 조회한다
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FestivalResponseDto>> getMyFestivalDetail(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role
    ){
        return ResponseEntity.ok(ApiResponse.success("본인이 등록한 페스티벌 상세 정보 조회",festivalService.getMyFestivalDetail(id,userId,role)));
    }
}
