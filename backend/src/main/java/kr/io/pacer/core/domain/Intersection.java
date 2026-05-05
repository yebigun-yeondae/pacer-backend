package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "intersections")
@Getter
@NoArgsConstructor
public class Intersection {

    @Id
    private Integer itstId;

    private String name;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    public static Intersection of(int itstId, String name, Point geom) {
        Intersection i = new Intersection();
        i.itstId = itstId;
        i.name   = name;
        i.geom   = geom;
        return i;
    }
}
