# 2466 XDBG D6 在线 eval REPL 与安全边界详设（roadmap item 7）

> Plan Status: completed
> Last Reviewed: 2026-10-16
> Source: `ai-dev/backlog/xlang-web-debugger-roadmap.md` Work Item 7（Stage detail "7. D6 在线 eval REPL 与安全边界详设"）；`00-overview.md` §3.6（范围裁定）；`01-ws-protocol.md`（REPL 消息面）/`02-lease-lifecycle.md`/`03-sessionization.md`（会话标记）/`04-execution-backend-link.md`（调试态解释器降级）/`05-frontend-architecture.md`（诊断端点 open question 归本档）
> Related: plans/2460-2465（均 done）

## Purpose

产出 `ai-dev/design/xlang-web-debugger/07-repl-security.md`：REPL 服务契约（compile+execute 端点）、取消机制三选一裁定（硬约束：XLang 引擎无 interrupt/cancel/deadline/stepLimit，`while(true)` 占死请求线程）、沙箱规则表、权限矩阵、审计事件、超时与资源限制；并兑现 05 登记的 open question（编译诊断端点与 payload schema owner）。

## Current Baseline

- **deadline 机制已有落地先例（已核验，修正 roadmap 过时前提）**：`LintDeadlineExecutor` + `XScriptDeadline` + `XScriptTimeoutException`（nop-lint-core，2026-09-22 落地）——经 `EvalExprProvider.registerGlobalExecutor` 安装的**全局 executor 装饰器**，每次 executor 入口检查 deadline（**覆盖循环 back-edge，不 gate 在 allowBreakPoint**）、无 deadline 时对原 executor 逐字透传、deadline 经非白名单 scope 键注入防脚本篡改、深度上限防递归失控、安装幂等。roadmap 写作时的"引擎无 deadline"前提已过时（对 nop-xlang/nop-core 内部仍成立，但平台已有零保护区装饰器先例）。注意：`XScriptTimeoutException extends NopLintException`（Exception 系）——**可被脚本 try/catch(Exception) 捕获**；`TryExecutable` 无差别 catch Exception——Exception 系取消异常存在被吞 + 吞后循环 livelock 的残余面。
- **检查点钩子的真实覆盖面（已核验，修正本 plan 初稿误判）**：`DebugExpressionExecutor.execute` 对 `allowBreakPoint()` 节点必调 `checkBreakpoint`（与断点无关），但 Literal/Null/Block 等 Executable `allowBreakPoint()=false`——`while(true){}` 空体在 `WhileExecutable` 内部自旋，**loop back-edge 零次经过钩子**；且 `checkBreakpoint` 首行 `if (loc == null || breakpointsMuted) return`——mute 常规操作会静默跳过钩子。**检查点（checkBreakpoint）不能承载取消**，取消必须放装饰器层（executor 入口）。D3 路由的 destroyed 分支 `plainExecute` 直通 DefaultExpressionExecutor——会**跳过包括 deadline 装饰器在内的全部装饰**（见 Goals ②的 D3 修订声明）。
- **REPL 与会话的关系（上游已裁定）**：D1 §3.5 `evt.completed` 来源 = "REPL 执行或被调试脚本执行完成"（gateway，item 12/14）；05 §3.4 `evalExpr` 动作映射 `debugger.getExprValue`，REPL 执行是独立服务面；05 §3.3 登记编译诊断端点与 payload schema 的 owner 归 item 14/D6（本档必须兑现）；D1 §3.5 `evt.completed` payload `{ok, result?, error?}`。
- **REPL 服务落点候选（roadmap item 14："nop-dev-tools/ 或 nop-biz（按 D0/D6 裁定）"）**：D0 §3.5 已裁定 WS 网关落 nop-dev-tools 新子模块（占位 `nop-xlang-debug-web`，配置键 `nop.xlang.debug-web.enabled`）；平台鉴权注解体系在 nop-biz（BizModel/WebResource 鉴权、`IUserContext`）；`XLangCompileTool` 为编译入口（nop-xlang）。
- **沙箱现状**：无运行时沙箱（禁写 Delta/VFS 守卫需自建）；nop-lint 是静态检查非运行时强制；XLang 标准库能力面（xlib/system 函数）即脚本可达面。
- **取消三选一（roadmap 硬约束原文）**：①引擎 interrupt 检查点（涉 nop-kernel，保护区 plan-first）②线程放弃（泄漏换响应）③进程隔离（worker 执行）。
- **审计与权限惯例**：平台审计日志体系与 BizModel 权限码模式（`@Auth`/权限码命名空间）为既有惯例；REPL 属高权限开发工具（等同代码执行）。

