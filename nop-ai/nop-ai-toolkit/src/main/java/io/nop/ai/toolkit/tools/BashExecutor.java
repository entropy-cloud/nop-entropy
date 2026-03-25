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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class BashExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "bash";

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
                } catch (Exception ignored) {
                }
            });

            Thread stderrThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (Exception ignored) {
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
                env.put(name, value);
            }
        }
        return env;
    }
}
