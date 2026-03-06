package io.github.mocanjie.base.myjpa.validation;

import io.github.mocanjie.base.myjpa.cache.TableCacheManager;
import io.github.mocanjie.base.myjpa.configuration.MyJpaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 模式验证运行器
 * 在Bean初始化阶段执行数据库模式验证，确保最高优先级
 */
@Component
@ConditionalOnBean(DatabaseSchemaValidator.class)
@ConditionalOnProperty(name = "myjpa.validate-schema", havingValue = "true", matchIfMissing = true)
public class SchemaValidationRunner implements InitializingBean, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(SchemaValidationRunner.class);
    private final boolean failOnValidationError;
    
    @Autowired(required = false)
    private DatabaseSchemaValidator schemaValidator;

    public SchemaValidationRunner(MyJpaProperties properties) {
        this.failOnValidationError = properties != null && properties.isFailOnValidationError();
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        if (schemaValidator == null) {
            log.warn("DatabaseSchemaValidator未配置，跳过数据库模式验证");
            return;
        }
        
        // 检查是否有缓存的表信息
        if (TableCacheManager.getAllTableNames().isEmpty()) {
            log.info("没有找到@MyTable注解配置，跳过数据库模式验证");
            return;
        }
        
        log.info("开始执行数据库模式验证...");
        
        try {
            DatabaseSchemaValidator.ValidationResult result = schemaValidator.validateAllTables();
            
            if (result.hasErrors()) {
                log.error("数据库模式验证发现错误，建议检查@MyTable配置与实际数据库表结构是否一致");
                
                // 可选：根据配置决定是否抛出异常阻止启动
                // 默认情况下仅记录错误，不阻止启动
                if (failOnValidationError) {
                    throw new IllegalStateException("数据库模式验证失败，应用启动终止。可通过 myjpa.fail-on-validation-error=false 或 -Dmyjpa.fail-on-validation-error=false 禁用此行为");
                }
            } else {
                log.info("数据库模式验证通过 ✓");
            }
            
        } catch (Exception e) {
            log.error("执行数据库模式验证时发生异常", e);
            
            // 验证异常时的处理策略
            if (failOnValidationError) {
                throw e;
            }
        }
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
