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

    @Column(name = "push_notification_agreed", nullable = false)
    private boolean pushNotificationAgreed = false;

    @Builder
    private Agreement(User user, boolean privacyAgreed, boolean pushNotificationAgreed) {
        this.user = user;
        this.privacyAgreed = privacyAgreed;
        this.pushNotificationAgreed = pushNotificationAgreed;
    }

    public static Agreement create(User user, boolean privacyAgreed, boolean pushNotificationAgreed) {
        return Agreement.builder()
                .user(user)
                .privacyAgreed(privacyAgreed)
                .pushNotificationAgreed(pushNotificationAgreed)
                .build();
    }

    public void updateConsent(boolean privacyAgreed, boolean pushNotificationAgreed) {
        this.privacyAgreed = privacyAgreed;
        this.pushNotificationAgreed = pushNotificationAgreed;
    }

    public void updatePushNotificationAgreement(boolean agreed) {
        this.pushNotificationAgreed = agreed;
    }
}
