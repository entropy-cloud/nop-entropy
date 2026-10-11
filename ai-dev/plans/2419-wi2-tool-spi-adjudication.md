# 2419 WI2 工具 SPI 归一裁定与落地（双路线共存 + 死接缝退役）

> Plan Status: completed
> Last Reviewed: 2026-10-11（round 1 修复；round 2 fixed 可执行；独立 closure audit APPROVE 后签核）
> Source: ai-dev/backlog/nop-ai-structure-refactor-roadmap.md（WI2）；ai-dev/design/nop-ai-agent/04-tool-invocation.md
> Related: 2414（WI1，已引入 core 接缝 IAiToolInvoker 并承诺本 plan 裁定其归宿）。编号说明：原编号 2415 与 nop-stream roadmap 会话的 2415-wi5 计划冲突，2026-10-11 改号 2419（沿当日既有改号先例）。
> deps: WI1（plan 2414，已 completed）

## Purpose

裁定 nop-ai 的工具注册路线格局：IToolExecutor 路线（toolkit）与 BizModel 路线（GraphQL/MCP 面）正式共存写入 owner doc，还是收归单一运行时；并兑现 WI1 移交的三项收口——core 过渡接缝 `IAiToolInvoker` 的归宿、nop-ai-tools 的 14 个 `.tool.json` 惰性资源处置、`_vfs` 工具声明目录的归属裁定。

## Current Baseline

（2026-10-11 执行时 HEAD 实测，Explore 子代理普查 + 主会话复核。）

- **运行时命名空间已统一**：`ToolManagerImpl.listTools()`（`nop-ai-toolkit/.../manager/ToolManagerImpl.java:269-281`）扫 `VirtualFileSystem.findAll("/nop/ai/tools", "*.tool.xml")`——_vfs 跨模块合并，**toolkit 25 + agent 9 + datav 6 = 40 个 tool.xml 已在同一运行时命名空间**（文件名与 name attr 实测零冲突）；executor 经 `nopToolExecutorProvider`（`ai-toolkit-defaults.beans.xml:59-63` collect-beans by-type）全局解析：20 toolkit + 14 agent（`ai-agent-tools.beans.xml`，其中 call-agent/send-message/read-memory/write-memory/search-memory 5 个 executor 在 agent 但声明在 toolkit 目录）+ 6 datav。两处「同名目录」只是源码组织分立，非运行时分立。
- **双路线零适配代码**：`grep IToolExecutor` 在 nop-ai-tools / nop-ai-mcp-server main 源码 0 命中。BizModel 路线：`FileToolBizModel`（13 action，纯经 GraphQL 引擎以 `FileTool__*` 暴露；底层压在 deprecated `IFileOperator` 上——WI6 域）+ `AiFileTool`（nop-ai-mcp-server，3 action，经 `GraphQLToolCallbackProvider`（nop-spring-mcp-server-support）作为 MCP 出口工具面）。IToolExecutor 路线：20 个 toolkit executor + 14 agent executor，唯一 fan-out chokepoint `AgentToolDispatcher.java:683 toolManager.callTool`。两路线消费者不相交：前者服务外部 GraphQL/MCP 调用面，后者服务 agent 引擎内部工具循环。
- **WI1 移交项 1——IAiToolInvoker 接缝生产链已死**：`ai.xlib` 的 `ai:Execute` tag 从不设置 toolInvoker（attr 面无 tools/toolInvoker；WI1 已删 `ai:toolSet` 死属性）；AiCommand 的工具循环（:444-454 executeTools、:496-510 addTools）生产不可达。全部活消费者 = 3 个测试（TestAiCommandExecute 匿名实现、TestDeepWikiPrompts @Disabled、TestGraphQLToolSetConditionalRegistration）+ GraphQLToolProvider/FactoryBean。task-xml 工具通路从未在生产存在过（`ai:toolSet` 一直是死属性）。
- **WI1 移交项 2——.tool.json 惰性资源 14 个**：`nop-ai-tools/src/main/resources/_vfs/nop/ai/tools/` 下 13 个 `FileTool__*.tool.json` + 1 个 `SequentialThinking__processThought.tool.json`（结构 `{description, inputSchema}`，无 name 字段，1:1 镜像 BizModel action，与 40 个 tool.xml 工具名零重叠）；唯一 loader `ToolSpecificationLoader` 已随 WI1 删除，全仓无任何 Java 加载 `.tool.json`。删除是纯资源操作。
- **WI15 交叉（本 plan 不动）**：nop-ai-mcp-server 对 nop-ai-coder 的唯一 compile 依赖（pom:19-22）用于 AiFileTool 的 2 个 Simplifier（JsonSimplifier/XNodeSimplifier，:4-5,:110,:116）——随 WI15 解除。
- **agent 引擎对 IToolManager 为 compile 依赖且调用点广泛**（DefaultAgentEngine/AgentExecutorResolver/ReActAgentExecutor/AgentToolDispatcher/AgentToolPlanResolver/AgentPromptAssembly/ReActAgentExecutorBuilder 约 15 处）——改 IToolManager 接口签名会波及 agent 大量测试；本 plan 不改 IToolManager 签名。
- **owner doc**：`04-tool-invocation.md` 已有 WI1 过渡接缝节（:139-141，预留本 plan 入口）；2.1 节「工具定义位于…（由 nop-ai-toolkit 提供）」表述不完整（实际含 agent/datav 三处源码目录）。

