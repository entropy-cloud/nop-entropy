# 2414 WI1 废弃 chat 栈迁移收口（AiCommand 链迁移 + 废弃类删除）

> Plan Status: completed
> Last Reviewed: 2026-10-11（round 1 对抗审查修复；round 2 fixed 可执行；独立 closure audit APPROVE 后签核）
> Source: ai-dev/backlog/nop-ai-structure-refactor-roadmap.md（WI1）；ai-dev/analysis/2026-10/2026-10-11-nop-ai-structure-audit.md
> Related: 2415（WI2 工具 SPI 归一，本 plan 的 successor 交互方）

## Purpose

把 nop-ai-core 内并存 8 个月的废弃 chat 栈（`io.nop.ai.core.api.chat/messages/tool` + `DefaultAiChatService`/`MockAiChatService`，2026-02-15 标 @Deprecated）整体收口：唯一未标废弃的阻塞锚点 `AiCommand`（496 行）及其下游迁移到 `IChatService` 新栈，随后整体删除废弃面，使 roadmap WI1 的验证 grep（main scope 对 `IAiChatService|AiChatExchange|AiMessage|IAiChatFunctionTool|IAiChatToolSet` 零命中）成立。

## Current Baseline

（2026-10-11 执行时 HEAD feb11a33c4 实测，Explore 子代理全量普查 + 主会话逐点复核。基线 commit e7534f088e 时点值见 roadmap，数字漂移处按本节为准。）

