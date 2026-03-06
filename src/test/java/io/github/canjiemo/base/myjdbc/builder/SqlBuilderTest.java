package io.github.canjiemo.base.myjdbc.builder;

import io.github.canjiemo.mycommon.pager.Pager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SqlBuilder 方言测试")
class SqlBuilderTest {

    @Test
    @DisplayName("KingbaseES 分页应沿用 MySQL LIMIT offset,count 语法")
    void kingbaseShouldReuseMysqlPaginationStyle() {
        SqlBuilder sqlBuilder = new SqlBuilder(DbType.KINGBASE_ES);

        String pageSql = sqlBuilder.buildPagerSql("SELECT * FROM user", new Pager<>(1, 10));
        String normalized = pageSql.toLowerCase();

        assertTrue(pageSql.contains("_mysqltb_"), "KingbaseES 当前约束下应沿用 MySQL 分页别名");
        assertTrue(normalized.contains("limit 0,10") || normalized.contains("limit 0, 10"),
                "KingbaseES 当前约束下应使用 MySQL 风格 LIMIT offset,count，实际: " + pageSql);
    }
}
