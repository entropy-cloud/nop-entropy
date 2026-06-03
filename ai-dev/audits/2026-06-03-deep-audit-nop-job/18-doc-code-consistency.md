# 维度 18：文档-代码一致性审查

## 通过检查

- source-anchors.md 中的所有锚点验证正确 ✓
- concurrency-and-transactions.md 描述与实际代码一致 ✓
- transaction-boundaries.md 描述与实际代码一致 ✓

## 发现

### [18-01] P3 — architecture-principles.md 描述与实际扩展点不符

- **文件**: architecture-principles.md:97
- **现状**: 文档中描述 nop-job 使用 `@BizAction + 任务配置` 作为扩展点，但实际的扩展点是 `IJobInvoker` 接口。
- **影响**: 描述不准确的文档可能误导开发者对扩展机制的理解。
- **建议**: 将文档描述修正为 `IJobInvoker 接口`。
