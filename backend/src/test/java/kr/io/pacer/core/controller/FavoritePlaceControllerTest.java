package kr.io.pacer.core.controller;

import kr.io.pacer.core.auth.JwtFilter;
import kr.io.pacer.core.config.SecurityConfig;
import kr.io.pacer.core.config.TestSecurityConfig;
import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.service.FavoritePlaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockBean FavoritePlaceService favoritePlaceService;

    private static final UUID TEST_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private Authentication mockAuth() {
        return new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                List.of(new SimpleGrantedAuthority("USER"))
        );
    }

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
}
