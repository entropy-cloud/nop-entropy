# Skill 与 Hook 关系的源码级调研：能否让 capability-unit 声明自己的 hook？

> Status: closed
> Date: 2026-06-17（2026-06-17 扩充 codex + soloncode）
> Scope: `~/ai/{opencode,claude-code,hermes-agent,pi,codex,soloncode}` 六框架源码直读（非二手分析）
> Conclusion: 六框架全部一致——**skill/command/agent/talent 都不能声明自己的 hook**（soloncode 的 Talent 仅有一次性 `onAttach` 初始化回调，非完整生命周期 hook）。Hook 恒居于更高层（plugin/extension/harness/global-config），方向永远是 hook→skill（hook 触发/加载 skill），从不 skill→hook。「bundle 携带 hook」的成熟形态是**平级 contribution**（codex/claude-code 的 plugin manifest、soloncode 的 HarnessExtension），不是 skill 内嵌。

## 一、调研动机

澄清一个设计问题：**「给 Skill 加 Hook 能力」是否可行？是否会降级为命令式注入器？** 此前的调研（`ai-dev/analysis/agent-survey/2026-06-05-*`）基于二手分析文档，结论是"无框架把 hook 嵌套在 skill 内"。本报告直接读源码复核，并提取 frontmatter 解析器 / hook 注册接口 / plugin manifest schema 的精确证据。

设计上下文见 `ai-dev/design/nop-ai-agent/skill-system-design.md` §6.4。

## 二、调研方法

4 个 explore subagent 并行直读源码（不读分析文档），提取：
- skill / command / agent 的 frontmatter schema（解析器实际消费的键）
- hook 的注册接口（声明式配置 vs 命令式代码）
- plugin/extension manifest schema
- 「capability-unit 能否声明 hook」的源码级证据

## 三、逐框架源码级发现

### 3.1 opencode（`~/ai/opencode`，TypeScript）

**结论：skill/agent/command 都不能声明 hook。Hook 是命令式 TS plugin。**

#### Skill
- **schema**（V2，`packages/core/src/skill.ts:49-55`）：`name` / `description?` / `slash?` / `location` / `content`。
- **frontmatter**（`packages/core/src/skill.ts:60-64`）：仅 `name` / `description` / `slash`。
- **frontmatter 守卫**（V1，`packages/opencode/src/skill/index.ts:54-60`）：`isSkillFrontmatter` 只检查 `name` + `description`，其余 YAML 键被 loader 丢弃。
- **文件名**：必须 `SKILL.md`（V2 也接受 `<name>.md`）。

#### Agent
- **schema**（V2 config，`packages/core/src/config/agent.ts:13-25`）：`model?` / `variant?` / `request?` / `system?` / `description?` / `mode?` / `hidden?` / `color?` / `steps?` / `disabled?` / `permissions?`。**无 hooks 键**。
- **已知键白名单**（V2，`packages/core/src/config/plugin/agent.ts:22-34`；V1，`packages/core/src/v1/config/agent.ts:43-60`）：均无 `hooks`。
- **加载**（`packages/opencode/src/config/agent.ts:14-38`）：扫描 `{agent,agents}/**/*.md`，body 成为 prompt。

#### Hook
- **接口**（V1，`packages/plugin/src/index.ts:222-335`）：`Hooks` 对象，**21 个 hook 点**（`dispose` / `event` / `config` / `tool` / `auth` / `provider` / `chat.message` / `chat.params` / `chat.headers` / `permission.ask` / `command.execute.before` / `tool.execute.before` / `shell.env` / `tool.execute.after` / `experimental.chat.messages.transform` / `experimental.chat.system.transform` / `experimental.provider.small_model` / `experimental.session.compacting` / `experimental.compaction.autocontinue` / `experimental.text.complete` / `tool.definition`）。
- **V2**（`packages/core/src/plugin.ts:23-56`）：`HookSpec`，4 个 hook 点（`catalog.transform` / `account.switched` / `aisdk.language` / `aisdk.sdk`）。
- **注册方式**：**命令式 TS**。Plugin 是一个函数 `Plugin = (input, options?) => Promise<Hooks>`（`packages/plugin/src/index.ts:74`），动态 `import` 为 `index.{ts,tsx,js,mjs,cjs}`（`packages/opencode/src/plugin/loader.ts:136-145`）。自动发现的 plugin 只扫描 `*.{ts,js}`（`packages/opencode/src/config/plugin.ts:21`）——**不接受 Markdown plugin**。

#### Plugin ↔ Skill ↔ Agent 关系
- Plugin 不能用 Markdown 编写，必须是 TS/JS 模块。
- V2 plugin **可编程式注册 skill/agent**（`SkillV2.transform` / `AgentV2.transform`，`packages/core/src/plugin/skill.ts:13-33`），但**反向不存在**——没有任何地方让 `SKILL.md` / `agent.md` 携带 hook 定义并自动注册。
- **skill/agent 是数据，plugin 是代码**。数据 schema 无 hook 字段。

#### Command
- **schema**（`packages/opencode/src/command/index.ts:29-41`）：`name` / `description?` / `agent?` / `model?` / `source?` / `template` / `subtask?` / `hints`。**无 hooks 字段**。
- 三个来源：`opencode.json` 的 `command:` 块、MCP prompts、**skill 自动暴露为 slash-command**（`:141-152`，template = skill content）。
- 有一个 plugin 级全局 hook `command.execute.before` 可拦截所有命令，但命令自身不能声明 hook。

#### 穷举式验证
- `grep \bhooks\b` in `packages/opencode/src/{skill,command,agent}` → skill/command 无匹配；agent 仅匹配 prompt 模板里的字面字符串 "React hooks"（无关）。
- 模式 `hooks.*frontmatter|skill.*hook|hook.*skill|agent.*hook|registerHook` 跨全包 → **无匹配**。

