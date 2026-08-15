# W5 模型类路由组 + 动态选择策略（nop-ai-core）

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Source: `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（W5）、`ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.3/§3.4/§4.1/§五 Q8/Q10）
> Related: `ai-dev/design/nop-ai-gateway/01-architecture.md`、`ai-dev/plans/2026-08-15-0849-1-w4-dialect-bidirectional-conversion.md`、`ai-dev/plans/2026-08-15-0604-3-w3-llm-xdef-concurrency-limit.md`
> Mission: nop-ai-gateway-failover
> Work Item: W5

## Purpose

在 nop-ai-core 落地模型类路由组 + 动态选择策略：模型按"级别/类"组织，每类对应一组候选（备选模型 + 账号组合）；候选选择顺序由可插拔策略接口决定（默认策略 = 健康度 + 并发感知 + 声明序）；提供候选集游走（类内 → provider 链扩展）与并发记账/全池饱和 fail-loud 语义（进程内状态）。这是 W6（本地适配器）与 W7（网关形态）的消费前置（roadmap Stage 5 critical path；依赖 W2/W3 已 done）。

## Current Baseline

（live repo 核实，2026-08-15）

- **W2 已 done**：LLM 可靠性子集下沉 nop-ai-core（`io.nop.ai.core.reliability`）——`AccountChain`（有序备用账号链游走器，`next()` 推进游标，链耗尽返回 null = 调用方 fail-loud）、`IAccountChainResolver`、`ThresholdBreaker`/`ICircuitBreaker`/`CircuitState`（`allowCall(modelKey)`/`recordSuccess`/`recordFailure`/`getState`，熔断键 = `ModelKeys.buildModelKey(options)` = `provider:model`）、`ProviderFailoverChain`/`IProviderFailoverChainResolver`、`LlmErrorClassifier`、`IRetryPolicy`/`StandardRetryPolicy` 等。
- **W3 已 done**：`concurrencyLimit` 配置面（provider 根元素缺省 + `<accounts><account>` 账号级覆盖；缺省 null = 不限制；显式 0/负数 = 显式不限制不回退）——读取入口 `LlmConfigHelper.resolveConcurrencyLimit(provider)` / `resolveConcurrencyLimit(provider, account)`（`LlmConfigHelper.java:187-207`）。
- **账号链读取**：`LlmConfigHelper.resolveAccountChain(provider)`（`:161`）→ `List<LlmAccountModel>`（有序备用链，不含主账号）。
- **provider 链读取**：`llm-failover.xdef`（`nop-kernel/nop-xdefs/.../ai/`，`LlmFailoverConfig`/`LlmFailoverProviderModel`：`provider`/`model`/`tier`）+ `LlmConfigHelper.resolveFailoverChain(primaryProvider)`（`:231`，返回 primary 之后的有序子表；无配置/未知 primary/表尾 = 空列表 = 零回归 fail-loud）。
- **候选下沉面**：`ChatOptions`（`nop-ai/nop-ai-api/.../chat/ChatOptions.java`）已有 `provider`/`model`/`accountKey`/`accountBaseUrl` 字段（getter 声明行 146/155/308/319）——W5 产物输出的候选信息可经这些字段表达（下沉动作本身属 W6/W7 编排）。
- **模型类路由组不存在**：无 model-class 数据结构、无解析、无配置面（需求 §3.3 确认）；`llm-failover.xdef` 是 provider 级链，非模型类级。
- **动态选择策略不存在**：仅固定顺序 `AccountChain` 游标（`AccountChain.next()`），无可插拔策略接口。
- **并发计数不存在**：`concurrencyLimit` 字段已就位（W3）但无进程内计数/检查运行时。
- **开放问题待 plan 阶段裁决**：Q8（模型类分组配置形态：扩展 `llm.xdef`/`llm-failover.xdef` vs 新 `model-class.xdef` 配置面）、Q10（规则策略 DSL 形态与 IoC bean 绑定）。W1 spike 的 Follow-up 已明确"Q8/Q10 归 W5 plan 阶段裁决"。
- **roadmap 粒度约束**：W5 工作按 roadmap 标注为两组可拆工作（数据结构/接口/默认策略 | 游走/并发记账/规则策略），要求 plan-first 先行拆分裁定，不硬撑单文件。本 plan Phase 1 执行该裁定。
- **归属约束**（需求 §4.1）：模型类候选集（数据结构 + 解析）与选择策略接口 + 默认/规则策略归属 nop-ai-core；网关游走/切换/缓冲/重试**编排**归属 nop-ai-gateway（W6/W7）——本 plan 交付 in-core 机制与游走原语，编排不落地。
- **配置面约束**（roadmap/project-context）：若 Q8 选择新增 xdef（nop-kernel/nop-xdefs，protected area），本 plan 即 plan-first 载体；生成物经 codegen 再生成（W3 同款流程：先 `./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` 再下游 codegen），禁止手编 `_gen` 产物。
- 无任何模型类/选择策略相关实现代码（grep 实证无 `ModelClass`/`SelectionStrategy` 类）。

