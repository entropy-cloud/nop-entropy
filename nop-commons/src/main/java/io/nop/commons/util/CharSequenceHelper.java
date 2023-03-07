/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util;

public class CharSequenceHelper {

    public static char[] toCharArray(CharSequence str) {
        if (str instanceof String)
            return str.toString().toCharArray();

        char[] ret = new char[str.length()];
        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            ret[i] = c;
        }
        return ret;
    }

    public static void appendIndent(StringBuilder seq, int n) {
        for (int i = 0; i < n; i++) {
            seq.append(' ');
        }
    }

    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() <= 0;
    }

    public static boolean isBlank(CharSequence str) {
        if (str == null)
            return true;

        return !containsText(str);
    }

    private static boolean containsText(CharSequence str) {
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static int skipWhitespace(CharSequence str, int i) {
        for (int n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            if (!StringHelper.isWhitespace(c))
                return i;
        }
        return i;
    }

    public static CharSequence trim(CharSequence str) {
        int n = str.length();
        int i, j;
        for (i = 0; i < n; i++) {
            char c = str.charAt(i);
            if (!Character.isWhitespace(c)) {
                break;
            }
        }
        for (j = n - 1; j > i; j--) {
            char c = str.charAt(j);
            if (!Character.isWhitespace(c))
                break;
        }
        if (i == 0 && j == n - 1)
            return str;

        return str.subSequence(i, j + 1);
    }

    public static boolean equals(CharSequence str, CharSequence str2) {
        if (str == str2)
            return true;
        if (str == null || str2 == null)
            return false;
        if (str.length() != str2.length())
            return false;

        for (int i = 0, n = str.length(); i < n; i++) {
            if (str.charAt(i) != str2.charAt(i))
                return false;
        }
        return true;
    }

    public static boolean startsWith(CharSequence str, CharSequence sub) {
        if (str == null || str.length() < sub.length())
            return false;

        for (int i = 0, n = sub.length(); i < n; i++) {
            if (str.charAt(i) != sub.charAt(i))
                return false;
        }
        return true;
    }

    public static boolean endsWith(CharSequence str, CharSequence sub) {
        if (str == null || str.length() < sub.length())
            return false;

        for (int i = 0, n = sub.length(), len = str.length(); i < n; i++) {
            if (str.charAt(len - i - 1) != sub.charAt(n - i - 1))
                return false;
        }
        return true;
    }

    public static int indexOf(CharSequence str, int pos, char c) {
        if (str.length() <= pos)
            return -1;
        for (int i = pos, n = str.length(); i < n; i++) {
            if (str.charAt(i) == c)
                return i;
        }
        return -1;
    }

    public static void getChars(CharSequence str, int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        if (str instanceof String) {
            ((String) str).getChars(srcBegin, srcEnd, dst, dstBegin);
            return;
        }

        getChars0(str, srcBegin, srcEnd, dst, dstBegin);
    }

    public static void getChars0(CharSequence str, int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        for (int i = srcBegin; i < srcEnd; i++) {
            int idx = i - srcBegin + dstBegin;
            dst[idx] = str.charAt(i);
        }
    }

    public static char[] getChars(CharSequence str, int srcBegin, int srcEnd) {
        char[] ret = new char[srcEnd - srcBegin];
        getChars(str, srcBegin, srcEnd, ret, 0);
        return ret;
    }

    public static boolean matchChars(CharSequence str, int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        if (srcEnd - srcBegin > dst.length - dstBegin)
            return false;

        for (int i = srcBegin; i < srcEnd; i++) {
            int idx = i - srcBegin + dstBegin;
            if (dst[idx] != str.charAt(i))
                return false;
        }
        return true;
    }

    public static boolean matchChars(CharSequence str, int srcBegin, char[] data) {
        return matchChars(str, srcBegin, srcBegin + data.length, data, 0);
    }

    public static void replace(StringBuilder sb, String subStr, String repStr) {
        int pos = 0;
        do {
            int i = sb.indexOf(subStr, pos);
            if (i < 0)
                return;

            sb.replace(i, i + subStr.length(), repStr);
            pos = i + repStr.length();
        } while (true);
    }
}
