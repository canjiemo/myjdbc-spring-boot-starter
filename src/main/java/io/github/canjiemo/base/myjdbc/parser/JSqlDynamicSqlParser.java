package io.github.canjiemo.base.myjdbc.parser;

import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.tenant.TenantContext;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 基于JSqlParser的动态SQL解析器
 * 提供更加精确和可靠的SQL解析和删除条件拼接功能
 */
public class JSqlDynamicSqlParser {

    private static final Logger log = LoggerFactory.getLogger(JSqlDynamicSqlParser.class);

    /** 租户参数名（SQL 占位符名称），内部固定，不对外暴露 */
    public static final String TENANT_PARAM_NAME = "myjdbcTenantId";

    /**
     * 为 INSERT SQL 追加租户列和值占位符（幂等：已含租户列时直接返回原 SQL）。
     *
     * <pre>
     * INSERT INTO user(name, email) VALUES (:name, :email)
     * → INSERT INTO user(name, email, tenant_id) VALUES (:name, :email, :myjdbcTenantId)
     * </pre>
     *
     * <p>注意：此方法不检查表名注册，由调用方（{@code BaseDaoImpl}）负责判断表是否有租户字段。
     *
     * @param sql 原始 INSERT SQL
     * @return 追加租户列后的 SQL；全局关闭、已含租户列或格式无法识别时返回原 SQL
     */
    public static String appendTenantToInsertSql(String sql, boolean tenantEnabled, String tenantColumn) {
        if (!tenantEnabled || sql == null || sql.isBlank()) return sql;
        String resolvedTenantColumn = normalizeTenantColumn(tenantColumn);
        if (sql.toLowerCase().contains(resolvedTenantColumn.toLowerCase())) return sql;
        String upperSql = sql.toUpperCase();
        int closeParen = upperSql.indexOf(") VALUES");
        if (closeParen == -1) return sql;
        int lastClose = sql.lastIndexOf(")");
        if (lastClose <= closeParen) return sql;
        return sql.substring(0, closeParen)
                + ", " + resolvedTenantColumn + ")"
                + sql.substring(closeParen + 1, lastClose)
                + ", :" + TENANT_PARAM_NAME + ")";
    }

    /**
     * 为SQL自动拼接逻辑删除条件
     *
     * @param sql 原始SQL语句
     * @return 拼接删除条件后的SQL语句
     */
    public static String appendDeleteCondition(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        return SqlParserSupport.rewriteSelectSql(sql, "逻辑删除条件", JSqlDynamicSqlParser::processSelectStatement, log);
    }
    
    // ===================== 多租户条件注入 =====================

