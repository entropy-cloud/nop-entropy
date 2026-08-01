# W6-1 Recipe 配方组合层

> Plan Status: completed
> Last Reviewed: 2026-08-02
> Source: `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W6-1（唯一剩余 todo）；`ai-dev/analysis/agent-survey/2026-08-01-goose-provider-hook-recipe-analysis.md`（Recipe 增量）；`ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md` §9（Recipe 草案）；架构探索 `ses_04078324effeHnL1d0634VVHzG`
> Mission: nop-ai-agent-harness-evolution
> Work Item: W6-1 Recipe 配方组合层
> Related: W3-2 声明式 filter-chain（`2026-08-01-1437-4`，已 completed，本计划的 xdef 子模型 + Resolver 接线前置范式）；Plan 231 team 声明（agent.xdef 子模型 + Binder 接线范式）

## Purpose

把 nop-ai-agent 从"单体静态配置"升级为"可组合行为单元"：引入 `recipe.xdef`（可分享的 agent 行为配方 = prompt 模板 + 工具集 + 模型配置 + hooks 快照），AgentModel 可引用 1..n recipes 在装配期有序叠加合并。这是 nop-ai-agent-harness-evolution roadmap 的最后一个 work item（W1-W5 全部 completed），完成后整个 mission 的 roadmap 定义达成。

## Current Baseline

> 已对照 live repo 核对（2026-08-02）。

- **agent.xdef** 位于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/agent.xdef`，`xdef:bean-package="io.nop.ai.agent.model"`。当前 AgentModel 是单体配置：prompt + tools(csv-set) + chatOptions + hooks + constraints + middlewares + filter-chain + team。**无任何 recipe/组合层**。
- **装配期唯一入口**：`AgentSessionSupport.loadAgentModel(String agentName)`（`nop-ai/nop-ai-agent/src/main/java/io/nop/ai/agent/engine/AgentSessionSupport.java:97`）经 `ResourceComponentManager.instance().loadComponentModel("/" + agentName + ".agent.xml")` 加载原始 AgentModel。该方法被 4 处调用（`DefaultAgentEngine.doExecute:711` + `AgentSessionLifecycle:302/440/620` 的 resume/wake/restore），是所有执行路径的单一汇聚点——**recipe 合并应在此处（或紧随其后）发生**，下游（resolveExecutor / AgentPromptAssembly / AgentToolPlanResolver）消费合并后的 AgentModel，无需感知 recipe。
- **既有子模型接线范式**（W3-2 / Plan 231）：filter-chain 经 `AgentExecutorResolver.resolveFilterChain` → `FilterChainResolver.resolve` → `ResolvedFilterChain`（声明侧 refs 与执行侧 resolved 同步不可变）；team 经 `AgentTeamBinder.precheckTeamDeclarations` / `autoBindTeam`。两者均遵循"xdef 子模型 → codegen 生成 `_gen/_XxxModel` + retention stub → 专用 Resolver/Binder 类"管线。
- **ToolSet 不存在**：新工具管线（`IToolManager.loadTool(name)`）按扁平工具名逐个加载，无分组/ToolSet 抽象。既有 `IAiChatToolSet`（nop-ai-core）已 deprecated 且仅用于 legacy AiCommand/task engine，**不**用于 agent 执行路径。→ recipe 工具集复用扁平 csv-set 模式。
- **skill 与 recipe 边界**：skill（`ISkillProvider` → `SkillResolver` → `SkillAssemblyResult`）在 `AgentPromptAssembly.consultSkills()` **运行时**注入工具名 + prompt 片段（additive，不改变 AgentModel 结构）。recipe 是**装配期结构化组合**（合并进 AgentModel 本身，影响 prompt/tools/chatOptions/hooks），两者正交。
- **design §9**（`nop-ai-agent-dsl.md:282-316`）已勾勒 recipe.xdef 雏形并标注"低优先，可选增强"，含三个 Open Questions（叠加冲突解析、动态引用、与 skill 边界）。本计划收口这些决策。
- **chat-options.xdef** 位于 `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/ai/chat-options.xdef`（`xdef:bean-package="io.nop.ai.core.model"`），recipe 的 model-config 复用它。
- **roadmap 状态**：W1-W5 全部 completed（W1 门控 / W2e 错误规范化 / W2 可靠性 / W3 中间件 / W4 上下文 / W5 guardrail），**W6-1 是唯一未完成项**。前序 harness 计划的 Deferred 项全部是 out-of-scope improvement / optimization candidate（持久化后端、LLM 驱动攻击生成、执行级 BAIL 等），均与 W6-1 正交，不可 re-trigger 进本计划。
- **register-model**：agent 经 `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/agent.register-model.xml` 注册（`xdsl-loader fileType="agent.xml"`），recipe 需新增同构注册。

