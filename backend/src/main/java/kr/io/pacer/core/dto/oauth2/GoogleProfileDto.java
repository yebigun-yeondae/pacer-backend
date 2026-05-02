package kr.io.pacer.core.dto.oauth2;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GoogleProfileDto {

    private String sub;
    private String name;
    private String email;

    @JsonProperty("picture")
    private String profileImageUrl;
}
