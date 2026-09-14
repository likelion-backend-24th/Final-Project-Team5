package org.example.authservice.user.entity;

public enum AccountStatus {
    PENDING_ACTIVATION,
    REVOKED,
    ACTIVE, //활동
    SUSPENDED, //정지
    WITHDRAWN //탈퇴
}
