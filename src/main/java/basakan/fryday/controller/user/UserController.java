package basakan.fryday.controller.user;

import basakan.fryday.common.response.MessageResponse;
import basakan.fryday.controller.user.request.SocialLoginRequest;
import basakan.fryday.controller.user.response.SocialLoginResponse;
import basakan.fryday.controller.user.response.NicknameCheckResponse;
import basakan.fryday.controller.user.response.RefreshTokenResponse;
import basakan.fryday.controller.user.request.RefreshTokenRequest;
import basakan.fryday.controller.user.request.ConsentRequest;
import basakan.fryday.controller.user.request.SetNicknameRequest;
import basakan.fryday.controller.user.request.UpdateNicknameRequest;
import basakan.fryday.controller.user.request.UpdateFcmTokenRequest;
import basakan.fryday.controller.user.request.LogoutRequest;
import basakan.fryday.service.auth.dto.SocialLoginDto;
import basakan.fryday.service.user.UserAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserAppService userAppService;

    @PostMapping("/social/login")
    public ResponseEntity<SocialLoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        SocialLoginDto result = userAppService.socialLogin(request.toServiceDto());
        SocialLoginResponse response = SocialLoginResponse.from(result);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = userAppService.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/nickname")
    public ResponseEntity<MessageResponse> setNickname(@Valid @RequestBody SetNicknameRequest request) {
        userAppService.setNickname(request.nickname());
        return ResponseEntity.ok(new MessageResponse("닉네임이 성공적으로 설정되었습니다."));
    }

    @GetMapping("/nickname/check")
    public ResponseEntity<NicknameCheckResponse> checkNickname(@RequestParam String nickname) {
        NicknameCheckResponse response = userAppService.checkNicknameAvailability(nickname);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/me/consent")
    public ResponseEntity<MessageResponse> agreeConsent(@Valid @RequestBody ConsentRequest request) {
        userAppService.agreeConsent(request.privacyRequired(), request.marketingOptional());
        return ResponseEntity.ok(new MessageResponse("개인정보 수집 및 이용에 동의하였습니다."));
    }

    @PostMapping("/me/onboarding")
    public ResponseEntity<MessageResponse> completeOnboarding() {
        userAppService.completeOnboarding();
        return ResponseEntity.ok(new MessageResponse("온보딩을 완료하였습니다."));
    }

    @DeleteMapping("/me")
    public ResponseEntity<MessageResponse> withdraw() {
        userAppService.withdraw();
        return ResponseEntity.ok(new MessageResponse("회원 탈퇴가 정상적으로 처리되었습니다."));
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<MessageResponse> updateNickname(@Valid @RequestBody UpdateNicknameRequest request) {
        userAppService.updateNickname(request.nickname());
        return ResponseEntity.ok(new MessageResponse("닉네임이 성공적으로 변경되었습니다."));
    }

    @PostMapping("/me/fcm-token")
    public ResponseEntity<MessageResponse> updateFcmToken(@Valid @RequestBody UpdateFcmTokenRequest request) {
        userAppService.updateDeviceFcmToken(request.deviceId(), request.fcmToken());
        return ResponseEntity.ok(new MessageResponse("FCM 토큰이 성공적으로 등록되었습니다."));
    }

    @PostMapping("/me/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
        userAppService.logout(request.deviceId(), request.refreshToken());
        return ResponseEntity.ok(new MessageResponse("로그아웃이 완료되었습니다."));
    }
}
