package kr.io.pacer.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.util.UUID;

@RedisHash(value = "refresh_token", timeToLive = 1209600) // 14일
@Getter
@AllArgsConstructor
public class RefreshToken {

    @Id
    private String token;   // refresh token 값이 key

    private UUID userId;
}
