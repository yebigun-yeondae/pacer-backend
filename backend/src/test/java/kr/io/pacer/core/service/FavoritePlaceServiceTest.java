package kr.io.pacer.core.service;

import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.dto.request.AddFavoriteRequest;
import kr.io.pacer.core.exception.FavoriteNotFoundException;
import kr.io.pacer.core.exception.FavoriteNotOwnedException;
import kr.io.pacer.core.repository.jpa.FavoritePlaceRepository;
import kr.io.pacer.core.repository.jpa.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FavoritePlaceService 단위 테스트")
class FavoritePlaceServiceTest {

    @InjectMocks FavoritePlaceService favoritePlaceService;
    @Mock FavoritePlaceRepository favoritePlaceRepository;
    @Mock UserRepository userRepository;

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

    @Test
    @DisplayName("즐겨찾기 추가 - 저장된 즐겨찾기 반환")
    void addFavorite_savesAndReturnsPlace() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        AddFavoriteRequest request = mock(AddFavoriteRequest.class);
        given(request.getLabel()).willReturn("집");
        given(request.getLat()).willReturn(37.5665);
        given(request.getLng()).willReturn(126.9780);
        given(request.getAddress()).willReturn("서울시 중구");
        given(userRepository.getReferenceById(userId)).willReturn(user);

        FavoritePlace saved = mock(FavoritePlace.class);
        given(favoritePlaceRepository.save(any(FavoritePlace.class))).willReturn(saved);

        FavoritePlace result = favoritePlaceService.addFavorite(userId, request);

        assertThat(result).isEqualTo(saved);
        then(favoritePlaceRepository).should().save(any(FavoritePlace.class));
    }

    @Test
    @DisplayName("즐겨찾기 삭제 - 존재하지 않음: FavoriteNotFoundException")
    void deleteFavorite_notFound_throwsFavoriteNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID favoriteId = UUID.randomUUID();
        given(favoritePlaceRepository.findById(favoriteId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> favoritePlaceService.deleteFavorite(userId, favoriteId))
                .isInstanceOf(FavoriteNotFoundException.class);
    }

    @Test
    @DisplayName("즐겨찾기 삭제 - 다른 유저 소유: FavoriteNotOwnedException")
    void deleteFavorite_notOwned_throwsFavoriteNotOwnedException() {
        UUID userId = UUID.randomUUID();
        UUID favoriteId = UUID.randomUUID();
        User owner = mock(User.class);
        given(owner.getId()).willReturn(UUID.randomUUID()); // 다른 userId
        FavoritePlace place = mock(FavoritePlace.class);
        given(place.getUser()).willReturn(owner);
        given(favoritePlaceRepository.findById(favoriteId)).willReturn(Optional.of(place));

        assertThatThrownBy(() -> favoritePlaceService.deleteFavorite(userId, favoriteId))
                .isInstanceOf(FavoriteNotOwnedException.class);
    }

    @Test
    @DisplayName("즐겨찾기 삭제 - 정상 삭제")
    void deleteFavorite_success_deletesPlace() {
        UUID userId = UUID.randomUUID();
        UUID favoriteId = UUID.randomUUID();
        User owner = mock(User.class);
        given(owner.getId()).willReturn(userId);
        FavoritePlace place = mock(FavoritePlace.class);
        given(place.getUser()).willReturn(owner);
        given(favoritePlaceRepository.findById(favoriteId)).willReturn(Optional.of(place));

        favoritePlaceService.deleteFavorite(userId, favoriteId);

        then(favoritePlaceRepository).should().delete(place);
    }

    @Test
    @DisplayName("방문 횟수 증가 - 근처 즐겨찾기 있음: incrementVisit 호출")
    void incrementVisitIfNearby_nearbyFavoriteFound_incrementsVisit() {
        UUID userId = UUID.randomUUID();
        FavoritePlace place = mock(FavoritePlace.class);
        given(favoritePlaceRepository.findNearestWithinDistance(userId, 37.5, 127.0, 50.0))
                .willReturn(Optional.of(place));

        favoritePlaceService.incrementVisitIfNearby(userId, 37.5, 127.0);

        then(place).should().incrementVisit();
    }

    @Test
    @DisplayName("방문 횟수 증가 - 근처 즐겨찾기 없음: incrementVisit 미호출")
    void incrementVisitIfNearby_noNearbyFavorite_noIncrement() {
        UUID userId = UUID.randomUUID();
        given(favoritePlaceRepository.findNearestWithinDistance(userId, 37.5, 127.0, 50.0))
                .willReturn(Optional.empty());

        favoritePlaceService.incrementVisitIfNearby(userId, 37.5, 127.0);

        then(favoritePlaceRepository).should().findNearestWithinDistance(userId, 37.5, 127.0, 50.0);
    }
}
