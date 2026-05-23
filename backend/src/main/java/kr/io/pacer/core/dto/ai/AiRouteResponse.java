package kr.io.pacer.core.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiRouteResponse {

    private String optimalRouteId;
    private double estimatedTotalTimeSeconds;
    private double estimatedWaitTimeSeconds;
    private List<SimulationDetail> simulationDetails;
    private List<Warning> warnings;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class SimulationDetail {
        private String routeId;
        private double totalTimeSeconds;
        private double travelTimeSeconds;
        private double waitTimeSeconds;
        private double citsCoverageRate;
        private List<CrosswalkResult> crosswalkResults;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class CrosswalkResult {
        private String crosswalkId;
        private double etaSeconds;
        private String signalStateAtArrival;
        private double waitSeconds;
        private boolean hasCitsData;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Warning {
        private String crosswalkId;
        private String reason;
        private double fallbackWaitSeconds;
    }
}
