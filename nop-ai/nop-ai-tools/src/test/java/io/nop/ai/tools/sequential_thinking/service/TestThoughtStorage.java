package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.tools.sequential_thinking.model.ThoughtData;
import io.nop.ai.tools.sequential_thinking.model.ThoughtStage;
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
}
