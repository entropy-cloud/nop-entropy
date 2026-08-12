# nop-stream red list（Cycle 2 / I2 权威版，移交 I3 裁决）

> Status: active（Cycle 2 / I2 权威版，2026-08-12 实测；Cycle 1 历史版见附录 §5，I4 已全部修复，存档不动）
> Created: 2026-08-12 (Cycle 2 / I2)；Cycle 1 版见附录（2026-08-12，I4 修复后存档）
> Sources: Cycle 2 / I1 plan `2026-08-12-1217-8-nop-stream-invariants-cycle2-I1-output-contract-gates.md`（门禁落档）；
> Cycle 2 / I2 plan `2026-08-12-1217-09-nop-stream-invariants-cycle2-I2-invariant-driven-audit.md`（本版权威化）；
> I1 输入 `ai-dev/audits/nop-stream-invariants/cycle2-I1-input.md`（102 tests / 2 pin / 4 实现类 / 6 发射点，零悬挂基线）
> Semantics: **I2 权威版** — 每条含 位置 / 关联不变式 / 关联 finding 或注册表条目 / 族标注 / 验证或探查结论 / 裁决输入。
> I3 依此逐条裁决（严重度 + 派发），I4 执行修复。I2 不修复任何项、不做严重度裁决。

## 0. Cycle 2 / I2 门禁全量运行结果（Phase 1，2026-08-12 实测）

- **mjs `all` 退出码 0**：inventory / sync / scan-iterations / scan-output-contract / self-test 五命令全绿
  （`node ai-dev/tools/check-nop-stream-invariants.mjs all` → exit 0；逐命令输出：
  `inventory: OK` / `sync: OK` / `scan-iterations: OK` / `scan-output-contract: OK` / `self-test: OK`）。
- **处置二分结论**：全部落入「无变化」分支——**无新增 pre-existing residual**（scan-output-contract 无新
  unpinned 违规：V1 类级枚举 / V4 新发射点 / V5 失效点均无命中；scan-iterations 无新违规）、**无行为漂移**
  （2 条过渡 pin 的 live 行为与 pin 描述一致，见 §1 C2-RL-1/2 复核）、**无 stale pin**（2 条 pin 的 key 与
  scanner 实际违规串匹配，pin 吸收生效——否则 scan-output-contract 将报 unpinned/stale 而 exit ≠ 0）。
- **10 类 JUnit 门禁 + 3 模块表完备性**（`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`，
  surefire 实测 2026-08-12）：TestCheckpointIDCounterInvariant 8 / TestSynchronizedCollectionInvariant 12 /
  TestOutputContractInvariant 10 / TestInvariantTableCompleteness 10 / TestWindowRoundTripInvariant 9 /
  TestClusterRegistryConsistencyInvariant 10 / TestWindowOperatorMergingCleanupInvariant 4 /
  TestRuntimeInvariantTableCompleteness 11 / TestCepReleaseSymmetryInvariant 21 / TestCepInvariantTableCompleteness 7
  —— **合计 102 tests，0 failures / 0 errors / 0 skipped**，BUILD SUCCESS。与 `cycle2-I1-input.md` 落档一致。
- **注册表自洽性复核表**（live 行号 vs 注册表，全部零漂移 ±0）：

| 类别 | 注册表条目 | live 复核（2026-08-12） | 结论 |
|---|---|---|---|
| 实现类 1/4 | `ChainingOutput.java` :111（forward） | :111 `collect(OutputTag, StreamRecord)` | 一致 |
| 实现类 2/4 | `TimestampedCollector.java` :97（forward） | :97 `collect(OutputTag, StreamRecord)`（:98 转发） | 一致 |
| 实现类 3/4 | `StreamTaskInvokable$RecordWriterOutput` :645（pinned-known-violation） | :645 `collect(OutputTag)` 空体（:646 注释 only） | 一致 |
| 实现类 4/4 | `StreamTaskInvokable$BroadcastingRecordWriterOutput` :705（pinned-known-violation） | :705 `collect(OutputTag)` 空体（无注释） | 一致 |
| 发射点 1/6 | `ProcessOperator.java` :111 | :111 `output.collect(outputTag, ...)` | 一致 |
| 发射点 2/6 | `ProcessOperator.java` :134 | :134 `output.collect(outputTag, ...)` | 一致 |
| 发射点 3/6 | `WindowOperator.java` :1030 | :1030 `output.collect(lateDataOutputTag, ...)` | 一致 |
| 发射点 4/6 | `WindowOperator.java` :1860 | :1860 `output.collect(outputTag, ...)` | 一致 |
| 发射点 5/6 | `CepOperator.java` :483 | :483 `output.collect(lateDataOutputTag, ...)` | 一致 |
| 发射点 6/6 | `CepOperator.java` :777 | :777 `output.collect(outputTag, ...)` | 一致 |
| V1 类级枚举 | 4 个 main `implements Output` 类 | live grep `implements Output`（3 模块 src/main）= 恰好 4 类，全部在注册表 | 一致 |
| 豁免清单 | 4 条 test-only 豁免 | 4 个类/文件名全部位于 `src/test`（`TestOutput` / `TestTimestampedCollectorJavadoc$MockOutput` / `TestMailboxWiring$NopOutput` / `TestWindowOperatorWatermarkReception$CapturingOutput`），src/main 无同名混入 | 一致 |

