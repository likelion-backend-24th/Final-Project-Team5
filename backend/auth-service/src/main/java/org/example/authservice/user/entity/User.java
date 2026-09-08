package org.example.authservice.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class) //자동으로 시간 들어가게 설정하는 에노테이션
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false,length = 50,unique = true)
    private String username;

    @Column(length = 255, nullable = true) //나중에 OAuth할때를 위해 null허용
    private String password;

    @Column(nullable = false,length = 50,unique = true)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'USER'") //디폴트값
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")  //디폴트값
    private AccountStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    //HELPER 계정이 배정된 페스티벌. 도우미 한 명은 정확히 한 페스티벌만 담당하므로 컬럼 하나로 충분하다.
    //HELPER가 아닌 계정은 항상 null이며, 로그인 시 JWT의 festivalId 클레임으로 실려 Gateway가 X-Festival-Id로 전달한다.
    @Column(name = "festival_id")
    private Long festivalId;

    //HELPER 계정 자동 탈퇴 배치가 쓰는 페스티벌 종료 시각 스냅샷. 배치가 매번 festival-service를 조회하지 않도록
    //계정 발급 시점에 복사해둔다(페스티벌 수정 API가 없어 endAt이 사후에 바뀌지 않는다).
    @Column(name = "festival_end_at")
    private LocalDateTime festivalEndAt;

//    @Column(name = "terms_agree_at", nullable = true)
//    private LocalDateTime termsAgreeAt;  // 회원가입 약관동의

    @Column(name = "failed_login_attempts",nullable = false)
    private int failedLoginAttempts = 0;  //연속으로 몇번 틀렸는지 확인하는 필드

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;  //로그인 몇번 틀리면 몇분간 잠그는 시간 필드
}
