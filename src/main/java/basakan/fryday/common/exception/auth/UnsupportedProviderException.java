package basakan.fryday.common.exception.auth;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;

public class UnsupportedProviderException extends BusinessException {

    public UnsupportedProviderException() {
        super(ErrorCode.UNSUPPORTED_PROVIDER);
    }

    public UnsupportedProviderException(String provider) {
        super(ErrorCode.UNSUPPORTED_PROVIDER,
                String.format("지원하지 않는 Provider입니다: %s", provider));
    }
}
