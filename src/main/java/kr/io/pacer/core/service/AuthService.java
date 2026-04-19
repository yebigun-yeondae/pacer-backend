package kr.io.pacer.core.service;

import kr.io.pacer.core.auth.JwtProvider;
import kr.io.pacer.core.domain.PedestrianProfile;
import kr.io.pacer.core.domain.RefreshToken;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.domain.UserOAuthAccount;
import kr.io.pacer.core.domain.enums.OAuthProvider;
import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import kr.io.pacer.core.dto.response.TokenResponse;
import kr.io.pacer.core.exception.InvalidTokenException;
import kr.io.pacer.core.repository.PedestrianProfileRepository;
import kr.io.pacer.core.repository.RefreshTokenRepository;
import kr.io.pacer.core.repository.UserOAuthAccountRepository;
import kr.io.pacer.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserOAuthAccountRepository oauthAccountRepository;
    private final PedestrianProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;

    @Transactional
    public TokenResponse loginWithKakao(KakaoProfileDto dto) {
        String providerId  = String.valueOf(dto.getId());
        String nickname    = dto.getKakaoAccount().getProfile().getNickname();
        String imageUrl    = dto.getKakaoAccount().getProfile().getProfileImageUrl();
        String email       = dto.getKakaoAccount().getEmail();

        log.info("[Auth] 카카오 로그인 시도 | providerId={}", providerId);
        User user = findOrCreateUser(OAuthProvider.KAKAO, providerId, nickname, email, imageUrl);
        log.info("[Auth] 카카오 로그인 완료 | userId={}", user.getId());
        return issueToken(user);
    }

    @Transactional
    public TokenResponse loginWithGoogle(GoogleProfileDto dto) {
        log.info("[Auth] 구글 로그인 시도 | providerId={}", dto.getSub());
        User user = findOrCreateUser(
                OAuthProvider.GOOGLE,
                dto.getSub(),
                dto.getName(),
                dto.getEmail(),
                dto.getProfileImageUrl()
        );
        log.info("[Auth] 구글 로그인 완료 | userId={}", user.getId());
        return issueToken(user);
    }

    private User findOrCreateUser(OAuthProvider provider, String providerId,
                                  String nickname, String email, String imageUrl) {
        return oauthAccountRepository
                .findByProviderAndProviderId(provider, providerId)
                .map(oauth -> {
                    log.debug("[Auth] 기존 유저 로그인 | provider={} userId={}", provider, oauth.getUser().getId());
                    oauth.getUser().update(nickname, imageUrl);
                    return oauth.getUser();
                })
                .orElseGet(() -> {
                    log.info("[Auth] 신규 유저 생성 | provider={} email={}", provider, email);
                    User newUser = userRepository.save(
                            User.of(nickname, email, imageUrl));
                    oauthAccountRepository.save(
                            UserOAuthAccount.of(newUser, provider, providerId));
                    profileRepository.save(
                            PedestrianProfile.createDefault(newUser));
                    return newUser;
                });
    }

    private TokenResponse issueToken(User user) {
        String accessToken  = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(new RefreshToken(refreshToken, user.getId()));
        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        if (!jwtProvider.isValid(refreshToken)) {
            log.warn("[Auth] 유효하지 않은 Refresh Token으로 재발급 시도");
            throw new InvalidTokenException("유효하지 않은 Refresh Token");
        }
        RefreshToken stored = refreshTokenRepository.findById(refreshToken)
                .orElseThrow(() -> {
                    log.warn("[Auth] 만료된 Refresh Token으로 재발급 시도");
                    return new InvalidTokenException("만료된 Refresh Token");
                });

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalStateException("유저 없음"));

        refreshTokenRepository.delete(stored);
        String newAccess  = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String newRefresh = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(new RefreshToken(newRefresh, user.getId()));

        log.info("[Auth] 토큰 재발급 완료 | userId={}", user.getId());
        return new TokenResponse(newAccess, newRefresh);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findById(refreshToken)
                .ifPresent(stored -> {
                    log.info("[Auth] 로그아웃 | userId={}", stored.getUserId());
                    refreshTokenRepository.delete(stored);
                });
    }
}
