# 2463 XDBG D3 调试器会话化详设（roadmap item 4）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 4（Stage detail "4. D3 调试器会话化详设"）；`00-overview.md` §一.3/§3.3（会话模型与默认会话兜底语义）；`01-ws-protocol.md`（会话消息）；`02-lease-lifecycle.md` §3.3（per-thread 定向唤醒消费方归 D3）
> Related: plans/2460/2461/2462（均 done）

## Purpose

产出 `ai-dev/design/xlang-web-debugger/03-sessionization.md`：会话化机制详设——方案 A（全局 executor 外壳内按会话路由）vs 方案 B（runtime 级注入）裁定、会话标识注入与嵌套调用透传机制、每会话 `XLangDebugger` 实例生命周期、多线程挂起时单步目标策略、IDEA 插件默认会话兼容方案。实现归属 item 8/9。

## Current Baseline

- **执行器链路（已核验，双机制）**：`EvalExprProvider._executor` 静态槽位（nop-core，公共 API `registerGlobalExecutor` 已存在，无需改动）；`IExpressionExecutor.execute(IExecutableExpression, EvalRuntime)` 单方法。嵌套传递是**双机制**：执行树内 executor 作为方法接收者逐层传递（`expr.execute(this, rt)`）；但**函数/标签调用边界一律重读全局静态槽位并新建 `EvalRuntime`**——再取点包括 `ExecutableFunction.invoke/call0-3`（`XLang.getExecutor()` + `new EvalRuntime(scope)`）、`ExecutableFunctionEvalAction.doInvoke`、`XLangSemantics`、`IXplTag`，树中段还有 `EvalBackendRouter`/`EvalStaticDegradedExecutable`/`BetweenOpExecutable`/`CompareOpExecutable` 等再取点。**推论**：基于 EvalRuntime 字段携带会话标识会在每个调用边界被丢弃（方案 B 的量化侵入面）；基于 ThreadLocal 的路由外壳在边界重取时仍拿到同一个全局外壳实例、按当前线程标识重新路由（方案 A 的机制性优势）。`EvalRuntime` 字段仅 `scope/exitMode/currentFrame/out`（无会话/executor 字段）。`DebugExpressionExecutor.execute`：`!expr.allowBreakPoint()` 直通，否则 `debugger.checkBreakpoint(loc, rt)`。
- **引擎实例态（已核验）**：`XLangDebugger` 全部状态为实例字段（`suspendedThreads`/`lastSuspendThread`/`stepMode`/`tempBreakpoint`/`notifier`/`suspended`/`closed` 全局标志/`monitorLock`/`cleanupThread`，唯一 static 是 LOG）。**`stepX()` 现状无定向语义**：`stepInto/stepOver/stepOut` 仅用 `lastSuspendThread` 做非空守卫，实际执行 `monitorNotify`——置全局 stepMode、清全局 `suspended`、`signalAll` 唤醒**全部**挂起线程；stepOver/Out 判定按 currentThreadId 查表 + 全局单字段 stepMode，多线程下存在语义竞争。多线程单步定向语义需配合 D2 §3.3 谓词线程局部化**从零建立**，不是扩展。`BreakpointManagerImpl` 断点表按 externalPath 索引。`XLangDebuggerInitializer.destroy()` 将全局 executor 重置回 `DefaultExpressionExecutor` 并 close debugger——serverShutdown（D2 §3.5）需与其衔接。
- **D0 已裁定（本档前提）**：会话化落点归本档裁定（组件图以中性"会话化落点"占位）；`enabled=true` 且 web 未激活 → 注入现状形态 executor（绑定默认会话唯一实例）；web 激活 → 无 web 会话标识的执行走**默认会话兜底**（IDEA 全局断点语义不变），"空转直通"已显式不采纳；`enabled=false` 路径零改动。
- **D1/D2 已裁定**：会话与连接 1:1 绑定、`session.open/rebind/close` 消息、属主红线（boundUserId）；多标签页共享租约；per-thread 定向唤醒原语的消费方 = 本档的单步目标选择；多标签页并发单步的断点表"后写覆盖"（D2 §3.4）。
- **IDEA 通道（已核验）**：`XLangDebuggerInitializer` 在 `nop.xlang.debugger.enabled=true` 时建 `SimpleRpcServer` + `addServiceImpl(IDebugger.class, debugger)` + `registerGlobalExecutor(new DebugExpressionExecutor(debugger))`；`nop-idea-plugin` 经 TCP 用 `IDebuggerAsync` 代理消费，零改动目标。
- **保护区**：方案 B 若动 `EvalRuntime`（nop-core）/执行 choke point（`XLang.java`，nop-xlang）触发 plan-first；方案 A 只落 `nop-dev-tools` 内新类，不触发保护区。

