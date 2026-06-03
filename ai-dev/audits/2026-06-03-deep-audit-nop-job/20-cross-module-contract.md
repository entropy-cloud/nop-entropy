# 维度 20：跨模块契约审查

## 通过检查

- `IJobRetryBridge` 契约健全（NoOp 默认实现 + adapter 覆盖）✓
- `IJobScheduleStore` / `IJobFireStore` / `IJobTaskStore` 接口定义清晰 ✓
- nop-retry 方法链完全类型安全 ✓

## 发现

### [20-01] P3 — JobFireFailedEvent 缺少 scheduledFireTime 和 jobParamsSnapshot 字段

- **文件**: JobFireFailedEvent
- **现状**: `JobFireFailedEvent` 缺少 `scheduledFireTime` 和 `jobParamsSnapshot` 字段。如果 retry 回调需要这些信息来决定重试策略，当前的事件模型不足以支撑。
- **影响**: 低风险——如果回调通过 ID 加载完整 Fire 实体，则可以获取这些信息。但如果事件消费者需要在不访问数据库的情况下做决策，则当前设计不足。
- **建议**: 评估 retry 回调的实际需求，如果需要这些字段则补充到事件中。