## Goals

- XDBG-D6-01：`07-repl-security.md`——①REPL 服务契约（compile 诊断端点 + execute 端点的请求/响应 schema，兑现 05 open question）；②取消机制三选一裁定（含拒绝记录）；③沙箱规则表（import 白名单/危险构造/未知处置）；④权限矩阵（权限码 × 操作）；⑤审计事件定义（谁在何时执行了什么——字段表）；⑥超时与资源限制表；⑦与 D3 会话标记/D4 降级/D1 evt.completed 的接缝；⑧item 14 实现职责与安全测试锚点（未授权拒绝/越权/沙箱逃逸用例被拦/死循环被取消/超时生效）。
- 设计目录 README 职责表状态更新（07 行）。

## Non-Goals

- REPL/网关的实现（item 12/14）、WS 通道细节（D1）、租约（D2）、JVM 级沙箱或进程隔离的完整实现（若裁定拒绝，理由入档）、前端 REPL 面板（item 19）。

## Scope

### In Scope

- `ai-dev/design/xlang-web-debugger/07-repl-security.md`（新建）
- `ai-dev/design/xlang-web-debugger/README.md`（职责表 07 行更新）
- `ai-dev/backlog/xlang-web-debugger-roadmap.md`（item 7 状态 + Stages 表第 7 行 owner plan）
- `ai-dev/logs/` 执行当日条目

### Out Of Scope

- 任何代码变更；`docs-for-ai/`；前端文档。

## Execution Plan

### Phase 1 - REPL 安全详设文档

Status: completed
Targets: `ai-dev/design/xlang-web-debugger/07-repl-security.md`

- Item Types: `Feature`、`Decision`（取消机制、服务落点、沙箱边界——均需文档内裁定）

- [x] 写 07-repl-security.md：①REPL 服务契约（**服务落点裁定**：nop-dev-tools 调试 web 子模块内以平台 Web 服务形态暴露 compile/execute 两端点——拒绝独立 nop-biz 模块与塞进网关 WS 通道的理由；compile 端点 = 编译诊断（兑现 05 open question：请求 {sourcePath?, source} → 响应 {diagnostics: [{line, column?, severity, message, errorCode?}]}——诊断 payload schema 自本档定义，与 D1 错误码承载对齐）；execute 端点 = {sessionId?, source, timeoutMs?} → 同步 {ok, result?, error?} 或经会话走 WS evt.completed 的裁定）；②取消机制裁定（**建议方向：装饰器层 per-entry 取消检查**——复用 `LintDeadlineExecutor` 机制先例：REPL 执行经非白名单 scope 键注入 deadline/取消旗标，装饰器在每次 executor 入口检查（覆盖循环 back-edge 与空体死循环），超时抛取消异常；**取消异常可捕获性须显式裁定**——Exception 系可被脚本 try/catch 吞并造成吞后 livelock，Error 系可绕过 `TryExecutable` 的 catch(Exception)（需论证引擎侧无 Throwable 兜底拦截的风险）；**残余兜底诚实声明**：对抗性吞异常循环的最终兜底是请求级超时/线程放弃（不可作为主机制，登记为残余风险交 item 25 安全审计复核）；**前提门控钉死**：web 开关激活蕴含 `debugger.enabled=true`（D0 §3.3 互斥红线的推论），`enabled=false` 时 REPL 端点不可达——非解释器/无钩子路径一期不可达的论证依赖此前提；拒绝①独立引擎 interrupt 检查点（保护区 + LintDeadlineExecutor 已证明装饰器层零保护区可达同一效果，重复建设）、③进程隔离（成本/复杂度，self-contained 原则））；③沙箱规则表（import 白名单判定：xlib/system 资源枚举原则、默认拒绝未知 import；危险构造清单——Delta 写/VFS 写/线程创建/反射类、一期处置=权限+审计覆盖而非运行时拦截的诚实声明及理由）；④权限矩阵（权限码命名空间 × compile/execute/调试操作；未登录/无权限的失败语义）；⑤审计事件字段表（actor/sessionId/source 截断摘要/动作/结果/耗时/时间戳；存储通道裁定）；⑥超时与资源限制表（execute 默认超时与上限、source 大小上限、输出/结果大小上限、频率限制——与 D1 §3.1 限制表衔接不冲突）；⑦接缝（D3 标记契约的 REPL 消费方式；**D3 契约修订声明**：destroyed 分支 `plainExecute` 直通会绕过 deadline 装饰器——本档裁定其语义修订为"仍经全局 executor 装饰链（deadline 装饰器保持有效）、仅跳过会话调试钩点"，须同步修订 `03-sessionization.md` §3.4 措辞并登记于两文档；mute 绕过面随装饰器层方案天然消除（取消检查不进 checkBreakpoint）；D4 降级前提引用；D1 §3.5 `evt.completed` 承载；若新增取消错误码则同步 D1 §3.7 登记表（或声明由 item 14 按 additive 规则登记））；⑧item 14 实现职责与安全测试锚点
- [x] README 职责表：07 行状态更新并加链接

