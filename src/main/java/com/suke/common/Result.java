package com.suke.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 自然醒
 * @version 1.0
 */
@Data
public class Result<T> implements Serializable {
    private Integer code; //状态码 200成功 401失败
    private String message; //返回信息
    private T data; //返回数据

    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.code = 200;
        return result;
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.code = ErrorCode.SUCCESS.getCode();
        result.data = data;
        return result;
    }

    public static <T> Result<T> error(String message) {
        Result result = new Result();
        result.code = ErrorCode.FORBIDDEN_ERROR.getCode();
        result.message = message;
        return result;
    }


}
