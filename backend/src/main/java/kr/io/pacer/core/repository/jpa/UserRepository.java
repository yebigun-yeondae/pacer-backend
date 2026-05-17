package kr.io.pacer.core.repository.jpa;


import kr.io.pacer.core.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByDeletedAtIsNotNullAndDeletedAtBefore(LocalDateTime cutoff);
}