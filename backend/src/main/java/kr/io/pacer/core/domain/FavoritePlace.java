package kr.io.pacer.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
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
@Table(name = "favorite_places")
@Getter
@NoArgsConstructor
public class FavoritePlace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String label;

    @JsonIgnore
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    private String address;
    private int    visitCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public double getLat() { return geom != null ? geom.getY() : 0.0; }
    public double getLng() { return geom != null ? geom.getX() : 0.0; }

    public void incrementVisit() {
        this.visitCount++;
    }

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    public static FavoritePlace of(User user, String label, double lat, double lng, String address) {
        FavoritePlace fp = new FavoritePlace();
        fp.user    = user;
        fp.label   = label;
        fp.geom    = GF.createPoint(new Coordinate(lng, lat));
        fp.address = address;
        return fp;
    }
}
