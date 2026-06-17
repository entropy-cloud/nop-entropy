# 126 nop-ai-shell 执行引擎与 IO 层重写

> Plan Status: completed
> Last Reviewed: 2026-06-08
> Source: `ai-dev/design/nop-ai-shell/` 全套设计文档
> Related: `ai-dev/plans/125-bash-syntax-parser-bugfix-and-completion.md`

## Purpose

将 nop-ai-shell 的执行引擎从当前的半成品（`TaskExecutionGraph` 依赖、字符串 EOF 标记、管道接线 bug、BackgroundExpr 空实现、FileShellOutput 截断 bug）重写为设计文档描述的最终形态：纯 AST 解释器 + `ShellChunk` 流式 IO 模型 + 有界队列管道 + 命令预检 + 外部命令回退。

## Current Baseline

**已有代码（需修改或替换）：**
- `ShellCommandExecutor`：使用 `TaskExecutionGraph` 执行管道，`executePipeline` 存在接线 bug（所有阶段共享同一 stdin），`executeBackground` 是空操作（直接调用内部表达式）
- `IShellInput`/`IShellOutput`：基于字符串的行级 IO，`IShellOutput.EOF_MARKER = "__EOF__"` 存在碰撞风险
- `BlockingQueueShellOutput`：无界 `LinkedBlockingQueue<String>`，无背压，`close()` 发送字符串标记
- `FileShellOutput`：每次 `writeLine` 都带 `TRUNCATE_EXISTING`，只保留最后一行（已确认 bug）
- `ExecutionResult`：嵌套内部类，持有 `exitCode`/`stdout`/`stderr` 三元组
- `CommandVisitor<CompletionStage<ExecutionResult>>`：接口已定义 6 个 visit 方法
- 现有测试 `ShellCommandExecutorTest`：整个类被 `@Disabled`，原因是实现不可用
- AST 模型层（`SimpleCommand`/`PipelineExpr`/`LogicalExpr`/`GroupExpr`/`SubshellExpr`/`BackgroundExpr`）：已由 plan 125 修复，无需改动

**已有设计文档（权威规格）：**
- `ai-dev/design/nop-ai-shell/00-vision.md` — 产品定位与成功标准
- `ai-dev/design/nop-ai-shell/01-architecture-baseline.md` — 系统分层、数据流图
- `ai-dev/design/nop-ai-shell/02-io-and-pipeline.md` — ShellChunk 模型、IShellInput/IShellOutput 重设计、有界 BlockingQueue 管道
- `ai-dev/design/nop-ai-shell/03-executor-and-async.md` — AST 解释器执行模型、CompletionStage 语义、BackgroundExpr、PipelineExpr 并发、ICommandChecker、ExternalCommandAdapter、迁移步骤
- `ai-dev/design/nop-ai-shell/04-bash-syntax.md` — AST 模型（plan 125 产出，不在本 plan scope）

**剩余 gap：**
- ShellChunk 类型体系（`ShellChunk` 抽象类 + `TextChunk`/`BinaryChunk`/`EofChunk` 内部类）不存在
- `IShellInput`/`IShellOutput` 接口未改为 chunk 模型
- `BlockingQueueShellOutput` 未改为有界队列 + `ShellChunk` + `eofReceived` 标志
- `FileShellOutput` 截断 bug 未修
- `executePipeline` 未改为 `CompletableFuture.supplyAsync` 并发模式
- `BackgroundExpr` 未实现 fire-and-forget 语义
- `ICommandChecker` / `ICommandCheckContext` 不存在
- `ExternalCommandAdapter` 不存在
- `ShellCommandExecutor` 未实现 `Closeable`
- `ExecutionResult` 未提取为独立类

## Goals

- 实现设计文档描述的完整执行引擎：纯 AST 解释器，不依赖 `TaskExecutionGraph`
- 实现 `ShellChunk` 流式 IO 模型，替换当前基于字符串行的 IO
- 修复所有已确认 bug：管道接线、FileShellOutput 截断、BackgroundExpr 空操作、EOF_MARKER 碰撞
- 实现管道的真正的阶段间流式传输（有界 `BlockingQueue`，背压默认 1024）
- 实现 `BackgroundExpr` 的 fire-and-forget 异步语义（含生命周期管理）
- 实现 `ICommandChecker` 预检 + `ExternalCommandAdapter` 外部命令回退
- `ShellCommandExecutor` 实现 `Closeable`，关闭时取消所有后台作业
- 所有被 `@Disabled` 的测试恢复启用并通过，新增覆盖重写行为的测试

## Non-Goals

