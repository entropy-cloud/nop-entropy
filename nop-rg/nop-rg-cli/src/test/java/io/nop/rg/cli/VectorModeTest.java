package io.nop.rg.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * --vector 集成测试（plan 2266 VSW-03）：
 * --vector 与标量模式同 corpus 输出完全一致；canary 守护 argLine 配置。
 */
public class VectorModeTest {
    @TempDir
    Path tempDir;

    private Path buildCorpus() throws Exception {
        Path dir = tempDir.resolve("corpus");
        Files.createDirectories(dir);
        for (int i = 0; i < 6; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 200; j++) {
                sb.append("line ").append(i).append(':').append(j).append(" needle alpha beta\n");
                if (j % 3 == 0) {
                    sb.append("plain line without match\n");
                }
            }
            Files.write(dir.resolve("part" + i + ".txt"), sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return dir;
    }

    private record RunResult(int exitCode, String stdout, String stderr) {
    }

    private RunResult run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        System.setErr(new PrintStream(errBytes, true));
        try {
            int code = new CommandLine(new NopRgMain()).execute(args);
            System.out.flush();
            System.err.flush();
            return new RunResult(code, outBytes.toString(), errBytes.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    public void canaryIncubatorModuleLoadable() throws ClassNotFoundException {
        Class.forName("jdk.incubator.vector.ByteVector");
    }

    // CoreInitialization 日志行（时间戳不同）不参与输出一致性对比
    private static final Pattern LOG_LINE = Pattern.compile(
            "^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[[^]]*] (INFO|DEBUG|WARN|ERROR) .*$");

    private static List<String> normalized(String stdout) {
        return stdout.lines()
                .filter(l -> !l.isBlank())
                .filter(l -> !LOG_LINE.matcher(l).matches())
                .collect(Collectors.toList());
    }

    @Test
    public void testVectorModeOutputIdenticalToScalar() throws Exception {
        Path corpus = buildCorpus();
        RunResult scalar = run("needle", corpus.toString());
        RunResult vector = run("--vector", "needle", corpus.toString());
        assertEquals(0, scalar.exitCode());
        assertEquals(0, vector.exitCode(), "STDERR=[" + vector.stderr() + "]");
        assertEquals(normalized(scalar.stdout()), normalized(vector.stdout()), "--vector 输出必须与标量一致");
        assertFalse(normalized(scalar.stdout()).isEmpty());
    }

    @Test
    public void testVectorCountModeMatchesScalarCount() throws Exception {
        Path corpus = buildCorpus();
        RunResult scalar = run("-c", "needle", corpus.toString());
        RunResult vector = run("-c", "--vector", "needle", corpus.toString());
        assertEquals(scalar.exitCode(), vector.exitCode());
        assertEquals(normalized(scalar.stdout()), normalized(vector.stdout()));
        assertTrue(vector.stdout().lines().anyMatch(l -> l.matches(".*part\\d+\\.txt:200")), vector.stdout());
    }

    @Test
    public void testRegexTakesPrecedenceOverVector() throws Exception {
        Path corpus = buildCorpus();
        // --regex 优先于 --vector：REGEX 策略（vector 不适用于正则）
        RunResult combined = run("-r", "--vector", "n[e]edle", corpus.toString());
        RunResult regexOnly = run("-r", "n[e]edle", corpus.toString());
        assertEquals(regexOnly.exitCode(), combined.exitCode());
        assertEquals(normalized(regexOnly.stdout()), normalized(combined.stdout()));
    }
}
