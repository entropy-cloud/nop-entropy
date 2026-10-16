# 2473 XDBG item 14 在线 eval REPL 服务端（roadmap item 14）

> Plan Status: draft
> Last Reviewed: 2026-10-16
> Source: roadmap Work Item 14；设计权威 `07-repl-security.md`（全量）+ `01-ws-protocol.md` §3.1/§3.7（限制衔接/错误码 additive）+ `03-sessionization.md` §3.4（标记契约插入点②/destroyed 分支取消保护）+ `04-execution-backend-link.md`（调试态解释器降级前提）
> Related: plans/2471/2472（item 12/13 done）；plan 2466（D6 设计收口）
> 保护区声明：`nop-kernel/nop-xlang`（XLangErrors 两码 additive 登记）——随本 plan 申请 audit（Phase 3 独立审计覆盖）

## Purpose

按 D6 落地 REPL 服务端：HTTP compile/execute 两端点（`POST /r/nop-xlang-debug/compile|execute`）、**融合执行器 per-entry 取消检查**（deadline 并入会话调试执行器入口 + destroyed 分支取消保护，ThreadLocal 通道脚本不可见）、Error 系取消异常、沙箱编译面校验（import 默认拒绝/Promise-async 禁用/`__repl*` 保留标识符）、权限校验（401/403）、审计日志、超时与资源限制。完成后翻转"后端调试服务 MVP"里程碑（items 8-14）。

## Current Baseline

- **取消通道插槽已在**：`ReplDeadlineChannel` 接口（session 包，D6 §3.2 executeDestroyed 槽位，"实现归 item 14；一期为 null 纯委托直通"）；`DestroyedSessionExecutor` 构造已收 channel 参数（测试传 lambda）；`SessionDebugManager.init` 的 destroyed 哨兵当前传 null channel。
- **会话调试执行器无取消检查**：`DebugExpressionExecutor.execute` = `!allowBreakPoint → 直通 / checkBreakpoint → 自线程化`，无 deadline 入口——D6 §3.2 融合裁定"deadline/取消检查并入会话调试执行器入口（每入口先查后 checkBreakpoint 再自线程化）"待落地。
- **编译/执行 API（已核验）**：`XLang.newCompileTool().compileFullExpr(SourceLocation, source)` → `ExprEvalAction`；`.invoke(IEvalContext)` 执行（newEvalScope() 返回子类型；TestSessionRoutingIntegration:73/116 先例）；`DebugSessionScope.withSession(handle, work)` 标记窗口覆盖 compile+execute（插入点②）；编译错误抛 NopException 系（message + errorLocation 可承载诊断）。
- **鉴权接缝（已核验）**：`IUserContextExtractor.extractFromHeaders(headers)`（HTTP 头可直读 Authorization，无需 token 参数映射）；`IActionAuthChecker.isPermitted(permission, ISecurityContext)` + `ISecurityContext.getUserContext()`（api-core，BeanContainer 取实现）；host 壳先例 quarkus JAX-RS `QuarkusJsonRpcWebService`（@POST @Path）+ spring `SpringJsonRpcWebService`（@RestController + @ConditionalOnProperty）。
- **网关面（item 12/13）**：`DebugGatewayRuntime`（配置/定时器/鉴权器）、`DebugSessionBinder`（binding.sessionId/sessionOwners（private，本 plan 增公开访问器）/pipe 面）、`XLangDebuggerConfigs` 配置键惯例、`GatewayNotifierBridge.fireCompleted`（item 13 预留口——REPL 提交走同步 HTTP 不经该事件，D6 §3.1 裁定）。
- **错误码**：`XLangErrors` 已有 debug-网关九码；本 plan additive 两码 `ERR_XLANG_DEBUGGER_REPL_CANCELLED`/`ERR_XLANG_DEBUGGER_REPL_TIMEOUT`（保护区）。
- **审计先例**：平台 slf4j 专用 logger 形态（无独立表）；`NOP审计事件表` D6 §3.5 八字段。

## Goals

