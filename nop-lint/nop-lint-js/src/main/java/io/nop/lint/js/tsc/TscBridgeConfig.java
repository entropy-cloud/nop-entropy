package io.nop.lint.js.tsc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

/**
 * The explicit environment contract of the tsc bridge (design 06 §5.3, item
 * 20 Decision): which Node binary to spawn, where the JS helper script
 * lives, and the deadline/restart budgets. The helper and the
 * {@code typescript} npm package are tooling-level dependencies of
 * {@code ai-dev/tools} (devDependency + shared script) — never a platform
 * npm dependency — so the default resolution walks up from the working
 * directory to find {@code ai-dev/tools/tsc-bridge/tsc-bridge-server.mjs},
 * with a system property and an env override for deployments that keep the
 * tool tree elsewhere.
 *
 * <p>Tests replace the whole spawn command with a scripted peer executable,
 * so the unit matrix runs without any Node/typescript installation.</p>
 */
public final class TscBridgeConfig {

    /**
     * System property overriding the helper script location.
     */
    public static final String HELPER_PROPERTY = "nop.lint.tsc.helper";

    /**
     * Environment variable overriding the helper script location.
     */
    public static final String HELPER_ENV = "NOP_LINT_TSC_HELPER";

    private static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DEFAULT_QUERY_TIMEOUT = Duration.ofSeconds(15);
    private static final int DEFAULT_MAX_RESTARTS = 2;

    private final List<String> spawnCommand;
    private final Path helperPath;
    private final Duration handshakeTimeout;
    private final Duration queryTimeout;
    private final int maxRestarts;

    private TscBridgeConfig(List<String> spawnCommand, Path helperPath,
                            Duration handshakeTimeout, Duration queryTimeout, int maxRestarts) {
        this.spawnCommand = List.copyOf(spawnCommand);
        this.helperPath = helperPath;
        this.handshakeTimeout = handshakeTimeout;
        this.queryTimeout = queryTimeout;
        this.maxRestarts = maxRestarts;
    }

    /**
     * The production configuration: the {@code node} binary on PATH and the
     * shared helper script resolved through the property/env/cwd-walk chain.
     */
    public static TscBridgeConfig defaultEnvironment() {
        return new TscBridgeConfig(List.of("node", resolveHelperPath().toString()),
                resolveHelperPath(), DEFAULT_HANDSHAKE_TIMEOUT, DEFAULT_QUERY_TIMEOUT,
                DEFAULT_MAX_RESTARTS);
    }

    /**
     * A configuration driving an explicit peer command (the unit-test path:
     * a scripted fake peer replaces the Node process entirely, so the
     * process-management matrix needs no Node installation).
     */
    public static TscBridgeConfig forPeerCommand(List<String> spawnCommand,
                                                 Duration handshakeTimeout, Duration queryTimeout,
                                                 int maxRestarts) {
        return new TscBridgeConfig(spawnCommand, null, handshakeTimeout, queryTimeout, maxRestarts);
    }

    /**
     * Resolves the helper script: {@value HELPER_PROPERTY} system property,
     * then {@value HELPER_ENV}, then a walk up from the working directory
     * looking for {@code ai-dev/tools/tsc-bridge/tsc-bridge-server.mjs}.
     *
     * @throws TscBridgeUnavailableException naming every attempted location
     *                                      when none exists (fail-visible,
     *                                      never a silent degrade)
     */
    public static Path resolveHelperPath() {
        String property = System.getProperty(HELPER_PROPERTY);
        if (notBlank(property)) {
            return Paths.get(property);
        }
        String env = System.getenv(HELPER_ENV);
        if (notBlank(env)) {
            return Paths.get(env);
        }
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("ai-dev/tools/tsc-bridge/tsc-bridge-server.mjs");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new TscBridgeUnavailableException("tsc bridge helper script not found (set system property '"
                + HELPER_PROPERTY + "' or env '" + HELPER_ENV + "' to "
                + "ai-dev/tools/tsc-bridge/tsc-bridge-server.mjs)");
    }

    /**
     * The availability probe (design 11 §3 lazy principle): checks the
     * environment without spawning anything — the program to spawn must
     * exist when named by absolute path and, for the production command, the
     * helper script must exist. Only the command's first element is resolved
     * as an executable (arguments are never looked up on the PATH), so a
     * scripted test peer with a long argument list stays "usable".
     * The resident process itself starts only on the first real query.
     */
    public boolean isEnvironmentUsable() {
        Path program = Paths.get(spawnCommand.get(0));
        if (program.isAbsolute() && !Files.isExecutable(program)) {
            return false;
        }
        return helperPath == null || Files.isRegularFile(helperPath);
    }

    /**
     * The full command line to spawn (Node + helper, or the test peer).
     */
    public List<String> spawnCommand() {
        return spawnCommand;
    }

    /**
     * The handshake budget: how long the helper may take to produce its
     * ready frame.
     */
    public Duration handshakeTimeout() {
        return handshakeTimeout;
    }

    /**
     * The per-query deadline: an unanswered request becomes a structured
     * failure and the process is recycled through the restart policy.
     */
    public Duration queryTimeout() {
        return queryTimeout;
    }

    /**
     * How many times a crashed/hung process may be restarted before the
     * bridge enters its explicit unavailable state (no infinite retry).
     */
    public int maxRestarts() {
        return maxRestarts;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "TscBridgeConfig[command=" + spawnCommand + ", handshakeTimeout=" + handshakeTimeout
                + ", queryTimeout=" + queryTimeout + ", maxRestarts=" + maxRestarts + "]";
    }
}
