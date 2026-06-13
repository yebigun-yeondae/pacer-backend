package kr.io.pacer.core.controller;

import jakarta.validation.constraints.DecimalMin;
import kr.io.pacer.core.dto.response.SubwayArrivalResponse;
import kr.io.pacer.core.dto.response.SubwayStationResponse;
import kr.io.pacer.core.service.SubwayStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/subway-stations")
@RequiredArgsConstructor
public class SubwayStationController {

    private final SubwayStationService subwayStationService;

    @GetMapping("/nearby")
    public ResponseEntity<List<SubwayStationResponse>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @DecimalMin("0") @RequestParam(defaultValue = "500") double radiusM) {
        return ResponseEntity.ok(subwayStationService.findNearby(lat, lng, radiusM));
    }

    @GetMapping("/{stationNm}/arrivals")
    public ResponseEntity<List<SubwayArrivalResponse>> getArrivals(
            @PathVariable String stationNm) {
        return ResponseEntity.ok(subwayStationService.findArrivals(stationNm));
    }
}
