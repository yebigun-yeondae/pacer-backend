package kr.io.pacer.core.repository.jpa;

import kr.io.pacer.core.domain.PedestrianProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface PedestrianProfileRepository extends JpaRepository<PedestrianProfile, UUID> {

    Optional<PedestrianProfile> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE PedestrianProfile p SET p.totalRoutes = p.totalRoutes + 1, p.totalDistanceM = p.totalDistanceM + :distanceM WHERE p.user.id = :userId")
    void incrementRouteStats(@Param("userId") UUID userId, @Param("distanceM") double distanceM);
}