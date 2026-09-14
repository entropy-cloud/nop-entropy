package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.tools.sequential_thinking.model.ThoughtData;
import io.nop.ai.tools.sequential_thinking.model.ThoughtStage;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P3-MA1-013: ThoughtStorage 直接测试（此前零测试）。
 * <p>
 * 验证 addThought 持久化到配置目录（写入后可从磁盘读回）、路径解析语义
 * （相对路径 → CWD、绝对路径直用、null/空 → 用户主目录回退）、会话隔离、
 * 阶段过滤、清空与导出/导入 round-trip。
 */
public class TestThoughtStorage {

    private File tempDir;

    @AfterEach
    public void tearDown() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            File[] files = tempDir.listFiles();
            if (files != null) {
                for (File f : files)
                    Files.deleteIfExists(f.toPath());
            }
            Files.deleteIfExists(tempDir.toPath());
        }
    }

    private File newTempDir() throws Exception {
        tempDir = Files.createTempDirectory("nop-ai-thought-storage").toFile();
        return tempDir;
    }

    private ThoughtData newThought(String thought, int number, int total, ThoughtStage stage) {
        ThoughtData data = new ThoughtData();
        data.setThought(thought);
        data.setThoughtNumber(number);
        data.setTotalThoughts(total);
        data.setStage(stage);
        return data;
    }

    @Test
    public void testAddThoughtPersistsToConfiguredDir() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());

        storage.addThought("session-1", newThought("first", 1, 2, ThoughtStage.ANALYSIS));

        // 持久化：文件已存在于配置目录，且可经新实例读回（磁盘 round-trip）
        File sessionFile = new File(dir, "session-1.json");
        assertTrue(sessionFile.exists(), "session file must exist after addThought");

        ThoughtStorage reloaded = new ThoughtStorage(dir.getAbsolutePath());
        assertEquals(1, reloaded.getAllThoughts("session-1").size());
        assertEquals("first", reloaded.getAllThoughts("session-1").get(0).getThought());
    }

    @Test
    public void testDefaultPathResolution() throws Exception {
        // 相对路径（./ 开头）→ 相对 JVM 工作目录解析
        File workDir = new File(System.getProperty("user.dir"));
        File rel = new File(workDir, "_tmp/ai/sequential-thinking/store");
        assertTrue(rel.getAbsolutePath().startsWith(workDir.getAbsolutePath()));

        // 绝对路径直用（文档化语义：resolveFile 对 / 开头路径直接 new File）
        File abs = newTempDir();
        ThoughtStorage absStorage = new ThoughtStorage(abs.getAbsolutePath());
        absStorage.addThought("abs-session", newThought("abs", 1, 1, ThoughtStage.SYNTHESIS));
        assertTrue(new File(abs, "abs-session.json").exists());
    }

    @Test
    public void testEmptyPathFallsBackToUserHome() throws Exception {
        // null/空路径 → ~/.mcp_sequential_thinking（用户主目录，不抛异常）
        ThoughtStorage nullStorage = new ThoughtStorage(null);
        ThoughtStorage emptyStorage = new ThoughtStorage("");
        assertNotNull(nullStorage);
        assertNotNull(emptyStorage);
        File homeDir = new File(System.getProperty("user.home"), ".mcp_sequential_thinking");
        assertTrue(homeDir.getAbsolutePath().startsWith(System.getProperty("user.home")));
    }

    @Test
    public void testSessionsAreIsolated() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());

        storage.addThought("s-a", newThought("a1", 1, 1, ThoughtStage.ANALYSIS));
        storage.addThought("s-b", newThought("b1", 1, 1, ThoughtStage.ANALYSIS));

        assertEquals(1, storage.getAllThoughts("s-a").size());
        assertEquals(1, storage.getAllThoughts("s-b").size());
        assertTrue(new File(dir, "s-a.json").exists());
        assertTrue(new File(dir, "s-b.json").exists());
    }

    @Test
    public void testGetThoughtsByStageAndClearHistory() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());

        storage.addThought("s1", newThought("analysis", 1, 3, ThoughtStage.ANALYSIS));
        storage.addThought("s1", newThought("plan", 2, 3, ThoughtStage.SYNTHESIS));
        storage.addThought("s1", newThought("review", 3, 3, ThoughtStage.SYNTHESIS));

        assertEquals(1, storage.getThoughtsByStage("s1", ThoughtStage.ANALYSIS).size());
        assertEquals(2, storage.getThoughtsByStage("s1", ThoughtStage.SYNTHESIS).size());

        storage.clearHistory("s1");
        assertTrue(storage.getAllThoughts("s1").isEmpty(), "clearHistory must empty the session");
    }

    @Test
    public void testExportImportRoundTrip() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());
        storage.addThought("src", newThought("exported", 1, 1, ThoughtStage.ANALYSIS));

        File export = new File(dir, "export.json");
        storage.exportSession("src", export.getAbsolutePath());
        assertTrue(export.exists());

        ThoughtStorage target = new ThoughtStorage(dir.getAbsolutePath());
        target.importSession("dst", export.getAbsolutePath());
        assertEquals(1, target.getAllThoughts("dst").size());
        assertEquals("exported", target.getAllThoughts("dst").get(0).getThought());
    }

    /**
     * P2 file-tool safety (plan 2026-09-14-1937-1 Phase 1): export/import used
     * to read/write the caller-supplied filePath verbatim (arbitrary file
     * read/write primitive). Ruling: keep + constrain — filePath must resolve
     * inside the storage dir, fail-closed with
     * {@code ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID}. No silent sanitize: the
     * path is rejected, never rewritten.
     */
    @Test
    public void testExportImportRejectPathOutsideStorageDir() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());
        storage.addThought("src", newThought("esc", 1, 1, ThoughtStage.ANALYSIS));

        File outsideTarget = new File(dir.getParentFile(), "evil-export.json");
        String[] badPaths = {
                outsideTarget.getAbsolutePath(),
                new File(dir, "../evil-export.json").getAbsolutePath(),
        };

        try {
            for (String bad : badPaths) {
                NopException ex = assertThrows(NopException.class,
                        () -> storage.exportSession("src", bad),
                        "exportSession must reject path outside storage dir: " + bad);
                assertEquals(NopAiCoreErrors.ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID.getErrorCode(),
                        ex.getErrorCode(), "export rejection must use the path error code: " + bad);
                assertEquals(bad, ex.getParam(NopAiCoreErrors.ARG_FILE_PATH),
                        "export rejection must carry the offending path: " + bad);

                NopException exImport = assertThrows(NopException.class,
                        () -> storage.importSession("dst", bad),
                        "importSession must reject path outside storage dir: " + bad);
                assertEquals(NopAiCoreErrors.ERR_AI_TOOLS_SESSION_FILE_PATH_INVALID.getErrorCode(),
                        exImport.getErrorCode(), "import rejection must use the path error code: " + bad);
                assertEquals(bad, exImport.getParam(NopAiCoreErrors.ARG_FILE_PATH),
                        "import rejection must carry the offending path: " + bad);
            }

            assertFalse(outsideTarget.exists(), "escape target outside the storage dir must not be written");

            // storage-dir-inside round trip must keep working (no silent sanitize)
            File export = new File(dir, "inside.json");
            storage.exportSession("src", export.getAbsolutePath());
            assertTrue(export.exists());
            storage.importSession("dst", export.getAbsolutePath());
            assertEquals(1, storage.getAllThoughts("dst").size());
            assertEquals("esc", storage.getAllThoughts("dst").get(0).getThought());
        } finally {
            Files.deleteIfExists(outsideTarget.toPath());
        }
    }

    @Test
    public void testUnknownSessionReturnsEmpty() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());
        assertTrue(storage.getAllThoughts("never-seen").isEmpty());
        assertTrue(storage.getThoughtsByStage("never-seen", ThoughtStage.ANALYSIS).isEmpty());
    }

    @Test
    public void testNullSessionIdRejected() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());
        assertThrows(NullPointerException.class, () -> storage.getAllThoughts(null));
        assertThrows(NullPointerException.class,
                () -> storage.addThought(null, newThought("x", 1, 1, ThoughtStage.ANALYSIS)));
    }

    /**
     * P0 path-traversal regression (audit ai-toolkit-skills, D5): sessionId used
     * to be concatenated raw into {@code new File(storageDir, sessionId + ".json")},
     * so ids like {@code ../../tmp/evil} escaped the storage dir (arbitrary
     * read/write of {@code .json} files, plus intermediate directory creation).
     * Malicious ids must be rejected with {@code ERR_AI_SESSION_ID_INVALID}
     * before any file-system touch; valid ids must keep working.
     */
    @Test
    public void testSessionIdPathTraversalRejected() throws Exception {
        File dir = newTempDir();
        ThoughtStorage storage = new ThoughtStorage(dir.getAbsolutePath());
        // the concrete escape target of the "../evil" ids below
        File outsideTarget = new File(dir.getParentFile(), "evil.json");

        try {
            String[] malicious = {"../evil", "../../tmp/evil", "a/b", "a\\b", "..", ".", "/tmp/evil"};
            for (String bad : malicious) {
                NopException ex = assertThrows(NopException.class,
                        () -> storage.addThought(bad, newThought("x", 1, 1, ThoughtStage.ANALYSIS)),
                        "sessionId must be rejected: " + bad);
                assertEquals(NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID.getErrorCode(), ex.getErrorCode(),
                        "sessionId rejection must use the path-traversal error code: " + bad);

                NopException exRead = assertThrows(NopException.class, () -> storage.getAllThoughts(bad),
                        "sessionId must be rejected on read: " + bad);
                assertEquals(NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID.getErrorCode(), exRead.getErrorCode());

                NopException exClear = assertThrows(NopException.class, () -> storage.clearHistory(bad),
                        "sessionId must be rejected on clear: " + bad);
                assertEquals(NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID.getErrorCode(), exClear.getErrorCode());
            }

            // empty id (non-null) is rejected with the dedicated empty error code
            NopException exEmpty = assertThrows(NopException.class, () -> storage.getAllThoughts(""));
            assertEquals(NopAiCoreErrors.ERR_AI_SESSION_ID_IS_EMPTY.getErrorCode(), exEmpty.getErrorCode());

            // file system untouched: nothing created inside the storage dir,
            // and the "../evil" escape target outside it was not written
            File[] entries = dir.listFiles();
            assertNotNull(entries, "storage dir must still exist");
            assertEquals(0, entries.length, "no file or directory may be created inside the storage dir");
            assertFalse(outsideTarget.exists(), "traversal target outside the storage dir must not be written");

            // valid ids still round-trip normally
            storage.addThought("session-1", newThought("ok", 1, 1, ThoughtStage.ANALYSIS));
            assertEquals(1, storage.getAllThoughts("session-1").size());
            assertTrue(new File(dir, "session-1.json").exists());
        } finally {
            // red-run safety net: if the guard regresses, the buggy write lands here
            Files.deleteIfExists(outsideTarget.toPath());
        }
    }
}
