package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(name = "traffic_signals")
@Getter
@NoArgsConstructor
public class TrafficSignal {

    @Id
    private Long id;

    @Column(name = "node_id")
    private Long nodeId;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    private int greenDuration;
    private int redDuration;
    private int cycleOffset;

    @UpdateTimestamp
    private LocalDateTime lastSyncedAt;

    // 도착 예상 시각 기준 대기시간(초) 계산
    public double calcWaitSeconds(double arriveAfterSeconds) {
        double cycle = greenDuration + redDuration;
        double phase = (arriveAfterSeconds + cycleOffset) % cycle;
        if (phase < greenDuration) return 0.0;
        return cycle - phase;
    }
}
