package org.example.authservice.user.entity;

public enum Role {
    USER,
    HOST,
    //주최자가 자기 페스티벌 현장 입장 검증을 맡기려고 발급하는 임시 계정. 셀프 가입으로는 절대 부여되지 않고,
    //호스트의 도우미 계정 발급 API로만 만들어진다. 페스티벌 종료 후 배치가 자동으로 탈퇴 처리한다.
    HELPER,
    ADMIN
}