## Goals

- 模型类路由组数据结构 + 配置解析（nop-ai-core，`LlmConfigHelper` 家族）：模型类 → 候选集（模型 + 账号组合，可引用 `<accounts>` 与 provider 链）的声明与读取；请求 model → 模型类映射；**未配置路由组时零回归**（沿用既有单 provider 行为）。
- 选择策略接口 + 默认策略（健康度 + 并发感知 + 声明序）：策略输入 = 请求、候选集、各候选健康状态（熔断/并发/冷却）、本轮已尝试候选；输出 = 选中候选。规则策略（Q10）经 Phase 1 裁定拆至 successor plan（roadmap 标注"可选实现"）。
- 候选集游走（类内 → 被动失败路径经 provider 链扩展）+ 并发记账（进程内 per-account 计数）+ 全池饱和 fail-loud（错误码 + 饱和语义，主动路径不跨 provider 链——需求 §3.3 裁决）。
- 复用优先不变式：不新建第二套账号池/熔断/错误分类——消费 `LlmConfigHelper`/`ThresholdBreaker`/`LlmErrorClassifier`/`ProviderFailoverChain` 既有机制。
- 需求文档 Q 表回填（Q8/Q10 处置结果）+ §3.3/§3.4 落地状态同步；roadmap W5 状态推进。

## Non-Goals

- 网关拦截器/本地适配器的编排（W6/W7 消费本 plan 产物；本 plan 不落地调用编排）。
- 规则策略（XLang 规则 DSL）实现（Q10 裁定 → successor plan；接口预留扩展点，IoC bean 绑定机制留 successor）。
- 熔断键维度扩展（保持 `provider:model`，需求 §3.3 裁决）。
- 配置热更新（显式 non-goal，需求 §3.4）。
- 多实例状态共享（进程内状态，需求 §3.6 显式 non-goal）。
- 动态选择策略的"权重/成本"维度（需求 §五 Q9 已决：默认策略不含权重/成本，委托规则策略）。

## Scope

### In Scope

- ROUTE-01: model-class 数据结构与解析（Q8 配置形态在 Phase 1 裁决；含 model → 类归属映射机制裁定）。
- ROUTE-02: 选择策略接口（输入/输出契约）。
- ROUTE-03: 默认策略（健康度 + 并发感知 + 声明序）。
- ROUTE-04: 候选集游走（类内 → provider 链扩展）+ 并发记账/全池饱和 fail-loud 语义（进程内状态）。
- Phase 1 裁定：Q8/Q10/规模拆分/包名/错误码/数据结构形态。

### Out Of Scope

- W6（本地适配器 + 流式 failover）/ W7（网关形态）——编排层消费。
- 规则策略 DSL（Q10，successor plan）。
- 并发计数的调用点接线（acquire/release 在真实调用路径上的挂钩属 W6/W7 编排；本 plan 交付计数原语 + router 读取 + 测试驱动的释放语义）。

## Execution Plan

### Phase 1 - 裁定：Q8 配置形态 + Q10 拆分 + 规模与设计基线（ROUTE-01 前置）

Status: completed
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`（§3.3/§3.4/§五 Q 表，仅裁定记录；正式回填在 Phase 5）

- Item Types: `Decision | Proof`

- [x] **规模拆分裁定（roadmap 粒度约束的落地）**：裁定 W5 拆分为 1 个 plan（本 plan）覆盖 ROUTE-01/02/03-默认/04，规则策略（Q10）拆至 successor plan。理由（落档）：(a) plan guide 反空壳原则——数据结构/接口/默认策略单独成 plan 会产出"无运行时消费方"的空壳组件，游走+并发记账是本 plan 内部唯一真实消费链（router 调策略、router 读并发状态），必须同 plan 才能满足接线验证；(b) 规则策略为 roadmap 标注"可选实现"，其 DSL 形态（Q10）有独立裁决面，不阻塞默认机制 closure；(c) 按 W2（18 类迁移）同量级评估，单 plan 规模可控。
  - **裁定（已执行）**：维持单 plan 拆分——本 plan 交付 ROUTE-01/02/03-默认/04；Q10 规则策略（XLang DSL + IoC 绑定）为显式 successor ownership（见 `Deferred But Adjudicated`，Successor Required: yes，Phase 5 在 roadmap/backlog 登记）。理由即本 item 的 (a)(b)(c)，落档成立。
- [x] **Q8 配置形态裁定**：二选一——① 扩展 `llm-failover.xdef`（新增 `<modelClasses>` 分组声明）；② 新建 `model-class.xdef` 配置面（opt-in 文件，`LlmConfigHelper` 家族解析）。裁决考量落档：模型类跨 provider 的全局语义 vs `llm.xdef` 的 per-provider 结构不匹配（llm.xdef 扩展倾向性弱）；protected area + codegen 流程（W3 同款）在两选项下相同；零回归要求（缺省无配置 = 无路由组 = 既有行为）两选项均可满足。若选新 xdef：落档文件路径、xdef:name、bean 包名（建议 `io.nop.ai.core.model` 与 `LlmFailoverConfig` 同构）、opt-in 路径（建议 `/nop/ai/llm/_default.model-class.xml` 同 `FAILOVER_CONFIG_PATH` 模式）；若选扩展：落档 xdef 结构变更面。
  - **裁定（已执行）：选 ② 新建 `model-class.xdef` 配置面**。理由：(a) 模型类候选集是**跨 provider 的全局语义**（一个模型类引用多个 provider 的账号），与 `llm.xdef` 的 per-provider 文件结构（每 provider 一文件）不匹配——扩展倾向性弱；(b) `llm-failover.xdef` 是 provider 级链（单全局表，按 primary 取后续子表），与"model → 类 → 候选集"语义不同层，混入会污染两个配置面的单一职责；(c) 新 xdef 保持 `llm.xdef`/`llm-failover.xdef` 零改动，零回归面最小（opt-in：缺省无文件 = 无路由组 = 既有行为）。落档：文件路径 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/model-class.xdef`；`xdef:name="ModelClassConfig"`；bean 包名 `io.nop.ai.core.model`（与 `LlmFailoverConfig` 同构）；opt-in 配置文件路径 `/nop/ai/llm/_default.model-class.xml`（同 `FAILOVER_CONFIG_PATH` 模式）；codegen 载体 `precompile/gen-ai-xdsl.xgen` 必须新增渲染行（否则零生成物）。
