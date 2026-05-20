package kr.io.pacer.core.repository.jpa;

import kr.io.pacer.core.domain.RouteHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RouteHistoryRepository extends JpaRepository<RouteHistory, UUID> {

    List<RouteHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    void deleteByUserId(UUID userId);
}