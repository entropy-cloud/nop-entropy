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
import io.nop.ai.shell.model.*;
import io.nop.ai.shell.parser.BashSyntaxParser;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.IoHelper;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellCommandExecutor implements Closeable {

    static final Logger LOG = LoggerFactory.getLogger(ShellCommandExecutor.class);

    private final ShellCommandRegistry registry;
    private final Executor executor;
    private final ICommandChecker checker;
    private final ExternalCommandAdapter externalAdapter;
    private final IToolFileSystem fileSystem;

    private Map<String, String> exportedEnv = new HashMap<>();
    private String currentWorkingDir = "/";
    private boolean workingDirInitialized = false;

    private final Map<String, CompletableFuture<?>> backgroundJobs = new LinkedHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(0);
    private volatile boolean closed = false;

    public ShellCommandExecutor(ShellCommandRegistry registry, Executor executor, ICommandChecker checker, IToolFileSystem fileSystem) {
        this.registry = registry;
        this.executor = executor != null ? executor : GlobalExecutors.globalWorker();
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

        try {
            int exitCode = executeSimpleCommandWithContext(cmd, context.stdin(), stdoutOutput, stderrOutput, context, cancelToken);

            stdoutOutput.close();
            stderrOutput.close();

            String stdout = collectOutput(stdoutOutput);
            String stderr = collectOutput(stderrOutput);

            ExecutionResult result = new ExecutionResult(exitCode, stdout, stderr);
            updateContextFromResult(cmd, result, context);
            return FutureHelper.success(result);
        } catch (Exception e) {
            IoHelper.safeClose(stdoutOutput);
            IoHelper.safeClose(stderrOutput);
            return FutureHelper.reject(e);
        }
    }

    private CompletionStage<ExecutionResult> executePipeline(PipelineExpr pipeline, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        List<CommandExpression> commands = pipeline.commands();
        IShellInput currentInput = context.stdin();
        BlockingQueueShellOutput prevOutput = null;
        List<CompletableFuture<Integer>> stageFutures = new ArrayList<>();

        for (int i = 0; i < commands.size(); i++) {
            CommandExpression cmdExpr = commands.get(i);

            if (!(cmdExpr instanceof SimpleCommand)) {
                return FutureHelper.success(new ExecutionResult(1, "", "Pipeline does not support complex commands yet"));
            }

            SimpleCommand cmd = (SimpleCommand) cmdExpr;
            BlockingQueueShellOutput output = new BlockingQueueShellOutput();
            IShellInput stageInput = (i == 0) ? currentInput : prevOutput.asInput();

            final IShellInput input = stageInput;
            final IShellOutput out = output;

            CompletableFuture<Integer> stageFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return executeSimpleCommandWithContext(cmd, input, out, context.stderr(), context, cancelToken);
                } catch (Exception e) {
                    throw io.nop.api.core.exceptions.NopException.adapt(e);
                } finally {
                    IoHelper.safeClose(out);
                }
            }, executor);

            stageFutures.add(stageFuture);
            prevOutput = output;
        }

        BlockingQueueShellOutput lastOutput = prevOutput;

        CompletableFuture<ExecutionResult> pipelineFuture =
                stageFutures.get(stageFutures.size() - 1).thenApply(lastExitCode -> {
                    String stdout = collectOutput(lastOutput);
                    return new ExecutionResult(lastExitCode, stdout, "");
                });

        return pipelineFuture;
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
        String jobId = String.valueOf(jobIdCounter.incrementAndGet());

        CompletableFuture<ExecutionResult> bgFuture = CompletableFuture.supplyAsync(() -> {
            return executeExpression(background.inner(), context, cancelToken)
                    .toCompletableFuture().join();
        }, executor);

        backgroundJobs.put(jobId, bgFuture);

        bgFuture.whenComplete((r, ex) -> backgroundJobs.remove(jobId));

        return FutureHelper.success(
                new ExecutionResult(0, "[" + jobId + "] running in background", "")
        );
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

        try {
            int targetFd = Integer.parseInt(targetStr);

            if (sourceFd == 0 && targetFd == 1) {
                streams.stdin = streams.stdout.asInput();
            }
        } catch (NumberFormatException e) { /* non-numeric fd target, ignore */ }
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

    private String collectOutput(BlockingQueueShellOutput output) {
        try {
            return output.asInput().readAllText();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
        for (Map.Entry<String, CompletableFuture<?>> entry : backgroundJobs.entrySet()) {
            entry.getValue().cancel(true);
        }
        for (Map.Entry<String, CompletableFuture<?>> entry : backgroundJobs.entrySet()) {
            try {
                entry.getValue().get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { /* wait for cancellation, ignore timeout/interrupt */ }
        }
        backgroundJobs.clear();
    }

    public Map<String, CompletableFuture<?>> getBackgroundJobs() {
        return Collections.unmodifiableMap(backgroundJobs);
    }

    public Map<String, String> getExportedEnv() {
        return Collections.unmodifiableMap(exportedEnv);
    }

    public String getCurrentWorkingDir() {
        return currentWorkingDir;
    }
}
