CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

CREATE TABLE IF NOT EXISTS intersections (
    itst_id INTEGER PRIMARY KEY,
    name    VARCHAR(255),
    geom    geometry(Point, 4326)
);

CREATE TEMP TABLE _itst_staging (
    itst_id INTEGER,
    name    TEXT,
    lat     DOUBLE PRECISION,
    lng     DOUBLE PRECISION,
    col5 TEXT, col6 TEXT, col7 TEXT, col8 TEXT, col9 TEXT, col10 TEXT
);

COPY _itst_staging FROM '/docker-entrypoint-initdb.d/intersections.csv'
WITH (FORMAT CSV, HEADER, DELIMITER ',');

INSERT INTO intersections (itst_id, name, geom)
SELECT itst_id, name, ST_SetSRID(ST_MakePoint(lng, lat), 4326)
FROM _itst_staging
ON CONFLICT (itst_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_intersections_geom ON intersections USING GIST (geom);

CREATE TABLE IF NOT EXISTS crosswalks (
    osm_way_id      BIGINT PRIMARY KEY,
    crossing_type   VARCHAR(100),
    source          VARCHAR(255),
    geom            geometry(LineString, 4326) NOT NULL,
    center_geom     geometry(Point, 4326)      NOT NULL,
    nearest_itst_id INTEGER REFERENCES intersections(itst_id)
);

CREATE INDEX IF NOT EXISTS idx_crosswalks_geom
    ON crosswalks USING GIST (geom);

CREATE INDEX IF NOT EXISTS idx_crosswalks_center_geom
    ON crosswalks USING GIST (center_geom);

CREATE INDEX IF NOT EXISTS idx_crosswalks_nearest_itst_id
    ON crosswalks (nearest_itst_id);

CREATE INDEX IF NOT EXISTS idx_crosswalks_geom_geography ON crosswalks USING GIST ((geom::geography));
