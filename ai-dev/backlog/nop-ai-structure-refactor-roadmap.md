---
audit-rounds: 0
---

# nop-ai 结构重构 roadmap——废弃栈收口、core 归位与装配面收敛

> Last updated: 2026-10-11（v2.2，round 3 共识回填。v1 来自 2026-10-11 对 nop-ai 21 个子模块的模块划分 + 分层 + 内部结构实证审计；v2 经 round 1 两名独立子代理对抗审查修订——撤销前提被证伪的 WI3（gateway 对旧消息模型零引用，无双模型桥接层）、修正 dsl-orm"零外部引用"为假（nop-dyn-service compile 消费）、WI20 Eclipse 文件实测 0 入库、security→engine 包环基线修正为 30 条 import（14+16）、toolkit tool.xml 修正为 25、新增 WI22 承接审计 P2-1（nop-ai-app 无产品组装闭环）、WI1/WI5/WI6/WI10/WI14/WI16/WI18 补齐消费者普查锚点与验证面；v2.1 经 round 2 独立子代理复审——修正 api.chat.messages 消费面口径标签（权威实测：排除 api/core 自身，main 77 文件 200 条 import / 含 test 289 文件 829 条）、channel 12→11 文件、单行委托约 78 处、WI1 删除清单集合口径、GptCodeGen 行归属、nop-task-ext 字面量 3 处等 7 项 Minor；v2.2 为 round 3 独立子代理裁定 CONSENSUS 后的共识回填（Baseline 括注 77→约78）。全部修订经主会话逐条复核确认，复审记录见文末 Authoring Review Record）
> 位置：按仓库 roadmap 惯例存放于 `ai-dev/backlog/`。书写约定：未来交付物路径用普通文本书写、不加反引号；已存在的 owner / 参考文档路径用反引号，持续受 check-doc-links 保护。该约定约束文件/目录路径；包名、类名、注解仍按代码格式使用反引号。
> mission-driver 兼容：本块采用 `## Work Item Status` 与 `- WI<n> 名称: \`status\`` 形态，状态集为 `todo` / `ready` / `planned` / `done`。状态行尾部括注必须单层非嵌套——只允许一组全角括号且其内不得再出现括号或半角右括号。改任何状态行后必须重跑解析核对条目数。里程碑行写作 `- ★ **里程碑：<名> …**（解锁条件 …）：\`status\``。
> 审计基线 commit = `e7534f088e`（round 1 复核确认 HEAD 至 d1bee4a565 期间 nop-ai 代码面与基线零差异）。规模：21 个 Maven 子模块 / 1,179 个 main java 文件（含 nop-ai-skills/nop-ai-code-analyzer 33 个）/ 约 161k main 行。agent 555 文件 76,789 行，core 180 文件 25,549 行，dao 69 文件 18,459 行（_gen 23 文件 17,845 行 + 手写 46 文件 614 行），api 95 文件 11,433 行（以上为基线 commit 钉住值）。
> 与既有 roadmap 的边界：本 roadmap **只做模块边界、废弃栈收口与结构层**。执行模型与基础设施语义归 `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口，25 项全 done——其 WI20-WI22 已覆盖 dialect 缓存/重试语义，本 roadmap WI8 只做代码结构消重，不触碰其已裁定语义）；自主执行能力归 `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（在途 19 项，其中 WI5/WI6/WI13 等将触碰 DefaultAgentEngineConfig/Builder/ReActAgentExecutor 构造——与本 roadmap WI10/WI11 同文件，按 Cross-Cutting 协同规则后动者复核，另经 round 1 核实其 WI7 的 SmartModelRouter 位于 agent/router 包，与本 roadmap WI5 无交集）；gateway failover 能力语义归 `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md`（active，本 roadmap WI5/WI19 只裁定模块承载边界并按协同规则复核）；nop-ai-shell 的 bash parser 前端演进归 `ai-dev/backlog/nop-ai-shell-bash-cst-frontend-roadmap.md`（active，本 roadmap WI16 的 shell 处置必须先与它划界裁定，不得单方面退役）；已收口的 `ai-dev/backlog/nop-ai-channel-integration-roadmap.md` 交付的 channel/login 承载是本 roadmap WI19 的裁定对象；安全/可靠性维度归 `ai-dev/audits/nop-ai-agent-audit-tracker.md` 记录的四轮既有审计（已收口）。各 roadmap 不重复立项。

> Sources:
> - `../analysis/2026-10/2026-10-11-nop-ai-structure-audit.md`（实证审计全文，全部 WI 的锚点来源；v2 已按 round 1 审查同步修正）
> - `docs-for-ai/01-repo-map/module-groups.md`（nop-ai 分层速查——本 roadmap 的规范基线，M2/M4 各 WI 落地后回写对象）
> - `docs-for-ai/03-modules/nop-ai-gateway.md`（gateway 能力承载现状——WI5/WI19 设计输入）
> - `ai-dev/design/nop-ai-agent/04-tool-invocation.md`（工具调用 owner doc——WI2 SPI 归一的回写对象）
> - `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口执行模型重构——装配面 WI11 的划界与后动复核对象）
> - 2026-10-11 结构实证审计 + round 1 两名独立子代理对抗审查（全部锚点经 live 核查）

## Purpose

把 nop-ai 从「分层方向正确、但迁移半途 + core 堆场 + 装配面蔓延 + 微模块碎片」推进到「双轨合一、关注点各归其位、装配面单一、模块边界可验证」。核心命题是四个结构性收敛：

1. **废弃栈收口**：`IAiChatService` 废弃栈与新 `IChatService` 栈并存 8 个月，卡点是未标废弃的 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java`（496 行）。双消息模型（`api/chat/messages/` vs `core/api/messages/`）的现存代价是删除阻力与认知负担——gateway 已整体在新栈上（round 1 实测 gateway main 对 `io.nop.ai.core.api.messages` 零 import，v1 所述"gateway 专职桥接层"不成立，见 Authoring Review Record WI3 条）；工具 SPI 三套并存、文件工具四份实现。先收口迁移，后续结构动作才有干净地基。
2. **core 归位**：nop-ai-core 混装 RAG SPI（vectorstore/embedding/search）、编码代理关注点（file/response，消费面在 nop-ai-tools/nop-cli-core/nop-dyn-service）、网关级选路（routing，连带 nop-rule-core 传递依赖）、main scope 测试设施（mock 两套）四类异质关注点；dialect 家族 helper 三份复制。
3. **装配面收敛**：agent 引擎 48 参构造 + 51 字段配置在 4 处重复暴露 + 8 个 telescoping 构造函数 + `NopAiAgentException` 居 engine 包造成 engine⇄security/engine⇄reliability 两个包级环（且 FQN 被 gateway 硬编码、被 nop-task 反射字符串引用——见 WI10）。
4. **模块整合**：api 模块 70% 是应用实体契约、零生产消费者孤儿模块（shell/maven；dsl-orm 仅存一个平台模块消费者 nop-dyn-service，见 WI16）、三个 MCP 模块共 5 个文件、skills 三级嵌套混装真模块与 prompt 资源包、nop-ai-app 无产品组装闭环（审计 P2-1，WI22 承接）。

