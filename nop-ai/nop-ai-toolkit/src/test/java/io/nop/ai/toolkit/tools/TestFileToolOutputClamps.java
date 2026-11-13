package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.fs.FileInfo;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.LineResult;
import io.nop.ai.toolkit.fs.SearchMatch;
import io.nop.ai.toolkit.fs.TextResult;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import io.nop.core.lang.xml.XNode;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.2-C8-008（plan 2557 裁定 H，行为钉）：七失效面中 toolkit 侧四面的服务端 clamp
 * （①grep ②glob ③read-file 行区间 ④patch-file 全量读回帽）+ ⑤⑦既有正确面 +
 * ApplyDelta 既有 10M 帽回归钉。⑥ FileToolBizModel 在 nop-ai-tools（跨模块面，
 * 非本模块 scope）。
 */
public class TestFileToolOutputClamps {

    /** 记录关键调用参数的 fs stub。 */
    static class RecordingFs implements IToolFileSystem {
        AtomicReference<int[]> grepArgs = new AtomicReference<>();
        AtomicReference<int[]> globArgs = new AtomicReference<>();
        AtomicReference<int[]> readLinesArgs = new AtomicReference<>();
        AtomicReference<Integer> readTextCap = new AtomicReference<>();
        TextResult textResult = new TextResult(null, "old", false);

