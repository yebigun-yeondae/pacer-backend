package kr.io.pacer.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalResponse {
    private int     itstId;
    private String  name;
    private Integer ntPdsgRmdrCs;
    private Integer etPdsgRmdrCs;
    private Integer stPdsgRmdrCs;
    private Integer wtPdsgRmdrCs;
    private Integer nePdsgRmdrCs;
    private Integer sePdsgRmdrCs;
    private Integer swPdsgRmdrCs;
    private Integer nwPdsgRmdrCs;
}
