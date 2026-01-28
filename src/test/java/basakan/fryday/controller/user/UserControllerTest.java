package basakan.fryday.controller.user;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.config.SecurityConfig;
import basakan.fryday.common.security.JwtAuthenticationFilter;
import basakan.fryday.common.security.JwtTokenProvider;
import basakan.fryday.controller.user.request.*;
import basakan.fryday.controller.user.response.NicknameCheckResponse;
import basakan.fryday.controller.user.response.NotificationSettingsResponse;
import basakan.fryday.domain.user.AuthProvider;
import basakan.fryday.domain.user.OnboardingStatus;
import basakan.fryday.domain.user.User;
import basakan.fryday.service.auth.dto.SocialLoginDto;
import basakan.fryday.service.user.UserAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.restdocs.request.RequestDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class UserControllerTest extends RestDocsSupport {

    @MockitoBean
    private UserAppService userAppService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("소셜 로그인 API (카카오, 네이버) - 신규 사용자")
    void socialLogin_NewUser() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.KAKAO,
                "kakao-access-token-123",
                "device-id-123",
                "iOS",
                "iPhone 14 Pro",
                "fcm-token-abc"
        );

        SocialLoginDto mockDto = SocialLoginDto.builder()
                .userId(1L)
                .provider(AuthProvider.KAKAO)
                .onboardingStatus(OnboardingStatus.NEEDS_AGREEMENT)
                .role(User.Role.USER)
                .nickname(null)
                .accessToken("jwt-access-token")
                .refreshToken("jwt-refresh-token")
                .deviceId("device-id-123")
                .build();

        given(userAppService.socialLogin(any()))
                .willReturn(mockDto);

        // when & then
        mockMvc.perform(post("/api/users/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("NEEDS_AGREEMENT"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andDo(document("user-social-login",
                        requestFields(
                                fieldWithPath("provider").type(JsonFieldType.STRING).description("소셜 로그인 제공자 (KAKAO, NAVER)"),
                                fieldWithPath("accessToken").type(JsonFieldType.STRING).description("소셜 제공자로부터 받은 Access Token"),
                                fieldWithPath("deviceId").type(JsonFieldType.STRING).description("디바이스 고유 ID"),
                                fieldWithPath("deviceType").type(JsonFieldType.STRING).description("디바이스 타입 (iOS, Android, Web)").optional(),
                                fieldWithPath("deviceName").type(JsonFieldType.STRING).description("디바이스 이름").optional(),
                                fieldWithPath("fcmToken").type(JsonFieldType.STRING).description("FCM 푸시 토큰").optional()
                        ),
                        responseFields(
                                fieldWithPath("onboardingStatus").type(JsonFieldType.STRING).description("온보딩 상태 (NEEDS_AGREEMENT, NEEDS_NICKNAME, NEEDS_ONBOARDING, NEEDS_MARKETING, COMPLETED)"),
                                fieldWithPath("accessToken").type(JsonFieldType.STRING).description("JWT Access Token"),
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("JWT Refresh Token"),
                                fieldWithPath("deviceId").type(JsonFieldType.STRING).description("디바이스 ID"),
                                fieldWithPath("user.id").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                fieldWithPath("user.provider").type(JsonFieldType.STRING).description("소셜 로그인 제공자"),
                                fieldWithPath("user.role").type(JsonFieldType.STRING).description("사용자 권한 (USER, ADMIN)"),
                                fieldWithPath("user.nickname").type(JsonFieldType.STRING).description("사용자 닉네임").optional()
                        )
                ));
    }

    @Test
    @DisplayName("애플 로그인 API - 신규 사용자")
    void appleLogin_NewUser() throws Exception {
        // given
        AppleLoginRequest request = new AppleLoginRequest(
                "eyJraWQiOiJXNldjT0tC...",  // Apple ID Token
                "c1234567890abc",            // Authorization Code (optional)
                "device-id-123",
                "iOS",
                "iPhone 14 Pro",
                "fcm-token-abc"
        );

        SocialLoginDto mockDto = SocialLoginDto.builder()
                .userId(1L)
                .provider(AuthProvider.APPLE)
                .onboardingStatus(OnboardingStatus.NEEDS_AGREEMENT)
                .role(User.Role.USER)
                .nickname(null)
                .accessToken("jwt-access-token")
                .refreshToken("jwt-refresh-token")
                .deviceId("device-id-123")
                .build();

        given(userAppService.socialLogin(any()))
                .willReturn(mockDto);

        // when & then
        mockMvc.perform(post("/api/users/apple/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("NEEDS_AGREEMENT"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andDo(document("user-apple-login",
                        requestFields(
                                fieldWithPath("idToken").type(JsonFieldType.STRING).description("Apple ID Token (JWT)"),
                                fieldWithPath("authorizationCode").type(JsonFieldType.STRING).description("Apple Authorization Code (선택)").optional(),
                                fieldWithPath("deviceId").type(JsonFieldType.STRING).description("디바이스 고유 ID"),
                                fieldWithPath("deviceType").type(JsonFieldType.STRING).description("디바이스 타입 (iOS, Android, Web)").optional(),
                                fieldWithPath("deviceName").type(JsonFieldType.STRING).description("디바이스 이름").optional(),
                                fieldWithPath("fcmToken").type(JsonFieldType.STRING).description("FCM 푸시 토큰").optional()
                        ),
                        responseFields(
                                fieldWithPath("onboardingStatus").type(JsonFieldType.STRING).description("온보딩 상태 (NEEDS_AGREEMENT, NEEDS_NICKNAME, NEEDS_ONBOARDING, NEEDS_MARKETING, COMPLETED)"),
                                fieldWithPath("accessToken").type(JsonFieldType.STRING).description("JWT Access Token"),
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING).description("JWT Refresh Token"),
                                fieldWithPath("deviceId").type(JsonFieldType.STRING).description("디바이스 ID"),
                                fieldWithPath("user.id").type(JsonFieldType.NUMBER).description("사용자 ID"),
                                fieldWithPath("user.provider").type(JsonFieldType.STRING).description("소셜 로그인 제공자"),
                                fieldWithPath("user.role").type(JsonFieldType.STRING).description("사용자 권한 (USER, ADMIN)"),
                                fieldWithPath("user.nickname").type(JsonFieldType.STRING).description("사용자 닉네임").optional()
                        )
                ));
    }

    @Test
    @DisplayName("개인정보 동의 완료 API")
    @WithMockUser
    void agreeConsent() throws Exception {
        // given
        ConsentRequest request = new ConsentRequest(true);

        doNothing().when(userAppService).agreeConsent(anyBoolean());

        // when & then
        mockMvc.perform(post("/api/users/me/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("개인정보 수집 및 이용에 동의하였습니다."))
                .andDo(document("user-consent",
                        requestFields(
                                fieldWithPath("privacyRequired").type(JsonFieldType.BOOLEAN).description("개인정보 수집 및 이용 동의 (필수)")
                        ),
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("개인정보 동의 API - 필수 동의 미체크 시 실패")
    @WithMockUser
    void agreeConsent_FailWhenPrivacyNotAgreed() throws Exception {
        // given
        ConsentRequest request = new ConsentRequest(false);

        // when & then
        mockMvc.perform(post("/api/users/me/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("온보딩 완료 API")
    @WithMockUser
    void completeOnboarding() throws Exception {
        // given
        doNothing().when(userAppService).completeOnboarding();

        // when & then
        mockMvc.perform(post("/api/users/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("온보딩을 완료하였습니다."))
                .andDo(document("user-onboarding",
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("닉네임 중복 체크 API - 사용 가능")
    void checkNickname_Available() throws Exception {
        // given
        given(userAppService.checkNicknameAvailability("프라이데이"))
                .willReturn(NicknameCheckResponse.available());

        // when & then
        mockMvc.perform(get("/api/users/nickname/check")
                        .param("nickname", "프라이데이"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.message").value("사용 가능한 닉네임입니다."))
                .andDo(document("user-nickname-check-available",
                        queryParameters(
                                parameterWithName("nickname").description("확인할 닉네임")
                        ),
                        responseFields(
                                fieldWithPath("available").type(JsonFieldType.BOOLEAN).description("사용 가능 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("닉네임 중복 체크 API - 이미 사용 중")
    void checkNickname_Unavailable() throws Exception {
        // given
        given(userAppService.checkNicknameAvailability("이미사용중"))
                .willReturn(NicknameCheckResponse.unavailable());

        // when & then
        mockMvc.perform(get("/api/users/nickname/check")
                        .param("nickname", "이미사용중"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 닉네임입니다."))
                .andDo(document("user-nickname-check-unavailable",
                        queryParameters(
                                parameterWithName("nickname").description("확인할 닉네임")
                        ),
                        responseFields(
                                fieldWithPath("available").type(JsonFieldType.BOOLEAN).description("사용 가능 여부"),
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("닉네임 설정 API (최초)")
    @WithMockUser
    void setNickname() throws Exception {
        // given
        SetNicknameRequest request = new SetNicknameRequest("프라이데이");

        doNothing().when(userAppService).setNickname(anyString());

        // when & then
        mockMvc.perform(post("/api/users/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("닉네임이 성공적으로 설정되었습니다."))
                .andDo(document("user-nickname-set",
                        requestFields(
                                fieldWithPath("nickname").type(JsonFieldType.STRING).description("설정할 닉네임 (2-10자)")
                        ),
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("계정 탈퇴 API")
    @WithMockUser
    void withdraw() throws Exception {
        // given
        doNothing().when(userAppService).withdraw();

        // when & then
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("회원 탈퇴가 정상적으로 처리되었습니다."))
                .andDo(document("user-withdraw",
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("닉네임 수정 API")
    @WithMockUser
    void updateNickname() throws Exception {
        // given
        UpdateNicknameRequest request = new UpdateNicknameRequest("새닉네임");

        doNothing().when(userAppService).updateNickname(anyString());

        // when & then
        mockMvc.perform(patch("/api/users/me/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("닉네임이 성공적으로 변경되었습니다."))
                .andDo(document("user-nickname-update",
                        requestFields(
                                fieldWithPath("nickname").type(JsonFieldType.STRING).description("변경할 닉네임 (2-10자)")
                        ),
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("알림 설정 조회 API")
    @WithMockUser
    void getNotificationSettings() throws Exception {
        // given
        NotificationSettingsResponse mockResponse = NotificationSettingsResponse.of(true, false);

        given(userAppService.getNotificationSettings())
                .willReturn(mockResponse);

        // when & then
        mockMvc.perform(get("/api/users/me/notification-settings"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushNotificationEnabled").value(true))
                .andExpect(jsonPath("$.marketingAgreed").value(false))
                .andDo(document("user-notification-settings-get",
                        responseFields(
                                fieldWithPath("pushNotificationEnabled").type(JsonFieldType.BOOLEAN).description("현재 디바이스의 푸시 알림 수신 여부"),
                                fieldWithPath("marketingAgreed").type(JsonFieldType.BOOLEAN).description("마케팅 정보 수신 동의 여부")
                        )
                ));
    }

    @Test
    @DisplayName("알림 설정 변경 API")
    @WithMockUser
    void updateNotificationSettings() throws Exception {
        // given
        NotificationSettingsRequest request = new NotificationSettingsRequest(true);

        doNothing().when(userAppService).updateNotificationSettings(anyBoolean());

        // when & then
        mockMvc.perform(patch("/api/users/me/notification-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("알림 설정이 성공적으로 변경되었습니다."))
                .andDo(document("user-notification-settings-update",
                        requestFields(
                                fieldWithPath("pushNotificationEnabled").type(JsonFieldType.BOOLEAN).description("현재 디바이스의 푸시 알림 수신 여부 (true: 수신, false: 거부)")
                        ),
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }

    @Test
    @DisplayName("마케팅 수신 동의 API")
    @WithMockUser
    void agreeMarketing() throws Exception {
        // given
        MarketingConsentRequest request = new MarketingConsentRequest(true);

        doNothing().when(userAppService).agreeMarketing(anyBoolean());

        // when & then
        mockMvc.perform(patch("/api/users/me/marketing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("마케팅 수신 정보가 성공적으로 변경되었습니다."))
                .andDo(document("user-marketing-consent",
                        requestFields(
                                fieldWithPath("marketingOptional").type(JsonFieldType.BOOLEAN).description("마케팅 정보 수신 동의 (선택)")
                        ),
                        responseFields(
                                fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지")
                        )
                ));
    }
}
