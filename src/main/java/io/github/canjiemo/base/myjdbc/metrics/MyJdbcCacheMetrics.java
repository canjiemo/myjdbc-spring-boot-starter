package io.github.canjiemo.base.myjdbc.metrics;

import io.github.canjiemo.base.myjdbc.dao.impl.BaseDaoImpl;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * 将 myjdbc SQL 改写缓存统计数据绑定到 Micrometer 注册表。
 *
 * <p>暴露三个 Gauge 指标：
 * <ul>
 *   <li>{@code myjdbc.sql_rewrite_cache.size}   — 缓存当前条目数</li>
 *   <li>{@code myjdbc.sql_rewrite_cache.hits}   — 累计命中次数</li>
 *   <li>{@code myjdbc.sql_rewrite_cache.misses} — 累计未命中次数</li>
 * </ul>
 *
 * <p>仅在 classpath 存在 {@code micrometer-core} 且应用上下文中存在
 * {@link MeterRegistry} Bean 时，由
 * {@link io.github.canjiemo.base.myjdbc.configuration.MyJdbcMetricsAutoConfiguration}
 * 自动注册。
 */
public class MyJdbcCacheMetrics implements MeterBinder {

	private final BaseDaoImpl dao;

	public MyJdbcCacheMetrics(BaseDaoImpl dao) {
		this.dao = dao;
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		Gauge.builder("myjdbc.sql_rewrite_cache.size", dao, d -> d.getQueryRewriteCacheSize())
				.description("SQL改写缓存当前条目数")
				.register(registry);

		Gauge.builder("myjdbc.sql_rewrite_cache.hits", dao, d -> (double) d.getQueryRewriteCacheHits())
				.description("SQL改写缓存累计命中次数")
				.register(registry);

		Gauge.builder("myjdbc.sql_rewrite_cache.misses", dao, d -> (double) d.getQueryRewriteCacheMisses())
				.description("SQL改写缓存累计未命中次数")
				.register(registry);
	}
}
