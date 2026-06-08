# SolonCode 技术分析

> Status: open
> > Date: 2026-06-08
> > Scope: ~/ai/soloncode — Solon AI 生态下的 AI Coding Agent 产品
> > Conclusion:

## Context

- SolonCode 是基于 Solon AI 的开源 AI 编码助手，定位为 Claude Code / OpenCode 的 Java 替代品
- 100% 开源、Java 实现（支持 Java 8~26），Provider 无关，同时支持 CLI / Web / Desktop / IM 多前端
- 对 Nop 意义：同为 Java 生态非 Spring 框架，是 Java AI Agent 产品化的典型案例

## Analysis

### 项目定位

- **组织**: org.noear (OpenSolon)
- **许可**: Apache 2.0
- **版本**: v2026.6.8
- **Java 范围**: JDK 8 ~ JDK 26（与 Solon AI 一致，业界最宽）
- **核心依赖**: solon-ai-harness (agent runtime), solon-ai-acp (ACP 协议), solon-ai-talent-memory (长期记忆)
- **Web 容器**: Jetty (embedded, solon-server-jetty) + WebSocket
- **CLI 库**: JLine 3 (终端交互) + JAnsi (ANSI 颜色)
- **响应式**: Project Reactor (reactor-core)
- **JSON**: snack4 (Solon 生态内建 JSON 库)
- **LOC**: ~74 Java 文件（soloncode-cli 模块），核心逻辑大量委托上游 solon-ai-harness
- **定位**: 全栈 AI Coding Agent 产品（CLI + Web + Desktop + IM 多通道）

### 代码结构

```
soloncode/
├── soloncode-parent/              # Maven parent POM (依赖版本管理)
├── soloncode-cli/                 # 主模块 (~74 Java files, 可执行 JAR)
│   └── src/main/java/org/noear/solon/codecli/
│       ├── App.java               # 入口（Solon.start，flag 分发到 5 种模式）
│       ├── Configurator.java      # @Configuration IoC 配置（397 行，最核心文件）
│       │                          #   - 构建 HarnessEngine
│       │                          #   - 注册 LSP/MCP/API/Model
│       │                          #   - 启动各 Portal（CLI/Web/Desktop/ACP）
│       ├── config/                # 配置层
│       │   ├── AgentProperties.java   # 主配置（config.yml 绑定，307 行）
│       │   ├── AgentSettings.java     # settings.json 持久化（381 行）
│       │   ├── AgentFlags.java        # 版本/更新检测/运行 flag 常量
│       │   ├── GeneralSettings.java   # 通用设置（窗口/沙盒/重试/记忆）
│       │   ├── ConfigExtension.java   # Harness 扩展点（注入 ConfigTalent）
│       │   ├── ConfigTalent.java      # AI 运行时动态配置 Talent（153 行）
│       │   └── entity/                # 配置实体（ModelDo, McpServerDo, ApiSourceDo, LspServerDo, MountDo）
│       ├── portal/                # 多门户层
│       │   ├── cli/
│       │   │   ├── CliShell.java       # CLI 主循环（701 行）
│       │   │   └── CliCompleter.java   # Tab 补全
│       │   ├── web/
│       │   │   ├── WebController.java  # HTTP REST 控制器（792 行）
│       │   │   ├── WebGate.java        # WebSocket 网关（590 行）
│       │   │   ├── WebStreamBuilder.java # 流式响应构建器（398 行）
│       │   │   ├── WebChunk.java       # 消息块 DTO（278 行）
│       │   │   ├── WebChannel.java     # Web + IM 通道组合
│       │   │   ├── WebSettingsController.java # 设置控制器
│       │   │   ├── GitService.java     # Git 业务逻辑（557 行）
│       │   │   ├── FileService.java    # 文件浏览服务
│       │   │   └── market/             # 技能市场（ClawHub, SkillHub）
│       │   ├── desktop/
│       │   │   ├── WsGate.java         # Desktop WebSocket 网关（688 行）
│       │   │   ├── WsController.java   # Desktop HTTP 控制器
│       │   │   ├── WsMessage.java      # 消息 DTO
│       │   │   └── provider/           # ModelProvider（Ollama, OpenAI, ZhiPu）
│       │   ├── acp/
│       │   │   └── AcpLink.java        # ACP 协议适配器（313 行）
│       │   └── WorkspaceWatcher.java   # 文件变化监听（216 行）
│       ├── channel/               # IM 通道层
│       │   ├── Channel.java           # 统一接口（41 行）
│       │   ├── wechat/                # 微信（Client + CredentialStore + Link）
│       │   ├── feishu/                # 飞书（Client + CredentialStore + Link + PbCodec）
│       │   └── dingtalk/              # 钉钉（Client + CredentialStore + Link）
│       ├── command/               # 命令系统
│       │   ├── CliCommandContext.java  # CLI 命令上下文（107 行）
│       │   ├── WebCommandContext.java  # Web 命令上下文（111 行）
│       │   └── builtin/               # 内置命令
│       │       ├── ExitCommand.java
│       │       ├── ClearCommand.java
│       │       ├── ResumeCommand.java
│       │       ├── RewindCommand.java
│       │       ├── ModelCommand.java
│       │       ├── LoopCommand.java     # /loop 定时任务（249 行）
│       │       ├── LoopScheduler.java   # 调度管理器（374 行）
│       │       └── LoopTask.java        # 任务模型（225 行）
│       └── memory/
│           └── MemoryFactory.java      # 记忆工厂（29 行）
└── examples/
    └── extension_demo/                 # HarnessExtension 扩展示例
```

### 运行模式

SolonCode 通过命令行 flag 进入不同运行模式（`App.java` 分发，`Configurator.init()` 启动）：

| 模式 | 命令 | 入口类 | 网络协议 | 说明 |
|------|------|--------|----------|------|
| **CLI** | `soloncode` | `CliShell` | 无 | 默认模式，JLine 终端交互 |
| **Web** | `soloncode web [port]` | `WebController` + `WebGate` | HTTP + WebSocket | 浏览器交互，自动打开浏览器 |
| **Serve** | `soloncode serve` | `WsController` + `WebChannel` | HTTP + WebSocket + IM | 全功能服务模式（含微信/飞书/钉钉） |
| **ACP** | `soloncode acp` | `AcpLink` | stdio 或 WebSocket | Agent Client Protocol 标准化通信 |
| **Run** | `soloncode run 'prompt'` | `CliShell.call()` | 无 | 单次任务执行，完成后退出 |
| **Version** | `soloncode version` | — | 无 | 打印版本号 |

### 核心模块详解

#### 1. HarnessEngine（核心引擎，上游 solon-ai-harness 提供）

