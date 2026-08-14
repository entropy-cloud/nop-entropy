# W1 前置 spike：流式重订阅可行性验证 + 决策暴露

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W1）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§五 Q2/Q5/Q7）
> Related: `ai-dev/design/nop-ai-gateway/01-architecture.md`、`ai-dev/design/nop-ai-agent/nop-ai-agent-reliability.md`
> Mission: nop-ai-gateway-failover
> Work Item: W1

## Purpose

验证流式 failover 的两个核心机制前提（Q7：网关形态字节流层"首段缓冲 + 替换式重订阅"与 `StreamingProcessor`/`StreamingResponse` 路由期绑定的耦合；本地形态 chunk 流层重订阅的背压/取消语义），并将 Q2（前端 model 参数语义）、Q5（RATE_LIMITED/TRANSIENT → 账号链切换偏离）整理为可裁决的人机决策点。本计划是纯调研/文档计划，**不产出任何实现代码**。

## Current Baseline

- 网关形态流式链路（live repo 核实的现状）：
  - `StreamingProcessor.executeStreaming`（`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/StreamingProcessor.java:59`）在**路由执行期**构建 `mappedPublisher`（`:80`），包装进 `StreamingResponse`（`:88`，`publisher` 为 **final 字段**，`StreamingResponse.java:24`），存入 `context` attribute（`:95`），返回空的成功 `ApiResponse`。
  - **流式路径绕过拦截器 `invoke()`**：`RouteExecutor.execute`（`RouteExecutor.java:76-77`）对流式路由直接 `return executeStreaming(...)`，不调用 `invocation.proceedInvoke`——`IGatewayInterceptor.invoke` 不参与流式路径（`InterceptedGatewayInvocation.proceedInvoke` 仅非流式可达）。流式路径的拦截器接触点：`onRequest` → `onStreamStart`（`StreamingProcessor.java:71`，早于 `:95` setAttribute——此时 attribute 尚未写入）→ `onStreamElement`（`:131`）→ **`onError`**（经 `invocation.proceedOnError`，`StreamingProcessor.java:147`，逆序钩子；返回非空 `ApiResponse` 时降级为 `onNext`+`onComplete`，`:148-151`）→ `onStreamComplete`。**注意**：`proceedOnStreamError`/`onStreamError` 钩子在现链路**不可达**（全代码库无调用点，仅接口定义）；`AiFailoverGatewayInterceptor` 内 `svcCtx.isStreamingMode()` 守卫看似 invoke 参与流式，实际该分支在现链路上不可达——spike 需以此为准，勿被误导。
  - 消费端 `GatewayHttpFilter`（`nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/http/GatewayHttpFilter.java`）在响应写阶段从 context 读 attribute 并强转 `StreamingResponse` 后用 `getPublisher()` 输出（`:158-159`）——publisher 在路由期已绑定，消费期无法替换；替换 attribute 值的包装器必须是 `StreamingResponse` 子类（final `publisher` 字段决定了只能覆写 `getPublisher()` 等非 final 成员）。
  - 拦截器钩子齐备：`IGatewayInterceptor.onStreamStart/onStreamElement/onStreamError/onStreamComplete`（default 方法）+ `IGatewayInvocation.proceedOnStream*`；`StreamingProcessor` 的 `createMappedPublisher` 在 onNext/onError 路径调用它们（`:112-168`）。
  - `StreamingResponse.upstreamSubscription`（AtomicReference）+ `abort()` 支持客户端断连取消上游（`GatewayHttpFilter` 在**消费订阅阶段**——元素流动之前——`setUpstreamSubscription`，约 `:188` 的 onSubscribe 回调中）；**`abort()` 在注入前是 no-op（null 检查）且为静默取消（cancel 不产生下游 onError/onComplete 信号）**——spike 需记录此语义边界。
  - **既有 AI 网关构件（roadmap Current baseline 同款）**：nop-gateway 已驻留 `AiFailoverGatewayInterceptor`（非流式 URL 级 fallback：注入 `IHttpClient`、重建 `HttpRequest`、429/5xx 重试；流式分支显式抛 `UnsupportedOperationException`，bean 注册于 `gateway-defaults.beans.xml`，含测试）与 `AiRateLimitGatewayInterceptor`/`AiAuthGatewayInterceptor` 等；需求文档 §六已裁决"不直接扩展 `AiFailoverGatewayInterceptor`（保持兼容不迁移）"。spike 的机制结论不得与 §六裁决矛盾。
