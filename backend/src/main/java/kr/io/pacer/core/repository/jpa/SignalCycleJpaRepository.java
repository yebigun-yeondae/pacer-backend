package kr.io.pacer.core.repository.jpa;

import kr.io.pacer.core.domain.SignalCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SignalCycleJpaRepository extends JpaRepository<SignalCycle, Long> {

    Optional<SignalCycle> findByItstIdAndDirection(Integer itstId, String direction);

    List<SignalCycle> findByItstIdIn(List<Integer> itstIds);

        @Modifying
        @Query(value = "INSERT INTO signal_cycle (itst_id, direction, green_duration, red_duration, updated_at) " +
                        "VALUES (:itstId, :direction, :greenDuration, :redDuration, now()) " +
                       "ON CONFLICT (itst_id, direction) " +
                       "DO UPDATE SET green_duration = EXCLUDED.green_duration, " +
                       "red_duration = EXCLUDED.red_duration, " +
                       "updated_at = EXCLUDED.updated_at",
                nativeQuery = true)
        void upsert(@Param("itstId") Integer itstId,
                    @Param("direction") String direction,
                    @Param("greenDuration") Integer greenDuration,
                    @Param("redDuration") Integer redDuration);
}