所有门户共享同一个 `HarnessEngine` 实例，由 `Configurator.agentRuntime()` 方法构建（~130 行）：

```java
HarnessEngine engine = HarnessEngine.of(workspace, harnessHome)
    .userAgent(userAgent)
    .systemPrompt(agentsMd)          // AGENTS.md 作为 system prompt
    .maxTurns(maxTurns)
    .autoRethink(autoRethink)        // 自动反思
    .sessionWindowSize(sessionWindowSize)
    .compressionThreshold(summaryWindowSize, summaryWindowToken)
    .compressionModel(summaryModel)  // 压缩用模型（可与对话模型不同）
    .memoryEnabled(memoryEnabled)    // 跨会话记忆
    .memorySolution(new MemoryFactory(agentProps))
    .sandboxEnabled(sandboxMode)     // 文件系统沙盒
    .bashAsyncEnabled(bashAsyncEnabled)
    .subagentEnabled(subagentEnabled)
    .hitlEnabled(hitlEnabled)        // Human-in-the-loop
    .apiRetries(3).modelRetries(3).mcpRetries(3)
    .build();
```

**HarnessEngine 来自上游 solon-ai-harness 模块**，提供：
- `HarnessEngine.of(workspace, home)` — Builder 模式
- `.prompt(input).session(session).stream()` — 响应式 ReAct Agent 流
- `.getSession(id)` — 文件持久化会话管理
- `.getModelOrMain(name)` / `.getAgentOrMain(name)` — 动态模型/Agent 路由
- `.addModel()` / `.addMcpServer()` / `.addLspServer()` / `.addApiServer()` — 运行时能力注册
- `.getCommandRegistry()` — 命令注册表
- `.getAgentManager()` — 子 Agent 管理
- `.getSkills()` — 技能列表
- `.getLspTalent()` — LSP Talent 管理

**HarnessExtension 扩展点**：
```java
public interface HarnessExtension {
    void configure(String agentName, ReActAgent.Builder agentBuilder);
}
```
用户可通过 `solon.extend` 目录放入扩展 JAR，自动被 Solon IoC 扫描并注册。

**关键设计决策**：
- **AGENTS.md 作为 system prompt**：类似 Claude Code 的 CLAUDE.md，用户可通过纯 Markdown 文件定制 Agent 行为。支持 1 万字符上限。
- **Session 持久化**：`FileAgentSession` 将对话存储在 `.soloncode/sessions/<sessionId>/` 下，ndjson 格式。
- **上下文压缩**：`compressionThreshold(summaryWindowSize, summaryWindowToken)` + `compressionModel`，达到消息数或 token 数阈值后用 LLM 自动总结历史。
- **工具权限控制**：`toolsAdd()` + `disallowedToolsAdd()` 双向白/黑名单。
- **沙盒模式**：`sandboxEnabled` + `sandboxAllowUserHome`，限制文件系统访问范围。

#### 2. 多门户架构（Portal Pattern）—— SolonCode 最核心的设计

所有门户（CLI/Web/Desktop/ACP）共享同一个 HarnessEngine 和 AgentSession 机制，
但各自实现输入接收和输出渲染。所有门户遵循相同的 **ReAct Chunk 流式协议**：

```
用户输入 → HarnessEngine.prompt().session().stream()
         → Flux<Chunk> 响应式流
            ├── ReasonChunk    → AI 推理/思考（增量块，thinking/non-thinking 两种）
            ├── ThoughtChunk   → 多任务并行中间结果（含 multitask 元数据标记）
            ├── ObservationChunk → 工具调用结果（含 toolName, args, content）
            └── ReActChunk     → 最终回复（含 trace metrics: model, tokens, time）
```

##### CLI 门户 (CliShell, 701 行)

**核心流程**：
1. `prepare()` 初始化终端、Session
2. `run()` 主循环：JLine `readLine()` → `performAgentTask()` → 订阅流
3. ESC 键中断：`waitForTask()` 中进入 raw mode 轮询，ESC (char 27) 触发 `disposable.dispose()`

**特色功能**：
- **JLine 3 终端交互**：raw mode + ANSI 8 色高亮（BOLD/GREEN/YELLOW/RED/CYAN/PURPLE/DIM）
- **HITL 审批**：bash 命令执行前弹出 `(y/n)` 审批提示，支持 approve/reject
- **双输出风格**：`cliPrintSimplified=true`（默认，单行摘要）vs 全量模式（完整参数+结果）
- **@agent 前缀路由**：`@agentName prompt` 可将任务路由到特定子 Agent
- **Tab 补全**：命令（`/`）、技能（`$`）、Agent（`@`）三类补全
- **trace 统计**：每轮完成后打印 `(model-name, 1523tk, 12s)` 摘要

**输出示例（简化模式）**：
```
❯ write_file path=src/App.java content=[1项]
❯ bash command=npm test (returned 15 lines)
```

##### Web 门户 (WebController + WebGate, 合计 ~1800 行)

**双通道架构**：HTTP REST API（输入）+ WebSocket（输出推送）

**WebController（792 行）HTTP 接口**：
- 会话管理：`/web/chat/sessions`（列表）、`delete`、`rename`
- 消息历史：`/web/chat/messages`（ndjson 逐行解析）
- 模型管理：`/web/chat/models`（列表）、`models/select`（切换）
- 聊天输入：`/web/chat/input`（文本 + 文件附件 + HITL 操作）
- 中断：`/web/chat/interrupt`
- 回退：`/web/chat/rewind`（删除 ndjson 末尾 N 条）
- Git 集成：`/web/chat/git/*`（status/init/diff/stage/unstage/commit/summary/file-content）
- 文件浏览：`/web/chat/filer/*`（tree/search/read）
- 命令提示：`/web/chat/hints`（命令 + 子 Agent + 技能列表）

**WebGate（590 行）WebSocket 网关**：
- 连接池：`CopyOnWriteArrayList<WebSocket>`，消息带 sessionId 广播
- 统一输出：`emitToClient(sessionId, WebChunk)` — 所有消息注入 sessionId 后序列化广播
- 原始广播：`broadcastRaw(json)` — 系统级事件（文件变更）无 sessionId 广播
- HITL 处理：`hitlAction` 参数 approve/reject → `HITL.approve/reject` → 恢复流
- 附件处理：图片走 Base64 + `ImageBlock`，文件走路径前缀 `[附件: filename]`

