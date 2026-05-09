package kr.io.pacer.core.controller;

import jakarta.validation.Valid;
import kr.io.pacer.core.dto.request.RouteRequest;
import kr.io.pacer.core.dto.response.RouteHistoryResponse;
import kr.io.pacer.core.dto.response.RouteResponse;
import kr.io.pacer.core.service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping("/history")
    public ResponseEntity<List<RouteHistoryResponse>> getHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(routeService.getHistory(userId, pageable));
    }

    @PostMapping // 경로 탐색
    public ResponseEntity<RouteResponse> getRoute(
            @RequestBody @Valid RouteRequest request,
            Authentication authentication) {

        UUID userId = (UUID) authentication.getPrincipal();

        return ResponseEntity.ok(routeService.findRoute(request, userId));
    }
}
