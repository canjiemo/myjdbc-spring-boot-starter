package io.github.canjiemo.base.myjdbc.lambda;

import io.github.canjiemo.base.myjdbc.MyTableEntity;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lambda 条件构造器接口。
 *
 * <p>用于统一查询与更新两条 DSL 路径的条件体验，支持链式调用。
 *
 * <p><b>基本用法示例：</b>
 * <pre>{@code
 * // 查询年龄等于 18、姓名不等于"张三"、且状态在列表中的用户
 * userService.lambdaQuery()
 *     .eq(User::getStatus, 1)
 *     .ne(User::getName, "张三")
 *     .in(User::getAge, List.of(18, 20, 25))
 *     .list();
 * }</pre>
 */
public interface LambdaFilter<T extends MyTableEntity> {

    /**
     * 等于（column = val）。
     *
     * <pre>{@code
     * // WHERE status = 1
     * .eq(User::getStatus, 1)
     * }</pre>
     */
    LambdaFilter<T> eq(SFunction<T, ?> fn, Object val);

    /**
     * 不等于（column != val）。
     *
     * <pre>{@code
     * // WHERE name != '张三'
     * .ne(User::getName, "张三")
     * }</pre>
     */
    LambdaFilter<T> ne(SFunction<T, ?> fn, Object val);

    /**
     * 大于（column &gt; val）。
     *
     * <pre>{@code
     * // WHERE age > 18
     * .gt(User::getAge, 18)
     * }</pre>
     */
    LambdaFilter<T> gt(SFunction<T, ?> fn, Object val);

    /**
     * 大于等于（column &gt;= val）。
     *
     * <pre>{@code
     * // WHERE age >= 18
     * .ge(User::getAge, 18)
     * }</pre>
     */
    LambdaFilter<T> ge(SFunction<T, ?> fn, Object val);

    /**
     * 小于（column &lt; val）。
     *
     * <pre>{@code
     * // WHERE age < 60
     * .lt(User::getAge, 60)
     * }</pre>
     */
    LambdaFilter<T> lt(SFunction<T, ?> fn, Object val);

    /**
     * 小于等于（column &lt;= val）。
     *
     * <pre>{@code
     * // WHERE age <= 60
     * .le(User::getAge, 60)
     * }</pre>
     */
    LambdaFilter<T> le(SFunction<T, ?> fn, Object val);

    /**
     * 模糊匹配，关键词两侧加 %（column LIKE '%val%'）。
     *
     * <pre>{@code
     * // WHERE name LIKE '%张%'
     * .like(User::getName, "张")
     * }</pre>
     */
    LambdaFilter<T> like(SFunction<T, ?> fn, String val);

    /**
     * 左模糊匹配，关键词左侧加 %（column LIKE '%val'）。
     *
     * <pre>{@code
     * // WHERE name LIKE '%三'
     * .likeLeft(User::getName, "三")
     * }</pre>
     */
    LambdaFilter<T> likeLeft(SFunction<T, ?> fn, String val);

    /**
     * 右模糊匹配，关键词右侧加 %（column LIKE 'val%'）。
     *
     * <pre>{@code
     * // WHERE name LIKE '张%'
     * .likeRight(User::getName, "张")
     * }</pre>
     */
    LambdaFilter<T> likeRight(SFunction<T, ?> fn, String val);

    /**
     * 包含（column IN (val1, val2, ...)）。
     *
     * <pre>{@code
     * // WHERE age IN (18, 20, 25)
     * .in(User::getAge, List.of(18, 20, 25))
     * }</pre>
     */
    LambdaFilter<T> in(SFunction<T, ?> fn, Collection<?> vals);

    /**
     * 不包含（column NOT IN (val1, val2, ...)）。
     *
     * <pre>{@code
     * // WHERE status NOT IN (0, 9)
     * .notIn(User::getStatus, List.of(0, 9))
     * }</pre>
     */
    LambdaFilter<T> notIn(SFunction<T, ?> fn, Collection<?> vals);

    /**
     * 区间查询（column BETWEEN v1 AND v2）。
     *
     * <pre>{@code
     * // WHERE age BETWEEN 18 AND 60
     * .between(User::getAge, 18, 60)
     * }</pre>
     */
    LambdaFilter<T> between(SFunction<T, ?> fn, Object v1, Object v2);

    /**
     * 为空（column IS NULL）。
     *
     * <pre>{@code
     * // WHERE email IS NULL
     * .isNull(User::getEmail)
     * }</pre>
     */
    LambdaFilter<T> isNull(SFunction<T, ?> fn);

