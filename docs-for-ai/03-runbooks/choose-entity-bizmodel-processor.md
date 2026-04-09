# 选择 Entity、BizModel 还是 Processor

## 适用场景

- 你已经知道要加一段逻辑，但还不确定应该放在哪一层。

## 默认判断

| 场景 | 默认位置 |
|------|---------|
| 纯状态判断、只读计算 | Entity |
| 面向 API 的查询和修改 | BizModel |
| 多步骤 orchestration、跨聚合流程 | Processor |
| 跨多个 Processor 复用的单一动作 | Step |

## 快速判断

### 放 Entity

适合：

- `isXxx()`
- `canXxx()`
- `calculateXxx()`

### 放 BizModel

适合：

- `@BizQuery`
- `@BizMutation`
- 需要 `IServiceContext`
- 需要框架安全 API

### 放 Processor

适合：

- 单个业务动作已经明显是多步骤流程
- 需要多个 BizModel 或外部系统协作
- 逻辑会被多个入口复用

## 常见坑

1. 在 Entity 中写持久化或外部调用。
2. BizModel 一个方法塞满全部业务流程。
3. 还没出现复用就过早抽 Step。

## 相关文档

- `../02-core-guides/domain-logic-and-ddd.md`
- `../02-core-guides/service-layer.md`
- `./implement-complex-business-flow.md`
- `./write-bizmodel-method.md`