## Goals

- 双路线正式共存写入 owner doc：IToolExecutor 路线 = agent 引擎运行时（40 tool.xml + 34 executor 单一命名空间）；BizModel 路线 = GraphQL/MCP 外部工具面（FileToolBizModel/AiFileTool），两者消费者不相交、无适配需求，登记重启条件（出现「外部工具需进 agent 循环」或「agent 工具需经 GraphQL/MCP 暴露」的真实需求时再立适配 plan）。
- 退役生产死代码：core 接缝 `IAiToolInvoker` + AiCommand 工具循环（executeTools/addTools/enabledTools/toolInvoker 字段族）+ `GraphQLToolProvider`/`GraphQLToolSetFactoryBean` + bean `nopGraphQLToolSet`（ai-tools-defaults.beans.xml 条件注册块）。
- 删除 14 个 `.tool.json` 惰性资源。
- 声明目录裁定：维持 `_vfs/nop/ai/tools/` 多模块分立 + 运行时单一命名空间的现状（迁移文件是纯组织学动作，零行为收益），在 owner doc 显式登记三处源码目录全集（toolkit/agent/datav）与命名约定（<tool-name>.tool.xml 唯一性约束）。

## Non-Goals

- 不改 `IToolManager`/`IToolExecutor` 接口签名与 `ToolManagerImpl` 行为（agent 引擎面冻结）。
- 不动 FileToolBizModel/AiFileTool 的实现与其对 `IFileOperator`/coder Simplifier 的依赖（WI6/WI15 域）。
- 不迁移 tool.xml 声明文件位置（运行时已合并）。
- 不触碰 `nop-datav` 的 6 个工具（平台模块，仅纳入登记全集）。

## Scope

### In Scope

- nop-ai-core：AiCommand 工具循环与接缝删除、相关测试改写。
- nop-ai-tools：GraphQLToolProvider/GraphQLToolSetFactoryBean 删除、beans 条目删除、.tool.json 资源删除、相关测试删除/改写。
- nop-ai-deepwiki：TestDeepWikiPrompts 整体删除 + 非 @Disabled 资源装载 smoke 替代（同 Phase 1 执行项裁定）。
- `ai-dev/design/nop-ai-agent/04-tool-invocation.md`：WI2 裁定节 + 2.1 目录全集修正 + WI1 节尾指针更新。

### Out Of Scope

- nop-ai-toolkit、nop-ai-agent、nop-datav、nop-spring-mcp-server-support、nop-ai-mcp-server 代码零改动。

## Execution Plan

### Phase 1 - 裁定落地（死代码退役 + 资源删除）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/command/`、`nop-ai/nop-ai-tools/`

- Item Types: `Decision` + `Fix`

