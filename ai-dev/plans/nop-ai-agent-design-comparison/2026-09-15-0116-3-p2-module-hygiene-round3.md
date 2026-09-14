---
status: active
mission: nop-ai-agent-design-comparison
work-item: P2-HYGIENE
group: "2026-09-15-0116"
verify: [test]
---

# P2 模块卫生与契约面收口（round-3 P2 第一批：命名 / 死面裁定 / 未用依赖 / 文档与 XDSL 卫生）

## Current Baseline

- 来源：deep-audit round 3 的 P2 Follow-up Backlog（roadmap `## Follow-up Backlog` 文档顺序前 10 项，未勾选），全部经 live repo 复核（HEAD `a993ea9f43`，2026-09-15）：
  1. **AiFileTool 命名三方不一致**：`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/mcp/McpConstants.java` `BIZ_OBJ_AI_TOOL = "AiTool"`；`nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java:45` `@BizModel(McpConstants.BIZ_OBJ_AI_TOOL)`（wire 上操作名 `AiTool__*`，`GraphQLToolCallbackProvider` 也经 `McpConstants.BIZ_OBJ_AI_TOOL` 查 BizObject）；但 `AiFileTool.java:59/:71/:121` `@Auth(permissions = "AiFileTool:read"/"AiFileTool:write")` 按类名；`docs-for-ai/02-core-guides/service-layer.md:203` 权限命名表 BizObjName 列写 `AiFileTool`——违反 `<BizObjName>:<action>` 约定，权限检查面与 wire 操作名面脱节。
  2. **FileTool 工具描述文件命名与加载约定不符**：`nop-ai/nop-ai-tools/src/main/resources/_vfs/nop/ai/tools/` 下的 grepFiles 描述文件原为单下划线 `FileTool_grepFiles.tool.json`，对应操作名 `FileTool__grepFiles`（双下划线，`IFileToolBiz.java:75`/`FileToolBizModel.java:183`）；`ToolSpecificationLoader.loadSpecification`（`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/api/tool/ToolSpecificationLoader.java:16`，`@Deprecated`）拼 `/nop/ai/tools/{toolName}.tool.json` 永不命中 → `GraphQLToolProvider.loadToolSpec`（`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/graphql/GraphQLToolProvider.java:88-99`）静默回退 schema 派生描述；另有 4 个 `.task.json`（`FileTool__loadDslSchema`/`FileTool__loadDslSchemaForFileType`/`FileTool__saveDslFile`/`FileTool__loadDslFile`）全仓无任何 loader，纯死资源。
  3. **`ChatStreamAccumulator`（nop-ai-api 公共类）全仓无生产消费**：`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/stream/ChatStreamAccumulator.java`（plan 328 Phase 3 产物，按 itemType 累积 text/reasoning/tool_call）在 nop-ai-core/nop-ai-agent/nop-ai-tools main 代码零引用；各 dialect（OpenAiDialect/ResponsesDialect 等）各自实现 chunk 累积逻辑——api 公共面与实际使用面不一致。
  4. **`ChatUserMessage.parts` 多模态无生产消费**：`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatUserMessage.java` 的 `parts`（image/audio 多模态 `ContentPart`）与 `attachments` 在 6 个 dialect 请求构建中只读 `getContent()` 文本视图；image/audio part 序列化时静默丢弃（纯图片消息 content=null）；plan 326 声明的多模态 API 表面未接线。
  5. **nop-ai-mcp-server 未用依赖**：`nop-ai/nop-ai-mcp-server/pom.xml:21` 声明 `nop-graphql-core` compile 依赖但全模块零引用（GraphQL 版 MCP 2023 遗留；BizModel 运行时由消费方 nop-biz 提供）。
  6. **nop-ai-coder 未用依赖**：`nop-ai/nop-ai-coder/pom.xml:22/:62` 声明 `nop-ai-api` 与 `nop-ui` compile 依赖但全模块零引用（coder 主代码只 import nop-ai-core/coder 包；nop-ui 为前端框架模块与 coder 职责无交集）。
  7. **nop-ai-shell 未用依赖**：`nop-ai/nop-ai-shell/pom.xml:35` 声明 `jline-reader`（optional）但全模块零引用（jline 代码移植到模块内重写后依赖未清理）。
  8. **nop-ai-agent 未用依赖**：`nop-ai/nop-ai-agent/pom.xml:44` 声明 `nop-autotest-junit`（test scope）但 agent 测试零使用（测试全为纯 JUnit5 自组装引擎/存储）。
  9. **module-groups.md:86-87 对 toolkit/tools 职责分层描述与 live 不符**：`docs-for-ai/01-repo-map/module-groups.md:86` 称 `nop-ai-toolkit` 为"工具抽象层"，但 toolkit 主代码实际含 30 个具体执行器（ReadFile/Bash/Http/ApplyDelta/Patch…）+ `LocalToolFileSystem` + `ToolManagerImpl`，"工具抽象层"表述失真。
  10. **toolkit/mcp-server beans.xml XDSL 卫生**：toolkit `ai-toolkit-defaults.beans.xml:1-3` 缺 `x:schema` 且 `xmlns:ioc="urn: nop-ioc:1.0"` 带空格非常规 URI；mcp-server `ai-mcp-server-defaults.beans.xml:1` 缺 XML 声明（MA2.7 P4-MA2-033 遗留未收敛，tools 侧已修）。
