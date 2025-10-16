package com.suke.exception;

/**
 * @author 自然醒
 * @version 1.0
 */
//业务异常
public class BaseException extends RuntimeException {
    public BaseException() {

    }

    public BaseException(String message) {
        super(message);
    }
}