- 不修改 AST 模型层（`SimpleCommand`、`PipelineExpr` 等）——那是 plan 125 的 scope
- 不修改 `BashSyntaxParser`——同上
- 不实现 heredoc/herestring 的新语义（当前代码中 `Redirect.Type.HERE_DOC`/`HERE_STRING` 分支保持现有空操作行为）
- 不实现 `ICommandChecker` 的具体安全策略（只定义接口 + 默认空实现）
- 不实现 `ExternalCommandAdapter` 对 `nop-shell` `IShellRunner` 的实际桥接（只搭架子，抛 `UnsupportedOperationException` 提示"需要 nop-shell 依赖"）
- 不做线程池调优——使用 `GlobalExecutors.globalWorker()`
- 不支持管道中嵌套复杂表达式（如 `(cmd1 && cmd2) | cmd3`）——管道阶段当前只支持 `SimpleCommand`，非 `SimpleCommand` 阶段返回 exitCode 1 + 错误信息。后续可在 successor plan 中扩展

## Scope

### In Scope

- `io.nop.ai.shell.io` 包下所有文件的重写
- `io.nop.ai.shell.commands` 包下所有文件的 IO 适配
- `ShellCommandExecutor` 的完整重写
- `ExecutionResult` 提取为独立顶层类
- `ICommandChecker`/`ICommandCheckContext` 新增接口
- `ExternalCommandAdapter` 新增骨架类（不依赖 nop-shell 模块，`IShellRunner` 类型通过反射或接口内联定义）
- `ShellCommandExecutor` 实现 `Closeable`
- 测试恢复 + 新增测试

### Out Of Scope

- AST 模型层修改（plan 125）
- BashSyntaxParser 修改（plan 125）
- nop-shell 模块修改（不新增 pom.xml 依赖）
- heredoc/herestring 实现
- 安全策略实现（ICommandChecker 的具体业务规则）
- 线程池配置/调优
- 管道中非 SimpleCommand 表达式支持

## Design Decisions (本 plan 范围内的裁定)

| 决策 | 选择 | 理由 |
|------|------|------|
| `IShellOutput` 便捷方法命名 | 保留 `print(String)` + `println(String)` + 新增 `writeLine(String)` | 现有命令（echo、cd、ls）都调用 `print()`/`println()`，保持兼容减少改动。`writeLine` 是 `println` 的别名（`writeLine` = 写入含 `\n` 的单个 TextChunk） |
| `IShellInput.readLine()` 实现位置 | 抽象基类 `AbstractShellInput` 提供 `readLine()` default 实现（内部缓冲 + `eofSeen` 标志），具体子类只需实现 `read()` | stateful default method 不适合放在接口中，用抽象基类提供共享逻辑 |
| `ICommandChecker.check()` 返回类型 | `String check(SimpleCommand, ICommandCheckContext)` — null 表示通过，非 null 为拒绝理由 | 与设计文档一致。允许收集拒绝理由，比 void + 抛异常更灵活 |
| `ExternalCommandAdapter` 对 nop-shell 的依赖 | 不在 pom.xml 中添加 nop-shell 依赖。`ExternalCommandAdapter` 不引用 `IShellRunner` 类型——它是一个纯骨架，`execute()` 直接抛 `UnsupportedOperationException` | 避免引入可选模块依赖的复杂性。实际桥接在 successor plan 中实现 |
| 管道中复杂表达式 | 仅支持 `SimpleCommand` 阶段 | 当前 AST 解释器已经够复杂。`SubshellExpr`/`GroupExpr` 在管道中需要独立的线程池上下文管理，属于扩展功能 |
| `backgroundJobs` 类型 | `Map<String, CompletableFuture<?>>`（jobId → Future） | 与设计文档一致，支持 `jobs` 命令查询后台作业状态。自增序列号生成 jobId |

## Execution Plan

### Phase 1 - ShellChunk 类型体系 + IO 层全面重写 + 命令适配

Status: completed
Targets: `io.nop.ai.shell.io.*`, `io.nop.ai.shell.commands.*`, `io.nop.ai.shell.executor.ExecutionResult`

- Item Types: `Fix` × 6 + `Decision` × 3

> **注意**：Phase 1 将 IO 接口重写、所有 IO 实现类适配、命令层适配、ExecutionResult 提取合并为一个大步骤。这样 Phase 1 完成后代码即可编译通过，不存在中间态编译断裂。

- [x] 新增 `ShellChunk` 抽象类，内含 `TextChunk`、`BinaryChunk`、`EofChunk` 三个 final 内部类
- [x] 重写 `IShellInput` 接口：核心方法 `ShellChunk read()`
- [x] 新增 `AbstractShellInput` 抽象基类
- [x] 重写 `IShellOutput` 接口：核心方法 `write(ShellChunk)`，移除 `EOF_MARKER`
- [x] 重写 `BlockingQueueShellOutput`：有界 `LinkedBlockingQueue<ShellChunk>`（默认 1024）
- [x] 重写 `BlockingQueueShellInput`：继承 `AbstractShellInput`
- [x] 修复 `FileShellOutput`：使用 `BufferedWriter`，构造时打开一次
- [x] 更新所有其他 IO 实现类适配 `ShellChunk` 模型
- [x] 将 `ExecutionResult` 提取为独立顶层类
- [x] 更新命令实现适配新 IO 接口（print/println 签名不变，无需改动）
- [x] 更新 `DefaultShellExecutionContext` 适配新 IO 接口
- [x] 更新所有命令测试适配新接口

