package io.github.canjiemo.base.myjdbc.dao.impl;

import io.github.canjiemo.base.myjdbc.parser.JSqlDynamicSqlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("BaseDaoImpl SQL 日志预览测试")
class BaseDaoImplSqlLoggingTest {

    @Test
    @DisplayName("命名参数应转换为 MyBatis 风格的 Preparing 和 Parameters")
    void shouldBuildSqlPreviewForNamedParameters() {
        MapSqlParameterSource sps = new MapSqlParameterSource()
                .addValue("id", 1L)
                .addValue(JSqlDynamicSqlParser.TENANT_PARAM_NAME, 1001L);

        BaseDaoImpl.SqlLogPreview preview = BaseDaoImpl.buildSqlLogPreview(
                "SELECT * FROM sys_user WHERE id = :id AND tenant_id = :" + JSqlDynamicSqlParser.TENANT_PARAM_NAME,
                sps
        );

        assertEquals("SELECT * FROM sys_user WHERE id = ? AND tenant_id = ?", preview.preparedSql());
        assertEquals("1(Long), 1001(Long)", preview.parameters());
    }

    @Test
    @DisplayName("IN 集合参数应展开为多个问号并按顺序打印")
    void shouldExpandCollectionParameters() {
        MapSqlParameterSource sps = new MapSqlParameterSource()
                .addValue("ids", List.of(1L, 2L, 3L));

        BaseDaoImpl.SqlLogPreview preview = BaseDaoImpl.buildSqlLogPreview(
                "SELECT * FROM sys_user WHERE id IN (:ids)",
                sps
        );

        assertEquals("SELECT * FROM sys_user WHERE id IN (?, ?, ?)", preview.preparedSql());
        assertEquals("1(Long), 2(Long), 3(Long)", preview.parameters());
    }
}
