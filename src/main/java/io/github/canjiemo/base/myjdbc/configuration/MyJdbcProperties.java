package io.github.canjiemo.base.myjdbc.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyJDBC 统一配置属性。
 */
@ConfigurationProperties(prefix = "myjdbc")
public class MyJdbcProperties {

    private final ShowSql showSql = new ShowSql();
    private boolean showSqlTime = false;
    private boolean validateSchema = true;
    private boolean failOnValidationError = false;
    private final Tenant tenant = new Tenant();

    public ShowSql getShowSql() {
        return showSql;
    }

    public boolean isShowSqlTime() {
        return showSqlTime;
    }

    public void setShowSqlTime(boolean showSqlTime) {
        this.showSqlTime = showSqlTime;
    }

    public boolean isValidateSchema() {
        return validateSchema;
    }

    public void setValidateSchema(boolean validateSchema) {
        this.validateSchema = validateSchema;
    }

    public boolean isFailOnValidationError() {
        return failOnValidationError;
    }

    public void setFailOnValidationError(boolean failOnValidationError) {
        this.failOnValidationError = failOnValidationError;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public static class ShowSql {
        private boolean enabled = false;
        private String sqlLevel = "DEBUG";
        private String paramLevel = "TRACE";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSqlLevel() {
            return sqlLevel;
        }

        public void setSqlLevel(String sqlLevel) {
            this.sqlLevel = sqlLevel;
        }

        public String getParamLevel() {
            return paramLevel;
        }

        public void setParamLevel(String paramLevel) {
            this.paramLevel = paramLevel;
        }
    }

    public static class Tenant {
        private boolean enabled = false;
        private String column = "tenant_id";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getColumn() {
            return column;
        }

        public void setColumn(String column) {
            this.column = column;
        }
    }
}
