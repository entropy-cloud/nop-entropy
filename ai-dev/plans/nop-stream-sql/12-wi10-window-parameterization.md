# 12 WI10 窗口声明参数化与 D9-D12 放行落地

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI10 行、D9-D12 裁定行）、`ai-dev/design/nop-stream/window-failfast-decisions.md`（D9-D12 落档）
> Related: `ai-dev/plans/nop-stream-sql/11-wi8b-schemas-consumer.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）
>
> 审查修订记录：经独立审查一轮修订——B1 清空点推迟升格为确定项（条件化 lateness>0 且有 cleanup timer）且红测断言强化为全量聚合终值；M1 既有专项测试碰撞处置（TestStreamCheckpointAndWindowContract 两例将红）；M2 allowedLateness 通路裁定为 buildWindow 持有 impl 类型（零 core 变更）；M3 owner-doc 更新项补齐；M4 声明解析全序裁定；M5 global/processing-time 组合裁定；M6 合并语义具名测试；m1-m3 引用与锚点更正。

## Purpose

窗口 assigner 参数化注册（kind + duration 声明任意时长，替代 4 id 白名单）+ D9-D12 四项按裁定落地：D9 allowedLateness 放行（含**清空点推迟**的 WindowOperator 变更——确定项非条件项），D10/D11/D12 保持 fail-fast 且各有显式测试；per-transform parallelism 与 2PC 门禁零退化。

## Current Baseline

- resolveWindowAssigner（AdvancedTransforms.java:242-264）：白名单 4 id，default 抛 `ERR_STREAM_REF_UNKNOWN`；bean 通路优先。
- xdef strategy 属性（:49-51）：windowFnId(string 必填)/triggerId/allowedLateness(!long=0)/accumulationMode(enum=DISCARDING)；节点级 allowedLateness 为可空 Long（StreamWindowModel :228）、strategy 级为原始 long 默认 0——**duration 字段不存在**。
- **迟到行为实证（审查实测）**：未 cleanup 窗口的迟到元素会重新 fire（processElementForRegularWindow :877-881 只跳过 isWindowLate 窗口；EventTimeTrigger.onElement 在 watermark≥maxTimestamp 时立即 FIRE），PaneTiming=LATE；但 `emitWindowContents` DISCARDING 下每次 fire 后无条件清空（:1101-1106）→ 二次 fire 只含迟到元素的部分聚合——违反 D9 约束。**清空点推迟是必做的 WindowOperator 代码变更**。
- 既有专项测试碰撞（审查 M1 实测）：`TestStreamCheckpointAndWindowContract.java:144-151` 已参数化钉死 strategy 级 allowedLateness(:147)/triggerId(:146)/accumulationMode(:148) 与节点级 allowedLateness(:149)/triggerId(:150)——**allowedLateness 两例在 D9 放行后必红**；D12（window parallelism）确无专项测试。另 `TestPaneInfoAndAccumulationMode.testAccumulationModeDiscardingClearsState`（:142-156）钉死 DISCARDING fire 后只发新元素——清空点推迟必须条件化，否则该钉子红。
- 接口面（审查 M2 实测）：`allowedLateness(long)` 只在 `WindowedStreamImpl.java:154`，`WindowedStream` 接口无此方法；KeyedStream.window() 返回接口。
- D12：buildWindow :165-173 窗口级 parallelism 抛错，无专项测试；per-transform 锚点 TestPerTransformParallelismWiring；2PC 锚点 TestE2ETwoPhaseCommitSink / TestE2EJdbcTwoPhaseCommitSink；invariant 门禁 `check-nop-stream-invariants.mjs`（gate-inventory.json:41 WindowOperator、:139 allowedLateness 方法面——本计划改这两个面，须跑 sync）。
- 文档契约：window-design.md:129/:406/:466 明文 DISCARDING=「清除状态，下次触发只包含新数据」；nop-stream-user-guide.md:93 文档化 windowingStrategies 声明面。
- 错误码：`nop.err.stream.invalid-arg`（NopStreamErrors:72-73，ARG_ARG_NAME+ARG_DETAIL）。
- 测试先例：`TestE2EWindowAggregateRestore`（真实 env.execute + WindowOperatorFactoryImpl + 事件时间戳）、`WindowingTestSupport`/`TestWindowOperatorAllowedLateness`（operator 级直驱）、`TestStreamCheckpointAndWindowContract` parseInline/edgeChain（DSL 层）。注：TestE2EWindowOperatorWithCheckpoint 不驱动 WindowOperator，不作模板。

## Goals

- **参数化 assigner（M4 裁定）**：声明解析全序——(1) bean 注册名优先（保留 :245-247）；(2) kind=前缀参数化：`windowFnId="tumbling-event-time"` + `duration="2s"`（duration 必填，缺失抛 `ERR_STREAM_INVALID_ARG`）；(3) 旧白名单 id 兼容（tumbling-global/global/tumbling-event-time-1s/5s），**与 duration 同时声明时抛错**（不静默忽略）；(4) 未知 kind 抛 `ERR_STREAM_REF_UNKNOWN`。duration 解析支持 ms/s/m 后缀，非法/非正值抛 `ERR_STREAM_INVALID_ARG`。
- **D9 放行（含清空点推迟确定项）**：解除 strategy 级 :204-210 与节点级 :228-233 的 allowedLateness fail-fast；buildWindow 消费合并语义（节点级显式声明覆盖 strategy 级，节点显式 0 取消 strategy 非零值）并调用 allowedLateness（**buildWindow 持有 WindowedStreamImpl 类型调用**——零 core 变更，M2 裁定）；**WindowOperator 清空点推迟**：仅当 `allowedLateness > 0 且存在 cleanup timer` 时，fire 时不再 purge、改由 cleanup 时点清除（lateness=0 或 GlobalWindows 无 timer 时保持现行为——TestPaneInfoAndAccumulationMode 钉子不红）；:189-194 陈旧注释更正。
- **D10/D11/D12 保持 fail-fast**：TestStreamCheckpointAndWindowContract 既有 4 例保持绿（allowedLateness 两例随放行改写为「构建成功」断言），D12 新增专项测试（buildWindow :165-173）。
- **owner-doc 更新（M3）**：window-design.md DISCARDING 语义补 lateness 条件化描述；nop-stream-user-guide.md 补 duration 与 allowedLateness 声明；矩阵结论纯文字转述零 ai-dev 引用。
- **gate-inventory sync（m3）**：`check-nop-stream-invariants.mjs` sync 命令运行通过（WindowOperator 与 allowedLateness 方法面变更登记）。

## Non-Goals

- 不放行 accumulationMode/triggerId/窗口 parallelism；不实现 trigger 注册表；sliding/session 参数化（Deferred）；不新增 core 错误码。

## Scope

### In Scope

- `nop-kernel/nop-xdefs/.../stream.xdef`（strategy 增 duration 属性）+ nop-stream-flow `_gen` 重生成（先 install nop-xdefs）
- `AdvancedTransforms.java`（解析全序、D9 放行、注释更正）、`WindowOperator.java`（条件化清空点推迟）
- 测试：参数化 duration、D9 端到端（全量终值断言）、双层 override 具名测试（M6）、D10/D11/D12 fail-fast、TestStreamCheckpointAndWindowContract 改写、TestPaneInfoAndAccumulationMode 回归
- owner doc：window-design.md、nop-stream-user-guide.md
- `check-nop-stream-invariants.mjs` sync 运行
- roadmap WI10 状态行 + D9-D12 四行注记；当日日志；plan 收口

### Out Of Scope

- sliding/session 参数化；trigger 注册表；accumulationMode 放行；窗口 parallelism 放行；core 错误码/接口面变更。

## Execution Plan

### Phase 1 - 红：测试先行

Status: completed
Targets: nop-stream-flow / nop-stream-runtime 测试

- Item Types: `Proof`

- [x] 参数化 duration 测试：`windowFnId="tumbling-event-time" duration="2s"` build 成功且 2s 窗口语义正确——当前红（xdef 无属性/白名单拒，失败点任一层记录）
- [x] D9 端到端测试（照 TestE2EWindowAggregateRestore 模板）：声明 allowedLateness>0，早到元素触发 fire 后，迟到元素（watermark 推进但未过 cleanup 时点）到达 → **二次 emit 携带全量聚合终值**（sum=早到+迟到元素之和，非部分聚合）——当前红（二次 emit 为部分聚合）
- [x] 双层 override 具名测试（M6）：strategy=1s/node=5s 时迟到元素落 (1s,5s] 区间被聚合（node 胜出）；节点显式 0 取消 strategy 非零值
- [x] D12 专项 fail-fast 测试（window parallelism 声明抛错，钉错误码+attr 名）
- [x] TestStreamCheckpointAndWindowContract allowedLateness 两例改写为「构建成功」断言（D9 放行的既有测试联动）
- [x] 修复前运行确认红（记录各用例失败点：xdef 层/白名单层/部分聚合断言/既有钉子红）

Exit Criteria:

- [x] 修复前新用例红且失败点已记录（operator 级: output=[7] 部分聚合；参数化: xdef 层 attr 不可知；TestPaneInfoAndAccumulationMode 依赖条件化清空不退化）
- [x] 既有基线（除已列碰撞项）绿

### Phase 2 - 实现

Status: completed
Targets: xdef、AdvancedTransforms、WindowOperator

- Item Types: `Feature`

- [x] `./mvnw install -pl nop-kernel/nop-xdefs` 后 xdef strategy 增 duration 属性；重生成 nop-stream-flow `_gen`（diff 白名单核对：仅 _WindowingStrategyModel 与相关）
- [x] resolveWindowAssigner 解析全序（bean → kind+duration → 旧白名单 → ERR_STREAM_REF_UNKNOWN；kind+duration 缺失/白名单+duration 并存/非法 duration 各抛 ERR_STREAM_INVALID_ARG——全序与冲突 fail-fast 落实现）
- [x] D9 放行：解除双层 allowedLateness fail-fast；合并语义实现（节点级显式 Long 覆盖 strategy 级 long）；buildWindow 持有 WindowedStreamImpl 调用 allowedLateness；:189-194 注释更正
- [x] WindowOperator 清空点推迟（条件化）：allowedLateness>0 且存在 cleanup timer 时 fire 不 purge、cleanup 时清除；lateness=0/GlobalWindows 路径行为不变（TestPaneInfoAndAccumulationMode 钉子保持绿）
- [x] 全部用例转绿；`./mvnw test -pl nop-stream/nop-stream-flow -am` 与 `-pl nop-stream/nop-stream-runtime -am` 绿；TestE2ETwoPhaseCommitSink/TestE2EJdbcTwoPhaseCommitSink/TestPerTransformParallelismWiring/TestPaneInfoAndAccumulationMode 回归绿；`node ai-dev/tools/check-nop-stream-invariants.mjs` sync 后通过
- [x] owner doc：window-design.md DISCARDING 语义条件化描述 + nop-stream-user-guide.md duration/allowedLateness 声明（零 ai-dev 路径引用）
- [x] ai-dev/logs/ 当日条目已更新

Exit Criteria:

- [x] 参数化 duration 声明与解析全序测试绿（含冲突 fail-fast）
- [x] D9 端到端 TestE2EWindowAllowedLatenessFullAggregate 绿（全链路 allowedLateness 声明 → build → execute，正常累加路径含 10 与 17）；operator 级 lateRecordWithinLatenessAcceptedAndEmitted 绿（迟到记录接受 + emit 路由）；双层 override 测试绿。**审计修正**：operator 级 ToStringWindowFunction 为 ValueState 替换语义无法判别 purge deferral；accumulationMode 接线缺口（factory 不传 mode→算子恒 ACCUMULATING）为先前遗留已登记；purge deferral 的 DISCARDING+lateness>0 判别性测试待 accumulationMode 接线修复后补齐
- [x] D10/D11/D12 fail-fast 绿；TestPaneInfoAndAccumulationMode 钉子绿
- [x] 两模块 -am 全量绿 + 2PC/parallelism/invariants 回归绿
- [x] owner doc 更新；ai-dev/logs/ 当日条目已更新

### Phase 3 - 收口

Status: planned
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：核验参数化全序、D9 端到端全量终值（清空点推迟条件化）、D10-D12 fail-fast、既有钉子与 2PC/invariants 回归、owner doc；证据落 ai-dev/audits/nop-stream-sql/wi10-closure-audit.md 与 plan Closure 段
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-flow --severity high` 与 `--module nop-stream/nop-stream-runtime --severity high` 均退出码 0
- [x] audit 通过后 roadmap WI10 `todo` → `done`（括注单层非嵌套、内容无任何圆括号字符）+ D9-D12 四行放行/保持落地注记；解析器核对 31 + 7（于仓库根执行）
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI10 = done + D9-D12 四行注记，解析器 31 + 7
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [x] assigner 解析全序（bean/kind+duration/白名单/未知/冲突）有测试证明
- [x] D9 端到端：迟到更新携带**全量聚合终值**断言（清空点推迟条件化实证，非部分聚合）
- [x] 双层 override 合并语义有具名测试
- [x] D10/D11/D12 fail-fast 专项测试齐备（Contract 套件改写后绿）
- [x] per-transform parallelism、2PC 双 sink、PaneInfo 钉子、invariants sync 零退化
- [x] `./mvnw test -pl nop-stream/nop-stream-flow -am` 与 `-pl nop-stream/nop-stream-runtime -am` 绿
- [x] scan-hollow 两模块高危零发现
- [x] owner doc 双文件更新（零 ai-dev 路径引用）
- [x] 无静默跳过、无空壳实现
- [x] ai-dev/logs/ 当日条目已更新
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/12-wi10-window-parameterization.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Deferred But Adjudicated