## Goals

- `03-sessionization.md` 落地：①方案 A/B 裁定（含理由与拒绝记录）；②会话标识注入机制（载体、who sets、嵌套 action/函数调用透传保证、跨线程行为裁定）；③每会话实例生命周期（创建/销毁时机、与 D2 会话状态机的对接）；④会话路由规则（有标识→会话实例；无标识→默认会话兜底；会话已销毁→按 D2 授予判定①）；⑤单步目标策略（stepX 作用域、多线程挂起、多标签页并发单步）；⑥IDEA 兼容（默认会话语义、回归基线）；⑦与 item 8/9 的实现职责分界与测试要求锚点。
- 设计目录 README 职责表状态更新。

## Non-Goals

- 租约语义（D2）、协议（D1）、静态绑定绕过路径消除（D4，但本档需声明会话化与其的接缝：路由 executor 只对 interpreter 可断点路径生效）、实现代码（item 8/9）。
- 不改 `IExpressionExecutor` SPI、`EvalRuntime`、`XLang.execute` choke point（若裁定方案 A 则全部不需要动）。

## Scope

### In Scope

- `ai-dev/design/xlang-web-debugger/03-sessionization.md`（新建）
- `ai-dev/design/xlang-web-debugger/README.md`（职责表更新）
- `ai-dev/backlog/xlang-web-debugger-roadmap.md`（item 4 状态 + Stages 表第 4 行 owner plan）
- `ai-dev/logs/` 执行当日条目

### Out Of Scope

- 任何代码变更；`docs-for-ai/`；D4-D6 文档。

## Execution Plan

### Phase 1 - 会话化详设文档

Status: completed
Targets: `ai-dev/design/xlang-web-debugger/03-sessionization.md`

- Item Types: `Feature`、`Decision`（方案 A/B、会话标识载体、单步目标策略、跨线程透传边界）

