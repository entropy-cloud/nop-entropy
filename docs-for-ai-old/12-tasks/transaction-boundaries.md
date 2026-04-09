# 事务边界与回调（BizMutation / Transactional / ITransactionTemplate）

## 适用场景

- 你在 **BizModel** 中写 `@BizMutation` 方法，不确定是否需要手动开事务
- 你需要 **提交前/提交后回调**（beforeCommit/afterCommit）
- 你在非 BizModel 场景（普通组件/工具类）需要事务

## AI 决策提示

- ✅ 默认：BizModel 写入操作用 `@BizMutation`，平台会处理事务边界
- ✅ 需要事务事件/更细粒度控制：使用 `ITransactionTemplate`（在 `CrudBizModel` 里可用 `txn()` 获取）
- ⚠️ `@Transactional` 是 Nop 的注解（不是 Spring），仅用于非 BizModel 场景或需要显式传播级别时

## 最小闭环

### 1) BizModel 写操作：只用 @BizMutation

```java
import io.nop.api.core.annotations.biz.BizMutation;

@BizMutation
public void doSomethingWrite(IServiceContext context) {
    // 不需要额外加 @Transactional
    // 不需要手写 open/commit
}
```

### 2) 需要事务回调：用 ITransactionTemplate

```java
import io.nop.dao.txn.ITransactionTemplate;

@Inject
protected ITransactionTemplate txnTemplate;

public void writeWithCallbacks() {
    txnTemplate.runInTransaction(txn -> {
        // ... 数据库写操作

        txnTemplate.beforeCommit(null, () -> {
            // 提交前
        });

        txnTemplate.afterCommit(null, () -> {
            // 提交后
        });

        return null;
    });
}
```

### 3) 非 BizModel 方法：可用 @Transactional

```java
import io.nop.api.core.annotations.txn.Transactional;

@Transactional
public void nonBizWrite() {
    // ...
}
```

## 相关类

- `io.nop.biz.crud.CrudBizModel`（`txn()` 方法）
- `io.nop.dao.txn.ITransactionTemplate`
- `io.nop.api.core.annotations.txn.Transactional`