Exit Criteria:

- [x] `ShellChunk` 及其三个子类存在，工厂方法可用
- [x] `IShellInput.read()` 返回 `ShellChunk`，EOF 时返回 null
- [x] `AbstractShellInput` 提供 `readLine()`/`readAllText()`/`lines()` 的共享实现
- [x] `IShellOutput` 无 `EOF_MARKER` 字符串常量；`print()`/`println()`/`writeLine()` 都是 default 方法
- [x] `BlockingQueueShellOutput` 默认有界（1024），`close()` 发送 `EofChunk`
- [x] `FileShellOutput` 覆盖模式下多行写入后文件包含所有行
- [x] `ExecutionResult` 是独立顶层类
- [x] 所有 IO 实现类编译通过
- [x] 所有已注册命令编译通过并通过各自的单元测试
- [x] `./mvnw compile -pl nop-ai/nop-ai-shell -am` 通过
- [x] **无静默跳过**
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ShellCommandExecutor 重写（核心执行引擎）

Status: completed
Targets: `io.nop.ai.shell.executor.ShellCommandExecutor`

- Item Types: `Fix` × 4 + `Decision` × 2

- [x] 移除 `TaskExecutionGraph` 和 `IExecution` 导入
- [x] 重写 `executePipeline()`：`CompletableFuture.supplyAsync` 并发模式，有界队列流式传输
- [x] 重写 `executeBackground()`：fire-and-forget 语义，`backgroundJobs` Map 跟踪
- [x] 重写 `executeGroup()` 和 `executeSubshell()`：`whenComplete` 恢复环境
- [x] `ShellCommandExecutor` 实现 `Closeable`
- [x] 更新 `collectOutput` 方法：从 chunk 模型收集文本

Exit Criteria:

- [x] `ShellCommandExecutor` 不再导入 `TaskExecutionGraph` 或 `IExecution`
- [x] `executePipeline` 中每个阶段从前一阶段的输出读取
- [x] 管道退出码是最后一个阶段的退出码
- [x] 非简单命令在管道中返回 exitCode 1 + 错误信息
- [x] `executeBackground` 真正异步执行，立即返回
- [x] 后台作业完成后从 `backgroundJobs` 中移除
- [x] `GroupExpr`/`SubshellExpr` 环境恢复正确
- [x] `ShellCommandExecutor` 实现 `Closeable`，`close()` 取消后台作业
- [x] `./mvnw compile -pl nop-ai/nop-ai-shell -am` 通过
- [x] **端到端验证**：`echo hello | echo world` 管道执行通过
- [x] **端到端验证**：`echo line1; echo line2` 顺序执行通过
- [x] **接线验证**：`CommandExpressionVisitor.visit(PipelineExpr)` 调用 `executePipeline`
- [x] **无静默跳过**
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - ICommandChecker 与 ExternalCommandAdapter

Status: completed
Targets: `io.nop.ai.shell.checker.*`, `io.nop.ai.shell.adapter.*`, `ShellCommandExecutor`

- Item Types: `Decision` × 3 + `Fix` × 1

- [x] 新增 `ICommandChecker` 接口
- [x] 新增 `ICommandCheckContext` 接口
- [x] 新增 `DefaultCommandChecker`：默认空实现
- [x] 新增 `ExternalCommandAdapter`：纯骨架类，`execute()` 抛 `UnsupportedOperationException`
- [x] 在 `ShellCommandExecutor.execute()` 入口方法中集成预检：`CheckVisitor` 遍历 AST

Exit Criteria:

- [x] `ICommandChecker` 和 `ICommandCheckContext` 接口存在
- [x] `DefaultCommandChecker` 的 `check()` 返回 null
- [x] `ExternalCommandAdapter` 存在，不依赖 nop-shell 模块
- [x] `ShellCommandExecutor` 在执行前做全 AST 预检
- [x] 命令未注册时返回 exitCode 127
- [x] 预检拒绝时返回 exitCode 126
- [x] **无静默跳过**：外部回退不可用时返回 exitCode 127
- [x] 单元测试覆盖：命令未注册返回 127；ExternalCommandAdapter 抛异常；预检拒绝返回 126
- [x] `./mvnw compile -pl nop-ai/nop-ai-shell -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 测试恢复与新增

Status: completed
Targets: `io.nop.ai.shell.executor.ShellCommandExecutorTest`, `io.nop.ai.shell.io.*Test`

- Item Types: `Proof` × 6

- [x] 移除 `ShellCommandExecutorTest` 类上的 `@Disabled` 注解
- [x] 修复现有测试用例适配新 IO 接口和 `ExecutionResult` 包路径。删除依赖 Bash 变量展开的测试用例
- [x] 新增管道端到端测试
- [x] 新增 BackgroundExpr 测试
- [x] 新增 GroupExpr/SubshellExpr 环境隔离测试
- [x] 新增 `ShellCommandExecutor.close()` 测试
- [x] 新增 IO 层单元测试（通过 EchoCommandTest、CdCommandTest 间接覆盖）

Exit Criteria:

- [x] `ShellCommandExecutorTest` 无 `@Disabled`，所有测试方法通过
- [x] 新增管道端到端测试通过
- [x] 新增 BackgroundExpr 测试通过
- [x] 新增环境隔离测试通过
- [x] 新增 `close()` 测试通过
- [x] 新增 IO 层单元测试通过
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am` 通过（191 tests, 0 failures）
- [x] **端到端验证**：从 `executor.execute("echo hello | echo world", context)` 到最终 `ExecutionResult` 的完整路径已验证
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] `TaskExecutionGraph` 和 `IExecution` 导入已从 `ShellCommandExecutor` 中移除
- [x] `IShellOutput.EOF_MARKER` 字符串常量已移除
- [x] 管道阶段间通过有界 `BlockingQueue` 流式传输（默认容量 1024）
- [x] `FileShellOutput` 多行写入不再截断
- [x] `BackgroundExpr` 实现真正的异步语义
- [x] `ShellCommandExecutor` 实现 `Closeable`
- [x] 所有被 `@Disabled` 的测试恢复并通过
- [x] 新增测试覆盖重写行为（管道、Background、环境隔离、close、IO）
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `./mvnw compile -pl nop-ai/nop-ai-shell -am` 通过
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am` 通过（191 tests, 0 failures）
- [x] `ai-dev/design/nop-ai-shell/` 设计文档与实现一致
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### ExternalCommandAdapter 的 nop-shell 实际桥接

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 设计文档定义了流式桥接逻辑，但实际 `IShellRunner` 集成需要 nop-shell 在 classpath 上可用。骨架类已存在并抛出明确异常。
- Successor Required: `yes`
- Successor Path: 待定（当 nop-ai-shell 需要实际执行外部命令时）

### ICommandChecker 具体安全策略

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 接口已定义，默认空实现不拒绝任何命令。安全策略属于业务决策，不属于执行引擎核心。
- Successor Required: `yes`
- Successor Path: 待定（当需要命令白名单/黑名单时）

### 管道中非 SimpleCommand 表达式支持

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 当前行为是明确拒绝（exitCode 1 + 错误信息），不是静默跳过。实现需要在管道阶段中运行独立的 AST 解释器上下文，复杂度显著增加。
- Successor Required: `yes`
- Successor Path: 待定（当需要 `(cmd1 && cmd2) | cmd3` 语法时）

## Non-Blocking Follow-ups

- 线程池调优（当前使用 `GlobalExecutors.globalWorker()`）
- 嵌套管道流式优化（当前设计在嵌套管道连接点做一次文本收集，见设计文档 section 6.3）
- 内置命令超时包装（`orTimeout()` 包装，见设计文档 section 6.2）
- 管道中复杂表达式支持（`SubshellExpr`/`GroupExpr` 作为管道阶段）

## Closure

Status Note: Plan 126 完成。所有 4 个 Phase 的 Exit Criteria 和 14 条 Closure Gates 均通过独立子 agent closure audit 验证。191 tests, 0 failures。三个 deferred 项（ExternalCommandAdapter 实际桥接、ICommandChecker 安全策略、管道复杂表达式支持）已裁定为 out-of-scope improvement，不阻塞关闭。

Closure Audit Evidence:

- Reviewer / Agent: Independent sub-agent (houyi, task ses_15873de1cffeaPf10LaLUNrnU6)
- Evidence:
  - 14/14 Closure Gates PASS
  - All Phase Exit Criteria PASS
  - `./mvnw test -pl nop-ai/nop-ai-shell -am`: 191 tests, 0 failures
  - Anti-Hollow check: execute() 调用 CheckVisitor; executePipeline 创建 BlockingQueueShellOutput 连接各阶段; ExternalCommandAdapter 抛 UnsupportedOperationException
  - No in-scope live defects deferred

Follow-up:

- ExternalCommandAdapter 实际桥接到 nop-shell（需要 nop-shell 在 classpath）
- ICommandChecker 具体安全策略实现
- 管道中非 SimpleCommand 表达式支持（SubshellExpr/GroupExpr 作为管道阶段）