- [x] **Q10 拆分裁定（规则策略 → successor plan）**：规则策略（XLang 规则 DSL + IoC bean 绑定）作为本 plan 的 successor ownership（Classification: out-of-scope improvement——roadmap 标注"可选实现"）。本 plan 只落接口扩展点（策略接口本身即可插拔，无需额外预留）；Q10 处置结果在 Phase 5 回填 Q 表。
  - **裁定（已执行）**：Q10 → successor plan（`Deferred But Adjudicated` 段登记，Classification: `out-of-scope improvement`，Successor Required: `yes`，Successor Path: 独立 successor plan（roadmap 登记为 W5b 或独立 backlog 条目），Phase 5 落登记）。本 plan 交付的 `ISelectionStrategy` 接口即扩展点（规则策略 = 同一接口的另一种实现），无需额外预留。
- [x] **数据结构形态裁定**（ROUTE-01 设计基线）：模型类声明内容 = id + 候选集；候选条目字段 = provider + model（可省略 = provider 默认模型）+ 可选 accountRef（引用 `{provider}.llm.xml` `<accounts>` 中账号 id；缺省 = 主账号 + 该 provider 有序账号链语义）+ 可选 model 覆盖；请求 model → 模型类归属机制 = 显式成员声明（每类列出归属模型的 model 名清单，推荐，确定性最强）vs 模式匹配（落档取舍）；**成员匹配键粒度**：model 名全局匹配（推荐——同 model 名跨 provider 歧义由"首个声明命中 + 请求 provider 上下文"消解）vs (provider, model) 复合匹配——落档取舍；未归属任何类的 model = 无路由组行为（零回归）。
  - **裁定（已执行）**：模型类声明 = `id` + `members`（归属 model 名清单）+ `candidates` 候选集；候选条目 = `provider`（必填）+ `model`（可选，省略 = provider 的 defaultModel）+ `accountRef`（可选，引用 `{provider}.llm.xml` `<accounts>` 中账号 id；缺省 = 主账号 + 该 provider 有序账号链展开——解析期展开为"主账号候选 + 每备用账号候选"，主账号在前）；**请求 model → 模型类归属 = 显式成员声明**（members 清单，确定性最强，拒绝模式匹配——模式匹配有歧义面且无既有先例）；**成员匹配键 = model 名全局匹配**（同 model 名跨 provider 歧义由"首个声明命中（模型类声明顺序）+ 请求 provider 上下文"消解——请求 model 归属类后，候选集内 provider 不同不影响归属）；未归属任何类的 model = 无路由组行为（零回归，返回空结果/无类）。
- [x] **包名与错误码裁定**：运行时包建议 `io.nop.ai.core.routing`（Router + 策略 + 并发注册表，落档理由：与 `reliability` 包同构的领域包划分）；配置模型随 Q8 产物（`io.nop.ai.core.model` + `_gen` 若 xdef 生成）；全池饱和 fail-loud 错误码 = `NopAiCoreErrors` 新增（建议 `ERR_AI_MODEL_CLASS_SATURATED`，英文消息，落档命名与语义；**饱和语义范围**：并发饱和（需求 §3.3 字面"所有候选均达并发上限"）与健康度饱和（全候选熔断 OPEN/不可用）共用此码，语义 = "无可用候选"——见 Phase 4 裁定）。
  - **裁定（已执行）**：运行时包 = `io.nop.ai.core.routing`（`ModelClassRouter` + `ISelectionStrategy`/`DefaultSelectionStrategy` + `ConcurrencyRegistry` + 候选/健康视图类型——与 `reliability` 包同构的领域包划分）；配置模型 = `io.nop.ai.core.model` + `_gen`（codegen 生成，禁止手编）；错误码 = `NopAiCoreErrors.ERR_AI_MODEL_CLASS_SATURATED`，ID `nop.err.ai.model-class.saturated`，**英文消息**（plan 裁定，语义 = "当前无可用候选"，落档理由：router 为公共能力组件，饱和错误会被 W6/W7 编排与前端消费，英文描述保证跨模块语义稳定、不经 i18n 改写），参数 `ARG_MODEL_CLASS`（= "modelClass"）区分触发类。饱和语义范围 = 并发饱和 + 健康度饱和（全候选熔断 OPEN/不可用）共用此码（Phase 4 裁定确认，不建第二错误码）。
