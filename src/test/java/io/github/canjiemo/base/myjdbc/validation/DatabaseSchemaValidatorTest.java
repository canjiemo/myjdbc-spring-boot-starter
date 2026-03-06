package io.github.canjiemo.base.myjdbc.validation;

import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.configuration.MyJdbcProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseSchemaValidator PostgreSQL 校验测试")
class DatabaseSchemaValidatorTest {

    @AfterEach
    void cleanup() {
        TableCacheManager.clearCache();
    }

    @Test
    @DisplayName("PostgreSQL 校验 SQL 不应依赖 current_database/current_schema 函数")
    void postgresValidationShouldAvoidBlockedFunctions() {
        TableCacheManager.initCache("io.github.canjiemo.base.myjdbc.test.entity");

        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        DatabaseSchemaValidator validator = new DatabaseSchemaValidator(
                jdbcTemplate,
                postgresDataSource("public", "fitness_demo"),
                new MyJdbcProperties()
        );

        DatabaseSchemaValidator.ValidationResult result = validator.validateAllTables();

        assertFalse(result.hasErrors());
        assertTrue(jdbcTemplate.executedSqls.stream().allMatch(sql ->
                        !sql.contains("current_database()") && !sql.contains("current_schema()")),
                "PostgreSQL 校验 SQL 不应包含 Druid Wall 默认拦截的函数");
        assertTrue(jdbcTemplate.executedSqls.stream().anyMatch(sql ->
                        sql.contains("INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")),
                "表存在性校验应改为参数化 schema/table 查询");
        assertTrue(jdbcTemplate.executedSqls.stream().anyMatch(sql ->
                        sql.contains("INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")),
                "列信息校验应改为参数化 schema/table 查询");
    }

    @Test
    @DisplayName("校验汇总统计应按表维度而不是消息条数")
    void validationSummaryShouldCountTablesBySeverity() {
        DatabaseSchemaValidator.ValidationResult result = new DatabaseSchemaValidator.ValidationResult();
        result.addSuccess("sys_user", "删除标记字段验证通过");
        result.addSuccess("sys_user", "租户字段存在");
        result.addWarning("sys_role", "删除标记字段不存在");
        result.addSuccess("sys_role", "租户字段存在");
        result.addError("sys_menu", "表不存在");

        assertEquals(3, result.getTotalTables());
        assertEquals(1, result.getSuccessTableCount());
        assertEquals(1, result.getWarningTableCount());
        assertEquals(1, result.getErrorTableCount());

        assertEquals(3, result.getSuccessCount());
        assertEquals(1, result.getWarningCount());
        assertEquals(1, result.getErrorCount());
    }

    private static DataSource postgresDataSource(String schema, String catalog) {
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class[]{DatabaseMetaData.class},
                (proxy, method, args) -> {
                    if ("getDatabaseProductName".equals(method.getName())) {
                        return "PostgreSQL";
                    }
                    return defaultValue(method.getReturnType());
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getMetaData" -> metaData;
                        case "getSchema" -> schema;
                        case "getCatalog" -> catalog;
                        case "close" -> null;
                        default -> defaultValue(method.getReturnType());
                    };
                });

        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        return connection;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class || returnType == short.class || returnType == int.class
                || returnType == long.class || returnType == float.class || returnType == double.class) {
            return 0;
        }
        return null;
    }

    private static class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<String> executedSqls = new ArrayList<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            executedSqls.add(sql);
            return requiredType.cast(Integer.valueOf(1));
        }

        @Override
        public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
            executedSqls.add(sql);
            String tableName = String.valueOf(args[1]);
            List<String> columns = switch (tableName) {
                case "user" -> List.of("id", "delete_flag");
                case "role" -> List.of("id", "is_deleted");
                default -> List.of();
            };
            List<T> results = new ArrayList<>(columns.size());
            for (String column : columns) {
                results.add(elementType.cast(column));
            }
            return results;
        }
    }
}
