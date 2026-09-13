package io.nop.ai.toolkit.tools.sandbox;

import io.nop.api.core.exceptions.NopException;
import static io.nop.ai.toolkit.NopAiToolkitErrors.ERR_AI_TOOLKIT_INVALID_ARGUMENT;
import java.util.Objects;

/**
 * Resource-limit envelope for a {@link IBashSandbox} execution (plan 335 DR-3a).
 *
 * <p>Fields map to concrete isolator controls:
 * <ul>
 *   <li>{@link #getCpuCores()} — Docker {@code --cpus} (fractional core quota); host backend
 *       cannot enforce it and records it only.</li>
 *   <li>{@link #getMemoryMb()} — Docker {@code --memory}; host backend cannot enforce it.</li>
 *   <li>{@link #getWallSeconds()} — enforced by every backend via {@code Process#waitFor}
 *       (host) or {@code docker kill} on timeout (Docker).</li>
 *   <li>{@link #getMaxOutputBytes()} — per-stream capture ceiling, enforced by every backend.</li>
 *   <li>{@link #getNetworkMode()} — Docker {@code --network none} when {@link NetworkMode#DENY};
 *       host backend cannot enforce it.</li>
 * </ul>
 *
 * <p>The working-directory jail ({@code allowedBaseDirs}) is a backend deployment policy, set at
 * backend construction (see {@link HostBashSandbox}/{@link DockerBashSandbox}), NOT a per-call
 * resource limit — it does not live in this envelope.
 *
 * <p>Immutable by construction. Defaults: cpuCores=1.0, memoryMb=1024, wallSeconds=60,
 * network=DENY, maxOutputBytes=1MiB.
 */
public final class BashSandboxConfig {

    public static final int DEFAULT_WALL_SECONDS = 60;
    public static final double DEFAULT_CPU_CORES = 1.0;
    public static final int DEFAULT_MEMORY_MB = 1024;
    public static final int DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    public enum NetworkMode {
        DENY,
        ALLOW
    }

    private final double cpuCores;
    private final int memoryMb;
    private final int wallSeconds;
    private final NetworkMode networkMode;
    private final int maxOutputBytes;

    private BashSandboxConfig(Builder b) {
        this.cpuCores = b.cpuCores;
        this.memoryMb = b.memoryMb;
        this.wallSeconds = b.wallSeconds;
        this.networkMode = Objects.requireNonNull(b.networkMode, "networkMode");
        this.maxOutputBytes = b.maxOutputBytes;
        if (!(this.cpuCores > 0)) {
            throw new NopException(ERR_AI_TOOLKIT_INVALID_ARGUMENT).param("detail", "cpuCores must be > 0: " + this.cpuCores);
        }
        if (this.memoryMb <= 0) {
            throw new NopException(ERR_AI_TOOLKIT_INVALID_ARGUMENT).param("detail", "memoryMb must be > 0: " + this.memoryMb);
        }
        if (this.wallSeconds <= 0) {
            throw new NopException(ERR_AI_TOOLKIT_INVALID_ARGUMENT).param("detail", "wallSeconds must be > 0: " + this.wallSeconds);
        }
        if (this.maxOutputBytes <= 0) {
            throw new NopException(ERR_AI_TOOLKIT_INVALID_ARGUMENT).param("detail", "maxOutputBytes must be > 0: " + this.maxOutputBytes);
        }
    }

    public static BashSandboxConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

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

        public BashSandboxConfig build() {
            return new BashSandboxConfig(this);
        }
    }
}
