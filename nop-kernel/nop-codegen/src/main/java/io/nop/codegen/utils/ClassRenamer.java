package io.nop.codegen.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassRenamer {
    // 匹配public类定义
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("(public\\s+class\\s+)(\\w+)");

    // 只匹配public构造函数
    private static final Pattern PUBLIC_CTOR_PATTERN =
            Pattern.compile("(public\\s+)(\\w+)\\s*\\(([^)]*)\\)");

    /**
     * 替换类名和public构造函数名为 类名_base
     *
     * @param source Java源代码
     * @return 替换后的代码
     */
    public static String renameClassAndConstructors(String source) {
        // 1. 提取并替换类名定义
        Matcher classMatcher = CLASS_PATTERN.matcher(source);
        if (!classMatcher.find()) {
            return source; // 不是public类直接返回
        }

        String className = classMatcher.group(2);
        String newClassName = className + "_base";
        String result = classMatcher.replaceFirst(classMatcher.group(1) + newClassName);

        // 2. 替换所有public构造函数
        Matcher ctorMatcher = PUBLIC_CTOR_PATTERN.matcher(result);
        StringBuilder sb = new StringBuilder();

        while (ctorMatcher.find()) {
            // 精确匹配类名的构造函数
            if (ctorMatcher.group(2).equals(className)) {
                ctorMatcher.appendReplacement(sb,
                        ctorMatcher.group(1) + newClassName + "(" + ctorMatcher.group(3) + ")");
            } else {
                ctorMatcher.appendReplacement(sb, ctorMatcher.group(0)); // 保持其他内容不变
            }
        }
        ctorMatcher.appendTail(sb);

        return sb.toString();
    }
}