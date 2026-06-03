# 维度 04：ORM 模型审查

## 通过检查

- 主键使用 VARCHAR(32) + seq 策略 ✓
- 域类型正确 ✓
- displayName 完全 i18n-en 本地化 ✓
- 索引覆盖所有 Store 查询路径 ✓
- 字典定义与 _NopJobCoreConstants 一致 ✓

## 发现

### [04-01] P2 — cancelFire 路径遗漏 totalFireCount/failFireCount 计数器更新

- **文件**: JobFireStoreImpl:169-177
- **现状**: `cancelFire` 方法在取消 Fire 记录时，未执行 `totalFireCount++` 和 `failFireCount++` 的更新操作。其他 3 个终态路径（completion、timeout、overlay-cancel）均正确维护了这些计数器。
- **影响**: 计数器不变式 `fireCount = activeFireCount + totalFireCount` 在每次通过 BizModel API 执行 cancel 时漂移 +1。随着取消操作累积，仪表盘统计数据将系统性偏低。
- **建议**: 在 `cancelFire` 方法的状态更新逻辑中补充 `totalFireCount` 和 `failFireCount` 的递增。

### [04-02] P3 — version 域类型使用 BIGINT 而非 INTEGER

- **文件**: ORM 模型中的 version 域定义
- **现状**: nop-job 的 version 域使用 BIGINT 类型，而 nop-auth、nop-task、nop-wf 等模块均使用 INTEGER 类型。SKILL.md 推荐使用 INTEGER。
- **影响**: 类型不一致可能影响代码生成和跨模块统一处理。功能上无影响（BIGINT 包含 INTEGER 范围）。
- **建议**: 将 version 域类型改为 INTEGER 以保持跨模块一致性。
