package basakan.fryday.controller.user.request;

import jakarta.validation.constraints.AssertTrue;

public record ConsentRequest(
        @AssertTrue(message = "개인정보 수집 및 이용 동의는 필수입니다.")
        boolean privacyRequired,

        boolean marketingOptional
) {
}