非目标（明确排除）：执行模型/工具语义/会话存储等能力语义改造（前序 roadmap 已覆盖或在途）；推倒式重组（审计已否决——双栈交汇面仅 gateway/service 两点、无编译期循环，推倒成本远超收益）；nop-ai-shell 的单方面退役（须先裁定，见 WI16）；性能优化与安全加固；P3 级命名问题（`agent/reliability` 与 `core/reliability` 同名不同义、compaction 类型散落 3 包）不单独立项，归 WI12 微包收敛时顺带评估。

## Work Item Status

> 唯一动态状态块。`done` = 独立 closure audit 通过（见 Cross-Cutting）。WI 编号全文件唯一递增；文档顺序即执行顺序，AI 不重排。无 deps 关系的 WI 允许并行，但同模块 WI 建议串行以避免冲突。**WI3 编号已退役**（round 1 证伪撤销，见 Authoring Review Record），编号不复用、后续 WI 编号不受影响。

- WI1 废弃 chat 栈迁移收口（AiCommand 链迁移 + 25 废弃类删除）: `done`
- WI2 工具 SPI 归一裁定与落地: `done`
- WI4 RAG SPI 归位 nop-ai-rag: `todo`
- WI5 模型选路 routing 归位 gateway: `todo`
- WI6 编码代理关注点归位裁定（core file/response）: `todo`
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
- WI20 仓库卫生（本地未跟踪文件清理 + 工作笔记归位）: `todo`
- WI22 nop-ai-app 产品组装闭环裁定: `todo`
- WI21 全量收口与分层速查回写校对: `todo`

- ★ **里程碑：M1 双轨合一**（WI1 + WI2 全部 done——废弃栈 25 类删除、工具 SPI 单一）：`done`
- ★ **里程碑：M2 core 瘦身归位**（WI4—WI9 全部 done——core 仅剩 LLM 内核关注点，rag/gateway/coder/testkit 各归其位，dialect 净省约 1,000 行）：`todo`
- ★ **里程碑：M3 装配面收敛**（WI10—WI13 全部 done——包级环消除、装配路径单一、净删约 800 行转发代码）：`todo`
- ★ **里程碑：M4 模块整合收口**（WI14—WI22 全部 done——api 回归纯能力契约、孤儿与碎片模块按裁定处置、结构门禁固化于 WI21）：`todo`

> 注：各 Phase 标题中的「完成门」指该 Phase 全部 WI done 时应达成的里程碑；执行顺序按 Phase 字母序与 WI 编号序（WI22 在 WI20 之后、WI21 之前执行）。M1 是 M2 各 WI 的事实前置（同模块冲突避让），不设硬 deps。

## Phase A——废弃栈收口与双轨合一（完成门：M1）

### WI1 废弃 chat 栈迁移收口（AiCommand 链迁移 + 25 废弃类删除）

> 全 roadmap 杠杆最高的一步：迁移面经 grep 验证很小，是低垂果实。round 1 裁定迁移与删除同 WI 执行（不拆分——分拆会复刻"迁移完、删除拖 8 个月"的既成失败模式）。Item Type: Fix。

- 迁移 `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java`（496 行，未标废弃的锚点类）及其下游到 IChatService 栈：prompt 构造改 `ChatRequest`、工具面接 toolkit `IToolManager`、持久化改 api 消息模型；下游含 GraphQLToolProvider.java（nop-ai-tools graphql 工具面；live 树中已不存在，执行时以 grep 复核） 与 `GraphQLToolSetFactoryBean.java`、nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/commons/aggregator/IAiTextAggregator.java（WI1 已删除）、`core/persist/`（5 文件，绑定废弃 AiChatExchange）。
- 迁移完成后整体删除废弃面（删除清单 25 文件 = 24 个 @Deprecated + 未标废弃的 MockAiChatService；core main @Deprecated 计 25 文件，含移交 WI6 的 IFileOperator；标记自 2026-02-15 至今 8 个月）：`core/api/chat/`（IAiChatService/IAiChatSession/IAiChatProgressListener/AiChatOptions）、`core/api/messages/`（AiMessage 族 13 文件，目录含 package-info 共 14 文件）、`core/api/tool/`（IAiChatFunctionTool 族 6 文件）、nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/DefaultAiChatService.java、nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/MockAiChatService.java（WI1 已删除）；`NopAiCoreErrors` 中仅废弃栈使用的错误码同步清理。**`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/file/IFileOperator.java` 不在本 WI 删除**（round 1 发现其实现类 LocalFileOperator 与消费面 FileToolBizModel/CliFileCommand 的联动未声明会留下验证面之外的破裂模块）——file 域整体（IFileOperator 废弃面 + LocalFileOperator + FileDiff 族）归 WI6 随归位一并处置。
- 已知 test scope 残留（基线实测各 1 个文件）：nop-ai-deepwiki 与 nop-ai-coder 的测试——随迁移一并改写或登记保留理由。
- plan 期必须核查：api 包外消费面以执行时 HEAD 复核为准（round 1 复核外部残留仅上述 2 个 test 文件）；删除属公共 API 变更，按 Protected Areas 走 plan-first + owner doc + 迁移说明。
- 验证：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-gateway -am` 全绿 + Cross-Cutting 全量 install 一次；全仓 grep `IAiChatService|AiChatExchange|AiMessage|IAiChatFunctionTool|IAiChatToolSet` main scope 零命中（javadoc 历史注释除外，逐处清理或改写）。

### WI2 工具 SPI 归一裁定与落地

- 裁定工具注册路线二选一：IToolExecutor 路线（toolkit，20 个 `*Executor` + `ToolManagerImpl`）为唯一运行时，BizModel 路线（`nop-ai-tools` 的 `FileToolBizModel`、`nop-ai-mcp-server` 的 `AiFileTool`）降级为其适配器；或双路线正式共存并写入 owner doc。适配器（若采降级方案）可为过渡态——删除时点随裁定登记重启条件，不默认永久保留。裁定记录落 `ai-dev/design/nop-ai-agent/04-tool-invocation.md` 新增裁定节。
- 按裁定收敛文件工具四份实现：toolkit fs + executors（保留）、tools.FileToolBizModel、mcp-server AiFileTool、core file 域（IFileOperator 废弃面随 WI6 处置）；`nop-ai-mcp-server` 为用 2 个 Simplifier 而 compile 依赖整个 coder 的倒挂随 WI15 一并解除。
- 统一 `_vfs` 工具声明目录：agent 9 个 `*.tool.xml` 与 toolkit 25 个 `*.tool.xml` 分属两处 `nop/ai/tools/` 同名目录（round 1 修正 toolkit 计数为 25），裁定归属并合并或显式分域命名。
- Item Type: Decision + Fix。deps: WI1（同包冲突避让）。验证：按裁定扩展模块 `-am` 测试全绿 + owner doc 同步。

## Phase B——core 瘦身与关注点归位（完成门：M2）

### WI4 RAG SPI 归位 nop-ai-rag

- 迁移 `core/api/vectorstore/`（IVectorStore 等 4 文件）、`core/api/embedding/`（IEmbeddingModel 等 4 文件，含 CosineSimilarity/RelevanceScore 纯数学工具）、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/search/AiModelTextEmbedding.java` 到 `nop-ai-rag`；rag 对 core 的依赖面随之收敛（round 1 实测 vectorstore/embedding 代码消费者仅 rag 自身 5 main + 5 test，`nop-search-lucene/LuceneSearchEngine.java:152` 仅 javadoc 提及无代码依赖——N4.2 桥接注释在该文件，非 nop-search-api；另 `nop-code/nop-code-service` TestCodeSearchHybridVector.java:84 为 javadoc 提及，plan 期普查勿误报——round 2 补记）。
- 同 plan 期裁定 rag 仍依赖的 core 面归属：`core/commons/splitter`（SimpleTextSplitter/IAiTextSplitter）、`core/api/document.AiDocument`、`core/api/support.VectorData`（round 1 补充实测）——随迁 rag 或保留 core 并登记理由，避免"SPI 走了、配套留下"的半迁移。
- SPI 属公共 API 面，按 Protected Areas 走 plan-first + owner doc + 迁移说明。
- 验证：`./mvnw test -pl nop-ai/nop-ai-rag -am` 全绿（含既有 PgVector E2E）。Item Type: Fix。deps: WI1。

