package io.nop.ai.agent.security;

import java.util.Objects;

/**
 * Resource-limit envelope for a sandbox execution (plan 219 Phase 1, design
 * §7.1). Defaults match the design table (cpuCores=1.0, memoryMb=1024,
 * wallSeconds=60, network=DENY) and an output-capture ceiling of 1 MiB.
 *
 * <p>The {@code cpuCores} field expresses a <b>CPU core-count quota</b>
 * following Docker {@code --cpus} semantics (Docker 1.13+): the value is a
 * fractional number of cores the sandboxed command may use (e.g.
 * {@code 1.0} = one full core, {@code 0.5} = half a core, {@code 2.5} =
 * two-and-a-half cores). It is NOT a CPU time budget in seconds.
 *
 * <p>Two ways to obtain an instance:
 * <ul>
 *   <li>{@link #defaults()} — the design-table baseline;</li>
 *   <li>{@link #builder()} — for per-call overrides (e.g. a larger core
 *       quota for a compute-heavy compilation).</li>
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
    /** Default CPU core-count quota (Docker {@code --cpus} semantics, design §7.1). */
    public static final double DEFAULT_CPU_CORES = 1.0;
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

    private final double cpuCores;
    private final int memoryMb;
    private final int wallSeconds;
    private final NetworkMode networkMode;
    private final int maxOutputBytes;

    private SandboxConfig(Builder b) {
        this.cpuCores = b.cpuCores;
        this.memoryMb = b.memoryMb;
        this.wallSeconds = b.wallSeconds;
        this.networkMode = Objects.requireNonNull(b.networkMode, "networkMode");
        this.maxOutputBytes = b.maxOutputBytes;
        // `!(x > 0)` rejects 0, negatives, AND NaN (NaN comparisons are
        // always false) — a NaN core quota must not silently pass.
        if (!(this.cpuCores > 0)) {
            throw new IllegalArgumentException("cpuCores must be > 0: " + this.cpuCores);
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

    /** The design-table baseline (cpuCores=1.0, memoryMb=1024, wallSeconds=60, network=DENY). */
    public static SandboxConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The CPU core-count quota (Docker {@code --cpus} semantics). A value
     * of {@code 1.0} means one full core, {@code 0.5} half a core, etc.
     * Always strictly positive (validated at construction).
     */
    public double getCpuCores() {
        return cpuCores;
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
        private double cpuCores = DEFAULT_CPU_CORES;
        private int memoryMb = DEFAULT_MEMORY_MB;
        private int wallSeconds = DEFAULT_WALL_SECONDS;
        private NetworkMode networkMode = NetworkMode.DENY;
        private int maxOutputBytes = DEFAULT_MAX_OUTPUT_BYTES;

        public Builder cpuCores(double cpuCores) {
            this.cpuCores = cpuCores;
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
