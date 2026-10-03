# Nop AI Agent 工具调用架构

**日期**：2026-06-06（更新于 2026-09-28）
**范围**：Agent Engine Layer 的工具发现、执行和并行策略
**状态**：active

---

## 一、设计结论

1. 工具发现通过 `agent.xdef` 的 `<tools>` 声明 → 引擎按名称加载 `.tool.xml` → 构建 LLM 可见的工具 schema
2. 工具执行流经 PRE_ACTING hook → IApprovalGate 检查 → 执行器 → POST_ACTING hook → 结果写回
3. 并行工具执行通过 `call-tools.xdef` 的 `parallel` 属性控制
4. 保持 XML Tool DSL 为主格式，JSON Schema 作为中间转换格式（Phase 2）

## 二、工具发现

```
agent.xdef 中的 <tools> 声明工具名列表
  → AgentEngine 根据 tool name 加载对应的 .tool.xml
  → 构建 LLM 可见的工具 schema
  → 工具 schema 注入 LLM 请求
```

### 2.1 实现机制：VFS + XLang DSL 组件模型注册表（disambiguation）

> **本节是 AI/读者消歧节**。"按 tool name 加载 `.tool.xml`" 这一行容易让读者脑补成"注解反射扫描"——**事实并非如此**。本节固化 nop-ai-toolkit 的实际工具发现机制。

工具定义位于 VFS（Virtual File System）路径 `/nop/ai/tools/<name>.tool.xml`（由 `nop-ai-toolkit/src/main/resources/_vfs/nop/ai/tools/` 提供）。发现与执行链路：

```
ToolManagerImpl.listTools()                           // io.nop.ai.toolkit.manager
  └─ VirtualFileSystem 遍历 /nop/ai/tools/ 目录
       └─ 每个 *.tool.xml 文件 → 工具名 = 文件名去除 .tool.xml
            └─ ToolManagerImpl.loadTool(name)
                 └─ ResourceComponentManager.loadComponentModel(path)
                      └─ 解析 XLang XML → 生成 AiToolModel 对象（IToolDefinition）
```

执行链路：

```
IToolManager.callTool(toolName, call, context)
  └─ IToolCallInterceptor 链（每个 interceptor.beforeCall 拦截）
  └─ IToolExecutorProvider.getExecutor(toolName)
       └─ IToolExecutor.executeAsync(call, context)
            └─ IToolCallInterceptor.afterCall 收尾
```

### 2.2 与注解扫描机制的关键差异

| 维度 | nop 当前实现 | 注解扫描（Spring `@Component` / Quarkus `@Tool` 等） |
|---|---|---|
| 定义位置 | VFS `/nop/ai/tools/*.tool.xml`（XLang XML DSL） | Java 类（`@Component` / `@Tool` 等） |
| 发现机制 | VFS 枚举 + `ResourceComponentManager.loadComponentModel(path)` 解析 XML | ClassPath 扫描 + 反射读取注解 |
| 注册时机 | IoC 启动期（XLang DSL 编译/缓存） | IoC 启动期（注解处理器） |
| 配置 vs 代码分离 | 配置（XML DSL）与执行代码（`IToolExecutor` Java 实现）分离 | 通常耦合在同一个 Java 类 |
| Delta 定制 | 原生支持（XLang Delta 机制覆盖 XML） | 通常需要额外 hook |
| 跨模块注册 | VFS 资源继承（`_vfs` 多 module 自动合并） | 包扫描 + `@SpringBootApplication` 配置 |

**重要**：`grep -rn "@Tool\b" nop-ai/nop-ai-toolkit/src/main` 返回 **0 命中**——**nop 没有 `@Tool` 注解**。所有工具都是 `.tool.xml` + `IToolExecutor` Java 实现的组合，工具描述（name/description/parameters schema）与执行逻辑（executeAsync）在不同抽象层。

**为什么这样设计**：XLang DSL + VFS 资源模型是 Nop 平台的核心抽象——模型与代码分离，让 Delta 定制、xdef schema 校验、跨 module 资源合并等机制可以统一应用到工具层。如果走注解扫描路径，就脱离了 XLang 生态，无法享受 Delta + 元编程。

