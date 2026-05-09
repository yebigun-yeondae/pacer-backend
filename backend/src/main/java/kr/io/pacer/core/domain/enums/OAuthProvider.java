package kr.io.pacer.core.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OAuthProvider {

    KAKAO("kakao"),
    GOOGLE("google");

    private final String key; // application.yml provider key와 일치

    public static OAuthProvider from(String key) {
        for (OAuthProvider p : values()) {
            if (p.key.equalsIgnoreCase(key)) return p;
        }
        throw new IllegalArgumentException("지원하지 않는 OAuth provider: " + key);
    }
}
