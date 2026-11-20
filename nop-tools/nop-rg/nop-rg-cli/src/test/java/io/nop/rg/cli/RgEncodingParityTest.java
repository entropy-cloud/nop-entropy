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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 三语料（GBK / Latin-1 / Shift-JIS + 合法 UTF-8）与系统 rg `--json` 对拍
 * （plan 2575 Phase E3，设计文档 03 验收表 + R3 对拍矩阵）：
 *
 * <ul>
 *   <li>默认编码：合法 UTF-8 行输出与 rg 逐字节一致；非法行 bytes base64 值与 rg 一致，
 *       text 为超集字段（不要求与 rg 互斥形态逐字符一致）。</li>
 *   <li>{@code --encoding gbk}：text 与 rg 转码输出一致。</li>
 *   <li>R6 硬门禁：运行环境系统 rg 不可用时测试直接失败（0 skipped——对拍 skipped 不算达成）。</li>
 * </ul>
 */
public class RgEncodingParityTest {

    @TempDir
    Path tempDir;

    private static boolean rgAvailable() {
        try {
            return new ProcessBuilder("rg", "--version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private record Msg(String path, long lineNo, long absOffset, String text, String bytes,
                       List<String> submatches) implements Comparable<Msg> {
        /** 仅按定位三元组排序（text/bytes 形态两侧允许差异，不参与排序）。 */
        @Override
        public int compareTo(Msg o) {
            int c = path.compareTo(o.path);
            if (c != 0) return c;
            c = Long.compare(lineNo, o.lineNo);
            if (c != 0) return c;
            return Long.compare(absOffset, o.absOffset);
        }
    }

    private static final Pattern LINE_OBJ = Pattern.compile(
            "\"lines\":\\{(.*?)\\},\"line_number\":(\\d+),\"absolute_offset\":(\\d+),\"submatches\":\\[(.*?)\\]}}");
    private static final Pattern PATH_OBJ = Pattern.compile("^\\{\"type\":\"(begin|match|end)\",\"data\":\\{\"path\":\\{\"text\":\"([^\"]*)\"");
    private static final Pattern SUB_OBJ = Pattern.compile(
            "\\{\"match\":\\{((?:[^\"\\\\]|\\\\.)*?)\\},\"start\":(\\d+),\"end\":(\\d+)\\}");
    private static final Pattern TEXT_FIELD = Pattern.compile("\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern BYTES_FIELD = Pattern.compile("\"bytes\":\"([^\"]*)\"");

    /** 解析 match 消息为结构化元组（兼容 rg 互斥形态与 nop-rg 双字段形态）。 */
    private static List<Msg> parseMatches(String stdout) {
        List<Msg> msgs = new ArrayList<>();
        for (String line : stdout.split("\n")) {
            if (!line.startsWith("{\"type\":\"match\"")) {
                continue;
            }
            Matcher pm = PATH_OBJ.matcher(line);
            assertTrue(pm.find(), line);
            String path = pm.group(2);
            if (path.startsWith("./")) {
                path = path.substring(2);
            }
            Matcher lm = LINE_OBJ.matcher(line);
            assertTrue(lm.find(), line);
            String linesObj = lm.group(1);
            String text = findField(TEXT_FIELD, linesObj);
            String bytes = findField(BYTES_FIELD, linesObj);
            List<String> subs = new ArrayList<>();
            Matcher sm = SUB_OBJ.matcher(lm.group(4));
            while (sm.find()) {
                String subObj = sm.group(1);
                // submatch 只比 text（bytes 为 nop-rg 超集字段，值由 lines.bytes 覆盖面断言）
                String subText = findField(TEXT_FIELD, subObj);
                subs.add(subText + "@" + sm.group(2) + "-" + sm.group(3));
            }
            msgs.add(new Msg(path, Long.parseLong(lm.group(2)), Long.parseLong(lm.group(3)), text, bytes, subs));
        }
        return msgs;
    }

    private static String findField(Pattern pattern, String obj) {
        Matcher m = pattern.matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    /** 去掉 submatch 中的偏移部分（非 UTF-8 编码下偏移属转码口径，两侧语义不同）。 */
    private static List<String> stripOffsets(List<String> subs) {
        List<String> out = new ArrayList<>();
        for (String s : subs) {
            out.add(s.substring(0, s.indexOf('@')));
        }
        return out;
    }

    private record RunResult(int exitCode, String stdout) {
    }

    private RunResult runNopRg(String... args) throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBytes, true, StandardCharsets.UTF_8));
        try {
            int code = new CommandLine(new NopRgMain()).execute(args);
            System.out.flush();
            return new RunResult(code, outBytes.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
        }
    }

    private RunResult runRg(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("rg");
        cmd.add("--no-ignore");
        cmd.add("--json");
        for (String arg : args) {
            cmd.add(arg);
        }
        cmd.add(".");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).directory(dir.toFile()).start();
        int code = p.waitFor();
        return new RunResult(code, new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private Path buildCorpus() throws Exception {
        // 合法 UTF-8（rg 输出 text 形态）
        Files.writeString(tempDir.resolve("utf8.txt"),
                "hello needle world\n中文 needle 测试\nplain\nemoji \uD83D\uDE00 needle\n");
        // GBK（非法 UTF-8：rg 输出 bytes 形态）
        byte[] gbk = "gbk needle 中文行\nplain 无命中\nneedle 再见\n".getBytes(Charset.forName("GBK"));
        Files.write(tempDir.resolve("gbk.txt"), gbk);
        // Latin-1（非法 UTF-8：高位单字节）
        byte[] latin = "latin needle caf\u00e9\nna\u00efve needle\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(tempDir.resolve("latin1.txt"), latin);
        // Shift-JIS（非法 UTF-8）
        byte[] sjis = "sjis needle テスト行\nneedle 日本語\n".getBytes(Charset.forName("Shift_JIS"));
        Files.write(tempDir.resolve("sjis.txt"), sjis);
        return tempDir;
    }

    /** 默认编码对拍（R3 矩阵）：命中集合、bytes 值、合法行 text 逐字节一致。 */
    @Test
    public void testDefaultEncodingParityWithRg() throws Exception {
        assertTrue(rgAvailable(), "R6 硬门禁：系统 rg 不可用时对拍不算达成（不允许 skipped）");
        Path dir = buildCorpus();
        RunResult ours = runNopRg("--no-ignore", "--json", "needle", dir.toString());
        RunResult theirs = runRg(dir, "needle");
        assertEquals(0, ours.exitCode(), ours.stdout());
        assertEquals(0, theirs.exitCode());

        List<Msg> o = parseMatches(ours.stdout());
        List<Msg> t = parseMatches(theirs.stdout());
        // rg 与 nop-rg 的文件间遍历顺序不同——按 (path, lineNo, absOffset) 排序后对拍
        o.sort(null);
        t.sort(null);
        assertEquals(t.size(), o.size(), "命中条数不一致:\nours=" + o + "\nrg=" + t);
        for (int i = 0; i < t.size(); i++) {
            Msg tm = t.get(i), om = o.get(i);
            assertEquals(tm.path(), om.path(), "path@" + i);
            assertEquals(tm.lineNo(), om.lineNo(), "lineNo@" + i);
            assertEquals(tm.absOffset(), om.absOffset(), "absOffset@" + i);
            assertEquals(tm.submatches(), om.submatches(), "submatches@" + i + " rg=" + tm + " ours=" + om);
            if (tm.bytes() != null) {
                // 非法行：bytes base64 值与 rg 逐字节一致；nop-rg 另有 lossy text（超集，R2）
                assertEquals(tm.bytes(), om.bytes(), "bytes@" + i);
                assertTrue(om.text() != null, "nop-rg 非法行须同时输出 lossy text（双字段超集）");
            } else {
                // 合法行：text 逐字节一致且无 bytes 字段
                assertEquals(tm.text(), om.text(), "text@" + i);
                assertTrue(om.bytes() == null, "合法行不得携带 bytes@" + i);
            }
        }
    }

    /** --encoding gbk 对拍（R3 矩阵）：text 与 rg 转码输出一致。 */
    @Test
    public void testGbkEncodingParityWithRg() throws Exception {
        assertTrue(rgAvailable(), "R6 硬门禁：系统 rg 不可用时对拍不算达成");
        Path dir = buildCorpus();
        RunResult ours = runNopRg("--no-ignore", "--encoding", "gbk", "--json", "needle", dir.toString());
        RunResult theirs = runRg(dir, "--encoding", "gbk", "needle");
        assertEquals(0, ours.exitCode(), ours.stdout());
        assertEquals(0, theirs.exitCode());

        List<Msg> o = parseMatches(ours.stdout());
        List<Msg> t = parseMatches(theirs.stdout());
        o.sort(null);
        t.sort(null);
        assertEquals(t.size(), o.size(), "命中条数不一致");
        for (int i = 0; i < t.size(); i++) {
            Msg tm = t.get(i), om = o.get(i);
            assertEquals(tm.path(), om.path(), "path@" + i);
            assertEquals(tm.lineNo(), om.lineNo(), "lineNo@" + i);
            // absOffset 有意不对拍：rg 在 --encoding 下报告转码后流的偏移（实测 gbk.txt 行3
            // rg=37 vs 原始文件 31），nop-rg 裁定保持原始文件字节偏移（与 bytes 字段引用
            // 原始字节一致，更有用）——分歧已裁定并写入设计文档决策 3（plan 2575 R11）
            assertEquals(stripOffsets(tm.submatches()), stripOffsets(om.submatches()),
                    "submatch 文本@" + i + "（submatch 偏移在非 UTF-8 编码下同属转码口径，不比）");
            // GBK 转码 text 一致（rg 为纯 text 形态；nop-rg 对非法 UTF-8 区间另附 bytes，超集）
            if (tm.text() != null) {
                assertEquals(tm.text(), om.text(), "gbk text@" + i + " rg=" + tm + " ours=" + om);
            }
        }
    }
}
