# nop-ai-shell 执行器与异步模型

**日期**：2026-06-08
**状态**：active

---

## 一、设计结论

1. **执行器是 AST 解释器**，动态遍历执行，不预构建执行图，不依赖 `TaskExecutionGraph`
2. **管道是唯一需要并发的场景**，通过 `CompletableFuture.supplyAsync` 直接提交各阶段
3. **`BackgroundExpr` 通过异步提交 + jobId 注册实现**，立即返回不阻塞
4. **`ShellCommandExecutor` 是有状态的单 session 对象**，不跨 session 复用
5. **GroupExpr/SubshellExpr 的环境隔离通过快照恢复实现**，利用 `try-finally` 保证安全性
6. **未注册命令可通过 `IShellRunner` 回退到 OS shell**，由 `ICommandChecker` 在执行前预检守卫安全

## 二、执行模型：动态 AST 遍历

### 2.1 为什么不用 TaskExecutionGraph

Bash 的执行模型本质是**解释器模式**：

```
cmd1 && cmd2 || cmd3 ; cmd4 &
```

Bash 从左到右**逐步执行**：
- 执行 `cmd1`，看退出码
- 退出码为 0 → 执行 `cmd2`
- `cmd2` 失败 → 执行 `cmd3`
- 然后执行 `cmd4`，放到后台
- 每一步都**依赖上一步的运行时结果**

这不是 DAG 调度问题——你无法在执行前知道哪些节点会被执行。`TaskExecutionGraph` 适用于"所有任务预先已知、依赖关系静态确定"的场景（如构建系统），不适合条件执行的 shell 语义。

### 2.2 所有 visit 返回 CompletionStage 的理由

Visitor 的泛型参数是 `CommandVisitor<CompletionStage<ExecutionResult>>`——每个 visit 方法统一返回 `CompletionStage`。

**为什么不用混合返回类型**（同步节点返回 `ExecutionResult`，异步节点返回 `CompletionStage`）：

```
cmd1 && cmd2 | cmd3
```

解析树：`LogicalExpr(AND, SimpleCommand("cmd1"), PipelineExpr(cmd2, cmd3))`

执行链：
1. `visit(SimpleCommand("cmd1"))` → 同步完成 → 返回 `FutureHelper.success(result1)`
2. `thenCompose(leftResult -> { ... })` → 根据 exitCode 决定是否继续
3. 如果继续 → `visit(PipelineExpr(cmd2, cmd3))` → **真正的异步**，多线程管道
4. 管道完成后 → 返回 `CompletionStage<ExecutionResult>`

如果同步节点返回 `ExecutionResult`，这根链条就断了——`thenCompose` 要求入参是 `CompletionStage`，你必须手动包装。统一的 `CompletionStage` 返回类型让 `thenCompose` 链自然贯穿整棵 AST 树。

**CompletionStage 在这里不是"异步"的意思，而是"可组合的延迟结果"**：
- 同步命令的 visit 内部直接执行，返回已经完成的 `FutureHelper.success(result)`
- 管道的 visit 内部启动多线程，返回一个尚未完成的 Future
- `thenCompose` 不关心结果是已完成的还是未完成的——它对两种情况一视同仁

### 2.3 线程模型：谁运行在哪个线程上

```
调用线程（通常是 Agent 框架的线程）
  │
  │ execute("cmd1 && cmd2 | cmd3 &")
  │
  ├─ parse → AST  （调用线程）
  │
  ├─ visit(LogicalExpr(AND, ...))    （调用线程）
  │    │
  │    ├─ visit(SimpleCommand("cmd1"))   （调用线程，同步）
  │    │   └─ command.execute(context) → int
  │    │   └─ return FutureHelper.success(result)  ← 已完成
  │    │
  │    └─ thenCompose: exitCode == 0 → 继续
  │         │
  │         └─ visit(PipelineExpr(cmd2, cmd3))  （调用线程发起，各阶段在线程池）
  │              │
  │              ├─ supplyAsync(cmd2) → 线程A
  │              ├─ supplyAsync(cmd3) → 线程B
  │              │   A 和 B 通过 BlockingQueue 流式连接
  │              │
  │              └─ return CompletableFuture  ← 未完成，等最后一个阶段结束
  │
  └─ 最终结果: CompletionStage<ExecutionResult>
```

**关键规则**：

