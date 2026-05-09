# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

**pacer-backend** — 교통 정보 및 인공지능을 활용한 개인 맞춤형 도보 내비게이션 앱의 Spring Boot 백엔드. Java 17, Spring Boot 3.5, PostgreSQL(PostGIS + pgRouting), Redis 기반.

---

## 빌드 및 실행 명령어

```bash
# 빌드
./gradlew build

# 빌드(테스트 제외)
./gradlew build -x test

# 로컬 실행 (dev 프로필 기본값)
./gradlew bootRun

# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스 하나만 실행
./gradlew test --tests "kr.io.pacer.core.service.AuthServiceTest"

# 특정 테스트 메서드 하나만 실행
./gradlew test --tests "kr.io.pacer.core.service.AuthServiceTest.login_success"
```

### 환경 변수 설정

`.env.example`을 복사해 `.env`로 만들고 값을 채운다. `application.yml`이 `optional:file:.env[.properties]`로 자동 로드한다.

필수 항목: `DB_HOST`, `DB_EXTERNAL_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_EXTERNAL_PORT`, `JWT_SECRET`(최소 32바이트), `JWT_ACCESS_TOKEN_EXPIRE_MS`, `JWT_REFRESH_TOKEN_EXPIRE_MS`, `OAUTH_GOOGLE_*`, `OAUTH_KAKAO_*`, `CITS_API_URL`, `CITS_API_KEY`

---

## 아키텍처

### 레이어 구조

```
Controller → Service → Repository / Client → DB / Redis / CITS API
```

- **Controller** (`controller/`): 요청 수신 및 응답 반환. 인증 없이 호출 가능한 Auth 엔드포인트와 `@AuthenticationPrincipal`로 userId를 주입받는 보호 엔드포인트로 구성.
- **Service** (`service/`): 모든 비즈니스 로직. `@Transactional` 단위로 DB 조작.
- **Repository** (`repository/`): 대부분 Spring Data JPA `JpaRepository`. 단, `RouteRepository`는 JPA 없이 `JdbcTemplate`으로 pgRouting SQL을 직접 실행.
- **Client** (`client/`): 외부 HTTP 클라이언트. `CitsSpatClient`만 존재하며 `RestClient`로 CITS SPAT API를 병렬 호출(`CompletableFuture`).
- **Domain** (`domain/`): JPA 엔티티 + 비즈니스 메서드 포함(예: `TrafficSignal.calcWaitSeconds`, `PedestrianProfile.updateSpeed`).

### 인증 흐름

1. `JwtFilter` → `Authorization: Bearer <accessToken>` 헤더를 검증해 `SecurityContext`에 userId(`UUID`) 설정.
2. Refresh Token은 Redis에 저장(`RefreshTokenRepository`는 `CrudRepository<RefreshToken, String>`).
3. OAuth(Kakao·Google)는 클라이언트가 액세스 토큰을 획득한 뒤 서버에 넘기면, `KakaoAuthService`/`GoogleAuthService`가 프로필을 조회하고 `AuthService.loginWithKakao/Google()`이 신규 또는 기존 유저를 처리해 JWT를 발급.

### 경로 탐색 핵심 흐름 (`RouteService.findRoute`)

1. `PedestrianProfile.avgSpeedMps`로 사용자 보행 속도 조회 (기본 1.4 m/s).
2. `RouteRepository.findNearestNode`: 출발·도착 좌표를 `road_nodes` 테이블의 최대 연결 컴포넌트에서 가장 가까운 노드로 스냅.
3. `RouteRepository.findRoute`: pgRouting `pgr_aStar`에 사용자 속도와 DB 함수 `signal_wait_seconds`를 동적으로 주입해, 신호 대기 시간까지 포함한 최단 경로를 계산.
4. `CitsSpatClient.fetchAll`: 경로 위 교차로의 실시간 SPAT(신호 잔여 시간) 데이터를 병렬로 수집.
5. `TrafficSignal.calcWaitSeconds`로 각 신호등 도착 시 대기 시간 계산 → `SignalState`(GREEN/RED) 및 `RecommendedPace`(SPEED_UP/SLOW_DOWN/NORMAL) 부여.
6. 경로 응답은 Google Encoded Polyline 형태로 반환(`PolylineEncoder`).
7. 결과는 Redis에 5분간 캐시(`@Cacheable("routes")`).

