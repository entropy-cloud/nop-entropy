package io.nop.rg.core.coordinator;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
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
 * 且不带 UNICODE_CASE（ASCII 折叠，与 {@link FoldingByteSearcher} 语义一致）。
 * 字符偏移经 UTF-16→字节偏移桥接映射回文件字节域。
 */
public class RegexSearcher {

    private final Pattern pattern;

    public RegexSearcher(String regex, boolean ignoreCase) {
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        this.pattern = Pattern.compile(regex, flags);
    }

    /**
     * 在整文件映射上执行正则搜索。
     *
     * @return 按起始偏移升序的字节域命中区间
     */
    public List<ByteSpan> findAll(MemorySegment seg, long size) {
        String content = decode(seg, size);
        Utf8OffsetMap offsets = new Utf8OffsetMap(content);
        Matcher matcher = pattern.matcher(content);
        List<ByteSpan> spans = new ArrayList<>();
        while (matcher.find()) {
            long byteStart = offsets.byteOffsetOf(matcher.start());
            long byteEnd = offsets.byteOffsetOf(matcher.end());
            spans.add(new ByteSpan(byteStart, byteEnd));
        }
        return spans;
    }

    private String decode(MemorySegment seg, long size) {
        if (size == 0) {
            return "";
        }
        byte[] bytes = seg.asSlice(0, size).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 命中区间（文件字节域，end exclusive）。
     */
    public record ByteSpan(long byteStart, long byteEnd) {
    }

    /**
     * UTF-16 char 偏移 → UTF-8 字节偏移映射（含 end 位置，长度 len+1）。
     */
    static final class Utf8OffsetMap {
        private final int[] charToByte;

        Utf8OffsetMap(String content) {
            int len = content.length();
            this.charToByte = new int[len + 1];
            long bytePos = 0;
            for (int i = 0; i < len; ) {
                int cp = content.codePointAt(i);
                int chars = Character.charCount(cp);
                // 同一码点的所有 char（代理对）共享同一字节起点
                for (int k = 0; k < chars; k++) {
                    charToByte[i + k] = (int) bytePos;
                }
                bytePos += utf8Length(cp);
                i += chars;
            }
            charToByte[len] = (int) bytePos;
        }

        int byteOffsetOf(int charIndex) {
            return charToByte[charIndex];
        }

        static int utf8Length(int codePoint) {
            if (codePoint < 0x80) return 1;
            if (codePoint < 0x800) return 2;
            if (codePoint < 0x10000) return 3;
            return 4;
        }
    }
}
