# 03 WI38 FU-8 accumulationMode 接线修复（含 D10 开门）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`（WI38 行、FU-8 继承自 `ai-dev/backlog/nop-stream-sql-roadmap.md` Follow-up）；`ai-dev/design/nop-stream/window-failfast-decisions.md` §2（D10 放行裁定，生效前提=本 plan done）
> Related: ai-dev/plans/nop-stream-sql/12-wi10-window-parameterization.md（D9/D10 首版实施）；ai-dev/plans/nop-stream-sql-enhancement/04-wi37-emit-two-tier.md（emit 两档实施，未来交付物——依赖本 plan 的 ACCUMULATING 接线）
> Owner: 仓库 owner（委托链：2026-10-08 执行指令「在 nop-entropy-stream-roadmap worktree 中执行 nop-stream-sql-enhancement-roadmap.md 直到彻底完成，每工作项按 plan guide 拟制计划，每完成一个计划提交一次」）

## Purpose

修复 FU-8 结构缺陷：xdef 声明 `accumulationMode`（含默认值 DISCARDING）从未被传递到 WindowOperator——模型构建出的算子恒为 ACCUMULATING（`WindowOperator.java:492` null 缺省），声明语义与运行时行为背离。修复接线后，按 WI0a D10 裁定放开 build 期门（放行 ACCUMULATING 声明，A&R 维持拒绝），使声明值真实生效；本 plan done 即 D10 放行的生效前提达成。

## Current Baseline

2026-10-08 实测（HEAD `55aa962595`，行号为当前口径）：

- **缺口链路五环**（`buildWindowOperator` 实际 :221-245，`new WindowOperator(` :227、第 14 参 accumulationMode :241——R1 修正）：
  1. `WindowingStrategyModel.getAccumulationMode()`（xdef 默认 DISCARDING）——`AdvancedTransforms.buildWindow`（:156-222）全程不读该值；唯一消费点是 `failFastOnUnsupportedWindowStrategy`（:231-249）的拒绝门 :241-248（拒绝一切非 DISCARDING）。
  2. `WindowedStreamImpl`（nop-stream-core datastream）无 accumulationMode 字段（字段全集 :53-84：keyedStream/windowAssigner/trigger/evictor/allowedLateness/lateDataOutputTag/maxDelay/emitWindowEvents/windowEventsOutputTag/windowingStrategyId/components）。
  3. `IWindowOperatorFactory`（nop-stream-core operators，:22-246）三代 create* 重载（基础 8 参→G-2+09e① tag 感知 default 委托→WI8 maxDelay+events default 委托）均无 accumulationMode 参数；default 方法逐代向下委托是既有扩展模式。
  4. `WindowOperatorFactoryImpl.configureBuilder`（nop-stream-runtime，:350-378）从不调用 `builder.accumulationMode(...)`。
  5. 管道尾段已通：`WindowOperatorBuilder.accumulationMode(...)`（:110-113，无条件赋值，null 直通）→ `buildWindowOperator`（:221-245）第 14 参传 `WindowOperator` → 字段 `protected final AccumulationMode accumulationMode`（:198）→ null 缺省 ACCUMULATING（:492）。
- **两层缺省相反**：core `WindowingStrategy.java` :52/:60 缺省 DISCARDING（模型层语义）vs `WindowOperator.java:492` 缺省 ACCUMULATING（算子层现状）——声明 DISCARDING 的模型实际得到 ACCUMULATING 算子。
- **运行时门**：`WindowOperator.open()` :590-596 对 `ACCUMULATING_AND_RETRACTING` fail-fast（spec-only）；DISCARDING fire 后清空 :1388-1400（`skipPurgeForLateness = allowedLateness>0 && isEventTime()` 时跳过——D9 语义，不改）。
- **既有钉码（须随裁定更新）**：`nop-stream-flow/src/test/java/io/nop/stream/flow/builder/TestStreamCheckpointAndWindowContract.java:281` 以 `Arguments.of("accumulationMode=\"ACCUMULATING\"", ...)` 钉「ACCUMULATING 声明构建期失败」——D10 放行后该钉码须改为仅钉 A&R。
- **既有运行时行为测试**：`TestPaneInfoAndAccumulationMode`（runtime，同包直构算子）已覆盖 ACCUMULATING/DISCARDING/A&R 算子级行为（G48）；`WindowRoundTripAssertions`/`TestWindowRoundTripInvariant` 钉 factory call-site 实参传输完备性——接口加参后须同步。
- **D10 裁定**（window-failfast-decisions §2，2026-10-08）：放行 ACCUMULATING 声明，生效前提=本 plan done；边界三条：A&R spec-only 门（:590-596）原样、skipPurgeForLateness（:1388-1400）不变、放行时同步放开 build 期 strategy 级门（唯一 build 门，window 节点无该属性）。
- **单 fire 窗口的可观察性**：模型路径窗口由 EventTimeTrigger 在窗口末单一 fire；DISCARDING purge 与否在无 lateness 时不产生可观察差异（fire 后窗口随即清理）——接线修复对既有 SQL/DSL E2E 应零行为变化（`TestTumbleAggregateQueryE2E` 等作为回归信号）。

