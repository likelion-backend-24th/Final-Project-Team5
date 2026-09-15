package org.example.festivalservice.domain.tickettype;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.festivalservice.domain.festival.Festival;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table(name = "ticket_types")
public class TicketType {
    @Id@GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "festival_id")
    private Festival festival;

    private String name;

    //구매 화면에서 티켓 이름 아래 한 줄로 보여주는 설명(선택)
    @Column(name = "description", length = 50)
    private String description;

    private int price;

    //이 티켓 종류가 좌석 선택형인지 스탠딩(수량제)인지 구분한다.
    //STANDING이면 zone/rows/seatsPerRow는 null이고 기존 remainQuantity 차감 로직을 그대로 쓴다.
    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_mode", columnDefinition = "VARCHAR(20)")
    private TicketMode ticketMode;

    //구역명(예: "VIP", "일반") — 좌석맵에서 이 구역 단위로 묶어 보여준다
    @Column(name = "zone")
    private String zone;
    //좌석 배치 — SEATED일 때만 값이 있다. rows × seatsPerRow = totalQuantity
    //컬럼명은 seat_rows로 매핑한다 — MySQL 8.0.19부터 ROWS가 예약어(FETCH ... ROWS ONLY)라 그대로
    //"rows"로 두면 ddl-auto: update가 컬럼 추가 DDL을 예약어 충돌로 조용히 실패시킨다(실제 운영 장애,
    //2026-09-15). Java 필드명·API 응답 필드명은 그대로 rows로 유지해 계약은 안 바뀐다.
    @Column(name = "seat_rows")
    private Integer rows;

    @Column(name = "seats_per_row")
    private Integer seatsPerRow;

    //총 수량 — STANDING이면 원자적 차감 대상, SEATED면 좌석 배치 설정값(참고용)
    @Column(name = "total_quantity")
    private int totalQuantity;

    //잔여 수량 — STANDING에서만 원자적으로 감소. SEATED는 더 이상 감소시키지 않는다(죽은 필드).
    @Column(name = "remain_quantity")
    private int remainQuantity;

    //판매 시작·종료 일시. 이 구간 밖이면 예매 신청을 막는다(reservation-service가 검증).
    @Column(name = "sale_start_at")
    private LocalDateTime saleStartAt;

    @Column(name = "sale_end_at")
    private LocalDateTime saleEndAt;

    //이틀 이상 지속되는 페스티벌에서 "몇째 날" 티켓인지 표시하는 날짜(선택). 하루짜리 페스티벌이거나
    //날짜 구분이 필요 없는 티켓(전체 기간 통용권 등)은 null로 둔다.
    @Column(name = "ticket_date")
    private LocalDate ticketDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private boolean isDeleted;
}
