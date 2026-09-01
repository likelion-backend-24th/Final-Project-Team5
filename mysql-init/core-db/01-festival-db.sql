-- core-db 인스턴스는 auth_db(MYSQL_DATABASE 환경변수로 자동 생성)와 festival_db를 같이 호스팅합니다.
-- MySQL 공식 이미지는 컨테이너 최초 기동 시 이 폴더의 스크립트를 한 번만 실행하므로,
-- 두 번째 스키마(festival_db)는 여기서 직접 만들어줍니다.
-- 계정/비밀번호는 .env.example의 FESTIVAL_DB_USERNAME/FESTIVAL_DB_PASSWORD 기본값과 맞춰뒀습니다.
-- 로컬에서 .env로 이 값을 바꿨다면, 이 파일도 같이 맞추거나 컨테이너를 재생성(볼륨 삭제 후 재기동)해야 반영됩니다.

CREATE DATABASE IF NOT EXISTS festival_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'festival_user'@'%' IDENTIFIED BY 'festival_password';
GRANT ALL PRIVILEGES ON festival_db.* TO 'festival_user'@'%';

FLUSH PRIVILEGES;