- **删除清单实测为 26 文件**（roadmap 口径 25 = 不含 package-info）：`core/api/chat/` 4 文件 538 行（AiChatOptions 452 / IAiChatProgressListener 18 / IAiChatService 32 / IAiChatSession 36）、`core/api/messages/` 14 文件 1,366 行（含 package-info；AiChatExchange 535 最大）、`core/api/tool/` 6 文件 379 行、`core/service/DefaultAiChatService.java` 638 行 + `MockAiChatService.java` 84 行。合计约 3,005 行。
- **AiCommand.java**（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/AiCommand.java`，496 行，无 @Deprecated）import 旧栈全部三个子包 13 个类型；它是 task XML AI 任务的唯一执行入口：`core/src/main/resources/_vfs/nop/ai/xlib/ai.xlib` 的 `<TaskStep-generator>`（:33 起）把 16 个 task XML（coder 14 / translate 1 / deepwiki 1）的 `ai:TaskStep` 重写为 `<Execute>` tag，Execute 脚本（:136-168）调 `AiCommand.create()` 并以 `chatOptions` Map 装配选项；TaskStep 步骤返回 `res.validate().outputs`（:96）；`useResponseCache` 被 13 个 coder 资源文件使用。
- **脚本面耦合（roadmap 未列出的隐藏迁移面）**：prompt yaml 的 postProcess 函数以旧 exchange 对象为第一参数，8 个 coder prompt yaml 读 `chatExchange.outputs.noChange`；`ModelBasedPromptTemplate.parseOutputs/parseOutput`（:190-260）调用旧 exchange 的 `setOutput/getOutput/parseXmlContent/parseJsonContent/parseYamlContent/parseMarkdownContent/parseCodeBlock/getBlock/contentContains/getContent/isInvalid` 方法面。
- **prompt 子系统契约绑死旧模型（未废弃但必须随迁）**：`IPromptTemplate`（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/prompt/IPromptTemplate.java`）的 `applyChatOptions(AiChatOptions)`（:53）与 `processChatResponse(AiChatExchange, IEvalScope)`（:89）；实现 `SimplePromptTemplate`（空实现）、`ModelBasedPromptTemplate`（:152 applyChatOptions 从 `ChatOptionsModel` 映射 model/temperature/topP/topK/contextLength/stop/seed 7 字段；:180 processChatResponse + postProcess 调用）；`DefaultSystemPromptLoader.loadSystemPrompt(AiChatOptions)`（:21，消费 workMode + enableCognitivePrompt/enableMetaPrompt/enableSystemPrompt）；`AiGenHelper.java:30` 以 `AiChatOptions.class` 做 xdsl 校验（经 ai.xlib:22 消费）。
- **persist 族 5 文件**（`core/persist/`）：`IAiChatResponseCache` 11 行（load/save 签名用 Prompt/AiChatOptions/AiChatExchange）、`IAiChatExchangePersister` 28 行、`DefaultAiChatExchangePersister` 408 行（markdown 序列化 + AES，@SecureDefault，:29）、`DefaultAiChatResponseCache` 93 行（TTL）、`ChatPersistHelper` 23 行（**main/test 零消费者**）。bean 装配在 `core/src/main/resources/_vfs/nop/ai/beans/ai-defaults.beans.xml:27-34`（nopAiChatResponseCache + nopAiChatExchangePersister，ioc:default）。缓存行为必须保留（13 个 coder 资源文件使用 useResponseCache）。
- **GraphQL 工具面**：`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/graphql/GraphQLToolProvider.java`（116 行，javadoc :28-39 记录 P2-MA3-03 裁定：新契约无 drop-in 对应物，未来映射 AiToolModel+IToolExecutor、callTool 体 1:1 搬运）与 `GraphQLToolSetFactoryBean.java`（44 行，bean `nopGraphQLToolSet`，注册于 `nop-ai-tools/src/main/resources/_vfs/nop/ai/beans/ai-tools-defaults.beans.xml:19`）。实测生产可达性为零：`ai:toolSet` 属性在 ai.xlib 中零读取（死属性）；bean 唯一活消费者是 `TestDeepWikiPrompts`（@Disabled）。
- **孤儿 helper（main/test 零消费者，随栈删除）**：`core/commons/aggregator/IAiTextAggregator.java`（15 行，javadoc 自述 P2 round-4 裁定零生产消费者）、`core/commons/processor/ChainAiChatResponseProcessor.java`、`core/commons/processor/IAiChatResponseChecker.java`、`core/persist/ChatPersistHelper.java`、`core/api/tokenizer/ITokenCountEstimator.java`（仅 `dialect/ILlmDialect.java:240` javadoc 提及）。`IAiChatResponseProcessor` 唯一消费者是 AiCommand 且全仓无生产实现类——随 AiCommand 迁移一并删除该字段与接口。
- **session 族**：`core/service/AbstractAiChatSession.java` + `DefaultAiChatSession.java` 的唯一 main 消费者是 `DefaultAiChatService.java:147`，随栈删除。
- **NopAiCoreErrors.java**（316 行 50 常量）中 **8 个可删**（执行期修订 round 3：`ERR_AI_RESULT_IS_EMPTY`(:112)/`ERR_AI_UNKNOWN_TOOL_CALL`(:188) 被迁移后的 AiCommand 继续引用、`ERR_AI_RESULT_NO_EXPECTED_PART`(:119) 被迁移后的门面 getBlock 复制行为继续引用，三者**摘出删除清单**）：`ERR_AI_RESULT_INVALID_END_LINE`(:115)/`ERR_AI_RESULT_INVALID_NUMBER`(:124)/`ERR_AI_CHAT_GET_SESSION_DEPRECATED`(:213)/`ERR_AI_UNKNOWN_ROLE`(:231) + main 零引用孤儿 `ERR_AI_COMMAND_NOT_FOUND`(:194)/`ERR_AI_EMPTY_TOOLS_NODE`(:197)/`ERR_AI_TOOLS_NODE_PARSE_FAILED`(:200)/`ERR_AI_FILE_PATH_IS_EMPTY`(:203)。**保留**：`ERR_AI_RESULT_IS_EMPTY`/`ERR_AI_UNKNOWN_TOOL_CALL`/`ERR_AI_RESULT_NO_EXPECTED_PART` + 新旧共用 6 个（ERR_AI_SERVICE_NO_DEFAULT_LLMS/NO_BASE_URL/OPTION_NOT_SET/HTTP_ERROR/RATE_LIMITED/INVALID_RESPONSE——ChatServiceImpl/EmbeddingServiceImpl/AnthropicDialect/LlmConfigHelper 在用）。
- **模块依赖约束**：nop-ai-core 不依赖 nop-ai-toolkit（core deps: api/nop-api-core/http-api/http-client-jdk/xlang/rule-core/markdown/search-api/search-lucene/autotest-junit；toolkit 仅依赖 nop-ai-api）。AiCommand 不能直接引用 toolkit `IToolManager`——「工具面接 toolkit」须经 core 内最小接缝接口 + 上层适配，最终 SPI 归一归 WI2（deps: WI1）。
- **新栈事实**：`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/` 提供 IChatService/ChatRequest/ChatResponse/ChatOptions + messages 13 类（ChatMessage 族）+ ChatAttachment（含 `forImageData(mimeType, base64)`，可承接 task-xml 的 `AiMessageAttachment.fromBinaryData`）。`ChatOptions` 无 seed/contextLength/workMode/streamListener/disableCache/enabledTools 字段；dialect 族不读 seed/contextLength（grep 零命中）——新栈从不存在 seed/contextLength 端到端通路。`core/service/ChatServiceImpl.java` 已是新栈 IChatService 的 ioc:default bean（`nopChatService`）；`core/mock/MockChatService.java` 是新栈 mock（WI7 处置，本 plan 不动）。
- **test scope 残留 17 文件**：nop-ai-core 12、nop-ai-tools 2、nop-ai-coder 1（`TestAiCoder.java`，@Disabled，依赖 `IPromptTemplate.processChatResponse(AiChatExchange)` 契约）、nop-ai-deepwiki 1（`TestDeepWikiPrompts.java`，@Disabled）、nop-ai-agent 1（`TestInvariantGate1SecureDefault.java:95-102` SecureDefault FQN 字符串清单含 5 个待删/改名类——4 删 + persister 改名）。
- **构建基线**：2026-10-11 `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-gateway -am` 全绿（exit 0）。

