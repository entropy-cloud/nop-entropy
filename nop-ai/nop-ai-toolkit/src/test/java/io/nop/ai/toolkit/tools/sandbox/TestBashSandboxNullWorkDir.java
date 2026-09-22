package io.nop.ai.toolkit.tools.sandbox;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-AI2-1 回归：null workingDirectory 曾直接放行（fail-open），使
 * allowedBaseDirs jail 完全失效。修复后必须显式拒绝。
 */
public class TestBashSandboxNullWorkDir {

    @Test
    public void testNullWorkDirRejected() {
        BashSandboxException ex = assertThrows(BashSandboxException.class,
                () -> BashSandboxPaths.validateWorkingDirectory(null, List.of()));
        assertTrue(ex.getMessage() != null && ex.getMessage().contains("null"),
                "rejection message should name the null workDir: " + ex.getMessage());
    }
}
