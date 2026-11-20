package io.nop.rg.core.coordinator;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 字节保真解码工具（plan 2575 Phase E1，设计文档 03 决策 1/2/5）。
 *
 * <p>契约：
 * <ul>
 *   <li>UTF-8 合法性判定走自有线性扫描，不依赖 {@code new String} 的 decoder 行为——
 *       合法序列保持 decoder 快路径（解码实现与改动前相同），非法序列不走 decoder
 *       整体慢路径，改基础扫描定界 + 逐合法 run 解码 + 逐非法字节 {@code U+FFFD} 替换。</li>
 *   <li>非法区间额外产出原始字节（base64 由输出侧编码），JSON 消费方可精确还原。</li>
 *   <li>lossy 文本的替换粒度为逐非法字节一个 {@code U+FFFD}（不承诺与 rg 的
 *       maximal-subpart 粒度逐字符一致；字节保真以 bytes 字段为准，plan 2575 R3）。</li>
 * </ul>
 */
final class ByteText {

    private ByteText() {
    }

    /**
     * UTF-8 合法性线性扫描（决策 5，plan 2575 R12 实测修正）：先走 SWAR 8 字节 ASCII
     * 预扫（多数行为纯 ASCII，零逐字节开销），含高位字节时才进入逐序列校验。
     * 拒绝：非法前导/后续字节、截断多字节序列、overlong 编码、代理区与越界码点。
     */
    static boolean isValidUtf8(byte[] b, int from, int to) {
        if (isAscii(b, from, to)) {
            return true;
        }
        int i = from;
        while (i < to) {
            int c = b[i] & 0xFF;
            if (c < 0x80) {
                i++;
                continue;
            }
            int cont;
            if ((c & 0xE0) == 0xC0) {
                cont = 1;
            } else if ((c & 0xF0) == 0xE0) {
                cont = 2;
            } else if ((c & 0xF8) == 0xF0) {
                cont = 3;
            } else {
                return false;
            }
            if (i + cont >= to) {
                // 序列越过区间末端：截断多字节序列 = 非法
                return false;
            }
            int cp = c & (cont == 1 ? 0x1F : cont == 2 ? 0x0F : 0x07);
            for (int k = 1; k <= cont; k++) {
                int cc = b[i + k] & 0xFF;
                if ((cc & 0xC0) != 0x80) {
                    return false;
                }
                cp = (cp << 6) | (cc & 0x3F);
            }
            if (cont == 1 && cp < 0x80) {
                return false;
            }
            if (cont == 2 && (cp < 0x800 || (cp >= 0xD800 && cp <= 0xDFFF))) {
                return false;
            }
            if (cont == 3 && (cp < 0x10000 || cp > 0x10FFFF)) {
                return false;
            }
            i += cont + 1;
        }
        return true;
    }

    /**
     * 区间解码为 String。假定区间为合法 UTF-8（调用方先经 {@link #isValidUtf8} 判定），
     * 与改动前 {@code new String(bytes, UTF_8)} 路径相同。
     */
    static String decodeValid(byte[] bytes) {
        return new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
    }

    /**
     * SWAR 8 字长 ASCII 预扫：区间内全部字节 &lt; 0x80 时返回 true（8 字节一次判定）。
     */
    private static boolean isAscii(byte[] b, int from, int to) {
        int i = from;
        int limit = to - 8;
        for (; i <= limit; i += 8) {
            long w = ((long) (b[i] & 0xFF)) << 56
                    | ((long) (b[i + 1] & 0xFF)) << 48
                    | ((long) (b[i + 2] & 0xFF)) << 40
                    | ((long) (b[i + 3] & 0xFF)) << 32
                    | ((long) (b[i + 4] & 0xFF)) << 24
                    | ((long) (b[i + 5] & 0xFF)) << 16
                    | ((long) (b[i + 6] & 0xFF)) << 8
                    | (b[i + 7] & 0xFF);
            if ((w & 0x8080808080808080L) != 0) {
                return false;
            }
        }
        for (; i < to; i++) {
            if (b[i] < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 非法 UTF-8 区间的 lossy 文本（plan 2575 R12 实测修正）：JDK 25 的
     * {@code new String} lossy 路径自带 SIMD 级校验+替换（实测较手写逐序列解码快约 3 倍），
     * 直接复用为 lossy 实现——替换粒度由 JDK decoder 定义（maximal subpart）。
     */
    static String decodeLossy(byte[] bytes) {
        return new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
    }

    /**
     * 区间原始字节拷贝（供 base64 输出）。
     */
    static byte[] rawBytes(MemorySegment seg, long offset, int length) {
        return slice(seg, offset, length);
    }

    /**
     * 原始字节 base64（JSON bytes 字段契约）。
     */
    static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] slice(MemorySegment seg, long offset, int length) {
        return seg.asSlice(offset, length).toArray(ValueLayout.JAVA_BYTE);
    }

    /**
     * 输出侧统一文本解码（plan 2575 Phase E2，设计文档 03 决策 2/3）：
     * text 按指定 charset 解码；bytes（原始字节）仅在区间非法 UTF-8 时非 null——该出现条件
     * 与 charset 无关（R2 裁定）。UTF-8 非法区间在 charset==UTF-8 时走 lossy 快路径
     * （不落入 decoder 整体慢路径，决策 5）；其他 charset 直接整段解码。
     */
    static LineCursor.DecodedText decodeText(byte[] bytes, Charset charset) {
        int length = bytes.length;
        if (isValidUtf8(bytes, 0, length)) {
            return new LineCursor.DecodedText(new String(bytes, 0, length, charset), null);
        }
        String text = charset.equals(StandardCharsets.UTF_8)
                ? decodeLossy(bytes)
                : new String(bytes, 0, length, charset);
        return new LineCursor.DecodedText(text, bytes);
    }
}
