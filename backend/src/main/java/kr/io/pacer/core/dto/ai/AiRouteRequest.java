package kr.io.pacer.core.dto.ai;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiRouteRequest {

    private String userId;
    private UserProfile userProfile;
    private List<RouteCandidate> routeCandidates;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class UserProfile {
        private double avgSpeed;
        private double speedStd;
        private int tripCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RouteCandidate {
        private String routeId;
        private List<Waypoint> waypoints;
        private List<Crosswalk> crosswalks;
        private double totalDistance;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Waypoint {
        private double lat;
        private double lng;
        private Double elevationM;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Crosswalk {
        private String crosswalkId;
        private Integer intersectionId;
        private double distanceFromStart;
        private Signal signal;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Signal {
        private String phase;
        private Double remainingSeconds;
        private Double cycleSeconds;
    }
}
