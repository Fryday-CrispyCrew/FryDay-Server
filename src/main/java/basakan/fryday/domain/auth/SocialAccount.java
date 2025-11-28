package basakan.fryday.domain.auth;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "social_accounts",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"provider", "providerUserId"})
        })
public class SocialAccount extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(nullable = false)
    private String providerUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    private SocialAccount(AuthProvider provider, String providerUserId, User user) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.user = user;
    }

    public static SocialAccount create(AuthProvider provider, String providerUserId, User user) {
        return SocialAccount.builder()
                .provider(provider)
                .providerUserId(providerUserId)
                .user(user)
                .build();
    }
}
