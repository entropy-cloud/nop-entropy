# 2302 WI20 per-provider 缓存键放置框架（refactor roadmap M6，吸收 autonomous WI8）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI20（Deliverable: 代码变更 + 测试；Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-context-compaction-economics.md` + `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md`；来源: unreal 比对 §6）
> 吸收裁定: `ai-dev/analysis/2026-10/2026-10-02-wi1-dedup-ruling.md` 必裁②——autonomous WI8（cache_control 位置纠错）合并归属本 plan，验收点=content block 级放置 + 测试
> Cross-Cutting: llm.xdef 位于 nop-kernel/nop-xdefs——平台级契约演进（plan-first + owner doc + 迁移说明，验证面扩展 nop-xdefs/nop-ai-core）；ILlmDialect 变更为 additive default 方法（零破坏）
> Adversarial Review: round 1 子代理 agent_c56d204b——0 Blocker / 1 Major（辅助调用路径未裁定）+ 8 Minor 全部吸收；round 2 子代理 agent_a683c7bf——APPROVED 可执行（9/9 修复验证 PASS），附带 N-1（断点块选择排除 thinking 块）/N-2（无 system 键跳过）两处 Minor 已吸收进 Goals

## Purpose

nop 缓存解析资产在位但**请求侧无断点构造、无缓存键放置策略**（unreal 比对 D6 领先域）。本 plan 落地 per-provider 缓存键放置框架：Anthropic 请求侧 content-block 级 cache_control 断点引擎（顺带完成 WI8 的顶层→块级纠错）、OpenAI `prompt_cache_key` 字段、会话亲和 header（openrouter/fireworks 形态）；全部配置门控、默认关闭（请求字节零变化，与 WI16/WI18 前缀缓存稳定裁定一致）。

## Current Baseline

（执行时 HEAD `4be11f2eba`，2026-10-03 实测）

- **`ChatOptions.sessionId` 已存在**（nop-ai-api `ChatOptions.java:37`）——缓存键的会话标识载体在位；**引擎侧无人写入**（ReActAgentExecutor.resolveRoutedOptions 构造 routedOptions 时不 stamp sessionId；SingleTurnExecutor.java:74 与 ReAct:884 的 `new ChatRequest(messages)` 不带 options）。
- **AnthropicDialect 请求侧**（nop-ai-core）：`buildBody:240-309` 顶层 system 字符串 + messages 合并轮；`convertMessage:631-632` 仅 providerHints.cache_control **透传到 message map 顶层**（WI8 指认的错误位置——Anthropic 合法位置是 content block 级）；合并轮顶层 cache_control 保留首个携带者（:293-297，注释明言"WI20 复核钉定"）。**providerHints.cache_control 无生产写入者**（全仓 `setProviderHints` 仅 copy() 保活，零业务调用）——移除透传零回归。
- **响应侧解析资产在位**（WI21 范围，本 plan 不触碰）：`AnthropicDialect` 响应侧 cache usage 解析（:392-406 区域）；`AbstractLlmDialect.parseUsage` 通用 path 配置通道；`LlmResponseModel` 的 promptCacheHitTokensPath/promptCacheCreationTokensPath（llm.xdef:99-100）。
- **OpenAiDialect.buildBody:150** 无 prompt_cache_key；**ChatServiceImpl.buildHttpRequest:256-278** 有 `dialect.setHeaders(httpRequest, apiKey, config.getApiKeyHeader())` header 接缝（:270）与 `dialect.buildBody`（:274）调用点。
- **ILlmDialect**（nop-ai-core `dialect/ILlmDialect.java`）：`setHeaders:61` 为 void 方法；无 cache header 概念。
- **llm.xdef**（nop-kernel/nop-xdefs `_vfs/nop/schema/ai/llm.xdef`）：`LlmModelModel`（:29-31）现有属性 maxTokensLimit/defaultMaxTokens/contextLength/enableThinkingPrompt/disableThinkingPrompt/thinkStartMarker/thinkEndMarker/supportToolCalls——per-model 配置载体；codegen 预编译再生 `_LlmModelModel`（WI16 tool.xdef 先例）。
- **system 块化 parse 侧不对称（round 1 Minor-2 登记）**：`AnthropicDialect.parseRequestBody:74-77` 对 system 仅 `toString()`——断点开启后 outbound system 为块数组，build→parse 自闭环有损（真实 provider 客户端发块数组同样命中此既有缺口）；本 plan 不改 parse 侧，登记后继。
- **llm-layer owner doc 既有契约**：convertMessage 纯函数序列化（§3.2）、providerHints 是元数据透传通道（§3.3 第 3 点——WI20 后该点的 cache_control 示例需改写）、前缀缓存设计 §八（原则层确定性 + 运行时机制）。
- **WI1 必裁②**（`2026-10-02-wi1-dedup-ruling.md`）：autonomous WI8 合并归属本 plan；autonomous roadmap WI8 条目 checkbox "待 refactor WI20 落地验收后关闭"。