- **注册表 disposition 措辞与 E2E 实际覆盖不一致（已记录，供 Phase 3-d / I3 评估，不静默放过）**：
  注册表 6 发射点 disposition 均写 "E2E covered by TestSideOutputChainingE2E"，实测该 E2E
  （`nop-stream-runtime/.../integration/TestSideOutputChainingE2E.java`，3 用例）**仅覆盖 WindowOperator
  late-data 路径**（`WindowOperator.java:1030` sideOutput(lateDataOutputTag) → ChainingOutput → 消费者；
  无消费者 fail-fast；StreamTaskInvokable 接线）——即 6 条中 5 条过 claim（仅 :1030 条属实）。
  `ProcessOperator:111/:134`（ProcessFunction/OnTimer ctx.output）、`WindowOperator:1860`（ProcessWindowFunction ctx.output）、
  `CepOperator:483`（late-data）、`CepOperator:777`（PatternProcessFunction）五个发射点**无 E2E 覆盖**——措辞过 claim。列入 §3 探查
  盲区 d 评估 + I3 参考（是否修订注册表措辞 / 扩展 E2E，属 I4 或后续类别清扫评估，非本 plan 处置）。
- **处置结论**：无行为漂移 → 无 blocked 升级；无 stale pin → 无 pin 移除；无新增 unpinned 违规 →
  无新增非 pin red list 项。red list 主体 = 2 条过渡 pin 已知实例（§1）+ 探查发现（§3）。

---

## 1. Cycle 2 red list 主体（I2 权威版条目）

> 仅含 Cycle 2 权威条目；Cycle 1 的 RL-1..7（已修复）见附录 §5。条目编号 C2-RL-n，与 Cycle 1 编号隔离。

### C2-RL-1. `StreamTaskInvokable$RecordWriterOutput.collect(OutputTag)` 跨 task 空体 no-op（过渡 pin `RWO-cross-task-noop`）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:645-647`
  （`collect(OutputTag, record)` 空体，:646 仅注释「Side outputs not supported in cross-task exchange」）
- **关联不变式**：#6（输出契约族——任何 `Output.collect(OutputTag, X)` 必须转发到注册消费者，不得静默丢弃；无消费者 → fail-fast）
- **关联 finding / 注册表条目**：`output-contract-registry.json` implementationClasses[2]（pinned-known-violation）；
  过渡 pin `mjs-pins.json` `RWO-cross-task-noop`；关联 `HG-01` 人工确认门；I6 双层裁决（adjudication-table.md §I6）
- **族标注**：已知族（不变式 #6 输出契约族；跨 task 实例兄弟，RL-7 修复 `b20fcd0e1` 的同族残余）
- **门禁/pin 来源**：mjs `scan-output-contract` V3 违规串（pin key 精确匹配）；`TestOutputContractInvariant`
  `testCollectOutputTagBehaviorMatchesRegistry` pinned-known-violation 分支反射断言（`partition.size()==0`）
- **验证结论（Phase 1 + Phase 2 live 复核）**：live :645-647 空体确认，行为与 pin 描述 / 注册表分类一致；
  pin key 与 scanner 违规串精确匹配（`compareViolationsToPins` 协议）；行号零漂移
