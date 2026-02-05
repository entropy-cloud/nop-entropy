package io.nop.ai.shell.executor;

import io.nop.ai.shell.commands.IShellCommand;
import io.nop.ai.shell.commands.IShellCommandExecutionContext;
import io.nop.ai.shell.commands.ShellCommandRegistry;
import io.nop.ai.shell.io.BlockingQueueShellOutput;
import io.nop.ai.shell.io.IShellInput;
import io.nop.ai.shell.io.IShellOutput;
import io.nop.ai.shell.model.*;
import io.nop.ai.shell.parser.BashSyntaxParser;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.core.execution.IExecution;
import io.nop.core.execution.TaskExecutionGraph;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Shell命令执行器
 * <p>
 * 负责将命令行解析为AST，转换为TaskExecutionGraph并执行。
 * 支持管道、环境变量传播、工作目录继承等bash特性。
 * </p>
 */
public class ShellCommandExecutor {

    private final ShellCommandRegistry registry;
    private final Executor executor;

    private Map<String, String> exportedEnv = new HashMap<>();
    private String currentWorkingDir = "/";

    public ShellCommandExecutor(ShellCommandRegistry registry, Executor executor) {
        this.registry = registry;
        this.executor = executor != null ? executor : GlobalExecutors.globalWorker();
    }

    public ShellCommandExecutor(ShellCommandRegistry registry) {
        this(registry, null);
    }

    public CompletionStage<ExecutionResult> execute(String commandLine, IShellCommandExecutionContext context) {
        return execute(commandLine, context, context.cancelToken());
    }

