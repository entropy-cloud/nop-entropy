package io.nop.rg.cli;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 与系统 rg 的对比测试（plan 2264 TEST-01..03，里程碑 M2 收口）。
 *
 * <p>归一化规则：--json 模式下按 match 消息抽取元组
 * {@code (path, line_number, absolute_offset, lines.text, submatch.start, submatch.text)}
 * 作多重集比较（消息顺序非确定）；end.stats/summary 不参与；corpus 纯 ASCII。
 * rg 以 corpus 为 CWD 运行（glob 基于相对 CWD 路径），文本场景加 `-n`（rg 默认仅在 tty 显示行号，
 * 管道下无行号；nop-rg 文本输出恒带行号，等价 rg -n）。归一化：剔除 CoreInitialization 日志行、
 * 剥离 rg 的 "./" 路径前缀。rg 不可用时跳过。
 *
 * <p>关键实测事实：rg 仅在 git 仓库内应用 .gitignore（非 repo 目录的 .gitignore 被忽略）；
 * nop-rg 无条件尊重 .gitignore。为保证可比性，corpus 以 {@code git init} 初始化。
 */
public class RgComparisonTest {
    @TempDir
    Path tempDir;

    private static final String NEEDLE = "needle";

    private static boolean rgAvailable() {
        try {
            Process p = new ProcessBuilder("rg", "--version").start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path buildCorpus() throws Exception {
        // rg 仅在 git 仓库内应用 .gitignore——corpus 必须 git init（见类注释）
        Process gitInit = new ProcessBuilder("git", "init", "-q").directory(tempDir.toFile()).start();
        boolean gitOk = gitInit.waitFor() == 0;
        org.junit.jupiter.api.Assumptions.assumeTrue(gitOk, "git not available for corpus init");
        Files.writeString(tempDir.resolve(".gitignore"), "gen/\n");
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Main.java"), "int needle = 0;\n// Needle here\nno match\n");
        Files.writeString(tempDir.resolve("src/Util.java"), "nothing to see\n");
        Files.writeString(tempDir.resolve("README.md"), "needle one\nplain line\nneedle two needle two\n");
        Files.createDirectories(tempDir.resolve("gen"));
        Files.writeString(tempDir.resolve("gen/out.txt"), "needle generated\n");
        Files.writeString(tempDir.resolve(".hidden.txt"), "needle hidden\n");
        Files.writeString(tempDir.resolve("empty.txt"), "");
        return tempDir;
    }

    private record RunResult(int exitCode, String stdout) {
    }

    private RunResult runNopRg(String... args) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        try {
            int code = new CommandLine(new NopRgMain()).execute(args);
            System.out.flush();
            return new RunResult(code, outBytes.toString());
        } finally {
            System.setOut(originalOut);
        }
    }

    private RunResult runRg(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("rg");
        for (String arg : args) {
            cmd.add(arg);
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false)
                .directory(tempDir.toFile()).start();
        int code = p.waitFor();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new RunResult(code, out);
    }

    private static final Pattern LOG_LINE = Pattern.compile(
            "^\\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[[^]]*] (INFO|DEBUG|WARN|ERROR) .*$");

    private void compareScenarios(String label, String[] nopRgArgs, String[] rgArgs) throws Exception {
        buildCorpus();
        RunResult ours = runNopRg(nopRgArgs);
        RunResult theirs = runRg(rgArgs);
        assertEquals(theirs.exitCode(), ours.exitCode(), label + ": exit code");
        assertEquals(sortLines(theirs.stdout()), sortLines(ours.stdout()), label + ": text output");
    }

    private static List<String> sortLines(String stdout) {
        return stdout.lines()
                .filter(l -> !l.isBlank())
                .filter(l -> !LOG_LINE.matcher(l).matches())
                .map(l -> l.startsWith("./") ? l.substring(2) : l)
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    public void testBasicSearchMatchesRg() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        compareScenarios("basic", new String[]{NEEDLE, tempDir.toString()},
                new String[]{"-n", NEEDLE, "."});
    }

    @Test
    public void testGlobIncludeMatchesRg() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        compareScenarios("glob-include", new String[]{"-g", "*.java", NEEDLE, tempDir.toString()},
                new String[]{"-n", "-g", "*.java", NEEDLE, "."});
    }

