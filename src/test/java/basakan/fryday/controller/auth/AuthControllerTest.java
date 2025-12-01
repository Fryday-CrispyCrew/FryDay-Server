package basakan.fryday.controller.auth;

import basakan.fryday.RestDocsSupport;
import basakan.fryday.common.exception.auth.InvalidProviderTokenException;
import basakan.fryday.common.exception.auth.UnsupportedProviderException;
import basakan.fryday.common.exception.auth.UserBlockedException;
import basakan.fryday.common.exception.auth.UserWithdrawnException;
import basakan.fryday.controller.auth.request.SocialLoginRequest;
import basakan.fryday.domain.auth.AuthProvider;
import basakan.fryday.domain.auth.LoginStatus;
import basakan.fryday.domain.auth.User;
import basakan.fryday.service.auth.AuthAppService;
import basakan.fryday.service.auth.dto.SocialLoginDto;
import basakan.fryday.service.auth.dto.SocialLoginServiceDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest extends RestDocsSupport {

    @MockitoBean
    private AuthAppService authAppService;

    @Test
    @DisplayName("소셜 로그인 API - 최초 로그인 (NEEDS_CONSENT)")
    void socialLogin_NewUser() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.KAKAO,
                "mock-access-token",
                null
        );

        SocialLoginDto mockResult = SocialLoginDto.builder()
                .userId(1L)
                .provider(AuthProvider.KAKAO)
                .role(User.Role.USER)
                .nickname(null)
                .consentAgreed(false)
                .onboardingCompleted(false)
                .nicknameSet(false)
                .loginStatus(LoginStatus.NEEDS_CONSENT)
                .accessToken("jwt-access-token")
                .refreshToken("uuid-refresh-token")
                .build();

        given(authAppService.socialLogin(any(SocialLoginServiceDto.class)))
                .willReturn(mockResult);

        // when & then
        mockMvc.perform(post("/api/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginStatus").value("NEEDS_CONSENT"))
                .andExpect(jsonPath("$.accessToken").value("jwt-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("uuid-refresh-token"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.provider").value("KAKAO"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.consentAgreed").value(false))
                .andExpect(jsonPath("$.user.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.user.nicknameSet").value(false))
                .andDo(document("auth/social-login-new-user",
                        requestFields(
                                fieldWithPath("provider").type(JsonFieldType.STRING)
                                        .description("소셜 Provider (KAKAO, NAVER, APPLE)"),
                                fieldWithPath("accessToken").type(JsonFieldType.STRING)
                                        .description("소셜 Provider로부터 발급받은 Access Token"),
                                fieldWithPath("idToken").type(JsonFieldType.STRING).optional()
                                        .description("Apple의 경우 ID Token (선택)")
                        ),
                        responseFields(
                                fieldWithPath("loginStatus").type(JsonFieldType.STRING)
                                        .description("로그인 상태 (NEEDS_CONSENT, NEEDS_ONBOARDING, NEEDS_NICKNAME, COMPLETED)"),
                                fieldWithPath("accessToken").type(JsonFieldType.STRING)
                                        .description("JWT Access Token"),
                                fieldWithPath("refreshToken").type(JsonFieldType.STRING)
                                        .description("Refresh Token (UUID)"),
                                fieldWithPath("user").type(JsonFieldType.OBJECT)
                                        .description("유저 정보"),
                                fieldWithPath("user.id").type(JsonFieldType.NUMBER)
                                        .description("유저 ID"),
                                fieldWithPath("user.provider").type(JsonFieldType.STRING)
                                        .description("소셜 Provider"),
                                fieldWithPath("user.role").type(JsonFieldType.STRING)
                                        .description("유저 권한 (USER, ADMIN)"),
                                fieldWithPath("user.nickname").type(JsonFieldType.STRING).optional()
                                        .description("닉네임 (설정 전에는 null)"),
                                fieldWithPath("user.consentAgreed").type(JsonFieldType.BOOLEAN)
                                        .description("개인정보 수집 동의 여부"),
                                fieldWithPath("user.onboardingCompleted").type(JsonFieldType.BOOLEAN)
                                        .description("온보딩 완료 여부"),
                                fieldWithPath("user.nicknameSet").type(JsonFieldType.BOOLEAN)
                                        .description("닉네임 설정 여부")
                        )
                ));
    }

    @Test
    @DisplayName("소셜 로그인 API - 기존 회원 (COMPLETED)")
    void socialLogin_ExistingUser_Completed() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.KAKAO,
                "mock-access-token",
                null
        );

        SocialLoginDto mockResult = SocialLoginDto.builder()
                .userId(1L)
                .provider(AuthProvider.KAKAO)
                .role(User.Role.USER)
                .nickname("영오")
                .consentAgreed(true)
                .onboardingCompleted(true)
                .nicknameSet(true)
                .loginStatus(LoginStatus.COMPLETED)
                .accessToken("jwt-access-token")
                .refreshToken("uuid-refresh-token")
                .build();

        given(authAppService.socialLogin(any(SocialLoginServiceDto.class)))
                .willReturn(mockResult);

        // when & then
        mockMvc.perform(post("/api/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.accessToken").value("jwt-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("uuid-refresh-token"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.nickname").value("영오"))
                .andExpect(jsonPath("$.user.consentAgreed").value(true))
                .andExpect(jsonPath("$.user.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.user.nicknameSet").value(true))
                .andDo(document("auth/social-login-existing-user"));
    }

    @Test
    @DisplayName("소셜 로그인 API - Naver Provider")
    void socialLogin_NaverProvider() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.NAVER,
                "naver-access-token",
                null
        );

        SocialLoginDto mockResult = SocialLoginDto.builder()
                .userId(2L)
                .provider(AuthProvider.NAVER)
                .role(User.Role.USER)
                .nickname(null)
                .consentAgreed(false)
                .onboardingCompleted(false)
                .nicknameSet(false)
                .loginStatus(LoginStatus.NEEDS_CONSENT)
                .accessToken("jwt-access-token")
                .refreshToken("uuid-refresh-token")
                .build();

        given(authAppService.socialLogin(any(SocialLoginServiceDto.class)))
                .willReturn(mockResult);

        // when & then
        mockMvc.perform(post("/api/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.provider").value("NAVER"))
                .andDo(document("auth/social-login-naver"));
    }

    @Test
    @DisplayName("소셜 로그인 API - Apple Provider")
    void socialLogin_AppleProvider() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.APPLE,
                "apple-access-token",
                "apple-id-token"
        );

        SocialLoginDto mockResult = SocialLoginDto.builder()
                .userId(3L)
                .provider(AuthProvider.APPLE)
                .role(User.Role.USER)
                .nickname(null)
                .consentAgreed(false)
                .onboardingCompleted(false)
                .nicknameSet(false)
                .loginStatus(LoginStatus.NEEDS_CONSENT)
                .accessToken("jwt-access-token")
                .refreshToken("uuid-refresh-token")
                .build();

        given(authAppService.socialLogin(any(SocialLoginServiceDto.class)))
                .willReturn(mockResult);

        // when & then
        mockMvc.perform(post("/api/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.provider").value("APPLE"))
                .andDo(document("auth/social-login-apple"));
    }

    @Test
    @DisplayName("소셜 로그인 API - 잘못된 토큰 (401 Unauthorized)")
    void socialLogin_InvalidToken() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.KAKAO,
                "invalid-token",
                null
        );

        given(authAppService.socialLogin(any(SocialLoginServiceDto.class)))
                .willThrow(new InvalidProviderTokenException());

        // when & then
        mockMvc.perform(post("/api/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("소셜 로그인 토큰이 유효하지 않습니다."))
                .andDo(document("auth/social-login-invalid-token",
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN)
                                        .description("실패"),
                                fieldWithPath("message").type(JsonFieldType.STRING)
                                        .description("에러 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING)
                                        .description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("소셜 로그인 API - 지원하지 않는 Provider (400 Bad Request)")
    void socialLogin_UnsupportedProvider() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.KAKAO,
                "mock-access-token",
                null
        );

        given(authAppService.socialLogin(any(SocialLoginServiceDto.class)))
                .willThrow(new UnsupportedProviderException("GOOGLE"));

        // when & then
        mockMvc.perform(post("/api/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("지원하지 않는 소셜 로그인 Provider입니다. (KAKAO, NAVER, APPLE만 지원)"))
                .andDo(document("auth/social-login-unsupported-provider",
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN)
                                        .description("실패"),
                                fieldWithPath("message").type(JsonFieldType.STRING)
                                        .description("에러 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING)
                                        .description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("소셜 로그인 API - 차단된 사용자 (403 Forbidden)")
    void socialLogin_BlockedUser() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.KAKAO,
                "mock-access-token",
                null
        );

        given(authAppService.socialLogin(any(SocialLoginServiceDto.class)))
                .willThrow(new UserBlockedException());

        // when & then
        mockMvc.perform(post("/api/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("차단된 사용자입니다. 관리자에게 문의하세요."))
                .andDo(document("auth/social-login-blocked-user",
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN)
                                        .description("실패"),
                                fieldWithPath("message").type(JsonFieldType.STRING)
                                        .description("에러 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING)
                                        .description("응답 시간")
                        )
                ));
    }

    @Test
    @DisplayName("소셜 로그인 API - 탈퇴한 사용자 (403 Forbidden)")
    void socialLogin_WithdrawnUser() throws Exception {
        // given
        SocialLoginRequest request = new SocialLoginRequest(
                AuthProvider.KAKAO,
                "mock-access-token",
                null
        );

        given(authAppService.socialLogin(any(SocialLoginServiceDto.class)))
                .willThrow(new UserWithdrawnException());

        // when & then
        mockMvc.perform(post("/api/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("탈퇴한 사용자입니다."))
                .andDo(document("auth/social-login-withdrawn-user",
                        responseFields(
                                fieldWithPath("success").type(JsonFieldType.BOOLEAN)
                                        .description("실패"),
                                fieldWithPath("message").type(JsonFieldType.STRING)
                                        .description("에러 메시지"),
                                fieldWithPath("timestamp").type(JsonFieldType.STRING)
                                        .description("응답 시간")
                        )
                ));
    }
}
