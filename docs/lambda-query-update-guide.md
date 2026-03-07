# Lambda 查询与更新 DSL 使用说明

> 本文档用于说明 `myjdbc` 当前已经落地的 Lambda 查询 / 更新 DSL，
> 同时约束下一阶段继续演进时的使用风格。
> 重点不是照搬 MyBatis-Plus，而是在现有 `lambdaQuery(...)` 思路上继续做强：
>
> - 条件仍然基于实体字段方法引用
> - 结果仍然支持实体 / DTO 双模式
> - 更强调动态条件、组合条件、安全默认值
> - 更新 DSL 与查询 DSL 保持同一套心智模型

---

## 1. 设计目标

这版 DSL 主要解决 6 个问题：

1. 当前 `lambdaQuery` 只有平铺 `AND`，复杂条件表达力不够。
2. 查询和更新分裂，缺少统一的 Lambda 使用体验。
3. 动态查询代码容易写成 `if/else` 散落一地。
4. DTO 查询缺少更明确的列别名表达方式。
5. 自定义 SQL 片段需要可控接入，但不能直接放开到“随便拼字符串”。
6. 更新操作必须默认安全，不能轻易全表更新。

---

## 2. 总体风格

核心入口仍然保持现有风格：

```java
lambdaQuery(UserPO.class)
lambdaQuery(UserPO.class, UserDTO.class)
lambdaUpdate(UserPO.class)
```

新增的不是另一套框架，而是在原有思路上补 3 类能力：

- 条件组合：`when / any / all / or(...)`
- 查询表达：`selectAs / selectRaw / groupBy / having / exists`
- 更新表达：`set / setIfPresent / setNull / increase / decrease`

---

## 3. 查询 DSL

### 3.1 基础查询

```java
List<UserPO> users = lambdaQuery(UserPO.class)
    .eq(UserPO::getStatus, 1)
    .like(UserPO::getUsername, "张")
    .orderByDesc(UserPO::getCreateTime)
    .list();
```

生成 SQL：

```sql
SELECT * FROM sys_user
WHERE status = :lwp0 AND username LIKE :lwp1
ORDER BY create_time DESC
```

说明：

- 条件值仍然自动跳过 `null / 空字符串 / 空集合`
- 逻辑删除、租户隔离仍由底层自动改写管道处理

### 3.2 DTO 结果映射

```java
List<UserDTO> users = lambdaQuery(UserPO.class, UserDTO.class)
    .select(UserPO::getId, UserPO::getUsername)
    .selectAs(UserPO::getCreateTime, "createdAt")
    .eq(UserPO::getStatus, 1)
    .list();
```

生成 SQL：

```sql
SELECT id, username, create_time AS createdAt
FROM sys_user
WHERE status = :lwp0
```

说明：

- 条件和列引用始终基于实体类 `UserPO`
- 返回类型由第二个泛型参数 `UserDTO` 决定

### 3.3 动态条件：`when`

```java
Pager<UserPO> page = lambdaQuery(UserPO.class)
    .when(param.getStatus() != null, q -> q.eq(UserPO::getStatus, param.getStatus()))
    .when(param.getKeyword() != null, q -> q.like(UserPO::getUsername, param.getKeyword()))
    .page(new Pager<>(1, 20));
```

生成 SQL：

```sql
SELECT * FROM sys_user
WHERE status = :lwp0 AND username LIKE :lwp1
```

说明：

- `when` 用来替代外层大量 `if`
- 条件不成立时，该段 DSL 不产生任何 SQL 片段

### 3.4 OR 条件组：`any`

```java
List<UserPO> users = lambdaQuery(UserPO.class)
    .eq(UserPO::getDeleteFlag, 0)
    .any(
        q -> q.eq(UserPO::getUsername, "张三"),
        q -> q.eq(UserPO::getUsername, "李四")
    )
    .list();
```

生成 SQL：

