# SolonCode 技术分析

> Status: open
> Date: 2026-06-08
> Scope: ~/ai/soloncode — Solon AI 生态下的 AI Coding Agent 产品
> Conclusion:

## Context

- SolonCode 是基于 Solon AI 的开源 AI 编码助手，定位为 Claude Code / OpenCode 的 Java 替代品
- 100% 开源、Java 实现（支持 Java 8~26），Provider 无关，同时支持 CLI / Web / Desktop / IM 多前端
- 对 Nop 意义：同为 Java 生态非 Spring 框架，是 Java AI Agent 产品化的典型案例

## Analysis

### 项目定位

- **组织**: org.noear (OpenSolon)
- **许可**: Apache 2.0
- **版本**: v2026.6.8
- **Java 范围**: JDK 8 ~ JDK 26
- **核心依赖**: solon-ai-harness (agent runtime), solon-ai-acp (ACP 协议), solon-ai-talent-memory (长期记忆)
- **Web 容器**: Jetty (embedded) + WebSocket
- **CLI 库**: JLine 3 (终端交互)
- **定位**: 全栈 AI Coding Agent 产品（CLI + Web + Desktop + IM 多通道）

### 代码结构

```
soloncode/
├── soloncode-parent/           # Maven parent POM (依赖管理)
├── soloncode-cli/              # 主模块 (~74 Java files)
│   └── src/main/java/org/noear/solon/codecli/
│       ├── App.java            # 入口（Solon.start，多模式分发）
│       ├── Configurator.java   # IoC 配置（HarnessEngine 构建 + 所有门户启动）
│       ├── config/             # 配置层
│       │   ├── AgentProperties.java   # 主配置（config.yml 绑定）
│       │   ├── AgentSettings.java     # settings.json 持久化
│       │   ├── AgentFlags.java        # 版本/更新/常量
│       │   ├── GeneralSettings.java   # 通用设置
│       │   ├── ConfigExtension.java   # Harness 扩展（注入 ConfigTalent）
│       │   ├── ConfigTalent.java      # AI 运行时动态配置 Talent
│       │   └── entity/                # 配置实体类
│       ├── portal/             # 多门户层
│       │   ├── cli/            # CLI 门户（JLine 交互）
│       │   ├── web/            # Web 门户（HTTP + WebSocket）
│       │   ├── desktop/        # Desktop 门户（WebSocket + ModelProvider）
│       │   ├── acp/            # ACP 协议门户
│       │   └── WorkspaceWatcher.java  # 文件变化监听
│       ├── channel/            # IM 通道层（微信/飞书/钉钉）
│       ├── command/            # 命令上下文
│       │   ├── builtin/        # 内置命令（exit/clear/resume/rewind/model/loop）
│       │   ├── CliCommandContext.java
│       │   └── WebCommandContext.java
│       └── memory/             # 记忆工厂
└── examples/
    └── extension_demo/         # 扩展示例
```

### 运行模式

SolonCode 通过命令行 flag 进入不同运行模式：

| 模式 | 命令 | 入口类 | 说明 |
|------|------|--------|------|
| **CLI** | `soloncode` | `CliShell` | 默认模式，JLine 终端交互 |
| **Web** | `soloncode web [port]` | `WebController` | 启动 HTTP + WebSocket，浏览器交互 |
| **Serve** | `soloncode serve` | `WsController` + `WebChannel` | 全功能服务模式（WebSocket + IM 通道） |
| **ACP** | `soloncode acp` | `AcpLink` | Agent Communication Protocol（stdio 或 WebSocket） |
| **Run** | `soloncode run 'prompt'` | `CliShell.call()` | 单次任务执行，完成后退出 |

### 核心架构

#### 1. HarnessEngine（核心引擎）

所有门户共享同一个 `HarnessEngine` 实例，由 `Configurator.agentRuntime()` 构建：

