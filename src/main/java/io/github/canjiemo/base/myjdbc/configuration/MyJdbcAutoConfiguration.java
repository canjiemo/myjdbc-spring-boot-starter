package io.github.canjiemo.base.myjdbc.configuration;

import io.github.canjiemo.base.myjdbc.builder.SqlBuilder;
import io.github.canjiemo.base.myjdbc.builder.TableInfoBuilder;
import io.github.canjiemo.base.myjdbc.dao.IBaseDao;
import io.github.canjiemo.base.myjdbc.dao.impl.BaseDaoImpl;
import io.github.canjiemo.base.myjdbc.service.IBaseService;
import io.github.canjiemo.base.myjdbc.service.impl.BaseServiceImpl;
import io.github.canjiemo.base.myjdbc.validation.DatabaseSchemaValidator;
import io.github.canjiemo.base.myjdbc.validation.SchemaValidationRunner;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@AutoConfiguration
@AutoConfigureAfter(MyJdbcTemplateAutoConfiguration.class)
@EnableConfigurationProperties(MyJdbcProperties.class)
public class MyJdbcAutoConfiguration {
    private static final Logger log = LoggerFactory.getLogger(MyJdbcAutoConfiguration.class);

    private final MyJdbcProperties properties;
    private final Environment environment;

    public MyJdbcAutoConfiguration(MyJdbcProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Bean
    @ConditionalOnBean({JdbcTemplate.class, NamedParameterJdbcTemplate.class})
    @ConditionalOnMissingBean(IBaseService.class)
    public BaseServiceImpl baseService(IBaseDao baseDao, JdbcTemplate jdbcTemplate,
                                       NamedParameterJdbcTemplate namedParameterJdbcTemplate){
        return new BaseServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean(TableInfoBuilder.class)
    public TableInfoBuilder tableInfoBuilder(){
        return new TableInfoBuilder();
    }

    @Bean
    @ConditionalOnBean(NamedParameterJdbcTemplate.class)
    @ConditionalOnMissingBean(IBaseDao.class)
    public BaseDaoImpl baseDao(MyJdbcProperties properties, NamedParameterJdbcTemplate namedParameterJdbcTemplate){
        return new BaseDaoImpl(properties);
    }

    @Bean
    @ConditionalOnClass(DataSource.class)
    @ConditionalOnMissingBean(SqlBuilder.class)
    public SqlBuilder sqlBuilder(){
        return new SqlBuilder();
    }
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnBean({JdbcTemplate.class, DataSource.class})
    @ConditionalOnProperty(name = "myjdbc.validate-schema", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    @ConditionalOnMissingBean(DatabaseSchemaValidator.class)
    public DatabaseSchemaValidator databaseSchemaValidator(JdbcTemplate jdbcTemplate, DataSource dataSource,
                                                           MyJdbcProperties properties){
        return new DatabaseSchemaValidator(jdbcTemplate, dataSource, properties);
    }
    
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    @ConditionalOnBean(DatabaseSchemaValidator.class)
    @ConditionalOnProperty(name = "myjdbc.validate-schema", havingValue = "true", matchIfMissing = true)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    @ConditionalOnMissingBean(SchemaValidationRunner.class)
    public SchemaValidationRunner schemaValidationRunner(MyJdbcProperties properties,
                                                         DatabaseSchemaValidator databaseSchemaValidator){
        return new SchemaValidationRunner(properties);
    }


    @PostConstruct
    void logInit(){
        applyLegacyPropertyAliases();
        normalizeProperties();
    }

    private void applyLegacyPropertyAliases() {
        if (!environment.containsProperty("myjdbc.show-sql.enabled")) {
            Boolean legacyEnabled = firstNonNull(
                    environment.getProperty("myjdbc.show-sql", Boolean.class),
                    environment.getProperty("myjdbc.showsql.enabled", Boolean.class),
                    environment.getProperty("myjdbc.showsql", Boolean.class));
            if (legacyEnabled != null) {
                properties.getShowSql().setEnabled(legacyEnabled);
                log.debug("检测到旧配置 myjdbc.showsql*，已映射到 myjdbc.show-sql.enabled");
            }
        }
    }

    private void normalizeProperties() {
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
}