- **裁决输入**：已确认契约缺口（P1，I6 预裁决）；修复方向（Cycle 2 / I4）= 空体 → 抛
  `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常（interim fail-fast，类内部行为修复，`Output` 接口零变更）；
  pin 移除条件 = I4 修复落地 + 注册表分类更新；线协议结构性支持 = `HG-01` 人工确认门（不阻塞 pin 移除）
- **修复状态（Cycle 2 / I4，plan `2026-08-12-1217-11`）**：**已修复**（2026-08-12）——`collect(OutputTag)`
  空体 → 抛 `StreamRuntimeException(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER)`（`ARG_OUTPUT_TAG` / `ARG_DETAIL`，
  镜像 `ChainingOutput.java:119`）；注册表分类迁移 `pinned-known-violation` → `fail-fast`；过渡 pin
  `RWO-cross-task-noop` 移除（removalTrigger 满足，留痕在案，非静默移除）；`TestOutputContractInvariant`
  断言翻转（反射断言 → fail-fast 分支）全绿 + 跨 task E2E 第 4 用例（`testCrossTaskTailOutputFailsFastOnSideOutput`）
  4/4 绿 + core/runtime 全量回归绿（core 1428 / runtime 805，0 failures）。`HG-01` 线协议支持 = 人工确认待办延续

### C2-RL-2. `StreamTaskInvokable$BroadcastingRecordWriterOutput.collect(OutputTag)` 跨 task 空体 no-op（过渡 pin `BRWO-cross-task-noop`）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java:705-706`
  （`collect(OutputTag, record)` 空体，无注释）
- **关联不变式**：#6（同上）
- **关联 finding / 注册表条目**：`output-contract-registry.json` implementationClasses[3]（pinned-known-violation）；
  过渡 pin `mjs-pins.json` `BRWO-cross-task-noop`；关联 `HG-01`；I6 双层裁决
- **族标注**：已知族（不变式 #6 输出契约族，跨 task 实例兄弟）
- **门禁/pin 来源**：mjs `scan-output-contract` V3 违规串（pin key 精确匹配）；`TestOutputContractInvariant`
  pinned-known-violation 分支反射断言（`sideReceived.size()==0`）
- **验证结论（Phase 1 + Phase 2 live 复核）**：live :705-706 空体确认，行为与 pin 描述 / 注册表分类一致；
  pin key 精确匹配；行号零漂移
- **裁决输入**：同 C2-RL-1（interim fail-fast 预授权分派 Cycle 2 / I4；`HG-01` 线协议人工确认门）
- **修复状态（Cycle 2 / I4，plan `2026-08-12-1217-11`）**：**已修复**（2026-08-12）——同 C2-RL-1
  （`collect(OutputTag)` 空体 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）；注册表分类迁移 → `fail-fast`；
  过渡 pin `BRWO-cross-task-noop` 移除（留痕在案）；断言翻转 + E2E 第 4 用例 BRWO 路径（2 fanOut writer）
  覆盖在案。`HG-01` 线协议支持 = 人工确认待办延续

### C2-RL-3. 注册表 disposition 措辞与 E2E 覆盖实际不符（过 claim，评估项）

- **位置**：`ai-dev/audits/nop-stream-invariants/output-contract-registry.json` emissionPoints 表
  （5 条 disposition 写 "E2E covered by TestSideOutputChainingE2E"，实际该 E2E 仅覆盖 WindowOperator late-data 路径）
- **关联不变式**：#6（门禁审计证据准确性）
- **关联 finding**：I2 Phase 1 注册表自洽性复核发现（本版 red list 首发；I1 落档时未核对该措辞）
- **族标注**：已知族（输出契约族审计证据质量问题，非代码缺陷）
- **门禁/pin 来源**：无（注册表文档措辞；mjs 不消费 disposition 字段，无门禁影响）
- **验证结论**：`TestSideOutputChainingE2E`（3 用例）源码确认仅覆盖 `WindowOperator.sideOutput(lateDataOutputTag)`
  → ChainingOutput 链路；ProcessOperator :111/:134、WindowOperator :1860、CepOperator :483/:777 共 5 个发射点
  无 E2E 覆盖（C2-PR-4 实测，探查报告盲区 d）
- **裁决输入**：I3 参考——修订注册表措辞（准确表述覆盖范围）与/或扩展 E2E 覆盖（6 发射点全路径），
  属优化项（非 defect），不阻塞移交；Phase 3-d 评估结论见探查报告盲区 d

---

## 2. 过渡 pin 状态裁定（Phase 2，2026-08-12 回写）

> 2 条过渡 pin（`RWO-cross-task-noop` / `BRWO-cross-task-noop`）逐项 live 复核 + 裁定。结论：**均维持**（跨 task 实例未修复，移除条件 = Cycle 2 / I4 interim fail-fast 落地 + 注册表分类更新，禁静默移除）。

