package io.nop.ai.agent.security;

import java.util.Set;

/**
 * Shared set of tool-call argument keys whose string values are interpreted as
 * filesystem paths. Used by the tool-dispatch path-access check
 * ({@code ReActAgentExecutor.checkPathAccess}) and by the LevelHints producer
 * ({@link DefaultLevelHintsProducer}, writesOutsideWorkspace evaluation).
 *
 * <p>Centralised here so both consumers evaluate exactly the same set of keys —
 * a path written outside the workspace is both a path-access concern (Layer 1)
 * and a {@code writesOutsideWorkspace} hint signal (Layer 2), and the two must
 * agree on which arguments count as paths.
 */
public final class ToolPathArgKeys {

    public static final Set<String> KEYS = Set.of(
            "path", "file", "filePath", "filename", "directory", "dir",
            "destination", "output", "input", "source", "target", "cwd"
    );

    private ToolPathArgKeys() {
    }
}
