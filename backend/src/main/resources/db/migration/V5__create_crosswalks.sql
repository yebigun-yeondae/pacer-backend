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