**WebChunk（278 行）消息块 DTO**：
| type | 用途 | 关键字段 |
|------|------|----------|
| `text` | 最终回复文本 | text |
| `reason` | AI 推理/思考过程 | text |
| `action` | 工具调用结果 | text, toolName, args |
| `command` | 命令输出 | text |
| `hitl` | 人工审批中断 | toolName, command |
| `rewind` | 回退指令 | text (count) |
| `done` | 流结束 | — |
| `error` | 错误 | text |
| `trace` | 性能指标 | model, totalTokens, elapsedSeconds |
| `user_input` | 用户输入回显 | text, toolName (source) |

**WebStreamBuilder（398 行）**：将 ReAct Chunk 映射为 WebChunk，同时：
- 过滤内部工具（multitask/task/memory）不输出
- IM 通道同步转发：在 ThoughtChunk 时调用 `replyToBoundChannel()`
- HITL 检测：流结束后自动检测是否有挂起的人工审批任务

##### Desktop 门户 (WsGate + WsController, 合计 ~840 行)

**WebSocket 单通道**：所有通信走 WebSocket（区别于 Web 的 HTTP+WS 双通道）

**特色功能**：
- **config 消息类型**：前端可通过 `{"type":"config", "chatModel":{...}}` 动态修改模型配置
- **hitl_action 消息类型**：`{"type":"hitl_action", "action":"approve|reject", "sessionId":"..."}`
- **三种模式**：`plan`（只读分析）、`auto`（文件编辑自动放行，shell 需审批）、`default`（全 HITL）
- **ModelProvider 抽象**：`ModelProviderFactory` 根据类型创建 `OllamaModelProvider` / `OpenAIModelProvider` / `ZhiPuModelProvider`
- **模型动态管理**：`/chat/models/fetch`（从 API 拉取）、`/chat/models/add`、`/chat/models/remove`
- **配置持久化**：修改后自动写入 `~/.soloncode/chat-model.yml`

##### ACP 门户 (AcpLink, 313 行)

**Agent Client Protocol 标准化实现**：
- 支持 **stdio** 和 **WebSocket** 两种传输层
- 实现 ACP 生命周期：`initialize` → `newSession` / `loadSession` → `prompt` → `cancel`
- **ReAct → ACP 映射**：
  - `PlanChunk` → `AcpSchema.Plan`（结构化计划输出）
  - `ReasonChunk` → `acpContext.sendThought()`（思考过程）
  - `ObservationChunk` → `AcpSchema.ToolCall`（工具调用结构化通知）
  - `ReActChunk` → `acpContext.sendMessage()`（最终回复）
- **多会话隔离**：`ConcurrentHashMap<String, AcpSessionContext>`，sessionId = "acp-" + UUID
- **取消支持**：`cancelHandler` 设置 `cancelled` 标志，`takeWhile` 过滤

#### 3. IM 通道集成—— SolonCode 独特的能力

通过 `Channel` 接口统一三种企业 IM：

```java
public interface Channel {
    String getChannelName();
    boolean isBound(String sessionId);
    void sendReply(String sessionId, String text, boolean isFinal);
}
```

| 通道 | 实现类 | 协议 |
|------|--------|------|
| 微信 | `WeChatClient` + `WeChatLink` | WebSocket 长连接 |
| 飞书 | `FeishuClient` + `FeishuLink` | Stream 长连接 + Protobuf |
| 钉钉 | `DingTalkClient` + `DingTalkLink` | Stream 长连接 |

**通道路由模式**：
1. `WebStreamBuilder.bind(Channel)` 注册通道
2. AI 响应流中 `ThoughtChunk` 触发 `replyToBoundChannel(sessionId, text, isFinal)`
3. `isFinal=true` 标记最终回复，`false` 标记中间过程
4. 支持命令执行结果也通知 IM 通道

**安全机制**：
- `CredentialStore` 存储凭证
- `sessionId` 绑定防止跨会话泄漏
- `safeChatInput()` 防止并发冲突（检查 `isSessionBusy`）

#### 4. 配置体系

**三层配置合并**（`AgentSettings.mergeFrom(AgentProperties)`）：

```
config.yml（YAML，静态绑定） → AgentProperties（内存对象）
                                    ↕ 双向合并（settings 优先）
settings.json（JSON，动态持久化） → AgentSettings
  ├── ~/.soloncode/settings.json   （全局配置，scope=user）
  └── ./.soloncode/settings.json   （工作区配置，scope=workspace，覆盖全局）
```

**配置项全景**：

| 类别 | 配置项 | 说明 |
|------|--------|------|
| **LLM 模型** | `models[]` | 多模型列表，支持 name/apiUrl/apiKey/standard/model/timeout/headers |
| **MCP 服务器** | `mcpServers{}` | SSE/Streamable/STDIO 三种传输，含 disallowedTools 黑名单 |
| **OpenAPI 源** | `apiServers{}` | docUrl + apiBaseUrl + headers |
| **LSP 服务器** | `lspServers{}` | 14 种语言内置，command + extensions |
| **挂载池** | `mountPools{}` | Skills/Agents 目录，支持 alias/type/path/primary/writeable |
| **通用设置** | `general` | sessionWindowSize(8), summaryWindowSize(30), summaryWindowToken(30000) |
| **运行时** | — | sandboxMode(true), hitlEnabled(false), bashAsyncEnabled(false) |
| **重试策略** | — | apiRetries(3), mcpRetries(3), modelRetries(3) |
| **记忆** | — | memoryEnabled(true), memoryIsolation(true) |
| **显示** | — | thinkPrinted(false), cliPrintSimplified(true) |

**ConfigTalent（153 行）—— AI 自管理配置**：

这是一个独特设计：AI Agent 可以通过工具调用动态添加自己的能力。

| 工具 | 参数 | 效果 |
|------|------|------|
| `add_model` | name, apiUrl, apiKey, standard, model, headers, timeout | 立即注册新 LLM + 持久化 |
| `add_mcp_server` | name, transport, url, command, args, env, disallowedTools, timeout | 立即注册新 MCP 服务 + 持久化 |
| `add_api_server` | docUrl, apiBaseUrl, headers, disallowedTools, timeout | 立即注册新 API 源 + 持久化 |

每个工具执行两步：`engine.addXxx()` (运行时生效) + `settings.getXxx().add()` + `settings.saveToFile()` (持久化)。

#### 5. Loop 定时任务系统

`/loop` 命令实现 Agent 定时循环任务，三个核心类合计 ~850 行：

**LoopTask（225 行）— 任务模型**：
- 固定间隔 / cron 表达式 / 自动间隔三种模式
- CAS 防重入：`synchronized tryStart()` / `finish()`
- 7 天自动过期
- ONode 序列化/反序列化

