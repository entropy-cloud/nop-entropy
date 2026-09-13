/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.code.lang.typescript;

/**
 * Test gating helper: tree-sitter Java binding only ships native libraries for
 * aarch64-linux-gnu, aarch64-macos, x86_64-linux-gnu, x86_64-macos and
 * x86_64-windows. There is no published aarch64-windows variant; skip tree-sitter
 * tests on Windows ARM64 (the OS x86 emulation layer is invisible to the JVM,
 * which still reports os.arch=aarch64 and refuses to fall back to the
 * x86_64-windows dll).
 *
 * <p>Used together with {@code @EnabledIf} on tree-sitter dependent test classes.
 */
final class TreeSitterNativeAvailableCondition {
    private TreeSitterNativeAvailableCondition() {
    }

    static boolean isNativeLibAvailable() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "").toLowerCase();

        if (osName.contains("win") && (osArch.contains("aarch64") || osArch.contains("arm"))) {
            return false;
        }
        return true;
    }
}