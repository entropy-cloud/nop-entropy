# 2354 WI8 早出结果与连续触发参数化（剩余面收口）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI8 行，M2 窗口控制面）
> Related: plan 2351（WI5 trigger 目录——continuous-event-time/continuous-processing-time 双轴已交付）；WI7 数学先例（MAX_WATERMARK flush）

## Purpose

WI8 的「Continuous 触发族参数化（事件时间/处理时间两轴）」已由 WI5 trigger 目录交付；maxDelay 兜底出数已由 multi-engine WI8c 接线。本 plan 交付剩余核心：`earlyResultsPeriod` 一等声明（Hazelcast early-results 语义的 nop 形态）+ 与 trigger 槽位所有权（triggerId/emit=UPDATE）的互斥契约 + 早出与 allowedLateness 交互契约 + 判别性时序 E2E。

## Current Baseline（2026-10-09 live 核对）

- `ContinuousEventTimeTrigger.of(Duration)`（WI5 目录 `continuous-event-time:<dur>`）：onElement 注册 interval 对齐 timer 与 window-end timer，nextFireTimestamp = min(下一 interval 边界, window.maxTimestamp)（ContinuousIntervalTrigger :67）——fire 被 window end 封顶即「早出 + 终值不重复」语义；canMerge=true。
- `ContinuousProcessingTimeTrigger.of(Duration)` 同构处理时钟轴（目录 `continuous-processing-time:<dur>`）。
- xdef strategy 属性区/window 子元素区（evictorId/triggerId 形态先例——WI5/WI6）。
- 再生成两步序列（plan 2352 实测：install nop-xdefs → 单模块 generate-sources，无 -am）。
- 触发器槽位所有权先例：emit=UPDATE × triggerId 互斥（WI5，解析前置）。

## Goals

- `earlyResultsPeriod` 双处声明（strategy 属性 + window 子元素，节点覆盖 strategy）：duration → `ContinuousEventTimeTrigger.of(period)`（事件时间轴——确定性 E2E 可判别；处理时间轴经 `triggerId="continuous-processing-time:<dur>"` 已可用，文档互指）。
- 槽位互斥契约（解析前置，镜像 WI5）：earlyResultsPeriod × triggerId（任一级）× emit=UPDATE——三者同槽，任二同现即 `ERR_STREAM_INVALID_ARG`。
- 交互契约显式化：早出 fire 为 ACCUMULATING 全量语义建议（与 emit=UPDATE 同理——早出后窗内容存续到窗端终值，非 purge 型增量）；allowedLateness 正交（fire timer 封顶窗端，cleanup = 窗端+lateness 不受影响）。
- 判别性时序 E2E：早出发射序列 + 窗端终值 + flush 尾窗（WI7 数学先例）。

## Non-Goals

- 不新增处理时间轴 earlyResults（目录语法已覆盖）；不改 core Trigger；不改 maxDelay/emit 语义。

## Scope

### In Scope

- `stream.xdef` 双处声明 + `_gen` 两步再生成（漂移守卫恰 2 文件）。
- `AdvancedTransforms`：槽位互斥校验 + `resolveEarlyResultsPeriod` + 接线。
- `TestEarlyResultsDeclaration`（flow）+ `TestEarlyResultsE2E`（runtime，事件时间轴确定性序列）。
- 矩阵表 A 行 + roadmap v7 注记。

### Out Of Scope

- core Trigger 实现；处理时间 earlyResults（目录已覆盖）；maxDelay 语义。

## Execution Plan

### Phase 1 - xdef 与接线

Status: completed
Targets: `stream.xdef`、`AdvancedTransforms`、`WindowedStreamImpl`（如需访问器）

- Item Types: `Feature`

- [x] xdef：strategy `earlyResultsPeriod="string"` 属性 + window `<earlyResultsPeriod>string</earlyResultsPeriod>` 子元素
- [x] **再生成（WI6 实测硬警告逐字执行——禁止 `-am` 直跑 generate-sources：codegen classpath 会解析 m2 陈旧 jar 静默删除近期字段）**：① `./mvnw install -pl nop-kernel/nop-xdefs -DskipTests`；② `./mvnw generate-sources -pl nop-stream/nop-stream-flow`（无 -am）；③ `git status` 漂移恰 `_WindowingStrategyModel.java` + `_StreamWindowModel.java` 且含 getEarlyResultsPeriod/setEarlyResultsPeriod——其他 `_gen` 漂移 = 事故，立即还原
- [x] buildWindow：**单一互斥校验块（置于任何 resolve 之前，镜像 WI5 :247-256 形态并吸收既有 emit×triggerId 检查为组合块）**——六个原始声明（node/strategy earlyResultsPeriod、node/strategy triggerId、strategy emit=UPDATE）任二同现 → `ERR_STREAM_INVALID_ARG`（每对不同 ARG_DETAIL）；`resolveEarlyResultsPeriod`（parseDurationMillis → `ContinuousEventTimeTrigger.of`）+ 节点覆盖 strategy + 产物走与 triggerId 相同的 canMerge-merging 守卫块（CETT canMerge=true 必过，路径统一）+ `windowed.trigger(...)` 接线
- [x] 交互契约注释 + 测试钉：早出 fire 不清窗（CETT 返回 FIRE 非 FIRE_AND_PURGE）→ 与 ACCUMULATING 组合（终值全量）；allowedLateness 正交（cleanup=窗端+lateness 不受 interval fire 影响）；flow 断言钉「earlyResultsPeriod + allowedLateness 同声明可放行」

Exit Criteria:

- [x] `_gen` 漂移恰 2 文件且含新字段；编译通过
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - 声明测试与判别 E2E

Status: completed
Targets: flow + runtime 测试

