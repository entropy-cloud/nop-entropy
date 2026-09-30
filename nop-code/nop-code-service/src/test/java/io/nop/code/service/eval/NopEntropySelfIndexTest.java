package io.nop.code.service.eval;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N9.1 Self-indexing: use nop-code's Java analyzer to index nop-entropy core
 * source files and record scale baseline (symbol count, file count, timing).
 * <p>
 * Disabled（WS4 2026-09-30）：原 @EnabledIf 引用的
 * org.junit.jupiter.api.condition.EnabledIf 在 JUnit 6 中已移除（缺 import），
 * 且其条件类 io.nop.code.service.eval.SourceDirCondition 从未提交入仓，
 * 导致模块 testCompile 失败；同时该测试自提交以来从未真正运行过——
 * 其 symbolCount>100 断言对实际目录（51 个 java 文件、46 行候选）不可能通过。
 * 修复保持其"默认跳过"的运行时语义并记录遗留，等待作者补齐条件类与基线口径后启用。
 */
@Disabled("WIP N9.1 scale-baseline: guard condition class SourceDirCondition never committed; "
        + "symbol-count heuristic assertions not calibrated against actual nop-code-core layout")
class NopEntropySelfIndexTest {

    @Test
    void selfIndexNopCodeCoreAndRecordBaseline() throws IOException {
        // index nop-code-core source tree (representative scale baseline)
        Path sourceDir = Path.of("../nop-code-core/src/main/java/io/nop/code");
        if (!sourceDir.toFile().exists()) {
            sourceDir = Path.of("src/main/java/io/nop/code");
        }
        assertTrue(sourceDir.toFile().exists(), "source dir must exist: " + sourceDir);

        long start = System.currentTimeMillis();

        int fileCount = 0;
        int symbolCount = 0;
        int lineCount = 0;

        try (Stream<Path> walk = Files.walk(sourceDir)) {
            var javaFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
            fileCount = javaFiles.size();
            for (Path file : javaFiles) {
                try {
                    String content = Files.readString(file);
                    lineCount += content.lines().count();
                    // count class/method/field declarations as proxy for symbols
                    for (String line : content.split("\\n")) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("public class ") || trimmed.startsWith("public interface ")
                                || trimmed.startsWith("public enum ") || trimmed.contains(" class ")
                                || trimmed.contains(" interface ")) {
                            symbolCount++;
                        }
                        if (trimmed.contains(" public ") && trimmed.contains("(")
                                && !trimmed.startsWith("//") && !trimmed.startsWith("*")
                                && trimmed.contains("(") && trimmed.endsWith(")") || trimmed.endsWith("{")
                                && trimmed.contains("(") && trimmed.contains(" public ")) {
                            symbolCount++;
                        }
                    }
                } catch (Exception e) {
                    // non-fatal
                }
            }
        }

        long elapsedMs = (System.currentTimeMillis() - start);
        double elapsedSec = elapsedMs / 1000.0;

        System.out.println("=== N9.1 Self-Indexing Scale Baseline ===");
        System.out.println("  Directory: " + sourceDir);
        System.out.println("  Files indexed: " + fileCount);
        System.out.println("  Symbols extracted: " + symbolCount);
        System.out.println("  Time: " + elapsedMs + "ms (" + elapsedSec + "s)");
        System.out.println("  Throughput: " + (fileCount > 0 ? String.format("%.1f", fileCount / elapsedSec) : "0") + " files/sec");

        assertTrue(fileCount > 50, "nop-code-core should have > 50 Java files");
        assertTrue(symbolCount > 100, "should extract > 100 symbols");
        assertTrue(elapsedMs < 120000, "indexing should complete in < 2 minutes");
    }
}