### WI5 模型选路 routing 归位 gateway

- 迁移 `core/routing/`（ModelClassRouter/CandidateHealth/ConcurrencyRegistry/RuleBasedSelectionStrategy 等 9 文件）到 gateway 侧（与既有 `failover/` 11 文件合流为完整的 LLM 网关能力或独立子包，plan 期裁定包结构）；`nop-rule-core` 依赖随迁，斩断 rag/coder/dsl-orm/agent 对规则引擎的传递依赖。
- **必须同 plan 处置 core 内部消费者**（round 1 补充锚点）：`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/LlmConfigHelper.java:12` import `io.nop.ai.core.routing.ModelClassCandidate`——routing 迁移后要么 LlmConfigHelper 的 model-class 配置解析随迁 gateway、要么把 routing 拆为「core 侧模型类配置类型 + gateway 侧选路引擎」两层，否则造成 core→gateway 分层倒置。拆分方案 plan 期裁定并记录。
- 协同规则：动工前按 `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md` 当前状态复核其在途项与本次迁移的文件交集，后动者复核前者落地形态（round 1 核实该 roadmap W1-W8 已完成，本 WI 属"后动者移动前者落地物"）；`ai-dev/design/nop-ai-agent/nop-ai-agent-llm-layer.md` 同步。
- 验证：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-gateway -am` 全绿；core 依赖树无 nop-rule-core 且 core main 无 `io.nop.ai.core.routing` 残留 import（`./mvnw dependency:tree -pl nop-ai/nop-ai-core` + grep 复核）。Item Type: Fix。deps: WI1。

### WI6 编码代理关注点归位裁定（core file/response）

> round 1 重写：v1 的"迁 coder"单方案与消费者普查（"toolkit/agent 侧"）均不实。Item Type: Decision + Fix。

- **消费面基线（round 1 实测）**：`core/file` 的 main 消费者是 `nop-ai-tools`（`FileToolBizModel.java` 用 FileContent/FileContents/IFileOperator/LocalFileOperator、`DslToolImpl.java`）与 `nop-runner/nop-cli-core`（`CliFileCommand.java`）；`core/response` 的 main 消费者是 `nop-dyn/nop-dyn-service`（`GptCodeGen.java` import XmlResponseParser——**nop-ai 之外的平台模块**）与 nop-ai-tools；测试侧消费者另有 dsl-orm/translate/deepwiki。toolkit/agent 零消费（v1 断言作废）。
- 裁定各项归宿（可分拆裁定）：①`core/file` → toolkit fs 域（工具执行层，与 `IToolFileSystem` 同域；迁 coder 会重建 `docs-for-ai/01-repo-map/module-groups.md` M5-P1 裁定刚解除的 tools→coder 分层倒置）；②`core/response`（LLM 输出结构化解析器）→ coder / toolkit / 保留 core 重命名，三选一——**约束条件：nop-dyn-service 是平台模块，任何迫使其新增 nop-ai-coder 依赖的方案都需论证**（如解析器下沉更低层或 nop-dyn-service 侧复制/替代实现）；③`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/file/IFileOperator.java`（@Deprecated forRemoval）与 `LocalFileOperator.java:49`（implements IFileOperator）的废弃面在归位时一并收口，消费者 FileToolBizModel/CliFileCommand 同步迁移。
- plan 期交付完整消费者对照表（文件级）并回写 owner doc。
- 验证：按裁定扩展模块列表 `-am` 全绿（**必须含 nop-runner/nop-cli-core 与 nop-dyn/nop-dyn-service**，round 1 修正——v1 验证面编译不到任何真实消费者）。deps: WI1。

### WI7 mock 设施收敛下沉

- 双 mock 合一：`core/mock/MockChatService`（新栈）保留为唯一无 key 入口；`core/service/MockAiChatService`（废弃栈）已随 WI1 删除。
- `FileSystemResponseProvider`（文件轮询 + 线程池 + 小时级超时的 record/replay 机制）与 `InMemoryResponseProvider`/`IRequestStore`/`IResponseProvider`/`MockChatConfigs` 下沉 test scope 或独立 nop-ai-testkit 模块（plan 期按消费面裁定）。消费面基线（round 1 修正）：直接消费 `io.nop.ai.core.mock` 的仅 core 1 个测试类（TestNopAiCoreErrorsContract）；nop-datav-service 4 个测试类各自定义**私有本地** MockChatService（implements IChatService），不消费 core mock；agent 0。
- 验证：`./mvnw test -pl nop-ai/nop-ai-core -am` 全绿。Item Type: Fix。deps: WI1。

### WI8 dialect 家族结构消重

- 私有 helper 下沉 `AbstractLlmDialect`：`toInt(Object)` 三份（`AnthropicDialect.java:665`、`OpenAiDialect.java:407`、`ResponsesDialect.java:764`，round 1 逐行核验）、`getString(Map,String)` 三份（Gemini:781/Ollama:577/OpenAi:490）、`reverseFinishReason` 两份（Anthropic:863/Gemini:702）——基类已有 `getStringByPath`（:452）未被复用；零语义风险纯下沉。
- buildBody 选项映射数据驱动化：仿 `parseErrorResponse` 已验证的 errorMappings 配置驱动模式，把「遍历 messages → 剥离 system → convertMessage → resolveMaxTokens → addOptionIfNotNull(温度/top_p/topK…)」的同构骨架改为字段名映射表；事件词表真差异（message_start/content_block_delta vs candidates/parts）**不**强行模板化。
- 划界：只做代码结构消重，不触碰 `ai-dev/backlog/nop-ai-agent-refactor-roadmap.md` WI20-WI22 已裁定并落地的缓存/重试/错误分类语义（round 1 确认该 roadmap 已收口、本 WI 恒为后动者，其落地的行为钉测试如 TestAnthropicCacheBreakpoints 为语义基线）；目标净省 800-1,200 行。
- 验证：`./mvnw test -pl nop-ai/nop-ai-core -am` 全绿（dialect 现有测试为语义基线，含 agent-refactor roadmap 落地的缓存行为钉测试）；如有条件补一轮各 provider llm.xml 的请求快照对照测试钉住字节级行为不变。Item Type: Fix。deps: WI1。

### WI9 错误码归位（NopAiCoreErrors 代管项归还各模块）

- `NopAiCoreErrors.java:83,129-158` 代管的上层模块错误码（`nop.err.ai.agent.*`、`nop.err.ai.tools.*`）归还 `NopAiAgentErrors`/toolkit 错误码注册表；core 只保留自身错误码。
- 注意静态初始化顺序与错误码注册时序：plan 期验证 ErrorCode.define 注册机制对拆分后的加载顺序无回归（全量回归跑 core+agent+toolkit）。
- 验证：三模块 `-am` 全绿 + 错误码 snapshot 测试（如已有）不回归。Item Type: Fix。deps: WI1。

## Phase C——agent 引擎装配面收敛（完成门：M3）

### WI10 NopAiAgentException 上移断包环

- `nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/NopAiAgentException.java` 上移到 agent 根包（或独立 api 包，plan 期裁定），连带评估 `AgentExecutionContext`、`SessionIds` 中被跨包依赖的部分同批上移；包内引用机械更新（可用 `ai-dev/backlog/nop-refactor-roadmap.md` 已交付的 rename/codemod 能力执行——round 1 确认其 13/13 done 且第二档 rename 覆盖模块内符号域 + import/FQN 同步）。
- **跨模块契约必须同步**（round 1 补充锚点）：①`nop-ai/nop-ai-gateway/src/main/java/io/nop/ai/gateway/channel/feishu/FeishuConnector.java` 4 处 FQN 硬编码（:346、:382、:383、:539）；②`nop-task/nop-task-dao/src/main/java/io/nop/task/dao/store/TaskExceptionRegistry.java:69` 以**反射字符串** `"io.nop.ai.agent.engine.NopAiAgentException"` 注册（跨模块反射契约，javadoc 自述部署缺类回退——模块内 codemod 不会更新它，漏改即静默运行时降级）。FQN 变更属公共 API 变更，按 Protected Areas 出迁移说明。
- 基线计量（round 1 修正）：main scope security→engine 共 **30 条** import（14 处 `NopAiAgentException` + 16 处 `AgentExecutionContext`，27 个文件）——上移两类即该方向 import 清零；engine→security 144 处为主向编排依赖，保留。目标：engine⇄security、engine⇄reliability（main 53/16）两个包级环消除或降至显式登记的最小集。
- 协同：与 `ai-dev/backlog/nop-ai-agent-autonomous-execution-improvement-roadmap.md`（在途）涉及同文件的在途项执行时互查，后动者复核。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent,nop-ai/nop-ai-gateway,nop-task/nop-task-dao -am` 全绿（round 1 修正——v1 验证面漏 gateway 与 nop-task）；全仓 grep 旧 FQN `agent.engine.NopAiAgentException`（**含字符串字面量**）零残留；包依赖扫描登记 before/after。Item Type: Fix。deps: 无。

