package basakan.fryday.controller.admin;

import basakan.fryday.common.response.ApiResponse;
import basakan.fryday.controller.admin.request.AdminLoginRequest;
import basakan.fryday.controller.admin.response.AdminLoginResponse;
import basakan.fryday.service.admin.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminLoginResponse response = adminAuthService.login(request.getLoginId(), request.getPassword());
        return ApiResponse.success(response, "로그인되었습니다.");
    }
}
