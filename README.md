# Pacer Backend Server

보행자 네비게이션 앱 **Pacer**의 Spring Boot 백엔드 서버입니다.
서울시 C-ITS 실시간 신호 데이터와 pgRouting 기반 보행 경로 탐색을 결합해 신호 대기시간까지 고려한 최적 경로를 제공하며, 사용할수록 개인화되는 보행 속도 프로필을 관리합니다.

## 기술 스택

| 항목 | 내용 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.5 |
| 데이터베이스 | PostgreSQL + PostGIS + pgRouting |
| 캐시 | Redis (TTL 5분) |
| ORM | Spring Data JPA + Hibernate Spatial |
| 인증 | JWT (jjwt 0.12.5) + OAuth2 (Kakao / Google) |
| 외부 API | C-ITS SPAT API (실시간 신호 잔여시간) |
| 경로 엔진 | Valhalla (보행 geometry) + pgr_aStar |
| 테스트 | JUnit 5 + Mockito + Testcontainers |
| 빌드 | Gradle |

## 아키텍처 개요

```
Client ──→ JwtFilter ──→ Controller ──→ Service ──→ Repository ──→ PostgreSQL
                                   │         │
                                   │         ├──→ CitsSpatClient ──→ C-ITS API
                                   │         ├──→ ValhallaClient ──→ Valhalla
                                   │         └──→ Redis Cache
                                   │
                              (인증 없음)
                         AuthController ──→ AuthService ──→ OAuth(Kakao/Google)
```

- **JWT 인증**: `JwtFilter`가 `Authorization: Bearer <token>` 헤더를 검증해 `SecurityContext`에 userId(`UUID`) 주입.
- **Redis 캐시**: 경로 탐색 결과를 5분간 캐싱 (`@Cacheable("routes")`). Refresh Token도 Redis에 저장.
- **경로 탐색**: pgRouting `pgr_aStar`에 사용자 보행 속도와 DB 함수 `signal_wait_seconds`를 주입해 신호 대기시간 포함 최단 경로 계산.
- **보행 프로필**: 경로 완료 시 EMA(지수이동평균, α=0.3)로 평균 속도 자동 갱신.

## 시작하기

### 요구사항

- Java 17+
- PostgreSQL (PostGIS + pgRouting 확장)
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

### Docker (운영 배포)

```bash
# JAR 빌드
./gradlew clean build -x test

# EC2로 전송
scp -i [키경로] build/libs/core-0.0.1-SNAPSHOT.jar ubuntu@[EC2_IP]:/home/ubuntu/pacer

# 운영 프로파일로 실행
java -jar -Dspring.profiles.active=prod core-0.0.1-SNAPSHOT.jar
```

## API 엔드포인트

> 🔒 표시된 엔드포인트는 `Authorization: Bearer <accessToken>` 헤더 필요

---

### 인증 `/api/v1/auth`

#### `POST /api/v1/auth/signup` — 일반 회원가입

**요청**
```json
{
  "email": "user@example.com",
  "password": "password123!",
  "nickname": "홍길동"
}
```

**응답** `201 Created`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

#### `POST /api/v1/auth/login` — 일반 로그인

**요청**
```json
{
  "email": "user@example.com",
  "password": "password123!"
}
```

**응답** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

#### `POST /api/v1/auth/kakao` — 카카오 로그인

**요청**
```json
{ "accessToken": "<카카오 액세스 토큰>" }
```

**응답** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

#### `POST /api/v1/auth/google` — 구글 로그인

**요청**
```json
{ "accessToken": "<구글 액세스 토큰>" }
```

**응답** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

#### `POST /api/v1/auth/reissue` — Access Token 재발급

**헤더**: `Refresh-Token: <refreshToken>`

**응답** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

#### `POST /api/v1/auth/logout` 🔒 — 로그아웃

**헤더**: `Refresh-Token: <refreshToken>`

**응답** `204 No Content`

---

#### `DELETE /api/v1/auth/withdraw` 🔒 — 회원탈퇴

**헤더**: `Refresh-Token: <refreshToken>`

**응답** `204 No Content`

---

### 경로 `/api/v1/routes`

#### `POST /api/v1/routes` 🔒 — 경로 탐색

**요청**
```json
{
  "origin": { "lat": 37.5012, "lng": 127.0396 },
  "destination": { "lat": 37.5045, "lng": 127.0480 },
  "originName": "강남역",
  "destinationName": "선릉역",
  "mode": "BALANCED",
  "waypoints": []
}
```

> `mode`: `BALANCED` (기본) | `FASTEST` | `SAFEST`

**응답** `200 OK`
```json
{
  "polyline": "wkysEkodfV}@...",
  "totalTimeSeconds": 412,
  "totalDistanceMeters": 520.3,
  "signalCheckpoints": [
    {
      "nodeId": 1023,
      "lat": 37.5021,
      "lng": 127.0410,
      "etaFromStartSeconds": 95,
      "signalState": "GREEN",
      "recommendedPace": "NORMAL"
    }
  ],
  "intersectionSignals": [
    {
      "itstId": 5501,
      "name": "강남역사거리",
      "lat": 37.5021,
      "lng": 127.0410,
      "ntPdsgRmdrCs": 23.0,
      "ntPdsgStatNm": "보행자녹색"
    }
  ]
}
```