### WI11 agent 引擎装配面收敛

> 划界：`ai-dev/backlog/nop-ai-agent-refactor-roadmap.md`（已收口）只裁定执行模型语义，明确未触碰装配面——本 WI 不改任何语义，只收敛装配路径；其落地锚点（如 `DefaultAgentEngineConfig.java` 的 toolTimeoutMs 分层）为语义基线，重构后行为零变化。协同：autonomous roadmap 在途的 WI5/WI6/WI13 将向 Config/Builder/Executor 构造新增配置项，后动者复核（见 Cross-Cutting）。

- 删除 `DefaultAgentEngine` 的 8 个 telescoping 构造函数（起始行 :303/:307/:312/:317/:324/:333/:342/:351，round 1 修正锚点窗）与 49 个纯单行 config 委托 getter/setter（:384-480 窗口内实测 49 处；扩展窗口 :384-:547 约 78 处单行 config 委托，口径：方法体仅一行 config 调用/返回——round 2 修正 v1"约 40"与 v2"77"），只留 Builder → Config → Executor 一条装配路径。
- `ReActAgentExecutor` 48 参构造（`ReActAgentExecutor.java:210-256`，实测 48 个参数）改为接收 Config/分组对象（如 SecurityWiring/ReliabilityWiring/ToolWiring，分组方案 plan 期裁定）；配套 `ReActAgentExecutorBuilder`（700 行）随之收敛。
- 目标净删约 800 行纯转发代码；新增引擎能力从「平均改 5-6 个文件」降为「Config 加字段 + 装配处接线」。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿（engine 包 109 个测试文件为行为基线）；装配路径单一性以「telescoping 构造 0 个、config 委托 getter/setter 0 个」grep 验收。Item Type: Fix。deps: WI10。

### WI12 扩展点微包收敛与 NoOp 审计

- 微包合并裁定：talent（2 文件 102 行）、budget（3 文件）、recipe（1 文件）等 300-800 行「I 接口 + Default + NoOp」微包（共 11 个）合并为 extension 包或并入相邻包；plan 期出合并清单逐包裁定，保留有真实生产实现者的包。
- 41 个 NoOp 类（实测）逐个审计：无生产实现者且无演进计划的接口删除或改 default method（`NopAiAgentErrors` 注释显示项目已开始此模式）；有保留价值的登记 owner。
- 顺带评估（不单独立项）：`agent/reliability` 与 `core/reliability` 同名不同义的改名机会、compaction 类型散落 3 包（toolkit/api 的 ICompactionArchive、agent/compact 实现、agent/session 的 Config/Result）的归拢。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿；NoOp 计数与微包计数登记 before/after。Item Type: Fix + Decision。deps: WI11（同包冲突避让）。

