package io.github.canjiemo.base.myjdbc.test;

import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.dao.IBaseDao;
import io.github.canjiemo.base.myjdbc.lambda.LambdaUpdateWrapper;
import io.github.canjiemo.base.myjdbc.scope.MyJdbcScope;
import io.github.canjiemo.base.myjdbc.test.entity.TestUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LambdaUpdateWrapper DSL 测试")
class LambdaUpdateWrapperTest {

    @BeforeAll
    static void setup() {
        TableCacheManager.initCache("io.github.canjiemo.base.myjdbc.test.entity");
    }

    private LambdaUpdateWrapper<TestUser> wrapper() {
        return new LambdaUpdateWrapper<>(TestUser.class, null);
    }

    @Test
    @DisplayName("基础 set + where 应生成 update SQL")
    void shouldBuildBasicUpdateSql() {
        LambdaUpdateWrapper<TestUser> wrapper = wrapper()
                .set(TestUser::getUsername, "新名称")
                .eq(TestUser::getId, 100L);

        assertEquals("UPDATE user SET username = :lwp0 WHERE id = :lwp1", wrapper.buildSql());
        assertEquals("新名称", wrapper.getParams().get("lwp0"));
        assertEquals(100L, wrapper.getParams().get("lwp1"));
    }

    @Test
    @DisplayName("setIfPresent 应跳过空值")
    void shouldSkipEmptyValuesInSetIfPresent() {
        LambdaUpdateWrapper<TestUser> wrapper = wrapper()
                .setIfPresent(TestUser::getUsername, "  ")
                .setIfPresent(TestUser::getDeleteFlag, 0)
                .eq(TestUser::getId, 1L);

        assertEquals("UPDATE user SET delete_flag = :lwp0 WHERE id = :lwp1", wrapper.buildSql());
    }

    @Test
    @DisplayName("increase / decrease / setNull 应正确生成 SQL")
    void shouldBuildComputedUpdateSql() {
        LambdaUpdateWrapper<TestUser> wrapper = wrapper()
                .increase(TestUser::getDeleteFlag, 2)
                .decrease(TestUser::getId, 1)
                .setNull(TestUser::getUsername)
                .eq(TestUser::getId, 8L);

        assertEquals(
                "UPDATE user SET delete_flag = delete_flag + :lwp0, id = id - :lwp1, username = NULL WHERE id = :lwp2",
                wrapper.buildSql());
    }

    @Test
    @DisplayName("默认应禁止无条件全表更新")
    void shouldRejectFullTableUpdateByDefault() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> wrapper().set(TestUser::getDeleteFlag, 1).buildSql());
        assertEquals("lambdaUpdate 默认禁止无条件全表更新，如确有需要请显式调用 allowFullTable()", ex.getMessage());
    }

    @Test
    @DisplayName("allowFullTable 后允许显式全表更新")
    void shouldAllowFullTableUpdateWhenExplicitlyRequested() {
        LambdaUpdateWrapper<TestUser> wrapper = wrapper()
                .set(TestUser::getDeleteFlag, 1)
                .allowFullTable();
        assertEquals("UPDATE user SET delete_flag = :lwp0", wrapper.buildSql());
    }

    @Test
    @DisplayName("update() 应委托给 IBaseDao.updateForSql")
    void shouldDelegateUpdateExecutionToBaseDao() {
        AtomicReference<String> capturedSql = new AtomicReference<>();
        AtomicReference<Map<String, Object>> capturedParams = new AtomicReference<>();
        AtomicReference<Class<?>> capturedClass = new AtomicReference<>();

        IBaseDao dao = (IBaseDao) Proxy.newProxyInstance(
                IBaseDao.class.getClassLoader(),
                new Class[]{IBaseDao.class},
                (proxy, method, args) -> {
                    if ("updateForSql".equals(method.getName())) {
                        capturedSql.set((String) args[0]);
                        capturedParams.set((Map<String, Object>) args[1]);
                        capturedClass.set((Class<?>) args[2]);
                        return 3;
                    }
                    if (method.getReturnType() == int.class) {
                        return 0;
                    }
                    return null;
                });

        int rows = new LambdaUpdateWrapper<>(TestUser.class, dao)
                .set(TestUser::getDeleteFlag, 0)
                .eq(TestUser::getId, 100L)
                .update();

        assertEquals(3, rows);
        assertEquals("UPDATE user SET delete_flag = :lwp0 WHERE id = :lwp1", capturedSql.get());
        assertInstanceOf(Map.class, capturedParams.get());
        assertEquals(TestUser.class, capturedClass.get());
    }

    @Test
    @DisplayName("allowFullTable.update() 应在执行期显式放开危险写操作")
    void shouldEnableUnsafeWriteScopeWhenAllowFullTable() {
        AtomicReference<Boolean> unsafeWrite = new AtomicReference<>(false);

        IBaseDao dao = (IBaseDao) Proxy.newProxyInstance(
                IBaseDao.class.getClassLoader(),
                new Class[]{IBaseDao.class},
                (proxy, method, args) -> {
                    if ("updateForSql".equals(method.getName())) {
                        unsafeWrite.set(MyJdbcScope.isUnsafeWriteAllowed());
                        return 1;
                    }
                    return null;
                });

        int rows = new LambdaUpdateWrapper<>(TestUser.class, dao)
                .set(TestUser::getDeleteFlag, 1)
                .allowFullTable()
                .update();

        assertEquals(1, rows);
        assertTrue(unsafeWrite.get(), "allowFullTable.update() 执行期应放开危险写操作");
        assertFalse(MyJdbcScope.isUnsafeWriteAllowed(), "执行结束后危险写作用域应恢复");
    }

    @Test
    @DisplayName("allTenants.update() 应在执行期临时关闭租户限制")
    void shouldEnableAllTenantsScopeOnUpdate() {
        AtomicReference<Boolean> tenantSkipped = new AtomicReference<>(false);

        IBaseDao dao = (IBaseDao) Proxy.newProxyInstance(
                IBaseDao.class.getClassLoader(),
                new Class[]{IBaseDao.class},
                (proxy, method, args) -> {
                    if ("updateForSql".equals(method.getName())) {
                        tenantSkipped.set(MyJdbcScope.isTenantSkipped());
                        return 1;
                    }
                    return null;
                });

        new LambdaUpdateWrapper<>(TestUser.class, dao)
                .set(TestUser::getDeleteFlag, 0)
                .eq(TestUser::getId, 100L)
                .allTenants()
                .update();

        assertTrue(tenantSkipped.get(), "allTenants.update() 执行期应关闭租户隔离");
        assertFalse(MyJdbcScope.isTenantSkipped(), "执行结束后租户作用域应恢复");
    }
}
