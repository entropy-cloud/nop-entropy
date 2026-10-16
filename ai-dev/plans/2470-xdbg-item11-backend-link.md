# 2470 XDBG item 11 调试模式执行后端联动（roadmap item 11，保护区）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 11；设计权威 `ai-dev/design/xlang-web-debugger/04-execution-backend-link.md`（三落点/谓词/矩阵/递归终止性）
> Related: plans/2469（item 10 done，commit 4192e27）；**保护区：nop-kernel/nop-xlang——本 plan 即 D4 要求的 plan audit 载体**

## Purpose

按 D4 实现调试模式与执行后端联动：三个静态绑定直通落点的运行期降级（单谓词多落点），使 `nop.xlang.debugger.enabled=true` 时绑定单元/标签体可断点；非调试路径逐字节零改动；同步 xlang-execution 基线判定输入枚举与 Router javadoc（D4 §3.7 owner-doc 任务）。

## Current Baseline

- **三个直通执行落点（D4 §3.1，已核验）**：① `EvalBackendRouter.executeBoundUnit`（L174-181）`bound.getBinding().execute(rt)` 裸调用；② `EvalStaticBoundExecutable.execute`（L60-62）忽略 executor 直调 binding（标签级 + fast-path 到达）；③ `executeAdjudicated` 的 STATIC 分支（L104-107，消费 `decide()` 产物）裸 `decision.getStaticBinding().execute(rt)`——`decide()` 只产决策不执行。
- **降级先例**：`EvalStaticDegradedExecutable.execute` 经 `EvalExprProvider.getGlobalExecutor()` 重取执行原树、`allowBreakPoint()` 委托原树——降级执行形态有 live 同构。
- **短路先例**：`decide()` 之首 `isForceInterpreter()` 短路（L236-237，静默不记降级观测）；加载期 `bindLoadedUnit`/`bindTagFunction` 同被短路。
- **配置谓词**：`CFG_XLANG_DEBUGGER_ENABLED`（XLangConfigs，Router 同模块可直读——`isForceInterpreter()` 同款消费模式）。
- **递归终止不变式**：每个包装的 `sourceTree` 必为非包装原树（绑定点只包新鲜编译产物、`LazyCompiledFunction.compile()` 一次性）。
- **item 10 已落**：调试执行器为融合形态（会话路由 + 检查点）——降级执行经 `getGlobalExecutor()` 到达会话调试器即得断点（D3 路由已就位）。
- **重启门控事实**：`XLangDebuggerInitializer.isEnabled()` 为启动期门控；`debugger.enabled` 运行期翻 true 只得半态（D4 §3.5 作用域限定）。

## Goals

- `EvalBackendRouter`：新增 `isDebugDegradeActive()`（读 `CFG_XLANG_DEBUGGER_ENABLED`，同 `isForceInterpreter()` 消费模式）；`decide()` 之首（force-interpreter 短路旁）新增调试短路 → interpreter，reason=`debugger-enabled`，静默不记降级观测；`executeBoundUnit` 分支：谓词激活时改经 `EvalExprProvider.getGlobalExecutor().execute(bound.getSourceTree(), rt)`。
- `EvalStaticBoundExecutable.execute`：谓词激活时改经 `EvalExprProvider.getGlobalExecutor().execute(sourceTree, rt)`。
- owner-doc 同步：`ai-dev/design/xlang-execution/01-architecture-baseline.md` §三判定输入枚举（四→五）+ `EvalBackendRouter` 类 javadoc 判定输入行。
- 加载期不读调试开关（D4 §3.3 裁定：bindLoadedUnit/bindTagFunction 保持单一职责）。
- 测试（D4 §3.7 锚点）：三落点各自断言（绑定单元降级断点命中/标签体断点命中/裁决路径短路 reason）+ `enabled=false` 绑定直通回归（含矩阵行 4 现状锁定）+ 调试态与生产态行为对比。

## Non-Goals

- truffle/java 后端调试能力、加载期绑定语义变更、`EvalStaticDegradedExecutable` 行为改动（其已可断点）、前端（item 15）。

## Scope

### In Scope

- `nop-kernel/nop-xlang`：`backend/EvalBackendRouter.java`（短路 + executeBoundUnit + 谓词 + javadoc）、`backend/EvalStaticBoundExecutable.java`（execute 降级分支）
- `ai-dev/design/xlang-execution/01-architecture-baseline.md`（判定输入枚举同步）
- `nop-kernel/nop-xlang/src/test/java/`（联动测试，或按现有测试布局就近）
- `ai-dev/logs/` 当日条目

