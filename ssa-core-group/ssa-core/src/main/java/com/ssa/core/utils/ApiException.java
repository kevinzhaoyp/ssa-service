package com.ssa.core.utils;

import lombok.Getter;

/**
 * 自定义 API异常
 *
 * <p>迭代 1 扩展（ADR-002/003）：增加数字业务码 code 与稳定业务码 errorCode（如 STATUS_CONFLICT / REF_BLOCKED），
 * 用于 ymlapi 前置校验（ASSERT 事件）失败时向调用方返回 409 + data.errorCode。
 * 原有构造行为不变（默认 code=50000，errorCode=null），向后兼容。</p>
 */
@Getter
public class ApiException extends RuntimeException {

    /** 数字错误码（HTTP 语义，默认 50000 与 ApiResultCode.FAILED 一致） */
    private final int code;

    /** 稳定业务码（如 STATUS_CONFLICT / REF_BLOCKED），可为 null */
    private final String errorCode;

    public ApiException(String message) {
        this(ApiResultCode.FAILED.getCode(), null, message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.code = ApiResultCode.FAILED.getCode();
        this.errorCode = null;
    }

    /**
     * 带业务码的异常（迭代 1 新增）
     *
     * @param code      数字错误码（HTTP 语义，如 409）
     * @param errorCode 稳定业务码（如 STATUS_CONFLICT），可为 null
     * @param message   可展示文案
     */
    public ApiException(int code, String errorCode, String message) {
        super(message);
        this.code = code;
        this.errorCode = errorCode;
    }

}