### 2.3 工具扩展点

新工具的实现路径（不需要改框架代码）：
1. 创建 `/nop/ai/tools/<name>.tool.xml`（受 `tool.xdef` schema 校验）
2. 实现 `IToolExecutor` 接口（`getToolName()` + `executeAsync(AiToolCall, IToolExecuteContext)`）
3. 注册 executor 到 `IToolExecutorProvider`（默认 `DefaultToolExecutorProvider`，可通过 `ToolManagerImpl.setExecutorProvider()` 覆盖）

参考实现：`AskOracleExecutor.java`（`io.nop.ai.toolkit.tools`）—— 91 行 Java（live `wc -l`，2026-09-15）+ 对应 `ask-oracle.tool.xml` DSL 的最小完整实现。

## 三、工具执行流程

```
LLM 返回工具调用（XML 格式，解析为 ToolCall 对象）
  → PRE_ACTING hook（可 block）
  → IApprovalGate 检查（高风险操作需人类审批）
  → 工具执行器执行
  → POST_ACTING hook（可修改结果）
  → 结果写回消息历史
```

## 四、并行工具执行

- `call-tools.xdef` 的 `parallel` 属性控制是否并行
- `maxConcurrency` 限制并发数：**已在 `ToolManagerImpl.executeParallel` 真正生效**（P2 round-4 收口）——`parallel=true` 且 `maxConcurrency` 为正整数时，批次内同时在飞的工具调用数 ≤ `maxConcurrency`，超限调用**排队等待空位而非丢弃**，批次内每个调用最终都会执行；`maxConcurrency` 为 null/非正数 = 无上限（全部立即提交）。语义契约见 `IToolManager.callTools` javadoc
- 引擎应支持并行执行多工具调用

### 4.1 会话作用域工具（update-todos）

`update-todos` 的 todo 表按 **session 作用域**键控（P2 round-4 收口）：`IToolExecuteContext.getSessionId()`（agent 引擎的 `AgentToolExecuteContext` 提供 sessionId；toolkit 的 `ToolExecuteContext` builder 可显式设置）作为表键——同一会话内 read/write 共享列表，跨会话完全隔离（A 会话 write 空列表只清自己的表）。**无会话标识时 fail-closed**：返回显式错误，绝不静默读写进程级全局表。

### 4.2 skill 工具 load 语义

`skill` 工具（`SkillExecutor`）的 `load` 动作返回**真实技能内容**（P2 round-4 收口）：从 VFS `/nop/skills/<name>/` 目录读取文件清单，并内联首个描述文件（README.txt/README.md/SKILL.md/skill.md/description.txt，内容超 4000 字符截断标注）的内容；目录不存在/不可读返回显式错误。不再有"目录存在即成功"的空操作路径。

### 4.3 SSRF 防护实际装配

AI HTTP 工具（`http-request`/`graphql-query`）的 SSRF 防护为**两阶段**（P2 round-4 收口，与 javadoc 矛盾已消除）：

1. **pre-flight 文本校验**：`SsrfAddressGuard.validateHost`（IP 字面量规范化 + 内网/云元数据黑名单）；
2. **解析时权威校验**：执行器在发请求前用 `SsrfGuardDnsResolver`（默认 `IDnsResolver`，可经 `setDnsResolver` 注入替换）解析目标主机名——解析到内网/元数据地址（含 DNS rebinding 多答案集）即 fail-closed，连接不建立。

**未接线面（显式登记）**：JDK HttpClient 与 OkHttp 不消费 `HttpClientConfig.dnsResolver`，因此 redirect-hop 级的 client 内解析拦截只对 Apache HttpClient 成立——生产部署如需完整 redirect-hop 防护，须把 `SsrfGuardDnsResolver` 装配进 `HttpClientConfig.dnsResolver` 并使用 Apache client；执行器级解析校验对全部 client 生效。

### 4.4 长时工具：超时分层与进度证据（已裁定目标形态）

