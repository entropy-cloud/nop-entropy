package io.nop.ai.toolkit.tools.sandbox;

import io.nop.api.core.exceptions.NopException;
import static io.nop.ai.toolkit.NopAiToolkitErrors.ERR_AI_TOOLKIT_INVALID_ARGUMENT;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable description of a single {@link IBashSandbox} execution request (plan 335 DR-3a).
 * Carries the process-style argv list, the working directory, the environment overlay, and the
 * {@link BashSandboxConfig} resource-limit envelope.
 *
 * <p>The {@code command} list matches {@link ProcessBuilder}'s argv form (each element is a
 * single argument — no shell tokenisation is performed by the backend). Callers that want shell
 * semantics pass {@code ["sh", "-c", "<script>"]} explicitly.
 */
public final class BashSandboxRequest {

    private final List<String> command;
    private final File workingDirectory;
    private final Map<String, String> environmentVariables;
    private final BashSandboxConfig config;

    private BashSandboxRequest(Builder b) {
        this.command = List.copyOf(Objects.requireNonNull(b.command, "command list must not be null"));
        if (this.command.isEmpty()) {
            throw new NopException(ERR_AI_TOOLKIT_INVALID_ARGUMENT).param("detail", "command list must not be empty");
        }
        for (String arg : this.command) {
            Objects.requireNonNull(arg, "command list must not contain null elements");
        }
        this.workingDirectory = b.workingDirectory;
        this.environmentVariables = b.environmentVariables != null
                ? Map.copyOf(b.environmentVariables)
                : Collections.emptyMap();
        this.config = Objects.requireNonNull(b.config, "BashSandboxConfig must not be null");
    }

    public List<String> getCommand() {
        return command;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public BashSandboxConfig getConfig() {
        return config;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private List<String> command;
        private File workingDirectory;
        private Map<String, String> environmentVariables;
        private BashSandboxConfig config = BashSandboxConfig.defaults();

        public Builder command(List<String> command) {
            this.command = command;
            return this;
        }

        public Builder workingDirectory(File workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = environmentVariables;
            return this;
        }

        public Builder config(BashSandboxConfig config) {
            this.config = config;
            return this;
        }

        public BashSandboxRequest build() {
            return new BashSandboxRequest(this);
        }
    }
}
