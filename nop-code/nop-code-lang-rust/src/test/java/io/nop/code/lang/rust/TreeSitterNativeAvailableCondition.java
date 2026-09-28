package io.nop.code.lang.rust;

/**
 * Test gating helper: mirror of the go/typescript module condition.
 */
final class TreeSitterNativeAvailableCondition {
    private TreeSitterNativeAvailableCondition() {
    }

    static boolean isNativeLibAvailable() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String osArch = System.getProperty("os.arch", "").toLowerCase();
        if (osName.contains("windows") && osArch.equals("aarch64")) {
            return false;
        }
        return true;
    }
}
