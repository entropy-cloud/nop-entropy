---
audit-rounds: 0
---

# nop-ai 结构重构 roadmap——废弃栈收口、core 归位与装配面收敛

> Last updated: 2026-10-11（v1。条目来自 2026-10-11 对 nop-ai 21 个子模块的模块划分 + 分层 + 内部结构实证审计；关键论断经本会话逐条二次抽查验证。证据全文见 `../analysis/2026-10/2026-10-11-nop-ai-structure-audit.md`）
> 位置：按仓库 roadmap 惯例存放于 `ai-dev/backlog/`。书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的 owner / 参考文档路径用反引号，持续受 check-doc-links 保护。该约定约束文件/目录路径；包名、类名、注解仍按代码格式使用反引号。
> mission-driver 兼容：本块采用 `## Work Item Status` 与 `- WI<n> 名称: \`status\`` 形态，状态集为 `todo` / `ready` / `planned` / `done`。状态行尾部括注必须单层非嵌套——只允许一组全角括号且其内不得再出现括号或半角右括号。改任何状态行后必须重跑解析核对条目数。里程碑行写作 `★ **里程碑：<名> …**（解锁条件 …）：\`status\``。
> 审计基线 commit = `e7534f088e`。规模：21 个 Maven 子模块 / 约 1,046 个 main java 文件 / 约 161k main 行。agent 555 文件 76,789 行，core 180 文件 25,549 行，dao 69 文件 18,459 行（手写约 500 行），api 95 文件 11,433 行（以上为基线 commit 钉住值）。
> 与既有 roadmap 的边界：本 roadmap **只做模块边界、废弃栈收口与结构层**。执行模型与基础设施语义归 `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口，25 项全 done——其 WI20-WI22 已覆盖 dialect 缓存/重试语义，本 roadmap WI8 只做代码结构消重，不触碰其已裁定语义）；自主执行能力归 `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（在途 19 项）；gateway failover 能力语义归 `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（active，本 roadmap WI5/WI19 只裁定模块承载边界并按协同规则复核）；nop-ai-shell 的 bash parser 前端演进归 `ai-dev/backlog/nop-ai-shell-bash-cst-frontend-roadmap.md`（active，本 roadmap WI16 的 shell 处置必须先与它划界裁定，不得单方面退役）；安全/可靠性维度归 `ai-dev/audits/nop-ai-agent-audit-tracker.md` 记录的四轮既有审计（已收口）。各 roadmap 不重复立项。

> Sources:
> - `../analysis/2026-10/2026-10-11-nop-ai-structure-audit.md`（实证审计全文，全部 WI 的锚点来源）
> - `docs-for-ai/01-repo-map/module-groups.md`（nop-ai 分层速查——本 roadmap 的规范基线，M2/M4 各 WI 落地后回写对象）
> - `docs-for-ai/03-modules/nop-ai-gateway.md`（gateway 能力承载现状——WI5/WI19 设计输入）
> - `ai-dev/design/nop-ai-agent/04-tool-invocation.md`（工具调用 owner doc——WI2 SPI 归一的回写对象）
> - `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口执行模型重构——装配面 WI11 的划界与后动复核对象）
> - 2026-10-11 结构实证审计（三路并行子代理深审 + 主会话逐条验证）

## Purpose

把 nop-ai 从「分层方向正确、但迁移半途 + core 堆场 + 装配面蔓延 + 微模块碎片」推进到「双轨合一、关注点各归其位、装配面单一、模块边界可验证」。核心命题是四个结构性收敛：

