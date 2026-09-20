package io.nop.rg.cli;

import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.core.coordinator.SearchCommand;
import io.nop.rg.core.coordinator.SearchCoordinator;
import io.nop.rg.core.coordinator.FileMatches;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * --jfr 开关测试（plan 2265 JFR-03）。
 *
 * <p>ExecutionSample 是运行中的 Java 线程周期采样：短/空闲录制可能为 0（审查实测空闲 2.5s 为 0）。
 * 因此 CPU 采样断言使用 JfrSupport 直连 + 满载工作负载（重复扫描 ≥2.5s）；
 * CLI 侧只断言文件生成且含周期性事件（CPULoad），避免 flaky。
 */
public class JfrSwitchTest {
    @TempDir
    Path tempDir;

    private Path buildCorpus(int fileCount, int chunkRepeat) throws Exception {
        Path dir = tempDir.resolve("corpus");
        Files.createDirectories(dir);
        byte[] chunk = ("alpha beta gamma needle delta epsilon zeta\n"
                + "plain line without match content here\n").repeat(chunkRepeat)
                .getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < fileCount; i++) {
            Files.write(dir.resolve("part" + i + ".txt"), chunk);
        }
        return dir;
    }

    @Test
    public void testJfrRecordingCapturesExecutionSamplesUnderLoad() throws Exception {
        CoreInitialization.initialize();
        try {
            Path corpus = buildCorpus(8, 2000); // ~7MB 文本
            Path jfrOutput = tempDir.resolve("loaded.jfr");
            AutoCloseable recording = JfrSupport.startRecording(jfrOutput);
            try {
                SearchCoordinator coordinator = new SearchCoordinator(4, false, false);
                long deadline = System.nanoTime() + 2_500_000_000L; // 2.5s 满载
                do {
                    Map<String, FileMatches> results = coordinator.search(
                            new SearchCommand(corpus, "needle", SearchCoordinator.Strategy.LITERAL,
                                    false, List.of(), 0));
                    if (results.isEmpty() && System.nanoTime() > deadline) {
                        break;
                    }
                } while (System.nanoTime() < deadline);
            } finally {
                recording.close();
            }
            assertTrue(Files.exists(jfrOutput));
            try (RecordingFile file = new RecordingFile(jfrOutput)) {
                List<String> typeNames = file.readEventTypes().stream().map(t -> t.getName()).toList();
                assertTrue(typeNames.contains("jdk.ExecutionSample"),
                        "满载录制应包含 CPU 采样，实际: " + typeNames);
                assertTrue(typeNames.contains("jdk.CPULoad"));
                assertTrue(typeNames.contains("jdk.ObjectAllocationInNewTLAB"));
            }
        } finally {
            CoreInitialization.destroy();
        }
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
    public void testJfrDumpStillGeneratedOnSearchError() throws Exception {
        // audit m7 专项：搜索中途抛错时 finally 路径仍 stop + dump
        Path corpus = buildCorpus(1, 10);
        // 构造不可读子目录触发 walker 快速失败（POSIX）
        Path locked = corpus.resolve("locked-dir");
        Files.createDirectories(locked);
        Files.writeString(locked.resolve("x.txt"), "needle");
        Files.setPosixFilePermissions(locked,
                java.nio.file.attribute.PosixFilePermissions.fromString("---------"));
        Path jfrOutput = tempDir.resolve("error.jfr");
        try {
            RunResult result = run("--jfr", jfrOutput.toString(), "needle", corpus.toString());
            assertEquals(2, result.exitCode());
        } finally {
            Files.setPosixFilePermissions(locked,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
        assertTrue(Files.exists(jfrOutput), "异常路径仍须 dump 录制文件");
        try (RecordingFile file = new RecordingFile(jfrOutput)) {
            List<String> typeNames = file.readEventTypes().stream().map(t -> t.getName()).toList();
            assertTrue(typeNames.contains("jdk.CPULoad"), "dump 应可解析: " + typeNames.size() + " types");
        }
    }

    @Test
    public void testCliJfrSwitchProducesParseableRecording() throws Exception {
        Path corpus = buildCorpus(2, 200); // 小 corpus，冒烟即可
        Path jfrOutput = tempDir.resolve("cli.jfr");
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        try {
            int code = new CommandLine(new NopRgMain())
                    .execute("--jfr", jfrOutput.toString(), "needle", corpus.toString());
            assertEquals(0, code);
        } finally {
            System.setOut(originalOut);
        }
        assertTrue(Files.exists(jfrOutput));
        assertTrue(Files.size(jfrOutput) > 0);
        try (RecordingFile file = new RecordingFile(jfrOutput)) {
            List<String> typeNames = file.readEventTypes().stream().map(t -> t.getName()).toList();
            assertTrue(typeNames.contains("jdk.CPULoad"), "周期性事件应在录制中出现: " + typeNames);
        }
    }
}