- 归属模块：nop-ai-mcp-server（1）、nop-ai-tools（2）、nop-ai-api（3/4）、pom（5/6/7/8）、docs（9）、nop-ai-toolkit/nop-ai-mcp-server（10）。
- 验证面：2/3/4 以裁定 + 清理为主；1 涉及权限命名契约；5/6/7/8 为 pom 依赖清理；9/10 为文档/XDSL 卫生。除 4 个 pom 依赖清理与命名修订外多为纯文档/裁定，涉及模块 `./mvnw test -pl <module> -am` 复核。

## Goals

- 命名契约收口：AiFileTool 的 BizObjName/权限/文档三方一致（`<BizObjName>:<action>` 约定成立）；FileTool 工具描述文件命名与加载约定一致（或显式裁定删除/保留死资源）。
- 公共 API 死面显式裁定：ChatStreamAccumulator 与 ChatUserMessage.parts 多模态要么接线、要么登记 reserved/移除承诺——不允许"API 面存在但零消费者且无说明"。
- 4 个模块的未用依赖清理（mcp-server/coder/shell/agent pom），清理后模块编译与测试通过。
- module-groups.md toolkit/tools 分层描述与 live 一致；toolkit/mcp-server beans.xml XDSL 卫生修复。
- 涉及模块 `./mvnw test -pl <module> -am` 通过；`node ai-dev/tools/check-doc-links.mjs --strict` 0 error。

## Non-Goals

- 不处理 Follow-up Backlog 其余 P2/P3（锚点漂移、测试反模式、session 生命周期、dialect/usage 缺陷等，另开计划）。
- 不实施 ChatStreamAccumulator / 多模态 parts 的完整接线实现（若裁定接线，登记 successor 计划，不在本计划内实现大规模功能）。
- 不删除或改名 nop-ai-api 公共 API 类（跨模块公共 API 变更属 plan-first；本计划仅裁定与登记，不实施删除）。
- 不运行 mvn 全量构建。

## Phase 1 — 命名契约收口（AiFileTool 三方一致 + FileTool 工具描述文件命名）

Status: planned

