-- =============================================
-- Extensions
-- =============================================
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgrouting;

-- =============================================
-- road_nodes
-- =============================================
CREATE TABLE IF NOT EXISTS road_nodes (
    id   bigint PRIMARY KEY,
    geom geometry(Point, 4326) NOT NULL
);

CREATE INDEX IF NOT EXISTS road_nodes_geom_idx ON road_nodes USING GIST (geom);

-- =============================================
-- road_edges
-- =============================================
CREATE TABLE IF NOT EXISTS road_edges (
    id            bigint PRIMARY KEY,
    source        bigint  NOT NULL REFERENCES road_nodes(id),
    target        bigint  NOT NULL REFERENCES road_nodes(id),
    geom          geometry(LineString, 4326) NOT NULL,
    is_pedestrian boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS road_edges_source_idx ON road_edges (source);
CREATE INDEX IF NOT EXISTS road_edges_target_idx ON road_edges (target);
CREATE INDEX IF NOT EXISTS road_edges_geom_idx   ON road_edges USING GIST (geom);

-- =============================================
-- signal_wait_seconds(node_id, arrive_after_seconds)
-- TrafficSignal.calcWaitSeconds() 와 동일한 로직
-- =============================================
CREATE OR REPLACE FUNCTION signal_wait_seconds(
    p_node_id       bigint,
    p_arrive_after  double precision
)
RETURNS double precision
LANGUAGE plpgsql STABLE
AS $$
DECLARE
    v_green  int;
    v_red    int;
    v_offset int;
    v_cycle  double precision;
    v_phase  double precision;
BEGIN
    SELECT green_duration, red_duration, cycle_offset
    INTO v_green, v_red, v_offset
    FROM traffic_signals
    WHERE node_id = p_node_id
    LIMIT 1;

    IF NOT FOUND THEN
        RETURN 0.0;
    END IF;

    v_cycle := v_green + v_red;
    v_phase := MOD(p_arrive_after + v_offset, v_cycle);

    IF v_phase < v_green THEN
        RETURN 0.0;
    END IF;

    RETURN v_cycle - v_phase;
END;
$$;
