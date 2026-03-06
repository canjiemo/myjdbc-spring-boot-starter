package io.github.canjiemo.base.myjdbc.configuration;

import io.github.canjiemo.base.myjdbc.builder.SqlBuilder;
import io.github.canjiemo.base.myjdbc.builder.TableInfoBuilder;
import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.dao.IBaseDao;
import io.github.canjiemo.base.myjdbc.dao.impl.BaseDaoImpl;
import io.github.canjiemo.base.myjdbc.service.IBaseService;
import io.github.canjiemo.base.myjdbc.service.impl.BaseServiceImpl;
import io.github.canjiemo.base.myjdbc.validation.DatabaseSchemaValidator;
import io.github.canjiemo.base.myjdbc.validation.SchemaValidationRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MyJdbcAutoConfiguration 配置归一化测试")
class MyJdbcAutoConfigurationTest {

    @Test
    @DisplayName("旧配置别名应映射到新配置模型")
    void legacyShowSqlAliasesShouldBeApplied() {
        MyJdbcProperties properties = new MyJdbcProperties();
        properties.getTenant().setColumn(" org_id ");
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> values = new HashMap<>();
        values.put("myjdbc.showsql", "false");
        values.put("myjdbc.showsql.sql-level", " info ");
        values.put("myjdbc.showsql.param-level", " trace ");
        environment.getPropertySources().addFirst(new MapPropertySource("test", values));

        MyJdbcAutoConfiguration autoConfiguration = new MyJdbcAutoConfiguration(properties, environment);
        autoConfiguration.logInit();

        assertFalse(properties.getShowSql().isEnabled());
        assertEquals("info", properties.getShowSql().getSqlLevel());
        assertEquals("trace", properties.getShowSql().getParamLevel());
        assertEquals("org_id", properties.getTenant().getColumn());
    }

    @Test
    @DisplayName("用户自定义 Bean 应覆盖默认自动配置 Bean")
    void userBeansShouldOverrideDefaults() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        Map<String, Object> values = new HashMap<>();
        values.put("myjdbc.validate-schema", "false");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", values));

        context.registerBean(DataSource.class, MyJdbcAutoConfigurationTest::stubDataSource);
        context.registerBean("customBaseDao", IBaseDao.class, () -> proxyOf(IBaseDao.class));
        context.registerBean("customBaseService", IBaseService.class, () -> proxyOf(IBaseService.class));
        context.registerBean("customTableInfoBuilder", TableInfoBuilder.class, TableInfoBuilder::new);
        context.registerBean("customSqlBuilder", SqlBuilder.class, SqlBuilder::new);
        context.register(MyJdbcAutoConfiguration.class);

        context.refresh();
        try {
            assertSame(context.getBean("customBaseDao"), context.getBean(IBaseDao.class));
            assertSame(context.getBean("customBaseService"), context.getBean(IBaseService.class));
            assertSame(context.getBean("customTableInfoBuilder"), context.getBean(TableInfoBuilder.class));
            assertSame(context.getBean("customSqlBuilder"), context.getBean(SqlBuilder.class));

            assertEquals(1, context.getBeansOfType(IBaseDao.class).size());
            assertEquals(1, context.getBeansOfType(IBaseService.class).size());
            assertEquals(1, context.getBeansOfType(TableInfoBuilder.class).size());
            assertEquals(1, context.getBeansOfType(SqlBuilder.class).size());
        } finally {
            context.close();
            resetTableInfoBuilderState();
        }
    }

    @Test
    @DisplayName("默认 Bean 工厂方法应声明为可覆盖")
    void defaultBeanFactoryMethodsShouldBeConditionalOnMissingBean() throws Exception {
        assertConditionalOnMissingBean("baseService", IBaseDao.class, JdbcTemplate.class, NamedParameterJdbcTemplate.class);
        assertConditionalOnMissingBean("tableInfoBuilder");
        assertConditionalOnMissingBean("baseDao", MyJdbcProperties.class, NamedParameterJdbcTemplate.class);
        assertConditionalOnMissingBean("sqlBuilder");
        assertConditionalOnMissingBean("databaseSchemaValidator", JdbcTemplate.class, DataSource.class, MyJdbcProperties.class);
        assertConditionalOnMissingBean("schemaValidationRunner", MyJdbcProperties.class, DatabaseSchemaValidator.class);
    }

    @Test
    @DisplayName("默认实现类不应再依赖组件扫描注册")
    void defaultImplementationsShouldNotUseComponentScanningAnnotations() {
        assertFalse(BaseDaoImpl.class.isAnnotationPresent(Component.class));
        assertFalse(BaseServiceImpl.class.isAnnotationPresent(Service.class));
        assertFalse(SchemaValidationRunner.class.isAnnotationPresent(Component.class));
    }

    private static void assertConditionalOnMissingBean(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = MyJdbcAutoConfiguration.class.getDeclaredMethod(methodName, parameterTypes);
        assertTrue(method.isAnnotationPresent(ConditionalOnMissingBean.class),
                () -> methodName + " 应声明 @ConditionalOnMissingBean");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxyOf(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[]{type},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        return null;
    }

    private static DataSource stubDataSource() {
        DatabaseMetaData metaData = (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class[]{DatabaseMetaData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDatabaseProductName" -> "MySQL";
                    case "getDatabaseProductVersion" -> "8.0";
                    default -> defaultValue(method.getReturnType());
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMetaData" -> metaData;
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                });

        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class[]{DataSource.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getConnection" -> connection;
                    default -> defaultValue(method.getReturnType());
                });
    }

    @SuppressWarnings("unchecked")
    private static void resetTableInfoBuilderState() {
        try {
            Field tableInfoMapField = TableInfoBuilder.class.getDeclaredField("tableInfoMap");
            tableInfoMapField.setAccessible(true);
            ((Map<Class<?>, ?>) tableInfoMapField.get(null)).clear();

            Field missingLoggedField = TableInfoBuilder.class.getDeclaredField("missingTableInfoLogged");
            missingLoggedField.setAccessible(true);
            ((java.util.Set<String>) missingLoggedField.get(null)).clear();
        } catch (Exception e) {
            throw new AssertionError("清理 TableInfoBuilder 静态状态失败", e);
        }
        TableCacheManager.clearCache();
    }
}