Targets: `nop-ai/nop-ai-mcp-server/src/main/java/io/nop/ai/mcp/server/AiFileTool.java`、`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/mcp/McpConstants.java`、`docs-for-ai/02-core-guides/service-layer.md`、`nop-ai/nop-ai-tools/src/main/resources/_vfs/nop/ai/tools/`、`GraphQLToolProvider` 相关测试

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` AiFileTool 命名裁定：**选 (A)**——权限字符串改为 wire 操作名面 `AiTool:read`/`AiTool:write`（与 `@BizModel("AiTool")` 一致）并同步 docs。理由：BizObjName `AiTool` 是 wire 面（`AiTool__*` 操作名、`GraphQLToolCallbackProvider` 经 `McpConstants.BIZ_OBJ_AI_TOOL` 查 BizObject），权限命名 `<BizObjName>:<action>` 的 BizObjName 应取 wire 面；方案 (B) 改 BizModel 名会改变全部 wire 操作名（破坏 MCP 消费面），改动面大且无收益；方案 (C) 维持不一致。落地后权限/类名/docs 三方一致，权限检查面与 wire 操作名面不再脱节。
- [x] `Fix` 按裁定落地命名统一：`AiFileTool.java:59/:71/:121` 权限字符串 → `AiTool:read`/`AiTool:write`；docs `service-layer.md:203` 权限命名表 BizObjName 列 `AiFileTool` → `AiTool`；`McpConstants.BIZ_OBJ_AI_TOOL = "AiTool"` 不变（wire 面即是它）；`GraphQLToolCallbackProvider` 消费面经常量引用无需改动。
- [x] `Fix` 回归测试：`TestAiFileTool` 新增 `testAuthPermissionsMatchBizObjNamePrefix`——反射遍历 `AiFileTool` 全部 `@Auth` 方法，断言每个 permission 字符串以 `McpConstants.BIZ_OBJ_AI_TOOL + ":"` 开头（防类名写法回归），16 例全绿。
- [x] `Decision` FileTool tool.json 处置裁定：**选 (A) + (B-接线)**——`FileTool_grepFiles.tool.json` 改名 `FileTool__grepFiles.tool.json`（与 `ToolSpecificationLoader` 加载约定 `/nop/ai/tools/{toolName}.tool.json` 一致，消除静默回退）；4 个死 `.task.json` 因内容与真实注册操作（loadDslSchema/loadDslSchemaForFileType/loadDslFile/saveDslFile）的入参完全一致，直接转 `.tool.json` 接线（不删除——删除会丢描述；不登记保留——`.task.json` 扩展名全仓无 loader，改名后立即生效），同时消除死资源与这 4 个操作的静默回退。
- [x] `Fix` 按裁定落地：`FileTool_grepFiles.tool.json` → `FileTool__grepFiles.tool.json`（git mv）；4 个 `.task.json` → `.tool.json`（git mv）；`grep -rn "FileTool_grepFiles|task\.json"` 源码/资源零残留。
- [x] `Proof` 复核：新增 `TestGraphQLToolProviderSpecLoading`（4 例）——`ToolSpecificationLoader.loadSpecification("FileTool__grepFiles")` 命中（描述为 JSON 英文非 schema 派生）；4 个原 task.json 操作现可加载；`GraphQLToolProvider.getTool("FileTool__grepFiles")` 描述非静默回退（端到端）；未知操作反例返回 null 不误伤回退语义。`grep -rn "tool.json\|task.json"` 无残留不一致命名。

Exit Criteria:

- [x] AiFileTool 命名四方一致（BizObjName `AiTool`/权限 `AiTool:*`/docs/service-layer.md:203/消费面经 `McpConstants`），权限命名 `<BizObjName>:<action>` 约定成立。
- [x] FileTool 工具描述文件命名与加载约定一致（`FileTool__grepFiles.tool.json` 双下划线命名命中 loader），原 4 个死 `.task.json` 已显式处置（转 `.tool.json` 接线），无"命名不符 + 静默回退"状态。
- [x] **端到端验证**：`TestGraphQLToolProviderSpecLoading#testGraphQLToolProviderUsesJsonDescriptionNotFallback` 经真实 `IGraphQLEngine` 构建 `GraphQLToolProvider`，断言 `getTool("FileTool__grepFiles")` 描述为 JSON 英文描述（非 schema 派生）。
- [x] **接线验证**：`.tool.json` 命名修正后 `ToolSpecificationLoader.loadSpecification("FileTool__grepFiles")` 返回非 null 且描述命中 JSON（测试断言描述非静默回退）；4 个原 task.json 操作同样断言可加载。
- [x] **无静默跳过**：命名不符导致的静默回退路径消除（grepFiles 命中 + 反例测试证明未命中返回 null 的既有语义不受影响）；死 `.task.json` 无说明残留已消除（转正接线）。
- [x] owner doc 更新：`service-layer.md` 权限命名表已同步（`AiFileTool` → `AiTool`）；nop-ai-tools 工具描述文件命名约定已由新回归测试固化，无需另开设计文档。
- [x] `./mvnw test -pl nop-ai/nop-ai-mcp-server,nop-ai/nop-ai-tools -am` 通过（TestAiFileTool 16/0、TestGraphQLToolProviderSpecLoading 4/0、TestFileToolBizRegistration 2/0、TestGraphQLToolSetConditionalRegistration 4/0、TestFileToolBizModelProjectDir 3/0）。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 2 — 公共 API 死面裁定（ChatStreamAccumulator / ChatUserMessage.parts 多模态）

