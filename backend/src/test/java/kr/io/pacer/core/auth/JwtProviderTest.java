package kr.io.pacer.core.auth;

import kr.io.pacer.core.domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtProvider 단위 테스트")
class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "secret",
                "test-secret-key-minimum-32-bytes-long!!");
        ReflectionTestUtils.setField(jwtProvider, "accessExpiryMs", 3_600_000L);
        ReflectionTestUtils.setField(jwtProvider, "refreshExpiryMs", 1_209_600_000L);
    }

    @Test
    @DisplayName("Access Token 생성 후 userId·role 추출 성공")
    void createAccessToken_and_extract() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.createAccessToken(userId, UserRole.USER);

        assertThat(jwtProvider.isValid(token)).isTrue();
        assertThat(jwtProvider.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtProvider.parse(token).get("role", String.class)).isEqualTo("USER");
    }

    @Test
    @DisplayName("Refresh Token 생성 후 userId 추출 성공")
    void createRefreshToken_and_extract() {
        UUID userId = UUID.randomUUID();
        String token = jwtProvider.createRefreshToken(userId);

        assertThat(jwtProvider.isValid(token)).isTrue();
        assertThat(jwtProvider.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("잘못된 형식의 토큰은 isValid false 반환")
    void isValid_withMalformedToken_returnsFalse() {
        assertThat(jwtProvider.isValid("this.is.not.a.jwt")).isFalse();
    }

    @Test
    @DisplayName("서명이 변조된 토큰은 isValid false 반환")
    void isValid_withTamperedToken_returnsFalse() {
        String token = jwtProvider.createAccessToken(UUID.randomUUID(), UserRole.USER);
        assertThat(jwtProvider.isValid(token + "tampered")).isFalse();
    }

    @Test
    @DisplayName("빈 문자열 토큰은 isValid false 반환")
    void isValid_withEmptyToken_returnsFalse() {
        assertThat(jwtProvider.isValid("")).isFalse();
    }

    @Test
    @DisplayName("서로 다른 userId로 생성한 토큰은 각각의 userId를 정확히 반환")
    void extractUserId_differentUsers_returnsCorrectId() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        String token1 = jwtProvider.createAccessToken(userId1, UserRole.USER);
        String token2 = jwtProvider.createAccessToken(userId2, UserRole.ADMIN);

        assertThat(jwtProvider.extractUserId(token1)).isEqualTo(userId1);
        assertThat(jwtProvider.extractUserId(token2)).isEqualTo(userId2);
    }

    @Test
    @DisplayName("ADMIN role로 Access Token 생성 시 role 클레임 ADMIN")
    void createAccessToken_withAdminRole_roleClaimIsAdmin() {
        String token = jwtProvider.createAccessToken(UUID.randomUUID(), UserRole.ADMIN);
        assertThat(jwtProvider.parse(token).get("role", String.class)).isEqualTo("ADMIN");
    }
}
