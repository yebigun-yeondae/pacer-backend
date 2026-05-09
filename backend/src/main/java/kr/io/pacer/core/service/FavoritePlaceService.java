package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.dto.request.AddFavoriteRequest;
import kr.io.pacer.core.exception.FavoriteNotFoundException;
import kr.io.pacer.core.exception.FavoriteNotOwnedException;
import kr.io.pacer.core.repository.jpa.FavoritePlaceRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoritePlaceService {

    private static final double NEARBY_DISTANCE_M = 50.0;

    private final FavoritePlaceRepository favoritePlaceRepository;
    private final UserRepository userRepository;

    public List<FavoritePlace> getFavorites(UUID userId) {
        List<FavoritePlace> result = favoritePlaceRepository.findByUserIdOrderByVisitCountDesc(userId);
        log.debug("[Favorite] 즐겨찾기 조회 | userId={} count={}", userId, result.size());
        return result;
    }

    @Transactional
    public FavoritePlace addFavorite(UUID userId, AddFavoriteRequest request) {
        User user = userRepository.getReferenceById(userId);
        FavoritePlace place = FavoritePlace.of(user, request.getLabel(), request.getLat(), request.getLng(), request.getAddress());
        FavoritePlace saved = favoritePlaceRepository.save(place);
        log.debug("[Favorite] 즐겨찾기 추가 | userId={} label={}", userId, request.getLabel());
        return saved;
    }

    @Transactional
    public void deleteFavorite(UUID userId, UUID favoriteId) {
        FavoritePlace place = favoritePlaceRepository.findById(favoriteId)
                .orElseThrow(() -> new FavoriteNotFoundException("즐겨찾기를 찾을 수 없습니다."));
        if (!place.getUser().getId().equals(userId)) {
            throw new FavoriteNotOwnedException("본인의 즐겨찾기만 삭제할 수 있습니다.");
        }
        favoritePlaceRepository.delete(place);
        log.debug("[Favorite] 즐겨찾기 삭제 | userId={} favoriteId={}", userId, favoriteId);
    }

    @Transactional
    public void incrementVisitIfNearby(UUID userId, double lat, double lng) {
        favoritePlaceRepository.findNearestWithinDistance(userId, lat, lng, NEARBY_DISTANCE_M)
                .ifPresent(place -> {
                    place.incrementVisit();
                    log.debug("[Favorite] 방문 횟수 증가 | userId={} label={} visitCount={}",
                            userId, place.getLabel(), place.getVisitCount());
                });
    }
}
