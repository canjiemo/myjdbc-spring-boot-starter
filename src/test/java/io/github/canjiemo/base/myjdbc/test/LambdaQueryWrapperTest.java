package io.github.canjiemo.base.myjdbc.test;

import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.dao.IBaseDao;
import io.github.canjiemo.base.myjdbc.lambda.LambdaQueryWrapper;
import io.github.canjiemo.base.myjdbc.scope.MyJdbcScope;
import io.github.canjiemo.base.myjdbc.test.entity.TestUser;
import org.junit.jupiter.api.*;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LambdaQueryWrapper 链式查询 API 测试
 * 不依赖 Spring 容器，直接验证 SQL 生成结果
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("LambdaQueryWrapper 链式查询 API 测试")
class LambdaQueryWrapperTest {

    @BeforeAll
    static void setup() {
        TableCacheManager.initCache("io.github.canjiemo.base.myjdbc.test.entity");
    }

    private LambdaQueryWrapper<TestUser, TestUser> wrapper() {
        return new LambdaQueryWrapper<>(TestUser.class, TestUser.class, null);
    }

    @Test
    @Order(1)
    @DisplayName("1. eq 条件生成")
    void testEq() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().eq(TestUser::getUsername, "张三");
        assertEquals("SELECT * FROM user WHERE username = :lwp0", q.buildSql());
        assertEquals("张三", q.getParams().get("lwp0"));
    }

    @Test
    @Order(2)
    @DisplayName("2. ne 条件生成")
    void testNe() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().ne(TestUser::getUsername, "李四");
        assertEquals("SELECT * FROM user WHERE username != :lwp0", q.buildSql());
        assertEquals("李四", q.getParams().get("lwp0"));
    }

    @Test
    @Order(3)
    @DisplayName("3. gt / ge / lt / le 条件生成")
    void testCompare() {
        assertTrue(wrapper().gt(TestUser::getId, 10L).buildSql().contains("> :lwp0"));
        assertTrue(wrapper().ge(TestUser::getId, 10L).buildSql().contains(">= :lwp0"));
        assertTrue(wrapper().lt(TestUser::getId, 10L).buildSql().contains("< :lwp0"));
        assertTrue(wrapper().le(TestUser::getId, 10L).buildSql().contains("<= :lwp0"));
    }

    @Test
    @Order(4)
    @DisplayName("4. like / likeLeft / likeRight 条件生成")
    void testLike() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().like(TestUser::getUsername, "张");
        assertEquals("SELECT * FROM user WHERE username LIKE :lwp0", q.buildSql());
        assertEquals("%张%", q.getParams().get("lwp0"));

        assertEquals("%三", wrapper().likeLeft(TestUser::getUsername, "三").getParams().get("lwp0"));
        assertEquals("张%", wrapper().likeRight(TestUser::getUsername, "张").getParams().get("lwp0"));
    }

    @Test
    @Order(5)
    @DisplayName("5. in / notIn 条件生成")
    void testIn() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().in(TestUser::getId, ids);
        assertEquals("SELECT * FROM user WHERE id IN (:lwp0)", q.buildSql());
        assertEquals(ids, q.getParams().get("lwp0"));

        assertTrue(wrapper().notIn(TestUser::getId, ids).buildSql().contains("NOT IN (:lwp0)"));
    }

    @Test
    @Order(6)
    @DisplayName("6. between 条件生成")
    void testBetween() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().between(TestUser::getId, 10L, 100L);
        assertEquals("SELECT * FROM user WHERE id BETWEEN :lwp0 AND :lwp1", q.buildSql());
        assertEquals(10L, q.getParams().get("lwp0"));
        assertEquals(100L, q.getParams().get("lwp1"));
    }

    @Test
    @Order(7)
    @DisplayName("7. isNull / isNotNull 条件生成")
    void testNullCheck() {
        assertEquals("SELECT * FROM user WHERE username IS NULL",
                wrapper().isNull(TestUser::getUsername).buildSql());
        assertEquals("SELECT * FROM user WHERE username IS NOT NULL",
                wrapper().isNotNull(TestUser::getUsername).buildSql());
    }

    @Test
    @Order(8)
    @DisplayName("8. select 指定列")
    void testSelect() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper().select(TestUser::getId, TestUser::getUsername);
        assertEquals("SELECT id, username FROM user", q.buildSql());
    }

    @Test
    @Order(9)
    @DisplayName("9. orderByAsc / orderByDesc")
    void testOrder() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .eq(TestUser::getDeleteFlag, 0)
                .orderByAsc(TestUser::getId)
                .orderByDesc(TestUser::getUsername);
        assertTrue(q.buildSql().contains("ORDER BY id ASC, username DESC"));
    }

    @Test
    @Order(10)
    @DisplayName("10. 多条件 AND 组合")
    void testMultipleConditions() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .eq(TestUser::getUsername, "张三")
                .gt(TestUser::getId, 0L)
                .isNotNull(TestUser::getDeleteFlag);
        assertEquals(
                "SELECT * FROM user WHERE username = :lwp0 AND id > :lwp1 AND delete_flag IS NOT NULL",
                q.buildSql());
    }

    @Test
    @Order(11)
    @DisplayName("11. 空条件时无 WHERE 子句")
    void testNoCondition() {
        assertEquals("SELECT * FROM user", wrapper().buildSql());
    }

    @Test
    @Order(12)
    @DisplayName("12. select + 条件 + 排序组合")
    void testFullQuery() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .select(TestUser::getId, TestUser::getUsername)
                .eq(TestUser::getDeleteFlag, 0)
                .orderByAsc(TestUser::getId);
        assertEquals(
                "SELECT id, username FROM user WHERE delete_flag = :lwp0 ORDER BY id ASC",
                q.buildSql());
    }

    @Test
    @Order(13)
    @DisplayName("13. null 值自动跳过，不生成 SQL 片段")
    void testSkipNull() {
        // null 值 → 跳过，最终无 WHERE
        assertEquals("SELECT * FROM user", wrapper().eq(TestUser::getUsername, null).buildSql());
        // 混合：null 跳过，非 null 保留
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .eq(TestUser::getUsername, null)
                .eq(TestUser::getDeleteFlag, 0);
        assertEquals("SELECT * FROM user WHERE delete_flag = :lwp0", q.buildSql());
    }

    @Test
    @Order(14)
    @DisplayName("14. 空字符串（trim 后）自动跳过")
    void testSkipBlankString() {
        assertEquals("SELECT * FROM user", wrapper().eq(TestUser::getUsername, "").buildSql());
        assertEquals("SELECT * FROM user", wrapper().like(TestUser::getUsername, "  ").buildSql());
        // 非空字符串正常生成
        assertTrue(wrapper().like(TestUser::getUsername, "张").buildSql().contains("LIKE"));
    }

    @Test
    @Order(15)
    @DisplayName("15. 空集合自动跳过（避免 IN () 语法错误）")
    void testSkipEmptyCollection() {
        assertEquals("SELECT * FROM user",
                wrapper().in(TestUser::getId, List.of()).buildSql());
        assertEquals("SELECT * FROM user",
                wrapper().notIn(TestUser::getId, List.of()).buildSql());
    }

    @Test
    @Order(16)
    @DisplayName("16. between 任意端为 null 则跳过")
    void testSkipBetweenNull() {
        assertEquals("SELECT * FROM user", wrapper().between(TestUser::getId, null, 100L).buildSql());
        assertEquals("SELECT * FROM user", wrapper().between(TestUser::getId, 10L, null).buildSql());
        // 两端均非 null 时正常生成
        assertTrue(wrapper().between(TestUser::getId, 10L, 100L).buildSql().contains("BETWEEN"));
    }

    @Test
    @Order(17)
    @DisplayName("17. any 应生成 OR 条件组")
    void testAnyGroup() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .eq(TestUser::getDeleteFlag, 0)
                .any(
                        x -> x.eq(TestUser::getUsername, "张三"),
                        x -> x.eq(TestUser::getId, 1L)
                );
        assertEquals(
                "SELECT * FROM user WHERE delete_flag = :lwp0 AND ((username = :lwp1) OR (id = :lwp2))",
                q.buildSql());
    }

    @Test
    @Order(18)
    @DisplayName("18. all 应生成显式 AND 条件组")
    void testAllGroup() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .all(
                        x -> x.eq(TestUser::getDeleteFlag, 0),
                        x -> x.like(TestUser::getUsername, "张")
                );
        assertEquals(
                "SELECT * FROM user WHERE ((delete_flag = :lwp0) AND (username LIKE :lwp1))",
                q.buildSql());
    }

    @Test
    @Order(19)
    @DisplayName("19. or 后续条件应按 OR 连接")
    void testOrNextCondition() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .eq(TestUser::getDeleteFlag, 0)
                .or()
                .eq(TestUser::getId, 1L);
        assertEquals("SELECT * FROM user WHERE delete_flag = :lwp0 OR id = :lwp1", q.buildSql());
    }

    @Test
    @Order(20)
    @DisplayName("20. or(consumer) 应生成 OR 嵌套组")
    void testOrConsumer() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .eq(TestUser::getDeleteFlag, 0)
                .or(x -> x.eq(TestUser::getUsername, "张三").eq(TestUser::getId, 1L));
        assertEquals(
                "SELECT * FROM user WHERE delete_flag = :lwp0 OR (username = :lwp1 AND id = :lwp2)",
                q.buildSql());
    }

    @Test
    @Order(21)
    @DisplayName("21. when 为 false 时应跳过条件")
    void testWhen() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .when(false, x -> x.eq(TestUser::getUsername, "不会生效"))
                .when(true, x -> x.eq(TestUser::getDeleteFlag, 0));
        assertEquals("SELECT * FROM user WHERE delete_flag = :lwp0", q.buildSql());
    }

    @Test
    @Order(22)
    @DisplayName("22. exists 应重写内部参数名")
    void testExists() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .exists("SELECT 1 FROM user_role ur WHERE ur.user_id = user.id AND ur.role_id = :roleId",
                        java.util.Map.of("roleId", 10L));
        assertEquals(
                "SELECT * FROM user WHERE EXISTS (SELECT 1 FROM user_role ur WHERE ur.user_id = user.id AND ur.role_id = :lwp0)",
                q.buildSql());
        assertEquals(10L, q.getParams().get("lwp0"));
    }

    @Test
    @Order(23)
    @DisplayName("23. selectAs + selectRaw + groupBy + having 应正确生成 SQL")
    void testAggregateQuery() {
        LambdaQueryWrapper<TestUser, TestUser> q = wrapper()
                .selectAs(TestUser::getDeleteFlag, "flag")
                .selectRaw("count(*)", "total")
                .groupBy(TestUser::getDeleteFlag)
                .having("count(*) >= :minCount", java.util.Map.of("minCount", 2));
        assertEquals(
                "SELECT delete_flag AS flag, count(*) AS total FROM user GROUP BY delete_flag HAVING count(*) >= :lwp0",
                q.buildSql());
        assertEquals(2, q.getParams().get("lwp0"));
    }

    @Test
    @Order(24)
    @DisplayName("24. allTenants + withDeleted 应只在本次执行内生效")
    void testExecutionScope() {
        AtomicReference<Boolean> tenantSkipped = new AtomicReference<>(false);
        AtomicReference<Boolean> deleteSkipped = new AtomicReference<>(false);

        IBaseDao dao = (IBaseDao) Proxy.newProxyInstance(
                IBaseDao.class.getClassLoader(),
                new Class[]{IBaseDao.class},
                (proxy, method, args) -> {
                    if ("queryListForSql".equals(method.getName())
                            && method.getParameterCount() == 3
                            && args[1] instanceof Map) {
                        tenantSkipped.set(MyJdbcScope.isTenantSkipped());
                        deleteSkipped.set(MyJdbcScope.isLogicDeleteSkipped());
                        return List.of();
                    }
                    return null;
                });

        new LambdaQueryWrapper<>(TestUser.class, TestUser.class, dao)
                .allTenants()
                .withDeleted()
                .list();

        assertTrue(tenantSkipped.get(), "执行期应临时关闭租户隔离");
        assertTrue(deleteSkipped.get(), "执行期应临时包含逻辑删除数据");
        assertFalse(MyJdbcScope.isTenantSkipped(), "执行结束后租户作用域应恢复");
        assertFalse(MyJdbcScope.isLogicDeleteSkipped(), "执行结束后逻辑删除作用域应恢复");
    }

    @Test
    @Order(25)
    @DisplayName("25. 危险 selectRaw 关键字应被拒绝")
    void testRejectDangerousSelectRaw() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> wrapper().selectRaw("update user set delete_flag = 1"));
        assertTrue(ex.getMessage().contains("危险关键字"));
    }
}
