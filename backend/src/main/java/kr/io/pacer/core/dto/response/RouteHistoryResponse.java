package kr.io.pacer.core.dto.response;

import kr.io.pacer.core.domain.RouteHistory;
import kr.io.pacer.core.domain.enums.RouteMode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class RouteHistoryResponse {

    private UUID id;

    private double originLat;
    private double originLng;
    private String originName;

    private double destinationLat;
    private double destinationLng;
    private String destinationName;

    private String encodedPolyline;
    private int totalTimeSec;
    private double totalDistanceM;
    private int signalStops;
    private RouteMode mode;
    private LocalDateTime createdAt;

    public static RouteHistoryResponse from(RouteHistory h) {
        return RouteHistoryResponse.builder()
                .id(h.getId())
                .originLat(h.getOriginGeom().getY())
                .originLng(h.getOriginGeom().getX())
                .originName(h.getOriginName())
                .destinationLat(h.getDestinationGeom().getY())
                .destinationLng(h.getDestinationGeom().getX())
                .destinationName(h.getDestinationName())
                .encodedPolyline(h.getEncodedPolyline())
                .totalTimeSec(h.getTotalTimeSec())
                .totalDistanceM(h.getTotalDistanceM())
                .signalStops(h.getSignalStops())
                .mode(h.getMode())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