- **引擎侧（零保护区 + XLangErrors additive）**：
  - `ReplCancelledError extends Error`（session 包）：携带 errorCode（REPL_CANCELLED / REPL_TIMEOUT 二值语义），绕过脚本 catch(Exception)（D6 §3.2 裁定；Promise/async 编译面禁用兜其已知反例）。
  - `ThreadLocalReplDeadlineChannel implements ReplDeadlineChannel`：deadline 为静态 ThreadLocal<Long>（脚本不可见）；**cancelled 为共享 volatile 旗标**（静态 AtomicBoolean——F3 修订：死循环占死执行线程期间外部调用方无法经 ThreadLocal 翻旗标，取消语义必须跨线程可见；deadline 保持线程本所以服务请求间不互扰）；`checkDeadline` 触发即抛 ReplCancelledError（cancelled→REPL_CANCELLED、到期→REPL_TIMEOUT）；`begin(deadline)/cancel()/end()` 生命周期 API（服务层 try/finally；**begin() 复位 cancelled 旗标；cancel 仅限单在飞请求的管理/测试场景**——并发在飞时 cancel 会连带取消他请求，一期无 HTTP 取消端点故生产无调用方）。
  - 融合执行器：`DebugExpressionExecutor` 增可选 channel 入口检查——每入口先 `channel.checkDeadline`（**不 gate allowBreakPoint**，对齐 D6"更宽"裁定）再走既有 checkBreakpoint 分支；`SessionDebugManager` createSession 双形态传入共享 channel 单例；destroyed 哨兵传入同 channel（destroyed 分支取消保护，D6 §3.2 executeDestroyed 语义）。
  - `XLangErrors` additive 两码（保护区，随 plan audit）。
  - `SessionRoutingExecutor.notifyException` 跳过 `ReplCancelledError`（F6 修订：REPL 超时/取消是服务契约内结果，不推 evt.exception 噪声——instanceof 前置短路）。
  - 配置键（XLangDebuggerConfigs）：`nop.xlang.debug-web.repl.execute-timeout-ms`（30000）/`repl.compile-timeout-ms`（10000）/`repl.max-source-bytes`（262144）/`repl.max-concurrent`（4）/`repl.import-whitelist`（空 csv=全拒绝）。
- **服务侧（nop-xlang-debug-web，容器无关核心）**：
  - `ReplService`：`compile(sourcePath?, source)` → `{diagnostics:[{line, column?, severity, message, errorCode?}]}`（编译 try/catch NopException 映射诊断——一期单错误条目，多错误收集 additive）；`execute(sessionId, source, timeoutMs?)` → `{ok, result?, error?, durationMs, cancelled?}`。
  - **compile 门控序**（F1 修订——编译期常量/宏求值会执行代码，非纯防御端点）：登录态（401）→ 权限码 `nop/xlang/debug/ReplCompile`（403）→ source 尺寸（413）→ 沙箱编译面校验（400）→ **compile deadline**（`ThreadLocalReplDeadlineChannel.begin(now + compile-timeout-ms)` 包裹编译，finally end()——编译期求值经全局执行器入口触发检查）→ 诊断映射；审计 action=compile。
  - **execute 门控序**：登录态（extractor null → 401 语义）→ 权限码 `nop/xlang/debug/ReplExecute`（checker 缺失或 false → 403 语义）→ 会话存在 + **属主校验**（`DebugSessionBinder.isSessionOwner(sessionId, principal)`——本 plan 为 sessionOwners 增公开访问器；非属主 → `ERR_XLANG_DEBUGGER_SESSION_BOUND_ELSEWHERE` 语义 403，不区分不存在/他属）→ source ≤256KB（413 语义）→ 白名单/Promise/`__repl*` 编译面校验（400 语义诊断拒绝）→ `debugger.enabled` 执行期谓词复查（`XLangConfigs.CFG_XLANG_DEBUGGER_ENABLED.get()`，false → 403 拒绝服务）→ 全局并发信号量（超限 `ERR_XLANG_DEBUGGER_RATE_LIMITED` 语义）→ 同会话串行（per-session in-flight 标记，超限同 RATE_LIMITED）。
  - **compile deadline 消费**：`repl.compile-timeout-ms` 由 ReplService.compile 读（F1 修订——此前无消费点）；execute deadline = `repl.execute-timeout-ms` 与请求 timeoutMs 取小，上限 120000。
  - 执行：`ThreadLocalReplDeadlineChannel.begin(now + min(timeoutMs, 120000))` → `DebugSessionScope.withSession(handle)` 窗口内 compileFullExpr + invoke（compile 与 execute 同窗口同 deadline，D6 插入点②）→ finally `end()`；结果 JsonTool 字符串化 + 1MB 截断（truncated 标记）；ReplCancelledError → `{ok:false, cancelled:true, error:{errorCode:...}}`；其他异常 → `{ok:false, error:{message, errorCode}}`。
  - `ReplAccessGuard`：extractor + IActionAuthChecker 封装（BeanContainer tryGet；extractor/checker 缺失 → 拒绝——无鉴权不开放红线），401/403 语义异常类型。
  - 审计：专用 logger `nop.xlang.debug.audit`，单行结构化（timestamp/actor/sessionId/action/sourceDigest(SHA-256)/sourcePreview(200 字符)/result/durationMs），compile+execute 全记。
  - **evt.completed 不经 REPL 提交**（D6 §3.1：同步 HTTP 响应承载；GatewayNotifierBridge.fireCompleted 不调用）。