### 裁定表

| # | pin id | live 行为（2026-08-12 实测） | 行号 | pin key 匹配 | 三方一致 | 裁定 |
|---|---|---|---|---|---|---|
| 1 | `RWO-cross-task-noop` | `StreamTaskInvokable.java:645-647` `collect(OutputTag, record)` 空体，:646 仅注释「Side outputs not supported in cross-task exchange」 | :645（注册表 645，零漂移） | 精确匹配（scanner V3 违规串 `[scan-output-contract] V3 behavior drift: io.nop.stream.core.execution.StreamTaskInvokable$RecordWriterOutput collect(OutputTag) body-classification=no-op registry-classification=pinned-known-violation (...StreamTaskInvokable.java:645)`；mjs scan 无 unpinned 无 stale = 吸收生效证明） | 一致（注册表 pinned-known-violation ↔ pin known-violation 过渡语义 ↔ JUnit 反射断言 `partition.size()==0`） | **维持**（依据：跨 task 实例未修复；interim fail-fast 预授权 Cycle 2 / I4；`HG-01` 线协议人工确认门未过） |
| 2 | `BRWO-cross-task-noop` | `StreamTaskInvokable.java:705-706` `collect(OutputTag, record)` 空体（无注释） | :705（注册表 705，零漂移） | 精确匹配（同上格式，:705；mjs scan 吸收生效证明） | 一致（注册表 pinned-known-violation ↔ pin ↔ JUnit 反射断言 `sideReceived.size()==0`） | **维持**（依据同上） |

### 接线路径复核（裁定决策输入，2026-08-12 实测）

跨 task 生产接线链 live 行号全部核对无漂移，6 发射点在跨 task 部署下可达性判定依据仍成立：

- **`GraphExecutionPlan.java:453-463`**：fanOutWriters 分支（:454 `if (fanOutWriters != null && !fanOutWriters.isEmpty())`）→ :456/:458 `new StreamTaskInvokable(chain, fanOutWriters[, inputGate])`；单 writer 分支 :461 `new StreamTaskInvokable(chain, recordWriter, inputGate)`。
- **`StreamTaskInvokable.java:200-249` `wireOperators(List<RecordWriter> fanOutWriters)`**：链内非尾算子 setOutput `ChainingOutput`（:220）；**尾算子 :233-248**——单 fanOut writer → :239 `op.setOutput(new RecordWriterOutput(fanOutWriters.get(0)))`；多 fanOut writer → :242-245 组装 `new BroadcastingRecordWriterOutput(outputs)`。
- **`StreamTaskInvokable.java:348-354` `wireTailToRecordWriter`**（单 recordWriter 接线）：:352 `op.setOutput(new RecordWriterOutput(outputWriter))`。
- **可达性结论**：跨 task 多 vertex 部署下，tail 算子（WindowOperator / CepOperator / ProcessOperator）的 Output = RWO（单下游）/ BRWO（多下游），其 6 个发射点的 side-output 发射全部落入 `collect(OutputTag)` 空体 = 静默丢弃——C2-RL-1/2 的判定依据（供 I3 确认 interim fail-fast 派发）成立，无变化。
- **时序语义补充观察（供 Phase 3-c 深入）**：`registerSideOutputConsumer`（:305+ 注释「may happen before or after wireOperators」）写共享 `sideOutputConsumers` map；tail 算子的 RWO/BRWO **不消费**该 map（RWO/BRWO 无 side-output 注册语义）——跨 task side-output 消费者注册语义缺失，与 `HG-01` 线协议缺口一致，非新发现。

---

## 3. 聚焦对抗探查发现（Phase 3，2026-08-12 回写）

> 完整探查报告：`ai-dev/audits/nop-stream-invariants/cycle2-I2-probing-report.md`（Phase 3 新建产物）。
> 本节收录探查结论：**无新增 red list 条目**（探查未发现 in-scope confirmed live defect 超出已 pin 项）；
> 5 条发现全部为已知族观察 / 门禁表达扩展候选，带族标注与证据，随本权威版移交 I3 / I6。

- **C2-PR-1（透传链中间形态）**：全部 6 发射点 side-output 直连算子 `output` 字段（ctx.output → output.collect），
  不经 TimestampedCollector（`Collector<T>` 不继承 `Output`，用户函数无法经 collector 发 side-output）；
  JUnit `testTimestampedCollectorWrappingRecordWriterOutputEqualsCrossTaskDrop` 为合成场景但分类语义有效。
  已知族（#6）表达完备性观察，非缺陷。—— **检查后无问题**（盲区 a）
