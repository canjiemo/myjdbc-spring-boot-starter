package io.github.mocanjie.base.myjpa.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("MyJpaErrorCode 错误码规范测试")
class MyJpaErrorCodeTest {

    @Test
    @DisplayName("CONFIG_ERROR 对外文案包含错误码")
    void configErrorShouldContainCode() {
        assertEquals("[MYJPA-1001] 数据配置异常，请联系管理员", MyJpaErrorCode.CONFIG_ERROR.userMessage());
    }

    @Test
    @DisplayName("DAO_ERROR 对外文案包含错误码")
    void daoErrorShouldContainCode() {
        assertEquals("[MYJPA-2001] 系统错误,请联系管理员", MyJpaErrorCode.DAO_ERROR.userMessage());
    }
}
