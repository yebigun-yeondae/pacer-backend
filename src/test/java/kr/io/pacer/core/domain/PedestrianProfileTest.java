package kr.io.pacer.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("PedestrianProfile 도메인 단위 테스트")
class PedestrianProfileTest {

    private PedestrianProfile profile;

    @BeforeEach
    void setUp() {
        User user = User.of("테스터", "test@example.com", "http://img.com/1.jpg");
        profile = PedestrianProfile.createDefault(user);
    }

    @Test
    @DisplayName("기본 생성 시 평균 속도는 1.4m/s")
    void createDefault_initialSpeed_is1point4() {
        assertThat(profile.getAvgSpeedMps()).isEqualTo(1.4);
        assertThat(profile.getTotalRoutes()).isZero();
        assertThat(profile.getTotalDistanceM()).isZero();
    }

    @Test
    @DisplayName("평지(기울기 0도)에서는 기본 속도 반환")
    void adjustedSpeed_flatTerrain_returnsAvgSpeed() {
        assertThat(profile.adjustedSpeed(0)).isEqualTo(1.4);
    }

    @Test
    @DisplayName("오르막(기울기 3도 초과)에서는 속도 감소(x0.8)")
    void adjustedSpeed_uphill_returnsReducedSpeed() {
        assertThat(profile.adjustedSpeed(5)).isCloseTo(1.4 * 0.8, within(0.0001));
    }

    @Test
    @DisplayName("내리막(기울기 -3도 미만)에서는 속도 증가(x1.1)")
    void adjustedSpeed_downhill_returnsIncreasedSpeed() {
        assertThat(profile.adjustedSpeed(-5)).isCloseTo(1.4 * 1.1, within(0.0001));
    }

    @Test
    @DisplayName("기울기 정확히 3도일 때는 평지로 처리")
    void adjustedSpeed_atExactBoundaryOf3deg_treatedAsFlat() {
        assertThat(profile.adjustedSpeed(3)).isEqualTo(1.4);
    }

    @Test
    @DisplayName("기울기 정확히 -3도일 때는 평지로 처리")
    void adjustedSpeed_atExactBoundaryOfMinus3deg_treatedAsFlat() {
        assertThat(profile.adjustedSpeed(-3)).isEqualTo(1.4);
    }

    @Test
    @DisplayName("updateSpeed: EMA 방식으로 속도 업데이트 (70% 기존 + 30% 신규)")
    void updateSpeed_appliesEmaFormula() {
        double measured = 2.0;
        double expected = 1.4 * 0.7 + 2.0 * 0.3; // 1.58

        profile.updateSpeed(measured);

        assertThat(profile.getAvgSpeedMps()).isCloseTo(expected, within(0.0001));
    }

    @Test
    @DisplayName("updateSpeed: 연속 업데이트 시 EMA가 누적 반영됨")
    void updateSpeed_consecutiveCalls_accumulatesEma() {
        profile.updateSpeed(2.0); // 1.4*0.7 + 2.0*0.3 = 1.58
        double afterFirst = profile.getAvgSpeedMps();

        profile.updateSpeed(1.0); // afterFirst*0.7 + 1.0*0.3
        double expected = afterFirst * 0.7 + 1.0 * 0.3;

        assertThat(profile.getAvgSpeedMps()).isCloseTo(expected, within(0.0001));
    }

    @Test
    @DisplayName("recordRoute: 경로 기록 시 총 경로 수와 거리 누적")
    void recordRoute_incrementsCountAndDistance() {
        profile.recordRoute(500.0);
        profile.recordRoute(300.0);

        assertThat(profile.getTotalRoutes()).isEqualTo(2);
        assertThat(profile.getTotalDistanceM()).isCloseTo(800.0, within(0.001));
    }

    @Test
    @DisplayName("recordRoute: 첫 경로 기록 시 총 경로 수 1, 거리 정확히 반영")
    void recordRoute_firstRoute_setsCorrectValues() {
        profile.recordRoute(1234.5);

        assertThat(profile.getTotalRoutes()).isEqualTo(1);
        assertThat(profile.getTotalDistanceM()).isCloseTo(1234.5, within(0.001));
    }
}
