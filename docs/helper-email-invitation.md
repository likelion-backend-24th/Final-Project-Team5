# 도우미 이메일 초대와 계정 활성화

## 사용자 흐름

HOST는 본인 행사 상세 화면에서 연락 이메일을 입력한다. Auth Service는 별도의 HELPER 계정을 PENDING_ACTIVATION으로 만들고 로그인 아이디(helper-...@helper.local)를 확정한다. 연락 이메일은 로그인 아이디나 일반 회원 계정과 별도로 관리한다.

수신자는 /helper-invite/:token에서 행사 이름·일정, 발급 아이디, 마스킹된 이메일을 확인하고 비밀번호를 설정한다. 활성화는 기존 로그인과 같은 TokenSessionService와 AuthCookieResponseBuilder를 사용한다. access token은 응답 body, refresh token은 Secure·HttpOnly·SameSite=Strict 쿠키로 전달한다. 화면은 토큰을 적용한 뒤 /api/users/me를 조회하고 /로 replace 이동한다. 공개 전 행사도 초대의 행사 스냅샷으로 HELPER 홈에 바로 표시한다.

기존 계정으로 열린 브라우저에는 전환 안내를 표시한다. 성공하면 기존 브라우저 refresh cookie 세션을 폐기하고 새 HELPER 세션으로 교체한다. 이미 활성화한 HELPER에는 일반 로그인에서 발급 아이디와 직접 설정한 비밀번호를 사용한다.

## 소유 데이터와 상태

- Auth Service: users, helper_invitations, helper_invitation_sends, refresh_token.
- Festival Service: HOST 역할·실제 행사 소유권 검증, 행사 ID·이름·시작·종료 시각·이메일 전달만 담당한다.
- users.status에 PENDING_ACTIVATION, REVOKED를 추가한다. 신규 도우미의 password는 null이며 활성화 후 BCrypt 해시와 ACTIVE 상태를 저장한다.
- helper_invitations: helper_user_id(일대일 FK), festival_id, festival_name, festival_start_at, normalized_email, token_hash, status, delivery_status, expires_at, sent_at, last_sent_at, accepted_at, revoked_at, resend_count, created_at, updated_at.
- (festival_id, normalized_email) UNIQUE로 동일 이메일·행사의 중복 생성 경합을 차단한다. 같은 이메일을 다른 행사에 초대하면 별도 계정을 만든다. 해지한 행도 삭제 시점까지 중복 제약에 포함된다.
- 초대 PENDING → ACCEPTED 또는 REVOKED. 만료는 expires_at으로 판단하고 목록에서 EXPIRED로 표시한다. GET은 상태·타임스탬프를 갱신하지 않는다. 만료된 PENDING 초대는 행사 종료 전 재발송할 수 있다.
- delivery_status는 SENDING, SENT, SEND_FAILED로 구분한다. 활성화·해지 상태와 독립적이다.
- helper_invitation_sends는 동일 초대의 최근 24시간 발송 시도 시각을 보관한다. 최초 발송과 실패를 포함해 최대 10회, 최근 시도 후 60초 cooldown을 적용한다. resend_count는 누적 재발송 횟수다.

## API

HOST API는 모두 X-User-Id/X-User-Role을 통해 행사 소유권을 확인한다.

| Method | Path | 요청/결과 |
|---|---|---|
| POST | /api/host/festivals/{festivalId}/helpers | { email }, 초대 발송 |
| GET | /api/host/festivals/{festivalId}/helpers | 아이디·전체 연락 이메일·상태·발송/만료 시각·legacy 목록 |
| POST | /api/host/festivals/{festivalId}/helpers/{helperUserId}/resend | 새 토큰으로 재발송 |
| POST | /api/host/festivals/{festivalId}/helpers/{helperUserId}/invitation | { email }, 기존 계정 이메일 초대 전환 |
| DELETE | /api/host/festivals/{festivalId}/helpers/{helperUserId} | 초대·계정·세션 해지 |
| GET | /api/auth/helper-invitations/{token} | 읽기 전용 초대 확인, 개인정보 최소화 |
| POST | /api/auth/helper-invitations/{token}/accept | { password, passwordConfirm }, 활성화와 자동 로그인 |

