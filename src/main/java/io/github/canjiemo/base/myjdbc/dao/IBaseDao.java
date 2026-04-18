package io.github.canjiemo.base.myjdbc.dao;

import io.github.canjiemo.base.myjdbc.MyTableEntity;
import io.github.canjiemo.mycommon.pager.Pager;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


/**
 * 基础 DAO 接口，提供通用的增删改查操作。
 *
 * <p>作为最底层的数据访问入口，所有查询 SQL 会经 {@code JSqlDynamicSqlParser}
 * 自动注入逻辑删除条件与多租户条件（若目标表配置了对应字段）；
 * 所有 PO 级写操作会自动填充审计字段（CREATE_TIME / CREATE_BY / UPDATE_TIME / UPDATE_BY）。
 *
 * <p>大多数业务代码应优先使用 {@code IBaseService} 而非直接调用本接口，
 * 仅在 Service 层封装不满足需求时才直接注入 {@code IBaseDao}。
 */
public interface IBaseDao {

	// ======================== 原生 SQL 查询 ========================

	/**
	 * 分页查询（参数为任意 JavaBean 对象）。
	 *
	 * <p>SQL 中使用 {@code :fieldName} 形式的命名占位符，框架自动从 param 对象中提取同名字段值。
	 * 逻辑删除与租户条件会被自动注入到 WHERE / JOIN ON 中。
	 *
	 * <pre>{@code
	 * String sql = "SELECT * FROM user WHERE status = :status ORDER BY create_time DESC";
	 * UserQuery query = new UserQuery();
	 * query.setStatus(1);
	 * Pager<User> page = queryPageForSql(sql, query, new Pager<>(1, 10), User.class);
	 * }</pre>
	 *
	 * @param sql   带命名占位符的查询 SQL，必须是 SELECT 语句
	 * @param param 参数对象（JavaBean），字段名与占位符对应
	 * @param pager 分页对象，包含页码与每页大小
	 * @param clazz 结果类型
	 * @return 填充了结果列表与总数的分页对象
	 * @throws IllegalArgumentException SQL 非 SELECT 或解析失败时
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
	 * @return 结果列表（不会为 {@code null}，无匹配时返回空列表）
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
	 * @return 单条结果，无匹配时返回 {@code null}
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
	 * @param param 参数 Map，key 与占位符名称对应，可为 {@code null} 或空
	 * @param pager 分页对象
	 * @param clazz 结果类型
	 * @return 填充了结果列表与总数的分页对象
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
	 * @param param 参数 Map，可为 {@code null} 或空
	 * @param clazz 结果类型
	 * @return 结果列表（不会为 {@code null}）
	 */
	<T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz);

	/**
	 * 查询单条记录（参数为 {@code Map<String, Object>}）。
	 *
	 * <p>若结果为空返回 {@code null}；也可用于聚合查询（如 {@code SELECT count(*)}）。
	 *
	 * <pre>{@code
	 * String sql = "SELECT count(*) FROM user WHERE status = :status";
	 * Long count = querySingleForSql(sql, Map.of("status", 1), Long.class);
	 * }</pre>
	 *
	 * @param sql   带命名占位符的查询 SQL
	 * @param param 参数 Map，可为 {@code null} 或空
	 * @param clazz 结果类型
	 * @return 单条结果，无匹配时返回 {@code null}
	 */
	<T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz);

	// ======================== 插入 ========================

	/**
	 * 插入单条实体。
	 *
	 * <p>会自动填充审计字段 {@code CREATE_TIME / CREATE_BY}；若启用多租户，自动写入 {@code tenant_id}。
	 *
	 * <pre>{@code
	 * User user = new User();
	 * user.setName("张三");
	 * user.setStatus(1);
	 * Serializable id = insertPO(user, true); // 由框架生成主键
	 * }</pre>
	 *
	 * @param po           实体对象
	 * @param autoCreateId {@code true} 由框架生成主键（雪花 ID / UUID 等，按 @MyTable 配置）；
	 *                     {@code false} 使用实体中已有的主键值
	 * @return 插入后的主键值
	 */
	<PO extends MyTableEntity> Serializable insertPO(PO po, boolean autoCreateId);

	/**
	 * 批量插入（默认批次大小）。
	 *
	 * <p>数据量较大时建议使用 {@link #batchInsertPO(List, boolean, int)} 显式指定批次。
	 *
	 * @param pos          实体列表，不能为空
	 * @param autoCreateId 是否由框架自动生成主键
	 * @return 最后一条记录的主键值
	 */
	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId);

	/**
	 * 批量插入（指定批次大小）。
	 *
	 * <p>按 {@code batchSize} 分批提交，避免单条 SQL 过长或超出数据库参数数量限制。
	 *
	 * <pre>{@code
	 * List<User> users = buildUserList(); // 10 万条
	 * batchInsertPO(users, true, 500);
	 * }</pre>
	 *
	 * @param pos          实体列表，不能为空
	 * @param autoCreateId 是否由框架自动生成主键
	 * @param batchSize    每批次插入的条数（建议 500 ~ 1000）
	 * @return 最后一条记录的主键值
	 */
	<PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize);

	// ======================== 更新 ========================

	/**
	 * 按主键更新实体（默认跳过值为 {@code null} 的字段）。
	 *
	 * <p>会自动填充 {@code UPDATE_TIME / UPDATE_BY}；WHERE 中自动注入逻辑删除与租户条件。
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
	 * <p>适用于需要将某些字段显式置为 {@code null} 或空字符串的场景。
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

	/**
	 * 执行带命名占位符的 UPDATE 语句（raw SQL 写操作入口）。
	 *
	 * <p><b>⚠ 仅接受 UPDATE 语句</b>：INSERT / DELETE / MERGE / UPSERT 等一律会在解析阶段
	 * 抛出 {@link IllegalArgumentException}，本方法<b>不是</b>通用的 "executeSql" 入口。
	 * 这是框架刻意做的限制，目的是防止绕过审计字段填充、多租户注入、逻辑删除等不变量。
	 *
	 * <p><b>其他写操作请走专用 PO 级 API：</b>
	 * <ul>
	 *     <li>INSERT → {@link #insertPO(MyTableEntity, boolean)} /
	 *         {@link #batchInsertPO(List, boolean)}（自动填充 CREATE_TIME / CREATE_BY、注入 tenant_id）</li>
	 *     <li>DELETE → {@link #delPO(MyTableEntity)} /
	 *         {@link #delByIds(Class, Object...)}（按 {@code @MyTable} 配置走逻辑删除或物理删除）</li>
	 *     <li>UPSERT → {@code IBaseService#saveOrUpdate(...)}（按主键自动 insert 或 update，方言差异由框架处理）</li>
	 *     <li>按条件批量更新 → {@code IBaseService#lambdaUpdate(...)}（类型安全、自动条件注入）</li>
	 * </ul>
	 *
	 * <p><b>安全约束：</b>
	 * <ul>
	 *     <li>默认<b>拒绝不带 WHERE 子句的全表更新</b>；确需全表更新需用
	 *         {@code MyJdbcScope.allowUnsafeWrite(() -> ...)} 显式放开</li>
	 *     <li>仅允许单条 SQL，多条语句（分号拼接）会被拒绝</li>
	 *     <li>若目标表配置了逻辑删除字段或租户字段，会自动注入对应条件到 WHERE 中</li>
	 * </ul>
	 *
	 * <pre>{@code
	 * // ✔ 正确用法：带 WHERE 的 UPDATE
	 * String sql = "UPDATE user SET status = :status WHERE dept_id = :deptId";
	 * int affected = updateForSql(sql, Map.of("status", 1, "deptId", 10L), User.class);
	 *
	 * // ✘ 错误用法：下列语句会抛 IllegalArgumentException
	 * updateForSql("INSERT INTO user ...", params, User.class); // 非 UPDATE
	 * updateForSql("DELETE FROM user WHERE ...", params, User.class); // 非 UPDATE
	 * updateForSql("UPDATE user SET status = 1", null, User.class); // 无 WHERE
	 * }</pre>
	 *
	 * @param sql   带命名占位符的 UPDATE SQL，必须包含 WHERE（除非显式放开）
	 * @param param 参数 Map，key 与 SQL 中的 {@code :xxx} 占位符一一对应，可为 {@code null} 或空
	 * @param clazz 目标实体类，用于定位表元数据（逻辑删除字段、租户字段等）
	 * @return 受影响行数
	 * @throws IllegalArgumentException 当 SQL 为空、非单条、非 UPDATE、或缺少 WHERE（默认）时
	 * @see io.github.canjiemo.base.myjdbc.security.SqlOperationGuard
	 * @see io.github.canjiemo.base.myjdbc.scope.MyJdbcScope#allowUnsafeWrite
	 */
	<PO extends MyTableEntity> int updateForSql(String sql, Map<String, Object> param, Class<PO> clazz);

	// ======================== 按主键查询 ========================

	/**
	 * 按主键查询单条实体。
	 *
	 * <p>自动注入逻辑删除与租户条件，因此已被逻辑删除或不属于当前租户的记录会返回 {@code null}。
	 *
	 * <pre>{@code
	 * User user = queryById(123L, User.class);
	 * }</pre>
	 *
	 * @param id    主键值，类型需与实体主键字段匹配（通常为 {@code Long} / {@code String}）
	 * @param clazz 实体类
	 * @return 实体对象，不存在则返回 {@code null}
	 */
	<PO extends MyTableEntity> PO queryById(Object id, Class<PO> clazz);

	// ======================== 删除 ========================

	/**
	 * 按主键删除实体。
	 *
	 * <p>若实体通过 {@code @MyTable(deleteField = ...)} 配置了逻辑删除字段，执行逻辑删除
	 * （实际发出 UPDATE 语句更新删除标记），否则执行物理删除（DELETE 语句）。
	 * 启用多租户时 WHERE 会自动携带租户条件，防止跨租户误删。
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
	 * 按多个主键批量删除。
	 *
	 * <p>行为与 {@link #delPO(MyTableEntity)} 一致：按 {@code @MyTable} 配置决定逻辑删除或物理删除，
	 * 启用多租户时自动携带租户条件。
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
