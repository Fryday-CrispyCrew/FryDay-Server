package basakan.fryday.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum CategoryColor {
    BR("BR", "#5D4037"),
    LG("LG", "#9CCC65"),
    CB("CB", "#5C85AA"),
    DP("DP", "#B05C8E"),
    MT("MT", "#4DB6AC"),
    VL("VL", "#8E67AC"),
    PK("PK", "#F06292"),
    MB("MB", "#A1887F"),
    YL("YL", "#FFCA28");

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
