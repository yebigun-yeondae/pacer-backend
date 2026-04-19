package kr.io.pacer.core.repository;

import kr.io.pacer.core.domain.UserOAuthAccount;
import kr.io.pacer.core.domain.enums.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOAuthAccountRepository extends JpaRepository<UserOAuthAccount, UUID> {

    Optional<UserOAuthAccount> findByProviderAndProviderId(OAuthProvider provider, String providerId);
}
