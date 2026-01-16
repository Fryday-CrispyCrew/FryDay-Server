package basakan.fryday.domain.category;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum CategoryColor {
    OR("OR", "#FF5B22"),
    BR("BR", "#693838"),
    LG("LG", "#82B236"),
    CB("CB", "#3E78AE"),
    DP("DP", "#D0509D"),
    MT("MT", "#3CB492"),
    VL("VL", "#9351A1"),
    PK("PK", "#F06B9C"),
    MB("MB", "#AA7459");

    private final String code; // API 통신용 코드
    private final String hex;  // 프론트엔드 표시용 헥사 코드

    // @JsonValue: 객체를 JSON으로 직렬화할 때 'code' 값만 나가도록 설정
    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static CategoryColor fromCode(String code) {
        return Arrays.stream(CategoryColor.values())
                .filter(v -> v.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 테마 색상입니다: " + code));
    }
}
