package kr.io.pacer.core.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpatResponse {

    private String  itstId;
    private Long    trsmUtcTime;

    private Integer ntPdsgRmdrCs;
    private Integer etPdsgRmdrCs;
    private Integer stPdsgRmdrCs;
    private Integer wtPdsgRmdrCs;
    private Integer nePdsgRmdrCs;
    private Integer sePdsgRmdrCs;
    private Integer swPdsgRmdrCs;
    private Integer nwPdsgRmdrCs;

    public int getItstIdAsInt() {
        return Integer.parseInt(itstId);
    }
}
