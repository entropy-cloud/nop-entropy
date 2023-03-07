/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.text;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;

import java.io.IOException;
import java.util.Arrays;

import static io.nop.commons.CommonErrors.ARG_LENGTH;
import static io.nop.commons.CommonErrors.ARG_LIMIT;
import static io.nop.commons.CommonErrors.ARG_START;
import static io.nop.commons.CommonErrors.ERR_TEXT_BUF_LIMIT_EXCEED_LENGTH;
import static io.nop.commons.CommonErrors.ERR_TEXT_BUF_START_EXCEED_LIMIT;

/**
 * 相比于StringBuilder提供更多帮助函数
 */
public class MutableString implements CharSequence, Appendable {
    //  static final char[] EMPTY_CHARS = new char[0];

    private char[] buf;
    private int start;
    private int limit;

    public MutableString(char[] buf, int start, int limit) {
        Guard.checkArgument(start >= 0, "buf start");
        Guard.checkArgument(limit >= start, "buf limit");
        this.buf = buf;
        this.start = start;
        this.limit = limit;
    }

    public void accept(int c) {
        append((char) c);
    }

    public MutableString(int bufSize) {
        this(new char[bufSize], 0, 0);
    }

    public MutableString(String str) {
        this(str.toCharArray(), 0, str.length());
    }

    public MutableString() {
        this(32);
    }

    public void clear() {
        this.start = 0;
        this.limit = 0;
    }

    public void resetLimit() {
        this.limit = start;
    }

    public MutableString start(int start) {
        Guard.checkPositionIndex(start, buf.length);

        if (start > limit)
            throw new NopException(ERR_TEXT_BUF_START_EXCEED_LIMIT).param(ARG_START, start).param(ARG_LIMIT, limit);
        this.start = start;
        return this;
    }

    public MutableString limit(int limit) {
        if (limit < start)
            throw new NopException(ERR_TEXT_BUF_START_EXCEED_LIMIT).param(ARG_START, start).param(ARG_LIMIT, limit);
        if (limit > buf.length)
            throw new NopException(ERR_TEXT_BUF_LIMIT_EXCEED_LENGTH).param(ARG_LIMIT, limit).param(ARG_LENGTH,
                    capacity());
        this.limit = limit;
        return this;
    }

    public MutableString skipLeading(int n) {
        start = Math.min(start + n, limit);
        return this;
    }

    public MutableString skipTrailing(int n) {
        limit = Math.min(start, limit - n);
        return this;
    }

    public int start() {
        return start;
    }

    public int limit() {
        return limit;
    }

    public int capacity() {
        return buf.length;
    }

    public int free() {
        return buf.length - limit;
    }

    public int length() {
        return limit - start;
    }

    @Override
    public char charAt(int index) {
        Guard.checkPositionIndex(index, length());
        return buf[start + index];
    }

    public String toString() {
        if (isEmpty())
            return StringHelper.EMPTY_STRING;
        return new String(buf, start, limit - start);
    }

    @Override
    public MutableString subSequence(int start, int end) {
        Guard.checkPositionIndex(start, length());
        return new MutableString(buf, this.start + start, Math.min(this.start + end, this.limit));
    }

    public MutableString subSequence(int start) {
        return subSequence(start, length());
    }

    public String substring(int start, int end) {
        if (start == end)
            return StringHelper.EMPTY_STRING;

        Guard.checkPositionIndex(start, length());
        return new String(buf, this.start + start, Math.min(start + end, this.limit));
    }

    public String substring(int start) {
        return substring(start, length());
    }

    public MutableString upperCase() {
        for (int i = start; i < limit; i++) {
            this.buf[i] = Character.toUpperCase(this.buf[i]);
        }
        return this;
    }

    public boolean isEmpty() {
        return length() <= 0;
    }

    /**
     * 从当前位置开始向后查找字符
     *
     * @param c
     * @return
     */
    public int indexOf(char c) {
        return indexOf(c, 0);
    }

    /**
     * 从指定位置开始向后查找字符
     *
     * @param c
     * @param pos
     * @return
     */
    public int indexOf(char c, int pos) {
        for (int i = start + pos; i < limit; i++) {
            char cc = buf[i];
            if (cc == c)
                return i - start;
        }
        return -1;
    }

    /**
     * 从后向前查找字符
     *
     * @param c
     * @return
     */
    public int lastIndexOf(char c) {
        return lastIndexOf(c, length() - 1);
    }

    /**
     * 从指定位置开始向前查找字符
     *
     * @param c
     * @param pos
     * @return
     */
    public int lastIndexOf(char c, int pos) {
        for (int i = start + pos; i >= start; i--) {
            char cc = buf[i];
            if (cc == c)
                return i - start;
        }
        return -1;
    }

    public int indexOf(String str) {
        return indexOf(str, 0);
    }

