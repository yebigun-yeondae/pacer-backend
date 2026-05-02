package kr.io.pacer.core.repository.redis;

import kr.io.pacer.core.domain.RefreshToken;
import org.springframework.data.repository.CrudRepository;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {
}
