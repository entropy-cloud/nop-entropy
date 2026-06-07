# nop-ai-shell 模块设计

**日期**：2026-06-07
**范围**：`nop-ai-shell` — 进程内虚拟 Shell 执行引擎
**状态**：active
**依赖模块**：`nop-ai-toolkit`（Tool 接口）、`nop-core`（IResourceStore）

---

## 一、设计结论

1. nop-ai-shell 作为 `IToolExecutor` 的替代实现接入 tool 调用管线，与真实 `BashExecutor` 通过 IoC/Delta 选择
2. 所有文件操作通过 `IToolFileSystem`，不直接访问 `java.io.File`
3. 命令白名单——只有注册的 `IShellCommand` 可以执行，未注册命令返回 exit code 127
4. shell 命令直接调用 `IToolFileSystem`，不调用 toolkit 的 `IToolExecutor`，避免两层异步嵌套

---

## 二、背景与动机

当前 Agent 的 `bash` 工具通过 `ProcessBuilder` 启动真实 OS 进程执行命令。nop-ai-shell 提供进程内虚拟 Shell 替代方案，解决以下问题：

1. **安全风险**：LLM 生成的命令直接在 OS shell 中执行，存在命令注入、路径穿越、数据外泄等风险
2. **不可控**：真实进程难以限制资源消耗（输出大小、执行时间），进程泄露难以清理
3. **不可测试**：依赖真实文件系统和 OS 环境，无法在测试中隔离
4. **无法虚拟化**：多 Agent 并行时无法为每个 Agent 提供独立的虚拟工作目录

项目已有 `nop-ai-shell` 模块，实现了完整的 bash 语法 parser 和命令执行框架，但与 toolkit 完全未连接。

---

## 三、现有代码盘点

### 3.1 nop-ai-shell 已有

| 组件 | 状态 | 说明 |
|------|------|------|
| BashLexer + BashSyntaxParser | ✅ 完成 | 递归下降 parser，覆盖管道/逻辑/重定向/环境变量/子shell/分组 |
| AST 模型 (6 种节点) | ✅ 完成 | SimpleCommand, PipelineExpr, LogicalExpr, GroupExpr, SubshellExpr, BackgroundExpr |
| IShellCommand 接口 | ✅ 完成 | name, description, usage, execute(ctx) → exitCode |
| IShellCommandExecutionContext | ✅ 完成 | stdin/stdout/stderr, environment, workingDirectory, arguments, resourceStore, cancelToken |
| ShellCommandRegistry | ✅ 完成 | name + alias 注册 |
| ShellCommandExecutor | ✅ 完成 | AST → 执行图，管道(TaskExecutionGraph)，逻辑短路，重定向，环境变量传播 |
| I/O 抽象 | ✅ 完成 | IShellInput/IShellOutput + BlockingQueue/List/File/PrintStream/Duplex/Tee |
| 命令实现 | ⚠️ 仅 3 个 | echo, cd, mock-ls |
| 集成测试 | ❌ @Disabled | ShellCommandExecutorTest 全部禁用 |

### 3.2 nop-ai-toolkit bash 工具已有

| 组件 | 说明 |
|------|------|
| `bash.tool.xml` | 定义 `<bash>` 的 XML schema: `<command>`, `<envs>`, `workingDir`, `timeoutMs` |
| `BashExecutor` | `IToolExecutor` 实现，通过 `ProcessBuilder` 调用 `sh -c` / `cmd -c` |
| `IToolExecuteContext` | 提供 getWorkDir, getEnvs, getFileSystem, getCancelToken |
| `IToolFileSystem` | 文件系统抽象：readText, writeText, listDirectory, glob, grep 等 |

### 3.3 接口差距

两套系统完全没有连接：

| 维度 | nop-ai-shell | nop-ai-toolkit |
|------|-------------|----------------|
| 命令接口 | `IShellCommand.execute(ctx)` → exitCode | `IToolExecutor.executeAsync(call, ctx)` → `CompletionStage<AiToolCallResult>` |
| 文件系统 | `IResourceStore`（nop-core） | `IToolFileSystem`（nop-ai-toolkit） |
| I/O 模型 | line-based（IShellInput/IShellOutput） | 无等价概念 |
| 执行上下文 | `IShellCommandExecutionContext` | `IToolExecuteContext` |

---

## 四、接口统一方案

### 4.1 核心决策：nop-ai-shell 依赖 nop-ai-toolkit

**决策**：nop-ai-shell 新增对 nop-ai-toolkit 的 compile 依赖。