```sql
SELECT * FROM sys_user
WHERE delete_flag = :lwp0
  AND ((username = :lwp1) OR (username = :lwp2))
```

说明：

- `any(...)` 表示“任一条件组成立”
- 这是这版 DSL 的主推写法，比直接串 `or()` 更稳定也更容易读

### 3.5 AND 条件组：`all`

```java
List<UserPO> users = lambdaQuery(UserPO.class)
    .any(
        q -> q.all(
            x -> x.eq(UserPO::getStatus, 1),
            x -> x.like(UserPO::getUsername, "张")
        ),
        q -> q.eq(UserPO::getId, 100L)
    )
    .list();
```

生成 SQL：

```sql
SELECT * FROM sys_user
WHERE (((status = :lwp0) AND (username LIKE :lwp1)) OR (id = :lwp2))
```

说明：

- `all(...)` 用于显式表达“这一组必须同时满足”
- 适合 `(A AND B) OR C` 这类场景

### 3.6 显式 OR 下一条件：`or()`

```java
List<UserPO> users = lambdaQuery(UserPO.class)
    .eq(UserPO::getStatus, 1)
    .or()
    .eq(UserPO::getId, 1L)
    .list();
```

生成 SQL：

```sql
SELECT * FROM sys_user
WHERE status = :lwp0 OR id = :lwp1
```

说明：

- 适合非常短的小条件切换
- 更复杂的组合建议优先使用 `any(...)`

### 3.7 EXISTS / NOT EXISTS

```java
List<UserPO> users = lambdaQuery(UserPO.class)
    .exists(
        "SELECT 1 FROM sys_user_role ur WHERE ur.user_id = sys_user.id AND ur.role_id = :roleId",
        Map.of("roleId", 10L)
    )
    .list();
```

生成 SQL：

```sql
SELECT * FROM sys_user
WHERE EXISTS (
    SELECT 1
    FROM sys_user_role ur
    WHERE ur.user_id = sys_user.id AND ur.role_id = :lwp0
)
```

说明：

- 自定义片段必须走命名参数
- 框架会把片段里的参数自动改写成内部参数名，避免和外层冲突

### 3.8 分组查询：`groupBy + having`

```java
List<RoleCountDTO> result = lambdaQuery(UserPO.class, RoleCountDTO.class)
    .selectAs(UserPO::getStatus, "status")
    .selectRaw("count(*)", "total")
    .groupBy(UserPO::getStatus)
    .having("count(*) >= :minCount", Map.of("minCount", 10))
    .list();
```

生成 SQL：

```sql
SELECT status AS status, count(*) AS total
FROM sys_user
GROUP BY status
HAVING count(*) >= :lwp0
```

说明：

- `selectRaw` 只开放到表达式级别
- `having` 也必须使用命名参数

### 3.9 运行期语义开关：`allTenants / withDeleted`

```java
List<UserPO> users = lambdaQuery(UserPO.class)
    .allTenants()
    .withDeleted()
    .eq(UserPO::getStatus, 1)
    .list();
```

构造 SQL：

```sql
SELECT * FROM sys_user
WHERE status = :lwp0
```

运行期额外行为：

- `allTenants()`：本次执行临时关闭租户隔离
- `withDeleted()`：本次执行不再自动补逻辑删除条件
- 这两个开关只在本次 `list / one / page / count / exists` 执行期生效，执行结束后自动恢复

说明：

- 它们不直接改 `buildSql()` 的字符串，而是控制底层自动 SQL 改写作用域
- 这样保留了“查询 DSL 只描述业务条件，基础设施条件由底层接管”的原有思路

### 3.10 分页、计数、存在性

```java
Pager<UserDTO> page = lambdaQuery(UserPO.class, UserDTO.class)
    .eq(UserPO::getStatus, 1)
    .page(new Pager<>(1, 10));

long total = lambdaQuery(UserPO.class)
    .eq(UserPO::getStatus, 1)
    .count();

boolean exists = lambdaQuery(UserPO.class)
    .eq(UserPO::getPhone, "13800138000")
    .exists();
```

