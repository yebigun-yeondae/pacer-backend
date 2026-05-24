package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pedestrian_profiles")
@Getter
@NoArgsConstructor
public class PedestrianProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private double avgSpeedMps    = 1.4;
    private double uphillFactor   = 0.8;
    private double downhillFactor = 1.1;
    private int    totalRoutes    = 0;
    @Column(name = "total_distance_m")
    private double totalDistanceM = 0.0;
    private double speedStd       = 0.2;
    private int    tripCount      = 0;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public static PedestrianProfile createDefault(User user) {
        PedestrianProfile p = new PedestrianProfile();
        p.user = user;
        return p;
    }

    public double adjustedSpeed(double slopeDeg) {
        if (slopeDeg > 3)  return avgSpeedMps * uphillFactor;
        if (slopeDeg < -3) return avgSpeedMps * downhillFactor;
        return avgSpeedMps;
    }

    public void updateFromAi(double avgSpeed, double speedStd, int tripCount) {
        this.avgSpeedMps = avgSpeed;
        this.speedStd    = speedStd;
        this.tripCount   = tripCount;
    }

    public void incrementTripCount(int tripCount) {
        this.tripCount = tripCount;
    }

    public void recordRoute(double distanceM) {
        this.totalRoutes++;
        this.totalDistanceM += distanceM;
    }
}