- [x] `AiCommand` 删除工具循环：`IAiToolInvoker` 字段/toolInvoker(...)/setToolInvoker/getToolInvoker、`enabledTools` 字段族（getEnabledTools/setEnabledTools/enableTools/enableTool）、`maxSteps` 字段族（getMaxSteps/setMaxSteps/maxSteps——round 1 审查 M1：唯一消费点是工具循环步进）、`addTools`、`executeTools`、`executeWithTools` 整方法（其剩余主体并入 executeOnceAsync 主路径）（保留单次 callAsync + retry + 门面返回的主路径）、`ERR_AI_UNKNOWN_TOOL_CALL` 引用（round 1 审查 M2 裁定：随最后一个 main 消费者删除——同步删 `TestNopAiCoreErrorsDescriptionEnglish` 的对应 CodeSpec 行并把计数断言 19→18，`ARG_TOOL_NAME` 常量一并删除）；`chatOptions(Object)` 中 `toolChoice` 键映射与 `ChatOptions` 上 tools/toolChoice 语义保留（请求级声明面不变）
- [x] 删除 `IAiToolInvoker.java`；删除 `GraphQLToolProvider.java`/`GraphQLToolSetFactoryBean.java`；删除 `ai-tools-defaults.beans.xml` 的 `nopGraphQLToolSet` bean 块（含 P3-MA1-016 条件注册注释的失效部分，保留其余 bean）
- [x] 删除 14 个 `.tool.json` 资源
- [x] 测试处置：删 `TestGraphQLToolSetConditionalRegistration.java`（裁定：其 4 例中 2 例守被删 bean；另 2 例守 biz-defaults 条件注册模型级守护随 bean 消亡而消散——接受理由：biz 面本 plan 零触碰，nop-ioc 引擎级条件测试仍在，P3-MA1-016 风险本体随被守护 bean 退役而消散）；`TestAiCommandExecute` 删工具循环用例（保留 execute/重试谓词/重试耗尽 3 例）；`TestNopAiCoreErrorsDescriptionEnglish` 删 UNKNOWN_TOOL_CALL CodeSpec 行 + 计数 19→18；`TestDeepWikiPrompts` **整体删除**（round 1 审查 M4：generate-readme.prompt.yaml 明文指示模型调用 FileTool__* 工具——去工具注入后重写是语义空转；该测试是 deepwiki 唯一测试且 @Disabled），以非 @Disabled 的最小资源装载 smoke 替代（deepwiki task-xml 经 TaskStep-generator transform + prompt yaml 经 promptTemplateManager 解析，无 LLM 调用，参照 coder AiTaskStepExecuteWiringTest 模式）；`nop.ai.tools.graphql-tool-names` 配置键消费点随类删除（检索 docs-for-ai 中该键的文档表述并同步）
- [x] 编译 + 受影响模块测试（round 1 审查 Blocker 1：验证面必须含下游 deepwiki）：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-skills/nop-ai-deepwiki -am`

Exit Criteria:

- [x] main scope grep `IAiToolInvoker|GraphQLToolProvider|GraphQLToolSetFactoryBean|nopGraphQLToolSet` 零命中；`.tool.json` 全仓（src 区）零文件
- [x] core/tools/deepwiki 测试全绿，删除用例数与留档一致
- [x] No new test required: 本 Phase 为纯删除 + smoke 替代测试（替代测试本身是新测试，其断言要求见执行项）；No owner-doc update required（Phase 2 统一回写）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc 裁定回写

Status: completed
Targets: `ai-dev/design/nop-ai-agent/04-tool-invocation.md`

- Item Types: `Decision` + `Proof`

- [x] 新增 `## WI2 工具 SPI 归一裁定` 节：双路线共存格局（路线 A = IToolExecutor 运行时：40 tool.xml/34 executor/单一 fan-out chokepoint；路线 B = BizModel GraphQL/MCP 外部面：FileToolBizModel 13 op + AiFileTool 3 op）、共存依据（消费者不相交、零适配需求）、重启条件两条（①跨路线互通真实需求出现时另立 plan，含 Protected Areas 迁移说明要求；②目录项专属——出现 tool.xml 文件名/name attr 冲突或新增第四处声明源码目录时重开目录统一裁定）
- [x] 裁定节补「文件工具四份实现按共存裁定的归属映射」一行（round 1 审查 M7，闭合 roadmap WI2「收敛文件工具四份实现」子句）：toolkit fs+executors 保留；FileToolBizModel/AiFileTool 维持 BizModel 外部面；core file 域（IFileOperator）随 WI6；coder Simplifier 依赖随 WI15
- [x] 修正 2.1 节目录全集表述（toolkit/agent/datav 三处源码目录 + 运行时单一命名空间 + `<tool-name>.tool.xml` 唯一性约定）；WI1 节尾「WI2 裁定入口」改为已裁定指针；核查「五、JSON Schema 兼容」节与 .tool.json 删除后表述是否仍成立；WI1 节尾「13 个 .tool.json」计数修正为 14（round 1 审查 M9）；`AiCommand.chatOptions(Object)` 不支持键调试列表中 enabledTools 字面量保留（map 键仍属不支持键面，非半删）
- [x] `docs-for-ai/03-modules/nop-ai.md` 三处同步（round 1 审查 M3）：:164 `nop.ai.tools.graphql-tool-names` 配置键行删除（键消亡）、迁移说明表 :198 IAiToolInvoker 行改写（接缝已退役）、:203④ nopGraphQLToolSet 行改写（bean 已随 WI2 退役）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] owner doc 三处更新在位且与 live code 一致（目录数、executor 数可复核）；nop-ai.md 三处同步在位
- [x] check-doc-links --strict 0 error

## Closure Gates