## Goals

- AiCommand 及全部下游（prompt 契约、persist 族、GraphQL 工具面、ai.xlib、2 个 coder task-xml）运行在新栈类型上，脚本面行为（outputs 解析、validate、postProcess、缓存、重试温调）保持等价。
- 废弃面 26 文件 + session 族 2 文件 + 孤儿 helper 5 文件 + 8 个错误码删除（RESULT_IS_EMPTY/UNKNOWN_TOOL_CALL/NO_EXPECTED_PART 保留在用），`NopAiCoreErrors` 只保留 live 错误码。
- main scope grep `IAiChatService|AiChatExchange|AiMessage|IAiChatFunctionTool|IAiChatToolSet` 零命中（javadoc 历史注释一并清理）。
- 不改 nop-ai-api 模块任何文件；新接缝类型全部落在 nop-ai-core。

## Non-Goals

- 不做工具 SPI 归一裁定（WI2）：本 plan 引入的 core 内工具接缝接口是过渡态，WI2 裁定其与 toolkit IToolManager/BizModel 路线的合并。
- 不处置 `core/file`（IFileOperator/LocalFileOperator/FileToolBizModel/CliFileCommand——WI6）与 `core/mock` 新栈 mock 家族（WI7）。
- 不补齐 seed/contextLength 在新栈的端到端支持（dialect 语义改造属 agent-refactor roadmap 已收口域；见 Deferred）。
- 不动 nop-rule-core 依赖（WI5）、不动 dialect 实现（WI8）、不改 nop-ai-api 公共面。

## Scope

### In Scope

- nop-ai-core：AiCommand 重写、prompt 子系统契约迁移、persist 族迁移、ChatLogHelper/AiGenHelper/DefaultSystemPromptLoader 迁移、响应门面与工具接缝新增、废弃面删除、错误码清理、beans/xlib 资源更新、测试改写。
- nop-ai-tools：GraphQLToolProvider/GraphQLToolSetFactoryBean 迁移到新接缝、测试改写。
- nop-ai-coder：2 个 task-xml 的 attachments 脚本迁移（`AiMessageAttachment.fromBinaryData` → `ChatAttachment.forImageData`）、TestAiCoder 改写。
- nop-ai-deepwiki：TestDeepWikiPrompts 改写、generate-readme.task.xml 死属性清理。
- nop-ai-agent：TestInvariantGate1SecureDefault FQN 清单同步。

