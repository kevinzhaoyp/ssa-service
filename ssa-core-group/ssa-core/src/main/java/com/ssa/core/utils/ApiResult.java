package com.ssa.core.utils;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ApiResult<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean ok;
    private int code;
    private String msg;
    private T data;

    public ApiResult(int code, T data, String msg) {
        this.ok = code == 0;
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "{ok=" + this.ok + ",code=" + this.code + ",msg=" + this.msg + ", data=" + this.data + '}';
    }

    public static <T> ApiResult<T> success(T data, String msg) {
        return new ApiResult<>(ApiResultCode.OK.getCode(), data, msg);
    }
    public static <T> ApiResult<T> success(T data) {
        return success(data, ApiResultCode.OK.getMsg());
    }

    // 失败
    public static <T> ApiResult<T> failed(int code, String msg, T data) {
        return new ApiResult<>(code, data, msg);
    }

    public static <T> ApiResult<T> failed(int code, String msg) {
        return failed(code, msg, null);
    }

    public static <T> ApiResult<T> failed(String errMsg) {
        return failed(ApiResultCode.FAILED.getCode(), errMsg);
    }

    public static <T> ApiResult<T> failed() {
        return failed(ApiResultCode.FAILED.getCode(), ApiResultCode.FAILED.getMsg());
    }
    // 枚举
    public static <T> ApiResult<T> failed(RestCode restCode, T data) {
        return failed(restCode.getCode(), restCode.getMsg(), data);
    }

    public static <T> ApiResult<T> failed(RestCode restCode) {
        return failed(restCode, null);
    }

}