### 3.2 claude-code（`~/ai/claude-code`，claw 的 Rust 实现）

> 注：此源码树是 `ultraworkers/claw-code`——Claude Code 的公开 Rust 重实现。源码注释显式对比 claw 与"Claude Code contract"的行为，因此是 Claude Code 实际架构的强证据。

**结论：skill/command/agent 都不能声明 hook。Hook 是声明式 JSON（shell 命令），但恒在 settings.json / plugin manifest 层，从不嵌套进 skill。**

#### Hook
- **事件**（`runtime/src/hooks.rs:21-37`）：**仅 3 个**——`PreToolUse` / `PostToolUse` / `PostToolUseFailure`。
- **不支持的事件**（显式拒绝为未知 / Claude-Code-contract-only）：`UserPromptSubmit` / `SessionStart` / `Stop` / `Notification` / `PreCompact` / `SubagentStop`（`config.rs:3720-3760`；`plugins/src/lib.rs:2713-2729` 测试断言 `SessionStart` "uses the Claude Code lifecycle contract" 被拒）。
- **配置 schema**：声明式 JSON。settings.json 顶层 `hooks` 对象（`config_validate.rs:163-166`），子键仅 `PreToolUse`/`PostToolUse`/`PostToolUseFailure`（`:221-234`），每个是 `HookArray`（字符串或 hook 对象数组）。
- **hook 条目字段**：`type`（必须 `"command"`）/ `command`（必填非空字符串）/ `matcher`（可选 glob，匹配工具名）。解析见 `config.rs:1767-1885`。
- **hook 能做什么**：**始终是一条 shell 命令**（`sh -lc` / `cmd /C`，`runtime/src/hooks.rs:742-758`），stdin 收 JSON payload + 环境变量（`HOOK_EVENT` / `HOOK_TOOL_NAME` / `HOOK_TOOL_INPUT`...，`:435-441`）。退出码协议：0=allow，2=deny，其他=失败（`:449-478`）。stdout 可返回 JSON（`systemMessage` / `reason` / `decision` / `hookSpecificOutput`）。
- **触发时机**：**仅围绕 tool use**（`runtime/src/conversation.rs:235-251,419`）。slash-command / skill / session / prompt 提交都**不触发** hook。
- **声明式 vs 命令式**：**完全声明式 JSON 配置**，加载期解析为 `RuntimeHookConfig`（`config.rs:201-225`）。无用户侧的代码注册 API。

#### Skill
- **存在**：`SKILL.md` 文件，从 `.claw/skills` / `.omc/skills` / `.agents/skills` / `~/.claude/skills/omc-learned` 等根目录发现（`tools/src/lib.rs:3861-3907`；`commands/src/lib.rs:4098-4170`）。
- **schema**（`commands/src/lib.rs:2178-2189` `SkillSummary`）：`name` / `description?` / `source` / `shadowed_by` / `origin` / `path` / `dir_name`。**无 hooks**。
- **frontmatter 解析器**（`commands/src/lib.rs:4217-4246` `parse_skill_frontmatter`）：**只取 `name:` 和 `description:`**，循环里只 strip_prefix 这两个键。**任何其他 frontmatter 键（含假设的 `hooks:`）被静默忽略。**
- **plugin-managed skills 被拒**（`plugins/src/lib.rs:1698-1702`）：plugin manifest 的 `skills` 字段被拒并报错 "uses the Claude Code plugin contract"。

#### Agent / Subagent
- **agent**：`.md`（frontmatter）或 `.toml`，从 agent 根目录加载（`commands/src/lib.rs:4005-4089`）。
- **agent frontmatter 解析器**（`commands/src/lib.rs:4265-4317` `parse_agent_frontmatter`）：**仅 4 键** `name` / `description` / `model` / `model_reasoning_effort`。**无 hooks**。
- **subagent**：运行时由工具 spawn（`tools/src/lib.rs:4111-4167`），取 `subagent_type` / `description` / `prompt`。无 hook 声明机制。
- **plugin-managed agents 被拒**（`plugins/src/lib.rs:1707-1710`）。

#### Command / slash-command
- **全部是编译期硬编码 builtin**（`commands/src/lib.rs:60-1038` `SLASH_COMMAND_SPECS`，`&'static`）。
- `SlashCommandSpec`（`:45-52`）：`name` / `aliases` / `summary` / `argument_hint` / `resume_supported`。**无 hooks**。
- **无 `~/.claude/commands/*.md` 文件式自定义命令加载器**。legacy `commands/*.md` 目录被当作 **skill** 加载（`SkillOrigin::LegacyCommandsDir`，`:4139-4170`）。

#### Plugin（唯一能携带 hook 的用户可安装物）
- **manifest**（`plugins/src/lib.rs:116-132` `PluginManifest`）：`name` / `version` / `description` / `permissions` / `defaultEnabled` / **`hooks: PluginHooks`** / `lifecycle` / `tools` / `commands`。
- `PluginHooks`（`:67-99`）：`PreToolUse` / `PostToolUse` / `PostToolUseFailure`，每个是 `Vec<String>`（命令字符串）。
- **关键**：plugin 的 hooks 合并进**全局** `RuntimeHookConfig`（`rusty-claude-cli/src/main.rs:11930-11935`），按 `matcher` glob 对工具名全局触发（`runtime/src/hooks.rs:1195-1199`）。**无 scope 机制**让 hook 只在某 command/skill 运行时激活——hook 不知道是哪个 slash command / skill 发起的当前 tool call。

### 3.3 hermes-agent（`~/ai/hermes-agent`，Python）

**结论：skill 不能声明 hook。Plugin hook 是命令式 Python。plugin.yaml 的 `hooks:` 块是纯装饰——加载器从不读。**