- [x] **并发计数键裁定**：进程内 per-account 计数键 = `(provider, accountId)`（`resolveConcurrencyLimit(provider, account)` 的消费键一致；主账号路径 = `(provider, null)` 取 provider 缺省——W3 语义对齐）；裁定"流建立 +1/结束 -1"的挂钩时点语义（W1 spike 输入：挂钩 `callStream` 调用时刻而非订阅时刻——本 plan 只落计数原语契约，挂钩编排归 W6/W7）。
  - **裁定（已执行）**：计数键 = `(provider, accountId)`；主账号（无 `LlmAccountModel` 实例）= `(provider, null)`，并发上限取 `resolveConcurrencyLimit(provider, null)` = provider 级缺省（W3 语义对齐）；`accountId` 具体值 = 展开候选时主账号 = null、备用账号 = 账号 `id`（与 `resolveConcurrencyLimit(provider, account)` 消费键一致）。挂钩时点语义（落档契约）："流建立 +1 / 结束 -1"，挂钩 `callStream` 调用时刻而非订阅时刻（W1 spike 输入）；本 plan 交付计数原语（acquire/release/currentCount）+ router 健康视图读取 + 测试驱动释放语义；真实调用路径挂钩编排归 W6/W7（Non-Blocking Follow-ups 已声明）。
- [x] **provider 链扩展的 primary 键来源裁定**：被动路径经 `resolveFailoverChain(primaryProvider)` 扩展候选集时，`primaryProvider` = **请求目标 provider**（`ChatOptions.provider` 经 `LlmConfigHelper.getProvider` 解析值）——类内候选可跨多 provider，但 failover 链声明以"请求入口 provider"为键（`llm-failover.xdef` 单全局表的既有语义），落档该裁定供 W6/W7 编排对齐。
  - **裁定（已执行）**：`primaryProvider` = 请求目标 provider（`ChatOptions.provider` 经 `LlmConfigHelper.getProvider(options)` 解析值，含 `CFG_AI_SERVICE_DEFAULT_LLM` 缺省回退）。类内候选可跨多 provider，但被动路径的 provider 链扩展以"请求入口 provider"为键（`llm-failover.xdef` 单全局表既有语义——`resolveFailoverChain(primaryProvider)` 取 primary 之后的有序子表）。provider 链候选展开 = 每个 failover provider 的主账号 + 有序账号链（model = 该 provider 声明的 model 覆盖或 defaultModel）。落档供 W6/W7 编排对齐。
- [x] 现有消费点盘点（Proof）：`AccountChain`/`ThresholdBreaker`/`resolveFailoverChain` 在 nop-ai-agent 既有调用方（`LlmCallCoordinator` 等）零改动确认——本 plan 新增组件不触碰既有消费路径。
  - **Proof（已核实）**：grep 实证既有消费方——`LlmCallCoordinator.java:72`（`new AccountChain(resolveAccountChain)`）、`:80`（`new ProviderFailoverChain(resolveFailoverChain)`）、`DefaultAgentEngine.java:152`/`ReActAgentExecutor.java:290`/`ReActAgentExecutorBuilder.java:656`/`DefaultAgentEngineConfig.java:129,497`（`new ThresholdBreaker()`）+ 测试（TestAccountFallbackChain/TestProviderFailoverChain/TestCircuitAwareRouting 等）。本 plan 新增组件全部落在 nop-ai-core 新包 `io.nop.ai.core.routing` + `LlmConfigHelper` **新增方法**（不改既有方法）+ 新 xdef 配置面——零改动既有 reliability 类与既有 helper 方法 → 既有消费路径零改动确认。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 全部 7 项裁定（规模/Q8/Q10/数据结构/包名错误码/并发键/provider 链 primary 键）落档于本 plan 文件（Phase 1 各裁定项内联落档），无未决项；Phase 5 统一回填 requirement doc §五 Q 表（不含"或"分支——落档位置唯一）。
- [x] Q8 裁定附理由与两选项比较；Q10 裁定附 successor 归属（out-of-scope improvement + Successor Required: yes）。
- [x] 规模拆分裁定满足 roadmap 粒度约束（"先行拆分裁定，不硬撑单文件"已执行）与 plan guide 反空壳原则。
- [x] `No owner-doc update required`（正式回填在 Phase 5）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - model-class 数据结构与解析（ROUTE-01）

