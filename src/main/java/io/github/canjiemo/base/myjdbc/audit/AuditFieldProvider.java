package io.github.canjiemo.base.myjdbc.audit;

/**
 * 审计字段操作人提供者接口（SPI）。
 *
 * <p>集成方实现此接口并注册为 Spring Bean，myjdbc 将在 INSERT/UPDATE 前自动调用，
 * 填充标注了 {@code @MyField(fill=AuditFill.CREATE_BY)} 或
 * {@code @MyField(fill=AuditFill.UPDATE_BY)} 的字段。
 *
 * <p>示例：
 * <pre>{@code
 * @Bean
 * public AuditFieldProvider auditFieldProvider() {
 *     return () -> SecurityContextHolder.getContext().getAuthentication().getName();
 * }
 * }</pre>
 *
 * <p>返回 {@code null} 时跳过操作人填充（匿名/系统操作）。
 */
public interface AuditFieldProvider {

    /**
     * 获取当前操作人 ID（或用户名）。
     *
     * @return 操作人标识，返回 {@code null} 表示跳过填充
     */
    Object getCurrentUserId();
}
