package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "signal_cycle",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_signal_cycle_itst_dir",
                columnNames = {"itstId", "direction"}
        )
)
@Getter
@NoArgsConstructor
public class SignalCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer itstId;

    @Column(nullable = false, length = 2)
    private String direction;

    private Integer greenDuration;

    private Integer redDuration;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public static SignalCycle of(Integer itstId, String direction, Integer greenDuration, Integer redDuration) {
        SignalCycle c = new SignalCycle();
        c.itstId         = itstId;
        c.direction      = direction;
        c.greenDuration  = greenDuration;
        c.redDuration    = redDuration;
        return c;
    }

    public void update(Integer greenDuration, Integer redDuration) {
        this.greenDuration = greenDuration;
        this.redDuration   = redDuration;
    }
}
