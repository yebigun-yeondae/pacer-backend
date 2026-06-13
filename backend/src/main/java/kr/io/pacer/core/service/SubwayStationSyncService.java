package kr.io.pacer.core.service;

import kr.io.pacer.core.client.SubwayStationApiClient;
import kr.io.pacer.core.dto.external.SubwayStationApiResponse.Row;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubwayStationSyncService {

    private static final int BATCH_SIZE = 500;
    private static final String UPSERT_SQL = """
            INSERT INTO subway_stations (station_cd, station_nm, line_num, geom)
            VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
            ON CONFLICT (station_cd) DO UPDATE SET
              station_nm = EXCLUDED.station_nm,
              line_num   = EXCLUDED.line_num,
              geom       = EXCLUDED.geom
            """;

    private final SubwayStationApiClient subwayStationApiClient;
    private final JdbcTemplate jdbcTemplate;

    public int sync() {
        List<Row> rows = subwayStationApiClient.fetchAll();
        int upserted = upsertBatch(rows);
        log.info("[Subway] 역사 동기화 완료 | {}건 upsert", upserted);
        return upserted;
    }

    private int upsertBatch(List<Row> rows) {
        int upserted = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<Object[]> params = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()))
                    .stream()
                    .filter(row -> row.getBlndId() != null
                            && row.getLat() != null
                            && row.getLot() != null)
                    .map(row -> {
                        try {
                            double lat = Double.parseDouble(row.getLat());
                            double lng = Double.parseDouble(row.getLot());
                            return new Object[]{
                                    row.getBlndId(),
                                    row.getBlndNm(),
                                    row.getRoute(),
                                    lng, lat   // ST_MakePoint(lng, lat)
                            };
                        } catch (NumberFormatException e) {
                            log.warn("[Subway] 좌표 파싱 실패 | blndId={}", row.getBlndId());
                            return null;
                        }
                    })
                    .filter(p -> p != null)
                    .toList();

            jdbcTemplate.batchUpdate(UPSERT_SQL, params);
            upserted += params.size();
        }
        return upserted;
    }
}
