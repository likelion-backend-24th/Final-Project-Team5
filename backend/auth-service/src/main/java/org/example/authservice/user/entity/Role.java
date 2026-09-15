package org.example.authservice.user.entity;

public enum Role {
    USER,
    HOST,
    //주최자가 자기 페스티벌 현장 입장 검증을 맡기려고 발급하는 임시 계정. 셀프 가입으로는 절대 부여되지 않고,
    //호스트의 도우미 계정 발급 API로만 만들어진다. 페스티벌 종료 후 배치가 자동으로 탈퇴 처리한다.
    HELPER,
    ADMIN,
    //페스티벌 내 부스를 개설·운영하는 계정. 부여 방법(신청·승인 플로우 등)은 아직 정해지지 않았고,
    //지금은 값만 추가해 부스 등록 API가 이 역할만 체크하도록 한다.
    STOREHOST
}
