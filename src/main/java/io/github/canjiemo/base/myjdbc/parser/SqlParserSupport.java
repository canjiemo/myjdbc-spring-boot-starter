package io.github.canjiemo.base.myjdbc.parser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NotExpression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.*;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class SqlParserSupport {

    private SqlParserSupport() {}

    static String rewriteSelectSql(String sql, String scene, Consumer<Select> processor, Logger log) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select selectStatement)) {
                return sql;
            }
            processor.accept(selectStatement);
            return selectStatement.toString();
        } catch (JSQLParserException e) {
            log.warn("解析SQL时发生异常（{}），返回原始SQL: {}, 异常: {}", scene, sql, e.getMessage());
            return sql;
        } catch (Exception e) {
            log.error("处理SQL时发生未知异常（{}），返回原始SQL: {}", scene, sql, e);
            return sql;
        }
    }

    static void processSelectTree(Select select, Consumer<PlainSelect> plainSelectProcessor) {
        if (select instanceof PlainSelect plainSelect) {
            plainSelectProcessor.accept(plainSelect);
        } else if (select instanceof SetOperationList setOperationList) {
            for (Select item : setOperationList.getSelects()) {
                processSelectTree(item, plainSelectProcessor);
            }
        }
    }

    static void processNestedSelects(PlainSelect plainSelect, Consumer<Select> selectProcessor) {
        if (plainSelect.getSelectItems() != null) {
            for (SelectItem item : plainSelect.getSelectItems()) {
                if (item.getExpression() != null) {
                    processNestedSelectsInExpression(item.getExpression(), selectProcessor);
                }
            }
        }
        if (plainSelect.getWhere() != null) {
            processNestedSelectsInExpression(plainSelect.getWhere(), selectProcessor);
        }
    }

    static void visitTables(PlainSelect plainSelect, Consumer<Select> nestedSelectProcessor,
                            BiConsumer<Table, Join> tableVisitor) {
        if (plainSelect.getFromItem() instanceof Table table) {
            tableVisitor.accept(table, null);
        } else if (plainSelect.getFromItem() instanceof ParenthesedSelect ps) {
            nestedSelectProcessor.accept(ps.getSelect());
        }

        if (plainSelect.getJoins() == null) {
            return;
        }
        for (Join join : plainSelect.getJoins()) {
            if (join.getRightItem() instanceof Table table) {
                tableVisitor.accept(table, join);
            } else if (join.getRightItem() instanceof ParenthesedSelect ps) {
                nestedSelectProcessor.accept(ps.getSelect());
            }
        }
    }

    static Expression combineWithAnd(Collection<Expression> expressions) {
        Expression combined = null;
        for (Expression expr : expressions) {
            combined = (combined == null) ? expr : new AndExpression(combined, expr);
        }
        return combined;
    }

    private static void processNestedSelectsInExpression(Expression expression, Consumer<Select> selectProcessor) {
        if (expression instanceof ParenthesedSelect ps) {
            selectProcessor.accept(ps.getSelect());
        } else if (expression instanceof NotExpression notExpr) {
            processNestedSelectsInExpression(notExpr.getExpression(), selectProcessor);
        } else if (expression instanceof InExpression inExpr) {
            if (inExpr.getRightExpression() != null) {
                processNestedSelectsInExpression(inExpr.getRightExpression(), selectProcessor);
            }
        } else if (expression instanceof ExistsExpression existsExpr) {
            processNestedSelectsInExpression(existsExpr.getRightExpression(), selectProcessor);
        } else if (expression instanceof BinaryExpression binExpr) {
            processNestedSelectsInExpression(binExpr.getLeftExpression(), selectProcessor);
            processNestedSelectsInExpression(binExpr.getRightExpression(), selectProcessor);
        }
    }
}
