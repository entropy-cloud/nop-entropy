# 2462 XDBG D2 租约续期与生命周期详设（roadmap item 3）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 3（Stage detail "3. D2 租约续期与生命周期详设"）；`ai-dev/design/xlang-web-debugger/00-overview.md` §一.4/§3.2；`01-ws-protocol.md` §3.4/§3.5（租约消息 schema 与事件）
> Related: plans/2460-xdbg-d0-design-overview（done）、plans/2461-xdbg-d1-ws-protocol（done）

## Purpose

产出 `ai-dev/design/xlang-web-debugger/02-lease-lifecycle.md`：租约模型详设——TTL/续期间隔/宽限参数、renew 帧语义（与心跳合帧与否裁定）、断连/休眠/杀页签边界时序、多标签页租约归属、**resume 与 close 双语义状态机**（到期自动放行 = resume 语义防请求线程泄漏；close 语义仅会话销毁）、到期事件与前端感知路径。

## Current Baseline

- D0 已裁定：挂起超时 = 前端租约续期，前端消失后端不得永久阻塞；到期自动放行 = resume 语义（脚本继续执行完）；**机制警示：现有 `resume()` 是全局放行（`monitorNotify`→`signalAll`），per-thread 自动放行不得直接复用**；close 语义抛 `ERR_XLANG_DEBUGGER_ALREADY_CLOSED` 仅用于会话销毁。
- D1 已登记 schema（语义归本档）：`LeasePolicy={ttlMs:int, renewIntervalMs:int}`、`LeaseState={expiresAtMs:long, suspendedThreadIds:long[]}`；`session.open`→LeasePolicy、`session.rebind`→LeaseState（属主红线：boundUserId 校验归 D1 §3.4）、`lease.renew`{}→LeaseState（request 形态）；事件 `evt.leaseExpired{threadIds}` 与 `evt.resumed{cause:"user"}` **互斥发射**；事件不重放，断连窗口状态经 rebind 快照补齐；`lease.renew` 频率限制"不得快于 renewIntervalMs，阈值归 D2/item 12"。
- 引擎现状（已核验）：`XLangDebugger` 挂起原语 = `ReentrantLock`+`Condition`+`monitorWaitInterval=200ms` 轮询窗口，`close()` 可中断并抛错；`SuspendedThread` 按 threadId 登记、**无 TTL 字段**；`resume()` 全局放行；多线程可并发挂起（`suspendedThreads`）；单步命令作用于 `lastSuspendThread`。
- roadmap item 10 落地锚点："SuspendedThread 级 per-thread TTL（现无 TTL 字段，在 monitorWait 轮询循环内按 deadline 退出）"。

## Goals

- `02-lease-lifecycle.md` 落地：状态机图（Mermaid stateDiagram）、参数表（TTL/renewInterval/宽限 默认值与配置键）、边界时序（正常断连/休眠恢复/杀页签/服务端停机）、多标签页归属裁定、双语义 release 状态机、到期事件路径、与 item 10/12/17 的落地职责分界。
- 设计目录 README 职责表状态更新（02-lease-lifecycle 待立项 → active 链接）。

## Non-Goals

- 租约消息 schema 变更（归 D1）、会话化机制（D3）、实现代码（item 10）、网关执行（item 12）、前端客户端（item 17）。
- 不改 `IDebugger` 接口语义。

## Scope

### In Scope

- `ai-dev/design/xlang-web-debugger/02-lease-lifecycle.md`（新建）
- `ai-dev/design/xlang-web-debugger/README.md`（职责表更新）
- `ai-dev/backlog/xlang-web-debugger-roadmap.md`（item 3 状态 + Stages 表第 3 行 owner plan）
- `ai-dev/logs/2026/10-16.md`（追加）

### Out Of Scope

- 任何代码变更；`docs-for-ai/`；D3-D6 文档。

## Execution Plan

### Phase 1 - 租约生命周期详设文档

Status: in progress
Targets: `ai-dev/design/xlang-web-debugger/02-lease-lifecycle.md`

- Item Types: `Feature`、`Decision`（TTL 参数、宽限、多标签页归属、合帧裁定——均需文档内裁定）

- [x] 写 02-lease-lifecycle.md：①租约模型（per-session 租约 + per-thread 到期判定；新挂起线程的租约授予时机）；②参数表（ttlMs/renewIntervalMs/断连宽限期 默认值 + 配置键命名 + 取值约束；续期帧与 keepalive ping 是否合帧的裁定；`lease.renew` 提前续期的处置裁定——限流错误 vs 宽松接受，rebind 后首个 renew 是否豁免 throttle；`LeaseState.expiresAtMs` 时钟域与客户端排程语义——服务端 epoch ms、休眠唤醒后以服务端值为准）；③双语义 release 状态机（stateDiagram：含**断连/宽限中**状态与 rebind 复原边、挂起中/租约存活/到期放行/close 中止/会话销毁；per-thread 放行的实现约束——不得复用全局 `resume()`）；④**会话存活判定**（D0 §3.2 转派：宽限期满且有挂起线程→放行并销毁；宽限期满且无挂起线程→直接销毁；有续期或有活跃绑定连接→存活；与 rebind 的关系）；⑤多标签页归属裁定（共享租约 vs 最后写者胜，含单步目标交互的 D3 转派声明）；⑥边界时序图（正常断连→宽限→到期放行、页面休眠恢复、直接杀页签、**beforeunload 优雅关闭**（前端主动 session.close 或停续期的语义裁定）、连续断连重连、服务端停机）；⑦到期事件路径（evt.leaseExpired 唯一性、rebind 快照中 LeaseState 的感知路径；**断连窗口内哪些状态可经快照补齐**的裁定——D1 §3.5 转派）；⑧与 item 10（引擎 TTL 字段/monitorWait deadline 退出）/item 12（网关计时器/断连检测）/item 17（前端续期心跳/可见性感知）的职责分界表
- [x] README 职责表：02-lease-lifecycle 行状态更新并加链接

