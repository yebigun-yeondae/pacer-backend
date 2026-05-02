package kr.io.pacer.core.controller;

import jakarta.validation.Valid;
import kr.io.pacer.core.dto.request.WalkingUpdateRequest;
import kr.io.pacer.core.dto.response.ProfileResponse;
import kr.io.pacer.core.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping // 내 프로필 조회
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PatchMapping("/walking-speed") // 걸음 속도 업데이트
    public ResponseEntity<Void> updateWalkingSpeed(
            @RequestBody @Valid WalkingUpdateRequest request,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();
        profileService.updateWalkingSpeed(userId, request);
        return ResponseEntity.noContent().build();
    }
}
