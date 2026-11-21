package io.nop.git.server;

import java.nio.file.Path;
import java.util.List;

/**
 * Registry configuration for the HTTP Git server (design 04 section 4): all
 * repositories under {@code root} are exposed as {@code <name>.git} smart
 * HTTP endpoints.
 *
 * <p>Plan 2582 hardening: the server binds the loopback interface by
 * default — remote exposure requires {@code bindAllInterfaces=true}; an
 * optional bearer {@code token} enables authenticated access control on top
 * (when set, requests without {@code Authorization: Bearer <token>} are
 * rejected with 401).
 *
 * @param port               HTTP port (0 = ephemeral, chosen at start)
 * @param root               repository root directory
 * @param protectedBranches  server-side protected branch patterns
 * @param token              bearer token for access control (null = no token
 *                           check; loopback binding is still the default)
 * @param bindAllInterfaces  false (default) binds 127.0.0.1 only; true binds
 *                           all interfaces and is an explicit opt-in
 */
public record GitServerConfig(int port, Path root, List<String> protectedBranches,
                              String token, boolean bindAllInterfaces) {

    public static GitServerConfig of(int port, Path root) {
        return new GitServerConfig(port, root,
                ProtectedBranchPreReceiveHook.DEFAULT_PROTECTED, null, false);
    }

    public static GitServerConfig of(int port, Path root, String token) {
        return new GitServerConfig(port, root,
                ProtectedBranchPreReceiveHook.DEFAULT_PROTECTED, token, false);
    }
}
