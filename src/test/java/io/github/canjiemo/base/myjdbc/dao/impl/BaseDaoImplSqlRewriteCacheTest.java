package io.github.canjiemo.base.myjdbc.dao.impl;

import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.configuration.MyJdbcProperties;
import io.github.canjiemo.base.myjdbc.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BaseDaoImpl SQL 改写缓存测试")
class BaseDaoImplSqlRewriteCacheTest {

    @BeforeEach
    void setup() {
        TableCacheManager.initCache("io.github.canjiemo.base.myjdbc.test.entity");
        TableCacheManager.registerTenantTable("user");
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        TableCacheManager.clearCache();
    }

    @Test
    @DisplayName("相同 SQL 的删除条件改写结果应命中缓存")
    void shouldCacheDeleteOnlyRewriteTemplate() {
        BaseDaoImpl dao = new BaseDaoImpl(tenantEnabledProperties(), 16);

        String sql = "SELECT * FROM user WHERE age > 18";
        String first = dao.rewriteQuerySql(sql, false);
        String second = dao.rewriteQuerySql(sql, false);

        assertEquals(first, second);
        assertTrue(first.contains("delete_flag"), "应注入逻辑删除条件");
        assertFalse(first.contains(":myjdbcTenantId"), "删除条件路径不应包含租户占位符");
        assertEquals(1, dao.getQueryRewriteCacheSize(), "相同模板应只缓存一份");
    }

    @Test
    @DisplayName("租户路径和非租户路径应使用独立缓存键")
    void shouldSeparateDeleteOnlyAndTenantRewriteEntries() {
        BaseDaoImpl dao = new BaseDaoImpl(tenantEnabledProperties(), 16);

        String sql = "SELECT * FROM user WHERE age > 18";
        String deleteOnly = dao.rewriteQuerySql(sql, false);
        String withTenant = dao.rewriteQuerySql(sql, true);

        assertTrue(deleteOnly.contains("delete_flag"), "删除条件路径应保留逻辑删除条件");
        assertFalse(deleteOnly.contains(":myjdbcTenantId"), "删除条件路径不应包含租户条件");
        assertTrue(withTenant.contains("delete_flag"), "租户路径应同时包含逻辑删除条件");
        assertTrue(withTenant.contains(":myjdbcTenantId"), "租户路径应包含租户条件");
        assertEquals(2, dao.getQueryRewriteCacheSize(), "同一 SQL 的两条改写路径应分别缓存");
    }

    @Test
    @DisplayName("改写缓存应保持上限，避免无限增长")
    void shouldKeepRewriteCacheBounded() {
        BaseDaoImpl dao = new BaseDaoImpl(tenantEnabledProperties(), 8);

        for (int i = 0; i < 32; i++) {
            dao.rewriteQuerySql("SELECT * FROM user WHERE age > " + i, true);
        }

        assertTrue(dao.getQueryRewriteCacheSize() <= 8,
                "缓存大小应受上限控制，当前大小=" + dao.getQueryRewriteCacheSize());
    }

    private static MyJdbcProperties tenantEnabledProperties() {
        MyJdbcProperties properties = new MyJdbcProperties();
        properties.getTenant().setEnabled(true);
        return properties;
    }
}
