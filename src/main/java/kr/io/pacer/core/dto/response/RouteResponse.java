package kr.io.pacer.core.dto.response;

import kr.io.pacer.core.domain.enums.RecommendedPace;
import kr.io.pacer.core.domain.enums.SignalState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

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
        private int     itstId;
        private String  name;
        private double  lat;
        private double  lng;
        private Integer ntPdsgRmdrCs;
        private Integer etPdsgRmdrCs;
        private Integer stPdsgRmdrCs;
        private Integer wtPdsgRmdrCs;
        private Integer nePdsgRmdrCs;
        private Integer sePdsgRmdrCs;
        private Integer swPdsgRmdrCs;
        private Integer nwPdsgRmdrCs;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalCheckpoint {
        private long            nodeId;
        private double          lat;
        private double          lng;
        private int             etaFromStartSeconds;
        private SignalState     signalState;
        private RecommendedPace recommendedPace;
    }
}
