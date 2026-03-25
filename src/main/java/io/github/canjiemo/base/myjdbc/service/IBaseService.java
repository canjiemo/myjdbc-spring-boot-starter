package io.github.canjiemo.base.myjdbc.service;

import io.github.canjiemo.base.myjdbc.MyTableEntity;
import io.github.canjiemo.mycommon.pager.Pager;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


/**
 * 基础 Service 接口，提供通用的增删改查操作。
 *
 * <p>业务 Service 接口继承此接口即可获得全套 CRUD 能力，
 * 配合 {@code BaseServiceImpl} 使用，无需重复实现。
 */
public interface IBaseService {

	// ======================== 原生 SQL 查询 ========================

	/**
	 * 分页查询（参数为任意 JavaBean 对象）。
	 *
	 * <p>SQL 中使用 {@code :fieldName} 形式的命名占位符，框架自动从 param 对象中提取同名字段值。
	 *
	 * <pre>{@code
	 * String sql = "SELECT * FROM user WHERE status = :status ORDER BY create_time DESC";
	 * UserQuery query = new UserQuery();
	 * query.setStatus(1);
	 * Pager<User> page = queryPageForSql(sql, query, new Pager<>(1, 10), User.class);
	 * }</pre>
	 *
	 * @param sql   带命名占位符的查询 SQL
	 * @param param 参数对象（JavaBean），字段名与占位符对应
	 * @param pager 分页对象，包含页码与每页大小
	 * @param clazz 结果类型
	 */
	<T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz);

	/**
	 * 列表查询（参数为任意 JavaBean 对象）。
	 *
	 * <pre>{@code
	 * String sql = "SELECT * FROM user WHERE dept_id = :deptId AND status = :status";
	 * UserQuery query = new UserQuery();
	 * query.setDeptId(10L);
	 * query.setStatus(1);
	 * List<User> list = queryListForSql(sql, query, User.class);
	 * }</pre>
	 *
	 * @param sql   带命名占位符的查询 SQL
	 * @param param 参数对象（JavaBean）
	 * @param clazz 结果类型
	 */
	<T> List<T> queryListForSql(String sql, Object param, Class<T> clazz);

	/**
	 * 查询单条记录（参数为任意 JavaBean 对象）。
	 *
	 * <p>若结果为空返回 {@code null}；若结果多于一条，取第一条。
	 *
	 * <pre>{@code
	 * String sql = "SELECT * FROM user WHERE id = :id";
	 * UserQuery query = new UserQuery();
	 * query.setId(123L);
	 * User user = querySingleForSql(sql, query, User.class);
	 * }</pre>
	 *
	 * @param sql   带命名占位符的查询 SQL
	 * @param param 参数对象（JavaBean）
	 * @param clazz 结果类型
	 */
	<T> T querySingleForSql(String sql, Object param, Class<T> clazz);

	/**
	 * 分页查询（参数为 {@code Map<String, Object>}）。
	 *
	 * <pre>{@code
	 * String sql = "SELECT * FROM user WHERE status = :status";
	 * Map<String, Object> params = Map.of("status", 1);
	 * Pager<User> page = queryPageForSql(sql, params, new Pager<>(1, 10), User.class);
	 * }</pre>
	 *
	 * @param sql   带命名占位符的查询 SQL
	 * @param param 参数 Map，key 与占位符名称对应
	 * @param pager 分页对象
	 * @param clazz 结果类型
	 */
	<T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz);

	/**
	 * 列表查询（参数为 {@code Map<String, Object>}）。
	 *
	 * <pre>{@code
	 * String sql = "SELECT * FROM user WHERE dept_id = :deptId";
	 * List<User> list = queryListForSql(sql, Map.of("deptId", 10L), User.class);
	 * }</pre>
	 *
	 * @param sql   带命名占位符的查询 SQL
	 * @param param 参数 Map
	 * @param clazz 结果类型
	 */
	<T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz);

	/**
	 * 查询单条记录（参数为 {@code Map<String, Object>}）。
	 *
	 * <p>若结果为空返回 {@code null}。
	 *
	 * <pre>{@code
	 * String sql = "SELECT count(*) FROM user WHERE status = :status";
	 * Long count = querySingleForSql(sql, Map.of("status", 1), Long.class);
	 * }</pre>
	 *
	 * @param sql   带命名占位符的查询 SQL
	 * @param param 参数 Map
	 * @param clazz 结果类型
	 */
	<T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz);

	// ======================== 插入 ========================

	/**
	 * 插入单条实体。
	 *
	 * @param po           实体对象
	 * @param autoCreateId 是否自动生成主键（{@code true} 时由框架生成 ID）
	 * @return 插入后的主键值
	 */
	<PO extends MyTableEntity> Serializable insertPO(PO po, boolean autoCreateId);

	/**
	 * 插入单条实体（默认自动生成主键）。
	 *
	 * <pre>{@code
	 * User user = new User();
	 * user.setName("张三");
	 * user.setStatus(1);
	 * Serializable id = insertPO(user);
	 * }</pre>
	 *
	 * @param po 实体对象
	 * @return 插入后的主键值
	 */
	<PO extends MyTableEntity> Serializable insertPO(PO po);

	/**
	 * 批量插入（默认批次大小）。
	 *
	 * @param pos          实体列表
	 * @param autoCreateId 是否自动生成主键
	 * @return 最后一条记录的主键值
	 */
	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId);

	/**
	 * 批量插入（默认批次大小，默认自动生成主键）。
	 *
	 * <pre>{@code
	 * List<User> users = buildUserList();
	 * batchInsertPO(users);
	 * }</pre>
	 *
	 * @param pos 实体列表
	 * @return 最后一条记录的主键值
	 */
	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos);

	/**
	 * 批量插入（指定批次大小）。
	 *
	 * <p>数据量较大时建议指定 {@code batchSize}（如 500），避免单次 SQL 过长。
	 *
	 * @param pos          实体列表
	 * @param autoCreateId 是否自动生成主键
	 * @param batchSize    每批次插入的条数
	 * @return 最后一条记录的主键值
	 */
	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize);

	/**
	 * 批量插入（指定批次大小，默认自动生成主键）。
	 *
	 * <pre>{@code
	 * batchInsertPO(users, 500);
	 * }</pre>
	 *
	 * @param pos       实体列表
	 * @param batchSize 每批次插入的条数
	 * @return 最后一条记录的主键值
	 */
	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, int batchSize);

	// ======================== 更新 ========================

	/**
	 * 按主键更新实体（默认跳过值为 {@code null} 的字段）。
	 *
	 * <pre>{@code
	 * User user = new User();
	 * user.setId(123L);
	 * user.setStatus(2); // 只更新 status，其他字段不变
	 * updatePO(user);
	 * }</pre>
	 *
	 * @param po 实体对象，主键必须有值
	 * @return 影响行数
	 */
	<PO extends MyTableEntity> int updatePO(PO po);

	/**
	 * 按主键更新实体，可控制是否跳过 {@code null} 字段。
	 *
	 * @param po         实体对象，主键必须有值
	 * @param ignoreNull {@code true} 跳过 null 字段（只更新有值的字段）；
	 *                   {@code false} 将 null 字段也写入数据库
	 * @return 影响行数
	 */
	<PO extends MyTableEntity> int updatePO(PO po, boolean ignoreNull);

	/**
	 * 按主键更新实体，并强制更新指定属性（即使其值为 {@code null}）。
	 *
	 * <p>适用于需要将某些字段显式置为 null 或空字符串的场景。
	 *
	 * <pre>{@code
	 * user.setRemark(null); // 需要清空 remark 字段
	 * updatePO(user, "remark"); // remark 会被强制更新为 null
	 * }</pre>
	 *
	 * @param po                    实体对象，主键必须有值
	 * @param forceUpdateProperties 需要强制更新的属性名（Java 字段名），可传多个
	 * @return 影响行数
	 */
	<PO extends MyTableEntity> int updatePO(PO po, @Nullable String... forceUpdateProperties);

	// ======================== 查询 ========================

	/**
	 * 按字符串类型主键查询单条实体。
	 *
	 * <pre>{@code
	 * User user = queryById("abc123", User.class);
	 * }</pre>
	 *
	 * @param id    主键值（String 类型）
	 * @param clazz 实体类
	 * @return 实体对象，不存在则返回 {@code null}
	 */
	<PO extends MyTableEntity> PO queryById(String id, Class<PO> clazz);

	/**
	 * 按 Long 类型主键查询单条实体。
	 *
	 * <pre>{@code
	 * User user = queryById(123L, User.class);
	 * }</pre>
	 *
	 * @param id    主键值（Long 类型）
	 * @param clazz 实体类
	 * @return 实体对象，不存在则返回 {@code null}
	 */
	<PO extends MyTableEntity> PO queryById(Long id, Class<PO> clazz);

	// ======================== 删除 ========================

	/**
	 * 按主键删除实体（支持逻辑删除，若实体配置了逻辑删除字段则执行逻辑删除）。
	 *
	 * <pre>{@code
	 * User user = queryById(123L, User.class);
	 * delPO(user);
	 * }</pre>
	 *
	 * @param po 实体对象，主键必须有值
	 * @return 影响行数
	 */
	<PO extends MyTableEntity> int delPO(PO po);

	/**
	 * 按多个主键批量删除（支持逻辑删除）。
	 *
	 * <pre>{@code
	 * delByIds(User.class, 1L, 2L, 3L);
	 * }</pre>
	 *
	 * @param clazz 实体类
	 * @param id    一个或多个主键值
	 * @return 影响行数
	 */
	<PO extends MyTableEntity> int delByIds(Class<PO> clazz, Object... id);

}
