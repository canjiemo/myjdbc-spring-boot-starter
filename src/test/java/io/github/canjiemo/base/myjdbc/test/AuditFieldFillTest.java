package io.github.canjiemo.base.myjdbc.test;

import io.github.canjiemo.base.myjdbc.annotation.MyTable;
import io.github.canjiemo.base.myjdbc.audit.AuditFieldProvider;
import io.github.canjiemo.base.myjdbc.builder.DbType;
import io.github.canjiemo.base.myjdbc.builder.SqlBuilder;
import io.github.canjiemo.base.myjdbc.builder.TableInfoBuilder;
import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.configuration.MyJdbcProperties;
import io.github.canjiemo.base.myjdbc.dao.impl.BaseDaoImpl;
import io.github.canjiemo.base.myjdbc.metadata.TableInfo;
import io.github.canjiemo.base.myjdbc.test.entity.AuditableUser;
import io.github.canjiemo.base.myjdbc.utils.MyReflectionUtils;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审计字段自动填充功能测试
 *
 * 覆盖范围：
 *  - TableInfo 正确缓存审计字段列表
 *  - INSERT 时所有 null 字段被填充
 *  - INSERT 时已有值的字段不被覆盖
 *  - UPDATE 时只填充 UPDATE_TIME / UPDATE_BY
 *  - userId=null 时 BY 字段跳过，TIME 字段正常填充
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("审计字段自动填充功能测试")
class AuditFieldFillTest {

    private static BaseDaoImpl dao;
    private static Method fillAuditFieldsMethod;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setup() throws Exception {
        // 初始化 TableCacheManager（SQL 改写逻辑删除条件）
        TableCacheManager.initCache("io.github.canjiemo.base.myjdbc.test.entity");

        // 手动构建 AuditableUser 的 TableInfo 并填充审计字段缓存
        MyTable annotation = AuditableUser.class.getAnnotation(MyTable.class);
        Field pkField = MyReflectionUtils.findFieldInHierarchy(AuditableUser.class, annotation.pkField());
        TableInfo tableInfo = new TableInfo()
                .setTableName(annotation.value())
                .setClazz(AuditableUser.class)
                .setPkField(pkField)
                .setPkFieldName(annotation.pkField())
                .setPkColumnName(annotation.pkColumn())
                .setFieldList(MyReflectionUtils.getFieldList(AuditableUser.class))
                .setDelColumnName(annotation.delColumn())
                .setDelFieldName(annotation.delField())
                .setDelValue(annotation.delValue());

        // 调用 TableInfoBuilder.buildAuditFields 填充 auditInsertFields / auditUpdateFields
        TableInfoBuilder builder = new TableInfoBuilder();
        Method buildAudit = TableInfoBuilder.class.getDeclaredMethod("buildAuditFields", TableInfo.class);
        buildAudit.setAccessible(true);
        buildAudit.invoke(builder, tableInfo);

        // 将 TableInfo 注入静态 tableInfoMap
        Field mapField = TableInfoBuilder.class.getDeclaredField("tableInfoMap");
        mapField.setAccessible(true);
        ((Map<Class<?>, TableInfo>) mapField.get(null)).put(AuditableUser.class, tableInfo);

        // 创建 BaseDaoImpl（package-private 构造函数）
        MyJdbcProperties props = new MyJdbcProperties();
        props.getShowSql().setEnabled(false);
        SqlBuilder sqlBuilder = new SqlBuilder(DbType.MYSQL);
        dao = new BaseDaoImpl(props, sqlBuilder);

        // 获取 fillAuditFields 反射 Method
        fillAuditFieldsMethod = BaseDaoImpl.class.getDeclaredMethod(
                "fillAuditFields",
                io.github.canjiemo.base.myjdbc.MyTableEntity.class,
                List.class,
                LocalDateTime.class,
                Object.class);
        fillAuditFieldsMethod.setAccessible(true);
    }

    @AfterAll
    @SuppressWarnings("unchecked")
    static void teardown() throws Exception {
        Field mapField = TableInfoBuilder.class.getDeclaredField("tableInfoMap");
        mapField.setAccessible(true);
        ((Map<?, ?>) mapField.get(null)).remove(AuditableUser.class);
        TableCacheManager.clearCache();
    }

    /** 获取已缓存的 TableInfo（断言其存在）。 */
    private TableInfo tableInfo() {
        return TableInfoBuilder.getTableInfo(AuditableUser.class);
    }

    /** 调用 fillAuditFields 的便捷包装。 */
    private void callFill(AuditableUser po, List<Field> fields, LocalDateTime now, Object userId)
            throws Exception {
        fillAuditFieldsMethod.invoke(dao, po, fields, now, userId);
    }

    // =========================================================
    // 1. TableInfo 审计字段缓存验证
    // =========================================================