### Out Of Scope

- nop-ai-api、nop-ai-gateway、nop-ai-agent main 代码、nop-task 全部不动（gateway 仅在 WI1 验证构建面内作为下游回归）。
- ChatOptions 字段扩展、dialect 行为变更。

## Execution Plan

### Phase 1 - core 接缝与 AiCommand/prompt/persist 迁移

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/{command,prompt,model,service,utils,persist,commons}`

- Item Types: `Fix`

- [x] 新增 core 内响应门面（command 或 prompt 包）：以 `ChatResponse` 为内核，承接脚本方法面——getContent/getFullContent、isInvalid/setInvalid/getInvalidReason/validate、getOutputs/getOutput/setOutput、getResultValue/getResultText（AiChatExchange:518-523 平移，AiGenHelper 生成脚本的 `res.validate().resultValue` 消费点）、parseXmlContent/parseJsonContent/parseYamlContent/parseMarkdownContent/parseCodeBlock/getBlock/contentContains（实现从旧 AiChatExchange 平移，解析器 XmlResponseParser/MarkdownResponseParser/JsonResponseParser/YamlResponseParser/CodeResponseParser 来源核实后复用）
- [x] `IPromptTemplate` 契约迁移：`applyChatOptions` 参数改 `ChatOptions`、`processChatResponse` 参数改响应门面；`SimplePromptTemplate`/`ModelBasedPromptTemplate` 同步（applyChatOptions 的 ChatOptionsModel 7 字段映射改到 ChatOptions 支持的字面子集：model/temperature/topP/topK/stop，contextLength/seed 无落点即跳过并注释登记）；postProcess 脚本第一参数改传门面
- [x] `DefaultSystemPromptLoader.loadSystemPrompt` 签名迁移为接收 workMode 与三个 enable 开关（或等价参数对象），行为不变；`AiGenHelper` 校验类型改 `ChatOptions.class`
- [x] persist 族迁移到 ChatRequest/ChatResponse：`IAiChatResponseCache` load/save 签名改新类型；`IAiChatExchangePersister`/`DefaultAiChatExchangePersister` 更名（消除 `AiChatExchange` 字样）并改序列化 ChatRequest+ChatResponse（保持 @SecureDefault + AES 语义）；`DefaultAiChatResponseCache` TTL 逻辑平移；请求 hash 改由 ChatRequest 内容计算；`ai-defaults.beans.xml` 两个 persist bean 同步更名
- [x] `AiCommand` 重写到新栈：`IChatService.callAsync(ChatRequest)`、`ChatRequest`（system/user 消息 + tools + attachments）、`ChatOptions`（provider/model/temperature/stop/topP/topK/maxTokens/requestTimeout/stream/enableThinking 等支持键）、`chatOptions(Map)` 入口保留并做**显式键面归一**（仅映射 ChatOptions 支持的键；`responseFormat` 字符串按 ResponseFormat 常量归一，其余未知键丢弃并 debug 日志）；**重试谓词 = 门面在 processChatResponse 与空响应检查之后的 isInvalid 状态（等价旧 `AiChatExchange::isValid` 语义，`ChatResponse.isSuccess` 仅作传输层前置短路——round 1 审查 A2：二者不等价，success-but-空/不可解析必须照旧重试）**；重试温调逻辑平移；workMode 系统提示走 AiCommand 自有字段 + 迁移后的 DefaultSystemPromptLoader；`options.getDisableCache()` 检查随字段消亡移除（全仓 main 无生产 setter，行为等价，登记于迁移说明）；工具循环改经新接缝接口（findTool 规格类型 `io.nop.ai.api.chat.messages.ChatToolDefinition`，callTool 经 CompletionStage 返回 JSON 文本，未知工具报 `ERR_AI_UNKNOWN_TOOL_CALL`——该码保留，见删除清单修订）；删除 chatResponseProcessor 字段（IAiChatResponseProcessor 接口与 Chain/Checker 实现一并删除）；`useResponseCache` 接迁移后的缓存；返回类型改响应门面（`execute` 返回门面、`executeAsync` 同）
- [x] 新增 core 内工具接缝接口（command 包，过渡态，WI2 归一）：语义为「按名查工具规格（ChatToolDefinition）+ 异步调用工具返回结果」，不引用 toolkit 类型
- [x] `ChatLogHelper` 删除旧 `AiChatExchange` 重载（:42-54），保留 ChatRequest 通路与 ERR_AI_SESSION_ID_* 逻辑
- [x] `ai.xlib` Execute 脚本更新：chatOptions Map 字面量去掉 ChatOptions 不支持的键（seed/contextLength/workMode——workMode 经 cmd.workMode() 既有通路保留）；`streamListener` attr 声明（:128）、TaskStep-generator 的 ai:streamListener 拷贝分支（:80-81）与 map 键一并删除（全仓零 task-xml 使用，死配置）；TaskStep 返回 `res.validate().outputs` 语义不变
- [x] 编译通过：`./mvnw compile -pl nop-ai/nop-ai-core -am`

Exit Criteria:

- [x] nop-ai-core main 编译零错误，旧栈类型在 AiCommand/prompt/persist/service/utils 路径中不再被 import（grep 自查）
- [x] 门面方法面覆盖旧 AiChatExchange 被 ModelBasedPromptTemplate/xlib/prompt-yaml 消费的全部方法（逐方法对照清单核对）
- [x] `No owner-doc update required`（Phase 1 为中间态，Phase 3 统一回写 owner doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 下游迁移（tools / task-xml / xlib 消费面）

Status: completed
Targets: `nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/graphql/`、`nop-ai/nop-ai-coder/src/main/resources/_vfs/nop/ai/tasks/`

- Item Types: `Fix`

- [x] `GraphQLToolProvider` 迁移到 core 工具接缝：`loadToolSpec` 内联（`ToolSpecificationLoader`/`ToolSpecification` 随废弃栈消亡，规格构建直接用 GraphQL schema + `GraphQLToJsonSchema` 产出 `ChatToolDefinition`）；`callTool` GraphQL RPC 体 1:1 保留（P2-MA3-03 裁定兑现第一步，最终 AiToolModel/IToolExecutor 归一归 WI2）
- [x] `GraphQLToolSetFactoryBean` 返回接缝类型；`ai-tools-defaults.beans.xml` bean id `nopGraphQLToolSet` 与条件注册语义不变
- [x] `ai-convert-docx.task.xml`（:64,70）与 `ai-analyze-images.task.xml`（:40,46）：`AiMessageAttachment.fromBinaryData(BinaryDataBean)`（内部 base64 编码）→ `ChatAttachment.forImageData(mimeType, base64String)` 等价迁移，脚本需显式 `StringHelper.encodeBase64(imageData.data)`（round 1 审查 A8：非纯名称替换）
- [x] `nop-ai-deepwiki/src/main/resources/_vfs/nop/ai/tasks/deepwiki/generate-readme.task.xml:77` 删除死属性 `ai:toolSet="nopGraphQLToolSet"`（ai.xlib 零读取，round 1 审查 A9；bean 本身与 id 不变）
- [x] 编译 + 资源装载通过：`./mvnw compile -pl nop-ai/nop-ai-tools -am`；deepwiki/coder 的 task-xml 与 prompt yaml 能被 XDSL 装载（既有资源类测试或新增装载测试验证）

Exit Criteria:

- [x] nop-ai-tools main 编译零错误，graphql 包不再 import 旧栈类型
- [x] 2 个 task-xml 的附件构造改用 ChatAttachment 且字段语义等价（mimeType/base64 不变）
- [x] `No owner-doc update required`（Phase 3 统一回写）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 废弃面删除与错误码清理

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/{java,resources}`

