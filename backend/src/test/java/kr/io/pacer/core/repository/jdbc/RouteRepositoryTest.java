package kr.io.pacer.core.repository.jdbc;

import kr.io.pacer.core.repository.jdbc.RouteRepository.CrosswalkInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("RouteRepository 공간 조회 테스트")
class RouteRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgrouting/pgrouting:latest")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("pacer_test")
                    .withUsername("test")
                    .withPassword("test");

    RouteRepository routeRepository;
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        routeRepository = new RouteRepository(jdbcTemplate);

        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS postgis");
        jdbcTemplate.execute("DROP TABLE IF EXISTS crosswalks");
        jdbcTemplate.execute("DROP TABLE IF EXISTS intersections");
        jdbcTemplate.execute("""
                CREATE TABLE intersections (
                    itst_id INTEGER PRIMARY KEY,
                    name VARCHAR(255),
                    geom geometry(Point, 4326)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE crosswalks (
                    osm_way_id BIGINT PRIMARY KEY,
                    crossing_type VARCHAR(100),
                    source VARCHAR(255),
                    geom geometry(LineString, 4326) NOT NULL,
                    center_geom geometry(Point, 4326) NOT NULL,
                    nearest_itst_id INTEGER REFERENCES intersections(itst_id)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_crosswalks_geom_test ON crosswalks USING GIST (geom)");
        jdbcTemplate.execute("CREATE INDEX idx_crosswalks_center_geom_test ON crosswalks USING GIST (center_geom)");

        jdbcTemplate.update("""
                INSERT INTO intersections (itst_id, name, geom)
                VALUES (101, '테스트 교차로', ST_SetSRID(ST_MakePoint(127.002, 37.0), 4326))
                """);
    }

    @Test
    @DisplayName("경로 20m 안의 횡단보도만 거리순으로 조회한다")
    void findCrosswalksByRouteWkt_filtersByRadiusAndOrdersByFraction() {
        insertCrosswalk(300L, 101, "LINESTRING(127.006 36.9999, 127.006 37.0001)");
        insertCrosswalk(100L, 101, "LINESTRING(127.002 36.9999, 127.002 37.0001)");
        insertCrosswalk(200L, 101, "LINESTRING(127.004 37.0010, 127.004 37.0012)");

        List<CrosswalkInfo> result = routeRepository.findCrosswalksByRouteWkt(
                "LINESTRING(127.0 37.0, 127.01 37.0)",
                1000.0);

        assertThat(result).extracting(CrosswalkInfo::crosswalkId)
                .containsExactly(100L, 300L);
        assertThat(result.get(0).itstId()).isEqualTo(101);
        assertThat(result.get(0).fraction()).isCloseTo(0.2, within(0.001));
        assertThat(result.get(0).distanceFromStart()).isCloseTo(200.0, within(1.0));
        assertThat(result.get(1).fraction()).isCloseTo(0.6, within(0.001));
    }

    @Test
    @DisplayName("nearest_itst_id가 없어도 횡단보도 후보는 조회한다")
    void findCrosswalksByRouteWkt_includesCrosswalkWithoutNearestIntersection() {
        insertCrosswalk(400L, null, "LINESTRING(127.002 36.9999, 127.002 37.0001)");

        List<CrosswalkInfo> result = routeRepository.findCrosswalksByRouteWkt(
                "LINESTRING(127.0 37.0, 127.01 37.0)",
                1000.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).crosswalkId()).isEqualTo(400L);
        assertThat(result.get(0).itstId()).isNull();
    }

    private void insertCrosswalk(long osmWayId, Integer nearestItstId, String wkt) {
        jdbcTemplate.update("""
                INSERT INTO crosswalks (osm_way_id, crossing_type, source, geom, center_geom, nearest_itst_id)
                VALUES (?, 'marked', 'test',
                        ST_GeomFromText(?, 4326),
                        ST_LineInterpolatePoint(ST_GeomFromText(?, 4326), 0.5),
                        ?)
                """, osmWayId, wkt, wkt, nearestItstId);
    }
}
