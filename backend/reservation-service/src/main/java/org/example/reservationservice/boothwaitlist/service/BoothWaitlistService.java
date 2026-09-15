package org.example.reservationservice.boothwaitlist.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.boothwaitlist.dto.BoothWaitlistResponseDto;
import org.example.reservationservice.boothwaitlist.entity.BoothWaitlist;
import org.example.reservationservice.boothwaitlist.entity.BoothWaitlistCounter;
import org.example.reservationservice.boothwaitlist.exception.BoothWaitlistErrorCode;
import org.example.reservationservice.boothwaitlist.repository.BoothWaitlistCounterRepository;
import org.example.reservationservice.boothwaitlist.repository.BoothWaitlistRepository;
import org.example.reservationservice.common.exception.ApiException;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.infrastructure.festival.dto.BoothDetailResponseDto;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BoothWaitlistService {

    private static final String OPEN_STATUS = "OPEN";

    //대기 신청 자격으로 인정할 예매 상태 — 입장 검증과 같은 기준. 부분 환불된 예매도 남은 장수만큼은
    //여전히 참가자이므로 포함한다.
    private static final List<ReservationStatus> TICKET_HOLDER_STATUSES =
            List.of(ReservationStatus.CONFIRMED, ReservationStatus.PARTIALLY_REFUNDED);

    private final BoothWaitlistRepository boothWaitlistRepository;
    private final BoothWaitlistCounterRepository boothWaitlistCounterRepository;
    private final ReservationRepository reservationRepository;
    private final FestivalServiceClient festivalServiceClient;

    //참가자가 부스 대기 신청을 한다: 부스 존재·공개 여부 확인 → 이 페스티벌 티켓 보유 확인 →
    //중복 신청 확인 → 대기번호를 원자적으로 발급.
    @Transactional
    public BoothWaitlistResponseDto requestWaitlist(Long userId, Long boothId) {
        BoothDetailResponseDto booth = getBoothOrThrow(boothId);
        if (!OPEN_STATUS.equals(booth.boothStatus())) {
            throw new ApiException(BoothWaitlistErrorCode.BOOTH_NOT_OPEN);
        }
        if (!reservationRepository.existsByUserIdAndFestivalIdAndReservationStatusIn(
                userId, booth.festivalId(), TICKET_HOLDER_STATUSES)) {
            throw new ApiException(BoothWaitlistErrorCode.TICKET_NOT_FOUND);
        }
        //DB 유니크 제약(booth_id, user_id)이 최종 방어선이지만, 사용자에게 명확한 에러코드를
        //돌려주기 위해 여기서 먼저 확인한다.
        if (boothWaitlistRepository.existsByBoothIdAndUserId(boothId, userId)) {
            throw new ApiException(BoothWaitlistErrorCode.ALREADY_REQUESTED);
        }

        int queueNumber = nextQueueNumber(boothId);
        BoothWaitlist waitlist = new BoothWaitlist(boothId, booth.festivalId(), userId, queueNumber);
        try {
            return BoothWaitlistResponseDto.from(boothWaitlistRepository.save(waitlist));
        } catch (DataIntegrityViolationException e) {
            //위에서 통과했어도 동시에 같은 사용자가 두 번 요청하면 유니크 제약에 걸린다 — 마지막 방어선.
            throw new ApiException(BoothWaitlistErrorCode.ALREADY_REQUESTED);
        }
    }

    //내 대기번호 조회
    public BoothWaitlistResponseDto getMyWaitlist(Long userId, Long boothId) {
        BoothWaitlist waitlist = boothWaitlistRepository.findByBoothIdAndUserId(boothId, userId)
                .orElseThrow(() -> new ApiException(BoothWaitlistErrorCode.WAITLIST_NOT_FOUND));
        return BoothWaitlistResponseDto.from(waitlist);
    }

    //다음 대기번호를 원자적으로 발급한다(내부 메서드).
    private int nextQueueNumber(Long boothId) {
        ensureCounterExists(boothId);
        boothWaitlistCounterRepository.increment(boothId);
        //같은 트랜잭션 안에서의 조회라 방금 반영한 증가값을 그대로 읽는다(다른 트랜잭션의 커밋을
        //기다릴 필요 없음 — 자기 자신이 쓴 값은 커밋 전에도 항상 보인다).
        return boothWaitlistCounterRepository.findById(boothId)
                .orElseThrow(() -> new IllegalStateException("방금 만든 카운터를 찾을 수 없습니다: boothId=" + boothId))
                .getNextNumber();
    }

    //카운터 행이 없으면 만든다(내부 메서드) — 최초 신청 시점에만 실행된다.
    private void ensureCounterExists(Long boothId) {
        if (boothWaitlistCounterRepository.existsById(boothId)) {
            return;
        }
        try {
            boothWaitlistCounterRepository.save(new BoothWaitlistCounter(boothId));
        } catch (DataIntegrityViolationException e) {
            //동시에 다른 요청이 먼저 만든 경우 — 이미 있으니 무시하고 진행한다.
        }
    }

    //Booth 불러오기(내부 메서드) — festival-service의 공개 상세 API를 그대로 쓴다(WAITING은 거기서 404로 숨겨준다).
    private BoothDetailResponseDto getBoothOrThrow(Long boothId) {
        try {
            BoothDetailResponseDto booth = festivalServiceClient.getBooth(boothId);
            if (booth == null) {
                throw new ApiException(BoothWaitlistErrorCode.BOOTH_NOT_FOUND);
            }
            return booth;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ApiException(BoothWaitlistErrorCode.BOOTH_NOT_FOUND);
        } catch (RestClientException e) {
            throw new ApiException(BoothWaitlistErrorCode.FESTIVAL_SERVICE_UNAVAILABLE);
        }
    }
}
