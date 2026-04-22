package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.repository.FavoritePlaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoritePlaceService 단위 테스트")
class FavoritePlaceServiceTest {

    @InjectMocks FavoritePlaceService favoritePlaceService;
    @Mock FavoritePlaceRepository favoritePlaceRepository;

    @Test
    @DisplayName("즐겨찾기 조회 - 방문수 내림차순 목록 반환")
    void getFavorites_returnsSortedList() {
        UUID userId = UUID.randomUUID();
        FavoritePlace place1 = mock(FavoritePlace.class);
        FavoritePlace place2 = mock(FavoritePlace.class);

        given(favoritePlaceRepository.findByUserIdOrderByVisitCountDesc(userId))
                .willReturn(List.of(place1, place2));

        List<FavoritePlace> result = favoritePlaceService.getFavorites(userId);

        assertThat(result).hasSize(2).containsExactly(place1, place2);
    }

    @Test
    @DisplayName("즐겨찾기 조회 - 등록된 즐겨찾기 없음: 빈 리스트 반환")
    void getFavorites_noFavorites_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        given(favoritePlaceRepository.findByUserIdOrderByVisitCountDesc(userId))
                .willReturn(List.of());

        List<FavoritePlace> result = favoritePlaceService.getFavorites(userId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("즐겨찾기 조회 - Repository에 정확한 userId 전달")
    void getFavorites_passesCorrectUserIdToRepository() {
        UUID userId = UUID.randomUUID();
        given(favoritePlaceRepository.findByUserIdOrderByVisitCountDesc(userId))
                .willReturn(List.of());

        favoritePlaceService.getFavorites(userId);

        then(favoritePlaceRepository).should().findByUserIdOrderByVisitCountDesc(userId);
    }
}
