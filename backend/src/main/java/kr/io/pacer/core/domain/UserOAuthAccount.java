package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import kr.io.pacer.core.domain.enums.OAuthProvider;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "user_oauth_accounts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"})
)
@Getter
@NoArgsConstructor
public class UserOAuthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuthProvider provider;

    @Column(nullable = false, length = 255)
    private String providerId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public static UserOAuthAccount of(User user, OAuthProvider provider, String providerId) {
        UserOAuthAccount account = new UserOAuthAccount();
        account.user = user;
        account.provider = provider;
        account.providerId = providerId;
        return account;
    }
}
