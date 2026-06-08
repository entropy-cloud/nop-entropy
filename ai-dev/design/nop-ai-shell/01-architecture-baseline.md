# nop-ai-shell Architecture Baseline

**日期**：2026-06-08
**状态**：active

---

## 一、系统分层

```
┌─────────────────────────────────────┐
│  Command Line String                │  用户/AI 输入
├─────────────────────────────────────┤
│  Parser Layer                       │  词法分析 + 语法分析 → AST
│  (BashLexer → BashSyntaxParser)     │
├─────────────────────────────────────┤
│  AST Model Layer                    │  不可变表达式树
│  (CommandExpression hierarchy)      │  定义在 04-bash-syntax.md
├─────────────────────────────────────┤
│  Executor Layer                     │  AST → 异步执行
│  (ShellCommandExecutor)             │  管道接线、异步调度、回退判断
├──────────────┬──────────────────────┤
│ IO Layer     │ External Shell Layer │
│ (IShellInput │ (IShellRunner 来自   │
│ /IShellOutput)│  nop-shell, 可选)   │
├──────────────┴──────────────────────┤
│  Command Layer                      │  具体命令实现
│  (IShellCommand implementations)    │  读写 IShellInput/Output
└─────────────────────────────────────┘
```

**关键约束**：层间依赖只能向下。Command 不依赖 Executor，Executor 不依赖 Parser。External Shell Layer 是可选的，未配置时不影响任何行为。

## 二、核心对象职责契约

### Parser Layer

| 对象 | 职责 | 不负责 |
|------|------|--------|
| `BashLexer` | 将字符串分词为 `Token` 列表 | 语义分析 |
| `BashSyntaxParser` | 将 `Token` 列表构建为 `CommandExpression` AST | 执行、环境展开 |

### AST Model Layer

定义在 `04-bash-syntax.md`，不在本文档重复。

### Executor Layer

| 对象 | 职责 | 不负责 |
|------|------|--------|
| `ShellCommandExecutor` | 动态遍历 AST 并执行，管理管道、环境变量、工作目录，决定回退 | 不做语法解析，不实现命令逻辑 |
| `ICommandChecker`（来自调用方） | AST 预检：在执行前遍历 AST，检查每个命令是否满足安全/权限要求 | 不执行命令 |

`ShellCommandExecutor` 的执行模型是**AST 解释器**（interpreter），不是 DAG 编排器。执行器通过 `CommandVisitor` 递归遍历 AST，走到哪个节点才执行哪个节点。不预构建执行图。

**重要约束**：`ShellCommandExecutor` 持有可变状态（`exportedEnv`、`currentWorkingDir`），因此**不是线程安全的**。一个 executor 实例对应一个逻辑 shell session。并发场景需每个 session 独立实例。

### IO Layer

| 对象 | 职责 | 不负责 |
|------|------|--------|
| `IShellOutput` | 写入数据，可转换为 `IShellInput` 供下游消费 | 不感知管道拓扑 |
| `IShellInput` | 读取数据，阻塞直到数据到达或 EOF | 不感知数据来源 |
| `BlockingQueueShellOutput` | 管道阶段间的连接器，基于 `LinkedBlockingQueue` | 不负责命令执行 |

### External Shell Layer（可选）

| 对象 | 职责 | 不负责 |
|------|------|--------|
| `IShellRunner`（来自 nop-shell） | 通过 `ProcessBuilder` 执行外部 OS 命令 | 不感知管道、不解析 AST |
| `ICommandChecker` | AST 预检，遍历整棵 AST 校验每个命令是否满足预设要求 | 不执行命令、不感知执行时机 |
| `ExternalCommandAdapter` | 将 `SimpleCommand` + `IShellInput`/`IShellOutput` 桥接到 `IShellRunner` | 不感知 AST 结构 |

**依赖方向**：nop-ai-shell → nop-shell（可选依赖）。nop-shell 不依赖 nop-ai-shell。

### Command Layer

| 对象 | 职责 | 不负责 |
|------|------|--------|
| `IShellCommand` | 执行具体命令逻辑，读写 stdin/stdout | 不感知管道、不管理环境 |
| `ShellCommandRegistry` | 命令名 → `IShellCommand` 的注册表 | 不执行命令 |

## 三、模块边界

nop-ai-shell 的依赖：
- `nop-core`（工具类）— 必需
- `nop-api-core`（`ICancelToken`、`FutureHelper`、`NopException`）— 必需
- `nop-commons`（`StringHelper`、`GlobalExecutors` 等）— 必需
- `nop-shell`（`IShellRunner`、`ShellCommand`、`ShellResult`）— **可选**，仅在启用外部回退时需要

nop-ai-shell 不依赖 `TaskExecutionGraph`（它属于 nop-core 的 DAG 编排工具，用于其他场景）。
nop-ai-shell 不依赖任何 AI 相关模块。它是一个通用的嵌入式命令执行框架，AI 只是其使用者之一。

## 四、数据流方向

```
AI Agent
   │
   │ 构造命令行字符串
   ▼
ShellCommandExecutor.execute(commandLine)
   │
   │ 解析 → AST
   │ 动态遍历 AST 执行
   │
   ▼
 ┌─ registry.findCommand(name) ──────────────────────┐
 │                                                    │
 │ 命令已注册？                                       │
 │  ├── YES → IShellCommand.execute(context)          │
 │  │         内置命令，JVM 内执行                     │
 │  │                                                  │
 │  └── NO  → shellRunner != null？                   │
 │            ├── YES → checker.check(cmd, checkContext)？      │
 │            │         ├── PASS → ExternalCommandAdapter │
 │            │         │         桥接到 IShellRunner     │
 │            │         │         → ProcessBuilder        │
 │            │         └── REJECT → exit code 127       │
 │            └── NO → exit code 127                    │
 │                                                     │
 └─────────────────────────────────────────────────────┘
   │
   ▼
┌──────────┐    BlockingQueue    ┌──────────┐    BlockingQueue    ┌──────────┐
│ Command A │ ──────────────────▶ │ Command B │ ──────────────────▶ │ Command C │
│ (内置)    │    (文本行流)       │ (外部git) │    (文本行流)       │ (内置grep)│
└──────────┘                    └──────────┘                    └──────────┘
   │                                                                 │
   ▼                                                                 ▼
EOF signal                                                      ExecutionResult
                                                                  (exitCode, stdout, stderr)
```

命令解析优先级：
1. `ShellCommandRegistry` 中已注册的内置命令 → JVM 内执行
2. 未注册但 `IShellRunner` 已配置且已通过 `ICommandChecker` 预检 → 外部 OS shell 执行
3. 以上都不满足 → exit code 127 "command not found"

关键约束：
1. 管道中数据只能从左向右流（stdout → stdin），不可反向
2. 每个管道阶段结束时必须 close 输出，发送 EOF 信号给下游
3. 所有阶段并发启动，不建立顺序依赖
4. 外部命令与内置命令在管道中享有同等地位，通过 `ExternalCommandAdapter` 适配到 `IShellOutput`
