package kr.io.pacer.core.controller;

import jakarta.validation.Valid;
import kr.io.pacer.core.domain.FavoritePlace;
import kr.io.pacer.core.dto.request.AddFavoriteRequest;
import kr.io.pacer.core.service.FavoritePlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @GetMapping
    public ResponseEntity<List<FavoritePlace>> getFavorites(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(favoritePlaceService.getFavorites(userId));
    }

    @PostMapping
    public ResponseEntity<FavoritePlace> addFavorite(
            @RequestBody @Valid AddFavoriteRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(favoritePlaceService.addFavorite(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFavorite(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        favoritePlaceService.deleteFavorite(userId, id);
        return ResponseEntity.noContent().build();
    }
}