Status: planned

Targets: `nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/stream/ChatStreamAccumulator.java`、`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/messages/ChatUserMessage.java`、nop-ai-core dialects、相关 owner docs

- Item Types: `Decision | Fix | Proof`

- [x] `Decision` ChatStreamAccumulator 归宿裁定：**选 (B) reserved**——javadoc 标注"当前无生产消费者、为统一累积预留"，与既有各 dialect 自实现并存。理由：各 dialect（OpenAiDialect/ResponsesDialect 等）chunk 累积逻辑已是稳定现状且各有流式事件语义差异（具名事件/参数通道等），统一属 optimization candidate，不阻塞契约面收口；(A) 接线是跨模块功能实现超出本计划范围；(C) 删除属跨模块公共 API 变更且类有独立单测守护累积语义，无删除必要。
- [x] `Decision` ChatUserMessage.parts 多模态归宿裁定：**选 (B) reserved**——登记 API 表面为未接线 reserved，消除"plan 326 声明但未落地"的隐含承诺。理由：6 个 dialect 请求构建只读 `getContent()` 文本视图，image/audio part 无 wire 编码（纯图片消息 content=null 静默丢弃）；(A) 接线（OpenAI image_url / Responses input_audio 编码）为跨模块功能实现登记 successor 不在本计划实施；(C) 移除 API 属跨模块公共 API 变更且 `TestChatOptionsAndUserMessageParts` 守护其拷贝/委托语义，无必要。
- [x] `Fix` 按裁定落地文档/javadoc：`ChatStreamAccumulator` javadoc 增 Reserved 标注（与 dialect 自实现并存）；`ChatUserMessage.parts` 与 `ContentPart` javadoc 改写为"未接线 reserved，image/audio 无 wire 编码，接线前按预留 API 面使用"（删除"330 ResponsesDialect 负责"的隐含承诺）；`ai-dev/design/nop-ai-responses-migration-design.md` 使用契约多模态条目补状态对齐裁定。
- [x] `Proof` 复核：grep 全仓 main 代码 `ChatStreamAccumulator` 消费面零命中（仅测试类引用）；`getParts()/setParts()/addPart(/ContentPart` main 代码仅 `ResponsesDialect.buildContentParts`（无关 wire-map 本地 helper，非 ContentPart 模型消费）——死面均有显式 reserved 登记，无隐含承诺残留。

Exit Criteria:

