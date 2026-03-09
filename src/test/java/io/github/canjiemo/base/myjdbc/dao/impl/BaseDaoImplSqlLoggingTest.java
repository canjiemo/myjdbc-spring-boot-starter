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

    @Test
    @DisplayName("数字参数应内联为无引号形式")
    void shouldBuildInlineSqlForNumericParams() {
        MapSqlParameterSource sps = new MapSqlParameterSource()
                .addValue("id", 1L)
                .addValue(JSqlDynamicSqlParser.TENANT_PARAM_NAME, 1001L);

        BaseDaoImpl.SqlLogPreview preview = BaseDaoImpl.buildSqlLogPreview(
                "SELECT * FROM sys_user WHERE id = :id AND tenant_id = :" + JSqlDynamicSqlParser.TENANT_PARAM_NAME,
                sps
        );

        assertEquals("SELECT * FROM sys_user WHERE id = 1 AND tenant_id = 1001", preview.inlineSql());
    }

    @Test
    @DisplayName("字符串参数应内联为单引号包裹形式")
    void shouldBuildInlineSqlForStringParams() {
        MapSqlParameterSource sps = new MapSqlParameterSource()
                .addValue("username", "Alice");

        BaseDaoImpl.SqlLogPreview preview = BaseDaoImpl.buildSqlLogPreview(
                "SELECT * FROM sys_user WHERE username = :username",
                sps
        );

        assertEquals("SELECT * FROM sys_user WHERE username = 'Alice'", preview.inlineSql());
    }

    @Test
    @DisplayName("字符串中含单引号应被转义为两个单引号")
    void shouldEscapeSingleQuoteInInlineSql() {
        MapSqlParameterSource sps = new MapSqlParameterSource()
                .addValue("name", "O'Brien");

        BaseDaoImpl.SqlLogPreview preview = BaseDaoImpl.buildSqlLogPreview(
                "SELECT * FROM sys_user WHERE name = :name",
                sps
        );

        assertEquals("SELECT * FROM sys_user WHERE name = 'O''Brien'", preview.inlineSql());
    }

    @Test
    @DisplayName("null 参数应内联为 NULL")
    void shouldBuildInlineSqlForNullParam() {
        MapSqlParameterSource sps = new MapSqlParameterSource()
                .addValue("remark", null);

        BaseDaoImpl.SqlLogPreview preview = BaseDaoImpl.buildSqlLogPreview(
                "SELECT * FROM sys_user WHERE remark = :remark",
                sps
        );

        assertEquals("SELECT * FROM sys_user WHERE remark = NULL", preview.inlineSql());
    }

    @Test
    @DisplayName("IN 集合参数应在内联 SQL 中展开为多个值")
    void shouldBuildInlineSqlForCollectionParams() {
        MapSqlParameterSource sps = new MapSqlParameterSource()
                .addValue("ids", List.of(1L, 2L, 3L));

        BaseDaoImpl.SqlLogPreview preview = BaseDaoImpl.buildSqlLogPreview(
                "SELECT * FROM sys_user WHERE id IN (:ids)",
                sps
        );

        assertEquals("SELECT * FROM sys_user WHERE id IN (1, 2, 3)", preview.inlineSql());
    }

    @Test
    @DisplayName("无参数时 inlineSql 应与原始 SQL 相同")
    void shouldReturnOriginalSqlWhenNoParams() {
        String sql = "SELECT * FROM sys_user";
        BaseDaoImpl.SqlLogPreview preview = BaseDaoImpl.buildSqlLogPreview(sql, null);

        assertEquals(sql, preview.inlineSql());
    }

    @Test
    @DisplayName("buildInlineSql 直接测试：混合数字和字符串")
    void shouldBuildInlineSqlDirectly() {
        String preparedSql = "SELECT * FROM t WHERE id = ? AND name = ?";
        Object[] values = {42L, "Bob"};

        String result = BaseDaoImpl.buildInlineSql(preparedSql, values);

        assertEquals("SELECT * FROM t WHERE id = 42 AND name = 'Bob'", result);
    }
}