**LoopScheduler（374 行）— 调度管理器**：
- 基于 Solon `IJobManager`（`JobManager.getInstance()`）原生调度
- `ConcurrentHashMap<sessionId, List<LoopTask>>` 会话级任务列表
- JSON 原子写入（先写 `.tmp` 再 `Files.move` + `ATOMIC_MOVE`）
- 进程重启恢复：`restore()` 从 JSON 重新注册未过期任务
- 每会话最多 50 个任务
- `TaskExecutor` 回调接口，CLI/Web 各自注入不同的执行器

**LoopCommand（249 行）— CLI/Web 命令**：

| 用法 | 说明 |
|------|------|
| `/loop 5m check deployment` | 固定间隔（支持 s/m/h 单位） |
| `/loop cron:"0 */5 * * * ?" check status` | Cron 表达式 |
| `/loop check CI status` | 自动间隔（默认 5 分钟） |
| `/loop ls` | 列出活跃任务（显示状态/调度/上次执行） |
| `/loop stop <id>` | 停止指定任务 |
| `/loop stop-all` | 停止所有任务 |

#### 6. 记忆系统

```java
MemoryFactory(AgentProperties) → MemorySolutionMdImpl（基于 Markdown 文件）
```

- **MemorySolution.Factory** 接口：`get(cwd)` 返回 `MemorySolution`
- **工作区隔离**：`memoryIsolation=true` 时按 cwd 隔离记忆；`false` 时共享全局记忆（`~/.soloncode/memory/`）
- **ConcurrentHashMap 缓存**：按 cwd 路径缓存实例
- 上游 `solon-ai-talent-memory` 提供实际的记忆存储和检索逻辑

#### 7. Git 集成 (GitService, 557 行)

Web 门户内置完整的 Git 管理能力：

| 接口 | Git 命令 | 说明 |
|------|----------|------|
| `/web/chat/git/status` | `git --version` + `rev-parse` + `branch` + `status --porcelain` | 三类变更文件列表 |
| `/web/chat/git/init` | `git init` + 自动 `.gitignore` | 可选 initial commit |
| `/web/chat/git/diff` | `git diff` + `git diff --cached` | 2000 行截断保护 |
| `/web/chat/git/stage` | `git add` | 单文件暂存 |
| `/web/chat/git/unstage` | `git reset HEAD` | 取消暂存 |
| `/web/chat/git/commit` | `git add` + `git commit` | 支持精确文件列表或全量 |
| `/web/chat/git/summary` | AI 生成提交摘要 | 复用当前会话模型，15 文件/500 行/20K 字符限制 |
| `/web/chat/git/file-content` | `git show ref:path` | 查看指定版本文件 |

