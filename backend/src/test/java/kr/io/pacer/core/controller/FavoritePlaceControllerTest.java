package kr.io.pacer.core.controller;

import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.exception.FavoriteNotFoundException;
import kr.io.pacer.core.exception.FavoriteNotOwnedException;
import kr.io.pacer.core.service.FavoritePlaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = FavoritePlaceController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtFilter.class}
        )
)
@Import(TestSecurityConfig.class)
@DisplayName("FavoritePlaceController 컨트롤러 테스트")
class FavoritePlaceControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean FavoritePlaceService favoritePlaceService;

    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private Authentication mockAuth() {
        return new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                List.of(new SimpleGrantedAuthority("USER"))
        );
    }

    // ─── GET ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/favorites - 즐겨찾기 있음: 200 + 목록 반환")
    void getFavorites_withItems_returns200WithList() throws Exception {
        FavoritePlace place = mock(FavoritePlace.class);
        given(favoritePlaceService.getFavorites(TEST_USER_ID)).willReturn(List.of(place));

        mockMvc.perform(get("/api/v1/favorites")
                        .with(authentication(mockAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/favorites - 즐겨찾기 없음: 200 + 빈 배열")
    void getFavorites_noItems_returns200WithEmptyArray() throws Exception {
        given(favoritePlaceService.getFavorites(TEST_USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/favorites")
                        .with(authentication(mockAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/favorites - Service에 정확한 userId 전달")
    void getFavorites_passesCorrectUserIdToService() throws Exception {
        given(favoritePlaceService.getFavorites(TEST_USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/favorites")
                        .with(authentication(mockAuth())))
                .andExpect(status().isOk());

        then(favoritePlaceService).should().getFavorites(TEST_USER_ID);
    }

    // ─── POST ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/favorites - 정상 추가: 201 반환")
    void addFavorite_validRequest_returns201() throws Exception {
        FavoritePlace saved = mock(FavoritePlace.class);
        given(favoritePlaceService.addFavorite(eq(TEST_USER_ID), any())).willReturn(saved);

        mockMvc.perform(post("/api/v1/favorites")
                        .with(authentication(mockAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"집","lat":37.5665,"lng":126.9780,"address":"서울시 중구"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/v1/favorites - label 누락: 400 반환")
    void addFavorite_missingLabel_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/favorites")
                        .with(authentication(mockAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lat":37.5665,"lng":126.9780}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ─── DELETE ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/favorites/{id} - 정상 삭제: 204 반환")
    void deleteFavorite_success_returns204() throws Exception {
        UUID favoriteId = UUID.randomUUID();
        willDoNothing().given(favoritePlaceService).deleteFavorite(TEST_USER_ID, favoriteId);

        mockMvc.perform(delete("/api/v1/favorites/{id}", favoriteId)
                        .with(authentication(mockAuth())))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/favorites/{id} - 존재하지 않음: 404 반환")
    void deleteFavorite_notFound_returns404() throws Exception {
        UUID favoriteId = UUID.randomUUID();
        willThrow(new FavoriteNotFoundException("즐겨찾기를 찾을 수 없습니다."))
                .given(favoritePlaceService).deleteFavorite(TEST_USER_ID, favoriteId);

        mockMvc.perform(delete("/api/v1/favorites/{id}", favoriteId)
                        .with(authentication(mockAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FAVORITE_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/v1/favorites/{id} - 다른 유저 소유: 403 반환")
    void deleteFavorite_notOwned_returns403() throws Exception {
        UUID favoriteId = UUID.randomUUID();
        willThrow(new FavoriteNotOwnedException("본인의 즐겨찾기만 삭제할 수 있습니다."))
                .given(favoritePlaceService).deleteFavorite(TEST_USER_ID, favoriteId);

        mockMvc.perform(delete("/api/v1/favorites/{id}", favoriteId)
                        .with(authentication(mockAuth())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FAVORITE_NOT_OWNED"));
    }
}
