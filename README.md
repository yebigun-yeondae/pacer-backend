# Pacer Backend Server

보행자 네비게이션 앱 **Pacer**의 Spring Boot 백엔드 서버입니다.
서울시 C-ITS 실시간 신호 데이터와 Valhalla 기반 보행 경로 탐색을 결합해 신호 대기시간까지 고려한 최적 경로를 제공하며, 사용할수록 개인화되는 보행 속도 프로필을 관리합니다.

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| 데이터베이스 | PostgreSQL + PostGIS |
| 캐시 | Redis (경로 캐싱 TTL 5분, Refresh Token 저장) |
| ORM | Spring Data JPA + Hibernate Spatial |
| 인증 | JWT (jjwt 0.12.5) + OAuth2 (Kakao / Google) |
| 외부 API | C-ITS SPAT API (실시간 신호 잔여시간) / AI 서버 (경로 최적화) / 서울 지하철 실시간 도착 API |
| 경로 엔진 | Valhalla (보행 경로 후보 geometry 생성) |
| 테스트 | JUnit 5 + Mockito + Testcontainers |
| 빌드 | Gradle |

## 아키텍처 개요

```
Client ──→ JwtFilter ──→ Controller ──→ Service ──→ Repository ──→ PostgreSQL
                                   │         │
                                   │         ├──→ CitsSpatClient    ──→ C-ITS API
                                   │         ├──→ ValhallaClient    ──→ Valhalla
                                   │         ├──→ AiRouteClient     ──→ AI Server
                                   │         ├──→ SubwayArrivalApiClient ──→ 서울 지하철 API
                                   │         └──→ Redis Cache
                                   │
                              (인증 없음)
                         AuthController ──→ AuthService ──→ OAuth(Kakao/Google)
```

- **JWT 인증**: `JwtFilter`가 `Authorization: Bearer <token>` 헤더를 검증해 `SecurityContext`에 userId(`UUID`) 주입.
- **Redis 캐시**: 경로 탐색 결과를 5분간 캐싱 (`@Cacheable("routes")`). Refresh Token도 Redis에 저장.
- **경로 탐색**: Valhalla로 경로 후보를 생성하고, PostGIS로 경로 상의 교차로/횡단보도를 탐색한 뒤, AI 서버가 C-ITS 신호 데이터를 반영해 최적 경로를 선택.
- **보행 프로필**: 경로 완료 시 EMA(지수이동평균, α=0.3)로 평균 속도 자동 갱신.

## 시작하기

### 요구사항

- Java 17+
- PostgreSQL (PostGIS 확장)
- Redis

### 환경변수 설정

```bash
cp .env.example .env
# .env 파일을 열어 필수 항목 입력
```

### 빌드 및 실행

```bash
# 빌드 (테스트 포함)
./gradlew build

# 빌드 (테스트 제외)
./gradlew build -x test

# 로컬 실행 (dev 프로파일)
./gradlew bootRun
```

서버가 `http://localhost:8080` 에서 실행됩니다.

| URL | 설명 |
|-----|------|
| `http://localhost:8080/api/v1/auth/login` | 로그인 테스트 |
| `http://localhost:8080/actuator/health` | 서버 상태 확인 |
| `http://localhost:8080/actuator/prometheus` | Prometheus 메트릭 |

### Docker Compose

```bash
# 최초 1회: Docker 네트워크 생성
docker network create pacer-network

# 전체 실행
docker compose -f docker-compose.local.yml up -d

# 로그 확인
docker compose -f docker-compose.local.yml logs -f backend
```

> Valhalla는 최초 실행 시 한국 OSM 데이터를 다운로드·빌드하므로 시작까지 수분이 걸릴 수 있습니다.

| 서비스 | 주소 |
|--------|------|
| API 서버 | `http://localhost:80` |
| PostgreSQL | `localhost:5433` |
| Redis | `localhost:6379` |
| Valhalla | `http://localhost:8002` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` or `http://localhost/grafana/` |

## API 엔드포인트

상세 명세는 [docs/API.md](docs/API.md)를 참고하세요.

## 테스트

```bash
# 전체 실행
./gradlew test

# 특정 클래스
./gradlew test --tests "kr.io.pacer.core.service.AuthServiceTest"

# 특정 메서드
./gradlew test --tests "kr.io.pacer.core.service.AuthServiceTest.login_success"
```

| 테스트 유형 | 방식 |
|------------|------|
| 단위 테스트 | `@ExtendWith(MockitoExtension.class)` + Mockito |
| 컨트롤러 테스트 | `@WebMvcTest` + `TestSecurityConfig` (보안 필터 비활성화) |
| 통합 테스트 | `@SpringBootTest` + Testcontainers (실제 PostgreSQL + Redis) |

## 환경변수

| 변수 | 설명 |
|------|------|
| `DB_HOST` | PostgreSQL 호스트 |
| `DB_EXTERNAL_PORT` | PostgreSQL 포트 |
| `DB_NAME` | DB 이름 |
| `DB_USERNAME` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `REDIS_HOST` | Redis 호스트 |
| `REDIS_EXTERNAL_PORT` | Redis 포트 |
| `JWT_SECRET` | JWT 서명 키 (최소 32바이트) |
| `JWT_ACCESS_TOKEN_EXPIRE_MS` | Access Token 만료 시간 (ms) |
| `JWT_REFRESH_TOKEN_EXPIRE_MS` | Refresh Token 만료 시간 (ms) |
| `CITS_API_URL` | C-ITS SPAT API URL |
| `CITS_STATE_API_URL` | C-ITS 신호 상태 API URL |
| `CITS_API_KEY` | C-ITS API 키 |
| `VALHALLA_URL` | Valhalla 서버 URL |
| `AI_API_URL` | AI 경로 최적화 서버 URL |
| `SUBWAY_API_KEY` | 서울 열린데이터 광장 지하철역 API 키 |
| `SUBWAY_ARRIVAL_API_KEY` | 서울 열린데이터 광장 지하철 도착 API 키 |

## 디렉토리 구조

```
backend/
├── build.gradle
├── Dockerfile
├── .env.example
└── src/
    ├── main/
    │   ├── java/kr/io/pacer/core/
    │   │   ├── auth/            # JWT 필터·프로바이더
    │   │   ├── client/          # 외부 HTTP 클라이언트 (CITS, Valhalla, AI, 지하철)
    │   │   ├── config/          # Spring 설정 (Security, Cache, Repository)
    │   │   ├── controller/      # REST API 엔드포인트
    │   │   ├── domain/          # JPA 엔티티 + enums
    │   │   ├── dto/             # 요청/응답/외부 DTO
    │   │   ├── exception/       # 커스텀 예외 + 전역 핸들러
    │   │   ├── filter/          # 로깅 필터
    │   │   ├── repository/      # JPA / JDBC(PostGIS) / Redis
    │   │   ├── scheduler/       # 주기적 작업 (지하철역 동기화, 탈퇴 유저 정리)
    │   │   ├── service/         # 비즈니스 로직
    │   │   ├── util/            # Polyline 인코더
    │   │   └── CoreApplication.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       ├── logback-spring.xml
    │       └── db/
    │           ├── migration/   # Flyway 마이그레이션 (V1~V8)
    │           └── seed/        # Flyway 반복 시딩 (횡단보도, 버스정류장 등)
    └── test/
        └── java/kr/io/pacer/core/
            ├── auth/
            ├── controller/      # @WebMvcTest 슬라이스 테스트
            ├── domain/
            ├── integration/     # Testcontainers 통합 테스트
            └── service/         # Mockito 단위 테스트
```
