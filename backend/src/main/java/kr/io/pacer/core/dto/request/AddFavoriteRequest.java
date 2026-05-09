package kr.io.pacer.core.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AddFavoriteRequest {

    @NotBlank
    private String label;

    @DecimalMin("-90") @DecimalMax("90")
    private double lat;

    @DecimalMin("-180") @DecimalMax("180")
    private double lng;

    private String address;
}