Status: completed
Targets: `nop-kernel/nop-xdefs/.../ai/`（若 Q8 选新配置面）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/model/`（配置模型 + _gen）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/LlmConfigHelper.java`（解析入口）、`nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/`（解析测试）

- Item Types: `Fix | Decision | Proof`

- [x] 按 Phase 1 Q8 裁定落地配置面：新 `model-class.xdef`（含 javadoc 语义：候选引用 accounts/provider 链、model 归属成员清单、opt-in 缺省零回归）或 `llm-failover.xdef` 扩展；`./mvnw install -pl nop-kernel/nop-xdefs -am -DskipTests` 重新打包 → nop-ai-core codegen 再生成（W3 同款流程）；`git diff` 核对再生 diff 仅含新字段（模板漂移检查，禁止手编 `_gen`）。**若 Q8 选新 xdef（必做）**：codegen 载体 `nop-ai/nop-ai-core/precompile/gen-ai-xdsl.xgen` 显式硬编码 4 行 `renderModel`（prompt/chat-options/llm/llm-failover）——新增 `model-class.xdef` 必须**先向该 xgen 增加渲染行**（`codeGenerator.renderModel('/nop/schema/ai/model-class.xdef', ...)`；该文件是生成管线/codegen 模板，protected area 触点，本 plan 即 plan-first 载体），否则照 W3 流程会静默产出零生成物；更新后再生并 `git diff` 核对新增 `_gen` 类。
- [x] 解析入口（`LlmConfigHelper` 家族）：model → modelClass 解析 + 模型类候选集解析（候选展开：accountRef → 账号链中账号；候选引用 provider → 主账号 + 有序账号链）；**缺省无配置 = 空结果 = 调用方零回归行为**（返回空而非异常，参照 `resolveFailoverChain` 零回归守卫模式）；返回不可变视图。
- [x] 解析测试（Minimum Rules #25）：模型类声明解析（多类、候选含 accountRef/纯 provider/model 覆盖）、model → 类归属（成员清单命中/未命中 = 无路由组）、无配置零回归（无 model-class 文件时行为不变）、未知类/未知 accountRef 的显式失败语义（落档：解析期校验 fail-fast vs 运行期忽略——不得静默吞）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 配置面 + 生成物（若 xdef）+ 解析入口就位；`./mvnw compile -pl :nop-ai-core -am` 通过。
- [x] 解析测试全绿（模型类解析 / model 归属 / 零回归 / 显式失败语义）。
- [x] `git diff` 实证生成物零模板漂移（若 xdef 改动）。
- [x] **无静默跳过**（Minimum Rules #24）：未知 accountRef/类声明冲突有显式失败或显式忽略落档，无空 catch。
- [x] `No owner-doc update required`（文档回填在 Phase 5）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 选择策略接口 + 默认策略（ROUTE-02 + ROUTE-03 默认）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/routing/`（新建：策略接口 + 默认策略 + 候选/健康视图类型）、`nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/routing/`（新建测试）

- Item Types: `Fix | Proof`

- [x] 候选/健康视图类型：候选（provider/model/accountRef/展开后的账号信息）+ 健康状态视图（熔断状态（`ThresholdBreaker.getState(modelKey)`）、并发计数（并发注册表读取）、冷却期状态——复用 `CircuitState`，不新建判定）。
- [x] 选择策略接口：输入 = 请求（ChatRequest/ChatOptions）、候选集、健康状态视图、本轮已尝试候选集；输出 = 选中候选或 null（null = 无可用候选，调用方 fail-loud）。javadoc 契约完整（含"并发超限/熔断 OPEN 跳过不记失败"语义说明）。
- [x] 默认策略实现：跳过熔断 OPEN + 并发饱和候选，其余按声明序返回首个；已尝试候选跳过（本轮游走不重复）；与既有 `AccountChain` 声明序语义一致。
- [x] 策略测试（Minimum Rules #25）：熔断 OPEN 跳过 / 并发饱和跳过 / 声明序 / 已尝试集跳过 / 全部跳过返回 null；并发饱和跳过**不触发**熔断失败记账（策略无 recordFailure 副作用——语义测试）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 策略接口 + 默认策略就位，`./mvnw compile -pl :nop-ai-core -am` 通过。
- [x] 策略测试全绿（6 项语义：OPEN 跳过/饱和跳过/声明序/已尝试/全跳 null/无熔断副作用）。
- [x] **接线验证**（Minimum Rules #23，跨 Phase）：Phase 4 的 router 是默认策略的运行时消费方——本 Phase 以接口 + 单测收口，消费接线验证在 Phase 4 exit criteria 完成（声明依赖，不在本 Phase 假造消费方）。
- [x] **无静默跳过**：策略对"候选健康视图缺失"有显式处理（落档：缺省视为健康 vs 视为不可用），无隐式假设。
- [x] `No owner-doc update required`（文档回填在 Phase 5）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 4 - 候选集游走 + 并发记账 + 全池饱和 fail-loud（ROUTE-04）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/routing/`（新建：ModelClassRouter + 并发注册表）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/NopAiCoreErrors.java`（新错误码）、`nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/routing/`（新建测试）

- Item Types: `Fix | Proof | Decision`

- [x] 并发注册表：进程内 per-`(provider, accountId)` 计数（acquire/release + 当前值查询 + `resolveConcurrencyLimit` 语义消费——null/≤0 = 不限制）；测试驱动释放语义（release 幂等裁定：重复 release 显式报错 vs 幂等——落档）；线程安全（并发测试）。
- [x] 候选集游走（ModelClassRouter）：类内游走（策略选候选 → 健康检查 → 选中）；**主动路径**（无失败事件，如并发饱和跳过）仅在类内游走，类内全饱和 → **全池饱和 fail-loud**（`ERR_AI_MODEL_CLASS_SATURATED`，不跨 provider 链——需求 §3.3 裁决）；**被动路径**（失败后重选，由编排传入"失败标记"）类内耗尽后经 `resolveFailoverChain` 扩展候选集（provider 链候选 = 目标 provider 主账号 + 账号链）。**饱和语义裁定（Decision）**：全池饱和覆盖两类情形——并发饱和（需求 §3.3 字面）与健康度饱和（全候选熔断 OPEN/不可用）——共用 `ERR_AI_MODEL_CLASS_SATURATED`（语义 = "当前无可用候选"，触发原因经错误参数区分，不建第二错误码），落档供 W6/W7 编排消费。
- [x] 游走状态语义（Decision）：per-execution 有状态游走器（对齐 `AccountChain` 模式——单线程 per-call 独占，跨并发调用各建独立实例）；"本轮已尝试候选"由游走器维护并喂给策略。**失败记账归属裁定（Decision）**：被动路径候选失败后的熔断记账（`ThresholdBreaker.recordFailure(modelKey)`）由**编排层**（W6/W7）调用——router 只提供"失败标记入参 + 游走/扩展/饱和语义"，不在 router 内部直接记熔断（保持 router 为纯选择机制；需求 §3.3"同一模型类内多账号连续失败记账跨账号累计"由编排按已尝试候选逐个 recordFailure 实现）——落档供 W6/W7 对齐。
- [x] 游走/饱和测试（Minimum Rules #25）：类内游走全链（model → 类 → 候选选中 → 候选信息可下沉为 ChatOptions 四字段（provider/model/accountKey/accountBaseUrl，accountBaseUrl 为 per-account baseUrl 映射；如选择仅断言最小集需显式说明）的语义断言）、主动路径饱和 fail-loud（并发饱和 + 健康度饱和两情形，断言错误码）、被动路径 provider 链扩展、链耗尽 fail-loud、并发注册表 acquire/release/饱和跳过（router 读取注册表状态——**接线验证**：router 在运行时调用策略 + 读取并发注册表，测试断言调用发生）。
- [x] **端到端验证**（Minimum Rules #22）：一条全链测试——配置（model-class 声明 + accounts + provider 链）→ 解析 → model 归属 → 游走 → 策略选中 → 候选下沉信息（provider/model/accountKey/accountBaseUrl 四字段语义）→ 全池饱和 fail-loud，从配置入口到最终决策出口完整走通。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 并发注册表 + ModelClassRouter + 新错误码就位；`./mvnw compile -pl :nop-ai-core -am` 通过。
- [x] 游走/饱和/并发测试全绿（含端到端全链用例 + 接线断言：策略被 router 调用、注册表被 router 读取）。
- [x] **无静默跳过**：全池饱和/链耗尽均 fail-loud（错误码断言），无 `continue`/空返回兜底。
- [x] 复用优先不变式验证：router/策略消费既有 `LlmConfigHelper`/`ThresholdBreaker`/`resolveFailoverChain`（代码路径实证），无新建第二套账号池/熔断。
- [x] `No owner-doc update required`（文档回填在 Phase 5）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 5 - 文档同步 + roadmap 状态推进

Status: completed
Targets: `ai-dev/design/nop-ai-gateway/02-account-failover-requirement.md`、`ai-dev/design/nop-ai-gateway/01-architecture.md`、`docs-for-ai/02-core-guides/error-handling.md`（核查）、`ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`

- Item Types: `Follow-up`

- [x] `02-account-failover-requirement.md`：§3.3/§3.4 落地状态更新（模型类路由组/动态选择/并发记账已落地 + 指向本 plan）；**§4.1 归属解读记录**——"in-core 游走原语（ModelClassRouter 等，nop-ai-core）vs 网关切换/缓冲/重试编排（nop-ai-gateway，W6/W7）"的边界解读显式落档，避免 W6/W7 审计时被误判归属矛盾（roadmap W5 module area = nop-ai-core 绑定支持该解读）；§五 Q 表回填（Q8 配置形态裁定结果、Q10 → successor plan 处置、Q9 既有已决）。
- [x] `01-architecture.md`：§四.2 分层图/模块归属与 nop-ai-core 新增（model-class 数据结构 + 策略 + routing 包）核对同步（如无需要改动的表述则记录核查结论）。
- [x] `docs-for-ai/02-core-guides/error-handling.md` 核查：`NopAiCoreErrors` 新增错误码不违反"英文码以模块为粒度"既有分界（W2 已确立的规则）；如需补充一行则补。
- [x] roadmap W5 状态：`todo` → `planned`（独立 draft review 通过时）→ `done`（独立 closure audit 通过后）；Q10 successor plan 在 roadmap/backlog 登记（显式 successor ownership）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] Q 表（Q8/Q10）回填完成；§3.3/§3.4 落地状态与 live baseline 一致。
- [x] roadmap W5 状态推进到位；Q10 successor 归属可追溯（plan Deferred 段 + roadmap/backlog 双登记）。
- [x] `check-doc-links.mjs --strict` 退出码 0。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。
>
> 关闭流程详见 `00-plan-authoring-and-execution-guide.md` 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] 模型类路由组（数据结构 + 解析 + model 归属）+ 选择策略接口 + 默认策略 + 候选集游走 + 并发记账 + 全池饱和 fail-loud 全部落地（nop-ai-core），无空洞组件（每个新组件有测试 + 运行时消费链）。
- [x] 端到端全链测试绿（配置 → 解析 → 归属 → 游走 → 选中 → 饱和 fail-loud）；接线验证（router 调策略 + 读并发注册表）通过。
- [x] 复用优先不变式验证通过（无第二套账号池/熔断/错误分类）。
- [x] 既有 nop-ai-agent/nop-ai-core 调用方零回归（`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` 绿）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项（规则策略 Q10 为显式 successor ownership，非降级）。
- [x] 受影响的 owner docs（requirement/architecture/error-handling 核查）已同步到 live baseline，或显式写明 No owner-doc update required + 理由。
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据（audit 验证：组件调用链运行时连通、fail-loud 语义、复用优先、零回归、Q8/Q10 处置可追溯）。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）router→策略→注册表→熔断的调用链在运行时确实连通（端到端测试断言），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile -pl :nop-ai-core -am`
- [x] `./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C`
- [x] checkstyle / 代码规范检查通过（或按 mission 既有 lint 兜底通道判定——参考 W2/W3 记录：无有效 checkstyle 门禁，以 compile/test + grep 零残留为准）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（关闭时执行）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（文档变更后执行）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0（关闭时执行——新 routing 组件在 W6/W7 前无生产调用方属空壳高危面，端到端测试为其解除证据；**兜底裁定（与 W3 先例一致）**：若 scanner 报 high 且经人工核对为"有完整实现 + 端到端测试消费、仅无生产调用方"（W3 新 helper 同款历史实证 0 findings），逐项裁定记录后放行，不以未接线为理由判定空壳）

