---
name: myjdbc-dev
description: |
  myjdbc-spring-boot-starter 开发指南。当用户在这个项目中进行任何开发工作时必须使用此 skill，包括：
  新增实体类、写查询逻辑、调试 SQL 改写问题、配置多租户、添加数据库支持、编写单元测试。
  只要涉及 @MyTable、@MyField、IBaseService、IBaseDao、LambdaQueryWrapper、LambdaUpdateWrapper、
  TenantContext、MyJdbcScope、SqlBuilder、JSqlDynamicSqlParser、TableCacheManager 任何一个类，
  都应立即调用此 skill。这是一个 Spring JDBC 封装项目，不是 MyBatis 也不是 JPA，
  请务必用此 skill 来确保代码风格、API 用法和测试模式的一致性。
---

# myjdbc-spring-boot-starter 开发指南

本项目是基于 Spring JDBC Template 的轻量级 ORM 封装，提供注解驱动的实体映射、自动逻辑删除、多租户隔离、Lambda 链式查询等能力。

---

## 一、实体类定义

### 基础模式

```java
@MyTable("user")  // value = 表名
public class User extends MyTableEntity {
    private String name;
    private Integer status;
    private Long deptId;
}
```

`MyTableEntity` 是所有实体的基类，包含主键 `id` 和通用字段。

### @MyTable 完整参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `value` | 必填 | 数据库表名 |
| `pkColumn` | `"id"` | 主键列名 |
| `pkField` | `"id"` | 主键 Java 字段名 |
| `delColumn` | `"delete_flag"` | 逻辑删除列名 |
| `delField` | `"deleteFlag"` | 逻辑删除 Java 字段名 |
| `delValue` | `1` | 已删除状态值（0=未删除，1=已删除） |

**没有 `delete_flag` 列的表**：在 `@MyTable` 中指定一个不存在的列，或者框架在 schema 验证时会跳过逻辑删除注入。

### @MyField 字段控制

```java
@MyField("user_name")          // 指定列名（Java 驼峰 → 数据库下划线，默认自动转换）
private String userName;

@MyField(serialize = false)    // 不参与 INSERT/UPDATE（如计算字段、虚拟列）
private String fullName;

@MyField(fill = AuditFill.INSERT)        // 插入时自动填充（通过 AuditFieldProvider SPI）
private LocalDateTime createTime;

@MyField(fill = AuditFill.INSERT_UPDATE) // 插入和更新时自动填充
private LocalDateTime updateTime;
```

**字段命名规则**：Java 驼峰 → 数据库下划线，框架自动转换；`@MyField("colName")` 可强制覆盖。

---

## 二、CRUD 操作

### 注入方式

```java
@Service
public class UserServiceImpl extends BaseServiceImpl implements UserService {
    // 直接继承 BaseServiceImpl，拥有全部 CRUD 能力
}
```

也可以在任何 Bean 中注入 DAO：

```java
@Autowired
private IBaseDao baseDao;
```

### 插入

```java
// 单条插入（自动生成主键）
Serializable id = service.insertPO(user);

// 单条插入（手动指定主键）
user.setId("my-custom-id");
service.insertPO(user, false);

// 批量插入（推荐大数据量时指定 batchSize）
service.batchInsertPO(users);            // 默认批次
service.batchInsertPO(users, 500);       // 每批 500 条
```

### 查询

```java
// 按主键查询
User user = service.queryById(123L, User.class);
User user = service.queryById("uuid-xxx", User.class);

// 原生 SQL 查询（Bean 参数）
String sql = "SELECT * FROM user WHERE dept_id = :deptId AND status = :status";
UserQuery q = new UserQuery();
q.setDeptId(10L); q.setStatus(1);
List<User> list = service.queryListForSql(sql, q, User.class);

// 原生 SQL 查询（Map 参数）
List<User> list = service.queryListForSql(sql, Map.of("deptId", 10L, "status", 1), User.class);

// 分页查询
Pager<User> page = service.queryPageForSql(sql, q, new Pager<>(1, 10), User.class);

// 查询标量值
Long count = service.querySingleForSql("SELECT count(*) FROM user WHERE status=:s", Map.of("s",1), Long.class);

// 查询 Map（不需要实体类时）
List<Map> rows = service.queryListForSql(sql, params, Map.class);
```