## Goals

- 交付 `recipe.xdef`：定义可分享的 agent 行为配方（prompt 模板 + 工具集 + 模型配置 + hooks），作为 VFS 中独立 `*.recipe.xml` 文件。
- AgentModel 新增 `<recipes>` 引用：agent 可引用 1..n 有序 recipes，每条带模板参数。
- 装配期合并：`loadAgentModel` 加载 agent 后，将引用的 recipes 按裁定语义合并进 AgentModel，下游消费合并后的模型（零下游改动）。
- 有序叠加 + 明确冲突解析：多 recipe + agent 自身配置按逐字段裁定规则合并，所有合并行为可验证、可观测。
- 端到端可用：recipe 的 prompt 模板（参数化渲染后）、tools、chatOptions、hooks 确实流入运行时（assembled system message 含 recipe prompt、toolManager 解析 recipe 工具、ChatOptions 含 recipe model-config）。

## Non-Goals

- **不**做运行时/动态 recipe 加载（recipe 仅在装配期 `loadAgentModel` 解析，不支持运行时切换）。
- **不**做 recipe 分享/市场/远程仓库基础设施。
- **不**引入 ToolSet 抽象（复用既有扁平工具名 csv-set 模式）。
- **不**把 recipe 参数持久化到 session/checkpoint。
- **不**让 recipe 覆盖 middlewares / filter-chain / constraints / team / permissions / path-rules（roadmap W6-1 明确定义 recipe = prompt 模板 + 工具集 + 模型配置 + hooks 四要素；其余字段显式排除，留 successor）。
- **不**做 recipe 的 recipe（recipe 不引用其他 recipe，无递归组合）。

## Scope

### In Scope

- `recipe.xdef` schema（kernel nop-xdefs）+ codegen 模型（nop-ai-agent）
- `agent.xdef` 新增 `<recipes>` 子模型（kernel nop-xdefs）
- `recipe.register-model.xml`（VFS 加载 `*.recipe.xml`）
- `RecipeResolver`：加载引用 recipes、渲染模板参数、按裁定语义合并进 AgentModel
- `AgentSessionSupport.loadAgentModel` 接线（合并发生在返回前）
- 合并语义裁定（裁定 A–I）+ focused tests
- 端到端验证（Anti-Hollow：recipe 贡献确在运行时被消费）
- design `nop-ai-agent-dsl.md` §9 → final；roadmap W6-1 → done

### Out Of Scope

- middlewares / filter-chain / constraints / team / permissions / path-rules 的 recipe 合并（显式排除，留 successor）
- recipe 递归组合（recipe 引用 recipe）
- 动态/运行时 recipe 加载与切换
- recipe 持久化、分享市场、版本兼容迁移
- ToolSet 抽象

## Execution Plan

### Phase 1 - recipe.xdef 模型层 + agent recipes 引用

Status: completed
Targets: `nop-kernel/nop-xdefs/.../schema/ai/recipe.xdef`, `nop-kernel/nop-xdefs/.../schema/ai/agent.xdef`, `nop-ai/nop-ai-agent/.../model/`（codegen）, `nop-ai/nop-ai-agent/src/main/resources/_vfs/nop/core/registry/recipe.register-model.xml`

- Item Types: `Decision | Proof`