## Goals

- **Anthropic 断点引擎（TTL 断点形态）**：`LlmModelModel` 新增配置 `cacheBreakpoints="int"`（默认 0=off）与 `cacheBreakpointTtl="string"`（空=默认 ephemeral；非空=Anthropic TTL 变体如 `1h`）。置非零时 `AnthropicDialect.buildBody` 请求侧构造 content-block 级 `cache_control`：① system 字符串 → 单元素块数组 + 末块断点；② messages **最后 N 个非空 turn**（content blocks 为空的 turn 跳过不计数——round 1 Minor-8 边界钉定）各自断点落在轮内**最后一个可承载 cache_control 的块**（跳过 thinking 块——Anthropic 契约禁止 thinking 块携带 cache_control，round 2 N-1；仅含 thinking 块的轮跳过）；① 中 body 无 system 键时跳过 system 形态（round 2 N-2）。负值=off（等价 0，debug log——round 1 Minor-7 钉定，不做"钳到 1"）。引擎在 buildBody 的执行位置：合并轮循环之后、`applyThinkingToContentBlocks` 之后收尾执行（thinking 改写首 text 块、断点加末块 cache_control——不同块不同键无冲突，round 1 Minor-3），组合场景有测试。构造确定性（相同消息序列 → 相同断点位置），引擎不依赖 providerHints。
- **WI8 纠错吸收**：删除 `convertMessage:631-632` 的 providerHints.cache_control 顶层透传与 `buildBody:293-297` 合并轮顶层保留逻辑（含注释更新——合并轮语义由断点引擎取代）；autonomous roadmap WI8 checkbox 关闭（落地验收后）。
- **OpenAI 字段形态**：`LlmModelModel` 新增 `promptCacheKey="boolean"`（默认 false）。置真且 `options.sessionId` 非空时 `OpenAiDialect.buildBody` 附加 `prompt_cache_key=<sessionId>`。
- **会话亲和 header 形态**：`ILlmDialect` 新增 default 方法 `applyCacheHeaders(HttpRequest, ChatRequest, LlmModelModel)`（default no-op，零破坏）；`AbstractLlmDialect` 实现：`LlmModelModel` 新增 `cacheKeyHeader="string"`（空=不发）置非空且 sessionId 非空时 `httpRequest.setHeader(header, sessionId)`；`ChatServiceImpl.buildHttpRequest` 在 setHeaders 后调用。OpenRouter/Fireworks 用 `cacheKeyHeader="x-session-id"`。
- **引擎 sessionId stamping**：ReAct regular 与两个 wakeup 请求路径（steering :1330 / heartbeat :1461）均先经 `resolveRoutedOptions`——stamp 收敛于其末尾一处（不覆盖调用方已设值）；SingleTurnExecutor 的 request 无 options 对象，从零创建仅含 sessionId 的 ChatOptions 并 setOptions（round 1 Minor-5：空 options 与 null options 在 provider 解析/stream 默认上语义等价，已核实无回归）。缺 sessionId 且配置了放置形态 → debug log + 跳过放置（成本优化语义：无键可放不是错误，不 fail-loud——显式登记于 owner doc）。
- **零回归默认**：三项配置默认关闭，关闭时请求字节与 HEAD 逐位一致（测试断言）。
- **测试**：断点引擎（system 块化/turn 断点位置/TTL 变体/钳制/确定性）、WI8 纠错（顶层不再出现 cache_control、块级存在）、prompt_cache_key、session header、sessionId stamping、零回归默认、缺失 sessionId 跳过。

