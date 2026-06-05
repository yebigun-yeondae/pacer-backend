package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "signal_history",
        indexes = @Index(name = "idx_signal_history_itst_dir_recorded",
                columnList = "itstId, direction, recordedAt")
)
@Getter
@NoArgsConstructor
public class SignalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer itstId;

    @Column(nullable = false, length = 2)
    private String direction;

    private String statNm;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    public static SignalHistory of(Integer itstId, String direction, String statNm, LocalDateTime recordedAt) {
        SignalHistory h = new SignalHistory();
        h.itstId      = itstId;
        h.direction   = direction;
        h.statNm      = statNm;
        h.recordedAt  = recordedAt;
        return h;
    }
}
