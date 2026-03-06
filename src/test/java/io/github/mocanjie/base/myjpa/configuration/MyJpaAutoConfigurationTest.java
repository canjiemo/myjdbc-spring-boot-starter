package io.github.mocanjie.base.myjpa.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("MyJpaAutoConfiguration 配置归一化测试")
class MyJpaAutoConfigurationTest {

    @Test
    @DisplayName("旧配置别名应映射到新配置模型")
    void legacyShowSqlAliasesShouldBeApplied() {
        MyJpaProperties properties = new MyJpaProperties();
        properties.getTenant().setColumn(" org_id ");
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> values = new HashMap<>();
        values.put("myjpa.showsql", "false");
        values.put("myjpa.showsql.sql-level", " info ");
        values.put("myjpa.showsql.param-level", " trace ");
        environment.getPropertySources().addFirst(new MapPropertySource("test", values));

        MyJpaAutoConfiguration autoConfiguration = new MyJpaAutoConfiguration(properties, environment);
        autoConfiguration.logInit();

        assertFalse(properties.getShowSql().isEnabled());
        assertEquals("info", properties.getShowSql().getSqlLevel());
        assertEquals("trace", properties.getShowSql().getParamLevel());
        assertEquals("org_id", properties.getTenant().getColumn());
    }
}
