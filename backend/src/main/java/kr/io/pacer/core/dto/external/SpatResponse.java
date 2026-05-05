package kr.io.pacer.core.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpatResponse {

    private String itstId;
    private Long   trsmUtcTime;

    private Double ntPdsgRmdrCs;
    private Double etPdsgRmdrCs;
    private Double stPdsgRmdrCs;
    private Double wtPdsgRmdrCs;
    private Double nePdsgRmdrCs;
    private Double sePdsgRmdrCs;
    private Double swPdsgRmdrCs;
    private Double nwPdsgRmdrCs;

    public int getItstIdAsInt() {
        return Integer.parseInt(itstId);
    }
}
