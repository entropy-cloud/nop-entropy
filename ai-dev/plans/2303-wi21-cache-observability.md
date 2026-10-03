# 2303 WI21 缓存观测全 dialect 接线（refactor roadmap M6）

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI21（Deliverable: 配置默认化 + ResponsesDialect 代码变更 + 测试；Owner: `ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md`；来源: unreal 比对 §6）
> 划界: WI1 必裁③——本 plan 只做**解析面**（全 dialect 缓存字段接入 + raw 保留）；**消费面**（IUsageRecorder/事件 payload 暴露）归 autonomous WI3，正交分层不合并
> Protected Areas: ChatUsage（nop-ai-api 公共 API）additive 字段 → plan-first + owner doc + 迁移说明
> Adversarial Review: round 1 子代理 agent_442356fa——1 Blocker（usageRootPath 无 config 通道裁定）+3 Major（deepseek 字段形态/cacheMissTokens/流式不对称）+5 Minor 全部吸收；round 2 子代理 agent_db637fad——APPROVED（10/10 修复 PASS），N-1（Gate 预勾选已回退）/N-2（free.llm.xml extends deepseek 继承面执行时登记）已记录

## Purpose

nop 缓存解析资产在位但开箱零观测：`AbstractLlmDialect.parseUsage` 通用通道（promptCacheHitTokensPath/promptCacheCreationTokensPath 配置）存在但仓库内无任何 provider 配置设置该 path；ResponsesDialect 自有 parseUsageFromResponses 无缓存字段；usage 原始形态不保留（方言翻译与审计无据）。本 plan 补齐全 dialect 解析面。

## Current Baseline

（执行时 HEAD `23beb4aa29`，2026-10-03 实测）

- `AbstractLlmDialect.parseUsage:420-450`：prompt/completion/total 走 default+override 通道；**缓存字段仅在有显式配置时解析**（responseConfig.getPromptCacheHitTokensPath() 非空才 getIntByPath）——无 dialect 级默认值。
- 四个调用点：OpenAiDialect:235、GeminiDialect:430、OllamaDialect:300、AnthropicDialect:474——均只传 prompt/completion/total 三个 default path。
- **AnthropicDialect 已有专用缓存解析**（:479-495 区域：`usage.cache_read_input_tokens`→cacheHitTokens、`usage.cache_creation_input_tokens`→cacheCreationTokens，映射注释在位）——Anthropic 面已完整，本 plan 不动其逻辑（仅补 raw 保留）。
- **ResponsesDialect.parseUsageFromResponses:465-475**：仅 input/output/total，无缓存字段；两处使用（non-stream :420 + stream chunk :563）。
- **Shipped llm 配置**（`nop-ai-core/src/main/resources/_vfs/nop/ai/llm/`）：default/gemini/ollama/claude 等均未设置 promptCacheHitTokensPath/promptCacheCreationTokensPath（开箱零观测confirmed）。
- Provider usage 形态：OpenAI chat completions `usage.prompt_tokens_details.cached_tokens`（命中；OpenAI 无 cache-write 计费概念）；Gemini `usageMetadata.cachedContentTokenCount`；Ollama 本地运行时**无缓存字段**；Responses `usage.input_tokens_details.cached_tokens`。
- `ChatUsage`（nop-ai-api）：cacheHitTokens/cacheCreationTokens/cacheMissTokens 在位；copy() 逐字段复制（:132-141）；无 raw 保留字段。
- `getIntByPath:390` 支持点号嵌套路径（BeanPropHelper.getIn 任意深度下钻、缺失路径安全 null）。
- **流式 usage 解析现状（round 1 P4 登记）**：OpenAI/Gemini/Ollama 的 parseStreamChunk 完全不解析 usage（连基础 token 都无）；有流式 usage 的仅 Anthropic（:609-619 手写，不走 parseUsage→raw 流式不捕获）与 Responses（:563 共用 parser→raw 自动捕获）。本 plan 限定 non-stream 响应面，流式缺口登记 Known Residual（锚 WI23 流式管线评估）。

## Goals