### WI13 guardrail/test harness 移出 main

- `agent/guardrail/test/`（24 文件 2,393 行：CheckpointTestHarness/GuardrailTestSuite/AttackPlugin/CrescendoAttackTransform 等）+ `_vfs/nop/ai/agent/guardrail-test/corpus` 攻击语料移出 main：落点裁定为独立 test-fixtures 模块或改名 guardrail/eval 并文档化其「随产品发布的自评估框架」定位（若裁定保留产品化定位则必须改名消除 main 源码中的 test 命名误导）。
- 与 engine 的 14 条 import（round 1 修正 v1"12 处"）随落点裁定同步收敛。
- 验证：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全绿；main 源码 grep `package io.nop.ai.agent.guardrail.test` 零命中或落点裁定文档化。Item Type: Decision + Fix。deps: 无（可与 WI10-WI12 并行）。

## Phase D——模块整合与收口（完成门：M4）

### WI14 nop-ai-api 瘦身（应用实体契约迁出）

- 迁出 `api/beans/`（44 文件 7,224 行）+ `api/crud/`（22 个 13 行接口，基线实测全仓 0 外部消费者）：去向裁定——随 `nop-ai-dao`/`nop-ai-service` 侧走、独立 nop-ai-entity 契约 jar、或确认死代码直接删除（round 1 确认"死代码直接删除"是现实选项：api/beans+crud 全仓含 service/web 零引用）。**生成链事实（round 1 补充）**：beans 文件带 `//__XGEN_FORCE_OVERRIDE__` 生成标记——凡涉及生成面从模型/模板侧入手后重新生成，不手改（Protected Areas）。
- api 回归纯 AI 能力契约 jar：chat/messages/stream + IToolDefinition + mcp/credential/secure/exceptions（约 25-30 文件）；toolkit/spring-mcp-server-support 等轻下游不再背实体 bean。
- 协同：beans 实体（NopAiSession/Project/Requirement 等）与 dao 实体同域，**去向裁定须与 WI18（dao 双限界上下文）一致**——WI18 先裁或同 plan 联动，避免二次搬迁。
- Public API 变更：plan-first + owner doc + 迁移说明（Protected Areas）。
- 验证：`./mvnw test -pl nop-ai/nop-ai-api,nop-ai/nop-ai-toolkit -am` 全绿；api 模块文件数登记 before/after。Item Type: Decision + Fix。deps: 无硬依赖（建议与 WI18 协同时序）。

### WI15 MCP 三模块整合与 servrer 更名

- `nop-ai-mcp-server` 的 AiFileTool 按 WI2 裁定并入 toolkit/tools（解除对 coder 的 compile 依赖）；`nop-spring-mcp-server-support`（spring-ai adapter）与 `nop-spring-mcp-server`（SpringBoot 启动器）合并为一个模块。
- 修正包名拼写错误 `io/nop/spring/mcp/servrer/` → `server`（模块独立发布周期，更新 `.settings` 与文档引用）。
- 验证：`./mvnw test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-spring-mcp-server -am` 全绿；子模块数登记 before/after。Item Type: Fix。deps: WI2。

### WI16 孤儿模块处置裁定（shell / maven / dsl-orm）

- **shell**：`nop-ai/nop-ai-agent/pom.xml:28-32` 注释自证 "no production consumer (orphan module, plan 2301 Known Residual)"（round 1 修正锚点），test scope 依赖；唯一生产引用为 0，但 `ai-dev/backlog/nop-ai-shell-bash-cst-frontend-roadmap.md`（active）仍在投入其 bash parser 前端、WI25（agent-refactor roadmap）刚为其 `&` 语义接了 IBackgroundJobSink 闭环——裁定三选一：保留为沙箱执行面并纳入正依赖图、降级 test scope 测试资产、退役（以 toolkit sandbox 为唯一 bash 面）；**裁定必须与 CST roadmap 负责人/用户确认，不得单方面处置**。
- **maven**（DeltaVirtualFileSystem/MavenWorkspaceReader，6 文件；round 1 复核全仓 `io.nop.ai.maven` 包外引用确为 0）：并入 toolkit fs 域或退役。
- **dsl-orm**（4 文件 + gpt/orm.xdef）：round 1 证伪 v1"零外部引用"——`nop-dyn/nop-dyn-service/pom.xml:86` compile 依赖它，`GptCodeGen.java` :11 import `XmlResponseParser`、:18 import 并调用 `io.nop.ai.dsl.orm.GptOrmModelParser`（2025-02 引入）。与 coder/orm 确有重复（GptOrmSqlType vs AiOrmSqlType、GptOrmModelParser vs AiOrmModel/AiOrmModelNormalizer）。裁定菜单必须先安置该平台模块消费者：并入 coder 并同步迁移 nop-dyn-service 依赖（含论证平台模块依赖 coder 的合理性）、GptOrmModelParser 迁 coder 后 nop-dyn-service 改依赖、或维持现状登记重启条件——**直接退役会编译打破 nop-dyn 构建，排除**。
- 验证：裁定记录 + 按裁定执行的模块变更 + 全量受影响模块（含 nop-dyn/nop-dyn-service）`-am` 绿。Item Type: Decision + Fix。deps: WI6（dsl-orm/file 域与 coder/toolkit 归属联动协调）。

### WI17 nop-ai-skills 拉平

- 三级嵌套聚合 pom 拉平：`nop-ai-code-analyzer`（33 个 java，货真价实的代码分析库，被 coder compile 依赖）升为 nop-ai 直属模块；`nop-ai-deepwiki`/`nop-ai-translate`（各 0 个 main java，纯 prompt 资源包）降级为 core（或 meta）的 _vfs/nop/ai/prompts/ 资源目录，不再配 Maven 模块。
- 与 WI12 的 skill 三处异构收敛联动：`agent/skill/`（provider 基础设施）、toolkit `SkillExecutor`（文档工具）、本项资源包——plan 期出一张 skill 概念对照表写入 owner doc，三处命名统一或显式分域。
- 验证：受影响模块 `-am` 绿 + 子模块数变化登记。Item Type: Fix。deps: 无。

### WI18 nop-ai-dao 双限界上下文裁定

