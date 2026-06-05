package kr.io.pacer.core.repository.jpa;

import kr.io.pacer.core.domain.FavoritePlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoritePlaceRepository extends JpaRepository<FavoritePlace, UUID> {

    List<FavoritePlace> findByUserIdOrderByVisitCountDesc(UUID userId);

    @Query(value = """
            SELECT * FROM favorite_places
            WHERE user_id = :userId
            AND ST_DWithin(
                geom::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                :distanceM
            )
            ORDER BY ST_Distance(
                geom::geography,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
            )
            LIMIT 1
            """, nativeQuery = true)
    Optional<FavoritePlace> findNearestWithinDistance(
            @Param("userId") UUID userId,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("distanceM") double distanceM);

    void deleteByUserId(UUID userId);
}