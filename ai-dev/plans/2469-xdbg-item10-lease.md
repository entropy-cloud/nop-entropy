# 2469 XDBG item 10 租约续期机制（roadmap item 10）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 10；设计权威 `02-lease-lifecycle.md`（§3.1 授予判定/§3.3 融合执行器与谓词局部化/§3.4 会话存活/§3.7 职责分界）+ `03-sessionization.md` §3.6（单步定向契约）+ `06/07`
> Related: plans/2467/2468（item 8/9 done）

## Purpose

落地租约机制引擎面（D2 §3.7 item 10 职责行）：per-thread 挂起谓词局部化、租约时钟与到期判定、`releaseAll`/`releaseThread` 原语、授予判定①②③、lease-expired 事件回调、stepX 定向语义重建（消费 D3 §3.6 契约）；gateway 侧时钟所有权归 item 12。

## Current Baseline

- `XLangDebugger.monitorWait`（L542-564，已核验）：全局 `while (suspended && !closed)` 谓词 + `resumeCondition.await(200ms)`；`resume()` = stepMode=RESUME + 全局 `suspended=false` + signalAll；`close()` 置 closed → signalAll → 出口 `if (closed) throw ALREADY_CLOSED`（无条件，D3 P1 竞态根源）；`makeSuspendedThread` 在 closed 时抛同码。
- `suspended` 全局标志被 `waitSuspended`/`isSuspended`（经 D1 暴露前端）/`sendBreakpointNotice` 消费。
- `checkBreakpoint`（L342）首行 `if (loc == null || breakpointsMuted) return`——取消/租约检查必须在此**之前**（D6 §3.2 mute 绕过面裁定）。
- item 9 已落：每会话实例（会话 A/B 断点/挂起/单步天然隔离）、`SessionDebugManager` 生命周期/回调、destroy abort 链。
- D2 裁定引用：会话租约时钟 per-session（`leaseDeadlineMillis` 概念字段）、到期判定 per-thread（200ms 轮询窗口内自查）；授予判定①（closed/销毁中→不挂起不抛错）②（租约过期→不挂起）③（正常挂起）；`releaseAll`（不置 closed 不抛错）vs `close()`（abort）；per-thread 定向唤醒消费 D3 §3.6 契约；单步"释放-再挂起零事件"。
- D1 登记的出站事件：`evt.leaseExpired{threadIds}`（engine-ext，item 10 产生源）、`evt.resumed{cause:"user"}` 互斥。
- 硬约束（roadmap/D2）：`resume()` 全局放行不可直接挪用；挂起语义仅 interpreter 后端承诺；默认会话（IDEA）无租约 → `leaseDeadlineMillis=0` 表示租约禁用（永不过期）。
- **B1 裁定（THREAD_NOT_SUSPENDED 登记归属）**：D1 §3.7 登记落点为 `io.nop.xlang.XLangErrors`（nop-xlang 保护区，归 item 12 additive 批次）——本 plan 改为**模块内定义 ErrorCode 常量**（`io.nop.xlang.debugger` 包内持有，字符串码 `nop.err.xlang.debugger.thread-not-suspended` 与 D1 登记表一致），stepX 现在即可抛出；item 12 additive 批次把它迁入 XLangErrors 并同步 import。零保护区声明保持。

## Goals

