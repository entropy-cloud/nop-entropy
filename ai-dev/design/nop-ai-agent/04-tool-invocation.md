# Nop AI Agent 工具调用架构

**日期**：2026-06-06
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
