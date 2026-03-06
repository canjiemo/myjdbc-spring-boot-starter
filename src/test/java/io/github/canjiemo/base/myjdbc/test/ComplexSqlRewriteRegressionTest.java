package io.github.canjiemo.base.myjdbc.test;

import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.parser.JSqlDynamicSqlParser;
import io.github.canjiemo.base.myjdbc.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("复杂 SQL 改写回归测试")
class ComplexSqlRewriteRegressionTest {

    private static final String TEST_ENTITY_PACKAGE = "io.github.canjiemo.base.myjdbc.test.entity";
    private static final String TENANT_PARAM = ":myjdbcTenantId";

    @BeforeEach
    void setUp() {
        TableCacheManager.initCache(TEST_ENTITY_PACKAGE);
        TableCacheManager.registerTenantTable("user");
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("CTE 内部基础表仍注入逻辑删除和租户条件")
    void shouldInjectConditionsIntoCteBody() {
        String sql = """
                WITH active_users AS (
                    SELECT u.id, u.username
                    FROM user u
                    WHERE u.status = 1
                ),
                role_summary AS (
                    SELECT r.id, r.role_name
                    FROM role r
                    WHERE r.id > 0
                )
                SELECT au.id, rs.role_name
                FROM active_users au
                LEFT JOIN role_summary rs ON au.id = rs.id
                """;

        String result = appendConditions(sql);

        assertTrue(result.contains("WITH active_users AS"), "CTE 结构应保留");
        assertTrue(result.contains("u.delete_flag = 0"), "CTE 内 user 应注入逻辑删除条件");
        assertTrue(result.contains("u.tenant_id = :myjdbcTenantId"), "CTE 内 user 应注入租户条件");
        assertTrue(result.contains("r.is_deleted = 0"), "CTE 内 role 应注入逻辑删除条件");
        assertEquals(1, countOccurrences(result, TENANT_PARAM), "仅 user 表有租户字段，租户参数应出现一次");
    }

    @Test
    @DisplayName("SELECT 列中的标量子查询会继续注入")
    void shouldInjectConditionsIntoScalarSubQueryInSelectItem() {
        String sql = """
                SELECT r.id,
                       (SELECT COUNT(*)
                        FROM user u
                        WHERE u.role_id = r.id) AS user_count
                FROM role r
                WHERE r.id > 0
                """;

        String result = appendConditions(sql);

        assertTrue(result.contains("r.is_deleted = 0"), "外层 role 应注入逻辑删除条件");
        assertTrue(result.contains("u.delete_flag = 0"), "标量子查询内 user 应注入逻辑删除条件");
        assertTrue(result.contains("u.tenant_id = :myjdbcTenantId"), "标量子查询内 user 应注入租户条件");
        assertEquals(1, countOccurrences(result, TENANT_PARAM), "该 SQL 只有子查询 user 应注入一次租户条件");
    }

    @Test
    @DisplayName("HAVING 子句中的 EXISTS 子查询会继续注入")
    void shouldInjectConditionsIntoHavingSubQuery() {
        String sql = """
                SELECT r.id, COUNT(*) AS cnt
                FROM role r
                GROUP BY r.id
                HAVING EXISTS (
                    SELECT 1
                    FROM user u
                    WHERE u.role_id = r.id
                      AND u.status = 1
                )
                """;

        String result = appendConditions(sql);

        assertTrue(result.contains("HAVING EXISTS"), "HAVING 子句应保留");
        assertTrue(result.contains("r.is_deleted = 0"), "外层 role 应注入逻辑删除条件");
        assertTrue(result.contains("u.delete_flag = 0"), "HAVING 子查询内 user 应注入逻辑删除条件");
        assertTrue(result.contains("u.tenant_id = :myjdbcTenantId"), "HAVING 子查询内 user 应注入租户条件");
    }

    @Test
    @DisplayName("JOIN ON 中的 EXISTS 子查询会继续注入")
    void shouldInjectConditionsIntoJoinOnSubQuery() {
        String sql = """
                SELECT r.id, u.username
                FROM role r
                LEFT JOIN user u
                  ON u.role_id = r.id
                 AND EXISTS (
                    SELECT 1
                    FROM user ux
                    WHERE ux.id = u.id
                      AND ux.status = 1
                 )
                """;

        String result = appendConditions(sql);

        assertTrue(result.contains("r.is_deleted = 0"), "外层 role 应注入逻辑删除条件");
        assertTrue(result.contains("u.delete_flag = 0"), "LEFT JOIN 的 user 应注入逻辑删除条件");
        assertTrue(result.contains("u.tenant_id = :myjdbcTenantId"), "LEFT JOIN 的 user 应注入租户条件");
        assertTrue(result.contains("ux.delete_flag = 0"), "JOIN ON 子查询内 user 应注入逻辑删除条件");
        assertTrue(result.contains("ux.tenant_id = :myjdbcTenantId"), "JOIN ON 子查询内 user 应注入租户条件");
        assertEquals(2, countOccurrences(result, TENANT_PARAM), "JOIN 表和 ON 子查询内 user 各应注入一次租户条件");
    }

    @Test
    @DisplayName("UNION ALL 混合分支仍分别改写并保留排序")
    void shouldInjectConditionsIntoUnionBranchesAndKeepOrderBy() {
        String sql = """
                SELECT u.id AS biz_id
                FROM user u
                WHERE u.status = 1
                UNION ALL
                SELECT r.id AS biz_id
                FROM role r
                WHERE r.id > 0
                ORDER BY biz_id DESC
                """;

        String result = appendConditions(sql);

        assertTrue(result.contains("u.delete_flag = 0"), "UNION 第一分支 user 应注入逻辑删除条件");
        assertTrue(result.contains("u.tenant_id = :myjdbcTenantId"), "UNION 第一分支 user 应注入租户条件");
        assertTrue(result.contains("r.is_deleted = 0"), "UNION 第二分支 role 应注入逻辑删除条件");
        assertTrue(result.contains("ORDER BY biz_id DESC"), "外层排序应保留");
        assertEquals(1, countOccurrences(result, TENANT_PARAM), "UNION 中只有 user 分支应注入租户条件");
    }

    private String appendConditions(String sql) {
        return JSqlDynamicSqlParser.appendConditions(sql, true, "tenant_id");
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
