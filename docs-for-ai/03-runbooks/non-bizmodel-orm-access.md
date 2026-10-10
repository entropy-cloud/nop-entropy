# 非 BizModel 场景的 ORM 访问

## 适用场景

- 你在 `@Scheduled` 定时任务中需要读写实体
- 你在事件监听器、Quartz Job、自定义线程或其它非 BizModel 组件中使用 DAO
- 你需要批量处理大量数据

## 问题

`@BizMutation` 自动管理 ORM Session 和事务边界。非 BizModel 代码不会自动获得这些：

```java
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MyTask {
    @Inject
    IDaoProvider daoProvider;

    @Scheduled(every = "60s")
    void doSomething() {
        IEntityDao<MyEntity> dao = daoProvider.daoFor(MyEntity.class);
        // 这里的 dao 操作没有 ORM Session，也没有事务
        // dao.findAll() 返回的实体是游离状态
        // entity.setXxx() 不会自动持久化
    }
}
```

## 解决方案

### 仅需 ORM Session（无事务或框架自动管理事务）

在方法上标注 `@io.nop.api.core.annotations.orm.SingleSession`：

```java
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MyTask {
    @Inject
    IDaoProvider daoProvider;

    @Scheduled(every = "60s")
    @SingleSession
    void process() {
        IEntityDao<MyEntity> dao = daoProvider.daoFor(MyEntity.class);
        List<MyEntity> list = dao.findAll();
        for (MyEntity e : list) {
            if (someCondition(e)) {
                e.setStatus("processed");
                // 实体被 Session 跟踪，方法结束时自动 flush
            }
        }
    }
}
```

`@SingleSession` 在方法进入时创建 ORM Session（如果当前没有），方法退出时自动 flush 并关闭。

如果需要强制新建 Session（不继承外层已有的），用 `@SingleSession(requireNew = true)`。

### 需要独立数据库事务

叠加 `@io.nop.api.core.annotations.txn.Transactional`：

```java
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.annotations.txn.Transactional;

@Scheduled(every = "60s")
@SingleSession
@Transactional
void process() {
    // 整个方法在一个数据库事务中
    // 异常时自动回滚
}
```

使用 `@Transactional` 的 `txnGroup` 参数可以指定事务组（对应不同数据源），`propagation` 参数控制传播行为。`propagation` 的默认值是 `TransactionPropagation.REQUIRED`。

### 独占新事务（长时间运行的调度任务）

对于长时间运行的定时任务，每条操作应在独立事务中完成：

```java
import io.nop.api.core.annotations.txn.TransactionPropagation;

@Scheduled(every = "60s")
@SingleSession
void process() {
    // 外层 Session 保持整个扫描周期的实体会话
    storeMethodA();  // 每个 Store 方法自有独立事务
    storeMethodB();
}

// 在另一个 Bean 或 Store 类中
@io.nop.api.core.annotations.txn.Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
void storeMethodA() { ... }

@io.nop.api.core.annotations.txn.Transactional(propagation = TransactionPropagation.REQUIRES_NEW)
void storeMethodB() { ... }
```

### 手动控制 Session

当需要更精细的 Session 生命周期控制（如分批释放内存）时，使用 `IOrmTemplate`：

```java
import io.nop.orm.IOrmTemplate;

@Inject
IOrmTemplate ormTemplate;

void process() {
    ormTemplate.runInSession(session -> {
        // 在这个回调中，DAO 操作有有效的 ORM Session
    });
}
```

`IOrmTemplate.runInSession()` 会在回调执行期间保证存在一个 ORM Session。如果当前线程已有 Session 则复用，否则新建。

### Session 与事务是两条正交的轴（重要）

**Session 是持久化上下文**（identity map、脏跟踪、flush 时机），**事务是连接级提交边界**（由 `ITransactionTemplate` / `TransactionRegistry` 管理）。两者独立选择、互不决定：

- ORM 的所有 SQL 执行统一经 `JdbcTemplateImpl.withTxn`，以 `SUPPORTS` 传播挂到**当前线程已注册的 `ITransaction`** 上复用其连接；无注册事务时用一个未 open 的事务对象承载普通连接。commit/rollback 只发生在业务边界的 `ITransactionTemplate.runInTransaction`。
- `OrmSessionImpl.close()` 只清空持久化上下文，**不持有、不提交、不回滚任何连接**；连接按次执行获取、用完即还（`eagerReleaseConnection`）。

