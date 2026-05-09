package kr.io.pacer.core.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpbiResponse {

    private String itstId;

    private String ntPdsgStatNm;
    private String etPdsgStatNm;
    private String stPdsgStatNm;
    private String wtPdsgStatNm;
    private String nePdsgStatNm;
    private String sePdsgStatNm;
    private String swPdsgStatNm;
    private String nwPdsgStatNm;

    public int getItstIdAsInt() {
        return Integer.parseInt(itstId);
    }
}
