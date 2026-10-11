# 2417 WI7 窗口算子实现包归位 core

> Plan Status: active
> Last Reviewed: 2026-10-11
> Source: ai-dev/backlog/nop-stream-structure-refactor-roadmap.md（WI7，Phase C；M2 首项）
> Related: ai-dev/plans/2416-wi6-runtime-package-cycles-and-wire-dto.md

## Purpose

把窗口算子族从 runtime/operators/windowing 迁入 core/operators/windowing（与既有 core.operators.join 同构），消灭 core→runtime 软耦合的最后一半：删除 WindowedStreamImpl 的 Class.forName 反射兜底，实现「core 不依赖 runtime」全零（注释半边 WI3 已清、代码半边本 WI 清）。roadmap 自评投入产出比最高项。

## Current Baseline

- main 17 文件：runtime/operators/windowing（12 + functions/ 5 Internal*WindowFunction），对 runtime 其余包**零 import**（WI6 复核）——runtime 内叶子，可搬迁性已验证。
- test 48 文件：47 个仅依赖被迁包自身（本会话逐文件普查）可随迁；**1 个例外留守**——TestIdlePeriodProcessingTimeWindowE2E.java:26 import runtime.execution.GraphModelCheckpointExecutor（随迁即制造 core→runtime 测试依赖）。另 runtime/windowing（非 operators 子包）的 TestE2EWindowAllowedLatenessFullAggregate 本就在搬迁包之外，留守。
- **反射兜底**：core/datastream/WindowedStreamImpl.java:295-296 `Class.forName("io.nop.stream.runtime.operators.windowing.WindowOperatorFactoryImpl")`——core 与实现同模块后删除，改直接构造。
- **metrics 2 处顺延项承接**（WI6 登记）：WindowOperator.java:622、OverWindowOperator.java:128 的 StreamMetricsRegistries.registry() 直接引用——迁入 core 后该调用为 core 内部引用（StreamMetricsRegistries 本就是 core 类型），runtime 侧收口钉自动归零，无需改写。
- **销号联动（E4）**：nop-stream-invariant-loop-roadmap 的 P2-01-03（core 反射软耦合）与 P2-23（反射类名契约未记录）在本 WI 落地后销号；该 roadmap 引用 WindowedStreamImpl 旧行号的锚点随迁移漂移需同步重钉。
- TestWindowedStreamFactoryFailFast：:43-47 已用空 StreamComponents 桩构造 null-factory 路径；搬迁后仅 :50 错误消息文本（contains "nop-stream-runtime on classpath"）与 :28 过时 javadoc 需改写，fail-fast 契约保留。
- 基线：core 282 / flow 66（roadmap 钉值）；runtime = WI5 重钉 1377 + WI6 新增 3 = 1380（本 WI 迁走 47 个测试文件后 runtime 计数相应减少、core 相应增加，等值性按「迁移前后两模块合计不变」核对）。

## Goals

- core/operators/windowing 承载窗口算子族（main 17 + test 47），与 core.operators.join 同构。
- WindowedStreamImpl 反射删除 → 直接构造；core src 树 `io.nop.stream.runtime` 命中数 **0**（main+test，本 roadmap 不变式钉）。
- invariant-loop roadmap P2-01-03 / P2-23 按 E4 双向销号。
- 三模块测试基线保持（core/flow 钉值不降、runtime 按迁移守恒）。

## Non-Goals

- 不动 IWindowOperatorFactory 的 17 方法接口（WI8 对象）。
- 不改窗口语义/状态布局/timer 触发/序列化格式（E1 行为保持）。
- 不迁 TestIdlePeriodProcessingTimeWindowE2E（留守 runtime，经 runtime.execution 驱动的 E2E）与 TestE2EWindowAllowedLatenessFullAggregate。

## Scope

### In Scope