- **现状**：`toolTimeoutMs` 默认 300s（`DefaultAgentEngineConfig.java:152`），fanout 期 `orTimeout` 超时转错误 tool result（`AgentToolDispatcher.java:271-283`）；调大超时则 round 阻塞至全部工具完成。数十分钟级工具（构建命令等）在两条路径下均不可用。
- **目标形态**：机械超时降级为兜底防线，按工具类型分层配置（build/命令类显著高于快调用）；卡死/死循环的**主判定交由模型**——心跳消息携带沙箱增量输出缓冲尾部作为进度证据（`NoOpSandboxBackend.java:101-113`、`DockerSandboxBackend.java:255` 的 capturedRef 增量 drain 已在位，无需新增采集机制）。
- **配套约束**：等待期 steering/心跳唤醒的循环侧语义见 `nop-ai-agent-react-engine.md` §5.5；本篇边界是工具执行面的超时参数分层与进度证据来源。

### 4.5 工具级后台模式（已裁定第二形态）

- **决策**：长任务的第二种承载形态——工具调用参数增加模型可调的 `runInBackground` 属性（camelCase，对齐既有 `timeoutMs`/`workingDir` 命名格式；语义对齐 zcode 的 `run_in_background`）。置真时调用**立即返回启动确认**（taskId + 输出文件路径），真实结果不占用本轮 tool result，而是经**完成通知桥**入 mailbox 作为新输入驱动下一轮。
- **配套构件**：跨调用可寻址的后台任务 registry（状态/pid/输出文件/进度快照——stdout/stderr tail+bytes，证据源复用沙箱 capturedRef 增量缓冲）；任务管理工具面（任务输出读取 + 停止）；读取与完成通知共享"恰好一次"claim 令牌，杜绝双份结果通道（zcode 的 TaskOutput 工具已废弃即此教训：双通道导致上下文重复）。
- **形态边界**（已由 `nop-ai-agent-async-tool-execution.md` §三.7 收口）：后台化要求模型预判长任务并主动选择，解决"模型主动并行"；占位+唤醒（§4.4 / `nop-ai-agent-react-engine.md` §5.5）解决"未预判长任务 + steering 响应"。两者正交互补，共享 registry/进度快照/通知桥/超时分层构件。后台任务超时独立分层（`backgroundTaskTimeoutMs`，默认 3_600_000，策略封顶），subagent 类（call-agent）不开放模型自选后台（member spawn 已有异步编排面，避免双通道）；开放面为声明 `background` 能力 tag 的 bash/shell 类长时工具。
- **既有 `&` 语义处置（已登记缺陷）**：`nop-ai-shell` 的 `cmd &` 启动即失联（`ShellCommandExecutor.java:346-361`——jobId 为实例内计数器，无查询/等待/取消/输出回收，结果随流丢弃）。后台模式落地时 `&` 必须接入任务 registry 获得闭环，或显式禁用并报错，不得维持现状。

## 五、JSON Schema 兼容

**决策**：保持 XML Tool DSL 作为主要格式，增加 JSON Schema 格式作为工具参数的中间转换格式。

**理由**：
- 部分 LLM Provider 更擅长处理 JSON 格式的工具定义
- 可以作为 `.tool.xml` 到 LLM prompt 的中间格式，而不改变 XML DSL 本身

**阶段归属**：Phase 2（可插拔增强），不在 Phase 1 核心闭环中。Phase 1 使用现有 XML Tool DSL 即可。

**拒绝了**：完全切换到 JSON Schema。理由是 XML DSL 是 Nop XLang 生态的一部分，放弃它会破坏一致性。

---

## 与其他文档的关系

- `02-execution-model.md` — 本篇嵌入的执行模型（ReAct 循环中的工具执行环节）
- `nop-ai-tool-dsl.md` — 工具 DSL 详细设计（`tool.xdef`、`tool-call.xdef`、`call-tools.xdef`）
- `nop-ai-call-agent-dsl.md` — call-agent 工具 DSL
- `nop-ai-agent-security-and-permissions.md` — 工具执行的安全边界

