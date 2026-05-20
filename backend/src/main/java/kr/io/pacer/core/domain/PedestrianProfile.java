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

    // EMA 방식으로 속도 점진 업데이트
    public void updateSpeed(double measuredSpeed) {
        this.avgSpeedMps = this.avgSpeedMps * 0.7 + measuredSpeed * 0.3;
    }

    public void recordRoute(double distanceM) {
        this.totalRoutes++;
        this.totalDistanceM += distanceM;
    }
}
