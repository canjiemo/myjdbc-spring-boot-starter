package io.github.canjiemo.base.myjdbc.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("MyJdbcErrorCode 错误码规范测试")
class MyJdbcErrorCodeTest {

    @Test
    @DisplayName("CONFIG_ERROR 对外文案包含错误码")
    void configErrorShouldContainCode() {
        assertEquals("[MYJDBC-1001] 数据配置异常，请联系管理员", MyJdbcErrorCode.CONFIG_ERROR.userMessage());
    }

    @Test
    @DisplayName("DAO_ERROR 对外文案包含错误码")
    void daoErrorShouldContainCode() {
        assertEquals("[MYJDBC-2001] 系统错误,请联系管理员", MyJdbcErrorCode.DAO_ERROR.userMessage());
    }
}