## Non-Goals

- 响应侧缓存观测接线（OpenAI/Ollama/Gemini 默认 path 配置化 + ResponsesDialect 接入 + Usage Raw 保留）——WI21 范围。
- **辅助 LLM 调用路径不 stamp sessionId**（round 1 审查 Major-1 裁定）：`Layer3FullSummaryStrategy`（压缩摘要）/`LlmCompletionJudge`/`LLMCurator` 直连 IChatService、绕过 resolveRoutedOptions——保持无 sessionId，"缺键跳过"语义使其天然不进会话缓存命名空间（摘要请求的会话键会污染会话亲和性）；与 autonomous WI11（辅助调用治理）划界，该处如需会话键由其显式裁定。
- **ResponsesDialect 不加 prompt_cache_key**（round 1 审查 Minor-9 裁定）：unreal §6 三形态原文针对 chat completions 形态的 openai 字段；Responses API 的缓存观测字段属 WI21，其请求侧放置登记 watch（无需求）。
- 缓存命中驱动的自适应策略（按 hit 率调整断点数）——无需求登记。
- Gemini/OpenAI 自有缓存 API（context caching 显式 create）——unreal 报告未要求，登记 watch。
- providerHints 通道整体废弃（仅移除 cache_control 消费；通道本身是 nop-ai-api 公共 API，保留）。

## Scope

### In Scope

- nop-kernel/nop-xdefs：llm.xdef `LlmModelModel` 三属性（平台级契约演进，附迁移说明）。
- nop-ai-core：AnthropicDialect（断点引擎 + WI8 纠错）、OpenAiDialect（prompt_cache_key）、ILlmDialect（default applyCacheHeaders）、AbstractLlmDialect（header 实现）、ChatServiceImpl（调用点）。
- nop-ai-agent：ReActAgentExecutor/SingleTurnExecutor sessionId stamping。
- 测试（nop-ai-core + nop-ai-agent）+ owner doc（llm-layer 新节 + compaction-economics 指针）+ log + 双 roadmap 同步。

### Out Of Scope

- WI21/ WI22 / WI23；autonomous roadmap 其余条目；nop-ai-api 消息类型变更。

## Execution Plan

### Phase 1 - llm.xdef 配置面 + Anthropic 断点引擎 + WI8 纠错

Status: completed
Targets: `nop-kernel/nop-xdefs/.../llm.xdef`、`nop-ai-core/.../AnthropicDialect.java`、nop-ai-core 测试

- Item Types: `Feature` + `Fix`（WI8：cache_control 错误位置为已确认 contract drift）

- [x] llm.xdef `LlmModelModel` 增 `cacheBreakpoints="int"`、`cacheBreakpointTtl="string"`、`promptCacheKey="boolean"`、`cacheKeyHeader="string"`；预编译再生 `_LlmModelModel`
- [x] AnthropicDialect 断点引擎：buildBody 内 system 块化 + 最后 N turn 末块 cache_control（TTL 变体）；确定性构造
- [x] WI8 纠错：移除 convertMessage providerHints.cache_control 顶层透传（:631-632）+ buildBody 合并轮顶层保留（:293-297），注释更新
- [x] nop-ai-core 单测：断点位置/TTL/负值=off/空块 turn 跳过/thinking 块跳过（N-1）/无 system 跳过（N-2）/确定性/块级放置/顶层无 cache_control/关闭时字节零变化/断点+thinking prompt 组合（round 1 Minor-3）——TestAnthropicCacheBreakpoints 12 项

Exit Criteria:

