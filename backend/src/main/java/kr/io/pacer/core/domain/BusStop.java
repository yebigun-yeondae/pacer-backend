package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "bus_stops")
@Getter
@NoArgsConstructor
public class BusStop {

    @Id
    @Column(name = "stop_id")
    private String stopId;

    private String name;

    @Column(name = "node_no")
    private String nodeNo;

    @Column(name = "city_code")
    private Integer cityCode;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    public static BusStop of(String stopId, String name, String nodeNo, Integer cityCode, Point geom) {
        BusStop b = new BusStop();
        b.stopId   = stopId;
        b.name     = name;
        b.nodeNo   = nodeNo;
        b.cityCode = cityCode;
        b.geom     = geom;
        return b;
    }
}
