CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

CREATE TABLE IF NOT EXISTS users (
    id                UUID PRIMARY KEY,
    nickname          VARCHAR(255),
    email             VARCHAR(255) UNIQUE,
    profile_image_url VARCHAR(255),
    password          VARCHAR(255),
    role              VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    deleted_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pedestrian_profiles (
    id               UUID PRIMARY KEY,
    user_id          UUID    NOT NULL UNIQUE REFERENCES users(id),
    avg_speed_mps    FLOAT8  DEFAULT 1.4,
    uphill_factor    FLOAT8  DEFAULT 0.8,
    downhill_factor  FLOAT8  DEFAULT 1.1,
    total_routes     INTEGER DEFAULT 0,
    total_distance_m FLOAT8  DEFAULT 0.0,
    updated_at       TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_oauth_accounts (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users(id),
    provider    VARCHAR(20)  NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP,
    UNIQUE (provider, provider_id)
);

CREATE TABLE IF NOT EXISTS favorite_places (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id),
    label       VARCHAR(255),
    geom        geometry(Point, 4326),
    address     VARCHAR(255),
    visit_count INTEGER DEFAULT 0,
    created_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS route_histories (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES users(id),
    origin_geom       geometry(Point, 4326),
    destination_geom  geometry(Point, 4326),
    origin_name       VARCHAR(255),
    destination_name  VARCHAR(255),
    encoded_polyline  TEXT,
    total_time_sec    INTEGER,
    total_distance_m  FLOAT8,
    signal_stops      INTEGER,
    mode              VARCHAR(50),
    created_at        TIMESTAMP
);

CREATE TABLE IF NOT EXISTS intersections (
    itst_id INTEGER PRIMARY KEY,
    name    VARCHAR(255),
    geom    geometry(Point, 4326)
);
