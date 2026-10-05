package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IBackgroundTaskRegistry;
import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolError;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxConfig;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxException;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxRequest;
import io.nop.ai.toolkit.tools.sandbox.BashSandboxResult;
import io.nop.ai.toolkit.tools.sandbox.IBashSandbox;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Executes a Bash tool call through an {@link IBashSandbox} isolation seam (plan 335 DR-3a).
 *
 * <p><b>Fail-closed default</b>: when no sandbox backend is wired ({@code sandbox == null}) the
 * executor refuses the call with an explicit error result and NEVER falls back to host
 * {@code sh -c} execution. Unrestricted host execution is only available via the explicit opt-in
 * {@code HostBashSandbox}. This replaces the legacy direct {@code ProcessBuilder("sh","-c",...)}
 * host-shell execution.
 *
 * <p>The {@code DESTRUCTIVE_COMMAND} regex is retained as defense-in-depth but is NOT the primary
 * control — the isolation backend (and its resource limits / working-directory jail) is the
 * primary control.
 */
@SuppressWarnings("unchecked")
public class BashExecutor implements IToolExecutor {
    static final Logger LOG = LoggerFactory.getLogger(BashExecutor.class);
    public static final String TOOL_NAME = "bash";

    /**
     * Prefix-shaped blacklist entry (G3-13-02): bash exports functions as env vars named
     * {@code BASH_FUNC_<name>%%} (Shellshock-style {@code BASH_FUNC_x%%=() {...}}), so this
     * entry requires prefix matching — an exact {@code Set.contains} can never fire on the
     * attack form. Kept separate from the exact-match {@link #DANGEROUS_ENV_VARS} set.
     */
    static final String BASH_FUNC_ENV_PREFIX = "BASH_FUNC_";

    private static final Set<String> DANGEROUS_ENV_VARS = Set.of(
            "LD_PRELOAD", "LD_LIBRARY_PATH", "LD_DEBUG", "LD_AUDIT",
            "SHELLOPTS", "BASH_ENV",
            "IFS", "PATH", "PYTHONPATH", "PERLLIB",
            "PERL5LIB", "RUBYLIB", "DYLD_INSERT_LIBRARIES"
    );

    private static final Pattern DESTRUCTIVE_COMMAND = Pattern.compile(
            "(^|\\s)(rm\\s+-[a-z]*[rf].*(/|\\s)|dd\\s|mkfs|mkfs\\..*|shutdown|reboot|init\\s|halt|poweroff|"
                    + "chmod\\s+-R\\s+777\\s+/|chown\\s+-R.*\\s+/|>\\s*/dev/(sda|sdb|nvme)|"
                    + "sudo\\s+rm\\s+-rf\\s+/|\\|\\s*bash|/dev/sd[a-z]\\s*$)",
            Pattern.CASE_INSENSITIVE);

    static final String NO_BACKEND_ERROR =
            "BashExecutor has no IBashSandbox backend wired (fail-closed): the bash tool refuses to "
                    + "execute on the host shell. Wire an explicit backend (e.g. HostBashSandbox for opt-in "
                    + "host execution, or DockerBashSandbox for isolation) before invoking the bash tool.";

    /**
     * Plan 2301 (WI25): default wall-clock budget for background tasks and
     * the hard cap for the model-tunable {@code backgroundTaskTimeoutMs}
     * (1h-class tasks; the foreground 30s default must not kill them).
     */
    public static final long DEFAULT_BACKGROUND_TIMEOUT_MS = 3_600_000L;
    private static final long BACKGROUND_TIMEOUT_CAP_MS = 3_600_000L;

    private IBashSandbox sandbox;

    public BashExecutor() {
    }

