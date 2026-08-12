# Cycle 2 / I4 — 修复执行（跨 task interim fail-fast + 注册表分类更新 + pin 移除）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Draft Review: 3 轮独立子 agent 对抗性审查通过（round 1：2 Major（F1 TimestampedCollector 翻转/注册表同步漏项 / F2 行为变更缺全量回归）+ 5 Minor，全部修复；round 2：4 Minor（N1-N4）修复；round 3：0 Blocker / 0 Major / 1 Minor（E2E 计数 3/3→4/4 传播遗漏，已修复），verdict 可转 active）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Cycle 2 / I4 行（跨 task interim fail-fast 修复（`RecordWriterOutput` / `BroadcastingRecordWriterOutput` `collect(OutputTag)` 空体 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常；类内部行为修复，private 嵌套类、`Output` 接口零变更，同 RL-7 先例）+ 注册表分类更新 + 过渡 pin 移除；test-first 先红后绿；类别清扫（全 `Output` 实现类兄弟））；I6 预裁决 `ai-dev/audits/nop-stream-invariants/adjudication-table.md` §6.2（interim fail-fast 自动信封 + `HG-01` 人工确认门）；I1 门禁 `cycle2-I1-input.md` / `output-contract-registry.json` / `mjs-pins.json`
> Related: 前置 `2026-08-12-1217-10-nop-stream-invariants-cycle2-I3-adjudication.md`（I3，硬串行：interim fail-fast 预授权确认 + 其余 P0/P1 派发清单 = 本 plan 输入）；后续 Cycle 2 / I5（全量验证）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 2 / I4. 修复执行

## Purpose

执行跨 task interim fail-fast 修复：`RecordWriterOutput.collect(OutputTag, X)`（`StreamTaskInvokable.java:645` 空体）/ `BroadcastingRecordWriterOutput.collect(OutputTag, X)`（:705 空体）→ 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常（先例 `ChainingOutput.java:119`）——消除跨 task 部署下 side-output 静默丢弃（已确认契约缺口 P1，I6 §6.2 预授权 + I3 确认）；同步完成**注册表分类更新**（`pinned-known-violation` → `fail-fast`，含 `TimestampedCollector` disposition 同步）+ **过渡 pin 2 条移除**；test-first 先红后绿；类别清扫（全部 main `Output` 实现类兄弟 + 全部 `collect(OutputTag` call-site）；门禁复跑零命中 + 全量模块回归（行为变更面，Cycle 1 / I4 先例）；并按 I3 派发清单执行其余 P0/P1 工作项（如有）。`HG-01` 线协议支持不在本 plan（人工确认门未过）。**用户可见行为变更声明**：跨 task 部署（多 vertex / fanOutWriters）下尾算子发射 side-output 将从「静默丢弃」变为「fail-fast 崩溃（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）」，直至 `HG-01` 线协议支持落地——行为契约转移必须随修复留痕（注册表 disposition + 本声明）。

## Current Baseline

> 已核对 live repo（2026-08-12）：两处空体、注册表、pin、既有门禁全部实测存在。

