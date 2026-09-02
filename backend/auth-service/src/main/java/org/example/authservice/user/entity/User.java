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

//    @Column(name = "terms_agree_at", nullable = true)
//    private LocalDateTime termsAgreeAt;  // 회원가입 약관동의
//
//    @Column(name = "failed_login_attempts",nullable = false) //나중에 쓸거
//    private int failedLoginAttempts = 0;  //연속으로 몇번 틀렸는지 확인하는 필드
//
//    @Column(name = "locked_until")      //나중에 쓸거
//    private LocalDateTime lockedUntil;  //로그인 틀리기 몇번 틀리면 몇분간 잠그는 시간 필드
}
