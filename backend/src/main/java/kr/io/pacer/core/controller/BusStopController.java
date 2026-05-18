package kr.io.pacer.core.controller;

import kr.io.pacer.core.dto.response.BusStopResponse;
import kr.io.pacer.core.service.BusStopService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bus-stops")
@RequiredArgsConstructor
public class BusStopController {

    private final BusStopService busStopService;

    @GetMapping("/nearby")
    public ResponseEntity<List<BusStopResponse>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") double radiusM) {
        return ResponseEntity.ok(busStopService.findNearby(lat, lng, radiusM));
    }

    @GetMapping("/search")
    public ResponseEntity<List<BusStopResponse>> search(
            @RequestParam String keyword) {
        return ResponseEntity.ok(busStopService.search(keyword));
    }

    @GetMapping("/{stopId}")
    public ResponseEntity<BusStopResponse> getById(
            @PathVariable String stopId) {
        return ResponseEntity.ok(busStopService.findById(stopId));
    }
}
