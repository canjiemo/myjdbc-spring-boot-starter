package io.github.canjiemo.base.myjdbc.cache;

import io.github.canjiemo.base.myjdbc.annotation.MyTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyTable 注解信息缓存管理器。
 *
 * <p>内部维护一个 {@link #INSTANCE} 单例，Spring 自动配置将该实例注册为 Bean，
 * 新代码可以直接注入 {@code TableCacheManager}；历史静态 API 继续委托到同一实例，
 * 不需要修改现有调用点（{@link io.github.canjiemo.base.myjdbc.parser.JSqlDynamicSqlParser}、
 * {@link io.github.canjiemo.base.myjdbc.validation.DatabaseSchemaValidator} 等）。
 */
public class TableCacheManager {

	private static final Logger log = LoggerFactory.getLogger(TableCacheManager.class);

	/** 全局单例，由自动配置注册为 Spring Bean。 */
	private static final TableCacheManager INSTANCE = new TableCacheManager();

	/** 返回全局单例，供自动配置将其注册为 Bean。 */
	public static TableCacheManager getInstance() {
		return INSTANCE;
	}

	// ── 实例级缓存（原先是 static final 字段）────────────────────────────

	/** 表名 → 删除条件 */
	private final Map<String, DeleteInfo> tableDeleteInfoCache = new ConcurrentHashMap<>();

	/** 类全限定名 → 删除条件 */
	private final Map<String, DeleteInfo> classDeleteInfoCache = new ConcurrentHashMap<>();

	/** 类全限定名 → 表名 */
	private final Map<String, String> classTableNameCache = new ConcurrentHashMap<>();

	/** 表名 → 主键信息 */
	private final Map<String, PkInfo> tablePkInfoCache = new ConcurrentHashMap<>();

	/** 数据库中实际存在租户字段的表名集合（由 DatabaseSchemaValidator 填充） */
	private final Set<String> tableTenantCache = Collections.newSetFromMap(new ConcurrentHashMap<>());

	// ── 内部数据结构 ────────────────────────────────────────────────────

	public static class DeleteInfo {
		private final String delColumn;
		private final String delField;
		private final int delValue;
		private boolean isValid = true;

		public DeleteInfo(String delColumn, String delField, int delValue) {
			this.delColumn = delColumn;
			this.delField = delField;
			this.delValue = delValue;
		}

		public String getDelColumn() { return delColumn; }
		public String getDelField()  { return delField; }
		public int    getDelValue()  { return delValue; }
		public int    getUnDelValue() { return delValue == 0 ? 1 : 0; }
		public boolean isValid()     { return isValid; }
		public void setValid(boolean valid) { isValid = valid; }
	}

	public static class PkInfo {
		private final String pkColumn;
		private final String pkField;

		public PkInfo(String pkColumn, String pkField) {
			this.pkColumn = pkColumn;
			this.pkField = pkField;
		}

		public String getPkColumn() { return pkColumn; }
		public String getPkField()  { return pkField; }
	}

	// ── 实例方法（可注入后直接调用）────────────────────────────────────

	public synchronized void init(String... basePackages) {
		log.info("开始扫描@MyTable注解的类并构建缓存...");
		clear();
		try {
			for (String basePackage : basePackages) {
				scanPackage(basePackage);
			}
			log.info("@MyTable注解缓存构建完成，共缓存{}个表的删除信息", tableDeleteInfoCache.size());
		} catch (Exception e) {
			log.error("初始化@MyTable注解缓存时发生异常", e);
		}
	}

	public synchronized void clear() {
		tableDeleteInfoCache.clear();
		classDeleteInfoCache.clear();
		classTableNameCache.clear();
		tablePkInfoCache.clear();
		tableTenantCache.clear();
		log.info("@MyTable注解缓存已清空");
	}

	public DeleteInfo findDeleteInfoByTableName(String tableName) {
		if (tableName == null) return null;
		return tableDeleteInfoCache.get(tableName.toLowerCase());
	}

	public DeleteInfo findDeleteInfoByClassName(String className) {
		if (className == null) return null;
		return classDeleteInfoCache.get(className);
	}

	public DeleteInfo findDeleteInfoByClass(Class<?> clazz) {
		if (clazz == null) return null;
		return classDeleteInfoCache.get(clazz.getName());
	}

	public String findTableNameByClass(Class<?> clazz) {
		if (clazz == null) return null;
		return classTableNameCache.get(clazz.getName());
	}

	public boolean existsDeleteCondition(String tableName) {
		DeleteInfo info = findDeleteInfoByTableName(tableName);
		return info != null && info.isValid()
				&& info.getDelColumn() != null && !info.getDelColumn().isBlank();
	}

	public boolean existsDeleteCondition(Class<?> clazz) {
		DeleteInfo info = findDeleteInfoByClass(clazz);
		return info != null && info.isValid()
				&& info.getDelColumn() != null && !info.getDelColumn().isBlank();
	}

	public Set<String> allTableNames() {
		return new HashSet<>(tableDeleteInfoCache.keySet());
	}

	public PkInfo findPkInfoByTableName(String tableName) {
		if (tableName == null) return null;
		return tablePkInfoCache.get(tableName.toLowerCase());
	}

	public void invalidateDeleteField(String tableName) {
		if (tableName == null) return;
		DeleteInfo info = tableDeleteInfoCache.get(tableName.toLowerCase());
		if (info != null) {
			info.setValid(false);
			log.info("标记表 '{}' 的删除字段 '{}' 为无效，将跳过删除条件拼接", tableName, info.getDelColumn());
		}
	}

	public void addTenantTable(String tableName) {
		if (tableName == null) return;
		tableTenantCache.add(tableName.toLowerCase());
		log.info("注册租户隔离表: {}", tableName);
	}

	public boolean containsTenantColumn(String tableName) {
		if (tableName == null) return false;
		return tableTenantCache.contains(tableName.toLowerCase());
	}

	public String stats() {
		return String.format("TableCache: %d tables, ClassCache: %d classes",
				tableDeleteInfoCache.size(), classDeleteInfoCache.size());
	}

	// ── 静态 API（向后兼容，委托到 INSTANCE）──────────────────────────

	public static synchronized void initCache(String... basePackages) {
		INSTANCE.init(basePackages);
	}

	public static DeleteInfo getDeleteInfoByTableName(String tableName) {
		return INSTANCE.findDeleteInfoByTableName(tableName);
	}

	public static DeleteInfo getDeleteInfoByClassName(String className) {
		return INSTANCE.findDeleteInfoByClassName(className);
	}

	public static DeleteInfo getDeleteInfoByClass(Class<?> clazz) {
		return INSTANCE.findDeleteInfoByClass(clazz);
	}

	public static String getTableNameByClass(Class<?> clazz) {
		return INSTANCE.findTableNameByClass(clazz);
	}

	public static boolean hasDeleteCondition(String tableName) {
		return INSTANCE.existsDeleteCondition(tableName);
	}

	public static boolean hasDeleteCondition(Class<?> clazz) {
		return INSTANCE.existsDeleteCondition(clazz);
	}

	public static Set<String> getAllTableNames() {
		return INSTANCE.allTableNames();
	}

	public static PkInfo getPkInfoByTableName(String tableName) {
		return INSTANCE.findPkInfoByTableName(tableName);
	}

	public static void markDeleteFieldAsInvalid(String tableName) {
		INSTANCE.invalidateDeleteField(tableName);
	}

	public static void registerTenantTable(String tableName) {
		INSTANCE.addTenantTable(tableName);
	}

	public static boolean hasTenantColumn(String tableName) {
		return INSTANCE.containsTenantColumn(tableName);
	}

	public static synchronized void clearCache() {
		INSTANCE.clear();
	}

	public static String getCacheStats() {
		return INSTANCE.stats();
	}

	// ── 私有扫描逻辑 ────────────────────────────────────────────────────

	private void scanPackage(String basePackage) {
		ClassPathScanningCandidateComponentProvider scanner =
				new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(MyTable.class));
		try {
			Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
			for (BeanDefinition bd : candidates) {
				try {
					Class<?> clazz = Class.forName(bd.getBeanClassName());
					MyTable myTable = clazz.getAnnotation(MyTable.class);
					if (myTable == null) continue;

					String tableName = myTable.value();
					String className = clazz.getName();

					DeleteInfo deleteInfo = new DeleteInfo(
							myTable.delColumn(), myTable.delField(), myTable.delValue());
					PkInfo pkInfo = new PkInfo(myTable.pkColumn(), myTable.pkField());

					tableDeleteInfoCache.put(tableName.toLowerCase(), deleteInfo);
					classDeleteInfoCache.put(className, deleteInfo);
					classTableNameCache.put(className, tableName);
					tablePkInfoCache.put(tableName.toLowerCase(), pkInfo);

					log.info("缓存表删除信息: table={}, class={}, delColumn={}, delValue={}",
							tableName, className, myTable.delColumn(), myTable.delValue());
				} catch (Exception e) {
					log.warn("处理类{}的@MyTable注解时发生异常: {}", bd.getBeanClassName(), e.getMessage());
				}
			}
		} catch (Exception e) {
			log.warn("扫描包 {} 时发生异常: {}", basePackage, e.getMessage());
		}
	}
}