### Out Of Scope

- 其他保护区文件；`EvalStaticDegradedExecutable` 行为；D5+ 前端。

## Execution Plan

### Phase 1 - 三落点降级实现

Status: completed
Targets: `EvalBackendRouter` + `EvalStaticBoundExecutable` + tests

- Item Types: `Fix`（两条断点失效路径为已确认 live defect）

- [x] `EvalBackendRouter.isDebugDegradeActive()`：读 `CFG_XLANG_DEBUGGER_ENABLED`（`isForceInterpreter()` 同款 varRef 消费）；类 javadoc 判定输入行同步（四→五：+debugger-enabled）
- [x] `decide()` 之首（force-interpreter 短路旁）调试短路：`interpreter("debugger-enabled", false)`——静默不记降级观测（D4 §3.2/§3.7 reason 契约）
- [x] `executeBoundUnit`：谓词激活 → `EvalExprProvider.getGlobalExecutor().execute(bound.getSourceTree(), rt)` 并**记 interpreter 决策**（reason=`debugger-enabled`、非降级观测、artifact=sourceTree，比照 `executeDegradedUnit` L223-228——D4 §3.6 观测面契约）；非降级分支保持 `boundUnit` 记录不变。落点②经函数边界/fast-path 到达时保持现状无决策记录（防执行者发明记录点）
- [x] `EvalStaticBoundExecutable.execute`：谓词激活 → `EvalExprProvider.getGlobalExecutor().execute(sourceTree, rt)`，否则原 binding 直调
- [x] 递归终止性注释：sourceTree 必为非包装原树不变式 + 嵌套调用按树深有界终止（D4 §3.2）
- [x] 测试（nop-xlang 域）：①调试态 `executeBoundUnit` 降级经全局执行器（recording executor 断言到达 + 决策环记 interpreter 决策）；②`enabled=false` 绑定直通回归（binding.execute 被调、global executor 不被调——需 router active，注册 fake static backend；`ResourceComponentManager.clearCache("xlib")` 防编译缓存跨用例泄漏）；③`decide()` 调试短路 reason=debugger-enabled 且不记降级观测
- [x] 测试（**nop-xlang-debugger 域端到端**，Major-2 选项 a）：内联 fake `IEvalStaticBackend`/`IEvalStaticBinding`（注册 EvalBackendRegistry 触发包装）+ 直接构造 `EvalStaticBoundExecutable(真编译树, fake binding)`——①调试态包装 execute 降级经全局执行器到达 DebugExpressionExecutor（真实断点设于 sourceTree 行 → checkBreakpoint 挂起 → resume 端到端命中）；②`enabled=false` 直通（binding 被调、调试器零命中）——全局槽位卫生照 TestSessionRoutingIntegration 先例 @AfterAll 恢复
- [x] `xlang-execution/01-architecture-baseline.md` §三判定输入枚举同步
- [x] D4 §3.7"调试态与生产态性能对比"裁定为**行为对比**（生产路径改动 = 单次布尔判断，无可测性能面——偏离理由登记，防收口审计按 D4 原文误判）
- [x] `ai-dev/logs/` 当日条目

Exit Criteria:

- [x] 三落点全部走谓词降级（单谓词多落点）；`enabled=false` 时三落点行为逐字节不变（clearCache 卫生防编译缓存串用）
- [x] `executeBoundUnit` 降级分支记 interpreter 决策（非降级保持 boundUnit）；落点② fast-path 保持无记录
- [x] 递归终止不变式成文（注释）；降级执行使用与 Degraded 包装同构的执行形态
- [x] 调试态绑定单元断点可命中端到端（nop-xlang-debugger 域：真断点挂起 → resume，M2 验收锚点；标签体经包装 execute 降级同机制覆盖）
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` 全绿 + `./mvnw test -pl nop-kernel/nop-xlang-java,nop-kernel/nop-xlang-java-e2e -am` 全绿（**下游静态绑定回归面**——java 后端绑定测试全部在依赖方模块，Major-1）
- [x] owner-doc 两处同步落盘；`ai-dev/logs/` 条目
- [x] 无静默跳过（谓词两分支均有行为与断言）
- [x] 变异探针：谓词短路注释 → ①②测试变红 → 恢复

#### Phase 2 - 保护区 plan audit 与收口

Status: completed
Targets: 代码、tests、owner docs、roadmap

- Item Types: `Proof`

- [x] **保护区 plan audit（独立子代理）**：已完成——无 Blocker，3 Major（验证面）+5 Minor 修订后实施（机制方向/改动最小性/递归终止性/零改动论证经 live code 核验成立）；实现后复审修复落点
- [x] 修复审计发现
- [x] roadmap item 11 → `done`
- [x] `ai-dev/logs/` 收口记录

Exit Criteria:

- [x] plan audit 通过（无未处置 Blocker）且发现全修复
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` 全绿
- [x] roadmap item 11 `done` → **会话化调试引擎 milestone（M2）翻转 `done`**（8-11 全 done）
- [x] `ai-dev/logs/` 收口记录

