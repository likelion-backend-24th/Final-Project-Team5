package org.example.festivalservice.domain.festival;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.festivalservice.common.UserLookupClient;
import org.example.festivalservice.common.UserLookupClient.UserSummary;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.tickettype.TicketMode;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.example.festivalservice.domain.tickettype.TicketTypeRepository;
import org.example.festivalservice.domain.tickettype.TicketTypeRequestDto;
import org.example.festivalservice.infrastructure.reservation.ReservationServiceClient;
import org.example.festivalservice.infrastructure.reservation.dto.SeatGenerationRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class FestivalService {

    private static final String HOST_ROLE = "HOST";
    private static final String ADMIN_ROLE = "ADMIN";

    private static final int MAX_DETAIL_IMAGE_COUNT = 2;
    private static final Logger log = LoggerFactory.getLogger(FestivalService.class);

    private final FestivalRepository festivalRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final FestivalImageRepository festivalImageRepository;
    private final UserLookupClient userLookupClient;
    private final ReservationServiceClient reservationServiceClient;

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
        request.ticketTypes().forEach(ticketTypeRequest -> {
            validateTicketTypeRequest(ticketTypeRequest, request);
            validateTicketTypeLayout(ticketTypeRequest);
        });

        Festival festival = Festival.builder()
                .hostUserId(hostUserId)
                .name(request.name())
                .description(request.description())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .region(request.region())
                .locationDetail(request.locationDetail())
                .festivalCategory(request.festivalCategory())
                .stageLayout(request.stageLayout())
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
        //SEATED는 seatLayout에서 실제 생성될 좌석 수를 계산, STANDING은 요청받은 quantity를 그대로 쓴다.
        int quantity = request.ticketMode() == TicketMode.SEATED
                ? request.seatLayout().totalSeatCount()
                : request.quantity();

        return TicketType.builder()
                .festival(festival)
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .ticketMode(request.ticketMode())
                .zone(request.zone())
                .seatLayout(request.seatLayout())
                .positionRow(request.positionRow())
                .positionCol(request.positionCol())
                .positionAngle(request.positionAngle())
                .totalQuantity(quantity)
                .remainQuantity(quantity)
                .saleStartAt(request.saleStartAt())
                .saleEndAt(request.saleEndAt())
                .ticketDate(request.ticketDate())
                .build();
    }

    //SEATED면 zone·seatLayout이 채워져 있고 각 행의 seatCount>0, excludedSeats가 그 행의 범위(1~seatCount) 안이어야 한다.
    //STANDING은 이 필드들을 검증하지 않는 대신 quantity가 채워져 있어야 한다.
    private void validateTicketTypeLayout(TicketTypeRequestDto request) {
        if (request.ticketMode() == TicketMode.SEATED) {
            if (request.zone() == null || request.zone().isBlank() || request.seatLayout() == null
                    || request.seatLayout().rows() == null || request.seatLayout().rows().isEmpty()) {
                throw new ApiException(FestivalErrorCode.INVALID_SEAT_LAYOUT);
            }
            boolean invalidRow = request.seatLayout().rows().stream().anyMatch(row ->
                    row.seatCount() <= 0
                            || row.excludedSeats().stream().anyMatch(seatNumber -> seatNumber < 1 || seatNumber > row.seatCount()));
            if (invalidRow) {
                throw new ApiException(FestivalErrorCode.INVALID_SEAT_LAYOUT);
            }
            //STANDING 전용 필드가 SEATED에 섞여 들어오면 데이터 혼란을 막기 위해 거부한다.
            if (request.quantity() != null) {
                throw new ApiException(FestivalErrorCode.INVALID_SEAT_LAYOUT);
            }
            return;
        }

        //STANDING인데 좌석 필드가 섞여 들어오면 데이터 혼란을 막기 위해 거부한다.
        boolean hasSeatFields = request.zone() != null || request.seatLayout() != null;
        if (hasSeatFields) {
            throw new ApiException(FestivalErrorCode.INVALID_SEAT_LAYOUT);
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new ApiException(FestivalErrorCode.INVALID_SEAT_LAYOUT);
        }
    }

    //상세에서 방문자에게 노출 가능한 상태 — 종료(CLOSED)된 것도 예약 내역에서 타고 들어올 수 있게 "종료됨" 배지로 보여준다.
    //취소 진행 중·취소된 행사도 이미 결제한 참가자가 내 예매에서 이름·환불 안내를 봐야 하므로 상세는 열어 둔다.
    //목록은 진행중(PUBLISHED)만 노출한다(종료·취소된 행사가 목록을 차지하지 않도록).
    private static final List<FestivalStatus> VISIBLE_STATUSES = List.of(FestivalStatus.PUBLISHED, FestivalStatus.CLOSED,
            FestivalStatus.CANCELLATION_PENDING, FestivalStatus.CANCELLED);

    //페스티벌 목록 조회(페이징), 인증 불필요 — 공개(PUBLISHED) 상태만 노출
    public Page<FestivalResponseDto> listFestivals(Pageable pageable) {
        return festivalRepository.findByFestivalStatus(FestivalStatus.PUBLISHED, pageable)
                .map(this::toResponseDto);
    }

    //페스티벌 상세 조회, 인증 불필요 — 공개·종료·취소 상태가 아니면 404(미승인·반려 페스티벌은 존재 자체를 숨김)
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
            //PENDING이면 먼저 PUBLISH_PENDING으로 확정 저장한 뒤 reservation-service에 좌석 생성을 요청한다.
            //HostApplicationService.review()의 APPROVAL_PENDING 패턴과 동일하다.
            festival.markPublishPending();
            festivalRepository.save(festival);
            try {
                generateSeatsForFestival(festival);
                festival.publish();
                festivalRepository.save(festival);
            } catch (RestClientException e) {
                //reservation-service 응답 유실·Timeout — PUBLISH_PENDING 상태로 안전하게 남겨두고
                //FestivalPublishRetryScheduler가 자동으로 재시도한다.
                log.warn("좌석 생성 응답을 받지 못해 페스티벌 {}을 PUBLISH_PENDING으로 둔다(자동 재시도 예정)",
                        festival.getId(), e);
            }
        } else {
            if (request.rejectReason() == null || request.rejectReason().isBlank()) {
                throw new ApiException(FestivalErrorCode.REJECT_REASON_REQUIRED);
            }
            festival.reject(request.rejectReason().trim());
        }
        return toResponseDto(festival);
    }

    //PUBLISH_PENDING에 머문 페스티벌을 다시 승인 처리한다. 좌석 생성은 ticketTypeId 단위로 멱등하므로
    //이미 생성된 티켓타입에 재요청해도 안전하다.
    public int retryPendingPublishes(LocalDateTime olderThan) {
        int healed = 0;
        for (Festival festival : festivalRepository.findByFestivalStatusAndUpdatedAtBefore(
                FestivalStatus.PUBLISH_PENDING, olderThan)) {
            try {
                generateSeatsForFestival(festival);
                festival.publish();
                festivalRepository.save(festival);
                healed++;
                log.info("PUBLISH_PENDING 페스티벌 {} 승인 확정(재시도 성공)", festival.getId());
            } catch (RestClientException e) {
                log.warn("PUBLISH_PENDING 페스티벌 {} 재시도 실패 — 다음 회차에 다시 시도", festival.getId(), e);
            }
        }
        return healed;
    }

    //Festival-Service → Reservation-Service 내부 호출(내부 메서드): SEATED 티켓타입에 대해서만
    //좌석 생성을 요청한다. STANDING은 좌석 개념이 없으므로 호출하지 않는다.
    private void generateSeatsForFestival(Festival festival) {
        List<TicketType> seatedTicketTypes = ticketTypeRepository.findByFestivalId(festival.getId()).stream()
                .filter(ticketType -> ticketType.getTicketMode() == TicketMode.SEATED)
                .toList();
        for (TicketType ticketType : seatedTicketTypes) {
            reservationServiceClient.generateSeats(new SeatGenerationRequestDto(
                    festival.getId(),
                    ticketType.getId(),
                    ticketType.getZone(),
                    ticketType.getSeatLayout()
            ));
        }
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
