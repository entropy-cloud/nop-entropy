package io.nop.rg.core.coordinator;

import io.nop.rg.core.search.ByteScan;
import io.nop.rg.core.search.PreparedLiteral;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则回退搜索器（vision 成功标准 4 / plan 2264 COORD-02）。
 *
 * <p>契约：独立接口形态（不实现 {@code ByteSearchStrategy} 的字节域签名）；
 * 整文件 UTF-8 解码（无阈值/分块，非法字节走 replacement char——与 rg 纯字节域的已知偏差，
 * 对比 corpus 以纯 ASCII 规避）；{@code ignoreCase} 用 {@link Pattern#CASE_INSENSITIVE}
 * 且不带 UNICODE_CASE（ASCII 折叠，与标量折叠实现语义一致）。
 * 字符偏移经 UTF-16→字节偏移桥接映射回文件字节域。
 */
public class RegexSearcher {

    private final Pattern pattern;
    private final Charset charset; // regex 解码与 prefilter 编码统一口径（plan 2575 Phase E2）
    // Wave 5 item 19（plan 2574 Phase C）：候选行两级路径的必须字面量（null = 回退整文件路径）
    private final PreparedLiteral requiredLiteral;

    // 测试接缝（同包可见）：钉住路径选择（接线验证）
    int fullScanCount;
    int candidateScanCount;

    public RegexSearcher(String regex, boolean ignoreCase) {
        this(regex, ignoreCase, StandardCharsets.UTF_8);
    }

    /**
     * @param charset 整文件/候选行解码与 prefilter 必须字面量编码的统一口径
     *                （plan 2575 Phase E2，设计文档 03 决策 3；UTF-8 外的 charset 下
     *                偏移映射按该 charset 实际字节长度计算——plan 2575 R5）
     */
    public RegexSearcher(String regex, boolean ignoreCase, Charset charset) {
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        this.pattern = Pattern.compile(regex, flags);
        this.charset = charset == null ? StandardCharsets.UTF_8 : charset;
        String literal = null;
        if (!RegexLiteralExtractor.mayMatchAcrossLines(regex)) {
            literal = RegexLiteralExtractor.extract(regex);
        }
        this.requiredLiteral = literal != null
                ? PreparedLiteral.compile(literal.getBytes(this.charset), ignoreCase)
                : null;
    }

    /**
     * 在整文件映射上执行正则搜索。
     *
     * @return 按起始偏移升序的字节域命中区间
     */
    public List<ByteSpan> findAll(MemorySegment seg, long size) {
        if (requiredLiteral == null) {
            fullScanCount++;
            return findAllWholeFile(seg, size);
        }
        candidateScanCount++;
        return findAllCandidateLines(seg, size);
    }

    /**
     * 整文件解码路径（原始语义）。
     */
    private List<ByteSpan> findAllWholeFile(MemorySegment seg, long size) {
        String content = decode(seg, size);
        CharsetOffsetMap offsets = new CharsetOffsetMap(content, charset);
        Matcher matcher = pattern.matcher(content);
        List<ByteSpan> spans = new ArrayList<>();
        while (matcher.find()) {
            long byteStart = offsets.byteOffsetOf(matcher.start());
            long byteEnd = offsets.byteOffsetOf(matcher.end());
            spans.add(new ByteSpan(byteStart, byteEnd));
        }
        return spans;
    }

    /**
     * 候选行两级路径（Wave 5 item 19，rg Confirmed/Candidate 思路）：先用必须字面量在映射段上
     * 按行扫描出候选行，仅对候选行解码并跑 regex 确认。跨行构造已在构造期拒绝
     * （{@link RegexLiteralExtractor}），匹配必落在单行内，逐行确认与整文件路径语义一致。
     */
    private List<ByteSpan> findAllCandidateLines(MemorySegment seg, long size) {
        List<ByteSpan> spans = new ArrayList<>();
        // 行游标：命中按字节升序，行也按升序处理；lineStart 为当前行起点（上一行 lineEnd+1）
        long lineStart = 0;
        long lineEnd = -1;
        long cursor = 0;
        while (true) {
            long pos = requiredLiteral.find(seg, cursor, size);
            if (pos < 0) {
                break;
            }
            if (pos < lineEnd) {
                // 同一行的后续命中：该行已确认，跳过
                cursor = pos + 1;
                continue;
            }
            long nl = ByteScan.findFirstByte(seg, pos, size, (byte) '\n');
            lineEnd = nl < 0 ? size : nl;
            processCandidateLine(seg, lineStart, lineEnd, spans);
            cursor = pos + 1;
            lineStart = lineEnd + 1;
        }
        return spans;
    }

    /**
     * 单候选行解码 + regex 确认；命中区间经行内 UTF-16→字节映射平移回文件字节域。
     */
    private void processCandidateLine(MemorySegment seg, long lineStart, long lineEnd, List<ByteSpan> spans) {
        if (lineEnd <= lineStart) {
            return;
        }
        int len = (int) (lineEnd - lineStart);
        byte[] bytes = seg.asSlice(lineStart, len).toArray(ValueLayout.JAVA_BYTE);
        String line = new String(bytes, charset);
        CharsetOffsetMap offsets = new CharsetOffsetMap(line, charset);
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            spans.add(new ByteSpan(lineStart + offsets.byteOffsetOf(matcher.start()),
                    lineStart + offsets.byteOffsetOf(matcher.end())));
        }
    }

    private String decode(MemorySegment seg, long size) {
        if (size == 0) {
            return "";
        }
        byte[] bytes = seg.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, charset);
    }

    /**
     * 命中区间（文件字节域，end exclusive）。
     */
    public record ByteSpan(long byteStart, long byteEnd) {
    }

    /**
     * UTF-16 char 偏移 → 字节偏移映射（含 end 位置，长度 len+1）。
     *
     * <p>plan 2575 Phase E2（R5）：字节长度按指定 Charset 实际编码计算——UTF-8 下保持
     * 原 {@code Utf8OffsetMap} 语义；GBK 等下码点字节长度不同，写死 UTF-8 长度会整体
     * 漂移命中区间。同一码点的所有 char（代理对）共享同一字节起点。
     */
    static final class CharsetOffsetMap {
        private final int[] charToByte;

        CharsetOffsetMap(String content, Charset charset) {
            int len = content.length();
            this.charToByte = new int[len + 1];
            long bytePos = 0;
            int i = 0;
            while (i < len) {
                int cp = content.codePointAt(i);
                int chars = Character.charCount(cp);
                for (int k = 0; k < chars; k++) {
                    charToByte[i + k] = (int) bytePos;
                }
                bytePos += encodedLength(content, i, chars, charset);
                i += chars;
            }
            charToByte[len] = (int) bytePos;
        }

        int byteOffsetOf(int charIndex) {
            return charToByte[charIndex];
        }

        /**
         * 单个码点在指定 Charset 下的字节长度（UTF-8 走算术快路径；其他 charset 经 encoder）。
         */
        static int encodedLength(String content, int charIndex, int chars, Charset charset) {
            if (charset.equals(StandardCharsets.UTF_8)) {
                int cp = content.codePointAt(charIndex);
                if (cp < 0x80) return 1;
                if (cp < 0x800) return 2;
                if (cp >= 0x10000) return 4;
                return 3;
            }
            ByteBuffer buf = charset.encode(content.substring(charIndex, charIndex + chars));
            return buf.remaining();
        }
    }
}
