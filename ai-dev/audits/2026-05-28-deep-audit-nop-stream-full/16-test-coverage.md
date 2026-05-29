# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### [维度16-01] ComputationState has zero test coverage despite being core NFA infrastructure

- **文件**: `nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/ComputationState.java`
- **严重程度**: P1
- **现状**: NFA 状态机处理的核心值对象，0个测试文件引用。包含非平凡默认值（DeweyNumber 版本初始化、eventTime=-1、startTimestamp=-1）。
- **风险**: 版本初始化 bug 可能静默破坏模式匹配结果。
- **建议**: 添加 TestComputationState 验证工厂方法的默认值和相等性语义。
- **误报排除**: 核心基础设施工件完全无测试覆盖是结构性测试盲区。
- **复核状态**: 未复核

### [维度16-02] CepOperator late-data output path is untested

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:315-318`
- **严重程度**: P1
- **现状**: lateDataOutputTag 功能（将迟到事件路由到侧输出）无测试覆盖。所有 CEP 测试均未配置 lateDataOutputTag。
- **风险**: 迟到数据处理是事件时间正确性的关键功能。回归会导致事件被静默丢弃。
- **建议**: 添加测试配置 lateDataOutputTag 并验证迟到事件被收集到侧输出。
- **误报排除**: 事件时间迟到数据处理是流处理引擎的核心特性，无测试覆盖是结构性缺陷。
- **复核状态**: 未复核

### [维度16-03] NFA window timeout path (.within()) not tested at NFA level

- **文件**: `nop-stream-cep/.../nfa/NFA.java:279-284`
- **严重程度**: P1
- **现状**: NFA 层的窗口超时路径（advanceTime 中的 windowTimes 检查）从未被直接测试。TestCepOperatorTimeout 在算子级别测试了超时，但未覆盖 NFA 内部超时路径。
- **风险**: windowTimes 映射跟踪每状态超时，是微妙的正确性机制。Bug 会导致假匹配或漏匹配。
- **建议**: 添加 NFA 级别测试设置 windowTimes 并验证超时 ComputationState 被正确修剪。
- **误报排除**: 虽然 operator 级别有测试，但 NFA 的窗口超时是独立的状态机逻辑，应被直接测试。
- **复核状态**: 已保留（独立复核确认：0个测试文件引用 ComputationState，间接覆盖存在但直接测试缺失）

## 维度复核结论

- [维度16-01]: **保留 P1** — 独立确认 0 测试引用，核心值对象应有直接测试
- [维度16-02]: **保留 P1** — 独立确认所有 CEP 测试 lateDataOutputTag 为 null，late-data 路径从未执行
- [维度16-03]: **降级 P2** — 独立复核发现 windowTime 整体超时路径在 TestNFAExtended 中有3个 NFA 级别测试，仅 windowTimes 按状态超时路径未测试。从 P1 降级为 P2。
- [维度16-04]: **保留 P2**
- [维度16-05]: **保留 P2**

## 最终保留项

| 编号 | 严重程度 | 文件 | 摘要 |
|------|---------|------|------|
| 16-01 | P1 | ComputationState.java | 核心 NFA 值对象零直接测试 |
| 16-02 | P1 | CepOperator.java:315-318 | late-data 输出路径零测试 |
| 16-03 | P2 | NFA.java:279-284 | 仅 windowTimes 按状态超时未测试（整体超时已有覆盖） |
| 16-04 | P2 | SharedBuffer.java | 缓存淘汰压力测试缺失 |
| 16-05 | P2 | StreamRecordComparator + NFAStateNameHandler | 零测试引用 |

### [维度16-04] SharedBuffer cache eviction under pressure is not tested

- **文件**: `nop-stream-cep/.../sharedbuffer/SharedBuffer.java`
- **严重程度**: P2
- **现状**: SharedBufferCacheConfig 支持可配置缓存槽位和 LRU 淘汰，但无测试验证缓存满时的行为。
- **建议**: 添加测试填满缓存并验证淘汰后模式匹配仍正确。
- **误报排除**: 缓存淘汰是高吞吐场景的关键行为。
- **复核状态**: 未复核

### [维度16-05] StreamRecordComparator and NFAStateNameHandler are untested

- **文件**: `StreamRecordComparator.java`, `NFAStateNameHandler.java`
- **严重程度**: P2
- **现状**: 零测试引用。前者决定 CEP 算子中的事件排序，后者管理 NFA 状态名称生成/解析。
- **建议**: 添加单元测试。
- **误报排除**: 确定性排序和状态名称序列化是 CEP 正确性的基础。
- **复核状态**: 未复核
