package io.nop.sys.dao;

public class NopSysDaoException extends RuntimeException {
    public NopSysDaoException(String message) {
        super(message);
    }

    public NopSysDaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