### 更新

```java
// 按主键更新（默认跳过 null 字段）
user.setId(123L);
user.setStatus(2);
service.updatePO(user);

// 强制将某字段更新为 null
user.setRemark(null);
service.updatePO(user, "remark");  // remark 会被写为 null

// 将所有字段都写入（包括 null）
service.updatePO(user, false);
```

### 删除

```java
// 逻辑删除（若表有 delete_flag 则更新，否则物理删除）
service.delPO(user);

// 按 ID 批量删除
service.delByIds(User.class, 1L, 2L, 3L);
```

---

## 三、Lambda 链式查询（推荐用于复杂条件）

### 从 Service 获取 Wrapper

```java
// 在 BaseServiceImpl 子类中
LambdaQueryWrapper<User, User> wrapper = lambdaQuery(User.class);
LambdaUpdateWrapper<User> updateWrapper = lambdaUpdate(User.class);
```

### 常用查询模式

```java
// 列表查询
List<User> list = lambdaQuery(User.class)
    .eq(User::getStatus, 1)
    .like(User::getName, "张")
    .orderByDesc(User::getCreateTime)
    .list();

// 查询单条
User user = lambdaQuery(User.class)
    .eq(User::getId, 123L)
    .one();

// 分页
Pager<User> page = lambdaQuery(User.class)
    .eq(User::getDeptId, 10L)
    .page(new Pager<>(1, 20));

// COUNT
long count = lambdaQuery(User.class)
    .eq(User::getStatus, 1)
    .count();

// EXISTS
boolean exists = lambdaQuery(User.class)
    .eq(User::getEmail, "foo@bar.com")
    .exists();
```

### 指定返回类型（查询部分字段或 VO）

```java
// 返回 VO（字段名需与 VO 字段一致）
List<UserVO> vos = lambdaQuery(User.class, UserVO.class)
    .select(User::getName, User::getStatus)
    .eq(User::getDeptId, deptId)
    .list();

// 带别名
lambdaQuery(User.class, Map.class)
    .selectAs(User::getCreateTime, "create_time_str")
    .list();
```

### 动态条件（避免 if-else）

```java
lambdaQuery(User.class)
    .when(name != null, q -> q.like(User::getName, name))
    .when(status != null, q -> q.eq(User::getStatus, status))
    .list();
```

### 组合条件（OR / 嵌套）

```java
// OR 条件
lambdaQuery(User.class)
    .or(q -> q.eq(User::getStatus, 1).eq(User::getType, "admin"))
    .list();

// any()/all() 也可以构造嵌套
lambdaQuery(User.class)
    .any(
        f -> f.eq(User::getDeptId, 1L),
        f -> f.eq(User::getDeptId, 2L)
    )
    .list();
```

### Lambda 更新

```java
lambdaUpdate(User.class)
    .set(User::getStatus, 2)
    .set(User::getUpdateTime, LocalDateTime.now())
    .eq(User::getId, 123L)
    .update();
```

---

## 四、逻辑删除

框架自动在所有查询中注入 `WHERE table.delete_flag = 0`，无需手动编写。

### 查询包含已删除数据

```java
// Lambda Wrapper 方式
List<User> all = lambdaQuery(User.class)
    .withDeleted()
    .list();

// 作用域方式（适用于原生 SQL）
List<User> all = MyJdbcScope.withDeleted(
    () -> service.queryListForSql(sql, params, User.class)
);
```

---

## 五、多租户隔离

**默认关闭**，通过 `myjdbc.tenant.enabled=true` 开启。

### 配置

