package kr.io.pacer.core.domain;

import jakarta.persistence.*;
import kr.io.pacer.core.domain.enums.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nickname;

    @Column(unique = true)
    private String email;

    private String profileImageUrl;

    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void markDeleted() {
        this.deletedAt = LocalDateTime.now();
    }

    public void update(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public static User of(String nickname, String email, String profileImageUrl) {
        User user = new User();
        user.nickname = nickname;
        user.email = email;
        user.profileImageUrl = profileImageUrl;
        return user;
    }

    public static User ofLocal(String nickname, String email, String encodedPassword) {
        User user = new User();
        user.nickname = nickname;
        user.email = email;
        user.password = encodedPassword;
        return user;
    }
}
