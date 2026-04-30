package kr.io.pacer.core.controller;

import kr.io.pacer.core.dto.response.SignalResponse;
import kr.io.pacer.core.service.SignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/signal")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;

    @GetMapping
    public ResponseEntity<List<SignalResponse>> getSignals(
            @RequestParam List<Integer> itstIds) {
        return ResponseEntity.ok(signalService.getSignals(itstIds));
    }
}
