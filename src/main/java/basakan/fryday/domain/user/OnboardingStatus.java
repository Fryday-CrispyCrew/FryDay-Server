package basakan.fryday.domain.user;

public enum OnboardingStatus {
    NEEDS_AGREEMENT,     // 1단계: 약관 동의 필요
    NEEDS_NICKNAME,      // 2단계: 닉네임 설정 필요
    NEEDS_ONBOARDING,    // 3단계: 온보딩 화면 확인 필요
    COMPLETED;           // 완료

    public OnboardingStatus getNextStep() {
        return switch (this) {
            case NEEDS_AGREEMENT -> NEEDS_NICKNAME;
            case NEEDS_NICKNAME -> NEEDS_ONBOARDING;
            case NEEDS_ONBOARDING -> COMPLETED;
            case COMPLETED -> COMPLETED;
        };
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