#### Skill
- **目录**：`skills/<category>/<skill-name>/SKILL.md`（in-repo 80 个）或 `~/.hermes/skills/`。
- **frontmatter 校验器**（`tools/skill_manager_tool.py:217-253` `_validate_frontmatter`）：**只强制 `name` + `description`**（≤1024 字符）。不查找 `hooks` / `lifecycle` 字段。
- **loader 实际消费的 frontmatter 键**（`agent/skill_utils.py`）：
  - 必填：`name` / `description`
  - 可选消费：`platforms`（`:148`）/ `environments`（`:255`，值 `kanban`/`docker`/`s6`）/ `metadata.hermes.{tags, related_skills, fallback_for_toolsets, requires_toolsets, fallback_for_tools, requires_tools, config}`
  - 约定-only（不解析）：`version` / `author` / `license`
  - **无 `hooks` 字段**
- **激活方式**：模型显式调 `skill_view("name")`，或 `--skills` 预加载。**无事件/hook 驱动的激活**。

#### Plugin
- **布局**：plugin = 目录 + `plugin.yaml` + `__init__.py`（含 `register(ctx)` 函数）。
- **manifest schema**（`hermes_cli/plugins.py:235-269` `PluginManifest`；解析 `_parse_manifest` `:1283-1372`）。loader 实际读取的 YAML 键：

  | 字段 | 行 | 类型/说明 |
  |------|----|----------|
  | `name` | 1300 | string |
  | `version` | 1357 | string |
  | `description` | 1358 | string |
  | `author` | 1359 | string |
  | `requires_env` | 1360 | list |
  | `provides_tools` | 1361 | list——声明式提示，**存储但从不用于注册** |
  | `provides_hooks` | 1362 | list——声明式提示，**存储但全代码库零消费者** |
  | `kind` | 1303 | `standalone`/`backend`/`exclusive`/`platform`/`model-provider` |

- **plugin.yaml 的 `hooks:` 块不被解析**：`_parse_manifest`（`:1283-1372`）**从不 `data.get("hooks")`**。例：`plugins/observability/langfuse/plugin.yaml` 有 `hooks:` 块（6 个 hook 名），但纯装饰——该插件实际在 `__init__.py:995-1004` 用 Python `ctx.register_hook(...)` 注册。
- **"3 个 plugin 面"实为 4 种 plugin**（`website/docs/user-guide/features/plugins.md:205-216`）：General / Memory-provider / Context-engine / Model-provider。

#### Hook
- **有效 hook 名集合**（`hermes_cli/plugins.py:128-170` `VALID_HOOKS`）：**19 个**——`pre_tool_call` / `post_tool_call` / `transform_terminal_output` / `transform_tool_result` / `transform_llm_output` / `pre_llm_call` / `post_llm_call` / `pre_api_request` / `post_api_request` / `api_request_error` / `on_session_start` / `on_session_end` / `on_session_finalize` / `on_session_reset` / `subagent_start` / `subagent_stop` / `pre_gateway_dispatch` / `pre_approval_request` / `post_approval_response`。
- **无基类/ABC**：hook 是普通 Python callable，经 `PluginManager.invoke_hook(name, **kwargs)` 派发（`:1537-1572`）。
- **注册方式**：**命令式 Python**。`PluginContext.register_hook(hook_name, callback)`（`:938-953`），在 plugin 的 `register(ctx)` 函数内调用。未知 hook 名产生 warning 但仍存储。
- **三套 hook 系统**（`website/docs/user-guide/features/hooks.md:9-15`）：
  - **Plugin hooks**：`ctx.register_hook()`——CLI + Gateway，命令式 Python。
  - **Gateway hooks**：`HOOK.yaml` + `handler.py`——Gateway only，**声明式 YAML**（`events:` 列表）+ Python handler。这是唯一在 YAML 里声明式订阅事件的系统，但事件名不同（`agent:start` / `session:start` / `gateway:startup`...），且仅 Gateway。
  - **Shell hooks**：`~/.hermes/config.yaml` 的 `hooks:` 块——CLI + Gateway，声明式 YAML + 外部 shell 命令（`agent/shell_hooks.py:175,213`）。

#### Skill ↔ Hook 关系
- **无任何关联**。skill 激活不触发 hook；hook 不加载 skill。
- plugin 可经 `ctx.register_skill(name, path, description)`（`:957-1000`）注册 skill（命名空间 `<plugin>:<skill>`），但**plugin 注册 skill 和 hook 是分开的**——skill 自身仍无 hook。
- 一个 plugin 可以（作者自行实现）在 `pre_llm_call` 里读 SKILL.md 文本并经 `{"context": skill_text}` 注入——但这是 plugin-Python 代码做的，不是 skill 侧声明。无内置 hook 做此事。

### 3.4 pi-agent（`~/ai/pi`，TypeScript，pi-monorepo）

> 注：`~/ai/awesome-pi-agent` 是无关的 Discord scraper；`~/ai/oh-my-pi` 是独立 Rust/TS 项目。**规范仓库是 `~/ai/pi`**（`packages/agent` = `@earendil-works/pi-agent-core`）。

**结论：skill/tool/agent 都不能声明 hook。Hook 命令式（loop config / harness.on / extension pi.on）。**

#### Skill
- **接口**（`packages/agent/src/harness/types.ts:46-57`）：`name` / `description` / `content` / `filePath` / `disableModelInvocation?`。**无 hook 字段**。
- **frontmatter**（`packages/agent/src/harness/skills.ts:30-35`）：`name?` / `description?` / `disable-model-invocation?` + `[key: string]: unknown`（索引签名纯宽容——**全代码库无任何代码读 `frontmatter.hooks`/`before`/`middleware`**）。
- **coding-agent 本地 Skill 类型**（`packages/coding-agent/src/core/skills.ts:74-81`）：镜像，无 hook 字段。
- **加载**：`loadSkills()`（`skills.ts:49-75`）递归找 `SKILL.md`。
- **匹配**：**纯按名**（`AgentHarness.skill(name, ...)`，`agent-harness.ts:645-660`）。
- **激活**：skill 作为 XML 格式化进 system prompt（`formatSkillsForPrompt`，`coding-agent/.../skills.ts:335-361`），模型"加载"skill 即读文件。**无自动激活/注册**。

