package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * Resource-limit envelope for a sandbox execution (plan 219 Phase 1, design
 * §7.1). Defaults match the design table (cpuSeconds=30, memoryMb=1024,
 * wallSeconds=60, network=DENY) and an output-capture ceiling of 1 MiB.
 *
 * <p>Two ways to obtain an instance:
 * <ul>
 *   <li>{@link #defaults()} — the design-table baseline;</li>
 *   <li>{@link #builder()} — for per-call overrides (e.g. a longer wall
 *       budget for a slow compilation).</li>
 * </ul>
 *
 * <p>The envelope is honoured differently per backend:
 * <ul>
 *   <li>{@link NoOpSandboxBackend} — host-level ProcessBuilder. Only
 *       {@code wallSeconds} and {@code maxOutputBytes} are enforceable
 *       (CPU/memory/network require an isolator). The unenforceable
 *       limits are still recorded on the config so a future
 *       functional backend picks them up automatically;</li>
 *   <li>{@code DockerSandboxBackend} — Docker CLI. Every field maps to a
 *       Docker flag ({@code --cpus}/{@code --memory}/{@code --network}).</li>
 * </ul>
 *
 * <p>Immutable by construction. Defensive copies are not needed (all
 * fields are primitives / enums).
 */
public final class SandboxConfig {

    /** Default wall-time budget in seconds (design §7.1). */
    public static final int DEFAULT_WALL_SECONDS = 60;
    /** Default CPU budget in seconds (design §7.1). */
    public static final int DEFAULT_CPU_SECONDS = 30;
    /** Default memory ceiling in MiB (design §7.1). */
    public static final int DEFAULT_MEMORY_MB = 1024;
    /** Default per-stream output ceiling: 1 MiB. */
    public static final int DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    /** Network exposure mode for the sandbox. */
    public enum NetworkMode {
        /** No network access (Docker {@code --network none}). Default. */
        DENY,
        /** Allow network access (no {@code --network} flag). */
        ALLOW
    }

    private final int cpuSeconds;
    private final int memoryMb;
    private final int wallSeconds;
    private final NetworkMode networkMode;
    private final int maxOutputBytes;

    private SandboxConfig(Builder b) {
        this.cpuSeconds = b.cpuSeconds;
        this.memoryMb = b.memoryMb;
        this.wallSeconds = b.wallSeconds;
        this.networkMode = Objects.requireNonNull(b.networkMode, "networkMode");
        this.maxOutputBytes = b.maxOutputBytes;
        if (this.cpuSeconds <= 0) {
            throw new IllegalArgumentException("cpuSeconds must be > 0: " + this.cpuSeconds);
        }
        if (this.memoryMb <= 0) {
            throw new IllegalArgumentException("memoryMb must be > 0: " + this.memoryMb);
        }
        if (this.wallSeconds <= 0) {
            throw new IllegalArgumentException("wallSeconds must be > 0: " + this.wallSeconds);
        }
        if (this.maxOutputBytes <= 0) {
            throw new IllegalArgumentException("maxOutputBytes must be > 0: " + this.maxOutputBytes);
        }
    }

    /** The design-table baseline (cpuSeconds=30, memoryMb=1024, wallSeconds=60, network=DENY). */
    public static SandboxConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getCpuSeconds() {
        return cpuSeconds;
    }

    public int getMemoryMb() {
        return memoryMb;
    }

    public int getWallSeconds() {
        return wallSeconds;
    }

    public NetworkMode getNetworkMode() {
        return networkMode;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public static final class Builder {
        private int cpuSeconds = DEFAULT_CPU_SECONDS;
        private int memoryMb = DEFAULT_MEMORY_MB;
        private int wallSeconds = DEFAULT_WALL_SECONDS;
        private NetworkMode networkMode = NetworkMode.DENY;
        private int maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES;

        public Builder cpuSeconds(int cpuSeconds) {
            this.cpuSeconds = cpuSeconds;
            return this;
        }

        public Builder memoryMb(int memoryMb) {
            this.memoryMb = memoryMb;
            return this;
        }

        public Builder wallSeconds(int wallSeconds) {
            this.wallSeconds = wallSeconds;
            return this;
        }

        public Builder networkMode(NetworkMode networkMode) {
            this.networkMode = networkMode;
            return this;
        }

        public Builder maxOutputBytes(int maxOutputBytes) {
            this.maxOutputBytes = maxOutputBytes;
            return this;
        }

        public SandboxConfig build() {
            return new SandboxConfig(this);
        }
    }
}
