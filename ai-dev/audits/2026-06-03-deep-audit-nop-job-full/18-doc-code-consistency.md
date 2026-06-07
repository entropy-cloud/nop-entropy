# 维度 18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] concurrency-and-transactions.md 代码示例与实际实现严重不符

- **文件**: `docs-for-ai/02-core-guides/concurrency-and-transactions.md:89-103`
- **严重程度**: P1
- **现状**: 展示的 `insertTasksAndMarkFireDispatching` 示例使用 `updateEntityDirectly`，无 null 检查、无乐观锁重试。实际代码使用 `tryUpdateManyWithVersionCheck` + 异常回退。
- **风险**: 按文档编写的代码会绕过乐观锁保护，在高并发场景产生数据不一致。
- **建议**: 将示例代码更新为实际实现，包含 null 检查和乐观锁。
- **信心水平**: 高
- **误报排除**: 实际代码与文档的操作顺序也相反（文档先保存 task 再更新状态，实际先更新状态再保存 task）。
- **复核状态**: 未复核

### [维度18-02] architecture-principles.md 注解描述错误

- **文件**: `docs-for-ai/02-core-guides/architecture-principles.md:97`
- **严重程度**: P2
- **现状**: 文档描述 nop-job 使用 `@BizAction`，实际所有 BizModel 方法使用 `@BizMutation`。
- **建议**: 将 `@BizAction` 改为 `@BizMutation`。
- **信心水平**: 高
- **误报排除**: 已确认 nop-job 中无任何 @BizAction 使用。
- **复核状态**: 未复核

### [维度18-03] architecture-principles.md 错误地将 NopJobScheduleBizModel 归类为"无独立表"

- **文件**: `docs-for-ai/02-core-guides/architecture-principles.md:26`
- **严重程度**: P2
- **现状**: 文档将 NopJobScheduleBizModel 归为"无独立表的编排型聚合根"。实际 NopJobSchedule 有完整的 ORM 实体和数据库表映射。
- **建议**: 将其从"编排型例外"移除。
- **信心水平**: 高
- **误报排除**: CrudBizModel<NopJobSchedule> 继承证明它有独立实体。
- **复核状态**: 未复核

### [维度18-04] where-things-live.md 遗漏 nop-job 关键子模块

- **文件**: `docs-for-ai/01-repo-map/where-things-live.md:65`
- **严重程度**: P2
- **现状**: 文档仅列出 4 个 nop-job 子模块，实际有 11 个。遗漏了 coordinator/worker/core/api/web 等关键模块。
- **建议**: 补充至少 coordinator、worker、web、api、core 子模块路径。
- **信心水平**: 高
- **误报排除**: nop-job/pom.xml 确认有 11 个子模块。
- **复核状态**: 未复核

### [维度18-05] source-anchors.md 与 architecture-principles.md 对 I*Biz 接口位置约定矛盾

- **文件**: `source-anchors.md:29`, `architecture-principles.md:61`
- **严重程度**: P3
- **现状**: source-anchors 指向 nop-job-dao 中的 I*Biz 接口，但 architecture-principles 声明"I*Biz 在 api 模块定义"。
- **建议**: 补充区分"同模块内 I*Biz 在 dao 层"和"跨模块在 api 层"。
- **信心水平**: 高
- **误报排除**: AGENTS.md 明确校准"I*Biz 接口放在 dao 模块不是问题"。
- **复核状态**: 未复核
