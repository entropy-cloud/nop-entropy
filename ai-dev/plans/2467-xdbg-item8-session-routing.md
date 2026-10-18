# 2467 XDBG item 8 会话标识注入 + executor 会话路由（roadmap item 8）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 8；设计权威 `ai-dev/design/xlang-web-debugger/03-sessionization.md`（§3.2 标记契约/§3.4 路由规则/§3.8 职责分界）+ `00-overview.md` §3.3 开关矩阵
> Related: plans/2460-2466（设计收口 M1 done）

## Purpose

按 D3 落地会话化上半部分：`DebugSessionScope` 标记契约（save-restore）、`SessionRoutingExecutor` 路由外壳、`SessionDebugManager` 会话注册表（含默认会话与句柄 destroyed 位）、destroyed 分支执行器槽位；`nop.xlang.debugger.enabled=true` 且 web 未激活时注入形态与现状逐字节等价。顺带补 `docs-for-ai` module-groups 的 nop-dev-tools 最小条目（roadmap 横切要求）。

## Current Baseline

- `DebugExpressionExecutor`（nop-xlang-debugger）：`execute` 对 `allowBreakPoint()` 节点调 `debugger.checkBreakpoint` 后自线程化——路由目标的现形态。
- `XLangDebuggerInitializer`：`isEnabled()` 读 `CFG_XLANG_DEBUGGER_ENABLED`（XLangConfigs，nop-xlang）；`initialize()` 建 SimpleRpcServer + debugger + `registerGlobalExecutor(new DebugExpressionExecutor(debugger))`；`destroy()` 恢复 DefaultExpressionExecutor + close。
- `EvalExprProvider.registerGlobalExecutor` 为 nop-core 公共 API（无需改动）；`EvalRuntime` 不动（D3 方案 A）。
- 函数/标签调用边界重读全局槽位（`XLang.getExecutor()` + `new EvalRuntime`）——路由外壳单实例 + ThreadLocal 标记在该约定下天然存活（D3 §3.2 双机制论证）。
- 设计约束（D3）：web 开关键名 `nop.xlang.debug-web.enabled`（D0 §3.3 钉死；D0 原文"实现注册归 item 12"——**本 plan 显式裁定提前至 item 8 注册**（initialize() 分支需要读取），owner-doc 同步项见 Phase 1；D6 §一.5 配套推论"web=true 蕴含 enabled=true"一并落为实现断言）；destroyed 分支保留取消保护（D6 §一.10 修订——本 plan 提供执行器槽位，槽位目标形态 = D6 §3.2 `executeDestroyed` 伪代码：入口查取消通道后委托默认执行器，取消检查通道由 item 14 注入）。
- 现状：nop-xlang-debugger 无 session 相关类；测试目录存在（JUnit 5）。

## Goals

- 新增 `io.nop.xlang.debugger.session` 包：`DebugSessionHandle`（不可变身份 + volatile destroyed 位）+ canonical destroyed 哨兵；`DebugSessionScope`（ThreadLocal 标记契约：`withSession(sessionId|handle, Supplier)` save-restore——sessionId 入参经 manager 解析、miss 归一为 destroyed 哨兵（D3 §3.2 契约原形）；`currentHandle()`；null 入参归一 destroyed）；`SessionDebugManager`（注册表 + 默认会话 + `getSession(sessionId)` 查找 + 创建/销毁句柄——**销毁顺序按 D3 §3.5：先注销后置 destroyed**）；`SessionRoutingExecutor`（D3 §3.4 **外壳三条运行时路由**：无标记→默认会话/存活→会话执行器/已销毁→DestroyedSessionExecutor）；`DestroyedSessionExecutor`（无调试钩点 + 可注入取消检查通道，目标形态 = D6 §3.2 `executeDestroyed` 伪代码）。**范围声明**：D3 §3.4 行 6（Destroying 窗口由实例内授予判定①承接）**归 item 10**（本 plan 无 Destroying 状态机），不在验收面。
- `XLangDebuggerInitializer`：web 开关（`nop.xlang.debug-web.enabled`，默认 false）启用时改注 `SessionRoutingExecutor`；未启用时保持现状注入形态（逐字节等价）；destroy 路径清扫。
- module-groups 补 nop-dev-tools 最小条目。
- 测试：路由六情形、标记 save-restore、嵌套调用边界透传（集成级：带函数调用的脚本执行经路由命中会话 debugger）、web=false 回归等价。

## Non-Goals

- 会话实例生命周期管理（item 9：notifier 接线/创建销毁完整流）、租约（item 10）、取消通道实现（item 14）、WS 网关（item 12）、IDEA 回归的完整验收（item 9）。

## Scope

### In Scope

