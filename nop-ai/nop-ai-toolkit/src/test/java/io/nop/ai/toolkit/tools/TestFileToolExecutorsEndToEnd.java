package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.executor.ToolExecuteContext;
import io.nop.ai.toolkit.fs.LocalToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-09-14-1937-1 Phase 2 (end-to-end / wiring): the file-mutating
 * executors (write-file / patch-file / apply-delta / create-dir / delete-file)
 * run against a real {@link LocalToolFileSystem} — from the tool entry point
 * through {@code IToolFileSystem} to the file system — proving the atomic
 * write and fail-fast mkdirs/delete semantics are consumed by the runtime
 * call sites, not just present as new methods.
 */
public class TestFileToolExecutorsEndToEnd {

    @TempDir
    File tempDir;

    private IToolExecuteContext newContext() {
        return ToolExecuteContext.builder()
                .workDir(tempDir)
                .fileSystem(new LocalToolFileSystem(tempDir))
                .executor(SyncThreadPoolExecutor.INSTANCE)
                .build();
    }

    private String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void testWriteFileExecutorWritesThroughRealFileSystem() throws Exception {
        XNode node = XNode.make("write-file");
        node.setAttr("id", "1");
        node.setAttr("path", "out.txt");
        node.makeChild("input").setContentValue("hello");
        AiToolCallResult result = new WriteFileExecutor()
                .executeAsync(AiToolCall.fromNode(node), newContext()).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertEquals("hello", read(new File(tempDir, "out.txt")));
    }

    @Test
    public void testWriteFileExecutorFailurePreservesOldContentEndToEnd() throws Exception {
        // target in non-replaceable form (existing non-empty directory):
        // the atomic replace must fail and the previous "content" must
        // survive through the whole executor path.
        File dir = new File(tempDir, "target.txt");
        assertTrue(dir.mkdir());
        Files.writeString(new File(dir, "keep.txt").toPath(), "keep", StandardCharsets.UTF_8);

        XNode node = XNode.make("write-file");
        node.setAttr("id", "1");
        node.setAttr("path", "target.txt");
        node.makeChild("input").setContentValue("new");
        AiToolCallResult result = new WriteFileExecutor()
                .executeAsync(AiToolCall.fromNode(node), newContext()).toCompletableFuture().join();

        assertEquals("failure", result.getStatus(), "unreplaceable target must fail the tool call");
        assertTrue(dir.isDirectory(), "old target must be preserved");
        assertEquals("keep", read(new File(dir, "keep.txt")));
    }

    @Test
    public void testPatchFileExecutorAppliesDiffToRealFile() throws Exception {
        Files.writeString(new File(tempDir, "app.txt").toPath(),
                "line1\nline2\n", StandardCharsets.UTF_8);

        XNode node = XNode.make("patch-file");
        node.setAttr("id", "1");
        node.setAttr("path", "app.txt");
        node.makeChild("input").setContentValue(
                "--- a/app.txt\n+++ b/app.txt\n@@ -1,2 +1,2 @@\n line1\n-line2\n+line2 patched\n");
        AiToolCallResult result = new PatchFileExecutor()
                .executeAsync(AiToolCall.fromNode(node), newContext()).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertEquals("line1\nline2 patched\n", read(new File(tempDir, "app.txt")));
    }

    @Test
    public void testApplyDeltaExecutorMergesToRealFile() throws Exception {
        Files.writeString(new File(tempDir, "cfg.xml").toPath(),
                "<root><item>old</item></root>", StandardCharsets.UTF_8);

        XNode node = XNode.make("apply-delta");
        node.setAttr("id", "1");
        node.setAttr("path", "cfg.xml");
        XNode deltaContent = node.makeChild("deltaContent");
        XNode delta = deltaContent.makeChild("root");
        delta.setAttr("x:override", "merge");
        XNode item = delta.makeChild("item");
        item.setContentValue("new");
        AiToolCallResult result = new ApplyDeltaExecutor()
                .executeAsync(AiToolCall.fromNode(node), newContext()).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        String content = read(new File(tempDir, "cfg.xml"));
        assertTrue(content.contains("new"), "merged content must be written: " + content);
        assertFalse(content.contains("old"), "base content must be replaced by the merge: " + content);
    }

    @Test
    public void testCreateDirectoryExecutorCreatesRealDir() throws Exception {
        XNode node = XNode.make("create-dir");
        node.setAttr("id", "1");
        node.setAttr("path", "a/b/c");
        AiToolCallResult result = new CreateDirectoryExecutor()
                .executeAsync(AiToolCall.fromNode(node), newContext()).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertTrue(new File(tempDir, "a/b/c").isDirectory());
    }

    @Test
    public void testCreateDirectoryExecutorReportsFailureViaFileSystem() throws Exception {
        Files.writeString(new File(tempDir, "block").toPath(), "x", StandardCharsets.UTF_8);

        XNode node = XNode.make("create-dir");
        node.setAttr("id", "1");
        node.setAttr("path", "block/sub");
        AiToolCallResult result = new CreateDirectoryExecutor()
                .executeAsync(AiToolCall.fromNode(node), newContext()).toCompletableFuture().join();

        assertEquals("failure", result.getStatus(), "mkdirs failure must propagate as an error result");
        assertTrue(result.getError().getBody().contains("Failed to create directory"),
                "error body must carry the mkdirs failure");
    }

    @Test
    public void testDeleteFileExecutorDeletesRealFile() throws Exception {
        Files.writeString(new File(tempDir, "gone.txt").toPath(), "x", StandardCharsets.UTF_8);

        XNode node = XNode.make("delete-file");
        node.setAttr("id", "1");
        node.setAttr("path", "gone.txt");
        AiToolCallResult result = new DeleteFileExecutor()
                .executeAsync(AiToolCall.fromNode(node), newContext()).toCompletableFuture().join();

        assertEquals("success", result.getStatus());
        assertFalse(new File(tempDir, "gone.txt").exists());
    }

    @Test
    public void testDeleteFileExecutorReportsFailureViaFileSystem() throws Exception {
        File dir = new File(tempDir, "d");
        assertTrue(dir.mkdir());
        Files.writeString(new File(dir, "keep.txt").toPath(), "keep", StandardCharsets.UTF_8);

        XNode node = XNode.make("delete-file");
        node.setAttr("id", "1");
        node.setAttr("path", "d");
        AiToolCallResult result = new DeleteFileExecutor()
                .executeAsync(AiToolCall.fromNode(node), newContext()).toCompletableFuture().join();

        assertEquals("failure", result.getStatus(), "delete failure must propagate as an error result");
        assertTrue(result.getError().getBody().contains("Failed to delete"),
                "error body must carry the delete failure");
        assertTrue(dir.isDirectory(), "non-recursive delete of a non-empty dir must fail");
    }
}