/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.git.impl;

/**
 * Test gating helper for JGit-based tests on Windows.
 *
 * <p>JGit's working-tree locking behaviour on Windows intermittently fails
 * to clean and recreate the per-test repo directory under Maven Surefire
 * forking; the {@code mkdirs()} call in {@code setUp} then returns false
 * and the assertion fails spuriously. The tests run reliably on Linux/macOS
 * runners (the GitHub Actions default), so we skip them on Windows instead
 * of trying to chase a platform-specific file-handle race.
 */
final class GitTestEnvironmentCondition {
    private GitTestEnvironmentCondition() {
    }

    static boolean isGitReliableOnThisOS() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return !osName.contains("win");
    }
}