#### Agent
- **低层 `Agent` 类**（`packages/agent/src/agent.ts:166`）：构造器 `AgentOptions`（`:96-116`）含 `beforeToolCall?` / `afterToolCall?`——**hook 在这里，不在 tool/skill 上**。
- **高层 `AgentHarness`**（`packages/agent/src/harness/agent-harness.ts:174`）：构造器选项 `AgentHarnessOptions`（`types.ts:798-831`）取 `tools` / `resources`（含 skills）/ `systemPrompt` / `model`，**无 hooks**。hook 经构造后的 `.on()` / `.subscribe()` 注册。

#### Tool
- **base `Tool`**（`packages/ai/src/types.ts:338-342`）：`name` / `description` / `parameters`。
- **`AgentTool`**（`packages/agent/src/types.ts:361-384`）：`label` / `prepareArguments?` / `execute` / `executionMode?`。**无 `beforeToolCall`/`afterToolCall` 字段**。
- **`ToolDefinition`**（扩展注册的 tool，`coding-agent/.../extensions/types.ts:433-480`）：加 `promptSnippet` / `promptGuidelines` / `renderShell` / `renderCall` / `renderResult`。**仍无 per-tool hook 字段**。

#### Hook（三套，全命令式）
- **(a) 低层 loop 回调** `AgentLoopConfig`（`packages/agent/src/types.ts:135-277`）：`transformContext` / `shouldStopAfterTurn` / `prepareNextTurn` / `getSteeringMessages` / `getFollowUpMessages` / **`beforeToolCall`（:262）** / **`afterToolCall`（:276）**。命令式函数字段。loop 全局派发（`agent-loop.ts:581-605,676-678`），非 per-tool。
- **(b) AgentHarness 事件系统**（`agent-harness.ts`）：`subscribe(listener)`（`:1038-1048`，全局 `*`）/ `on(type, handler)`（`:1050-1063`，类型化）。事件类型见 `AgentHarnessEventResultMap`（`types.ts:704-724`）：`before_agent_start` / `context` / `before_provider_request` / `before_provider_payload` / `after_provider_response` / `tool_call` / `tool_result` / `session_before_compact` / `session_compact` / `session_before_tree` / `session_tree` + fire-and-forget（`model_update` / `tools_update` / `resources_update` / `queue_update` / `save_point` / `abort` / `settled`）。harness 在 `createLoopConfig()`（`:421-470`）把订阅翻译成 loop 的 `beforeToolCall`/`afterToolCall`。
- **(c) Extension**（coding-agent 层，`coding-agent/.../extensions/types.ts:1093-1320`）：`ExtensionAPI` 暴露 `on(...)` 重载 + `registerTool` / `registerCommand` / `registerShortcut` / `registerFlag` / ... 。Extension 是 TS 文件导出 factory `ExtensionFactory = (pi: ExtensionAPI) => void | Promise<void>`（`:1388`），factory 体**命令式**调 `pi.on("tool_call", handler)`。

#### Skill ↔ Hook 关系
- **无**。`Skill` 类型零 hook 字段；loader 从不调 `harness.on()` / `subscribe()` / `beforeToolCall`。skill 唯一消费者是 `AgentHarness.skill()`（`:645-660`）格式化为 prompt。
- 唯一间接交汇：extension 的 `resources_discover` handler（`types.ts:502-513`）可返回额外 skill 路径让框架扫描——是 extension 告诉框架去哪找 skill，**不是 skill 声明 hook**。

### 3.5 codex（`~/ai/codex`，OpenAI Codex CLI，Rust + TS 壳）

**结论：skill/agent/tool 都不能声明 hook。有两套独立 hook 系统。Plugin manifest 携带 hooks 与 skills 为平级字段（声明式）。**

#### Skill（存在）
- **文件**：`SKILL.md`（`codex-rs/core-skills/src/loader.rs:107`）+ 可选 sidecar `agents/openai.yaml`（`:108-110`）。
- **frontmatter**（`loader.rs:39-53`）：仅 `name` / `description` / `metadata.short-description`。sidecar schema（`:55-105`）：`interface`（display_name/icon/brand_color/default_prompt）+ `dependencies.tools[]`（MCP 依赖）+ `policy`。
- **grep `hook|Hook` in `core-skills/src/` → 零匹配**。**skill 无 hooks 字段**。
- **激活**：经 mention（`skill://` 前缀 / `SKILL.md` 路径 / 名字查找）注入为 prompt 片段（`ext/skills/src/selection.rs`；`core-skills/src/injection.rs:57-112`）。

#### Hook（两套独立系统）
- **System A — 声明式 ClaudeHooks**（`codex-rs/hooks/`）：JSON/TOML 配置，**10 事件**（`hooks/src/lib.rs:19-30`）：`PreToolUse`/`PermissionRequest`/`PostToolUse`/`PreCompact`/`PostCompact`/`SessionStart`/`UserPromptSubmit`/`SubagentStart`/`SubagentStop`/`Stop`。`HookHandlerConfig` 三种 handler（`config/src/hook_config.rs:137-156`）：`Command`（shell）/ `Prompt` / `Agent`（后两者为 stub）。加载为 `HooksConfig`（`hooks/src/registry.rs:29-82`），默认 `hooks/hooks.json`。
- **System B — 命令式 Rust traits**（`codex-rs/ext/extension-api/`）：编译期注册，无外部动态代码。`ToolLifecycleContributor`（`contributors.rs:173-183`，`on_tool_start`/`on_tool_finish`）/ `ThreadLifecycleContributor`（`on_thread_start`/`on_thread_resume`/`on_thread_idle`/`on_thread_stop`）/ `TurnLifecycleContributor` / `ContextContributor` / `ToolContributor` 等，经 `ExtensionRegistryBuilder` 注册（`registry.rs:55-130`）。
- **关键区分**（源码注释 `contributors.rs:172`）："Use `ToolContributor` for owning a tool implementation and **hooks** for policy that needs tool payloads"——即「hooks」=声明式 ClaudeHooks（能看 tool payload），`ToolLifecycleContributor` 是轻量观察者。

