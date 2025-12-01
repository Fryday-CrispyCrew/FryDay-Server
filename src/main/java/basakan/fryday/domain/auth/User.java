package basakan.fryday.domain.auth;

import basakan.fryday.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User extends BaseEntity {

    private String email;

    private String name;

    private LocalDate birthDate;

    @Column(nullable = false)
    private boolean consentAgreed = false;

    @Column(nullable = false)
    private boolean onboardingCompleted = false;

    @Column(nullable = false)
    private boolean nicknameSet = false;

    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Builder
    private User(String email, String name, LocalDate birthDate,
                 boolean consentAgreed, boolean onboardingCompleted, boolean nicknameSet,
                 String nickname, Status status, Role role) {
        this.email = email;
        this.name = name;
        this.birthDate = birthDate;
        this.consentAgreed = consentAgreed;
        this.onboardingCompleted = onboardingCompleted;
        this.nicknameSet = nicknameSet;
        this.nickname = nickname;
        this.status = status != null ? status : Status.ACTIVE;
        this.role = role != null ? role : Role.USER;
    }

    public static User createNewUser() {
        return User.builder()
                .consentAgreed(false)
                .onboardingCompleted(false)
                .nicknameSet(false)
                .status(Status.ACTIVE)
                .role(Role.USER)
                .build();
    }

    public LoginStatus calculateLoginStatus() {
        if (!consentAgreed) {
            return LoginStatus.NEEDS_CONSENT;
        }
        if (!onboardingCompleted) {
            return LoginStatus.NEEDS_ONBOARDING;
        }
        if (!nicknameSet) {
            return LoginStatus.NEEDS_NICKNAME;
        }
        return LoginStatus.COMPLETED;
    }

    public void agreeConsent() {
        this.consentAgreed = true;
    }

    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameSet = true;
    }

    public void withdraw() {
        this.status = Status.WITHDRAWN;
    }

    public void block() {
        this.status = Status.BLOCKED;
    }

    public boolean isActive() {
        return this.status == Status.ACTIVE;
    }

    public enum Status {
        ACTIVE,
        WITHDRAWN,
        BLOCKED
    }

    public enum Role {
        USER,
        ADMIN
    }
}
