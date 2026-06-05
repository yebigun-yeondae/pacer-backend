package kr.io.pacer.core.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiProfileUpdateResponse {

    private boolean updated;

    @JsonProperty("updated_profile")
    private UpdatedProfile updatedProfile;

    @JsonProperty("skip_reason")
    private String skipReason;

    @Getter
    @NoArgsConstructor
    public static class UpdatedProfile {

        @JsonProperty("avg_speed")
        private double avgSpeed;

        @JsonProperty("speed_std")
        private double speedStd;

        @JsonProperty("trip_count")
        private int tripCount;
    }
}
