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

public class ReadFileExecutorTest {
    private ReadFileExecutor executor;
    private MockFileSystem mockFs;

    @BeforeEach
    void setUp() {
        executor = new ReadFileExecutor();
        mockFs = new MockFileSystem();
    }

    @Test
    void testToolName() {
        assertEquals("read-file", executor.getToolName());
    }

    @Test
    void testExecuteWithoutFileSystem() {
        AiToolCall call = createCall("/test.txt");
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
    void testReadFullFile() {
        mockFs.setContent("/test.txt", "line1\nline2\nline3");
        AiToolCall call = createCall("/test.txt");
        AiToolCallResult result = executor.executeAsync(call, new MockContext(mockFs)).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("line1"));
        assertEquals(3, result.getOutput().getTotalLines());
    }

    @Test
    void testReadLines() {
        mockFs.setContent("/test.txt", "line1\nline2\nline3\nline4\nline5");
        XNode node = XNode.make("read-file");
        node.setAttr("id", "1");
        node.setAttr("path", "/test.txt");
        node.setAttr("fromLine", 2);
        node.setAttr("toLine", 3);
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext(mockFs)).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("2: line2"));
        assertTrue(result.getOutput().getBody().contains("3: line3"));
    }

    @Test
    void testReadLastLines() {
        mockFs.setContent("/test.txt", "line1\nline2\nline3\nline4\nline5");
        XNode node = XNode.make("read-file");
        node.setAttr("id", "1");
        node.setAttr("path", "/test.txt");
        node.setAttr("lastLines", 2);
        AiToolCall call = AiToolCall.fromNode(node);
        AiToolCallResult result = executor.executeAsync(call, new MockContext(mockFs)).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        assertTrue(result.getOutput().getBody().contains("4: line4"));
        assertTrue(result.getOutput().getBody().contains("5: line5"));
        assertFalse(result.getOutput().getBody().contains("line1"));
    }

    @Test
    void testReadFileException() {
        mockFs.setThrowException(true);
        AiToolCall call = createCall("/test.txt");
        AiToolCallResult result = executor.executeAsync(call, new MockContext(mockFs)).toCompletableFuture().join();
        assertEquals("failure", result.getStatus());
    }

    private AiToolCall createCall(String path) {
        XNode node = XNode.make("read-file");
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
        private final Map<String, String> files = new HashMap<>();
        private boolean throwException;

        void setContent(String path, String content) { files.put(path, content); }
        void setThrowException(boolean throwException) { this.throwException = throwException; }

        @Override public String normalizePath(String path) { return path; }
        @Override public boolean isPathAllowed(String path) { return true; }
        @Override public boolean exists(String path) { return files.containsKey(path); }
        @Override public boolean isFile(String path) { return files.containsKey(path); }
        @Override public boolean isDirectory(String path) { return false; }

        @Override
        public TextResult readText(String path, int maxChars) {
            if (throwException) throw new RuntimeException("Read error");
            String content = files.getOrDefault(path, "");
            return new TextResult(null, content, false);
        }

        @Override
        public LineResult readLines(String path, int fromLine, int toLine, int maxLineLength) {
            if (throwException) throw new RuntimeException("Read error");
            String content = files.getOrDefault(path, "");
            String[] lines = content.split("\n", -1);
            List<Line> result = new ArrayList<>();
            for (int i = fromLine - 1; i < Math.min(toLine, lines.length); i++) {
                result.add(new Line(i + 1, lines[i], false));
            }
            return new LineResult(path, lines.length, fromLine, Math.min(toLine, lines.length), result);
        }

        @Override public int countLines(String path, int maxLines) {
            return files.getOrDefault(path, "").split("\n", -1).length;
        }
        @Override public void writeText(String path, String content, boolean append) { files.put(path, content); }
        @Override public List<FileInfo> listDirectory(String dirPath, int depth, int maxCount) { return List.of(); }
        @Override public void mkdirs(String path) {}
        @Override public void delete(String path, boolean recursive, boolean force) { files.remove(path); }
        @Override public void move(String fromPath, String toPath, boolean overwrite) {}
        @Override public void copy(String fromPath, String toPath, boolean recursive, boolean overwrite) {}
        @Override public List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults) { return List.of(); }
        @Override public List<SearchMatch> grep(String pattern, String searchDir, boolean recursive, boolean ignoreCase, int maxMatchesPerFile, int maxFiles, int maxDepth) { return List.of(); }
    }
}
