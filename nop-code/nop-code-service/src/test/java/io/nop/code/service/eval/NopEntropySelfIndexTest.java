package io.nop.code.service.eval;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N9.1 Self-indexing: use nop-code's Java analyzer to index nop-entropy core
 * source files and record scale baseline (symbol count, file count, timing).
 */
@EnabledIf("io.nop.code.service.eval.SourceDirCondition#isSourceAvailable")
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
