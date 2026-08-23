package com.ssa.core.utils;

/**
 * SSA Api返回Code 接口
 */
public interface RestCode {
    int getCode();

    String getMsg();

    String getMsg(String msg);
}