- **宿主壳**：`nop-quarkus-web` JAX-RS 资源 + `nop-spring-web-starter` RestController（照 jsonrpc 壳先例），`@ConditionalOnProperty`/运行态判空挂 `nop.xlang.debug-web.enabled` 门；POST body JSON → core service → JSON 响应；401/403/413 状态码映射。
- **测试**：取消/超时（`while(true){}` 死循环被 deadline 打断——融合执行器真实打断实测）、沙箱（import 拒绝/Promise 拒绝/`__repl` 拒绝/白名单内放行）、权限（未登录 401/无权限 403/跨用户会话拒绝）、属主、门控序、审计字段、结果截断、同会话串行限流、destroyed 分支取消保护、REPL 代码可断点（与编辑器一体语义）。

## Non-Goals

- 运行时沙箱强制（Delta 写/线程/反射拦截——item 25）；异步长执行；多错误收集诊断（一期单条，additive 演进）；独立审计表；servlet async 请求级超时；JsPromise Error 重抛（保护区，item 25）；集群多实例并发协调。

## Scope

### In Scope

- `nop-dev-tools/nop-xlang-debugger`：ReplCancelledError/ThreadLocalReplDeadlineChannel/DebugExpressionExecutor 融合/SessionDebugManager 接线/SessionRoutingExecutor 取消异常短路/配置键
- `nop-kernel/nop-xlang`：XLangErrors additive 两码（保护区）
- `nop-dev-tools/nop-xlang-debug-web`：ReplService/ReplAccessGuard/审计
- `nop-quarkus-web` + `nop-spring-web-starter`：HTTP 壳（+pom 已有 debug-web 依赖，无需新增）
- 测试（debugger + debug-web 两域）

### Out Of Scope

- nop-biz / nop-api-core 改动；前端（item 17/19）；IDEA 通道行为变更（default 会话挂起的 REPL deadline 计入为已声明限制）

## Execution Plan

### Phase 1 - 引擎侧：取消通道 + 融合执行器（含保护区错误码）

Status: completed
Targets: nop-xlang-debugger（Error/Channel/融合执行器/manager 接线/配置键）+ nop-xlang XLangErrors（additive 两码）

- Item Types: `Feature`

- [x] `ReplCancelledError extends Error`（errorCode 承载 CANCELLED/TIMEOUT 二值）
- [x] `ThreadLocalReplDeadlineChannel`：begin/cancel/end/checkDeadline（deadline ThreadLocal + cancelled 共享旗标，脚本不可见）；checkDeadline 抛 ReplCancelledError
- [x] `DebugExpressionExecutor` 融合：入口先 channel.checkDeadline（不 gate allowBreakPoint）再既有分支；`SessionDebugManager` createSession 双形态 + destroyed 哨兵接共享 channel
- [x] `XLangErrors` additive：ERR_XLANG_DEBUGGER_REPL_CANCELLED / ERR_XLANG_DEBUGGER_REPL_TIMEOUT（保护区）
- [x] `SessionRoutingExecutor.notifyException` 前置 ReplCancelledError 短路（F6：REPL 取消/超时不推 evt.exception）
- [x] `XLangDebuggerConfigs` 五配置键（execute-timeout/compile-timeout/max-source-bytes/max-concurrent/import-whitelist）
- [x] 测试：死循环 while(true) 真实打断（deadline 到期 → ReplCancelledError 穿透脚本 catch(Exception)）、cancel() 即刻打断、无 deadline 零影响回归、destroyed 分支取消保护、REPL 代码可断点命中（融合执行器双能力）