- [x] 单测全绿；`./mvnw test -pl nop-ai/nop-ai-core -am` 通过（440 全绿，2026-10-03）
- [x] **接线验证**：断点引擎由 buildBody 真实调用（TestAnthropicCacheBreakpoints 对 body 块级 cache_control 断言）
- [x] **无静默跳过**：cacheBreakpoints 负数=off 显式钉定（debug log），无歧义路径
- [x] owner doc 更新由 Phase 3 统一收口（本 plan 内闭环，无跨 plan 漂移窗口——round 1 Minor-6）
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - OpenAI 字段 + 会话 header + 引擎 stamping

Status: completed
Targets: `OpenAiDialect.java`、`ILlmDialect.java`、`AbstractLlmDialect.java`、`ChatServiceImpl.java`、ReActAgentExecutor/SingleTurnExecutor、测试

- Item Types: `Feature`

- [x] OpenAiDialect.buildBody：promptCacheKey 且 sessionId 非空 → `prompt_cache_key`
- [x] ILlmDialect default `applyCacheHeaders`（no-op）+ AbstractLlmDialect 实现（cacheKeyHeader，全部方言继承）+ ChatServiceImpl.buildHttpRequest 调用
- [x] 引擎 stamp 收敛于 resolveRoutedOptions 末尾一处（regular+双 wakeup 路径均先经它）+ SingleTurnExecutor 从零创建 options（round 1 Minor-5）
- [x] 单测：TestCacheKeyPlacement 8 项（prompt_cache_key 含缺 sessionId 跳过与 null modelConfig、header 注入含未配置/缺键、全方言继承 header placement）+ TestCacheKeySessionStamping 2 项（regular/SingleTurn stamping）

Exit Criteria:

- [x] 测试全绿；`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am` 通过（core 全绿 / agent 3535 全绿，2026-10-03）
- [x] **接线验证**：ChatServiceImpl 真实调用 applyCacheHeaders（buildHttpRequest 调用点 + header 断言测试）
- [x] **无静默跳过**：配置放置但缺 sessionId → debug log 显式记录（AbstractLlmDialect.applyCacheHeaders + owner doc 登记该语义）
- [x] `ai-dev/logs/` 条目更新（ai-dev/logs/2026/10-03.md WI20 条目）

### Phase 3 - owner doc 与收口

Status: completed
Targets: `nop-ai-agent-llm-layer.md`、`nop-ai-agent-context-compaction-economics.md`、双 roadmap、log

- Item Types: `Proof`

- [x] llm-layer.md：新增 §八点五 缓存键放置框架（三形态表 + 配置面 + 缺 sessionId 语义 + **辅助调用路径不 stamp 裁定** + system 块化 parse 侧不对称登记 + 迁移说明：providerHints.cache_control 不再被消费）；§3.3 第 3 点 providerHints 示例改写
- [x] compaction-economics.md：§四点五 指针注记（断点引擎与"KV 前缀保持"原则互补）
- [x] autonomous roadmap WI8 checkbox 关闭（附落地验收引用：测试名 + 删除锚点）；refactor roadmap WI20 勾选 + Current Baseline 锚点（closure audit 后）

Exit Criteria:

- [x] owner doc 在位且与 live 一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2026-10-03 实测 0 error）

## Closure Gates

- [x] 缓存键放置三形态端到端连通（配置 → buildBody/header 真实产出；引擎 sessionId → 键值——audit Anti-Hollow 链逐段验证）
- [x] 独立子 agent closure-audit 已完成并记录证据（agent_2a941a7e，APPROVED：0 Blocker / 0 Major / 6 Minor 非阻塞）
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-agent -am` 通过（audit 实测 exit 0；xdef 面含 nop-xdefs 预编译）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2302-wi20-cache-key-placement.md --strict` 退出码 0
- [x] checkstyle：commit 钩子承担（No separate checkstyle gate required）
- [x] 受影响 owner docs 已同步
- [x] git commit 完成（剔除无关 CRLF 噪声文件）

## Known Residuals