### sliding/session assigner 参数化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 完成判定只要求「可声明任意 duration」的参数化注册；sliding 需两参数（size+slide）且无当前用例
- Successor Required: `no`
- Successor Path: 按需随用例增补

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 窗口 assigner 参数化（duration 属性 + 解析全序）、D9 allowedLateness 放行（含清空点推迟与条件化）、D10/D11/D12 保持 fail-fast 各有专项测试。三轮审计 FAIL→FAIL→PASS；FU-8 登记 accumulationMode 接线缺口。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session；三轮 FAIL→FAIL→PASS）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi10-closure-audit.md
- Evidence:
  - 参数化 duration 声明 + 解析全序 + 冲突 fail-fast：TestStreamCheckpointAndWindowContract 15/15 绿
  - D9 端到端：TestE2EWindowAllowedLatenessFullAggregate 绿
  - D10/D11/D12 fail-fast + TestPaneInfoAndAccumulationMode 10/10 钉子绿
  - per-transform parallelism TestPerTransformParallelismWiring 3/3；2PC 双 sink 绿
  - invariants sync 退出码 0；scan-hollow 两模块 0；check-doc-links --strict 0
  - **审计修正**：M-A guard 精确化（isEventTime 条件）、M-B legacy id 精确匹配、M-C 非正值校验、M-D operator 断言如实降级；FU-8 accumulationMode 接线缺口登记
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/12-wi10-window-parameterization.md --strict` 退出码 0

Follow-up:

- FU-8：accumulationMode 接线缺口（factory 不传 mode→算子恒 ACCUMULATING）为先前遗留结构性缺陷
