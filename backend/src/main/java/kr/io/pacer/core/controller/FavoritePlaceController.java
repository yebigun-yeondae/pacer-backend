package kr.io.pacer.core.controller;

import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.service.FavoritePlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoritePlaceController {

    private final FavoritePlaceService favoritePlaceService;

    @GetMapping // 즐겨찾기 목록 조회
    public ResponseEntity<List<FavoritePlace>> getFavorites(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(favoritePlaceService.getFavorites(userId));
    }
}
