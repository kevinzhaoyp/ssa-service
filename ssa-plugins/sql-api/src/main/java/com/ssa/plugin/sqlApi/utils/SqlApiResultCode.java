package com.ssa.plugin.sqlApi.utils;

import com.ssa.core.utils.RestCode;

public enum SqlApiResultCode implements RestCode {
    ;

    @Override
    public int getCode() {
        return 0;
    }

    @Override
    public String getMsg() {
        return "";
    }

    @Override
    public String getMsg(String msg) {
        return "";
    }
}
