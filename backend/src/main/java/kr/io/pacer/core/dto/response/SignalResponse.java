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
    private Double ntPdsgRmdrCs;
    private Double etPdsgRmdrCs;
    private Double stPdsgRmdrCs;
    private Double wtPdsgRmdrCs;
    private Double nePdsgRmdrCs;
    private Double sePdsgRmdrCs;
    private Double swPdsgRmdrCs;
    private Double nwPdsgRmdrCs;
}
