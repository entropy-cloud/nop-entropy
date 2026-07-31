package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.core.lang.xml.XNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BashExecutor implements IToolExecutor {
    static final Logger LOG = LoggerFactory.getLogger(BashExecutor.class);
    public static final String TOOL_NAME = "bash";

    private static final Set<String> DANGEROUS_ENV_VARS = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH", "LD_DEBUG", "LD_AUDIT",
            "SHELLOPTS", "BASH_ENV", "BASH_FUNC_",
            "IFS", "PATH", "PYTHONPATH", "PERLLIB",
            "PERL5LIB", "RUBYLIB", "DYLD_INSERT_LIBRARIES"
    );

    private static final Pattern DESTRUCTIVE_COMMAND = Pattern.compile(
            "(^|\\s)(rm\\s+-[a-z]*[rf].*(/|\\s)|dd\\s|mkfs|mkfs\\..*|shutdown|reboot|init\\s|halt|poweroff|"
                    + "chmod\\s+-R\\s+777\\s+/|chown\\s+-R.*\\s+/|>\\s*/dev/(sda|sdb|nvme)|"
                    + "sudo\\s+rm\\s+-rf\\s+/|\\|\\s*bash|/dev/sd[a-z]\\s*$)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        return context.getExecutor().submit(() -> doExecute(call, context));
    }

    private AiToolCallResult doExecute(AiToolCall call, IToolExecuteContext context) {
        try {
            String command = call.childText("command", "");
            String workingDir = call.attrText("workingDir");
            Map<String, String> env = parseEnv(call);
            int timeoutMs = call.attrInt("timeoutMs", call.getTimeoutMs() != null ? call.getTimeoutMs() : 30000);

            String validationError = validateCommand(command);
            if (validationError != null) {
                return AiToolCallResult.errorResult(call.getId(),
                        "Command blocked: " + validationError);
            }

            if (workingDir == null || workingDir.isEmpty()) {
                workingDir = context.getWorkDir() != null ? context.getWorkDir().getAbsolutePath() : ".";
            }

            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                pb.command("cmd", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            pb.directory(new File(workingDir));
            pb.redirectErrorStream(false);

            Map<String, String> processEnv = pb.environment();
            processEnv.putAll(env);

            Process process = pb.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (Exception e) {
                    LOG.warn("BashExecutor.stdout-read-failed", e);
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (Exception e) {
                    LOG.warn("BashExecutor.stderr-read-failed", e);
                }
            });

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return AiToolCallResult.errorResult(call.getId(), "Command timed out after " + timeoutMs + "ms");
            }

            stdoutThread.join(1000);
            stderrThread.join(1000);

            int exitCode = process.exitValue();

            AiToolCallResult result = new AiToolCallResult();
            result.setId(call.getId());
            result.setExitCode(exitCode);

            if (exitCode == 0) {
                result.setStatus("success");
                AiToolOutput output = new AiToolOutput();
                output.setBody(stdout.toString().trim());
                result.setOutput(output);
            } else {
                result.setStatus("failure");
                result.setError(new io.nop.ai.toolkit.model.AiToolError());
                result.getError().setBody(stderr.length() > 0 ? stderr.toString().trim() : "Command exited with code " + exitCode);
            }

            return result;
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private String validateCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "empty command";
        }
        if (DESTRUCTIVE_COMMAND.matcher(command).find()) {
            return "destructive command pattern detected: " + command;
        }
        return null;
    }

    private Map<String, String> parseEnv(AiToolCall call) {
        Map<String, String> env = new HashMap<>();
        XNode node = call.getNode();
        if (node == null) return env;

        List<XNode> envNodes = node.childrenByTag("env");
        if (envNodes == null) return env;

        for (XNode envNode : envNodes) {
            String name = envNode.attrText("name");
            String value = envNode.attrText("value");
            if (name != null && value != null) {
                String upperName = name.toUpperCase();
                if (DANGEROUS_ENV_VARS.contains(upperName)) {
                    LOG.warn("BashExecutor: rejecting dangerous env var {}", name);
                    continue;
                }
                if (name.startsWith("-")) {
                    LOG.warn("BashExecutor: rejecting env var with leading dash: {}", name);
                    continue;
                }
                env.put(name, value);
            }
        }
        return env;
    }
}
