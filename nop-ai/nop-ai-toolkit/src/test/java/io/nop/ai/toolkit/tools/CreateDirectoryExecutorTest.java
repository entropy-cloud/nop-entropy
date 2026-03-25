package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.*;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class CreateDirectoryExecutorTest {
    private CreateDirectoryExecutor executor;
    private MockFileSystem mockFs;

    @BeforeEach
    void setUp() {
        executor = new CreateDirectoryExecutor();
        mockFs = new MockFileSystem();
    }

    @Test
    void testToolName() {
        assertEquals("create-dir", executor.getToolName());
    }

    @Test
    void testExecuteWithoutFileSystem() {
        AiToolCall call = createCall("/newdir");
        AiToolCallResult result = executor.executeAsync(call, new MockContext(null)).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("File system not available"));
    }

    @Test
    void testExecuteWithEmptyPath() {
        AiToolCall call = createCall("");
        AiToolCallResult result = executor.executeAsync(call, new MockContext(mockFs)).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("Path is required"));
    }

    @Test
    void testCreateDirectory() {
        AiToolCall call = createCall("/newdir");
        AiToolCallResult result = executor.executeAsync(call, new MockContext(mockFs)).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("Directory created"));
    }

    private AiToolCall createCall(String path) {
        XNode node = XNode.make("create-dir");
        node.setAttr("id", "1");
        node.setAttr("path", path);
        return AiToolCall.fromNode(node);
    }

    static class MockContext implements IToolExecuteContext {
        private final IToolFileSystem fs;
        MockContext(IToolFileSystem fs) { this.fs = fs; }
        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return fs; }
        @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
    }

    static class MockFileSystem implements IToolFileSystem {
        private final Set<String> directories = new HashSet<>();

        @Override public String normalizePath(String path) { return path; }
        @Override public boolean isPathAllowed(String path) { return true; }
        @Override public boolean exists(String path) { return directories.contains(path); }
        @Override public boolean isFile(String path) { return false; }
        @Override public boolean isDirectory(String path) { return directories.contains(path); }
        @Override public TextResult readText(String path, int maxChars) { return new TextResult(null, "", false); }
        @Override public LineResult readLines(String path, int fromLine, int toLine, int maxLineLength) { return null; }
        @Override public int countLines(String path, int maxLines) { return 0; }
        @Override public void writeText(String path, String content, boolean append) {}
        @Override public List<FileInfo> listDirectory(String dirPath, int depth, int maxCount) { return List.of(); }
        @Override public void mkdirs(String path) { directories.add(path); }
        @Override public void delete(String path, boolean recursive, boolean force) { directories.remove(path); }
        @Override public void move(String fromPath, String toPath, boolean overwrite) {}
        @Override public void copy(String fromPath, String toPath, boolean recursive, boolean overwrite) {}
        @Override public List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults) { return List.of(); }
        @Override public List<SearchMatch> grep(String pattern, String searchDir, boolean recursive, boolean ignoreCase, int maxMatchesPerFile, int maxFiles, int maxDepth) { return List.of(); }
    }
}