        @Override public String normalizePath(String path) { return path; }
        @Override public boolean isPathAllowed(String path) { return true; }
        @Override public boolean exists(String path) { return true; }
        @Override public boolean isFile(String path) { return true; }
        @Override public boolean isDirectory(String path) { return false; }
        @Override public TextResult readText(String path, int maxChars) {
            readTextCap.set(maxChars);
            return textResult;
        }
        @Override public LineResult readLines(String path, int fromLine, int toLine, int maxLineLength) {
            readLinesArgs.set(new int[]{fromLine, toLine});
            return new LineResult(path, 2000, fromLine, toLine, List.of());
        }
        @Override public int countLines(String path, int maxLines) { return 2000; }
        @Override public void writeText(String path, String content, boolean append) {}
        @Override public List<FileInfo> listDirectory(String dirPath, int depth, int maxCount) { return List.of(); }
        @Override public void mkdirs(String path) {}
        @Override public void delete(String path, boolean recursive, boolean force) {}
        @Override public void move(String fromPath, String toPath, boolean overwrite) {}
        @Override public void copy(String fromPath, String toPath, boolean recursive, boolean overwrite) {}
        @Override public List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults) {
            globArgs.set(new int[]{maxDepth, maxResults});
            return List.of();
        }
        @Override public List<SearchMatch> grep(String pattern, String searchDir, boolean recursive,
                boolean ignoreCase, int maxMatchesPerFile, int maxFiles, int maxDepth) {
            grepArgs.set(new int[]{maxMatchesPerFile, maxFiles});
            return new ArrayList<>();
        }
    }

    static class Ctx implements IToolExecuteContext {
        final RecordingFs fs = new RecordingFs();
        @Override public File getWorkDir() { return new File("."); }
        @Override public Map<String, String> getEnvs() { return Map.of(); }
        @Override public long getExpireAt() { return Long.MAX_VALUE; }
        @Override public ICancelToken getCancelToken() { return null; }
        @Override public IToolFileSystem getFileSystem() { return fs; }
        @Override public IThreadPoolExecutor getExecutor() { return SyncThreadPoolExecutor.INSTANCE; }
    }

    private static void attr(XNode node, String name, String value) {
        node.setAttr(name, value);
    }

    @Test
    void grepClampsMaxMatchesPerFileAndMaxFiles() {
        SearchContentExecutor executor = new SearchContentExecutor();
        Ctx ctx = new Ctx();
        XNode node = XNode.make("grep");
        node.setAttr("id", "1");
        node.setAttr("pattern", "p");
        node.setAttr("path", ".");
        attr(node, "maxMatchesPerFile", "99999999");
        attr(node, "maxFiles", "99999999");
        executor.executeAsync(AiToolCall.fromNode(node), ctx).toCompletableFuture().join();
        assertEquals(SearchContentExecutor.MAX_MATCHES_PER_FILE, ctx.fs.grepArgs.get()[0], "① per-file 命中 ≤1万");
        assertEquals(SearchContentExecutor.MAX_FILES, ctx.fs.grepArgs.get()[1], "① maxFiles ≤1万");

        // 非正值回落缺省
        attr(node, "maxMatchesPerFile", "-5");
        attr(node, "maxFiles", "0");
        executor.executeAsync(AiToolCall.fromNode(node), ctx).toCompletableFuture().join();
        assertEquals(100, ctx.fs.grepArgs.get()[0]);
        assertEquals(10, ctx.fs.grepArgs.get()[1]);
    }

    @Test
    void globClampsMaxResults() {
        SearchFilesExecutor executor = new SearchFilesExecutor();
        Ctx ctx = new Ctx();
        XNode node = XNode.make("glob");
        node.setAttr("id", "1");
        node.setAttr("pattern", "*.java");
        attr(node, "maxResults", "99999999");
        executor.executeAsync(AiToolCall.fromNode(node), ctx).toCompletableFuture().join();
        assertEquals(SearchFilesExecutor.MAX_RESULTS, ctx.fs.globArgs.get()[1], "② maxResults ≤1万");
    }

    @Test
    void readFileLineRangeClampedToMaxLineRange() {
        ReadFileExecutor executor = new ReadFileExecutor();
        Ctx ctx = new Ctx();
        XNode node = XNode.make("read-file");
        node.setAttr("id", "1");
        node.setAttr("path", "a.txt");
        attr(node, "fromLine", "1");
        attr(node, "toLine", "99999999");
        executor.executeAsync(AiToolCall.fromNode(node), ctx).toCompletableFuture().join();
        assertEquals(ReadFileExecutor.MAX_LINE_RANGE, ctx.fs.readLinesArgs.get()[1],
                "③ 行区间总量 ≤100K 行");

        // lastLines 巨值同样收口（stub totalLines=2000 → 区间收在文件尾部 2000 行）
        XNode node2 = XNode.make("read-file");
        node2.setAttr("id", "2");
        node2.setAttr("path", "a.txt");
        attr(node2, "lastLines", "99999999");
        executor.executeAsync(AiToolCall.fromNode(node2), ctx).toCompletableFuture().join();
        int[] args = ctx.fs.readLinesArgs.get();
        assertTrue(args[1] - args[0] + 1 <= ReadFileExecutor.MAX_LINE_RANGE,
                "③ lastLines 区间 ≤ MAX_LINE_RANGE, got " + (args[1] - args[0] + 1));
        assertEquals(2000, args[1] - args[0] + 1);
    }

    @Test
    void patchFileReadsBackWithApplyDeltaCap() {
        PatchFileExecutor executor = new PatchFileExecutor();
        Ctx ctx = new Ctx();
        XNode node = XNode.make("patch-file");
        node.setAttr("id", "1");
        node.setAttr("path", "a.txt");
        node.makeChild("input").setContentValue("not a diff");
        executor.executeAsync(AiToolCall.fromNode(node), ctx).toCompletableFuture().join();
        // ④ 原 Integer.MAX_VALUE 无界 → ApplyDelta 10M 帽族
        assertEquals(10_000_000, ctx.fs.readTextCap.get());
        assertEquals(PatchFileExecutor.MAX_READ_CHARS, 10_000_000);
    }

    @Test
    void applyDeltaExistingCapRegression() {
        // 既有帽回归钉：apply-delta 的 base 读回保持 10M 帽（帽面不回退）
        ApplyDeltaExecutor executor = new ApplyDeltaExecutor();
        Ctx ctx = new Ctx();
        XNode node = XNode.make("apply-delta");
        node.setAttr("id", "1");
        node.setAttr("path", "a.xml");
        XNode delta = node.makeChild("deltaContent");
        delta.makeChild("node").setAttr("tag", "x");
        executor.executeAsync(AiToolCall.fromNode(node), ctx).toCompletableFuture().join();
        assertEquals(ApplyDeltaExecutor.MAX_READ_CHARS, ctx.fs.readTextCap.get(),
                "ApplyDelta 既有 10M 帽不回退");
        assertEquals(10_000_000, ApplyDeltaExecutor.MAX_READ_CHARS);
    }
}