- **parseUsage 通用通道扩展**：新增 `defaultCacheHitPath`/`defaultCacheCreationPath`/`defaultUsageRootPath` 三参数。**config 优先序**：hit/creation 维持既有"responseConfig 显式配置优先、dialect 默认兜底"（round 1 P1 裁定：**usageRootPath 仅有 dialect 级默认、无 config 通道**——config 通道需动 llm.xdef/`_LlmResponseModel` 生成管线，超本 plan 面）。`defaultUsageRootPath` 非空时抽取该子 map 存入 `ChatUsage.rawUsage`（Usage Raw 原始保留——方言翻译与审计依据）。**签名裁定（round 1 i）**：旧 5 参签名保留为 delegating 过载（缓存参数传 null），新 8 参为主实现；仓内 4 个调用点全部迁移新签名（protected 方法为仓外自定义 dialect 扩展面，shim 避免 source break）。
- **交付物形态偏差声明（round 1 P8）**：roadmap 原文"默认路径配置化"落地为 **Java 级 dialect 默认值**（parseUsage 参数）而非逐 llm.xml 配置文件——x:extends 继承链全量覆盖、零配置重复，严格优于逐文件配置；deepseek 例外见下。
- **dialect 级默认值**：OpenAI（hit=`usage.prompt_tokens_details.cached_tokens`，creation 无——OpenAI 无 cache-write 计费概念，传 null，root=`usage`）；Gemini（hit=`usageMetadata.cachedContentTokenCount`，creation 无，root=`usageMetadata`）；Ollama（三者皆 null——本地运行时无缓存字段，显式登记不假装）；Anthropic（root=`usage`，hit/creation 专用逻辑不动）。
- **deepseek 显式配置（round 1 P2）**：deepseek 缓存字段为顶层 `usage.prompt_cache_hit_tokens`（非 OpenAI 形态），dialect 默认值对其解析为 null——`deepseek.llm.xml` 加显式 `promptCacheHitTokensPath="usage.prompt_cache_hit_tokens"`（一行配置入 scope）。volcengine/azure/lm-studio/bailian 字段形态执行时以 fixture 实证，无实证前不配置（watch）
- **ResponsesDialect 接入**：parseUsageFromResponses 增 `input_tokens_details.cached_tokens`→cacheHitTokens；rawUsage=usage map 本身（non-stream 与 stream chunk 两处共用同一 parser，自动同享）。
- **ChatUsage.rawUsage（nop-ai-api additive）**：`Map<String,Object> rawUsage` 字段 + getter/setter + copy() 携带；迁移说明=additive（旧构建反序列化新 JSON 忽略未知字段，无 roundtrip 破坏；新构建读旧数据字段为 null）。
- **测试**：OpenAI/Gemini 默认解析（含 raw 捕获）、config override 优先、Ollama 显式无缓存字段、Responses non-stream + stream chunk 缓存解析与 raw、copy() 保真、既有 usage 解析零回归。

## Non-Goals

- **消费面**（cacheHitTokens 经 IUsageRecorder/事件 payload 暴露为可观测指标）——autonomous WI3 范围（WI1 必裁③：解析/消费正交分层）。
- **cacheMissTokens 不在本 plan 填充通道内（round 1 P3 裁定）**：WI1 裁定书所列共享契约点含 cacheMissTokens，但 live 全仓零生产写入方且 llm.xdef 无 miss path 配置——补通道需动生成管线，超本 plan 面；登记 Known Residual（successor=autonomous WI3 消费面落地时按 provider 计算或补配置通道），owner doc 收口时注记 WI1 裁定书该句按 hit/creation 两字段成立。
- 计价扩展（cache write/read 差价）——autonomous WI3 文档同步范围。
- Anthropic 专用缓存解析逻辑重构（已在位且正确）。
- deepseek 之外的 x:extends 配置逐文件 path 配置——volcengine 等字段形态未经实证，无实证前不配置（watch-only）；azure/lm-studio/bailian 为 OpenAI 兼容形态，dialect 默认值覆盖。

## Scope

### In Scope

- nop-ai-api：ChatUsage.rawUsage additive 字段。
- nop-ai-core：AbstractLlmDialect.parseUsage 扩展、OpenAI/Gemini/Ollama/Anthropic 调用点默认值、ResponsesDialect 缓存字段 + raw、测试。
- owner doc（llm-layer）+ roadmap 锚点 + log。

### Out Of Scope

- autonomous WI3 消费面；计价；nop-ai-agent 变更。

## Execution Plan

### Phase 1 - 解析面落地

Status: completed
Targets: `ChatUsage.java`、`AbstractLlmDialect.java`、OpenAI/Gemini/Ollama/AnthropicDialect 调用点、`ResponsesDialect.java`、nop-ai-core 测试

- Item Types: `Feature`

- [x] ChatUsage.rawUsage 字段 + getter/setter（`@JsonInclude(NON_NULL)` 对齐既有风格——round 1 P6）+ copy() 携带（**共享引用**语义：raw map 为解析期一次性构建的只读视图，不做防御性拷贝，javadoc 写明——round 1 P7；迁移说明=additive 字段无生产持久化消费方，无 roundtrip 面——round 1 P9 改写）
- [x] parseUsage 扩展三默认参数（旧 5 参签名保留 delegating shim——round 1 i 裁定；hit/creation config 优先序不变；rawUsage 经 getRawByPath 抽取）
- [x] OpenAI/Gemini/Ollama/Anthropic 调用点接入 dialect 级默认值（Ollama 显式 null 三参；Anthropic 仅补 raw root）
- [x] ResponsesDialect.parseUsageFromResponses 增缓存字段 + rawUsage（non-stream/stream 共用；getByPath 点号下钻）
- [x] deepseek.llm.xml 显式 `promptCacheHitTokensPath="usage.prompt_cache_hit_tokens"`（round 1 P2）——x:extends 属性级合并语义由测试锁定（继承 OpenAI 形态路径存活）
- [x] 单测：TestCacheObservability 8 项（OpenAI/Gemini 默认解析+raw、override 优先、Ollama 显式无字段、Anthropic 专用映射不动+raw、Responses 双路径、copy 保真、deepseek 继承合并）

