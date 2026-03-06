package io.github.canjiemo.base.myjdbc.error;

/**
 * MyJDBC 统一业务错误码（面向前端可见）。
 */
public enum MyJdbcErrorCode {
    CONFIG_ERROR("MYJDBC-1001", "数据配置异常，请联系管理员"),
    DAO_ERROR("MYJDBC-2001", "系统错误,请联系管理员");

    private final String code;
    private final String message;

    MyJdbcErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String userMessage() {
        return "[" + code + "] " + message;
    }
}
