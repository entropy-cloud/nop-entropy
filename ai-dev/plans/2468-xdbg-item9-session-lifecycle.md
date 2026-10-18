# 2468 XDBG item 9 每会话实例生命周期 + IDEA 默认会话兼容（roadmap item 9）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 9；设计权威 `03-sessionization.md` §3.5（生命周期）/§3.7（IDEA 兼容）/§3.8（职责分界）、`02-lease-lifecycle.md` §3.3（谓词局部化与定向唤醒辖区）
> Related: plans/2467（item 8 done，commit 4638764）

## Purpose

按 D3 §3.5 落地会话化下半部分（生命周期面）：`SessionDebugManager` 承接每会话 `XLangDebugger` 实例的创建/销毁完整流（含专属 `IDebugNotifier` 接线/拆线）、`destroySession` 的 abort 收尾（close 语义）、服务端停机清扫衔接；IDEA 默认会话兼容回归；双会话并发互不干扰回归。

## Current Baseline

- item 8 已落地（commit 4638764）：session 包 7 类（`SessionDebugManager` 已有 init/createSession/getSession/destroySession 骨架——**destroySession 目前只置 destroyed 位，不含实例 close 与 notifier 拆线**）；`buildGlobalExecutor` 两态注入已接。
- `XLangDebugger` 实例字段自带 BreakpointManagerImpl/suspendedThreads/stepMode/tempBreakpoint/notifier/suspended/closed 标志/cleanupThread；`close()` 置 closed → signalAll → 挂起线程抛 `ERR_XLANG_DEBUGGER_ALREADY_CLOSED`；`setNotifier` 可换接线。
- item 8 审计移交登记：①默认会话句柄不在 sessions 表（`getSession("default")=null`）——本 plan 裁定与修正；②wiring 测试未断言 executor 绑定同一 debugger 实例、destroy 未逐实例 close——本 plan 承接。
- **辖区裁定（本 plan 显式声明）**：`XLangDebugger` 的 per-thread 谓词局部化与定向唤醒原语、多线程单步目标策略的**语义重建**依赖 item 10 的租约/谓词改造（D2 §3.3 已声明其为前提）——本 plan 只做生命周期与隔离面；单步语义重建随 item 10 落地（D3 §3.6 契约在 item 10 实现时消费），roadmap item 9 的"多线程单步目标策略落地"据此**显式移归 item 10 承接**（Deferred 登记）。
- 停机衔接：`XLangDebuggerInitializer.destroy()` 现状 = server.stop() 后已对**默认实例** close()（注释明示不 close 会泄漏），但 `sessionManager.reset()` 只置位+清表、**无 web 会话实例清扫**——item 9 补齐 web 实例清扫，且必须保留 `sessionManager==null` 分支的默认实例 close（否则退回既有泄漏缺陷）。

## Goals

- `SessionDebugManager` 生命周期完成：`createSession`（每会话新 `XLangDebugger` 实例 + notifier 接线回调）、`destroySession`（close 语义 abort：`close()` 中止挂起 → notifier 拆线 → 注销 → destroyed 位）、`shutdownAll()`（停机清扫：逐实例 close）、默认会话句柄入 sessions 表修正（`getSession("default")` 可达）。
- `SessionDebugManager.SessionListener`（可选回调接口）：notifier 接线由网关（item 12）注册而非 manager 硬编码——manager 提供 `setSessionListener(cb)`，创建/销毁时回调（接线/拆线动作归 item 12）。
- IDEA 兼容回归测试：默认会话路径（SimpleRpcServer 形态、无标记执行、stepX/resume 单实例语义）行为与改造前一致。
- 双会话隔离测试：两会话各自断点/挂起/单步/求值互不干扰。
- 停机测试：shutdownAll 后全部实例 closed、路由 destroyed。

## Non-Goals

- 租约状态（item 10）、per-thread 谓词局部化与单步语义重建（item 10 承接，Deferred 登记）、notifier 的 web 出站实现（item 12）、WS 通道。

## Scope

### In Scope

- `nop-dev-tools/nop-xlang-debugger` session 包（`SessionDebugManager`/`DebugSessionHandle` 增强 + 新增 `SessionListener` 接口）
- `XLangDebuggerInitializer.destroy()` 停机清扫增强
- 对应测试