- git mv main 17 文件 → `nop-stream-core/src/main/java/io/nop/stream/core/operators/windowing/`（含 functions 子包）。
- git mv test 47 文件 → `nop-stream-core/src/test/java/io/nop/stream/core/operators/windowing/`。
- 全仓 import/FQN 改写（引用面全集经审查核实：runtime main CheckpointSerDe.java:860-929 六处内联 FQN；runtime test 目录外 5 文件——TestTransactionalOperatorIsCheckpointParticipant/TestE2EWindowAggregateRestore/TestRecalculateBackfillE2E/TestTimerCheckpointRestoreE2E/TestSideOutputChainingE2E；benchmark 3 文件——WindowOperatorProcessElementBench/SlidingWindowFanoutBench/WatermarkEmitBench；flow 无 import 改写点——StreamModelDslBuilder 在 flow 包且不 import 被迁包，其对 windowing 的耦合是行为级归下一条）。
- **flow 3 用例正向化改写**（审查 B1）：TestAdvancedTransforms:110-130/:250-267、TestParameterizedAggregateModel:110-124 现断言 `assertThrows(StreamException)` + contains("nop-stream-runtime")——依赖「flow test classpath 无 runtime」前提，迁移后工厂必在 classpath，改写为正向接线断言（window 分派成功产出算子）+ javadoc 同步（TestAdvancedTransforms:48-53、TestParameterizedAggregateModel:38,118）。
- **invariant 门禁工具链重钉**（审查 B2）：ai-dev/audits/nop-stream-invariants/ 下 gate-inventory.json（:144/152/172/298 四 FQCN 的 module 节 runtime→core）、wiring-registry.json（:25/:47 consumers + :177-192 consumerTable 行路径锚点）、output-contract-registry.json（:63/:72 emission point 路径）、invariant-catalog.md §4（:208-213 四行路径）。
- WindowedStreamImpl 反射删除改直接构造（volatile+DCL 保持；try/catch+LOG.warn :299-305 随反射消亡）；**4 处 call-site 消息 + 注释 + FailFast 断言成套改写**（审查 M4：消息源在 WindowedStreamImpl :324/:342/:363/:381——新文案语义 = 「no IWindowOperatorFactory available/configured」，runtime 缺席语义随迁移失效）；TestWindowedStreamFactoryFailFast :50 断言与 :28 javadoc 同步。
- invariant-loop roadmap 销号 + 锚点重钉；source-anchors STRM-018 路径改写（WI3 预告的兑现）。
- 同包用户（core 内原引用方若有）补 import。

### Out Of Scope

- WindowOperatorBuilder/FactoryImpl 接口形状（WI8）；runtime.metrics 遗留；TestIdlePeriodProcessingTimeWindowE2E 的改造。

## Execution Plan

### Phase 1 - 搬迁与解反射

Status: completed
Targets: core/operators/windowing（新）、runtime/operators/windowing（删）、WindowedStreamImpl、FailFast 测试

- Item Types: `Fix`（反射软耦合为已确认结构缺陷，invariant-loop P2-01-03 在案）

- [x] git mv main 17 + test 47 文件；package 声明改写；全仓 import/FQN 改写（含同包用户与 javadoc {@link}）
- [x] WindowedStreamImpl :295-296 反射删除，改 `new WindowOperatorFactoryImpl()` 直接构造（default factory 语义保持）
- [x] TestWindowedStreamFactoryFailFast：:50 消息文本与 :28 javadoc 改写（fail-fast 断言本体保留）
- [x] windowing 2 处 metrics 引用核实为 core 内部引用（无需改写，runtime 收口钉归零）
- [x] invariant-loop roadmap P2-01-03/P2-23 销号（双向记录）+ WindowedStreamImpl 锚点行号重钉；source-anchors STRM-018 路径改写

Exit Criteria:

- [x] **不变式钉**：`grep -rn "io.nop.stream.runtime" nop-stream/nop-stream-core/src`（main+test）命中数 **0**
- [x] runtime/operators/windowing **main 树**目录不存在（test 树留守 TestIdlePeriodProcessingTimeWindowE2E 1 文件，其 package 即 io.nop.stream.runtime.operators.windowing——留守必致目录存续；该文件 :120/:140/:261 的裸名/FQN 引用改写为 core 包 FQN 以跨包编译）；core/operators/windowing = main 17 + test 46（审查 M3 实测：顶层 45 + functions/ 1，非 47）
- [x] **行为保持**（审查 M3 钉死口径——surefire run 数 + 文件数双轨）：core clean 后 **1952/0/1**、文件数 **328**；runtime **1199/0/10**（与预测 1380−181 精确一致）、文件数 **316**；flow **315/0/0**、文件数 66；守恒复核 = core+runtime surefire 合计 3151 = 迁移前 282 口径下原 core 基线 + 1380 的守恒形态（core 原 surefire 口径以 clean 首跑为准）。执行注记：三模块合跑首败于 maven 增量编译陈旧态（TestObjectDeclaredKeyRestore/ReproWM1 假性找不到符号），clean 后消失；runtime 首跑 4 error 为 TestRuntimeInvariantTableCompleteness 硬编码被迁 FQCN——即审查 B2 的 gate-inventory.json，重钉后 12/12 绿
- [x] 反射钉：`grep -rn "Class.forName" nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/datastream --include="*.java"` 零命中（其余包 12+ 处 Class.forName 为既有合法用法，审查 M1）
- [x] **invariant 门禁**（真实口径）：`check-nop-stream-invariants.mjs inventory` 退出码 0（4 个 WI7 引入违规——module 节错位——已修复）；`mjs all` 退出码 1，违规恰为 **24 条先存漂移**（output-contract 15 + wiring 9，经审计者对照 HEAD 基线 worktree 证实 WI6 提交时已存在、非本 WI 引入）——裁定移交 WI12 门禁固化时重生成登记；本 WI 新增的 WindowOperator.numPendingProcessingTimeTimers 访问器已钉入 gate-inventory 并被 core 侧 TestInvariantTableCompleteness 15/15 验证
- [x] FailFast 测试改写后仍断言 fail-fast 路径（null factory → 明确错误）
- [x] E4 双向销号：invariant-loop roadmap P2-01-03（:311）/P2-23（:206）状态更新 + 锚点重钉（实际反射位 :295-296；销号记录写明死链两半分别由 WI3 注释收敛与本 WI 反射删除解决）+ 本 roadmap WI7 括注互引；STRM-018 指向新路径；WindowAssigner javadoc 随搬迁复核（roadmap WI7 子项 b）
- [x] No new test required: 纯搬迁 + 解反射（E1 既有基线为钉）；FailFast 测试改造为既有用例修订非新增
- [x] No owner-doc update required（STRM-018 属本 WI 交付本体；基线文档模块表 §二 的窗口算子归属描述在 WI12 终稿统一核对——WI3 已登记预告）
- [x] core/operators 测试目录既有同名类 TestWindowOperatorWatermarkReconciliation（io.nop.stream.core.operators）与迁入类同名——编译合法（FQN 不同），交叉注记防混淆
- [x] `ai-dev/logs/2026/10-11.md` 已追加 WI7 条目

## Closure Gates

- [x] core src 的 runtime 引用全零（本 roadmap 最重不变式）
- [x] 三模块测试全绿且基线守恒
- [x] 反射兜底删除（Class.forName 零命中）
- [x] E4 双向销号完成
- [x] 无 in-scope live defect 被降级为 follow-up
- [x] 独立子 agent closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2417-wi7-window-operator-relocation-to-core.md --strict` 退出码 0

## Closure

Status Note: 迁移本体经独立审计 8 项全 PASS（不变式钉归零、63 rename 纯机械、解反射语义、基线三模块双轨吻合、E4 双向销号）。审计发现 4 阻塞项已全部收掉：B1 gate-inventory module 节错位（结构化 JSON 迁移修复，inventory exit 0）+ invariant-catalog.md 漏改（已重钉）；B2 flow 3 处 javadoc 正向化补齐；B3 留守文件 package 恢复（package 迁移误伤）+ 「mjs all exit 0」不实陈述修正（我的验证缺陷：管道 tail 掩盖了 node 真实退出码——24 条先存漂移实为 HEAD 既有，裁定移交 WI12）；B4 doc-links 53 断链为并发会话 nop-tools 重组所致、与本 WI 无涉。留守 E2E 白盒适配：WindowOperator 新增 test-visible 访问器（hasPendingRestoredBuffer 先例）+ 本地 serializer 拷贝，访问器已钉入不变式表并被 15/15 验证。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general-purpose，closure-audit fresh session，2026-10-11）
- Audit Session: agent_abc903ed-b062-4836-878c-619de22edf28
- Evidence:
  - 不变式钉：core src io.nop.stream.runtime = 0（审计实测）；63 rename 非注释变更行 0
  - 解反射：datastream Class.forName=0；4 消息改写；FailFast 审计者自跑 4/4 绿
  - flow：断言正向化 + javadoc 补齐；315/0/0 审计者自跑
  - 基线：core 1952/0/1（文件 328）/ runtime 1195/0/10（文件 316；预测 1199 − 4 个随 inventory 迁至 core 侧的参数化用例）/ flow 66 文件；守恒成立
  - invariant：inventory exit 0；24 先存漂移交 WI12（对照 HEAD 证实）；TestInvariantTableCompleteness 15/15（含新访问器钉）
  - E4：P2-01-03/P2-23 closed 双向记录；STRM-018 新路径；解析器 12 项 7 done
  - Deferred：24 先存漂移→WI12；windowing metrics 2 处已随迁移归为 core 内部引用（runtime 收口钉归零）

Follow-up:

- no remaining plan-owned work