Exit Criteria:

- [x] 取消机制裁定唯一且论证完整：装饰器层方案含 LintDeadlineExecutor 先例引用、取消异常可捕获性裁定（Exception vs Error 系及引擎侧风险论证）、残余兜底（线程放弃）诚实定位、REPL 门控前提钉死；三选一逐项拒绝/采纳理由齐备（含 D3 destroyed 分支修订声明）
- [x] 服务落点唯一裁定且有理由；compile 诊断 payload schema 成文（兑现 05 open question）
- [x] 沙箱规则表三态齐备（允许/拒绝/默认拒绝）且"权限+审计覆盖而非运行时拦截"的诚实声明有理由
- [x] 权限矩阵覆盖 compile/execute/调试操作 × 未登录/无权限语义
- [x] 审计事件字段表齐备且存储通道有裁定
- [x] 限制表与 D1 §3.1 无冲突（expr 8KB 等既有约束引用而非重定义）
- [x] 若新增错误码，与 D1 错误码登记表同步（已声明由 item 14 按 additive 规则登记）
- [x] 文档不引用 `ai-dev/analysis/`/`ai-dev/discussions/`；与 D0-D5 无矛盾（D3 修订已同步其 §3.4）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] owner-doc 更新 = 本文档 + README 职责表 + 03 §3.4 修订；`docs-for-ai/` 不动
- [x] `ai-dev/logs/` 执行当日条目已更新（随收口追加）
- [x] No new test required：纯文档计划（guide rule 25）

### Phase 2 - 对抗性评审与收口

Status: completed
Targets: `07-repl-security.md`、roadmap

- Item Types: `Proof`

- [x] 独立子代理对抗性评审一轮：**安全攻防视角**（取消绕过面：纯表达式死循环/无检查点路径/取消异常被脚本 catch 吞掉——取消异常必须不可捕获的机制论证；沙箱逃逸面：import 白名单绕过/静态字段污染/资源耗尽；越权面：跨会话 REPL/未鉴权通道）；取消方向论证是否经得起挑战（checkBreakpoint 覆盖率、非会话执行路径）；与 D0-D5 一致性
- [x] 修复评审发现的问题
- [x] roadmap item 7 状态翻转（激活时 `planned`、audit 后 `done`）+ Stages 表第 7 行 owner plan
- [x] `ai-dev/logs/` 收口记录更新

Exit Criteria:

- [x] 评审发现全部修复或书面裁定
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap item 7 `done` 且设计收口 milestone 未翻转
- [x] `ai-dev/logs/` 执行当日条目含本项记录

## Closure Gates

> 纯文档计划：构建验证豁免，doc-links --strict 为机械验证门。

