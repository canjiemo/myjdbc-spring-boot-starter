package io.github.canjiemo.base.myjdbc.configuration;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * 将 myjdbc 初始化失败转换为友好的启动错误报告。
 * <p>
 * 当 {@link BeanInitializationException} 消息中包含 "myjdbc" 时（即由
 * {@link io.github.canjiemo.base.myjdbc.builder.SqlBuilder} 检测数据库类型失败抛出），
 * Spring Boot 启动时会输出结构化的故障说明，而不是一段难以定位的堆栈。
 */
public class MyJdbcInitFailureAnalyzer extends AbstractFailureAnalyzer<BeanInitializationException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, BeanInitializationException cause) {
		String msg = cause.getMessage();
		if (msg == null || !msg.contains("myjdbc")) {
			return null;
		}
		return new FailureAnalysis(
				msg,
				"请检查数据库连接配置（spring.datasource.*）是否正确，并确认数据库服务可以正常访问。",
				cause);
	}
}
