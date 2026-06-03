# 维度 11：xmeta-BizModel 对齐审查

## 通过检查

- 无 `@BizLoader` 对齐偏差 ✓
- 字典定义一致 ✓
- 无死字段 ✓

## 发现

### [11-01] P2 — NopJobSchedule.xmeta scheduleStatus 字段 insertable=false 但 ORM 无 defaultValue

- **文件**: NopJobSchedule.xmeta:5
- **现状**: `NopJobSchedule.xmeta` 将 `scheduleStatus` 设置为 `insertable=false`，但 ORM 模型中该字段标记为 `mandatory=true` 且没有 `defaultValue`。
- **影响**: 通过标准 CRUD API 创建 Schedule 时，`scheduleStatus` 既不能通过 API 设置（insertable=false），又没有默认值（ORM mandatory），可能导致 NOT NULL 违反错误。
- **建议**: 在 ORM 模型中为 `scheduleStatus` 设置 `defaultValue`（如 `"0"` 表示初始状态），或在 xmeta 中允许该字段在创建时可写入。

### [11-02] P3 — NopJobFire.xmeta 缺少 readonly 声明

- **文件**: NopJobFire.xmeta
- **现状**: 以下引擎管理字段缺少 `readonly`（或 `insertable=false`/`updatable=false`）声明：
  - `triggerSource`
  - `triggeredBy`
  - `jobParamsSnapshot`
  - `executorKind`
  - `retryPolicyId`
- **建议**: 在 xmeta Delta 中补充这些字段的只读声明。

### [11-03] P3 — NopJobTask.xmeta 缺少 readonly 声明（与维度 10 发现重叠）

- **文件**: NopJobTask.xmeta
- **现状**: 以下系统管理字段缺少 readonly 声明：
  - `taskPayload`
  - `progress`
  - `progressMessage`
  - `targetHost`
  - `shardingIndex`
  - `shardingTotal`
- **注**: 此发现与维度 10 [10-01] 重叠，修复一处即可同时解决两个维度的问题。
- **建议**: 在 xmeta Delta 中补充这些字段的只读声明。