- [x] ChatStreamAccumulator 与 ChatUserMessage.parts 均有显式归宿（均裁定 **reserved**，javadoc + design doc 双处登记），无隐含承诺残留。
- [x] **端到端验证**（接线裁定）：不适用——两项均裁定 reserved，写明：无 dialect 多模态接线路径（接线已登记为后续计划事项）。
- [x] **接线验证**（接线裁定）：不适用——reserved 裁定，无 parts 消费在 dialect 运行时被使用；grep 证明 main 代码零消费。
- [x] **无静默跳过**：纯图片消息静默丢弃问题已显式登记为 reserved（javadoc 明示"接线前按预留 API 面使用"），不再无说明残留死面。
- [x] owner doc 更新：`ai-dev/design/nop-ai-responses-migration-design.md` 多模态使用契约条目补状态对齐裁定（plan 326 声明与实际状态对齐）；nop-ai-api 类 javadoc 已同步。
- [x] `No new test required`（reserved 裁定）：既有 `TestChatStreamAccumulator`（2 例）与 `TestChatOptionsAndUserMessageParts`（10 例）继续守护模型语义；涉及模块编译验证 `./mvnw test -pl nop-ai/nop-ai-api,nop-ai/nop-ai-core -am` 通过。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 3 — 未用依赖清理（mcp-server / coder / shell / agent pom）

Status: planned

Targets: `nop-ai/nop-ai-mcp-server/pom.xml`、`nop-ai/nop-ai-coder/pom.xml`、`nop-ai/nop-ai-shell/pom.xml`、`nop-ai/nop-ai-agent/pom.xml`

- Item Types: `Fix | Proof`

- [x] `Fix` nop-ai-mcp-server：删除 `nop-graphql-core` compile 依赖（`:21`）；Proof 先行——全模块（main+test）`io.nop.graphql` import 零命中。
- [x] `Fix` nop-ai-coder：删除 `nop-ai-api` 与 `nop-ui` compile 依赖（`:22/:62`）；Proof 先行——`io.nop.ai.api.*`/`nop.ui.*` import 零命中。
- [x] `Fix` nop-ai-shell：删除 `jline-reader`（optional）依赖（`:35`）；Proof 先行——`jline` 引用零命中。
- [x] `Fix` nop-ai-agent：删除 `nop-autotest-junit`（test scope）依赖（`:44`）；Proof 先行——测试代码 `io.nop.autotest`/`JunitBaseTestCase` 零使用（纯 JUnit5 自组装）。**执行发现**：H2 驱动原经 nop-autotest-junit 传递提供，DB 存储测试（TestMultiTenantDbIsolation 等）实际需要 `org.h2.Driver`——按 declare-what-you-use 补 `com.h2database:h2` test scope 直接依赖（版本 BOM 管理 2.4.240），语义等价且无隐式传递。
- [x] `Proof` 逐项 Proof 引用清零：`grep -rn "io.nop.graphql\|io.nop.ui\|jline\|autotest" <module>/src --include="*.java"` 零命中（main/test 均查）；删除后 4 模块 `./mvnw test -pl <module> -am` 全绿（合计 3768 tests / 0 failures / 0 errors；mcp-server 含 Phase 1 新增测试）。

Exit Criteria:

- [x] 4 个 pom 的未用依赖声明已删除（nop-graphql-core / nop-ai-api / nop-ui / jline-reader / nop-autotest-junit；git diff 只含 pom 变更 + 等价 h2 直接声明替代原传递提供）。
- [x] 各模块删除依赖后 `./mvnw test -pl <module> -am` 通过（nop-ai-mcp-server / nop-ai-coder / nop-ai-shell / nop-ai-agent 一次联测全绿，3768 tests / 0 failures / 0 errors）。
- [x] **端到端验证**：依赖删除后模块编译 + 测试全链通过（mvn exit=0 为判定依据）。
- [x] **接线验证**（不适用）：依赖删除不涉及运行时调用连线；以编译+测试通过为准。
- [x] **无静默跳过**：不以"保留但不使用"替代删除；删除前 Proof 确认零引用（grep 零命中）；agent 的 H2 依赖为等价显式声明（非恢复 nop-autotest-junit）。
- [x] `No owner-doc update required`：module-groups.md 已声明 nop-ai-agent 的 nop-dao/nop-message-core 为 test scope（保留），未声明 mcp-server/coder/shell/agent 的上述被删依赖；无文档需同步。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Phase 4 — 文档分层描述修正 + beans.xml XDSL 卫生

