/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

/**
 * 展示层脱敏工具：供「脱敏展示字段」（计算属性，日常查询与页面使用）派生 masked 值。
 * 平台裁定：脱敏是 meta/xmeta 层概念——真正的敏感字段以 not-pub 等发布控制默认不暴露，
 * ORM 层不做静默改写（见 ai-dev/design/nop-orm-scope-and-non-goals.md §四）。
 * 本工具只做字符串变换，不感知实体与存储。
 */
public class MaskHelper {
    public static final char DEFAULT_MASK_CHAR = '*';

    private MaskHelper() {
    }

    /**
     * 保留前 n 位与后 m 位，中间遮盖。n+m 覆盖全长时不遮盖（原文过短，全保留或全遮盖由调用方裁定）。
     */
    public static String keepFirstNLastM(String value, int n, int m) {
        return transform(value, n, m, false);
    }

    /**
     * 遮盖前 n 位与后 m 位，保留中间。
     */
    public static String maskFirstNLastM(String value, int n, int m) {
        return transform(value, n, m, true);
    }

    /**
     * 仅遮盖 [from, to) 区间（0 起下标，区间越界部分截断），其余保留。
     */
    public static String maskFromXToY(String value, int from, int to) {
        return maskRange(value, from, to, DEFAULT_MASK_CHAR);
    }

    /**
     * 遮盖全部字符，保留原长度。
     */
    public static String maskAll(String value) {
        if (StringHelper.isEmpty(value))
            return value;
        return repeat(DEFAULT_MASK_CHAR, value.length());
    }

    /**
     * 遮盖全部字符并固定展示长度（不泄露原长度），常用于替代 maskAll 的场景。
     */
    public static String maskFixed(String value, int displayLength) {
        if (displayLength <= 0)
            displayLength = 1;
        if (StringHelper.isEmpty(value))
            return value;
        return repeat(DEFAULT_MASK_CHAR, displayLength);
    }

    private static String transform(String value, int n, int m, boolean maskEdges) {
        if (StringHelper.isEmpty(value))
            return value;
        if (n < 0)
            n = 0;
        if (m < 0)
            m = 0;
        int len = value.length();
        if (n + m >= len) {
            // 区间覆盖全长：保留语义下原样返回；遮盖语义下全遮
            return maskEdges ? repeat(DEFAULT_MASK_CHAR, len) : value;
        }
        StringBuilder sb = new StringBuilder(len);
        if (maskEdges) {
            sb.append(repeat(DEFAULT_MASK_CHAR, n));
            sb.append(value, n, len - m);
            sb.append(repeat(DEFAULT_MASK_CHAR, m));
        } else {
            sb.append(value, 0, n);
            sb.append(repeat(DEFAULT_MASK_CHAR, len - n - m));
            sb.append(value, len - m, len);
        }
        return sb.toString();
    }

    private static String maskRange(String value, int from, int to, char maskChar) {
        if (StringHelper.isEmpty(value))
            return value;
        int len = value.length();
        int start = Math.max(0, Math.min(from, len));
        int end = Math.max(start, Math.min(to, len));
        if (start == end)
            return value;
        StringBuilder sb = new StringBuilder(len);
        sb.append(value, 0, start);
        sb.append(repeat(maskChar, end - start));
        sb.append(value, end, len);
        return sb.toString();
    }

    private static String repeat(char c, int count) {
        if (count <= 0)
            return "";
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}