| 场景 | 执行线程 | 是否阻塞调用线程 |
|------|----------|----------------|
| `SimpleCommand` | 调用线程 | 是（同步执行，微秒级） |
| `LogicalExpr` | 调用线程 | 左操作数同步阻塞；右操作数取决于其类型 |
| `PipelineExpr` | 调用线程创建各阶段，各阶段在 `Executor` 线程池 | `thenCompose` 回调中等待最后一个阶段完成 |
| `BackgroundExpr` | 提交到 `Executor` 线程池 | **否**——立即返回已完成 Future |
| `GroupExpr` | 调用线程 | 是（串行执行内部命令） |
| `SubshellExpr` | 调用线程 | 是（执行内部表达式） |

**调用线程的阻塞行为**：
- 非 Background 场景：调用线程会一路同步遍历 AST，遇到管道时在线程池上启动各阶段然后 `thenCompose` 等待完成。整个 `execute()` 返回的 `CompletionStage` 在管道/Background 结束后完成。
- 调用方可以选择 `toCompletableFuture().join()` 同步等待，也可以 `thenAccept(...)` 异步消费。

### 2.4 BackgroundExpr 的异步机制

BackgroundExpr 是唯一让调用线程**不阻塞**的节点：

```java
// 调用线程
CompletionStage<ExecutionResult> visit(BackgroundExpr bg) {
    String jobId = generateJobId();

    // 内部表达式提交到线程池，独立运行
    CompletableFuture<ExecutionResult> bgFuture =
        CompletableFuture.supplyAsync(() -> {
            // 工作线程中执行
            return executeExpression(bg.inner(), context, cancelToken)
                .toCompleFuture().join();
        }, executor);

    backgroundJobs.put(jobId, bgFuture);

    // 调用线程立即返回，不等内部表达式完成
    return FutureHelper.success(
        new ExecutionResult(0, "[" + jobId + "] running in background", "")
    );
}
```

调用线程拿到的 `CompletionStage` 是**已经完成的**（`FutureHelper.success`），而内部表达式在另一个线程上异步运行。这是 fire-and-forget 语义。

### 2.5 PipelineExpr 的并发机制

PipelineExpr 是唯一需要多线程的节点。但它不是"为每个阶段开一个线程然后等所有完成"——而是 producer-consumer 流式模型：

```java
CompletionStage<ExecutionResult> visit(PipelineExpr pipeline) {
    List<CommandExpression> commands = pipeline.commands();
    IShellInput currentInput = context.stdin();
    BlockingQueueShellOutput prevOutput = null;
    List<CompletableFuture<Integer>> stageFutures = new ArrayList<>();

    for (int i = 0; i < commands.size(); i++) {
        BlockingQueueShellOutput output = new BlockingQueueShellOutput();
        IShellInput stageInput = (i == 0) ? currentInput : prevOutput.asInput();

        final IShellInput input = stageInput;
        final IShellOutput out = output;

        // 每个阶段提交到线程池，立即返回 CompletableFuture
        CompletableFuture<Integer> stageFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return executeSimpleCommandWithContext(cmd, input, out, ...);
            } finally {
                out.close();  // 发送 EOF → 下游 read() 返回 null → 下游自然结束
            }
        }, executor);

        stageFutures.add(stageFuture);
        prevOutput = output;
    }

    // 等最后一个阶段完成，收集输出
    CompletableFuture<ExecutionResult> pipelineFuture =
        stageFutures.get(stageFutures.size() - 1).thenApply(lastExitCode -> {
            String stdout = collectTextFromOutput(prevOutput);
            return new ExecutionResult(lastExitCode, stdout, "");
        });

    return pipelineFuture;
}
```

**并发是怎么发生的**：所有 `supplyAsync` 调用在循环中立即提交到线程池。线程池为每个阶段分配线程。阶段之间通过 `BlockingQueue` 连接——上游 `write` 下游 `read`，自然流式。

**CompletionStage 在这里的角色**：
- `supplyAsync` 返回未完成的 `CompletableFuture<Integer>`
- 最后一个阶段的 Future 通过 `thenApply` 收集输出，映射为 `ExecutionResult`
- 这个 `thenApply` 链在最后一个阶段**完成后**的回调中执行
- 调用线程通过 `thenCompose` 等待这个 Future 完成——这是唯一真正的异步等待点

### 2.6 各 AST 节点的完整执行语义

