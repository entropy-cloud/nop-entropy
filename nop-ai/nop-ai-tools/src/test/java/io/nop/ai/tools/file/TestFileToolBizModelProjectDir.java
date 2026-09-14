package io.nop.ai.tools.file;

import io.nop.ai.core.NopAiCoreErrors;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M6-P1 (round-2 audit): {@code FileToolBizModel.getProjectDir} must be
 * fail-closed against path-traversal project names. Pre-fix,
 * {@code StringHelper.fileName("..")} returned {@code ".."} verbatim and
 * {@code isValidFileName} only rejects control chars / platform invalid
 * chars — so {@code new File(baseDir, "..")} escaped the sandbox one level
 * up (default /nop/projects -> /nop) for readFiles/saveFile/saveFiles/
 * mergeFile/saveDslFile. The fix rejects the raw input before any
 * truncation (separators) and rejects {@code .} / {@code ..} results,
 * aligned with the fail-closed posture of
 * {@code AiToolsHelper.requireValidSessionId}.
 */
public class TestFileToolBizModelProjectDir {

    @TempDir
    Path tempDir;

    private static class ExposedFileToolBizModel extends FileToolBizModel {
        File exposeGetProjectDir(String projectName) {
            return getProjectDir(projectName);
        }
    }

    private FileToolBizModel newModel() {
        FileToolBizModel model = new FileToolBizModel();
        model.setBaseDir(tempDir.toFile());
        return model;
    }

    private void assertRejected(FileToolBizModel model, String projectName, String label) {
        NopException ex = assertThrows(NopException.class,
                () -> model.saveFile(projectName, "probe.txt", "x", null),
                label + " must be rejected at the tool entry (saveFile)");
        assertEquals(NopAiCoreErrors.ERR_AI_TOOLS_INVALID_PROJECT_NAME.getErrorCode(), ex.getErrorCode(),
                label + " must carry ERR_AI_TOOLS_INVALID_PROJECT_NAME");
        if (projectName != null) {
            assertEquals(projectName, ex.getParam(NopAiCoreErrors.ARG_VALUE),
                    label + " must be attached as ARG_VALUE");
        }
    }

    @Test
    public void testDotDotDotAndSeparatorsAreRejectedFailClosed() {
        FileToolBizModel model = newModel();

        assertRejected(model, "..", "'..'");
        assertRejected(model, ".", "'.'");
        assertRejected(model, "a/b", "forward-slash path");
        assertRejected(model, "a\\b", "backslash path");
        assertRejected(model, "/etc", "absolute path");
        assertRejected(model, "", "empty name");
        assertRejected(model, null, "null name");

        // The direct getProjectDir entry is fail-closed too (same path).
        ExposedFileToolBizModel exposed = new ExposedFileToolBizModel();
        exposed.setBaseDir(tempDir.toFile());
        for (String evil : List.of("..", ".", "a/b", "a\\b", "")) {
            NopException ex = assertThrows(NopException.class,
                    () -> exposed.exposeGetProjectDir(evil),
                    "getProjectDir must reject: " + evil);
            assertEquals(NopAiCoreErrors.ERR_AI_TOOLS_INVALID_PROJECT_NAME.getErrorCode(), ex.getErrorCode());
        }
    }

    @Test
    public void testEscapeAttemptDoesNotTouchFilesystem() {
        FileToolBizModel model = newModel();
        File outside = tempDir.resolveSibling("escaped-probe-" + System.nanoTime() + ".txt").toFile();

        assertThrows(NopException.class,
                () -> model.saveFile("..", outside.getName(), "evil", null),
                "saveFile with '..' must be rejected before touching the filesystem");

        assertTrue(!outside.exists(),
                "no file may be written outside the sandbox via projectName='..'");
        assertEquals(0, listSandboxFiles(""),
                "no file may be written inside the sandbox either (rejected before any write)");
    }

    @Test
    public void testNormalProjectNameStaysInsideSandbox() throws Exception {
        FileToolBizModel model = newModel();
        String projectName = "my-project";

        model.saveFile(projectName, "hello.txt", "hello world", null);
        String content = model.readFiles(projectName, List.of("hello.txt"), 0, 0, null);

        assertTrue(content.contains("hello world"),
                "normal projectName must read back the written content, got: " + content);

        File projectDir = new File(tempDir.toFile(), projectName);
        assertTrue(projectDir.isDirectory(), "project dir must exist under baseDir");
        File written = new File(projectDir, "hello.txt");
        String baseCanonical = tempDir.toFile().getCanonicalPath();
        String writtenCanonical = written.getCanonicalPath();
        assertTrue(writtenCanonical.startsWith(baseCanonical),
                "written file must stay under baseDir canonical path: " + writtenCanonical);
        assertEquals("hello world", new String(java.nio.file.Files.readAllBytes(written.toPath())),
                "file bytes must be exactly the written content");
    }

    private int listSandboxFiles(String prefix) {
        File[] files = tempDir.toFile().listFiles();
        return files == null ? 0 : files.length;
    }
}