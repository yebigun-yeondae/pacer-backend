package kr.io.pacer.core.controller;

import kr.io.pacer.core.auth.JwtTokenProvider;
import kr.io.pacer.core.domain.SocialType;
import kr.io.pacer.core.domain.User;
import kr.io.pacer.core.dto.oauth2.AccessTokenDto;
import kr.io.pacer.core.dto.oauth2.GoogleProfileDto;
import kr.io.pacer.core.dto.oauth2.KakaoProfileDto;
import kr.io.pacer.core.dto.oauth2.RedirectDto;
import kr.io.pacer.core.service.GoogleAuthService;
import kr.io.pacer.core.service.KakaoAuthService;
import kr.io.pacer.core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleAuthService googleAuthService;
    private final KakaoAuthService kakaoAuthService;

    @PostMapping("/google/doLogin")
    public ResponseEntity<?> googleLogin(@RequestBody RedirectDto redirectDto) {

        AccessTokenDto accessTokenDto = googleAuthService.getAccessToken(redirectDto.getCode());

        GoogleProfileDto googleProfileDto = googleAuthService.getGoogleProfile(accessTokenDto.getAccess_token());

        User originalUser = userService.getMemberBySocialId(googleProfileDto.getSub());
        if(originalUser == null){
            originalUser = userService.createOauth(googleProfileDto.getSub(), googleProfileDto.getEmail(), SocialType.GOOGLE);
        }
        String jwtToken = jwtTokenProvider.createToken(originalUser.getEmail(), originalUser.getRole().toString());

        Map<String, Object> loginInfo = new HashMap<>();
        loginInfo.put("id", originalUser.getId());
        loginInfo.put("token", jwtToken);
        return new ResponseEntity<>(loginInfo, HttpStatus.OK);
    }

    @PostMapping("/kakao/doLogin")
    public ResponseEntity<?> kakaoLogin(@RequestBody RedirectDto redirectDto) {
        AccessTokenDto accessTokenDto = kakaoAuthService.getAccessToken(redirectDto.getCode());
        KakaoProfileDto kakaoProfileDto = kakaoAuthService.getKakaoProfile(accessTokenDto.getAccess_token());

        User originalUser = userService.getMemberBySocialId(kakaoProfileDto.getId());
        if(originalUser == null){
            originalUser = userService.createOauth(kakaoProfileDto.getId(), kakaoProfileDto.getKakao_account().getEmail(), SocialType.KAKAO);
        }

        String jwtToken = jwtTokenProvider.createToken(originalUser.getEmail(), originalUser.getRole().toString());

        Map<String, Object> loginInfo = new HashMap<>();
        loginInfo.put("id", originalUser.getId());
        loginInfo.put("token", jwtToken);
        return new ResponseEntity<>(loginInfo, HttpStatus.OK);
    }
}