### Out Of Scope

- 保护区（零改动）；item 10/12 的功能面。

## Execution Plan

### Phase 1 - 生命周期实现与测试

Status: completed
Targets: session 包 + 测试

- Item Types: `Feature`

- [x] `SessionDebugManager` 生命周期增强：`createSession` 每会话创建新 `XLangDebugger` 实例（或接受外部实例——按网关需要，一期内部创建）；`destroySession` abort 收尾链（`close()` → listener.onDestroy 回调（notifier 拆线由 listener 做）→ 注销 → destroyed 位）；`shutdownAll()`（逐实例 close + 全量清扫）；默认会话句柄注册进 sessions 表（键 "default"，`getSession("default")` 可达；defaultHandle 与表内同实例）
- [x] `SessionListener` 接口：`onCreated(handle, debugger)` / `onDestroyed(handle, debugger)`（item 12 接线 notifier 用）
- [x] Initializer `destroy()`：调 `shutdownAll()` 替代 `reset()`
- [x] 测试——生命周期：createSession 产生独立实例（断点表互不可见）、destroySession 后实例 closed（挂起线程抛 ALREADY_CLOSED）、shutdownAll 全量 closed、默认会话 getSession 可达
- [x] 测试——双会话隔离：会话 A 设断点+挂起，会话 B 无感知（断点表/挂起线程/单步互不干扰）；B 的求值不命中 A 的断点
- [x] 测试——**同实例绑定断言**（item 8 移交项②）：web=false 形态断言 `DebugExpressionExecutor` 绑定的就是注册到 SimpleRpcServer 的同一 debugger 实例；web=true 断言默认会话 executor 绑定同一实例（DebugExpressionExecutor 增加包私有 getter 或以 checkBreakpoint 行为断言）
- [x] 测试——IDEA 兼容回归：默认会话（无标记执行 + TCP 形态 API 调用 addBreakpoint/stepInto/resume/waitSuspended 全链路）行为与 item 8 前基线一致（复用 TestXLangDebugger 模式对默认会话实例执行）
- [x] `XLangDebugger.notifier` 字段 volatile 化（一行改动，网关线程 setNotifier/脚本线程读的可见性）
- [x] 测试卫生：manager 复位

Exit Criteria:

- [x] 双会话并发互不干扰（断点表/挂起/单步/求值四维隔离断言）
- [x] IDEA 默认会话回归绿（现有 TestXLangDebugger/TestRemoteDebugger 全绿 + 默认会话全链路新测试绿）
- [x] destroySession/shutdownAll 后实例 closed 且路由 destroyed；notifier 拆线回调触发
- [x] web=false/web=true 两形态的 executor 均绑定注册到 SimpleRpcServer 的同一 debugger 实例（同实例断言）
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` 全绿
- [x] 无静默跳过（destroy/shutdown 的每个分支有行为与断言；含并发 create-vs-shutdown 用例）
- [x] owner-doc 同步（随收口执行）：D3 §3.8 item 9 职责行"定向唤醒原语与 stepX 重建"移归注记 → item 10；roadmap item 9/10 Goal 文本同步修订（单步措辞移至 item 10）
- [x] `ai-dev/logs/` 执行当日条目已更新

### Phase 2 - 独立审计与收口

Status: planned
Targets: 代码、tests、roadmap

- Item Types: `Proof`

- [x] 独立子代理 closure audit：双会话隔离测试有效性（变异探针）、生命周期链运行时连通、IDEA 回归基线核对、Deferred 移归（单步语义重建→item 10）的裁定复核
- [x] 修复审计发现
- [x] owner-doc 同步执行：roadmap item 9 Goal"多线程单步目标策略落地"移至 item 10 Goal；D3 §3.8 item 9/10 职责行移归注记（§3.5 destroy 链顺序如有偏离一并同步）
- [x] roadmap item 9 → `done`
- [x] `ai-dev/logs/` 收口记录

Exit Criteria:

- [x] 审计发现全部修复
- [x] 测试门全绿
- [x] roadmap item 9 `done` 且会话化 milestone 未翻转
- [x] `ai-dev/logs/` 收口记录

## Closure Gates

- [x] 全部 in-scope 行为实现且有测试
- [x] Anti-Hollow：生命周期链（create→使用→destroy→closed）运行时连通
- [x] Deferred 移归裁定（单步语义重建→item 10）已登记且理由成立，**并已同步至 roadmap item 9/10 Goal 与 D3 §3.8 职责行**（owner-doc 锚点修正：§3.8/§3.5 而非 §3.4）
- [x] shutdownAll 并发纪律（同监视器锁）实现且有 create-vs-shutdown 测试
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2468-xdbg-item9-session-lifecycle.md --strict` 退出码 0
- [x] No protected-area changes

