package basakan.fryday.domain.user;

public enum OnboardingStatus {
    NEEDS_AGREEMENT,     // 1단계: 개인정보 동의 필요
    NEEDS_NICKNAME,      // 2단계: 닉네임 설정 필요
    NEEDS_ONBOARDING,    // 3단계: 온보딩 화면 확인 필요
    NEEDS_MARKETING,     // 4단계: 마케팅 수신 동의 필요
    COMPLETED;           // 완료 (모든 단계 통과)

    public OnboardingStatus getNextStep() {
        return switch (this) {
            case NEEDS_AGREEMENT -> NEEDS_NICKNAME;
            case NEEDS_NICKNAME -> NEEDS_ONBOARDING;
            case NEEDS_ONBOARDING -> NEEDS_MARKETING;
            case NEEDS_MARKETING -> COMPLETED;
            case COMPLETED -> COMPLETED;
        };
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }
}