#### Plugin（唯一把 hooks 与 skills 平级捆绑的物）
- **manifest**（`codex-rs/core-plugins/src/manifest.rs:12-59`）：`plugin.json`，**平级字段** `name`/`version`/`description`/`keywords`/`skills`/`mcpServers`/`apps`/`hooks`/`interface`。
- `PluginManifestPaths`（`:47-53`）：`skills` / `mcp_servers` / `apps` / `hooks` 平级。`PluginManifestHooks`（`:55-59`）：`Paths(Vec)` 或 `Inline(Vec<HooksFile>)`（**允许 inline hook 定义**）。
- 运行时 `LoadedPlugin`（`plugin/src/load_outcome.rs:14-29`）：`skill_roots` / `mcp_servers` / `apps` / `hook_sources` 平级持有。
- **加载**（`core-plugins/src/loader.rs:884-941` `load_plugin_hooks`）：从 manifest `hooks` 或默认 `hooks/hooks.json` 读取声明式 JSON。
- **关键**：plugin 把 skills 和 hooks 一起捆绑，但流入**不相交子系统**——skills → `SkillsManager`，hooks → `HooksConfig.plugin_hook_sources` → `ClaudeHooksEngine`。

#### Skill ↔ Hook 关系
- **无直接关联**。skill 不声明 hook；skills 扩展（`ext/skills/src/extension.rs:249-267`）只注册 `thread_lifecycle_contributor`/`config_contributor`/`prompt_contributor`/`turn_input_contributor`，**不碰** `HooksConfig`/`tool_lifecycle_contributor`。
- 唯一共享在 **Plugin 层**：一个 `plugin.json` 平级声明 `skills` + `hooks`。

### 3.6 soloncode（`~/ai/soloncode` + 上游 `~/ai/solon-ai`，Java）

**结论：skill 是纯 Markdown 文档。Talent 仅有一个一次性 `onAttach` 初始化回调，非完整生命周期 hook。无 Talent 同时实现 ReActInterceptor。HarnessExtension 把 talent/tool/interceptor 作为平级能力捆绑（命令式）。**

#### Skill（存在，纯文档）
- **定义**：目录含 `SKILL.md`（`solon-ai-talent-mount` 的 `MountManager.java:262-264` `isSkillDir`）。
- **加载**：`MountManager.scanSkillAndCache`（`:236-259`）扫文件树，`parseDescription`（`:299-320`）取 frontmatter。
- **`SkillDir`**（`SkillDir.java:26-39`）：纯数据类（name/mountAlias/aliasPath/realPath/description）。**零可执行代码、零 hook**。
- **surfaced**：经 `SkillTalent`（`solon-ai-talent-cli`，extends `AbsTalent`）暴露 4 个工具 `skilllist`/`skillsearch`/`skillread`/`skillrefresh`（`SkillTalent.java:134,156,179,190`）。三级自适应模式（`:81-110`）。

#### Talent（Java 能力单元，最接近 skill 的代码物）
- **接口**（`solon-ai-core` 的 `Talent.java:35-95`，全 default）：`isEnabled`（:39）/ `name`（:46）/ `description`（:53）/ `metadata`（:60）/ `isSupported`（:70，准入）/ **`onAttach`（:78，挂载钩子）** / `getInstruction`（:85）/ `getTools`（:92）。
- **`onAttach` 是 Talent 唯一的生命周期回调**——一次性，在 reasoning 开始前触发（`TalentUtil.activeTalents:58` ← `ReActTrace.activeTalents():191` ← `ReActAgent.call:226`）。语义是初始化（"初始化会话状态、审计日志、上下文预处理"，`Talent.java:75-77`）。
- **38 个 Talent 实现**（TerminalTalent/LspTalent/CodeTalent/SkillTalent/Text2SqlTalent/...）。

#### Hook / ReActInterceptor（生命周期面）
- **`ReActInterceptor`**（`solon-ai-agent` 的 `ReActInterceptor.java:36-103`，extends `AgentInterceptor` + `ChatInterceptor`）：**8 个 ReAct 生命周期** `onAgentStart`（:41）/ `onReasonStart`（:48）/ `onReasonEnd`（:54）/ `onPlan`（:61）/ `onThought`（:73）/ `onAction`（:80）/ `onObservation`（:93）/ `onAgentEnd`（:102）+ **3 个 chat/tool 链**（`ChatInterceptor` 的 `onPrepare`/`interceptCall`/`interceptStream` + `ToolInterceptor` 的 `interceptTool`）。
- **仅 5 个实现**（`solon-ai-agent/.../react/intercept/`）：ToolSanitizerInterceptor / ToolRetryInterceptor / StopLoopInterceptor / HITLInterceptor / ContextCompressionInterceptor。
- **Talent 与 ReActInterceptor 实现集完全不相交**——**无 Talent 同时是 ReActInterceptor**（38 vs 5，零交集）。
- **不同注册表**（`ModelOptionsAmend.java:54-55`）：`talents`（按 `name()` 键）vs `interceptors`（按 `getClass()` 键）。
- **不同派发时机**：Talent 一次性激活注入工具/指令；Interceptor 在每个 ReAct 阶段边界 + 每次 chat/tool 调用反复派发。

