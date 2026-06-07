# 维度 03：API 表面积与契约一致性

## 第 1 轮（初审）

### [维度03-01] BizModel 自定义方法缺少 @BizMutation 操作名

- **文件**: `NopJobScheduleBizModel.java:57,69,84,97,107,124`
- **证据片段**: BizModel 使用 `@BizMutation`（无值），接口使用 `@BizMutation("enableSchedule")`（有值）。
- **严重程度**: P3
- **现状**: Nop 框架通过方法名自动推导操作名，运行时行为正确。
- **建议**: 保持现状，这是 Nop 标准模式。
- **信心水平**: 95%
- **复核状态**: 未复核

### [维度03-02] xmeta 引擎字段 insertable/updatable=false 与 BizModel 直接写入不一致

- **严重程度**: P3（信息性）
- **现状**: xmeta 保护外发 API，BizModel 通过 Store/Dao 直接操作。这是 Nop 标准架构模式。
- **信心水平**: 98%
- **复核状态**: 未复核

### [维度03-03] cancelFire 的 TOCTOU 间隔

- **文件**: `NopJobFireBizModel.java:48-59`
- **严重程度**: P3
- **现状**: requireEntity 和 fireStore.cancelFire 使用不同事务上下文，存在 TOCTOU。但 Store 层有独立的状态检查保证并发安全。
- **建议**: 可在注释中说明前置校验 vs Store 层保证。
- **信心水平**: 90%
- **复核状态**: 未复核
