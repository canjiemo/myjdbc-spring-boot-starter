package io.github.canjiemo.base.myjdbc.builder;

import io.github.canjiemo.mycommon.pager.Pager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Locale;
import java.util.regex.Pattern;

public class SqlBuilder {

	protected Logger logger = LoggerFactory.getLogger(getClass());

	private final DbType dbType;

	/** 排序列白名单：只允许字母、数字、下划线、点（支持 table.column 写法） */
	private static final Pattern SAFE_SORT_COLUMN_PATTERN =
			Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$");

	/**
	 * Spring 使用：传入 DataSource，构造时自动检测数据库类型。
	 */
	public SqlBuilder(DataSource dataSource) {
		this.dbType = detectDbType(dataSource);
	}

	/**
	 * 直接指定数据库类型（枚举，推荐）。适用于测试或多数据源手动配置。
	 */
	public SqlBuilder(DbType dbType) {
		this.dbType = dbType;
	}

	/**
	 * 直接指定数据库类型码（整型），不合法时立即抛出异常。
	 * 适用于已有 int 常量的兼容场景；新代码优先使用 {@link #SqlBuilder(DbType)}。
	 */
	public SqlBuilder(int code) {
		this.dbType = DbType.fromCode(code);
	}

	private static DbType detectDbType(DataSource ds) {
		Logger log = LoggerFactory.getLogger(SqlBuilder.class);
		String typeName = null;
		String version = null;
		Connection connection = null;
		DbType detected = DbType.MYSQL;
		try {
			connection = ds.getConnection();
			typeName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
			version  = connection.getMetaData().getDatabaseProductVersion().toLowerCase(Locale.ROOT);
			if ("mysql".equals(typeName)) {
				detected = DbType.MYSQL;
			} else if ("oracle".equals(typeName)) {
				detected = DbType.ORACLE;
			} else if ("sqlserver".equals(typeName) || typeName.contains("microsoft")) {
				detected = DbType.SQL_SERVER;
			} else if ("kingbasees".equals(typeName)) {
				detected = DbType.KINGBASE_ES;
			} else if ("postgresql".equals(typeName)) {
				detected = DbType.POSTGRESQL;
			} else {
				log.info("没匹对正确的数据库版本，默认使用mysql模式");
			}
		} catch (Exception e) {
			log.error("获取数据库类型异常", e);
			throw new Error("myjdbc组件加载失败");
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (Exception ignore) {}
			}
		}
		log.info("数据库类型: {}  版本信息:{}", typeName, version);
		return detected;
	}

	/**
	 * 校验并转换排序列名（驼峰→下划线），非法值抛出 IllegalArgumentException。
	 * 防止排序字段 SQL 注入。
	 */
	private static String validateAndConvertSortColumn(String sortColumn) {
		if (!StringUtils.hasText(sortColumn)) return null;
		String converted = camelCaseToUnderscore(sortColumn);
		if (!SAFE_SORT_COLUMN_PATTERN.matcher(converted).matches()) {
			throw new IllegalArgumentException("非法排序字段: " + sortColumn);
		}
		return converted;
	}

	/**
	 * 校验排序方向，只允许 asc / desc（大小写不敏感），非法值抛出 IllegalArgumentException。
	 */
	private static String validateOrder(String order) {
		if (!StringUtils.hasText(order)) return null;
		if (!"asc".equalsIgnoreCase(order) && !"desc".equalsIgnoreCase(order)) {
			throw new IllegalArgumentException("非法排序方向: " + order);
		}
		return order.toLowerCase(Locale.ROOT);
	}

	public String buildPagerSql(String sql, Pager pager) {
		return switch (dbType) {
			case ORACLE     -> buildOraclePagerSql(sql, pager);
			case SQL_SERVER -> buildSqlServerPagerSql(sql, pager);
			case POSTGRESQL -> buildPgsqlPagerSql(sql, pager);
			default         -> buildMysqlPagerSql(sql, pager); // MYSQL + KINGBASE_ES
		};
	}

	/**
	 * sqlserver
	 */
	private static String buildSqlServerPagerSql(String sql, Pager pager) {
		StringBuilder pagingSelect = new StringBuilder(300);
		sql = sql.replaceFirst("^\\s*[sS][eE][lL][eE][cC][tT]\\s+", "select top " + (pager.getStartRow() + pager.getPageSize()) + " ");
		pagingSelect.append(" select * from ( select row_number()over(order by __tc__)tempRowNumber,* from (select    __tc__=0, *  from ( ");
		pagingSelect.append(" select top 100 percent * from ( ");
		pagingSelect.append(sql);
		pagingSelect.append(" )  as _sqlservertb_  ");
		String sortColumn = validateAndConvertSortColumn(pager.getSort());
		String order = validateOrder(pager.getOrder());
		if (sortColumn != null && order != null) {
			pagingSelect.append(" order by ").append(sortColumn).append(" ").append(order);
		}
		pagingSelect.append(" ) t )tt )ttt where tempRowNumber > ").append(pager.getStartRow()).append(" and tempRowNumber <= ").append(pager.getStartRow() + pager.getPageSize());
		return pagingSelect.toString();
	}

	/**
	 * postgresql
	 */
	private static String buildPgsqlPagerSql(String sql, Pager pager) {
		StringBuilder pagingSelect = new StringBuilder(300);
		pagingSelect.append(" select * from ( ");
		pagingSelect.append(sql);
		pagingSelect.append(" ) as _pgsqltb_ ");
		String sortColumn = validateAndConvertSortColumn(pager.getSort());
		String order = validateOrder(pager.getOrder());
		if (sortColumn != null && order != null) {
			pagingSelect.append(" order by ").append(sortColumn).append(" ").append(order);
		}
		pagingSelect.append(" OFFSET ").append(pager.getStartRow()).append(" LIMIT ").append(pager.getPageSize());
		return pagingSelect.toString();
	}

	/**
	 * mysql / kingbasees
	 */
	private static String buildMysqlPagerSql(String sql, Pager pager) {
		StringBuilder pagingSelect = new StringBuilder(300);
		pagingSelect.append(" select * from ( ");
		pagingSelect.append(sql);
		pagingSelect.append(" ) as _mysqltb_ ");
		String sortColumn = validateAndConvertSortColumn(pager.getSort());
		String order = validateOrder(pager.getOrder());
		if (sortColumn != null && order != null) {
			pagingSelect.append(" order by ").append(sortColumn).append(" ").append(order);
		}
		pagingSelect.append(" limit ").append(pager.getStartRow()).append(",").append(pager.getPageSize());
		return pagingSelect.toString();
	}

	/**
	 * oracle
	 * 注：Oracle 子查询别名不使用 AS 关键字
	 */
	private static String buildOraclePagerSql(String sql, Pager pager) {
		StringBuilder pagingSelect = new StringBuilder(300);
		pagingSelect.append("select * from ( select row_.*, rownum rownum_userforpage from ( ");
		pagingSelect.append(" select * from ( ");
		pagingSelect.append(sql);
		pagingSelect.append(" )  _oracletb_  ");
		String sortColumn = validateAndConvertSortColumn(pager.getSort());
		String order = validateOrder(pager.getOrder());
		if (sortColumn != null && order != null) {
			pagingSelect.append(" order by ").append(sortColumn).append(" ").append(order);
		}
		pagingSelect.append(" ) row_ where rownum <= ")
				.append(pager.getStartRow() + pager.getPageSize())
				.append(") where rownum_userforpage > ")
				.append(pager.getStartRow());
		return pagingSelect.toString();
	}

	public static String camelCaseToUnderscore(String str) {
		if (!StringUtils.hasText(str)) return str;
		StringBuilder sb = new StringBuilder(str.length());
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (Character.isUpperCase(c)) {
				sb.append("_");
				sb.append(Character.toLowerCase(c));
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}

}