```java
HarnessEngine engine = HarnessEngine.of(workspace, harnessHome)
    .userAgent(userAgent)
    .systemPrompt(agentsMd)          // AGENTS.md 作为 system prompt
    .maxTurns(maxTurns)
    .autoRethink(autoRethink)
    .sessionWindowSize(sessionWindowSize)
    .compressionThreshold(...)       // 上下文压缩
    .memoryEnabled(memoryEnabled)    // 跨会话记忆
    .sandboxEnabled(sandboxMode)     // 沙盒模式
    .hitlEnabled(hitlEnabled)        // Human-in-the-loop
    .subagentEnabled(subagentEnabled)
    .build();
```

关键设计决策：
- **AGENTS.md 作为 system prompt**：类似 Claude Code 的 CLAUDE.md，用户可通过文件定制 agent 行为
- **Session 持久化**：`FileAgentSession` 将对话存储在 `.soloncode/sessions/<sessionId>/` 下
- **上下文压缩**：达到阈值后用 LLM 自动总结历史，减少 token 消耗
- **工具权限控制**：`tools` + `disallowedTools` 双向白/黑名单

#### 2. 多门户架构（Portal Pattern）

所有门户（CLI/Web/Desktop/ACP）实现相同的交互模式：
1. 接收用户输入
2. 调用 `engine.prompt(input).session(session).stream()` 
3. 订阅响应式流，处理 ReAct 循环中的各类 Chunk：
   - `ReasonChunk` — AI 推理/思考（流式增量）
   - `ThoughtChunk` — 多任务并行中间结果
   - `ObservationChunk` — 工具调用结果
   - `ReActChunk` — 最终回复

**CLI 门户特色**：
- JLine 3 终端交互（raw mode + ANSI 颜色）
- ESC 键中断正在执行的任务
- HITL（Human-in-the-loop）：bash 命令执行前弹出审批提示
- 简化/全量两种输出风格（`cliPrintSimplified`）
- Tab 补全（命令、技能、Agent）

**Web 门户特色**：
- HTTP REST API + WebSocket 双通道
- 会话管理（列表、删除、重命名、消息历史、回退）
- Git 集成（status、init、diff、stage、commit）
- 文件浏览（树状结构、搜索、读取）
- 模型动态切换
- WorkspaceWatcher 实时推送文件变更

**ACP 门户特色**：
- 实现 Agent Client Protocol（标准化 Agent 通信协议）
- 支持 stdio 和 WebSocket 传输
- 将 ReAct 循环映射为 ACP 结构化输出（Plan、ToolCall、Thought）
- 多会话隔离（sessionId = "acp-" + UUID）

**Desktop 门户特色**：
- WebSocket 双向通信
- ModelProvider 抽象：支持从 Ollama / OpenAI / 智谱动态拉取模型列表
- 运行时动态添加/移除模型

#### 3. IM 通道集成

SolonCode 独特的 IM 通道架构，通过 `Channel` 接口统一：

```
Channel 接口: getChannelName() / isBound(sessionId) / sendReply(sessionId, text, isFinal)
├── WeChatLink    — 微信通道
├── FeishuLink    — 飞书通道  
└── DingTalkLink  — 钉钉通道
```

- `WebStreamBuilder` 将 AI 响应流式分发到已绑定的 IM 通道
- 支持 serve 模式下同时服务多个 IM 渠道

#### 4. 配置体系

**三层配置合并**：

```
config.yml（YAML，静态） → AgentProperties
                              ↕ 双向合并
settings.json（JSON，动态） → AgentSettings
  ├── ~/.soloncode/settings.json   （全局配置）
  └── ./.soloncode/settings.json   （工作区配置，覆盖全局）
```

配置项涵盖：
- LLM 模型列表（多模型支持）
- MCP 服务器（SSE/Streamable/STDIO）
- OpenAPI 源
- LSP 服务器（14 种语言内置注册，默认禁用）
- 挂载池（Skills/Agents 目录）
- 运行时参数（沙盒、重试、窗口大小、上下文压缩）

