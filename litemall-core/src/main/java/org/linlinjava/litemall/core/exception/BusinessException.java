package org.linlinjava.litemall.core.exception;

/**
 * 可预期的业务失败，按 errno 返回给前端，同时触发事务回滚。
 */
public class BusinessException extends RuntimeException {

    private final int errno;

    public BusinessException(int errno, String message) {
        super(message);
        this.errno = errno;
    }

    public int getErrno() {
        return errno;
    }
}