| AST 节点 | 执行策略 | 返回的 CompletionStage | 实际线程 |
|----------|----------|----------------------|----------|
| `SimpleCommand` | 同步执行 `command.execute(context)` | 已完成（`FutureHelper.success`） | 调用线程 |
| `PipelineExpr` | 所有阶段 `supplyAsync` 并发提交 | 未完成，等最后阶段 | 各阶段在 Executor 线程池 |
| `LogicalExpr(AND)` | 执行 left，退出码 0 才 `thenCompose` 执行 right | 取决于 right | 调用线程 |
| `LogicalExpr(OR)` | 执行 left，退出码非 0 才 `thenCompose` 执行 right | 取决于 right | 调用线程 |
| `LogicalExpr(SEMICOLON)` | `thenCompose` 先 left 后 right，忽略退出码 | 取决于 right | 调用线程 |
| `GroupExpr` | 串行执行内部命令序列，执行完毕恢复环境 | 取决于最后一个内部命令 | 调用线程 |
| `SubshellExpr` | 执行内部表达式，环境完全隔离 | 取决于内部表达式 | 调用线程 |
| `BackgroundExpr` | `supplyAsync` 提交内部表达式到线程池 | 已完成（立即返回 jobId） | 内部在工作线程，visit 在调用线程 |

## 三、其他 AST 节点的执行细节

管道的完整并发机制已在 2.5 节描述。本节补充其余节点。

```
管道时间线（回顾 2.5 节）：

Thread-1:  [A writes chunk1] → [A writes chunk2] → [A writes chunk3] → [A close] → done
Thread-2:                      [B reads chunk1]  → [B reads chunk2]  → [B reads chunk3] → [B reads EOF] → [B writes out] → [B close] → done
Thread-3:                                                                                                              [C reads...] → [C close] → done
```

### 3.1 SimpleCommand 执行：同步阻塞

单个命令的执行是同步的——`command.execute(context)` 返回 `int` 退出码。

在管道中，这个同步执行被包装在 `CompletableFuture.supplyAsync` 中，由线程池调度。命令本身不需要感知异步。

### 3.2 LogicalExpr 执行：thenCompose 条件链

`&&`、`||`、`;` 的执行通过 `thenCompose` 串联。左操作数的 `CompletionStage` 完成后，回调中检查退出码决定是否执行右操作数。

```java
// AND: 左成功才执行右
return executeExpression(left, ...).thenCompose(leftResult -> {
    if (leftResult.exitCode() == 0) {
        return executeExpression(right, ...);
    }
    return FutureHelper.success(leftResult);
});
```

这些操作符天然是串行的（右操作数依赖左操作数的结果），不存在并发问题。

### 3.3 BackgroundExpr 执行：fire-and-forget

BackgroundExpr 的完整机制已在 2.4 节描述。要点：`supplyAsync` 提交到线程池，visit 立即返回已完成的 `FutureHelper.success(jobInfo)`。

`backgroundJobs` 是 executor 实例上的 Map，存储 jobId → `CompletableFuture`，可后续通过 `jobs` 命令查询状态。取消通过 `ICancelToken` 传播。

### 3.4 GroupExpr / SubshellExpr：环境隔离

```java
private CompletionStage<ExecutionResult> executeGroup(GroupExpr group, ...) {
    Map<String, String> savedEnv = new HashMap<>(this.exportedEnv);
    String savedDir = this.currentWorkingDir;

    try {
        return executeSequence(group.commands(), context, cancelToken, true)
            .whenComplete((result, ex) -> {
                // 无论成功失败，都恢复环境
                this.exportedEnv = savedEnv;
                this.currentWorkingDir = savedDir;
            });
    } catch (Exception e) {
        this.exportedEnv = savedEnv;
        this.currentWorkingDir = savedDir;
        throw e;
    }
}
```

**与当前实现的差异**：
- 当前用 `thenAccept` 恢复环境，在异步回调中执行，可能与其他操作竞争
- 改用 `whenComplete`，保证无论成功失败都恢复
- 对于 GroupExpr（在当前 shell 中执行），命令期间的环境修改对后续命令可见，执行完毕后回滚
- 对于 SubshellExpr（在子 shell 中执行），修改从一开始就不影响父 shell

### 3.5 错误传播

管道中的错误传播规则：

1. **阶段 N 非零退出码**：该阶段正常结束，退出码记录。阶段 N+1 仍然读取阶段 N 的输出
2. **阶段 N 抛异常**：关闭该阶段的输出（发送 EOF），下游正常结束。整个管道返回错误
3. **取消（`ICancelToken`）**：所有阶段应检查取消状态，快速退出

