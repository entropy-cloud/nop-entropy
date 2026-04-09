# 扩展 CRUD 钩子

## 适用场景

- 你只想在保存、更新、删除、查询前后追加逻辑。
- 你想保留 `CrudBizModel` 的内置流程，而不是自己重写整套 CRUD。

## AI 决策提示

- 优先重写 `CrudBizModel<T>` 的扩展点。
- 不要为了少量前后处理直接退回到底层 DAO。

## 最小模板

```java
@Override
protected void defaultPrepareSave(EntityData<User> entityData, IServiceContext context) {
    super.defaultPrepareSave(entityData, context);
    User user = entityData.getEntity();
    // 补充或修正字段
}
```

## 常见扩展点

- `defaultPrepareSave(...)`
- `defaultPrepareQuery(...)`
- `defaultPrepareDelete(...)`

## 什么时候不要这样做

如果逻辑已经不是“前后补一小段”，而是独立业务动作或复杂编排流程，就不要继续堆在 hook 里，而应该考虑 BizModel 自定义方法或 Processor。

## 常见坑

1. 一上来就直接 `dao().saveEntity(...)`。
2. 把复杂流程全塞进 hook。
3. 在 hook 里继续使用 `private` 字段注入。

## 相关文档

- `../02-core-guides/service-layer.md`
- `./choose-entity-bizmodel-processor.md`