## Deferred But Adjudicated

### 规则策略（XLang 规则 DSL，Q10）→ successor plan

- Classification: `out-of-scope improvement`（roadmap ROUTE-03 标注"可选实现"；需求 §五 Q9/Q10）
- Why Not Blocking Closure: 默认策略（健康度 + 并发感知 + 声明序）已满足需求 §3.3 的默认语义；规则策略是同一策略接口的另一种实现，属可插拔扩展面，不落地不影响默认机制 closure；其 DSL 形态（Q10）与 IoC bean 绑定方式有独立裁决面，需独立设计与测试。本 plan 的策略接口即扩展点（无需额外预留）。
- Successor Required: `yes`
- Successor Path: 独立 successor plan（建议 roadmap 登记为 W5b 或独立 backlog 条目，Phase 5 落登记）

### 候选权重/成本维度

- Classification: `out-of-scope improvement`（需求 §五 Q9 已决：默认策略不含权重/成本，委托规则策略）
- Why Not Blocking Closure: 需求已明确委托规则策略；Q9 在需求文档已决。
- Successor Required: `no`（随 Q10 successor plan 一并覆盖）

## Non-Blocking Follow-ups

- 无（并发计数的调用点挂钩 = W6/W7 编排职责，roadmap 显式归属，非本 plan 残余）。

