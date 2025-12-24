package basakan.fryday.domain.user;

public enum OnboardingStatus {
    NEEDS_NICKNAME,      // 1단계: 닉네임 설정 필요
    NEEDS_AGREEMENT,     // 2단계: 개인정보 동의 필요
    NEEDS_ONBOARDING,    // 3단계: 온보딩 화면 확인 필요
    COMPLETED;           // 완료 (모든 단계 통과)

    public OnboardingStatus getNextStep() {
        return switch (this) {
            case NEEDS_NICKNAME -> NEEDS_AGREEMENT;
            case NEEDS_AGREEMENT -> NEEDS_ONBOARDING;
            case NEEDS_ONBOARDING -> COMPLETED;
            case COMPLETED -> COMPLETED;
        };
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