**ConfigTalent**：允许 AI 在运行时动态添加模型、MCP 服务、API 源——配置立即生效并持久化到 `settings.json`。这是一个独特设计：AI 自管理自己的能力扩展。

#### 5. Loop 命令系统

`/loop` 命令实现定时循环 AI 任务：

| 用法 | 说明 |
|------|------|
| `/loop 5m check deployment` | 固定间隔（5分钟） |
| `/loop cron:"0 */5 * * * ?" check status` | Cron 表达式 |
| `/loop check CI status` | 自动间隔（默认5分钟） |
| `/loop ls` | 列出活跃任务 |
| `/loop stop <id>` | 停止指定任务 |
| `/loop stop-all` | 停止所有任务 |

- 基于 Solon 的 `IJobManager` 原生调度
- JSON 持久化（原子写入），支持进程重启恢复
- 7 天自动过期
- 每会话最多 50 个任务

#### 6. 记忆系统

```java
MemoryFactory → MemorySolutionMdImpl（基于 Markdown 文件的记忆存储）
```

- 支持工作区隔离（`memoryIsolation`）
- 按工作区路径缓存 MemorySolution 实例
- 存储在 `.soloncode/memory/` 下

#### 7. LSP 集成

内置 14 种语言的 LSP 服务器配置：

Java, TypeScript, Go, Python, Rust, C/C++, C#, Ruby, PHP, Bash, Lua, Dart, Swift, Kotlin, YAML

- 默认禁用，用户按需启用
- 用户自定义同名配置可覆盖系统默认
- 通过 `engine.getLspTalent()` 统一管理

#### 8. 工作区目录结构

```
.soloncode/
├── config.yml           # 静态配置
├── settings.json        # 动态配置
├── AGENTS.md            # Agent 行为指令
├── sessions/            # 会话数据
│   ├── cli/
│   ├── web-xxx/
│   └── acp-xxx/
├── skills/              # 技能（全局 + 工作区）
├── agents/              # 子代理定义
├── commands/            # 自定义命令
├── memory/              # 跨会话记忆
├── download/            # 下载文件
├── channels/            # IM 通道数据
└── extensions/          # 扩展
```

### 优势

1. **100% Java 开源**：Java 8~26 兼容，企业级可审计
2. **Provider 无关**：不绑定任何特定 LLM 供应商，配置即用
3. **四门户统一**：CLI/Web/Desktop/ACP 共享同一引擎，一致的 Agent 体验
4. **IM 原生集成**：微信/飞书/钉钉通道，AI 编码 Agent 直接接入企业 IM
5. **ConfigTalent**：AI 自管理配置扩展（动态添加模型/MCP/API），独特创新
6. **Loop 系统**：定时循环 AI 任务，支持 cron 表达式，适合监控/巡检场景
7. **AGENTS.md 定制**：类似 CLAUDE.md 的行为定制，但基于纯 Markdown
8. **上下文压缩**：自动 LLM 摘要历史，降低长会话 token 消耗
9. **HITL 审批**：bash 命令执行前人工审批，安全可控
10. **LSP 深度集成**：14 种语言内置 LSP 配置，IDE 级代码智能

### 劣势

1. **代码规模有限**：soloncode-cli 仅 ~74 个 Java 文件，核心逻辑依赖上游 solon-ai-harness
2. **测试薄弱**：测试文件仅 9 个，多为集成测试，缺乏单元测试
3. **IM 通道安全**：微信/飞书/钉钉的 CredentialStore 使用明文存储
4. **会话数据格式**：ndjson 文件存储，缺乏索引，大量会话时性能堪忧
5. **无插件系统**：不像 OpenCode 有完整的 plugin 架构，扩展性受限
6. **文档主要中文**：限制国际社区参与
7. **LSP 默认禁用**：需要用户手动启用，初次体验门槛较高
8. **单用户设计**：无多用户/多租户支持，不适合团队部署

