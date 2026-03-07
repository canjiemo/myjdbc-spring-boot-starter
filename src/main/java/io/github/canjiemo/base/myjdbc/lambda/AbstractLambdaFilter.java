package io.github.canjiemo.base.myjdbc.lambda;

import io.github.canjiemo.base.myjdbc.MyTableEntity;

import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;

abstract class AbstractLambdaFilter<T extends MyTableEntity, SELF extends AbstractLambdaFilter<T, SELF>>
        implements LambdaFilter<T> {

    protected final Class<T> entityClazz;
    protected final LambdaConditionBuilder<T> conditionBuilder;

    protected AbstractLambdaFilter(Class<T> entityClazz, LambdaConditionBuilder<T> conditionBuilder) {
        this.entityClazz = entityClazz;
        this.conditionBuilder = conditionBuilder;
    }

    protected abstract SELF self();

    protected String col(SFunction<T, ?> fn) {
        return conditionBuilder.column(fn);
    }

    @Override
    public SELF eq(SFunction<T, ?> fn, Object val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " = " + conditionBuilder.bindValue(val));
        return self();
    }

    @Override
    public SELF ne(SFunction<T, ?> fn, Object val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " != " + conditionBuilder.bindValue(val));
        return self();
    }

    @Override
    public SELF gt(SFunction<T, ?> fn, Object val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " > " + conditionBuilder.bindValue(val));
        return self();
    }

    @Override
    public SELF ge(SFunction<T, ?> fn, Object val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " >= " + conditionBuilder.bindValue(val));
        return self();
    }

    @Override
    public SELF lt(SFunction<T, ?> fn, Object val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " < " + conditionBuilder.bindValue(val));
        return self();
    }

    @Override
    public SELF le(SFunction<T, ?> fn, Object val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " <= " + conditionBuilder.bindValue(val));
        return self();
    }

    @Override
    public SELF like(SFunction<T, ?> fn, String val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " LIKE " + conditionBuilder.bindValue("%" + val + "%"));
        return self();
    }

    @Override
    public SELF likeLeft(SFunction<T, ?> fn, String val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " LIKE " + conditionBuilder.bindValue("%" + val));
        return self();
    }

    @Override
    public SELF likeRight(SFunction<T, ?> fn, String val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " LIKE " + conditionBuilder.bindValue(val + "%"));
        return self();
    }

    @Override
    public SELF in(SFunction<T, ?> fn, Collection<?> vals) {
        if (conditionBuilder.isEmptyValue(vals)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " IN (" + conditionBuilder.bindValue(vals) + ")");
        return self();
    }

    @Override
    public SELF notIn(SFunction<T, ?> fn, Collection<?> vals) {
        if (conditionBuilder.isEmptyValue(vals)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " NOT IN (" + conditionBuilder.bindValue(vals) + ")");
        return self();
    }

    @Override
    public SELF between(SFunction<T, ?> fn, Object v1, Object v2) {
        if (conditionBuilder.isEmptyValue(v1) || conditionBuilder.isEmptyValue(v2)) {
            return self();
        }
        conditionBuilder.addCondition(col(fn) + " BETWEEN "
                + conditionBuilder.bindValue(v1) + " AND " + conditionBuilder.bindValue(v2));
        return self();
    }

    @Override
    public SELF isNull(SFunction<T, ?> fn) {
        conditionBuilder.addCondition(col(fn) + " IS NULL");
        return self();
    }

    @Override
    public SELF isNotNull(SFunction<T, ?> fn) {
        conditionBuilder.addCondition(col(fn) + " IS NOT NULL");
        return self();
    }

    @Override
    public SELF or() {
        conditionBuilder.nextOr();
        return self();
    }

    @Override
    public SELF and(Consumer<LambdaFilter<T>> consumer) {
        conditionBuilder.addNestedGroup(null, consumer);
        return self();
    }

    @Override
    public SELF or(Consumer<LambdaFilter<T>> consumer) {
        conditionBuilder.addNestedGroup("OR", consumer);
        return self();
    }

    @Override
    @SafeVarargs
    public final SELF any(Consumer<LambdaFilter<T>>... consumers) {
        conditionBuilder.addComposedGroup("OR", consumers);
        return self();
    }

    @Override
    @SafeVarargs
    public final SELF all(Consumer<LambdaFilter<T>>... consumers) {
        conditionBuilder.addComposedGroup("AND", consumers);
        return self();
    }

    @Override
    public SELF when(boolean condition, Consumer<LambdaFilter<T>> consumer) {
        if (condition && consumer != null) {
            consumer.accept(self());
        }
        return self();
    }

    @Override
    public SELF exists(String subQuery) {
        return exists(subQuery, Map.of());
    }

    @Override
    public SELF exists(String subQuery, Map<String, ?> params) {
        String bound = conditionBuilder.bindFragment(subQuery, params, "EXISTS 子查询");
        conditionBuilder.addCondition("EXISTS (" + bound + ")");
        return self();
    }

    @Override
    public SELF notExists(String subQuery) {
        return notExists(subQuery, Map.of());
    }

    @Override
    public SELF notExists(String subQuery, Map<String, ?> params) {
        String bound = conditionBuilder.bindFragment(subQuery, params, "NOT EXISTS 子查询");
        conditionBuilder.addCondition("NOT EXISTS (" + bound + ")");
        return self();
    }
}