1. **废弃栈收口**：`IAiChatService` 废弃栈与新 `IChatService` 栈并存 8 个月，卡点是未标废弃的 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java`（496 行）；双消息模型（`api/chat/messages/` vs `core/api/messages/`）迫使 gateway 维护专职桥接层；工具 SPI 三套并存、文件工具四份实现。先收口迁移，后续结构动作才有干净地基。
2. **core 归位**：nop-ai-core 混装 RAG SPI（vectorstore/embedding/search）、编码代理关注点（file/response）、网关级选路（routing，连带 nop-rule-core 传递依赖）、main scope 测试设施（mock 两套）四类异质关注点；dialect 家族 helper 三份复制。
3. **装配面收敛**：agent 引擎 48 参构造 + 51 字段配置在 4 处重复暴露 + 8 个 telescoping 构造函数 + `NopAiAgentException` 居 engine 包造成 engine⇄security/engine⇄reliability 两个包级环。
4. **模块整合**：api 模块 70% 是应用实体契约、三个零消费者孤儿模块、三个 MCP 模块共 5 个文件、skills 三级嵌套混装真模块与 prompt 资源包。

非目标（明确排除）：执行模型/工具语义/会话存储等能力语义改造（前序 roadmap 已覆盖或在途）；推倒式重组（审计已否决——双栈交汇面仅 gateway/service 两点、无编译期循环，推倒成本远超收益）；nop-ai-shell 的单方面退役（须先裁定，见 WI16）；性能优化与安全加固。

## Work Item Status

> 唯一动态状态块。`done` = 独立 closure audit 通过（见 Cross-Cutting）。WI 编号全文件递增；文档顺序即执行顺序，AI 不重排。无 deps 关系的 WI 允许并行，但同模块 WI 建议串行以避免冲突。

- WI1 废弃 chat 栈迁移收口（AiCommand 链迁移 + 25 废弃类删除）: `todo`
- WI2 工具 SPI 归一裁定与落地: `todo`
- WI3 gateway 双消息模型桥接层简化: `todo`
- WI4 RAG SPI 归位 nop-ai-rag: `todo`
- WI5 模型选路 routing 归位 gateway: `todo`
- WI6 编码代理关注点归位（core file/response → coder）: `todo`
- WI7 mock 设施收敛下沉: `todo`
- WI8 dialect 家族结构消重: `todo`
- WI9 错误码归位（NopAiCoreErrors 代管项归还各模块）: `todo`
- WI10 NopAiAgentException 上移断包环: `todo`
- WI11 agent 引擎装配面收敛: `todo`
- WI12 扩展点微包收敛与 NoOp 审计: `todo`
- WI13 guardrail/test harness 移出 main: `todo`
- WI14 nop-ai-api 瘦身（应用实体契约迁出）: `todo`
- WI15 MCP 三模块整合与 servrer 更名: `todo`
- WI16 孤儿模块处置裁定（shell / maven / dsl-orm）: `todo`
- WI17 nop-ai-skills 拉平: `todo`
- WI18 nop-ai-dao 双限界上下文裁定: `todo`
- WI19 nop-ai-gateway 能力承载边界裁定: `todo`
- WI20 仓库卫生（Eclipse 工程文件出库 + 工作笔记归位）: `todo`
- WI21 全量收口与分层速查回写校对: `todo`

★ **里程碑：M1 双轨合一**（WI1 + WI2 + WI3 全部 done——废弃栈 25 类删除、工具 SPI 单一、gateway 桥接层收敛）：`todo`
★ **里程碑：M2 core 瘦身归位**（WI4—WI9 全部 done——core 仅剩 LLM 内核关注点，rag/gateway/coder/testkit 各归其位，dialect 净省约 1,000 行）：`todo`
★ **里程碑：M3 装配面收敛**（WI10—WI13 全部 done——包级环消除、装配路径单一、净删约 800 行转发代码）：`todo`
★ **里程碑：M4 模块整合收口**（WI14—WI21 全部 done——api 回归纯能力契约、孤儿与碎片模块按裁定处置、门禁固化）：`todo`

> 注：各 Phase 标题中的「完成门」指该 Phase 全部 WI done 时应达成的里程碑；执行顺序按 Phase 字母序与 WI 编号序。M1 是 M2 各 WI 的事实前置（同模块冲突避让），不设硬 deps。

## Phase A——废弃栈收口与双轨合一（完成门：M1）

### WI1 废弃 chat 栈迁移收口（AiCommand 链迁移 + 25 废弃类删除）

> 全 roadmap 杠杆最高的一步：迁移面经 grep 验证很小，是低垂果实。Item Type: Fix。

- 迁移 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java`（496 行，未标废弃的锚点类）及其下游到 IChatService 栈：prompt 构造改 `ChatRequest`、工具面接 toolkit `IToolManager`、持久化改 api 消息模型；下游含 `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/graphql/GraphQLToolProvider.java` 与 `GraphQLToolSetFactoryBean.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/commons/aggregator/IAiTextAggregator.java`、`core/persist/`（5 文件，绑定废弃 AiChatExchange）。
- 迁移完成后整体删除废弃面（core main 25 个 @Deprecated 文件，2026-02-15 标记至今 8 个月）：`core/api/chat/`（IAiChatService/IAiChatSession/IAiChatProgressListener/AiChatOptions）、`core/api/messages/`（AiMessage 族 13 文件）、`core/api/tool/`（IAiChatFunctionTool 族 6 文件）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultAiChatService.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/MockAiChatService.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/file/IFileOperator.java`；`NopAiCoreErrors` 中仅废弃栈使用的错误码同步清理。
- plan 期必须核查：api 包外消费面以执行时 HEAD 复核为准（审计基线时外部残留仅 deepwiki/coder 各 1 个 test 文件）；删除属公共 API 变更，按 Protected Areas 走 plan-first + owner doc + 迁移说明。
- 验证：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-gateway -am` 全绿；全仓 grep `IAiChatService|AiChatExchange|AiMessage` main scope 零命中（javadoc 历史注释除外，逐处清理或改写）。

