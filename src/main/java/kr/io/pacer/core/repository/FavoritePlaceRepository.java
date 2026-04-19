package kr.io.pacer.core.repository;

import kr.io.pacer.core.domain.FavoritePlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FavoritePlaceRepository extends JpaRepository<FavoritePlace, UUID> {

    List<FavoritePlace> findByUserIdOrderByVisitCountDesc(UUID userId);
}