    /**
     * 为 SQL 自动拼接租户隔离条件（参数化，生成 :myjdbcTenantId 占位符）
     *
     * <p>以下情况不注入：
     * <ul>
     *   <li>全局开关 {@code myjdbc.tenant.enabled=false}</li>
     *   <li>当前线程调用了 {@link TenantContext#skip()}</li>
     *   <li>SQL 中的表在数据库中不存在租户字段（由启动时扫描确定）</li>
     * </ul>
     *
     * @param sql 原始 SQL（通常已经过 appendDeleteCondition 处理）
     * @return 拼接租户条件后的 SQL，不需要拼接则返回原 SQL
     */
    public static String appendTenantCondition(String sql, boolean tenantEnabled, String tenantColumn) {
        if (!tenantEnabled || sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        if (TenantContext.isSkipped()) {
            log.debug("当前线程已标记跳过租户隔离，不注入租户条件");
            return sql;
        }

        String resolvedTenantColumn = normalizeTenantColumn(tenantColumn);
        return SqlParserSupport.rewriteSelectSql(sql, "租户条件",
                select -> processTenantSelect(select, resolvedTenantColumn), log);
    }

    /**
     * 处理 SELECT 语句，注入租户条件（复用 processSelect 框架，但走租户分支）
     */
    private static void processTenantSelect(Select select, String tenantColumn) {
        SqlParserSupport.processSelectTree(select, plainSelect -> processTenantPlainSelect(plainSelect, tenantColumn));
    }

    /**
     * 处理简单 SELECT 语句的租户条件注入，逻辑与 processPlainSelect 对称
     */
    private static void processTenantPlainSelect(PlainSelect plainSelect, String tenantColumn) {
        List<TableTenantCondition> whereConditions = new ArrayList<>();
        List<TableTenantCondition> joinConditions = new ArrayList<>();

        Expression existingWhere = plainSelect.getWhere();

        SqlParserSupport.visitTables(plainSelect, select -> processTenantSelect(select, tenantColumn),
                (table, join) -> addTableTenantConditionByType(table, existingWhere,
                        join == null ? JoinType.FROM_TABLE : getJoinType(join), join,
                        whereConditions, joinConditions, tenantColumn));

        // 注入 WHERE 条件
        if (!whereConditions.isEmpty()) {
            Expression tenantConditions = buildTenantConditionsExpression(whereConditions, tenantColumn);
            Expression currentWhere = plainSelect.getWhere();
            plainSelect.setWhere(currentWhere != null
                    ? new AndExpression(currentWhere, tenantConditions)
                    : tenantConditions);
        }

        // 注入 JOIN ON 条件
        for (TableTenantCondition condition : joinConditions) {
            addTenantConditionToJoinOn(condition, tenantColumn);
        }

        // 递归处理子查询
        SqlParserSupport.processNestedSelects(plainSelect, select -> processTenantSelect(select, tenantColumn));
    }

    private static void addTableTenantConditionByType(Table table, Expression existingWhere, JoinType joinType,
                                                       Join joinObject,
                                                       List<TableTenantCondition> whereConditions,
                                                       List<TableTenantCondition> joinConditions,
                                                       String tenantColumn) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;

        if (!TableCacheManager.hasTenantColumn(tableName)) {
            return;
        }

        // 幂等检查：如果 WHERE 中已经有 tenant 条件，跳过
        if (isTenantConditionExists(existingWhere, tenantColumn, alias, tableName)) {
            log.debug("表 {} 的租户条件已存在，跳过自动拼接", tableName);
            return;
        }

        TableTenantCondition condition = new TableTenantCondition(tableName, alias, joinType, joinObject);
        if (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) {
            joinConditions.add(condition);
        } else {
            whereConditions.add(condition);
        }
        log.debug("表 {}({}) 的租户条件将添加到 {}", tableName, joinType,
                (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) ? "JOIN ON" : "WHERE");
    }

    private static Expression buildTenantConditionsExpression(List<TableTenantCondition> conditions, String tenantColumn) {
        Expression result = null;
        for (TableTenantCondition tc : conditions) {
            Expression cond = createTenantConditionExpression(tc.alias != null ? tc.alias : tc.tableName, tenantColumn);
            result = (result == null) ? cond : new AndExpression(result, cond);
        }
        return result;
    }

    private static void addTenantConditionToJoinOn(TableTenantCondition condition, String tenantColumn) {
        if (condition.joinObject == null) return;

        Expression tenantCond = createTenantConditionExpression(
                condition.alias != null ? condition.alias : condition.tableName, tenantColumn);

        Collection<Expression> onExpressions = condition.joinObject.getOnExpressions();
        if (onExpressions != null && !onExpressions.isEmpty()) {
            String tenantColumnRef = (condition.alias != null ? condition.alias : condition.tableName)
                    + "." + tenantColumn;
            for (Expression expr : onExpressions) {
                if (expr.toString().contains(tenantColumnRef)) {
                    return; // 已存在，跳过
                }
            }
            Expression combined = SqlParserSupport.combineWithAnd(onExpressions);
            List<Expression> newExpressions = new ArrayList<>();
            newExpressions.add(new AndExpression(combined, tenantCond));
            condition.joinObject.setOnExpressions(newExpressions);
        } else {
            List<Expression> newExpressions = new ArrayList<>();
            newExpressions.add(tenantCond);
            condition.joinObject.setOnExpressions(newExpressions);
        }
    }

