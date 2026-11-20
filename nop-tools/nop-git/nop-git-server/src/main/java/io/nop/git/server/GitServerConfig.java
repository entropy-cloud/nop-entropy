package io.nop.git.server;

import java.nio.file.Path;
import java.util.List;

/**
 * Registry configuration for the HTTP Git server (design 04 section 4): all
 * repositories under {@code root} are exposed as {@code <name>.git} smart
 * HTTP endpoints.
 *
 * @param port                  HTTP port (0 = ephemeral, chosen at start)
 * @param root                  repository root directory
 * @param protectedBranches     server-side protected branch patterns
 */
public record GitServerConfig(int port, Path root, List<String> protectedBranches) {

    public static GitServerConfig of(int port, Path root) {
        return new GitServerConfig(port, root, ProtectedBranchPreReceiveHook.DEFAULT_PROTECTED);
    }
}
