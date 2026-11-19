package io.nop.rg.core.glob;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * glob 集合级正/负规则匹配（rg {@code -g} 语义，plan 2263 钉死）：
 *
 * <ul>
 *   <li>包含正则（非 {@code !} 前缀）时：任一正则命中且无负则命中 → 保留。</li>
 *   <li>仅负则时：全集减去负则命中（对齐 rg 单独使用排除 glob 的行为）。</li>
 *   <li>空集合：保留全部（等价无过滤）。</li>
 * </ul>
 *
 * <p>Wave 5 item 20（roadmap，plan 2574 Phase D，globset 策略分类思路）：编译期按模式形态
 * 分派策略——{@code *.ext} 扩展名后缀集、无通配符字面量集（basename/path 各一集）走
 * O(1) 集合快路径，其余模式走原有两指针慢路径。快路径语义与慢路径逐例一致（分类单测 +
 * 对拍 fuzz 钉死），include/exclude 语义与优先级不变。
 */
public class GlobMatcher {
    private static final GlobMatcher ACCEPT_ALL = new GlobMatcher(
            new ArrayList<>(), new ArrayList<>(),
            Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());

    // 慢路径之外的三个快路径族：扩展名后缀（*.ext）、字面量 basename、字面量完整路径
    private final Set<String> includeExtensions;
    private final Set<String> includeLiteralBasenames;
    private final Set<String> includeLiteralPaths;
    private final Set<String> excludeExtensions;
    private final Set<String> excludeLiteralBasenames;
    private final Set<String> excludeLiteralPaths;
    private final List<CompiledGlob> includes;
    private final List<CompiledGlob> excludes;

    private GlobMatcher(List<CompiledGlob> includes, List<CompiledGlob> excludes,
                        Set<String> includeExtensions, Set<String> includeLiteralBasenames,
                        Set<String> includeLiteralPaths,
                        Set<String> excludeExtensions, Set<String> excludeLiteralBasenames,
                        Set<String> excludeLiteralPaths) {
        this.includes = includes;
        this.excludes = excludes;
        this.includeExtensions = includeExtensions;
        this.includeLiteralBasenames = includeLiteralBasenames;
        this.includeLiteralPaths = includeLiteralPaths;
        this.excludeExtensions = excludeExtensions;
        this.excludeLiteralBasenames = excludeLiteralBasenames;
        this.excludeLiteralPaths = excludeLiteralPaths;
    }

    public static GlobMatcher acceptAll() {
        return ACCEPT_ALL;
    }

    /**
     * 从 {@code -g} 风格规则列表构造：{@code !} 前缀为排除规则，其余为包含规则。
     */
    public static GlobMatcher of(List<String> globs) {
        List<CompiledGlob> includes = new ArrayList<>();
        List<CompiledGlob> excludes = new ArrayList<>();
        Set<String> incExt = new HashSet<>();
        Set<String> incLitBase = new HashSet<>();
        Set<String> incLitPath = new HashSet<>();
        Set<String> excExt = new HashSet<>();
        Set<String> excLitBase = new HashSet<>();
        Set<String> excLitPath = new HashSet<>();
        for (String glob : globs) {
            if (glob.isEmpty()) {
                continue; // 空规则不构成任何匹配语义，跳过（rg 对空 glob 同样忽略）
            }
            boolean exclude = glob.startsWith("!");
            String pattern = exclude ? glob.substring(1) : glob;
            Classification cls = classify(pattern);
            if (exclude) {
                addTo(cls, pattern, excludes, excExt, excLitBase, excLitPath);
            } else {
                addTo(cls, pattern, includes, incExt, incLitBase, incLitPath);
            }
        }
        return new GlobMatcher(includes, excludes,
                incExt, incLitBase, incLitPath, excExt, excLitBase, excLitPath);
    }

    private static void addTo(Classification cls, String pattern, List<CompiledGlob> slow,
                              Set<String> exts, Set<String> litBase, Set<String> litPath) {
        switch (cls.kind()) {
            case EXTENSION -> exts.add(pattern.substring(1)); // '*' 之后的 ".ext" 后缀
            case LITERAL_BASENAME -> litBase.add(pattern);
            case LITERAL_PATH -> litPath.add(pattern);
            case SLOW -> slow.add(CompiledGlob.compile(pattern));
        }
    }

    /**
     * 判断相对路径是否被本规则集合保留。
     */
    public boolean accept(String relativePath) {
        if (excluded(relativePath)) {
            return false;
        }
        if (includes.isEmpty() && includeExtensions.isEmpty()
                && includeLiteralBasenames.isEmpty() && includeLiteralPaths.isEmpty()) {
            return true; // 无正则（含仅负则/空集合）：全集减负则
        }
        return matchesAny(relativePath, includeExtensions, includeLiteralBasenames,
                includeLiteralPaths, includes);
    }

    public boolean excluded(String relativePath) {
        return matchesAny(relativePath, excludeExtensions, excludeLiteralBasenames,
                excludeLiteralPaths, excludes);
    }

    private static boolean matchesAny(String relativePath, Set<String> extensions,
                                      Set<String> literalBasenames, Set<String> literalPaths,
                                      List<CompiledGlob> slow) {
        String base = basename(relativePath);
        for (String ext : extensions) {
            // CompiledGlob 语义：'*' 可匹配空串 → basename 以 ".ext" 结尾即命中（含 basename == ".ext"）
            if (base.endsWith(ext)) {
                return true;
            }
        }
        if (literalBasenames.contains(base)) {
            return true;
        }
        if (literalPaths.contains(relativePath)) {
            return true;
        }
        for (CompiledGlob glob : slow) {
            if (glob.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }

    private static String basename(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    // ---- 编译期策略分类（Wave 5 item 20）----

    enum Kind {EXTENSION, LITERAL_BASENAME, LITERAL_PATH, SLOW}

    record Classification(Kind kind) {
    }

    /**
     * 模式形态分类（同包测试接缝）：
     * <ul>
     * <li>{@code *.<suffix>}（'*' + 以 '.' 开头的无通配符后缀，且不含 '/'）→ EXTENSION；</li>
     * <li>无任何通配符/转义且不含 '/' → LITERAL_BASENAME；</li>
     * <li>无任何通配符/转义且含 '/' → LITERAL_PATH；</li>
     * <li>其余 → SLOW。</li>
     * </ul>
     */
    static Classification classify(String pattern) {
        if (pattern.length() > 1 && pattern.indexOf('*') == 0 && !pattern.contains("/")
                && !containsWildcard(pattern, 1) && pattern.charAt(1) == '.') {
            return new Classification(Kind.EXTENSION);
        }
        boolean hasSlash = pattern.indexOf('/') >= 0;
        if (!containsWildcard(pattern, 0)) {
            return new Classification(hasSlash ? Kind.LITERAL_PATH : Kind.LITERAL_BASENAME);
        }
        return new Classification(Kind.SLOW);
    }

    /**
     * 自 from 起是否含通配符/转义字符（* ? [ \ {——brace 为字面量但形态罕见，归 SLOW 保守处理）。
     */
    private static boolean containsWildcard(String s, int from) {
        for (int i = Math.max(0, from); i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '*' || c == '?' || c == '[' || c == '\\' || c == '{') {
                return true;
            }
        }
        return false;
    }
}
