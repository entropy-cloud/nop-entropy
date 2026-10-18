# 2461 XDBG D1 WebSocket 调试协议详设（roadmap item 2）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 2（Stage detail "2. D1 WS 协议详设"）；`ai-dev/design/xlang-web-debugger/00-overview.md` §3.4/§3.7（传输容器与辖区裁定）
> Related: plans/2460-xdbg-d0-design-overview（已完成）

## Purpose

产出 `ai-dev/design/xlang-web-debugger/01-ws-protocol.md`：浏览器↔网关的 JSON-RPC envelope 协议详设——消息集与 `IDebugger` 方法映射表、受控扩展事件登记、鉴权裁定（浏览器 WS 无自定义头硬约束）、版本化与错误语义、示例报文；另以独立章节记录与 CSV 文本协议的对照及不采纳理由（XDBG-D1-02）。

## Current Baseline

- D0 已裁定（00-overview.md §3.4/§3.7，本计划的硬前提）：JSON-RPC 2.0 over WS、独立端点 `/ws/xlang-debugger`、`IUserContextExtractor` 握手鉴权 + 4401 红线、4409 仅复用码值（处置强度归 D1）、无 id 消息仅服务端出站、租约消息 schema 归 D1 / 语义归 D2、消息集层唯一仲裁基准是 D1 文档。
- `IDebugger`（nop-api-debugger）方法面（已核验签名，自有方法 **18 个**）：`stepInto/stepOver/stepOut/suspend/resume/waitSuspended/close/muteBreakpoints(muted)/updateBreakpoints(bps,muted)/isSuspended/isBreakpointsMuted/getSuspendedThreads/runToPosition(bp)/getExprValue(threadId,frameIndex,expr)/expandExprValue(threadId,frameIndex,expr,keys)/getStackInfo(threadId)/getScopeVariables(threadId)/getFrameVariables(threadId,frameIndex)`；`IBreakpointManager`：`addBreakpoint/removeBreakpoint/setBreakpoints/getBreakpoints/clearBreakpoints/getBreakpointAt`（6 个；对账口径 18+6=24）。数据类型：`Breakpoint{sourcePath,line,condition,logExpr}`、`DebugVariable{name,type,value,kind,scope,index,ownerClass,hash,assignLoc}`（`assignLoc` 类型为 `LineLocation`）、`DebugValueKey{name,index,ownerClass}`、`StackInfo{threadId,threadName,stackTrace}`（`topElement`/`topLocation` 为 `@JsonIgnore` 派生属性，不出站）（`stackTrace` 元素为自有类 `io.nop.api.debugger.StackTraceElement{sourcePath,line,funcName}`，非 JDK 同名类）、`ThreadInfo{threadName,threadId,suspended}`、`LineLocation{sourcePath,line}`、`BreakpointHitMessage{type,stackInfo}`。
- 引擎事件回调面 `IDebugNotifier`（nop-xlang-debugger，7 个方法）：`notifyWarn/notifyLog/notifySuspend/notifyStepInto/notifyStepOver/notifyStepOut/notifyBreakAt(SuspendedThread, SourceLocation, EvalRuntime)`——协议事件登记表必须给出这 7 个回调到出站事件的合一/拆分裁定。
- `JsonRpcWebSocketHandler` 现有语义：method 分发（**保留方法名 `ping/pong/unsubscribe/tokenRefresh`**——调试方法命名空间必须避让或显式接管）、`IUserContextExtractor` 绑定身份、4401/4403/4400/4409 关闭码、10s keepalive；错误响应形态为 `error.errorCode`（平台字符串码）+ `error.data`。`ILoginService.extractFromHeaders` 默认读 `Authorization`（Bearer）。
- 鉴权平台现状（裁定输入）：quarkus/spring 端点均把握手 HTTP 头全量（含 lowercase `cookie`）传入 headers map，但 `extractFromHeaders` 只读 `Authorization`；平台鉴权链路无 cookie 读取/签发路径（biz-auth-core 无生产 cookie 鉴权）。
- 浏览器硬约束（已核验）：flux `env.openSocket` 的 `headers` 选项在浏览器原生实现中被忽略——浏览器 WS 握手只能靠 cookie / URL 参数 / subprotocol / 首帧鉴权之一。
- `nop-idea-plugin` 经 TCP `SimpleRpcServer` + `IDebuggerAsync` 消费，不经过本协议；本协议是新增消费面，不改动 `IDebugger` 接口。

## Goals