### 보행자 프로필 (`PedestrianProfile`)

회원가입 시 자동 생성. 경로 완료 시 EMA(지수이동평균, α=0.3)로 평균 속도 갱신. 경사도±3° 기준으로 오르막/내리막 속도 보정(`uphillFactor`/`downhillFactor`).

### DB 의존 사항

일반 JPA 엔티티 외에 PostGIS/pgRouting에 의존하는 커스텀 테이블이 필요하다.

| 테이블 | 용도 |
|---|---|
| `road_nodes` | 보행 노드 (PostGIS Point, `component` 컬럼) |
| `road_edges` | 보행 엣지 (`is_pedestrian`, pgRouting 호환) |
| `intersections` | 교차로 정보 (`itst_id`, PostGIS Point) |
| `traffic_signals` | 신호 주기 정보 (`green/redDuration`, `cycleOffset`) |

`signal_wait_seconds(node_id, epoch_sec)` PostgreSQL 함수가 pgRouting 쿼리 내에서 직접 호출되므로, DB에 이 함수가 존재해야 경로 탐색이 작동한다.

---

## 배포 (AWS EC2)

인프라: AWS EC2 Ubuntu 24.04, Docker 기반. DB 컨테이너 포트는 `5433:5432`, Redis는 `6379`.

```bash
# 1. JAR 빌드
./gradlew clean build -x test

# 2. EC2로 전송
scp -i [키경로] build/libs/core-0.0.1-SNAPSHOT.jar ubuntu@[EC2_IP]:/home/ubuntu/pacer

# 3. EC2에서 애플리케이션 실행 (prod 프로필 사용)
java -jar -Dspring.profiles.active=prod core-0.0.1-SNAPSHOT.jar
```

### 인프라 초기 세팅 (최초 1회)

DB 컨테이너(`pgrouting/pgrouting` 이미지)와 Redis 컨테이너를 Docker로 실행한 뒤 다음 데이터를 수동 적재해야 경로 탐색이 작동한다.

1. OSM 데이터 → `road_nodes`, `road_edges` 테이블로 적재 후 pgRouting 토폴로지 생성
2. 교차로 CSV → `intersections` 테이블 적재
3. `signal_wait_seconds(node_id bigint, epoch_sec double precision)` PostgreSQL 함수 생성
4. `road_nodes.component` 컬럼에 pgRouting `pgr_connectedComponents` 결과 반영

---

## 테스트 전략

- **단위 테스트**: `@ExtendWith(MockitoExtension.class)` + Mockito. Service/Controller/Domain 레이어별로 분리.
- **컨트롤러 테스트**: `@WebMvcTest` + `TestSecurityConfig`(보안 필터 비활성화)로 인증 없이 슬라이스 테스트.
- **통합 테스트** (`integration/`): `@SpringBootTest` + Testcontainers(`pgrouting/pgrouting:latest` 이미지 + `redis:7-alpine`). 외부 OAuth 서비스만 `@MockBean`으로 대체. 실제 DB 연동 검증.
- 테스트 프로필은 `application-test.yml`에 정의(`@ActiveProfiles("test")`).

---

## 주요 기술 스택

| 영역 | 기술 |
|---|---|
| 프레임워크 | Spring Boot 3.5 (Java 17) |
| 데이터베이스 | PostgreSQL + PostGIS + pgRouting |
| 캐시 | Redis (Spring Data Redis, TTL 5분) |
| ORM | Spring Data JPA + Hibernate Spatial 6.4 |
| JWT | jjwt 0.12.5 |
| OAuth | Kakao / Google (자체 구현) |
| 외부 API | CITS SPAT API (교통신호 실시간 데이터) |
| 테스트 | JUnit 5 + Mockito + Testcontainers |
