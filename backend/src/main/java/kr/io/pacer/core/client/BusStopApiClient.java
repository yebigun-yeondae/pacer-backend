package kr.io.pacer.core.client;

import kr.io.pacer.core.dto.external.BusStopApiResponse;
import kr.io.pacer.core.dto.external.BusStopApiResponse.Body;
import kr.io.pacer.core.dto.external.BusStopApiResponse.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class BusStopApiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final int pageSize;

    public BusStopApiClient(
            @Value("${bus-stop.api-url}") String apiUrl,
            @Value("${bus-stop.api-key}") String apiKey,
            @Value("${bus-stop.page-size:1000}") int pageSize) {
        this.apiKey   = apiKey;
        this.pageSize = pageSize;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    public List<Item> fetchAll(int cityCode) {
        List<Item> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            Body body = fetchPage(cityCode, pageNo);
            if (body == null || body.getItems() == null || body.getItems().getItem() == null) break;

            List<Item> items = body.getItems().getItem();
            result.addAll(items);
            log.debug("[BusStop] cityCode={} page={} fetched={} total={}", cityCode, pageNo, items.size(), body.getTotalCount());

            if (result.size() >= body.getTotalCount()) break;
            pageNo++;
        }

        log.info("[BusStop] cityCode={} 조회 완료 | {}건", cityCode, result.size());
        return result;
    }

    private Body fetchPage(int cityCode, int pageNo) {
        try {
            BusStopApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("serviceKey", apiKey)
                            .queryParam("cityCode", cityCode)
                            .queryParam("numOfRows", pageSize)
                            .queryParam("pageNo", pageNo)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(BusStopApiResponse.class);

            return (response != null && response.getResponse() != null)
                    ? response.getResponse().getBody()
                    : null;
        } catch (Exception e) {
            log.debug("[BusStop] 페이지 조회 실패 | cityCode={} page={} error={}", cityCode, pageNo, e.getMessage());
            return null;
        }
    }
}