**安全措施**：所有路径参数校验 `..`、`/`、`\`；`GIT_TERMINAL_PROMPT=0`；10 秒超时保护。

#### 8. WorkspaceWatcher（216 行）

基于 JDK `WatchService` 的文件变化监听：

- **排除目录**：`.soloncode`, `.claude`, `.opencode`, `.git`, `.idea`, `node_modules`, `target` 等
- **去重防抖**：`ConcurrentHashMap.newKeySet()` 收集变更 → `flushChanges()` 批量推送
- **JSON 广播**：`{"type": "filer_change", "changes": [...], "createdAt": ...}`
- **自动注册新目录**：`ENTRY_CREATE` 时递归注册
- **守护线程**：随主进程退出

#### 9. 技能市场 (MarketManager)

多市场技能发现：
- `SkillhubMarket` — Solon 官方技能市场
- `ClawhubMarket` — Claw 生态市场
- `SkillsShMarket` — Skills.sh 市场（已注释）
- `Market` 接口：`name()`, `description()`, `search(keyword)`, `detail(name)`, `install(name)`

#### 10. 扩展机制 (HarnessExtension)

用户扩展通过 Solon IoC 自动发现：

```java
// extension_demo 示例
public class Extension1 implements HarnessExtension {
    @Override
    public void configure(String agentName, ReActAgent.Builder agentBuilder) {
        agentBuilder.defaultInterceptorAdd(new ReActInterceptor() {
            void onAgentStart(ReActTrace trace) { ... }
            void onReasonEnd(ReActTrace trace, ...) { ... }
            ChatResponse interceptCall(ChatRequest req, CallChain chain) { ... }
        });
    }
}
```

扩展 JAR 放入 `~/.soloncode/extensions/` 目录（`System.setProperty("solon.extend", "!" + path)`），
Solon IoC 自动扫描并注册到 `HarnessEngine`。

### 上游依赖关系

SolonCode 自身是一个薄壳层，核心 AI 能力来自 Solon AI 生态：

```
soloncode-cli
├── solon-ai-harness           # Agent 执行引擎（HarnessEngine, ReActAgent, Command, CodeTalent, HITL）
│   ├── solon-ai-agent         # Agent 核心（ReActAgent, SimpleAgent, TeamAgent）
│   ├── solon-ai-core          # ChatModel, FunctionTool, Talent, Repository
│   └── solon-ai-talents       # 17 个预构建 Talent（cli, file, web, lsp, text2sql...）
├── solon-ai-acp               # ACP 协议 SDK
├── solon-ai-talent-memory     # 记忆 Talent
├── solon-server-jetty         # 嵌入式 Web 容器
├── solon-server-jetty-add-websocket  # WebSocket 支持
├── solon-scheduling-simple    # 定时任务调度（LoopScheduler 的 IJobManager）
└── solon-web-cors             # 跨域支持
```

**solon-ai-harness 提供的核心能力**（SolonCode 未自行实现）：
- ReActAgent 循环（Think→Act→Observe）
- 工具系统（bash, file, web, lsp, code execution 等 17+ 个 Talent）
- 多语言代码执行（Java, Python, Go, Rust, Node, C#, Flutter, CMake, Maven, Gradle, PHP, Cangjie）
- HITL 拦截器
- Session 管理（FileAgentSession）
- 上下文压缩
- 子 Agent 管理（AgentManager, AgentFactory, AgentDefinition）
- 命令注册表（CommandRegistry, MarkdownCommandLoader）

### 技术亮点

1. **Portal 统一引擎模式**：一个 `HarnessEngine` 实例服务 5 个门户（CLI/Web/Desktop/ACP/Serve），每个门户独立实现输入/输出适配
2. **IM 原生集成**：微信/飞书/钉钉三种企业 IM 通道，`Channel` 接口统一路由，`WebStreamBuilder` 流式转发
3. **ConfigTalent**：AI 通过工具调用自管理能力扩展（动态添加 Model/MCP/API），配置立即生效并持久化——这是 Agent 自进化的雏形
4. **Loop 定时任务**：基于 IJobManager 的 cron 调度 + JSON 原子写入 + 进程重启恢复 + 7 天自动过期
5. **WebChunk 统一消息协议**：10 种消息类型覆盖所有交互场景（text/reason/action/hitl/rewind/done/error/trace/command/user_input）
6. **ACP 标准协议**：将 ReAct 循环映射为 ACP 结构化输出（Plan/ToolCall/Thought），支持 IDE 集成
7. **AGENTS.md 定制**：纯 Markdown 无 DSL，1 万字符限制，workspace 级别定制
8. **Git 深度集成**：AI 生成提交摘要（`git-summary` Agent），复用当前会话模型
9. **多模型路由**：`@agentName` 前缀路由到特定 Agent，`/model` 命令切换模型
10. **三级重试**：apiRetries(3) + modelRetries(3) + mcpRetries(3)，每种外部调用独立重试策略
11. **安全设计**：路径校验（防 `..` 穿越）、sandbox 模式、GIT_TERMINAL_PROMPT=0、10s 超时保护
12. **扩展机制**：`HarnessExtension` + Solon IoC 自动扫描 + `solon.extend` 目录

### 劣势

1. **代码规模有限**：soloncode-cli 仅 ~74 个 Java 文件，核心 AI 能力全部委托上游 solon-ai-harness，自身是薄壳产品层
2. **测试薄弱**：测试文件仅 9 个（`CliTest`, `WebTest`, `AcpTest` 等），多为集成测试，缺乏单元测试和 mock 测试
3. **IM 通道安全**：微信/飞书/钉钉的 `CredentialStore` 使用明文文件存储凭证
4. **会话数据格式**：ndjson 文件存储，缺乏索引和压缩，大量会话时 I/O 性能堪忧
5. **无插件系统**：不像 OpenCode 有完整的 plugin 架构（7 种 contribution type + 28 种 hook），扩展仅靠 Solon IoC 扫描
6. **文档主要中文**：限制国际社区参与
7. **LSP 默认禁用**：需要用户手动在 settings.json 中启用，初次体验门槛较高
8. **单用户设计**：无多用户/多租户支持，不适合团队部署
9. **WebGate 无 sessionId 绑定**：所有 WebSocket 连接收到所有会话的消息，前端自行过滤——存在信息泄漏风险
10. **WsGate/Desktop 代码重复**：WsGate（688 行）与 WebGate（590 行）大量重复的 chunk 处理逻辑，未抽象共享

### 与同类产品的对比

| 维度 | SolonCode | OpenCode | Claude Code | PilotDeck | Hermes Agent |
|------|-----------|----------|-------------|-----------|-------------|
| **定位** | AI Coding Agent | AI Coding Agent | AI Coding Agent | Agent OS | 自改进 Agent |
| **许可** | Apache 2.0 | Apache 2.0 | 闭源 | AGPL-3.0 | MIT |
| **语言** | Java 8~26 | TypeScript (Effect) | TypeScript | TypeScript | Python |
| **LOC** | ~5K (cli only) | ~430K | N/A | ~142K | ~2,027K |
| **多门户** | CLI+Web+Desktop+ACP+IM | CLI+Web+Desktop | CLI | CLI+TUI+Web+23 IM | CLI+TUI+30 IM |
| **IM 集成** | 微信/飞书/钉钉 (3) | 无 | 无 | 23 通道 | 30 通道 |
| **Agent 协议** | ACP | 无 | 无 | 无 | 无 |
| **定时任务** | Loop (cron, 850 行) | 无 | 无 | Cron (4 工具) | Cron (多格式) |
| **配置动态性** | ConfigTalent（AI 自管理） | 手动 | 手动 | 手动 | 手动 |
| **插件系统** | HarnessExtension（薄） | 完整 plugin (7 contribution) | 无 | 7 种 contribution (28 hooks) | 3 种 plugin 面 (72 yaml) |
| **上下文压缩** | LLM 摘要 + 窗口滑动 | 无 | 单层压缩 | 三级渐进 | 无 |
| **HITL** | 可选（默认关闭） | 默认开启 | 默认开启 | 5 种模式 | 无 |
| **记忆系统** | Markdown 文件 | SQLite | 无 | EdgeClaw 白盒 | 9 Provider 插件 |
| **测试** | 9 文件 | 大量 | N/A | 3 文件 | 1,409 文件 |
| **多 Agent** | subagentEnabled | Agent Team (7 agents) | 无 | Sub-Agent Fork (4 类) | Kanban + delegate |

### 与 nop-ai-agent 的逐项对比

#### 现状概要

| 维度 | SolonCode (含上游 solon-ai-harness) | nop-ai-agent (含 nop-ai-core/toolkit) |
|------|--------------------------------------|---------------------------------------|
| **ReAct 执行引擎** | **已实现** — `ReActAgent` 循环（Think→Act→Observe），含 autoRethink | **未实现** — 仅有 `BaseAgent` 桩类和设计文档 |
| **LLM 多 Provider** | **已实现** — Dialect SPI 自动发现（OpenAI/Ollama/DashScope/Gemini/Anthropic） | **已实现** — `ILlmDialect` SPI（OpenAI/Anthropic/Gemini/Ollama），无内置 DashScope 但有 `other` 扩展点 |
| **工具系统** | **已实现** — 17+ Talent（solon-ai-talents），包含 bash/file/web/lsp/text2sql 等 | **已实现** — 19 个 `.tool.xml` + `IToolManager` + `IToolCallInterceptor`，实际可用约 15 个（部分工具如 call-agent/ask-oracle 未完全接通） |
| **会话持久化** | **已实现** — `FileAgentSession`，ndjson 格式，每会话独立目录 | **部分** — `DefaultAiChatExchangePersister` JSONL 持久化，但缺少 Session 抽象 |
| **上下文压缩** | **已实现** — `compressionThreshold` + LLM 自动摘要 + 窗口滑动 | **未实现** — 配置模型已定义（`AiMemoryConfig` 含 trimRounds/enableSummary/summaryRounds），引擎未实现，设计中有 5 层压缩管线 |
| **多门户/多通道** | **已实现** — 5 个 Portal（CLI/Web/Desktop/ACP/Serve）+ 3 IM 通道 | **未实现** — 设计中有 `IPermissionMatrix` + channel 类型（webui/api/dm/group） |
| **HITL 审批** | **已实现** — 可选开启，CLI 用 `(y/n)` 提示，Web/Desktop 用 HITL WebChunk | **部分** — `AskOracleExecutor` 可运行但仅实现自动通过模式（无 endpoint 时），缺少实际人类审批 UI 流程 |
| **记忆系统** | **已实现** — `MemorySolutionMdImpl`（Markdown 文件），支持工作区隔离 | **未实现** — `IAiMemoryStore` 接口已定义，无实现类 |
| **定时任务** | **已实现** — Loop 系统 850 行，cron 调度 + JSON 持久化 + 进程重启恢复 | **未实现** — 无对应设计 |
| **子 Agent** | **已实现** — `AgentManager` + `@agentName` 路由 + `subagentEnabled` | **已定义** — `CallAgentExecutor` 工具 + `call-agent.tool.xml`，未接引擎 |
| **LSP 集成** | **已实现** — 14 种语言预注册，默认禁用，`LspTalent` 管理 | **未实现** — 设计中列 `lsp` 为计划 Talent |
| **Agent 协议** | **已实现** — ACP 协议适配器，ReAct→ACP 结构化映射 | **未实现** — 无对应设计 |
| **配置动态性** | **已实现** — ConfigTalent 让 AI 自管理 Model/MCP/API 配置 | **未实现** — 无对应设计 |
| **Agent DSL** | 无 DSL，纯 Java Builder + YAML/JSON 配置 | **已实现** — `agent.xdef` 生成完整 AgentModel，含 constraints/hooks/permissions/plan |
| **Plan DSL** | 无结构化 Plan | **已实现** — `agent-plan.xdef` 生成完整 Plan 模型（phases/tasks/scope/closure） |
| **Prompt 模板** | 简单 system prompt（AGENTS.md） | **已实现** — `PromptTemplateManager` + prompt 语法解析器 |
| **响应解析** | 无结构化解析 | **已实现** — JSON/XML/YAML/Code/Markdown 响应解析器 |
| **File Diff** | 委托上游 Talent | **已实现** — `FileDiff` + `FileDiffApplier` + `FileDiffGenerator` + `PatchFileExecutor` |
| **Mock 测试** | 无 mock | **已实现** — `io.nop.ai.core.mock.MockChatService`（`IResponseProvider` SPI + 流式逐字符发送） |
| **MCP Server** | 委托上游 solon-ai-mcp | **已实现** — `nop-ai-mcp-server` + `nop-spring-mcp-server` |
| **代码分析** | 无 | **已实现** — `nop-ai-skills/nop-ai-code-analyzer`（Java 解析器、Maven 结构统计） |
| **代码生成** | 无 | **已实现** — `nop-ai-coder`（ORM→Java, API→Java） |
| **虚拟 Shell** | 无 | **已实现** — `nop-ai-shell`（Bash 语法解析、命令注册、管道/重定向） |
| **向量存储/RAG** | 无 | **已实现** — `IVectorStore` + `CosineSimilarity` + `IEmbeddingModel` + `ITextClassifier` 接口 |
| **响应缓存** | 无（每次调 LLM） | **已实现** — `DefaultAiChatResponseCache` 基于 `calcRequestHash` 的 LLM 响应缓存 |
| **执行状态机** | 无显式状态机 | **已实现** — `AgentExecStatus` 枚举定义 Agent 执行状态 |

#### 成熟度对比总结

```
                       SolonCode/solon-ai          nop-ai-agent
                       ─────────────────          ────────────
  Agent Runtime          ██████████ (生产)          ██░░░░░░░░ (设计)
  LLM Provider           ██████████ (生产)          ██████████ (生产)
  Tool System            ██████████ (生产)          ███████░░░ (生产，部分工具未接通)
  Session/State          ████████░░ (生产)          ███░░░░░░░ (部分)
  Context Compression    ██████░░░░ (可用)          █░░░░░░░░░ (配置模型已定义)
  Multi-Portal           ██████████ (生产)          ░░░░░░░░░░ (设计)
  IM Integration         ██████░░░░ (可用)          ░░░░░░░░░░ (设计)
  HITL                   ██████░░░░ (可用)          ██░░░░░░░░ (骨架)
  Memory                 ████░░░░░░ (可用)          █░░░░░░░░░ (接口)
  DSL/Model              ██░░░░░░░░ (配置)          ██████████ (生产)
  Code Gen/Analysis      ░░░░░░░░░░ (无)           ██████████ (生产)
