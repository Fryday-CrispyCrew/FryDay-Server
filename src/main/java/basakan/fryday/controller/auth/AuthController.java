package basakan.fryday.controller.auth;

import basakan.fryday.controller.auth.request.SocialLoginRequest;
import basakan.fryday.controller.auth.response.SocialLoginResponse;
import basakan.fryday.service.auth.AuthAppService;
import basakan.fryday.service.auth.dto.SocialLoginDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/social")
@RequiredArgsConstructor
public class AuthController {

    private final AuthAppService authAppService;

    @PostMapping("/login")
    public ResponseEntity<SocialLoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request) {
        SocialLoginDto result = authAppService.socialLogin(request.toServiceDto());
        SocialLoginResponse response = SocialLoginResponse.from(result);
        return ResponseEntity.ok(response);
    }
}
