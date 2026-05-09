package kr.io.pacer.core.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import kr.io.pacer.core.domain.enums.RouteMode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RouteRequest {

    @NotNull @Valid
    private Coordinate origin;

    @NotNull @Valid
    private Coordinate destination;

    private String originName;
    private String destinationName;

    private RouteMode mode = RouteMode.BALANCED;

    @Getter
    @NoArgsConstructor
    public static class Coordinate {

        @DecimalMin("-90") @DecimalMax("90")
        private double lat;

        @DecimalMin("-180") @DecimalMax("180")
        private double lng;
    }
}
