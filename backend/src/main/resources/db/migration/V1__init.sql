CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

CREATE TABLE IF NOT EXISTS users (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    nickname          VARCHAR(255),
    email             VARCHAR(255) UNIQUE,
    profile_image_url VARCHAR(255),
    password          VARCHAR(255),
    role              VARCHAR(255) NOT NULL DEFAULT 'USER'
                                   CHECK (role IN ('USER', 'ADMIN')),
    created_at        TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pedestrian_profiles (
    id               UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID             NOT NULL UNIQUE REFERENCES users(id),
    avg_speed_mps    DOUBLE PRECISION NOT NULL DEFAULT 1.4,
    uphill_factor    DOUBLE PRECISION NOT NULL DEFAULT 0.8,
    downhill_factor  DOUBLE PRECISION NOT NULL DEFAULT 1.1,
    total_routes     INT              NOT NULL DEFAULT 0,
    total_distance_m DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    updated_at       TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS user_oauth_accounts (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id),
    provider    VARCHAR(20)  NOT NULL CHECK (provider IN ('KAKAO', 'GOOGLE')),
    provider_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_id)
);

CREATE TABLE IF NOT EXISTS favorite_places (
    id          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID             NOT NULL REFERENCES users(id),
    label       VARCHAR(255),
    geom        geometry(Point, 4326),
    address     VARCHAR(255),
    visit_count INT              NOT NULL DEFAULT 0,
    created_at  TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS route_histories (
    id                UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID             NOT NULL REFERENCES users(id),
    origin_geom       geometry(Point, 4326),
    destination_geom  geometry(Point, 4326),
    origin_name       VARCHAR(255),
    destination_name  VARCHAR(255),
    encoded_polyline  TEXT,
    total_time_sec    INT              NOT NULL,
    total_distance_m  DOUBLE PRECISION NOT NULL,
    signal_stops      INT              NOT NULL,
    mode              VARCHAR(255)     CHECK (mode IN ('FASTEST', 'SHORTEST', 'BALANCED')),
    created_at        TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS intersections (
    itst_id INT PRIMARY KEY,
    name    VARCHAR(255),
    geom    geometry(Point, 4326)
);
