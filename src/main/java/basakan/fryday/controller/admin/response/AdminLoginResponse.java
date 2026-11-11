package basakan.fryday.controller.admin.response;

import lombok.Getter;

@Getter
public class AdminLoginResponse {

    private final String accessToken;
    private final long expiresIn;
    private final String name;

    public AdminLoginResponse(String accessToken, long expiresIn, String name) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.name = name;
    }
}