- [x] XDBG-D6-01 交付物齐备且满足 Stage detail 要求（契约+取消机制裁定+沙箱规则表+权限矩阵+审计事件）
- [x] 设计评审完成，无未处置 Blocker/Major
- [x] 文档与 live repo 事实一致（executor 自线程化约定、TryExecutable catch(Exception)、JsPromise catch(Throwable)、LintDeadlineExecutor 机制、checkBreakpoint 早退——审计逐条对照源码）
- [x] 无静默降级项（残余风险 5 项 + 降级态 livelock 显式登记 item 25，分类诚实）
- [x] owner docs 已同步（README 职责表 07 行 + 03 §3.4 修订段 + D1 §3.5 来源注记）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2466-xdbg-d6-repl-security.md --strict` 退出码 0
- [x] No build verification required（纯文档计划）

## Deferred But Adjudicated

### async 构造解除禁用（JsPromise Error 重抛）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 一期 REPL 编译面禁用 Promise/async（禁用全局校验），取消不变量在允许面内成立；解除须先做 JsPromise Error 重抛（nop-xlang 保护区改动），成本/收益归 item 25 评估。
- Successor Required: `yes`
- Successor Path: item 25 安全审计评估立项；07 §3.3 规则表已登记解除前置。

### 挂起感知 deadline / servlet async 超时宿主

- Classification: `optimization candidate`
- Why Not Blocking Closure: 绝对时钟 deadline 的挂起计入已在 §3.4 如实声明并给出调高 timeoutMs 的操作指引；降级态请求级超时宿主缺失已作为残余④登记 item 25。
- Successor Required: `yes`
- Successor Path: item 25 复核；如需实施由 remediation plan 承接。

## Non-Blocking Follow-ups

- D4 §3.2 的 LintDeadlineExecutor 装饰器引用无需修订（无 deadline 时透传路径字面为真）；lint-deadline-active 下嵌套断点旁路属 lint 域既有行为，留 item 11/25 关注。
- 脚本自建线程逃逸取消（ThreadLocal 不跨线程）已落"线程创建一期无运行时拦截"行 + D3 §3.3 跨线程限制伞内；item 25 清单补充点名。

## Closure

Status Note: 07-repl-security.md 六要素齐备：服务契约（compile/execute 端点路由与全字段 schema、属主校验、每请求谓词复查）、取消机制（融合执行器 per-entry 检查——deadline 检查并入会话调试执行器入口 + destroyed 分支独立取消检查执行器；Error 系取消异常 + Promise 一期禁用裁定；ThreadLocal 不可见通道 + 保留标识符纵深；三选一逐项裁定）、沙箱规则表（含类导入白名单/Promise 禁用/保留标识符 + 5 项残余风险 item 25 登记）、权限矩阵（WebResource+服务内校验，不引入 nop-biz 依赖）、审计事件、限制表。对抗性评审 3 Blocker + M2-M5 + B4 + m1-m7 全部修复（B1/B2/B3 为实测 live code 证伪后的方案重构）；closure audit 逐条 PASS 判可收口，附带 3 处措辞统一与 D1 来源注记随收口完成。roadmap Work Item 7 翻 `done`。
Completed: 2026-10-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子代理（对抗性评审 agent_159d5a40-a919-4bcb-be17-830c900f5c67；closure audit agent_fe07614f-d99e-492b-b22e-3a257c6e0e90）
- Audit Session: closure audit 由 fresh 子代理执行，live code 逐条对照
- Evidence:
  - B1/B2/B3/M2-M5/B4/m1-m7 修复验证全部 PASS（audit 附 live 锚点：LintDeadlineExecutor L104 自线程化、TryExecutable L42 catch(Exception)、JsPromise L43/47/101/135 catch(Throwable)、LexicalScopeAnalysis L143/157 Promise 内建、XScriptDeadline javadoc 编译白名单、WhileExecutable 回边经 executor）
  - audit 结论"item 7 可翻 done"；附带 Minor-1 三处措辞统一（§3.2 标题/§3.6 两句）与 Minor-2 D1 §3.5 来源注记已随收口完成
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2466-xdbg-d6-repl-security.md --strict` 退出码 0
  - Deferred 项分类检查：async 解除禁用（out-of-scope improvement）、挂起感知 deadline（optimization candidate）均附 Why Not Blocking + successor path；无 in-scope live defect 被降级

Follow-up:

- async 解除禁用与挂起感知 deadline → item 25 复核；D4 装饰器引用与自建线程逃逸 → item 11/25 关注。
