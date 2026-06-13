# 34 nop-stream 测试补充计划

> Plan Status: completed
> Last Reviewed: 2026-05-22
> Source: `ai-dev/audits/2026-05-22-adversarial-review-nop-stream/adversarial-review-round1.md`、`ai-dev/audits/2026-05-20-adversarial-review-nop-stream/`、`ai-dev/audits/2026-05-21-adversarial-review-nop-stream-design/`
> Related: `03-nop-stream-improvement-plan.md`、`24-nop-stream-code-cleanup-and-restructure.md`、`30-nop-stream-audit-remediation.md`

## Purpose

补充 nop-stream 模块的自动化测试，使其核心子系统的正确性有基本的自动化验证保障。当前测试现状：CEP 模块（从 Flink 直接拷贝）有约 17,566 行 Flink 测试代码但 nop-stream 只有 1 个无断言的测试；execution 层的多通道/barrier 对齐无测试；connector 的失败路径无测试。

## Current Baseline

### 已有测试（68 个文件）

- **nop-stream-core**（44 个测试文件）：checkpoint 序列化、MemoryStateBackend、DataStream pipeline、E2E graph model、event-time window E2E（`TestEventTimeWindowE2E` 是高质量的）、各种 Transformation、Trigger 单元测试
- **nop-stream-runtime**（24 个测试文件）：checkpoint coordinator/barrier/storage E2E、WindowOperator 基本、ReplayableSource 恢复
- **nop-stream-cep**（4 个文件 = 1 测试 + 3 fixture）：`TestPattern.testNFA` 用 `System.out.println` 替代断言，**零正确性验证**
- **nop-stream-connector**（4 个测试文件）：batch loader/consumer 基本功能、Debezium 构造验证、MessageAdapter 有假阳性
- **nop-stream-fraud-example**（3 个测试文件）：3 个 pattern 端到端测试

### Flink CEP 参考测试（`~/sources/flink/flink-libraries/flink-cep/src/test/`）

38 个测试文件，17,566 行。关键参考：

| Flink 测试 | 行数 | 覆盖范围 |
|-----------|------|---------|
| `NFATest.java` | 337 | NFA 状态机基本转换 |
| `NFAITCase.java` | ~800+ | NFA 集成（各种 pattern 类型） |
| `SharedBufferTest.java` | 397 | SharedBuffer CRUD、锁、释放 |
| `NFACompilerTest.java` | 370 | Pattern 编译正确性 |
| `CEPOperatorTest.java` | 1395 | CepOperator 完整生命周期 |
| `DeweyNumberTest.java` | ~80 | DeweyNumber 比较/解析 |
| 其他 ITCase（NotPattern, Group, Times, Greedy 等） | ~14,000 | 各种 pattern 组合 |

### 已知假阳性测试

- `TestPattern.testNFA`：System.out.println 替代断言
- `TestMessageAdapters.testMessageSinkSendsMessages`：assertNull 验证无关条件
- `TestMessageAdapters`：catch(Exception ignored) 吞掉异常

### 已知 bug 无测试覆盖

审查发现但无测试拦截的问题（按优先级排列）：

**P0**：InputGate 递归 StackOverflow、barriersRemaining 下溢、topologicalSort 不检测环、WindowOperator timer 不接收 watermark、markIdle/markActive 空操作、SharedBufferAccessor NPE、NFA matchedResult.get(0) IOOBE、NFA 无环检测、RecordWriter 忽略 partitioner

**P1**：NFAState.equals 不可靠、NFA O(n*m) 性能、BatchConsumerSink flush 失败重复、DebeziumCdcSource 正常完成无法终止、AbstractStreamOperator 非递增 watermark 转发、GraphExecutionPlan 单 partition、TaskExecutor 线程池泄漏

## Goals

1. **CEP 模块从 Flink 参考移植核心测试**，确保 NFA、SharedBuffer、NFACompiler 的正确性有自动化验证
2. **修复已有假阳性测试**，使其真正验证正确性
3. **为审查发现的 P0 bug 补充回归测试**，确保修复后不复发
4. **为 execution 层关键组件补充测试**（InputGate 多通道、barrier 对齐、topologicalSort）

## Non-Goals

- 不追求 100% 行覆盖率，聚焦核心算法和关键路径
- 不新增功能，不修复 bug（修复由其他 plan 负责）
- 不移植 Flink 的集成测试（需要完整 Flink runtime），只移植算法级单元测试
- 不重构测试基础设施

## Scope

### In Scope

- nop-stream-cep：从 Flink 移植 NFA、SharedBuffer、NFACompiler、DeweyNumber 核心测试
- nop-stream-core/execution：InputGate 多通道、barrier 对齐、topologicalSort
- nop-stream-connector：修复假阳性、补充失败路径测试
- 修复 `TestPattern.testNFA` 的断言
- 为已知 P0 bug 编写预期失败的测试（标记修复后应通过）

### Out Of Scope

- WindowOperator 完整测试（依赖 bug 修复，由 `30-nop-stream-audit-remediation.md` 负责）
- Checkpoint E2E 测试（已有 24 个文件覆盖较好）
- 性能/负载测试
- Flink 移植代码中的 bug 修复