## Deferred But Adjudicated

### 多线程单步目标策略语义重建

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 语义重建的机制前提（per-thread 谓词局部化 + 定向唤醒原语）按 D2 §3.3 归 item 10 的挂起循环改造；本 plan 交付的生命周期/隔离面不受其影响（item 8 后跨会话单步互扰已消除，会话内多线程单步为既有全局语义、由 item 10 重建为定向语义）。
- Successor Required: `yes`
- Successor Path: item 10（租约续期机制 plan）消费 D3 §3.6 契约落地。

### Destroying 窗口过渡行为（close 先行、destroyed 位末置）

- Classification: `watch-only residual`
- Why Not Blocking Closure: close() 与 destroyed 置位之间的窗口内，携带该会话标记的迟到命中会在 makeSuspendedThread 抛 ALREADY_CLOSED（与 D2 §3.1①"不抛错直接继续"的终态语义偏差）——该终态语义由 item 10 的授予判定①（checkBreakpoint 最前部）消除，非本 plan 辖区；窗口极窄（销毁即终态）。
- Successor Required: `yes`
- Successor Path: item 10 授予判定①落地（03 §3.4 双层分工已登记）。

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 生命周期面落地：SessionDebugManager 增强（createSession 双形态/句柄携带 debugger 引用/destroy abort 收尾链 close→注销→destroyed→**锁外**回调/shutdownAll 同监视器锁+shutdown 拒绝+回调锁外批量触发/默认会话入表/SessionListener 异常隔离回调）+ XLangDebugger.notifier volatile 化与 isClosed() 访问器 + Initializer destroy 保留默认实例 close 并增 shutdownAll。**38/38 测试绿**（生命周期/回调隔离含真实锁外重入验证/双会话四维隔离/同实例绑定断言/默认会话全链路/并发 create-vs-shutdown 无泄漏）。Deferred 移归（单步语义重建→item 10）已同步 roadmap item 9/10 Goal 与 D3 §3.8。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（plan 审查 agent_029cde20-f932-4725-b1cd-d7533dabe00d；closure audit agent_6f8bcc8f-3ed9-4583-8758-43f5ab035a0b——首轮 NO-GO，修复后判可收口）
- Evidence:
  - 首轮 closure audit（NO-GO）发现：F1 回调锁内触发与三方"锁外"文档矛盾；F2 证据不实（37→实为 36、并发用例缺失、默认会话全链路缺失、logs 虚勾）；F3 abort ALREADY_CLOSED 行为无回归测试
  - 修复：F1 回调真正移出锁（createSession/shutdownAll 收集-锁外触发模式；destroySession 原本正确）+ 重写 testCallbackOutsideManagerLock 为真实重入验证（回调内跨会话 destroy，锁内将死锁）；F2 补默认会话全链路测试 + 并发 create-vs-shutdown 测试 + 数字修正（38/38）+ 本日志条目 + 本审计 ID；F3 登记 follow-up（随 item 10）
  - 变异探针（首轮 audit 实操）：destroySession 的 close 调用短路 → 双测试变红 → 逐字节恢复 md5 一致 → 36/36 复绿
  - 最终测试：38/38 全绿；`-am` 全依赖链 exit 0（_tmp/xdbg-item9-am-test2.log）
  - Deferred 项分类检查：单步语义重建（moved to successor item 10，roadmap+D3 双登记）；Destroying 窗口过渡行为（watch-only，item 10 授予判定①承接）；分类诚实

Follow-up:

- abort ALREADY_CLOSED 挂起线程行为回归测试 → 随 item 10 谓词改造补齐（首轮 audit F3）。