- 本地形态流式链路：
  - `IChatService.callStream(ChatRequest, ICancelToken)` 返回 `Flow.Publisher<ChatStreamChunk>`（`nop-ai/nop-ai-api/.../chat/IChatService.java:33`）。
  - `ChatServiceImpl.callStream`（`nop-ai/nop-ai-core/.../service/ChatServiceImpl.java:176`）**每次调用新建 `SubmissionPublisher`**，且在**调用时（急切地）**subscribe 到一次新的 HTTP SSE fetch（`:199-230`）——返回的 publisher 是"单次使用 + 上游急切启动"的发布者（非严格意义冷流：上游 fetch 早于调用方 subscribe 就已发起），绑定当次账号配置（`ChatOptions.accountKey/accountBaseUrl/model` 经 `buildHttpRequest` 每次调用读取，`ChatServiceImpl.java:253-275`）。推论：W6 并发计数"流建立 +1"应挂钩 **`callStream` 调用**而非订阅时刻，否则存在未计数窗口——spike 需把此推论记录为对 W6 的输入。
- 设计文档 Q7 尚未裁决：字节流层"缓冲 + 替换式重订阅"与路由期绑定 publisher 的耦合结论不存在；`02-account-failover-requirement.md` §五 Q2/Q5/Q7 仍为开放问题。
- 无任何流式 failover 实现代码（roadmap Current baseline 确认）。

## Goals

- 产出 Q7 的验证结论：网关形态字节流层重订阅**可行/不可行** + 可行的最小机制方案（哪些类需改动、改动是否触碰 nop-gateway、是否保持 nop-gateway 零 nop-ai 依赖）。
- 产出本地形态 `Flow.Publisher` chunk 流层重订阅结论：背压/取消语义下重订阅的可行方案（含 `ChatServiceImpl.callStream` 单次冷流事实的推论）。
- 将 Q2（前端 model 参数语义：路由覆盖 vs 保留）与 Q5（RATE_LIMITED/TRANSIENT → 账号链切换的产品决策确认）整理为结论明确、附影响分析的决策点，暴露给人机裁决。
- 若 spike 结论推翻需求文档假设，以 plan-first 方式修订 `02-account-failover-requirement.md`。

## Non-Goals

- 不产出任何 `src/` 实现代码（验证方式 = 只读代码追踪 + 结论文档）。若需运行性实证（如 `SubmissionPublisher` 背压/取消竞态），允许在 `<project-root>/_tmp/` 下写 scratch 验证类——scratch 不进入任何 src/ 目录、不依赖其结论成立，实证结论须复述于结论文档。
- 不裁决 Q8/Q10（模型类配置形态、规则策略 DSL 形态）——归 W5 plan 阶段。
- 不裁决流式缓冲窗口参数默认值（N 元素/T 毫秒）与重试预算默认值（Q3）——留 W6/W7 执行期裁决，本计划只记录其依赖面。
- 不改动 nop-gateway / nop-ai-core / nop-ai-api 任何源码。

## Scope

### In Scope

- SPIKE-01: 网关形态字节流层重订阅可行性验证与结论（`StreamingProcessor`/`StreamingResponse`/`GatewayHttpFilter`/`IGatewayInterceptor` 只读追踪）。
- SPIKE-02: 本地形态 `Flow.Publisher` chunk 流层重订阅可行性验证与结论（`ChatServiceImpl.callStream` + `IChatService.callStream` 契约只读追踪）。
- SPIKE-03: Q2/Q5 决策点整理与暴露（问题陈述、两种选项的语义/影响/测试面、推荐默认值）。
- SPIKE-04: 结论回馈——推翻假设时修订需求文档（plan-first）。

### Out Of Scope

- W2-W8 的任何实现。
- 网关/本地形态的任何代码改动（含为验证而临时修改源码）。

## Execution Plan

### Phase 1 - 网关形态 Q7 验证（SPIKE-01）

Status: completed
Targets: `nop-service-framework/nop-gateway/src/main/java/io/nop/gateway/core/executor/StreamingProcessor.java`、`.../core/streaming/StreamingResponse.java`、`.../http/GatewayHttpFilter.java`、`.../core/interceptor/IGatewayInterceptor.java`、`.../core/executor/RouteExecutor.java`（只读）

- Item Types: `Proof | Decision`