Festival → Auth 내부 API는 /internal/v1/helper-accounts 아래에서 같은 create/list/resend/invitation/delete 의미를 사용한다. 내부 요청에는 기존 INTERNAL_AUTH_TOKEN Bearer 인증이 필요하다. Gateway는 /internal/v1/helper-accounts/session?userId=...&festivalId=...&version=...을 내부 인증으로 호출하며 외부 라우팅은 제공하지 않는다.

기존 POST .../helpers/{helperUserId}/password 및 내부 password API는 제거했다. CredentialResponse와 무작위 비밀번호 생성·표시 상태도 제거했다. HelperCredentialGenerator는 아이디·닉네임 전용 HelperIdentityGenerator로 변경했다.

## 트랜잭션·보안

- SecureRandom 32바이트 URL-safe 토큰을 생성하고 SHA-256 digest만 저장한다. 원문은 SMTP 링크 작성에만 사용하며 HOST/공개 DTO, DB, 예외 및 로그에 포함하지 않는다.
- 초대 만료는 생성·재발송 시점 +24시간과 행사 종료 중 빠른 시각이다. 행사 시각은 APP_TIMEZONE(기본 Asia/Seoul)을 기준으로 비교한다.
- 계정과 초대를 독립 트랜잭션으로 먼저 커밋한 뒤 SMTP를 실행한다. 실패는 별도 트랜잭션으로 SEND_FAILED를 기록하고 502 INVITATION_SEND_FAILED를 반환한다. HOST는 목록을 재조회하여 복구할 수 있다.
- SMTP 직전/직후 프로세스가 종료되면 SENDING이 남을 수 있다. 60초 이후 HOST가 재발송할 수 있으며 이전 토큰은 교체된다. 자동 재시도용 원문 토큰을 저장하지 않는다.
- 수락·재발송·해지·로그인 성공·refresh는 계정 행 잠금으로 충돌을 제어한다. 수락은 잠금 획득 뒤 토큰을 다시 조회한다. GET은 토큰을 소비하지 않아 메일 보안 스캐너가 열어도 안전하다.
- 활성화·해지는 users.helper_session_version을 증가시키고 기존 refresh token을 폐기한다. JWT와 refresh_token에도 발급 당시 버전을 기록한다. 이전 버전은 같은 초에 발급한 토큰이라도 거부한다. 구형 토큰의 버전 누락은 0으로 해석한다.
- Gateway의 HELPER allowlist와 festivalId claim은 유지한다. 허용된 보호 API마다 Auth의 계정 상태·행사 종료·세션 버전을 검사한다. Auth 연결 실패/시간 초과 시 요청을 거부한다. 공개 행사 조회와 공개 인증 API는 기존 공개 정책을 유지한다.
- PENDING/REVOKED/종료된 행사 HELPER는 로그인·refresh·토큰 발급을 거부한다. 일반 회원가입과 비밀번호 재설정으로 HELPER 활성화를 우회할 수 없다.
- 최소 8자 정책을 공유하고 활성화 비밀번호는 BCrypt의 UTF-8 72바이트 한계까지 검증한다. HTML 동적 문자열을 escape한다. 응답은 no-store, 페이지는 no-referrer 정책을 사용한다.
- 두 Nginx 설정은 초대 URL을 REDACTED로 기록하고 Referrer는 기록하지 않는다. 초대 경로의 URI 포함 오류 로그는 비활성화한다. DB bind 값 및 SQL 예외 상세 로그도 기록하지 않는다. 별도 프록시/APM을 추가한다면 동일한 URL·본문 마스킹이 필요하다.

