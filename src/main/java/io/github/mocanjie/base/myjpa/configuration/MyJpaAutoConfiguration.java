package io.github.mocanjie.base.myjpa.configuration;

import io.github.mocanjie.base.myjpa.builder.SqlBuilder;
import io.github.mocanjie.base.myjpa.builder.TableInfoBuilder;
import io.github.mocanjie.base.myjpa.dao.IBaseDao;
import io.github.mocanjie.base.myjpa.dao.impl.BaseDaoImpl;
import io.github.mocanjie.base.myjpa.parser.JSqlDynamicSqlParser;
import io.github.mocanjie.base.myjpa.service.IBaseService;
import io.github.mocanjie.base.myjpa.service.impl.BaseServiceImpl;
import io.github.mocanjie.base.myjpa.validation.DatabaseSchemaValidator;
import io.github.mocanjie.base.myjpa.validation.SchemaValidationRunner;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class MyJpaAutoConfiguration implements BeanPostProcessor, Ordered {
    private static final Logger log = LoggerFactory.getLogger(MyJpaAutoConfiguration.class);

    @Value("${myjpa.show-sql.enabled:${myjpa.show-sql:${myjpa.showsql.enabled:${myjpa.showsql:false}}}}")
    public boolean showSql;

    @Value("${myjpa.show-sql.sql-level:${myjpa.showsql.sql-level:DEBUG}}")
    public String showSqlLevel;

    @Value("${myjpa.show-sql.param-level:${myjpa.showsql.param-level:TRACE}}")
    public String showSqlParamLevel;

    @Value("${myjpa.show-sql-time:false}")
    public boolean showSqlTime;

    @Value("${myjpa.validate-schema:true}")
    public boolean validateSchema;

    @Value("${myjpa.tenant.enabled:false}")
    public boolean tenantEnabled;

    @Value("${myjpa.tenant.column:tenant_id}")
    public String tenantColumn;

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
    public IBaseDao getBaseDaoImpl(){
        return new BaseDaoImpl();
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
    public DatabaseSchemaValidator getDatabaseSchemaValidator(JdbcTemplate jdbcTemplate, DataSource dataSource){
        return new DatabaseSchemaValidator(jdbcTemplate, dataSource);
    }
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    @ConditionalOnProperty(name = "myjpa.validate-schema", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    public SchemaValidationRunner getSchemaValidationRunner(){
        return new SchemaValidationRunner();
    }


    @PostConstruct
    void logInit(){
        // 将租户配置同步到解析器静态字段
        JSqlDynamicSqlParser.tenantEnabled = tenantEnabled;
        JSqlDynamicSqlParser.tenantColumn = tenantColumn;
        // 同步 SQL 执行时间打印开关
        BaseDaoImpl.showSqlTime = showSqlTime;
        // 通过 myjpa 配置控制 SQL 日志级别（由应用方决定是否开启）
        if (showSql) {
            applySqlLogLevels();
        }
    }

    private void applySqlLogLevels() {
        try {
            LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());
            LogLevel sqlLevel = LogLevel.valueOf(showSqlLevel.trim().toUpperCase());
            LogLevel paramLevel = LogLevel.valueOf(showSqlParamLevel.trim().toUpperCase());
            loggingSystem.setLogLevel("org.springframework.jdbc.core.JdbcTemplate", sqlLevel);
            loggingSystem.setLogLevel("org.springframework.jdbc.core.StatementCreatorUtils", paramLevel);
        } catch (Exception e) {
            log.warn("应用 myjpa.show-sql 日志级别失败: {}", e.getMessage());
        }
    }


    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