- [x] 写 03-sessionization.md：①方案裁定（A：单一全局 executor 外壳 + ThreadLocal 会话标识路由；B：runtime 级注入 `EvalRuntime`/choke point——逐条给理由与拒绝原因，含保护区/侵入面/IDEA 兼容/性能四维对比；**侵入面对比必须引用双机制事实**：EvalRuntime 字段在函数/标签调用边界随 `new EvalRuntime` 丢失，需改全部再取点（nop-xlang 保护区）；ThreadLocal 方案在边界重取全局槽位时天然存活）；②会话标识注入（载体裁定：执行入口 ThreadLocal 标记；**set/clear 插入点按 live 入口点枚举**——REPL/调试触发入口执行前 set、finally 恢复；**编译期排除裁定**：set 窗口是否覆盖 compile 期求值必须显式裁定；**嵌套重入裁定**：重入 set 采用 save-restore 而非盲 clear；产出 item 12/14/21 可消费的**标记契约**（API 形态语义，非代码））；③跨线程边界（显式新线程/线程池不携带标识的行为裁定与理由）；④路由规则表（有标识+会话存活→该会话实例；有标识+会话已销毁→D2 §3.1 授予判定①衔接；无标识→默认会话兜底；`enabled=true` 且 web 未激活→现状形态直注）；⑤每会话实例生命周期（创建时机=session.open、销毁=按 D2 DestroyingBy*、归属清单含 BreakpointManagerImpl/suspendedThreads/租约/**notifier 接线**、`suspended/closed` 标志与 cleanupThread；与服务端停机 `XLangDebuggerInitializer.destroy()` 的衔接——D2 §3.5 serverShutdown）；⑥单步目标策略（**现状为全局释放**的重建方案：stepX 目标语义配合 D2 §3.3 谓词线程局部化从零建立——目标选择规则（默认 lastSuspendThread）、多线程挂起时的语义、多标签页并发单步裁定、指定线程单步的 additive 协议演进路径、**per-thread 定向唤醒原语的行为契约**——D2 §3.3 转派的显式承接）；⑦IDEA 兼容（默认会话预置、SimpleRpcServer 绑定不变、回归基线定义）；⑧与 item 8/item 9 的职责分界与测试锚点
- [x] README 职责表：03-sessionization 行状态更新并加链接

Exit Criteria:

- [x] 方案裁定唯一且有四维（保护区/侵入面/IDEA 兼容/性能）理由；侵入面对比引用双机制事实（调用边界重取点清单）；被拒方案有记录
- [x] 会话标识载体唯一裁定；透传保证覆盖**接收者链与全局槽位再取点双机制**；set/clear 插入点按 live 入口点枚举且含编译期窗口与嵌套重入（save-restore）裁定；跨线程行为显式裁定（不得悬空）；产出 item 12/14/21 可消费的标记契约
- [x] 路由规则覆盖四种情形（有标识存活/有标识已销毁/无标识/enabled 配置组合）且与 D0 §3.3 规则 3、D2 §3.1① 一致
- [x] 单步目标策略基于"现状全局释放"重建且与 D2 §3.3（stepX 不发事件、定向唤醒消费方）相容；per-thread 定向唤醒原语行为契约成文；多标签页并发单步有裁定
- [x] 生命周期归属含 notifier 接线与 serverShutdown 衔接
- [x] IDEA 兼容方案保证 `nop-idea-plugin` 零改动且回归基线可验证
- [x] 文档不引用 `ai-dev/analysis/`/`ai-dev/discussions/`；D1/D2 接缝未越权
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] owner-doc 更新 = 本文档 + README 职责表；`docs-for-ai/` 不动
- [x] `ai-dev/logs/` 执行当日条目已更新（随收口追加）
- [x] No new test required：纯文档计划，无代码变更（guide rule 25）

### Phase 2 - 对抗性评审与收口

Status: completed
Targets: `03-sessionization.md`、roadmap

- Item Types: `Proof`

- [x] 独立子代理对抗性评审一轮：**想象性分析**（item 8 工程师能否照此实现路由 executor——ThreadLocal set/clear 的插入点是否明确、嵌套调用透传论证是否经得起执行树推敲、双会话并发测试能否按测试锚点写出）；与 D0/D1/D2 三文档一致性；方案对比是否有漏维
- [x] 修复评审发现的问题（P1 宽限路径竞态 dispose 收尾+出口判定顺序、P2 双层分工、P3 suspendOnce+归属清单，及 M1-M8；closure audit 复核 11/11 PASS 判 GO）
- [x] roadmap item 4 状态翻转（激活时 `planned`、audit 后 `done`）+ Stages 表第 4 行 owner plan
- [x] `ai-dev/logs/` 收口记录更新

Exit Criteria:

- [x] 评审发现全部修复或书面裁定
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap item 4 `done` 且设计收口 milestone 未翻转
- [x] `ai-dev/logs/` 执行当日条目含本项记录

## Closure Gates

> 纯文档计划：构建验证豁免，doc-links --strict 为机械验证门。

- [x] XDBG-D3-01 交付物齐备且满足 Stage detail 要求（裁定+理由+透传机制+生命周期图+IDEA 兼容评估）
- [x] 设计评审完成，无未处置 Blocker/Major
- [x] 文档与 live repo 事实一致（双机制传递链与再取点、stepX 全局释放现状、实例字段、Initializer 注入/销毁点——audit 逐点核验零偏差）
- [x] 无静默降级项
- [x] owner docs 已同步（README 职责表 + D1 触发列 + D0 M2 限定）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2463-xdbg-d3-sessionization.md --strict` 退出码 0
- [x] No build verification required（纯文档计划）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 03-sessionization.md 五要素齐备（方案 A 四维裁定+双机制传递链论证拒绝方案 B / 标记契约含 save-restore、编译期窗口、插入点枚举、withSession 裁定 / 路由规则表六情形含双层分工钉死 / 生命周期图含 graceful/abort 两种收尾与出口判定顺序 / 单步定向重建含 per-thread 唤醒契约与 suspendOnce / IDEA 零改动+显式行为变更声明）。对抗性评审 P1-P3 Major + M1-M8 Minor 全部修复；独立 closure audit 逐条验证（含 live code 逐点核验）判 GO。连带修订 D1 错误码触发列与 D0 M2 判据限定，消除跨文档矛盾。roadmap Work Item 4 翻 `done`。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（对抗性评审 agent_f55c8e29-6440-4500-bf2f-90a9bbb7c172；closure audit agent_114caa73-9ba4-436a-ba28-2a33c6fea948）
- Audit Session: closure audit 由 fresh 子代理执行，与实现会话隔离
- Evidence:
  - 修复验证：P1/P2/P3/M1-M8 共 11 项全部 PASS（audit 含 live code 锚点：monitorWait L540 全局谓词与 L553-555 无条件抛错——P1 根因确凿；ExecutableFunction 5 处重取点；stepInto 静默 no-op L149-156）
  - 新矛盾扫描：dispose vs D2 releaseAll、出口判定 vs D2 close 语义、suspendOnce vs D1 suspend 映射、§3.7 vs D0 M2——全部相容（细化非矛盾）；audit 附带措辞项（abort 括注）已随收口统一
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2463-xdbg-d3-sessionization.md --strict` 退出码 0；grep analysis/discussions 0 命中
  - Deferred 项分类检查：Deferred 为空；无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work
