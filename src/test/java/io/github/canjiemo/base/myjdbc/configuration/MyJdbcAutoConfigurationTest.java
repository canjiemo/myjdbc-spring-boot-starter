package io.github.canjiemo.base.myjdbc.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