- **C2-PR-2（扫描器静默跳过形态）**：`scan-output-contract` 显式 fail 五路径全部实现 + self-test 覆盖；
  静默跳过形态（匿名类 / record implements Output / raw OutputTag 声明）当前 0 实例，门禁表达扩展候选。
  已知族（#6）门禁扩展候选，非 live defect。—— **盲区 b 结论：显式 fail 面完备 + 零实例 + 候选登记**
- **C2-PR-3（重复注册覆盖）**：`sideOutputConsumers.put` last-wins，同一 OutputTag 重复注册静默覆盖前一消费者，
  无 fail-fast / 无广播语义。不违反 #6（仍转发到注册消费者），生产无重复注册调用面。观察项（I3 可裁 P3）。
  —— **盲区 c 其余时序（wiring 前后注册 / fanOutWriters 多 vertex / 单线程并发）检查后无问题**
- **C2-PR-4（E2E 缺口）**：6 发射点仅 `WindowOperator.java:1030`（late-data）有 `TestSideOutputChainingE2E`
  覆盖；ProcessOperator:111/:134、WindowOperator:1860、CepOperator:483/:777 共 5 个发射点零 E2E 覆盖
  （单元层亦无 OutputTag 发射断言）。缺口确认，优化级候选，**不升格 red list**（与 C2-RL-3 联动，I3 参考）。
- **C2-PR-5（控制面方法族候选）**：RWO :640-642/:650-652（注释文档化）与 BRWO :701-702/:709-710（空体无注释）
  的 `emitWatermarkStatus` / `emitLatencyMarker` 跨 task 空体 = 同根因（跨 task Output 方法无线协议支持）、
  同处置门（`HG-01`）；影响 = 控制面遥测 / 空闲检测降级，非用户数据丢失。**不变式 #6 陈述扩展候选**
  （「跨 task Output 控制面方法不得静默丢弃或显式文档化」），触发证据 `StreamTaskInvokable.java:640-642/:650-652/:701-702/:709-710`，
  供 I6 按 Loop Rule 评估派生 Cycle 3 / I1——**不升格独立新族**。
- **非族候选评估**：R16-AR-14（OperatorChain.processElement 广播）**不升格**（已随类重构消失，复核无变化）；
  R16-AR-19/20（BatchConsumerSinkFunction buffer）**不升格**（文档化 unsynchronized by design + fail-fast flush，
  复核无变化）；emitWatermarkStatus/emitLatencyMarker → C2-PR-5 扩展候选。
- **零悬挂核对（Phase 3 部分）**：探查未发现超出 C2-RL-1/2（已 pin）的 in-scope confirmed live defect；
  全部发现已带族标注随本报告 / 本 red list 存档，无静默遗漏。

---

## 4. 移交声明（I3 裁决输入就绪，2026-08-12 回写）

- **零悬挂核对（vs `cycle2-I1-input.md` 登记项，全部有处置）**：

| I1 登记项 | 数量 | I2 处置 |
|---|---|---|
| 过渡 pin（`RWO-cross-task-noop` / `BRWO-cross-task-noop`） | 2 | §1 C2-RL-1 / C2-RL-2（live 复核 + 裁定维持，§2） |
| main `Output` 实现类（ChainingOutput / TimestampedCollector / RWO / BRWO） | 4 | §0 复核表零漂移 + §1 分类在册（C2-RL-1/2 覆盖跨 task 两实例） |
| 发射点（ProcessOperator:111/:134、WindowOperator:1030/:1860、CepOperator:483/:777） | 6 | §0 复核表零漂移；:1030 有 E2E 覆盖、其余 5 个覆盖缺口 = C2-RL-3 + C2-PR-4 |
| JUnit 门禁（10 类 / 102 tests） | — | §0 实测 102/102 绿 |
| mjs 门禁（`all` 五命令） | — | §0 实测 exit 0 |
| E2E 基线（TestSideOutputChainingE2E 3/3） | — | Phase 4 Anti-Hollow 实跑 3/3 绿（2026-08-12 21:20） |