- [x] IAiToolInvoker/GraphQLToolProvider/GraphQLToolSetFactoryBean/nopGraphQLToolSet/14 .tool.json 全部删除，AiCommand 无工具循环残留
- [x] 双路线共存裁定写入 owner doc 并附重启条件；声明目录三处全集登记
- [x] main scope 相关 grep 零命中（Phase 1 exit criteria 同款）
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-ai/nop-ai-skills/nop-ai-deepwiki -am` 全绿
- [x] 不存在被静默降级的 in-scope live defect（退役的生产死代码均有死链证据锚点，非 live defect）
- [x] owner docs（04-tool-invocation.md；docs-for-ai 中 graphql-tool-names 配置键表述）已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：AiCommand 主路径（prompt→ChatRequest→IChatService→门面）行为与 WI1 基线一致（TestAiCommandExecute 保留用例全绿即为回归证据）；无空方法体/静默跳过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-core --severity high` 无本次新增 high 发现
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2419-wi2-tool-spi-adjudication.md --strict` 退出码 0（自指验证：勾选后复跑退出码 0）

## Deferred But Adjudicated

### FileToolBizModel 底层 deprecated IFileOperator

- Classification: `watch-only residual`
- Why Not Blocking Closure: 属 core/file 域归位裁定（roadmap WI6），BizModel 路线的 FS 抽象迁移与本 plan 的 SPI 格局裁定正交。
- Successor Required: `yes`
- Successor Path: roadmap WI6

### mcp-server 对 coder 的 compile 依赖（AiFileTool 2 个 Simplifier）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 倒挂解除需先裁定 Simplifier 的归属（下沉或复制），roadmap 已指派 WI15。
- Successor Required: `yes`
- Successor Path: roadmap WI15

## Non-Blocking Follow-ups

- deepwiki generate-readme.prompt.yaml 的工具指示文本（指示调用 FileTool__*，而生产 task 路径从不挂工具）是既存 live 内容漂移，随本 plan 删除该测试后在 log 登记后续处置归 deepwiki 模块 owner。
- roadmap 基线「agent 9 + toolkit 25」补记 datav 6 个（WI21 收口校对时同步 roadmap 表述，不改历史数字语义）。

## Closure

Status Note: WI2 两 Phase 完成：双路线共存裁定（含重启条件与四份实现归属映射）写入 owner doc；core 接缝/AiCommand 工具循环/GraphQL 工具面/14 个 .tool.json/unknown-tool-call 错误码全部退役；main scope 相关 grep 零命中；core 459/tools 29/deepwiki 2 测试全绿（deepwiki 首个非 @Disabled smoke 测试落地，含 StubHttpClient fail-loud 接线）。原编号 2415 与 stream 会话冲突改号 2419。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit，2026-10-11）
- Evidence:
  - A 退役面 PASS：接缝/graphql 目录/两测试/14 .tool.json 全部 ABSENT，beans 仅剩 2 bean，NopAiCoreErrors 无 UNKNOWN_TOOL_CALL/ARG_TOOL_NAME（datav 同名常量为 scope 外不同常量）
  - B Anti-Hollow PASS：AiCommand 406 行仅保留 toolChoice 声明面与 enabledTools 调试字面量（均为 plan 明示保留项），主路径完整（retryNTimes/callChat/ERR_AI_RESULT_IS_EMPTY 门面）；main scope grep 零命中；src 区 .tool.json 零文件
  - C 测试 PASS：TestAiCommandExecute 恰 3 例断言真实；错误码契约钉 18；TestDeepwikiResourcesLoad 2 例非 @Disabled（StubHttpClient fail-loud 接线与 log 执行修正②吻合）
  - D owner docs PASS：04-tool-invocation.md WI2 节（重启条件/归属映射）+ 2.1 三目录全集（toolkit 25/agent 9/datav 6 与 live 逐一复核）+ WI1 指针更新 + .tool.json 计数修正 14；nop-ai.md 三处同步；check-doc-links --strict exit 0
  - E 文本一致性 PASS：Phase 全 completed、执行项全 [x]、Deferred 两项在位、当日 log WI2 条目在位；scan-hollow 门由审计者代验（唯一 high 为 2026-08 既存 NoOp，非本次新增）
  - 审计 Minor 处置：executor 计数笔误 34→40 已随签核修复（owner doc + 本 plan）；plan 编号冲突已改号 2419 并登记
  - 测试绿留档：core 459 / tools 29 / deepwiki 2（执行者 run，审计者按 scope 未重跑）

Follow-up:

- deepwiki generate-readme.prompt.yaml 的工具指示文本 live 漂移（指示调用 FileTool__*，生产 task 路径从不挂工具）——归 deepwiki 模块 owner 后续处置
- ai-dev/plans/ 存在双 2418 编号冲突（nop-tools 会话与 stream 会话各一）——属并行会话协调事项，登记待其自行处理
- （承袭）FileToolBizModel 底层 IFileOperator → WI6；mcp-server Simplifier → WI15
