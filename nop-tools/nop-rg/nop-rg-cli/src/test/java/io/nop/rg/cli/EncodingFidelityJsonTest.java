package io.nop.rg.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 输出字节保真契约测试（plan 2575 Phase E1，设计文档 03 决策 1/2）：
 *
 * <ul>
 *   <li>合法 UTF-8 语料：{@code --json} 输出与改动前逐字节一致（golden 全串断言，
 *       bytes 字段零增长）。</li>
 *   <li>非法 UTF-8 语料：lines.bytes 为行区间（含终止符）原始字节的 base64，解码后与
 *       文件字节逐字节相等；text 为 lossy 形态（U+FFFD）。</li>
 * </ul>
 */
public class EncodingFidelityJsonTest {

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

    /**
     * 合法 UTF-8 输出零变化（plan 2575 Phase E1 golden 断言，R10）。
     * 该期望串即改动前行为（text-only schema），任何 schema 漂移（如错误引入 bytes 字段）
     * 都会变红。
     */
    @Test
    public void testValidUtf8OutputUnchanged() throws Exception {
        byte[] corpus = ("hello abc world\n中文 abc 测试\nplain\n").getBytes(StandardCharsets.UTF_8);
        Files.write(tempDir.resolve("mixed.txt"), corpus);
        RunResult result = run("--json", "abc", tempDir.toString());
        assertEquals(0, result.exitCode(), result.stderr());
        String line1 = "{\"type\":\"match\",\"data\":{\"path\":{\"text\":\"mixed.txt\"},"
                + "\"lines\":{\"text\":\"hello abc world\\n\"},"
                + "\"line_number\":1,\"absolute_offset\":0,"
                + "\"submatches\":[{\"match\":{\"text\":\"abc\"},\"start\":6,\"end\":9}]}}";
        String line2 = "{\"type\":\"match\",\"data\":{\"path\":{\"text\":\"mixed.txt\"},"
                + "\"lines\":{\"text\":\"中文 abc 测试\\n\"},"
                + "\"line_number\":2,\"absolute_offset\":16,"
                + "\"submatches\":[{\"match\":{\"text\":\"abc\"},\"start\":7,\"end\":10}]}}";
        // 进程内 fork 的 stdout 会混入 CoreInitialization 日志——只比 JSON 消息行
        String jsonLines = result.stdout().lines()
                .filter(l -> l.startsWith("{\"type\":"))
                .collect(java.util.stream.Collectors.joining("\n", "", "\n"));
        assertEquals("{\"type\":\"begin\",\"data\":{\"path\":{\"text\":\"mixed.txt\"}}}\n"
                + line1 + "\n" + line2 + "\n"
                + "{\"type\":\"end\",\"data\":{\"path\":{\"text\":\"mixed.txt\"},"
                + "\"binary_offset\":null,\"binary_end\":null}}\n", jsonLines);
        assertFalse(result.stdout().lines().anyMatch(l -> l.startsWith("{\"type\":") && l.contains("\"bytes\"")),
                "合法 UTF-8 输出不得出现 bytes 字段");
    }

    /**
     * 非法 UTF-8 行：bytes base64 解码 == 文件行区间（含终止符）逐字节相等（设计文档 03 验收表）；
     * text 为 lossy 形态。
     */
    @Test
    public void testInvalidUtf8BytesFieldRestoresRawBytes() throws Exception {
        ByteArrayOutputStream corpus = new ByteArrayOutputStream();
        corpus.writeBytes("hello abc world\n".getBytes(StandardCharsets.UTF_8));
        // 第二行：合法中文 + 非法字节 \xB2\xE2\xB4\xD9 + abc + 尾部 \n
        corpus.writeBytes("中文 abc ".getBytes(StandardCharsets.UTF_8));
        corpus.writeBytes(new byte[]{(byte) 0xB2, (byte) 0xE2, (byte) 0xB4, (byte) 0xD9});
        corpus.writeBytes(" abc tail\n".getBytes(StandardCharsets.UTF_8));
        Files.write(tempDir.resolve("bad.txt"), corpus.toByteArray());

        RunResult result = run("--json", "abc", tempDir.toString());
        assertEquals(0, result.exitCode(), result.stderr());
        String out = result.stdout();
        assertTrue(out.contains("\"type\":\"match\""), out);

        String bytesLine = out.lines().filter(l -> l.contains("\"bytes\"")).findFirst().orElse(null);
        assertTrue(bytesLine != null, "非法 UTF-8 行必须输出 bytes 字段: " + out);
        int b64Start = bytesLine.indexOf("\"bytes\":\"") + "\"bytes\":\"".length();
        String b64 = bytesLine.substring(b64Start, bytesLine.indexOf('"', b64Start));
        byte[] restored = Base64.getDecoder().decode(b64);
        // 行区间 = lineStart..lineEnd（含 \n）：第二行起点 16，内容 + 非法 4 字节 + tail + \n
        byte[] expected = new byte[corpus.size() - 16];
        byte[] all = corpus.toByteArray();
        System.arraycopy(all, 16, expected, 0, expected.length);
        assertArrayEquals(expected, restored, "bytes 解码必须与文件行区间逐字节相等");

        // text 为 lossy 形态：非法字节各替换为 U+FFFD，可读部分保留
        RunResult plain = run("abc", tempDir.toString());
        assertEquals(0, plain.exitCode(), plain.stderr());
        // abc 命中：第二行应有 2 处 abc（非法字节前 + tail 前）
        assertTrue(out.contains("\\ufffd") || out.contains("�"),
                "非法行 text 必须为 lossy 形态: " + out);
    }

    /**
     * 合法 UTF-8 文件（含非法文件并存的目录）中，合法行不携带 bytes（零增长，决策 2）。
     */
    @Test
    public void testValidLinesInMixedCorpusHaveNoBytes() throws Exception {
        ByteArrayOutputStream corpus = new ByteArrayOutputStream();
        corpus.writeBytes("good abc line\n".getBytes(StandardCharsets.UTF_8));
        corpus.writeBytes(new byte[]{(byte) 0xB2, 'a', 'b', 'c', '\n'});
        Files.write(tempDir.resolve("mix.txt"), corpus.toByteArray());
        RunResult result = run("--json", "abc", tempDir.toString());
        assertEquals(0, result.exitCode(), result.stderr());
        String goodLine = result.stdout().lines().filter(l -> l.contains("good abc line")).findFirst().orElseThrow();
        assertFalse(goodLine.contains("\"bytes\""), "合法行不得携带 bytes 字段: " + goodLine);
        String badLine = result.stdout().lines().filter(l -> l.contains("\"bytes\"")).findFirst().orElseThrow();
        assertTrue(badLine.contains("abc"), badLine);
    }
}