**与 Bash 的差异**：Bash 中管道的退出码是最后一个阶段的退出码，中间阶段失败不影响管道继续。当前设计遵循同一语义。

### 3.6 管道退出码

管道的退出码是**最后一个阶段的退出码**。如果中间阶段失败（非零退出码），它的错误信息写入 stderr，但不会中断管道。退出码直接取 `stageFutures.get(stageFutures.size() - 1)` 的结果（见 2.5 节伪代码），不需要遍历所有阶段的 Future。

## 四、IO 实现的修正

### 4.1 FileShellOutput 修正
当前 Bug：非追加模式下每次 `writeLine` 都带 `TRUNCATE_EXISTING`，只保留最后一行。

修正策略：
- 构造时一次性 truncate 文件
- 后续写入只用 `CREATE` + `APPEND`（或保持打开的 `BufferedWriter`）

### 4.2 EOF 语义统一

所有 `IShellOutput` 实现的 `close()` 方法必须：
1. 刷新内部缓冲区
2. 写入 `EofChunk.INSTANCE`
3. 不再接受后续写入

所有 `IShellInput` 实现的 `read()` 方法：
- 读到 `EofChunk` 时返回 null
- 关闭后所有 `read()` 返回 null

不再使用 `IShellOutput.EOF_MARKER` 字符串常量。

## 五、命令校验与外部 Shell 回退

### 5.1 设计动机

两个独立但相关的需求：

1. **AST 预检**：在执行任何命令之前，遍历整棵 AST 检查每个 `SimpleCommand` 是否满足预设的安全/权限要求。这对内置命令和外部命令同样适用——`rm -rf /` 即使有内置实现也不该允许。
2. **外部回退**：未注册的命令可通过 `IShellRunner` 委托给 OS shell，但需要在回退前验证安全性。

当前设计只有一个校验接口 `ICommandChecker`，在执行前对整棵 AST 做全量预检。无论命令最终由内置实现执行还是委托给 OS shell，都经过同一套校验逻辑。

### 5.2 ICommandChecker — AST 级命令校验器

```java
public interface ICommandChecker {
    /**
     * 校验一个 SimpleCommand 是否满足预设要求。
     * 在 AST 预检阶段和外部回退阶段都会调用。
     *
     * @param command   解析后的 SimpleCommand（包含命令名、参数、环境变量、重定向）
     * @param context   校验上下文（当前工作目录、环境变量等）
     * @return null 表示通过，非 null 表示拒绝理由
     */
    String check(SimpleCommand command, ICommandCheckContext context);
}
```

```java
public interface ICommandCheckContext {
    String workingDirectory();
    Map<String, String> environment();
    boolean isRegisteredCommand(String commandName);
}
```

**设计决策**：
- 校验对象是 `SimpleCommand`（AST 节点），不是原始字符串。调用方可以检查命令名、参数列表、环境变量赋值、重定向目标等所有信息
- `ICommandCheckContext` 提供运行时上下文（工作目录、环境变量），以及 `isRegisteredCommand()` 用于判断该命令是内置还是外部
- 返回 String 拒绝理由而非 boolean，便于写入 stderr 告知用户/AI
- 校验器由调用方注入，nop-ai-shell 自身不提供默认实现
- 多个校验器可组合（全部通过才算通过）

### 5.3 AST 预检流程

`ShellCommandExecutor.execute()` 在解析 AST 后、执行前，先用 `ICommandChecker` 遍历整棵 AST：

```
execute(commandLine)
  │
  ├─ 1. parse → AST
  │
  ├─ 2. 预检（如果 checker != null）
  │     AST.accept(CheckVisitor)
  │       → 遍历每个 SimpleCommand 节点
  │       → checker.check(simpleCmd, checkContext)
  │       → 任一不通过 → 立即返回 ExecutionResult(126, "", rejectReason)
  │
  └─ 3. 执行
        AST.accept(ExecuteVisitor)
          → 按原有逻辑动态遍历执行
```

预检使用独立的 `CommandVisitor<String>` 遍历 AST：
- `visit(SimpleCommand)` → 调用 `checker.check()`，返回拒绝理由或 null
- `visit(PipelineExpr)` → 遍历所有 commands
- `visit(LogicalExpr)` → 遍历 left 和 right
- `visit(GroupExpr)` → 遍历所有 commands
- `visit(SubshellExpr)` → 遍历 inner
- `visit(BackgroundExpr)` → 遍历 inner