    public int indexOf(String str, int pos) {
        Guard.checkPositionIndex(pos, length());

        pos += start;
        if (pos + str.length() > limit)
            return -1;

        for (int i = pos, n = limit - str.length(); i < n; i++) {
            if (_startsWith(str, i))
                return i - start;
        }
        return -1;
    }

    public MutableString trim() {
        return trimLeading().trimTrailing();
    }

    public MutableString trimLeading() {
        int begin = start, end = limit;

        for (; begin < end; begin++) {
            char c = buf[begin];
            if (!Character.isWhitespace(c)) {
                break;
            }
        }
        start = begin;
        return this;
    }

    public MutableString trimTrailing() {
        int begin = start, end = limit;

        for (; end > begin; end--) {
            char c = buf[end - 1];
            if (!Character.isWhitespace(c)) {
                break;
            }
        }
        limit = end;

        return this;
    }

    public MutableString trimLeading(char c) {
        int begin = start, end = limit;

        for (; begin < end; begin++) {
            if (buf[begin] != c)
                break;
        }
        start = begin;
        return this;
    }

    public MutableString trimTrailing(char c) {
        int begin = start, end = limit;

        for (; end > begin; end--) {
            if (buf[end - 1] != c)
                break;
        }
        limit = end;

        return this;
    }

    public int indexOfAny(char[] chars) {
        return indexOfAny(chars, 0);
    }

    public int indexOfAny(char[] chars, int pos) {
        for (int i = start + pos; i < limit; i++) {
            char c = buf[i];
            for (char cc : chars) {
                if (cc == c) {
                    return i - start;
                }
            }
        }
        return -1;
    }

    public int indexOfRange(char first, char last) {
        return indexOfRange(first, last, 0);
    }

    /**
     * 查找在一个区间内的任意一个字符
     *
     * @param first
     * @param last
     * @return
     */
    public int indexOfRange(char first, char last, int pos) {
        for (int i = start + pos; i < limit; i++) {
            char c = buf[i];
            if (c >= first && c <= last) {
                return i - start;
            }
        }
        return -1;
    }

    public boolean startWith(char c) {
        return startWith(c, 0);
    }

    public boolean startWith(char c, int pos) {
        if (pos >= length())
            return false;

        return buf[start + pos] == c;
    }

    public boolean endWith(char c) {
        return startWith(c, length() - 1);
    }

    boolean _matchAny(char[] chars, int pos) {
        char c = buf[pos];
        for (char cc : chars) {
            if (cc == c)
                return true;
        }
        return false;
    }

    public boolean startWithAny(char[] chars) {
        return startWithAny(chars, 0);
    }

    public boolean startWithAny(char[] chars, int pos) {
        if (pos >= length())
            return false;
        return _matchAny(chars, start + pos);
    }

    public boolean endWithAny(char[] chars) {
        return startWithAny(chars, length() - 1);
    }

    public boolean startsWith(CharSequence str) {
        return startsWith(str, 0);
    }

    public boolean startsWith(CharSequence str, int pos) {
        if (pos < 0)
            return false;

        if (pos + str.length() > length())
            return false;
        return _startsWith(str, start + pos);
    }

    boolean _startsWith(CharSequence str, int pos) {
        for (int i = 0, n = str.length(); i < n; i++) {
            char c1 = buf[pos + i];
            char c2 = str.charAt(i);
            if (c1 != c2)
                return false;
        }
        return true;
    }

    public boolean endsWith(CharSequence str) {
        return startsWith(str, length() - str.length());
    }

    public boolean match(CharSequence str) {
        return length() == str.length() && startsWith(str);
    }

    public boolean startsWithIgnoreCase(CharSequence other) {
        return startsWithIgnoreCase(other, 0);
    }

    public boolean startsWithIgnoreCase(CharSequence other, int pos) {
        return regionMatches(true, pos, other, 0, other.length());
    }

    public boolean regionMatches(boolean ignoreCase, int toffset, CharSequence other, int ooffset, int len) {
        if ((ooffset < 0) || (toffset < 0) || (toffset > limit - len) || (ooffset > other.length() - len)) {
            return false;
        }

        char ta[] = buf;
        int to = toffset + start;
        int po = ooffset;

        while (len-- > 0) {
            char c1 = ta[to++];
            char c2 = other.charAt(po++);
            if (c1 != c2) {
                if (ignoreCase) {
                    char u1 = Character.toUpperCase(c1);
                    char u2 = Character.toUpperCase(c2);
                    if (u1 == u2) {
                        continue;
                    }
                    if (Character.toLowerCase(u1) == Character.toLowerCase(u2)) {
                        continue;
                    }
                }
                return false;
            }
        }
        return true;
    }