## Goals

- 声明值全程接线：`WindowingStrategyModel.getAccumulationMode()` → `AdvancedTransforms.buildWindow` → `WindowedStreamImpl.accumulationMode(...)` → `IWindowOperatorFactory` 新代 create* → `WindowOperatorFactoryImpl.configureBuilder` → `WindowOperatorBuilder.accumulationMode` → `WindowOperator`。null 语义分两层：Java API 路径不调用 fluent setter → 字段 null → 算子缺省 ACCUMULATING（:492 既有行为不变，纯 Java API 用户零感知）；模型路径 `buildWindow` 消费点将 `strategy.getAccumulationMode()` 的 null **归一为 DISCARDING**（把 xdef 声明缺省在消费点显式化——`DslBeanModelParser.parseObject` 对缺失属性回填 xdef 默认值，但 `_gen/_WindowingStrategyModel` 字段无初始化器，程序化构造的模型可能为 null，消费点归一使声明缺省不依赖 parser 行为）。
- 按 D10 开门：build 期 :241-248 改为仅拒绝 `ACCUMULATING_AND_RETRACTING`（错误文案同步「Use DISCARDING or ACCUMULATING instead」口径）；A&R 由 build 门 + 运行时 :590-596 双层维持拒绝。
- 接口扩展遵循仓库既有 default 委托代际模式（第四代：WI8 感知变体 + accumulationMode 尾参，default 向下委托）。
- 判别性测试：声明 DISCARDING 的模型构建出的算子 accumulationMode==DISCARDING、声明 ACCUMULATING 构建通过且算子==ACCUMULATING、A&R 构建期仍 fail-fast、Java API null 路径算子==ACCUMULATING（legacy 不变）。

## Non-Goals

- 不实施 emit 两档（WI37 承接——本 plan 只打通 accumulationMode 通道）。
- 不改 triggerId 双层门（D11 保留 fail-fast）；不改 D9 allowedLateness 通路与 skipPurgeForLateness。
- 不改 xdef（accumulationMode 属性已存在，默认 DISCARDING 不变）；不改 WindowOperator 运行时语义（:590-596、:1388-1400 原样）。
- 不改 core `WindowingStrategy.java` 缺省（DISCARDING 语义锚点不动）。

## Scope

### In Scope

- `nop-stream/nop-stream-core`：`IWindowOperatorFactory` 第四代 default 重载（4 方法）；`WindowedStreamImpl` 字段+fluent setter/getter+4 个 call site。
- `nop-stream/nop-stream-runtime`：`WindowOperatorFactoryImpl` 覆写第四代 4 方法；`configureBuilder` 增参并接线 `builder.accumulationMode(...)`。
- `nop-stream/nop-stream-flow`：`AdvancedTransforms` 门改写（:241-248 仅拒 A&R）+ `buildWindow` 消费 `strategy.getAccumulationMode()`。
- 测试：`TestStreamCheckpointAndWindowContract:281` 钉码更新（A&R）；flow 新增声明接线测试；runtime 新增 factory→operator 传递测试；既有全量回归。
- 文档：`ai-dev/design/nop-stream/window-failfast-decisions.md` §2 增「生效前提达成（WI38 done）」注记；`ai-dev/design/nop-stream/sql-subset-and-semantics.md` §4b 窗口声明行补 D10 接线注记；`docs-for-ai/03-modules/nop-stream-sql.md` :52「accumulationMode/triggerId/窗口级 parallelism 仍保持 fail-fast」行更正（实测唯一携带该表述的用户文档，R1-m5）。
- roadmap：WI38 状态行；当日 `ai-dev/logs/2026/10-08.md`。

### Out Of Scope

- emit 属性/xdef 变更（WI37）；`<sql>` 声明面变化；multi-engine roadmap 文本；状态后端/checkpoint 协议。

## Execution Plan

### Phase 1 - 接线修复 + 开门 + 判别测试

Status: completed
Targets: `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/operators/IWindowOperatorFactory.java`、`.../datastream/WindowedStreamImpl.java`、`nop-stream/nop-stream-runtime/src/main/java/io/nop/stream/runtime/operators/windowing/WindowOperatorFactoryImpl.java`、`nop-stream/nop-stream-flow/src/main/java/io/nop/stream/flow/builder/AdvancedTransforms.java`

- Item Types: `Fix`

