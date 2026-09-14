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
    //총 수량
    @Column(name = "total_quantity")
    private int totalQuantity;
    //잔여 수량
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
