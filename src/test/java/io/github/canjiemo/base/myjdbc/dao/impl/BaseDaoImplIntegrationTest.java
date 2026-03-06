package io.github.canjiemo.base.myjdbc.dao.impl;

import io.github.canjiemo.base.myjdbc.annotation.MyTable;
import io.github.canjiemo.base.myjdbc.builder.DbType;
import io.github.canjiemo.base.myjdbc.builder.SqlBuilder;
import io.github.canjiemo.base.myjdbc.builder.TableInfoBuilder;
import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.configuration.MyJdbcProperties;
import io.github.canjiemo.base.myjdbc.metadata.TableInfo;
import io.github.canjiemo.base.myjdbc.test.entity.TestUser;
import io.github.canjiemo.base.myjdbc.utils.MyReflectionUtils;
import io.github.canjiemo.mycommon.pager.Pager;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseDaoImpl 端对端集成测试。
 *
 * <p>使用 H2 内存数据库验证：
 * <ul>
 *   <li>逻辑删除条件 SQL 注入（{@link BaseDaoImpl#rewriteQuerySql}）</li>
 *   <li>SQL 改写缓存命中/未命中计数器</li>
 *   <li>分页 SQL 构建（{@link SqlBuilder#buildPagerSql}）</li>
 *   <li>真实 DB 交互（通过原生 JDBC 执行 COUNT，绕过行映射器）</li>
 * </ul>
 *
 * <p>注意：本测试与 {@link BaseDaoImpl} 同包，故可访问其包级私有方法。
 */
@DisplayName("BaseDaoImpl H2 集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BaseDaoImplIntegrationTest {

    private static EmbeddedDatabase db;
    private static BaseDaoImpl dao;
    private static SqlBuilder sqlBuilder;
    private static NamedParameterJdbcTemplate namedTpl;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setUp() throws Exception {
        // H2 不使用 MySQL 模式，避免 2.2.x 的 ROW/JSON 类型兼容问题；
        // NON_KEYWORDS=USER 允许 user 作为表名使用。
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("inttest;NON_KEYWORDS=USER;DATABASE_TO_LOWER=TRUE")
                .addScript("classpath:schema-h2.sql")
                .build();
        namedTpl = new NamedParameterJdbcTemplate(db);

        // 1. 初始化 TableCacheManager（供 SQL 改写注入逻辑删除条件）
        TableCacheManager.initCache("io.github.canjiemo.base.myjdbc.test.entity");

        // 2. 手动填充 TableInfoBuilder.tableInfoMap（不调用 init()，
        //    避免其内部 initTableCacheManager() 覆盖上面的缓存）
        MyTable annotation = TestUser.class.getAnnotation(MyTable.class);
        Field pkField = MyReflectionUtils.findFieldInHierarchy(TestUser.class, annotation.pkField());
        TableInfo tableInfo = new TableInfo()
                .setTableName(annotation.value())
                .setClazz(TestUser.class)
                .setPkField(pkField)
                .setPkFieldName(annotation.pkField())
                .setPkColumnName(annotation.pkColumn())
                .setFieldList(MyReflectionUtils.getFieldList(TestUser.class))
                .setDelColumnName(annotation.delColumn())
                .setDelFieldName(annotation.delField())
                .setDelValue(annotation.delValue());
        Field mapField = TableInfoBuilder.class.getDeclaredField("tableInfoMap");
        mapField.setAccessible(true);
        ((Map<Class<?>, TableInfo>) mapField.get(null)).put(TestUser.class, tableInfo);

        // 3. 创建 BaseDaoImpl 并注入 NamedParameterJdbcTemplate
        MyJdbcProperties props = new MyJdbcProperties();
        props.getShowSql().setEnabled(false);
        sqlBuilder = new SqlBuilder(DbType.MYSQL);
        dao = new BaseDaoImpl(props, sqlBuilder);
        Field tplField = BaseDaoImpl.class.getDeclaredField("namedParameterJdbcTemplate");
        tplField.setAccessible(true);
        tplField.set(dao, namedTpl);

        // 4. 预插入测试数据（通过裸 JDBC，不经过 BaseDaoImpl.insertPO）
        namedTpl.update(
                "INSERT INTO user (id, username, delete_flag) VALUES (:id, :name, :del)",
                new MapSqlParameterSource().addValue("id", 100L).addValue("name", "alice").addValue("del", 0));
        namedTpl.update(
                "INSERT INTO user (id, username, delete_flag) VALUES (:id, :name, :del)",
                new MapSqlParameterSource().addValue("id", 200L).addValue("name", "bob").addValue("del", 0));
        // 逻辑删除记录（delete_flag=1）
        namedTpl.update(
                "INSERT INTO user (id, username, delete_flag) VALUES (:id, :name, :del)",
                new MapSqlParameterSource().addValue("id", 300L).addValue("name", "ghost").addValue("del", 1));
    }

    @AfterAll
    @SuppressWarnings("unchecked")
    static void tearDown() throws Exception {
        TableCacheManager.clearCache();
        Field mapField = TableInfoBuilder.class.getDeclaredField("tableInfoMap");
        mapField.setAccessible(true);
        ((Map<?, ?>) mapField.get(null)).remove(TestUser.class);
        Field loggedField = TableInfoBuilder.class.getDeclaredField("missingTableInfoLogged");
        loggedField.setAccessible(true);
        ((Set<?>) loggedField.get(null)).clear();
        if (db != null) db.shutdown();
    }

    // ── SQL 改写：逻辑删除条件注入 ───────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("rewriteQuerySql 应向 user 表查询注入 delete_flag 条件")
    void rewriteShouldInjectDeleteCondition() {
        String original = "SELECT * FROM user";
        String rewritten = dao.rewriteQuerySql(original, false);
        assertTrue(rewritten.contains("delete_flag"),
                "改写后 SQL 应包含 delete_flag 条件，实际: " + rewritten);
        assertTrue(rewritten.contains("0"),
                "改写后 SQL 应包含 delete_flag = 0 的值，实际: " + rewritten);
    }

    @Test
    @Order(2)
    @DisplayName("rewriteQuerySql 对无 @MyTable 的表不注入条件")
    void rewriteShouldNotInjectConditionForUnknownTable() {
        String original = "SELECT * FROM order_detail";
        String rewritten = dao.rewriteQuerySql(original, false);
        // order_detail 未注册到 TableCacheManager，不应改写
        assertFalse(rewritten.contains("delete_flag"),
                "未知表不应注入删除条件，实际: " + rewritten);
    }

    // ── 真实 DB 交互：通过 H2 验证过滤效果 ──────────────────────────────

    @Test
    @Order(3)
    @DisplayName("H2 中 delete_flag=0 的记录应有 2 条，=1 的应有 1 条")
    void h2DataShouldHaveCorrectRowCounts() {
        Long total = namedTpl.queryForObject(
                "SELECT COUNT(*) FROM user", Map.of(), Long.class);
        Long notDeleted = namedTpl.queryForObject(
                "SELECT COUNT(*) FROM user WHERE delete_flag = 0", Map.of(), Long.class);
        Long deleted = namedTpl.queryForObject(
                "SELECT COUNT(*) FROM user WHERE delete_flag = 1", Map.of(), Long.class);

        assertEquals(3L, total, "H2 表中应有 3 行");
        assertEquals(2L, notDeleted, "其中 delete_flag=0 应有 2 行");
        assertEquals(1L, deleted, "其中 delete_flag=1 应有 1 行");
    }

    // ── 分页 SQL 构建 ─────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("buildPagerSql (MySQL) 应生成含 LIMIT 的分页 SQL")
    void mysqlPagerSqlShouldContainLimitClause() {
        Pager<?> pager = new Pager<>(1, 10);
        String pageSql = sqlBuilder.buildPagerSql("SELECT * FROM user", pager);
        assertTrue(pageSql.toLowerCase().contains("limit"),
                "MySQL 分页 SQL 应含 LIMIT 子句，实际: " + pageSql);
    }

    // ── SQL 改写缓存指标 ──────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("重复改写相同 SQL 后缓存命中次数应增加")
    void cacheHitsShouldIncreaseWithRepeatRewrites() {
        long hitsBefore = dao.getQueryRewriteCacheHits();
        String sql = "SELECT * FROM user WHERE status = 1";
        for (int i = 0; i < 5; i++) {
            dao.rewriteQuerySql(sql, false);
        }
        assertTrue(dao.getQueryRewriteCacheHits() > hitsBefore,
                "缓存命中次数应增加（before=%d, after=%d）"
                        .formatted(hitsBefore, dao.getQueryRewriteCacheHits()));
    }

    @Test
    @Order(7)
    @DisplayName("缓存条目数应在首次改写后大于 0")
    void cacheSizeShouldBePositiveAfterRewrites() {
        assertTrue(dao.getQueryRewriteCacheSize() > 0, "缓存条目数应大于 0");
    }

    @Test
    @Order(8)
    @DisplayName("不同 SQL 的未命中次数应大于 0")
    void cacheMissesShouldBePositiveAfterFirstRewrite() {
        assertTrue(dao.getQueryRewriteCacheMisses() > 0, "首次改写未命中次数应大于 0");
    }
}
