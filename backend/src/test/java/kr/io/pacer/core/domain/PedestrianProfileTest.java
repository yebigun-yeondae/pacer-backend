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
    @DisplayName("기본 생성 시 초기값 확인")
    void createDefault_hasCorrectInitialValues() {
        assertThat(profile.getAvgSpeedMps()).isEqualTo(1.4);
        assertThat(profile.getSpeedStd()).isEqualTo(0.2);
        assertThat(profile.getTripCount()).isZero();
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
    @DisplayName("updateFromAi - updated:true 시 세 필드 모두 갱신")
    void updateFromAi_updatesAllThreeFields() {
        profile.updateFromAi(1.6, 0.15, 6);

        assertThat(profile.getAvgSpeedMps()).isCloseTo(1.6, within(0.0001));
        assertThat(profile.getSpeedStd()).isCloseTo(0.15, within(0.0001));
        assertThat(profile.getTripCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("incrementTripCount - updated:false 시 tripCount만 증가")
    void incrementTripCount_onlyUpdatesTripCount() {
        profile.updateFromAi(1.6, 0.15, 4);
        profile.incrementTripCount(5);

        assertThat(profile.getTripCount()).isEqualTo(5);
        assertThat(profile.getAvgSpeedMps()).isCloseTo(1.6, within(0.0001));
        assertThat(profile.getSpeedStd()).isCloseTo(0.15, within(0.0001));
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