- 裁定项目管理域实体（Project/ProjectConfig/ProjectRule、Requirement/RequirementHistory、TestCase/TestResult、Todo、GenFile/GenFileHistory——约 11/22 实体）是否从会话/聊天域独立（独立 ORM 模型文件或独立模块）；其消费面基线只有 `nop-ai-web` CRUD 页面（22 实体 × 5 文件）与 `nop-ai-service` 的 22 个 BizModel，coder 不消费 dao。
- ORM 模型结构变更为 plan-first Protected Area：owner doc + 迁移说明 + 生成链全量回归（`nop-ai-codegen` postcompile 跑 DDL/ORM 生成的链路必须复验）；**DDL 交付物**：若拆分，`nop-ai/deploy/sql/{mysql,oracle,postgresql}` 三方言迁移脚本随 plan 交付（round 1 补充，channel-integration W0-1 有三方言先例）。
- 验证：裁定记录 + （若拆分）dao/codegen/web/service 四模块联动回归绿 + 三方言 DDL 在位。Item Type: Decision（拆分实施按裁定另立 successor plan）。deps: 无（WI14 的 beans 去向须与本裁定一致，见 WI14 协同条）。

### WI19 nop-ai-gateway 能力承载边界裁定

- 裁定三块能力（failover 11 文件 / channel 11 文件——10 顶层 + feishu/FeishuConnector，round 2 修正 / login 4 文件）的承载：拆为 LLM 网关（failover，WI5 routing 归位后成形）与 ai-channel（feishu + 扫码登录，依赖 agent/dao/auth）两个模块，或维持单模块但以 pom profile/classifier 隔离依赖面。裁决依据：只用 failover 的下游不应传递依赖 IM/auth/netty 全家桶。
- 与 `ai-dev/backlog/nop-ai-gateway-failover-roadmap.md` 协同：本项只裁定模块边界（其 W1-W8 已落地，本项属后动者移动/承载前者交付物）；failover 能力演进仍归该 roadmap；拆分实施（若立项）另立 successor plan 并回写 `docs-for-ai/03-modules/nop-ai-gateway.md`。
- 验证：裁定记录（+ 若实施：依赖树对照 `./mvnw dependency:tree`）。Item Type: Decision。deps: WI5。

### WI20 仓库卫生（本地未跟踪文件清理 + 工作笔记归位）

> round 1 重写：v1 的"Eclipse 工程文件出库（git rm --cached）"前提不实——`git ls-files` 实测全仓 0 个 `.project`/`.classpath`/`.settings` 被跟踪（根 .gitignore:10-12 与 nop-ai/.gitignore 均已忽略，磁盘文件为未跟踪忽略态），无出库对象。

- 清理本地工作树中未跟踪的 `.settings/`/`.project`/`.classpath` 文件（可选，仅磁盘卫生）；确认根 `.gitignore` 与 `nop-ai/.gitignore` 的忽略规则持续覆盖（若有模块目录新增遗漏则补规则）。
- 根级 `nop-ai/model/chat-history/`（14 个产品分析笔记，非构建资产）迁到 `ai-dev/`（analysis 归档或 discussions）；`nop-ai/model/nop-ai.orm.xlsx` + design.md 保留（ORM 设计模型属构建资产）。
- 验证：`git status` 干净 + `node ai-dev/tools/check-doc-links.mjs --strict` 0 error + `./mvnw clean install -DskipTests -T 1C` 一次确认无构建期引用残留。Item Type: Fix。deps: 无（XS 级，可随时插入）。

### WI22 nop-ai-app 产品组装闭环裁定

> round 1 新增：承接证据审计 P2-1（v1 被无声丢弃）。Item Type: Decision。

- 裁定 nop-ai-app 的产品定位：当前 `nop-ai/nop-ai-app/pom.xml` 只依赖 service/web/auth/quarkus，**不依赖 agent 与 gateway**，application.yaml 无 agent/gateway 配置——555 文件的 agent 引擎没有任何默认宿主，只能经 gateway 或测试运行。裁定二选一：①app 补装 agent/gateway（含 beans 装配与 application.yaml 配置），形成 AI 子系统默认启动器；②显式登记「nop-ai 是库集合、宿主由下游应用承担」的定位并回写 module-groups 与 gateway owner doc，消除"有 app 模块即应有默认宿主"的误导。
- 若裁定①：与 WI19（gateway 承载边界）联动——gateway 拆分与否影响装配面；实施另立 successor plan。
- 验证：裁定记录（+ 若实施：app 启动冒烟测试/宿主装配证明）。deps: 无硬依赖（建议在 WI19 裁定后执行以复用其结论）。

### WI21 全量收口与分层速查回写校对

- 回写 `docs-for-ai/01-repo-map/module-groups.md` nop-ai 分层速查表与 `docs-for-ai/04-reference/source-anchors.md` 受影响锚点（WI4/WI5/WI6/WI14-WI17 涉及的路径变更全量核对）；`docs-for-ai/03-modules/nop-ai-gateway.md` 按 WI19 裁定同步。
- **结构门禁固化**（round 1 补充，M4 括注的交付载体）：把可机械化的边界检查固化为脚本落 ai-dev/tools/（或登记使用方式）——dependency:tree 断言 core 无 nop-rule-core、全仓 grep 断言旧 FQN/废弃类零残留、包环 import 计数不回升；后续回归可重复执行。
- 独立子代理交叉校对：本 roadmap 全部产物间结论一致、与 6 个既有 roadmap（agent-refactor / autonomous / gateway-failover / shell-cst / channel-integration / invariant-loop）无重复无遗漏、全部锚点 live 有效；当日 log 登记收口记录。
- 验证：`node ai-dev/tools/check-doc-links.mjs --strict` 0 error；全量 `./mvnw clean install -T 1C` 绿。Item Type: Proof。deps: WI1、WI2、WI4—WI20、WI22 全部。

## Current Baseline

