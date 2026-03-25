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

public class SearchFilesExecutorTest {
    private SearchFilesExecutor executor;
    private MockFileSystem mockFs;

    @BeforeEach
    void setUp() {
        executor = new SearchFilesExecutor();
        mockFs = new MockFileSystem();
    }

    @Test
    void testToolName() {
        assertEquals("glob", executor.getToolName());
    }

    @Test
    void testExecuteWithoutFileSystem() {
        AiToolCall call = createCall("*.txt", ".");
        AiToolCallResult result = executor.executeAsync(call, new MockContext(null)).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
        assertTrue(result.getError().getBody().contains("File system not available"));
    }

    @Test
    void testSearchFiles() {
        mockFs.addFile(new FileInfo("/dir/file1.txt", "file1.txt", false, 100, 1000));
        mockFs.addFile(new FileInfo("/dir/file2.txt", "file2.txt", false, 200, 2000));
        AiToolCall call = createCall("*.txt", "/dir");
        AiToolCallResult result = executor.executeAsync(call, new MockContext(mockFs)).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("file1.txt"));
        assertTrue(result.getOutput().getBody().contains("file2.txt"));
    }

    @Test
    void testSearchFilesEmpty() {
        AiToolCall call = createCall("*.txt", "/empty");
        AiToolCallResult result = executor.executeAsync(call, new MockContext(mockFs)).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
    }

    private AiToolCall createCall(String pattern, String directory) {
        XNode node = XNode.make("glob");
        node.setAttr("id", "1");
        node.setAttr("pattern", pattern);
        node.setAttr("directory", directory);
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
        private final List<FileInfo> files = new ArrayList<>();

        void addFile(FileInfo file) { files.add(file); }

        @Override public String normalizePath(String path) { return path; }
        @Override public boolean isPathAllowed(String path) { return true; }
        @Override public boolean exists(String path) { return true; }
        @Override public boolean isFile(String path) { return false; }
        @Override public boolean isDirectory(String path) { return true; }
        @Override public TextResult readText(String path, int maxChars) { return new TextResult(null, "", false); }
        @Override public LineResult readLines(String path, int fromLine, int toLine, int maxLineLength) { return null; }
        @Override public int countLines(String path, int maxLines) { return 0; }
        @Override public void writeText(String path, String content, boolean append) {}
        @Override public List<FileInfo> listDirectory(String dirPath, int depth, int maxCount) { return files; }
        @Override public void mkdirs(String path) {}
        @Override public void delete(String path, boolean recursive, boolean force) {}
        @Override public void move(String fromPath, String toPath, boolean overwrite) {}
        @Override public void copy(String fromPath, String toPath, boolean recursive, boolean overwrite) {}
        @Override public List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults) { return files; }
        @Override public List<SearchMatch> grep(String pattern, String searchDir, boolean recursive, boolean ignoreCase, int maxMatchesPerFile, int maxFiles, int maxDepth) { return List.of(); }
    }
}
