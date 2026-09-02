package org.example.festivalservice.domain.festival;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.ApiException;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.example.festivalservice.domain.tickettype.TicketTypeRepository;
import org.example.festivalservice.domain.tickettype.TicketTypeRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FestivalService {

    private static final String HOST_ROLE = "HOST";
    private static final String FORBIDDEN_ROLE = "FORBIDDEN_ROLE";
    private static final String FESTIVAL_NOT_FOUND = "FESTIVAL_NOT_FOUND";
    private static final String FORBIDDEN_NOT_OWNER = "FORBIDDEN_NOT_OWNER";

    private final FestivalRepository festivalRepository;
    private final TicketTypeRepository ticketTypeRepository;

    //승인된 주최자가 새 페스티벌(및 티켓 종류)을 등록한다
    @Transactional
    public FestivalResponseDto createFestival(Long hostUserId, String role, FestivalRequestDto request) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_ROLE, "주최자 권한이 없습니다");
        }

        Festival festival = Festival.builder()
                .hostUserId(hostUserId)
                .name(request.name())
                .description(request.description())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .location(request.location())
                .festivalCategory(request.festivalCategory())
                .festivalStatus(FestivalStatus.PUBLISHED)
                .build();
        Festival saved = festivalRepository.save(festival);

        List<TicketType> ticketTypes = request.ticketTypes().stream()
                .map(ticketTypeRequest -> toTicketType(saved, ticketTypeRequest))
                .toList();
        ticketTypeRepository.saveAll(ticketTypes);

        return FestivalResponseDto.from(saved, ticketTypes);
    }

    //주최자가 본인이 등록한 페스티벌 목록을 조회한다
    public List<FestivalResponseDto> listMyFestivals(Long hostUserId, String role) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_ROLE, "주최자 권한이 없습니다");
        }

        return festivalRepository.findByHostUserId(hostUserId).stream()
                .map(festival -> FestivalResponseDto.from(festival, ticketTypeRepository.findByFestivalId(festival.getId())))
                .toList();
    }

    //주최자가 본인 페스티벌의 상세 정보를 조회한다
    public FestivalResponseDto getMyFestivalDetail(Long id, Long hostUserId,String role) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_ROLE, "주최자 권한이 없습니다");
        }

        Festival festival = getOwnedFestival(id, hostUserId);
        return FestivalResponseDto.from(festival, ticketTypeRepository.findByFestivalId(id));
    }

    //Festival 불러오기(내부 메서드)
    private Festival getOwnedFestival(Long id, Long hostUserId) {
        Festival festival = festivalRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FESTIVAL_NOT_FOUND, "존재하지 않는 페스티벌입니다"));
        if (!festival.getHostUserId().equals(hostUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, FORBIDDEN_NOT_OWNER, "본인 소유 페스티벌만 조회할 수 있습니다");
        }
        return festival;
    }

    //dto로 TicketType으로 생성(내부 메서드)
    private TicketType toTicketType(Festival festival, TicketTypeRequestDto request) {
        return TicketType.builder()
                .festival(festival)
                .name(request.name())
                .price(request.price())
                .totalQuantity(request.quantity())
                .remainQuantity(request.quantity())
                .build();
    }

    //페스티벌 목록 조회(페이징), 인증 불필요 — 공개(PUBLISHED) 상태만 노출
    public Page<FestivalResponseDto> listFestivals(Pageable pageable) {
        return festivalRepository.findByFestivalStatus(FestivalStatus.PUBLISHED, pageable)
                .map(festival -> FestivalResponseDto.from(festival, ticketTypeRepository.findByFestivalId(festival.getId())));
    }

    //페스티벌 상세 조회, 인증 불필요 — 공개(PUBLISHED) 상태가 아니면 404(미승인·반려 페스티벌은 존재 자체를 숨김)
    public FestivalResponseDto getFestivalDetail(Long id) {
        Festival festival = festivalRepository.findByIdAndFestivalStatus(id, FestivalStatus.PUBLISHED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FESTIVAL_NOT_FOUND, "존재하지 않는 페스티벌입니다"));
        return FestivalResponseDto.from(festival, ticketTypeRepository.findByFestivalId(id));
    }
}
