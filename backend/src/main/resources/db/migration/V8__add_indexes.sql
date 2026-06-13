CREATE INDEX IF NOT EXISTS idx_crosswalks_center_geom_geography ON crosswalks    USING GIST ((center_geom::geography));
CREATE INDEX IF NOT EXISTS idx_intersections_geom_geography     ON intersections USING GIST ((geom::geography));
CREATE INDEX IF NOT EXISTS idx_bus_stops_geom_geography         ON bus_stops     USING GIST ((geom::geography));
