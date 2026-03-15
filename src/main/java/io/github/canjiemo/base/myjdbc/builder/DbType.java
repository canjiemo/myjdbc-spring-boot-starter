package io.github.canjiemo.base.myjdbc.builder;

/**
 * 支持的数据库类型。
 * 用于替代魔数整型，在编译期约束合法值范围。
 */
public enum DbType {

    /** MySQL 数据库，类型码 1 */
    MYSQL(1),
    /** Oracle 数据库，类型码 2 */
    ORACLE(2),
    /** SQL Server 数据库，类型码 3 */
    SQL_SERVER(3),
    /** 人大金仓 KingbaseES 数据库，类型码 4 */
    KINGBASE_ES(4),
    /** PostgreSQL 数据库，类型码 5 */
    POSTGRESQL(5);

    private final int code;

    DbType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 将整型 code 转为枚举值；不合法时抛出 {@link IllegalArgumentException}。
     */
    public static DbType fromCode(int code) {
        for (DbType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知的数据库类型码: " + code + "，合法值为 1(MySQL) 2(Oracle) 3(SQL Server) 4(KingbaseES) 5(PostgreSQL)");
    }
}
