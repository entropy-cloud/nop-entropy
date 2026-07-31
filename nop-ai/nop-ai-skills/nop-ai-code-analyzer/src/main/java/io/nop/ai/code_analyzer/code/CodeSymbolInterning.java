package io.nop.ai.code_analyzer.code;

import io.nop.commons.util.CollectionHelper;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 字符串驻留（interning）工具，用于压缩 CodeFileInfo 数据模型中的重复字符串引用。
 * 从 CodeFileInfo.java 中提取，CodeFileInfo 及其符号类型统一委托本类完成 interning。
 */
public class CodeSymbolInterning {

    private CodeSymbolInterning() {
    }

    public static String internString(String str) {
        return str != null ? str.intern() : null;
    }

    public static Set<String> internStringSet(Set<String> set) {
        if (set == null) return null;
        Set<String> ret = new LinkedHashSet<>();
        for (String s : set) {
            ret.add(s != null ? s.intern() : null);
        }
        return ret;
    }

    public static Map<String, String> internStringMap(Map<String, String> map) {
        if (map == null) return null;
        Map<String, String> ret = CollectionHelper.newLinkedHashMap(map.size());
        map.forEach((k, v) -> {
            ret.put(internString(k), internString(v));
        });
        return ret;
    }
}