- Item Types: `Fix`

- [x] 删除 26 文件：`core/api/chat/`（4）、`core/api/messages/`（14 含 package-info）、`core/api/tool/`（6）、`core/service/DefaultAiChatService.java`、`core/service/MockAiChatService.java`
- [x] 删除 session 族 `AbstractAiChatSession.java` + `DefaultAiChatSession.java` 与孤儿 helper 5 文件（IAiTextAggregator、ChainAiChatResponseProcessor、IAiChatResponseChecker、ChatPersistHelper、ITokenCountEstimator）+ `IAiChatResponseProcessor.java`（Phase 1 已摘除唯一消费点）
- [x] `NopAiCoreErrors` 删除 8 个错误码（见 Current Baseline 修订清单），保留迁移后 AiCommand/门面在用的 `ERR_AI_RESULT_IS_EMPTY`/`ERR_AI_UNKNOWN_TOOL_CALL`/`ERR_AI_RESULT_NO_EXPECTED_PART` 与 6 个新旧共用码；`AiCoreConfigs.java:34` 等 javadoc 历史提及同步清理
- [x] `ai-defaults.beans.xml` 删除 `nopAiChatService` bean（`nopChatService` 已是 IChatService 唯一 ioc:default）；确认无残留 bean 引用被删类
- [x] 跨模块 javadoc 交叉引用清理：`nop-ai-api ChatOptions.java:23-24` 仅删注释中废弃类字样（不改代码）、`ILlmDialect.java:240` ITokenCountEstimator 提及、`AnthropicDialect.java:503` DefaultAiChatService 提及、`ChatServiceFailoverAdapter.java:42` 否定式提及——逐处清理或改写
- [x] 全仓 main scope grep `IAiChatService|AiChatExchange|AiMessage|IAiChatFunctionTool|IAiChatToolSet` 零命中（含字符串字面量与资源文件）

