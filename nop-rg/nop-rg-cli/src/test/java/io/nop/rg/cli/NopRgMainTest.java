package io.nop.rg.cli;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.rg.core.coordinator.FileMatches;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLI 集成测试：进程内调用主类，断言参数解析、输出形态与退出码。
 */
public class NopRgMainTest {
    @TempDir
    Path tempDir;

    private Path buildTree() throws IOException {
        Files.writeString(tempDir.resolve(".gitignore"), "gen/\n");
        Files.createDirectories(tempDir.resolve("src"));
        Files.writeString(tempDir.resolve("src/Main.java"), "int needle = 0;\n// Needle here\n");
        Files.writeString(tempDir.resolve("README.md"), "needle one\nplain\nneedle two needle two\n");
        Files.createDirectories(tempDir.resolve("gen"));
        Files.writeString(tempDir.resolve("gen/out.txt"), "needle generated\n");
        return tempDir;
    }

    private record RunResult(int exitCode, String stdout, String stderr) {
    }

    private RunResult runMain(NopRgMain main, String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true));
        System.setErr(new PrintStream(errBytes, true));
        try {
            int code = new CommandLine(main).execute(args);
            System.out.flush();
            System.err.flush();
            return new RunResult(code, outBytes.toString(), errBytes.toString());
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private RunResult run(String... args) {
        return runMain(new NopRgMain(), args);
    }

    @Test
    public void testPlainTextOutputAndExitCodes() throws IOException {
        buildTree();
        RunResult hit = run("needle", tempDir.toString());
        assertEquals(0, hit.exitCode());
        assertTrue(hit.stdout().contains("src/Main.java:1:int needle = 0;"), "STDOUT=[" + hit.stdout() + "]");
        assertTrue(hit.stdout().contains("README.md:1:needle one"));
        // gen/ 被 gitignore 剪掉
        assertFalse(hit.stdout().contains("generated"));

        RunResult miss = run("nonexistent-pattern-xyz", tempDir.toString());
        assertEquals(1, miss.exitCode());
        // stdout 可能含 CoreInitialization 日志，断言无匹配输出行而非完全为空
        assertTrue(miss.stdout().lines().noneMatch(l -> l.contains("nonexistent-pattern-xyz") && l.contains(":")));
    }

    @Test
    public void testCountIsMatchingLineCountNotHitCount() throws IOException {
        buildTree();
        // README.md 第二行有两个命中：rg -c 计匹配行数 → 2（不是 3）
        RunResult result = run("-c", "needle", tempDir.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("README.md:2"), result.stdout());
        assertTrue(result.stdout().contains("src/Main.java:1"), result.stdout());
    }

    @Test
    public void testFilesWithMatchesAndGlobAndIgnoreCase() throws IOException {
        buildTree();
        RunResult files = run("-l", "needle", tempDir.toString());
        assertEquals(0, files.exitCode());
        assertTrue(files.stdout().contains("src/Main.java"));
        assertFalse(files.stdout().contains("gen/"));

        RunResult globbed = run("-l", "-g", "*.md", "needle", tempDir.toString());
        assertTrue(globbed.stdout().contains("README.md"));
        assertFalse(globbed.stdout().contains("Main.java"));

        RunResult folded = run("-i", "needle", tempDir.toString());
        assertTrue(folded.stdout().contains("src/Main.java:2:// Needle here"));
    }

    @Test
    public void testJsonMessageStructure() throws IOException {
        buildTree();
        RunResult result = run("--json", "needle", tempDir.toString());
        assertEquals(0, result.exitCode());
        String out = result.stdout();
        // begin/end 仅对有命中文件；无 summary
        assertTrue(out.contains("\"type\":\"begin\""));
        assertTrue(out.contains("\"type\":\"match\""));
        assertTrue(out.contains("\"type\":\"end\""));
        assertFalse(out.contains("\"type\":\"summary\""));
        assertTrue(out.contains("\"path\":{\"text\":\"src/Main.java\"}"));
        assertTrue(out.contains("\"line_number\":1"));
        assertTrue(out.contains("\"absolute_offset\":0"));
        assertTrue(out.contains("\"match\":{\"text\":\"needle\"}"));
        assertTrue(out.contains("\"binary_offset\":null"));
        // submatch start 为行内偏移
        assertTrue(out.contains("\"start\":4"));
    }

    @Test
    public void testCountCombinedWithJsonKeepsMatchMessages() throws IOException {
        buildTree();
        // plan 2275 G1：-c 只在非 JSON 输出下走 count 快速路径；--json 需要完整 match 消息——
        // 组合时不得输出零 match 的 begin/end 空 JSON（缺陷回归守护）
        RunResult result = run("-c", "--json", "needle", tempDir.toString());
        assertEquals(0, result.exitCode());
        String out = result.stdout();
        assertTrue(out.contains("\"type\":\"match\""), "STDOUT=[" + out + "]");
        assertTrue(out.contains("\"path\":{\"text\":\"README.md\"}"));
        assertTrue(out.contains("\"match\":{\"text\":\"needle\"}"));
    }

    @Test
    public void testNoIgnoreDisablesGitignoreButKeepsHiddenFilter() throws IOException {
        buildTree();
        RunResult result = run("--no-ignore", "needle", tempDir.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("needle generated"));
        // 隐藏文件（.gitignore）仍被跳过——rg --no-ignore 真语义
        assertFalse(result.stdout().contains(".gitignore"));
    }

    @Test
    public void testRegexFlag() throws IOException {
        buildTree();
        RunResult result = run("-r", "n[e]edle = \\d", tempDir.toString());
        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("src/Main.java:1:int needle = 0;"));
    }

    @Test
    public void testErrorPathNotDirectory() {
        RunResult result = run("needle", tempDir.resolve("no-such-dir").toString());
        assertEquals(2, result.exitCode());
        assertFalse(result.stderr().isEmpty());
    }

    @Test
    public void testDelegateRgPositiveBridgeUsesSystemRg() throws IOException {
        buildTree();
        // 正向桥接：经 main 的预解析透传给真实系统 rg 并执行
        List<String> rgArgs = NopRgMain.buildRgArgs(new String[]{"--delegate-rg", "needle", tempDir.toString()});
        assertEquals(List.of("rg", "needle", tempDir.toString()), rgArgs);
        assertEquals(0, NopRgMain.invokeRg(rgArgs));
        // =path 形式指定 rg 可执行路径
        List<String> custom = NopRgMain.buildRgArgs(new String[]{"--delegate-rg=/custom/rg", "-i", "x"});
        assertEquals(List.of("/custom/rg", "-i", "x"), custom);
    }

    @Test
    public void testDelegateRgMissingBinaryFailsWithCode2() {
        // 可注入测试缝：指向不存在的 rg 可执行文件 → 明确报错 + 退出码 2
        int code = NopRgMain.invokeRg(List.of(tempDir.resolve("no-such-rg").toString(), "--version"));
        assertEquals(2, code);
    }

    /**
     * 裸 NopException（非 NopRgException 子类，模拟 CoreInitialization/VFS/GitIgnoreFile 资源层
     * 故障）归位错误退出码 2 + stderr 消息，不落入 picocli 默认处理的 exit 1（plan 2276 G2）。
     */
    @Test
    public void testBareNopExceptionExits2() throws IOException {
        buildTree();
        NopException simulated = new NopException(
                ErrorCode.define("TEST_SIMULATED_CORE_FAILURE", "simulated core failure"));
        NopRgMain failing = new NopRgMain() {
            @Override
            Map<String, FileMatches> search(Path root) {
                throw simulated;
            }
        };
        RunResult result = runMain(failing, "needle", tempDir.toString());
        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("nop-rg:"), "STDERR=[" + result.stderr() + "]");
    }
}