    /**
     * 创建租户条件表达式：tableRef.tenant_id = :myjdbcTenantId
     */
    private static Expression createTenantConditionExpression(String tableRef, String tenantColumn) {
        Column column = new Column();
        if (tableRef != null && !tableRef.trim().isEmpty()) {
            column.setTable(new Table(tableRef));
        }
        column.setColumnName(tenantColumn);

        JdbcNamedParameter param = new JdbcNamedParameter();
        param.setName(TENANT_PARAM_NAME);

        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(param);
        return equalsTo;
    }

    /**
     * 幂等检查：WHERE 表达式中是否已包含租户条件
     */
    private static boolean isTenantConditionExists(Expression whereExpression, String column,
                                                    String tableAlias, String tableName) {
        return isDeleteConditionExists(whereExpression, column, tableAlias, tableName);
    }

    /**
     * 租户条件信息
     */
    private static class TableTenantCondition {
        final String tableName;
        final String alias;
        final JoinType joinType;
        final Join joinObject;

        TableTenantCondition(String tableName, String alias, JoinType joinType, Join joinObject) {
            this.tableName = tableName;
            this.alias = alias;
            this.joinType = joinType;
            this.joinObject = joinObject;
        }
    }

    // ===================== 合并处理（单次解析同时注入逻辑删除 + 租户条件）=====================

