package basakan.fryday.common.exception.auth;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;

public class InvalidProviderTokenException extends BusinessException {

    public InvalidProviderTokenException() {
        super(ErrorCode.PROVIDER_TOKEN_INVALID);
    }

    public InvalidProviderTokenException(String provider) {
        super(ErrorCode.PROVIDER_TOKEN_INVALID,
                String.format("%s 토큰이 유효하지 않습니다.", provider));
    }
}
