package kr.io.pacer.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("TrafficSignal 도메인 단위 테스트")
class TrafficSignalTest {

    private TrafficSignal buildSignal(int greenDuration, int redDuration, int cycleOffset) {
        TrafficSignal signal = new TrafficSignal();
        ReflectionTestUtils.setField(signal, "greenDuration", greenDuration);
        ReflectionTestUtils.setField(signal, "redDuration", redDuration);
        ReflectionTestUtils.setField(signal, "cycleOffset", cycleOffset);
        return signal;
    }

    @Test
    @DisplayName("도착 시각이 녹색 신호 구간이면 대기시간 0")
    void calcWaitSeconds_arriveDuringGreen_returnsZero() {
        TrafficSignal signal = buildSignal(30, 30, 0);
        // phase = (10 + 0) % 60 = 10 < 30 → 녹색
        assertThat(signal.calcWaitSeconds(10)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("도착 시각이 적색 신호 구간이면 나머지 대기시간 반환")
    void calcWaitSeconds_arriveDuringRed_returnsRemainingWait() {
        TrafficSignal signal = buildSignal(30, 30, 0);
        // cycle=60, phase=(40+0)%60=40 ≥ 30 → 적색, wait=60-40=20
        assertThat(signal.calcWaitSeconds(40)).isCloseTo(20.0, within(0.0001));
    }

    @Test
    @DisplayName("cycleOffset 적용 시 phase 계산에 offset 반영됨")
    void calcWaitSeconds_withCycleOffset_adjustsPhase() {
        TrafficSignal signal = buildSignal(30, 30, 10);
        // phase = (5 + 10) % 60 = 15 < 30 → 녹색
        assertThat(signal.calcWaitSeconds(5)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("phase가 정확히 greenDuration이면 적색 신호 시작")
    void calcWaitSeconds_phaseExactlyAtGreenEnd_isRed() {
        TrafficSignal signal = buildSignal(30, 30, 0);
        // phase = 30 → 적색 시작, wait = 60 - 30 = 30
        assertThat(signal.calcWaitSeconds(30)).isCloseTo(30.0, within(0.0001));
    }

    @Test
    @DisplayName("phase가 0이면 녹색 신호 시작")
    void calcWaitSeconds_phaseZero_isGreen() {
        TrafficSignal signal = buildSignal(30, 30, 0);
        assertThat(signal.calcWaitSeconds(0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("한 사이클을 초과한 도착 시각도 정확히 계산됨")
    void calcWaitSeconds_beyondOneCycle_wrapsCorrectly() {
        TrafficSignal signal = buildSignal(30, 30, 0);
        // phase = (90 + 0) % 60 = 30 → 적색, wait = 60 - 30 = 30
        assertThat(signal.calcWaitSeconds(90)).isCloseTo(30.0, within(0.0001));
    }

    @Test
    @DisplayName("사이클 끝 직전 도착 시 1초 미만 대기")
    void calcWaitSeconds_nearEndOfCycle_returnsSmallWait() {
        TrafficSignal signal = buildSignal(30, 30, 0);
        // phase = 59 → wait = 60 - 59 = 1
        assertThat(signal.calcWaitSeconds(59)).isCloseTo(1.0, within(0.0001));
    }
}