```

### SolonCode 对 nop-ai-agent 的参考价值

#### P0 — 直接可参考（nop-ai-agent 正在实现的核心功能）

1. **ReAct Agent 执行引擎**：SolonCode 的 `HarnessEngine.prompt().session().stream()` 是 ReAct 循环的参考原型。但 nop-ai-agent **不需要 stream 字符级交互**，整体采用 **Actor 消息模型**——Agent 作为一个 Actor 接收完整 Message，处理后返回完整 Message，不涉及流式 chunk 或 TUI。**建议**：nop-ai-agent 的 `ReActExecutor` 参考 SolonCode 的 Think→Act→Observe 循环结构，但将 ReActChunk 简化为完整的 `AgentMessage`（而非流式 fragment）。每次 LLM 调用、工具调用、观察结果都是一个完整的 Actor 消息。

2. **Actor 消息模型（替代 Portal 统一引擎）**：SolonCode 用 Portal 模式（一个 Engine + 多个 Portal 适配器）实现多通道。nop-ai-agent 采用更简洁的 **Actor 模型**——每个 Channel（Web/API/IM）作为独立 Actor 与 Agent Actor 通过消息通信，无需 stream 适配、无需 TUI。Agent Actor 接收 `UserMessage`，内部执行 ReAct 循环，产出 `AgentResponse`（完整消息），由 Channel Actor 负责投递。**建议**：nop-ai-agent 可定义 `IAgentActor`（`onMessage(AgentMessage): AgentResponse`）+ `IChannelActor`（`send(reply: AgentResponse)`），参考 SolonCode 的 Channel 路由但去掉 stream/WebChunk 复杂度。

3. **消息类型设计（简化版 WebChunk）**：SolonCode 的 `WebChunk` 有 10 种流式消息类型。nop-ai-agent 不需要流式，但消息类型分类仍有价值——可简化为 Actor 消息枚举：`TextMessage`（最终文本响应）、`ToolCallMessage`（工具调用请求）、`ToolResultMessage`（工具执行结果）、`ErrorMessage`、`ApprovalRequestMessage`（HITL）。**建议**：用 XMeta 定义 `AgentMessage` schema，类型数从 10 种压缩到 5~6 种，每条消息都是完整内容（非 chunk）。

4. **安全校验统一模式**：SolonCode 在所有输入端统一校验路径穿越（`..`）、sessionId 安全、超时保护。nop-ai-toolkit 已实现 `IToolCallInterceptor`（`beforeCall`/`afterCall`），可直接在 `beforeCall` 中注入安全校验。**建议**：nop-ai-agent 可参考 SolonCode 的轻量校验模式，通过 `IToolCallInterceptor` 统一注入路径穿越、超时保护等安全策略。

#### P1 — 中期可参考

5. **上下文压缩**：nop-ai-agent 设计了 5 层压缩管线（Layer 0~4），远比 SolonCode 的单层 LLM 摘要更先进。`AiMemoryConfig` 已定义 trimRounds/enableSummary/summaryRounds 等配置。SolonCode 提供了一个**可运行的实现**——`compressionThreshold(summaryWindowSize, summaryWindowToken)` + `compressionModel`。**建议**：nop-ai-agent 可先实现 Layer 0（工具结果截断）和 Layer 3（LLM 摘要），参考 SolonCode 的 `compressionThreshold` 参数设计。

6. **ConfigTalent 自管理配置**：SolonCode 让 AI 通过 `add_model`/`add_mcp_server`/`add_api_server` 工具自管理配置。nop-ai-agent 的 `ITalent` 接口已设计但未实现。**建议**：nop-ai-agent 可实现一个 `ConfigTalent`，利用 Nop 的 Delta 定制机制——AI 动态生成 `.tool.xml` 或修改 `agent.xdef`，通过 Delta 层叠加到现有配置。

7. **IM Channel Actor**：nop-ai-agent 设计中有 channel 类型（webui/api/dm/group）但无具体实现。SolonCode 的 `Channel` 接口 + `WebStreamBuilder.bind()` 是参考，但 nop-ai-agent 用 Actor 模型更简洁——每个 IM 通道是一个 Channel Actor，接收 `AgentResponse` 完整消息后投递到 IM，无需流式转发。**建议**：nop-ai-agent 可定义 `IChannelActor` 接口（`onAgentResponse(response: AgentResponse)`），每个 IM（钉钉/飞书/企微）独立实现。

8. **Loop 定时任务**：SolonCode 的 850 行 Loop 系统是 Agent 监控/巡检场景的完整参考。nop-ai-agent 当前无对应设计。**建议**：nop-ai-agent 可利用 nop-job 的调度能力实现类似功能，定义 `LoopCommand` + `LoopTask` + JSON 持久化。

9. **ACP 协议适配**：SolonCode 将 ReAct 循环映射为 ACP 结构化输出（Plan/ToolCall/Thought）。nop-ai-agent 无 Agent 协议设计。**建议**：nop-ai-agent 可在 `IAgentPortal` 的基础上增加 ACP Portal，复用 ReAct→ACP 的映射逻辑。

10. **HITL 审批流程**：nop-ai-agent 有 `AskOracleExecutor`（可运行但仅自动通过模式）和 `IApprovalGate` 设计。SolonCode 提供了完整的 HITL 实现（CLI raw mode + Web HITL chunk），但 nop-ai-agent 用 Actor 模型更简洁——Agent 发送 `ApprovalRequestMessage`，Channel Actor 转发给人类，人类回复后 Agent Actor 继续执行。**建议**：nop-ai-agent 的 `IApprovalGate` 产出 `ApprovalRequestMessage`，通过 Actor 消息传递实现审批，无需 SolonCode 那种 stream 暂停/恢复机制。

#### P2 — 长期可参考

11. **LSP Talent 预注册模式**：SolonCode 内置 14 种语言 LSP 配置但默认禁用。nop-ai-agent 计划实现 `lsp` Talent。**建议**：采用同样的预注册+默认禁用模式，通过 `AgentModel.tools` 白名单控制启用。

12. **MarketManager 技能市场**：nop-ai-agent 可参考 SolonCode 的多市场适配器模式，利用 Nop 的 VFS 机制实现技能分发。

#### nop-ai-agent 的优势（SolonCode 应当学习的）

| 维度 | nop-ai-agent 优势 | SolonCode 现状 |
|------|-------------------|----------------|
| **Agent DSL** | `agent.xdef` 完整 DSL，声明式定义约束/钩子/权限/计划 | 无 DSL，纯 Java Builder + YAML/JSON |
| **Plan 模型** | `agent-plan.xdef` 结构化计划（phases/tasks/scope/closure/criterion） | 无结构化 Plan |
| **工具定义** | `.tool.xml` 声明式工具定义，含 schema/example | Talent 通过 `@ToolMapping` 注解，无独立 schema |
| **Prompt 模板** | `PromptTemplateManager` + prompt 语法解析器 | 仅 AGENTS.md 纯 Markdown |
| **响应解析** | JSON/XML/YAML/Code/Markdown 结构化解析器 | 无结构化解析 |
| **File Diff** | `FileDiff` + `FileDiffApplier` + `PatchFileExecutor` + `ApplyDeltaExecutor` | 委托上游 Talent |
| **Mock 测试** | `MockChatService` 支持无真实后端测试 | 无 mock，依赖真实 LLM |
| **虚拟 Shell** | `nop-ai-shell` Bash 解析器，管道/重定向/命令注册 | 委托上游 bash Talent |
| **代码分析/生成** | `nop-ai-code-analyzer` + `nop-ai-coder` | 无 |
| **MCP Server** | 独立 MCP Server 模块 + Spring 集成 | 委托上游 solon-ai-mcp |
| **向量存储/RAG** | `IVectorStore` + `CosineSimilarity` + `IEmbeddingModel` 接口 | 无 |
| **响应缓存** | `DefaultAiChatResponseCache` 基于 hash 的 LLM 响应缓存 | 无 |
| **执行状态机** | `AgentExecStatus` 枚举定义 Agent 执行状态 | 无显式状态机 |
| **持久化层** | 完整的 Dao/Service/Meta/Web 层（Nop 平台标准能力） | 无持久化层 |

### 与 Nop 平台的关联

#### 可借鉴 (高价值)

1. **Actor 消息模型（替代 Portal 统一引擎）**：SolonCode 的 Portal 模式（一个 Engine + 多个 Portal 适配器）可简化为 Actor 模型——Agent Actor + Channel Actor 通过消息通信，无需 stream/TUI
2. **消息类型设计**：SolonCode 的 WebChunk 10 种消息类型可简化为 nop-ai-agent 的 `AgentMessage` 枚举（5~6 种完整消息类型），用 XMeta 定义 schema
3. **ConfigTalent 自管理**：AI 通过工具调用自管理配置扩展，可与 Nop 的 Delta 定制结合——AI 动态注册新的 BizModel/Meta
4. **IM Channel 路由表**：`Channel` 接口 + `WebStreamBuilder.bind()` 路由表模式，可用于 Nop 企业 IM 集成（钉钉/飞书/企微）
5. **Loop 定时任务**：IJobManager cron 调度 + JSON 原子写入 + 进程重启恢复，是 Nop nop-job 集成 Agent 的参考实现
6. **ACP 协议**：标准化 Agent 通信协议实现，为 Nop Agent 互操作（与 IDE/其他 Agent 集成）提供标准参考
7. **LSP Talent 预注册**：14 种语言内置 LSP 配置但默认禁用的模式，平衡开箱即用和资源消耗——适合 Nop 的多语言支持策略
8. **GitService AI 摘要**：`git-summary` Agent 生成提交摘要的模式，可直接用于 Nop 的代码评审辅助

#### 可借鉴 (中等价值)

9. **AGENTS.md 纯 Markdown 定制**：比 Nop 当前的 AGENTS.md 方案更简洁，值得考虑简化
10. **三级重试策略**：apiRetries / modelRetries / mcpRetries 独立配置，对 Nop AI 的容错策略有参考价值
11. **安全校验模式**：路径穿越校验（`..`）、sessionId 安全校验、超时保护的统一模式
12. **MarketManager 技能市场**：多市场适配器模式可用于 Nop AI 的技能分发生态

#### 不适用

- **流式输出/WebChunk**：nop-ai-agent 不需要 stream 字符级交互，采用 Actor 完整消息模型，WebChunk 的 chunk 类型设计不适用
- **TUI/终端交互**：nop-ai-agent 不需要 TUI 支持，SolonCode 的 JLine/CliShell 终端交互模式不适用
- **Stream 暂停/恢复机制**：SolonCode 的 HITL 用 stream 暂停实现审批，nop-ai-agent 用 Actor 消息传递，无需 stream 控制
- Solon IoC 与 Nop IoC 差异（但哲学相似：无 private field 注入，都用 `@Inject`）
- snack4 JSON 库（Nop 使用自有 JSON 工具）
- Jetty 嵌入式 Web 容器（Nop 有自己的 Web 集成方案）
- Project Reactor（Nop 生态未使用 Reactor）
- 核心逻辑在上游 solon-ai-harness，SolonCode 自身架构过于薄，不具备框架级参考价值

## Conclusion

SolonCode 是 Java AI 编码 Agent 生态中 **多通道能力最强** 的项目。其最大差异化在于：(1) **Portal 统一引擎** — 一个 HarnessEngine 服务 CLI/Web/Desktop/ACP/IM 五个门户，每个门户独立实现输入/输出适配，共享 Agent 逻辑；(2) **IM 原生集成** — 微信/飞书/钉钉三种企业 IM 通过 `Channel` 接口统一路由，`WebStreamBuilder` 将 AI 响应流式转发到 IM；(3) **ConfigTalent** — AI 通过工具调用自管理配置扩展（动态添加 Model/MCP/API），是 Agent 自进化的雏形；(4) **Loop 定时任务** — cron 调度 + JSON 持久化 + 进程重启恢复，填补了 Agent 监控/巡检场景的空白。

**与 nop-ai-agent 的关系**：两者处于不同的成熟阶段，且设计哲学有本质差异。nop-ai-agent 在 **DSL/模型/代码生成/虚拟 Shell/Mock 测试** 等"框架基础"维度更强（声明式 Agent DSL、结构化 Plan、响应解析器），但在 **Agent 运行时**（ReAct 引擎、多通道、上下文压缩、HITL、记忆、IM 集成）等"产品能力"维度尚未实现。**关键设计差异**：nop-ai-agent 采用 **Actor 消息模型**（完整 Message 进出，无 stream/TUI），而 SolonCode 是 **流式 Portal 模型**（chunk 级 stream + 多 Portal 适配器）。这意味着 nop-ai-agent 不应直接照搬 WebChunk 或 Portal 模式，而应参考其功能分类（消息类型、通道路由），用 Actor 模型重新简化实现。nop-ai-agent 应在保持 DSL 优势和 Actor 简洁性的同时，优先从 SolonCode 借鉴 **P0 级** 的运行时逻辑（ReAct 循环结构、安全校验、消息类型分类）。

## Open Questions

- [ ] Actor 消息模型中 Agent Actor 与 Channel Actor 的消息协议如何定义？`AgentMessage` 的类型枚举是否 5~6 种足够？
- [ ] SolonCode 的 WebChunk 10 种消息类型在 Actor 模型下如何简化？哪些类型可合并？
- [ ] ConfigTalent 的 AI 自管理配置模式是否适合 Nop 的 Delta 定制机制？AI 动态生成 `.tool.xml` 是否安全？
- [ ] Loop 定时任务的 JSON 持久化 + nop-job 调度方案能否直接在 Nop 中复用？
- [ ] ACP 协议是否应作为 Nop Agent 互操作的标准化方向？在 Actor 模型中如何适配？
- [ ] IM Channel Actor 模式能否扩展为 Nop 的多渠道消息分发基础设施？
- [ ] nop-ai-agent 的 5 层压缩管线设计是否应参考 SolonCode 先实现 Layer 0 + Layer 3 的最小可用版本？
- [ ] nop-ai-agent 已有 `IVectorStore` + `IEmbeddingModel` 接口，是否应作为 RAG/Talent 检索的底层？
- [ ] `DefaultAiChatResponseCache` 的 hash 缓存策略是否适合 Agent 场景（工具结果差异导致请求不同）？

## References

- ~/ai/soloncode/README.md
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/App.java (146 行, 入口)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/Configurator.java (397 行, IoC 核心)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/portal/cli/CliShell.java (701 行, CLI 门户)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/portal/web/WebController.java (792 行, Web HTTP)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/portal/web/WebGate.java (590 行, Web WebSocket)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/portal/web/WebStreamBuilder.java (398 行, 流式构建)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/portal/desktop/WsGate.java (688 行, Desktop 门户)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/portal/acp/AcpLink.java (313 行, ACP 协议)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/portal/web/GitService.java (557 行, Git 集成)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/command/builtin/LoopScheduler.java (374 行, 定时调度)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/config/ConfigTalent.java (153 行, AI 自管理配置)
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/config/AgentSettings.java (381 行, 配置持久化)
- ~/ai/solon-ai/ (Solon AI 框架，上游依赖)
- ~/ai/solon-ai/solon-ai-harness/ (Agent 引擎，核心上游)
- nop-ai/nop-ai-agent/ (Nop AI Agent 模块，数据模型+设计文档)
- nop-ai/nop-ai-core/ (Nop AI 核心引擎，LLM Dialect + ChatService)
- nop-ai/nop-ai-toolkit/ (Nop AI 工具系统，19 个 IToolExecutor)
- ai-dev/design/nop-ai-agent/ (Nop AI Agent 设计文档)
- https://solon.noear.org/article/soloncode
- https://github.com/opensolon/solon-ai
