package io.nop.find.cli;

import io.nop.find.core.model.FindMatch;

import java.io.PrintWriter;
import java.util.List;

/**
 * nop-find 输出渲染。
 *
 * <ul>
 *   <li>文本：每行 {@code SCORE\tRELATIVE_PATH}，score 升序（结果已排序，渲染不再重排）。</li>
 *   <li>JSON：{@code {"matches":[{"path":...,"relativePath":...,"score":N},...],"count":N}}。</li>
 * </ul>
 *
 * <p>文本行打印相对 root 的路径（模糊查找器的直接消费场景：喂给编辑器/复制粘贴）；
 * JSON 同时给出绝对 path 与 relativePath 两种形态。
 */
public final class ResultPrinter {

    private ResultPrinter() {
    }

    public static void printText(PrintWriter out, List<FindMatch> matches) {
        StringBuilder sb = new StringBuilder(256);
        for (FindMatch match : matches) {
            sb.setLength(0);
            sb.append(match.getScore()).append('\t').append(match.getRelativePath()).append('\n');
            out.print(sb);
        }
    }

    public static void printJson(PrintWriter out, List<FindMatch> matches) {
        out.print("{\"matches\":[");
        boolean first = true;
        for (FindMatch match : matches) {
            if (!first) {
                out.print(',');
            }
            first = false;
            out.print("{\"path\":\"" + escape(match.getAbsolutePath().toString())
                    + "\",\"relativePath\":\"" + escape(match.getRelativePath())
                    + "\",\"score\":" + match.getScore() + '}');
        }
        out.print("],\"count\":" + matches.size() + "}\n");
    }

    /**
     * JSON 字符串转义（仅处理控制字符、引号与反斜杠；路径中的非 ASCII 原样输出为 UTF-8）。
     */
    static String escape(String value) {
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            String replacement = switch (c) {
                case '"' -> "\\\"";
                case '\\' -> "\\\\";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                case '\t' -> "\\t";
                default -> c < 0x20 ? String.format("\\u%04x", (int) c) : null;
            };
            if (replacement != null) {
                if (sb == null) {
                    sb = new StringBuilder(value.length() + 16).append(value, 0, i);
                }
                sb.append(replacement);
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? value : sb.toString();
    }
}