    @Test
    public void testGlobExcludeMatchesRg() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        compareScenarios("glob-exclude", new String[]{"-g", "!*.md", NEEDLE, tempDir.toString()},
                new String[]{"-n", "-g", "!*.md", NEEDLE, "."});
    }

    @Test
    public void testIgnoreCaseMatchesRgOnAsciiCorpus() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        compareScenarios("ignore-case", new String[]{"-i", NEEDLE, tempDir.toString()},
                new String[]{"-n", "-i", NEEDLE, "."});
    }

    @Test
    public void testNoIgnoreMatchesRg() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        // --no-ignore：两侧都恢复被 gitignore 剪掉的 gen/，且都不搜隐藏文件
        compareScenarios("no-ignore", new String[]{"--no-ignore", NEEDLE, tempDir.toString()},
                new String[]{"-n", "--no-ignore", NEEDLE, "."});
    }

    @Test
    public void testCountModeMatchesRg() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        compareScenarios("count", new String[]{"-c", NEEDLE, tempDir.toString()},
                new String[]{"-c", NEEDLE, "."});
    }

    @Test
    public void testFilesWithMatchesModeMatchesRg() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        compareScenarios("files-with-matches", new String[]{"-l", NEEDLE, tempDir.toString()},
                new String[]{"-l", NEEDLE, "."});
    }

    @Test
    public void testJsonModeMatchesRg() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        buildCorpus();
        RunResult ours = runNopRg("--json", NEEDLE, tempDir.toString());
        RunResult theirs = runRg("--json", NEEDLE, ".");
        assertEquals(0, ours.exitCode());
        assertEquals(0, theirs.exitCode());
        assertEquals(extractJsonTuples(theirs.stdout()), extractJsonTuples(ours.stdout()),
                "json match tuples must match rg");
    }

    @Test
    public void testNoMatchExitCodeMatchesRg() throws Exception {
        Assumptions.assumeTrue(rgAvailable(), "system rg not available");
        buildCorpus();
        RunResult ours = runNopRg("zzz-nonexistent", tempDir.toString());
        RunResult theirs = runRg("zzz-nonexistent", ".");
        assertEquals(theirs.exitCode(), ours.exitCode());
        assertEquals(1, ours.exitCode());
    }

    // (path, line_number, absolute_offset, lines.text, submatch.start, submatch.text) 元组抽取
    private static final Pattern MATCH_LINE = Pattern.compile(
            "\"type\":\"match\",\"data\":\\{\"path\":\\{\"text\":\"([^\"]*)\"\\},\"lines\":\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"\\},\"line_number\":(\\d+),\"absolute_offset\":(\\d+),\"submatches\":\\[(.*)\\]\\}");

    private static final Pattern SUBMATCH = Pattern.compile(
            "\\{\"match\":\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"\\},\"start\":(\\d+),\"end\":(\\d+)\\}");

    private static List<String> extractJsonTuples(String jsonOutput) {
        List<String> tuples = new ArrayList<>();
        for (String line : jsonOutput.split("\n")) {
            Matcher m = MATCH_LINE.matcher(line);
            if (!m.find()) {
                continue;
            }
            String path = m.group(1);
            String lineText = m.group(2);
            String lineNo = m.group(3);
            String absOffset = m.group(4);
            String submatches = m.group(5);
            Matcher sm = SUBMATCH.matcher(submatches);
            String normPath = path.startsWith("./") ? path.substring(2) : path;
            while (sm.find()) {
                tuples.add(String.join("|", normPath, lineNo, absOffset, lineText, sm.group(2), sm.group(1)));
            }
        }
        return tuples.stream().sorted().collect(Collectors.toList());
    }
}