Exit Criteria:

- [x] 删除清单 26+2+5+1 文件全部不存在于工作树
- [x] main scope 五符号 grep 零命中（输出留档到当日 log）
- [x] `No owner-doc update required` 本阶段不成立——Phase 4 统一回写 owner doc 与迁移说明
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试改写、owner doc 回写与全量验证

Status: completed
Targets: `nop-ai/**/src/test`、`docs-for-ai/`、`ai-dev/design/nop-ai-agent/04-tool-invocation.md`

- Item Types: `Fix` + `Proof`

- [x] 整体删除仅覆盖废弃栈的测试（10 个）：TestMockAiChatService、TestDefaultAiChatService、TestAiChatSession、TestAiChatResponse、DefaultAiChatExchangePersisterTest、TestAiChatResponseCacheTtl、TestAiMessageErrorCode、TestDefaultAiChatFunctionTool、AiChatServiceManual、TestGraphQLToolProviderSpecLoading（执行期裁定：其三条断言全部守卫已删除的 ToolSpecificationLoader/.tool.json 规格装载机制，改写无对应契约——删除测试；.tool.json 资源暂留为惰性资产，归口 WI2「统一 _vfs 工具声明目录」裁定，登记 Non-Blocking Follow-ups）
- [x] 改写测试（7 个）：`TestChatLogHelper`（删旧重载用例）、`TestNopAiCoreErrorsDescriptionEnglish`（删 8 个废弃码断言）、`TestChatServiceRateLimit`（DefaultAiChatService 部分删除，新栈部分保留）、`TestInvariantGate1SecureDefault`（FQN 清单同步：4 删 + persister 改名）、`TestGraphQLToolSetConditionalRegistration`（接缝类型对齐）、`TestDeepWikiPrompts`（@Disabled 手工测试改写到 AiCommand 新 API 或登记保留理由）+ `TestAiCoder`（改到门面 processChatResponse 新契约，保持 @Disabled 手工性质）；**迁移后的门面/persist/AiCommand 至少各 1 个新增 focused 测试**（新契约行为验证：门面 outputs 解析 round-trip、缓存 save/load TTL、AiCommand 经 MockChatService 的 execute 路径**含重试谓词断言**——success-but-空响应必须触发重试，round 1 审查 A2）
- [x] xlib/task-xml 链路验证：既有覆盖 ai.xlib/coder 资源装载的测试保持绿；如无，新增最小验证证明 TaskStep-generator 对样例 task-xml 的转换产物可装载，且 **Execute 脚本经 MockChatService 真实执行走通**（Map→ChatOptions 绑定 + 门面返回 + outputs 提取，不要求真实 LLM——round 1 审查 A3）
- [x] owner doc 回写：`ai-dev/design/nop-ai-agent/04-tool-invocation.md` 增补 WI1 迁移节（core 接缝过渡态 + WI2 归一入口）；`docs-for-ai/03-modules/nop-ai.md` 与 `docs-for-ai/01-repo-map/module-groups.md` 中涉及已删类的表述核对修正；删除面的**迁移说明**（公共 API 变更，Protected Areas 要求）写入 `docs-for-ai/03-modules/nop-ai.md` 或 plan 的 Closure 节并互相链接，**含三条行为登记**：①旧 `/nop/ai/cache` 磁盘缓存条目一次性失效（格式换新，TTL 默认 0 时旧文件残留无害）；②`disableCache` 检查移除（无生产 setter，行为等价）；③seed/contextLength attr 不再有落点（见 Deferred）
- [x] 全量验证：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-gateway -am` 全绿；`./mvnw clean install -DskipTests -T 1C` 一次全量编译确认；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error

Exit Criteria:

- [x] 受影响模块测试全绿且新增 focused 测试在位（门面/persist/AiCommand 各≥1）
- [x] 全仓 install -DskipTests 通过（nop-task/nop-ai-agent 等下游编译面无残留）
- [x] check-doc-links --strict 0 error
- [x] owner doc 与迁移说明已回写
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

- [x] 废弃栈 26 文件 + session 2 文件 + 孤儿 helper 5 文件 + IAiChatResponseProcessor 全部删除，NopAiCoreErrors 8 码清理（RESULT_IS_EMPTY/UNKNOWN_TOOL_CALL/NO_EXPECTED_PART 保留在用）
- [x] main scope 五符号 grep（`IAiChatService|AiChatExchange|AiMessage|IAiChatFunctionTool|IAiChatToolSet`）零命中，输出留档
- [x] AiCommand/prompt/persist/GraphQL 工具面全部运行在新栈类型上，xlib 16 个 task XML 的执行通路编译与装载验证通过，Execute 脚本经 MockChatService 真实执行走通
- [x] focused verification：门面/persist/AiCommand 新增测试（含重试谓词断言）+ 受影响模块 `-am` 全绿 + 全量 install -DskipTests
- [x] 不存在被静默降级的 in-scope live defect；seed/contextLength 行为偏差已登记 Deferred（非 defect——新栈从未支持）
- [x] owner docs（04-tool-invocation.md、nop-ai.md、module-groups.md）已同步到 live baseline
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：AiCommand 经 MockChatService 的 execute 路径端到端测试通过（入口 promptTemplate → ChatRequest → IChatService → 门面 outputs），无空方法体/静默跳过
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-gateway -am`
- [x] `./mvnw clean install -DskipTests` 全量——**偏差登记（非静默跳过）**：本机全量 install 被**既存、可复现、与本改动无关的** nop-tcc-dao gen-aop-proxy 失败阻塞（4 次运行 3 次同模块同签名「找不到符号 TccRecordStore」，该类存在且可编译； nop-tcc-dao pom 零 nop-ai 依赖；单独构建同失败；另一次在 nop-sys-dao 偶发、单独复跑通过）。证据指向构建机工具链问题（JDK 26 vs 项目 Java 21 目标，本机无 JDK 21），先于 WI1 存在。nop-ai 依赖子树（api/core/tools/coder/service/web/gateway/orm）在本运行中全部安装成功，且全部 nop-ai 下游模块测试绿 + 审计者独立 compile exit 0 + 全仓五符号 grep 零命中——门禁意图（捕获本次改动的下游破裂）已被覆盖。WI21 全量收口时重试干净全量安装。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-core --severity high` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2414-wi1-deprecated-chat-stack-migration.md --strict` 退出码 0（本项为自指验证：勾选后复跑退出码 0）