### WI2 工具 SPI 归一裁定与落地

- 裁定工具注册路线二选一：IToolExecutor 路线（toolkit，20 个 `*Executor` + `ToolManagerImpl`）为唯一运行时，BizModel 路线（`nop-ai-tools` 的 `FileToolBizModel`、`nop-ai-mcp-server` 的 `AiFileTool`）降级为其适配器；或双路线正式共存并写入 owner doc。裁定记录落 `ai-dev/design/nop-ai-agent/04-tool-invocation.md` 新增裁定节。
- 按裁定收敛文件工具四份实现：toolkit fs + executors（保留）、tools.FileToolBizModel、mcp-server AiFileTool、core IFileOperator（WI1 已删）；`nop-ai-mcp-server` 为用 2 个 Simplifier 而 compile 依赖整个 coder 的倒挂随 WI15 一并解除。
- 统一 `_vfs` 工具声明目录：agent 9 个 `*.tool.xml` 与 toolkit 15 个 `*.tool.xml` 分属两处 `nop/ai/tools/` 同名目录，裁定归属并合并或显式分域命名。
- Item Type: Decision + Fix。deps: WI1（同包冲突避让）。验证：按裁定扩展模块 `-am` 测试全绿 + owner doc 同步。

### WI3 gateway 双消息模型桥接层简化

- WI1 删除 `core/api/messages/` 后，`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/AiDialectBackendMessageConverter.java` 与 `nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/failover/ChatServiceFailoverAdapter.java` 中面向 AiMessage 族的转换面收敛为直连 `api/chat/messages/` 类型；failover 能力语义不变（与 `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md` 划界：只动类型面不动切换/熔断语义）。
- 验证：`./mvnw test -pl nop-ai/nop-ai-gateway -am` 全绿；gateway main scope grep `io.nop.ai.core.api.messages` 零命中。Item Type: Fix。deps: WI1。

## Phase B——core 瘦身与关注点归位（完成门：M2）

### WI4 RAG SPI 归位 nop-ai-rag

- 迁移 `core/api/vectorstore/`（IVectorStore 等 4 文件）、`core/api/embedding/`（IEmbeddingModel 等 4 文件，含 CosineSimilarity/RelevanceScore 纯数学工具）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/search/AiModelTextEmbedding.java` 到 `nop-ai-rag`；rag 对 core 的反向依赖随之解除（rag 只保留对 dialect/chat 契约的依赖）。
- SPI 属公共 API 面，按 Protected Areas 走 plan-first + owner doc + 迁移说明；外部消费者执行时普查（基线时 `nop-search-api` 桥接注释 K1/N4.2 在位）。
- 验证：`./mvnw test -pl nop-ai/nop-ai-rag -am` 全绿（含既有 PgVector E2E）。Item Type: Fix。deps: WI1。

### WI5 模型选路 routing 归位 gateway

- 迁移 `core/routing/`（ModelClassRouter/CandidateHealth/ConcurrencyRegistry/RuleBasedSelectionStrategy 等 9 文件）到 gateway 侧（与既有 `failover/` 11 文件合流为完整的 LLM 网关能力或独立子包，plan 期裁定包结构）；`nop-rule-core` 依赖随迁，斩断 rag/coder/dsl-orm/agent 对规则引擎的传递依赖。
- 协同规则：动工前按 `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md` 当前状态复核其在途项与本次迁移的文件交集，后动者复核前者落地形态；`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` 同步。
- 验证：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-gateway -am` 全绿；core 依赖树无 nop-rule-core（`./mvnw dependency:tree -pl nop-ai/nop-ai-core` 复核）。Item Type: Fix。deps: WI1。

