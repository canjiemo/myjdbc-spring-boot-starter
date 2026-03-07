package io.github.canjiemo.base.myjdbc.lambda;

import io.github.canjiemo.base.myjdbc.MyTableEntity;
import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.dao.IBaseDao;
import io.github.canjiemo.base.myjdbc.scope.MyJdbcScope;
import io.github.canjiemo.mycommon.pager.Pager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lambda 链式查询构造器。
 *
 * <p>与早期版本相比，这一版的重点不是继续堆平铺方法，而是补“组合能力”：
 * <ul>
 *     <li>动态条件：{@code when(...)}</li>
 *     <li>组合条件：{@code any(...)} / {@code all(...)} / {@code or(...)}</li>
 *     <li>表达式查询：{@code exists(...)} / {@code groupBy(...)} / {@code having(...)}</li>
 * </ul>
 */
public class LambdaQueryWrapper<T extends MyTableEntity, R>
        extends AbstractLambdaFilter<T, LambdaQueryWrapper<T, R>> {

    private final Class<R> resultClazz;
    private final IBaseDao baseDao;
    private final List<String> selectColumns = new ArrayList<>();
    private final List<String> groupByColumns = new ArrayList<>();
    private final List<String> orderByClauses = new ArrayList<>();
    private String havingClause;
    private boolean acrossTenants;
    private boolean includeDeleted;

    public LambdaQueryWrapper(Class<T> entityClazz, Class<R> resultClazz, IBaseDao baseDao) {
        super(entityClazz, new LambdaConditionBuilder<>(entityClazz));
        this.resultClazz = resultClazz;
        this.baseDao = baseDao;
    }

    @Override
    protected LambdaQueryWrapper<T, R> self() {
        return this;
    }

    @SafeVarargs
    public final LambdaQueryWrapper<T, R> select(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            selectColumns.add(col(fn));
        }
        return this;
    }

    public LambdaQueryWrapper<T, R> selectAs(SFunction<T, ?> fn, String alias) {
        SqlFragmentGuard.requireSafeIdentifier(alias, "selectAs 别名");
        selectColumns.add(col(fn) + " AS " + alias.trim());
        return this;
    }

    public LambdaQueryWrapper<T, R> selectRaw(String expression) {
        selectColumns.add(conditionBuilder.normalizeFragment(expression, "selectRaw 表达式"));
        return this;
    }

    public LambdaQueryWrapper<T, R> selectRaw(String expression, String alias) {
        SqlFragmentGuard.requireSafeIdentifier(alias, "selectRaw 别名");
        selectColumns.add(conditionBuilder.normalizeFragment(expression, "selectRaw 表达式")
                + " AS " + alias.trim());
        return this;
    }

    /**
     * 在当前查询上临时关闭租户隔离，适合后台管理查询。
     */
    public LambdaQueryWrapper<T, R> allTenants() {
        this.acrossTenants = true;
        return this;
    }

    /**
     * 在当前查询上包含逻辑删除数据，不再自动补 delete_flag = 0。
     */
    public LambdaQueryWrapper<T, R> withDeleted() {
        this.includeDeleted = true;
        return this;
    }

    @SafeVarargs
    public final LambdaQueryWrapper<T, R> orderByAsc(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            orderByClauses.add(col(fn) + " ASC");
        }
        return this;
    }

    @SafeVarargs
    public final LambdaQueryWrapper<T, R> orderByDesc(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            orderByClauses.add(col(fn) + " DESC");
        }
        return this;
    }

    @SafeVarargs
    public final LambdaQueryWrapper<T, R> groupBy(SFunction<T, ?>... fns) {
        for (SFunction<T, ?> fn : fns) {
            groupByColumns.add(col(fn));
        }
        return this;
    }

    public LambdaQueryWrapper<T, R> having(String expression) {
        this.havingClause = conditionBuilder.normalizeFragment(expression, "HAVING 表达式");
        return this;
    }

    public LambdaQueryWrapper<T, R> having(String expression, Map<String, ?> params) {
        this.havingClause = conditionBuilder.bindFragment(expression, params, "HAVING 表达式");
        return this;
    }

    public String buildSql() {
        String tableName = TableCacheManager.getTableNameByClass(entityClazz);
        if (tableName == null) {
            throw new IllegalStateException(
                    "未找到实体类 " + entityClazz.getName() + " 对应的表名，请检查 @MyTable 注解");
        }
        StringBuilder sql = new StringBuilder("SELECT ");
        if (selectColumns.isEmpty()) {
            sql.append("*");
        } else {
            sql.append(String.join(", ", selectColumns));
        }
        sql.append(" FROM ").append(tableName);

        String whereSql = conditionBuilder.render();
        if (!whereSql.isBlank()) {
            sql.append(" WHERE ").append(whereSql);
        }
        if (!groupByColumns.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByColumns));
        }
        if (havingClause != null && !havingClause.isBlank()) {
            sql.append(" HAVING ").append(havingClause);
        }
        if (!orderByClauses.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orderByClauses));
        }
        return sql.toString();
    }

    public Map<String, Object> getParams() {
        return conditionBuilder.getParamsView();
    }

    public List<R> list() {
        return executeInScope(() -> baseDao.queryListForSql(buildSql(), getParams(), resultClazz));
    }

    public R one() {
        List<R> results = list();
        return results.isEmpty() ? null : results.get(0);
    }

    public long count() {
        String countSql = "SELECT count(*) FROM (" + buildSql() + ") _lqw_count";
        Long result = executeInScope(() -> baseDao.querySingleForSql(countSql, getParams(), Long.class));
        return result == null ? 0L : result;
    }

    public Pager<R> page(Pager<R> pager) {
        return executeInScope(() -> baseDao.queryPageForSql(buildSql(), getParams(), pager, resultClazz));
    }

    public boolean exists() {
        return count() > 0;
    }

    private <V> V executeInScope(Supplier<V> supplier) {
        if (baseDao == null) {
            throw new IllegalStateException("当前 LambdaQueryWrapper 未绑定 IBaseDao，无法执行查询");
        }
        Supplier<V> action = supplier;
        if (includeDeleted) {
            Supplier<V> delegate = action;
            action = () -> MyJdbcScope.withDeleted(delegate);
        }
        if (acrossTenants) {
            Supplier<V> delegate = action;
            action = () -> MyJdbcScope.allTenants(delegate);
        }
        return action.get();
    }
}
