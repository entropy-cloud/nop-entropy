package io.nop.codegen.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassRenamer {
    // 匹配public类定义
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("(public\\s+class\\s+)(\\w+)");

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
        return renamePublicConstructors(result, className, newClassName);
    }

    /**
     * 构造函数参数可能包含括号、引号、$ 等字符（如注解参数 @Prop("a(b")、嵌套泛型），
     * 正则无法正确匹配平衡括号，因此采用手工扫描：定位 public 后的类名构造函数，
     * 跳过字符串/字符字面量找到平衡的右括号，参数列表原样保留
     */
    static String renamePublicConstructors(String source, String className, String newClassName) {
        StringBuilder sb = new StringBuilder(source.length());
        int len = source.length();
        int i = 0;
        while (i < len) {
            int pos = source.indexOf("public", i);
            if (pos < 0) {
                sb.append(source, i, len);
                break;
            }
            // 前侧单词边界检查，避免命中 xpublic 之类的子串
            if (pos > 0 && Character.isJavaIdentifierPart(source.charAt(pos - 1))) {
                sb.append(source, i, pos + 6);
                i = pos + 6;
                continue;
            }
            int j = skipBlanks(source, pos + 6);
            String word = readIdentifier(source, j);
            if (!className.equals(word)) {
                // 不是构造函数声明（public class / public static 等），原样复制
                sb.append(source, i, Math.max(j, pos + 6));
                i = Math.max(j, pos + 6);
                continue;
            }
            int k = skipBlanks(source, j + word.length());
            if (k >= len || source.charAt(k) != '(') {
                sb.append(source, i, Math.min(k, len));
                i = Math.min(k, len);
                continue;
            }
            int end = findBalancedParenEnd(source, k);
            if (end < 0) {
                // 括号不平衡的异常输入，保持原样
                sb.append(source, i, len);
                break;
            }
            sb.append(source, i, j);
            sb.append(newClassName);
            sb.append(source, j + word.length(), end + 1);
            i = end + 1;
        }
        return sb.toString();
    }

    static int skipBlanks(String s, int pos) {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos)))
            pos++;
        return pos;
    }

    static String readIdentifier(String s, int pos) {
        int end = pos;
        while (end < s.length() && Character.isJavaIdentifierPart(s.charAt(end)))
            end++;
        return s.substring(pos, end);
    }

    /**
     * 从 openParen（'(' 所在位置）开始查找与之平衡的右括号下标。
     * 跳过字符串/字符字面量（处理转义），避免注解参数中的括号和引号内容干扰配对
     */
    static int findBalancedParenEnd(String s, int openParen) {
        int depth = 0;
        int i = openParen;
        int len = s.length();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
                i++;
            } else if (c == ')') {
                depth--;
                if (depth == 0)
                    return i;
                i++;
            } else if (c == '"' || c == '\'') {
                i = skipStringLiteral(s, i);
            } else {
                i++;
            }
        }
        return -1;
    }

    static int skipStringLiteral(String s, int start) {
        char quote = s.charAt(start);
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote)
                return i + 1;
            // 字符串字面量不允许换行，遇到换行说明不是合法字面量，按普通字符继续
            if (c == '\n')
                return i + 1;
            i++;
        }
        return i;
    }
}