- **Cycle 2 权威 red list 条目**（I3 逐条裁决输入）：C2-RL-1（RWO 跨 task no-op，已确认契约缺口，interim
  fail-fast 预授权 Cycle 2 / I4）、C2-RL-2（BRWO 跨 task no-op，同上）、C2-RL-3（注册表 disposition 措辞
  过 claim——5 发射点无 E2E 覆盖却写 "E2E covered"，评估项）；探查发现 C2-PR-1..5（已知族观察 / 门禁表达
  扩展候选 / 不变式 #6 陈述扩展候选，详 §3 + `cycle2-I2-probing-report.md`），无新增 red list 条目。
- **每条含裁决信息**：位置（`文件:行`）/ 关联不变式 / 族标注 / 验证或探查结论 / 修复方向参考（C2-RL-1/2 =
  I4 预授权信封内；C2-RL-3 = 措辞修订或 E2E 扩展，P3 级评估）。
- **门禁保持全绿（pin-and-record 语义下）**：mjs `all` exit 0 + JUnit 10 类 102 tests 0 failures + E2E 3/3；
  本 plan 未改动任何被测类代码（Non-Goals 遵守）；2 条过渡 pin 维持（removalTrigger = Cycle 2 / I4 修复落地
  + 注册表分类更新，禁静默移除）。
- **族标注汇总**：全部发现属已知族（不变式 #6 输出契约族）或其后继扩展候选（C2-PR-2 门禁形态覆盖 /
  C2-PR-5 控制面陈述扩展）；**无新独立族**——I6 按 Loop Rule 评估 2 个扩展候选即可。
- **注册表行号同步**：本次复核 4 实现类 / 6 发射点行号零漂移，注册表无需更新（0 处移动）。
- **roadmap 流转**：Cycle 2 / I2 行 `planned` → `done`（closure audit 通过后由本 plan Closure 流程记录，
  执行结果回写 roadmap）。

---

## 5. 附录：Cycle 1 历史版本（I4 已全部修复，存档）

> Cycle 1 / I2 权威版（2026-08-12）——7 条 RL（RL-1..7）已于 Cycle 1 / I4 全部修复
> （plan `2026-08-12-1217-5`，commit `fcc71fc05` / `58255014b` / `b20fcd0e1`），
> 本节为历史裁定与修复记录，供追溯；Cycle 2 裁决输入以 §1-§4 为准。

### Cycle 1 §0. 门禁全量运行结果（I2 Phase 1，2026-08-12 实测）

- **mjs `all` 退出码 0**：inventory / sync / scan-iterations / self-test 四命令全绿。
- **scan-iterations 输出**：live 违规 = 1（`TwoPhaseCommitSinkFunction.java:83` saveState 无锁 copy，
  copy-constructor 口径），被 `mjs-pins.json` 唯一 pin 吸收 → unpinned=0、stale=0。
  **无新增 pre-existing residual、无行为漂移、无 stale pin**。
- **五族 JUnit 门禁 + 3 模块表完备性**（surefire 实测）：TestWindowRoundTripInvariant 9 /
  TestSynchronizedCollectionInvariant 10 / TestCheckpointIDCounterInvariant 8 / TestCepReleaseSymmetryInvariant 21 /
  TestClusterRegistryConsistencyInvariant 8 / TestInvariantTableCompleteness 10 / TestRuntimeInvariantTableCompleteness 11 /
  TestCepInvariantTableCompleteness 7 —— 合计 84 tests，0 failures / 0 errors / 0 skipped，BUILD SUCCESS。
- **4 条已知 residual 复核表**（I2 Phase 1，live 行号 vs I1 pin 记录）：

| # | 位置（I1 pin） | live 行号（本次复核） | 漂移 | pin 状态 | 行为是否仍匹配 I1 断言 |
|---|---|---|---|---|---|
| 1 | `JdbcClusterRegistry.java:112-115`（INSERT lease_expire_at=0L） | :112-115（:115 写 0L） | 无（±0） | mjs 无 pin（JUnit pin） | 是 |
| 2 | `JdbcClusterRegistry.java:172-174`（getActiveNodes 按 > now 过滤） | :172-174（:174 `lease_expire_at > ?`） | 无（±0） | 同上 | 是 |
| 3 | `InMemoryClusterRegistry.java:68-81`（renewLease 忽略 leaseTimeoutMs） | :68-81（:74 只存时间戳） | 无（±0） | 同上 | 是 |
| 4 | `TwoPhaseCommitSinkFunction.java:83`（saveState 无锁 copy） | :83（`new TreeMap<>(pendingCommits)`） | 无（±0） | mjs pin 命中（`mjs-pins.json[0]`） | 是 |
| 5 | `TwoPhaseCommitSinkFunction.java:76-78`（setPendingCommits 接受任意 Map） | :76-78 | 无（±0） | mjs 无 pin | 是 |

