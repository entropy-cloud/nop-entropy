package io.nop.code.lang.go;

/**
 * Test gating helper: mirror of the typescript module's condition — skip
 * tree-sitter dependent tests on platforms without a published native-free
 * runtime assumption (pure-Java runtime has none, but keep the shape identical
 * so platform-specific skips stay consistent across lang modules).
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
