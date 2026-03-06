package io.github.canjiemo.base.myjdbc.configuration;

import io.github.canjiemo.base.myjdbc.dao.impl.BaseDaoImpl;
import io.github.canjiemo.base.myjdbc.metrics.MyJdbcCacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 当 classpath 存在 {@code micrometer-core} 且应用上下文中有 {@link MeterRegistry} 时，
 * 自动将 myjdbc SQL 改写缓存指标绑定到 Micrometer。
 */
@AutoConfiguration
@AutoConfigureAfter(MyJdbcAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean({MeterRegistry.class, BaseDaoImpl.class})
public class MyJdbcMetricsAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(MyJdbcCacheMetrics.class)
	public MyJdbcCacheMetrics myJdbcCacheMetrics(MeterRegistry registry, BaseDaoImpl dao) {
		MyJdbcCacheMetrics metrics = new MyJdbcCacheMetrics(dao);
		metrics.bindTo(registry);
		return metrics;
	}
}
