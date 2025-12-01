package basakan.fryday.common.exception.auth;

import basakan.fryday.common.ErrorCode;
import basakan.fryday.common.exception.BusinessException;

public class UserWithdrawnException extends BusinessException {

    public UserWithdrawnException() {
        super(ErrorCode.USER_WITHDRAWN);
    }
}
