package kr.io.pacer.core.dto.response;

import kr.io.pacer.core.domain.SubwayStation;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubwayStationResponse {

    private String stationCd;
    private String stationNm;
    private String lineNum;
    private double lat;
    private double lng;

    public static SubwayStationResponse from(SubwayStation station) {
        return SubwayStationResponse.builder()
                .stationCd(station.getStationCd())
                .stationNm(station.getStationNm())
                .lineNum(station.getLineNum())
                .lat(station.getGeom().getY())
                .lng(station.getGeom().getX())
                .build();
    }
}
