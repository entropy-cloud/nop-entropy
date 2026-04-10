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
- `defaultPrepareUpdate(...)`
- `defaultPrepareQuery(...)`
- `prepareFindPageQuery(...)`
- `defaultPrepareDelete(...)`
- `afterEntityChange(...)`
- `isAllowGetDeleted()`

## 这些扩展点分别适合什么

| 扩展点 | 适用场景 |
|------|---------|
| `defaultPrepareSave(...)` | 保存前补字段、做轻量校验、初始化状态 |
| `defaultPrepareUpdate(...)` | 更新前补字段、做轻量校验 |
| `defaultPrepareQuery(...)` | 给查询补默认过滤或排序前处理 |
| `prepareFindPageQuery(...)` | 需要更深度控制 query 预处理流程时使用 |
| `defaultPrepareDelete(...)` | 删除前做引用检查之外的补充校验 |
| `afterEntityChange(...)` | 保存、更新、删除后追加统一后处理 |
| `isAllowGetDeleted()` | 控制是否允许读取逻辑删除数据 |

## 默认写法提示

1. 多数 override 应先调用 `super`，保留 `CrudBizModel` 的默认行为。
2. 如果只是给保存和更新都补同一段规则，优先分别覆盖 `defaultPrepareSave(...)` / `defaultPrepareUpdate(...)`。
3. 如果需求已经变成独立业务动作，不要继续堆在 hook 里。

仓库里的真实参考：

1. `nop-auth/nop-auth-service/.../NopAuthRoleBizModel.java` 在 `defaultPrepareSave(...)` 和 `defaultPrepareUpdate(...)` 中复用 `checkAllowEdit(...)`。
2. `nop-rule/nop-rule-service/.../NopRuleDefinitionBizModel.java` 在 save / update hook 中补导入和模型校验。

## 什么时候不要这样做

如果逻辑已经不是“前后补一小段”，而是独立业务动作或复杂编排流程，就不要继续堆在 hook 里，而应该考虑 BizModel 自定义方法或 Processor。

## 常见坑

1. 一上来就直接 `dao().saveEntity(...)`。
2. 把复杂流程全塞进 hook。
3. 在 hook 里继续使用 `private` 字段注入。

## 相关文档

- `../02-core-guides/service-layer.md`
- `./choose-entity-bizmodel-processor.md`
