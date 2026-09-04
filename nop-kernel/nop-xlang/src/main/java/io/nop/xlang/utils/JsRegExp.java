/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import java.util.regex.Pattern;

/**
 * JavaScript RegExp 全局对象兼容：XScript 中 {@code new RegExp(pat)} 解析为本类（未 import 时 fallback）。
 * <p>
 * RegExp 与 java.util.regex.Pattern 不能简单别名：
 * <ul>
 *     <li>JS flags 是 "i"/"g"/"m" 字符串，Java flags 是 int 常量（CASE_INSENSITIVE 等）</li>
 *     <li>JS test/exec 与 Java matcher.find()/matches() 命名/返回不同</li>
 *     <li>Pattern 是 final 类无法继承</li>
 * </ul>
 * <p>
 * 本类包装 Pattern，提供 JS 风格 test()/exec() 方法。单参 Object 构造器（解决多 1 参冲突）。
 */
public class JsRegExp {

    private final Pattern pattern;

    public JsRegExp(Object pattern) {
        this.pattern = compile(pattern, null);
    }

    public JsRegExp(Object pattern, Object flags) {
        this.pattern = compile(pattern, flags);
    }

    private static Pattern compile(Object pat, Object flags) {
        String p = String.valueOf(pat);
        if (flags == null) {
            return Pattern.compile(p);
        }
        int intFlags = parseFlags(String.valueOf(flags));
        return Pattern.compile(p, intFlags);
    }

    /** JS flags 字符串解析为 Java int flags */
    private static int parseFlags(String flags) {
        int result = 0;
        for (char c : flags.toCharArray()) {
            switch (c) {
                case 'i':
                    result |= Pattern.CASE_INSENSITIVE;
                    break;
                case 'm':
                    result |= Pattern.MULTILINE;
                    break;
                case 's':
                    result |= Pattern.DOTALL;
                    break;
                case 'x':
                    result |= Pattern.COMMENTS;
                    break;
                case 'u':
                    result |= Pattern.UNICODE_CASE;
                    break;
            }
        }
        return result;
    }

    public boolean test(String s) {
        return pattern.matcher(s).find();
    }

    public boolean matches(String s) {
        return pattern.matcher(s).matches();
    }

    /** JS exec(input)：返回第一个匹配结果（null 表示无匹配） */
    public String exec(String s) {
        java.util.regex.Matcher m = pattern.matcher(s);
        if (m.find()) {
            return m.group();
        }
        return null;
    }

    public Pattern pattern() {
        return pattern;
    }
}