package kr.io.pacer.core.dto.response;

import kr.io.pacer.core.domain.enums.SignalState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    private String polyline;
    private int    totalTimeSeconds;
    private double totalDistanceMeters;
    private List<SignalCheckpoint>    signalCheckpoints;
    private List<IntersectionSignal>  intersectionSignals;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntersectionSignal {
        private int     order;
        private int     itstId;
        private String  name;
        private double  lat;
        private double  lng;
        private Double ntPdsgRmdrCs;
        private Double etPdsgRmdrCs;
        private Double stPdsgRmdrCs;
        private Double wtPdsgRmdrCs;
        private Double nePdsgRmdrCs;
        private Double sePdsgRmdrCs;
        private Double swPdsgRmdrCs;
        private Double nwPdsgRmdrCs;
        private String ntPdsgStatNm;
        private String etPdsgStatNm;
        private String stPdsgStatNm;
        private String wtPdsgStatNm;
        private String nePdsgStatNm;
        private String sePdsgStatNm;
        private String swPdsgStatNm;
        private String nwPdsgStatNm;
        private Map<String, SignalCycle> signalCycles;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalCycle {
        private Double redMaxSec;
        private Double greenMaxSec;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalCheckpoint {
        private int             order;
        private String          crosswalkId;
        private Integer         intersectionId;
        private double          lat;
        private double          lng;
        private int             etaFromStartSeconds;
        private String          signalDirection;
        private Double          remainingSeconds;
        private SignalState     signalState;
    }
}