- `SuspendedThread` 增强：per-thread `localRelease`（局部放行标志）、per-thread step 判定状态（stepMode 随目标线程局部化）、挂起时间戳。
- `XLangDebugger` 租约面（新原语入 `IXLangDebugger` 接口——item 12 消费面）：`volatile long leaseDeadlineMillis`（0=禁用；getLeaseState 禁用时 expiresAtMs=0）+ `renewLease(long ttlMs)` + `getLeaseState()`（`{expiresAtMs, suspendedThreadIds}` 对齐 D1 LeaseState）+ `releaseAll()`（resume 语义：置全部局部放行 + signalAll，不置 closed 不抛错）+ `releaseThread(threadId)`（定向唤醒，no-op 幂等）。
- **边界注记**：一期授予判定①以 `closed` 承载"销毁中"（当前装配 destroySession 先 close，closed≈销毁中成立）；item 12 的 graceful dispose（不置 closed）落地时需引擎侧 disposing 位——届时修订。
- **实现偏差登记（审计 F6）**：① `getLeaseState()` 复合方法以 `getLeaseDeadlineMillis()`+`getSuspendedThreadIds()` 两件套替代（能力等价：{expiresAtMs, suspendedThreadIds} 可由 item 12 组装）；② 新原语暂在具体类 `XLangDebugger` 上（`DebugSessionHandle.getDebugger()` 返回具体类型，item 12 可消费），`IXLangDebugger` 接口扩展随 item 12 落地时一并纳入。两处偏差不改变能力面。
- 挂起谓词局部化：monitorWait 循环谓词改为 `!closed && !thread.localReleased && !leaseExpired()`（signalAll 醒来即重查）；出口判定顺序钉死——局部放行/租约到期 → 正常返回（**即使 closed 并存也不抛**），仅 `closed && !局部放行` → 抛 `ERR_XLANG_DEBUGGER_ALREADY_CLOSED`（D2 §3.3/D3 F1 裁定）。
- 全局 `suspended` 标志维护：无 `isSuspended()==true` 的挂起条目时同步清除（map 条目滞留不清，按谓词判定非按表空）。
- **放行/step 标志生命周期与锁纪律**：`localReleased`/`threadStepMode` 在**每次挂起入口**于 monitorLock 内复位（防上次放行残留导致下次不挂起）；复位与 `releaseAll`/`releaseThread` 同锁串行化（防"放行后复位抹掉放行标志"竞态——D3 §3.5"消 flaky"同级时序点）；`threadStepMode` 在 step 判定消费后（再挂起时）清除。
- 授予判定①②（checkBreakpoint 最前部，先于 mute guard）：closed/销毁中 → 直接返回；租约已过期 → 直接返回（warn 日志，不推事件——D2 §3.1）。
- stepX 定向重建（D3 §3.6）：stepX 作用于 `lastSuspendThread`（无挂起 → 抛 `ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED`，语义扩展登记 D1 follow-up）；仅置目标线程局部 step 模式与放行标志；stepOver/Out 帧深度判定随目标线程；stepX 不发事件（D2 §3.3 note）。
- `suspend()` 重建为会话级 `suspendOnce` 旗标（任一下一检查点挂起该线程并清除）。
- lease-expired/resumed 事件：`IDebugNotifier` 增 default 方法 `notifyLeaseExpired(List<Long> threadIds)` 与 `notifyResumed(List<Long> threadIds, String cause)`（受控扩展，默认空实现非破坏）。**发射点裁定**：租约到期自退出的线程自行回调一次（threadIds=[自身]，单元素）；宽限 releaseAll 由调用方（item 12 网关）聚合发一条；resume()/stepX 经 `notifyResumed`（cause=user）——与 evt.leaseExpired 互斥（D1 §3.5）。Initializer DebugNotifier 实现两回调（TCP 侧记日志，WS 出站归 item 12）。
- 测试（9 组，D2 §3.6 + M2/M4 锚点）：①续期保活 ②停止续期到期放行且脚本执行完+leaseExpired 互斥 ③close 与放行竞态（resume 后 close 与 stepOver 后 close 两路径都测：正常返回不抛）④releaseAll 宽限路径 ⑤授予判定①② ⑥stepX 定向（双线程仅释放目标）+ 无目标抛模块内错误码 ⑦isSuspended 维护 ⑧双会话租约隔离 ⑨suspendOnce 一次生效（两线程同到仅挂起一个）+ mute×租约共存（①②先于 mute 排序）+ releaseThread 幂等 + getLeaseState 快照。

## Non-Goals

- gateway 时钟所有权与限流（item 12）、宽限计时器（item 12）、`evt.leaseExpired` 的 WS 出站（item 12）、renew 频率限制执行（item 12）、Error 系取消异常与 REPL 通道（item 14）、D4 联动（item 11）。

