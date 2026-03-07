package io.github.canjiemo.base.myjdbc.lambda;

import io.github.canjiemo.base.myjdbc.MyTableEntity;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Lambda 条件作用域。
 *
 * <p>用于统一查询与更新两条 DSL 路径的条件体验。
 */
public interface LambdaFilter<T extends MyTableEntity> {

    LambdaFilter<T> eq(SFunction<T, ?> fn, Object val);

    LambdaFilter<T> ne(SFunction<T, ?> fn, Object val);

    LambdaFilter<T> gt(SFunction<T, ?> fn, Object val);

    LambdaFilter<T> ge(SFunction<T, ?> fn, Object val);

    LambdaFilter<T> lt(SFunction<T, ?> fn, Object val);

    LambdaFilter<T> le(SFunction<T, ?> fn, Object val);

    LambdaFilter<T> like(SFunction<T, ?> fn, String val);

    LambdaFilter<T> likeLeft(SFunction<T, ?> fn, String val);

    LambdaFilter<T> likeRight(SFunction<T, ?> fn, String val);

    LambdaFilter<T> in(SFunction<T, ?> fn, Collection<?> vals);

    LambdaFilter<T> notIn(SFunction<T, ?> fn, Collection<?> vals);

    LambdaFilter<T> between(SFunction<T, ?> fn, Object v1, Object v2);

    LambdaFilter<T> isNull(SFunction<T, ?> fn);

    LambdaFilter<T> isNotNull(SFunction<T, ?> fn);

    LambdaFilter<T> or();

    LambdaFilter<T> and(Consumer<LambdaFilter<T>> consumer);

    LambdaFilter<T> or(Consumer<LambdaFilter<T>> consumer);

    LambdaFilter<T> any(Consumer<LambdaFilter<T>>... consumers);

    LambdaFilter<T> all(Consumer<LambdaFilter<T>>... consumers);

    LambdaFilter<T> when(boolean condition, Consumer<LambdaFilter<T>> consumer);

    LambdaFilter<T> exists(String subQuery);

    LambdaFilter<T> exists(String subQuery, Map<String, ?> params);

    LambdaFilter<T> notExists(String subQuery);

    LambdaFilter<T> notExists(String subQuery, Map<String, ?> params);
}