- [x] **裁定 A**（Decision）：Recipe 文件格式——独立 `recipe.xdef`（根节点 `<recipe>`），VFS 中 `*.recipe.xml`，经 `recipe.register-model.xml`（`xdsl-loader`，与 `agent.register-model.xml` 同构）加载为 `RecipeModel`。recipe 字段 = `name`(`!string`) + `version`(`string`) + `description`(`string`) + `prompt-template`(`string`，**非** `prompt-syntax`——见裁定 D/G：模板参数替换与 prompt 拼接在源字符串层完成，合并后统一解析为 `IPromptSyntaxNode`）+ `tools`(`csv-set`) + `model-config`(`xdef:ref="chat-options.xdef"`) + `hooks`（`xdef:body-type="list"`，每个 `<on>` 用 `xdef:bean-class="io.nop.ai.agent.model.AgentHookModel"` 引用既有生成类，**不**在 recipe 包内重新生成，避免合并时类型不匹配）。`xdef:bean-package="io.nop.ai.agent.model.recipe"`。
- [x] **裁定 B**（Decision）：Agent 引用机制——`agent.xdef` 新增 `<recipes xdef:body-type="list">`，每条 `<recipe xdef:name="AgentRecipeRefModel" ref="!string">`，`ref` = recipe 名（VFS 路径主键，与 agent name 同为 valid identifier），有序列表（顺序即叠加顺序）。每条可带 0..n `<param xdef:name="RecipeParamModel" name="!string" value="!string"/>` 子节点供模板参数化。`<recipes>` 缺省 = 无 recipe（零回归）。
- [x] **裁定 H**（Decision）：Scope 边界——recipe 仅承载 prompt-template + tools + model-config + hooks 四要素（roadmap 定义）；middlewares/filter-chain/constraints/team/permissions/path-rules 显式不在 recipe 字段中（Out Of Scope）。
- [x] 创建 `recipe.xdef`（kernel nop-xdefs `/nop/schema/ai/recipe.xdef`）
- [x] 在 `agent.xdef` 新增 `<recipes>` 子模型（含 `AgentRecipeRefModel` + `RecipeParamModel`）
- [x] 创建 `recipe.register-model.xml`（`nop-ai/nop-ai-agent/.../_vfs/nop/core/registry/`，`xdsl-loader fileType="recipe.xml" schemaPath="/nop/schema/ai/recipe.xdef"`）
- [x] 运行 codegen（`./mvnw compile -pl nop-ai/nop-ai-agent -am`）生成 `_gen/_RecipeModel`、`_gen/_AgentRecipeRefModel`、`_gen/_RecipeParamModel` + retention stub；确认 `_AgentModel` 自动获得 `_recipes` 字段
- [x] 编写 model 层单测：解析合法 `*.recipe.xml` → `RecipeModel` 字段正确；解析带 `<recipes>` 的 `*.agent.xml` → `AgentRecipeRefModel` 列表 + param 正确；非法 recipe（缺 `name` / ref 非法标识符）parse fail-loud

Exit Criteria:

- [x] `recipe.xdef` + `agent.xdef` 改动经 `./mvnw compile -pl nop-ai/nop-ai-agent -am` 校验通过（`-am` 拉入 kernel nop-xdefs；xdef 自洽 + `xdef:bean-class` 引用的 `AgentHookModel` 在 nop-ai-agent classpath 可解析）
- [x] `agent.xdef` 新增 `<recipes>` 后既有 `*.agent.xml`（无 recipes）解析零回归（向后兼容：缺省 = 无 recipe）
- [x] codegen 产物存在且 `_AgentModel` 含 recipes 字段（`./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过）
- [x] `recipe.register-model.xml` 注册成功，`ResourceComponentManager` 能加载测试用 `*.recipe.xml`
- [x] **无静默跳过**：recipe 缺 `name`、ref 非法标识符等配置错误显式抛异常（NopAiAgentException + 错误码），不返回 null/空
- [x] model 层单测覆盖：合法 recipe 解析、带 recipes 的 agent 解析、param 传递、非法配置 fail-loud
- [x] No owner-doc update required at this Phase（design 更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - RecipeResolver + 合并语义 + loadAgentModel 接线

Status: completed
Targets: `nop-ai/nop-ai-agent/.../recipe/RecipeResolver.java`, `nop-ai/nop-ai-agent/.../engine/AgentSessionSupport.java`

- Item Types: `Decision | Fix | Proof`

- [x] **裁定 C**（Decision）：合并模型——recipe = base layer，agent 自身配置 = override layer。agent 引用有序 recipes `[R1, R2, ...]`，合并从空基线开始，依次应用 R1 → R2 → ... → agent 自身配置。later layer 对 override 字段逐字段覆盖，对 additive 字段取并集。
- [x] **裁定 D**（Decision）：prompt 组合——合并发生在**源字符串层**。各 recipe 的 `prompt-template`（`string` 类型）经参数渲染（裁定 G）后，按 recipe 顺序拼接成 recipe prompt 段；agent 自身 `prompt`（`IPromptSyntaxNode`）经 `.getSource()` 取源字符串拼接在最后（agent 最具体）。结果字符串经 `PromptSyntaxParser` 解析为 `IPromptSyntaxNode` 后 set 到克隆的 AgentModel 上（重解析须复用与 agent.xml `<prompt>` 加载一致的 parser 配置，如 `enableInclude`/`allowUnknownPrefix`，避免含 `{{include}}`/前缀表达式的 agent prompt 重解析时配置不一致而抛异常）。agent 无 prompt 时仅用 recipe 段；recipe 无 prompt-template 时跳过。
- [x] **裁定 E**（Decision）：tools / hooks 合并——并集（additive）。`merged.tools = agent.tools ∪ ⋃ recipe.tools`；`merged.hooks = agent.hooks ∪ ⋃ recipe.hooks`（按声明顺序拼接，hook id 重复 fail-loud，错误码含重复 id）。因 hooks 经 `xdef:bean-class` 共享同一 `AgentHookModel` 类型（裁定 A），合并为同类型 `List<AgentHookModel>` 的拼接，无类型转换。
- [x] **裁定 F**（Decision）：model-config（chatOptions）合并——逐字段覆盖，later-wins。创建新 `ChatOptionsModel` 实例 → 复制 R1.model-config 非空字段 → R2 非空字段覆盖 → agent.chatOptions 非空字段覆盖。null 字段 = 不覆盖（保留下层值）。`ChatOptionsModel` 为 nop-ai-core 生成类（`io.nop.ai.core.model`），逐字段即 provider/model/seed/temperature/topP/topK/maxTokens/contextLength/stop。
- [x] **裁定 G**（Decision）：模板参数化——`prompt-template`（`string`）中 `{{paramName}}` 占位符由引用处 `<param name="..." value="..."/>` 替换（纯字符串 replace）。引用处缺参数（template 含 `{{x}}` 但无对应 param）= fail-loud（错误码含缺失参数名 + recipe ref）。param 值为纯字符串（首版不支持表达式求值）。渲染在源字符串上完成，**不**修改缓存的 RecipeModel 实例。
- [x] 实现 `RecipeResolver`（静态工具类，与 `FilterChainResolver` 同构，经 `ResourceComponentManager.instance()` 加载 recipe）：输入 `AgentModel`（含 `<recipes>` refs）→ 加载各 recipe → 渲染 prompt 模板参数（源字符串层）→ 按裁定 C–F 合并 → 返回合并后的 `AgentModel`。**缓存安全（裁定 I）**：`ResourceComponentManager` 返回共享缓存实例，RecipeResolver **必须** `cloneInstance()` 拷贝 AgentModel 后再修改；`cloneInstance()` 的 `copyTo()` 是浅拷贝，故集合字段必须创建**新的同类型集合实例**隔离后再修改（`tools` 是 `Set<String>` → 新 Set；`hooks` 是按 id 键控的 `KeyedList<AgentHookModel>` → 新 KeyedList 保持键控语义），不得原地修改共享引用；recipe 参数渲染在源字符串上完成，**不**修改缓存的 RecipeModel。无 `<recipes>` 时直接返回原 model（零回归 fast-path，跳过 clone）。
- [x] **裁定 I**（Decision）：缓存安全——`loadComponentModel` 返回的 AgentModel / RecipeModel 是共享缓存实例且可能 `freeze()`。RecipeResolver 合并前必须 `cloneInstance()` 拷贝（拷贝态默认未冻结）；浅拷贝的集合字段必须创建**新的同类型集合实例**隔离（`tools` → 新 `Set<String>`，如 `LinkedHashSet`；`hooks` → 新 `KeyedList<AgentHookModel>` 按 id 键控）后修改，不丢失键控语义；prompt 经 `.getSource()` 取源串拼接后 `PromptSyntaxParser` 重解析 set，不共享 AST 节点引用。
- [x] 接线 `AgentSessionSupport.loadAgentModel`（`:97`）：`loadComponentModel` 取得原始 AgentModel 后、返回前调用 `RecipeResolver.resolve(model)`，返回合并后模型。RecipeResolver 为静态工具类（无需在 AgentSessionSupport 增加字段/注入）。覆盖全部 4 个调用点（doExecute:711 + resume/wake/restore lifecycle:302/440/620）。
- [x] 编写合并语义单测（每条裁定一个验证用例）：
  - 裁定 C：2 recipes + agent，验证叠加顺序
  - 裁定 D：prompt 源字符串拼接顺序 = R1 + R2 + agent（经 `.getSource()` 取 agent 源串），合并结果经 PromptSyntaxParser 重解析
  - 裁定 E：tools 并集；hooks 并集 + 重复 id fail-loud
  - 裁定 F：chatOptions 逐字段覆盖（agent 覆盖 recipe，R2 覆盖 R1；null 字段保留下层）
  - 裁定 G：`{{param}}` 源字符串替换正确；缺参数 fail-loud
  - 裁定 I：缓存安全——合并后原缓存 AgentModel 实例未被修改（断言 `original.getPrompt()` / `original.getTools()` 不变）；原 RecipeModel 实例未被参数渲染修改
  - 零回归：无 recipes 的 agent 行为不变（fast-path，返回原实例引用）

Exit Criteria:

- [x] `RecipeResolver.resolve` 对无 recipes 的 AgentModel 返回原模型实例（零回归 fast-path 验证：`result == original`）
- [x] 2 recipes + agent 的合并结果逐字段符合裁定 C–F（prompt 源字符串顺序、tools/hooks 并集、chatOptions 逐字段覆盖）
- [x] prompt 模板 `{{param}}` 源字符串替换正确，缺失参数 fail-loud（NopAiAgentException + 错误码 + 缺失参数名）
- [x] hooks id 重复 fail-loud（错误码 + 重复 id）；hooks 经 `xdef:bean-class` 共享 `AgentHookModel` 类型，合并无类型转换
- [x] **缓存安全（裁定 I）**：合并后原缓存 AgentModel 实例未被修改（`original.getPrompt()` 等不变）；原 RecipeModel 实例未被参数渲染修改；集合字段经新 ArrayList 隔离
- [x] **接线验证**：`loadAgentModel` 返回的 AgentModel 确为合并后模型（断言合并结果，非原始 model）——4 个调用点中至少 doExecute 路径被测试覆盖
- [x] **无静默跳过**：recipe 加载失败（VFS 不存在）、ref 非法、参数缺失均显式抛异常，不返回半合并结果
- [x] 合并语义单测覆盖裁定 C–I 全部 + 零回归 fast-path
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] No owner-doc update required at this Phase（design 更新在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 端到端验证 + design 收口 + roadmap done

Status: completed
Targets: `nop-ai/nop-ai-agent/.../recipe/`（e2e 测试）, `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md` §9, `ai-dev/backlog/nop-ai-agent-harness-evolution-roadmap.md` W6-1

- Item Types: `Proof | Decision`

- [x] 编写端到端测试：构造 2 个 `*.recipe.xml`（R1 带 prompt-template + tools + model-config + hooks；R2 带 prompt-template + tools + 覆盖性 model-config）+ 1 个引用它们的 `*.agent.xml`（带自身 prompt + tools + chatOptions + 一个 `<param>`）→ 经 `loadAgentModel` 合并 → 经 `AgentSessionLifecycle.buildBaseExecutionContext()` 构建执行上下文（base system message 在此从 `agentModel.getPrompt().getSource()` 构建，`:161-173`）→ 断言：
  - 执行上下文的 base system message（`ChatSystemMessage`）含 R1 + R2 渲染后 prompt 段 + agent prompt（顺序正确）——注意 base prompt 经 recipe 合并流入 `getPrompt()`，**不是**经 `AgentPromptAssembly`（后者仅注入 talent/skill 额外 system message）
  - toolManager 解析到的工具集 = agent.tools ∪ R1.tools ∪ R2.tools（经 `AgentToolPlanResolver.buildToolDefinitions`）
  - ChatOptions 非空字段 = agent 覆盖 R2 覆盖 R1 的逐字段结果
  - hooks registry 含 R1 + R2 + agent 全部 hooks（经 `AgentExecutorResolver` → `DefaultHookRegistry.fromAgentModel`）
- [x] **端到端验证**（Minimum Rules #22）：从 `loadAgentModel`（用户入口）到 `buildBaseExecutionContext` 构建出含 recipe prompt 的 base system message + `AgentToolPlanResolver` 解析 toolDefs（最终输出）的完整路径跑通，recipe 贡献在每个环节可观测。
- [x] **Anti-Hollow 接线验证**（Minimum Rules #23）：recipe prompt 段确实出现在 `buildBaseExecutionContext` 产出的 base `ChatSystemMessage` 中（非仅 RecipeModel 字段存在）；recipe tools 确实经 `toolManager.loadTool` 解析（非仅 csv-set 拼接）；recipe model-config 确实流入 `AgentPromptAssembly.buildChatOptions()` 产出的 ChatOptions。
- [x] **裁定收口**（Decision）：将裁定 A–I 写入 design `nop-ai-agent-dsl.md` §9 为最终状态（移除"低优先/可选"措辞与 Open Questions，替换为已裁定语义；§9 标题从"外部调研驱动的增量设计"改为"Recipe 组合层"最终设计，符合 Minimum Rules #14 design 文档不含叙事性标题）；§9.2 组合语义、§9.4 落地优先级更新为 final。
- [x] design 更新明确 recipe vs skill 边界（recipe = 装配期结构化组合；skill = 运行时能力注入），消除 §9 Open Question。
- [x] roadmap `nop-ai-agent-harness-evolution-roadmap.md` W6-1 `[ ]` → `[x]` 并附收口说明。
- [x] 零回归：`./mvnw test -pl nop-ai/nop-ai-agent -am` 全过（无 recipes 的既有 agent 行为不变）。

Exit Criteria:

- [x] 端到端测试通过：2 recipes + agent 全链路（loadAgentModel → buildBaseExecutionContext → AgentToolPlanResolver → AgentExecutorResolver）跑通，4 类断言（base system message prompt 顺序 / tools 并集 / chatOptions 覆盖 / hooks 并集）全绿
- [x] Anti-Hollow 证据：recipe 贡献在 base `ChatSystemMessage`（prompt）、toolDefs（tools）、ChatOptions（model-config）、hooks registry 中均可观测——非仅模型层存在
- [x] design `nop-ai-agent-dsl.md` §9 已更新为最终状态（裁定 A–I 落地，Open Questions 已收口，无"Proposed vs Current"残留）
- [x] recipe vs skill 边界在 design 中明确
- [x] roadmap W6-1 标记 done + 收口说明
- [x] **无静默跳过**：端到端路径中无空方法体 / 静默 continue / 吞异常
- [x] 缓存安全（裁定 I）验证：合并后原缓存实例未被修改
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am` 通过
- [x] 受影响 owner docs（`docs-for-ai/`）已同步——`No owner-doc update required: recipe 是 nop-ai-agent 内部 DSL 扩展，docs-for-ai 无 agent DSL 用户文档需同步`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] W6-1 recipe 组合层端到端可用：agent 可引用 1..n recipes，合并后 prompt/tools/chatOptions/hooks 流入运行时
- [x] 合并语义（裁定 C–I）全部有 focused test 验证
- [x] 无 recipes 的既有 agent 零回归（fast-path 验证 + `./mvnw test -pl nop-ai/nop-ai-agent -am` 全过）
- [x] 缓存安全（裁定 I）：合并不修改 `ResourceComponentManager` 缓存的共享实例
- [x] 所有配置错误（缺参数 / ref 非法 / hook id 重复 / recipe 不存在）显式 fail-loud，无静默跳过
- [x] design `nop-ai-agent-dsl.md` §9 已为最终状态
- [x] roadmap W6-1 标记 done
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）recipe 合并结果在运行时被 `buildBaseExecutionContext` / `AgentToolPlanResolver` / `AgentExecutorResolver` 消费（不只是 RecipeModel 类型存在），（b）端到端从 loadAgentModel 到 base system message + toolDefs 路径连通，（c）无空方法体 / 静默跳过 / no-op
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am`
- [x] checkstyle / 代码规范检查通过
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 退出码 0

## Deferred But Adjudicated

### recipe 对 middlewares / filter-chain / constraints / team / permissions / path-rules 的合并

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: roadmap W6-1 明确定义 recipe = prompt 模板 + 工具集 + 模型配置 + hooks 四要素。middlewares/filter-chain/constraints 等是更晚引入的字段，roadmap 未纳入 recipe scope。首版四要素即可使"可组合行为单元"端到端成立；其余字段的 recipe 合并是独立增强。
- Successor Required: yes
- Successor Path: recipe 字段扩展 successor（W6-1 完成后，按需扩展）

### recipe 递归组合（recipe 引用 recipe）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 首版 agent → recipe 单层引用即可覆盖"可分享行为配方"用例。递归组合引入环检测与深度限制，是独立复杂度。
- Successor Required: no

### 运行时/动态 recipe 加载与切换

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: recipe 是装配期结构化组合，运行时切换会与 session 一致性冲突。首版装配期合并即可成立。
- Successor Required: no

## Non-Blocking Follow-ups

- recipe prompt-template 的表达式求值（首版仅纯字符串 `{{param}}` 替换；XPL 表达式是增强）
- recipe 版本兼容/迁移策略（首版 `version` 字段仅作记录，不参与合并语义）
- recipe 的可观测性指标（recipe 命中率、合并冲突统计）——observability 增强

## Closure

Status Note: W6-1 recipe 组合层全部落地。recipe.xdef + agent.xdef `<recipes>` + RecipeResolver + loadAgentModel 接线 + 裁定 A–I 全部实现并测试。端到端验证 recipe prompt/tools/chatOptions/hooks 在运行时消费点可观测（Anti-Hollow）。零回归（3379 测试 0 failures）。design §9 final，roadmap W6-1 done（W1–W6 全部完成）。独立 closure audit PASS（CAN CLOSE）。
Completed: 2026-08-02

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（task ses_0404f99dbffeKZG7peJ7lkkddT，fresh session）
- Evidence:
  - **11/11 verification items PASS**：
    1. recipe.xdef valid — `recipe.xdef:6,9,16,18,21,25-29`（prompt-template=string, tools=csv-set, model-config=ref, hooks=bean-class AgentHookModel）
    2. agent.xdef `<recipes>` — `agent.xdef:122-126`（AgentRecipeRefModel + params KeyedList）
    3. recipe.register-model.xml — `:5` xdsl-loader fileType="recipe.xml"
    4. codegen 产物 — `_RecipeModel.java:55`(String promptTemplate), `:32`(KeyedList hooks), `:39`(ChatOptionsModel), `:62`(Set tools); `_AgentModel.java:166`(List recipes)
    5. RecipeResolver — `:97`(resolve), `:101-105`(fast-path same instance), `:108`(cloneInstance), `:112-113`(new collections), `:154-179`(source-string prompt re-parse), `:248-255`(dup hook fail), `:263-304`(chatOptions field override), `:214-231`(param replace + missing fail)
    6. loadAgentModel wired — `AgentSessionSupport.java:114` RecipeResolver.resolve(model)
    7. error codes — `NopAiAgentErrors.java:165,171,177,183`
    8. tests — TestRecipeModelLoading(5) + TestRecipeResolver(17) + TestRecipeEndToEnd(3) = 25 tests, all green
    9. Anti-Hollow — `TestRecipeEndToEnd:72-88`(prompt→ChatSystemMessage), `:125-132`(hooks→DefaultHookRegistry PRE_CALL); RecipeResolver 无空方法体/静默跳过/吞异常
    10. design §9 final — `nop-ai-agent-dsl.md:282` "Recipe 组合层", 裁定 A–I, recipe vs skill 边界, 无"低优先/Open Questions"
    11. roadmap W6-1 done — `roadmap.md:62` [x] + 收口说明
  - `./mvnw test -pl nop-ai/nop-ai-agent -am`：3379 tests, 0 failures, 0 errors（BUILD SUCCESS）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-agent --severity high` 退出码 0（2 findings 均为 pre-existing，非 recipe 代码）
  - Deferred 项分类检查：3 项 deferred 均 `out-of-scope improvement`（middlewares/filter-chain 等合并、递归组合、运行时加载），无 in-scope defect 降级

Follow-up:

- no remaining plan-owned work（W1–W6 全部 completed）
- Non-blocking follow-ups：recipe prompt-template 表达式求值 / version 兼容迁移 / recipe 可观测性指标（均记录于 Non-Blocking Follow-ups）