## Scope

### In Scope

- `nop-dev-tools/nop-xlang-debugger`：`SuspendedThread`/`XLangDebugger`/`IDebugNotifier`/`XLangDebuggerInitializer`（DebugNotifier 实现 notifyLeaseExpired）
- 对应测试

### Out Of Scope

- 保护区（零改动——本项全部落 nop-dev-tools）；D4 联动（item 11）；网关/REPL。

## Execution Plan

### Phase 1 - 租约机制引擎面实现

Status: completed
Targets: `nop-xlang-debugger` 核心类

- Item Types: `Feature`

- [x] `SuspendedThread` 增强：`volatile boolean localReleased`、`volatile DebugStepMode threadStepMode`（stepOver/Out 帧深度判定随线程）、setter/访问器
- [x] `XLangDebugger` 租约字段与原语：`volatile long leaseDeadlineMillis`（0=禁用）、`renewLease(long ttlMs)`、`getLeaseState()`、`releaseAll()`、`releaseThread(threadId)`（无该线程 no-op 幂等）
- [x] monitorWait 谓词局部化：循环谓词 `!closed && !thread.localReleased && !leaseExpired()`；**出口判定顺序**（局部放行/租约到期优先正常返回；仅 closed 无放行 → 抛 ALREADY_CLOSED）
- [x] 授予判定①②插入 checkBreakpoint 最前部（先于 mute guard）
- [x] 全局 suspended 标志维护：线程离开挂起集（finally 中检测集合空 → `suspended=false`）
- [x] stepX 定向重建：目标=lastSuspendThread；定向释放；stepOver/Out 判定状态随目标线程局部化；无目标抛 THREAD_NOT_SUSPENDED；stepX 零事件
- [x] `suspend()` → 会话级 suspendOnce 旗标（下一检查点挂起该线程并清除）
- [x] `IDebugNotifier.notifyLeaseExpired` default 方法 + Initializer DebugNotifier 实现（日志）
- [x] 单元/集成测试（8 组，见 Goals 末条清单）：每组独立用例，挂起编排复用 TestXLangDebugger/TestSessionIsolation 模式

Exit Criteria:

- [x] 续期保活：挂起线程在 TTL 内 renew → 持续挂起不误放
- [x] 停止续期：到期 → 挂起线程正常返回且脚本执行完、`evt.leaseExpired`（notifier 回调）触发恰一次、`evt.resumed` 不触发（互斥）
- [x] close 与租约/放行竞态：先 stepOver（局部放行）后 close → 线程正常返回不抛；仅 close → 抛 ALREADY_CLOSED（既有语义保持）
- [x] releaseAll：宽限路径全量放行不抛错不置 closed
- [x] 授予判定①②：closed → 新命中不挂起不抛；租约过期 → 新命中不挂起（warn 日志）
- [x] stepX 定向：双线程挂起，stepInto 仅释放目标（lastSuspendThread），另一线程仍挂起；无目标（lastSuspendThread==null 或 !isSuspended() 处理时刻快照）抛模块内 `nop.err.xlang.debugger.thread-not-suspended`
- [x] isSuspended 维护：全部放行后为 false
- [x] 双会话租约隔离：A 到期放行不影响 B 挂起
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` 全绿；既有 IDEA 语义测试（TestXLangDebugger/TestRemoteDebugger/TestSessionIsolation）零改动全绿（stepX 无目标报错为 D3 §3.7 声明的显式行为变更，若既有测试覆盖该路径需按新语义修订并注明）
- [x] owner-doc：No owner-doc update required（stepX 行为变更 D3 §3.7 已覆盖；evt.resumed/leaseExpired 产生源 D1 §3.5 已登记归 item 10）
- [x] `ai-dev/logs/` 执行当日条目已更新

### Phase 2 - 独立审计与收口

Status: completed
Targets: 代码、tests、roadmap

- Item Types: `Proof`

- [x] 独立子代理 closure audit：变异探针（谓词局部化/出口顺序/授予判定各杀一次）+ 竞态时序审查（close/放行/mute 交错）+ D2 §3.3 四条实现约束逐条对账 + 测试有效性
- [x] 修复审计发现
- [x] roadmap item 10 → `done`
- [x] `ai-dev/logs/` 收口记录

Exit Criteria:

- [x] 审计发现全部修复
- [x] 测试门全绿
- [x] roadmap item 10 `done` 且会话化 milestone 未翻转（依赖 item 11）
- [x] `ai-dev/logs/` 收口记录

## Closure Gates

- [x] 全部 in-scope 行为实现且有测试（8 组测试齐备）
- [x] Anti-Hollow：租约链 renew→挂起保活→停续→到期放行→事件 运行时连通（测试证明）
- [x] D2 §3.3 四条实现约束逐条落地（谓词局部化/releaseAll/标志维护/出口顺序）
- [x] Deferred 移归（来自 item 9 的单步语义重建）已在本 plan 消费并回归
- [x] owner docs：evt.resumed 产生源（IDebugNotifier 扩展，D1 §3.5 已登记归 item 10）实现即同步；stepX 行为变更 D3 §3.7 已覆盖（无需再动）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2469-xdbg-item10-lease.md --strict` 退出码 0
- [x] No protected-area changes

