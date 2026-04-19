package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.repository.FavoritePlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FavoritePlaceService {

    private final FavoritePlaceRepository favoritePlaceRepository;

    public List<FavoritePlace> getFavorites(UUID userId) {
        return favoritePlaceRepository.findByUserIdOrderByVisitCountDesc(userId);
    }
}