- XDBG-D1-01：`01-ws-protocol.md` 落地，含：envelope 字段 schema、方法集与 `IDebugger` 映射表（含"不暴露"项及理由）、受控扩展事件登记（suspended/resumed/completed/leaseExpired/log/exception/sessionClosed 等）、鉴权具体方案裁定、版本化与错误码语义、关闭码映射表、尺寸限制、时序图与示例报文。
- XDBG-D1-02：CSV 协议对照与不采纳理由（文档独立章节，不引用 analysis）。
- 设计目录 README 职责表状态更新（01-ws-protocol 待立项 → 链接）。

## Non-Goals

- 租约 TTL 参数与状态机（D2）、会话化机制（D3）、网关实现（item 12）、前端客户端实现（item 17）。
- 不改 `IDebugger`/`IBreakpointManager` 接口与 `JsonRpcWebSocketHandler` 现有行为。

## Scope

### In Scope

- `ai-dev/design/xlang-web-debugger/01-ws-protocol.md`（新建）
- `ai-dev/design/xlang-web-debugger/README.md`（职责表更新）
- `ai-dev/backlog/xlang-web-debugger-roadmap.md`（item 2 状态 + Stages 表第 2 行 owner plan）
- `ai-dev/logs/2026/10-16.md`（当日日志追加）

### Out Of Scope

- 任何代码/模块变更；`docs-for-ai/`；D2-D6 文档。

## Execution Plan

### Phase 1 - 协议详设文档

Status: completed
Targets: `ai-dev/design/xlang-web-debugger/01-ws-protocol.md`

- Item Types: `Feature`（协议设计文档）、`Decision`（鉴权方案、waitSuspended 暴露形态、renew 请求/通知形态、错误码承载位置、入站无 id 处置——均需在文档中裁定）

- [x] 写 01-ws-protocol.md：①envelope schema（JSON-RPC 2.0 字段 + 约束：消息尺寸上限、expr 长度上限；声明容器保留方法名 `ping/pong/unsubscribe/tokenRefresh` 的避让规则）；②鉴权裁定（四选一：cookie/查询参数/subprotocol/首帧，含各候选拒绝/采纳理由与硬约束回放，cookie 候选须引平台无 cookie 鉴权链路事实）；③方法集映射表（`IDebugger` 18 个自有方法 + `IBreakpointManager` 6 个方法逐一映射 → JSON-RPC method 名或显式"不暴露"+理由，含 waitSuspended/close 映射裁定）；④受控扩展事件登记表（服务端 notification 事件集：命名统一；含 `IDebugNotifier` 7 个回调 → 事件的合一/拆分裁定，`completed`/`leaseExpired`/`sessionClosed` 属新增产生源须显式标注）+ 租约消息（lease.renew：schema 归 D1、TTL/状态机语义引 D2）；⑤会话生命周期消息（session.open/session.rebind/session.close——**仅 method 名与 payload schema 登记**，重连恢复与双语义状态机归 D2，"rebind" 命名消歧避免与 `IDebugger.resume()` 全局放行混淆）；⑥版本化规则（additive-only、-32601 未知方法、未知字段忽略）；⑦错误语义（标准 JSON-RPC 码 + **平台错误码承载位置裁定**——基座现状 `error.errorCode`+`error.data`，若偏离须给理由；**入站无 id 请求的处置裁定**——拒绝/报错/关闭）；⑧关闭码映射表（4400/4401/4403/4409 调试语境；4409 须显式裁定**触发条件**与**处置强度**（拒绝新会话绑定 vs 断开整条连接），不得照搬"重复订阅即断连"）；⑨时序图（正常调试流/断连重连恢复流）+ 示例报文（请求/响应/事件至少各 2 例）；⑩CSV 协议对照与不采纳理由章节
- [x] README 职责表：01-ws-protocol 行状态 `待立项` → active 并加链接

Exit Criteria:

- [x] `IDebugger` 18 个自有方法 + `IBreakpointManager` 6 个方法（合计 24）在映射表中**逐一**出现（映射为 method 名、或显式"不暴露"+理由），无遗漏
- [x] 鉴权裁定明确唯一方案且与 D0 锚定一致（4401 红线、`IUserContextExtractor` 复用），四个候选各有拒绝/适用理由
- [x] 扩展事件在登记表中显式列出 payload 字段 schema，suspended 族事件的 payload 与 `BreakpointHitMessage`/`StackInfo` 字段对齐，且含 `IDebugNotifier` 7 回调的合一/拆分裁定
- [x] 五项 D0 点名裁定全部落档：鉴权方案、4409 触发条件+处置强度、入站无 id 处置、错误码承载位置、租约消息 schema（语义引 D2）
- [x] 文档不引用 `ai-dev/analysis/`/`ai-dev/discussions/`；CSV 对照章节自包含
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] owner-doc 更新 = 本文档自身 + README 职责表；`docs-for-ai/` 不动
- [x] `ai-dev/logs/` 执行当日条目已更新（与 item 1 同日文件，随收口追加）