预检是**全量检查**：即使 `cmd1 && cmd2` 中 `cmd1` 可能失败导致 `cmd2` 不执行，预检也会检查 `cmd2`。这是"先批准后执行"的安全模型——宁可误拒不可漏检。

### 5.4 外部回退的决策流程

预检通过后，执行阶段的回退决策在 `executeSimpleCommandWithContext` 中：

```
1. registry.findCommand(commandName)
2. 如果找到 → 内置执行
3. 如果未找到：
   a. shellRunner == null → exit code 127
   b. shellRunner != null → 已通过预检，直接执行 executeExternalCommand()
```

注意：外部回退时不再单独调用 checker——预检阶段已经检查过了。如果预检不通过，整个命令行在步骤 2 就被拒绝了，根本不会进入执行阶段。

### 5.5 ShellCommandExecutor 的构造扩展

```java
public class ShellCommandExecutor {
    private final ShellCommandRegistry registry;
    private final Executor executor;
    private final IShellRunner shellRunner;     // 可选，null = 不回退
    private final ICommandChecker checker;      // 可选，null = 不校验（不推荐）
    // ...
    public ShellCommandExecutor(ShellCommandRegistry registry, Executor executor) {
        this(registry, executor, null, null);
    }

    public ShellCommandExecutor(ShellCommandRegistry registry, Executor executor,
                                 IShellRunner shellRunner, ICommandChecker checker) {
        this.registry = registry;
        this.executor = executor != null ? executor : GlobalExecutors.globalWorker();
        this.shellRunner = shellRunner;
        this.checker = checker;
    }
}
```

### 5.6 ICommandChecker 的典型实现

校验器由上层（如 Agent 框架）提供。以下是两种常见策略的思路（不是实现规范）：

**白名单策略**：只允许预定义的命令名（如 `git`、`docker`、`curl`、`python`），拒绝参数中的危险模式

**黑名单策略**：拒绝已知危险命令（如 `rm`、`mkfs`、`dd`），允许其他所有命令

校验器可以根据 `context.isRegisteredCommand()` 对内置命令和外部命令采用不同策略：内置命令可以宽松一些（已经过安全审计），外部命令严格一些。

nop-ai-shell 不提供内置的校验器实现——这是安全策略，应由使用方根据场景决定。

### 5.7 ExternalCommandAdapter — 桥接 nop-shell

`ExternalCommandAdapter` 负责将 nop-ai-shell 的执行模型桥接到 nop-shell 的 `IShellRunner`：

```
输入侧（流式）：
  独立线程：IShellInput.read() → Process.getOutputStream().write()

输出侧（流式）：
  独立线程：Process.getInputStream() → IShellOutput.print()
  独立线程：Process.getErrorStream() → IShellOutput(stderr).print()
```

**桥接逻辑（流式写入 stdin）**：
1. 创建 `ShellCommand`，设置命令名、参数、工作目录、环境变量
2. 启动外部进程（`ProcessBuilder.start()`），获得 `Process`
3. 在独立线程中将 `IShellInput` 的数据逐行写入 `Process.getOutputStream()`（进程的 stdin），写完后关闭
4. 在另一个线程中从 `Process.getInputStream()` 逐行读取，通过 `IShellOutputCollector.onOutput()` 转写到 `IShellOutput`
5. 在另一个线程中从 `Process.getErrorStream()` 逐行读取，转写到 stderr 的 `IShellOutput`
6. 等待进程退出，收集 exit code

**不使用全量缓冲**：不在启动前收集 `readAllText()` → `inputBytes`，而是逐行从 `IShellInput` 写入进程的 stdin。这样即使上游产出大量数据（如 `yes | grep "pattern"`），也不会 OOM。

### 5.8 取消传播到外部进程

`IShellRunner.run()` 不接受 `ICancelToken`。`ExternalCommandAdapter` 需要自行桥接取消信号：

```
ICancelToken 注册回调 → process.destroy() → 关闭所有流 → 设置 stopped 标志
```

在 `IShellOutputCollector` 的每一行回调中检查 `cancelToken.isCancelled()`，如果已取消则停止读取。

### 5.9 管道中的外部命令

管道中混合内置命令和外部命令时的行为：

