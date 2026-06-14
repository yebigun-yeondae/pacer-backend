# Pacer API 명세

> 🔒 표시된 엔드포인트는 `Authorization: Bearer <accessToken>` 헤더 필요

---

## 인증 `/api/v1/auth`

### `POST /api/v1/auth/signup` — 일반 회원가입

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

### `POST /api/v1/auth/login` — 일반 로그인

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

### `POST /api/v1/auth/kakao` — 카카오 로그인

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

### `POST /api/v1/auth/google` — 구글 로그인

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

### `POST /api/v1/auth/reissue` — Access Token 재발급

**헤더**: `Refresh-Token: <refreshToken>`

**응답** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

### `POST /api/v1/auth/logout` 🔒 — 로그아웃

**헤더**: `Refresh-Token: <refreshToken>`

**응답** `204 No Content`

---

### `DELETE /api/v1/auth/withdraw` 🔒 — 회원탈퇴

**헤더**: `Refresh-Token: <refreshToken>`

**응답** `204 No Content`

---

## 경로 `/api/v1/routes`

### `POST /api/v1/routes` 🔒 — 경로 탐색

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

> `mode`: `BALANCED` (기본) | `FASTEST` | `SHORTEST`

**응답** `200 OK`
```json
{
  "polyline": "wkysEkodfV}@...",
  "totalTimeSeconds": 412,
  "totalDistanceMeters": 520.3,
  "signalCheckpoints": [
    {
      "order": 1,
      "crosswalkId": "9001",
      "intersectionId": 5501,
      "lat": 37.5022,
      "lng": 127.0412,
      "etaFromStartSeconds": 95,
      "signalDirection": "nt",
      "remainingSeconds": 23.0,
      "signalState": "GREEN"
    }
  ],
  "intersectionSignals": [
    {
      "order": 1,
      "itstId": 5501,
      "name": "강남역사거리",
      "lat": 37.5021,
      "lng": 127.0410,
      "ntPdsgRmdrCs": 23.0,
      "etPdsgRmdrCs": null,
      "stPdsgRmdrCs": null,
      "wtPdsgRmdrCs": null,
      "nePdsgRmdrCs": null,
      "sePdsgRmdrCs": null,
      "swPdsgRmdrCs": null,
      "nwPdsgRmdrCs": null,
      "ntPdsgStatNm": "permissive-Movement-Allowed",
      "etPdsgStatNm": null,
      "stPdsgStatNm": null,
      "wtPdsgStatNm": null,
      "nePdsgStatNm": null,
      "sePdsgStatNm": null,
      "swPdsgStatNm": null,
      "nwPdsgStatNm": null,
      "signalCycles": {
        "nt": { "redMaxSec": 30.0, "greenMaxSec": 25.0 }
      }
    }
  ]
}
```

---

### `GET /api/v1/routes/history` 🔒 — 경로 이용 이력 조회

**쿼리 파라미터**: `page=0&size=20` (기본값, `createdAt` 내림차순)

**응답** `200 OK`
```json
[
  {
    "id": "uuid",
    "originLat": 37.5012,
    "originLng": 127.0396,
    "originName": "강남역",
    "destinationLat": 37.5045,
    "destinationLng": 127.0480,
    "destinationName": "선릉역",
    "encodedPolyline": "wkysEkodfV}@...",
    "totalTimeSec": 412,
    "totalDistanceM": 520.3,
    "signalStops": 1,
    "mode": "BALANCED",
    "createdAt": "2026-05-17T10:30:00"
  }
]
```

---

## 프로필 `/api/v1/profile`

### `GET /api/v1/profile` 🔒 — 내 프로필 조회

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

### `PATCH /api/v1/profile/walking-speed` 🔒 — 걸음 속도 업데이트

**요청**
```json
{ "speedMps": 1.55 }
```

**응답** `204 No Content`

---

## 즐겨찾기 `/api/v1/favorites`

### `GET /api/v1/favorites` 🔒 — 즐겨찾기 목록 조회

**응답** `200 OK`
```json
[
  {
    "id": "uuid",
    "label": "회사",
    "address": "서울시 강남구 ...",
    "lat": 37.5012,
    "lng": 127.0396,
    "visitCount": 42,
    "createdAt": "2026-05-17T10:30:00"
  }
]
```

---

### `POST /api/v1/favorites` 🔒 — 즐겨찾기 추가

**요청**
```json
{
  "label": "회사",
  "lat": 37.5012,
  "lng": 127.0396,
  "address": "서울시 강남구 ..."
}
```

**응답** `201 Created`

---

### `DELETE /api/v1/favorites/{id}` 🔒 — 즐겨찾기 삭제

**응답** `204 No Content`

---

## 신호 `/api/v1/signal`

### `GET /api/v1/signal?itstIds=5501,5502` — 교차로 신호 정보 조회

**응답** `200 OK`
```json
[
  {
    "itstId": 5501,
    "name": "강남역사거리",
    "ntPdsgRmdrCs": 23.0,
    "etPdsgRmdrCs": null,
    "stPdsgRmdrCs": null,
    "wtPdsgRmdrCs": null,
    "nePdsgRmdrCs": null,
    "sePdsgRmdrCs": null,
    "swPdsgRmdrCs": null,
    "nwPdsgRmdrCs": null,
    "ntPdsgStatNm": "permissive-Movement-Allowed",
    "etPdsgStatNm": null,
    "stPdsgStatNm": null,
    "wtPdsgStatNm": null,
    "nePdsgStatNm": null,
    "sePdsgStatNm": null,
    "swPdsgStatNm": null,
    "nwPdsgStatNm": null,
    "signalCycles": {
      "nt": { "redMaxSec": 30.0, "greenMaxSec": 25.0 }
    }
  }
]
```

---

## 버스 정류장 `/api/v1/bus-stops`

### `GET /api/v1/bus-stops/nearby?lat=37.5&lng=127.0&radiusM=500` — 근처 버스 정류장 조회

**응답** `200 OK`
```json
[
  { "stopId": "1234", "name": "강남역", "lat": 37.5012, "lng": 127.0396 }
]
```

---

### `GET /api/v1/bus-stops/search?keyword=강남` — 버스 정류장 검색

**응답** `200 OK` — 위와 동일한 배열 형태

---

### `GET /api/v1/bus-stops/{stopId}` — 버스 정류장 단건 조회

**응답** `200 OK` — 위와 동일한 단건 형태

---

## 지하철역 `/api/v1/subway-stations`

### `GET /api/v1/subway-stations/nearby?lat=37.5&lng=127.0&radiusM=500` — 근처 지하철역 조회

**응답** `200 OK`
```json
[
  {
    "stationCd": "1001",
    "stationNm": "강남",
    "lineNum": "02호선",
    "lat": 37.4979,
    "lng": 127.0276
  }
]
```

---

### `GET /api/v1/subway-stations/{stationNm}/arrivals` — 실시간 도착 정보 조회

**응답** `200 OK`
```json
[
  {
    "lineId": "1002",
    "stationNm": "강남",
    "trainLineNm": "성수행",
    "currentStation": "역삼",
    "remainingSeconds": 120,
    "arrivalMessage": "2분 후 도착",
    "positionMessage": "역삼",
    "arrivalCode": "1",
    "lastTrain": false
  }
]
```
