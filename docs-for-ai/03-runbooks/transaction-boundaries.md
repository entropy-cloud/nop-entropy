# 事务边界

## 适用场景

- 你在 BizModel 中写 `@BizMutation` 方法，不确定是否需要手动开事务。
- 你需要提交后回调。
- 你在非 BizModel 场景需要显式事务控制。

## AI 决策提示

- 普通 BizModel 写操作默认只用 `@BizMutation`（R1.10 起 mutation 经 action 装饰器自动进入事务，见下方入口矩阵）。
- 需要事务回调时，优先使用 `txn().afterCommit(...)` 或 `ITransactionTemplate`。
- `@Transactional` 仅用于非 BizModel 场景或必须显式控制传播级别时。
- `txn().afterCommit(...)` 只能在当前已经有事务时调用；如果当前不在事务里，会直接报错。

## 事务入口矩阵（谁在哪里开事务）

biz 层写动作的事务由**入口层决定**，各入口现状（R1.10 起口径，代码锚见下）：

| 入口 | 事务来源 | 说明 |
|------|---------|------|
| GraphQL mutation operation（`/graphql`、`/r/`、`/p/`） | operation 层 `GraphQLTransactionOperationInvoker`（外层 REQUIRED） + action 装饰器（内层，join 外层事务） | `biz-defaults.beans.xml:50-58` |
| biz action 装饰器（所有经 biz 管道的 action） | `TransactionActionDecoratorCollector`：xbiz 未声明 `<txn>` 时 **mutation 默认开 REQUIRED 事务**，query 不加；`<txn transactional="false">` 可显式关断 | R1.10 起注册（`biz-defaults.beans.xml:42-48`，`on-bean=nopTransactionTemplate` 守卫——无 nop-dao 的 IoC 环境豁免）；收集器逻辑 `TransactionActionDecoratorCollector.java:45-51` |
| `BizActionInvoker.invokeActionSync/Async` 直调 | helper 自身 `runInSession` + 非 query 显式 `REQUIRED`（`BizActionInvoker.java:34-76`） | 与 action 装饰器叠加时 join 同一事务 |
| `I*Biz` 代理注入（job/MQ/service bean 后台直调） | 经 `BizProxyInvocationHandler.buildActionMap` → `requireAction` 取**已装饰** action——mutation 吃到 action 装饰器事务（R1.10 起） | query 无事务 |

**query 全程无事务**（operation 层仅 mutation 走 transactional invoker；装饰器仅 mutation 默认加）。

## 普通 BizModel 默认模式

```java
@BizMutation
public void doSomethingWrite(IServiceContext context) {
    // 不需要额外加 @Transactional
}
```

## 提交后回调

```java
txn().afterCommit(null, () -> {
    // 提交后副作用
});
```

这个模式最适合：

1. 普通 `@BizMutation` 方法。
2. 显式 `runInTransaction(...)` 包裹的代码块。

不要在默认无事务的 query 场景直接调用它。

## 非 BizModel 场景

如果你处在普通组件、store、infra 或其他非 BizModel 场景，才考虑：

- `@Transactional`
- `ITransactionTemplate`
- `REQUIRES_NEW`

## 边界场景提示

当前仓库里，`nop-job` 的 store 层存在 `REQUIRES_NEW + saveEntityDirectly()` 这类实现。它们是边界样例，不是普通业务层默认模板。

## 常见坑

1. `@BizMutation @Transactional`
2. 在普通 BizModel 中复制 store 层的 `REQUIRES_NEW` 模式
3. 把 after-commit 副作用直接塞进事务体主体里处理

## 事务对象状态机语义（2.3-C2-016，plan 2553 裁定 D）

直接持有 `ITransaction`（非 BizModel 场景 / 自管事务模板）时，以下语义为当前契约：

- **markRollbackOnly 后（同步）`commit()` = 自动回滚 + WARN**（`nop.warn.dao.txn-commit-on-rollback-only`），不再抛 `ERR_TXN_ROLLBACK_ONLY_NOT_ALLOW_COMMIT`——调用方若依赖旧异常信号分支，需改为检查事务终态。终态为 ROLLED_BACK。（限定：`commitAsync()` 与公开 `beforeCommit()` 仍抛该错误码——自动回滚语义仅同步 commit 主路径。）
- **二次 commit = no-op + WARN**（`nop.warn.dao.txn-commit-after-committed`）——不再重复触发 afterCommit/afterCompletion 监听器（原形态的监听器重复通知缺陷已消除）。
- **commit 的 doCommit 抛错**：监听器仍收 `afterCompletion(UNKNOWN)`，事务尽力置 ROLLED_BACK 后抛 `ERR_TXN_COMMIT_FAIL`（原始异常在 cause）。
- **提交快照开始后 addSubTransaction 显式拒绝**（`nop.err.dao.txn.late-sub-transaction`）——提交期迟到的子事务注册不再静默丢写。
- **状态机误用错误码 = `ERR_TXN_ALREADY_FINISHED`**（plan 2587）：终态（COMMITTED/ROLLED_BACK/CLOSED）后再 `commit()`/`rollback()` 抛该码并附 `state` 参数；`open()` 双开仍为 `ERR_TXN_ALREADY_STARTED`。
- **`close()` 任意状态 CAS 到 CLOSED，双 close no-op**（plan 2587）——第二次 `close()` 不重复触发 onClose 监听器、不重跑 `doClose`；任何状态下调用都不抛异常（`close` 经 `IoHelper.safeClose` 在 finally 调用）。
- **`FutureHelper.bindCancel(promise, future)` 收紧**：仅当 promise 以 `CancellationException` 终结时才 cancel 关联 future；正常/异常完结不再连带取消（原「任意完结即取消」的误取消面已修复——依赖旧行为清理排队任务的调用方需自行取消）。

## 锁租约/超时对象的时间语义（R4-32，plan 2566）

`IResourceLockState.getExpireTime()` / `TimeOut.getExpireTime()` 在本地实现（LocalResourceLockManager 等）下返回**单调时钟相对值（纳秒）**——仅可与同进程 `CoreMetrics.nanoTime()` 比较或相减求剩余，不得持久化、不得与 `currentTimeMillis`/epoch 比较、不得跨进程传输。其他实现（如 DB 锁）语义以实现方为准。

## 相关文档

- `../02-core-guides/service-layer.md`
- `../02-core-guides/error-handling.md`
- `../02-core-guides/ioc-and-config.md`（阻塞等待有界化：throttle 60s / rateLimit 5min 缺省上界）
- `../04-reference/source-anchors.md`
