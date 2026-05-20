package kr.io.pacer.core.dto.response;

import kr.io.pacer.core.domain.BusStop;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BusStopResponse {

    private String stopId;
    private String name;
    private String nodeNo;
    private Integer cityCode;
    private double lat;
    private double lng;

    public static BusStopResponse from(BusStop busStop) {
        return BusStopResponse.builder()
                .stopId(busStop.getStopId())
                .name(busStop.getName())
                .nodeNo(busStop.getNodeNo())
                .cityCode(busStop.getCityCode())
                .lat(busStop.getGeom().getY())
                .lng(busStop.getGeom().getX())
                .build();
    }
}
