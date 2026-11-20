package io.nop.rg.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * `--encoding` 显式编码契约测试（plan 2575 Phase E2，设计文档 03 决策 3/4）：
 *
 * <ul>
 *   <li>三处一致：GBK 语料 literal 命中 / regex 命中（含 submatch 字节偏移）/ 输出文本全链路正确；
 *       漏改任一处对应变异探针必红。</li>
 *   <li>{@code --encoding none} 与未指定逐字节一致。</li>
 *   <li>非法 charset 值 exit 3 + stderr 显式报错（不回退 UTF-8）。</li>
 *   <li>二进制判定不受 {@code --encoding} 影响（决策 4）。</li>
 * </ul>
 */
public class EncodingCliTest {

    @TempDir
    Path tempDir;

    private record RunResult(int exitCode, String stdout, String stderr) {
    }

    private RunResult run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new NopRgMain()).execute(args);
            System.out.flush();
            System.err.flush();
            return new RunResult(code, outBytes.toString(StandardCharsets.UTF_8),
                    errBytes.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    /** GBK 语料：两行 GBK 编码文本。 */
    private byte[] gbkCorpus() throws Exception {
        return ("needle 中文needle行\nplain 无命中\n").getBytes(Charset.forName("GBK"));
    }

    /**
     * GBK literal 全链路：--encoding gbk 命中两行 needle，text 为正确 GBK 解码
     * （模式编码 / 输出解码一致）；bytes 字段为原始 GBK 字节 base64（区间非法 UTF-8，R2）。
     */
    @Test
    public void testGbkLiteralFullChain() throws Exception {
        byte[] corpus = gbkCorpus();
        Files.write(tempDir.resolve("gbk.txt"), corpus);
        RunResult result = run("--encoding", "gbk", "--json", "needle", tempDir.toString());
        assertEquals(0, result.exitCode(), "stderr=" + result.stderr());
        String jsonLines = jsonLines(result.stdout());
        assertTrue(jsonLines.contains("needle 中文needle行"), "GBK 输出文本必须正确解码: " + jsonLines);
        // submatch 0..6 为第一行 needle 的字节区间
        assertTrue(jsonLines.contains("\"start\":0,\"end\":6"), jsonLines);
        // bytes 字段：行原始字节 base64，解码 == 文件第一行区间（含 \n）
        String bytesLine = jsonLines.lines().filter(l -> l.contains("\"bytes\":\"")).findFirst().orElse(null);
        assertTrue(bytesLine != null, "GBK 行原始字节非法 UTF-8，必须输出 bytes: " + jsonLines);
        int s = bytesLine.indexOf("\"bytes\":\"") + 9;
        byte[] restored = Base64.getDecoder().decode(bytesLine.substring(s, bytesLine.indexOf('"', s)));
        byte[] expected = new byte[20]; // "needle 中文needle行\n" 的 GBK 字节长度（6+1+4+6+2+1）
        System.arraycopy(corpus, 0, expected, 0, expected.length);
        assertArrayEquals(expected, restored);
    }

    /**
     * GBK regex 全链路（含 R5 断言）：multibyte 构造 regex 在 GBK 上命中，
     * submatch 字节偏移按 GBK 实际长度计算（Utf8OffsetMap 写死 UTF-8 时必漂移）。
     */
    @Test
    public void testGbkRegexSubmatchByteOffsets() throws Exception {
        byte[] corpus = gbkCorpus();
        Files.write(tempDir.resolve("gbk.txt"), corpus);
        // 模式含 multibyte + regex 构造：命中 "needle 中文"（第一行 0..11 GBK 字节）
        RunResult result = run("--encoding", "gbk", "--json", "--regex", "needle 中.", tempDir.toString());
        assertEquals(0, result.exitCode(), "stderr=" + result.stderr());
        String jsonLines = jsonLines(result.stdout());
        // submatch 字节偏移按 GBK 实际长度：needle 6 + 空格 1 + 中文 2*2 = 11；
        // Utf8OffsetMap 写死 UTF-8（中文字算 3 字节）时必漂移为 13 → 用例红
        assertTrue(jsonLines.contains("\"start\":0,\"end\":11"), "GBK regex submatch 字节偏移: " + jsonLines);
    }

    /**
     * GBK 非 ASCII 模式（设计第三节强制探针面）：模式「中文」按 GBK 编码后才能命中 GBK 语料；
     * 模式编码漏改回退 UTF-8（字节不同）时该用例必红。
     */
    @Test
    public void testGbkMultibytePatternHit() throws Exception {
        Files.write(tempDir.resolve("gbk.txt"), gbkCorpus());
        RunResult result = run("--encoding", "gbk", "--json", "中文", tempDir.toString());
        assertEquals(0, result.exitCode(), "GBK 模式必须按 GBK 编码后命中: stderr=" + result.stderr());
        String jsonLines = jsonLines(result.stdout());
        assertTrue(jsonLines.contains("needle 中文needle行"), jsonLines);
        // 命中区间 7..11（"中文" 的 GBK 字节）
        assertTrue(jsonLines.contains("\"start\":7,\"end\":11"), jsonLines);
    }

    /**
     * 默认（无 --encoding）下 GBK 文件静默失真的对拍面保持现状语义：needle 的 ASCII 字节
     * 在 GBK 中不与中文冲突（GBK 双字节首字节 ≥ 0x81），literal 仍按字节命中，
     * 但 text 为 UTF-8 lossy 形态（不按 GBK 解码）。
     */
    @Test
    public void testGbkCorpusWithoutEncodingIsByteFidelityDefault() throws Exception {
        byte[] corpus = gbkCorpus();
        Files.write(tempDir.resolve("gbk.txt"), corpus);
        RunResult result = run("--json", "needle", tempDir.toString());
        assertEquals(0, result.exitCode(), result.stderr());
        String jsonLines = jsonLines(result.stdout());
        // 输出不得按 GBK 解码出中文（否则就是未指定编码却偷偷转码）
        assertFalse(jsonLines.contains("中文needle行"), jsonLines);
        // 非法 UTF-8 行仍输出 bytes（E1 契约不因语料编码改变）
        assertTrue(jsonLines.contains("\"bytes\":\""), jsonLines);
    }

    /** --encoding none 与未指定逐字节一致（决策 2 显式形式）。 */
    @Test
    public void testEncodingNoneEquivalentToDefault() throws Exception {
        Files.write(tempDir.resolve("gbk.txt"), gbkCorpus());
        Files.write(tempDir.resolve("utf8.txt"), "hello 中文 needle\n".getBytes(StandardCharsets.UTF_8));
        RunResult plain = run("--json", "needle", tempDir.toString());
        RunResult none = run("--encoding", "none", "--json", "needle", tempDir.toString());
        assertEquals(jsonLines(plain.stdout()), jsonLines(none.stdout()), "--encoding none 必须与默认逐字节一致");
    }

    /** 非法 charset 值：exit 3 + stderr 显式报错，不回退 UTF-8（R4）。 */
    @Test
    public void testInvalidCharsetExits3() throws Exception {
        Files.write(tempDir.resolve("utf8.txt"), "needle\n".getBytes(StandardCharsets.UTF_8));
        RunResult bad = run("--encoding", "not-a-charset-xyz", "needle", tempDir.toString());
        assertEquals(3, bad.exitCode(), "stdout=" + bad.stdout());
        assertTrue(bad.stderr().contains("not-a-charset-xyz"), bad.stderr());
        assertEquals("", jsonLines(bad.stdout()), "非法 charset 不得产生搜索输出");
    }

    /** 二进制判定不受 --encoding 影响（决策 4）：含 NUL 头部的文件仍跳过。 */
    @Test
    public void testBinarySniffUnaffectedByEncoding() throws Exception {
        ByteArrayOutputStream bin = new ByteArrayOutputStream();
        bin.writeBytes(new byte[16]); // 头部 NUL
        bin.writeBytes("needle in binary".getBytes(StandardCharsets.ISO_8859_1));
        Files.write(tempDir.resolve("bin.dat"), bin.toByteArray());
        RunResult result = run("--encoding", "iso-8859-1", "needle", tempDir.toString());
        assertEquals(1, result.exitCode(), "二进制文件必须仍被 NUL 嗅探跳过: " + result.stdout());
    }

    private static String jsonLines(String stdout) {
        return stdout.lines()
                .filter(l -> l.startsWith("{\"type\":"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
