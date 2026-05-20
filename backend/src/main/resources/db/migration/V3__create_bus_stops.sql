CREATE TABLE IF NOT EXISTS bus_stops (
    stop_id   VARCHAR(255) PRIMARY KEY,
    name      VARCHAR(255),
    node_no   VARCHAR(255),
    city_code INTEGER,
    geom      geometry(Point, 4326)
);