### Closure Gates

- [x] 三落点降级实现且有测试；`enabled=false` 直通回归绿
- [x] Anti-Hollow：降级链从绑定命中到会话调试器 checkBreakpoint 运行时连通（端到端测试证明）
- [x] 递归终止不变式注释成文
- [x] owner docs：xlang-execution 基线 + Router javadoc 同步；D1 §3.7 无需动（无新增码——降级路径复用既有 interpreter 决策，不产生新错误码）
- [x] 独立子代理 plan audit（保护区）完成并记录证据
- [x] `./mvnw test -pl nop-kernel/nop-xlang -am` + 下游 `-pl nop-kernel/nop-xlang-java,nop-kernel/nop-xlang-java-e2e -am` 通过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-xlang --severity high` 退出码 0（指南 5b）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2470-xdbg-item11-backend-link.md --strict` 退出码 0
- [x] 改动面仅声明文件（nop-kernel/nop-xlang 两类 + 设计文档 + 测试）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 一次性调试票据、挂起感知 deadline → item 25（上游登记）。

## Closure

Status Note: 三落点运行期降级落地（Router 谓词 isDebugDegradeActive + decide 之首短路 reason=debugger-enabled + executeBoundUnit 降级经全局执行器记 interpreter 决策 + EvalStaticBoundExecutable.execute 降级，含递归终止不变式注释）；非调试路径逐字节不变（enabled=false 全回归绿）；owner-doc 两处同步（xlang-execution 基线判定输入五项 + Router javadoc）。测试：nop-xlang 864 全绿（含新 TestDebuggerBackendLink 5 用例）+ debugger 域 E2E 4/4（真断点挂起→resume 端到端命中，M2 锚点）+ 下游 nop-xlang-java/-e2e 回归。变异探针 RED 验证（探针恢复教训已记录）。保护区 plan audit 已通过并承载于本 plan。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（保护区 plan audit + 对抗性审查 agent_3acff44e-5047-464b-8cf4-a77f8fd7d3b5——无 Blocker、3 Major+5 Minor 修订后实施；探针与恢复复核由实现者执行、audit 报告已确认恢复纪律要求）
- Evidence:
  - 三落点测试：①executeBoundUnit 降级（recording executor 命中 + 决策环 interpreter/debugger-enabled）②enabled=false 直通（binding 被调、global 不被调）③decide 短路 reason=debugger-enabled 静默（degradation delta 0）④debug 先于 force-interpreter ⑤E2E 端到端断点命中（debugger 域，真 DebugExpressionExecutor + 真断点挂起 + resume + 求值 42）
  - 回归：nop-xlang 864 绿；下游 nop-xlang-java/-e2e 绿（_tmp/xdbg-item11-downstream-test.log）；debugger 链 -am exit 0
  - 变异探针：executeBoundUnit 谓词短路 kill → TestDebuggerBackendLink① RED → 恢复（git checkout 回退未提交改动的教训：改用重放式恢复 + grep 锚点复核；本例 router 重放后 5 处锚点全在）
  - 零保护区溢出：改动面 = EvalBackendRouter/EvalStaticBoundExecutable/xlang-execution 基线/两域测试
  - Deferred 项分类检查：无 deferred；D4 §3.7 行为对比裁定已登记

Follow-up:

- graceful dispose（不置 closed）引擎侧 disposing 位 → item 12 落地时修订；THREAD_NOT_SUSPENDED 迁入 XLangErrors → item 12 additive 批次。
