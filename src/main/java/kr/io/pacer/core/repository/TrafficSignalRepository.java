package kr.io.pacer.core.repository;

import kr.io.pacer.core.domain.TrafficSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface TrafficSignalRepository extends JpaRepository<TrafficSignal, Long> {

    List<TrafficSignal> findByNodeIdIn(List<Long> nodeIds);

    default Map<Long, TrafficSignal> findByNodeIds(List<Long> nodeIds) {
        return findByNodeIdIn(nodeIds).stream()
                .collect(Collectors.toMap(TrafficSignal::getNodeId, s -> s));
    }
}