| 场景 | 处理方式 |
|------|----------|
| 内置 → 内置 | `BlockingQueue` 流式（最优） |
| 内置 → 外部 | 逐行从 Queue 读取，写入 `Process.getOutputStream()`，流式 |
| 外部 → 内置 | `Process.getInputStream()` 逐行读取，写入 `BlockingQueue`，流式 |
| 外部 → 外部 | 不支持 OS pipe 直连；中间通过 `BlockingQueue` 适配，均为流式 |

所有场景均为流式，不存在全量缓冲。

### 5.10 与 nop-shell 的复用关系

| 方面 | nop-shell | nop-ai-shell |
|------|-----------|--------------|
| 定位 | 底层 OS 命令执行工具 | 高层嵌入式命令框架 |
| 执行模型 | 同步 `ProcessBuilder` | 异步管道 + 内置命令 |
| IO 模型 | `IShellOutputCollector`（回调） | `IShellInput`/`IShellOutput`（流） |
| 命令解析 | `ShellCommand.splitCommandLine`（简单分词） | `BashSyntaxParser`（完整 AST） |
| 依赖方向 | 无 AI 依赖 | 可选依赖 nop-shell |

nop-ai-shell **复用** nop-shell 的 `IShellRunner` 接口和 `ShellCommand` 数据对象作为外部回退的执行通道，**不复用**其命令解析或 IO 模型。

## 六、补充规范

### 6.1 BackgroundExpr 作业生命周期

- 后台作业完成后，通过 `whenComplete` 回调从 `backgroundJobs` 中移除，防止内存泄漏
- `ShellCommandExecutor` 实现 `Closeable`，`close()` 方法取消所有后台作业并等待终止
- 后台线程持有 executor 引用，executor 的 `close()` 是显式退出的唯一方式

### 6.2 内置命令超时

内置命令的 `execute()` 是同步调用，不自带超时。依赖两点：
1. 命令实现应周期性检查 `ICancelToken.isCancelled()`，及时退出
2. 执行器可在 `CompletableFuture.supplyAsync` 中包装 `command.execute()`，配合 `orTimeout()` 设置上限

外部命令的超时由 `ShellCommand.setTimeout()` 控制，在 `ShellRunner` 中通过定时器强制销毁进程。

### 6.3 嵌套管道的流式性

`(cmd1 | cmd2) | cmd3` 中，子 shell 中的 `cmd1 | cmd2` 的 stdout 在 `visit(SubshellExpr)` 中被收集为 `ExecutionResult.stdout`（字符串），然后 `cmd3` 从字符串输入中读取。这意味着嵌套管道的连接点会做一次文本收集，不是纯流式。这是当前设计的已知限制——只有顶层管道各阶段之间是流式的。

### 6.4 ICommandCheckContext 的创建

`ICommandCheckContext` 由 `ShellCommandExecutor` 内部创建，包装 `this.registry`、`this.currentWorkingDir`、`this.exportedEnv`。预检在执行前同步完成，此时这些状态是稳定的。

### 6.5 错误路径的资源清理

- 管道中每个阶段的 `finally` 块关闭其输出（发送 EOF）
- `executeSimpleCommandWithContext` 的 `finally` 块关闭 `RedirectedStreams`（文件重定向等）
- `IShellInput` 是被动消费的，不需要显式关闭——上游关闭输出后，下游的 `read()` 自然返回 null

### 6.6 迁移步骤（相对当前源码）

1. 移除 `TaskExecutionGraph` 和 `IExecution` 导入
2. 重写 `executePipeline()` 为 `CompletableFuture.supplyAsync` 模式（参考 2.5 节）
3. 重写 IO 层：`IShellInput`/`IShellOutput` 改为 `ShellChunk` 模型
4. 移除 `IShellOutput.EOF_MARKER` 字符串常量
5. 修复 `FileShellOutput`：构造时 truncate 一次，后续只用 APPEND
6. 新增 `ICommandChecker` 预检流程
7. 新增 `ExternalCommandAdapter` 外部回退
8. 实现 `BackgroundExpr` 真正的异步语义
9. `ShellCommandExecutor` 实现 `Closeable`

## 七、与已有设计的关系

- IO 模型：`02-io-and-pipeline.md`
- AST 模型：`04-bash-syntax.md`
- `IShellRunner`/`ShellCommand`：来自 `nop-utils/nop-shell`，作为外部回退的执行通道
- 不依赖 `TaskExecutionGraph`：执行器是纯 AST 解释器，不需要 DAG 编排
