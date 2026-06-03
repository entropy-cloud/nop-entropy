# 维度 07：BizModel 一致性审查

## 通过检查

- 所有 3 个 BizModel 正确继承 `CrudBizModel<T>` ✓
- 均调用 `setEntityName()` 设置实体名 ✓
- ORM/xmeta/xbiz 三者匹配 ✓
- 无 `@BizLoader` 使用 ✓
- 无 `Map<String,Object>` 反模式 ✓

## 发现

### [07-01] P2 — resolveTriggeredBy 方法在两个 BizModel 中完全重复

- **文件**: NopJobScheduleBizModel:224-235, NopJobFireBizModel:149-160
- **现状**: `resolveTriggeredBy` 方法在两个 BizModel 中各有一份完全相同的实现（各 12 行代码）。该方法根据触发来源解析触发者信息。
- **风险**: 任何逻辑修改需要在两处同步。
- **建议**: 提取到 nop-job-core 的共享工具类或基类中。

### [07-02] P2 — cancelFire 可取消性判定逻辑分裂在 BizModel 和 Store 之间

- **文件**: BizModel 层 `isCancelableStatus` 检查 3 个 Fire 状态；Store 层 `isCancelableFire` 额外增加 Task 完成检查
- **现状**: cancelFire 的可取消性检查被分裂为两层：BizModel 层先检查 Fire 状态是否在可取消范围内（3 种状态），Store 层再增加 Task 完成度检查。
- **风险**: 边界情况下 BizModel 预检查通过但 Store 层拒绝，导致用户收到误导性的错误消息（BizModel 层的错误信息不会反映 Store 层的拒绝原因）。
- **建议**: 统一可取消性判定逻辑，要么全部放在 BizModel 层（推荐，因为这是业务决策），要么在 Store 层拒绝时返回明确的错误信息。

### [07-03] P3 — NopJobFireBizModel 使用 fireStore.loadFire() 而非 requireEntity()

- **文件**: NopJobFireBizModel:48,63
- **现状**: `NopJobFireBizModel` 在加载 Fire 实体时使用 `fireStore.loadFire()` 方法，而非 `CrudBizModel` 提供的 `requireEntity(id, action, context)` 方法。
- **风险**: 当实体不存在时，错误消息中将缺少 action 名称上下文，降低问题排查效率。
- **建议**: 改用 `requireEntity()` 以获得更丰富的错误上下文信息。