- [x] `IWindowOperatorFactory`：新增第四代 4 个 default 方法（createAggregateOperator/createReduceOperator/createApplyOperator/createProcessOperator，WI8 感知签名 + 尾参 `AccumulationMode accumulationMode`），default 体向下委托 WI8 代（丢弃 mode，保持既有实现源兼容）；javadoc 记 FU-8/D10 语义
- [x] `WindowedStreamImpl`：增 `private AccumulationMode accumulationMode`（缺省 null=算子层 ACCUMULATING，Java API legacy 不变）+ fluent `accumulationMode(AccumulationMode)`（null 合法=显式回到缺省）+ getter；apply/aggregate/reduce/process 四 call site 改调第四代并传值
- [x] `WindowOperatorFactoryImpl`：覆写第四代 4 方法；`configureBuilder` 增 `AccumulationMode` 参数并无条件 `builder.accumulationMode(...)`（builder 无条件赋值、null 直通算子缺省——:110-113/:492 既有语义）
- [x] `AdvancedTransforms`：:241-248 门改为仅拒绝 `ACCUMULATING_AND_RETRACTING`（文案同步两可用档口径）；`buildWindow` 读 `strategy.getAccumulationMode()` 并**归一 null→DISCARDING** 后 `windowed.accumulationMode(mode)`；方法注释更新（WI10 时代注释「must fail fast on non-default values」修正）；**类级 javadoc :96-99**「triggerId/accumulationMode ... rejected at build time」同步改写（R1-m4）
- [x] flow 测试：`TestStreamCheckpointAndWindowContract:281` 钉码 `ACCUMULATING` → `ACCUMULATING_AND_RETRACTING`；新增声明接线测试 `TestWindowAccumulationModeDeclaration` 4 例——**钉环 1→2（strategy 模型 → WindowedStreamImpl 字段）**：断言 `windowed.getAccumulationMode()`（flow 测试类路径无 nop-stream-runtime，operator 字段断言归 runtime 层）；含**缺省档钉码**（strategy 不带 accumulationMode 属性 → DISCARDING，防 parser 回填行为回归）与显式 ACCUMULATING/DISCARDING 两例；A&R fail-fast 断言保留。（closure audit M-1 修订：五环分工证据为 flow 环1→2 + core 环2→3 + runtime 环3→5，每环均有直接钉码，闭环成立）
- [x] runtime 测试：新增 factory→operator 传递测试（同包直调 `WindowOperatorFactoryImpl` 第四代，**四个 create* 方法全部参数化覆盖**——apply/reduce/process 无模型宿主，只能在 runtime 层钉，防部分覆写漏检 R1-M1；断言 DISCARDING/ACCUMULATING/null→ACCUMULATING）；`WindowRoundTripAssertions` javadoc :30/:44-46（「8 实参」「14 参」陈旧表述）与 `TestWindowOperatorAllowedLateness.java:119-120`（「accumulationMode wiring gap is a pre-existing issue registered as FU」失效注释）同步更正（R1-m4）
- [x] 全量回归：`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow,nop-stream/nop-stream-sql -am` 全绿——core 138（0 skip）/ runtime 1336（10 gated skip）/ flow 103（14 gated skip）/ sql 91（0 skip），BUILD SUCCESS（_tmp/wi38-full-regression2.log）；gate-invariant 补登记 accumulationMode 后 TestInvariantTableCompleteness 11/11 绿

Exit Criteria:

- [x] 声明即所得：模型 strategy accumulationMode=DISCARDING → 算子 DISCARDING；=ACCUMULATING → 算子 ACCUMULATING；**缺省档（不带属性）→ 算子 DISCARDING**（三层分工证据：flow 层声明→WindowedStreamImpl 字段断言、core 层四 call-site 传输断言、runtime 层四方法×三模式算子字段反射断言——每环直接钉码，防漏检与缺省档回归）
- [x] **接线验证**（guide #23）：AdvancedTransforms→WindowedStreamImpl→factory→builder→WindowOperator 调用链运行时连通——五环逐环钉码（flow：registeredStream 断言 WindowedStreamImpl.getAccumulationMode=声明值；core：capturing fake 断言四 call-site 实参=字段值；runtime：真实 factory 产物反射断言 WindowOperator 字段=传入值），不止类型存在
- [x] **无静默跳过**（guide #24）：第四代 default 委托向下丢弃 mode 仅作为「未覆写的旧实现」兼容路径存在，运行时唯一生产实现 WindowOperatorFactoryImpl 必须覆写第四代（测试断言经真实 factory 的传递）；无空方法体/吞异常
- [x] A&R 双层拒绝保持：build 门（改后）+ 运行时 :590-596（不动）均有钉码
- [x] Java API null 路径行为不变（算子缺省 ACCUMULATING——core wiring 测试 undeclaredModeStaysNullAtCallSite + runtime null→ACCUMULATING 两例）
- [x] `./mvnw test -pl <四模块> -am` 全绿；既有钉码仅按裁定更新一处（:281）
- [x] **端到端验证**（guide #22）：既有 SQL/DSL E2E 套件（含 `TestTumbleAggregateQueryE2E`、`TestAdvancedPipelineE2E`）全绿——从模型/XML 入口到 sink 输出完整路径在修复后行为不变
- [x] owner doc 更新：window-failfast-decisions §2 生效注记、sql-subset §4b 窗口声明行、docs-for-ai 用户文档同步（nop-stream-sql.md）
- [x] ai-dev/logs/2026/10-08.md 已更新

