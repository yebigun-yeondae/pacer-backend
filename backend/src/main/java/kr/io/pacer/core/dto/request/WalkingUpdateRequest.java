package kr.io.pacer.core.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class WalkingUpdateRequest {

    @NotEmpty
    @Valid
    private List<Segment> segments;

    @Getter
    @NoArgsConstructor
    public static class Segment {
        @Positive
        private double distanceM;

        @Positive
        private double durationS;

        @DecimalMin("-45.0") @DecimalMax("45.0")
        private double slopeDeg;
    }
}
