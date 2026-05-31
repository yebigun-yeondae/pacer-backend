#!/usr/bin/env bash
set -euo pipefail

PBF_PATH="${1:-/Users/home/pacer_map_data/merged.osm.pbf}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-pacer}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"

if [[ ! -f "$PBF_PATH" ]]; then
  echo "PBF 파일을 찾을 수 없습니다: $PBF_PATH" >&2
  exit 1
fi

for cmd in osmium ogr2ogr psql; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "필수 명령을 찾을 수 없습니다: $cmd" >&2
    exit 1
  fi
done

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

filtered_pbf="$tmpdir/crossings.osm.pbf"
crossings_geojson="$tmpdir/crossings.geojsons"

echo "[crosswalks] OSM crossing way 추출: $PBF_PATH"
osmium tags-filter "$PBF_PATH" \
  w/footway=crossing \
  w/highway=crossing \
  -o "$filtered_pbf" \
  --overwrite \
  --no-progress

echo "[crosswalks] GeoJSONSeq 변환"
osmium export "$filtered_pbf" \
  --geometry-types=linestring \
  --attributes=type,id \
  --output-format=geojsonseq \
  -o "$crossings_geojson" \
  --overwrite \
  --no-progress

export PGPASSWORD="$DB_PASSWORD"
ogr_pg_dsn="PG:host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USERNAME password=$DB_PASSWORD"
psql_dsn="host=$DB_HOST port=$DB_PORT dbname=$DB_NAME user=$DB_USERNAME"

echo "[crosswalks] PostGIS 임시 테이블 적재"
ogr2ogr -f PostgreSQL "$ogr_pg_dsn" "$crossings_geojson" \
  -overwrite \
  -nln _crosswalk_import \
  -nlt LINESTRING \
  -lco GEOMETRY_NAME=geom \
  -dialect SQLite \
  -sql 'SELECT geometry, "@id" AS osm_way_id, COALESCE(crossing, footway, highway) AS crossing_type, source FROM crossings'

echo "[crosswalks] crosswalks 테이블 upsert 및 nearest_itst_id 매핑"
psql "$psql_dsn" -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO crosswalks (osm_way_id, crossing_type, source, geom, center_geom)
SELECT osm_way_id,
       NULLIF(crossing_type, '') AS crossing_type,
       NULLIF(source, '') AS source,
       ST_Force2D(ST_SetSRID(geom, 4326))::geometry(LineString, 4326) AS geom,
       ST_LineInterpolatePoint(ST_Force2D(ST_SetSRID(geom, 4326)), 0.5)::geometry(Point, 4326) AS center_geom
FROM _crosswalk_import
WHERE osm_way_id IS NOT NULL
  AND geom IS NOT NULL
ON CONFLICT (osm_way_id) DO UPDATE
SET crossing_type = EXCLUDED.crossing_type,
    source = EXCLUDED.source,
    geom = EXCLUDED.geom,
    center_geom = EXCLUDED.center_geom;

UPDATE crosswalks c
SET nearest_itst_id = (
    SELECT i.itst_id
    FROM intersections i
    ORDER BY c.center_geom <-> i.geom
    LIMIT 1
)
WHERE EXISTS (SELECT 1 FROM intersections);

DROP TABLE IF EXISTS _crosswalk_import;
SQL

echo "[crosswalks] 완료"