**理由**：
- `IToolFileSystem` 是 toolkit 文件操作的唯一抽象，shell 命令必须复用它
- `IToolExecutor` 是 Agent Engine 调用工具的唯一入口，模拟 shell 必须实现它
- 让 shell 依赖 toolkit 而非反过来，保持 toolkit 不感知 shell

**需要对 toolkit 做的改动**：无。toolkit 不需要任何修改。

**需要对 shell 做的改动**：
1. `IShellCommandExecutionContext` 新增 `getFileSystem()` → `IToolFileSystem`
2. 新增 `ShellBashExecutor` 实现 `IToolExecutor`，作为模拟 shell 的入口
3. 新增适配层，将 `IToolExecuteContext` 转换为 `IShellCommandExecutionContext`

### 4.2 调用链

```
Agent Engine
    │
    ▼
IToolExecutor.executeAsync(AiToolCall, IToolExecuteContext)
    │
    ├── BashExecutor (真实 OS shell)     ← 现有，不改
    │
    └── ShellBashExecutor (虚拟 shell)   ← 新增，在 nop-ai-shell 中
            │
            ▼
        ShellCommandExecutor.execute(commandLine, context)
            │
            ▼
        IShellCommand.execute(IShellCommandExecutionContext)
            │
            ▼
        IToolFileSystem (来自 IToolExecuteContext)
```

### 4.3 ShellBashExecutor 的职责

作为 `IToolExecutor` 的实现，负责：
- 从 `AiToolCall` 提取 `<command>`、`<envs>`、`workingDir`、`timeoutMs`
- 构建 `IShellCommandExecutionContext`，将 `IToolExecuteContext` 的文件系统、环境变量、工作目录、取消令牌传入
- 调用 `ShellCommandExecutor` 执行命令
- 收集 stdout/stderr，构建 `AiToolCallResult`（status, exitCode, output, error）

### 4.4 IToolFileSystem 与 Shell 命令的集成

**设计决策**：`IShellCommandExecutionContext` 新增 `getFileSystem()` 返回 `IToolFileSystem`，所有 shell 命令的文件操作通过此接口。

**理由**：shell 命令直接调用 `IToolFileSystem`，不调用 toolkit 的 `IToolExecutor`。避免两层异步嵌套，且性能更好。

shell 命令中涉及文件操作（cat, ls, grep, find 等）必须通过 `IToolFileSystem`，而不是直接访问 `java.io.File`。路径解析遵循：相对路径 → 基于 `workingDirectory` 解析为绝对路径 → `IToolFileSystem.isPathAllowed()` 校验。

### 4.5 toolkit 命令与 shell 命令的复用关系

| toolkit 工具 | 对应 shell 命令 | 底层共用 |
|-------------|----------------|---------|
| read-file | cat, head, tail | `IToolFileSystem.readText/readLines` |
| write-file | shell 重定向 `>` `>>` | `IToolFileSystem.writeText` |
| list-dir | ls | `IToolFileSystem.listDirectory` |
| glob | find | `IToolFileSystem.glob` |
| grep | grep | `IToolFileSystem.grep` |
| create-dir | mkdir | `IToolFileSystem.mkdirs` |
| delete-file | rm | `IToolFileSystem.delete` |
| copy-file | cp | `IToolFileSystem.copy` |
| move-file | mv | `IToolFileSystem.move` |

---

## 五、拒绝了什么

### 5.1 拒绝：让 toolkit 依赖 shell

**方案**：在 toolkit 的 `BashExecutor` 中判断是否使用模拟 shell。

**拒绝理由**：toolkit 是工具层，不应感知具体的 shell 实现策略。模拟 shell 是一种可选的执行策略，应该通过 IoC/Delta 定制注入，而不是 toolkit 硬编码。

### 5.2 拒绝：shell 命令通过调用 toolkit 的 IToolExecutor 实现文件操作

**方案**：cat 命令内部调用 `read-file` 工具（`toolManager.callTool("read-file", ...)`）。

**拒绝理由**：
- 两层异步嵌套（shell executor → tool executor），性能差
- shell 命令需要 stdin/stdout 管道语义，tool executor 没有等价概念
- 循环依赖风险（shell 调 tool，tool 调 shell）

### 5.3 拒绝：在 nop-ai-shell 中实现完整的 bash 兼容

**方案**：使用类似 brush-shell 的完整 bash 实现。

**拒绝理由**：
- 完整 bash 兼容需要 15000+ 行代码（brush-shell 规模），收益不成比例
- AI Agent 只使用 bash 的基础子集（管道、重定向、变量展开），控制流和函数极少使用
- 完整 bash 兼容反而增加攻击面（允许更复杂的命令构造）