Exit Criteria:

- [x] 死循环打断实测绿（Error 系穿透脚本 try/catch(Exception) 断言）——testWhileTrueInterruptedByDeadline/testCancelErrorPiercesScriptCatch/testCrossThreadCancelInterrupts/testDestroyedBranchStillCancels/testNoDeadlineZeroEffect/testReplCodeHitsBreakpoint
- [x] 既有 debugger 模块回归绿（68/68 = 62 基线 + 6 新用例）
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debugger` 全绿（68/68）
- [x] owner-doc：XLangErrors 两码 additive 登记 + D1 §3.7 表 additive 两行（01-ws-protocol.md 已补，标注 HTTP 通道不经 WS）；No other owner-doc update required
- [x] `ai-dev/logs/` 执行当日条目已更新

### Phase 2 - 服务侧：ReplService + 门控/沙箱/审计 + 宿主壳

Status: completed
Targets: nop-xlang-debug-web（ReplService/ReplAccessGuard/审计/DebugSessionBinder.isSessionOwner 访问器）+ quarkus/spring HTTP 壳

- Item Types: `Feature`

- [x] `ReplAccessGuard`：extractor + IActionAuthChecker（BeanContainer tryGet，缺失拒绝）；401/403 语义异常；权限码 `nop/xlang/debug/ReplCompile` 与 `ReplExecute` 两常量
- [x] `DebugSessionBinder.isSessionOwner(sessionId, userId)` 公开访问器（F2 修订：属主校验的访问路径）
- [x] `ReplService`：compile（门控序 + **compile deadline** + 诊断映射）+ execute 全门控序（登录/权限/属主/尺寸/沙箱编译面/enabled 复查/全局并发/同会话串行）+ deadline 窗口执行 + 结果字符串化截断 + 审计输出（compile/execute 双 action）
- [x] 沙箱编译面校验：import 白名单（默认空=全拒绝）、Promise/async/await 词法拒绝、`__repl*` 保留标识符拒绝。**词法近似边界诚实声明（F5）**：源文本扫描对字符串/注释中的同名词误伤（假阳=可用性损失，一期接受）、编译期宏/生成代码可绕过源文本扫描（假阴=安全残余）；两项登记 item 25 复核清单扩展，测试覆盖词法面（正反用例）
- [x] 宿主壳：quarkus JAX-RS `NopXlangDebugReplService`（@Path("/r/nop-xlang-debug")）+ spring RestController；状态码映射 401/403/413/400
- [x] 测试：权限三态（401/403/跨用户会话 403 语义）compile+execute 双端点、门控序逐条、沙箱三拒绝 + 白名单放行、execute 端到端（真会话 + 死循环超时 → cancelled/timeout 响应）、**挂起计入超时**（REPL 代码断点挂起超 deadline → resume 后首入口 REPL_TIMEOUT，F9 钉子）、结果截断、同会话串行 RATE_LIMITED、compile 超时（编译期求值死循环被 compile deadline 打断；若 full-expr 模式造不出编译期用户控制流，允许以编译期常量求值 `[[const]]` 宏构造或通道级单测退化承接——执行期如实登记构造方式）、审计字段齐备（compile+execute 双 action）、壳层状态码映射（quarkus/spring 至少编译通过 + 壳逻辑单测）

Exit Criteria:

- [x] D6 §3.4 权限矩阵/限制表逐条有测试对应（401/403/属主 403 同码/429 同会话串行/413 尺寸/沙箱 400/结果 1MB 截断/超时 300ms 实测打断——TestReplService 11 用例）
- [x] **端到端验证**：壳→门控→沙箱→标记窗口→融合执行器→deadline 打断→响应（service.execute 端到端 11 用例 + 壳状态码映射编译过；执行路径直接以会话融合执行器为根入口——执行中发现经全局执行器 choke point 在测试/嵌入环境会绕过 deadline 检查，jstack 实测 while(true) 永转后修复）
- [x] **接线验证**：ThreadLocal channel 确被融合执行器运行时消费（testExecuteDeadlineInterruptsInfiniteLoop）；owner 校验确走 sessionOwners（isSessionOwner + testGateOrder 跨用户/不存在同码）
- [x] 无静默跳过：checker/extractor 缺失显式拒绝（guard 401/403）；白名单空=全拒绝（testSandboxValidation）
- [x] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` 全绿（46/46 + 依赖链）；quarkus/spring 编译通过
- [x] `ai-dev/logs/` 执行当日条目已更新

