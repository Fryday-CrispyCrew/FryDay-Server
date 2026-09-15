package basakan.fryday.domain.group;

/**
 * 응답 표현 전용 권한. DB에는 저장하지 않고 {@link FryGroup#getOwnerId()}에서 파생한다.
 */
public enum GroupRole {

    OWNER,
    MEMBER;

    public static GroupRole of(FryGroup group, Long userId) {
        return group.isOwner(userId) ? OWNER : MEMBER;
    }
}