#### HarnessExtension（bundle 点）
- **接口**（`solon-ai-harness` 的 `HarnessExtension.java:28-33`）：`void configure(String agentName, ReActAgent.Builder agentBuilder)`。
- **Builder 三个平级方法**（`ReActAgent.java`）：`defaultTalentAdd`（:453）/ `defaultToolAdd`（:463）/ `defaultInterceptorAdd`（:494）——一个 extension 可混合注册三者。
- **调用**（`AgentFactory.java:107-109`）：每个 agent 构建时遍历所有 extension 调 `configure`。
- **例**：`ConfigExtension`（`soloncode-cli/.../config/ConfigExtension.java:23-28`）加 Talent；`Extension1`（`examples/extension_demo/.../Extension1.java:20-42`）加 Interceptor。
- **注册**：命令式（`engine.addExtension`，`HarnessEngine.java:658`）或 Solon Plugin 自动发现（`META-INF/solon/*.properties`，`Configurator.java:200-205` `subBeansOfType(HarnessExtension.class)`）。

#### 关键发现：声明式 hooks 字段被预留但未实现
- `AgentDefinition.java:211-212`：`// Hooks 配置（暂不解析，保留字段） private Object hooks;`——**soloncode 曾考虑声明式 hooks 配置，但显式 deferred**。

#### Tool
- `@ToolMapping`（注解，`solon-ai-core` 的 `ToolMapping.java:29-31`，方法级）/ `FunctionTool`（接口，`FunctionTool.java:31-187`）。**无 per-tool hook**（仅 `handle(args)`）。跨工具拦截经全局 `ToolInterceptor.interceptTool(req, chain)`。

## 四、跨框架对比表

| 维度 | opencode | claude-code | hermes-agent | pi-agent | codex | soloncode |
|------|----------|------------|--------------|----------|-------|-----------|
| **skill 能声明 hook？** | ❌ | ❌ | ❌ | ❌ | ❌ | ❌（skill 是纯 Markdown） |
| **agent/profile 能声明 hook？** | ❌ | ❌（4 键） | n/a（代码类） | ❌（构造器） | ❌（profile 无 hooks） | n/a（Talent 有 `onAttach` 一次性初始化，非完整生命周期） |
| **command 能声明 hook？** | ❌ | ❌（builtin） | n/a | ❌ | n/a（无自定义命令） | n/a |
| **tool 有 per-unit hook？** | n/a | n/a | n/a | ❌（全局 loop） | ❌（`ToolSpec` 无 hook；声明式 matcher scope 到工具名但属 hooks 配置） | ❌（仅 `handle`；全局 `ToolInterceptor`） |
| **hook 形态** | 命令式 TS | **声明式 JSON**（shell） | 命令式 Python（plugin.yaml `hooks:` 装饰） | 命令式（loop/harness/ext） | **双系统**：声明式 JSON（ClaudeHooks，10 事件）+ 命令式 Rust trait | 命令式 Java（Builder.add）；**声明式 hooks 字段预留但未实现**（`AgentDefinition.java:211`） |
| **hook 数量** | 21+4 | 3 | 19 | ~17+ext | 10（声明式）+~6 trait（命令式） | 8 ReAct + 3 chat/tool = 11 |
| **谁携带 hook？** | plugin（TS） | settings.json / **plugin manifest**（全局） | plugin（Python） | harness owner / ext | settings.json / **plugin manifest**（全局合并） | **HarnessExtension**（命令式 bundle 点） |
| **bundle 内 hook 与 skill 关系** | 分离 | **平级**（manifest） | 分离 | 分离 | **平级**（manifest `skills`∥`hooks`） | **平级**（`configure` 三方法 `defaultTalentAdd`∥`defaultToolAdd`∥`defaultInterceptorAdd`） |
| **skill frontmatter 键** | `name`/`description`/`slash` | `name`/`description`（丢弃其余） | `name`/`description`/`platforms`/`environments`/`metadata.*` | `name`/`description`/`disable-model-invocation` | `name`/`description`/`metadata.short-description`（+ sidecar `openai.yaml`：interface/dependencies/policy） | `name`/`description`（frontmatter 取 description） |
| **方向** | hook→skill | hook→skill（全局） | 无关联 | 无关联 | 无关联（共享仅在 plugin 层） | hook/talent 分离（零交集） |

## 五、三个关键洞见（源码揭示，二手分析未点透）

### 5.1「声明式 hook 配置」存在，但恒在更高层，不嵌套进 skill

claude-code 的 hook 是**声明式 JSON**（shell 命令，settings.json）；hermes 的 plugin.yaml 有 `hooks:` 块——但**前者在 settings.json / plugin manifest 层，后者是装饰性**（加载器从不读，`plugins.py:1283-1372` 不取 `data.get("hooks")`）。**没有任何框架把 hook 声明放进 skill 数据模型。** 即使是声明式能力最强的 claude-code，也把 hook 留在 plugin manifest（与 skill 平级）而非 skill 内嵌。

### 5.2「bundle 携带 hook」的成熟形态是平级 contribution，不是 skill 内嵌