    public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin) {
        if (srcBegin < 0) {
            throw new StringIndexOutOfBoundsException(srcBegin);
        }
        if (srcEnd > length()) {
            throw new StringIndexOutOfBoundsException(srcEnd);
        }
        if (srcBegin > srcEnd) {
            throw new StringIndexOutOfBoundsException(srcEnd - srcBegin);
        }
        System.arraycopy(buf, start + srcBegin, dst, dstBegin, srcEnd - srcBegin);
    }

    public MutableString append(CharSequence str) {
        return append(str, 0, str.length());
    }

    public MutableString append(CharSequence str, int begin, int end) {
        if (str == null)
            str = "null";

        Guard.checkArgument(end >= begin, "invalid range", begin, end);

        int len = end - begin;
        ensureCapacity(limit + len);
        if (str instanceof String) {
            ((String) str).getChars(begin, end, buf, limit);
        } else {
            for (int i = begin, j = limit; i < end; i++, j++) {
                buf[j] = str.charAt(i);
            }
        }
        limit += len;
        return this;
    }

    private void ensureCapacity(int minimumCapacity) {
        if (minimumCapacity - buf.length > 0) {
            buf = Arrays.copyOf(buf, newCapacity(minimumCapacity));
        }
    }

    private int newCapacity(int minCapacity) {
        int newCapacity = (buf.length << 1) + 2;
        if (newCapacity > minCapacity + 1024 * 1024) {
            newCapacity = minCapacity + 1024 * 1024;
        } else if (newCapacity < minCapacity) {
            newCapacity = minCapacity + 16;
        }
        return newCapacity;
    }

    public MutableString append(char c) {
        ensureCapacity(limit + 1);
        buf[limit++] = c;
        return this;
    }

    public MutableString escapeJava(char c) {
        try {
            StringHelper.escapeCharTo(c, StringHelper.JAVA_ESCAPE_CHARS, StringHelper.JAVA_ESCAPE_STRS, this);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return this;
    }

    public MutableString escapeJava(String str) {
        try {
            StringHelper.escapeTo(str, StringHelper.JAVA_ESCAPE_CHARS, StringHelper.JAVA_ESCAPE_STRS, this);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return this;
    }

    public MutableString escapeXml(char c) {
        try {
            StringHelper.escapeCharTo(c, StringHelper.XML_ESCAPE_CHARS, StringHelper.XML_ESCAPE_STRS, this);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return this;
    }

    public MutableString escapeXml(String str) {
        try {
            StringHelper.escapeTo(str, StringHelper.XML_ESCAPE_CHARS, StringHelper.JAVA_ESCAPE_STRS, this);
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return this;
    }

    public MutableString replace(char oldChar, char newChar) {
        for (int i = start; i < limit; i++) {
            if (buf[i] == oldChar)
                buf[i] = newChar;
        }
        return this;
    }

    public MutableString replace(int begin, int end, CharSequence str) {
        if (end > limit)
            end = limit;
        int len = str.length();
        int newCount = limit + len - (end - begin);

        ensureCapacity(newCount);

        System.arraycopy(buf, end, buf, begin + len, limit - end);
        if (str instanceof String) {
            ((String) str).getChars(0, len, buf, begin);
        } else {
            for (int i = 0; i < len; i++) {
                buf[begin + i] = str.charAt(i);
            }
        }
        limit = newCount;
        return this;
    }

    public MutableString insert(int offset, CharSequence str) {
        if ((offset < 0) || (offset > length()))
            throw new StringIndexOutOfBoundsException(offset);
        if (str == null)
            str = "null";
        int len = str.length();
        ensureCapacity(limit + len);
        offset = start + limit;
        System.arraycopy(buf, offset, buf, offset + len, limit - offset);
        if (str instanceof String) {
            ((String) str).getChars(0, len, buf, offset);
        } else {
            for (int i = 0; i < len; i++) {
                buf[offset + i] = str.charAt(i);
            }
        }
        limit += len;
        return this;
    }

    public MutableString deleteCharAt(int pos) {
        return delete(pos, pos + 1);
    }

    public MutableString delete(int begin, int end) {
        if (end > limit)
            end = limit;
        int newCount = limit - (end - begin);

        System.arraycopy(buf, end, buf, begin, limit - end);
        limit = newCount;
        return this;
    }

    public MutableString replace(String subStr, String repStr) {
        int pos = 0;
        do {
            int i = this.indexOf(subStr, pos);
            if (i < 0)
                return this;

            replace(i, i + subStr.length(), repStr);
            pos = i + repStr.length();
        } while (true);
    }

    public MutableString deleteWhitespace() {
        for (int i = start, n = limit(); i < n; i++) {
            char c = charAt(i);
            if (Character.isWhitespace(c)) {
                deleteCharAt(i);
                i--;
                n--;
            }
        }
        return this;
    }
}