- **审计基线**：commit `e7534f088e`（2026-10-11 工作树；round 1 复核 HEAD d1bee4a565 时 nop-ai 代码面零差异）。三路并行子代理深审 + 主会话验证 + round 1 两名独立审查员逐条核查。关键实测数字（round 1 修订后）：agent 555 文件 76,789 行（459 测试文件，主测行数比 1:1.59）；core 180 文件 25,549 行；api 95 文件 11,433 行（beans 44 文件 7,224 行带 `//__XGEN_FORCE_OVERRIDE__` 生成标记 + crud 22 接口 = 70% 实体契约，全仓 0 外部消费者）；`ReActAgentExecutor` 构造 48 参（:210-256）；`DefaultAgentEngine` 8 个 telescoping 构造（起始 :303-:351）+ 49 个窗口内 config 委托（:384-480；扩展窗口 :384-:547 约 78 处单行 config 委托，口径同 WI11——round 3 共识回填修正 v2 残留的"77"）；`DefaultAgentEngineConfig` 51 私有字段（含 1 static logger）；NoOp 41 个；guardrail/test 24 文件 2,393 行（与 engine 14 条 import）；`toInt` 三份、`getString` 三份、`reverseFinishReason` 两份；`io/nop/spring/mcp/servrer/` 拼写错误；toolkit 25 个 `*.tool.xml`（agent 9 个）；dao `_gen` 23 文件 17,845 行 + 手写 46 文件 614 行；core main @Deprecated 25 文件（4+13+6+1+1）；全组 main 1,179 个 java 文件。
- **废弃栈时间线**（round 1 git 取证复核）：旧栈 2024-12-27 引入（665bbb8e6f），新 API IChatService 2026-02-14 落地（257e01778f），废弃标记 2026-02-15（d02f757722）；并存至今 8 个月。外部 test 残留仅 deepwiki/coder 各 1 文件。
- **gateway 双消息模型事实（round 1 修正）**：gateway main 对 `io.nop.ai.core.api.messages` 零 import；`AiDialectBackendMessageConverter` 是前端 wire-format ↔ ChatRequest 协议转换器（implements `io.nop.gateway.conversion.IBackendMessageConverter`）；`failover/ChatServiceFailoverAdapter` implements 新栈 `IChatService`（javadoc :42 明示非废弃栈）。全仓唯一双模型转换点是 core 测试类 TestChatLogHelper。双模型的现存代价是 core 自留废弃面与删除阻力，非运行时桥接。
- **跨模块 FQN/反射契约（round 1 新增）**：`FeishuConnector.java` 4 处 FQN 硬编码 `io.nop.ai.agent.engine.NopAiAgentException`（:346/:382/:383/:539）；`nop-task/nop-task-dao/.../TaskExceptionRegistry.java:69` 反射字符串注册同 FQN（+ nop-task-ext `TestTaskExceptionRegistry.java` 3 处 FQN 字面量 :43/:113/:143，round 2 修正口径）；`LlmConfigHelper.java:12` import `io.nop.ai.core.routing.ModelClassCandidate`；`nop-dyn/nop-dyn-service/pom.xml:86` + `GptCodeGen.java`（:11/:18）消费 nop-ai-dsl-orm。
- **包环计量（round 1 修正）**：engine→security 144 / security→engine 30（14 NopAiAgentException + 16 AgentExecutionContext，27 文件）；engine→reliability 53 / reliability→engine 16（main scope）；`NopAiAgentException` 位于 engine 包，main scope engine 包外 130 个文件 import 它。
- **仓库卫生事实（round 1 修正）**：`.project`/`.classpath`/`.settings` 被 git 跟踪数为 **0**（根 `.gitignore`:10-12 与 `nop-ai/.gitignore`:10-12 均已忽略，磁盘存在未跟踪副本）；`nop-ai/model/chat-history/` 为 14 个 .md 产品分析笔记。
- **消费面普查（round 2 修正口径）**：core/file 消费者 = nop-ai-tools（FileToolBizModel/DslToolImpl）+ nop-cli-core（CliFileCommand）；core/response 消费者 = nop-dyn-service（GptCodeGen :11 import XmlResponseParser、:18 import 并调用 GptOrmModelParser）+ nop-ai-tools；vectorstore/embedding 消费者 = 仅 rag（5 main + 5 test，另 `nop-code/nop-code-service` TestCodeSearchHybridVector.java:84 有 javadoc 提及无代码依赖）；core mock 直接消费者 = 仅 core 1 个测试类（datav 4 个测试类用各自私有本地 mock）；api.chat.messages 外部消费面（import 语句口径、排除 nop-ai-api/nop-ai-core 自身）= main scope 77 文件 200 条 import，含 test 289 文件 829 条（round 2 勘误 v2"252 个 main 文件 669 条"口径标签错误）。
- **既有 roadmap 收敛状态（round 1 复核）**：安全/可靠性四轮审计全收口；执行模型 roadmap 25 项全收口（2026-10-03）；gateway-failover W1-W8 已完成；harness-evolution/channel-integration/invariant-loop done；autonomous roadmap 在途 19 项（其 WI7 SmartModelRouter 位于 agent/router，与 WI5 无交集）；shell-cst roadmap active 12 项待办。本 roadmap 不重复上述已修复/在途面。
- **依赖图事实**（compile scope POM 实证）：api→仅 nop-api-core；core→api（0 反向 import）；全组无 compile 循环、无分层倒置；nop-rule-core 经 core/routing 传递给全部 core 下游；nop-ai-app 不依赖 agent/gateway。
- **正面基线（重构时必须保持）**：异常纪律（588 处 NopAiAgentException、错误码集中）、测试密度（agent 主测行数比 1:1.59、engine 包 109 测试文件）、meta 生成模型规范、provider llm.xml 注册表（9 厂商 + default）配置驱动模式。

## Cross-Cutting

- **证据纪律**：每条执行结论必须带代码锚点（仓库相对路径 + 类/函数名，必要时 `:line`）；执行时按当前 HEAD 复核基线锚点，数字漂移时在对应 plan 的 Current Baseline 登记差值，不改本文件历史数字。
- **Protected Areas**：`nop-ai-api` 公共 API 变更（WI1/WI2/WI14/WI10 的 FQN 变更）一律 plan-first + owner doc + 迁移说明；ORM 模型结构（WI18）plan-first + 生成链回归；`_gen/` 生成物不手改——凡涉及生成面的迁移（WI14/WI18）从模型/模板侧入手后重新生成；`nop-kernel/nop-xdefs` 平台级 schema 不在本 roadmap 范围（若 WI2 裁定需要动 tool.xdef，按 agent-refactor roadmap 先例升级为平台级契约演进流程）。
- **Plan 工作流**：每个 WI 执行前按 `ai-dev/plans/00-plan-authoring-and-execution-guide.md` 拟 plan（含对抗性审查循环），plan 完成后独立 closure audit，通过后勾选本文件 checkbox。
- **验证面**：各 WI 按其验证条执行对应模块 `-am` 测试；跨 3 个以上模块的 WI（WI1/WI10）跑 `./mvnw clean install -DskipTests -T 1C` 一次全量确认；每份文档产出后 `node ai-dev/tools/check-doc-links.mjs --strict` 保持 0 error；改状态行后重跑 mission-driver 的 roadmap-check 解析核对。
- **协同规则**：与 autonomous roadmap 涉及同文件（ReActAgentExecutor/DefaultAgentEngine/DefaultAgentEngineConfig 等）时后动者先复核前者落地形态；与 gateway-failover（WI5/WI19）、shell-cst（WI16）的交集按各 WI 协同条执行；发现新的重叠/冲突时登记当日 log 并裁定归属，不重复立项、不重排对方工作项。
- **closure audit**：每个工作项的完成判定 = 独立子代理（fresh session）对照 live code 验证 exit criteria + owner doc 同步 + 测试在位；通过后才置 `done`。
- **报告语言**：中文行文，类名/函数名/术语保留英文原名。

