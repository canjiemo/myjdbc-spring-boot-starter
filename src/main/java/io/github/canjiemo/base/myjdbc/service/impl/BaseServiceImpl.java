package io.github.canjiemo.base.myjdbc.service.impl;


import io.github.canjiemo.base.myjdbc.MyTableEntity;
import io.github.canjiemo.base.myjdbc.dao.IBaseDao;
import io.github.canjiemo.base.myjdbc.lambda.LambdaQueryWrapper;
import io.github.canjiemo.base.myjdbc.lambda.LambdaUpdateWrapper;
import io.github.canjiemo.base.myjdbc.service.IBaseService;
import io.github.canjiemo.mycommon.pager.Pager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Transactional(readOnly = true)
public class BaseServiceImpl implements IBaseService {

	@Autowired
	protected IBaseDao baseDao;

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@Autowired
	protected NamedParameterJdbcTemplate namedParameterJdbcTemplate;

	public BaseServiceImpl() {
	}

	public BaseServiceImpl(IBaseDao baseDao, JdbcTemplate jdbcTemplate,
	                      NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
		this.baseDao = baseDao;
		this.jdbcTemplate = jdbcTemplate;
		this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
	}

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Object param, Pager<T> pager, Class<T> clazz) {
		return baseDao.queryPageForSql(sql, param, pager,clazz);
	}

	@Override
	public <T> List<T> queryListForSql(String sql, Object param,Class<T> clazz) {
		return baseDao.queryListForSql(sql, param,clazz);
	}

	@Override
	public <T> T querySingleForSql(String sql, Object param,Class<T> clazz) {
		return baseDao.querySingleForSql(sql, param,clazz);
	}

	@Override
	public <T> Pager<T> queryPageForSql(String sql, Map<String, Object> param, Pager<T> pager, Class<T> clazz) {
		return baseDao.queryPageForSql(sql, param, pager, clazz);
	}


	@Override
	public <T> List<T> queryListForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		return baseDao.queryListForSql(sql, param, clazz);
	}

	@Override
	public <T> T querySingleForSql(String sql, Map<String, Object> param, Class<T> clazz) {
		return baseDao.querySingleForSql(sql, param, clazz);
	}

	@Transactional
	public <PO extends MyTableEntity> Serializable insertPO(PO po, boolean autoCreateId) {
		return baseDao.insertPO(po, autoCreateId);
	}

	@Transactional
	public <PO extends MyTableEntity> int updatePO(PO po) {
		return baseDao.updatePO(po);
	}


	@Override
	public <PO extends MyTableEntity> PO queryById(String id, Class<PO> clazz) {
		return baseDao.queryById(id, clazz);
	}

	@Override
	public <PO extends MyTableEntity> PO queryById(Long id, Class<PO> clazz) {
		return baseDao.queryById(id, clazz);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> int delPO(PO po) {
		return baseDao.delPO(po);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> int delByIds(Class<PO> clazz, Object... id) {
		return baseDao.delByIds(clazz, id);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> Serializable insertPO(PO po) {
		return baseDao.insertPO(po, true);
	}


	@Transactional
	public <PO extends MyTableEntity> int updatePO(PO po, boolean ignoreNull) {
		return baseDao.updatePO(po, ignoreNull);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> int updatePO(PO po, @Nullable String... forceUpdateProperties) {
		return baseDao.updatePO(po, forceUpdateProperties);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId) {
		return baseDao.batchInsertPO(pos, autoCreateId);
	}

	@Override
	@Transactional
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos) {
		return baseDao.batchInsertPO(pos, true);
	}

	@Override
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, boolean autoCreateId, int batchSize) {
		return baseDao.batchInsertPO(pos, autoCreateId, batchSize);
	}

	@Override
	public <PO extends MyTableEntity> Serializable batchInsertPO(List<PO> pos, int batchSize) {
		return this.batchInsertPO(pos, true, batchSize);
	}

	/**
	 * 创建 Lambda 链式查询构造器（实体类型即返回类型，最常用）。
	 *
	 * <p>适用于查询结果直接映射回同一实体类的场景。
	 *
	 * <pre>{@code
	 * // 查询状态为 1、姓名包含"张"的用户列表
	 * List<User> users = lambdaQuery(User.class)
	 *     .eq(User::getStatus, 1)
	 *     .like(User::getName, "张")
	 *     .orderByDesc(User::getCreateTime)
	 *     .list();
	 *
	 * // 分页查询
	 * Pager<User> page = lambdaQuery(User.class)
	 *     .eq(User::getStatus, 1)
	 *     .page(new Pager<>(1, 10));
	 *
	 * // 查询单条（无结果返回 null）
	 * User user = lambdaQuery(User.class)
	 *     .eq(User::getId, 123L)
	 *     .one();
	 *
	 * // 统计数量
	 * long count = lambdaQuery(User.class)
	 *     .eq(User::getStatus, 1)
	 *     .count();
	 * }</pre>
	 *
	 * @param clazz 实体类，同时作为查询结果类型
	 * @return Lambda 查询构造器，支持链式调用
	 */
	protected <T extends MyTableEntity> LambdaQueryWrapper<T, T> lambdaQuery(Class<T> clazz) {
		return new LambdaQueryWrapper<>(clazz, clazz, baseDao);
	}

	/**
	 * 创建 Lambda 链式查询构造器（实体用于条件构造，结果映射到指定 DTO/VO）。
	 *
	 * <p>适用于需要将查询结果映射到与实体不同的 DTO / VO 类的场景，
	 * 例如只查询部分字段并映射到视图对象。
	 *
	 * <pre>{@code
	 * // 用 User 实体构造条件，结果映射到 UserVO
	 * List<UserVO> vos = lambdaQuery(User.class, UserVO.class)
	 *     .select(User::getId, User::getName, User::getEmail)
	 *     .eq(User::getStatus, 1)
	 *     .orderByAsc(User::getName)
	 *     .list();
	 *
	 * // 分页查询并映射到 DTO
	 * Pager<UserDTO> page = lambdaQuery(User.class, UserDTO.class)
	 *     .eq(User::getDeptId, deptId)
	 *     .page(new Pager<>(1, 20));
	 * }</pre>
	 *
	 * @param entityClazz  实体类，用于条件字段的 Lambda 引用解析
	 * @param resultClazz  结果类，查询结果将映射到该类型（支持 DTO/VO/Map 等）
	 * @return Lambda 查询构造器，支持链式调用
	 */
	protected <T extends MyTableEntity, R> LambdaQueryWrapper<T, R> lambdaQuery(Class<T> entityClazz, Class<R> resultClazz) {
		return new LambdaQueryWrapper<>(entityClazz, resultClazz, baseDao);
	}

	/**
	 * 创建 Lambda 链式更新构造器。
	 *
	 * <p>默认禁止无条件全表更新（必须添加至少一个 WHERE 条件）。
	 *
	 * <pre>{@code
	 * // 将状态为 0 的用户全部置为禁用（status = 2）
	 * lambdaUpdate(User.class)
	 *     .set(User::getStatus, 2)
	 *     .eq(User::getStatus, 0)
	 *     .update();
	 *
	 * // 按 ID 更新指定字段
	 * lambdaUpdate(User.class)
	 *     .set(User::getEmail, "new@example.com")
	 *     .set(User::getRemark, "已更新")
	 *     .eq(User::getId, userId)
	 *     .update();
	 * }</pre>
	 *
	 * @param clazz 实体类
	 * @return Lambda 更新构造器，支持链式调用
	 */
	protected <T extends MyTableEntity> LambdaUpdateWrapper<T> lambdaUpdate(Class<T> clazz) {
		return new LambdaUpdateWrapper<>(clazz, baseDao);
	}

}