### Phase 2 - roadmap 同步与收口

Status: completed
Targets: `ai-dev/backlog/nop-stream-sql-enhancement-roadmap.md`

- Item Types: `Proof`

- [x] 独立子 agent closure audit（与起草审查不同 task）：核验接线链路五环 live 代码、测试证据、D10 生效注记、零越权；证据落 ai-dev/audits/nop-stream-sql-enhancement/wi38-closure-audit.md 与本 plan Closure 段
- [x] audit 通过后 roadmap WI38 状态行 `todo` → `done`（括注单层、内无 `）` 与 `)` 字符——首版括注含嵌套全角括号被 BULLET_RE 静默丢弃致分母 17，即时修正并重跑解析器）；重跑解析器核对 items 18 milestones 4
- [x] plan Closure 段写入证据，Plan Status → `completed`，check-plan-checklist --strict 退出码 0，check-doc-links --strict 退出码 0，`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 退出码 0

Exit Criteria:

- [x] roadmap WI38 行同步，解析器实测 items 18 milestones 4 wi38 done
- [x] 独立 audit 证据已写入 plan Closure 段与 ai-dev/audits/nop-stream-sql-enhancement/wi38-closure-audit.md
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0；scan-hollow high 退出码 0
- [x] ai-dev/logs/2026/10-08.md 收口记录与 plan 状态一致

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（FU-8 五环接线闭合，声明即所得）
- [x] 行为/契约结果已达成：DISCARDING/ACCUMULATING 声明语义可验证；A&R 双层拒绝；Java API 零感知
- [x] 必要 focused verification 已完成（判别性测试四例 + 回归全绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline（裁定文档/设计文档/用户文档三处）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）调用链五环运行时连通（flow 层字段值断言 + runtime 层传递断言），（b）无空方法体/静默跳过（default 向下委托仅为接口兼容，生产实现覆写）
- [x] `./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-flow,nop-stream/nop-stream-sql -am` 全绿
- [x] checkstyle / 代码规范检查通过（import 分组等）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: FU-8 五环接线闭合（声明即所得：DISCARDING/ACCUMULATING 双档 + 缺省档归一），D10 放行生效（build 门仅拒 A&R），三层判别测试 21 例钉死每一环，四模块回归全绿（138/1336/103/91），owner doc 三处同步，roadmap WI38 → done。独立 closure audit APPROVED（1 项 Major 文本偏差已修复后收口）。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，与起草审查 agent_1443da31 均不同 task）：agent_825b8c7a-3c93-48ac-b0fd-d26214550c16
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql-enhancement/wi38-closure-audit.md
- Evidence:
  - Exit Criteria 逐条 PASS：接线五环 live 代码逐环行号核对（IWindowOperatorFactory :261-340 / WindowedStreamImpl :235-242+四 call-site / WindowOperatorFactoryImpl :356-448+:479 / AdvancedTransforms :229-232+门 :255-261 / WindowOperatorBuilder :110-112→WindowOperator :198/:492）；三层测试 21 例断言实质；钉码更新与双层门保持；D10 文档三处同步；gate-inventory 登记后完备性门 11/11；回归日志 BUILD SUCCESS（138/1336/103/91）；git diff 零越权
  - Anti-Hollow PASS：每环有直接运行时钉码（非类型存在）；生产 factory 实现唯一且覆写第四代；无空方法体/静默跳过；scan-hollow --severity high 退出码 0
  - audit Major M-1（plan flow 层证据描述与 live 测试分工不符）已修复：plan 三处改写为 flow 环1→2/core 环2→3/runtime 环3→5 的实际三层分工
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql-enhancement/03-wi38-accumulation-mode-wiring-fix.md --strict` 退出码 0（翻转后复跑确认）
  - roadmap 状态行翻转时括注嵌套全角括号被 BULLET_RE 静默丢弃（分母 17）即时捕获并修正——解析器重跑门有效

Follow-up:

- no remaining plan-owned work；emit 两档实施归 WI37（依赖本 plan 打通的 ACCUMULATING 通道）