    public BashExecutor(IBashSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public void setSandbox(IBashSandbox sandbox) {
        this.sandbox = sandbox;
    }

    public IBashSandbox getSandbox() {
        return sandbox;
    }

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
            // Plan 2301 (WI25): dual-source argument resolution (the
            // CallAgentExecutor precedent) — the engine dispatch path
            // provides JSON arguments in call.getInput() with a null node;
            // the XML DSL path provides node attributes/children.
            Map<String, Object> jsonArgs = parseJsonArgs(call);

            if (boolArg(jsonArgs, call, "runInBackground", false)) {
                return doExecuteBackground(call, context, jsonArgs);
            }

            IBashSandbox backend = this.sandbox;
            if (backend == null) {
                LOG.warn("BashExecutor: refusing call {} — no IBashSandbox backend wired (fail-closed)",
                        call.getId());
                return AiToolCallResult.errorResult(call.getId(), NO_BACKEND_ERROR);
            }

            String command = textArg(jsonArgs, call, "command", "");
            File workingDir = resolveWorkingDir(textArg(jsonArgs, call, "workingDir", null), context);
            Map<String, String> env = parseEnv(call, jsonArgs);
            int timeoutMs = intArg(jsonArgs, call, "timeoutMs",
                    call.getTimeoutMs() != null && call.getTimeoutMs() > 0 ? call.getTimeoutMs() : 30000);

            String validationError = validateCommand(command);
            if (validationError != null) {
                return AiToolCallResult.errorResult(call.getId(), "Command blocked: " + validationError);
            }

            List<String> argv = buildArgv(command);
            BashSandboxConfig config = BashSandboxConfig.builder()
                    .wallSeconds(Math.max(1, (timeoutMs + 999) / 1000))
                    .build();

            BashSandboxRequest request = BashSandboxRequest.builder()
                    .command(argv)
                    .workingDirectory(workingDir)
                    .environmentVariables(env)
                    .config(config)
                    // Plan 2285 (WI4): address the in-flight execution by the
                    // provider call id for heartbeat progress + abandon cancel
                    .callId(call.getCallId())
                    .build();

            BashSandboxResult sr;
            try {
                sr = backend.execute(request);
            } catch (BashSandboxException e) {
                LOG.warn("BashExecutor: sandbox refused call {}: [{}]", call.getId(),
                        e.getReason(), e);
                return AiToolCallResult.errorResult(call.getId(),
                        "Sandbox refused execution [" + e.getReason() + "]");
            }

            return toResult(call.getId(), sr);
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e);
        }
    }

    /**
     * Plan 2301 (WI25): background submission — returns the startup
     * confirmation immediately; the real command runs on the registry's
     * dedicated background executor (never the foreground dispatch pool) and
     * its terminal state is bridged into the session input channel by the
     * engine (single result channel: the notification carries the taskId,
     * the full tail is read via the task-output tool).
     *
     * <p>Fail-loud seams: no agent-engine context (registry bridge UOE),
     * unwired registry, unwired sandbox, or a blocked command each return an
     * explicit error result — never a silent foreground fallback.
     */
    private AiToolCallResult doExecuteBackground(AiToolCall call, IToolExecuteContext context,
                                                 Map<String, Object> jsonArgs) {
        if (this.sandbox == null) {
            return AiToolCallResult.errorResult(call.getId(), NO_BACKEND_ERROR);
        }

        IBackgroundTaskRegistry registry;
        try {
            registry = context.getBackgroundTaskRegistry();
        } catch (UnsupportedOperationException e) {
            return AiToolCallResult.errorResult(call.getId(),
                    "runInBackground requires the agent engine context (no background task registry on "
                            + context.getClass().getSimpleName() + ")");
        }
        if (registry == null) {
            return AiToolCallResult.errorResult(call.getId(),
                    "Background task registry is not wired in this engine (runInBackground unavailable)");
        }

        String command = textArg(jsonArgs, call, "command", "");
        String validationError = validateCommand(command);
        if (validationError != null) {
            return AiToolCallResult.errorResult(call.getId(), "Command blocked: " + validationError);
        }

        long timeoutMs = clampBackgroundTimeout(
                longArg(jsonArgs, call, "backgroundTaskTimeoutMs", DEFAULT_BACKGROUND_TIMEOUT_MS));
        String callId = call.getCallId();
        String sessionId = context.getSessionId();
        String taskId = registry.submit(TOOL_NAME, sessionId, callId, timeoutMs);

        File workingDir = resolveWorkingDir(textArg(jsonArgs, call, "workingDir", null), context);
        Map<String, String> env = parseEnv(call, jsonArgs);
        List<String> argv = buildArgv(command);
        IBashSandbox backend = this.sandbox;

        registry.run(taskId, () -> {
            int wallSeconds = (int) Math.max(1, (timeoutMs + 999) / 1000);
            BashSandboxRequest request = BashSandboxRequest.builder()
                    .command(argv)
                    .workingDirectory(workingDir)
                    .environmentVariables(env)
                    .config(BashSandboxConfig.builder().wallSeconds(wallSeconds).build())
                    // same callId as the foreground path — the progress
                    // tracker / registry kill channel address the in-flight
                    // process by this id
                    .callId(callId)
                    .build();

            String status;
            String output;
            try {
                BashSandboxResult sr = backend.execute(request);
                output = combineOutput(sr);
                status = sr.isTimedOut() ? IBackgroundTaskRegistry.STATUS_TIMEOUT
                        : sr.getExitCode() == 0 ? IBackgroundTaskRegistry.STATUS_COMPLETED
                        : IBackgroundTaskRegistry.STATUS_FAILED;
            } catch (BashSandboxException e) {
                LOG.warn("BashExecutor: background task {} sandbox refused call {}: [{}]",
                        taskId, call.getId(), e.getReason(), e);
                output = "Sandbox refused execution [" + e.getReason() + "]";
                status = IBackgroundTaskRegistry.STATUS_FAILED;
            }
            registry.complete(taskId, status, output);
        });

        AiToolCallResult result = new AiToolCallResult();
        result.setId(call.getId());
        result.setStatus("success");
        AiToolOutput output = new AiToolOutput();
        output.setBody("[BACKGROUND_TASK started taskId=" + taskId
                + " backgroundTaskTimeoutMs=" + timeoutMs
                + "]\nThe command is running in the background. A completion notification will arrive "
                + "as a new message. Before it arrives you can read the current output tail with the "
                + "task-output tool (taskId=" + taskId + ") or cancel with task-stop.");
        result.setOutput(output);
        return result;
    }

    static long clampBackgroundTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            return DEFAULT_BACKGROUND_TIMEOUT_MS;
        }
        return Math.min(timeoutMs, BACKGROUND_TIMEOUT_CAP_MS);
    }

    private static String combineOutput(BashSandboxResult sr) {
        String out = sr.getStdout() == null ? "" : sr.getStdout();
        String err = sr.getStderr() == null ? "" : sr.getStderr();
        if (out.isEmpty()) {
            return err;
        }
        if (err.isEmpty()) {
            return out;
        }
        return out + "\n--- stderr ---\n" + err;
    }

    private AiToolCallResult toResult(int id, BashSandboxResult sr) {        AiToolCallResult result = new AiToolCallResult();
        result.setId(id);
        result.setExitCode(sr.getExitCode());

        if (sr.isTimedOut()) {
            result.setStatus("failure");
            AiToolError error = new AiToolError();
            error.setBody("Command timed out");
            result.setError(error);
            return result;
        }

        if (sr.getExitCode() == 0) {
            result.setStatus("success");
            AiToolOutput output = new AiToolOutput();
            output.setBody(sr.getStdout().trim());
            result.setOutput(output);
        } else {
            result.setStatus("failure");
            AiToolError error = new AiToolError();
            String body = sr.getStdout().trim();
            if (body.isEmpty()) {
                body = "Command exited with code " + sr.getExitCode();
            }
            error.setBody(body);
            result.setError(error);
        }
        return result;
    }

    private List<String> buildArgv(String command) {
        List<String> argv = new ArrayList<>(3);
        if (isWindows()) {
            argv.add("cmd");
            argv.add("/c");
        } else {
            argv.add("sh");
            argv.add("-c");
        }
        argv.add(command);
        return argv;
    }

    private File resolveWorkingDir(String workingDir, IToolExecuteContext context) {
        if (workingDir == null || workingDir.isEmpty()) {
            return context.getWorkDir();
        }
        return new File(workingDir);
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

    private Map<String, String> parseEnv(AiToolCall call, Map<String, Object> jsonArgs) {
        Map<String, String> env = new HashMap<>();
        XNode node = call.getNode();
        if (node != null) {
            List<XNode> envNodes = node.childrenByTag("env");
            if (envNodes != null) {
                for (XNode envNode : envNodes) {
                    String name = envNode.attrText("name");
                    String value = envNode.attrText("value");
                    if (name != null && value != null) {
                        putSafeEnv(env, name, value);
                    }
                }
            }
        }
        // dual-source: the engine dispatch path carries envs inside the input
        // JSON — either a map {name: value} or a list of {name, value} objects
        Object jsonEnvs = jsonArgs.get("envs");
        if (jsonEnvs instanceof Map<?, ?> envMap) {
            for (Map.Entry<?, ?> entry : envMap.entrySet()) {
                if (entry.getValue() != null) {
                    putSafeEnv(env, String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        } else if (jsonEnvs instanceof List<?> envList) {
            for (Object item : envList) {
                if (item instanceof Map<?, ?> itemMap) {
                    Object name = itemMap.get("name");
                    Object value = itemMap.get("value");
                    if (name != null && value != null) {
                        putSafeEnv(env, String.valueOf(name), String.valueOf(value));
                    }
                }
            }
        }
        return env;
    }

    private void putSafeEnv(Map<String, String> env, String name, String value) {
        String upperName = name.toUpperCase();
        if (DANGEROUS_ENV_VARS.contains(upperName)
                || upperName.startsWith(BASH_FUNC_ENV_PREFIX)) {
            LOG.warn("BashExecutor: rejecting dangerous env var {}", name);
            return;
        }
        if (name.startsWith("-")) {
            LOG.warn("BashExecutor: rejecting env var with leading dash: {}", name);
            return;
        }
        env.put(name, value);
    }

    // ------------------------------------------------------------------------
    // Plan 2301 (WI25): dual-source argument resolution (CallAgentExecutor
    // precedent). The engine dispatch path provides JSON arguments in
    // call.getInput() with a null node; the XML DSL path provides node
    // attributes/children. Malformed JSON never throws — the empty map makes
    // every lookup fall back to the node view.
    // ------------------------------------------------------------------------

    static Map<String, Object> parseJsonArgs(AiToolCall call) {
        String input = call.getInput();
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        try {
            Object parsed = io.nop.core.lang.json.JsonTool.parseNonStrict(input);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception e) {
            LOG.debug("BashExecutor: input is not a JSON object — node args only: {}", e.toString());
        }
        return Map.of();
    }

    static String textArg(Map<String, Object> jsonArgs, AiToolCall call, String key, String defaultValue) {
        Object value = jsonArgs.get(key);
        if (value != null && !String.valueOf(value).isEmpty()) {
            return String.valueOf(value);
        }
        return call.childText(key, defaultValue);
    }

    static boolean boolArg(Map<String, Object> jsonArgs, AiToolCall call, String key, boolean defaultValue) {
        Object value = jsonArgs.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return call.attrBoolean(key, defaultValue);
    }

    static int intArg(Map<String, Object> jsonArgs, AiToolCall call, String key, int defaultValue) {
        Object value = jsonArgs.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isEmpty()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                LOG.debug("BashExecutor: non-numeric {} value ignored: {}", key, s);
            }
        }
        return call.attrInt(key, defaultValue);
    }

    static long longArg(Map<String, Object> jsonArgs, AiToolCall call, String key, long defaultValue) {
        Object value = jsonArgs.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isEmpty()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                LOG.debug("BashExecutor: non-numeric {} value ignored: {}", key, s);
            }
        }
        return call.attrLong(key, defaultValue);
    }
}
