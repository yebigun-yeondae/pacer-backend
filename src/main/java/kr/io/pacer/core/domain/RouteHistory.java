package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import kr.io.pacer.core.domain.enums.RouteMode;
import kr.io.pacer.core.dto.request.RouteRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "route_histories")
@Getter
@NoArgsConstructor
public class RouteHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point originGeom;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point destinationGeom;

    private String originName;
    private String destinationName;

    @Column(columnDefinition = "text")
    private String encodedPolyline;

    private int    totalTimeSec;
    private double totalDistanceM;
    private int    signalStops;

    @Enumerated(EnumType.STRING)
    private RouteMode mode;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    public static RouteHistory of(User user, RouteRequest req,
                                  String polyline, int timeSec,
                                  double distM, int signalStops) {
        RouteHistory h = new RouteHistory();
        h.user            = user;
        h.originGeom      = GF.createPoint(new Coordinate(req.getOrigin().getLng(), req.getOrigin().getLat()));
        h.destinationGeom = GF.createPoint(new Coordinate(req.getDestination().getLng(), req.getDestination().getLat()));
        h.originName      = req.getOriginName();
        h.destinationName = req.getDestinationName();
        h.encodedPolyline = polyline;
        h.totalTimeSec    = timeSec;
        h.totalDistanceM  = distM;
        h.signalStops     = signalStops;
        h.mode            = req.getMode();
        return h;
    }
}