### 5.4 拒绝：保留 IResourceStore 作为 shell 的文件系统

**方案**：shell 命令继续使用 `IResourceStore`（nop-core），独立于 toolkit 的 `IToolFileSystem`。

**拒绝理由**：
- 两套文件系统抽象会导致路径校验不一致（`IResourceStore` 没有路径权限检查）
- toolkit 的 `LocalToolFileSystem` 已有完善的 `isPathAllowed()` 和路径规范化
- Agent Engine 通过 `IToolFileSystem` 管理文件访问控制，shell 必须在同一控制链路内

---

## 六、命令实现分层

### 6.1 P0 — 基础文件操作（必须实现）

| 命令 | 实现方式 | 使用的 IToolFileSystem 能力 |
|------|---------|---------------------------|
| `cat` | 读取文件内容输出到 stdout | readText, readLines |
| `ls` | 列出目录内容 | listDirectory, isFile, isDirectory |
| `cd` | 修改 workingDirectory（已有） | — |
| `pwd` | 打印 workingDirectory | — |
| `mkdir` | 创建目录 | mkdirs |
| `rm` | 删除文件/目录 | delete |
| `cp` | 复制文件 | copy |
| `mv` | 移动/重命名文件 | move |
| `touch` | 创建空文件 | writeText(path, "", false) |
| `head` | 输出文件前 N 行 | readLines |
| `tail` | 输出文件后 N 行 | readLines |
| `wc` | 统计行数/词数/字符数 | readText, countLines |
| `find` | 查找文件（委托给 glob） | glob |
| `grep` | 搜索文件内容 | grep |
| `sort` | 排序 stdin 或文件内容 | readText → 排序 → stdout |
| `xargs` | 从 stdin 构建并执行命令 | stdin + registry 查找 |
| `tee` | 同时输出到 stdout 和文件 | stdout + writeText |
| `echo` | 打印参数（已有） | — |
| `true` / `false` | 返回 exitCode 0/1 | — |
| `test` / `[` | 条件判断 | exists, isFile, isDirectory |
| `env` | 打印环境变量 | — |
| `which` | 查找命令是否注册 | registry |
| `basename` / `dirname` | 路径处理 | — |
| `tr` | 字符替换 | stdin → transform → stdout |
| `sed` (基础) | 流编辑（仅 s 命令） | stdin → transform → stdout |
| `awk` (基础) | 文本处理（仅 print NR NF） | stdin → transform → stdout |

### 6.2 P1 — 构建和版本控制（委托模式）

P1 命令不在 JVM 内模拟，而是通过 `ProcessBuilder` 委托给真实 OS 进程。委托命令必须遵守：
- 工作目录 = `IToolExecuteContext.getWorkDir()`
- 环境变量 = 白名单（仅传安全变量）
- stdout/stderr 大小限制
- 进程组终止策略（超时或取消时 kill 整个进程组）

| 命令 | 实现方式 | 支持的子集 |
|------|---------|-----------|
| `git` | 委托给真实 `git` 进程 | status, log, diff, add, commit, branch |
| `mvn` / `gradle` | 委托给真实进程 | compile, test, package |
| `npm` / `pnpm` | 委托给真实进程 | install, run, test |

### 6.3 P2 — 网络（受限）

| 命令 | 实现方式 | 限制 |
|------|---------|------|
| `curl` (只读) | 委托给 toolkit 的 http-request | 仅 GET/HEAD，禁止 POST 到外部 |
| `wget` | 类似 curl | 受限模式 |

---

## 七、安全设计

### 7.1 命令白名单

`ShellCommandRegistry` 本身就是白名单——只有注册的命令可以执行。未注册命令返回 exit code 127。

### 7.2 路径安全

所有文件操作通过 `IToolFileSystem.isPathAllowed()` 校验：

1. 路径解析：相对路径 → 基于 `workingDirectory` 解析为绝对路径
2. 路径规范化：消除 `..`、`.`、符号链接
3. 权限检查：`isPathAllowed(normalizedPath)`
4. 拒绝模板模式：防止 `/etc/passwd`、`../../../` 等越权访问

### 7.3 资源限制

| 限制 | 机制 | 默认值 |
|------|------|-------|
| 输出大小 | stdout/stderr 行数上限 | 10000 行 |
| 执行超时 | ICancelToken + timeoutMs | 30000ms |
| 管道深度 | PipelineExpr 最大级联数 | 10 |
| 环境变量数 | environment() 上限 | 100 |
| 递归命令数 | xargs 嵌套深度 | 3 |

