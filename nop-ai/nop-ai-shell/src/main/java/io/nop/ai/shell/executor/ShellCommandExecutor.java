package io.nop.ai.shell.executor;

import io.nop.ai.shell.adapter.ExternalCommandAdapter;
import io.nop.ai.shell.checker.ICommandCheckContext;
import io.nop.ai.shell.checker.ICommandChecker;
import io.nop.ai.shell.commands.DefaultShellExecutionContext;
import io.nop.ai.shell.commands.IShellCommand;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.commands.ShellCommandRegistry;
import io.nop.ai.shell.io.AbstractShellInput;
import io.nop.ai.shell.io.BlockingQueueShellOutput;
import io.nop.ai.shell.io.DuplexShellOutput;
import io.nop.ai.shell.io.FileShellInput;
import io.nop.ai.shell.io.FileShellOutput;
import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.ai.shell.io.TeeOutput;
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

public class ShellCommandExecutor implements Closeable {

    private final ShellCommandRegistry registry;
    private final Executor executor;
    private final ICommandChecker checker;
    private final ExternalCommandAdapter externalAdapter;
    private final IToolFileSystem fileSystem;

    private Map<String, String> exportedEnv = new HashMap<>();
    private String currentWorkingDir = "/";

    private final Map<String, CompletableFuture<?>> backgroundJobs = new LinkedHashMap<>();
    private final AtomicLong jobIdCounter = new AtomicLong(0);
    private volatile boolean closed = false;

    public ShellCommandExecutor(ShellCommandRegistry registry, Executor executor, ICommandChecker checker, IToolFileSystem fileSystem) {
        this.registry = registry;
        this.executor = executor != null ? executor : GlobalExecutors.globalWorker();
        this.checker = checker;
        this.externalAdapter = new ExternalCommandAdapter();
        this.fileSystem = fileSystem;
    }

    public ShellCommandExecutor(ShellCommandRegistry registry, IToolFileSystem fileSystem) {
        this(registry, null, null, fileSystem);
    }

    public CompletionStage<ExecutionResult> execute(String commandLine, IShellCommandExecutionContext context) {
        return execute(commandLine, context, context.cancelToken());
    }

    public CompletionStage<ExecutionResult> execute(String commandLine, IShellCommandExecutionContext context, ICancelToken cancelToken) {
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

        BlockingQueueShellOutput stdoutOutput = new BlockingQueueShellOutput();
        BlockingQueueShellOutput stderrOutput = new BlockingQueueShellOutput();

        try {
            int exitCode = executeSimpleCommandWithContext(cmd, context.stdin(), stdoutOutput, stderrOutput, context, cancelToken);

            stdoutOutput.close();
            stderrOutput.close();

            String stdout = collectOutput(stdoutOutput);
            String stderr = collectOutput(stderrOutput);

            return FutureHelper.success(new ExecutionResult(exitCode, stdout, stderr));
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
            return executeSequence(group.commands(), context, cancelToken, true)
                    .whenComplete((v, ex) -> {
                        this.exportedEnv = savedEnv;
                        this.currentWorkingDir = savedDir;
                    }).thenApply(v -> new ExecutionResult(0, "", ""));
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

    private CompletionStage<Void> executeSequence(List<CommandExpression> commands, IShellCommandExecutionContext context, ICancelToken cancelToken, boolean isolatedEnv) {
        if (commands.isEmpty()) {
            return FutureHelper.voidPromise();
        }

        CompletionStage<Void> stage = FutureHelper.voidPromise();
        for (CommandExpression cmd : commands) {
            stage = stage.thenCompose(v -> {
                CompletionStage<ExecutionResult> resultStage = executeExpression(cmd, context, cancelToken);
                return resultStage.thenApply(r -> {
                    updateContextFromResult(cmd, r, context, isolatedEnv);
                    return null;
                });
            });
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
                    env, context.workingDirectory(), args, context.fileSystem(), cancelToken
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
        TeeOutput teeOutput = new TeeOutput(fileOutput, fileOutput);
        streams.stdout = teeOutput;
        streams.stderr = teeOutput;
        streams.addOwnedOutput(fileOutput);
        streams.addOwnedOutput(teeOutput);
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

    private void updateContextFromResult(CommandExpression cmd, ExecutionResult result, IShellCommandExecutionContext context, boolean isolatedEnv) {
        if (cmd instanceof SimpleCommand) {
            SimpleCommand simpleCmd = (SimpleCommand) cmd;
            for (EnvVar envVar : simpleCmd.getEnvVars()) {
                if (envVar.type() == EnvVar.Type.EXPORT) {
                    exportedEnv.put(envVar.name(), envVar.value());
                }
            }
        }

        if (cmd instanceof SimpleCommand) {
            SimpleCommand simpleCmd = (SimpleCommand) cmd;
            if (simpleCmd.getCommand().equals("cd")) {
                List<String> args = simpleCmd.getArgs();
                if (!args.isEmpty()) {
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
            IShellInput input = output.asInput();
            if (input instanceof AbstractShellInput) {
                return ((AbstractShellInput) input).readAllText();
            }
            StringBuilder sb = new StringBuilder();
            while (true) {
                var chunk = input.read();
                if (chunk == null || chunk.isEof()) break;
                if (chunk.isText()) {
                    sb.append(chunk.asText());
                }
            }
            return sb.toString();
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