- [x] 代码追踪：从 `RouteExecutor`（流式路由入口）→ `StreamingProcessor.executeStreaming` → `StreamingResponse` 存 context → `GatewayHttpFilter` 消费 publisher 的完整路径，确认 publisher 绑定时机（路由期）与替换约束（final 字段）——含 `GatewayHttpFilter` 读 attribute 的时点（响应写阶段）与 `setUpstreamSubscription` 注入时点（`:188` 附近，核实行号）。
- [x] 验证候选机制 A（nop-gateway 内缓冲层）：字节流层缓冲 wrapper（在 `createMappedPublisher` 与 `StreamingResponse` 之间插入"缓冲 + 可重订阅"层）——首段元素缓冲、失败时取消上游并重新发起路由/请求、替换下游输出源——在现有结构下是否成立；若不成立，最小改动是什么（如 `StreamingResponse` 增加可替换 publisher holder，或 gateway 通用流式层扩展——**此改动不得引入 nop-ai 依赖**）。
- [x] 验证候选机制 C（拦截器侧替换，**nop-gateway 零改动候选——需先验证入口存在性**）：外层拦截器 `invoke()` 是否参与流式路径（**预期否**：`RouteExecutor.java:76-77` 流式路由直接 `return executeStreaming(...)`，不调 `proceedInvoke`；`onStreamStart` 时 attribute 尚未写入、`executeStreaming` 会覆盖）——若 `invoke` 不参与，机制 C 在现结构下**不可行**（或需改 RouteExecutor 接线，即退化为机制 A 的改动面），该结论须显式落档；若存在替代入口（如 `onRequest` 后 hook 或 context 变更监听），核实其替换时点与 `GatewayHttpFilter` 读 attribute 时点的先后。
- [x] 验证候选机制 B：拦截器钩子（onStreamStart/onStreamElement/**onError（经 proceedOnError）**）承载首段缓冲 + 重试的边界——**核实而非预设**：消费订阅阶段 `GatewayHttpFilter` 已 `setUpstreamSubscription`（约 `:188`），拦截器可经 `context.getAttribute(StreamingResponse.class.getName()).abort()` 取消上游（取消通道存在）；但 `abort()` 注入前是 no-op、且是**静默取消**（不产生下游 onError/onComplete），钩子内无法感知取消完成也无法触发重订阅——给出"能取消 / 能重订阅"四象限结论与需机制 A 配合的边界；同时核实 `proceedOnStreamError` 不可达事实（若错误经 `onError` 逆序钩子到达，重试决策挂在哪个钩子）。
- [x] 机制比较结论：A vs C（含 nop-gateway 改动面差异、对"nop-gateway 只读复用"假设的影响），推荐方案 + 理由落档。
- [x] 结论落档：`ai-dev/analysis/2026-08/2026-08-15-w1-streaming-resubscribe-spike.md` 写 SPIKE-01 结论（可行/不可行 + 机制方案 + 涉及类清单 + nop-gateway 依赖约束保持性说明 + 对 W7 的输入）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] SPIKE-01 结论文档已写：明确"可行/不可行/需最小网关改动"三选一结论 + 支撑代码位置（文件:行号）+ 对 W7 实现方式的输入。
- [x] 已验证候选机制 A/C/B 三条路径都有明确结论（非仅单一路径）；A vs C 有显式比较与推荐（含 nop-gateway 改动面差异）。
- [x] 结论明确标注 nop-gateway 零 nop-ai 依赖约束是否可保持（若需改 nop-gateway，必须是不含 nop-ai 引用的通用改动）。
- [x] No owner-doc update required（本 Phase 仅读代码 + 产出 analysis 文档；需求文档修订触发条件是"推翻假设"，进入 Phase 3 统一处理）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 本地形态 Q7 验证（SPIKE-02）

Status: completed
Targets: `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/IChatService.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/ChatServiceImpl.java`（只读）

- Item Types: `Proof | Decision`

- [x] 代码追踪：`ChatServiceImpl.callStream` 单次冷流事实（每次调用新建 `SubmissionPublisher` + 新 HTTP SSE fetch）确认；`IChatService.callStream` 契约（Flow.Publisher 背压/取消、ICancelToken 语义）确认。
- [x] 结论裁定：本地形态重订阅 = 以新 `ChatOptions`（新账号）**重新调用 `callStream`** 的可行性；"上游急切启动（调用时发起 fetch，早于订阅）"事实下，首段缓冲窗口内取消上游的语义（`cancelToken`/subscription cancel 路径）是否可用；"窗口内失败 → 重订阅"在背压语义下是否有竞态（如缓冲元素未消费时取消）。**细节需落档为 W6 输入**：fetch subscriber 直接 `request(Long.MAX_VALUE)`（约 `:205`），调用方背压仅经 `SubmissionPublisher` 默认缓冲在 `submit()` 阻塞处体现；每次重调 `callStream` 会重复执行 `checkRateLimit`（约 `:182`）与 `chatLogger.logRequest`（重试可能被本地限流拦住）。
- [x] 结论落档：同一 spike 文档写 SPIKE-02 结论（重订阅方案 + 对 W6 的输入：缓冲层位置、重订阅入口、并发计数挂钩点——挂钩 `callStream` 调用而非订阅时刻的推论）。

Exit Criteria:

- [x] SPIKE-02 结论已写入 spike 文档：明确重订阅机制（重新调用 callStream vs 其他）、取消/背压边界结论、并发计数挂钩点（callStream 调用时刻）推论、对 W6 本地适配器流式层的输入。
- [x] 已验证 `ChatServiceImpl.callStream` 的"单次使用 + 上游急切启动"事实与"每次重订阅 = 新 HTTP 请求"的推论。
- [x] No owner-doc update required（本 Phase 仅读代码 + 产出 analysis 文档）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 决策暴露 + 结论回馈（SPIKE-03/SPIKE-04）

Status: completed
Targets: `ai-dev/analysis/2026-08/2026-08-15-w1-streaming-resubscribe-spike.md`、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（仅假设被推翻时）

- Item Types: `Decision | Follow-up`

- [x] Q2 决策点整理：前端 `model` 参数语义两种选项——(a) 路由覆盖（切换时重写 model，跨模型切换生效，前端无感知）；(b) 保留前端 model（仅同模型账号间切换）——各附影响分析（协议转换范围、模型类路由组依赖、W4/W6/W7 实现面差异）+ 推荐默认（建议按需求 §一.4 方向性承诺：覆盖）。
- [x] Q5 决策点整理：RATE_LIMITED/TRANSIENT → 账号链切换偏离确认（需求 §3.1 已声明偏离 coordinator 语义，需产品确认）——整理为"确认/拒绝"两态决策点 + 拒绝时的回退语义（回退 coordinator 行为：TRANSIENT 走模型 tier、RATE_LIMITED 原地退避）。
- [x] 决策暴露方式：决策点写入 spike 文档专用章节 + 当日 `ai-dev/logs/` 显式列出"待人工裁决"标记。
- [x] SPIKE-04 结论回馈：若 Q7/Q2/Q5 结论推翻需求文档假设，以 plan-first 修订 `02-account-failover-requirement.md` 并在文档顶部记录修订缘由；未推翻时在 spike 文档记录"假设未变，无需修订"。

Exit Criteria:

- [x] Q2/Q5 决策点已在 spike 文档 + daily log 中完整暴露（选项、影响、推荐默认、待裁决标记），W6/W7 实现前可被人工裁决。
- [x] 需求文档修订（若触发）已落地或"无需修订"结论已记录——两态必居其一。
- [x] `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md` 若被修订：修订内容与 spike 结论一致，且 Q 表状态同步。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
>
> **纯文档计划**：本计划不涉及任何代码变更（仅修改 `ai-dev/` 下的 analysis/design/log 文档），构建验证条目从 Closure Gates 中删除，不执行。

- [x] SPIKE-01/02/03/04 四个交付物全部产出并落档于 spike 文档（含结论、机制方案、代码位置证据、对 W6/W7 的输入）。
- [x] Q2/Q5 决策点已暴露（文档 + daily log 双通道）。
- [x] 需求文档修订结论（修订或无需修订）已明确落档。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（audit 验证：spike 结论与 live 代码事实一致——可抽查 StreamingProcessor/StreamingResponse/ChatServiceImpl 代码位置；无 src 实现代码被误写）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（新增 analysis 文档的链接合法）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关闭时执行）。

## Deferred But Adjudicated

无（本计划为纯调研计划，无 in-scope 实现项；Q2/Q5 裁决不属于本计划收口范围——W6/W7 实现前裁决即可，未裁决不阻塞本计划关闭，但必须已暴露）。

## Non-Blocking Follow-ups

- Q3（缓冲窗口参数/重试预算默认值）：留 W6/W7 执行期裁决；本计划只确认其依赖面（无硬依赖）。
- Q8/Q10（模型类配置形态、规则策略 DSL）：归 W5 plan 阶段裁决。

## Closure

Status Note: 纯调研/文档计划（SPIKE-01/02/03/04）全部落地——网关形态 Q7 结论（需最小 nop-gateway 通用改动，机制 A 推荐）、本地形态重订阅结论（重调 callStream + 背压/取消边界实证）、Q2/Q5 决策点双通道暴露、需求文档"假设未变，无需修订"落档；roadmap W1 → done、W7 module/area 同步；独立 closure audit 通过后关闭。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: fresh general subagent（独立 session，非实现 session）
- Audit Session: ses_ffd8b432fffeqleCLqOuoGq43T
- Evidence:
  - 1. SPIKE-01 代码位置准确性：PASS——RouteExecutor.java:76-77（流式直返 executeStreaming 不经 proceedInvoke）；StreamingProcessor.java:59/71/88/95/131/147/148-151/161；StreamingResponse.java:24（final publisher）/62-66/71-76（abort no-op+静默，AbstractServerEventSubscription.java:146-152 无下游信号）；GatewayHttpFilter.java:158-159/182/188；proceedOnStreamError 全库 src/main 零生产调用点（grep 核实，仅接口/实现定义 + 测试）；AiFailoverGatewayInterceptor.java:42-45 isStreamingMode 守卫不可达；attribute 写入(:95)与 filter 读取(:159)之间无钩子（GatewayHandler.processRoute 仅 exceptionally/thenApply）。
  - 2. SPIKE-02 代码位置准确性：PASS——IChatService.java:33；ChatServiceImpl.java:176/182/186-187/191/199/201/202/205/206-208/215/221/226/230/255-257；急切启动链验证（ServerEventPublisher.subscribe 惰性 :47-53 → AbstractServerEventSubscription.request :31-45 startRequest → sendAsync，全部在 callStream() 调用栈内同步完成）。
  - 3. 无 src 误写：PASS——git status 仅 ai-dev/ 文档 + _tmp/ scratch（JDK-only，计划 Non-Goals 允许）+ roadmap/plans/missions，零 src/ 文件改动。
  - 4. 交付物完整：PASS——spike 文档 SPIKE-01（三选一结论"需最小网关改动（可行）"+ 机制 A/B/C 裁决 + 代码证据 + W7 输入）、SPIKE-02（重订阅机制 + 取消/背压边界 + 并发挂钩点 + W6 输入）、SPIKE-03（Q2/Q5 选项+影响+推荐默认）、SPIKE-04（"假设未变，无需修订"）。
  - 5. Closure Gates 完整性：本 audit 后已全部勾选；check-plan-checklist.mjs --strict 退出码 0（0 unchecked）。
  - 6. 文本一致性：Plan Status=completed、Phase 1/2/3 Status=completed、Exit Criteria 全部 [x]、Closure Gates 全部 [x]、daily log 收口条目一致。
  - 7. Anti-Hollow 检查：零代码变更计划，无空壳/静默跳过面；scratch 实证结论（SubmissionPublisher 背压/取消竞态，S1-S10）已复述于 spike 文档。
  - 8. Deferred 项分类检查：Q3（缓冲窗口/重试预算默认值）→ W6/W7 执行期裁决（无硬依赖，adjudicated）；Q8/Q10 → W5 plan 阶段；Q2/Q5 待人工裁决但已暴露（不阻塞本计划关闭）；Q7 Q 表回填归 W8 OBS-04——全部为 non-blocking 分类，无 in-scope live defect 被降级。

Follow-up:

- Q2/Q5 待人工裁决（W6/W7 实现前；已暴露于 spike 文档 + daily log"待人工裁决"标记）。
- Q3（缓冲窗口参数/重试预算默认值）留 W6/W7 执行期裁决。
- Q8/Q10（模型类配置形态、规则策略 DSL）归 W5 plan 阶段。
- Q7 处置结果回填需求文档 Q 表归 W8 OBS-04。
- W7 实施前置：按 spike 结论对 nop-gateway 做最小通用改动（StreamingProcessor 缓冲/重执行扩展点 ± StreamingResponse publisher 可替换化），零 nop-ai 依赖保持。
- no remaining plan-owned work.
