package io.github.canjiemo.base.myjdbc.lambda;

import io.github.canjiemo.base.myjdbc.MyTableEntity;
import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.dao.IBaseDao;
import io.github.canjiemo.base.myjdbc.scope.MyJdbcScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lambda 链式更新构造器。
 *
 * <p>强调“条件更新”和“安全默认值”，默认禁止无条件全表更新。
 */
public class LambdaUpdateWrapper<T extends MyTableEntity> extends AbstractLambdaFilter<T, LambdaUpdateWrapper<T>> {

    private final IBaseDao baseDao;
    private final List<String> setClauses = new ArrayList<>();
    private boolean allowFullTable;
    private boolean acrossTenants;

    public LambdaUpdateWrapper(Class<T> entityClazz, IBaseDao baseDao) {
        super(entityClazz, new LambdaConditionBuilder<>(entityClazz));
        this.baseDao = baseDao;
    }

    @Override
    protected LambdaUpdateWrapper<T> self() {
        return this;
    }

    public LambdaUpdateWrapper<T> set(SFunction<T, ?> fn, Object val) {
        setClauses.add(col(fn) + " = " + conditionBuilder.bindValue(val));
        return this;
    }

    public LambdaUpdateWrapper<T> setIfPresent(SFunction<T, ?> fn, Object val) {
        if (conditionBuilder.isEmptyValue(val)) {
            return this;
        }
        return set(fn, val);
    }

    public LambdaUpdateWrapper<T> setNull(SFunction<T, ?> fn) {
        setClauses.add(col(fn) + " = NULL");
        return this;
    }

    public LambdaUpdateWrapper<T> increase(SFunction<T, ?> fn, Number delta) {
        if (delta == null) {
            return this;
        }
        String column = col(fn);
        setClauses.add(column + " = " + column + " + " + conditionBuilder.bindValue(delta));
        return this;
    }

    public LambdaUpdateWrapper<T> decrease(SFunction<T, ?> fn, Number delta) {
        if (delta == null) {
            return this;
        }
        String column = col(fn);
        setClauses.add(column + " = " + column + " - " + conditionBuilder.bindValue(delta));
        return this;
    }

    public LambdaUpdateWrapper<T> allowFullTable() {
        this.allowFullTable = true;
        return this;
    }

    /**
     * 在当前更新上临时关闭租户限制，适合后台跨租户修复数据。
     */
    public LambdaUpdateWrapper<T> allTenants() {
        this.acrossTenants = true;
        return this;
    }

    public String buildSql() {
        if (setClauses.isEmpty()) {
            throw new IllegalStateException("lambdaUpdate 至少需要一个 set 操作");
        }
        String tableName = TableCacheManager.getTableNameByClass(entityClazz);
        if (tableName == null) {
            throw new IllegalStateException(
                    "未找到实体类 " + entityClazz.getName() + " 对应的表名，请检查 @MyTable 注解");
        }
        String whereSql = conditionBuilder.render();
        if (whereSql.isBlank() && !allowFullTable) {
            throw new IllegalStateException("lambdaUpdate 默认禁止无条件全表更新，如确有需要请显式调用 allowFullTable()");
        }
        StringBuilder sql = new StringBuilder("UPDATE ")
                .append(tableName)
                .append(" SET ")
                .append(String.join(", ", setClauses));
        if (!whereSql.isBlank()) {
            sql.append(" WHERE ").append(whereSql);
        }
        return sql.toString();
    }

    public Map<String, Object> getParams() {
        return conditionBuilder.getParamsView();
    }

    public int update() {
        if (baseDao == null) {
            throw new IllegalStateException("当前 LambdaUpdateWrapper 未绑定 IBaseDao，无法执行 update()");
        }
        Supplier<Integer> action = () -> baseDao.updateForSql(buildSql(), getParams(), entityClazz);
        if (allowFullTable) {
            Supplier<Integer> delegate = action;
            action = () -> MyJdbcScope.allowUnsafeWrite(delegate);
        }
        if (acrossTenants) {
            Supplier<Integer> delegate = action;
            action = () -> MyJdbcScope.allTenants(delegate);
        }
        return action.get();
    }
}
