package io.github.canjiemo.base.myjdbc.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(FIELD)
public @interface MyField {
    String value() default "";        // 对应数据库列名称
    boolean serialize() default true; // 持久化
    AuditFill fill() default AuditFill.NONE; // 审计字段自动填充策略
}
