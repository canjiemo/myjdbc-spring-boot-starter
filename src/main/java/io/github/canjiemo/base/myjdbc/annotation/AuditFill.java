package io.github.canjiemo.base.myjdbc.annotation;

/**
 * 审计字段自动填充策略。
 *
 * <ul>
 *   <li>{@link #NONE}        — 不填充（默认）</li>
 *   <li>{@link #CREATE_TIME} — 仅 INSERT 时填充 {@code LocalDateTime.now()}</li>
 *   <li>{@link #UPDATE_TIME} — INSERT 和 UPDATE 时均填充 {@code LocalDateTime.now()}</li>
 *   <li>{@link #CREATE_BY}   — 仅 INSERT 时填充当前用户ID</li>
 *   <li>{@link #UPDATE_BY}   — INSERT 和 UPDATE 时均填充当前用户ID</li>
 * </ul>
 *
 * <p>所有策略：当字段已有非 null 值时跳过，不覆盖业务侧显式赋值。
 */
public enum AuditFill {
    NONE,
    /** 仅 INSERT 时填充 {@code LocalDateTime.now()} */
    CREATE_TIME,
    /** INSERT 和 UPDATE 时均填充 {@code LocalDateTime.now()}（注意：不仅限于 UPDATE） */
    UPDATE_TIME,
    /** 仅 INSERT 时填充当前用户ID */
    CREATE_BY,
    /** INSERT 和 UPDATE 时均填充当前用户ID */
    UPDATE_BY
}
