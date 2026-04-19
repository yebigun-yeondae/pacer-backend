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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

        User user = findOrCreateUser(OAuthProvider.KAKAO, providerId, nickname, email, imageUrl);
        return issueToken(user);
    }

    @Transactional
    public TokenResponse loginWithGoogle(GoogleProfileDto dto) {
        User user = findOrCreateUser(
                OAuthProvider.GOOGLE,
                dto.getSub(),
                dto.getName(),
                dto.getEmail(),
                dto.getProfileImageUrl()
        );
        return issueToken(user);
    }

    // 기존 계정 조회 or 신규 생성 (카카오/구글 공통 로직)
    private User findOrCreateUser(OAuthProvider provider, String providerId,
                                  String nickname, String email, String imageUrl) {
        return oauthAccountRepository
                .findByProviderAndProviderId(provider, providerId)
                .map(oauth -> {
                    oauth.getUser().update(nickname, imageUrl);
                    return oauth.getUser();
                })
                .orElseGet(() -> {
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
            throw new InvalidTokenException("유효하지 않은 Refresh Token");
        }
        RefreshToken stored = refreshTokenRepository.findById(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("만료된 Refresh Token"));

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalStateException("유저 없음"));

        // Refresh Token Rotation
        refreshTokenRepository.delete(stored);
        String newAccess  = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String newRefresh = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(new RefreshToken(newRefresh, user.getId()));

        return new TokenResponse(newAccess, newRefresh);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findById(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }
}
