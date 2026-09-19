package io.nop.rg.core.glob;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 编译后的单个 glob 模式（rg/globset 兼容语义，plan 2263 钉死）：
 *
 * <ul>
 *   <li>不含 {@code /} 的模式隐式匹配 basename（{@code *.java} 命中任意深度路径）。</li>
 *   <li>含 {@code /} 的模式匹配完整相对路径；单段内 {@code *} 不跨段。</li>
 *   <li>{@code **} 作为独立路径段时可匹配零个或多个段（模式 a、**、b 三段式命中 {@code a/b} 与 {@code a/x/b}）；
 *       尾部 {@code a/**} 要求 a 下至少还有一个段；独立 {@code **} 命中一切。</li>
 *   <li>{@code ?} 匹配单字符；{@code [...]} 字符类支持范围，前导 {@code !} 或 {@code ^} 反转（二者等价）；
 *       {@code \} 转义下一字符。</li>
 *   <li>空模式不匹配任何路径。</li>
 * </ul>
 *
 * <p>通过 {@link #compile(String)} 获取实例：相同 pattern 复用编译产物（ConcurrentHashMap 缓存，
 * 生命周期与进程一致——CLI 场景模式数量有限且只增不减，不做淘汰）。
 */
public final class CompiledGlob {
    private static final ConcurrentHashMap<String, CompiledGlob> CACHE = new ConcurrentHashMap<>();

    private final String pattern;
    // null 表示空模式（不匹配任何路径）
    private final List<String> components; // "**" 段或单段通配模式
    private final boolean basenameOnly; // 模式不含 '/'

    private CompiledGlob(String pattern, List<String> components, boolean basenameOnly) {
        this.pattern = pattern;
        this.components = components;
        this.basenameOnly = basenameOnly;
    }

    public static CompiledGlob compile(String pattern) {
        return CACHE.computeIfAbsent(pattern, CompiledGlob::doCompile);
    }

    private static CompiledGlob doCompile(String pattern) {
        List<String> components = splitComponents(pattern);
        boolean basenameOnly = !pattern.contains("/") && !pattern.isEmpty();
        return new CompiledGlob(pattern, components, basenameOnly);
    }

    /**
     * 按 '/' 切分为路径段；{@code \} 转义的 '/' 不切分。
     */
    private static List<String> splitComponents(String pattern) {
        List<String> components = new ArrayList<>();
        if (pattern.isEmpty()) {
            return components;
        }
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                current.append(c); // 保留转义符，段匹配时消费
                escaped = true;
                continue;
            }
            if (c == '/') {
                components.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        components.add(current.toString());
        return components;
    }

    public String getPattern() {
        return pattern;
    }

    /**
     * 判断相对路径（'/' 分隔）是否命中本模式。
     */
    public boolean matches(String relativePath) {
        if (components.isEmpty()) {
            return false; // 空模式不匹配
        }
        if (basenameOnly) {
            String base = basename(relativePath);
            return segmentMatches(components.get(0), base);
        }
        List<String> pathComponents = splitPath(relativePath);
        return matchComponents(components, 0, pathComponents, 0);
    }

    private static String basename(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    private static List<String> splitPath(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    /**
     * 组件级带回溯匹配：** 段可消耗 0..n 个路径段（尾部 ** 要求至少 1 个），其余段逐一单段匹配。
     */
    private static boolean matchComponents(List<String> pats, int pi, List<String> paths, int si) {
        while (pi < pats.size()) {
            String pc = pats.get(pi);
            boolean isDoubleStar = pc.equals("**");
            boolean isLast = pi == pats.size() - 1;
            if (isDoubleStar && isLast) {
                // 尾部 **：要求至少消耗 1 个路径段（rg/globset：a/** 不命中 a 自身）
                return si < paths.size();
            }
            if (isDoubleStar) {
                // 中间 **：尝试消耗 0..(n-si) 个路径段（含 0，如 a/**/b 命中 a/b）
                for (int take = 0; take <= paths.size() - si; take++) {
                    if (matchComponents(pats, pi + 1, paths, si + take)) {
                        return true;
                    }
                }
                return false;
            }
            if (si >= paths.size() || !segmentMatches(pc, paths.get(si))) {
                return false;
            }
            pi++;
            si++;
        }
        // 模式耗尽：路径也必须耗尽
        return si == paths.size();
    }

    /**
     * 单段匹配：两指针贪心 + 星号回溯；? 任意单字符；[...] 字符类；\ 转义。
     */
    static boolean segmentMatches(String pat, String value) {
        int p = 0, v = 0;
        int starP = -1, starV = -1; // 最近一个 '*' 的位置与回溯点
        while (v < value.length()) {
            if (p < pat.length()) {
                char pc = pat.charAt(p);
                if (pc == '\\') {
                    // 转义字符：字面匹配下一字符
                    if (p + 1 < pat.length() && pat.charAt(p + 1) == value.charAt(v)) {
                        p += 2;
                        v++;
                        continue;
                    }
                } else if (pc == '*') {
                    starP = p;
                    starV = v;
                    p++; // 先尝试 * 匹配空串
                    continue;
                } else if (pc == '?') {
                    p++;
                    v++;
                    continue;
                } else if (pc == '[') {
                    int close = findClassEnd(pat, p);
                    if (close < 0) {
                        // 未闭合的 '[' 按字面字符处理
                        if (value.charAt(v) == '[') {
                            p++;
                            v++;
                            continue;
                        }
                    } else if (classMatches(pat, p, close, value.charAt(v))) {
                        p = close + 1;
                        v++;
                        continue;
                    }
                } else if (pc == value.charAt(v)) {
                    p++;
                    v++;
                    continue;
                }
            }
            // 失配：回溯到最近的 '*'，让其多吞一个字符
            if (starP >= 0) {
                p = starP + 1;
                starV++;
                v = starV;
            } else {
                return false;
            }
        }
        // 值耗尽：模式剩余部分必须全为 '*'
        while (p < pat.length() && pat.charAt(p) == '*') {
            p++;
        }
        return p == pat.length();
    }

    /**
     * 返回字符类闭合 ']' 的下标（紧随 '[' 或 '!'/'^' 之后的 ']' 视为字面成员），未闭合返回 -1。
     */
    static int findClassEnd(String pat, int start) {
        int i = start + 1;
        if (i < pat.length() && (pat.charAt(i) == '!' || pat.charAt(i) == '^')) {
            i++;
        }
        boolean first = true;
        while (i < pat.length()) {
            char cc = pat.charAt(i);
            if (cc == '\\' && i + 1 < pat.length()) {
                i += 2;
                continue;
            }
            if (cc == ']' && !first) {
                return i;
            }
            first = false;
            i++;
        }
        return -1;
    }

    /**
     * 字符类命中判定：pat[start]=='['，pat[close]==']'；前导 '!' 或 '^' 反转（等价）。
     */
    static boolean classMatches(String pat, int start, int close, char c) {
        int i = start + 1;
        boolean negated = false;
        if (pat.charAt(i) == '!' || pat.charAt(i) == '^') {
            negated = true;
            i++;
        }
        boolean matched = false;
        while (i < close) {
            char lo = pat.charAt(i);
            if (lo == '\\' && i + 1 < close) {
                i++;
                lo = pat.charAt(i);
            }
            if (i + 2 < close && pat.charAt(i + 1) == '-') {
                char hi = pat.charAt(i + 2);
                if (c >= lo && c <= hi) {
                    matched = true;
                }
                i += 3;
            } else {
                if (c == lo) {
                    matched = true;
                }
                i++;
            }
        }
        return matched != negated;
    }
}
