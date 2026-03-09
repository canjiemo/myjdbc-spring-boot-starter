package io.github.canjiemo.base.myjdbc.dao.impl;

import io.github.canjiemo.base.myjdbc.MyTableEntity;
import io.github.canjiemo.base.myjdbc.builder.SqlBuilder;
import io.github.canjiemo.base.myjdbc.builder.TableInfoBuilder;
import io.github.canjiemo.base.myjdbc.cache.TableCacheManager;
import io.github.canjiemo.base.myjdbc.configuration.MyJdbcProperties;
import io.github.canjiemo.base.myjdbc.dao.IBaseDao;
import io.github.canjiemo.base.myjdbc.error.MyJdbcErrorCode;
import io.github.canjiemo.base.myjdbc.metadata.TableInfo;
import io.github.canjiemo.base.myjdbc.parser.JSqlDynamicSqlParser;
import io.github.canjiemo.base.myjdbc.parser.SqlParser;
import io.github.canjiemo.base.myjdbc.rowmapper.MyBeanPropertyRowMapper;
import io.github.canjiemo.base.myjdbc.scope.MyJdbcScope;
import io.github.canjiemo.base.myjdbc.security.SqlOperationGuard;
import io.github.canjiemo.base.myjdbc.tenant.TenantAwareSqlParameterSource;
import io.github.canjiemo.base.myjdbc.tenant.TenantContext;
import io.github.canjiemo.base.myjdbc.tenant.TenantIdProvider;
import io.github.canjiemo.mycommon.exception.BusinessException;
import io.github.canjiemo.mycommon.pager.Pager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BaseDaoImpl implements IBaseDao {

	protected static Logger log = LoggerFactory.getLogger(BaseDaoImpl.class);
	private static final SingleColumnRowMapper<Long> COUNT_ROW_MAPPER = new SingleColumnRowMapper<>(Long.class);
	private static final int MAX_LOG_PARAM_LENGTH = 128;
	private static final int DEFAULT_QUERY_REWRITE_CACHE_SIZE = 1024;
	private final Map<Class<?>, RowMapper<?>> rowMapperCache = new ConcurrentHashMap<>();
	private final Map<String, ParsedSql> parsedSqlCache = new ConcurrentHashMap<>();
	// 高频 DAO 路径只读这些快照字段，避免每次执行 SQL 都走可变配置对象访问。
	private final boolean showSqlEnabled;
	private final boolean showSqlTimeEnabled;
	private final boolean tenantIsolationEnabled;
	private final String tenantColumn;
	private final String tenantColumnLowerCase;
	private final String tenantFieldName;
	private final SqlRewriteCache queryRewriteCache;
	private final SqlBuilder sqlBuilder;

	@Autowired
	protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public BaseDaoImpl(MyJdbcProperties properties, SqlBuilder sqlBuilder) {
		this(properties, DEFAULT_QUERY_REWRITE_CACHE_SIZE, sqlBuilder);
	}

	public BaseDaoImpl(MyJdbcProperties properties, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
	                  SqlBuilder sqlBuilder) {
		this(properties, DEFAULT_QUERY_REWRITE_CACHE_SIZE, sqlBuilder);
		this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
	}

	BaseDaoImpl(MyJdbcProperties properties, int queryRewriteCacheSize, SqlBuilder sqlBuilder) {
		this.showSqlEnabled = properties != null && properties.getShowSql().isEnabled();
		this.showSqlTimeEnabled = this.showSqlEnabled || (properties != null && properties.isShowSqlTime());
		this.tenantIsolationEnabled = properties != null && properties.getTenant().isEnabled();
		this.tenantColumn = resolveTenantColumn(properties);
		this.tenantColumnLowerCase = this.tenantColumn.toLowerCase(Locale.ROOT);
		this.tenantFieldName = io.github.canjiemo.base.myjdbc.utils.CommonUtils.underscoreToCamelCase(this.tenantColumn);
		this.queryRewriteCache = new SqlRewriteCache(queryRewriteCacheSize);
		this.sqlBuilder = sqlBuilder;
	}

	private <T> T executeWithTiming(String sql, java.util.function.Supplier<T> operation) {
		return executeWithTiming(sql, (SqlParameterSource) null, operation);
	}

	private <T> T executeWithTiming(String sql, @Nullable SqlParameterSource sps, java.util.function.Supplier<T> operation) {
		if (!showSqlEnabled && !showSqlTimeEnabled) return operation.get();
		logSqlPreview(sql, sps);
		long start = showSqlTimeEnabled ? System.currentTimeMillis() : 0L;
		try {
			return operation.get();
		} finally {
			logElapsed(sql, start);
		}
	}

	private <T> T executeWithTiming(String sql, @Nullable SqlParameterSource[] batchParams, java.util.function.Supplier<T> operation) {
		if (!showSqlEnabled && !showSqlTimeEnabled) return operation.get();
		logBatchSqlPreview(sql, batchParams);
		long start = showSqlTimeEnabled ? System.currentTimeMillis() : 0L;
		try {
			return operation.get();
		} finally {
			logElapsed(sql, start);
		}
	}

	private void logSqlPreview(String sql, @Nullable SqlParameterSource sps) {
		if (!showSqlEnabled || !log.isInfoEnabled()) {
			return;
		}
		try {
			SqlLogPreview preview = buildSqlLogPreviewCached(sql, sps);
			log.info("[MyJDBC] 执行语句: {}", preview.preparedSql());
			if (!preview.parameters().isBlank()) {
				log.info("[MyJDBC] 参数列表: {}", preview.parameters());
				log.info("[MyJDBC] 完整SQL : {}", preview.inlineSql());
			}
		} catch (Exception e) {
			log.debug("格式化 SQL 日志失败，回退为原始 SQL: {}", e.getMessage());
			log.info("[MyJDBC] 执行语句: {}", sql);
		}
	}

	private void logBatchSqlPreview(String sql, @Nullable SqlParameterSource[] batchParams) {
		if (!showSqlEnabled || !log.isInfoEnabled()) {
			return;
		}
		if (batchParams == null || batchParams.length == 0) {
			logSqlPreview(sql, (SqlParameterSource) null);
			return;
		}
		try {
			SqlLogPreview preview = buildSqlLogPreviewCached(sql, batchParams[0]);
			log.info("[MyJDBC] 执行语句(批量 x{}): {}", batchParams.length, preview.preparedSql());
			if (!preview.parameters().isBlank()) {
				log.info("[MyJDBC] 参数列表(首条): {}", preview.parameters());
				log.info("[MyJDBC] 完整SQL(首条) : {}", preview.inlineSql());
			}
		} catch (Exception e) {
			log.debug("格式化批量 SQL 日志失败，回退为原始 SQL: {}", e.getMessage());
			log.info("[MyJDBC] 执行语句(批量 x{}): {}", batchParams.length, sql);
		}
	}

	private void logElapsed(String sql, long start) {
		if (!showSqlTimeEnabled || !log.isInfoEnabled()) {
			return;
		}
		log.info("[MyJDBC] 执行时间: {}ms", System.currentTimeMillis() - start);
	}

	private SqlLogPreview buildSqlLogPreviewCached(String sql, @Nullable SqlParameterSource sps) {
		if (sps == null) {
			return new SqlLogPreview(sql, "", sql);
		}
		ParsedSql parsedSql = parsedSqlCache.computeIfAbsent(sql, NamedParameterUtils::parseSqlStatement);
		return buildSqlLogPreview(parsedSql, sps);
	}

	static SqlLogPreview buildSqlLogPreview(String sql, @Nullable SqlParameterSource sps) {
		if (sps == null) {
			return new SqlLogPreview(sql, "", sql);
		}
		return buildSqlLogPreview(NamedParameterUtils.parseSqlStatement(sql), sps);
	}

	private static SqlLogPreview buildSqlLogPreview(ParsedSql parsedSql, SqlParameterSource sps) {
		String preparedSql = NamedParameterUtils.substituteNamedParameters(parsedSql, sps);
		Object[] values = NamedParameterUtils.buildValueArray(parsedSql, sps, null);
		return new SqlLogPreview(preparedSql, formatParameters(values), buildInlineSql(preparedSql, values));
	}

	/**
	 * 将 preparedSql 中的 ? 占位符逐一替换为真实参数值，生成可直接在数据库客户端执行的 SQL。
	 * 字符串类型加单引号并转义内部单引号；数字/布尔直接输出；null 输出 NULL；byte[] 输出 '[bytes:N]'。
	 * Collection/数组参数会先展开为独立元素，与 preparedSql 中展开的多个 ? 一一对应。
	 */
	static String buildInlineSql(String preparedSql, Object[] values) {
		if (values == null || values.length == 0) return preparedSql;
		List<Object> flat = new ArrayList<>();
		for (Object v : values) {
			Object actual = unwrapParameterValue(v);
			if (actual instanceof Collection<?> col) {
				flat.addAll(col);
			} else if (actual instanceof Object[] arr) {
				flat.addAll(Arrays.asList(arr));
			} else {
				flat.add(actual);
			}
		}
		StringBuilder sb = new StringBuilder(preparedSql.length() + flat.size() * 4);
		int idx = 0;
		for (int i = 0; i < preparedSql.length(); i++) {
			if (preparedSql.charAt(i) == '?' && idx < flat.size()) {
				sb.append(formatValueInline(flat.get(idx++)));
			} else {
				sb.append(preparedSql.charAt(i));
			}
		}
		return sb.toString();
	}

	private static String formatValueInline(Object value) {
		Object actual = unwrapParameterValue(value);
		if (actual == null) return "NULL";
		if (actual instanceof Number || actual instanceof Boolean) return actual.toString();
		if (actual instanceof byte[] bytes) return "'[bytes:" + bytes.length + "]'";
		String text = actual.toString();
		if (text.length() > MAX_LOG_PARAM_LENGTH) text = text.substring(0, MAX_LOG_PARAM_LENGTH) + "...";
		return "'" + text.replace("'", "''") + "'";
	}

	private static String formatParameters(Object[] values) {
		if (values == null || values.length == 0) {
			return "";
		}
		List<String> formatted = new ArrayList<>(values.length);
		for (Object value : values) {
			appendFormattedParameter(formatted, value);
		}
		return String.join(", ", formatted);
	}

	private static void appendFormattedParameter(List<String> formatted, Object value) {
		Object actualValue = unwrapParameterValue(value);
		if (actualValue instanceof Collection<?> collection) {
			for (Object item : collection) {
				formatted.add(formatParameter(item));
			}
			return;
		}
		if (actualValue instanceof Object[] array) {
			for (Object item : array) {
				formatted.add(formatParameter(item));
			}
			return;
		}
		formatted.add(formatParameter(actualValue));
	}

	private static Object unwrapParameterValue(Object value) {
		return value instanceof SqlParameterValue sqlParameterValue ? sqlParameterValue.getValue() : value;
	}

	private static String formatParameter(Object value) {
		Object actualValue = unwrapParameterValue(value);
		if (actualValue == null) {
			return "null";
		}
		if (actualValue instanceof byte[] bytes) {
			return "[bytes:" + bytes.length + "](byte[])";
		}
		String text = String.valueOf(actualValue);
		if (text.length() > MAX_LOG_PARAM_LENGTH) {
			text = text.substring(0, MAX_LOG_PARAM_LENGTH) + "...";
		}
		return text + "(" + actualValue.getClass().getSimpleName() + ")";
	}

	record SqlLogPreview(String preparedSql, String parameters, String inlineSql) {}

	public boolean isWrapClass(Class<?> clz) {
		return BeanUtils.isSimpleValueType(clz) || clz == java.sql.Date.class;
	}

	@SuppressWarnings("unchecked")
	private <T> RowMapper<T> getRowMapper(Class<T> clazz) {
		return (RowMapper<T>) rowMapperCache.computeIfAbsent(clazz, c ->
				isWrapClass(c) ? new SingleColumnRowMapper<>(c) : new MyBeanPropertyRowMapper<>(c));
	}

	/** 租户ID提供者（SPI），集成方注册 Bean 后自动注入；未注册则为 null */
	@Autowired(required = false)
	private TenantIdProvider tenantIdProvider;

	protected JdbcTemplate getJdbcTemplate() {
		return (JdbcTemplate) this.namedParameterJdbcTemplate.getJdbcOperations();
	}

	/**
	 * 获取当前租户ID
	 * 优先级：TenantIdProvider SPI > TenantContext ThreadLocal
	 * 返回 null 表示超级管理员，不注入租户条件
	 */
	private Object getCurrentTenantId() {
		if (tenantIdProvider != null) {
			return tenantIdProvider.getTenantId();
		}
		return TenantContext.getTenantId();
	}

	/** 持有处理后的 SQL 和参数源 */
	private record ConditionResult(String sql, SqlParameterSource sps) {}

	/**
	 * 统一条件处理入口：根据是否需要租户选择解析路径。
	 *
	 * <ul>
	 *   <li>需要租户（enabled + 未跳过 + tenantId 非 null）→ 调用 {@code appendConditions}，
	 *       单次解析同时注入逻辑删除条件和租户条件</li>
	 *   <li>其他情况 → 调用 {@code appendDeleteCondition}，单次解析只注入逻辑删除条件</li>
	 * </ul>
	 *
	 * tenantId=null（超管）时不改写 SQL，避免 :myjdbcTenantId 占位符缺少参数导致运行时异常。
	 */
	private ConditionResult applyConditions(String sql, SqlParameterSource sps) {
		SqlOperationGuard.requireQueryStatement(sql, "queryForSql");
		boolean includeDelete = !MyJdbcScope.isLogicDeleteSkipped();
		boolean needTenant = tenantIsolationEnabled && !TenantContext.isSkipped();
		Object tenantId = needTenant ? getCurrentTenantId() : null;
		boolean includeTenant = tenantId != null;
		QueryRewriteMode mode = resolveQueryRewriteMode(includeDelete, includeTenant);
		if (mode == QueryRewriteMode.NONE) {
			return new ConditionResult(sql, sps);
		}
		String processedSql = rewriteQuerySql(sql, mode);
		if (includeTenant && processedSql.contains(":" + JSqlDynamicSqlParser.TENANT_PARAM_NAME)) {
			return new ConditionResult(processedSql,
					new TenantAwareSqlParameterSource(sps, JSqlDynamicSqlParser.TENANT_PARAM_NAME, tenantId));
		}
		return new ConditionResult(processedSql, sps);
	}

	String rewriteQuerySql(String sql, boolean includeTenantCondition) {
		return rewriteQuerySql(sql,
				includeTenantCondition ? QueryRewriteMode.DELETE_AND_TENANT : QueryRewriteMode.DELETE_ONLY);
	}

	/** SQL 改写缓存当前条目数。 */
	public int getQueryRewriteCacheSize() { return queryRewriteCache.size(); }

	/** SQL 改写缓存累计命中次数。 */
	public long getQueryRewriteCacheHits() { return queryRewriteCache.getHitCount(); }

	/** SQL 改写缓存累计未命中次数。 */
	public long getQueryRewriteCacheMisses() { return queryRewriteCache.getMissCount(); }

	private String rewriteQuerySql(String sql, QueryRewriteMode mode) {
		if (sql == null || sql.isBlank()) {
			return sql;
		}
		return queryRewriteCache.getOrLoad(new RewriteCacheKey(sql, mode, tenantColumn),
				BaseDaoImpl::loadRewrittenSql);
	}

	private static String loadRewrittenSql(RewriteCacheKey key) {
		return switch (key.mode()) {
			case NONE -> key.sql();
			case DELETE_ONLY -> JSqlDynamicSqlParser.appendDeleteCondition(key.sql());
			case TENANT_ONLY -> JSqlDynamicSqlParser.appendTenantCondition(key.sql(), true, key.tenantColumn());
			case DELETE_AND_TENANT -> JSqlDynamicSqlParser.appendConditions(key.sql(), true, key.tenantColumn());
		};
	}

	private static QueryRewriteMode resolveQueryRewriteMode(boolean includeDelete, boolean includeTenant) {
		if (includeDelete && includeTenant) {
			return QueryRewriteMode.DELETE_AND_TENANT;
		}
		if (includeDelete) {
			return QueryRewriteMode.DELETE_ONLY;
		}
		if (includeTenant) {
			return QueryRewriteMode.TENANT_ONLY;
		}
		return QueryRewriteMode.NONE;
	}

	private <T> T querySingleInternal(String sql, SqlParameterSource sps, Class<T> clazz) {
		var r = applyConditions(sql, sps);
		RowMapper<T> rowMapper = getRowMapper(clazz);
		return executeWithTiming(r.sql(), r.sps(), () ->
				namedParameterJdbcTemplate.query(r.sql(), r.sps(), rs -> rs.next() ? rowMapper.mapRow(rs, 0) : null));
	}

	private BusinessException businessError(String op, Exception e, String message, Object... details) {
		String detailText = (details == null || details.length == 0) ? "" : ", details=" + java.util.Arrays.toString(details);
		log.error("DAO操作失败: {}{}", op, detailText, e);
		return new BusinessException(message);
	}

	// ===================== 写操作租户 helper =====================

	/**
	 * 获取写操作所需的租户 ID。
	 * 返回 null 表示不需要注入（全局关闭 / 跳过 / 超管 / 表无租户列）。
	 */
	private Object getWriteTenantId(String tableName) {
		if (!tenantIsolationEnabled || TenantContext.isSkipped() || MyJdbcScope.isTenantSkipped()) return null;
		if (!TableCacheManager.hasTenantColumn(tableName)) return null;
		return getCurrentTenantId();
	}

	/**
	 * 为 UPDATE / 逻辑DELETE SQL 的 WHERE 子句追加租户条件。
	 * UPDATE table SET ... WHERE pk=:pk  →  ... WHERE pk=:pk AND tenant_id=:myjdbcTenantId
	 */
	private ConditionResult applyWriteConditions(String sql, SqlParameterSource sps, String tableName) {
		Object tenantId = getWriteTenantId(tableName);
		if (tenantId == null) return new ConditionResult(sql, sps);
		String processedSql = appendTenantConditionToWriteSql(sql);
		return new ConditionResult(processedSql,
				new TenantAwareSqlParameterSource(sps, JSqlDynamicSqlParser.TENANT_PARAM_NAME, tenantId));
	}

	private String appendTenantConditionToWriteSql(String sql) {
		String tenantCondition = tenantColumn + " = :" + JSqlDynamicSqlParser.TENANT_PARAM_NAME;
		if (sql == null || sql.isBlank()) {
			return sql;
		}
		if (sql.matches("(?is).*\\bwhere\\b.*")) {
			return sql + " AND " + tenantCondition;
		}
		return sql + " WHERE " + tenantCondition;
	}

	/** 在类继承链中查找字段（支持父类）。 */
	private static Field findFieldInHierarchy(Class<?> clazz, String fieldName) {
		for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
			try { return c.getDeclaredField(fieldName); } catch (NoSuchFieldException ignored) {}
		}
		return null;
	}

	/** 通过反射将租户 ID 写入 PO 字段（仅当字段当前为 null 时才写入，自动做类型适配）。 */
	private <PO extends MyTableEntity> void setTenantField(Field field, PO po, Object tenantId) {
		try {
			if (field.get(po) != null) return;
			Class<?> ft = field.getType();
			Object val = tenantId;
			if (ft == Long.class || ft == long.class)        val = Long.parseLong(tenantId.toString());
			else if (ft == Integer.class || ft == int.class) val = Integer.parseInt(tenantId.toString());
			else if (ft == String.class)                     val = tenantId.toString();
			field.set(po, val);
		} catch (Exception e) {
			log.warn("自动填充租户字段 {} 失败: {}", field.getName(), e.getMessage());
		}
	}

	@Override
	public <T> List<T> queryListForSql(String sql, Object param, Class<T> clazz) {
		SqlParameterSource sps = param == null
				? new EmptySqlParameterSource()
				: new BeanPropertySqlParameterSource(param);
		var r = applyConditions(sql, sps);
		return executeWithTiming(r.sql(), r.sps(), () -> namedParameterJdbcTemplate.query(r.sql(), r.sps(), getRowMapper(clazz)));
	}

	@Override
	public <T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		SqlParameterSource sps = (param == null || param.isEmpty())
				? new EmptySqlParameterSource()
				: new MapSqlParameterSource(param);
		var r = applyConditions(sql, sps);
		return executeWithTiming(r.sql(), r.sps(), () -> namedParameterJdbcTemplate.query(r.sql(), r.sps(), getRowMapper(clazz)));
	}

	@Override
	public <T> T querySingleForSql(String sql, Object param, Class<T> clazz) {
		SqlParameterSource sps = param == null
				? new EmptySqlParameterSource()
				: new BeanPropertySqlParameterSource(param);
		return querySingleInternal(sql, sps, clazz);
	}

	@Override
	public <T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		SqlParameterSource sps = (param == null || param.isEmpty())
				? new EmptySqlParameterSource()
				: new MapSqlParameterSource(param);
		return querySingleInternal(sql, sps, clazz);
	}

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz) {
		SqlParameterSource sps = param == null
				? new EmptySqlParameterSource()
				: new BeanPropertySqlParameterSource(param);
		var r = applyConditions(sql, sps);
		if (!pager.getIgnoreCount()) {
			String countSql = "select count(*) from ( " + r.sql() + " ) mkt_page_count";
			pager.setTotalRows(executeWithTiming(countSql, r.sps(), () -> namedParameterJdbcTemplate.queryForObject(countSql, r.sps(), COUNT_ROW_MAPPER)));
			if (pager.getTotalRows() > 0) {
				String pageSql = this.sqlBuilder.buildPagerSql(r.sql(), pager);
				pager.setPageData(executeWithTiming(pageSql, r.sps(), () -> namedParameterJdbcTemplate.query(pageSql, r.sps(), getRowMapper(clazz))));
			} else {
				pager.setPageData(new ArrayList<>());
			}
		} else {
			String pageSql = this.sqlBuilder.buildPagerSql(r.sql(), pager);
			pager.setPageData(executeWithTiming(pageSql, r.sps(), () -> namedParameterJdbcTemplate.query(pageSql, r.sps(), getRowMapper(clazz))));
		}
		return pager;
	}

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz) {
		SqlParameterSource sps = (param == null || param.isEmpty())
				? new EmptySqlParameterSource()
				: new MapSqlParameterSource(param);
		var r = applyConditions(sql, sps);
		if (!pager.getIgnoreCount()) {
			String countSql = "select count(*) from ( " + r.sql() + " ) mkt_page_count";
			pager.setTotalRows(executeWithTiming(countSql, r.sps(), () -> namedParameterJdbcTemplate.queryForObject(countSql, r.sps(), COUNT_ROW_MAPPER)));
			if (pager.getTotalRows() > 0) {
				String pageSql = this.sqlBuilder.buildPagerSql(r.sql(), pager);
				pager.setPageData(executeWithTiming(pageSql, r.sps(), () -> namedParameterJdbcTemplate.query(pageSql, r.sps(), getRowMapper(clazz))));
			} else {
				pager.setPageData(new ArrayList<>());
			}
		} else {
			String pageSql = this.sqlBuilder.buildPagerSql(r.sql(), pager);
			pager.setPageData(executeWithTiming(pageSql, r.sps(), () -> namedParameterJdbcTemplate.query(pageSql, r.sps(), getRowMapper(clazz))));
		}
		return pager;
	}

	@Override
	public <PO extends MyTableEntity> PO queryById(Object id, Class<PO> clazz) {
		TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
		String sql = SqlParser.getSelectByIdSql(tableInfo);
		Map<String, Object> param = new HashMap<>();
		param.put(tableInfo.getPkFieldName(), id);
		return querySingleForSql(sql, param, clazz);
	}

	@Override
	public <PO extends MyTableEntity> Serializable insertPO(PO po, boolean autoCreateId) {
		try {
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(po.getClass());
			if (autoCreateId) tableInfo.setPkValue(po);

			String sql = SqlParser.getInsertSql(tableInfo, po);
			SqlParameterSource paramSource = new BeanPropertySqlParameterSource(po);

			// 租户处理：SQL 未含租户列（ignoreNull=true 时字段为 null 被跳过）→ 追加列+参数
			Object tenantId = getWriteTenantId(tableInfo.getTableName());
			if (tenantId != null && !sql.toLowerCase(Locale.ROOT).contains(tenantColumnLowerCase)) {
				sql = JSqlDynamicSqlParser.appendTenantToInsertSql(sql, true, tenantColumn);
				paramSource = new TenantAwareSqlParameterSource(paramSource, JSqlDynamicSqlParser.TENANT_PARAM_NAME, tenantId);
			}

			final String fSql = sql;
			final SqlParameterSource fPs = paramSource;
			if (autoCreateId) {
				executeWithTiming(fSql, fPs, () -> namedParameterJdbcTemplate.update(fSql, fPs));
				return (Serializable) tableInfo.getPkValue(po);
			} else {
				Object pkValue = tableInfo.getPkValue(po);
				if (pkValue != null) {
					executeWithTiming(fSql, fPs, () -> namedParameterJdbcTemplate.update(fSql, fPs));
					return (Serializable) pkValue;
				}
				KeyHolder holder = new GeneratedKeyHolder();
				executeWithTiming(fSql, fPs, () -> namedParameterJdbcTemplate.update(fSql, fPs, holder));
				long id = holder.getKey().longValue();
				tableInfo.setPkValue(po, id);
				return id;
			}
		} catch (Exception e) {
			if (e instanceof DuplicateKeyException) {
				throw (DuplicateKeyException) e;
			} else {
				throw businessError("insertPO", e, MyJdbcErrorCode.DAO_ERROR.userMessage(), po != null ? po.getClass().getName() : "null");
			}
		}
	}

	@Override
	public <PO extends MyTableEntity> int updatePO(PO po) {
		return updatePO(po, true);
	}

	@Override
	public <PO extends MyTableEntity> int updatePO(PO po, boolean ignoreNull) {
		return updatePO(po, ignoreNull, (String[]) null);
	}

	@Override
	public <PO extends MyTableEntity> int updatePO(PO po, @Nullable String... forceUpdateFields) {
		return updatePO(po, true, forceUpdateFields);
	}

	private <PO extends MyTableEntity> int updatePO(PO po, boolean ignoreNull, @Nullable String... forceUpdateFields) {
		try {
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(po.getClass());
			String sql = SqlParser.getUpdateSql(tableInfo, po, ignoreNull, forceUpdateFields);
			SqlParameterSource paramSource = new BeanPropertySqlParameterSource(po);
			var r = applyWriteConditions(sql, paramSource, tableInfo.getTableName());
			return executeWithTiming(r.sql(), r.sps(), () -> namedParameterJdbcTemplate.update(r.sql(), r.sps()));
		} catch (Exception e) {
			throw businessError("updatePO", e, MyJdbcErrorCode.DAO_ERROR.userMessage(), po != null ? po.getClass().getName() : "null");
		}
	}

	@Override
	public <PO extends MyTableEntity> int updateForSql(String sql, Map<String, Object> param, Class<PO> clazz) {
		try {
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
			SqlOperationGuard.requireUpdateStatement(sql, MyJdbcScope.isUnsafeWriteAllowed(), "updateForSql");
			SqlParameterSource sps = (param == null || param.isEmpty())
					? new EmptySqlParameterSource()
					: new MapSqlParameterSource(param);
			var r = applyWriteConditions(sql, sps, tableInfo.getTableName());
			return executeWithTiming(r.sql(), r.sps(), () -> namedParameterJdbcTemplate.update(r.sql(), r.sps()));
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw businessError("updateForSql", e, MyJdbcErrorCode.DAO_ERROR.userMessage(),
					clazz != null ? clazz.getName() : "null");
		}
	}

	@Override
	public <PO extends MyTableEntity> int delPO(PO po) {
		try {
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(po.getClass());
			String sql = SqlParser.getDelByIdSql(tableInfo);
			MapSqlParameterSource sps = new MapSqlParameterSource(tableInfo.getPkFieldName(), tableInfo.getPkValue(po));
			var r = applyWriteConditions(sql, sps, tableInfo.getTableName());
			return executeWithTiming(r.sql(), r.sps(), () -> namedParameterJdbcTemplate.update(r.sql(), r.sps()));
		} catch (Exception e) {
			throw businessError("delPO", e, MyJdbcErrorCode.DAO_ERROR.userMessage(), po != null ? po.getClass().getName() : "null");
		}
	}

	@Override
	public <PO extends MyTableEntity> int delByIds(Class<PO> clazz, Object... id) {
		try {
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(clazz);
			String sql = SqlParser.getDelByIdsSql(tableInfo);
			Object tenantId = getWriteTenantId(tableInfo.getTableName());
			SqlParameterSource[] params;
			if (tenantId != null) {
				sql = sql + " AND " + tenantColumn
						+ " = :" + JSqlDynamicSqlParser.TENANT_PARAM_NAME;
				List<SqlParameterSource> spsList = new ArrayList<>(id.length);
				for (Object o : id) {
					spsList.add(new TenantAwareSqlParameterSource(
							new MapSqlParameterSource(tableInfo.getPkFieldName(), o),
							JSqlDynamicSqlParser.TENANT_PARAM_NAME, tenantId));
				}
				params = spsList.toArray(new SqlParameterSource[0]);
			} else {
				List<Map<String, Object>> beanList = new ArrayList<>(id.length);
				for (Object o : id) {
					Map<String, Object> map = new HashMap<>();
					map.put(tableInfo.getPkFieldName(), o);
					beanList.add(map);
				}
				params = SqlParameterSourceUtils.createBatch(beanList);
			}
			final String fSql = sql;
			final SqlParameterSource[] fParams = params;
			return executeWithTiming(fSql, fParams, () -> namedParameterJdbcTemplate.batchUpdate(fSql, fParams)).length;
		} catch (Exception e) {
			throw businessError("delByIds", e, MyJdbcErrorCode.DAO_ERROR.userMessage(), clazz != null ? clazz.getName() : "null",
					id == null ? 0 : id.length);
		}
	}

	@Override
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId) {
		if (pos == null || pos.isEmpty()) return 0;
		try {
			TableInfo tableInfo = TableInfoBuilder.getTableInfo(pos.get(0).getClass());
			if (autoCreateId) {
				for (PO po : pos) TableInfoBuilder.getTableInfo(po.getClass()).setPkValue(po);
			}

			// ignoreNull=false：保证批次内所有行 schema 一致
			String sql = SqlParser.getInsertSql(tableInfo, pos.get(0), false);
			Object tenantId = getWriteTenantId(tableInfo.getTableName());
			SqlParameterSource[] params;

			if (tenantId != null) {
				boolean sqlHasTenantCol = sql.toLowerCase(Locale.ROOT).contains(tenantColumnLowerCase);
				if (sqlHasTenantCol) {
					// POJO 有租户字段（ignoreNull=false 包含了它）→ 反射批量赋值（字段为 null 时赋值）
					Field tenantField = findFieldInHierarchy(pos.get(0).getClass(), tenantFieldName);
					if (tenantField != null) {
						tenantField.setAccessible(true);
						for (PO po : pos) setTenantField(tenantField, po, tenantId);
					}
					params = SqlParameterSourceUtils.createBatch(pos);
				} else {
					// POJO 没有租户字段 → SQL 追加列，每个元素包装 TenantAwareSqlParameterSource
					sql = JSqlDynamicSqlParser.appendTenantToInsertSql(sql, true, tenantColumn);
					final Object tid = tenantId;
					List<SqlParameterSource> spsList = new ArrayList<>(pos.size());
					for (PO po : pos) {
						spsList.add(new TenantAwareSqlParameterSource(
								new BeanPropertySqlParameterSource(po), JSqlDynamicSqlParser.TENANT_PARAM_NAME, tid));
					}
					params = spsList.toArray(new SqlParameterSource[0]);
				}
			} else {
				params = SqlParameterSourceUtils.createBatch(pos);
			}

			final String fSql = sql;
			final SqlParameterSource[] fParams = params;
			int[] counts = executeWithTiming(fSql, fParams, () -> namedParameterJdbcTemplate.batchUpdate(fSql, fParams));
			return counts.length;
		} catch (Exception e) {
			throw businessError("batchInsertPO", e, MyJdbcErrorCode.DAO_ERROR.userMessage(), pos.get(0).getClass().getName(), pos.size());
		}
	}

	@Override
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize) {
		if (pos == null || pos.isEmpty()) return 0;
		if (batchSize <= 0) {
			throw new IllegalArgumentException("batchSize must be greater than 0");
		}
		int totalSize = pos.size();
		int batchCount = (int) Math.ceil((double) totalSize / batchSize);
		int currentIndex = 0;
		int affected = 0;
		for (int i = 0; i < batchCount; i++) {
			int remainingSize = totalSize - currentIndex;
			int currentBatchSize = Math.min(batchSize, remainingSize);
			List<PO> batchList = pos.subList(currentIndex, currentIndex + currentBatchSize);
			Serializable result = this.batchInsertPO(batchList, autoCreateId);
			if (result instanceof Number n) {
				affected += n.intValue();
			}
			currentIndex += currentBatchSize;
		}
		return affected;
	}

	private static String resolveTenantColumn(MyJdbcProperties properties) {
		if (properties == null || properties.getTenant().getColumn() == null || properties.getTenant().getColumn().isBlank()) {
			return "tenant_id";
		}
		return properties.getTenant().getColumn().trim();
	}

	private enum QueryRewriteMode {
		NONE,
		DELETE_ONLY,
		TENANT_ONLY,
		DELETE_AND_TENANT
	}

	private record RewriteCacheKey(String sql, QueryRewriteMode mode, String tenantColumn) {}

	private static final class SqlRewriteCache {
		private final int maxSize;
		private final LinkedHashMap<RewriteCacheKey, String> delegate;
		private final java.util.concurrent.atomic.AtomicLong hitCount  = new java.util.concurrent.atomic.AtomicLong();
		private final java.util.concurrent.atomic.AtomicLong missCount = new java.util.concurrent.atomic.AtomicLong();

		private SqlRewriteCache(int maxSize) {
			this.maxSize = Math.max(1, maxSize);
			this.delegate = new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<RewriteCacheKey, String> eldest) {
					return size() > SqlRewriteCache.this.maxSize;
				}
			};
		}

		String getOrLoad(RewriteCacheKey key, java.util.function.Function<RewriteCacheKey, String> loader) {
			synchronized (this) {
				String cached = delegate.get(key);
				if (cached != null) {
					hitCount.incrementAndGet();
					return cached;
				}
			}
			missCount.incrementAndGet();
			String loaded = loader.apply(key);
			synchronized (this) {
				String cached = delegate.get(key);
				if (cached != null) {
					return cached;
				}
				delegate.put(key, loaded);
				return loaded;
			}
		}

		synchronized int size() { return delegate.size(); }
		long getHitCount()      { return hitCount.get(); }
		long getMissCount()     { return missCount.get(); }
	}

}