**源码级证据（三个独立 plugin manifest + 一个 extension bundle，全部平级）：**
- **codex `PluginManifest`**（`core-plugins/src/manifest.rs:47-59`）：`skills` / `mcp_servers` / `apps` / `hooks` **平级字段**；`PluginManifestHooks` 支持 `Inline(Vec<HooksFile>)`（允许 inline hook 定义）；`LoadedPlugin` 运行时平级持有 `skill_roots`/`hook_sources`（`plugin/src/load_outcome.rs:14-29`）。
- **claude-code `PluginManifest`**（`plugins/src/lib.rs:116-132`）：`hooks` / `commands` / `tools` / `lifecycle` **平级字段**，合并进全局 tool-use hook 集。
- **PilotDeck plugin manifest**（二手分析 `pilotdeck-analysis.md:152-166`）：`hooks?` / `skills?` / `commands?` / `agents?` 平级 + 5 种 Hook 执行器（Agent/Callback/Command-shell/HTTP/Prompt）。
- **soloncode `HarnessExtension`**（`HarnessExtension.java:32`）：命令式 bundle 点，`configure(agentName, builder)` 暴露 `defaultTalentAdd`/`defaultToolAdd`/`defaultInterceptorAdd` **三个平级方法**——一个 extension 可混合注册 talent/tool/interceptor。
- **Nop `IContributionRegistry`**（`nop-ai-agent-hook-skill-engine.md` §8）：HOOK 是 7 种 contribution 之一（HOOK/PROMPT/TOOL/COMMAND/MCP_SERVER/PERMISSION_RULE/ROUTER），与其他 contribution **平级**。**Nop 已采用此成熟模式，与 codex/claude-code/PilotDeck 的 plugin manifest 平级模式一致。**

### 5.3「静默丢弃 / 预留未实现」是刻意 schema 收敛，非文档遗漏

- **claude-code** `parse_skill_frontmatter`（`commands/src/lib.rs:4217-4246`）：循环只 `strip_prefix("name:")` / `strip_prefix("description:")`，其余键不处理。
- **codex** `SkillFrontmatter`（`core-skills/src/loader.rs:39-53`）：serde 仅取 `name`/`description`/`metadata.short-description`；grep `hook|Hook` in `core-skills/src/` → **零匹配**。
- **hermes** `_validate_frontmatter`（`tools/skill_manager_tool.py:217-253`）：只强制 `name` + `description`。
- **opencode** `isSkillFrontmatter`（`packages/opencode/src/skill/index.ts:54-60`）：只检查 `name` + `description`。
- **pi-agent** frontmatter 索引签名 `[key: string]: unknown`（`skills.ts:30-35`）虽宽容，但全代码库无消费者读非标键。
- **soloncode**（关键反证）：`AgentDefinition.java:211-212` 显式预留 `// Hooks 配置（暂不解析，保留字段） private Object hooks;`——**soloncode 考虑过声明式 hooks 但主动 deferred**，证明这是设计选择而非遗漏。

→ 六框架一致：「skill 不携带 hook」是**设计意图**（schema 刻意收敛或主动 defer），不是"还没实现"。

## 六、对 Nop 设计的启示

### 6.1 技术可行性不受影响

源码证据**不否定** `skill-system-design.md` §6.4.1/§6.4.2 的技术结论：
- xpl 的 `xpl-fn:` 把声明式 XML body 编译为 `IEvalFunction` POJO 字段，Skill 持有它仍保持声明式（form A 不降级）。
- `agent.xdef:43`（`<hooks><on event="...">xpl-fn:(event,agentRt)=>void`）、`state-machine.xdef:73-81`（on-entry/on-exit）、`batch.xdef:55-70`（12 种 listener）都是 1:1 先例。

### 6.2 但业界一致选「skill=纯数据 + hook=平级 contribution」分层

- 四框架源码一致证明：skill/command/agent 都不声明 hook。
- 「bundle 携带 hook」成熟形态是平级 contribution（claude-code/PilotDeck），Nop `IContributionRegistry` 已踩在此共识线上。
- **Nop 若走 skill 内嵌 xpl hook，是无先例的原创设计**——技术上可行，但有举证责任（需证明比已有的 `IContributionRegistry` 平级模式更好，例如：为什么 hook 要 scope 到特定 skill 而非全局/plugin 级）。

### 6.3 对 Talent 定位的影响（与 §6.4.4 一致）

xpl 抹平「声明式 vs 命令式」对立——Skill 用 `xpl-predicate` 表达准入、`xpl-fn`/`xpl` 表达动态 instruction/tools，long-term 可覆盖 Talent 大部分用例。但这是 Nop 独有的 xpl 能力，**业界无此先例**（其他框架的 skill 恒是纯数据，动态逻辑只能在 plugin/extension 代码里）。Talent 作为已交付的 Java SPI 程序化外壳，phase-1 保留，phase-2+ 评估 Skill+xpl 收敛路径。

## 七、References（源码路径）

所有路径基于 `~/ai/<framework>/`：

**opencode**:
- `packages/core/src/skill.ts:30-64`（skill schema/frontmatter）
- `packages/core/src/config/agent.ts:13-25`（agent config schema）
- `packages/core/src/config/plugin/agent.ts:22-34`（agent 已知键白名单）
- `packages/plugin/src/index.ts:74,222-335`（Plugin 类型 + Hooks 接口 21 hook）
- `packages/core/src/plugin.ts:23-56`（V2 HookSpec 4 hook）
- `packages/opencode/src/plugin/loader.ts:136-145`（plugin 动态 import）
- `packages/opencode/src/command/index.ts:29-41,141-152`（command schema + skill→command）

**claude-code**:
- `rust/crates/runtime/src/hooks.rs:21-37,435-441,449-478,742-758`（HookEvent + 执行协议）
- `rust/crates/runtime/src/config.rs:201-225,1741-1743,1767-1885`（hook config schema + 解析）
- `rust/crates/runtime/src/config_validate.rs:163-166,221-234`（hooks 字段规格）
- `rust/crates/commands/src/lib.rs:45-52,2178-2189,4217-4246,4265-4317`（SlashCommandSpec + SkillSummary + skill/agent frontmatter 解析器）
- `rust/crates/plugins/src/lib.rs:67-99,116-132,1698-1710`（PluginHooks + PluginManifest + 拒绝 plugin-managed skills/agents）
- `rusty-claude-cli/src/main.rs:11930-11935`（plugin hooks 全局合并）

