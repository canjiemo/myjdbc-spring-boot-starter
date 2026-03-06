package io.github.canjiemo.base.myjdbc.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyJDBC 统一配置属性。
 */
@ConfigurationProperties(prefix = "myjdbc")
@Data
public class MyJdbcProperties {

    private final ShowSql showSql = new ShowSql();
    private boolean showSqlTime = false;
    private boolean validateSchema = true;
    private boolean failOnValidationError = false;
    private final Tenant tenant = new Tenant();

    @Data
    public static class ShowSql {
        private boolean enabled = false;
    }

    @Data
    public static class Tenant {
        private boolean enabled = false;
        private String column = "tenant_id";
    }
}