### 与同类产品的对比

| 特性 | SolonCode | Claude Code | OpenCode |
|------|-----------|-------------|----------|
| 语言 | Java | TypeScript | TypeScript (Effect) |
| 开源 | 100% | 闭源 | Apache 2.0 |
| 运行环境 | Java 8~26 | Node.js | Bun |
| 多门户 | CLI + Web + Desktop + IM | CLI | CLI + Web + Desktop |
| IM 集成 | 微信/飞书/钉钉 | 无 | 无 |
| Agent 协议 | ACP | 无 | 无 |
| 定时任务 | Loop (cron) | 无 | 无 |
| 配置动态性 | ConfigTalent（AI 自管理） | 手动 | 手动 |
| 插件系统 | 扩展目录 | 无 | 完整 plugin |
| HITL | 可选 | 默认 | 默认 |
| 记忆系统 | Markdown 文件 | 无 | SQLite |

### 与 Nop 平台的关联

#### 可借鉴

1. **多门户统一引擎**：SolonCode 的 Portal 模式——一个 HarnessEngine 服务多个前端——可直接启发 Nop AI 的多通道接入设计
2. **ConfigTalent**：AI 自管理配置扩展是独特模式，Nop 可通过 Delta 定制机制实现类似效果
3. **AGENTS.md 定制**：与 Nop 的 AGENTS.md 概念一致，但 SolonCode 的实现更简洁（纯 Markdown，无 DSL）
4. **Loop 定时任务**：基于原生 IJobManager 的 cron 调度 + JSON 持久化 + 进程重启恢复，是 Agent 监控场景的参考实现
5. **IM 通道路由**：`Channel` 接口 + `WebStreamBuilder` 路由表模式，可用于 Nop 企业 IM 集成
6. **ACP 协议**：Agent Communication Protocol 实现，为 Nop Agent 互操作提供标准参考
7. **LSP Talent 预注册**：14 种语言内置 LSP 配置但默认禁用的模式，平衡了开箱即用和资源消耗
8. **上下文压缩**：LLM 自动摘要 + 窗口滑动，对 Nop AI 长会话管理有参考价值

#### 不适用

- Solon IoC 与 Nop IoC 差异（但同样无 private field 注入）
- JLine 终端库（Nop 可选择其他方案）
- snack4 JSON 库
- Jetty 嵌入式 Web 容器

## Conclusion

SolonCode 是 Java AI 编码 Agent 生态中产品化程度最高的项目之一。其最大差异化在于：(1) 多门户统一引擎 + IM 原生集成，使 AI Agent 从 CLI 工具升级为全通道服务；(2) ConfigTalent 让 AI 自管理能力扩展，是 Agent 自进化的雏形；(3) Loop 定时任务系统，填补了 Agent 监控/巡检场景的空白。对 Nop 最有价值的借鉴是 Portal 模式（多通道统一引擎）、ConfigTalent（AI 自管理配置）、Loop 调度（Agent 定时任务）和 IM 通道路由设计。

## Open Questions

- [ ] SolonCode 的 Portal 模式能否映射到 Nop 的多端接入架构？
- [ ] ConfigTalent 的 AI 自管理配置模式是否适合 Nop 的 Delta 定制机制？
- [ ] Loop 定时任务系统的 JSON 持久化 + IJobManager 调度方案是否可直接在 Nop 中复用？
- [ ] ACP 协议是否应作为 Nop Agent 互操作的标准化方向？

## References

- ~/ai/soloncode/README.md
- ~/ai/soloncode/soloncode-cli/src/main/java/org/noear/solon/codecli/
- ~/ai/solon-ai/ (Solon AI 框架，上游依赖)
- https://solon.noear.org/article/soloncode
- https://github.com/opensolon/solon-ai
