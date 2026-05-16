package kr.io.pacer.core.repository.jpa;

import kr.io.pacer.core.domain.PedestrianProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PedestrianProfileRepository extends JpaRepository<PedestrianProfile, UUID> {

    Optional<PedestrianProfile> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}