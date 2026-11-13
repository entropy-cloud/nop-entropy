package io.nop.ai.core.file;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import io.nop.ai.core.file.IFileOperator.GrepResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.1-MA-20（plan 2555 裁定 H / R1-M3）护栏载体：LocalFileOperator 读取面限额——
 * readText 按 offset/limit 有界读取（模块内 BoundedFileReader helper）、分页 readLines
 * 行数上界、findFilesByFilter/globGrep 文件数上界 + 截断 WARN、grepFiles 每文件读取上界。
 */
public class TestLocalFileOperatorLimits {

    private static final Logger LOG = LoggerFactory.getLogger(TestLocalFileOperatorLimits.class);

    private final List<File> tempDirs = new ArrayList<>();

    private File newTempDir() throws IOException {
        File dir = Files.createTempDirectory("lfo-limits").toFile();
        tempDirs.add(dir);
        return dir;
    }

    @AfterEach
    void cleanup() {
        for (File dir : tempDirs) {
            if (dir.exists() && !io.nop.commons.util.FileHelper.deleteAll(dir)) {
                LOG.warn("nop.test.fail-delete-temp-dir:dir={}", dir);
            }
        }
        tempDirs.clear();
    }

    private static File writeFile(File dir, String name, byte[] bytes) throws IOException {
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
        return f;
    }

    @Test
    public void testBoundedReadHelperReadsOnlyBoundedWindow() throws Exception {
        File dir = newTempDir();
        // 正常前缀 + 距离读取窗很远的非法 UTF-8 字节：全量读取路径会产生替换字符，
        // 有界读取路径（只解码 [offset, offset+limit)）不可见——这是读取量上限的行为证据
        byte[] clean = "0123456789".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[100_000];
        for (int i = 0; i < all.length; i += clean.length) {
            System.arraycopy(clean, 0, all, i, Math.min(clean.length, all.length - i));
        }
        // 非法 UTF-8 序列放在 offset 50_000 处（远离 10..30 的读取窗）
        all[50_000] = (byte) 0xFF;
        all[50_001] = (byte) 0xFF;
        File file = writeFile(dir, "big.txt", all);

        String window = BoundedFileReader.readText(file, 10, 20);
        assertEquals(20, window.length(), "bounded read must return exactly limit chars");
        assertEquals("01234567890123456789", window);
        assertFalse(window.contains("\uFFFD"),
                "bounded read must not decode bytes outside the window");
    }

    @Test
    public void testReadFileContentOffsetLimitSubstringCorrectness() throws Exception {
        File dir = newTempDir();
        String content = repeat("abcdefghij", 1000); // 10_000 chars
        writeFile(dir, "a.txt", content.getBytes(StandardCharsets.UTF_8));

        LocalFileOperator op = new LocalFileOperator(dir);
        FileContent fc = op.readFileContent("a.txt", 100, 50);
        assertNotNull(fc.getContent());
        assertEquals(content.substring(100, 150), fc.getContent(),
                "offset/limit window must match the full-content substring");
        assertEquals(50, fc.getContent().length());
    }

    @Test
    public void testReadFileContentLimitBeyondEofClamps() throws Exception {
        File dir = newTempDir();
        String content = "0123456789";
        writeFile(dir, "b.txt", content.getBytes(StandardCharsets.UTF_8));

        LocalFileOperator op = new LocalFileOperator(dir);
        FileContent fc = op.readFileContent("b.txt", 4, 100);
        assertEquals("456789", fc.getContent());
        // offset 超出文件长度 → 空内容
        FileContent fc2 = op.readFileContent("b.txt", 100, 10);
        assertEquals("", fc2.getContent());
    }

    @Test
    public void testPaginatedReadLinesRowBound() throws Exception {
        File dir = newTempDir();
        StringBuilder sb = new StringBuilder();
        List<String> expectedAll = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String line = "line-" + i;
            expectedAll.add(line);
            sb.append(line).append('\n');
        }
        writeFile(dir, "c.txt", sb.toString().getBytes(StandardCharsets.UTF_8));

        LocalFileOperator op = new LocalFileOperator(dir);
        List<String> page = op.readLines("c.txt", 10, 5);
        assertEquals(5, page.size(), "pagination must be bounded by lineCount");
        for (int i = 0; i < 5; i++) {
            assertEquals("line-" + (10 + i), page.get(i));
        }
        // lineCount<0 = 从 startLines 读到底（既有契约）
        List<String> tail = op.readLines("c.txt", 95, -1);
        assertEquals(5, tail.size());
        assertEquals("line-99", tail.get(4));

        // helper 单测：startLine 越界返回空
        File file = new File(dir, "c.txt");
        assertTrue(BoundedFileReader.readLines(file, 500, 10).isEmpty());
    }

    @Test
    public void testGrepFilesPerFileAndTotalLimits() throws Exception {
        File dir = newTempDir();
        for (int f = 0; f < 3; f++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                sb.append("needle-line-").append(i).append('\n');
            }
            writeFile(dir, "g" + f + ".txt", sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        LocalFileOperator op = new LocalFileOperator(dir);
        List<String> files = new ArrayList<>();
        files.add("g0.txt");
        files.add("g1.txt");
        files.add("g2.txt");

        // limitPerFile=2 → 每文件至多 2 条
        List<GrepResult> perFile = op.grepFiles(files, "needle", false, 2, 0);
        assertEquals(6, perFile.size());
        // totalLimit=4 → 总量截断
        List<GrepResult> total = op.grepFiles(files, "needle", false, 0, 4);
        assertEquals(4, total.size());

        // 每文件读取上界常量钉（裁定 H：grep 每文件扫描行数有界）
        assertTrue(LocalFileOperator.DEFAULT_GREP_MAX_LINES_PER_FILE > 0);
    }

    @Test
    public void testFindFilesTruncatesAtMaxFileCountAndCapsGlobGrep() throws Exception {
        File dir = newTempDir();
        File sub = new File(dir, "sub");
        sub.mkdirs();
        for (int i = 0; i < 5; i++) {
            writeFile(dir, "f" + i + ".txt", "data".getBytes(StandardCharsets.UTF_8));
            writeFile(sub, "s" + i + ".txt", "data".getBytes(StandardCharsets.UTF_8));
        }
        LocalFileOperator op = new LocalFileOperator(dir);

        // 命中 maxFileCount 即精确截断（修复前返回 max+1 条）+ 截断 WARN
        List<String> found = op.findFilesByAntPath("", "**/*.txt", 3);
        assertEquals(3, found.size(), "findFiles must truncate exactly at maxFileCount");

        // globGrep 内部缺省上界常量钉（原 :441 传 0 = 无界自弃）
        assertEquals(1000, LocalFileOperator.DEFAULT_GLOB_GREP_MAX_FILES,
                "globGrep default file cap must be 1000 (plan 2555 裁定 H)");
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder(s.length() * n);
        for (int i = 0; i < n; i++)
            sb.append(s);
        return sb.toString();
    }
}