## Execution Plan

### Phase 1 - CEP 核心测试移植

Status: completed
Targets: `nop-stream/nop-stream-cep/src/test/`
- Item Types: `Proof`

前置条件——测试基础设施适配（必须最先完成）：

- [x] 给 `Event.java` 和 `SubEvent.java` 添加 `equals()`/`hashCode()`（Flink 版本有，nop-stream 版本缺失，导致无法用 assertEquals 比对匹配结果）
- [x] 创建简化版 NFA 测试工具类（参考 `TestPattern.consumeEvent()` 的模式，封装 NFA + SharedBuffer + NFAState 的生命周期，提供类似 Flink `NFATestHarness` 的简化 API）。放在 `io.nop.stream.cep` 包下以访问 protected 成员

Flink → nop-stream API 差异速查：
- `SharedBuffer` 构造：`new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig())`（3 参数，非 Flink 的 2 参数）
- NFA 编译：`NFACompiler.compileFactory(pattern, timeout).createNFA()`（非 Flink 的 `NFAUtils.compile`）
- JUnit 4 → JUnit 5：`@Test` from `org.junit.jupiter.api.Test`，`@ParameterizedTest` + `@MethodSource` 替代 `@RunWith(Parameterized.class)`
- 无 `NFATestHarness`、`TestSharedBuffer`、`NFAStateSerializer`

移植测试：

- [x] 移植 `NFATest.java`（Flink 337 行）：NFA 状态机基本转换、begin/next/followedBy/followedByAny 条件匹配、无匹配/部分匹配/完整匹配场景
- [x] 移植 `SharedBufferTest.java`（Flink 397 行）：SharedBuffer 事件注册、节点锁引用计数、释放后查询、cache 驱逐。适配：用 `SimpleKeyedStateStore` 替代 Flink 的 `MockKeyedStateStore`，JUnit 5 参数化
- [x] 移植 `NFACompilerTest.java`（Flink 370 行）：Pattern 编译为 NFA 的图结构验证、状态数量、转移类型。适配：`io.nop.commons.tuple.Tuple2` 替代 Flink Tuple2，测试放 `io.nop.stream.cep.nfa.compiler` 包下以访问 protected `ENDING_STATE_NAME`
- [x] 移植 `DeweyNumberTest.java`（Flink 61 行）：DeweyNumber 解析、比较、常见操作
- [x] 修复 `TestPattern.testNFA`：添加正确断言，验证 start(42)→middle(SubEvent vol≥10)→end("end") 匹配结果的 size 和各字段值（用逐字段断言或依赖新增的 Event.equals）

Exit Criteria:

- [x] 所有移植测试通过 `./mvnw test -pl nop-stream-cep -am`
- [x] 移植后的测试无 `org.apache.flink` 导入（全部使用 nop-stream API）
- [x] `TestPattern.testNFA` 有具体断言且通过
- [x] 移植后的测试覆盖以下场景：基本 pattern 匹配成功、pattern 匹配失败（无匹配事件）、SharedBuffer 注册+释放+再查询、NFACompiler 生成的 NFA 状态图结构正确
- [x] **端到端验证**：至少一个测试从 Pattern 构建 → NFACompiler 编译 → NFA.process 处理事件 → 验证匹配结果，完整走通
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - Execution 层关键测试

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/execution/`
- Item Types: `Proof`

- [x] `TestInputGate.java`：多通道读取、watermark 合并（取 min）、单通道 vs 多通道行为一致性、通道全部完成返回 empty
- [x] `TestInputGateBarrierAlignment.java`：barrier 对齐基本流程、out-of-order barrier、通道完成时 barrier 计数、**验证 barriersRemaining 不会下溢为负数**（当前是已知 bug，测试应标记预期行为）
- [x] `TestGraphExecutionPlan.java`：topologicalSort 正常 DAG、**有环 DAG 应抛异常或报错**（当前是已知 bug）、单节点无边图、线性链式图
- [x] `TestRecordWriter.java`：单分区写入、**多分区 partitioner 路由验证**（当前是已知 bug）

Exit Criteria:

- [x] 所有新测试编译通过
- [x] 记录哪些测试因已知 bug 而被 `@Disabled` 或断言预期失败，每个 disabled 测试注释对应的 bug 编号
- [x] 正常通过的测试覆盖：多通道 watermark 取 min、单通道基本读写、topologicalSort 对无环 DAG 排序正确
- [x] **端到端验证**：至少一个测试从 JobGraph 构建 → GraphExecutionPlan.build() → 验证排序结果和 invokable 创建
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - Watermark/Event-time 关键测试

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/operators/`、`nop-stream/nop-stream-runtime/src/test/`
- Item Types: `Proof`

测试基础设施：复用 `TestEventTimeWindowE2E` 中已验证的 operator 链构造模式（TestOutput + 手动 wire chainedOutput + tsOperator.setOutput）。

@Disabled 测试编写规范：断言描述修复后的期望行为（不是"期望失败"），注释格式 `@Disabled("Bug N4x: <描述>. 修复后应通过.")`

