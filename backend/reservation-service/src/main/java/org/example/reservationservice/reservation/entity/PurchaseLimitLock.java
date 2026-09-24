package org.example.reservationservice.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1인당 구매 제한 검사를 (사용자, 페스티벌)마다 한 번에 하나씩만 하게 하는 잠금 행.
 * 같은 사용자가 동시에 예매를 보내면 둘 다 "아직 산 게 없음"을 읽고 한도 검사를 통과할 수 있어서, 합산 전에 이 행을
 * 잠가(SELECT ... FOR UPDATE) 뒤 요청이 앞 요청의 예매까지 보고 검사하게 한다. 수량은 담지 않는다 — 보유 수량은
 * 항상 예매 테이블에서 합산하므로 따로 맞출 값이 없다. 첫 예매 시점에 행이 없으면 그때 만든다.
 */
@Entity
@Table(name = "purchase_limit_locks",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_limit_locks_user_festival", columnNames = {"user_id", "festival_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseLimitLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "festival_id", nullable = false)
    private Long festivalId;

    public PurchaseLimitLock(Long userId, Long festivalId) {
        this.userId = userId;
        this.festivalId = festivalId;
    }
}
