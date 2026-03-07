package io.github.canjiemo.base.myjdbc.security;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 对 raw SQL API 做最小必要的语义校验。
 *
 * <p>目标不是替代数据库权限，而是尽量在框架层阻止明显的误用：
 * 查询 API 只接收查询语句，更新 API 只接收更新语句，危险全表更新需要显式放开。
 */
public final class SqlOperationGuard {

    private SqlOperationGuard() {
    }

    public static void requireQueryStatement(String sql, String scene) {
        Statement statement = parseSingleStatement(sql, scene);
        if (!(statement instanceof Select)) {
            throw new IllegalArgumentException(scene + " 只允许执行查询 SQL");
        }
    }

    public static void requireUpdateStatement(String sql, boolean allowUnsafeWrite, String scene) {
        Statement statement = parseSingleStatement(sql, scene);
        if (!(statement instanceof Update update)) {
            throw new IllegalArgumentException(scene + " 只允许执行 UPDATE SQL");
        }
        if (update.getWhere() == null && !allowUnsafeWrite) {
            throw new IllegalArgumentException(scene + " 默认禁止无 WHERE 的全表更新，如确有需要请显式放开");
        }
    }

    private static Statement parseSingleStatement(String sql, String scene) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException(scene + " SQL 不能为空");
        }
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements == null || statements.getStatements() == null || statements.getStatements().isEmpty()) {
                throw new IllegalArgumentException(scene + " SQL 不能为空");
            }
            if (statements.getStatements().size() != 1) {
                throw new IllegalArgumentException(scene + " 只允许执行单条 SQL");
            }
            return statements.getStatements().get(0);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(scene + " SQL 解析失败: " + e.getMessage(), e);
        }
    }
}