说明：

- 保持当前 `list / one / page / count / exists` 终结方法不变
- 这样老用户迁移成本最低

---

## 4. 更新 DSL

### 4.1 条件更新

```java
int rows = lambdaUpdate(UserPO.class)
    .set(UserPO::getUsername, "新名称")
    .eq(UserPO::getId, 100L)
    .update();
```

生成 SQL：

```sql
UPDATE sys_user
SET username = :lwp0
WHERE id = :lwp1
```

说明：

- 条件部分与 `lambdaQuery` 完全同构
- 底层仍走租户写入保护逻辑

### 4.2 动态更新：`setIfPresent`

```java
int rows = lambdaUpdate(UserPO.class)
    .setIfPresent(UserPO::getUsername, cmd.getUsername())
    .setIfPresent(UserPO::getPhone, cmd.getPhone())
    .eq(UserPO::getId, cmd.getId())
    .update();
```

生成 SQL：

```sql
UPDATE sys_user
SET username = :lwp0, phone = :lwp1
WHERE id = :lwp2
```

说明：

- `null / 空字符串 / 空集合` 默认跳过
- 用于“局部更新”场景比 `updatePO(ignoreNull)` 更可读

### 4.3 置空字段：`setNull`

```java
int rows = lambdaUpdate(UserPO.class)
    .setNull(UserPO::getPhone)
    .eq(UserPO::getId, 100L)
    .update();
```

生成 SQL：

```sql
UPDATE sys_user
SET phone = NULL
WHERE id = :lwp0
```

### 4.4 数值增减：`increase / decrease`

```java
int rows = lambdaUpdate(AccountPO.class)
    .increase(AccountPO::getBalance, 100)
    .decrease(AccountPO::getFrozenAmount, 50)
    .eq(AccountPO::getId, 1L)
    .update();
```

生成 SQL：

```sql
UPDATE account
SET balance = balance + :lwp0,
    frozen_amount = frozen_amount - :lwp1
WHERE id = :lwp2
```

说明：

- 这类能力比“先查再改再存”更适合并发敏感字段

### 4.5 无条件更新的安全默认值

```java
lambdaUpdate(UserPO.class)
    .set(UserPO::getStatus, 0)
    .update();
```

默认行为：

```text
抛出异常：lambdaUpdate 默认禁止无条件全表更新
```

只有显式声明才允许：

```java
lambdaUpdate(UserPO.class)
    .set(UserPO::getStatus, 0)
    .allowFullTable()
    .update();
```

说明：

- 这是和 `mybatis-plus` 不同的一个默认策略
- 我们强调“安全默认值”，把高风险操作改成显式确认

### 4.6 跨租户更新：`allTenants`

```java
int rows = lambdaUpdate(UserPO.class)
    .allTenants()
    .set(UserPO::getStatus, 0)
    .eq(UserPO::getId, 100L)
    .update();
```

构造 SQL：

```sql
UPDATE sys_user
SET status = :lwp0
WHERE id = :lwp1
```

运行期额外行为：

- 关闭本次执行的租户写入保护
- 不再自动追加 `AND tenant_id = :myjdbcTenantId`

说明：

- 适合后台跨租户修复数据
- 默认业务更新仍建议保留租户写入保护

### 4.7 全表更新与租户保护叠加

```java
int rows = lambdaUpdate(UserPO.class)
    .set(UserPO::getStatus, 0)
    .allowFullTable()
    .update();
```

构造 SQL：

```sql
UPDATE sys_user
SET status = :lwp0
```

运行期额外行为：

- 若当前启用了租户隔离且未调用 `allTenants()`，底层执行时会自动补成：

```sql
UPDATE sys_user
SET status = :lwp0
WHERE tenant_id = :myjdbcTenantId
```

- 若同时调用了 `allTenants()`，才会真的跨租户全表更新