### Phase 2 - 对抗性评审与收口

Status: completed
Targets: `01-ws-protocol.md`、roadmap

- Item Types: `Proof`

- [x] 独立子代理对抗性评审一轮：schema 可实现性（item 12 网关能照此实现）、映射表完整性（24 方法对账）、鉴权方案与浏览器约束相容、与 D0/D2 辖区声明一致性、示例报文可解析性
- [x] 修复评审发现的问题（B1-B2 Blocker + M1-M9 Major + m1-m7 Minor 首轮修复；closure audit 复核发现 N-1 Major + N-2~N-6 Minor，二轮修复并机械验证）
- [x] roadmap item 2 状态翻转（激活时已翻 `planned`；本项为 audit 后翻 `done`）+ Stages 表 owner plan 修正
- [x] `ai-dev/logs/` 收口记录更新

Exit Criteria:

- [x] 评审发现全部修复或书面裁定
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap item 2 `done` 且设计收口 milestone 未翻转
- [x] `ai-dev/logs/2026/10-16.md` 含本项记录

## Closure Gates

> 纯文档计划：构建验证豁免，doc-links --strict 为机械验证门。

- [x] XDBG-D1-01、XDBG-D1-02 交付物齐备且满足 Stage detail 要求（消息 schema/时序图/映射表/鉴权裁定/扩展事件登记/示例报文/CSV 对照）
- [x] 设计评审完成，无未处置 Blocker/Major
- [x] 文档与 live repo 事实一致（`IDebugger` 方法签名、关闭码、auth 头链路、StackInfo 序列化行为）
- [x] 无静默降级项（一次性调试票据为已裁定的 deferred，见下）
- [x] owner docs 已同步（design README 职责表 01-ws-protocol → active + 链接）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2461-xdbg-d1-ws-protocol.md --strict` 退出码 0
- [x] No build verification required（纯文档计划）

## Deferred But Adjudicated

### 一次性调试票据（one-time ticket）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 一期以平台现有短 TTL access token + `tokenRefresh` 旋转达标；一次性票据涉及新签发/核销端点，属安全加固而非协议闭环必需项（01-ws-protocol.md §3.2/§四）。
- Successor Required: `yes`
- Successor Path: roadmap item 25（安全深度审计）复核；如需实施由该审计产生的 remediation plan 承接。

## Non-Blocking Follow-ups

（无其余——集群转发/协同/热发布为 roadmap 级 follow-up，非本 plan 债务）

## Closure

Status Note: 01-ws-protocol.md 十项内容齐备（envelope+约束/鉴权四选一裁定/24 方法逐一映射/事件登记/会话租约消息/版本化/错误语义+错误码登记总表/关闭码映射/时序+6 例报文/CSV 对照）；首轮对抗性评审 B1-B2 Blocker + M1-M9 Major + m1-m7 Minor 全部修复；独立 closure audit 复核确认修复落地并发现 N-1（StackInfo @JsonIgnore 序列化事实）+ N-2~N-6，二轮修复经机械验证。roadmap Work Item 2 翻 `done`，设计收口 milestone 保持 `todo`。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（对抗性评审 agent_4fc09fc9-d702-458d-91b1-d3ad39cebc15；closure audit agent_ac722009-00bf-4ffe-95fd-5c0eff9e1b6f）
- Audit Session: closure audit 由 fresh 子代理执行，与实现会话隔离
- Evidence:
  - 首轮修复验证：B1/B2/M1-M9 全部 PASS（audit 逐条含 live code 证据：JsonRpcRequest.id=String、handlePong 为 no-op、XLangErrors:1025 ALREADY_CLOSED 既有、BreakpointHitMessage type 六值枚举对齐、24 方法逐一核对）
  - audit 复核发现 N-1~N-6 已修复并 grep/机械验证：topElement 仅存于 @JsonIgnore 说明行；cause 枚举收窄 `"user"`；tokenRefresh 注明 Authorization 映射否则刷新必 4401；示例共 6 例（3 请求/2 响应含 1 错误/2 事件）；plan Phase 1 Status=completed
  - audit 观察项（D0 §3.2 "重连后补推" vs D1 "事件不重放"）已同步修订 00-overview.md 时序图措辞（rebind 快照感知），消除 M1 门潜在矛盾点
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2461-xdbg-d1-ws-protocol.md --strict` 退出码 0
  - Deferred 项分类检查：唯一 deferred（一次性票据）= out-of-scope improvement，附 Why Not Blocking + successor path，分类诚实；无 in-scope live defect 被降级

Follow-up:

- 一次性调试票据 → roadmap item 25 安全审计复核。
