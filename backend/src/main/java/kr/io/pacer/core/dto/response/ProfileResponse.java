package kr.io.pacer.core.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProfileResponse {
    private String nickname;
    private String email;
    private String profileImageUrl;
    private double avgSpeedMps;
    private int    totalRoutes;
    private double totalDistanceM;
}
