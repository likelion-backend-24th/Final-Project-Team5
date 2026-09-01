-- reservation_db도 같은 core-db 인스턴스(포트 3307) 안의 별도 스키마로 통합한다.
-- 계정/비밀번호는 .env.example의 RESERVATION_DB_USERNAME/RESERVATION_DB_PASSWORD 기본값과 맞춰뒀습니다.
-- 로컬에서 .env로 이 값을 바꿨다면, 이 파일도 같이 맞추거나 컨테이너를 재생성(볼륨 삭제 후 재기동)해야 반영됩니다.

CREATE DATABASE IF NOT EXISTS reservation_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'reservation_user'@'%' IDENTIFIED BY 'reservation_password';
GRANT ALL PRIVILEGES ON reservation_db.* TO 'reservation_user'@'%';

FLUSH PRIVILEGES;