### WI6 编码代理关注点归位（core file/response → coder）

- 迁移 `core/file/`（FileDiff/FileDiffApplier/FileContentParser/LocalFileOperator 等，IFileOperator 已随 WI1 删除）与 `core/response/`（Code/Json/Markdown/Xml/Yaml 5 个结构化输出解析器）到 `nop-ai-coder`（与其既有 orm/convert/meta 转换器域同构）；执行时普查 toolkit/agent 侧消费者并同步 import。
- 命名 rebalance：coder 定位从「AI 编程助手」更正为「AI 输出 → Nop 元模型转换库」——同步 `ai-dev/backlog` 无关，回写 `docs-for-ai/01-repo-map/module-groups.md` 一行定性（归 WI21 统一校对）。
- 验证：`./mvnw test -pl nop-ai/nop-ai-coder -am` 全绿。Item Type: Fix。deps: WI1。

### WI7 mock 设施收敛下沉

- 双 mock 合一：`core/mock/MockChatService`（新栈）保留为唯一无 key 入口；`core/service/MockAiChatService`（废弃栈）已随 WI1 删除。
- `FileSystemResponseProvider`（文件轮询 + 线程池 + 小时级超时的 record/replay 机制）与 `InMemoryResponseProvider`/`IRequestStore`/`IResponseProvider`/`MockChatConfigs` 下沉 test scope 或独立 nop-ai-testkit 模块（plan 期按消费面裁定：基线消费面全部是 test scope——agent 6、core 1、nop-datav-service 4 个测试类）。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent,nop-datav/nop-datav-service -am` 全绿。Item Type: Fix。deps: WI1。

### WI8 dialect 家族结构消重

- 私有 helper 下沉 `AbstractLlmDialect`：`toInt(Object)` 三份（`AnthropicDialect.java:665`、`OpenAiDialect.java:407`、`ResponsesDialect.java:764`）、`getString(Map,String)` 三份（复用基类既有 `getStringByPath`）、`reverseFinishReason` 两份——零语义风险纯下沉。
- buildBody 选项映射数据驱动化：仿 `parseErrorResponse` 已验证的 errorMappings 配置驱动模式，把「遍历 messages → 剥离 system → convertMessage → resolveMaxTokens → addOptionIfNotNull(温度/top_p/topK…)」的同构骨架改为字段名映射表；事件词表真差异（message_start/content_block_delta vs candidates/parts）**不**强行模板化。
- 划界：只做代码结构消重，不触碰 `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI20-WI22 已裁定的缓存/重试/错误分类语义及其锚点行为；目标净省 800-1,200 行。
- 验证：`./mvnw test -pl nop-ai/nop-ai-core -am` 全绿（dialect 现有测试为语义基线）；如有条件补一轮各 provider llm.xml 的请求快照对照测试钉住字节级行为不变。Item Type: Fix。deps: WI1。

### WI9 错误码归位（NopAiCoreErrors 代管项归还各模块）

- `NopAiCoreErrors.java:83,129-158` 代管的上层模块错误码（`nop.err.ai.agent.*`、`nop.err.ai.tools.*`）归还 `NopAiAgentErrors`/toolkit 错误码注册表；core 只保留自身错误码。
- 注意静态初始化顺序与错误码注册时序：plan 期验证ErrorCode.define 注册机制对拆分后的加载顺序无回归（全量回归跑 core+agent+toolkit）。
- 验证：三模块 `-am` 全绿 + 错误码 snapshot 测试（如已有）不回归。Item Type: Fix。deps: WI1。

