package io.nop.rg.core.coordinator;

import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 大文件（>1GB）正确性/稳定性测试（plan 2265 LRG-01..04，@Tag("large-file") 默认排除）。
 *
 * <p>显式运行（受控堆验证分块路径无 OOM）：
 * <pre>
 * ./mvnw test -pl nop-rg/nop-rg-core -DexcludedGroups= -Dgroups=large-file -DargLine="-Xmx256m"
 * </pre>
 *
 * <p>corpus：稀疏文件（APFS setLength 0KB 实际占用）+ 指定偏移植入模式（含 256MB chunk 边界横跨）。
 * 默认 coordinator（阈值 256MB）走分块路径；threshold=Long.MAX_VALUE 的 coordinator 走整文件路径；
 * 两者结果必须一致。
 */
@Tag("large-file")
public class LargeFileSearchTest {

    private static final long FILE_SIZE = 1_200_000_000L; // 1.2GB
    private static final long CHUNK_BOUNDARY = 256L * 1024 * 1024; // 268435456
    private static final byte[] NEEDLE = "needle".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BOUNDARY_MARK = "BOUNDARY".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private Path bigFile;

    @BeforeAll
    public static void initAll() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /**
     * 生成稀疏大文件并植入模式；返回植入的 needle 起始偏移（升序）。
     */
    private List<Long> buildBigFile() throws IOException {
        bigFile = tempDir.resolve("big.bin");
        List<Long> planted = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(bigFile.toFile(), "rw")) {
            raf.setLength(FILE_SIZE);
            // 头部写入真实文本（前 8KB 无 NUL）——否则二进制嗅探会按 rg 语义跳过该文件
            byte[] header = "header filler text without target word\n".repeat(450)
                    .getBytes(StandardCharsets.UTF_8);
            raf.seek(0);
            raf.write(header);
            // 稀疏区每 1MB 戳一个换行——否则命中"行"会长达数百 MB，行文本解码会 OOM
            for (long off = 1_000_000L; off < FILE_SIZE; off += 1_000_000L) {
                raf.seek(off);
                raf.write('\n');
            }
            // 常规植入
            long[] offsets = {16L * 1024 * 1024, 100L * 1024 * 1024, FILE_SIZE - 200};
            for (long off : offsets) {
                raf.seek(off);
                raf.write(NEEDLE);
                planted.add(off);
            }
            // 跨 256MB chunk 边界横跨（起点在边界前 4 字节）
            raf.seek(CHUNK_BOUNDARY - 4);
            raf.write(BOUNDARY_MARK);
            // 密集区：700MB 处 10KB 内 50 次植入
            raf.seek(700L * 1024 * 1024);
            for (int i = 0; i < 50; i++) {
                raf.write(NEEDLE);
                raf.write(new byte[100]);
                planted.add(700L * 1024 * 1024 + i * (NEEDLE.length + 100L));
            }
        }
        return planted;
    }

    private static List<Long> collectHits(Map<String, SearchCoordinator.FileMatches> results) {
        List<Long> hits = new ArrayList<>();
        for (SearchCoordinator.FileMatches matches : results.values()) {
            for (SearchCoordinator.LineMatch line : matches.getLines()) {
                for (SearchCoordinator.Submatch sub : line.getSubmatches()) {
                    hits.add(sub.byteStart());
                }
            }
        }
        return hits;
    }

    @Test
    public void testLargeFileChunkedPathMatchesWholeFilePath() throws IOException {
        List<Long> planted = buildBigFile();
        try {
            // 分块路径（默认阈值 256MB；1.2GB > 阈值）
            SearchCoordinator chunked = new SearchCoordinator(4, false, false);
            Map<String, SearchCoordinator.FileMatches> chunkedResult =
                    chunked.search(new SearchCommand(tempDir, "needle",
                            SearchCoordinator.Strategy.LITERAL, false, List.of("big.bin"), 0));

            // 整文件路径（threshold = MAX）
            SearchCoordinator whole = new SearchCoordinator(4, false, false, Long.MAX_VALUE);
            Map<String, SearchCoordinator.FileMatches> wholeResult =
                    whole.search(new SearchCommand(tempDir, "needle",
                            SearchCoordinator.Strategy.LITERAL, false, List.of("big.bin"), 0));

            List<Long> chunkedHits = collectHits(chunkedResult);
            List<Long> wholeHits = collectHits(wholeResult);
            // collectHits 按扫描序（升序）返回；planted 按写入序，需排序后比较
            List<Long> plantedSorted = new ArrayList<>(planted);
            java.util.Collections.sort(plantedSorted);
            assertEquals(plantedSorted, chunkedHits, "分块路径命中偏移必须与植入位置一致（含边界横跨）");
            assertEquals(wholeHits, chunkedHits, "整文件路径与分块路径结果必须一致");
        } finally {
            // 无句柄/映射残留：close 后删除成功
            Files.deleteIfExists(bigFile);
        }
        assertTrue(Files.notExists(bigFile));
    }

    @Test
    public void testBoundaryStraddlingPatternFoundOnce() throws IOException {
        buildBigFile();
        try {
            SearchCoordinator chunked = new SearchCoordinator(2, false, false);
            Map<String, SearchCoordinator.FileMatches> results =
                    chunked.search(new SearchCommand(tempDir, "BOUNDARY",
                            SearchCoordinator.Strategy.LITERAL, false, List.of(), 0));
            List<Long> hits = collectHits(results);
            // "BOUNDARY" 起点在 chunk 边界前 4 字节——主区间去重后恰好一次
            assertEquals(List.of(CHUNK_BOUNDARY - 4), hits);
        } finally {
            Files.deleteIfExists(bigFile);
        }
    }

    @Test
    public void testRepeatScanConsistency() throws IOException {
        buildBigFile();
        try {
            SearchCoordinator chunked = new SearchCoordinator(4, false, false);
            SearchCommand command = new SearchCommand(tempDir, "needle",
                    SearchCoordinator.Strategy.LITERAL, false, List.of("big.bin"), 0);
            List<Long> first = collectHits(chunked.search(command));
            List<Long> second = collectHits(chunked.search(command));
            assertEquals(first, second);
        } finally {
            Files.deleteIfExists(bigFile);
        }
    }
}