### 缓存放置效果无 live provider 验证（无外部 API 凭证）
- Classification: watch-only residual
- Why Not Blocking Closure: 请求形态按 provider 公开契约构造（Anthropic content-block cache_control / OpenAI prompt_cache_key / x-session-id），字节级测试锁形；实际命中率取决于 provider 侧
- Successor Required: no

### 缺 sessionId 时放置跳过（debug log，不报错）
- Classification: watch-only residual
- Why Not Blocking Closure: 成本优化语义（zcode/unreal 同为 optional key）；owner doc 显式登记
- Successor Required: no

## Non-Blocking Follow-ups

- 仅含 thinking 块的轮"不计入 N"分支无直达测试（现测试注释与 convertMessage 合并轮实际行为不符，断言结果仍正确）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: 断言语义（thinking 永不携带、断点落可承载块）已覆盖；补一条直达用例为增强
  - Successor Required: no
- §八点五"缺 sessionId → debug log"措辞待收紧（live 仅 header 形态有 log，OpenAI 字段形态静默跳过）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: plan 文本已正确限定；owner doc 措辞收紧随下次触碰
  - Successor Required: no
- ChatServiceImpl 全链 header 测试（cacheKeyHeader → 捕获 httpClient 最后请求断言）可选加强
  - Classification: `optimization candidate`
  - Why Not Blocking Closure: 调用点 live 核实 + dialect 级测试 + 既有 wiring 测试全量经过该路径
  - Successor Required: no
- plan L22 相对路径链接 doc-links warning（warning-only，工具不判 fail）
  - Classification: `watch-only residual`
  - Why Not Blocking Closure: exit 0 不受影响；nop-bytecode 旧 plan 同款先例
  - Successor Required: no

## Closure

Status Note: 三形态全部落地且配置门控默认关闭（请求字节零变化）：Anthropic content-block 断点引擎（system 块化/非空 turn 倒序/thinking 跳过/负值=off/TTL 变体/确定性）、OpenAI prompt_cache_key、会话亲和 header（全方言继承）；WI8 纠错吸收（providerHints.cache_control 顶层透传删除，零测试牵连）；引擎 sessionId stamping 收敛 resolveRoutedOptions 单点 + SingleTurnExecutor 从零创建；辅助调用路径不 stamp（Non-Goal 裁定，与 autonomous WI11 划界）。llm.xdef 平台级契约演进（additive 四属性 + _LlmModelModel 再生）。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理 agent_2a941a7e-9320-456c-bcab-c91e94a9e8f8（fresh session）
- Evidence:
  - Phase 1-3 全部 Exit Criteria 逐条 PASS（live 锚点：AnthropicDialect.applyCacheBreakpoints :341-394、OpenAiDialect :169-171、ILlmDialect :77-79 + AbstractLlmDialect :278-292、ChatServiceImpl :277、ReActAgentExecutor :976-978、SingleTurnExecutor :80-83）
  - Anti-Hollow：三条配置→产出链（断点/字段/header）均 body/header 级测试证据 + sessionId stamp→键值流动经 IChatService 边界真实 ChatRequest 断言；hollow-scan 0 findings
  - 实测：core+agent 全量 exit 0；check-doc-links 0 error；scan-hollow 0；check-plan-checklist 0
  - autonomous WI8 验收点达成（content block 级放置 + 测试 providerHintsCacheControlNoLongerLeaksTopLevel）；Known Residuals 无 in-scope live defect 降级（parse 侧不对称已双登记后继）
  - Minor 6 条（非阻塞）：L99 stale 已补勾；仅 thinking 轮分支无直达测试（测试注释与实际合并行为不符，断言结果正确）；§八点五"缺 sessionId log"措辞待收紧（仅 header 形态有 log）；ChatServiceImpl 全链 header 测试可选加强；commit 剔除 CRLF 噪声文件；plan L22 相对路径 warning-only——后 4 条登记 Non-Blocking Follow-ups

Follow-up:

- 见 Known Residuals + Non-Blocking Follow-ups
