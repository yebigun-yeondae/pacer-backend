package kr.io.pacer.core.dto.internal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RouteSegment {
    private int    seq;
    private long   edgeId;
    private long   targetNodeId;
    private double costSeconds;
    private String geomWkt;
    private double lengthMeters;
    private double endLat;
    private double endLng;
    private double cumulativeSeconds;
}
