# 事务边界

## 适用场景

- 你在 BizModel 中写 `@BizMutation` 方法，不确定是否需要手动开事务。
- 你需要提交后回调。
- 你在非 BizModel 场景需要显式事务控制。

## AI 决策提示

- 普通 BizModel 写操作默认只用 `@BizMutation`。
- 需要事务回调时，优先使用 `txn().afterCommit(...)` 或 `ITransactionTemplate`。
- `@Transactional` 仅用于非 BizModel 场景或必须显式控制传播级别时。

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

## 相关文档

- `../02-core-guides/service-layer.md`
- `../02-core-guides/error-handling.md`
- `../04-reference/source-anchors.md`
