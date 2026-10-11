# howmuch-finance-connector

HowMuch Backend V2와 금융 제공자 사이에서 계좌 거래를 수집하는 별도 서버입니다.

## 기술 스택

- Java 21
- Spring Boot 4.1
- PostgreSQL 18, Flyway
- Gradle 9.5.1

## 로컬 검증

```bash
docker compose -f compose.test.yml up -d
SPRING_PROFILES_ACTIVE=test ./gradlew clean build --no-daemon
docker compose -f compose.test.yml down -v
```

기본 포트는 `8081`이며 인증 없이 공개되는 endpoint는 `GET /actuator/health`뿐입니다.

## 공동인증서 가져오기 앱

Windows 설치 앱은 [`apps/windows-certificate`](apps/windows-certificate/README.md)에서 개발합니다. Node.js 24를 사용하며 Java connector 서버와 별도로 빌드합니다. 인증서 API/CODEF 실행은 현재 Backend API가 담당합니다.

```sh
cd apps/windows-certificate
npm ci
npm start
# Windows 설치 파일
npm run configure -- https://dev.howmuch.wars.p-e.kr
npm run dist:win
```

설치 파일 위치, GitHub Actions 다운로드, 로컬 API 연결, USB 탐색, QR/모바일 승인과 오류 대응은 [앱 빌드·사용 가이드](apps/windows-certificate/README.md)를 확인합니다.
