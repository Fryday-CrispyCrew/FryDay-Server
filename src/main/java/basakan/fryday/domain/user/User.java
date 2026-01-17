package basakan.fryday.domain.user;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(nullable = false)
    private String providerUserId;

    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnboardingStatus onboardingStatus = OnboardingStatus.NEEDS_NICKNAME;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    private LocalDateTime withdrawnAt;

    @Builder
    private User(AuthProvider provider, String providerUserId, String nickname,
                 OnboardingStatus onboardingStatus, AccountStatus accountStatus, Role role) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.nickname = nickname;
        this.onboardingStatus = onboardingStatus != null ? onboardingStatus : OnboardingStatus.NEEDS_NICKNAME;
        this.accountStatus = accountStatus != null ? accountStatus : AccountStatus.ACTIVE;
        this.role = role != null ? role : Role.USER;
    }

    public static User createNewUser(AuthProvider provider, String providerUserId) {
        return User.builder()
                .provider(provider)
                .providerUserId(providerUserId)
                .onboardingStatus(OnboardingStatus.NEEDS_NICKNAME)
                .accountStatus(AccountStatus.ACTIVE)
                .role(Role.USER)
                .build();
    }

    public void completeAgreementStep() {
        this.onboardingStatus = OnboardingStatus.NEEDS_ONBOARDING;
    }

    public void completeOnboardingStep() {
        this.onboardingStatus = OnboardingStatus.COMPLETED;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.onboardingStatus = OnboardingStatus.NEEDS_AGREEMENT;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        // onboardingStatus는 변경하지 않음 (이미 COMPLETED 상태)
    }

    public void withdraw() {
        this.accountStatus = AccountStatus.WITHDRAWN;
        this.withdrawnAt = java.time.LocalDateTime.now();
    }

    public boolean canReregister() {
        if (this.withdrawnAt == null) {
            return true;
        }
        return java.time.LocalDateTime.now().isAfter(this.withdrawnAt.plusDays(7));
    }

    public void block() {
        this.accountStatus = AccountStatus.BLOCKED;
    }

    public boolean isActive() {
        return this.accountStatus == AccountStatus.ACTIVE;
    }

    public enum AccountStatus {
        ACTIVE,
        WITHDRAWN,
        BLOCKED
    }

    public enum Role {
        USER,
        ADMIN
    }
}
