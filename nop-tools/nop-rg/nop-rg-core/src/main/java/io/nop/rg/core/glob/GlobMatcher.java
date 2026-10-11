package io.nop.rg.core.glob;

import java.util.ArrayList;
import java.util.List;

/**
 * glob 集合级正/负规则匹配（rg {@code -g} 语义，plan 2263 钉死）：
 *
 * <ul>
 *   <li>包含正则（非 {@code !} 前缀）时：任一正则命中且无负则命中 → 保留。</li>
 *   <li>仅负则时：全集减去负则命中（对齐 rg 单独使用排除 glob 的行为）。</li>
 *   <li>空集合：保留全部（等价无过滤）。</li>
 * </ul>
 */
public class GlobMatcher {
    private static final GlobMatcher ACCEPT_ALL = new GlobMatcher(new ArrayList<>(), new ArrayList<>());

    private final List<CompiledGlob> includes;
    private final List<CompiledGlob> excludes;

    private GlobMatcher(List<CompiledGlob> includes, List<CompiledGlob> excludes) {
        this.includes = includes;
        this.excludes = excludes;
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
        for (String glob : globs) {
            if (glob.isEmpty()) {
                continue; // 空规则不构成任何匹配语义，跳过（rg 对空 glob 同样忽略）
            }
            if (glob.startsWith("!")) {
                excludes.add(CompiledGlob.compile(glob.substring(1)));
            } else {
                includes.add(CompiledGlob.compile(glob));
            }
        }
        return new GlobMatcher(includes, excludes);
    }

    /**
     * 判断相对路径是否被本规则集合保留。
     */
    public boolean accept(String relativePath) {
        if (excluded(relativePath)) {
            return false;
        }
        if (includes.isEmpty()) {
            return true; // 无正则（含仅负则/空集合）：全集减负则
        }
        for (CompiledGlob include : includes) {
            if (include.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }

    public boolean excluded(String relativePath) {
        for (CompiledGlob exclude : excludes) {
            if (exclude.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }
}
