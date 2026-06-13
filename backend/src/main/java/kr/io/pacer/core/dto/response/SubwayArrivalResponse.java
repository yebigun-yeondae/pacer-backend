package kr.io.pacer.core.dto.response;

import kr.io.pacer.core.dto.external.SubwayArrivalApiResponse.RealtimeArrival;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubwayArrivalResponse {

    private String lineId;
    private String stationNm;
    private String trainLineNm;
    private String currentStation;
    private Integer remainingSeconds;
    private String arrivalMessage;
    private String positionMessage;
    private String arrivalCode;
    private boolean isLastTrain;

    public static SubwayArrivalResponse from(RealtimeArrival arrival) {
        Integer remainingSec = null;
        try {
            if (arrival.getBarvlDt() != null) {
                remainingSec = Integer.parseInt(arrival.getBarvlDt());
            }
        } catch (NumberFormatException ignored) {}

        return SubwayArrivalResponse.builder()
                .lineId(arrival.getSubwayId())
                .stationNm(arrival.getStatnNm())
                .trainLineNm(arrival.getTrainLineNm())
                .currentStation(arrival.getBstatnNm())
                .remainingSeconds(remainingSec)
                .arrivalMessage(arrival.getArvlMsg2())
                .positionMessage(arrival.getArvlMsg3())
                .arrivalCode(arrival.getArvlCd())
                .isLastTrain("1".equals(arrival.getLstcarAt()))
                .build();
    }
}
