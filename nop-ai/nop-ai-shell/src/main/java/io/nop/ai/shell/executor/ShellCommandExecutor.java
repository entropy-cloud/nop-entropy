package io.nop.ai.shell.executor;

import io.nop.ai.shell.adapter.ExternalCommandAdapter;
import io.nop.ai.shell.checker.DefaultCommandChecker;
import io.nop.ai.shell.checker.ICommandCheckContext;
import io.nop.ai.shell.checker.ICommandChecker;
import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommand;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.commands.ShellCommandRegistry;
import io.nop.ai.shell.io.BlockingQueueShellOutput;
import io.nop.ai.shell.io.DuplexShellOutput;
import io.nop.ai.shell.io.FileShellInput;
import io.nop.ai.shell.io.FileShellOutput;
import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.ai.shell.io.ShellChunk;
import io.nop.ai.shell.model.*;
import io.nop.ai.shell.parser.BashSyntaxParser;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ExecutorHelper;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.util.IoHelper;

import static io.nop.ai.shell.NopAiShellErrors.ARG_MSG;
import static io.nop.ai.shell.NopAiShellErrors.ERR_AI_SHELL_INVALID_REDIRECT;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellCommandExecutor implements Closeable {

    static final Logger LOG = LoggerFactory.getLogger(ShellCommandExecutor.class);

    /**
     * CRA 3.2-C4-004 (plan 2498 Phase 4): name of the dedicated pipeline pool
     * created when no executor is injected — pipeline stages are identifiable
     * in thread dumps and isolated from the platform {@code nop-global-worker}
     * pool and from the foreground bash tool pool.
     */
    public static final String DEFAULT_PIPELINE_POOL_NAME = "nop-ai-shell-pipeline";

    /**
     * Default pipeline pool width. Sized against the deployment envelope
     * "max concurrent shell sessions x max pipeline stages per pipeline";
     * threads are created lazily on demand, so this is an upper bound.
     */
    static final int DEFAULT_PIPELINE_POOL_SIZE = 32;

    /** Default bounded pipeline stage queue depth (saturation rejects loud). */
    static final int DEFAULT_PIPELINE_QUEUE_SIZE = 1024;

    private final ShellCommandRegistry registry;
    private final Executor executor;
    private final ICommandChecker checker;
    private final ExternalCommandAdapter externalAdapter;
    private final IToolFileSystem fileSystem;

    /**
     * The pipeline pool created internally by the default-carrier assembly
     * (null when an executor was injected — an injected executor is not owned
     * by this instance and is never shut down by {@link #close()}).
     */
    private final DefaultThreadPoolExecutor ownedExecutor;

    private Map<String, String> exportedEnv = new HashMap<>();
    private String currentWorkingDir = "/";
    private boolean workingDirInitialized = false;

    private volatile boolean closed = false;

    /**
     * Plan 2301 (WI25): the addressable-lifecycle sink for {@code &} jobs.
     * Null (shipped default) = {@code &} fails loud — an unwired sink would
     * mean start-and-lose jobs (the pre-WI25 defect).
     */
    private volatile IBackgroundJobSink backgroundJobSink;

    /**
     * CRA 3.2-C4-004 (plan 2498 Phase 4): a null {@code executor} no longer
     * falls back to the platform global worker pool — pipeline stages (and
     * other submitted stage tasks) previously shared {@code nop-global-worker}
     * with every other platform consumer (core=4/max=30/queue=10000), so
     * unrelated load amplified pipeline latency and the deep queue masked
     * saturation. The default assembly now creates a dedicated named bounded
     * module pool ({@value #DEFAULT_PIPELINE_POOL_NAME}, built through the
     * R1-02 fail-fast validated factory) isolated from the platform worker
     * pool and from the foreground bash tool pool; pool parameters target the
     * "max concurrent sessions x max pipeline stages" envelope. An explicitly
     * injected executor keeps precedence (embedders can still wire their own
     * pool); the internally created pool is owned by this instance and is
     * shut down by {@link #close()}.
     */
    public ShellCommandExecutor(ShellCommandRegistry registry, Executor executor, ICommandChecker checker, IToolFileSystem fileSystem) {
        this.registry = registry;
        DefaultThreadPoolExecutor owned = null;
        if (executor != null) {
            rejectSynchronousExecutor(executor);
            this.executor = executor;
        } else {
            owned = DefaultThreadPoolExecutor.newExecutor(DEFAULT_PIPELINE_POOL_NAME,
                    DEFAULT_PIPELINE_POOL_SIZE, DEFAULT_PIPELINE_QUEUE_SIZE, true);
            this.executor = owned;
        }
        this.ownedExecutor = owned;
        if (checker == null) {
            LOG.warn("ShellCommandExecutor constructed without a command checker: dangerous commands are not filtered");
        }
        this.checker = checker;
        this.externalAdapter = new ExternalCommandAdapter();
        this.fileSystem = fileSystem;
    }

    public ShellCommandExecutor(ShellCommandRegistry registry, IToolFileSystem fileSystem) {
        this(registry, null, new DefaultCommandChecker(), fileSystem);
    }

    /**
     * Minor-8 注入面守卫：同步/direct Executor（调用线程内联执行，如 {@code Runnable::run}）
     * 会使 {@code submitCollector} 的收集任务在调用线程内联阻塞于 {@code collectOutput}
     * 的队列轮询——生产者 stage 尚未提交即自锁死锁。构造期以 no-op 探针检测内联执行并
     * fail-fast（typed IllegalArgumentException）；异步 executor 上探针只是入队一个空任务。
     */
    private static void rejectSynchronousExecutor(Executor executor) {
        // 记录探针的执行线程而非布尔标志：boolean 版本存在误判竞态——异步 executor 的
        // 空闲 worker 可能在 execute() 返回后、标志读取前抢跑完成探针任务，把健康的
        // 异步 executor 当成内联执行而拒绝（满载构建下随机炸测试/生产装配）。
        // 仅当探针由"调用线程自身"执行（= 内联语义）时才判定为同步 executor。
        java.util.concurrent.atomic.AtomicReference<Thread> ranBy = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            executor.execute(() -> ranBy.compareAndSet(null, Thread.currentThread()));
        } catch (Throwable e) {
            // 探针提交失败按既有 RejectedExecutionException 语义留给首个真实提交点暴露
            return;
        }
        if (ranBy.get() == Thread.currentThread()) {
            throw new IllegalArgumentException(
                    "ShellCommandExecutor: injected executor must be asynchronous — a synchronous "
                            + "(caller-thread inline) executor deadlocks the pipeline stage/collector design "
                            + "in collectOutput(): " + executor);
        }
    }

    public CompletionStage<ExecutionResult> execute(String commandLine, IShellCommandExecutionContext context) {
        return execute(commandLine, context, context.cancelToken());
    }

    public CompletionStage<ExecutionResult> execute(String commandLine, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        // 首次执行时以调用方 context 的工作目录初始化执行器状态：cd 之前 pwd/ls
        // 语义正确；cd 之后执行器当前目录在后续 execute 调用间保持（bash 会话模型）
        if (!workingDirInitialized && context != null && context.workingDirectory() != null) {
            currentWorkingDir = context.workingDirectory();
            workingDirInitialized = true;
        }

        BashSyntaxParser parser = new BashSyntaxParser(commandLine);
        CommandExpression expr = parser.parse();

        if (checker != null) {
            String rejection = checkAst(expr);
            if (rejection != null) {
                return FutureHelper.success(new ExecutionResult(126, "", rejection));
            }
        }

        return executeExpression(expr, context, cancelToken);
    }

    private String checkAst(CommandExpression expr) {
        CheckVisitor visitor = new CheckVisitor();
        return expr.accept(visitor);
    }

    private class CheckVisitor implements CommandVisitor<String> {
        @Override
        public String visit(SimpleCommand cmd) {
            ICommandCheckContext checkContext = new ICommandCheckContext() {
                @Override
                public String workingDirectory() { return currentWorkingDir; }
                @Override
                public Map<String, String> environment() { return Collections.unmodifiableMap(exportedEnv); }
                @Override
                public boolean isRegisteredCommand(String commandName) { return registry.findCommand(commandName) != null; }
            };
            return checker.check(cmd, checkContext);
        }

        @Override
        public String visit(PipelineExpr pipe) {
            for (CommandExpression cmd : pipe.commands()) {
                String result = cmd.accept(this);
                if (result != null) return result;
            }
            return null;
        }

        @Override
        public String visit(LogicalExpr logical) {
            String left = logical.left().accept(this);
            if (left != null) return left;
            return logical.right().accept(this);
        }

        @Override
        public String visit(GroupExpr group) {
            for (CommandExpression cmd : group.commands()) {
                String result = cmd.accept(this);
                if (result != null) return result;
            }
            return null;
        }

        @Override
        public String visit(SubshellExpr subshell) {
            return subshell.inner().accept(this);
        }

        @Override
        public String visit(BackgroundExpr background) {
            return background.inner().accept(this);
        }
    }

    protected CompletionStage<ExecutionResult> executeExpression(CommandExpression expr, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        return expr.accept(new CommandExpressionVisitor(context, cancelToken));
    }

    private class CommandExpressionVisitor implements CommandVisitor<CompletionStage<ExecutionResult>> {
        private final IShellCommandExecutionContext baseContext;
        private final ICancelToken cancelToken;

        public CommandExpressionVisitor(IShellCommandExecutionContext baseContext, ICancelToken cancelToken) {
            this.baseContext = baseContext;
            this.cancelToken = cancelToken;
        }

        @Override
        public CompletionStage<ExecutionResult> visit(SimpleCommand cmd) {
            return executeSimpleCommand(cmd, baseContext, cancelToken);
        }

        @Override
        public CompletionStage<ExecutionResult> visit(PipelineExpr pipeline) {
            return executePipeline(pipeline, baseContext, cancelToken);
        }

        @Override
        public CompletionStage<ExecutionResult> visit(LogicalExpr logical) {
            return executeLogicalExpr(logical, baseContext, cancelToken);
        }

        @Override
        public CompletionStage<ExecutionResult> visit(GroupExpr group) {
            return executeGroup(group, baseContext, cancelToken);
        }

        @Override
        public CompletionStage<ExecutionResult> visit(SubshellExpr subshell) {
            return executeSubshell(subshell, baseContext, cancelToken);
        }

        @Override
        public CompletionStage<ExecutionResult> visit(BackgroundExpr background) {
            return executeBackground(background, baseContext, cancelToken);
        }
    }

    private CompletionStage<ExecutionResult> executeSimpleCommand(SimpleCommand cmd, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        String commandName = cmd.getCommand();
        IShellCommand command = registry.findCommand(commandName);

        if (command == null) {
            return FutureHelper.success(new ExecutionResult(127, "", "Command not found: " + commandName));
        }

        // cd 目标不存在（或超出沙箱拒绝访问）时显式失败，不改变工作目录
        // （bash 语义：cd 到不存在的目录报错且保持原目录；不静默忽略）
        if (commandName.equals("cd") && !cmd.getArgs().isEmpty() && fileSystem != null) {
            String targetDir = resolvePath(currentWorkingDir, cmd.getArgs().get(0));
            boolean isDir;
            try {
                isDir = fileSystem.isDirectory(targetDir);
            } catch (Exception e) {
                isDir = false;
            }
            if (!isDir) {
                return FutureHelper.success(
                        new ExecutionResult(1, "", "cd: " + cmd.getArgs().get(0) + ": No such file or directory"));
            }
        }

        BlockingQueueShellOutput stdoutOutput = new BlockingQueueShellOutput();
        BlockingQueueShellOutput stderrOutput = new BlockingQueueShellOutput();

        // C4-003 C8-001 mechanism A (MA-06, plan 2501 Phase 3): collectors must run
        // CONCURRENTLY with production — the previous produce-then-collect order
        // deadlocked the calling thread on queue.put once a command emitted more
        // than the queue capacity (1024 chunks). Collector submission failure
        // (saturated pool) fails loud BEFORE the command runs — no partial state.
        CompletableFuture<String> stdoutText;
        CompletableFuture<String> stderrText;
        try {
            stdoutText = submitCollector(stdoutOutput);
            stderrText = submitCollector(stderrOutput);
        } catch (RejectedExecutionException e) {
            IoHelper.safeClose(stdoutOutput);
            IoHelper.safeClose(stderrOutput);
            throw NopException.adapt(e);
        }

        try {
            int exitCode = executeSimpleCommandWithContext(cmd, context.stdin(), stdoutOutput, stderrOutput, context, cancelToken);

            stdoutOutput.close();
            stderrOutput.close();

            String stdout = stdoutText.join();
            String stderr = stderrText.join();

            ExecutionResult result = new ExecutionResult(exitCode, stdout, stderr);
            updateContextFromResult(cmd, result, context);
            return FutureHelper.success(result);
        } catch (Exception e) {
            IoHelper.safeClose(stdoutOutput);
            IoHelper.safeClose(stderrOutput);
            return FutureHelper.reject(e);
        }
    }

    /**
     * C4-003 (MA-06, plan 2501 Phase 3): overflow / rejection declaration. Stage
     * and collector tasks run on the injected (or default dedicated) bounded pool:
     * pool saturation fails loud with {@link RejectedExecutionException} and is
     * rolled back (see {@link #rollbackStages}); stage output queues are bounded
     * (1024 chunks) with blocking writes — the bounded-queue backpressure is
     * consumed by the always-started concurrent collector (pipeline last stage) or
     * the next pipeline stage, and every abort path closes the queues it created
     * and cancels the stages it submitted, so no producer stays pinned on a
     * consumer that will never exist.
     */
    private CompletableFuture<String> submitCollector(BlockingQueueShellOutput output) {
        Callable<String> drain = () -> collectOutput(output);
        if (executor instanceof IThreadPoolExecutor) {
            return ((IThreadPoolExecutor) executor).submit(drain);
        }
        if (executor instanceof ExecutorService) {
            return ExecutorHelper.submit((ExecutorService) executor, drain);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return drain.call();
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }, executor);
    }

    /**
     * C4-003 (MA-06): interrupt-capable stage submission — stage futures backed by
     * a real FutureTask (platform bindCancel) so {@code cancel(true)} in the
     * rollback / upstream-unblock paths genuinely interrupts a stage blocked in
     * {@code queue.put}. A plain-Executor injection falls back to supplyAsync
     * (cancel marks the future; the close-fail-loud path still aborts producers
     * not yet inside put).
     */
    private CompletableFuture<Integer> submitStage(Callable<Integer> task) {
        if (executor instanceof IThreadPoolExecutor) {
            return ((IThreadPoolExecutor) executor).submit(task);
        }
        if (executor instanceof ExecutorService) {
            return ExecutorHelper.submit((ExecutorService) executor, task);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        }, executor);
    }

    /**
     * C4-003 (MA-06): submission-abort / early-exit rollback — cancel the stages
     * already submitted (interrupts a running stage on FutureTask-backed futures)
     * and close every queue this pipeline created, so producers not yet inside
     * {@code queue.put} fail loud on the next write and no thread stays pinned on
     * a consumer that will never be submitted. Rollback is observable (WARN).
     */
    private void rollbackStages(List<CompletableFuture<Integer>> stageFutures,
                                List<BlockingQueueShellOutput> stageOutputs, String reason) {
        if (stageFutures.isEmpty()) {
            return;
        }
        LOG.warn("ShellCommandExecutor: pipeline rollback ({} committed stage(s)): {}", stageFutures.size(), reason);
        for (CompletableFuture<Integer> stageFuture : stageFutures) {
            stageFuture.cancel(true);
        }
        for (BlockingQueueShellOutput out : stageOutputs) {
            IoHelper.safeClose(out);
        }
    }

    private CompletionStage<ExecutionResult> executePipeline(PipelineExpr pipeline, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        List<CommandExpression> commands = pipeline.commands();
        IShellInput currentInput = context.stdin();
        BlockingQueueShellOutput prevOutput = null;
        List<CompletableFuture<Integer>> stageFutures = new ArrayList<>();
        List<BlockingQueueShellOutput> stageOutputs = new ArrayList<>();

        for (int i = 0; i < commands.size(); i++) {
            CommandExpression cmdExpr = commands.get(i);

            if (!(cmdExpr instanceof SimpleCommand)) {
                // C6-004 early-exit face (MA-06): stages may already be committed —
                // roll them back instead of abandoning them
                rollbackStages(stageFutures, stageOutputs,
                        "pipeline does not support complex commands (mid-pipeline early exit)");
                return FutureHelper.success(new ExecutionResult(1, "", "Pipeline does not support complex commands yet"));
            }

            SimpleCommand cmd = (SimpleCommand) cmdExpr;
            BlockingQueueShellOutput output = new BlockingQueueShellOutput();
            IShellInput stageInput = (i == 0) ? currentInput : prevOutput.asInput();

            final IShellInput input = stageInput;
            final IShellOutput out = output;

            try {
                CompletableFuture<Integer> stageFuture = submitStage(() -> {
                    try {
                        return executeSimpleCommandWithContext(cmd, input, out, context.stderr(), context, cancelToken);
                    } catch (Exception e) {
                        throw NopException.adapt(e);
                    } finally {
                        IoHelper.safeClose(out);
                    }
                });
                stageFutures.add(stageFuture);
            } catch (RejectedExecutionException e) {
                // C4-003 (MA-06): submission abort — the consumer of the last committed
                // stage will never be submitted; cancel what runs and close what was
                // created, then fail loud
                rollbackStages(stageFutures, stageOutputs, "stage submission rejected (pool saturated)");
                throw NopException.adapt(e);
            }
            stageOutputs.add(output);
            prevOutput = output;
        }

        // C8-003 face (MA-06): failure propagation never travelled upstream — a dead
        // stage's producer blocked in queue.put forever. Every stage failure now
        // cancels its upstream stages (interrupt aborts their put) and is observed
        // (CL-008①: a non-last stage's exception is no longer silently dropped).
        for (int i = 0; i < stageFutures.size(); i++) {
            int stageIndex = i;
            stageFutures.get(i).whenComplete((v, e) -> {
                if (e != null) {
                    LOG.warn("ShellCommandExecutor: pipeline stage {} failed", stageIndex, e);
                    for (int upstream = 0; upstream < stageIndex; upstream++) {
                        stageFutures.get(upstream).cancel(true);
                    }
                }
            });
        }

        BlockingQueueShellOutput lastOutput = prevOutput;

        // C8-001 mechanism B (MA-06): the last stage's collection starts CONCURRENTLY
        // with its production — chaining collectOutput only after the last stage
        // future completed deadlocked that stage on its own queue for >1024-chunk
        // outputs (the collector was topologically forbidden from starting).
        CompletableFuture<String> lastStdout;
        try {
            lastStdout = submitCollector(lastOutput);
        } catch (RejectedExecutionException e) {
            rollbackStages(stageFutures, stageOutputs, "last-stage collection submission rejected (pool saturated)");
            throw NopException.adapt(e);
        }

        return stageFutures.get(stageFutures.size() - 1).thenCombine(lastStdout,
                (lastExitCode, stdout) -> new ExecutionResult(lastExitCode, stdout, ""));
    }

    private CompletionStage<ExecutionResult> executeLogicalExpr(LogicalExpr logical, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        switch (logical.operator()) {
            case SEMICOLON:
                return executeExpression(logical.left(), context, cancelToken)
                        .thenCompose(result1 -> executeExpression(logical.right(), context, cancelToken)
                                .thenApply(result2 -> new ExecutionResult(result2.exitCode(), result1.stdout() + result2.stdout(), result2.stderr())));

            case AND:
                return executeExpression(logical.left(), context, cancelToken)
                        .thenCompose(result1 -> {
                            if (result1.exitCode() != 0) {
                                return FutureHelper.success(result1);
                            }
                            return executeExpression(logical.right(), context, cancelToken);
                        });

            case OR:
                return executeExpression(logical.left(), context, cancelToken)
                        .thenCompose(result1 -> {
                            if (result1.exitCode() == 0) {
                                return FutureHelper.success(result1);
                            }
                            return executeExpression(logical.right(), context, cancelToken);
                        });

            default:
                return FutureHelper.success(new ExecutionResult(1, "", "Unknown logical operator"));
        }
    }

    private CompletionStage<ExecutionResult> executeGroup(GroupExpr group, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        Map<String, String> savedEnv = new HashMap<>(this.exportedEnv);
        String savedDir = this.currentWorkingDir;

        try {
            // bash 语义：组返回组内最后一条命令的退出码与输出（stdout 聚合）
            return executeSequence(group.commands(), context, cancelToken)
                    .whenComplete((v, ex) -> {
                        this.exportedEnv = savedEnv;
                        this.currentWorkingDir = savedDir;
                    });
        } catch (Exception e) {
            this.exportedEnv = savedEnv;
            this.currentWorkingDir = savedDir;
            throw e;
        }
    }

    private CompletionStage<ExecutionResult> executeSubshell(SubshellExpr subshell, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        Map<String, String> parentExportedEnv = new HashMap<>(this.exportedEnv);
        String parentWorkingDir = this.currentWorkingDir;

        this.exportedEnv = new HashMap<>();
        this.currentWorkingDir = parentWorkingDir;

        return executeExpression(subshell.inner(), context, cancelToken)
                .whenComplete((result, ex) -> {
                    this.exportedEnv = parentExportedEnv;
                    this.currentWorkingDir = parentWorkingDir;
                });
    }

    private CompletionStage<ExecutionResult> executeBackground(BackgroundExpr background, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        // Plan 2301 (WI25): '&' closure lives in the background job sink —
        // the pre-WI25 form started the job and lost it (instance-counted
        // jobId, no query/wait/cancel/output recovery). No sink wired =
        // fail loud (non-zero exit) instead of silently losing the job.
        IBackgroundJobSink sink = this.backgroundJobSink;
        if (sink == null) {
            return FutureHelper.success(new ExecutionResult(126, "",
                    "'&' background execution is not available: no IBackgroundJobSink is wired "
                            + "(start-and-lose background jobs are rejected — wire a sink "
                            + "or run the command in the foreground)"));
        }

        String taskId = sink.submit(String.valueOf(background.inner()),
                () -> executeExpression(background.inner(), context, cancelToken)
                        .toCompletableFuture().join());

        return FutureHelper.success(
                new ExecutionResult(0, "[taskId=" + taskId + "] running in background", "")
        );
    }

    /** Plan 2301 (WI25): wire the addressable-lifecycle sink for {@code &} jobs. */
    public void setBackgroundJobSink(IBackgroundJobSink backgroundJobSink) {
        this.backgroundJobSink = backgroundJobSink;
    }

    private CompletionStage<ExecutionResult> executeSequence(List<CommandExpression> commands, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        // 聚合执行结果：stdout 拼接、退出码与 stderr 取最后一条命令（与 executeLogicalExpr
        // SEMICOLON 分支同一口径）；环境/目录副作用由 executeSimpleCommand 的
        // updateContextFromResult 就地更新，group/subshell 的快照恢复负责隔离
        CompletionStage<ExecutionResult> stage = FutureHelper.success(new ExecutionResult(0, "", ""));
        for (CommandExpression cmd : commands) {
            stage = stage.thenCompose(prev -> executeExpression(cmd, context, cancelToken)
                    .thenApply(cur -> new ExecutionResult(cur.exitCode(), prev.stdout() + cur.stdout(), cur.stderr())));
        }
        return stage;
    }

    private int executeSimpleCommandWithContext(SimpleCommand cmd, IShellInput stdin, IShellOutput stdout, IShellOutput stderr, IShellCommandExecutionContext context, ICancelToken cancelToken) throws Exception {
        String commandName = cmd.getCommand();
        IShellCommand command = registry.findCommand(commandName);

        if (command == null) {
            try {
                return externalAdapter.execute(cmd, stdin, stdout, stderr, cancelToken);
            } catch (UnsupportedOperationException e) {
                stderr.println("Command not found: " + commandName);
                return 127;
            }
        }

        Map<String, String> env = buildEnvironment(cmd.getEnvVars(), context.environment());
        String[] args = cmd.getArgs().toArray(new String[0]);

        RedirectedStreams redirectedStreams = applyRedirects(cmd.getRedirects(), stdin, stdout, stderr);

        try {
            IShellCommandExecutionContext cmdContext = new DefaultShellExecutionContext(
                    redirectedStreams.stdin, redirectedStreams.stdout, redirectedStreams.stderr,
                    env, currentWorkingDir, args, context.fileSystem(), cancelToken
            );

            return command.execute(cmdContext);
        } finally {
            redirectedStreams.close();
        }
    }

    private static class RedirectedStreams {
        IShellInput stdin;
        IShellOutput stdout;
        IShellOutput stderr;
        private final List<IShellInput> ownedInputs = new ArrayList<>();
        private final List<IShellOutput> ownedOutputs = new ArrayList<>();

        RedirectedStreams(IShellInput stdin, IShellOutput stdout, IShellOutput stderr) {
            this.stdin = stdin;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        void addOwnedInput(IShellInput input) {
            ownedInputs.add(input);
        }

        void addOwnedOutput(IShellOutput output) {
            ownedOutputs.add(output);
        }

        void close() {
            for (IShellInput input : ownedInputs) {
                IoHelper.safeClose(input);
            }
            for (IShellOutput output : ownedOutputs) {
                IoHelper.safeClose(output);
            }
        }
    }

    private RedirectedStreams applyRedirects(List<Redirect> redirects, IShellInput stdin, IShellOutput stdout, IShellOutput stderr) {
        RedirectedStreams streams = new RedirectedStreams(stdin, stdout, stderr);

        for (Redirect redirect : redirects) {
            switch (redirect.type()) {
                case OUTPUT:
                    handleOutputRedirect(streams, redirect, false);
                    break;
                case APPEND:
                    handleOutputRedirect(streams, redirect, true);
                    break;
                case INPUT:
                    handleInputRedirect(streams, redirect);
                    break;
                case FD_OUTPUT:
                    handleFdOutputRedirect(streams, redirect);
                    break;
                case FD_INPUT:
                    handleFdInputRedirect(streams, redirect);
                    break;
                case MERGE:
                    handleMergeRedirect(streams, redirect, false);
                    break;
                case MERGE_APPEND:
                    handleMergeRedirect(streams, redirect, true);
                    break;
                case HERE_DOC:
                case HERE_STRING:
                    break;
            }
        }

        return streams;
    }

    private void handleOutputRedirect(RedirectedStreams streams, Redirect redirect, boolean append) {
        FileShellOutput fileOutput = new FileShellOutput(redirect.target(), fileSystem, append);
        streams.stdout = fileOutput;
        streams.addOwnedOutput(fileOutput);
    }

    private void handleInputRedirect(RedirectedStreams streams, Redirect redirect) {
        FileShellInput fileInput = new FileShellInput(redirect.target(), fileSystem);
        streams.stdin = fileInput;
        streams.addOwnedInput(fileInput);
    }

    private void handleFdOutputRedirect(RedirectedStreams streams, Redirect redirect) {
        int sourceFd = redirect.sourceFd() != null ? redirect.sourceFd() : 1;
        String targetStr = redirect.target();

        try {
            int targetFd = Integer.parseInt(targetStr);

            switch (sourceFd) {
                case 1:
                    if (targetFd == 2) {
                        streams.stdout = streams.stderr;
                    }
                    break;
                case 2:
                    if (targetFd == 1) {
                        streams.stderr = new DuplexShellOutput(streams.stdout);
                    }
                    break;
            }
        } catch (NumberFormatException e) { /* non-numeric fd target, ignore */ }
    }

    private void handleFdInputRedirect(RedirectedStreams streams, Redirect redirect) {
        int sourceFd = redirect.sourceFd() != null ? redirect.sourceFd() : 0;
        String targetStr = redirect.target();

        int targetFd;
        try {
            targetFd = Integer.parseInt(targetStr);
        } catch (NumberFormatException e) {
            // C8-011 (MA-06, plan 2501 Phase 3): FD_INPUT combinations were silently
            // ignored pre-fix — silent misdirection is the same face; make it explicit
            throw new NopException(ERR_AI_SHELL_INVALID_REDIRECT).param(ARG_MSG,
                    "non-numeric fd input redirect target: " + targetStr);
        }

        if (sourceFd == 0 && targetFd == 1) {
            // C8-011 (MA-06, plan 2501 Phase 3): stdin aliased to the command's own
            // stdout queue is a deterministic deadlock — nothing will ever produce the
            // chunk the reader waits for. Reject loud instead of hanging the caller.
            throw new NopException(ERR_AI_SHELL_INVALID_REDIRECT).param(ARG_MSG,
                    "0<&1 self-referential input redirection rejected: stdin would read this "
                            + "command's own stdout queue and deadlock");
        }
        // 其余 FD_INPUT 组合（修复前静默忽略面，C8-011 同修）：显式拒绝
        throw new NopException(ERR_AI_SHELL_INVALID_REDIRECT).param(ARG_MSG,
                "unsupported fd input redirection " + sourceFd + "<&" + targetFd);
    }

    private void handleMergeRedirect(RedirectedStreams streams, Redirect redirect, boolean append) {
        FileShellOutput fileOutput = new FileShellOutput(redirect.target(), fileSystem, append);
        // M6-P1 (round-2 audit): stdout and stderr share ONE output target.
        // Pre-fix this was new TeeOutput(fileOutput, fileOutput) — the same
        // instance twice — so TeeOutput.write fanned every chunk out to two
        // legs writing the same buffer, doubling the merged file content on
        // each flush (echo stdout &> f produced two lines; CI only asserted
        // contains("stdout") so the doubling stayed green).
        streams.stdout = fileOutput;
        streams.stderr = fileOutput;
        streams.addOwnedOutput(fileOutput);
    }

    private Map<String, String> buildEnvironment(List<EnvVar> envVars, Map<String, String> baseEnv) {
        Map<String, String> env = new HashMap<>(baseEnv);
        env.putAll(exportedEnv);

        for (EnvVar envVar : envVars) {
            if (envVar.type() == EnvVar.Type.EXPORT) {
                exportedEnv.put(envVar.name(), envVar.value());
            }
            env.put(envVar.name(), envVar.value());
        }

        return env;
    }

    private void updateContextFromResult(CommandExpression cmd, ExecutionResult result, IShellCommandExecutionContext context) {
        if (cmd instanceof SimpleCommand) {
            SimpleCommand simpleCmd = (SimpleCommand) cmd;
            for (EnvVar envVar : simpleCmd.getEnvVars()) {
                if (envVar.type() == EnvVar.Type.EXPORT) {
                    exportedEnv.put(envVar.name(), envVar.value());
                }
            }
        }

        // cd 失败（非零退出码）不改变工作目录
        if (result.exitCode() != 0) {
            return;
        }

        if (cmd instanceof SimpleCommand) {
            SimpleCommand simpleCmd = (SimpleCommand) cmd;
            if (simpleCmd.getCommand().equals("cd")) {
                List<String> args = simpleCmd.getArgs();
                if (args.isEmpty()) {
                    currentWorkingDir = "/";
                } else {
                    currentWorkingDir = resolvePath(currentWorkingDir, args.get(0));
                }
            }
        }
    }

    private String resolvePath(String currentDir, String targetPath) {
        if (targetPath.startsWith("/")) {
            return normalizePath(targetPath);
        }

        String[] currentParts = currentDir.split("/");
        String[] targetParts = targetPath.split("/");

        List<String> result = new ArrayList<>();
        for (String part : currentParts) {
            if (!part.isEmpty()) {
                result.add(part);
            }
        }

        for (String part : targetParts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            } else if (part.equals("..")) {
                if (!result.isEmpty()) {
                    result.remove(result.size() - 1);
                }
            } else {
                result.add(part);
            }
        }

        return normalizePath("/" + String.join("/", result));
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        return path.isEmpty() ? "/" : path;
    }

    /**
     * C4-003 F9 (MA-06, plan 2501 Phase 3): a collection failure is observable —
     * logged and propagated — never silently mapped to an empty string.
     *
     * <p>The drain itself runs on the queue with a closed-and-drained backstop: the
     * EOF chunk is delivered by {@code close()} via a non-blocking offer (MA-05 face,
     * untouched here), so once the producer side is closed a momentarily-full queue
     * could drop the EOF chunk; {@code closed && queue.isEmpty()} is terminal
     * regardless (after close no further data chunk can arrive — {@code write()}
     * rejects on a closed output).
     */
    private String collectOutput(BlockingQueueShellOutput output) {
        try {
            BlockingQueue<ShellChunk> queue = output.getQueue();
            StringBuilder sb = new StringBuilder();
            while (true) {
                ShellChunk chunk = queue.poll(50, TimeUnit.MILLISECONDS);
                if (chunk == null) {
                    if (output.isClosed() && queue.isEmpty()) {
                        break;
                    }
                    continue;
                }
                if (chunk.isEof()) {
                    break;
                }
                if (chunk.isText()) {
                    sb.append(chunk.asText());
                }
            }
            return sb.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("ShellCommandExecutor: shell output collection interrupted", e);
            throw NopException.adapt(e);
        } catch (RuntimeException e) {
            LOG.warn("ShellCommandExecutor: failed to collect shell output", e);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        // Plan 2301 (WI25): '&' jobs are owned by the IBackgroundJobSink
        // (addressable + cancellable through its taskId channel) — close() no
        // longer tracks or cancels them.
        closed = true;
        // CRA 3.2-C4-004: an internally-created pipeline pool is owned by this
        // executor — shut it down on close (graceful; daemon threads cannot
        // block JVM exit). An injected executor is not owned and is left
        // untouched.
        if (ownedExecutor != null) {
            ownedExecutor.destroy();
        }
    }

    public Map<String, String> getExportedEnv() {
        return Collections.unmodifiableMap(exportedEnv);
    }

    public String getCurrentWorkingDir() {
        return currentWorkingDir;
    }
}