### 7.4 模拟 shell 固有安全优势

以下风险在 nop-ai-shell 中不可能发生（因为不启动真实进程）：
- 进程注入（无 fork/exec）
- 网络 exfiltration（curl/wget 受限或不存在）
- 系统调用（无 ptrace, no seccomp bypass）
- 文件系统逃逸（所有操作经过 IToolFileSystem）

---

## 八、变量展开设计

现有 parser 不支持变量展开。LLM 经常生成 `$VAR` 和 `${VAR}` 形式的命令。需要在执行前增加展开阶段。

### 8.1 展开阶段

在 `ShellCommandExecutor` 中，AST 构建完成后、命令分发前，新增展开阶段：

```
Parse → AST → ExpansionPhase → Execute
                    ↓
             变量替换: $VAR, ${VAR}, $?
             参数默认值: ${VAR:-default}
             不支持: $(cmd), `cmd`, $((expr))
```

### 8.2 展开规则

| 语法 | 展开为 | 优先级 |
|------|-------|--------|
| `$VAR` / `${VAR}` | environment 中的值，未定义则空字符串 | P0 |
| `$?` | 上一个命令的 exitCode | P0 |
| `$$` | 当前 session ID（或固定值） | P1 |
| `$0` | 当前 shell 名称（固定 "nop-sh"） | P2 |
| `${VAR:-default}` | VAR 存在则用 VAR，否则用 default | P1 |
| `${VAR:+alternate}` | VAR 存在则用 alternate | P2 |

展开规则遵循 bash 语义：单引号内不展开，双引号内展开，无引号时展开。

---

## 九、管道执行模型

`ShellCommandExecutor` 使用 `TaskExecutionGraph` + `BlockingQueueShellOutput` 实现管道——每个命令在独立线程执行，通过 blocking queue 传递行级数据。

需要增加的改进：

1. **管道缓冲区大小限制**：防止一个命令产生无限输出导致 OOM
2. **背压**：消费者慢于生产者时，生产者阻塞
3. **超时传播**：管道中任一命令超时，整个管道取消
4. **pipefail 可配置**：管道中任一命令失败时，可配置是否终止后续命令

管道中间结果不经过 `IToolFileSystem`——仅在内存中传递。只有重定向 (`>`, `>>`) 写入 `IToolFileSystem`。

---

## 十、模块依赖变更

### 10.1 pom.xml 变更

nop-ai-shell 新增对 nop-ai-toolkit 的 compile 依赖。

### 10.2 模块关系

```
nop-ai-agent
    └── depends on → nop-ai-toolkit (tool 接口)
                          └── nop-ai-shell (可选，通过 IoC 注入)
                                                    └── nop-core (已有)
```

`nop-ai-shell` 通过 IoC 注册 `ShellBashExecutor` bean，替换默认的 `BashExecutor`。应用层通过 Nop Delta 定制选择使用真实 OS shell 还是 nop-ai-shell 虚拟 shell。

---

## 十一、实施优先级

### Layer 1：接口对齐

1. `IShellCommandExecutionContext` 新增 `getFileSystem()`
2. 新增 `ShellBashExecutor`（实现 `IToolExecutor`）
3. 新增适配层（`IToolExecuteContext` → shell context）
4. pom.xml 新增 nop-ai-toolkit 依赖
5. 修复 ShellCommandExecutorTest（取消 @Disabled）

### Layer 2：核心命令实现

6. 实现 P0 文件命令：cat, pwd, mkdir, rm, cp, mv, touch, head, tail, wc, find, grep, sort, tee, test
7. 变量展开：$VAR, ${VAR}, $?
8. 改进 LsCommand（从 mock 改为使用 IToolFileSystem）
9. 管道缓冲区大小限制和背压

### Layer 3：增强命令

10. xargs, sed(基础), awk(基础), tr, cut, uniq, diff
11. 变量展开增强：${VAR:-default}
12. git 子命令（委托模式）
13. mvn/gradle/npm（委托模式）

---

## 与其他文档的关系

- `nop-ai-shell-syntax-spec.md` — 支持的 Shell 语法规范（本篇的姊妹篇）
- `04-tool-invocation.md` — 工具调用架构
- `nop-ai-agent-security-and-permissions.md` — 权限和安全设计
- `nop-ai-agent-reliability.md` — 超时和输出限制
- `nop-ai-agent-context-model.md` — Tool 上下文可见性