## Phase C——agent 引擎装配面收敛（完成门：M3）

### WI10 NopAiAgentException 上移断包环

- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java` 上移到 agent 根包（或独立 api 包，plan 期裁定），连带评估 `SessionIds`、`AgentExecutionContext` 中被跨包依赖的部分同批上移；全模块 130 个引用文件机械更新（可用 `ai-dev/backlog/nop-refactor-roadmap.md` 的 rename/codemod 能力执行）。
- 目标：security→engine 的 46 处 import 降至仅剩真实编排依赖（基线 29 处只为异常类、16 处为 AgentExecutionContext）、reliability→engine 的 16 处同理；包级环（engine⇄security 144/46、engine⇄reliability 53/16）消除或降至显式登记的最小集。
- 协同：与 `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（在途）涉及同文件的在途项执行时互查，后动者复核。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿；包依赖扫描（脚本化 grep import 计数）登记基线与目标值。Item Type: Fix。deps: 无。

### WI11 agent 引擎装配面收敛

> 划界：`ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口）只裁定执行模型语义，明确未触碰装配面——本 WI 不改任何语义，只收敛装配路径；其落地锚点（如 `DefaultAgentEngineConfig.java` 的 toolTimeoutMs 分层）为语义基线，重构后行为零变化。

- 删除 `DefaultAgentEngine` 的 8 个 telescoping 构造函数（`DefaultAgentEngine.java:304-345`）与约 40 个纯委托 getter/setter（`:384-480`），只留 Builder → Config → Executor 一条装配路径。
- `ReActAgentExecutor` 48 参构造（`ReActAgentExecutor.java:209-256`，实测 48 个参数）改为接收 Config/分组对象（如 SecurityWiring/ReliabilityWiring/ToolWiring，分组方案 plan 期裁定）；配套 `ReActAgentExecutorBuilder`（约 700 行）随之收敛。
- 目标净删约 800 行纯转发代码；新增引擎能力从「平均改 5-6 个文件」降为「Config 加字段 + 装配处接线」。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（engine 包 109 个测试文件为行为基线）；装配路径单一性以「telescoping 构造 0 个、委托 getter/setter 0 个」grep 验收。Item Type: Fix。deps: WI10。

### WI12 扩展点微包收敛与 NoOp 审计

- 微包合并裁定：talent（2 文件 102 行）、budget（3 文件）、recipe（1 文件）等 300-800 行「I 接口 + Default + NoOp」微包（共 11 个）合并为 extension 包或并入相邻包；plan 期出合并清单逐包裁定，保留有真实生产实现者的包。
- 41 个 NoOp 类（实测）逐个审计：无生产实现者且无演进计划的接口删除或改 default method（`NopAiAgentErrors` 注释显示项目已开始此模式）；有保留价值的登记 owner。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿；NoOp 计数与微包计数登记 before/after。Item Type: Fix + Decision。deps: WI11（同包冲突避让）。

### WI13 guardrail/test harness 移出 main

- `agent/guardrail/test/`（24 文件 2,393 行：CheckpointTestHarness/GuardrailTestSuite/AttackPlugin/CrescendoAttackTransform 等）+ `_vfs/nop/ai/agent/guardrail-test/corpus` 攻击语料移出 main：落点裁定为独立 test-fixtures 模块或改名 guardrail/eval 并文档化其「随产品发布的自评估框架」定位（若裁定保留产品化定位则必须改名消除 main 源码中的 test 命名误导）。
- 与 engine 的 12 处 import 随落点裁定同步收敛。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿；main 源码 grep `package io.nop.ai.agent.guardrail.test` 零命中或落点裁定文档化。Item Type: Decision + Fix。deps: 无（可与 WI10-WI12 并行）。

## Phase D——模块整合与收口（完成门：M4）

### WI14 nop-ai-api 瘦身（应用实体契约迁出）

- 迁出 `api/beans/`（44 文件 7,224 行）+ `api/crud/`（22 个 13 行接口，基线实测全仓 0 外部消费者）：去向裁定——随 `nop-ai-dao`/`nop-ai-service` 侧走、独立 nop-ai-entity 契约 jar、或确认死代码直接删除；plan 期核查 beans 与 dao `_gen` 生成链的关系（生成物不手改，从模型侧迁移）。
- api 回归纯 AI 能力契约 jar：chat/messages/stream + IToolDefinition + mcp/credential/secure/exceptions（约 25-30 文件）；toolkit/spring-mcp-server-support 等轻下游不再背实体 bean。
- Public API 变更：plan-first + owner doc + 迁移说明（Protected Areas）。
- 验证：`./mvnw test -pl nop-ai/nop-ai-api,nop-ai/nop-ai-toolkit -am` 全绿；api 模块文件数登记 before/after。Item Type: Decision + Fix。deps: 无（建议在 WI1 后执行以复用同轮 API 面普查）。

### WI15 MCP 三模块整合与 servrer 更名

- `nop-ai-mcp-server` 的 AiFileTool 按 WI2 裁定并入 toolkit/tools（解除对 coder 的 compile 依赖）；`nop-spring-mcp-server-support`（spring-ai adapter）与 `nop-spring-mcp-server`（SpringBoot 启动器）合并为一个模块。
- 修正包名拼写错误 `io/nop/spring/mcp/servrer/` → `server`（模块独立发布周期，更新 `.settings` 与文档引用）。
- 验证：`./mvnw test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-spring-mcp-server -am` 全绿；子模块数登记 before/after。Item Type: Fix。deps: WI2。

### WI16 孤儿模块处置裁定（shell / maven / dsl-orm）

- **shell**：`nop-ai-agent/pom.xml:30-32` 注释自证 "no production consumer (orphan module, plan 2301 Known Residual)"，但 `ai-dev/backlog/nop-ai-shell-bash-cst-frontend-roadmap.md`（active）仍在投入其 bash parser 前端、WI25（agent-refactor roadmap）刚为其 `&` 语义接了 IBackgroundJobSink 闭环——裁定三选一：保留为沙箱执行面并纳入正依赖图、降级 test scope 测试资产、退役（以 toolkit sandbox 为唯一 bash 面）；**裁定必须与 CST roadmap 负责人/用户确认，不得单方面处置**。
- **maven**（DeltaVirtualFileSystem/MavenWorkspaceReader，6 文件全仓零外部引用）：并入 toolkit fs 域或退役。
- **dsl-orm**（4 文件 + gpt/orm.xdef，零外部引用）：与 coder/orm 重复（GptOrmSqlType vs AiOrmSqlType、GptOrmModelParser vs AiOrmModel/AiOrmModelNormalizer）——并入 coder/orm 消重或随 gpt/orm.xdef 一起退役。
- 验证：裁定记录 + 按裁定执行的模块变更 + 全量受影响模块 `-am` 绿。Item Type: Decision + Fix。deps: WI6（dsl-orm 归 coder 同域协调）。

### WI17 nop-ai-skills 拉平

- 三级嵌套聚合 pom 拉平：`nop-ai-code-analyzer`（33 个 java，货真价实的代码分析库，被 coder compile 依赖）升为 nop-ai 直属模块；`nop-ai-deepwiki`/`nop-ai-translate`（各 0 个 main java，纯 prompt 资源包）降级为 core（或 meta）的 `_vfs/nop/ai/prompts/` 资源目录，不再配 Maven 模块。
- 与 WI12 的 skill 三处异构收敛联动：`agent/skill/`（provider 基础设施）、toolkit `SkillExecutor`（文档工具）、本项资源包——plan 期出一张 skill 概念对照表写入 owner doc，三处命名统一或显式分域。
- 验证：受影响模块 `-am` 绿 + 子模块数变化登记。Item Type: Fix。deps: 无。

### WI18 nop-ai-dao 双限界上下文裁定

- 裁定项目管理域实体（Project/ProjectConfig/ProjectRule、Requirement/RequirementHistory、TestCase/TestResult、Todo、GenFile/GenFileHistory——约 11/22 实体）是否从会话/聊天域独立（独立 ORM 模型文件或独立模块）；其消费面基线只有 `nop-ai-web` CRUD 页面（22 实体 × 5 文件）与 `nop-ai-service` 的 22 个 BizModel，coder 不消费 dao。
- ORM 模型结构变更为 plan-first Protected Area：owner doc + 迁移说明 + 生成链全量回归（`nop-ai-codegen` postcompile 跑 DDL/ORM 生成的链路必须复验）。
- 验证：裁定记录 + （若拆分）dao/codegen/web/service 四模块联动回归绿。Item Type: Decision（拆分实施按裁定另立 successor plan）。deps: 无。

### WI19 nop-ai-gateway 能力承载边界裁定

- 裁定三块能力（failover 11 文件 / channel 12 文件 / login 4 文件）的承载：拆为 LLM 网关（failover，WI5 routing 归位后成形）与 ai-channel（feishu + 扫码登录，依赖 agent/dao/auth）两个模块，或维持单模块但以 pom profile/classifier 隔离依赖面。裁决依据：只用 failover 的下游不应传递依赖 IM/auth/netty 全家桶。
- 与 `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md` 协同：本项只裁定模块边界，failover 能力演进仍归该 roadmap；拆分实施（若立项）另立 successor plan 并回写 `docs-for-ai/03-modules/nop-ai-gateway.md`。
- 验证：裁定记录（+ 若实施：依赖树对照 `./mvnw dependency:tree`）。Item Type: Decision。deps: WI5。

### WI20 仓库卫生（Eclipse 工程文件出库 + 工作笔记归位）

- 全部 21 个模块的 `.project`/`.classpath`/`.settings/` 出库（`git rm --cached`；根 .gitignore 已列这三项，出库后即生效）；执行前核对无 IDE 特有配置丢失风险。
- 根级 `nop-ai/model/chat-history/`（产品分析笔记，非构建资产）迁到 `ai-dev/`（analysis 或 discussions 归档）；`nop-ai/model/nop-ai.orm.xlsx` + design.md 保留（ORM 设计模型属构建资产）。
- 验证：`git status` 干净 + `node ai-dev/tools/check-doc-links.mjs --strict` 0 error + 全量构建一次（`./mvnw clean install -DskipTests -T 1C`）确认出库文件无构建期引用。Item Type: Fix。deps: 无（XS 级，可随时插入）。

### WI21 全量收口与分层速查回写校对

- 回写 `docs-for-ai/01-repo-map/module-groups.md` nop-ai 分层速查表与 `docs-for-ai/04-reference/source-anchors.md` 受影响锚点（WI4/WI5/WI6/WI14-WI17 涉及的路径变更全量核对）；`docs-for-ai/03-modules/nop-ai-gateway.md` 按 WI19 裁定同步。
- 独立子代理交叉校对：本 roadmap 21 项产物间结论一致、与 5 个既有 roadmap（agent-refactor / autonomous / gateway-failover / shell-cst / channel-integration）无重复无遗漏、全部锚点 live 有效；当日 log 登记收口记录。
- 验证：`node ai-dev/tools/check-doc-links.mjs --strict` 0 error；全量 `./mvnw clean install -T 1C` 绿。Item Type: Proof。deps: WI1—WI20 全部。

## Current Baseline

- **审计基线**：commit `e7534f088e`（2026-10-11 工作树）。三路并行子代理深审 + 主会话逐条验证；全部锚点摘录见 `../analysis/2026-10/2026-10-11-nop-ai-structure-audit.md`。关键实测数字：agent 555 文件 76,789 行（459 测试文件）；core 180 文件 25,549 行；api 95 文件 11,433 行（beans 44 + crud 22 = 70% 实体契约）；`ReActAgentExecutor` 构造 48 参（:209-256 实测）；`DefaultAgentEngine` 8 个 telescoping 构造；`DefaultAgentEngineConfig` 51 私有字段；NoOp 41 个；guardrail/test 24 文件；`toInt` 三份复制（`AnthropicDialect.java:665`/`OpenAiDialect.java:407`/`ResponsesDialect.java:764`）；`io/nop/spring/mcp/servrer/` 拼写错误；`api/crud/` 全仓 0 外部消费者；`AiCommand` 无 @Deprecated 标记（:50）。
- **废弃栈时间线**：旧栈 2024-12-27 引入（665bbb8e6f），新 API IChatService 2026-02-14 落地（257e01778f），废弃标记 2026-02-15（d02f757722）；并存至今 8 个月。
- **既有审计收敛状态**：安全/可靠性四轮审计全收口（`ai-dev/audits/nop-ai-agent-audit-tracker.md` 全 ✅）；执行模型 roadmap 25 项全收口（2026-10-03，plan 2306）；harness-evolution 与 channel-integration 两 roadmap done；autonomous roadmap 在途 19 项（2026-09-16 起）；gateway-failover 与 shell-cst 两 roadmap active。本 roadmap 不重复上述已修复/在途面。
- **依赖图事实**（compile scope POM 实证）：api→仅 nop-api-core；core→api（0 反向 import）；全组无 compile 循环、无分层倒置；nop-rule-core 经 core/routing 传递给全部 core 下游；nop-ai-app 不依赖 agent/gateway。
- **正面基线（重构时必须保持）**：异常纪律（588 处 NopAiAgentException、错误码集中）、测试密度（agent 主测行数比 1:1.59）、meta 生成模型规范、provider llm.xml 注册表（9 厂商）配置驱动模式。

## Cross-Cutting

- **证据纪律**：每条执行结论必须带代码锚点（仓库相对路径 + 类/函数名，必要时 `:line`）；执行时按当前 HEAD 复核基线锚点，数字漂移时在对应 plan 的 Current Baseline 登记差值，不改本文件历史数字。
- **Protected Areas**：`nop-ai-api` 公共 API 变更（WI1/WI2/WI14）一律 plan-first + owner doc + 迁移说明；ORM 模型结构（WI18）plan-first + 生成链回归；`_gen/` 生成物不手改——凡涉及生成面的迁移（WI14/WI18）从模型/模板侧入手后重新生成；`nop-kernel/nop-xdefs` 平台级 schema 不在本 roadmap 范围（若 WI2 裁定需要动 tool.xdef，按 agent-refactor roadmap 先例升级为平台级契约演进流程）。
- **Plan 工作流**：每个 WI 执行前按 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 拟 plan（含对抗性审查循环），plan 完成后独立 closure audit，通过后勾选本文件 checkbox。
- **验证面**：各 WI 按其验证条执行对应模块 `-am` 测试；跨 3 个以上模块的 WI（WI1）跑 `./mvnw clean install -DskipTests -T 1C` 一次全量确认；每份文档产出后 `node ai-dev/tools/check-doc-links.mjs --strict` 保持 0 error。
- **协同规则**：与 autonomous roadmap 涉及同文件（ReActAgentExecutor/DefaultAgentEngine 等）时后动者先复核前者落地形态；与 gateway-failover（WI5/WI19）、shell-cst（WI16）的交集按各 WI 协同条执行；发现新的重叠/冲突时登记当日 log 并裁定归属，不重复立项、不重排对方工作项。
- **closure audit**：每个工作项的完成判定 = 独立子代理（fresh session）对照 live code 验证 exit criteria + owner doc 同步 + 测试在位；通过后才置 `done`。
- **报告语言**：中文行文，类名/函数名/术语保留英文原名。

## Rules

- 状态只在本文件 `## Work Item Status` 的状态通道维护；不设第二状态面，里程碑无状态。
- WI 编号全文件唯一递增；执行顺序 = 文档顺序；AI 不重排优先级、不发明工作项；需新增/调整工作项时先提请人工确认。
- 本文件不写实现方案正文；设计裁定落 `ai-dev/design/`，执行细节落 plan，代码放源码。
- 每个 execution item 必须能归类为 `Fix`、`Decision`、`Proof`、`Follow-up`；已确认 live defect 不得降级为 Follow-up。
- 三个纯裁定项（WI16 shell 处置、WI18 dao 拆分、WI19 gateway 拆分）允许裁定结论为「维持现状」，但必须附裁定依据与重启条件登记。
- `done` 必须来自单独的 closure audit，不在完成最后一个编码 slice 的同时顺手宣布关闭。
