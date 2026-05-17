package kr.io.pacer.core.domain;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.UUID;

@RedisHash("refresh_token")
@Getter
public class RefreshToken {

    @Id
    private String token;

    private UUID userId;

    @TimeToLive
    private long ttlSeconds;

    public RefreshToken(String token, UUID userId, long ttlSeconds) {
        this.token = token;
        this.userId = userId;
        this.ttlSeconds = ttlSeconds;
    }
}