    /**
     * 不为空（column IS NOT NULL）。
     *
     * <pre>{@code
     * // WHERE email IS NOT NULL
     * .isNotNull(User::getEmail)
     * }</pre>
     */
    LambdaFilter<T> isNotNull(SFunction<T, ?> fn);

    /**
     * 将下一个条件用 OR 连接（默认条件之间为 AND）。
     *
     * <pre>{@code
     * // WHERE status = 1 OR status = 2
     * .eq(User::getStatus, 1)
     * .or()
     * .eq(User::getStatus, 2)
     * }</pre>
     */
    LambdaFilter<T> or();

    /**
     * AND 分组，括号内的条件整体作为一个 AND 子句。
     *
     * <pre>{@code
     * // WHERE status = 1 AND (age > 18 AND age < 60)
     * .eq(User::getStatus, 1)
     * .and(q -> q.gt(User::getAge, 18).lt(User::getAge, 60))
     * }</pre>
     */
    LambdaFilter<T> and(Consumer<LambdaFilter<T>> consumer);

    /**
     * OR 分组，括号内的条件整体作为一个 OR 子句。
     *
     * <pre>{@code
     * // WHERE status = 1 OR (name = '张三' AND age = 18)
     * .eq(User::getStatus, 1)
     * .or(q -> q.eq(User::getName, "张三").eq(User::getAge, 18))
     * }</pre>
     */
    LambdaFilter<T> or(Consumer<LambdaFilter<T>> consumer);

    /**
     * 任意匹配（OR 组合），多个子条件组之间用 OR 连接。
     *
     * <pre>{@code
     * // WHERE (status = 1) OR (status = 2 AND name = '张三')
     * .any(
     *     q -> q.eq(User::getStatus, 1),
     *     q -> q.eq(User::getStatus, 2).eq(User::getName, "张三")
     * )
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    LambdaFilter<T> any(Consumer<LambdaFilter<T>>... consumers);

    /**
     * 全部匹配（AND 组合），多个子条件组之间用 AND 连接。
     *
     * <pre>{@code
     * // WHERE (status = 1) AND (age > 18)
     * .all(
     *     q -> q.eq(User::getStatus, 1),
     *     q -> q.gt(User::getAge, 18)
     * )
     * }</pre>
     */
    @SuppressWarnings("unchecked")
    LambdaFilter<T> all(Consumer<LambdaFilter<T>>... consumers);

    /**
     * 条件控制：仅当 condition 为 true 时才追加括号内的条件，避免在业务层写 if 判断。
     *
     * <pre>{@code
     * String keyword = request.getKeyword(); // 可能为 null
     * // 只有 keyword 不为空时才追加 LIKE 条件
     * .when(keyword != null, q -> q.like(User::getName, keyword))
     * }</pre>
     */
    LambdaFilter<T> when(boolean condition, Consumer<LambdaFilter<T>> consumer);

    /**
     * EXISTS 子查询（EXISTS (subQuery)）。
     *
     * <pre>{@code
     * // WHERE EXISTS (SELECT 1 FROM role WHERE role.user_id = user.id)
     * .exists("SELECT 1 FROM role WHERE role.user_id = user.id")
     * }</pre>
     */
    LambdaFilter<T> exists(String subQuery);

    /**
     * EXISTS 子查询（带命名参数）。
     *
     * <pre>{@code
     * // WHERE EXISTS (SELECT 1 FROM role WHERE role.user_id = user.id AND role.code = :roleCode)
     * .exists("SELECT 1 FROM role WHERE role.user_id = user.id AND role.code = :roleCode",
     *         Map.of("roleCode", "ADMIN"))
     * }</pre>
     */
    LambdaFilter<T> exists(String subQuery, Map<String, ?> params);

    /**
     * NOT EXISTS 子查询（NOT EXISTS (subQuery)）。
     *
     * <pre>{@code
     * // WHERE NOT EXISTS (SELECT 1 FROM order WHERE order.user_id = user.id)
     * .notExists("SELECT 1 FROM order WHERE order.user_id = user.id")
     * }</pre>
     */
    LambdaFilter<T> notExists(String subQuery);

    /**
     * NOT EXISTS 子查询（带命名参数）。
     *
     * <pre>{@code
     * // WHERE NOT EXISTS (SELECT 1 FROM order WHERE order.user_id = user.id AND order.status = :status)
     * .notExists("SELECT 1 FROM order WHERE order.user_id = user.id AND order.status = :status",
     *            Map.of("status", 0))
     * }</pre>
     */
    LambdaFilter<T> notExists(String subQuery, Map<String, ?> params);
}