- **I6 预裁决（前置依据）**：`adjudication-table.md` §6.2 双层裁决——层次 1 = interim fail-fast（自动信封内，private 嵌套类行为修复，`Output` 接口零变更，同 RL-7 先例 `b20fcd0e1`），预授权分派 Cycle 2 / I3 确认 → I4 执行；层次 2 = `HG-01` 线协议结构性重构（人工确认门，不在本 plan）。
- **I3 为硬前置**：本 plan 执行前 `2026-08-12-1217-10-...`（Cycle 2 / I3）必须已 `completed`——interim fail-fast 预授权确认 + 其余 P0/P1 派发清单（`adjudication-table.md` Cycle 2 节）为本 plan 输入。若执行时 I3 未 completed，本 plan 不得开始任何 Phase——立即返回 `blocked`。
- **live 代码（本日实测）**：`RecordWriterOutput`（`StreamTaskInvokable.java:611`，`collect(OutputTag)` :645 = 仅注释「Side outputs not supported in cross-task exchange」的空体）/ `BroadcastingRecordWriterOutput`（:660，`collect(OutputTag)` :705 = 空体）。fail-fast 先例 = `ChainingOutput.java:111-124`（`collect(OutputTag)` :119 抛 `StreamRuntimeException(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER)` + `ARG_OUTPUT_TAG` / `ARG_DETAIL` 参数）；错误码定义 = `NopStreamErrors.java:104`。
- **跨 task 接线链（live 复核）**：`GraphExecutionPlan.java:454-458`（fanOutWriters 构造 `StreamTaskInvokable`）→ `wireOperators(List<RecordWriter>)`（`StreamTaskInvokable.java:200-245`）/ `wireTailToRecordWriter`（:348-352）→ tail 算子 setOutput(`RecordWriterOutput` / `BroadcastingRecordWriterOutput`) → 发射点 side-output 静默丢弃。in-task 链式路径已 fail-fast（`ChainingOutput` 无消费者抛错，`b20fcd0e1`）——本 plan 补齐跨 task 尾接线路径。
- **注册表（live）**：`output-contract-registry.json` — RWO/BRWO 分类 = `pinned-known-violation`（disposition 注明「interim fail-fast pre-authorized to Cycle 2 / I4；pin removal = Cycle 2 / I4 interim fail-fast fix landed + registry classification updated」）。
- **过渡 pin（live）**：`mjs-pins.json` pinnedViolations = 2——`RWO-cross-task-noop` / `BRWO-cross-task-noop`（key = `scan-output-contract` V3 违规串精确匹配；removalTrigger = 本 plan 修复落地 + 注册表分类更新后移除，禁静默移除）。**移除语义**：修复后 V3 违规串消失 → pin 无对应违规 → 必须移除（stale pin = 错误）。
- **JUnit 门禁（live）**：`TestOutputContractInvariant`（nop-stream-core，10 用例）——含反射实例化 RWO/BRWO 断言「空体 no-op = pin 语义」（RWO/BRWO 分类迁移须同步注册表与断言，先例 = I4 pin 翻转测试）；`TestSideOutputChainingE2E`（runtime，3 用例，in-task 端到端基线）。
- **其余 I1 门禁基线**：10 门禁类 / 102 tests / 0 failures（`cycle2-I1-input.md`）；mjs `all` exit 0。
- **真正剩余的 gap**：跨 task 尾接线路径 side-output 静默丢弃（RWO/BRWO 空体）；注册表分类与 pin 为过渡态待迁移；无跨 task fail-fast 的端到端验证。

## Goals

