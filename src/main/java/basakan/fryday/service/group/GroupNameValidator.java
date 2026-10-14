package basakan.fryday.service.group;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;
import basakan.fryday.domain.group.FryGroup;

/**
 * 그룹 이름 규칙: 양끝 공백을 제거한 뒤 1~10자여야 하고, 특수문자는 허용하되 이모지는 허용하지 않는다.
 */
public final class GroupNameValidator {

    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int VARIATION_SELECTOR_16 = 0xFE0F;

    private GroupNameValidator() {
    }

    public static String validateAndNormalize(String rawName) {
        String name = (rawName == null) ? "" : rawName.trim();

        if (name.isEmpty() || name.length() > FryGroup.MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_GROUP_NAME);
        }
        if (name.codePoints().anyMatch(GroupNameValidator::isEmoji)) {
            throw new BusinessException(ErrorCode.GROUP_NAME_HAS_EMOJI);
        }
        return name;
    }

    private static boolean isEmoji(int codePoint) {
        return codePoint > Character.MAX_VALUE
                || codePoint == ZERO_WIDTH_JOINER
                || codePoint == VARIATION_SELECTOR_16
                || Character.getType(codePoint) == Character.OTHER_SYMBOL;
    }
}
