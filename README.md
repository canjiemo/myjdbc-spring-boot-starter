# myjdbc-spring-boot-starter

> 🚀 一个专注 **WHERE 条件构建** 的 Lambda DSL，让 JDBC 查询更优雅

---

## 🔥 为什么需要它？

在实际开发中，我们经常写这样的代码：

### ❌ 传统写法（充满 if 判断）

```java
if (name != null) {
    wrapper.like("name", name);
}
if (status != null) {
    wrapper.eq("status", status);
}
```

👉 问题：

* ❗ if 判断爆炸
* ❗ 可读性差
* ❗ 条件难复用

---

## ✅ 使用 myjdbc

```java
query()
    .like(User::getName, name)
    .eq(User::getStatus, status);
```

👉 自动忽略 `null` 条件，无需 if 判断

---

# ✨ 核心特性

* ✅ **Lambda 类型安全**（避免字符串字段名）
* ✅ **自动过滤 null 条件**（告别 if 判断）
* ✅ **条件组合（and / or /any / all）**
* ✅ **支持 exists / 子查询**
* ✅ **SQL + DSL 混用**
* ✅ **基于 JDBC，轻量无侵入**

---

# 📖 使用文档（详细指南）

👉 👉 👉 **完整用法请查看：**
👉 https://github.com/canjiemo/myjdbc-spring-boot-starter/blob/master/docs/lambda-query-update-guide.md

> 包含：
>
> * Lambda 查询完整 API
> * update 用法
> * 高级组合示例
> * 实战用法说明

---

# 💡 示例

## 1️⃣ 基础查询

```java
query()
    .eq(User::getStatus, 1)
    .like(User::getName, keyword);
```

---

## 2️⃣ 动态条件（无 if）

```java
query()
    .eq(User::getStatus, status)
    .like(User::getName, keyword);
```

👉 当 `status / keyword == null` 时自动忽略

---

## 3️⃣ 条件组合（AND / OR）

```java
query()
    .and(q -> q.eq(User::getStatus, 1)
               .like(User::getName, keyword))
    .or(q -> q.eq(User::getStatus, 2));
```

---

## 4️⃣ 高级组合（any / all）

```java
query()
    .any(
        q -> q.eq(User::getStatus, 1),
        q -> q.eq(User::getStatus, 2)
              .like(User::getName, keyword)
    );
```

---

## 5️⃣ exists 子查询

```java
query()
    .exists(sub -> sub
        .from("orders")
        .eq("orders.user_id", User::getId)
    );
```

---

## 6️⃣ 条件复用（🔥 推荐）

```java
Consumer<LambdaFilter<User>> activeUser =
    q -> q.eq(User::getStatus, 1);

Consumer<LambdaFilter<User>> keywordFilter =
    q -> q.like(User::getName, keyword);

query().all(activeUser, keywordFilter);
```

---

# 🧩 SQL + DSL 混用

```java
query("SELECT * FROM user")
    .like(User::getName, keyword)
    .eq(User::getStatus, 1);
```

👉 不替代 SQL，只增强 WHERE

---

# 🔍 调试（推荐）

```java
query()
    .eq(User::getStatus, 1)
    .like(User::getName, "张")
    .toSql();
```

```java
.toParams();
```

```java
.toDebugSql();
```

---

# 🚀 快速开始

## 1️⃣ 引入依赖

```xml
<dependency>
    <groupId>io.github.canjiemo</groupId>
    <artifactId>myjdbc-spring-boot-starter</artifactId>
    <version>最新版本</version>
</dependency>
```

---

## 2️⃣ 使用

```java
query()
    .eq(User::getStatus, 1)
    .like(User::getName, keyword);
```

---

# 🧠 设计理念

> ❗不替代 SQL，而是增强 SQL

* 专注 WHERE 条件构建
* 不做 ORM
* 不做复杂 SQL DSL（如 JOIN / GROUP BY）
* 保持简单、可控、可读

---

# 🆚 对比

| 特性        | MyBatis | MyBatis-Plus | myjdbc      |
| --------- | ------- | ------------ | ----------- |
| 动态条件      | ❌ 手写 if | ⚠️ 部分支持      | ✅ 自动过滤 null |
| Lambda 支持 | ❌       | ✅            | ✅           |
| 条件组合      | ❌       | ⚠️ 一般        | ✅ 强         |
| SQL 控制力   | ✅       | ⚠️           | ✅           |
| 学习成本      | 低       | 中            | 低           |

---

# 🎯 适用场景

* ✔️ 复杂动态查询（条件多、变化多）
* ✔️ 不想写大量 if 判断
* ✔️ 想保持 SQL 控制权
* ✔️ MyBatis / JDBC 项目增强

---

# 📌 总结

> 👉 用更少的代码，写更清晰的查询逻辑

---

# ⭐ 如果这个项目对你有帮助，欢迎 Star！
