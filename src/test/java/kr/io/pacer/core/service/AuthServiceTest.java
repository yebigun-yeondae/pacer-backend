package kr.io.pacer.core.service;

import kr.io.pacer.core.auth.JwtProvider;
import kr.io.pacer.core.domain.*;
import kr.io.pacer.core.domain.enums.OAuthProvider;
import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import kr.io.pacer.core.dto.request.LoginRequest;
import kr.io.pacer.core.dto.request.SignupRequest;
import kr.io.pacer.core.dto.response.TokenResponse;
import kr.io.pacer.core.exception.DuplicateEmailException;
import kr.io.pacer.core.exception.InvalidCredentialsException;
import kr.io.pacer.core.exception.InvalidTokenException;
import kr.io.pacer.core.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @InjectMocks AuthService authService;
    @Mock UserRepository userRepository;
    @Mock UserOAuthAccountRepository oauthAccountRepository;
    @Mock PedestrianProfileRepository profileRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtProvider jwtProvider;
    @Mock PasswordEncoder passwordEncoder;

    // ── 일반 회원가입 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("회원가입 - 신규 이메일: User·Profile 저장 후 토큰 발급")
    void signup_newEmail_savesUserAndReturnsTokens() {
        SignupRequest request = makeSignupRequest("new@test.com", "password1!", "테스터");
        User savedUser = User.ofLocal("테스터", "new@test.com", "encoded");

        given(userRepository.existsByEmail("new@test.com")).willReturn(false);
        given(passwordEncoder.encode("password1!")).willReturn("encoded");
        given(userRepository.save(any())).willReturn(savedUser);
        given(profileRepository.save(any())).willReturn(mock(PedestrianProfile.class));
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh");

        TokenResponse result = authService.signup(request);

        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getRefreshToken()).isEqualTo("refresh");
        then(userRepository).should().save(any());
        then(profileRepository).should().save(any());
    }

    @Test
    @DisplayName("회원가입 - 중복 이메일: DuplicateEmailException")
    void signup_duplicateEmail_throwsDuplicateEmailException() {
        SignupRequest request = makeSignupRequest("dup@test.com", "password1!", "중복유저");
        given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateEmailException.class);
        then(userRepository).should(never()).save(any());
    }

    // ── 일반 로그인 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로그인 - 올바른 이메일·비밀번호: 토큰 발급")
    void login_validCredentials_returnsTokens() {
        LoginRequest request = makeLoginRequest("user@test.com", "password1!");
        User user = User.ofLocal("유저", "user@test.com", "encoded");

        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1!", "encoded")).willReturn(true);
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh");

        TokenResponse result = authService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    @DisplayName("로그인 - 존재하지 않는 이메일: InvalidCredentialsException")
    void login_unknownEmail_throwsInvalidCredentialsException() {
        LoginRequest request = makeLoginRequest("ghost@test.com", "password1!");
        given(userRepository.findByEmail("ghost@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("로그인 - 비밀번호 불일치: InvalidCredentialsException")
    void login_wrongPassword_throwsInvalidCredentialsException() {
        LoginRequest request = makeLoginRequest("user@test.com", "wrongpw");
        User user = User.ofLocal("유저", "user@test.com", "encoded");

        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongpw", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("로그인 - OAuth 전용 유저(비밀번호 없음): InvalidCredentialsException")
    void login_oauthOnlyUser_throwsInvalidCredentialsException() {
        LoginRequest request = makeLoginRequest("oauth@test.com", "password1!");
        User oauthUser = User.of("OAuth유저", "oauth@test.com", null); // password == null

        given(userRepository.findByEmail("oauth@test.com")).willReturn(Optional.of(oauthUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    // ── 카카오 로그인 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("카카오 로그인 - 기존 유저: 토큰 발급, 신규 DB 저장 없음")
    void loginWithKakao_existingUser_returnsTokensWithoutSaving() {
        User user = User.of("카카오유저", "kakao@test.com", "http://img.com/1.jpg");
        UserOAuthAccount oauthAccount = UserOAuthAccount.of(user, OAuthProvider.KAKAO, "12345");

        given(oauthAccountRepository.findByProviderAndProviderId(OAuthProvider.KAKAO, "12345"))
                .willReturn(Optional.of(oauthAccount));
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh");

        TokenResponse result = authService.loginWithKakao(makeKakaoProfile(12345L, "카카오유저", "kakao@test.com", "http://img.com/1.jpg"));

        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getRefreshToken()).isEqualTo("refresh");
        then(userRepository).should(never()).save(any());
        then(profileRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("카카오 로그인 - 신규 유저: User·OAuthAccount·Profile 저장 후 토큰 발급")
    void loginWithKakao_newUser_createsAllEntitiesAndReturnsTokens() {
        User newUser = User.of("신규", "new@test.com", "http://img.com/2.jpg");

        given(oauthAccountRepository.findByProviderAndProviderId(any(), any()))
                .willReturn(Optional.empty());
        given(userRepository.save(any())).willReturn(newUser);
        given(oauthAccountRepository.save(any())).willReturn(mock(UserOAuthAccount.class));
        given(profileRepository.save(any())).willReturn(mock(PedestrianProfile.class));
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh");

        TokenResponse result = authService.loginWithKakao(makeKakaoProfile(99999L, "신규", "new@test.com", "http://img.com/2.jpg"));

        assertThat(result.getAccessToken()).isNotNull();
        then(userRepository).should().save(any());
        then(oauthAccountRepository).should().save(any());
        then(profileRepository).should().save(any());
    }

    // ── 구글 로그인 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("구글 로그인 - 기존 유저: 토큰 발급, 신규 DB 저장 없음")
    void loginWithGoogle_existingUser_returnsTokensWithoutSaving() {
        User user = User.of("구글유저", "google@test.com", "http://img.com/3.jpg");
        UserOAuthAccount oauthAccount = UserOAuthAccount.of(user, OAuthProvider.GOOGLE, "google-sub-123");

        given(oauthAccountRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "google-sub-123"))
                .willReturn(Optional.of(oauthAccount));
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh");

        TokenResponse result = authService.loginWithGoogle(makeGoogleProfile("google-sub-123", "구글유저", "google@test.com", "http://img.com/3.jpg"));

        assertThat(result.getAccessToken()).isEqualTo("access");
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("구글 로그인 - 신규 유저: User·OAuthAccount·Profile 저장 후 토큰 발급")
    void loginWithGoogle_newUser_createsAllEntitiesAndReturnsTokens() {
        User newUser = User.of("신규구글", "newgoogle@test.com", "http://img.com/4.jpg");

        given(oauthAccountRepository.findByProviderAndProviderId(any(), any()))
                .willReturn(Optional.empty());
        given(userRepository.save(any())).willReturn(newUser);
        given(oauthAccountRepository.save(any())).willReturn(mock(UserOAuthAccount.class));
        given(profileRepository.save(any())).willReturn(mock(PedestrianProfile.class));
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access");
        given(jwtProvider.createRefreshToken(any())).willReturn("refresh");

        TokenResponse result = authService.loginWithGoogle(makeGoogleProfile("new-sub", "신규구글", "newgoogle@test.com", "http://img.com/4.jpg"));

        assertThat(result.getAccessToken()).isNotNull();
        then(userRepository).should().save(any());
    }

    // ── 토큰 재발급 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("토큰 재발급 - 유효한 리프레시 토큰: 기존 삭제 후 새 토큰 발급")
    void reissue_validRefreshToken_deletesOldAndReturnsNew() {
        UUID userId = UUID.randomUUID();
        User user = User.of("유저", "user@test.com", null);
        RefreshToken stored = new RefreshToken("old-refresh", userId);

        given(jwtProvider.isValid("old-refresh")).willReturn(true);
        given(refreshTokenRepository.findById("old-refresh")).willReturn(Optional.of(stored));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(jwtProvider.createAccessToken(any(), any())).willReturn("new-access");
        given(jwtProvider.createRefreshToken(any())).willReturn("new-refresh");

        TokenResponse result = authService.reissue("old-refresh");

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh");
        then(refreshTokenRepository).should().delete(stored);
        then(refreshTokenRepository).should().save(any());
    }

    @Test
    @DisplayName("토큰 재발급 - 서명이 유효하지 않은 토큰: InvalidTokenException")
    void reissue_invalidSignature_throwsInvalidTokenException() {
        given(jwtProvider.isValid("bad-token")).willReturn(false);
        assertThatThrownBy(() -> authService.reissue("bad-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("토큰 재발급 - Redis에 존재하지 않는(만료된) 토큰: InvalidTokenException")
    void reissue_expiredToken_throwsInvalidTokenException() {
        given(jwtProvider.isValid("expired")).willReturn(true);
        given(refreshTokenRepository.findById("expired")).willReturn(Optional.empty());
        assertThatThrownBy(() -> authService.reissue("expired"))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ── 로그아웃 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("로그아웃 - Redis에 있는 토큰: 삭제 처리")
    void logout_existingToken_deletesIt() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = new RefreshToken("my-refresh", userId);
        given(refreshTokenRepository.findById("my-refresh")).willReturn(Optional.of(stored));

        authService.logout("my-refresh");

        then(refreshTokenRepository).should().delete(stored);
    }

    @Test
    @DisplayName("로그아웃 - Redis에 없는 토큰: 예외 없이 무시")
    void logout_nonExistentToken_doesNothing() {
        given(refreshTokenRepository.findById("ghost")).willReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> authService.logout("ghost"));
        then(refreshTokenRepository).should(never()).delete(any());
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private SignupRequest makeSignupRequest(String email, String password, String nickname) {
        SignupRequest req = new SignupRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        ReflectionTestUtils.setField(req, "nickname", nickname);
        return req;
    }

    private LoginRequest makeLoginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    private KakaoProfileDto makeKakaoProfile(long id, String nickname, String email, String imageUrl) {
        KakaoProfileDto dto = new KakaoProfileDto();
        ReflectionTestUtils.setField(dto, "id", id);

        KakaoProfileDto.KakaoAccount account = new KakaoProfileDto.KakaoAccount();
        ReflectionTestUtils.setField(account, "email", email);

        KakaoProfileDto.KakaoAccount.Profile profile = new KakaoProfileDto.KakaoAccount.Profile();
        ReflectionTestUtils.setField(profile, "nickname", nickname);
        ReflectionTestUtils.setField(profile, "profileImageUrl", imageUrl);

        ReflectionTestUtils.setField(account, "profile", profile);
        ReflectionTestUtils.setField(dto, "kakaoAccount", account);
        return dto;
    }

    private GoogleProfileDto makeGoogleProfile(String sub, String name, String email, String picture) {
        GoogleProfileDto dto = new GoogleProfileDto();
        ReflectionTestUtils.setField(dto, "sub", sub);
        ReflectionTestUtils.setField(dto, "name", name);
        ReflectionTestUtils.setField(dto, "email", email);
        ReflectionTestUtils.setField(dto, "profileImageUrl", picture);
        return dto;
    }
}
