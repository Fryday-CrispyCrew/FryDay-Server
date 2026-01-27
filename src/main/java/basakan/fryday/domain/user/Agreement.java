package basakan.fryday.domain.user;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "agreements")
public class Agreement extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private boolean privacyAgreed = false;

    @Column(name = "marketing_agreed", nullable = false)
    private boolean marketingAgreed = false;

    @Builder
    private Agreement(User user, boolean privacyAgreed, boolean marketingAgreed) {
        this.user = user;
        this.privacyAgreed = privacyAgreed;
        this.marketingAgreed = marketingAgreed;
    }

    public static Agreement create(User user, boolean privacyAgreed, boolean marketingAgreed) {
        return Agreement.builder()
                .user(user)
                .privacyAgreed(privacyAgreed)
                .marketingAgreed(marketingAgreed)
                .build();
    }

    public void updateConsent(boolean privacyAgreed, boolean marketingAgreed) {
        this.privacyAgreed = privacyAgreed;
        this.marketingAgreed = marketingAgreed;
    }

    public void updateMarketingAgreement(boolean agreed) {
        this.marketingAgreed = agreed;
    }
}