```yaml
myjdbc:
  tenant:
    enabled: true
    column: tenant_id   # 默认值，可自定义
```

### 注入租户 ID 的三种方式（优先级从高到低）

**方式 1：实现 SPI Bean（推荐 Web 项目）**
```java
@Component
public class WebTenantIdProvider implements TenantIdProvider {
    @Override
    public Object getTenantId() {
        return SecurityContextHolder.getContext().getTenantId(); // 从安全上下文获取
    }
}
```

**方式 2：ThreadLocal 编程式（适合批处理、定时任务）**
```java
TenantContext.setTenantId(tenantId);
try {
    // 所有查询自动注入租户条件
} finally {
    TenantContext.clearTenantId();  // 必须清理，防止内存泄漏
}
```

**方式 3：返回 null = 超级管理员（查询全部租户数据）**

### 临时绕过租户隔离

```java
// 推荐：Lambda 形式，自动清理
List<User> all = TenantContext.withoutTenant(
    () -> service.queryListForSql("SELECT * FROM user", null, User.class)
);

// 或使用 Wrapper 的 allTenants()
List<User> all = lambdaQuery(User.class).allTenants().list();

// 手动控制（需确保 finally 中 restore）
TenantContext.skip();
try { ... } finally { TenantContext.restore(); }
```

**注意**：Web 请求结束时，在 Filter 或 HandlerInterceptor 的 `afterCompletion` 中调用 `TenantContext.clear()` 防止内存泄漏。

---

## 六、MyJdbcScope —— 统一作用域控制

`MyJdbcScope` 是比 `TenantContext` 更底层、更通用的机制，同时管理逻辑删除和租户两个特性：

```java
// 关闭逻辑删除注入
MyJdbcScope.withDeleted(() -> dao.queryListForSql(...));

// 关闭租户注入
MyJdbcScope.allTenants(() -> dao.queryListForSql(...));

// 同时关闭两个（等效于超级管理员查全量）
MyJdbcScope.without(MyJdbcFeature.TENANT, () ->
    MyJdbcScope.withDeleted(() -> dao.queryListForSql(...))
);
```

**`updateForSql` 不带 WHERE 的安全保护**：框架默认禁止不带 WHERE 的全表更新，需要显式允许：
```java
MyJdbcScope.allowUnsafeWrite(() -> dao.updateForSql(sql, params, User.class));
```

---

## 七、审计字段自动填充

实现 `AuditFieldProvider` SPI Bean，框架会在 INSERT/UPDATE 时自动填充带 `@MyField(fill=...)` 的字段：

```java
@Component
public class MyAuditFieldProvider implements AuditFieldProvider {
    @Override
    public Object getAuditValue(AuditFill fillType) {
        return switch (fillType) {
            case INSERT -> LocalDateTime.now();
            case INSERT_UPDATE -> LocalDateTime.now();
            default -> null;
        };
    }
}
```

---

## 八、单元测试模式

**本项目测试不需要 Spring 上下文**，直接设置静态字段即可：

```java
@BeforeAll
static void setup() {
    // 1. 设置数据库类型
    sqlBuilder = new SqlBuilder(DbType.MYSQL);    // 或 DbType.POSTGRESQL 等

    // 2. 初始化实体元数据缓存（指定 @MyTable 实体所在包）
    TableCacheManager.initCache("com.example.entity");

    // 3. 如需测试租户功能
    JSqlDynamicSqlParser.tenantEnabled = true;
    TableCacheManager.registerTenantTable("user");  // 注册哪些表有 tenant_id
}

@AfterAll
static void teardown() {
    // 多租户测试结束后重置，避免影响其他测试
    JSqlDynamicSqlParser.tenantEnabled = false;
}
```

### 典型测试结构

