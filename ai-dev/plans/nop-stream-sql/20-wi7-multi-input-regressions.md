# 20 WI7 多输入回归三件套

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI7 行、§3.2 约束 4、§八 4/6）、`ai-dev/design/nop-stream/multi-input-model.md`
> Related: `ai-dev/plans/nop-stream-sql/13-wi6-union-multi-input.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立子 agent 对抗性审查一轮修订（live API 逐点核验）——B1 self-join 改独立类 TestMultiInputSelfJoinEdgeDedup（roadmap 明文四类）；M1 watermark 断言载体钉死（sink 无 watermark 回调——用 transform() 注入观测算子覆写 processWatermark 记录序列；source 经 SourceContext.emitWatermark；EOS 陷阱=两源 latch 驻留防 MAX_WATERMARK 污染）；M2 restore 双证据绑定（manifest offset ↔ initializeState 收到的 offset，setJobId/setPipelineId 两跑一致）；M3 barrier 对齐判别（观测算子 processElement+processBarrier 序列：aligned 阻塞下 post-barrier 元素严格出现在 barrier 之后）；M4 §八 4 断言写死（collect 边界滞后 + snapshotState 同线程，端到端管线内）；m2 owner-doc 显式裁定；m3 纯 DataStream API 免容器；m5 scan-hollow 不适用。Status draft→active。
> 收口裁定注记（第四轮 audit 后）：M3 的判别义务路由 FU-9（union 顶点 E2E 对齐阻塞缺位为 live defect，须 Fix 单独立项）；M4 的线程同一性由 core TestSourcePullBarrierInjection 既有断言承载——两项裁定的完整推理在 Goals #1/§八 4 goal、Deferred But Adjudicated 与日志 WI7 条目四处承载。

## Purpose

补齐多输入通路的回归证据三件套：barrier 对齐、水位 min 合并、exactly-once checkpoint——全部走两源进单算子的端到端形态；另有 self-join 边去重回归与 §八 4/6 不变量断言。

## Current Baseline

- WI6 已交付 union 通路与约束 1/2 修复，已有测试覆盖 wire/unit 级（TestUnionTransformation/TestJobGraphParallelEdges/TestMultiEdgeGateConfigConsistency）与 DSL E2E 两源合流输出，但**无** barrier 对齐/水位合并/exactly-once checkpoint 的两源端到端回归（roadmap WI7 行明文）。
- 既有 wire 级资产：`TestWatermarkMultiInputCombineWire`（直接调 processWatermark1/2）、`TestUnalignedCheckpointMultiInput`（ChannelState serde）——非端到端。
- barrier 对齐机制：InputGate 的 aligned/unaligned 形态（barrierAlignment 参数）；checkpoint engine 经 ServiceLoader（runtime 模块）；DSL 声明 checkpoint 即启用（WI14 实测）。
- §八 4：barrier 只由 source 读取线程注入；§八 6：恢复从最新 durable epoch manifest 开始。
- 同 JVM 首 execute 限制（WI6 发现）：每个 E2E 测试类独立 init/destroy 或单测试方法。
- runtime 测试形态先例：TestCheckpointGateServiceLoaderE2E（enableCheckpointing + LocalFileCheckpointStorage + durable manifest 断言）、TestE2EDimLookupWithCheckpoint（operator 级）。

## Goals

- **具名测试类四个**（roadmap 完成判定明文，落 nop-stream-runtime，全部 DataStream API 端到端、免容器）：
  1. `TestMultiInputBarrierAlignment`：两源（快慢速率）→ union → `transform()` 注入观测算子。**当前 E2E 契约断言**：无丢失、无重复、双通道有序、barrier 送达下游。执行期发现（已路由 FU-9）：union 顶点 E2E 层对齐阻塞缺位——时序探针 gap(f-4→f-5)=8ms≈源速率而非对齐窗口 ≈80ms（单算子级 gate 阻塞由 TestProcessingGuaranteeBehavior 承载）；时序判别断言待 FU-9 修复后启用。§八 4 行为断言：下游 barrier 不得先于同 collect 边界的 source 元素（线程同一性由 core TestSourcePullBarrierInjection 承载）。
  2. `TestMultiInputWatermarkMinMerge`：两源经 `SourceContext.emitWatermark` 发水位（快侧远超慢侧）→ union → 观测算子覆写 processWatermark 记录合并序列后转发。断言：合并值被慢侧拖住（快侧高水位不抬升合并值）、慢侧推进后合并值跟随。**EOS 陷阱**：任一 source 顶点 finish 会发 MAX_WATERMARK 拉满合并值——两源 latch 驻留，EOS MAX 排除断言。
  3. `TestMultiInputExactlyOnceCheckpoint`：两源 CheckpointedSourceFunction（offset 写入 snapshotState）→ union → sink；enableCheckpointing + LocalFileCheckpointStorage + 显式 setJobId/setPipelineId。断言：输出无重复无丢失 + durable manifest 存在 + **§八 6 双证据绑定**：manifest task snapshots 的 operator states 绑定两源 durable offset（a=6/b=6）；第二跑 restore 后各源 initializeState 收到 durable offset（RESTORE_PROBE=6）、消费区间不重发。
  4. `TestMultiInputSelfJoinEdgeDedup`（独立类——roadmap 明文）：`s.union(s)` 同源两条声明边 → sink，断言每元素恰两份（WI6 约束 2 修复回归守卫）。
- **§八 4 断言**（并入 BarrierAlignment 类）：下游 barrier 不得先于同 collect 边界的 source 元素——端到端管线内的行为断言；注入线程同一性由 core TestSourcePullBarrierInjection 既有断言承载（E2E 观测算子看不到注入线程）。

## Non-Goals

- 不修改内核代码（三件套是 WI6 交付的回归证据；若测试暴露 live defect，按 roadmap 规则按 Fix 单独立项——在 plan 内记录发现即可）；不做分布形态（LOCAL execution）。

## Scope

### In Scope

- `nop-stream/nop-stream-runtime`：四个具名测试类（+ 测试用 source/观测算子辅助类）
- roadmap WI7 行与 M2 里程碑（Phase 2 翻转）；当日日志
- Owner-doc 裁定：`No owner-doc update required`（纯测试计划；multi-input-model.md 无行为变更）

### Out Of Scope

- 内核修复；分布部署形态。

## Execution Plan

### Phase 1 - 三件套测试

Status: completed
Targets: `nop-stream/nop-stream-runtime`

- Item Types: `Proof`

- [x] `TestMultiInputBarrierAlignment`：两源交错 barrier 端到端 + 当前 E2E 契约断言（无丢失/无重复/双通道有序/barrier 送达）；对齐阻塞 E2E 缺位已路由 FU-9（时序判别断言待修复后启用）；§八 4 行为断言（barrier 后于同边界 source 元素）
- [x] `TestMultiInputWatermarkMinMerge`：两源 watermark min 合并端到端断言（观测算子 + latch 防 EOS 污染）
- [x] `TestMultiInputExactlyOnceCheckpoint`：两源 exactly-once 端到端 + durable manifest + §八 6 双证据 restore 断言
- [x] `TestMultiInputSelfJoinEdgeDedup`：独立类，同源两边恰两份断言
- [x] ai-dev/logs/ 当日条目更新

Exit Criteria:

- [x] **端到端验证（规则 #22）**：四个具名测试类齐备且隔离实跑绿，全部走 env.addSource→union→sink 完整路径
- [x] TestMultiInputSelfJoinEdgeDedup 独立类承载约束 2 回归
- [x] §八 4 与 §八 6 断言落地且形态可核（行为断言非结构性读代码）
- [x] Owner-doc：`No owner-doc update required`
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime` 绿（全量零退化）
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：五轮轨迹——首轮 FAIL（B-1 判别空洞、B-2 恒真析取）、复审 FAIL（plan 缺 Deferred 节、B-2 未修 + javadoc 失实）、第三轮（B-2 机制修复成立，剩余文本清账）、第四轮（日志标题误标一行）、第五轮 PASS；证据落 ai-dev/audits/nop-stream-sql/wi7-closure-audit.md
- [x] audit 通过后 roadmap WI7 `todo` → `done`（括注单层一对）+ M2 里程碑翻转；`parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑、20 done、里程碑 3 done、无静默丢弃
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI7 = done + M2 = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Deferred But Adjudicated

### union 顶点 E2E 对齐阻塞缺位（WI7 执行期发现的 live defect）

- Classification: `moved to explicit successor ownership`（roadmap FU-9，须 Fix 单独立项——Plan guide 规则 15：已确认 live defect 不得降级为普通 follow-up）
- Why Not Blocking Closure: WI7 是 Proof 类——回归测试暴露的内核缺陷按 roadmap 规则「按 Fix 单独立项」路由；本 plan 明文不修改内核代码。证据：TestMultiInputBarrierAlignment 时序探针 gap(f-4→f-5)=8ms（源速率）vs 单算子级 gate 阻塞（TestProcessingGuaranteeBehavior）期望 ≈80ms；严格模式接线与能力声明均已核（STRICT env + REPLAYABLE sources + 2PC sink）。已登记 roadmap FU-9；修复后启用测试内的时序判别断言。
- Successor Required: `yes`
- Successor Path: roadmap Follow-up Backlog FU-9 → Fix plan

## Closure Gates

- [x] TestMultiInputBarrierAlignment / TestMultiInputWatermarkMinMerge / TestMultiInputExactlyOnceCheckpoint 三者齐备且实跑绿
- [x] TestMultiInputSelfJoinEdgeDedup 独立类覆盖约束 2（roadmap 完成判定明文四类）
- [x] §八 4（barrier 只由 source 读取线程注入）断言落地（行为断言 + 线程同一性由 core TestSourcePullBarrierInjection 承载）
- [x] §八 6（恢复从最新 durable epoch manifest 开始）断言落地（双证据绑定）
- [x] 全部端到端（非 wire 级直调）
- [x] 既有测试零退化
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，五轮轨迹最终 PASS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/20-wi7-multi-input-regressions.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

Status Note: 多输入回归三件套落地——四个具名测试类实跑绿（严格模式契约/min 合并被慢侧拖住/§八 6 双证据 manifest 绑定 + RESTORE_PROBE/self-join 恰两份），§八 4 行为断言承载。执行期发现 union 顶点 E2E 对齐阻塞缺位（live defect）按规则路由 FU-9 须 Fix 单独立项，E2E 断言当前契约。独立 closure audit 五轮轨迹（FAIL→FAIL→文本清账→日志标题误标→PASS）。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，五轮独立实读实跑）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi7-closure-audit.md（五轮轨迹，最终 PASS）
- Evidence:
  - 四类隔离 6/6 绿；runtime 全量 1196 零退化（多轮独立复跑）
  - 判别性：水位 min（max 下双断言失败）、self-join（塌边 6→3 失败）、§八 6（manifest 绑定 + RESTORE_PROBE + sink.isEmpty 三断言）
  - FU-9 路由五要素齐备（roadmap + plan Deferred + 测试注释三处一致）
  - 门禁：doc-links strict 0、invariants sync OK、roadmap 31+7 done 20 / 里程碑 3
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/20-wi7-multi-input-regressions.md --strict` 退出码 0

Follow-up:

- FU-9（union 顶点 E2E 对齐阻塞缺位）→ Fix 单独立项；修复后启用测试内时序判别断言
