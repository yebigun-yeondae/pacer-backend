package kr.io.pacer.core.dto.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoProfileDto {

    private Long id;

    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;

    @Getter
    @NoArgsConstructor
    public static class KakaoAccount {

        private String email;
        private Profile profile;

        @Getter
        @NoArgsConstructor
        public static class Profile {

            private String nickname;

            @JsonProperty("profile_image_url")
            private String profileImageUrl;
        }
    }
}
