package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String label;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    private String address;
    private int    visitCount = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public void incrementVisit() {
        this.visitCount++;
    }
}
