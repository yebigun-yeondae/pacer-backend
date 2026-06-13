package kr.io.pacer.core.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubwayStationApiResponse {

    @JsonProperty("subwayStationMaster")
    private Service subwayStationMaster;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Service {
        @JsonProperty("list_total_count")
        private int listTotalCount;

        @JsonProperty("row")
        private List<Row> row;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {
        @JsonProperty("BLDN_ID")
        private String blndId;

        @JsonProperty("BLDN_NM")
        private String blndNm;

        @JsonProperty("ROUTE")
        private String route;

        @JsonProperty("LAT")
        private String lat;

        @JsonProperty("LOT")
        private String lot;
    }
}