Status: planned

Targets: `docs-for-ai/01-repo-map/module-groups.md:86-87`、`nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/beans/ai-toolkit-defaults.beans.xml`、`nop-ai/nop-ai-mcp-server/src/main/resources/_vfs/nop/ai/beans/ai-mcp-server-defaults.beans.xml`、`ai-dev/design/nop-ai-agent/`（如有 toolkit/tools 设计文档）

- Item Types: `Fix | Proof`

- [x] `Fix` module-groups.md:86 的 `nop-ai-toolkit` 描述改为与 live 一致（"工具执行层：19 个具体 `*Executor`（ReadFile/Bash/Http/GraphqlQuery/ApplyDelta/Patch/Skill 等）+ `LocalToolFileSystem` + `ToolManagerImpl`/`DefaultToolExecutorProvider`；抽象面为 `IToolExecutor`/`IToolManager` 与工具 DSL（tool.xdef）"——live 实测 `io.nop.ai.toolkit.tools` 19 个 `*Executor` 类，非"工具抽象层"）；`module-groups.md:17` 总览行的 `nop-ai-toolkit`（工具抽象层）同步为"工具执行层：抽象契约 + 具体执行器"；`nop-ai-tools` 行职责边界（具体工具实现 / skill 引擎 + DSL 文档工具契约实现）与 toolkit 已区分无需改动。
- [x] `Fix` toolkit `ai-toolkit-defaults.beans.xml` 补 `x:schema="/nop/schema/beans.xdef"` 并修正 `xmlns:ioc="urn: nop-ioc:1.0"`（空格 URI）→ `xmlns:ioc="ioc"`（与 nop-ai-tools 侧已修形态一致）；同型遗留 `test-mock.beans.xml`（toolkit test resources，同样 `urn: nop-ioc:1.0`）一并修正。
- [x] `Fix` mcp-server `ai-mcp-server-defaults.beans.xml` 补 XML 声明（`<?xml version="1.0" encoding="UTF-8"?>`）；扫描发现 `ai-tools-defaults.beans.xml`（nop-ai-tools）同样缺 XML 声明（plan 声称"tools 侧已修"不准确，实为仅修了 namespace），按 Proof 的"同型遗留"扫描一并补齐。
- [x] `Proof` 复核：`grep -rln 'xmlns:ioc="urn: '` 全仓零命中；nop-ai 全模块 beans.xml 首个非注释行均为 XML 声明（零 NODECL）；`x:schema="/nop/schema/beans.xdef"` 全覆盖（零 NOXSCHEMA）；module-groups.md 描述与 live（`find nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/` 19 个 `*Executor`）一致。

Exit Criteria:

- [x] module-groups.md toolkit/tools 分层描述与 live 一致（读后可在仓库核对其描述：19 个 `*Executor` + `LocalToolFileSystem` + `ToolManagerImpl`）。
- [x] toolkit/mcp-server beans.xml XDSL 卫生修复（x:schema / xmlns URI / XML 声明三者达标；toolkit main + test-mock、mcp-server、tools 四处补齐）。
- [x] **端到端验证**（不适用）：文档/XDSL 卫生无运行时路径。
- [x] **接线验证**（不适用）：无新组件协作。
- [x] **无静默跳过**（不适用）：无代码行为变更。
- [x] No new test required: 文档/XDSL 卫生修订；涉及模块编译验证 `./mvnw test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-ai-mcp-server -am` 通过（mvn exit=0）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0。
- [x] `ai-dev/logs/` 对应日期条目已更新（收口时统一追加）。

## Closure Gates