## Closure

Status Note: W5 全 5 Phase 落地——模型类路由组（`model-class.xdef` 配置面（Q8 裁定）+ `LlmConfigHelper` 解析 + `io.nop.ai.core.routing` 包：`ISelectionStrategy`/`DefaultSelectionStrategy`/`ModelClassRouter`/`ConcurrencyRegistry`/健康视图）+ 全池饱和 fail-loud（`ERR_AI_MODEL_CLASS_SATURATED`，并发饱和 + 健康度饱和共用） + 35 新用例（helper 10 + 策略 7 + 注册表 6 + router 12）全绿 + 端到端全链 + 接线验证 + 复用优先（消费既有 `LlmConfigHelper`/`ThresholdBreaker`/`resolveFailoverChain`，无第二套账号池/熔断/错误分类）+ 零回归（core 356/agent 3387/gateway 96 BUILD SUCCESS）+ 文档回填（requirement §3.3/§3.4/§4.1 归属解读/Q 表 Q8-Q10、error-handling plan 裁定英文码、roadmap W5 done + W5b successor 登记）。Q8/Q10 处置可追溯（Q8 = 新建配置面；Q10 = successor plan）。独立 closure audit closure-approve（无 Blocker/Major，2 Minor 已收尾：测试计数 28→35、roadmap done 时序经 audit 确认合法）。deferred 两项分类诚实（out-of-scope improvement，均非 in-scope live defect）。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent（fresh session，read-only）
- Audit Session: `ses_ffcabd969ffed4fyTmRQtVAVBR`
- Evidence:
  - 每条 Exit Criterion 验证结果（5 Phase 全 PASS，审计报告逐条落档）：
    - Phase 1 PASS：7 项裁定内联落档（plan :74-87），Phase Status completed + 全部 items/exit criteria [x]
    - Phase 2 PASS：model-class.xdef（xdef:name/bean-package/keyed modelClasses/candidate 字段）、xgen 第 5 行 renderModel、生成物 3 保留类 + 3 _gen（codegen 输出）、llm.register-model.xml 双 fileType 注册（含 llm-failover 附带修复）、`LlmConfigHelper` 三新入口（resolveModelClass 零回归 null 语义 / resolveModelClassCandidates 展开 + fail-fast / resolveProviderChainCandidates）、`ERR_AI_MODEL_CLASS_SATURATED` 英文码；TestLlmConfigHelperModelClass 10/10 green
    - Phase 3 PASS：routing 包 7 类就位（候选值类型含 concurrencyLimit / CandidateHealth / IModelClassHealth / CandidateHealthProvider / ConcurrencyRegistry（release 下溢 fail-fast）/ ISelectionStrategy 契约 / DefaultSelectionStrategy）；TestDefaultSelectionStrategy 7/7（含无熔断失败副作用 + null 健康视图 fail-fast）
    - Phase 4 PASS：ModelClassRouter 语义（主动路径类内 + 饱和 fail-loud 错误码与 ARG_MODEL_CLASS 断言 / 被动路径 provider 链扩展（primary = 请求目标 provider）/ toChatOptions 四字段下沉 / router 内零 recordFailure（grep 实证）/ 无路由组误用 fail-fast）；TestModelClassRouter 12/12（含接线 `routerInvokesStrategyAndReadsRegistryAtRuntime` + 端到端全链 `endToEndFullChainFromConfigToSaturatedFailLoud`）+ TestConcurrencyRegistry 6/6（含 8 线程并发）
    - Phase 5 PASS：requirement §3.3 落地状态块 / §3.4 双落地状态 / §4.1 W5 归属解读记录 / §五 Q8-Q10 回填；error-handling plan 裁定英文码注；roadmap W5 done + W5b（Q10 successor）todo；check-doc-links exit 0
  - 每条 Closure Gate 验证结果（PASS，evidence 同上）：
    - `./mvnw compile -pl :nop-ai-core -am` PASS；`./mvnw test -pl :nop-ai-core,:nop-ai-gateway,:nop-ai-agent -am -T 1C` PASS（审计复跑 BUILD SUCCESS；core 356 + agent 3387 + gateway 96，35/35 新用例 green）
    - checkstyle 无有效门禁（仓库既有 9164 条 nop-api-core 遗留基线；`echo 'lint not configured'` 兜底，与 W2-W4 记录一致）
    - `check-plan-checklist.mjs --strict` 退出码 0（本轮最终复跑：全勾选 + Closure Evidence 落档）
    - `scan-hollow-implementations.mjs --module nop-ai-core --severity high` 退出码 0（0 findings，审计复跑；routing 新组件空壳风险由端到端 + 接线测试解除）
    - `check-doc-links.mjs --strict` 退出码 0（审计复跑 0 errors）
  - Anti-Hollow 检查结果：调用链运行时连通（router → 策略 → CandidateHealthProvider → ThresholdBreaker/ConcurrencyRegistry，端到端 + 接线测试断言；`rg` 无空方法体/无 catch-and-ignore）；scan-hollow 0 findings
  - Deferred 项分类检查：规则策略（Q10）与权重/成本维度均 out-of-scope improvement（非 in-scope live defect 降级），Successor Required yes/no 如实登记
  - 审计 2 Minor 收尾：roadmap 测试计数 28→35 已修正；roadmap W5 done 时序偏差经审计确认合法（audit 通过即状态成立）
  - 文本一致性（pre-flip 已收尾）：Plan Status completed、5 Phase Status completed、全部 items/exit criteria/closure gates [x]、daily log 各 Phase + closure 条目在

Follow-up:

- no remaining plan-owned work（并发计数调用点挂钩 + 失败记账编排 = W6/W7 独立计划职责；Q10 规则策略 = W5b successor 条目登记）