由此推论：**`runInNewSession` 不会改变事务归属**——在业务事务内新开的 session，其写入仍在同一连接上，随外层事务提交/回滚。session 的选择只决定"操作发生在哪个持久化上下文里"。

### 多步 DAO 操作的作用域选择：runInSession，不要默认 runInNewSession

跨多个 DAO 调用的复合操作（如 load → 校验 → save/delete）必须包在一个显式 session 作用域里，否则无外层 session 时每个 DAO 调用各开一次性 session：load 得到的实体在 save 时已 detached，`saveOrUpdate` 按 `contains(entity)` 判分支会误走 save 分支（`save-entity-not-transient`）。

作用域用 **`ormTemplate.runInSession(...)`**：有外层 session 时复用（与 `IEntityDao` 自身约定一致），无外层时自动退化为 newSession。不要默认 `runInNewSession`：

- 强制隔离没有事务收益（见上节：事务归属与 session 正交）；
- 会让 store 内部的实体状态与调用方上下文脱节——同一实体在外层 session 中留下过期副本，flush 期回调场景下还会绕开引擎的 flush 中变更机制（`addChangeDuringFlush` / `flushChanged`）。

典型案例：`DaoResourceFileStore.attachFile/detachFile`（P1-3.8-002 修订，2026-10-10 作者裁定）。

## 批量处理 — 分页流式

切勿使用 `dao.findAll()` 全量加载一个可能持续增长的表。对于定时任务，使用 **分页流式**：

```java
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.quarkus.scheduler.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class BatchTask {

    private static final int PAGE_SIZE = 200;

    @Inject
    IDaoProvider daoProvider;

    @Scheduled(every = "60s")
    @SingleSession
    void processBatch() {
        IEntityDao<MyEntity> dao = daoProvider.daoFor(MyEntity.class);

        int page = 0;
        List<MyEntity> batch;

        do {
            QueryBean query = new QueryBean();
            query.setPageSize(PAGE_SIZE);
            query.setOffset(page * PAGE_SIZE);

            batch = dao.findListByQuery(query);
            for (MyEntity e : batch) {
                if (someCondition(e)) {
                    e.setStatus("processed");
                }
            }

            // 每页处理完后可以主动 flush 释放一级缓存
            // ormTemplate.flushSession();
            // ormTemplate.clearSession();

            page++;
        } while (!batch.isEmpty());
    }
}
```

对十万级以上大表，考虑加时间范围条件（如只处理最近 N 天的数据），避免扫描全表。

## 决策表

| 场景 | 方案 |
|------|------|
| 需要 ORM Session，不需要显式事务 | `@SingleSession` |
| 方法的整个逻辑应在一个数据库事务中 | `@SingleSession` + `@Transactional` |
| 每个内部操作需要独立事务（长时间扫描） | 外层 `@SingleSession` + 内层 `@Transactional(REQUIRES_NEW)` |
| 需要精细控制 Session 生命周期（分批释放内存） | `IOrmTemplate.runInSession()` |
| BizModel 中的普通写操作 | 只用 `@BizMutation` |

## 常见坑

1. **误用 `jakarta.transaction.Transactional`** 代替 `io.nop.api.core.annotations.txn.Transactional` — Nop 事务拦截器只识别前者
2. **`@SingleSession` 没有开启数据库事务** — 它只绑定 ORM Session 生命周期；事务需要 `@Transactional`
3. **`dao.findAll()` 全量加载不可控增长表** — 定时任务必须分页或加时间范围过滤
4. **修改后不持久化** — 没有 ORM Session 时 `entity.setXxx()` 是空操作
5. **`@SingleSession` + `@Transactional` 已覆盖大多数场景** — 不需要手动注入 `IOrmTemplate` 调用 `runInSession()`

## 相关文档

- `../02-core-guides/concurrency-and-transactions.md`（Scanner 的 `@SingleSession` + `REQUIRES_NEW` 模式详解）
- `../03-runbooks/transaction-boundaries.md`（事务边界选择）
- `../04-reference/common-java-helpers.md`
