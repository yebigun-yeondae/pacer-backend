package kr.io.pacer.core.service;

import kr.io.pacer.core.client.BusStopApiClient;
import kr.io.pacer.core.dto.external.BusStopApiResponse.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusStopSyncService {

    private static final int BATCH_SIZE = 500;
    private static final String UPSERT_SQL = """
            INSERT INTO bus_stops (stop_id, name, node_no, city_code, geom)
            VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
            ON CONFLICT (stop_id) DO UPDATE SET
              name      = EXCLUDED.name,
              node_no   = EXCLUDED.node_no,
              city_code = EXCLUDED.city_code,
              geom      = EXCLUDED.geom
            """;

    private final BusStopApiClient busStopApiClient;
    private final JdbcTemplate jdbcTemplate;

    public int sync(List<Integer> cityCodes) {
        int total = 0;
        for (int cityCode : cityCodes) {
            List<Item> items = busStopApiClient.fetchAll(cityCode);
            int upserted = upsertBatch(items);
            log.info("[BusStop] cityCode={} upsert {}건 완료", cityCode, upserted);
            total += upserted;
        }
        return total;
    }

    private int upsertBatch(List<Item> items) {
        int upserted = 0;
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            List<Object[]> params = items.subList(i, Math.min(i + BATCH_SIZE, items.size()))
                    .stream()
                    .filter(item -> item.getNodeid() != null
                            && item.getGpslati() != null
                            && item.getGpslong() != null)
                    .map(item -> new Object[]{
                            item.getNodeid(),
                            item.getNodenm(),
                            item.getNodeno(),
                            item.getCitycode(),
                            item.getGpslong(),  // ST_MakePoint(lng, lat)
                            item.getGpslati()
                    })
                    .toList();

            jdbcTemplate.batchUpdate(UPSERT_SQL, params);
            upserted += params.size();
        }
        return upserted;
    }
}