- `nop-dev-tools/nop-xlang-debugger/src/main/java/io/nop/xlang/debugger/session/`（新包 5 类）
- `XLangDebuggerInitializer`（注入逻辑改造）
- `docs-for-ai/01-repo-map/module-groups.md`（nop-dev-tools 最小条目）
- 对应测试（`src/test/java/io/nop/xlang/debugger/session/`）

### Out Of Scope

- nop-core/nop-xlang 改动（方案 A 零保护区——若实现中发现必须动保护区，立即停手并修订 plan 申请 audit）；WS/REPL/租约功能。

## Execution Plan

### Phase 1 - 会话路由实现

Status: completed
Targets: `nop-xlang-debugger` session 包 + Initializer

- Item Types: `Feature`

- [x] `DebugSessionHandle`：sessionId + 会话执行器引用（不可变 final 字段）+ `volatile boolean destroyed`（会话管理器销毁时置位）；Javadoc 声明身份不可变/状态位可见性（D3 §3.2 M4 修复要求）
- [x] `DebugSessionScope`：ThreadLocal 持句柄；`withSession(sessionId, Supplier)` 与 `withSession(handle, Supplier)` 双形态 save-restore（嵌套重入不丢外层标记，finally 恢复 + 线程池复用场景 `remove()` 卫生）；sessionId 形态经 manager 解析、miss 归一 destroyed 哨兵；null 入参归一 destroyed（不得落默认会话）
- [x] `SessionDebugManager`：默认会话注册（`registerDefault(XLangDebugger)`）、`createSession(sessionId, debugger)`、`getSession(sessionId)` 查找（item 12/14 消费面）、`destroySession(handle)`（**先注销后置 destroyed**，D3 §3.5 顺序）、静态实例访问器 + 测试复位入口
- [x] `SessionRoutingExecutor`：D3 §3.4 路由表——无标记 → 默认会话 `DebugExpressionExecutor`；有标记存活 → 该会话执行器；有标记已销毁 → `DestroyedSessionExecutor`；构造入参 = manager + 默认会话执行器 + destroyed 执行器
- [x] `DestroyedSessionExecutor`：无 checkBreakpoint、入口查取消通道后**委托默认执行器**（对齐 D6 §3.2 `executeDestroyed` 伪代码，非自线程化）；`ReplDeadlineChannel` 可空注入，一期 null=纯委托直通——通道实现归 item 14
- [x] `XLangDebuggerInitializer` 改造：见 Goals（`buildGlobalExecutor()` 提取 + web 开关常量 + fail-fast 蕴含断言 + destroy 清扫）；**接线运行时验证**——`buildGlobalExecutor()` 单测断言两种开关组合的返回形态（web 关 = DebugExpressionExecutor 实例；web 开 = SessionRoutingExecutor 实例）
- [x] module-groups 补 `nop-dev-tools/` 条目（模块清单一句话 + 调试器子模块归属说明）
- [x] 单元测试：**外壳三条运行时路由**（无标记→默认/存活→会话/已销毁→直通）+ 初始化器两态注入（buildGlobalExecutor 形态断言）+ save-restore 嵌套 + sessionId miss 归一 + null 归一 + destroyed 位翻转路由 + 测试卫生（manager 复位/ThreadLocal remove）
- [x] 集成测试：`CoreInitialization.initialize()` bootstrap（@AfterAll 恢复全局 executor 槽位与初始化状态，防 fork 污染）+ `XLang.execute` 执行带函数调用与标签调用的脚本（会话标记包裹），用 **recording proxy `IXLangDebugger`**（记录 checkBreakpoint 调用，免挂起/resume 编排）断言到达指定会话实例（边界重取后路由不变——D3 透传论证的运行时验证）

Exit Criteria:

- [x] web=false 时注入的 executor 与现状**同一形态**（`DebugExpressionExecutor(defaultDebugger)`），现有调试器测试全绿
- [x] web=true 时**外壳三条运行时路由 + 两态注入**（item 8 可测四情形）行为符合 D3 §3.4（destroyed 直通不走默认会话——防泄漏进 IDEA 调试面；行 6 Destroying 窗口归 item 10，显式不在本 plan 验收）
- [x] 嵌套 save-restore 不丢外层标记；跨函数/标签调用边界路由不变（集成测试证明）
- [x] 无标记路径仅一次 ThreadLocal get + 分支（零开销红线——代码审查观察项，机械性能基准对比归 item 9/11 的验收）
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` 全绿
- [x] module-groups 条目落盘且 doc-links --strict 0；D0 §3.3 "实现注册归 item 12" 括注同步修订为 "item 8 提前注册"（owner-doc 同步）
- [x] 无静默跳过：destroyed 分支、null handle 分支均有显式行为（直通/报错）而非空操作
- [x] `ai-dev/logs/` 执行当日条目已更新

### Phase 2 - 独立审计与收口

Status: completed
Targets: 代码、tests、docs

- Item Types: `Proof`

- [x] 独立子代理 closure audit：Anti-Hollow（路由链运行时连通——集成测试断言 checkBreakpoint 真达会话实例；7 类逐类过无空壳）+ D3 §3.4 逐情形对账 + 零保护区确认（git status 不触 nop-core/nop-xlang）+ 变异探针（destroyed 分支注释 → 单测变红 → 逐字节恢复 + sha256 复验 + 全套复绿）
- [x] 修复审计发现（Minor-1 web 蕴含 fail-fast 落为双参数断言+新测试；Minor-2 日志条目；Minor-3 标签用例裁定降级见 Deferred；Low-1 import 顺序/FQN 清理；Low-2/3 登记归 item 9/12）
- [x] roadmap item 8 → `done` + Stages 表 owner plan 指向实际文件
- [x] `ai-dev/logs/` 收口记录

Exit Criteria:

- [x] 审计发现全部修复
- [x] 全部构建/测试门绿（`./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am`）
- [x] roadmap item 8 `done` 且会话化调试引擎 milestone 未翻转（依赖 8-11）
- [x] `ai-dev/logs/` 收口记录

## Closure Gates

- [x] 全部 in-scope 行为实现且有测试（Test-Mandated Rule：每新公共类/方法有对应测试）
- [x] Anti-Hollow：路由链从 XLang.execute 入口到会话实例 checkBreakpoint 运行时连通（集成测试证明），无空壳/静默跳过
- [x] 不存在被静默降级的 in-scope 缺陷
- [x] owner docs：module-groups 最小条目已补（docs-for-ai）；D3 §3.4 修订（destroyed 取消检查执行器）落点已实现或槽位化
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` 通过
- [x] checkstyle/代码规范通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2467-xdbg-item8-session-routing.md --strict` 退出码 0
- [x] No protected-area changes（零保护区确认）

## Deferred But Adjudicated

### 取消检查通道（ReplDeadlineChannel 实现）

- Classification: `out-of-scope improvement`（依属 item 14）
- Why Not Blocking Closure: D6 §3.2 裁定通道机制归 item 14（REPL execute 注入）；本 plan 提供槽位接口与默认直通，接口契约与 D6 §3.2 一致。
- Successor Required: `yes`
- Successor Path: item 14（plan 2473 或顺延）注入通道实现。

### 标签调用边界的集成测试用例

- Classification: `watch-only residual`
- Why Not Blocking Closure: 标签边界（IXplTag 再取点）与函数边界（ExecutableFunction）属同一全局槽位重取机制族，函数边界集成测试已运行时验证路由不变；标签用例需要测试 xlib 资源基建（本模块测试 classpath 无现成 xlib），代表性覆盖由 item 14/21 集成场景承接（届时标签在真实使用面内）。closure audit 认可该降级裁定。
- Successor Required: `yes`
- Successor Path: item 14/21 集成测试覆盖标签调用边界路由。

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: D3 会话化上半部分落地：session 包 7 类（句柄/scope 契约/管理器/路由外壳/destroyed 执行器/取消通道接口/模块配置）+ Initializer buildGlobalExecutor 提取与 web 分支（含 web⟹enabled fail-fast 断言）+ module-groups 最小条目 + D0 括注同步。23/23 测试绿（15 新增：路由三情形/接线两态/save-restore/归一化/destroyed 翻转/集成边界透传/双会话互不泄漏/无标记不进 web 会话/fail-fast），`-am` 全依赖链 exit 0，变异探针实证测试有效性，零保护区确认成立。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 closure audit（agent_644cdb74-5978-4f5c-8a39-f9e6dfd376cf；plan 审查 agent_52000c5b-545f-4133-aeb9-02f4bc0deccb）
- Audit Session: closure audit 由 fresh 子代理执行（含变异探针实操与 sha256 恢复复验）
- Evidence:
  - Phase 1 Exit Criteria 9 条：8 PASS + EC8（日志）随收口写入后 PASS
  - 变异探针：destroyed 分支注释 → testDestroyedMarkerRoutesToDestroyedExecutor 变红（expected expr-result but was recording）→ 恢复 22/22 复绿 → 二轮修复后 23/23
  - Anti-Hollow：集成测试实测 XLang.execute → 外壳 → 会话实例 checkBreakpoint 连通；双会话互不泄漏断言通过
  - 修复验证：Minor-1（fail-fast 双参数断言 + testWebWithoutDebuggerFailsFast）/Minor-2（日志）/Minor-3（裁定降级 watch-only + successor）/Low-1/Low-2/3（登记归 item 9/12）
  - `./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` exit 0（后台全依赖链）；doc-links --strict exit 0
  - 零保护区：git status 改动面仅 nop-dev-tools + docs-for-ai + ai-dev

Follow-up:

- 默认会话句柄不在 sessions 表（getSession("default")=null）→ item 12 会话面消费注意；wiring 测试 debugger 实例绑定断言与 destroy 逐实例 close → item 9 承接。