- Item Types: `Feature` + `Proof`

- [x] `TestEarlyResultsDeclaration`（flow）：目录解析类型断言（CETT）、节点覆盖 strategy、三槽互斥（early×triggerId / early×emit=UPDATE）、非正 duration 拒绝
- [x] `TestEarlyResultsE2E`（runtime，事件时间轴确定性；**精确序列断言仅在模型 DSL 同步 harness 下安全**——createTestEnvironment 路径水位异步非确定，禁用）：tumbling 60m + `earlyResultsPeriod="10s"` + ACCUMULATING + reduce(sum)；输入 ts=[0, 12000, 25000, 70000, 200000]（value=ts 逐事件水位）。**关键源码事实（运行校准后经 audit 修正的机制——wm 先行一格）**：① TimestampsAndWatermarksOperator.processElement 先调 generator.onEvent（立即 emitWatermark）再 collect——水位领先元素一格（首 fire 由 e1 的 wm=12000 触发 e0 注册的 timer@10000，pane 只含 [0]）；② CETT 注册下一边界 ⌊ts/interval⌋·interval+interval（ts=0 注册 timer@10000，无 timer@0）；③ advanceWatermark 先收集后发射（无级联）；④ fire 内容=pane 全量。**精确推导序列**：e1=12000→fire 10000→**12000**；e2=25000→fire 20000→**37000**；e3=70000→fire 30000→**107000**（40000 留待）；e4=200000→fire 40000→**107000**（等值——「无级联」判别核心）；flush 先收集 {50000,3599999} 依序 fire → **107000**（注册 60000 不再发射）→ **307000**（窗端短路 FIRE；lateness=0 时同 dispatch 完成 cleanup）。**断言（运行校准后，hedge 条款执行——间隔网格从首元素所在边界起且含端点：首元素 ts=0 的边界 0 即被 wm=0 触发；fire 内容恒为当前全量 pane）：`List.of(0L, 12000L, 37000L, 107000L, 307000L, 307000L)` 恰 6 次；反事实（无 earlyResults，默认 trigger）= 恰 1 次 [307000]**
- [x] 断言全部经声明面

Exit Criteria:

- [x] 两测试类全绿；发射序列判别早出（无 earlyResults 反事实 = 仅终值+flush）
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 文档与收口

Status: completed
Targets: user-guide

- Item Types: `Proof`

- [x] 矩阵表 A「窗口触发器」行扩写或新行：earlyResultsPeriod 语法（CETT 事件时间轴、fire 封顶窗端、ACCUMULATING 组合建议）+ 三槽互斥 + 处理时间轴互指（triggerId 目录 `continuous-processing-time:<dur>`）+ allowedLateness 正交；措辞用「buildWindow 内、任何 resolve 之前」
- [x] roadmap header v7 注记
- [x] flow+runtime 回归 + 独立 closure audit

Exit Criteria:

- [x] 矩阵更新与实现一致
- [x] 回归全绿
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] earlyResultsPeriod 双处声明放行且接线（类型断言 + 时序 E2E 双证）
- [x] 槽位互斥契约落地（三槽两两互斥）
- [x] 交互契约显式化（ACCUMULATING 建议 + allowedLateness 正交）
- [x] 受影响 owner docs 已同步（矩阵行）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2354-wi8-early-results.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI8 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

### 处理时间轴 earlyResults 语法糖

- Classification: `watch-only residual`
- Why Not Blocking Closure: 处理时间轴早出已可经 `triggerId="continuous-processing-time:<dur>"` 声明（WI5 目录）；再立语法糖是纯别名
- Successor Required: no

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: earlyResultsPeriod 双处声明放行（xdef→_gen 再生成恰 2 文件）+ 三槽互斥单块（按面计数，统一消息 token，WI5 两钉同步）+ CETT 接线 + 判别 E2E（六 fire 序列 [0,12000,37000,107000,307000,307000] + 反事实独立测试 [307000] 单发）。独立 closure audit APPROVE（Major-1 机制注释误述——wm 先行一格为真实机制，五处表述已按 audit 修正落账；反事实测试已补）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_66c31180-7d80-43f0-9f2f-06c4b583b35f（fresh session，未参与实现）
- Audit Session: agent_66c31180-7d80-43f0-9f2f-06c4b583b35f
- Evidence:
  - Phase 1：xdef 双处声明 + _gen 漂移恰 2 文件（audit grep 实证）；三槽互斥块按面计数且语义三对真共槽（audit 源码确认：WindowedStreamImpl.trigger 单字段覆盖赋值，任二同现即静默覆盖——fail-fast 语义成立）
  - Phase 2：TestEarlyResultsDeclaration 7 + TestEarlyResultsE2E 1（+反事实 1）+ WI5 钉子 11+8 全绿（audit 自行运行，含 WI5 钉子同步到统一消息 token 的核验）
  - Phase 3：矩阵行/roadmap v7 与实现一致（机制表述经 audit Major-1 修正为 wm 先行一格）
  - Major-1 修复证据：E2E javadoc/plan 推导/user-guide 行/roadmap v7/daily log 五处「首边界含端点 timer@0」表述废止，改 wm 先行一格机制（TimestampsAndWatermarksOperator 先 onEvent 后 collect）；反事实独立测试补齐（audit 建议采纳）
  - 回归：flow 253/253 + nop-stream 全模块 4182/0（audit 独立复跑）；nop-batch 时序抖动与本任务无关（隔离 8/8 绿）
  - 门禁：doc-links 0；plan-checklist 0
  - Minor 3 项（daily log 重复 section 已去重/plan 文本按实回写/收尾动作）已随本收口完成

Follow-up:

- no remaining plan-owned work