Exit Criteria:

- [x] 状态机图覆盖挂起→（续期|到期放行|close 中止）全部分支 + 断连/宽限中/rebind 复原态，且 resume/close 双语义边界与 D0 §一.4 一致（到期放行不杀线程、close 仅会话销毁）
- [x] 参数表每项有默认值、配置键、取值约束；合帧裁定明确（与 D1 保留方法 `ping/pong` 的关系说清）；renew 提前续期处置与 expiresAtMs 时钟域有裁定
- [x] 多标签页归属有唯一裁定且与 D1 §3.4 属主红线、`session.rebind` 语义相容
- [x] 边界时序覆盖 roadmap 要求的"断网/休眠/杀页签"三类 + beforeunload 优雅关闭 + 双语义 release 区分
- [x] D1 三处转派口子全部显式消费：renew 频率阈值、expiresAtMs 语义、快照可补齐状态清单
- [x] 文档不引用 `ai-dev/analysis/`/`ai-dev/discussions/`；D1 接缝（schema 归 D1）未被越权
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] owner-doc 更新 = 本文档 + README 职责表；`docs-for-ai/` 不动
- [x] `ai-dev/logs/` 执行当日条目已更新（随收口追加）

### Phase 2 - 对抗性评审与收口

Status: completed
Targets: `02-lease-lifecycle.md`、roadmap

- Item Types: `Proof`

- [x] 独立子代理对抗性评审一轮：**对照 roadmap item 3 Goal 与 D0/D1 转派清单逐条核对交付覆盖**、状态机完备性（有无不可达态/死锁路径）、参数可实现性（item 10 引擎侧能否照此落地——monitorWait 200ms 轮询窗口内的 deadline 判定）、边界时序与 D1 事件语义一致性、多标签页裁定自洽性
- [x] 修复评审发现的问题（B1-B3 Blocker + M1-M3 Major + m1-m6 Minor 全部修复；closure audit 复核 GO，附带 N-1/N-2/N-3 已随收口修复）
- [x] roadmap item 3 状态翻转（激活时 `planned`、audit 后 `done`）+ Stages 表第 3 行 owner plan
- [x] `ai-dev/logs/` 收口记录更新

Exit Criteria:

- [x] 评审发现全部修复或书面裁定
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap item 3 `done` 且设计收口 milestone 未翻转
- [x] `ai-dev/logs/` 执行当日条目含本项记录

## Closure Gates

> 纯文档计划：构建验证豁免，doc-links --strict 为机械验证门。

- [x] XDBG-D2-01 交付物齐备且满足 Stage detail 要求（状态机图/参数表/边界时序：断网/休眠/杀页签 + beforeunload/双语义 release 区分）
- [x] 设计评审完成，无未处置 Blocker/Major
- [x] 文档与 live repo 事实一致（monitorWait 轮询、close() 语义、SuspendedThread 无 TTL 字段、resume()=signalAll）
- [x] 无静默降级项
- [x] owner docs 已同步（README 职责表 02-lease-lifecycle → active + 链接）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2462-xdbg-d2-lease-lifecycle.md --strict` 退出码 0
- [x] No build verification required（纯文档计划）

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: 02-lease-lifecycle.md 八项内容齐备（租约模型/参数表含合帧+限流+时钟域/双语义 release 双状态机/会话存活判定/多标签页共享租约/六类边界时序/事件路径与快照补齐/三方职责分界）；对抗性评审 B1-B3 Blocker + M1-M3 Major + m1-m6 Minor 全部修复；独立 closure audit 逐条验证修复落地（含 live code 对照）判 GO，附带 N-1（sessionClosed reason=graceExpired，已同步登记 D1 additive 枚举）/N-2（Grace→DestroyingByClose 停机边）/N-3（授予判定①措辞覆盖销毁中窗口）随收口修复。roadmap Work Item 3 翻 `done`。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（对抗性评审 agent_58d95e9a-df4c-409e-bcb9-272a25e6bd12；closure audit agent_30066538-9adb-40ed-978a-4a61e9d124c8）
- Audit Session: closure audit 由 fresh 子代理执行，与实现会话隔离
- Evidence:
  - 修复验证：B1/B2/B3/M1/M2/M3/m1-m6 全部 PASS（audit 逐条含现文行号与 live code 证据：全局谓词 while(suspended&&!closed)、resume()=monitorNotify→signalAll、close() 抛 ALREADY_CLOSED、isSuspended 经 D1 暴露）
  - audit 结论 GO；附带 N-1/N-2/N-3 已修复（graceExpired 已按 additive 规则登记进 01-ws-protocol.md §3.5 evt.sessionClosed 枚举；Grace 停机边已补；§3.1① 已改"已销毁或销毁中"）
  - 四方一致性（§3.1 授予判定 ↔ 两张状态机 ↔ 存活判定表 ↔ §3.5 时序）经 audit 扫描无新 Blocker/Major
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2462-xdbg-d2-lease-lifecycle.md --strict` 退出码 0
  - Deferred 项分类检查：Deferred 为空；无 in-scope live defect 被降级

Follow-up:

- no remaining plan-owned work
