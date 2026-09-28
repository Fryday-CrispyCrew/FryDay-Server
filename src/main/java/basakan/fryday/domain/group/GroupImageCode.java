package basakan.fryday.domain.group;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 그룹 그래픽 코드. 앱이 이 코드로 그래픽 파일을 찾으므로 약속된 값만 내려준다.
 */
public final class GroupImageCode {

    private static final List<String> CODES = List.of("01", "02", "03");

    private GroupImageCode() {
    }

    public static String random() {
        return CODES.get(ThreadLocalRandom.current().nextInt(CODES.size()));
    }
}