    public CompletionStage<ExecutionResult> execute(String commandLine, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        BashSyntaxParser parser = new BashSyntaxParser(commandLine);
        CommandExpression expr = parser.parse();

        return executeExpression(expr, context, cancelToken);
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

            String stdout = collectOutput(stdoutOutput);
            String stderr = collectOutput(stderrOutput);

            return FutureHelper.success(new ExecutionResult(exitCode, stdout, stderr));
        } catch (Exception e) {
            return FutureHelper.reject(e);
        } finally {
            try {
                stdoutOutput.close();
            } catch (Exception e) {
            }
            try {
                stderrOutput.close();
            } catch (Exception e) {
            }
        }
    }

    private CompletionStage<ExecutionResult> executePipeline(PipelineExpr pipeline, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        TaskExecutionGraph graph = new TaskExecutionGraph(executor, "pipeline");

        List<BlockingQueueShellOutput> outputs = new ArrayList<>();
        List<IShellInput> inputs = new ArrayList<>();
        List<CommandExpression> commands = pipeline.commands();

        IShellInput currentInput = context.stdin();

        for (int i = 0; i < commands.size(); i++) {
            CommandExpression cmdExpr = commands.get(i);
            BlockingQueueShellOutput output = new BlockingQueueShellOutput();
            outputs.add(output);

            final IShellInput cmdInput = (i == 0) ? currentInput : inputs.get(i - 1);
            inputs.add(cmdInput);

            if (cmdExpr instanceof SimpleCommand) {
                final BlockingQueueShellOutput finalOutput = output;
                SimpleCommand cmd = (SimpleCommand) cmdExpr;

                graph.addTask("pipe-task-" + i, (IExecution<Integer>) token -> {
                    try {
                        return FutureHelper.success(executeSimpleCommandWithContext(cmd, cmdInput, finalOutput, context.stderr(), context, token));
                    } catch (Exception e) {
                        return FutureHelper.reject(e);
                    }
                });

                if (i > 0) {
                    graph.addDepend("pipe-task-" + i, "pipe-task-" + (i - 1));
                }
            } else {
                return FutureHelper.success(new ExecutionResult(1, "", "Pipeline does not support complex commands yet"));
            }
        }

        BlockingQueueShellOutput lastOutput = outputs.get(outputs.size() - 1);

        return graph.executeAsync(cancelToken).thenApply(v -> {
            String lastStdout = collectOutput(lastOutput);
            return new ExecutionResult(0, lastStdout, "");
        });
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
        Map<String, String> originalExportedEnv = new HashMap<>(exportedEnv);
        String originalWorkingDir = currentWorkingDir;

        final CompletableFuture<ExecutionResult> result = new CompletableFuture<>();

        executeSequence(group.commands(), context, cancelToken, true).thenRun(() -> result.complete(new ExecutionResult(0, "", "")));

        result.thenAccept(r -> {
            exportedEnv = originalExportedEnv;
            currentWorkingDir = originalWorkingDir;
        });

        return result;
    }

    private CompletionStage<ExecutionResult> executeSubshell(SubshellExpr subshell, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        Map<String, String> parentExportedEnv = new HashMap<>(exportedEnv);
        String parentWorkingDir = currentWorkingDir;

        exportedEnv = new HashMap<>();
        currentWorkingDir = parentWorkingDir;

        CompletionStage<ExecutionResult> result = executeExpression(subshell.inner(), context, cancelToken);

        result.thenAccept(r -> {
            exportedEnv = parentExportedEnv;
            currentWorkingDir = parentWorkingDir;
        });

        return result;
    }

    private CompletionStage<ExecutionResult> executeBackground(BackgroundExpr background, IShellCommandExecutionContext context, ICancelToken cancelToken) {
        return executeExpression(background.inner(), context, cancelToken);
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
            context.stderr().println("Command not found: " + commandName);
            return 127;
        }

        Map<String, String> env = buildEnvironment(cmd.getEnvVars(), context.environment());
        String[] args = cmd.getArgs().toArray(new String[0]);

        RedirectedStreams redirectedStreams = applyRedirects(cmd.getRedirects(), stdin, stdout, stderr, context.workingDirectory());

        try {
            IShellCommandExecutionContext cmdContext = new IShellCommandExecutionContext() {
                @Override
                public IShellInput stdin() { return redirectedStreams.stdin; }

                @Override
                public IShellOutput stdout() { return redirectedStreams.stdout; }

                @Override
                public IShellOutput stderr() { return redirectedStreams.stderr; }

                @Override
                public Map<String, String> environment() { return env; }

                @Override
                public String workingDirectory() { return context.workingDirectory(); }

                @Override
                public String[] arguments() { return args; }

                @Override
                public boolean hasFlag(String flag) { return false; }

                @Override
                public String getFlagValue(String flag) { return null; }

                @Override
                public String[] positionalArguments() { return args; }

                @Override
                public io.nop.core.resource.IResourceStore resourceStore() { return context.resourceStore(); }

                @Override
                public ICancelToken cancelToken() { return cancelToken; }
            };

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
                try {
                    input.close();
                } catch (Exception e) {
                }
            }
            for (IShellOutput output : ownedOutputs) {
                try {
                    output.close();
                } catch (Exception e) {
                }
            }
        }
    }

    private RedirectedStreams applyRedirects(List<Redirect> redirects, IShellInput stdin, IShellOutput stdout, IShellOutput stderr, String workingDir) {
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
        io.nop.ai.shell.io.FileShellOutput fileOutput = new io.nop.ai.shell.io.FileShellOutput(redirect.target(), append);
        streams.stdout = fileOutput;
        streams.addOwnedOutput(fileOutput);
    }

    private void handleInputRedirect(RedirectedStreams streams, Redirect redirect) {
        io.nop.ai.shell.io.FileShellInput fileInput = new io.nop.ai.shell.io.FileShellInput(redirect.target());
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
                    } else if (targetFd == 1) {
                    }
                    break;
                case 2:
                    if (targetFd == 1) {
                        streams.stderr = new io.nop.ai.shell.io.DuplexShellOutput(streams.stdout);
                    } else if (targetFd == 2) {
                    }
                    break;
            }
        } catch (NumberFormatException e) {
        }
    }

    private void handleFdInputRedirect(RedirectedStreams streams, Redirect redirect) {
        int sourceFd = redirect.sourceFd() != null ? redirect.sourceFd() : 0;
        String targetStr = redirect.target();

        try {
            int targetFd = Integer.parseInt(targetStr);

            if (sourceFd == 0 && targetFd == 1) {
                streams.stdin = streams.stdout.asInput();
            }
        } catch (NumberFormatException e) {
        }
    }

    private void handleMergeRedirect(RedirectedStreams streams, Redirect redirect, boolean append) {
        io.nop.ai.shell.io.FileShellOutput fileOutput = new io.nop.ai.shell.io.FileShellOutput(redirect.target(), append);
        io.nop.ai.shell.io.TeeOutput teeOutput = new io.nop.ai.shell.io.TeeOutput(fileOutput, fileOutput);
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
            StringBuilder sb = new StringBuilder();
            java.util.Iterator<String> it = output.asInput().lines();
            while (it.hasNext()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(it.next());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static class ExecutionResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public ExecutionResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int exitCode() {
            return exitCode;
        }

        public String stdout() {
            return stdout;
        }

        public String stderr() {
            return stderr;
        }
    }
}