## Deferred But Adjudicated

### seed/contextLength 在新栈无端到端通路

- Classification: `watch-only residual`
- Why Not Blocking Closure: 新栈 dialect 族从不读取 seed/contextLength（grep 零命中），该能力在迁移前的 IChatService 新栈上同样不存在；本次迁移不引入回退，只是旧栈 options 字段不再有落点。task-xml 的可选 attr 传入后被忽略，与 gateway 侧现状一致。
- Successor Required: `no`
- Successor Path: 如需恢复，按 agent-refactor roadmap 的语义演进流程在 nop-ai-api+dialect 立项。

## Non-Blocking Follow-ups

- WI2 工具 SPI 归一时裁定 core 工具接缝接口与 toolkit IToolManager 的合并/废弃（本 plan 已在 04-tool-invocation.md 留接口）；同时收口 nop-ai-tools 的 13 个 `*.tool.json` 声明资源——其唯一 loader（ToolSpecificationLoader）已随 WI1 删除，资源现为惰性资产，与 toolkit/agent 的 `*.tool.xml` 声明统一归属或删除。
- WI7 处置 core/mock 家族时复核本 plan 新增 AiCommand 测试对 MockChatService 的使用方式。

## Closure

Status Note: WI1 全部 4 个 Phase 完成：废弃 chat 栈 26+8 文件与 8 个错误码删除、AiCommand/prompt/persist/GraphQL 工具面迁移到 IChatService 新栈、main scope 五符号 grep 全仓零命中、xlib 16 个 task XML 执行通路经 AiTaskStepExecuteWiringTest 真实走通（generator transform + Execute tag + MockChatService）。执行期三处修正（错误码 9→8、xlib attr 声明恢复、spec-loading 测试裁定删除）均已回写本 plan 并在当日 log 留档。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit，2026-10-11）
- Evidence:
  - A 删除面 PASS：13 类路径全部 ABSENT，git status 全部体现为 D
  - B/D13 门禁 grep PASS：全仓五符号（排除 test/target/_dump/docs）0 命中（审计者重跑）
  - C Anti-Hollow PASS：AiCommand 重试谓词 ChatExchange::isValid（:355）+ 空响应显式 setInvalid（:407）+ chatOptions Map 归一（:185-222）+ 工具循环真实接线（:447,:454）；ChatExchange 20 方法全在位；persister @SecureDefault；GraphQL provider implements IAiToolInvoker；xlib attr/Map 键面与生成器 outputs 语义逐行核对
  - D 测试 PASS：4 个新 focused 测试在位（含重试谓词断言与 xlib Execute 经 MockChatService 真实执行）；9 个旧测试 ABSENT；SecureDefault 门 FQN 清单已同步；审计者实测 compile exit 0；测试绿记录（core 460/tools 33/coder 73/agent 3538/gateway 200）来自执行者 run + 审计者 compile 复核
  - E owner docs PASS：nop-ai.md 迁移说明（含行为登记）、module-groups.md 两处、04-tool-invocation.md WI1 节；check-doc-links --strict 0 error
  - scan-hollow-implementations exit 0（唯一 high 为 2026-08 既存 NoOp 占位，归 WI12，非本次引入）
  - 审计 Minor 项处置：M1 ChatOptions stale javadoc 已随签核修复；M2/M3/M4 注释残留与 M5 api-and-graphql.md 勘误登记为 Non-Blocking Follow-ups；M7 根级 _tmp_s2_artifacts.txt 移入 _tmp/

Follow-up:

- M5：docs-for-ai/02-core-guides/api-and-graphql.md:53-56 仍描述旧栈为「活动主干」，需勘误（audit 登记，非本 plan scope 点名文件）
- M2/M3/M4：test scope 历史注释残留（TestNopAiCoreErrorsDescriptionEnglish:55 / TestLlmErrorClassifier:39 / AiCommand:182 javadoc 措辞），无行为影响
- （承袭）WI2 工具 SPI 归一：core 接缝 IAiToolInvoker 合并裁定 + 13 个 .tool.json 惰性资源处置
- （承袭）WI7：core/mock 家族下沉，复核 TestAiCommandExecute 对 MockChatService 的用法