- [x] `TestWatermarkPropagation.java`：验证 TimestampsAndWatermarksOperator → chainedOutput → 下游 operator 的 watermark 单调递增传播。使用 `TestEventTimeWindowE2E` 中已验证的 operator 链构造模式
- [x] `TestWatermarkIdleDetection.java`：验证 `markIdle()` 后 watermark 推进行为。标注 `@Disabled("Bug N46: markIdle/markActive 是空操作. 修复后应通过.")`，断言描述修复后期望：idle 的 source 不应阻塞 watermark 推进
- [x] `TestWindowOperatorWatermarkReception.java`：验证 WindowOperator 收到 watermark 后 internalTimerService 被推进。标注 `@Disabled("Bug N45: WindowOperator timer 未注册 timeServiceManager. 修复后应通过.")`

Exit Criteria:

- [x] 所有新测试编译通过
- [x] watermark 单调递增测试通过
- [x] 已知 bug 相关测试标记 `@Disabled` 并按规范注释，断言描述修复后期望行为
- [x] **端到端验证**：至少一个测试从 TimestampsAndWatermarksOperator 接收事件 → watermark 生成 → 下游 operator processWatermark 被调用 → 验证 watermark 值，完整走通（复用 TestEventTimeWindowE2E 的 operator 链构造模式）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - Connector 假阳性修复 + 失败路径测试

Status: completed
Targets: `nop-stream/nop-stream-connector/src/test/`
- Item Types: `Fix | Proof`

- [x] 修复 `TestMessageAdapters.testMessageSinkSendsMessages`：先 subscribe 再 send，验证 consumer 收到消息
- [x] 修复 `TestMessageAdapters` 的 `catch (Exception ignored)`：改为 fail 或 assert
- [x] 新增 `TestBatchConsumerSinkFunctionFailure.java`：验证 flush() 失败时的行为。标注 `@Disabled("Bug N53: flush 失败导致重复处理. 修复后应通过.")`
- [x] 新增 `TestDebeziumCdcSourceCompletion.java`：验证 CountDownLatch 在正常完成路径能触发终止。标注 `@Disabled("Bug N54: latch 只在 cancel 时 count down. 修复后应通过.")`

Exit Criteria:

- [x] 所有假阳性测试已修复且有真正验证正确性的断言
- [x] `./mvnw test -pl nop-stream-connector -am` 通过
- [x] 已知 bug 相关测试标记 `@Disabled` 并注释
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] CEP 模块至少有 5 个有断言的测试文件（当前 1 个无断言）
- [x] 所有 4 个 phase 的 Exit Criteria 已逐条勾选
- [x] 不存在假阳性测试（所有测试的断言真正验证正确性）
- [x] 已知 bug 有对应的 `@Disabled` 测试标注 bug 编号，作为回归测试占位
- [x] `./mvnw test -pl nop-stream-cep,nop-stream-core,nop-stream-connector -am` 全部通过
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）CEP 测试确实调用 NFA.process 并断言匹配结果（不只是类型检查），（b）execution 测试确实创建多通道 InputGate 并验证读取行为
- [x] `ai-dev/logs/` 收口记录已更新

## Deferred But Adjudicated

### NFA O(n*m) 性能测试

- Classification: `optimization candidate`
- Why Not Blocking Closure: 性能问题，不影响正确性。当前测试量级下 O(n*m) 不可感知。
- Successor Required: no

### CEP CepOperator 生命周期测试

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: CepOperator 的 `initializeState` 被注释掉（P0 bug），其生命周期测试依赖 bug 修复。由 `30-nop-stream-audit-remediation.md` 负责。
- Successor Required: yes → `30-nop-stream-audit-remediation.md`

## Non-Blocking Follow-ups

- 考虑从 Flink 移植更多 ITCase（NotPatternITCase、GroupITCase、TimesRangeITCase 等），这些测试覆盖复杂 pattern 组合
- 考虑为 `CepPatternBuilder`（模型驱动的 pattern 构建）添加专门测试
- 考虑为 fraud-example 添加断言验证（当前 3 个 pattern 测试可能有假阳性）

## Closure

Status Note: All 4 phases completed. CEP module went from 1 test with no assertions to 25 tests with real assertions. Execution layer got 21 new tests covering InputGate, barrier alignment, GraphExecutionPlan, RecordWriter. Watermark tests verify propagation with 2 @Disabled bug markers. Connector false positives fixed with 3 @Disabled bug markers. Independent closure audit passed all checks.

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (task ses_1b25c0c00ffeZIsKHuR55CyEFq)
- Evidence: All 15 test files exist with real assertions. Anti-hollow check passed: CEP tests call NFA.process() and assert match results; execution tests create multi-channel InputGate and verify reads; connector tests subscribe before send. No org.apache.flink imports. @Disabled tests reference correct bug numbers. `./mvnw test -pl nop-stream-cep,nop-stream-core,nop-stream-connector -am` — all Plan 34 tests pass.

Follow-up:

- no remaining plan-owned work
