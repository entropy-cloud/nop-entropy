package io.nop.ai.toolkit.tools.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared working-directory host-path validation (the working-directory jail). Both backends use
 * it to reject a working directory that contains a {@code ..} traversal component, does not
 * resolve to a real existing path, or falls outside the configured {@code allowedBaseDirs}
 * whitelist — <i>before</i> any process/container is launched (plan 335 DR-3a).
 */
final class BashSandboxPaths {

    private BashSandboxPaths() {
    }

    static void validateWorkingDirectory(File workingDirectory, List<Path> allowedBaseDirs) {
        if (workingDirectory == null) {
            // F-AI2-1：null workDir 曾直接放行（fail-open），使 allowedBaseDirs
            // jail 完全失效。改为显式拒绝——无 workDir 的调用方不得静默逃逸沙箱。
            throw new BashSandboxException(BashSandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                    "BashSandbox: workingDirectory is null; refusing to run outside the sandbox jail");
        }
        String pathStr = workingDirectory.getPath();
        for (String part : pathStr.replace("\\", "/").split("/")) {
            if ("..".equals(part)) {
                throw new BashSandboxException(BashSandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                        "BashSandbox: workingDirectory contains '..' traversal component: " + pathStr);
            }
        }
        Path real;
        try {
            real = workingDirectory.toPath().toRealPath();
        } catch (IOException e) {
            throw new BashSandboxException(BashSandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                    "BashSandbox: workingDirectory does not resolve to a real path: " + pathStr, e);
        }
        if (allowedBaseDirs == null || allowedBaseDirs.isEmpty()) {
            throw new BashSandboxException(BashSandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                    "BashSandbox: no allowedBaseDirs configured, workingDirectory mount denied: " + real);
        }
        for (Path base : allowedBaseDirs) {
            Path normalizedBase = base;
            try {
                normalizedBase = base.toRealPath();
            } catch (IOException ignored) {
                // an allowedBaseDir that cannot be resolved is treated as its lexical form
            }
            if (real.equals(normalizedBase) || real.startsWith(normalizedBase)) {
                return;
            }
        }
        throw new BashSandboxException(BashSandboxFailureReason.HOST_PATH_NOT_ALLOWED,
                "BashSandbox: workingDirectory outside allowedBaseDirs: " + real);
    }
}
