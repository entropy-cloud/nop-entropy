package io.nop.ai.toolkit.fs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import io.nop.ai.toolkit.fs.LineResult;
import io.nop.ai.toolkit.fs.SearchMatch;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-6.7-003/004（plan 2399）判别：
 * ①readLines totalLines 返回文件真实总行数（非请求值 toLine）；
 * ②grep 对文件路径输入执行 grepInFile 而非静默返回空。
 */
public class TestLocalToolFileSystemP1Fixes {

    @TempDir
    Path dir;

    @Test
    public void testReadLinesTotalLinesIsRealCount() throws Exception {
        // 10 行文件，请求 fromLine=1 toLine=2 → 修复前 totalLines 填 toLine=2（假），
        // 修复后填真实总行数 10
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            sb.append("line-").append(i).append("\n");
        }
        Path file = dir.resolve("test.txt");
        Files.write(file, sb.toString().getBytes());

        LocalToolFileSystem fs = new LocalToolFileSystem(dir.toFile());
        LineResult result = fs.readLines(file.toString(), 1, 2, 0);

        assertEquals(10, result.getTotalLines(),
                "totalLines must be the real file line count, not the request value (P1-6.7-003)");
        assertEquals(2, result.getLines().size(), "only requested lines returned");
    }

    @Test
    public void testGrepOnFilePathSearchesFile() throws Exception {
        Path file = dir.resolve("target.txt");
        Files.write(file, "hello world\nfoo bar\nhello again\n".getBytes());

        LocalToolFileSystem fs = new LocalToolFileSystem(dir.toFile());
        List<SearchMatch> matches =
                fs.grep("hello", file.toString(), false, false, 10, 10, 0);

        assertFalse(matches.isEmpty(),
                "grep on a file path must search the file, not silently return empty (P1-6.7-004)");
        assertEquals(2, matches.size(), "both 'hello' lines matched");
    }
}