**处置结论**：无行为漂移 → 无 blocked 升级；无 stale pin → 无 pin 移除；无新增 unpinned 违规 → 无新增 red list 项。

### Cycle 1 §1. red list 主体（I0 catalog §6 候选，I2 复核确认在册）

#### RL-1. JdbcClusterRegistry.registerNode 写 lease_expire_at=0L（catalog #1）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:112-115`
- **关联不变式**：#5（ClusterRegistry 多实现语义一致性）
- **关联 finding**：R16-AR-9
- **族标注**：F5（ClusterRegistry 族，已知族兄弟实例）
- **门禁/pin 来源**：`TestClusterRegistryConsistencyInvariant.testRegisterNodeVisibilityIsPinnedPerImpl` JDBC 分支
- **验证结论**：live 确认 :112-115 INSERT 写 0L，renewLease 后可见（per-renewal 生效路径绿）；差异属实。
- **裁决输入**：修复方向 = registerNode INSERT 写 `now + leaseTtlMs` 而非 0L；行为影响 = 新注册节点在首次 renewLease 前对调度器不可见。**I4 已修复（commit `fcc71fc05`，P1）**。

#### RL-2. JdbcClusterRegistry.getActiveNodes 按 > now 过滤（catalog #2）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/JdbcClusterRegistry.java:172-174`
- **关联不变式**：#5；**关联 finding**：R16-AR-9（与 RL-1 同 finding）；**族标注**：F5
- **验证结论**：与 RL-1 构成因果链——INSERT 写 0L ⇒ 过滤条件立即排除新节点。
- **裁决输入**：与 RL-1 联合裁决/修复。**I4 已修复（commit `fcc71fc05`，P1）**。

#### RL-3. InMemoryClusterRegistry.renewLease 忽略 leaseTimeoutMs（catalog #3）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/cluster/InMemoryClusterRegistry.java:68-81`
- **关联不变式**：#5；**关联 finding**：R16-AR-18；**族标注**：F5
- **门禁/pin 来源**：`TestClusterRegistryConsistencyInvariant.testRenewLeasePerRenewalTimeoutIsPinnedPerImpl` InMemory 分支
- **裁决输入**：修复方向 = renewLease 记录 `now + leaseTimeoutMs` 并按参数计算活性。**I4 已修复（commit `fcc71fc05`，P2 触发闭合）**。

#### RL-4. TwoPhaseCommitSinkFunction.saveState 无锁 copy（catalog #4）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:83`
- **关联不变式**：#2；**关联 finding**：R16-AR-1；**族标注**：F2
- **门禁/pin 来源**：mjs `mjs-pins.json` pinnedViolations[0] + `TestSynchronizedCollectionInvariant.testSaveStatePinsSnapshotContentComplete`
- **裁决输入**：修复方向 = saveState 内 `synchronized (pendingCommits)` 包裹 copy。**I4 已修复（commit `fcc71fc05`，P0）**。

#### RL-5. TwoPhaseCommitSinkFunction.setPendingCommits 接受任意 Map（catalog #5）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/common/functions/sink/TwoPhaseCommitSinkFunction.java:76-78`
- **关联不变式**：#2；**关联 finding**：R16-AR-11；**族标注**：F2
- **裁决输入**：修复方向 = setter 内部包装 `Collections.synchronizedMap` 或文档化调用方契约。**I4 已修复（commit `fcc71fc05`，P1）**。

#### RL-6. WindowOperator onEventTime cleanup 未 retire 合并窗口（R15-AR-8 升格，I2 新增）

- **位置**：`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperator.java:773-782`
- **关联不变式**：#1 语义扩展——Window 生命周期收敛；**关联 finding**：R15-AR-8；**族标注**：F1
- **验证结论**：动态验证确认——cleanup 后 MergingWindowSet 映射不收敛（无界增长）+ stale 范围复活。
- **裁决输入**：修复方向 = onEventTime cleanup 分支补 `mergingWindows.retireWindow(stateWindow)`。**I4 已修复（commit `58255014b`，P1）**。

#### RL-7. ChainingOutput 静默丢弃 side-output（R15-AR-4 确认仍 live，I2 探查新增）

