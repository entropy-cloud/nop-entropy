package io.nop.ai.toolkit.tools.sandbox;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.exceptions.NopException;
import static io.nop.ai.toolkit.NopAiToolkitErrors.ERR_AI_TOOLKIT_INVALID_ARGUMENT;
import io.nop.commons.util.IoHelper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The selected isolation backend (plan 335 DR-3a): isolates command execution inside a Docker
 * container, reusing the nop-ai-agent {@code DockerSandboxBackend} pattern ({@code allowedBaseDirs}
 * whitelist + resource limits + network egress deny). Talks to Docker via the {@code docker} CLI
 * through {@link ProcessBuilder} — no extra Maven dependency.
 *
 * <p><b>Fail-closed guarantee</b>: this backend NEVER falls back to host execution. Any failure to
 * reach Docker or start the container surfaces as a {@link BashSandboxException} with the matching
 * {@link BashSandboxFailureReason}. A non-zero command exit code is NOT a sandbox failure — it is
 * returned as a normal {@link BashSandboxResult} so the caller sees the real exit code and output
 * (see {@link #classifyFailure}). A timeout issues {@code docker kill} + {@code docker rm -f}
 * cleanup.
 *
 * <p><b>Resource limits mapping</b>:
 * <ul>
 *   <li>{@code cpuCores} → {@code --cpus=<n>} (fractional core quota).</li>
 *   <li>{@code memoryMb} → {@code --memory=<n>m}.</li>
 *   <li>{@code wallSeconds} → enforced via {@code Process#waitFor} + {@code docker kill} on timeout.</li>
 *   <li>{@code networkMode == DENY} → {@code --network none} (the default).</li>
 *   <li>{@code maxOutputBytes} → enforced by the stdout-drain loop (truncates rather than erroring).</li>
 *   <li>{@code allowedBaseDirs} → working-directory host-path whitelist (validated before launch).</li>
 * </ul>
 *
 * <p><b>Declared output boundary (CRA 3.2-C6-001, plan 2501 Phase 2)</b>: the {@code docker run}
 * CLI proxy can outlive the container command (a detached process inside the container keeps the
 * CLI's stdio pipe open). The reader thread is terminated by closing the pipe (bounded grace +
 * forced EOF), so no sandbox thread or fd leaks; output produced after the CLI's main stream
 * ends is discarded. Container-side cleanup is best-effort with visible failures — see
 * {@code killContainer} (plan 2501 Phase 2, C6-002: kill/rm failures log WARN with the
 * container name so an unaddressable residue is observable and ops-cleanable; the timeout
 * path additionally destroys the local CLI process as a leak backstop — C8-009 face).
 * <p>Short cleanup helper commands ({@code docker kill}/{@code docker rm -f}) run with a
 * bounded drain + bounded exit wait + forcible destroy — a wedged docker daemon can no
 * longer pin the timeout/cleanup thread (C6-003/MA-04, plan 2501 Phase 2).
 *
 * <p>Stateless after construction — safe for concurrent use. Non-final since plan 2501
 * Phase 2: the C10-006 fault-injection seam ({@link #startDockerCli}) is exercised by
 * test subclasses (same-package tests substitute a fake {@code docker} CLI).
 */
public class DockerBashSandbox implements IBashSandbox {
    private static final Logger LOG = LoggerFactory.getLogger(DockerBashSandbox.class);

    /** Plan 2285 (WI4): incremental-progress publish cadence (time-based). */
    private static final long PUBLISH_INTERVAL_NS = 50_000_000L;


    public static final String CONTAINER_WORKDIR = "/workspace";

    static final Pattern ENV_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final String dockerImage;
    private final BashSandboxConfig defaultConfig;
    private final List<Path> allowedBaseDirs;
    /** Plan 2285 (WI4): callId-addressed in-flight registry for progress/cancel. */
    private final BashExecutionTracker tracker = new BashExecutionTracker();

    public DockerBashSandbox(String dockerImage) {
        this(dockerImage, BashSandboxConfig.defaults(), List.of());
    }

    public DockerBashSandbox(String dockerImage, BashSandboxConfig defaultConfig) {
        this(dockerImage, defaultConfig, List.of());
    }

    public DockerBashSandbox(String dockerImage, BashSandboxConfig defaultConfig, List<Path> allowedBaseDirs) {
        if (dockerImage == null || dockerImage.isEmpty()) {
            throw new NopException(ERR_AI_TOOLKIT_INVALID_ARGUMENT).param("detail", "dockerImage must not be null or empty");
        }
        this.dockerImage = dockerImage;
        this.defaultConfig = defaultConfig != null ? defaultConfig : BashSandboxConfig.defaults();
        this.allowedBaseDirs = allowedBaseDirs != null ? List.copyOf(allowedBaseDirs) : List.of();
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public BashSandboxConfig getDefaultConfig() {
        return defaultConfig;
    }

    public List<Path> getAllowedBaseDirs() {
        return allowedBaseDirs;
    }

    @Override
    public BashSandboxResult execute(BashSandboxRequest request) {
        BashSandboxConfig config = request.getConfig() != null ? request.getConfig() : defaultConfig;
        BashSandboxPaths.validateWorkingDirectory(request.getWorkingDirectory(), allowedBaseDirs);
        String containerName = "nop-bash-sandbox-" + UUID.randomUUID();
        List<String> dockerCmd = buildDockerCommand(containerName, dockerImage, request, config);

        ProcessBuilder pb = new ProcessBuilder(dockerCmd).redirectErrorStream(true);
        // C6-001 (MA-02, plan 2501 Phase 2): the CLI's stdin used to stay a PIPE
        // nobody closes — point it at the null device (no docker command reads it).
        pb.redirectInput(nullInput());
        Process process = startDockerCli(pb);

        int maxBytes = config.getMaxOutputBytes();
        AtomicReference<StringBuilder> capturedRef = new AtomicReference<>();
        // C4-001 member (MA-02): unique reader name — thread dumps must be able to
        // attribute a (terminating) reader to its container run
        String readerName = "nop-bash-sandbox-docker-reader-"
                + (request.getCallId() != null ? request.getCallId() : UUID.randomUUID());
        Thread reader = new Thread(() -> drainStream(process.getInputStream(), maxBytes, capturedRef),
                readerName);
        reader.setDaemon(true);
        reader.start();

        // Plan 2285 (WI4): register in-flight under the caller's callId —
        // cancel hook kills the container; unregistered in every exit path.
        String callId = request.getCallId();
        if (callId != null) {
            tracker.register(callId, process, capturedRef, () -> killContainer(containerName));
        }
        try {
            return doExecute(config, process, reader, capturedRef, containerName);
        } finally {
            if (callId != null) {
                tracker.unregister(callId);
            }
        }
    }

    /**
     * C10-006 shared fault-injection seam (MA-03/MA-04, plan 2501 Phase 2): starts
     * the {@code docker run} CLI process. Package-private so tests in this package
     * can substitute a fake CLI (no Docker daemon required for timeout/cleanup
     * path coverage).
     */
    Process startDockerCli(ProcessBuilder pb) {
        try {
            return pb.start();
        } catch (IOException e) {
            throw new BashSandboxException(BashSandboxFailureReason.BACKEND_UNAVAILABLE,
                    "DockerBashSandbox: failed to launch 'docker' CLI: " + e.getMessage(),
                    e);
        }
    }

    private BashSandboxResult doExecute(BashSandboxConfig config, Process process, Thread reader,
                                        AtomicReference<StringBuilder> capturedRef, String containerName) {
        boolean timedOut;
        try {
            timedOut = !process.waitFor(config.getWallSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            timedOut = true;
        }

        if (timedOut) {
            killContainer(containerName);
            // C6-002 (MA-03, plan 2501 Phase 2): CLI-process leak backstop — kill the
            // local `docker run` CLI unconditionally on the timeout path (container
            // kill failure no longer leaves the CLI process behind either)
            process.destroyForcibly();
            awaitReader(process, reader, config.getWallSeconds());
            return new BashSandboxResult(exitCodeOrNegative(process), capturedToString(capturedRef), "", true);
        }

        awaitReader(process, reader, config.getWallSeconds());
        int exitCode = exitCodeOrNegative(process);
        String stdout = capturedToString(capturedRef);

        BashSandboxFailureReason reason = classifyFailure(exitCode, stdout);
        if (reason != null) {
            throw new BashSandboxException(reason,
                    "DockerBashSandbox: container " + containerName + " exited with code " + exitCode
                            + " (reason=" + reason + ")"
                            + (stdout.isEmpty() ? "" : "; output: " + truncateForMessage(stdout)));
        }

        return new BashSandboxResult(exitCode, stdout, "", false);
    }

    @Override
    public BashProgressSnapshot progressSnapshot(String callId) {
        return tracker.progressSnapshot(callId);
    }

    @Override
    public boolean cancelExecution(String callId) {
        return tracker.cancelExecution(callId);
    }

    static List<String> buildDockerCommand(String containerName, String image,
                                           BashSandboxRequest request, BashSandboxConfig config) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--cpus");
        cmd.add(Double.toString(config.getCpuCores()));
        cmd.add("--memory");
        cmd.add(config.getMemoryMb() + "m");
        if (config.getNetworkMode() == BashSandboxConfig.NetworkMode.DENY) {
            cmd.add("--network");
            cmd.add("none");
        }
        if (request.getWorkingDirectory() != null) {
            String hostPath = request.getWorkingDirectory().getAbsolutePath();
            cmd.add("-v");
            cmd.add(hostPath + ":" + CONTAINER_WORKDIR);
            cmd.add("--workdir");
            cmd.add(CONTAINER_WORKDIR);
        }
        for (Map.Entry<String, String> e : request.getEnvironmentVariables().entrySet()) {
            validateEnvironmentVariableKey(e.getKey());
            cmd.add("-e");
            cmd.add(e.getKey() + "=" + e.getValue());
        }
        cmd.add(image);
        cmd.addAll(request.getCommand());
        return cmd;
    }

    static void validateEnvironmentVariableKey(String key) {
        if (key == null || !ENV_KEY_PATTERN.matcher(key).matches()) {
            throw new BashSandboxException(BashSandboxFailureReason.INVALID_ENVIRONMENT_VARIABLE,
                    "DockerBashSandbox: invalid environment variable name rejected: " + key);
        }
    }

    static BashSandboxFailureReason classifyFailure(int exitCode, String captured) {
        if (exitCode == 0) {
            return null;
        }
        if (containsAny(captured,
                "Cannot connect to the Docker daemon",
                "docker: command not found",
                "Is the docker daemon running")) {
            return BashSandboxFailureReason.BACKEND_UNAVAILABLE;
        }
        if (containsAny(captured,
                "Unable to find image",
                "permission denied",
                "OCI runtime failed",
                "no such image")) {
            return BashSandboxFailureReason.CONTAINER_START_FAILED;
        }
        if (exitCode == 137) {
            return BashSandboxFailureReason.RESOURCE_LIMIT_EXCEEDED;
        }
        if (exitCode == 124) {
            return BashSandboxFailureReason.TIMEOUT;
        }
        // Any other non-zero exit is an ordinary command failure (e.g. `ls /nonexistent` exit 2,
        // `grep` with no match exit 1): NOT a sandbox failure. The caller receives the real exit
        // code and output through the normal BashSandboxResult path.
        return null;
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static String truncateForMessage(String s) {
        return s.length() <= 256 ? s : s.substring(0, 256) + "...(truncated)";
    }

    /**
     * C6-002 (MA-03, plan 2501 Phase 2): two-step container cleanup with visible
     * failures. A swallowed kill/rm failure used to leave an unaddressable running
     * container (its unique {@code --name} is the only addressability) plus the
     * local CLI process; both failure faces now log WARN with the container name
     * and step so residue is observable and ops-cleanable.
     *
     * <p><b>Container-side bottoming adjudication (三选一, plan 2501 Phase 2
     * Decision)</b>: declared ops boundary — the WARNed container name is the
     * cleanup envelope; {@code --stop-timeout} needs daemon-side cooperation and a
     * background reaper is deferred (see plan Deferred "容器侧兜底增强"). Registered
     * in the owner doc ({@code docs-for-ai/03-modules/nop-ai.md} sandbox section).
     */
    private static void killContainer(String containerName) {
        try {
            int killExit = runShortCommand("docker", "kill", containerName);
            if (killExit != 0) {
                // C6-002: a non-zero `docker kill` exit (daemon down, container gone)
                // used to be invisible — the container name is the only addressability
                LOG.warn("DockerBashSandbox: docker kill FAILED (exit {}) for container {} — container may "
                        + "remain running (--rm will not fire); clean up manually with the container name",
                        killExit, containerName);
            }
        } catch (Exception e) {
            LOG.warn("DockerBashSandbox: docker kill FAILED for container {} — container may remain running "
                    + "(--rm will not fire); clean up manually with the container name", containerName, e);
        }
        try {
            int rmExit = runShortCommand("docker", "rm", "-f", containerName);
            if (rmExit != 0) {
                LOG.warn("DockerBashSandbox: docker rm -f FAILED (exit {}) for container {} — container may "
                        + "remain; clean up manually with the container name", rmExit, containerName);
            }
        } catch (Exception e) {
            LOG.warn("DockerBashSandbox: docker rm -f FAILED for container {} — container may remain; "
                    + "clean up manually with the container name", containerName, e);
        }
    }

    /** C6-003 (MA-04, plan 2501 Phase 2): bounded drain budget for cleanup helpers. */
    static final int SHORT_COMMAND_DRAIN_SECONDS = 3;

    /** C6-003 (MA-04): bounded exit wait before the forcible destroy. */
    static final int SHORT_COMMAND_EXIT_WAIT_SECONDS = 1;

    /**
     * C6-003 (MA-04, plan 2501 Phase 2): bounded execution for the cleanup helper
     * commands ({@code docker kill} / {@code docker rm -f}). The previous form drained
     * with an unbounded blocking read BEFORE the exit wait — a daemon-wedged helper
     * (output pipe held open, process never exiting) pinned the timeout/cleanup
     * thread forever (the wall-clock cleanup itself became invisible, F5) and the
     * exit wait ignored its timeout without any destroy. Now: cumulative-bounded
     * drain (available-probe + deadline), bounded exit wait, and forcible destroy
     * on timeout — the helper can never outlive the short-command budget.
     */
    private static int runShortCommand(String... argv) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(Arrays.asList(argv)).redirectErrorStream(true).start();
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(SHORT_COMMAND_DRAIN_SECONDS);
        byte[] buf = new byte[1024];
        try {
            try (InputStream in = p.getInputStream()) {
                while (System.nanoTime() < deadlineNanos) {
                    int available = in.available();
                    if (available > 0) {
                        // available() > 0 guarantees this read returns without blocking
                        in.read(buf, 0, Math.min(buf.length, available));
                    } else if (!p.isAlive()) {
                        // exited; remaining buffered bytes are drained by the loop above
                        break;
                    } else {
                        Thread.sleep(5);
                    }
                }
            }
            if (p.isAlive() && !p.waitFor(SHORT_COMMAND_EXIT_WAIT_SECONDS, TimeUnit.SECONDS)) {
                // wedged helper: forcible destroy, never a bare (or ignored) wait
                p.destroyForcibly();
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            throw e;
        }
        // C6-002: surface the exit code (un-reaped helper → -1, treated as failure)
        return p.isAlive() ? -1 : p.exitValue();
    }

    private static void drainStream(InputStream in, int maxBytes, AtomicReference<StringBuilder> capturedRef) {
        StringBuilder captured = new StringBuilder(Math.min(maxBytes, 8192));
        byte[] buf = new byte[4096];
        boolean truncated = false;
        // Plan 2285 (WI4): publish an immutable copy periodically so the
        // in-flight progress registry sees incremental output during the run
        // (previously the buffer was only published in the finally block).
        // Time-based rate limit (first chunk publishes immediately); a copy
        // per chunk would be quadratic for max-bytes outputs.
        long lastPublishNs = 0L;
        try {
            int n;
            while ((n = in.read(buf)) != -1) {
                if (truncated) {
                    continue;
                }
                int remaining = maxBytes - captured.length();
                if (remaining <= 0) {
                    truncated = true;
                    continue;
                }
                int take = Math.min(n, remaining);
                captured.append(new String(buf, 0, take, StandardCharsets.UTF_8));
                if (take < n) {
                    truncated = true;
                }
                long nowNs = CoreMetrics.nanoTime();
                if (nowNs - lastPublishNs >= PUBLISH_INTERVAL_NS) {
                    lastPublishNs = nowNs;
                    capturedRef.set(new StringBuilder(captured));
                }
            }
        } catch (IOException ignored) {
            // keep the partial capture
        } finally {
            // C6-008 (MA-02 member): publish an immutable snapshot — never the live
            // buffer a (possibly still-draining) reader could keep appending to
            capturedRef.set(new StringBuilder(captured));
            // C6-001: release the pipe fd as soon as this drain ends
            IoHelper.safeClose(in);
        }
    }

    private static String capturedToString(AtomicReference<StringBuilder> ref) {
        StringBuilder sb = ref.get();
        return sb != null ? sb.toString() : "";
    }

    /**
     * C6-001 (MA-02, plan 2501 Phase 2): bounded wait for the reader thread with
     * exit compensation. {@code interrupt()} has no effect on a thread blocked in a
     * native pipe read (the CLI proxy's stdio stays held open by a detached process
     * inside the container) — closing the pipe forces the blocked read to abort,
     * after which the reader publishes its final snapshot and exits.
     */
    private static void awaitReader(Process process, Thread reader, int wallSeconds) {
        long graceMillis = Math.max(500L, Math.min(2_000L, wallSeconds * 500L));
        try {
            reader.join(graceMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (reader.isAlive()) {
            IoHelper.safeClose(process.getInputStream());
            reader.interrupt();
        }
    }

    private static ProcessBuilder.Redirect nullInput() {
        File nul = File.separatorChar == '\\' ? new File("NUL") : new File("/dev/null");
        return ProcessBuilder.Redirect.from(nul);
    }

    private static int exitCodeOrNegative(Process process) {
        try {
            return process.exitValue();
        } catch (IllegalThreadStateException stillAlive) {
            return -1;
        }
    }
}
