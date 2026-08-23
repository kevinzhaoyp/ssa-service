package com.ssa.core.utils;

public enum ApiResultCode implements RestCode {
    OK(0, "success"),
    FAILED(50000, "failed"),

    NOT_FOUND(201, "接口未实现"),
    ERROR_RETURN_TYPE(202, "ReturnType 反回传类型未实现，查看接口是否正确"),
    ERROR_SQL(203, "Sql 语句执行错误"),
    NOT_DBNAME(204, "需要指定数据库名：dbname"),//前端没有指定 dbname 执行存储过程时需要指定数据库名
    NOT_API_NAME(205, "缺少参数name"),
    ERROR_API_CLASS(206, "自定义API类错误"),
    NOT_DS_NAME(207, "未找到数据源"),
    API_DISABLED(300, "接口被禁用"),
    NOT_API_METHOD(301, "未找到方法"),

    TOKEN_INVALID(401, "token无效或已过期"),
    TOKEN_ACCESS_FORBIDDEN(101, "token已被禁止访问"),
    RESOURCE_NOT_FOUND(102, "请求资源不存在"),
    USERNAME_OR_PASSWORD_ERROR(103, "用户名或密码错误"),
    PASSWORD_ENTER_EXCEED_LIMIT(104, "用户输入密码次数超限"),
    ACCESS_UNAUTHORIZED(105, "访问未授权");

    private final int code;
    private final String msg;

    ApiResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public int getCode() {
        return this.code;
    }

    @Override
    public String getMsg() {
        return this.msg;
    }

    @Override
    public String getMsg(String msg) {
        if (this.code == ERROR_SQL.code) {
            return msg.replace("com.microsoft.sqlserver.jdbc.SQLServerException:", "");
        } else {
            return this.msg;
        }
    }

    /**
     * 这里可以对 msg 进行处理后输出
     * @param msg 返回Result
     * @return 返回Result
     */
    public ApiResult<Object> getResult(String msg) {
        return ApiResult.failed(this.getCode(), this.getMsg(msg));
    }

    public ApiResult<Object> getResult() {
        return ApiResult.failed(this.getCode(), this.getMsg());
    }
}