- **位置**：`nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/ChainingOutput.java:84-86`（原始丢弃点，已修复）
- **关联不变式**：新族候选——输出契约族（后续定稿为不变式 #6）；**对应 finding**：R15-AR-4；**族标注**：新族
- **验证/探查结论**：live 确认丢弃点；默认链式部署下 late-data 侧输出静默丢失。
- **裁决输入**：修复方向 = 转发至下游 side-output 通道或 fail-fast。**I4 已修复（commit `b20fcd0e1`，P1；修复基线 = Cycle 2 / PD-15 不变式 #6 的门禁依据）**。

### Cycle 1 §2. watch-only residual 裁定（I2 Phase 2 动态验证结果）

- **WO-1. LocalFileCheckpointStorage 按文件名 ID 排序（AR-15）→ verified**（`TestLocalFileCheckpointStorage` 13/13 绿；max-ID 排序正确 + 单调恢复级联解除）。
- **WO-2. WindowOperator onEventTime cleanup 未见 retireWindow（R15-AR-8）→ 升格 red list（RL-6）**。
- **WO-3. InputGate per-id inFlightAlignments（R15-AR-9）→ verified**（`TestInputGateMultiEpochBarrier` 5/5 绿；aligned 重叠 + AT_LEAST_ONCE 交错均正确）。

### Cycle 1 §3. 聚焦对抗探查发现（I2 Phase 3）

> 完整探查报告：`ai-dev/audits/nop-stream-invariants/I2-probing-report.md`（Cycle 1 产物）。

- **RL-7**（ChainingOutput 静默丢弃 side-output）→ 已修复（见上）。

### Cycle 1 §4. 移交声明（I3 裁决输入就绪）

- 零悬挂核对：I0 catalog §6 全部 8 项有处置（RL-1..5 在册 + RL-6/RL-7 新增 + WO-1..3 逐项裁定）。**零悬挂达成**。
- I2 未改动任何被测类代码；全部结论基于 live 代码 + 测试载体。

### Cycle 1 §5. 修复状态（I4 执行结果，2026-08-12）

| RL | 严重度 | 修复 commit | 翻转/新增测试 | 门禁复跑 |
|---|---|---|---|---|
| RL-1+RL-2 | P1 | `fcc71fc05` | `testRegisterNodeVisibilityIsPinnedPerImpl` 翻转 + 新增 `testJdbcReregisterAfterLeaseExpiryIsImmediatelyVisible` | 4 失败红 → 10/10 绿 |
| RL-3 | P2 触发闭合 | `fcc71fc05` | `testRenewLeasePerRenewalTimeoutIsPinnedPerImpl` 翻转 + 新增 `testInMemoryRenewLeaseHonorsPerRenewalTimeout` | 同上 |
| RL-4 | P0 | `fcc71fc05` | mjs pin 移除后 scan 变红 → 修复后违规清零；新增 `testSaveStateConcurrentWithCommitAbortIsSafe` | `mjs all` exit 0；core 1418 全绿 |
| RL-5 | P1 | `fcc71fc05` | 新增 `testSetPendingCommitsWrapsUnsafeMap` | 同上 |
| RL-6 | P1 | `58255014b` | `TestWindowOperatorMergingCleanupInvariant` 2 pin 翻转 + 长跑收敛 + processing-time 用例 | 4 失败红 → 4/4 绿 |
| RL-7 | P1 | `b20fcd0e1` | 新增 `TestSideOutputChainingE2E`（3 用例）——先红 3/3，修复后 3/3 绿 | runtime 804 / core 1418 / cep 320 全绿 |

- **修复基线门禁复跑**：五族 JUnit 门禁 92 tests 0 failures；`./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS；
  `mjs all` exit 0；`mjs-pins.json` pinnedViolations 已清空（Cycle 1 末尾；Cycle 2 / I1 新增 2 条过渡 pin 为输出契约族，见 §1）。
- **类别清扫结论汇总（四族）**：F5 两实现全部 lease 路径一致；F2 pendingCommits 全使用点锁路径核对；
  F1 MergingWindowSet 生产消费方仅 WindowOperator；输出契约族 ChainingOutput 为唯一 in-task 丢弃点（已修）+
  跨 task no-op（RWO/BRWO）记录为同族已知实例 → 移交 I6 人工确认候选（`HG-01`）。
- **RL-3 backlog 状态**：已由 I4 触发闭合（roadmap Follow-up Backlog 条目状态已更新）。
