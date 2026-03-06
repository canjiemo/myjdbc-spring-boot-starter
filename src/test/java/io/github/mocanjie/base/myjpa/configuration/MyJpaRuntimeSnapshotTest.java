package io.github.mocanjie.base.myjpa.configuration;

import io.github.mocanjie.base.myjpa.dao.impl.BaseDaoImpl;
import io.github.mocanjie.base.myjpa.validation.SchemaValidationRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MyJPA 运行时配置快照测试")
class MyJpaRuntimeSnapshotTest {

    @Test
    @DisplayName("BaseDaoImpl 应在构造时缓存高频配置")
    void baseDaoImplShouldSnapshotHotPathConfig() throws Exception {
        MyJpaProperties properties = new MyJpaProperties();
        properties.setShowSqlTime(true);
        properties.getTenant().setEnabled(true);
        properties.getTenant().setColumn(" org_id ");

        BaseDaoImpl dao = new BaseDaoImpl(properties);

        properties.setShowSqlTime(false);
        properties.getTenant().setEnabled(false);
        properties.getTenant().setColumn("tenant_id");

        assertTrue((Boolean) readField(dao, "showSqlTimeEnabled"));
        assertTrue((Boolean) readField(dao, "tenantIsolationEnabled"));
        assertEquals("org_id", readField(dao, "tenantColumn"));
        assertEquals("orgId", readField(dao, "tenantFieldName"));
    }

    @Test
    @DisplayName("SchemaValidationRunner 应在构造时缓存失败策略")
    void schemaValidationRunnerShouldSnapshotFailFastFlag() throws Exception {
        MyJpaProperties properties = new MyJpaProperties();
        properties.setFailOnValidationError(true);

        SchemaValidationRunner runner = new SchemaValidationRunner(properties);
        properties.setFailOnValidationError(false);

        assertTrue((Boolean) readField(runner, "failOnValidationError"));
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