Exit Criteria:

- [x] 单测全绿；`./mvnw test -pl nop-ai/nop-ai-core -am` 通过（456 全绿，2026-10-03，含 nop-ai-api）
- [x] **接线验证**：默认配置（无任何 path 配置）下 OpenAI/Gemini/Responses 响应解析出 cacheHitTokens（开箱观测成立——TestCacheObservability 断言）
- [x] **无静默跳过**：Ollama 无缓存字段为显式登记（测试断言 null），非遗漏
- [x] `ai-dev/logs/` 条目更新（owner doc 归 Phase 2）

### Phase 2 - owner doc 与收口

Status: completed
Targets: `nop-ai-agent-llm-layer.md`、roadmap、log

- Item Types: `Proof`

- [x] llm-layer.md：§八点六 缓存观测解析面（全 dialect 默认路径表/ChatUsage.rawUsage 契约与迁移说明/与 autonomous WI3 消费面的分层边界/miss 与流式 residual 注记）
- [x] refactor roadmap WI21 勾选 + Current Baseline 锚点（closure audit agent_3fd29b04 APPROVED 后落位）

Exit Criteria:

- [x] owner doc 在位且与 live 一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（2026-10-03 实测）

## Closure Gates

- [x] 全 dialect 缓存解析开箱成立（**non-stream 响应面**——round 1 P4 限定；默认配置下 OpenAI/Gemini/deepseek/Responses cacheHitTokens 可解析；rawUsage 在位）
- [x] 独立子 agent closure-audit 已完成并记录证据（agent_3fd29b04，APPROVED：0 Blocker / 0 Major / 2 Minor 非阻塞）
- [x] `./mvnw test -pl nop-ai/nop-ai-core -am` 通过（456 全绿）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2303-wi21-cache-observability.md --strict` 退出码 0
- [x] checkstyle：commit 钩子承担（No separate checkstyle gate required）
- [x] 受影响 owner docs 已同步
- [x] git commit 完成

## Known Residuals

### OpenAI/Gemini 无 cache-write 概念（creation 恒空）
- Classification: watch-only residual
- Why Not Blocking Closure: provider 契约如此（仅 Anthropic/OpenRouter 有 cache-write 计费）；creation 路径留空是诚实映射非遗漏
- Successor Required: no

### 流式 usage/raw 缺口（OpenAI/Gemini/Ollama 流式不解析 usage；Anthropic 流式 raw 不捕获）
- Classification: watch-only residual
- Why Not Blocking Closure: 流式管线整体缺 usage 解析是存量形态（非本 plan 引入）；WI23 流式管线可行性评估为 natural successor 锚点
- Successor Required: no

### cacheMissTokens 无填充通道（全仓零生产写入方）
- Classification: watch-only residual
- Why Not Blocking Closure: 补通道需动 llm.xdef 生成管线；WI1 裁定书该表述按 hit/creation 两字段成立（owner doc 收口注记）
- Successor Required: no（autonomous WI3 消费面落地时裁定：按 provider 计算 prompt−hit 或补配置通道）

### volcengine 等字段形态未实证
- Classification: watch-only residual
- Why Not Blocking Closure: 无 fixture 实证前配置是猜测；OpenAI 兼容形态（azure/lm-studio/bailian）已由 dialect 默认覆盖
- Successor Required: no

### rawUsage 暂无消费方（WI3 消费面未落地）
- Classification: watch-only residual
- Why Not Blocking Closure: WI1 必裁③ 分层裁定——raw 是消费面的数据前提，先解析后消费
- Successor Required: no（autonomous WI3 承接）

## Closure

Status Note: 解析面全 dialect 落地——parseUsage 8 参主实现 + 5 参 shim（仓外 dialect 子类零 source break）、OpenAI/Gemini/Ollama/Anthropic 调用点默认值、Responses 缓存字段（双路径共用 parser）、deepseek 显式配置（x:extends 属性级合并语义经测试锁定）、ChatUsage.rawUsage additive（nop-ai-api）；消费面归 autonomous WI3（WI1 必裁③ 正交分层，audit 确认零越界）。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理 agent_3fd29b04-e65d-4952-9f1a-9bdbec958c24（fresh session）
- Evidence:
  - A-G 逐条 PASS（live 锚点：ChatUsage :56/:127-134/:157；AbstractLlmDialect 8 参 :438-478 + shim :425-429 + getRawByPath :485-491；四调用点 :236-238/:430-432/:301-302/:475-476；ResponsesDialect :465-481；deepseek.llm.xml:6）
  - Anti-Hollow：parseResponse/parseStreamChunk → parseUsage/parseUsageFromResponses → ChatUsage.cacheHitTokens/rawUsage 五个响应面全链路连通
  - 实测：core 456 + api 57 全绿；doc-links 0 error；hollow 0；checklist exit 0
  - Minor 2 条（非阻塞）：AbstractLlmDialect:469 注释归属笔误（plan 2302→2303，已顺手修正）；Closure Gates 记账排序（本 audit 解锁）

Follow-up:

- 见 Known Residuals
