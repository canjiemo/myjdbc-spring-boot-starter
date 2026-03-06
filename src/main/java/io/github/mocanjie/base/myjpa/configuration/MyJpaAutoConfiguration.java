package io.github.mocanjie.base.myjpa.configuration;

import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.dao.impl.BaseDaoImpl;
import io.github.mocanjie.base.myjpa.service.IBaseService;
import io.github.mocanjie.base.myjpa.service.impl.BaseServiceImpl;
import io.github.mocanjie.base.myjpa.validation.DatabaseSchemaValidator;
import io.github.mocanjie.base.myjpa.validation.SchemaValidationRunner;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(MyJpaProperties.class)
public class MyJpaAutoConfiguration implements BeanPostProcessor, Ordered {
    private static final Logger log = LoggerFactory.getLogger(MyJpaAutoConfiguration.class);

    private final MyJpaProperties properties;
    private final Environment environment;

    public MyJpaAutoConfiguration(MyJpaProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Bean
    @Primary
    public IBaseService getBaseService(){
        return new BaseServiceImpl();
    }

    @Bean
    @Primary
    public TableInfoBuilder getTableInfoBuilder(){
        return new TableInfoBuilder();
    }

    @Bean
    @Primary
    public IBaseDao getBaseDaoImpl(MyJpaProperties properties){
        return new BaseDaoImpl(properties);
    }

    @Bean
    @Primary
    @ConditionalOnClass(DataSource.class)
    public SqlBuilder getSqlBuilder(){
        return new SqlBuilder();
    }
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(name = "myjpa.validate-schema", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    public DatabaseSchemaValidator getDatabaseSchemaValidator(JdbcTemplate jdbcTemplate, DataSource dataSource,
                                                              MyJpaProperties properties){
        return new DatabaseSchemaValidator(jdbcTemplate, dataSource, properties);
    }
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    @ConditionalOnProperty(name = "myjpa.validate-schema", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    public SchemaValidationRunner getSchemaValidationRunner(MyJpaProperties properties){
        return new SchemaValidationRunner(properties);
    }


    @PostConstruct
    void logInit(){
        applyLegacyPropertyAliases();
        normalizeProperties();
        // 通过 myjpa 配置控制 SQL 日志级别（由应用方决定是否开启）
        if (properties.getShowSql().isEnabled()) {
            applySqlLogLevels();
        }
    }

    private void applySqlLogLevels() {
        try {
            LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());
            LogLevel sqlLevel = LogLevel.valueOf(properties.getShowSql().getSqlLevel().trim().toUpperCase());
            LogLevel paramLevel = LogLevel.valueOf(properties.getShowSql().getParamLevel().trim().toUpperCase());
            loggingSystem.setLogLevel("org.springframework.jdbc.core.JdbcTemplate", sqlLevel);
            loggingSystem.setLogLevel("org.springframework.jdbc.core.StatementCreatorUtils", paramLevel);
        } catch (Exception e) {
            log.warn("应用 myjpa.show-sql 日志级别失败: {}", e.getMessage());
        }
    }

    private void applyLegacyPropertyAliases() {
        if (!environment.containsProperty("myjpa.show-sql.enabled")) {
            Boolean legacyEnabled = firstNonNull(
                    environment.getProperty("myjpa.show-sql", Boolean.class),
                    environment.getProperty("myjpa.showsql.enabled", Boolean.class),
                    environment.getProperty("myjpa.showsql", Boolean.class));
            if (legacyEnabled != null) {
                properties.getShowSql().setEnabled(legacyEnabled);
                log.debug("检测到旧配置 myjpa.showsql*，已映射到 myjpa.show-sql.enabled");
            }
        }

        if (!environment.containsProperty("myjpa.show-sql.sql-level")) {
            String legacySqlLevel = environment.getProperty("myjpa.showsql.sql-level");
            if (legacySqlLevel != null && !legacySqlLevel.isBlank()) {
                properties.getShowSql().setSqlLevel(legacySqlLevel.trim());
            }
        }

        if (!environment.containsProperty("myjpa.show-sql.param-level")) {
            String legacyParamLevel = environment.getProperty("myjpa.showsql.param-level");
            if (legacyParamLevel != null && !legacyParamLevel.isBlank()) {
                properties.getShowSql().setParamLevel(legacyParamLevel.trim());
            }
        }
    }

    private void normalizeProperties() {
        properties.getShowSql().setSqlLevel(normalizeText(properties.getShowSql().getSqlLevel(), "DEBUG"));
        properties.getShowSql().setParamLevel(normalizeText(properties.getShowSql().getParamLevel(), "TRACE"));
        properties.getTenant().setColumn(normalizeText(properties.getTenant().getColumn(), "tenant_id"));
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalizeText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }


    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