## Rules

- 状态只在本文件 `## Work Item Status` 的状态通道维护；不设第二状态面，里程碑无状态。
- WI 编号全文件唯一递增；执行顺序 = 文档顺序；AI 不重排优先级、不发明工作项；需新增/调整工作项时先提请人工确认。**例外已登记**：WI3 经 round 1 对抗审查证伪撤销（编号退役不复用）；WI22 经 round 1 审查新增（承接审计 P2-1），编号延续全文件最大值之后、WI21 之前执行。
- 本文件不写实现方案正文；设计裁定落 `ai-dev/design/`，执行细节落 plan，代码放源码。
- 每个 execution item 必须能归类为 `Fix`、`Decision`、`Proof`、`Follow-up`；已确认 live defect 不得降级为 Follow-up。
- 纯裁定项（WI16 shell 处置、WI18 dao 拆分、WI19 gateway 拆分、WI22 app 宿主）允许裁定结论为「维持现状」，但必须附裁定依据与重启条件登记。
- `done` 必须来自单独的 closure audit，不在完成最后一个编码 slice 的同时顺手宣布关闭。

## Authoring Review Record

> 本 roadmap 拟制过程执行独立子代理对抗性审查循环，直至无 Blocker/Major 共识。

- **round 1**（2026-10-11，两名独立子代理并行，fresh session）：两名审查员均裁定 REVISE。
  - 审查员 A（事实核查，agent_c51ae336-e62d-4493-8836-5c3b20075689）：28 组断言逐条 live 核查，多数逐字节吻合（含 git 三 commit 时间线、588 异常、48 参构造、24 文件 2,393 行）；报 2 Blocker + 3 Major + 8 Minor。Blocker：①WI3 前提为假——gateway main 对 `io.nop.ai.core.api.messages` 零 import，AiDialectBackendMessageConverter 为 wire-format 协议转换器、ChatServiceFailoverAdapter implements 新栈 IChatService，"双模型桥接层"不存在；②dsl-orm"零外部引用"为假——nop-dyn-service compile 消费（pom :86 + GptCodeGen :11,18）。Major：Eclipse 文件 0 入库、security→engine 应为 30 条 import（14+16/27 文件）、toolkit tool.xml 应为 25。
  - 审查员 B（立项与划界，agent_ff07ed19-18be-402d-90f2-e20f6f95ca34）：0 Blocker + 6 Major + 9 Minor。Major：WI6 消费者普查对象错误且验证面漏掉全部真实消费者（tools/cli-core/dyn-service）；dsl-orm 同上；WI5 缺 LlmConfigHelper 锚点（core→gateway 倒置风险）；WI1 删除清单与 LocalFileOperator/nop-cli-core 消费面自相矛盾；WI10 验证面漏 gateway FQN 与 nop-task 反射字符串契约；审计 P2-1（nop-ai-app 无产品组装闭环）被无声丢弃。另确认：WI8 划界可守、WI1 不应拆分、WI4 未低估迁移面、WI14 生成链处理足够（beans 带 XGEN 标记）、milestone 括注"门禁固化"无交付载体、channel-integration 漏列划界段。
  - **处置**：全部 Blocker/Major 已修复——WI3 撤销（编号退役）、WI22 新增承接 P2-1、WI6 重写为裁定项（消费面基线重做）、WI1 收窄范围（IFileOperator 移交 WI6）、WI5/WI10/WI14/WI16/WI18 补齐锚点与验证面、WI20 重写、全部数字按实测修正、门禁固化归 WI21、channel-integration 补入划界段、P3 命名问题显式排除至 WI12；Minor 全部采纳（含里程碑行加 `- ` 前缀、corpus 路径反引号、WI2 适配器过渡态、WI14-WI18 协同声明、mock 消费计数、K1/N4.2 锚点改 nop-search-lucene、dao _gen 23/17,845、总文件数 1,179）。v1 的两处事实错误同步修正到 `../analysis/2026-10/2026-10-11-nop-ai-structure-audit.md`。
- **round 2**（2026-10-11，独立子代理 agent_8b76e023-d593-4d12-b747-b1990098c55a，fresh session）：裁定 REVISE——round 1 十项修复中九项半经 live code 精确证实（WI3 撤销/WI22 新增/WI1 收窄/WI6 重写/WI16 dsl-orm/WI5 LlmConfigHelper/WI10 跨模块契约/WI20 重写/其余采纳项全部 ✅），0 Blocker / 1 Major / 7 Minor。Major：Baseline"api.chat.messages 外部消费面 252 个 main 文件 669 条 import"口径标签错误（252/669 为推导口径且非 main-only；主会话按 import 语句口径权威实测：排除 api/core 自身，main scope 77 文件 200 条 import、含 test 289 文件 829 条）。Minor：单行委托全文件 77→约 78（:384-:547 扩展窗口）、WI1 删除清单两个"25"集合不同（24 @Deprecated + MockAiChatService；messages 目录含 package-info 14 文件）、GptCodeGen :11 归属 XmlResponseParser 仅 :18 为 GptOrmModelParser、gateway channel 12→11 文件（两名 round 1 审查员口径冲突，主会话实测 11 定谳）、nop-task-ext 测试字面量 2→3 处、WI17 未来路径反引号/WI20 既有 .gitignore 缺反引号、WI4 补记 nop-code-service javadoc 提及防误报。**处置**：Major 与全部 Minor 已修复，争议数字经主会话按声明的 grep 口径重新实测定谳；round 2 同时确认 deps/里程碑/编号一致性、mission-driver 解析性、check-doc-links 0 error、与 6 个既有 roadmap 划界无冲突。
- **round 3**（2026-10-11，独立子代理 agent_96ac8e00-8f0f-4fd6-8e00-ba96ce3f7aa5，fresh session）：裁定 **CONSENSUS（可进入执行）**——round 2 全部 8 项修复经 live 复核成立（含 200/77/829/289 消费面四数字逐字节复现、WI1 删除清单 25=24+1 集合等式逐目录实数闭合、.gitignore :10-12 锚点核验）；0 Blocker / 0 Major / 1 项非阻塞自由发现 Minor（Baseline 括注 stale"77 处"与 WI11 的"约 78 处"同事实不同值），已在共识回填时一并修正。round 3 另复核：deps 闭包完整（WI21 deps 恰覆盖其余 20 项）、里程碑集合与 Phase 组成一致、Review Record 与正文一致、基线钉住声明在 HEAD 7355f54d55 仍成立、check-doc-links 0 error。三轮累计约 40 组锚点复核无实质性错误残留。
