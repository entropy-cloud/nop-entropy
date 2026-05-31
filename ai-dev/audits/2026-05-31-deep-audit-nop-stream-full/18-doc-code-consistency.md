# 维度 18：文档-代码一致性

## 第 1 轮（初审）

### [维度18-01] window-design.md 已知限制 #1 和 #2 已过时（Plan 51 修复未回注）

- **文件**: `ai-dev/design/nop-stream/window-design.md:354-355`
- **证据片段**:
  ```
  354: 1. **API 层三个方法全部抛 UnsupportedOperationException** — WindowedStreamImpl apply()/aggregate()/reduce() 全部抛异常
  355: 2. **SimpleAccumulator.getLocalValue() bug** — 累加器类型腐蚀
  ```
- **严重程度**: P2
- **现状**: 两个已知限制已被 Plan 51 修复（component-roadmap.md 已标注），但 window-design.md 未同步更新。
- **风险**: 开发者依据过时文档做决策，浪费时间排查已修复的问题。
- **建议**: 更新 window-design.md 的已知限制，标注为已修复并引用 Plan 51。
- **信心水平**: 确定
- **误报排除**: 不是误报——component-roadmap.md 明确标注了 Plan 51 修复。
- **复核状态**: 未复核

### [维度18-02] window-design.md 已知限制 #5 不正确（Session Window 已存在）

- **文件**: `ai-dev/design/nop-stream/window-design.md:358`
- **证据片段**:
  ```
  358: 5. **无 Session Window 实现** — MergingWindowAssigner 和合并窗口路径已实现，但缺少具体的 Session Window Assigner
  ```
- **严重程度**: P2
- **现状**: EventTimeSessionWindows 已存在于 `nop-stream-core/.../windowing/assigners/EventTimeSessionWindows.java`（76 行完整实现）。
- **建议**: 更新文档标注为已实现。
- **信心水平**: 确定
- **误报排除**: 不是误报——EventTimeSessionWindows 是可用的具体实现。
- **复核状态**: 未复核

### [维度18-03] state-management-design.md 已知限制 #6 过时

- **文件**: `ai-dev/design/nop-stream/state-management-design.md:246`
- **证据片段**:
  ```
  246: 6. **无状态恢复路径** — AbstractUdfStreamOperator.snapshotState() 被注释掉
  ```
- **严重程度**: P2
- **现状**: AbstractUdfStreamOperator.snapshotState() 已是完整的方法实现（行 87-106），且多个算子有活跃的覆写。
- **建议**: 更新文档。
- **信心水平**: 确定
- **误报排除**: 不是误报——代码验证确认方法已正常实现。
- **复核状态**: 未复核

### [维度18-04] time-model-design.md §9 与 §7 自相矛盾

- **文件**: `ai-dev/design/nop-stream/time-model-design.md:229 vs :200`
- **严重程度**: P2
- **现状**: §7 声称 TimestampsAndWatermarksOperator 已集成到 execute()，§9 已知限制说未集成。
- **建议**: 删除 §9 的过时限制。
- **信心水平**: 确定
- **误报排除**: 不是误报——同文档自相矛盾。
- **复核状态**: 未复核

### [维度18-05] cep-design.md §6.2 提及的 CepWindowOperator/CepWindowAssigner/CepWindowTrigger 不存在

- **文件**: `ai-dev/design/nop-stream/cep-design.md:254-259`
- **证据片段**:
  ```
  254: ### 6.2 CepWindowOperator
  256: CepWindowOperator 将 CEP 作为窗口算子集成：
  257: - 使用 CepWindowAssigner 将事件分配到 CEP 窗口
  258: - 使用 CepWindowTrigger 在模式匹配完成时触发
  ```
- **严重程度**: P3（独立复核降级：设计文档描述规划功能是正常的，非"过时的已知限制"）
- **现状**: 三个类在代码库中不存在，只有 CepOperator 是实际的 CEP 算子。设计文档描述的是目标架构/规划功能，不是对当前实现状态的错误断言。
- **建议**: 在设计文档中标注此功能为"规划中"或"未实现"。
- **信心水平**: 确定
- **误报排除**: 不是误报——类确实不存在，但设计文档描述规划功能是正常行为。
- **复核状态**: 已降级至 P3

### [维度18-06~12] 其他低优先级文档不一致（P3）

- README.md:27 comparison.md 描述对象与实际内容不符
- time-model-design.md:168 TimestampsAndWatermarksOperator 归属标错为 runtime（实际在 core）
- architecture.md:284-286 RuntimeNode/NodeLease 类不存在（实际为 NodeInfo/LeaseInfo）
- core-design.md:33-44 StreamComponents 的 Map 类型化声明与实际 Map<String, Object> 不符
- checkpoint-design.md:233-248 CheckpointParticipant 接口签名差异
- architecture.md:53,68 nop-stream-checkpoint 职责描述误导