## Deferred But Adjudicated

（无——本 plan 即 item 9 Deferred 的 successor）

## Non-Blocking Follow-ups

- gateway 侧 lease 时钟计时器/限流/宽限 → item 12。
- `ERR_XLANG_DEBUGGER_THREAD_NOT_SUSPENDED` 错误码 XLangErrors 登记 → item 12/14 additive 批次。

## Closure

Status Note: 租约机制引擎面落地：monitorWait 谓词局部化（per-thread 组合谓词 + 入口复位与 release 同锁临界区——审计 F4 修复）+ 出口判定顺序钉死（探针 a 可杀）+ releaseAll/releaseThread/renewLease 原语 + 授予判定①②（②钉住断言经探针 c 迭代强化——审计 F1 修复）+ stepX 定向重建（全局 stepMode 删除）+ suspendOnce + leaseExpired/resumed 受控扩展（Initializer TCP 日志——审计 F5 修复）。53/53 测试绿（15 新租约用例 + 38 既有零回归）；三组变异探针可杀（a/b/c）；D2 §3.3 四约束对账成立；零保护区。审计 F6 偏差已登记（getLeaseState 两件套替代/接口扩展随 item 12）。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理 closure audit（agent_0ab106ae-3d7b-47cc-90b6-d41a598908b6——首轮 NO-GO；plan 审查 agent_b549cd0e-4cc1-4fa6-a015-5f0616af948c——拦截 B1 登记归属矛盾）
- Evidence:
  - 首轮 audit：三组变异探针（a 出口顺序 RED 2/2、b leaseExpired 回调 RED 1/1、c 授予② FAIL-to-kill → 迭代强化断言后实测可杀）；D2 §3.3 四约束对账 PASS；F4 锁外预发布竞态发现（Major）
  - 修复：F1（判定②钉住断言：断点+零事件）、F4（发布并入锁内临界区）、F5（Initializer 两回调 TCP 日志）、F6（偏差登记）、F2（TestSessionIsolation 改写注记）、F3（本日志条目）
  - 修复后实测：53/53 全绿（BUILD SUCCESS）；零保护区复核（改动面仅 nop-dev-tools + ai-dev/backlog）
  - Deferred 项分类检查：无新增 deferred；取消通道（item 14）与 gateway 时钟（item 12）为上游登记的 successor
  - plan 卫生：Phase 1/Phase 2/Closure Gates 全部勾选；F2/F7 注记落档

Follow-up:

- getLeaseState 复合方法与 IXLangDebugger 接口扩展 → item 12；THREAD_NOT_SUSPENDED 错误码迁入 XLangErrors → item 12 additive 批次；lint-deadline-active 嵌套断点旁路 → item 11/25 关注。