```java
@Test
void test_logicalDeleteInjection() {
    String sql = "SELECT * FROM user WHERE status = 1";
    String result = JSqlDynamicSqlParser.appendDeleteCondition(sql);
    assertTrue(result.contains("delete_flag = 0"));
    assertFalse(result.contains("delete_flag = 0") && result.indexOf("delete_flag = 0")
        != result.lastIndexOf("delete_flag = 0"), "条件不应重复注入");
}

@Test
void test_tenantInjection() {
    JSqlDynamicSqlParser.tenantEnabled = true;
    MapSqlParameterSource sps = new MapSqlParameterSource();
    String sql = JSqlDynamicSqlParser.appendConditions("SELECT * FROM user", sps);
    assertTrue(sql.contains("tenant_id = :myjdbcTenantId"));
    assertTrue(sps.hasValue("myjdbcTenantId") || /* TenantAwareSqlParameterSource包装 */ true);
}
```

---

## 九、添加新数据库类型支持

1. 在 `DbType.java` 添加新的枚举值（参考现有枚举，type 值不能重复）
2. 在 `SqlBuilder.java` 添加对应的 `case`，实现 `buildPagerSql` 分页逻辑
3. 在 `MyJdbcAutoConfiguration.java` 中，添加 DataSource URL 前缀到 `DbType` 的映射
4. 在 `src/test/` 添加新数据库的兼容性测试类，参考 `MySQL8CompatibilityTest`

---

## 十、SQL 改写调试

SQL 改写由 `JSqlDynamicSqlParser` 完成，全链路：

```
queryXxx()
  → BaseDaoImpl.applyConditions(sql, sps)
      ├─ tenant enabled + !isSkipped + tenantId != null
      │    → appendConditions(sql)   // 逻辑删除 + 租户，一次解析
      └─ otherwise
           → appendDeleteCondition(sql)  // 仅逻辑删除，一次解析
```

**常见问题排查**：

| 现象 | 可能原因 | 排查方式 |
|------|---------|---------|
| 逻辑删除条件重复 | 方法内部委托了其他方法导致二次注入 | `isDeleteConditionExists` 应已防止，检查 parser 是否被绕过 |
| LEFT JOIN 语义错误 | 条件被加到 WHERE 而不是 ON | 检查 `JSqlDynamicSqlParser` JOIN 类型判断逻辑 |
| 租户条件未注入 | `TenantIdProvider` 返回 null | 按超级管理员处理，这是预期行为 |
| 租户参数缺失异常 | tenant 表未被 `TABLE_TENANT_CACHE` 注册 | 检查 `DatabaseSchemaValidator` 启动时是否扫到了该表 |
| SQL 解析报错 | JSqlParser 不支持的语法 | 升级 JSqlParser 版本或手动绕过（`MyJdbcScope.without`） |

**开启 SQL 日志**（`application.properties`）：
```properties
myjdbc.showsql=true
```

---

## 十一、配置属性速查

```properties
myjdbc.showsql=true               # SQL 执行日志
myjdbc.validate-schema=true       # 启动时 schema 验证
myjdbc.tenant.enabled=false       # 多租户开关（默认关闭）
myjdbc.tenant.column=tenant_id    # 租户列名
```

---

## 十二、常见陷阱

1. **`@MyField(serialize=false)` 的字段不会出现在 INSERT/UPDATE**，但会出现在查询结果映射中——它只控制写操作方向。

2. **`updatePO` 默认跳过 null 字段**。若需要将字段清空，必须用 `updatePO(po, "fieldName")` 或 `updatePO(po, false)`。

3. **`batchInsertPO` 大数据量时指定 `batchSize`**，避免单条 SQL 超过数据库参数限制。

4. **`TenantContext.clearTenantId()` vs `TenantContext.clear()`**：前者只清租户 ID，后者清所有 ThreadLocal（包括 skip 状态）。在请求结束时用 `clear()`，在代码块内用 `clearTenantId()`。

5. **测试间隔离**：多个测试类若都用到 `JSqlDynamicSqlParser.tenantEnabled`，记得在 `@AfterAll` 中重置，否则测试顺序不同可能导致结果不稳定。