> 关闭条件（01-file-ledger §4.3 派生）：4 个 in-scope 问题簇（命名契约 / 公共 API 死面 / 未用依赖 / 文档与 XDSL 卫生）全部收口——修复落地或按裁定登记（reserved / successor），无确认的 live defect / contract drift 被静默降级到 deferred / follow-up；AiFileTool 命名四方一致、FileTool 描述文件命名与加载约定一致（或死资源显式处置）、死面均有显式归宿、4 个 pom 未用依赖删除、module-groups.md 与 beans.xml 卫生达标；各 Phase Exit Criteria 全数达成（含端到端 / 接线 / 无静默跳过验证）；涉及模块 `./mvnw test -pl <module> -am` 通过、`node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0；受影响的 owner docs（`docs-for-ai/02-core-guides/service-layer.md`、`docs-for-ai/01-repo-map/module-groups.md`、`ai-dev/design/nop-ai-agent/` 等）已同步到 live baseline 或明确写明 `No owner-doc update required`；独立子 agent closure-audit 已完成并记录证据（含 Anti-Hollow 检查，见 `## Closure` 段）；`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0。本 section 不保留可写 checkbox，机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生。

## Draft Review Record

（空，由独立 reviewer 填写；drafter 不自行 dispatch）
- dispatch review #review-2026-09-14-110620-mission-driver-2026-09-15-0116-3-p2-module-hygiene-round3-1-7170e4a1 to opencode-pid-95032
- 2026-09-15：iteration 1，共识 approved #review-2026-09-14-110620-mission-driver-2026-09-15-0116-3-p2-module-hygiene-round3-1-7170e4a1

## Verification

 - pass test 20260915053052 exit=0

## Closure

- dispatch audit #audit-20260915053052-2026-09-15-0116-3-p2-module-hygiene-round3-1-b425afdc to opencode-pid-43721 models={exec:build,aud:opencode-go/deepseek-v4-flash}
- accepted #audit-20260915053052-2026-09-15-0116-3-p2-module-hygiene-round3-1-b425afdc：独立 closure audit 通过——Phase 1-4 全部落地并经 live repo 复核（Phase 1：AiFileTool.java 3 处 `@Auth` 改 `AiTool:read/write` + service-layer.md:203 BizObjName 改 `AiTool` + McpConstants.BIZ_OBJ_AI_TOOL="AiTool" 不变，TestAiFileTool#testAuthPermissionsMatchBizObjNamePrefix 反射守护；`FileTool_grepFiles.tool.json`→`FileTool__grepFiles.tool.json` + 4 个死 `.task.json` 转 `.tool.json`，源码树零 `task.json`/零残留，TestGraphQLToolProviderSpecLoading 4 例含 IGraphQLEngine 端到端非静默回退断言；Phase 2：ChatStreamAccumulator/ChatUserMessage.parts/ContentPart javadoc reserved 登记 + design doc 状态对齐，main 代码消费 grep 零命中；Phase 3：4 pom 删除未用依赖 + agent h2 直接 test 声明（等价替代），git diff 仅 pom+等价声明；Phase 4：module-groups.md:17/:86 改"工具执行层"（live 实测 19 个 *Executor）+ 4 个 beans.xml 补 x:schema/XML 声明/`xmlns:ioc="ioc"`，源码树 `urn: nop-ioc` 零遗留），本 visit 独立验证 doc-links --strict 退出码 0（0 errors，plan 文件 1 条旧文件名 warning 已修）+ `./mvnw test -pl nop-ai/nop-ai-mcp-server,nop-ai/nop-ai-tools -am` 全绿（TestAiFileTool 16/0、TestGraphQLToolProviderSpecLoading 4/0、TestGraphQLToolSetConditionalRegistration 4/0）、plan-check 49/49 全勾选，无 in-scope defect 降级（reserved 裁定均有显式登记与 successor 指认），roadmap 10 项勾选 + daily log 09-15 已同步