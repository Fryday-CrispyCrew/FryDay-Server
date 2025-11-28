package basakan.fryday.common.exception.auth;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;

public class UserBlockedException extends BusinessException {

    public UserBlockedException() {
        super(ErrorCode.USER_BLOCKED);
    }
}
