package io.github.canjiemo.base.myjdbc.lambda;

import io.github.canjiemo.base.myjdbc.MyTableEntity;

final class LambdaNestedFilter<T extends MyTableEntity> extends AbstractLambdaFilter<T, LambdaNestedFilter<T>> {

    LambdaNestedFilter(Class<T> entityClazz, LambdaConditionBuilder<T> conditionBuilder) {
        super(entityClazz, conditionBuilder);
    }

    @Override
    protected LambdaNestedFilter<T> self() {
        return this;
    }
}
