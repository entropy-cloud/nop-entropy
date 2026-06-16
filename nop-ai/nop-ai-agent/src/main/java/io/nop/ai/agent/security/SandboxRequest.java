package io.nop.ai.agent.security;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable description of a single sandbox execution request (plan 219
 * Phase 1). Carries everything a {@link ISandboxBackend} needs to launch
 * and bound the command: the {@code argv} list (process style —
 * {@code ["sh", "-c", "..."]} or {@code ["git", "status"]}), the working
 * directory, the environment overlay, and the {@link SandboxConfig}
 * resource-limit envelope.
 *
 * <p>The {@code command} list matches {@link ProcessBuilder}'s argv form
 * (each element is a single argument — no shell tokenisation is performed
 * by the backend). Callers that want shell semantics (pipes, redirects,
 * globbing) pass {@code ["sh", "-c", "<script>"]} explicitly. This keeps
 * the contract unambiguous about argv boundaries and avoids injection
 * surface at the backend layer.
 *
 * <p>Immutable by construction: all collection fields are defensively
 * copied. The {@link Builder} is the only way to construct an instance;
 * the canonical {@link #of(List, SandboxConfig)} factory covers the common
 * no-env/no-cwd case.
 */
public final class SandboxRequest {

    private final List<String> command;
    private final java.io.File workingDirectory;
    private final Map<String, String> environmentVariables;
    private final SandboxConfig config;

    private SandboxRequest(Builder b) {
        this.command = List.copyOf(Objects.requireNonNull(b.command,
                "command list must not be null"));
        if (this.command.isEmpty()) {
            throw new IllegalArgumentException("command list must not be empty");
        }
        for (String arg : this.command) {
            Objects.requireNonNull(arg, "command list must not contain null elements");
        }
        this.workingDirectory = b.workingDirectory;
        this.environmentVariables = b.environmentVariables != null
                ? Map.copyOf(b.environmentVariables)
                : Collections.emptyMap();
        this.config = Objects.requireNonNull(b.config,
                "SandboxConfig must not be null (use SandboxConfig.defaults())");
    }

    /** The process-style argv list (e.g. {@code ["sh","-c","echo hi"]}). */
    public List<String> getCommand() {
        return command;
    }

    /** The working directory, or {@code null} to inherit the JVM's cwd. */
    public java.io.File getWorkingDirectory() {
        return workingDirectory;
    }

    /** The environment-variable overlay (merged on top of the inherited env). */
    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    /** The resource-limit envelope. Never null. */
    public SandboxConfig getConfig() {
        return config;
    }

    /** Convenience factory for the common no-env/no-cwd case. */
    public static SandboxRequest of(List<String> command, SandboxConfig config) {
        return builder().command(command).config(config).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<String> command;
        private java.io.File workingDirectory;
        private Map<String, String> environmentVariables;
        private SandboxConfig config;

        public Builder command(List<String> command) {
            this.command = command;
            return this;
        }

        public Builder workingDirectory(java.io.File workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables;
            return this;
        }

        public Builder config(SandboxConfig config) {
            this.config = config;
            return this;
        }

        public SandboxRequest build() {
            return new SandboxRequest(this);
        }
    }
}
