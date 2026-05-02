package kr.io.pacer.core.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class WalkingUpdateRequest {

    @NotEmpty
    private List<Segment> segments;

    @Getter
    @NoArgsConstructor
    public static class Segment {
        private double distanceM;
        private double durationS;
        private double slopeDeg;
    }
}
