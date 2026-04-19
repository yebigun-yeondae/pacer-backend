package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.repository.FavoritePlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoritePlaceService {

    private final FavoritePlaceRepository favoritePlaceRepository;

    public List<FavoritePlace> getFavorites(UUID userId) {
        List<FavoritePlace> result = favoritePlaceRepository.findByUserIdOrderByVisitCountDesc(userId);
        log.debug("[Favorite] 즐겨찾기 조회 | userId={} count={}", userId, result.size());
        return result;
    }
}