**hermes-agent**:
- `tools/skill_manager_tool.py:111-112,164-168,217-253`（skill 校验常量 + frontmatter 校验器）
- `agent/skill_utils.py:88-122,128-169,233-269,441-517`（frontmatter 解析 + 平台/环境/条件匹配）
- `hermes_cli/plugins.py:128-170,235-269,938-953,1283-1372,1408-1431,1537-1572`（VALID_HOOKS + PluginManifest + register_hook + _parse_manifest + 加载 + invoke_hook）
- `plugins/observability/langfuse/plugin.yaml:1-14` + `__init__.py:995-1004`（plugin.yaml `hooks:` 装饰 vs 实际 Python 注册的对照）
- `website/docs/user-guide/features/hooks.md:9-15`（三套 hook 系统）
- `website/docs/user-guide/features/plugins.md:205-216`（四种 plugin）

**pi-agent**:
- `packages/agent/src/harness/types.ts:46-57,704-724,798-831`（Skill 接口 + 事件类型 + AgentHarnessOptions）
- `packages/agent/src/harness/skills.ts:30-35,49-75,259-275,281-301`（frontmatter + 加载 + 校验）
- `packages/agent/src/harness/agent-harness.ts:174,421-470,645-660,1038-1063`（AgentHarness + createLoopConfig + skill() + on/subscribe）
- `packages/agent/src/agent.ts:96-116,166-210`（Agent + AgentOptions，beforeToolCall/afterToolCall 在此）
- `packages/agent/src/types.ts:262,276,361-384`（loop config hook + AgentTool 无 per-tool hook）
- `packages/agent/src/agent-loop.ts:581-605,676-678`（hook 全局派发）
- `packages/coding-agent/src/core/extensions/types.ts:433-480,1093-1320,1388`（ToolDefinition + ExtensionAPI + ExtensionFactory）

**codex**:
- `codex-rs/core-skills/src/loader.rs:39-53,107-110,483-629`（SkillFrontmatter + SKILLS_FILENAME + sidecar openai.yaml schema + 发现）
- `codex-rs/core-skills/src/model.rs:13-25`（SkillMetadata，无 hooks）
- `codex-rs/core-skills/src/injection.rs:57-112`（skill prompt 注入）
- `codex-rs/hooks/src/lib.rs:19-30`（10 个 HOOK_EVENT_NAMES）
- `codex-rs/hooks/src/registry.rs:29-82,94-205`（HooksConfig + ClaudeHooksEngine 派发）
- `codex-rs/config/src/hook_config.rs:11-156`（HooksFile + HookEventsToml + MatcherGroup + HookHandlerConfig Command/Prompt/Agent）
- `codex-rs/ext/extension-api/src/contributors.rs:65-183`（ThreadLifecycle/TurnLifecycle/ToolLifecycleContributor）
- `codex-rs/ext/extension-api/src/registry.rs:55-130`（ExtensionRegistryBuilder 命令式注册）
- `codex-rs/ext/skills/src/extension.rs:249-267`（skills 扩展注册的 contributor，不碰 HooksConfig）
- `codex-rs/core-plugins/src/manifest.rs:12-59`（RawPluginManifest + PluginManifestPaths：skills/mcpServers/apps/hooks 平级 + PluginManifestHooks Inline）
- `codex-rs/core-plugins/src/loader.rs:884-941`（load_plugin_hooks）
- `codex-rs/plugin/src/load_outcome.rs:14-29`（LoadedPlugin 平级持有 skill_roots/hook_sources）
- `codex-rs/tools/src/tool_spec.rs:15-51`（ToolSpec 无 hooks）

**soloncode**（+ 上游 `~/ai/solon-ai`）:
- `solon-ai/solon-ai-core/src/main/java/org/noear/solon/ai/chat/talent/Talent.java:35-95`（Talent 接口 8 方法，onAttach:78 是唯一生命周期回调）
- `solon-ai/solon-ai-core/src/main/java/org/noear/solon/ai/chat/talent/TalentUtil.java:41-72`（activeTalents 唯一调 onAttach）
- `solon-ai/solon-ai-agent/src/main/java/org/noear/solon/ai/agent/react/ReActInterceptor.java:36-103`（8 ReAct 生命周期 hook）
- `solon-ai/solon-ai-agent/src/main/java/org/noear/solon/ai/agent/react/ReActAgent.java:226,238,298,453,463,494`（call 派发 + Builder 三平级方法 defaultTalentAdd/defaultToolAdd/defaultInterceptorAdd）
- `solon-ai/solon-ai-agent/src/main/java/org/noear/solon/ai/agent/react/intercept/*`（5 个 ReActInterceptor 实现，与 38 Talent 实现零交集）
- `solon-ai/solon-ai-harness/src/main/java/org/noear/solon/ai/harness/HarnessExtension.java:28-33`（configure bundle 接口）
- `solon-ai/solon-ai-harness/src/main/java/org/noear/solon/ai/harness/agent/AgentFactory.java:107-109`（每 agent 构建调 extension.configure）
- `solon-ai/solon-ai-harness/src/main/java/org/noear/solon/ai/harness/agent/AgentDefinition.java:211-212`（**声明式 hooks 字段预留但未实现** `// 暂不解析，保留字段`）
- `solon-ai/solon-ai-talents/solon-ai-talent-mount/src/main/java/.../MountManager.java:236-264`（skill 扫描 + isSkillDir）
- `solon-ai/solon-ai-talents/solon-ai-talent-mount/src/main/java/.../SkillDir.java:26-39`（skill 纯数据类）
- `solon-ai/solon-ai-talents/solon-ai-talent-cli/src/main/java/.../SkillTalent.java:42,81-110,134,156,179,190`（SkillTalent 暴露 4 skill 工具）
- `soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/Configurator.java:195,200-205`（subBeansOfType(HarnessExtension) 桥接）
- `soloncode/examples/extension_demo/.../Extension1.java:20-42`（HarnessExtension 加 Interceptor 示例）
