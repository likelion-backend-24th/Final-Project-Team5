# 배포 가이드 (Oracle Cloud A1.Flex)

이 문서는 `docker compose`로 gateway/auth-service/festival-service/reservation-service + core-db를 한 대의 서버에 올리는 절차입니다. payment-service는 아직 구현이 없어 compose에서 제외되어 있습니다.

## 0. 사전 준비 (Oracle 콘솔)

- VCN → Security List(또는 NSG)에서 Ingress 허용: `22/tcp`(SSH, 가능하면 내 IP로 제한), `80/tcp`, `443/tcp`
- 인스턴스 OS 방화벽(Ubuntu 기준):
  ```bash
  sudo ufw allow 22/tcp
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
  sudo ufw enable
  ```
- Docker 설치:
  ```bash
  sudo apt update && sudo apt install -y docker.io docker-compose-plugin git
  sudo systemctl enable --now docker
  sudo usermod -aG docker $USER   # 재로그인 필요
  ```

## 1. 소스 준비 및 빌드

```bash
git clone https://github.com/likelion-backend-24th/Final-Project-Team5.git
cd Final-Project-Team5/backend
./gradlew build -x test
```
`docker compose build`가 각 서비스의 `build/libs/*.jar`를 그대로 이미지에 복사하는 방식이라, **compose를 올리기 전에 반드시 위 빌드를 먼저 실행**해야 합니다.

## 2. 환경 변수 설정

```bash
cd ..   # 저장소 루트
cp .env.example .env
```
`.env`를 열어 아래 값을 실제 배포용으로 교체하세요 (기본값 그대로 배포 금지):

- `JWT_SECRET`
- `INTERNAL_AUTH_TOKEN` — auth-service와 festival-service가 내부 Role 부여 호출에 함께 쓰는 공유 토큰. 두 서비스가 같은 `.env`를 쓰므로 값은 자동으로 일치합니다.
- `AUTH_DB_PASSWORD`, `AUTH_DB_ROOT_PASSWORD`, `FESTIVAL_DB_PASSWORD`, `RESERVATION_DB_PASSWORD`

## 3. 기동

```bash
docker compose up -d --build
docker compose ps
```
컨테이너 간 통신은 서비스명으로 이뤄집니다(`core-db`, `auth-service`, `festival-service`, `reservation-service`, `gateway`) — `docker-compose.yml`의 `environment` 항목이 각 서비스의 DB 호스트/포트와 서비스 간 호출 URL을 로컬 기본값(`localhost`)에서 컨테이너 이름으로 덮어씁니다.

- `nginx`(80번 포트)가 `gateway`(8080)로 프록시합니다. 외부에는 `nginx`만 노출되고, 나머지 컨테이너는 내부 Docker 네트워크에서만 접근됩니다.

## 4. HTTPS 적용 (도메인 필요)

로그인 리프레시 토큰 쿠키가 `Secure` 플래그로 설정되어 있어 **HTTPS가 아니면 브라우저가 쿠키를 거부**합니다. 도메인(또는 무료 서브도메인)을 연결한 뒤:

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```
`docker compose`가 아니라 호스트에 직접 nginx를 하나 더 띄우는 방식이 가장 간단합니다. Compose의 `nginx` 서비스와 포트가 겹치지 않도록, 인증서 발급 후에는 컨테이너 nginx 설정에 443/인증서 경로를 추가하고 볼륨으로 인증서를 마운트하는 방식으로 전환하세요.

## 5. 알려진 제약

- `payment-service`는 `application.yaml`/DB 연동이 없는 스켈레톤 상태라 compose에 포함하지 않았습니다. 구현 완료 후 다른 서비스와 같은 패턴(Dockerfile은 이미 존재)으로 추가하면 됩니다.
- CI/CD 자동 배포는 아직 없습니다. 현재는 서버에 SSH로 접속해 `git pull` → `./gradlew build -x test` → `docker compose up -d --build`를 수동으로 실행합니다.