오류코드는 INVITATION_INVALID/EXPIRED/ACCEPTED/REVOKED, HELPER_FESTIVAL_ENDED, INVITATION_DUPLICATE/COOLDOWN/SEND_LIMIT/SEND_FAILED, HELPER_PENDING_ACTIVATION, HELPER_SESSION_REVOKED 등으로 구분한다. 재발송 전 링크는 INVITATION_INVALID가 된다.

## 기존 계정·개인정보 수명

운영 데이터를 삭제하거나 호환 불필요를 가정하지 않는다. 연락 이메일 없는 기존 HELPER는 legacy로 표시하고 행사 종료 전까지 기존 비밀번호 로그인을 유지한다. HOST가 이메일 전환 링크를 보낸 시점에는 비밀번호를 바꾸지 않는다. 수락할 때만 비밀번호·세션을 교체한다. 현재 실제 운영 데이터는 조회하지 않았다.

기존 스케줄러의 행사 종료 +24시간 hard delete 정책과 10분 실행 간격을 유지한다. ACTIVE/PENDING_ACTIVATION/REVOKED 등 상태와 무관하게 만료 도우미를 찾는다. invitation 발송 이력 → invitation → refresh token → user 순서로 삭제하여 연락 이메일도 남지 않는다. 종료 즉시 로그인·인증 이용은 중지하고 개인정보 삭제는 24시간 유예 후 수행한다.

## 스키마와 적용 전 확인

기존 JPA ddl-auto=update를 유지하며 별도 마이그레이션 프레임워크를 도입하지 않는다. 신규 테이블 두 개, users/refresh_token의 helper_session_version(nullable BIGINT), users.status의 VARCHAR(20) 및 새 상태 값이 필요하다. 기존 session version null은 0과 호환된다.

MySQL 기존 status 컬럼이 native ENUM이면 ddl-auto만으로 enum 값 확장이 보장되지 않는다. 적용 담당자는 백업과 스키마 확인 후 users.status가 VARCHAR(20)인지 확인하고, 필요하면 유지보수 절차에서 다음 DDL을 적용한다. 이 작업에서 운영 DB 변경은 실행하지 않았다.

    ALTER TABLE users MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

Auth의 새 스키마·내부 API를 먼저 준비하고 Gateway/Festival/Frontend를 호환되는 버전으로 함께 전환해야 한다. Gateway는 INTERNAL_AUTH_TOKEN과 AUTH_SERVICE_URL이 필요하며 Auth 장애 시 HELPER 체크인을 거부한다. FRONTEND_URL, MAIL_USERNAME, MAIL_APP_PASSWORD, APP_TIMEZONE을 확인한다. SMTP 실제 수신은 별도 검증 환경에서 수행한다. Secure 쿠키이므로 HTTPS 환경 또는 브라우저의 localhost 보안 예외를 사용하는 기존 조건을 유지한다.

## 검증 명령

    cd backend
    ./gradlew :auth-service:test :festival-service:test :gateway:test
    ./gradlew test
    cd ../frontend
    npm ci
    npm run test
    npm run lint
    npm run build

HelperAccountServiceTest는 기본 H2 DB를 사용한다. 격리된 MySQL을 검증할 때만 HELPER_TEST_DB_URL, HELPER_TEST_DB_DRIVER=com.mysql.cj.jdbc.Driver, HELPER_TEST_DB_USER, HELPER_TEST_DB_PASSWORD를 설정하고 해당 테스트 클래스를 실행한다. 테스트는 create-drop을 사용하므로 운영·공유 DB를 지정하면 안 된다.

검증 범위는 DB/HTTP 활성화·쿠키, 중복 및 동시 수락, 재발송 제한, 메일 실패 복구, 권한·오류 매핑, 기존 계정 전환, FK 삭제, HTML escape, 화면 상태·비밀번호 검증·자동 로그인·공개 라우트·HELPER 홈이다. 메일 테스트는 모의 SMTP 발송을 사용한다.