---

#### `GET /api/v1/routes/history` 🔒 — 경로 이용 이력 조회

**쿼리 파라미터**: `page=0&size=20` (기본값)

**응답** `200 OK`
```json
[
  {
    "id": "uuid",
    "originName": "강남역",
    "destinationName": "선릉역",
    "totalTimeSeconds": 412,
    "totalDistanceMeters": 520.3,
    "signalStops": 1,
    "createdAt": "2026-05-17T10:30:00"
  }
]
```

---

### 프로필 `/api/v1/profile`

#### `GET /api/v1/profile` 🔒 — 내 프로필 조회

**응답** `200 OK`
```json
{
  "nickname": "홍길동",
  "email": "user@example.com",
  "profileImageUrl": "https://...",
  "avgSpeedMps": 1.46,
  "totalRoutes": 13,
  "totalDistanceM": 6890.5
}
```

---

#### `PATCH /api/v1/profile/walking-speed` 🔒 — 걸음 속도 업데이트

**요청**
```json
{ "speedMps": 1.55 }
```

**응답** `204 No Content`

---

### 즐겨찾기 `/api/v1/favorites`

#### `GET /api/v1/favorites` 🔒 — 즐겨찾기 목록 조회

**응답** `200 OK`
```json
[
  {
    "id": "uuid",
    "name": "회사",
    "lat": 37.5012,
    "lng": 127.0396,
    "visitCount": 42
  }
]
```

---

#### `POST /api/v1/favorites` 🔒 — 즐겨찾기 추가

**요청**
```json
{
  "name": "회사",
  "lat": 37.5012,
  "lng": 127.0396
}
```

**응답** `201 Created`

---

#### `DELETE /api/v1/favorites/{id}` 🔒 — 즐겨찾기 삭제

**응답** `204 No Content`

---

### 신호 `/api/v1/signal`

#### `GET /api/v1/signal?itstIds=5501,5502` — 교차로 신호 정보 조회

**응답** `200 OK`
```json
[
  {
    "itstId": 5501,
    "name": "강남역사거리",
    "ntPdsgRmdrCs": 23.0,
    "ntPdsgStatNm": "보행자녹색"
  }
]
```

---

### 버스 정류장 `/api/v1/bus-stops`

#### `GET /api/v1/bus-stops/nearby?lat=37.5&lng=127.0&radiusM=500` — 근처 버스 정류장 조회

**응답** `200 OK`
```json
[
  { "stopId": "1234", "name": "강남역", "lat": 37.5012, "lng": 127.0396 }
]
```

---

#### `GET /api/v1/bus-stops/search?keyword=강남` — 버스 정류장 검색

#### `GET /api/v1/bus-stops/{stopId}` — 버스 정류장 단건 조회

---

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
| `OAUTH_KAKAO_CLIENT_ID` | 카카오 앱 키 |
| `OAUTH_KAKAO_CLIENT_SECRET` | 카카오 Client Secret |
| `OAUTH_KAKAO_REDIRECT_URI` | 카카오 리다이렉트 URI |
| `OAUTH_GOOGLE_CLIENT_ID` | 구글 클라이언트 ID |
| `OAUTH_GOOGLE_CLIENT_SECRET` | 구글 클라이언트 Secret |
| `OAUTH_GOOGLE_REDIRECT_URI` | 구글 리다이렉트 URI |
| `CITS_API_URL` | C-ITS SPAT API URL |
| `CITS_STATE_API_URL` | C-ITS 신호 상태 API URL |
| `CITS_API_KEY` | C-ITS API 키 |
| `VALHALLA_URL` | Valhalla 서버 URL |
| `BUS_STOP_API_KEY` | 공공데이터 버스 정류장 API 키 |

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
    │   │   ├── client/          # 외부 HTTP 클라이언트 (CITS, Valhalla, 버스)
    │   │   ├── config/          # Spring 설정 (Security, Cache, Repository)
    │   │   ├── controller/      # REST API 엔드포인트
    │   │   ├── domain/          # JPA 엔티티 + enums
    │   │   ├── dto/             # 요청/응답/외부 DTO
    │   │   ├── exception/       # 커스텀 예외 + 전역 핸들러
    │   │   ├── filter/          # 로깅 필터
    │   │   ├── repository/      # JPA / JDBC(pgRouting) / Redis
    │   │   ├── scheduler/       # 주기적 작업 (버스 동기화, 유저 정리)
    │   │   ├── service/         # 비즈니스 로직
    │   │   ├── util/            # Polyline 인코더
    │   │   └── CoreApplication.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       ├── logback-spring.xml
    │       └── db/
    │           ├── migration/   # Flyway (V1~V3)
    │           └── data/        # 신호 주기 통계 원본 데이터
    └── test/
        └── java/kr/io/pacer/core/
            ├── auth/
            ├── controller/      # @WebMvcTest 슬라이스 테스트
            ├── domain/
            ├── integration/     # Testcontainers 통합 테스트
            └── service/         # Mockito 단위 테스트
```
