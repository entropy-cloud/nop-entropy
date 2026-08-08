package io.nop.ai.toolkit.tools;

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

    static final String NO_BACKEND_ERROR =
            "BashExecutor has no IBashSandbox backend wired (fail-closed): the bash tool refuses to "
                    + "execute on the host shell. Wire an explicit backend (e.g. HostBashSandbox for opt-in "
                    + "host execution, or DockerBashSandbox for isolation) before invoking the bash tool.";

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
            IBashSandbox backend = this.sandbox;
            if (backend == null) {
                LOG.warn("BashExecutor: refusing call {} — no IBashSandbox backend wired (fail-closed)",
                        call.getId());
                return AiToolCallResult.errorResult(call.getId(), NO_BACKEND_ERROR);
            }

            String command = call.childText("command", "");
            File workingDir = resolveWorkingDir(call.attrText("workingDir"), context);
            Map<String, String> env = parseEnv(call);
            int timeoutMs = call.attrInt("timeoutMs", call.getTimeoutMs() != null ? call.getTimeoutMs() : 30000);

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

    private AiToolCallResult toResult(int id, BashSandboxResult sr) {
        AiToolCallResult result = new AiToolCallResult();
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
