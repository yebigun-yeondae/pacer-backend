package kr.io.pacer.core.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AiProfileUpdateRequest {

    @JsonProperty("current_profile")
    private CurrentProfile currentProfile;

    @JsonProperty("actual_avg_speed")
    private double actualAvgSpeed;

    @JsonProperty("actual_duration_seconds")
    private double actualDurationSeconds;

    @Getter
    @AllArgsConstructor
    public static class CurrentProfile {

        @JsonProperty("avg_speed")
        private double avgSpeed;

        @JsonProperty("speed_std")
        private double speedStd;

        @JsonProperty("trip_count")
        private int tripCount;
    }
}