    @Test
    @Order(1)
    @DisplayName("1.1 auditInsertFields 包含全部 4 个审计字段")
    void tableInfoCachesAuditFields() {
        TableInfo info = tableInfo();

        List<Field> insertFields = info.getAuditInsertFields();
        List<Field> updateFields = info.getAuditUpdateFields();

        assertEquals(4, insertFields.size(),
                "INSERT 时应有 4 个审计字段（createTime/updateTime/createBy/updateBy）");
        assertEquals(2, updateFields.size(),
                "UPDATE 时应只有 2 个审计字段（updateTime/updateBy）");

        // createTime/createBy 不应出现在 updateFields 中
        List<String> updateFieldNames = updateFields.stream().map(Field::getName).toList();
        assertFalse(updateFieldNames.contains("createTime"),
                "createTime 不应在 auditUpdateFields 中");
        assertFalse(updateFieldNames.contains("createBy"),
                "createBy 不应在 auditUpdateFields 中");
        assertTrue(updateFieldNames.contains("updateTime"),
                "updateTime 应在 auditUpdateFields 中");
        assertTrue(updateFieldNames.contains("updateBy"),
                "updateBy 应在 auditUpdateFields 中");
    }

    // =========================================================
    // 2. INSERT — 全部 null 字段被填充
    // =========================================================

    @Test
    @Order(2)
    @DisplayName("2.1 INSERT 时所有 null 审计字段被填充")
    void insertFillsAllAuditFieldsWhenNull() throws Exception {
        AuditableUser po = new AuditableUser();
        po.setUsername("alice");

        LocalDateTime now = LocalDateTime.now();
        callFill(po, tableInfo().getAuditInsertFields(), now, 42L);

        assertNotNull(po.getCreateTime(), "createTime 应被填充");
        assertNotNull(po.getUpdateTime(), "updateTime 应被填充");
        assertEquals(42L, po.getCreateBy(), "createBy 应为 42");
        assertEquals(42L, po.getUpdateBy(), "updateBy 应为 42");
    }

    // =========================================================
    // 3. INSERT — 已有值不被覆盖
    // =========================================================

    @Test
    @Order(3)
    @DisplayName("3.1 INSERT 时已有值的字段不被覆盖")
    void insertDoesNotOverrideExistingValues() throws Exception {
        AuditableUser po = new AuditableUser();
        po.setUsername("bob");

        LocalDateTime fixedTime = LocalDateTime.of(2020, 1, 1, 0, 0, 0);
        po.setCreateTime(fixedTime);
        po.setCreateBy(99L);

        LocalDateTime now = LocalDateTime.now();
        callFill(po, tableInfo().getAuditInsertFields(), now, 42L);

        // 已有值不应被覆盖
        assertEquals(fixedTime, po.getCreateTime(), "预设的 createTime 不应被覆盖");
        assertEquals(99L, po.getCreateBy(), "预设的 createBy 不应被覆盖");

        // null 字段应被正常填充
        assertNotNull(po.getUpdateTime(), "updateTime（null）应被填充");
        assertEquals(42L, po.getUpdateBy(), "updateBy（null）应被填充为 42");
    }

    // =========================================================
    // 4. UPDATE — 只填充 UPDATE_TIME / UPDATE_BY
    // =========================================================

    @Test
    @Order(4)
    @DisplayName("4.1 UPDATE 时只填充 updateTime/updateBy，createTime/createBy 保持 null")
    void updateOnlyFillsUpdateFields() throws Exception {
        AuditableUser po = new AuditableUser();
        po.setUsername("carol");
        // createTime/createBy 保持 null

        LocalDateTime now = LocalDateTime.now();
        callFill(po, tableInfo().getAuditUpdateFields(), now, 42L);

        // UPDATE 字段被填充
        assertNotNull(po.getUpdateTime(), "updateTime 应被填充");
        assertEquals(42L, po.getUpdateBy(), "updateBy 应为 42");

        // CREATE 字段不应被触及（auditUpdateFields 不含它们）
        assertNull(po.getCreateTime(), "createTime 应保持 null（UPDATE 不填充）");
        assertNull(po.getCreateBy(), "createBy 应保持 null（UPDATE 不填充）");
    }

    // =========================================================
    // 5. userId=null — BY 字段跳过，TIME 字段正常填充
    // =========================================================

    @Test
    @Order(5)
    @DisplayName("5.1 userId=null 时 BY 字段跳过，TIME 字段正常填充")
    void noAuditProviderSkipsByFields() throws Exception {
        AuditableUser po = new AuditableUser();
        po.setUsername("dave");

        LocalDateTime now = LocalDateTime.now();
        // userId = null → BY 字段跳过
        callFill(po, tableInfo().getAuditInsertFields(), now, null);

        // TIME 字段应正常填充
        assertNotNull(po.getCreateTime(), "createTime 应被填充（TIME 字段不依赖 userId）");
        assertNotNull(po.getUpdateTime(), "updateTime 应被填充（TIME 字段不依赖 userId）");

        // BY 字段应保持 null
        assertNull(po.getCreateBy(), "userId=null 时 createBy 应保持 null");
        assertNull(po.getUpdateBy(), "userId=null 时 updateBy 应保持 null");
    }
}
