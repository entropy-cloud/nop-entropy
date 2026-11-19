package io.nop.rg.core.coordinator;

/**
 * Wave 5 item 19（roadmap，plan 2574 Phase C）：从 regex pattern 保守提取「必然出现在任一
 * 匹配中的最长字面量子串」，作为候选行两级 regex 路径的预滤字面量。
 *
 * <p>接受语法子集（显式钉死，见 plan 2574）：仅收集**顶层**（组外）、未被量化的连续字面量
 * run；转义还原。拒绝情形（返回 null 回退整文件路径）：
 * <ul>
 * <li>交替 {@code |}：任意分支可绕过任意 run，无法保守提取；</li>
 * <li>组内 run：组可能被整体量化（{@code (ab)?}）而不可靠；</li>
 * <li>跨行风险构造：{@code \s \S \R \n \r \D \W \H \V \X \p \P}、否定字符类 {@code [^…]}、
 *     八进制/十六进制转义（可能编码 LF）、以及 pattern 中的真实换行字符——凡可能匹配
 *     LF/CR 的构造一律拒绝（{@code .} 无 DOTALL 不匹配 LF，安全）；</li>
 * <li>字母/数字转义（{@code \d、\1、backslash-u 系列} 等语义不可静态判定）终止当前 run。</li>
 * </ul>
 * 量化符 {@code * + ? {…}} 削掉当前 run 的末字符（被量化原子不再必然出现）。
 */
public final class RegexLiteralExtractor {

    private RegexLiteralExtractor() {
    }

    /**
     * 返回必然出现在任一匹配中的最长字面量子串；无法保守提取时返回 null。
     */
    public static String extract(String regex) {
        StringBuilder best = new StringBuilder();
        StringBuilder run = new StringBuilder();
        int depth = 0;
        int i = 0;
        int len = regex.length();
        while (i < len) {
            char c = regex.charAt(i);
            if (c == '\\' && i + 1 < len) {
                char next = regex.charAt(i + 1);
                if (isWordChar(next)) {
                    // \d \w \s \b \1 及 backslash-u 系列：语义不可静态判定；其中 \s \n 等已由跨行检查拒绝，
                    // 其余（\d \w \1 等）终止当前 run
                    keepBest(best, run);
                    i += 2;
                    continue;
                }
                // 转义标点/空白：字面量该字符
                run.append(next);
                i += 2;
                continue;
            }
            if (c == '[') {
                // 字符类：内容非字面量，终止 run；整个类跳过（含转义与首 ']' 字面量）
                keepBest(best, run);
                i = skipClass(regex, i);
                continue;
            }
            if (c == '|') {
                // 交替：任意 run 都可能被绕过，整体放弃（保守子集裁定）
                return null;
            }
            if (c == '(') {
                keepBest(best, run);
                depth++;
                i++;
                continue;
            }
            if (c == ')') {
                keepBest(best, run);
                if (depth > 0) {
                    depth--;
                }
                i++;
                continue;
            }
            if (c == '^' || c == '$' || c == '.') {
                keepBest(best, run);
                i++;
                continue;
            }
            if (c == '*' || c == '+' || c == '?') {
                chopLast(run);
                i++;
                continue;
            }
            if (c == '{') {
                // 保守按量化符处理（{n,m}）；非量化 '{' 字面量场景在此子集内被放弃
                chopLast(run);
                i = skipQuantifierBraces(regex, i);
                continue;
            }
            if (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029') {
                // 真实行终止字符：跨行风险（上游检查已拒绝，此处兜底）
                return null;
            }
            if (depth == 0) {
                run.append(c);
            }
            i++;
        }
        keepBest(best, run);
        return best.length() > 0 ? best.toString() : null;
    }

    /**
     * pattern 是否含跨行匹配构造（否定类、行终止族 shorthand、真实行终止字符）。
     * 含任一则候选行预滤不可用（匹配可能跨 LF/CR），必须回退整文件路径。
     */
    public static boolean mayMatchAcrossLines(String regex) {
        int i = 0;
        int len = regex.length();
        while (i < len) {
            char c = regex.charAt(i);
            if (c == '\\') {
                if (i + 1 >= len) {
                    return false;
                }
                char next = regex.charAt(i + 1);
                if (next == 's' || next == 'S' || next == 'R' || next == 'n' || next == 'r'
                        || next == 'D' || next == 'W' || next == 'H' || next == 'V'
                        || next == 'X' || next == 'p' || next == 'P') {
                    return true;
                }
                // 八进制（\012 = LF）与 \xhh：可能编码行终止符，保守拒绝
                if (next >= '0' && next <= '9') {
                    return true;
                }
                if (next == 'x') {
                    return true;
                }
                i += 2;
                continue;
            }
            if (c == '[' && i + 1 < len && regex.charAt(i + 1) == '^') {
                return true;
            }
            if (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029') {
                return true;
            }
            if (c == '[') {
                i = skipClass(regex, i);
                continue;
            }
            i++;
        }
        return false;
    }

    private static void keepBest(StringBuilder best, StringBuilder run) {
        if (run.length() > best.length()) {
            best.setLength(0);
            best.append(run);
        }
        run.setLength(0);
    }

    private static void chopLast(StringBuilder run) {
        if (run.length() > 0) {
            run.setLength(run.length() - 1);
        }
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /**
     * 返回字符类结束 ']' 之后的位置（含首 ']' 字面量与转义处理）。
     */
    private static int skipClass(String regex, int start) {
        int i = start + 1;
        int len = regex.length();
        // 首字符 ']' 为字面量（POSIX 惯例）
        if (i < len && regex.charAt(i) == ']') {
            i++;
        }
        while (i < len) {
            char c = regex.charAt(i);
            if (c == '\\' && i + 1 < len) {
                i += 2;
                continue;
            }
            if (c == ']') {
                return i + 1;
            }
            i++;
        }
        return len;
    }

    private static int skipQuantifierBraces(String regex, int start) {
        int end = regex.indexOf('}', start);
        return end < 0 ? regex.length() : end + 1;
    }
}