### Phase 3 - 独立审计与收口（含保护区 audit）

Status: planned
Targets: 全部、roadmap、M3 里程碑

- Item Types: `Proof`

- [ ] 独立子代理 closure audit：D6 逐节对账（§3.1 契约/§3.2 取消机制三选一与融合形态/§3.3 沙箱规则表/§3.4 权限矩阵与限制表/§3.5 审计字段）+ 保护区 XLangErrors 两码 additive 核对 + 变异探针（deadline 检查/权限门禁/沙箱拒绝）+ 并发（信号量/同会话串行）+ Anti-Hollow + 残余风险清单与 D6 §3.3 一致性
- [ ] 修复审计发现
- [ ] roadmap item 14 → `done`；**Milestone 后端调试服务 MVP 翻转 done**（items 8-14 全 done）
- [ ] `ai-dev/logs/` 收口记录

Exit Criteria:

- [ ] audit 发现全修复
- [ ] 测试门全绿（两域）
- [ ] roadmap item 14 `done` 且 MVP milestone 翻转 `done`
- [ ] `ai-dev/logs/` 收口记录

## Closure Gates

- [ ] 全部 in-scope 行为实现且有测试
- [ ] Anti-Hollow：HTTP 壳→门控→沙箱→标记窗口→融合执行器→取消→响应全链运行时连通
- [ ] D6 §3.1-§3.5 逐节对账无遗漏（限制表衔接 D1 §3.1 不重定义）
- [ ] owner docs：XLangErrors 两码 + D1 §3.7 表两行 + 配置键描述完整；无其他 owner-doc 漂移
- [ ] 独立子代理 closure audit 完成并记录证据（含保护区 audit）
- [ ] `./mvnw test -pl nop-dev-tools/nop-xlang-debug-web -am` 通过
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-dev-tools/nop-xlang-debug-web --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2473-xdbg-item14-repl-server.md --strict` 退出码 0
- [ ] 改动面仅声明文件（debugger 六文件[ReplCancelledError/ThreadLocalReplDeadlineChannel/DebugExpressionExecutor/SessionDebugManager/XLangDebuggerConfigs/SessionRoutingExecutor] + XLangErrors + debug-web 四文件[ReplService/ReplAccessGuard/审计/DebugSessionBinder 访问器] + 两宿主壳 + 测试）

## Deferred But Adjudicated

### 多错误编译诊断收集
- Classification: `optimization candidate`
- Why Not Blocking Closure: 一期单条诊断已满足诊断桥最小契约；D6 schema 为 additive 演进面
- Successor Required: `no`

### 挂起感知 deadline（挂起时间不计入超时窗）
- Classification: `optimization candidate`
- Why Not Blocking Closure: D6 §3.4 明示"绝对时钟，断点挂起时间计入"为一期限制并声明调试 REPL 应调高 timeoutMs
- Successor Required: `no`

### servlet async 请求级超时 / 独立审计表 / 运行时沙箱强制 / JsPromise Error 重抛 / 词法沙箱假阳假阴面
- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 全部为 D6 §3.3/§3.4 显式登记的 item 25 复核项或 follow-up；一期纵深防御四要素（白名单×权限×时间窗×审计）已成立
- Successor Required: `yes`
- Successor Path: item 25 安全深度审计

## Closure

Status Note: （待收口后填写）
Completed: （待填写）

Closure Audit Evidence:

- Reviewer / Agent: （待填写）
- Evidence: （待填写）