这体现的是 `myjdbc` 的一个特点：  
**DSL 负责表达业务意图，最终写保护仍由底层统一接管。**

---

## 5. 片段安全规则

### 5.1 允许的场景

- `exists(subQuery, params)`
- `notExists(subQuery, params)`
- `having(expression, params)`
- `selectRaw(expression)`

### 5.2 约束

- 禁止 `;`
- 禁止 `--`
- 禁止 `/* */`
- 禁止 `insert / update / delete / drop / truncate / alter / create / grant / revoke / merge` 这类危险关键字出现在片段中
- 优先要求命名参数，不接受字符串拼值

### 5.3 设计意图

不是彻底禁止自定义 SQL，而是把它收口到“可控片段”级别。

---

## 6. raw SQL 安全护栏

对 `BaseDaoImpl / BaseServiceImpl` 的 raw SQL 方法，当前有下面这些默认约束：

### 6.1 查询方法只允许查询语句

适用方法：

- `queryListForSql`
- `querySingleForSql`
- `queryPageForSql`

约束：

- 只允许单条 `SELECT` 查询语句
- 误把 `UPDATE / DELETE / INSERT` 传进查询 API 时，会在框架层直接抛错

### 6.2 更新方法只允许 UPDATE 语句

适用方法：

- `updateForSql`

约束：

- 只允许单条 `UPDATE` 语句
- 默认禁止无 `WHERE` 的全表更新

如确有需要，可显式放开：

```java
int rows = MyJdbcScope.allowUnsafeWrite(() ->
    baseDao.updateForSql(
        "UPDATE sys_user SET status = :status",
        Map.of("status", 0),
        UserPO.class
    )
);
```

说明：

- `lambdaUpdate(...).allowFullTable().update()` 内部也是走同一套安全模型
- 这样 raw SQL 和 Lambda DSL 的心智模型保持一致

---

## 7. 作用域 API

除了链式 DSL，也可以直接在代码块层面控制执行作用域：

```java
List<UserPO> allUsers = MyJdbcScope.allTenants(() ->
    queryListForSql("SELECT * FROM sys_user", Map.of(), UserPO.class)
);

List<UserPO> usersWithDeleted = MyJdbcScope.withDeleted(() ->
    queryListForSql("SELECT * FROM sys_user", Map.of(), UserPO.class)
);

int rows = MyJdbcScope.allowUnsafeWrite(() ->
    baseDao.updateForSql(
        "UPDATE sys_user SET status = :status",
        Map.of("status", 0),
        UserPO.class
    )
);
```

说明：

- `MyJdbcScope` 适合 raw SQL、后台脚本、批处理和管理端场景
- `lambdaQuery/lambdaUpdate` 上的 `allTenants()/withDeleted()/allowFullTable()` 更适合日常业务代码

---

## 8. 与现有接口的关系

保留：

- `lambdaQuery(entityClass)`
- `lambdaQuery(entityClass, resultClass)`
- `list / one / page / count / exists`

新增：

- `lambdaUpdate(entityClass)`
- `when / any / all / exists / notExists / groupBy / having / selectAs / selectRaw`
- `set / setIfPresent / setNull / increase / decrease / allowFullTable`

不改变：

- 逻辑删除自动改写
- 租户隔离自动改写
- DTO 映射模式

---

## 9. 实施建议

建议按下面顺序逐步实现：

已经完成：

1. 通用条件内核，支撑 `when / any / all / or(...)`
2. `LambdaUpdateWrapper`
3. `exists / groupBy / having / selectRaw`
4. 第一版作用域开关与 raw SQL 安全护栏

下一阶段建议继续做：

1. Stream Query
2. Batch Framework
3. 更通用的插件链 / 数据权限能力
4. 更完整的 SQL 安全审计与调试预览

---

## 10. 文件定位建议

建议本文档保留在：

```text
docs/lambda-query-update-guide.md
```

`README.md` 继续负责总览；
本文档负责“逐个方法如何用、会生成什么 SQL、适合什么场景”。
