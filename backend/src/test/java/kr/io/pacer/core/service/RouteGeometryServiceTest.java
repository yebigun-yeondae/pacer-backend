package kr.io.pacer.core.service;

import kr.io.pacer.core.client.ValhallaClient;
import kr.io.pacer.core.domain.enums.RouteMode;
import kr.io.pacer.core.dto.external.ValhallaResponse;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.repository.jdbc.RouteRepository;
import kr.io.pacer.core.repository.jpa.PedestrianProfileRepository;
import kr.io.pacer.core.service.RouteGeometryService.CachedRoute;
import kr.io.pacer.core.util.PolylineEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("RouteGeometryService 경로 후보 생성 테스트")
class RouteGeometryServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgrouting/pgrouting:latest")
                            .asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("pacer_test")
                    .withUsername("test")
                    .withPassword("test");

    RouteGeometryService routeGeometryService;
    ValhallaClient valhallaClient;
    PedestrianProfileRepository profileRepository;
    PolylineEncoder polylineEncoder;
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUsername(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        valhallaClient = mock(ValhallaClient.class);
        profileRepository = mock(PedestrianProfileRepository.class);
        polylineEncoder = new PolylineEncoder();
        routeGeometryService = new RouteGeometryService(
                valhallaClient,
                new RouteRepository(jdbcTemplate),
                profileRepository,
                polylineEncoder);

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
        jdbcTemplate.execute("CREATE INDEX idx_crosswalks_geom_route_test ON crosswalks USING GIST (geom)");
        jdbcTemplate.execute("CREATE INDEX idx_crosswalks_center_geom_route_test ON crosswalks USING GIST (center_geom)");

        jdbcTemplate.update("""
                INSERT INTO intersections (itst_id, name, geom)
                VALUES (101, '경로상 교차로', ST_SetSRID(ST_MakePoint(127.004, 37.0), 4326))
                """);
        insertCrosswalk(100L, 101, "LINESTRING(127.002 36.9999, 127.002 37.0001)");
        insertCrosswalk(300L, 101, "LINESTRING(127.006 36.9999, 127.006 37.0001)");
        insertCrosswalk(200L, 101, "LINESTRING(127.004 37.0010, 127.004 37.0012)");
    }

    @Test
    @DisplayName("Valhalla 경로 탐색 후 실제로 건널 횡단보도를 경로 순서로 선택한다")
    void fetchAll_selectsCrosswalksAfterRouteSearch() {
        UUID userId = UUID.randomUUID();
        RouteRequest request = makeRouteRequest();
        String routeShape = encode(List.of(
                new double[]{37.0, 127.0},
                new double[]{37.0, 127.01}
        ));

        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(valhallaClient.route(
                eq(37.0), eq(127.0),
                eq(37.0), eq(127.01),
                any(), eq(RouteMode.BALANCED), anyDouble()))
                .willReturn(List.of(makeTrip(routeShape, 600.0, 1.0)));

        List<CachedRoute> result = routeGeometryService.fetchAll(request, userId);

        assertThat(result).hasSize(1);
        CachedRoute route = result.get(0);
        assertThat(route.crosswalks()).extracting(c -> c.crosswalkId())
                .containsExactly(100L, 300L);
        assertThat(route.crosswalks().get(0).distanceFromStart()).isCloseTo(200.0, within(1.0));
        assertThat(route.crosswalks().get(1).distanceFromStart()).isCloseTo(600.0, within(1.0));
    }

    private RouteRequest makeRouteRequest() {
        RouteRequest request = new RouteRequest();
        RouteRequest.Coordinate origin = new RouteRequest.Coordinate();
        RouteRequest.Coordinate destination = new RouteRequest.Coordinate();
        ReflectionTestUtils.setField(origin, "lat", 37.0);
        ReflectionTestUtils.setField(origin, "lng", 127.0);
        ReflectionTestUtils.setField(destination, "lat", 37.0);
        ReflectionTestUtils.setField(destination, "lng", 127.01);
        ReflectionTestUtils.setField(request, "origin", origin);
        ReflectionTestUtils.setField(request, "destination", destination);
        ReflectionTestUtils.setField(request, "mode", RouteMode.BALANCED);
        return request;
    }

    private ValhallaResponse.Trip makeTrip(String shape, double timeSec, double distanceKm) {
        ValhallaResponse.Leg leg = new ValhallaResponse.Leg();
        ReflectionTestUtils.setField(leg, "shape", shape);

        ValhallaResponse.Summary summary = new ValhallaResponse.Summary();
        ReflectionTestUtils.setField(summary, "time", timeSec);
        ReflectionTestUtils.setField(summary, "length", distanceKm);

        ValhallaResponse.Trip trip = new ValhallaResponse.Trip();
        ReflectionTestUtils.setField(trip, "legs", List.of(leg));
        ReflectionTestUtils.setField(trip, "summary", summary);
        return trip;
    }

    @SuppressWarnings("unchecked")
    private String encode(List<double[]> points) {
        return (String) ReflectionTestUtils.invokeMethod(polylineEncoder, "encode", points);
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