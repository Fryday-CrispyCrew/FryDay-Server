package basakan.fryday.common.exception.auth;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;

public class UserReregisterNotAllowedException extends BusinessException {

    public UserReregisterNotAllowedException() {
        super(ErrorCode.USER_REREGISTER_NOT_ALLOWED);
    }
}
