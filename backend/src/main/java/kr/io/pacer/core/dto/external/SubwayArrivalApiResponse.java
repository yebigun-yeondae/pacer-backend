package kr.io.pacer.core.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubwayArrivalApiResponse {

    private ErrorMessage errorMessage;
    private List<RealtimeArrival> realtimeArrivalList;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorMessage {
        private int status;
        private String code;
        private String message;
        private int total;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RealtimeArrival {
        private String subwayId;
        private String statnNm;
        private String trainLineNm;
        private String barvlDt;
        private String bstatnNm;
        private String recptnDt;
        private String arvlMsg2;
        private String arvlMsg3;
        private String arvlCd;
        private String lstcarAt;
    }
}
