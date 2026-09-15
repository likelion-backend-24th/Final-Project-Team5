package org.example.festivalservice.domain.booth;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.festival.Festival;
import org.example.festivalservice.domain.festival.FestivalErrorCode;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoothService {

    private static final String STOREHOST_ROLE = "STOREHOST";

    //관람자에게 노출 가능한 상태 — WAITING(개설 직후 대기)은 숨긴다.
    private static final List<BoothStatus> VISIBLE_STATUSES = List.of(BoothStatus.OPEN, BoothStatus.CLOSED);

    private final BoothRepository boothRepository;
    private final FestivalRepository festivalRepository;

    //STOREHOST가 부스를 개설한다. 페스티벌 소유자인지, 부스 개설이 가능한 페스티벌 상태인지는 검증하지 않는다
    //(요청대로 역할 체크만) — 필요해지면 여기 한 곳만 고치면 된다.
    @Transactional
    public BoothResponseDto createBooth(Long hostUserId, String role, BoothRequestDto request) {
        if (!STOREHOST_ROLE.equals(role)) {
            throw new ApiException(BoothErrorCode.FORBIDDEN_STOREHOST_ROLE);
        }
        Festival festival = festivalRepository.findById(request.festivalId())
                .orElseThrow(() -> new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND));

        Booth booth = Booth.builder()
                .festival(festival)
                .hostUserId(hostUserId)
                .title(request.title())
                .description(request.description())
                .boothHostName(request.boothHostName())
                .imageUrl(request.imageUrl())
                .boothStatus(BoothStatus.WAITING)
                .build();

        return BoothResponseDto.from(boothRepository.save(booth));
    }

    //관람자용 목록 — 인증 불필요, WAITING 상태는 숨긴다
    public List<BoothResponseDto> listBoothsForFestival(Long festivalId) {
        return boothRepository.findByFestivalIdAndBoothStatusIn(festivalId, VISIBLE_STATUSES).stream()
                .map(BoothResponseDto::from)
                .toList();
    }

    //관람자용 상세 — WAITING이면 404(존재 자체를 숨김). 대기 신청 전 프론트가 부스 정보를 보여줄 때도 이걸 쓴다.
    public BoothResponseDto getBoothDetail(Long id) {
        Booth booth = boothRepository.findByIdAndBoothStatusIn(id, VISIBLE_STATUSES)
                .orElseThrow(() -> new ApiException(BoothErrorCode.BOOTH_NOT_FOUND));
        return BoothResponseDto.from(booth);
    }

    //STOREHOST 본인이 개설한 부스 목록(상태 무관)
    public List<BoothResponseDto> listMyBooths(Long hostUserId, String role) {
        if (!STOREHOST_ROLE.equals(role)) {
            throw new ApiException(BoothErrorCode.FORBIDDEN_STOREHOST_ROLE);
        }
        return boothRepository.findByHostUserId(hostUserId).stream()
                .map(BoothResponseDto::from)
                .toList();
    }

    //STOREHOST 본인의 부스 상세(상태 무관 — WAITING이어도 본인은 봐야 한다)
    public BoothResponseDto getMyBoothDetail(Long id, Long hostUserId, String role) {
        return BoothResponseDto.from(getOwnedBooth(id, hostUserId, role));
    }

    //STOREHOST가 본인 부스 상태를 바꾼다(대기/운영중/마감)
    @Transactional
    public BoothResponseDto changeStatus(Long id, Long hostUserId, String role, BoothStatusUpdateRequestDto request) {
        Booth booth = getOwnedBooth(id, hostUserId, role);
        booth.changeStatus(request.boothStatus());
        return BoothResponseDto.from(booth);
    }

    //본인 소유 부스 불러오기(내부 메서드)
    private Booth getOwnedBooth(Long id, Long hostUserId, String role) {
        if (!STOREHOST_ROLE.equals(role)) {
            throw new ApiException(BoothErrorCode.FORBIDDEN_STOREHOST_ROLE);
        }
        Booth booth = boothRepository.findById(id)
                .orElseThrow(() -> new ApiException(BoothErrorCode.BOOTH_NOT_FOUND));
        if (!booth.getHostUserId().equals(hostUserId)) {
            throw new ApiException(BoothErrorCode.FORBIDDEN_NOT_OWNER);
        }
        return booth;
    }
}
