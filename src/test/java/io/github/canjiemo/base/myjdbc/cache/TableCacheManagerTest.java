package io.github.canjiemo.base.myjdbc.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TableCacheManager 缓存隔离测试")
class TableCacheManagerTest {

    @AfterEach
    void cleanup() {
        TableCacheManager.clearCache();
    }

    @Test
    @DisplayName("重复初始化时应清理旧缓存和旧租户表")
    void initCacheShouldResetPreviousState() {
        TableCacheManager.initCache("io.github.canjiemo.base.myjdbc.test.entity");
        TableCacheManager.registerTenantTable("user");

        assertNotNull(TableCacheManager.getDeleteInfoByTableName("user"));
        assertNotNull(TableCacheManager.getDeleteInfoByTableName("role"));
        assertFalse(TableCacheManager.hasTenantColumn("alt_user"));

        TableCacheManager.initCache("io.github.canjiemo.base.myjdbc.altentity");

        assertNull(TableCacheManager.getDeleteInfoByTableName("user"));
        assertNull(TableCacheManager.getDeleteInfoByTableName("role"));
        assertNotNull(TableCacheManager.getDeleteInfoByTableName("alt_user"));
        assertFalse(TableCacheManager.hasTenantColumn("user"));
    }

}
