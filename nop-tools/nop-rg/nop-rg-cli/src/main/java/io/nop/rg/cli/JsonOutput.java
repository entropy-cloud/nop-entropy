package io.nop.rg.cli;

import io.nop.rg.core.coordinator.FileMatches;
import io.nop.rg.core.coordinator.LineMatch;
import io.nop.rg.core.coordinator.SearchCoordinator;
import io.nop.rg.core.coordinator.Submatch;

import java.io.PrintWriter;
import java.util.Base64;
import java.util.Map;

/**
 * rg --json 兼容消息输出（plan 2264 CLI-02，schema 对齐 rg 15.1.0 实测）：
 *
 * <ul>
 *   <li>begin / match / end 三类消息，无命中的文件零消息；不实现 summary。</li>
 *   <li>end 消息含 binary_offset/binary_end（恒 null）与 stats（本实现豁免，恒缺省）。</li>
 *   <li>lines.text 含行终止符；absolute_offset 为行首字节偏移；
 *       submatch start/end 为行内字节偏移。</li>
 *   <li>字节保真契约（plan 2575 Phase E1）：行内容/命中区间非法 UTF-8 时额外输出
 *       bytes 字段（原始字节 base64；lines 含终止符），合法 UTF-8 输出零增长——
 *       与 rg 15.x text/bytes 互斥形态的有意超集（设计文档 03 决策 2）。</li>
 * </ul>
 */
public final class JsonOutput {

    private JsonOutput() {
    }

    public static void writeMessages(PrintWriter out, Map<String, FileMatches> results) {
        for (Map.Entry<String, FileMatches> entry : results.entrySet()) {
            String path = entry.getKey();
            // 路径转义每文件一次（plan 2276 G3：原先每条 match 消息重复转义）
            String escapedPath = escape(path);
            out.println("{\"type\":\"begin\",\"data\":{\"path\":{\"text\":\"" + escapedPath + "\"}}}");
            for (LineMatch line : entry.getValue().getLines()) {
                writeMatch(out, escapedPath, line);
            }
            out.println("{\"type\":\"end\",\"data\":{\"path\":{\"text\":\"" + escapedPath
                    + "\"},\"binary_offset\":null,\"binary_end\":null}}");
        }
    }

    private static void writeMatch(PrintWriter out, String escapedPath, LineMatch line) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"type\":\"match\",\"data\":{\"path\":{\"text\":\"").append(escapedPath)
                .append("\"},\"lines\":{\"text\":\"").append(escape(line.getLineWithTerminator()));
        // bytes 字段契约（plan 2575 Phase E1）：仅行内容非法 UTF-8 时输出 base64（含行终止符，
        // lineStart..lineEnd），合法 UTF-8 输出零增长；与 rg 15.x 互斥形态的有意超集（设计文档 03 决策 2）
        if (line.getContentBytes() != null) {
            sb.append("\",\"bytes\":\"").append(Base64.getEncoder().encodeToString(line.getContentBytes()));
        }
        sb.append("\"},\"line_number\":").append(line.getLineNumber())
                .append(",\"absolute_offset\":").append(line.getLineStart())
                .append(",\"submatches\":[");
        for (int i = 0; i < line.getSubmatches().size(); i++) {
            Submatch sub = line.getSubmatches().get(i);
            if (i > 0) {
                sb.append(',');
            }
            long startInLine = sub.byteStart() - line.getLineStart();
            long endInLine = sub.byteEnd() - line.getLineStart();
            sb.append("{\"match\":{\"text\":\"").append(escape(sub.text()));
            // bytes 字段契约（plan 2575 Phase E1）：仅命中区间非法 UTF-8 时输出 base64
            if (sub.bytes() != null) {
                sb.append("\",\"bytes\":\"").append(Base64.getEncoder().encodeToString(sub.bytes()));
            }
            sb.append("\"},\"start\":").append(startInLine)
                    .append(",\"end\":").append(endInLine).append('}');
        }
        sb.append("]}}");
        out.println(sb);
    }

    /**
     * JSON 字符串转义（含控制字符与 CJK 直出——与 rg 的 UTF-8 输出一致）。
     */
    static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
