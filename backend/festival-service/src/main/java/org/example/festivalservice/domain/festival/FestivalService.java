package org.example.festivalservice.domain.festival;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.festivalservice.common.UserLookupClient;
import org.example.festivalservice.common.UserLookupClient.UserSummary;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
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
    private static final String ADMIN_ROLE = "ADMIN";

    private static final int MAX_DETAIL_IMAGE_COUNT = 2;

    private final FestivalRepository festivalRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final FestivalImageRepository festivalImageRepository;
    private final UserLookupClient userLookupClient;

    //승인된 주최자가 새 페스티벌(및 티켓 종류·이미지)을 등록한다
    @Transactional
    public FestivalResponseDto createFestival(Long hostUserId, String role, FestivalRequestDto request) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_HOST_ROLE);
        }
        if (!request.endAt().isAfter(request.startAt())) {
            throw new ApiException(FestivalErrorCode.INVALID_PERIOD);
        }
        if (request.operatingStartTime() != null && request.operatingEndTime() != null
                && !request.operatingEndTime().isAfter(request.operatingStartTime())) {
            throw new ApiException(FestivalErrorCode.INVALID_OPERATING_HOURS);
        }
        String thumbnailImageUrl = request.thumbnailImageUrl();
        List<String> detailImageUrls = request.detailImageUrls() == null ? List.of() : request.detailImageUrls();
        if (detailImageUrls.size() > MAX_DETAIL_IMAGE_COUNT) {
            throw new ApiException(FestivalErrorCode.INVALID_DETAIL_IMAGE_COUNT);
        }
        request.ticketTypes().forEach(ticketTypeRequest -> validateTicketTypeRequest(ticketTypeRequest, request));

        Festival festival = Festival.builder()
                .hostUserId(hostUserId)
                .name(request.name())
                .description(request.description())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .region(request.region())
                .locationDetail(request.locationDetail())
                .festivalCategory(request.festivalCategory())
                .festivalStatus(FestivalStatus.PENDING)
                .entryStartTime(request.entryStartTime())
                .operatingStartTime(request.operatingStartTime())
                .operatingEndTime(request.operatingEndTime())
                .build();
        Festival saved = festivalRepository.save(festival);

        List<TicketType> ticketTypes = request.ticketTypes().stream()
                .map(ticketTypeRequest -> toTicketType(saved, ticketTypeRequest))
                .toList();
        ticketTypeRepository.saveAll(ticketTypes);

        List<FestivalImage> images = new ArrayList<>();
        if (thumbnailImageUrl != null) {
            images.add(FestivalImage.builder().festival(saved).imageUrl(thumbnailImageUrl)
                    .imageType(FestivalImageType.THUMBNAIL).build());
        }
        detailImageUrls.forEach(imageUrl -> images.add(FestivalImage.builder().festival(saved).imageUrl(imageUrl)
                .imageType(FestivalImageType.DETAIL).build()));
        festivalImageRepository.saveAll(images);

        return FestivalResponseDto.from(saved, ticketTypes, images);
    }

    //주최자가 본인이 등록한 페스티벌 목록을 조회한다
    public List<FestivalResponseDto> listMyFestivals(Long hostUserId, String role) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_HOST_ROLE);
        }

        return festivalRepository.findByHostUserId(hostUserId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    //주최자가 본인 페스티벌의 상세 정보를 조회한다
    public FestivalResponseDto getMyFestivalDetail(Long id, Long hostUserId,String role) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_HOST_ROLE);
        }

        Festival festival = getOwnedFestival(id, hostUserId);
        return toResponseDto(festival);
    }

    //Festival 불러오기(내부 메서드)
    private Festival getOwnedFestival(Long id, Long hostUserId) {
        Festival festival = festivalRepository.findById(id)
                .orElseThrow(() -> new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        if (!festival.getHostUserId().equals(hostUserId)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_NOT_OWNER);
        }
        return festival;
    }

    //티켓 종류별 판매 기간·날짜 검증(내부 메서드) — 페스티벌 저장 전에 먼저 확인해 잘못된 요청으로
    //페스티벌·티켓이 절반만 생기는 일이 없게 한다.
    private void validateTicketTypeRequest(TicketTypeRequestDto request, FestivalRequestDto festivalRequest) {
        if (!request.saleEndAt().isAfter(request.saleStartAt())) {
            throw new ApiException(FestivalErrorCode.INVALID_TICKET_SALE_PERIOD);
        }
        if (request.ticketDate() != null) {
            LocalDate festivalStartDate = festivalRequest.startAt().toLocalDate();
            LocalDate festivalEndDate = festivalRequest.endAt().toLocalDate();
            if (request.ticketDate().isBefore(festivalStartDate) || request.ticketDate().isAfter(festivalEndDate)) {
                throw new ApiException(FestivalErrorCode.INVALID_TICKET_DATE);
            }
        }
    }

    //dto로 TicketType으로 생성(내부 메서드)
    private TicketType toTicketType(Festival festival, TicketTypeRequestDto request) {
        return TicketType.builder()
                .festival(festival)
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .totalQuantity(request.quantity())
                .remainQuantity(request.quantity())
                .saleStartAt(request.saleStartAt())
                .saleEndAt(request.saleEndAt())
                .ticketDate(request.ticketDate())
                .build();
    }

    //방문자에게 노출 가능한 상태 — 진행중(PUBLISHED)뿐 아니라 종료(CLOSED)된 것도 "종료됨" 배지로 계속 보여준다
    private static final List<FestivalStatus> VISIBLE_STATUSES = List.of(FestivalStatus.PUBLISHED, FestivalStatus.CLOSED);

    //페스티벌 목록 조회(페이징), 인증 불필요 — 공개(PUBLISHED)·종료(CLOSED) 상태만 노출
    public Page<FestivalResponseDto> listFestivals(Pageable pageable) {
        return festivalRepository.findByFestivalStatusIn(VISIBLE_STATUSES, pageable)
                .map(this::toResponseDto);
    }

    //페스티벌 상세 조회, 인증 불필요 — 공개·종료 상태가 아니면 404(미승인·반려 페스티벌은 존재 자체를 숨김)
    public FestivalResponseDto getFestivalDetail(Long id) {
        Festival festival = festivalRepository.findByIdAndFestivalStatusIn(id, VISIBLE_STATUSES)
                .orElseThrow(() -> new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        return toResponseDto(festival);
    }

    //운영자가 심사할 페스티벌 목록을 조회한다 — 대기뿐 아니라 공개·반려·종료된 것도 이력으로 함께 내려준다.
    //주최자 닉네임은 auth-service에서 한 번에 조회해 붙인다(실패하면 null, 목록은 유지).
    public List<FestivalResponseDto> listPendingFestivals(String role) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
        List<Festival> festivals = festivalRepository.findAllByOrderByCreatedAtDesc();
        Map<Long, UserSummary> hosts = userLookupClient.findByIds(
                festivals.stream().map(Festival::getHostUserId).toList());
        return festivals.stream()
                .map(festival -> FestivalResponseDto.from(
                        festival,
                        ticketTypeRepository.findByFestivalId(festival.getId()),
                        festivalImageRepository.findByFestivalId(festival.getId()),
                        hosts.get(festival.getHostUserId())))
                .toList();
    }

    //운영자가 대기 중인 페스티벌을 공개(PUBLISHED)·반려(REJECTED) 처리한다
    @Transactional
    public FestivalResponseDto reviewFestival(Long id, String role, FestivalReviewRequestDto request) {
        if (!ADMIN_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_ADMIN_ROLE);
        }
        if (request.decision() != FestivalStatus.PUBLISHED && request.decision() != FestivalStatus.REJECTED) {
            throw new ApiException(FestivalErrorCode.INVALID_DECISION);
        }

        Festival festival = festivalRepository.findById(id)
                .orElseThrow(() -> new ApiException(FestivalErrorCode.FESTIVAL_NOT_FOUND));
        if (festival.getFestivalStatus() != FestivalStatus.PENDING) {
            throw new ApiException(FestivalErrorCode.ALREADY_REVIEWED);
        }

        if (request.decision() == FestivalStatus.PUBLISHED) {
            festival.publish();
        } else {
            if (request.rejectReason() == null || request.rejectReason().isBlank()) {
                throw new ApiException(FestivalErrorCode.REJECT_REASON_REQUIRED);
            }
            festival.reject(request.rejectReason().trim());
        }
        return toResponseDto(festival);
    }

    //Festival을 응답 DTO로 조립(내부 메서드) — 티켓 종류·이미지를 함께 조회해 붙인다
    private FestivalResponseDto toResponseDto(Festival festival) {
        return FestivalResponseDto.from(
                festival,
                ticketTypeRepository.findByFestivalId(festival.getId()),
                festivalImageRepository.findByFestivalId(festival.getId())
        );
    }
}
