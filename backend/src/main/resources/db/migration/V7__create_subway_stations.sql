CREATE TABLE IF NOT EXISTS subway_stations (
    station_cd      VARCHAR(20)  PRIMARY KEY,
    station_nm      VARCHAR(100) NOT NULL,
    line_num        VARCHAR(20),
    station_nm_eng  VARCHAR(100),
    fr_code         VARCHAR(20),
    geom            geometry(Point, 4326)
);

CREATE INDEX IF NOT EXISTS idx_subway_stations_geom ON subway_stations USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_subway_stations_nm   ON subway_stations (station_nm);
