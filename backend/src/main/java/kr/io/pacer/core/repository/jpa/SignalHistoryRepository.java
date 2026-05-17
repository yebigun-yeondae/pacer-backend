package kr.io.pacer.core.repository.jpa;

import kr.io.pacer.core.domain.SignalHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignalHistoryRepository extends JpaRepository<SignalHistory, Long> {

    List<SignalHistory> findByItstIdAndDirectionOrderByRecordedAtDesc(Integer itstId, String direction, Pageable pageable);
}
