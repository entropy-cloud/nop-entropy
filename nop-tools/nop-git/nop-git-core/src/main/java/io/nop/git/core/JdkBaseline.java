package io.nop.git.core;

import io.nop.git.api.NopGitException;

/**
 * Runtime baseline check (design 00-vision section 5): nop-git requires a JDK
 * with feature level 21 or higher. The check happens at repository open/init
 * so embedded deployments fail with a clear English message instead of an
 * {@link UnsupportedClassVersionError} deep inside JGit.
 */
public final class JdkBaseline {

    /**
     * Required runtime feature level for the nop-git module.
     */
    public static final int REQUIRED_FEATURE = 21;

    private JdkBaseline() {
    }

    /**
     * Validate the current JVM feature level against the module baseline.
     *
     * @throws NopGitException when the current feature level is too low
     */
    public static void requireBaseline() {
        requireFeature(REQUIRED_FEATURE);
    }

    /**
     * Validate the current JVM feature level against an explicit minimum.
     * Package-visible so tests can exercise the failure path on modern JVMs.
     *
     * @param min minimum required feature level
     * @throws NopGitException when the current feature level is below min
     */
    static void requireFeature(int min) {
        int feature = Runtime.version().feature();
        if (feature < min) {
            throw new NopGitException("nop-git requires JDK " + min
                    + " or higher, but the current JVM feature level is " + feature
                    + ". Please run nop-git on a newer JDK.");
        }
    }
}