## 修复前后差异审计（WI12，plan 2293）

- **记录通道**：TOOL_CALL_STARTED 事件 payload 扩展——`callId`（join key，恒在）；repair 变更时附加 `repairApplied=true` + `nameBefore`/`nameAfter` + `argsBefore`/`argsAfter`（getArgumentsText，截断 512 字符）。变更检测 = 引用不等快速路径 + name/argsText 字符串权威比较（ChainRepairer stage 为 copy-on-change，引用不等是必要非充分条件）。
- **一致性**：审批所见（checkpoint chain 收 repair 后 call）=执行所用（AiToolCall 构造同 call）——差异审计补的是"LLM 实际发出 vs 审批/执行"的可见性。heartbeat 唤醒轮派生调用走同一 repair 阶段（adjudicateHeartbeatReply 内 repair→checkpoint→dispatch 一致）。
- **边界**：audit logger 通道为 follow-up（executor 无句柄）；无 repair 时 repairApplied 缺席（前向兼容 payload 契约）。

## 工具输出预算（WI16，plan 2297）

**契约**：输出预算是模型可调的工具参数（unreal 对位：per-call `max_output_length`，nop 保留参数名 `maxOutputChars`）。解析优先级（`ToolOutputBudgetResolver`）：per-call 参数（钳制到 ≤ 工具声明的 max-chars，再钳到全局硬上限 1,000,000）→ 工具 `<output-budget max-chars>` 声明 → 默认 8000（历史值不变）。spill 开关 = 工具 `spill` 声明（默认 true）。`NON_TRUNCATABLE_TOOLS`（ask-oracle/ask-human）语义最高：永不截断、永不 spill。

**截断协议**（`ToolResultTruncator.applyBudget`）：头尾保留 + 精确移除数标记；head/tail 按预算等比缩放（75%/25%，上限既有 6000/1000 窗口），预算 ≤32 硬切——任意正整数预算不越界、不虚标。

**前缀缓存裁定**：不向任何已发布工具 schema 自动注入预算参数——全部已发布工具 LLM 定义面字节不变；工具要向模型宣传该旋钮，自行在 `<schema>` 加可选参数（opt-in 才有定义面代价）。spill 指针协议不变（`[SPILL_REF id=...] Full result stored; use read-spill to retrieve.`）。

**迁移说明**：tool.xdef 新增可选 `<output-budget xdef:name="AiToolOutputBudgetModel" .../>`——additive，不声明的工具零改动、运行时走默认；xdef 属平台级契约（nop-kernel/nop-xdefs），生成模型 `AiToolOutputBudgetModel` 随构建 precompile 自动再生。

## 并发安全与路径串行（WI17，plan 2298）

**契约**：工具以 `tool.xdef` 的 `<concurrency-safe>`（默认 false=不安全）声明并发安全；不安全工具的调用若携带保留路径键（`path`/`file_path`/`filePath`，以 `AiToolCall.getInput()` JSON 为准——dispatch 路径唯一数据源），在 `ToolManagerImpl.callTool`（全进程唯一 chokepoint：dispatcher 逐调用 fan-out、callTools、CLI、call-agent 子代理全经此）按路径串行：同路径排队（非阻塞未来链，执行体惰性——排队调用不占线程、不提前启动），异路径并行。默认 false 保守正确；只读工具显式声明 true 豁免。

**锁粒度裁定**：manager 实例级全局（跨 session 保守——同进程写同一文件本就应互斥）。与 callTools 面的 Semaphore 叠加语义：排队占 permit 至真实完成，同路径队列可耗尽 permit 预算推迟异路径调用（吞吐公平性，无死锁——permit 释放只依赖工具完成，单锁无嵌套）。

**已知 residual**：路径键不规范化（a/b.txt 与 ./a/b.txt 视为不同键）；带 path 参数且同步等待嵌套同路径 callTool 的自定义工具自死锁（现网无此形态，登记为自定义工具契约限制）；提取失败（畸形输入）=不串行的文档化降级。