- RWO/BRWO `collect(OutputTag, X)` 空体 → fail-fast（抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 风格异常，含 `ARG_OUTPUT_TAG` / `ARG_DETAIL`），跨 task 部署下 side-output 不再静默丢弃（Rule #24）。
- 注册表分类迁移：RWO/BRWO `pinned-known-violation` → `fail-fast`（`output-contract-registry.json` disposition / subSemantics 同步）；过渡 pin 2 条移除（`mjs-pins.json`，含移除留痕）。
- JUnit 断言翻转（test-first 先红后绿）：`TestOutputContractInvariant` RWO/BRWO 反射断言从「空体 no-op」翻转为「抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`」；新增跨 task fail-fast 端到端用例。
- 类别清扫：全部 main `Output` 实现类兄弟（4 个）+ 全部 `collect(OutputTag` call-site + 跨 task 接线链核对，无同族兄弟遗留静默丢弃。
- 门禁复跑零命中（JUnit 门禁子集 0 failure + mjs `all` exit 0 + E2E 全绿）+ **core / runtime 全量模块回归**（行为变更面，Cycle 1 / I4 先例：无既有测试依赖旧行为）；按 I3 派发清单执行其余 P0/P1 工作项（如有）；文档收口（含用户可见行为变更声明）+ roadmap 行流转 `todo`→`planned`→`done`。

## Non-Goals

- **不做 `HG-01` 线协议结构性重构**（跨 task side-output 序列化 / 路由 / 消费注册——人工确认门未过；interim fail-fast 为过渡保护，`HG-01` 支持属增强不阻塞本 plan）。
- **不改 `Output` 公共接口 / RecordWriter 线协议 / 模块边界**（接口契约零变更，I6 裁定信封内）。
- **不执行 Cycle 2 / I5 全量验证**（属下一 work item，消费本 plan 修复结果）。
- **不新增门禁 / 不改既有门禁语义**（RWO/BRWO 分类迁移 = 注册表 + 断言同步更新，属本 plan 收尾；既有 10 门禁类其他内容不改写、不弱化；**新增 / 扩展端到端用例（`TestSideOutputChainingE2E` 第 4 用例）与断言翻转（分类迁移 + 透传目标敏感迁移）属修复必要同步，非门禁语义弱化**）。
- **不裁决新族 / 不派生 Cycle 3**（属 I3 裁决 + I6 收口；本 plan 只执行派发清单）。

## Scope

### In Scope

- RWO/BRWO fail-fast 修复（test-first 先红后绿）+ 类别清扫（4 实现类兄弟 + 全部 call-site + 接线链）。
- 注册表分类迁移 + 过渡 pin 移除（含留痕）+ JUnit 断言翻转 + 跨 task fail-fast 端到端用例。
- 门禁复跑零命中 + 文档收口（`ai-dev/logs/`、roadmap 行流转、如涉及 design 文档同步）。
- I3 派发清单其余 P0/P1 工作项（如有，按 I3 `adjudication-table.md` Cycle 2 节执行）。

### Out Of Scope

- `HG-01` 线协议支持、Cycle 2 / I5–I6（后续 work items）、Cycle 3 派生（I6）、新门禁沉淀（Cycle 3 / I1）。
- 既有 10 门禁类内容改写（只增不弱化；RWO/BRWO 断言翻转属分类迁移必要同步，非弱化）。

## Execution Plan

### Phase 1 - 跨 task interim fail-fast 修复（RWO/BRWO）

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/execution/StreamTaskInvokable.java`（RWO :645 / BRWO :705）；`nop-stream/nop-stream-core/src/test/java/io/nop/stream/core/`（`TestOutputContractInvariant`）；nop-stream-runtime（E2E 用例，见接线验证）

- Item Types: `Fix | Proof`
- [x] **test-first**：先翻转 / 新增断言——`TestOutputContractInvariant` RWO/BRWO 反射实例化断言改为「`collect(OutputTag, record)` 抛 `StreamRuntimeException`，错误码 = `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`，含 `ARG_OUTPUT_TAG` / `ARG_DETAIL` 参数」→ 先红（当前空体不抛错）→ 修复后转绿（先例 = I4 pin 翻转测试同风格）。**同步翻转联动项（Must）**：`testTimestampedCollectorWrappingRecordWriterOutputEqualsCrossTaskDrop`（断言包装 RWO 后分区大小为 0 无异常）改为断言包装 RWO 后 `collect(OutputTag)` 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`（透传目标敏感语义随 RWO 修复迁移）+ 测试类 javadoc 相关描述同步；参数化用例 `testCollectOutputTagBehaviorMatchesRegistry` 的 switch 增加 `fail-fast` 分类 case（当前仅 forward / pinned 两 case，default fail——注册表迁移后必须有新 case，否则门禁自红）
- [x] 修复实现：RWO / BRWO `collect(OutputTag, X)` 空体 → 抛 `StreamRuntimeException(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER).param(ARG_OUTPUT_TAG, outputTag.getId()).param(ARG_DETAIL, ...)`（镜像 `ChainingOutput.java:119` 语义：跨 task 部署下 side-output 无消费通道 = 配置错误，必须快速失败，禁止静默丢弃）；**保留 `Output` 接口零变更、private 嵌套类结构零变更**（信封内，I6 §6.2 边界）
- [x] **端到端验证（Rule #22）**：新增跨 task fail-fast 端到端用例——从任务入口（`env` source 或既有 E2E 先例等价构造，见下）到 task tail 算子（WindowOperator / CepOperator / ProcessOperator 任一发射点）经 fanOutWriters 尾接线（`GraphExecutionPlan` → `StreamTaskInvokable(List<RecordWriter>)` → **`wireOperators(fanOutWriters)`（:233-248，tail 接线点；1 个 writer → RWO（:239），≥2 个 writer → BRWO（:245），BRWO 仅出自此路径**）→ tail 算子 setOutput(RWO/BRWO)）→ 算子发射 side-output → 断言任务抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`（入口到出口完整路径，非组件级孤立断言）。**构造方式对齐先例**：`TestSideOutputChainingE2E` 第 3 用例风格（直接构造 invokable + 驱动算子方法 processElement → 发射 → 异常断言）为验收模式，不强制 `env.execute()` 全栈 harness；`wireTailToRecordWriter`（:348）仅无参 wireOperators 的 recordWriter 路径使用（单 RWO），本用例的 fanOutWriters 路径走 `wireOperators(fanOutWriters)`。**BRWO 覆盖要求**：用例构造 **2 个 fanOut writer** 覆盖 BRWO 路径（1 个 writer 仅覆盖 RWO）；新用例**加入 `TestSideOutputChainingE2E`（作为第 4 用例）**，确保 Phase 3 门禁命令 `-Dtest=TestSideOutputChainingE2E` 覆盖到它
- [x] **接线验证（Rule #23）**：断言 RWO/BRWO 实例确实由跨 task 接线链在运行时注入（tail 算子 `setOutput` 收到的是 RWO/BRWO 且该实例的 `collect(OutputTag)` 抛错被实际调用——计数器 / 标志位 / 异常断言任一方式；异常断言即可充当接线证明：抛错发生 = 该实例被运行时调用）
- [x] **类别清扫（roadmap 强制）**：grep 全部 main `Output` 实现类兄弟（4 个：ChainingOutput / TimestampedCollector / RWO / BRWO——逐一核对 `collect(OutputTag)` 无其余静默丢弃点）+ 全部 `collect(OutputTag` call-site（6 发射点 + 转发调用）+ 接线链（`GraphExecutionPlan.java:454-458` / `wireOperators` / `wireTailToRecordWriter`）——只修报到的实例 = 未完成
- [x] 无静默跳过（Rule #24）：修复后 `collect(OutputTag)` 路径 = 显式 fail-fast；不留空体 / continue / 吞异常

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 先红后绿证据在案（翻转断言在修复前红、修复后绿；**「修复后绿」语义 = 直接翻转断言绿；registry-backed 参数化用例（`testCollectOutputTagBehaviorMatchesRegistry`）按注册表路由、须待 Phase 3 注册表迁移后转绿——Phase 1 完成时不要求该参数化用例绿，先红证据不受影响**）
- [x] **端到端验证**：跨 task fail-fast 端到端用例绿（入口 → fanOutWriters 尾接线 → 发射 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER` 完整路径断言；对齐先例 `TestSideOutputChainingE2E` 验收模式——直接构造 invokable + 驱动算子方法亦可，完整路径 = 尾接线实例上 processElement → 发射 → 异常断言）
- [x] **接线验证**：RWO/BRWO 实例运行时注入 + `collect(OutputTag)` 抛错被实际调用的断言在案
- [x] **无静默跳过**：4 实现类兄弟 + 6 发射点 + 接线链类别清扫结论记录（无遗留静默丢弃点）
- [x] `Output` 接口 / RecordWriter 线协议 / 模块边界零变更（git diff 核对）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - I3 派发清单其余 P0/P1 工作项（如有）

Status: completed
Targets: 按 `ai-dev/audits/nop-stream-invariants/adjudication-table.md` Cycle 2 节「P0/P1 派发清单」（I3 产出）

- Item Types: `Fix | Proof`
- [x] 与 I3 派发清单核对：除 interim fail-fast（Phase 1）外的其余 P0/P1 工作项——逐项按 I3 六要素执行（目标类 + 缺陷描述 + 预期行为 + 类别清扫范围 + test-first 先红后绿 + 门禁复跑要求）；每项执行前确认信封边界（结构性重构项须「需人工确认」标注，不得自动执行）
- [x] **无追加派发时的显式声明**：若 I3 派发清单仅有 interim fail-fast 一项（预期情形），本 Phase 记录 `No additional I4 work items dispatched by I3` 并标记完成（不允许空 Phase 静默关闭——必须有显式结论记录）
- [x] 每项执行后：类别清扫结论 + 测试证据 + 门禁复跑记录写入 `ai-dev/logs/` 与 red-list.md 修复状态节

Exit Criteria:

- [x] I3 派发清单逐项有执行结论（修复完成 + 测试绿）或显式「无追加派发」记录在案
- [x] 结构性重构标注项（如有）未被自动执行（人工确认门遵守记录在案）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 注册表分类迁移、pin 移除与门禁复跑

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/{output-contract-registry.json,mjs-pins.json}`；`cycle2-I1-input.md`（仅回读，不回写——唯一落点原则）；JUnit 门禁子集

- Item Types: `Fix | Proof | Follow-up`
- [x] 注册表分类迁移：`output-contract-registry.json` RWO/BRWO `classification` = `pinned-known-violation` → `fail-fast`；`disposition` / `subSemantics` 同步更新（注明 interim fail-fast 落地日期 / commit；`HG-01` 关联保留为「线协议支持属增强」）；**`TimestampedCollector` 条目 disposition / subSemantics 同步**（现文本「wrapping-RecordWriterOutput scenario equals cross-task drop … (no fix in this plan)」在 RWO 修复后为事实错误——改为「包装 RWO 时 collect(OutputTag) 透传 → RWO fail-fast 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`」，透传目标敏感语义随被包装对象迁移）；`updated` 字段更新。**commit 顺序约束**：修复代码 + 注册表迁移 + pin 移除 + 断言翻转须同 commit 落地（防中间态——修复落地而注册表未迁移期间 mjs V3 红 + stale pin 为预期中间态，不允许中间态 commit 保留；JUnit 先红后绿叙事以翻转断言为唯一先红证据）
- [x] 过渡 pin 移除：`mjs-pins.json` 删除 `RWO-cross-task-noop` / `BRWO-cross-task-noop`（含留痕：文件头 note 说明移除原因 = Cycle 2 / I4 interim fail-fast 修复落地 + 注册表分类更新，遵守 removalTrigger；**禁止静默移除**——移除必须与修复 + 注册表更新同 commit / 同记录）
- [x] JUnit 断言同步复核：`TestOutputContractInvariant` 分类迁移断言（Phase 1 已翻转）与注册表新分类（fail-fast）一致；参数化用例来源（registry-backed）读注册表不报不一致
- [x] 门禁复跑零命中：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` 0 failure（含翻转后断言）+ `node ai-dev/tools/check-nop-stream-invariants.mjs all` exit 0（pins = 0；无 unpinned / 无 stale）+ `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestSideOutputChainingE2E` 绿（**4/4——含 Phase 1 新增跨 task fail-fast 第 4 用例**；既有 in-task 3 用例不回归）
- [x] **全量模块回归（行为变更面，F2 先例）**：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime` 全量跑（RWO/BRWO 是跨 task 部署下全部 6 个发射点的出口路径，collect(OutputTag) 从静默 no-op 翻为抛异常 = 行为变更）；结论 = 无既有测试依赖旧行为（Cycle 1 / I4 Phase 4 先例：core 1418 / runtime 804 全绿声明），证明本 plan 未引入回归；全量跑失败 = 先于本 plan closure 修复（不得把回归延迟到 I5）。**cep 排除依据**：nop-stream-cep 不实例化 StreamTaskInvokable / RecordWriterOutput（CepOperator 发射点走 in-task 链式路径），且其 2 个门禁类已含在 `Test*Invariant*` 子集复跑中——排除 cep 全量不引入覆盖缺口
- [x] 文档收口：`ai-dev/design/nop-stream/core-design.md` §6.1 不变式节同步（跨 task 实例处置状态 = fail-fast 落地；`HG-01` 保留待办标注）——**如 §6.1 已含过渡 pin 描述则更新，未含则记录 `No owner-doc update required` 依据**；`ai-dev/audits/nop-stream-invariants/red-list.md` Cycle 2 节修复状态回写（如 I2/I3 有对应条目）；`ai-dev/logs/` 顶部条目更新；roadmap Cycle 2 / I4 行流转（active 时 `todo`→`planned`；closure audit 通过后 `planned`→`done`）
- [x] committed 回归测试（修复 + 翻转断言 + 注册表 + pin 移除同 commit 防漂移；mission commitFormat：`fix(nop-stream): <description>`）

Exit Criteria:

- [x] 注册表分类 = `fail-fast`（RWO/BRWO），disposition 注明修复落地；`HG-01` 关联保留
- [x] `mjs-pins.json` pinnedViolations = 0（2 条过渡 pin 已移除，留痕在案——非静默移除）
- [x] JUnit 门禁子集 0 failure + mjs `all` exit 0（pins 0）+ `TestSideOutputChainingE2E` **4/4** 绿（含新增跨 task fail-fast 用例）+ **core / runtime 全量回归绿**（无既有测试依赖旧行为，记录测试数）
- [x] 文档收口完成（core-design 或显式 `No owner-doc update required` + red-list 回写 + logs + roadmap 行流转）
- [x] **无静默跳过**：全部新增 / 翻转路径有断言覆盖；空 Phase 2 有显式结论记录（见 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] I3（`2026-08-12-1217-10-...`）已完成（硬前置）；interim fail-fast 预授权确认在案
- [x] 跨 task 静默丢弃已消除：RWO/BRWO `collect(OutputTag)` fail-fast（`ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）——已确认契约缺口修复，非降级
- [x] 注册表分类迁移 + 过渡 pin 移除完成（留痕在案，禁静默移除遵守）；JUnit 断言与注册表一致
- [x] 端到端 + 接线验证通过（跨 task fail-fast 完整路径；tail 算子运行时注入断言）
- [x] 类别清扫完成（4 实现类兄弟 + 6 发射点 + 接线链，无同族遗留静默丢弃）
- [x] 门禁复跑零命中（JUnit 门禁子集 0 failure + mjs `all` exit 0 + E2E 全绿）；**全量模块回归通过**（core + runtime 全量，无既有测试依赖旧行为）
- [x] 文档收口：core-design / red-list / logs / roadmap 行流转（或显式 `No owner-doc update required`）；**用户可见行为变更声明随注册表 disposition + 本 plan 落档**（跨 task side-output 发射 fail-fast，直至 `HG-01` 落地）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（`HG-01` = 人工确认门延续，非本 plan scope；其余派发项均有执行结论）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证（a）RWO/BRWO fail-fast 在运行时确实被调用（端到端用例断言异常实际抛出），（b）端到端路径从入口到出口完整连通，（c）无空方法体 / 静默跳过作为正常实现
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 运行记录在案（基线 = I1 closure 钉定的 `34aed42c1` 既有 14 项 high findings：11× `UnsupportedOperationException` + 3× not-yet-implemented 注释；**RWO/BRWO 空体不在该工具检出范围**，本 plan 修复不改变 findings 集——判据 = findings 集与基线一致，执行时先实跑记录作为对比基线）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'` 通过（0 failure）
- [x] `./mvnw compile -pl nop-stream/nop-stream-core -q` 通过（checkstyle 依 mission lint 命令执行，如配置则须通过）

## Deferred But Adjudicated

### `HG-01` 跨 task side-output 线协议结构性变更（人工确认待办延续）

- Classification: `Fix`（已确认契约缺口 P1，执行门 = 人工确认）——**已确认 live defect，必须修（线协议支持），不属 deferral**，仅执行门未过
- Why Not Blocking Closure: ① 修复需 RecordWriter 线协议结构性重构，人工确认门未过（I6 登记在案：`HG-01`）；② 本 plan 落地 interim fail-fast 后跨 task 部署下 side-output 不再静默丢弃（快速失败替代静默丢失），`HG-01` 支持属增强不阻塞；③ 已确认缺口登记 = 处置（人工确认执行门 + interim 保护），非延期。
- Successor Required: `yes`
- Successor Path: 人工确认后另立 plan（跨 task 侧输出线协议设计 + 实现 + E2E）；触发条件 = 人工批准 + 跨 task side-output 需求出现（或 CI 门禁红暴露新实例）

### I3 派发清单中标注「需人工确认」的结构性重构项（如有）

- Classification: `watch-only residual`（执行门 = 人工确认，I3 派发时如标注）
- Why Not Blocking Closure: 结构性重构（公共 API / 模块边界 / Operator 接口变更）执行前须人工确认（mission Cross-Cutting）；如 I3 派发清单含此类项，本 plan 只登记不执行，不阻塞信封内修复项关闭。
- Successor Required: `yes`
- Successor Path: 人工确认后另立 plan

## Non-Blocking Follow-ups

- Cycle 2 / I5（全量验证与门禁零命中）为直接 successor——消费本 plan 修复结果 + 门禁复跑基线。
- `TestSideOutputChainingE2E` 多输出路径扩展（ctx.output / ProcessWindowFunction / PatternProcessFunction E2E 用例）：由 I2 探查评估结论 + I5 视情况覆盖，不阻塞本 plan 关闭。
- `HG-01` 线协议支持（人工确认后另立 plan，见 Deferred）。

## Closure

Status Note: Cycle 2 / I4 修复执行全部完成——跨 task interim fail-fast 落地（RWO/BRWO `collect(OutputTag)` 空体 → 抛 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`）、注册表分类迁移（`pinned-known-violation` → `fail-fast`）、过渡 pin 2 条移除（留痕在案）、JUnit 断言翻转 + 跨 task E2E 第 4 用例、类别清扫零遗留、门禁复跑零命中 + core/runtime 全量回归绿、文档收口完成；独立子 agent closure audit 10/10 PASS；`HG-01` 线协议支持为人工确认待办延续（增强不阻塞）。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_009afddd2ffe9fBOlwYbXi3B0z`，audit-only 禁改文件，10/10 项 PASS）
- Evidence:
  - **Plan checklist 状态**：PASS——Phase 1/2/3 全部 items + Exit Criteria `[x]`，Phase Status 全 `completed`；Closure Gates 由本 closure pass 勾选；`Plan Status: active`（关闭前）诚实。
  - **I3 硬前置**：PASS——`2026-08-12-1217-10-...md` `> Plan Status: completed`。
  - **live 代码修复**：PASS——`StreamTaskInvokable.java:649-659`（RWO）/ `:717-727`（BRWO）抛 `StreamRuntimeException(ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER)` + `ARG_OUTPUT_TAG`/`ARG_DETAIL`（错误码定义 `NopStreamErrors.java:104`），无空体残留；commit `88bc0270c` 主代码 delta = 仅 StreamTaskInvokable.java + 2 测试文件；`Output` 接口 / RecordWriter 线协议 / 模块边界零变更。
  - **注册表 + pin**：PASS——注册表 RWO/BRWO `classification: fail-fast`（disposition 注明落地日期/commit `88bc0270c`）；`TimestampedCollector` disposition 不再声称「cross-task drop」（透传目标敏感 = fail-fast）；`mjs-pins.json` `pinnedViolations: []` + PIN REMOVAL TRACE（非静默移除）；注册表行号同步 live（649/717）。
  - **测试**：PASS——`TestOutputContractInvariant` switch `fail-fast` case → `assertFailFastBehavior`（错误码 + `ARG_OUTPUT_TAG`/`ARG_DETAIL` 断言）；`testTimestampedCollectorWrappingRecordWriterOutputFailsFast` 期望 `ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER`；E2E 4 用例，第 4 用例构造 1 writer（RWO）+ 2 writers（BRWO）经 fanOutWriters 尾接线 + 实例类型断言 + 真实 late-data 发射抛错。
  - **Anti-Hollow 检查**：PASS——调用链 live 追踪：`WindowOperator.sideOutput`（:1030 `output.collect(lateDataOutputTag, element)`）→ RWO/BRWO（`wireOperators(List)` :243/:249 注入）→ throw；E2E 由审计 agent 独立复跑两次（standalone 4/4 + `-am` 4/4）＝运行时调用证明（非类型系统存在）；修复后两方法无空体/静默跳过作为正常实现；project-local-repo jar（22:05）bytecode 含 throw（javap 验证），测试类路径解析的是修复后 jar。
  - **门禁**：PASS——mjs `all` exit 0（pins 0 / 无 unpinned / 无 stale）；doc-links exit 0（3 条 BROKEN_LINK = credential plan 既有基线）；scan-hollow findings 集与基线 commit `34aed42c1` 完全一致（既有 14 项 high：11× UOE + 3× not-yet-implemented，**无新增 finding**；工具 exit 1 = 既有基线 high findings 的预期退出码，工具无豁免机制，判据 = findings 集一致而非退出码，沿用 I1/I2 判据）；JUnit 门禁子集 fresh run 102/102 绿（core 40 / runtime 34 / cep 28，TestOutputContractInvariant 10/10）；E2E 4/4（两轮）；全量回归 = core **1428** / runtime **805** 0 failures。
  - **文本一致性**：PASS——日志 08-12.md Phase 1/2/3 条目含具体命令与结果；roadmap C2/I4 行 `planned` → `done`（本 closure pass 执行）；red-list C2-RL-1/2 修复状态回写；core-design §6.1 fail-fast 落地 + `HG-01` 待办。
  - **Deferred 分类检查**：PASS——Deferred 区仅 `HG-01`（人工确认门）+ I3 结构性重构 watch 项；C2-RL-1/2 已在 plan 内执行（未降级）；Follow-up 诚实（I5 / E2E 扩展 / `HG-01`）。
  - **工具**：`node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` exit 0（Passed: 1，Closure Evidence 写入后复跑）；`node ai-dev/tools/check-doc-links.mjs --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0（findings 集与 I1 基线 `34aed42c1` 一致 = 14 项 high）。
  - **非阻塞观察（已处理）**：注册表 `collectOutputTagLine` 为 pre-fix 行号 → 已同步 live（649/717）；~/.m2 旧 jar 与 project-local-repo 新 jar 差异 → 测试解析路径为 project-local-repo（post-fix），无 stale 陷阱。

Follow-up:

- Cycle 2 / I5（全量验证与门禁零命中）= 直接 successor，消费本 plan 修复结果 + 门禁复跑基线。
- `TestSideOutputChainingE2E` 多输出路径扩展（ctx.output / ProcessWindowFunction / PatternProcessFunction E2E 用例）由 I2 探查评估结论 + I5 视情况覆盖（backlog 条目在案）。
- `HG-01` 跨 task side-output 线协议支持 = 人工确认门待办延续（人工批准后另立 plan）。
- no remaining plan-owned work。

## Optional Sections

## Outdated Note

无（I3 为唯一前置，未失效）