    /**
     * 一次 SQL 解析，同时注入逻辑删除条件和租户隔离条件。
     *
     * <p>仅在租户功能开启且 tenantId 非 null 时由 BaseDaoImpl 调用，
     * 调用前 TenantContext.isSkipped() 已检查，此处无需重复判断。
     *
     * @param sql 原始 SQL
     * @return 注入两类条件后的 SQL
     */
    public static String appendConditions(String sql, boolean tenantEnabled, String tenantColumn) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        String resolvedTenantColumn = normalizeTenantColumn(tenantColumn);
        return SqlParserSupport.rewriteSelectSql(sql, "合并条件",
                select -> processUnifiedSelect(select, tenantEnabled, resolvedTenantColumn), log);
    }

    private static void processUnifiedSelect(Select select, boolean tenantEnabled, String tenantColumn) {
        SqlParserSupport.processSelectTree(select,
                plainSelect -> processUnifiedPlainSelect(plainSelect, tenantEnabled, tenantColumn));
    }

    private static void processUnifiedPlainSelect(PlainSelect plainSelect, boolean tenantEnabled, String tenantColumn) {
        List<UnifiedTableConditions> whereConditions = new ArrayList<>();
        List<UnifiedTableConditions> joinConditions = new ArrayList<>();

        Expression existingWhere = plainSelect.getWhere();

        SqlParserSupport.visitTables(plainSelect, select -> processUnifiedSelect(select, tenantEnabled, tenantColumn),
                (table, join) -> collectUnifiedConditions(table, existingWhere,
                        join == null ? JoinType.FROM_TABLE : getJoinType(join), join,
                        whereConditions, joinConditions, tenantEnabled, tenantColumn));

        // 注入 WHERE 条件
        if (!whereConditions.isEmpty()) {
            Expression allConditions = buildUnifiedWhereExpression(whereConditions);
            Expression currentWhere = plainSelect.getWhere();
            plainSelect.setWhere(currentWhere != null
                    ? new AndExpression(currentWhere, allConditions)
                    : allConditions);
        }

        // 注入 JOIN ON 条件
        for (UnifiedTableConditions utc : joinConditions) {
            applyUnifiedConditionsToJoinOn(utc);
        }

        // 递归处理子查询
        SqlParserSupport.processNestedSelects(plainSelect,
                select -> processUnifiedSelect(select, tenantEnabled, tenantColumn));
    }

    /**
     * 收集单个表所有需要注入的条件（逻辑删除 + 租户），并按 JOIN 类型分组
     */
    private static void collectUnifiedConditions(Table table, Expression existingWhere,
                                                  JoinType joinType, Join joinObject,
                                                  List<UnifiedTableConditions> whereConditions,
                                                  List<UnifiedTableConditions> joinConditions,
                                                  boolean tenantEnabled,
                                                  String tenantColumn) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;
        String tableRef = alias != null ? alias : tableName;

        List<Expression> exprs = new ArrayList<>();

        // 1. 逻辑删除条件
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByTableName(tableName);
        if (deleteInfo != null && deleteInfo.isValid() && StringUtils.isNotBlank(deleteInfo.getDelColumn())
                && !isDeleteConditionExists(existingWhere, deleteInfo.getDelColumn(), alias, tableName)) {
            Column col = new Column();
            col.setTable(new Table(tableRef));
            col.setColumnName(deleteInfo.getDelColumn());
            EqualsTo eq = new EqualsTo();
            eq.setLeftExpression(col);
            eq.setRightExpression(new LongValue(deleteInfo.getUnDelValue()));
            exprs.add(eq);
        }

        // 2. 租户条件
        if (tenantEnabled && TableCacheManager.hasTenantColumn(tableName)
                && !isDeleteConditionExists(existingWhere, tenantColumn, alias, tableName)) {
            exprs.add(createTenantConditionExpression(tableRef, tenantColumn));
        }

        if (exprs.isEmpty()) return;

        UnifiedTableConditions utc = new UnifiedTableConditions(tableName, alias, joinType, joinObject, exprs);
        if (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) {
            joinConditions.add(utc);
        } else {
            whereConditions.add(utc);
        }
        log.debug("表 {}({}) 收集到 {} 个条件，将添加到 {}", tableName, joinType, exprs.size(),
                (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) ? "JOIN ON" : "WHERE");
    }

    private static Expression buildUnifiedWhereExpression(List<UnifiedTableConditions> conditions) {
        Expression result = null;
        for (UnifiedTableConditions utc : conditions) {
            for (Expression expr : utc.expressions) {
                result = (result == null) ? expr : new AndExpression(result, expr);
            }
        }
        return result;
    }

    private static void applyUnifiedConditionsToJoinOn(UnifiedTableConditions utc) {
        if (utc.joinObject == null) return;

        Expression newConditions = SqlParserSupport.combineWithAnd(utc.expressions);

        Collection<Expression> onExpressions = utc.joinObject.getOnExpressions();
        if (onExpressions != null && !onExpressions.isEmpty()) {
            Expression combined = SqlParserSupport.combineWithAnd(onExpressions);
            List<Expression> newExprs = new ArrayList<>();
            newExprs.add(new AndExpression(combined, newConditions));
            utc.joinObject.setOnExpressions(newExprs);
        } else {
            List<Expression> newExprs = new ArrayList<>();
            newExprs.add(newConditions);
            utc.joinObject.setOnExpressions(newExprs);
        }
    }

    /**
     * 单个表收集到的所有条件（逻辑删除 + 租户），及其 JOIN 元数据
     */
    private static class UnifiedTableConditions {
        final String tableName;
        final String alias;
        final JoinType joinType;
        final Join joinObject;
        final List<Expression> expressions;

        UnifiedTableConditions(String tableName, String alias, JoinType joinType,
                               Join joinObject, List<Expression> expressions) {
            this.tableName = tableName;
            this.alias = alias;
            this.joinType = joinType;
            this.joinObject = joinObject;
            this.expressions = expressions;
        }
    }

    // ===================== 逻辑删除条件处理 =====================

    /**
     * 处理SELECT语句，自动识别表并拼接删除条件
     */
    private static void processSelectStatement(Select selectStatement) {
        processSelect(selectStatement);
    }
    
    /**
     * 处理Select，支持各种类型的SELECT结构
     */
    private static void processSelect(Select select) {
        SqlParserSupport.processSelectTree(select, JSqlDynamicSqlParser::processPlainSelect);
    }
    
    /**
     * 处理简单SELECT语句
     */
    private static void processPlainSelect(PlainSelect plainSelect) {
        // 收集需要处理的表信息，按JOIN类型分类
        List<TableDeleteCondition> whereConditions = new ArrayList<>(); // 放入WHERE的条件
        List<TableDeleteCondition> joinConditions = new ArrayList<>();  // 放入JOIN ON的条件
        
        // 获取现有的WHERE条件，用于检查是否已包含删除条件
        Expression existingWhere = plainSelect.getWhere();

        SqlParserSupport.visitTables(plainSelect, JSqlDynamicSqlParser::processSelect,
                (table, join) -> addTableConditionByType(table, existingWhere,
                        join == null ? JoinType.FROM_TABLE : getJoinType(join), join,
                        whereConditions, joinConditions));
        
        // 处理WHERE条件（主表和INNER JOIN）
        if (!whereConditions.isEmpty()) {
            Expression whereDeleteConditions = buildDeleteConditionsExpression(whereConditions);
            Expression currentWhere = plainSelect.getWhere();
            if (currentWhere != null) {
                plainSelect.setWhere(new AndExpression(currentWhere, whereDeleteConditions));
            } else {
                plainSelect.setWhere(whereDeleteConditions);
            }
        }
        
        // 处理JOIN ON条件（LEFT/RIGHT JOIN）
        if (!joinConditions.isEmpty()) {
            for (TableDeleteCondition condition : joinConditions) {
                addDeleteConditionToJoinOn(condition);
            }
        }
        
        // 处理子查询中的SELECT
        SqlParserSupport.processNestedSelects(plainSelect, JSqlDynamicSqlParser::processSelect);
    }
    
    
    /**
     * 根据JOIN类型添加表的删除条件信息
     */
    private static void addTableConditionByType(Table table, Expression existingWhere, JoinType joinType, Join joinObject,
                                               List<TableDeleteCondition> whereConditions, List<TableDeleteCondition> joinConditions) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;
        
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByTableName(tableName);
        if (deleteInfo != null && deleteInfo.isValid() && StringUtils.isNotBlank(deleteInfo.getDelColumn())) {
            // 检查现有WHERE条件中是否已包含删除字段条件
            if (!isDeleteConditionExists(existingWhere, deleteInfo.getDelColumn(), alias, tableName)) {
                TableDeleteCondition condition = new TableDeleteCondition(tableName, alias, deleteInfo, joinType, joinObject);
                
                // 根据JOIN类型决定条件放置位置
                if (joinType == JoinType.FROM_TABLE || joinType == JoinType.INNER_JOIN) {
                    // 主表和INNER JOIN的条件放入WHERE
                    whereConditions.add(condition);
                } else if (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) {
                    // LEFT/RIGHT JOIN的条件放入JOIN ON
                    joinConditions.add(condition);
                } else {
                    // FULL JOIN 也放入WHERE（较少使用）
                    whereConditions.add(condition);
                }
                
                log.debug("表{}({})的删除条件将添加到{}", tableName, joinType,
                    (joinType == JoinType.LEFT_JOIN || joinType == JoinType.RIGHT_JOIN) ? "JOIN ON" : "WHERE");
            } else {
                log.debug("表{}的删除条件已存在，跳过自动拼接", tableName);
            }
        }
    }
    
    /**
     * 获取JOIN类型
     */
    private static JoinType getJoinType(Join join) {
        if (join.isLeft()) {
            return JoinType.LEFT_JOIN;
        } else if (join.isRight()) {
            return JoinType.RIGHT_JOIN;
        } else if (join.isFull()) {
            return JoinType.FULL_JOIN;
        } else {
            // 默认为INNER JOIN（包括JOIN、INNER JOIN等）
            return JoinType.INNER_JOIN;
        }
    }
    
    /**
     * 将删除条件添加到JOIN ON子句中
     * 使用 setOnExpressions 避免 JSqlParser 废弃的 setOnExpression 方法导致的重复 ON 子句问题
     */
    private static void addDeleteConditionToJoinOn(TableDeleteCondition condition) {
        if (condition.joinObject == null) {
            log.warn("JOIN对象为空，无法添加删除条件到ON子句");
            return;
        }

        Expression deleteCondition = createDeleteConditionExpression(condition.deleteInfo, condition.alias);

        log.debug("要添加的删除条件: {}", deleteCondition);

        // 获取现有的ON条件
        Collection<Expression> onExpressions = condition.joinObject.getOnExpressions();

        if (onExpressions != null && !onExpressions.isEmpty()) {
            // 检查删除条件是否已经存在
            String deleteColumnRef = (condition.alias != null ? condition.alias : condition.tableName) + "." + condition.deleteInfo.getDelColumn();
            for (Expression expr : onExpressions) {
                if (expr.toString().contains(deleteColumnRef)) {
                    log.debug("表{}的删除条件已存在于ON子句中，跳过添加", condition.tableName);
                    return;
                }
            }

            // 组合所有现有条件
            Expression combined = SqlParserSupport.combineWithAnd(onExpressions);

            // 添加删除条件
            combined = new AndExpression(combined, deleteCondition);

            // 使用正确的 setOnExpressions 方法
            List<Expression> newExpressions = new ArrayList<>();
            newExpressions.add(combined);
            condition.joinObject.setOnExpressions(newExpressions);

            log.debug("组合后的ON条件: {}", combined);
        } else {
            // 没有现有条件，直接设置删除条件
            List<Expression> newExpressions = new ArrayList<>();
            newExpressions.add(deleteCondition);
            condition.joinObject.setOnExpressions(newExpressions);
        }

        log.debug("已将表{}的删除条件添加到JOIN ON子句", condition.tableName);
    }
    
    /**
     * 添加表的删除条件信息（如果尚未存在相关条件）- 保留用于兼容性
     */
    @Deprecated
    private static void addTableCondition(Map<String, TableDeleteCondition> tableConditions, Table table, Expression existingWhere) {
        String tableName = table.getName();
        String alias = table.getAlias() != null ? table.getAlias().getName() : null;
        
        TableCacheManager.DeleteInfo deleteInfo = TableCacheManager.getDeleteInfoByTableName(tableName);
        if (deleteInfo != null && deleteInfo.isValid() && StringUtils.isNotBlank(deleteInfo.getDelColumn())) {
            // 检查现有WHERE条件中是否已包含删除字段条件
            if (!isDeleteConditionExists(existingWhere, deleteInfo.getDelColumn(), alias, tableName)) {
                String key = alias != null ? alias : tableName;
                tableConditions.put(key, new TableDeleteCondition(tableName, alias, deleteInfo, JoinType.FROM_TABLE));
            } else {
                log.debug("表{}的删除条件已存在，跳过自动拼接", tableName);
            }
        }
    }
    
    /**
     * 构建删除条件表达式
     */
    private static Expression buildDeleteConditionsExpression(Collection<TableDeleteCondition> tableConditions) {
        List<Expression> conditions = new ArrayList<>();
        
        for (TableDeleteCondition tc : tableConditions) {
            Expression condition = createDeleteConditionExpressionForTable(tc);
            conditions.add(condition);
        }
        
        if (conditions.isEmpty()) {
            return null;
        }
        
        Expression result = conditions.get(0);
        for (int i = 1; i < conditions.size(); i++) {
            result = new AndExpression(result, conditions.get(i));
        }
        
        return result;
    }
    
    /**
     * 检查WHERE表达式中是否已包含指定的删除字段条件
     * 
     * @param whereExpression WHERE表达式
     * @param deleteColumn 删除字段名
     * @param tableAlias 表别名
     * @param tableName 表名
     * @return 如果已存在删除条件返回true，否则返回false
     */
    private static boolean isDeleteConditionExists(Expression whereExpression, String deleteColumn, String tableAlias, String tableName) {
        if (whereExpression == null) {
            return false;
        }
        
        return containsDeleteColumn(whereExpression, deleteColumn, tableAlias, tableName);
    }
    
    /**
     * 递归检查表达式中是否包含删除字段
     */
    private static boolean containsDeleteColumn(Expression expression, String deleteColumn, String tableAlias, String tableName) {
        if (expression == null) {
            return false;
        }
        
        // 检查二元表达式（=, !=, <>, IS, IS NOT等）
        if (expression instanceof BinaryExpression) {
            BinaryExpression binExpr = (BinaryExpression) expression;
            Expression left = binExpr.getLeftExpression();
            
            // 检查左侧是否为我们关心的列
            if (left instanceof Column) {
                Column column = (Column) left;
                if (isTargetColumn(column, deleteColumn, tableAlias, tableName)) {
                    log.debug("发现删除字段条件: {}", expression.toString());
                    return true;
                }
            }
        }
        
        // 递归检查复合表达式（AND, OR等）
        if (expression instanceof AndExpression) {
            AndExpression andExpr = (AndExpression) expression;
            return containsDeleteColumn(andExpr.getLeftExpression(), deleteColumn, tableAlias, tableName) ||
                   containsDeleteColumn(andExpr.getRightExpression(), deleteColumn, tableAlias, tableName);
        }
        
        if (expression instanceof OrExpression) {
            OrExpression orExpr = (OrExpression) expression;
            return containsDeleteColumn(orExpr.getLeftExpression(), deleteColumn, tableAlias, tableName) ||
                   containsDeleteColumn(orExpr.getRightExpression(), deleteColumn, tableAlias, tableName);
        }
        
        return false;
    }
    
    /**
     * 检查列是否为目标删除字段
     */
    private static boolean isTargetColumn(Column column, String deleteColumn, String tableAlias, String tableName) {
        String columnName = column.getColumnName();
        
        // 检查列名是否匹配
        if (!deleteColumn.equalsIgnoreCase(columnName)) {
            return false;
        }
        
        // 如果列没有指定表，认为匹配（可能是无限定的字段引用）
        Table columnTable = column.getTable();
        if (columnTable == null) {
            return true;
        }
        
        String columnTableName = columnTable.getName();
        
        // 检查表名或别名是否匹配
        if (tableAlias != null && tableAlias.equalsIgnoreCase(columnTableName)) {
            return true;
        }
        
        if (tableName != null && tableName.equalsIgnoreCase(columnTableName)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 创建单个删除条件表达式
     */
    private static Expression createDeleteConditionExpression(TableCacheManager.DeleteInfo deleteInfo, String tableAlias) {
        Column column = new Column();
        if (tableAlias != null && !tableAlias.trim().isEmpty()) {
            column.setTable(new Table(tableAlias));
        }
        column.setColumnName(deleteInfo.getDelColumn());
        
        LongValue value = new LongValue(deleteInfo.getUnDelValue());
        
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(value);
        
        return equalsTo;
    }
    
    /**
     * 为表条件创建删除条件表达式
     */
    private static Expression createDeleteConditionExpressionForTable(TableDeleteCondition tableCondition) {
        Column column = new Column();
        String tableRef = tableCondition.alias != null ? tableCondition.alias : tableCondition.tableName;
        column.setTable(new Table(tableRef));
        column.setColumnName(tableCondition.deleteInfo.getDelColumn());
        
        LongValue value = new LongValue(tableCondition.deleteInfo.getUnDelValue());
        
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(column);
        equalsTo.setRightExpression(value);
        
        return equalsTo;
    }
    
    /**
     * JOIN类型枚举
     */
    private enum JoinType {
        FROM_TABLE,    // 主表
        INNER_JOIN,    // 内连接
        LEFT_JOIN,     // 左连接
        RIGHT_JOIN,    // 右连接
        FULL_JOIN      // 全连接
    }
    
    /**
     * 表删除条件信息
     */
    private static class TableDeleteCondition {
        final String tableName;
        final String alias;
        final TableCacheManager.DeleteInfo deleteInfo;
        final JoinType joinType;
        final Join joinObject; // 用于修改JOIN的ON条件
        
        TableDeleteCondition(String tableName, String alias, TableCacheManager.DeleteInfo deleteInfo, JoinType joinType) {
            this(tableName, alias, deleteInfo, joinType, null);
        }
        
        TableDeleteCondition(String tableName, String alias, TableCacheManager.DeleteInfo deleteInfo, JoinType joinType, Join joinObject) {
            this.tableName = tableName;
            this.alias = alias;
            this.deleteInfo = deleteInfo;
            this.joinType = joinType;
            this.joinObject = joinObject;
        }
    }

    private static String normalizeTenantColumn(String tenantColumn) {
        return (tenantColumn == null || tenantColumn.isBlank()) ? "tenant_id" : tenantColumn.trim();
    }

}
