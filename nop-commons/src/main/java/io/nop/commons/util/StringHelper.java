/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import com.google.common.base.CaseFormat;
import com.google.common.base.Utf8;
import io.nop.api.core.annotations.core.Description;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.lang.Deterministic;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.CommonConstants;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.text.FormatCheckers;
import io.nop.commons.text.RawText;
import io.nop.commons.text.XMLChar;
import io.nop.commons.text.tokenizer.SimpleTextReader;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.random.IRandom;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static io.nop.commons.CommonConfigs.CFG_UTILS_STRING_MAX_PAD_LEN;
import static io.nop.commons.CommonConfigs.CFG_UTILS_STRING_MAX_REPEAT_LEN;
import static io.nop.commons.CommonErrors.ARG_LENGTH;
import static io.nop.commons.CommonErrors.ARG_PARAM_NAME;
import static io.nop.commons.CommonErrors.ARG_QUERY;
import static io.nop.commons.CommonErrors.ARG_STR;
import static io.nop.commons.CommonErrors.ERR_TEXT_ILLEGAL_HEX_STRING;
import static io.nop.commons.CommonErrors.ERR_TEXT_INVALID_UNICODE;
import static io.nop.commons.CommonErrors.ERR_TEXT_INVALID_UUID_RANGE;
import static io.nop.commons.CommonErrors.ERR_UTILS_DUPLICATE_PARAM_NAME_IS_NOT_ALLOWED_IN_SIMPLE_QUERY;
import static io.nop.commons.CommonErrors.ERR_UTILS_INVALID_QUOTED_STRING;

/**
 * 所有关于String的常用帮助函数。为方便在EL表达式中调用，所有参数都允许为null。 为了与EL表达式集成，第一个参数一般情况下应该是String或者CharSequence类型。
 */
public class StringHelper extends ApiStringHelper {
    static final Logger LOG = LoggerFactory.getLogger(StringHelper.class);

    /**
     * Eclipse的缺省编码。
     * <p>
     * 微软所用的ANSI代码页1252(CP1252)对应于ISO 8859-1字符集(即Latin-1字符集，但CP1252对Latin-1有扩展，
     * 其中编码128~159也被定义了字符，这是与Latin-1字符集不同之处)，用于英语和大多数欧洲语言(西班牙语和各种日耳曼/斯堪的纳维亚语)， 而IBM所用的OEM代码页932(CP932)对应于Shift
     * JIS字符集(但CP932对Shift JIS有扩展；另外，对应的微软ANSI代码页为CP943， 也对Shift JIS有扩展)，用于日本字符。
     * <p>
     * <p>
     * Default encoding for unknown byte encodings of native files (at least it's better than to rely on a platform
     * dependent encoding for legacy stuff ...)
     */
    // public static final Charset CHARSET_1252 = Charset.forName("CP1252");

    public static final byte[] EMPTY_BYTES = new byte[0];
    public static final String[] EMPTY_STRINGS = new String[0];

    public static final String EMPTY_STRING = "";

    static class Lazy {
        static final int s_maxRepeatLen = CFG_UTILS_STRING_MAX_REPEAT_LEN.get();

        static final int s_maxPadLen = CFG_UTILS_STRING_MAX_PAD_LEN.get();
    }

    public static String safeToString(Object value) {
        if (value == null)
            return "null";

        String str;
        try {
            str = value.toString();
        } catch (Throwable e) {
            str = "<" + e.getClass().getName() + ">";
            LOG.error("nop.err.commons.to-string-fail", str);
        }
        return str;
    }

    @Deterministic
    public static Charset toCharset(String encoding) {
        if (encoding == null || encoding.isEmpty())
            return CHARSET_UTF8;
        if (ENCODING_UTF8.equals(encoding))
            return CHARSET_UTF8;
        return Charset.forName(encoding);
    }

    public static final char[] HTML_ESCAPE_CHARS = new char[]{'<', '>', '&', 160, '"', '\'', '\r', '\n'};
    public static final String[] HTML_ESCAPE_STRS = new String[]{"&lt;", "&gt;", "&amp;", "&#160;", "&quot;",
            "&apos;", "", "<br/>"};

    @Deterministic
    public static String escapeHtml(String str) {
        return escape(str, HTML_ESCAPE_CHARS, HTML_ESCAPE_STRS);
    }

    public static final char[] XML_ESCAPE_CHARS = new char[]{'<', '>', '&', 160, '"', '\''};
    public static final String[] XML_ESCAPE_STRS = new String[]{"&lt;", "&gt;", "&amp;", "&#160;", "&quot;",
            "&apos;"};

    /**
     * xml转义，>被转换为"&gt;"
     */
    @Deterministic
    public static String escapeXml(String str) {
        return escape(str, XML_ESCAPE_CHARS, XML_ESCAPE_STRS);
    }

    static final char[] XML_VALUE_ESCAPE_CHARS = new char[]{'<', '>', '&', 160};
    static final String[] XML_VALUE_ESCAPE_STRS = new String[]{"&lt;", "&gt;", "&amp;", "&#160;"};

    /**
     * 不处理引号
     */
    @Deterministic
    public static String escapeXmlValue(String str) {
        return escape(str, XML_VALUE_ESCAPE_CHARS, XML_VALUE_ESCAPE_STRS);
    }

    static final char[] XML_ATTR_ESCAPE_CHARS = new char[]{'<', '>', '&', 160, '"',};
    static final String[] XML_ATTR_ESCAPE_STRS = new String[]{"&lt;", "&gt;", "&amp;", "&#160;", "&quot;",};

    /**
     * 不处理单引号
     */
    @Deterministic
    public static String escapeXmlAttr(String str) {
        return escape(str, XML_ATTR_ESCAPE_CHARS, XML_ATTR_ESCAPE_STRS);
    }

    public static int escapeCharTo(char c, char[] fromChars, String[] toStrs, Appendable buf) throws IOException {
        int index = _indexOf(fromChars, c);
        if (index < 0) {
            buf.append(c);
            return 1;
        } else {
            String toStr = toStrs[index];
            buf.append(toStr);
            return toStr.length();
        }
    }

    public static int escapeJsonCharTo(char c, Appendable buf) throws IOException {
        return escapeCharTo(c, JSON_ESCAPE_CHARS, JSON_ESCAPE_STRS, buf);
    }

    /**
     * 将特殊字符替换为指定字符串后输出
     *
     * @param str       需要处理的字符串
     * @param fromChars 需要转义的特殊字符
     * @param toStrs    长度与fromChars相同，一一对应的指定特殊字符对应的转义字符串。如果转义字符串为空串，则相当于从原字符串中删除特殊字符
     * @param buf       输出到此对象中
     * @return 输出的总字符数
     * @throws IOException
     */
    public static int escapeTo(CharSequence str, char[] fromChars, String[] toStrs, Appendable buf) throws IOException {
        if (str == null || str.length() == 0 || fromChars == null || fromChars.length == 0)
            return 0;

        if (toStrs == null || fromChars.length != toStrs.length)
            throw new IllegalArgumentException("escape fromChars and toChars length not match");

        int count = 0;
        int sz = str.length();
        int prev = 0;
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);

            int idx = _indexOf(fromChars, ch);
            if (idx >= 0) {
                if (i > prev) {
                    buf.append(str, prev, i);
                    count += i - prev;
                }
                prev = i + 1;
                String toStr = toStrs[idx];
                buf.append(toStr);
                count += toStr.length();
            }
        }
        if (prev < sz) {
            buf.append(str, prev, sz);
            count += sz - prev;
        }
        return count;
    }

    public static int escapeJsonTo(CharSequence str, Appendable buf) throws IOException {
        if (str == null) {
            buf.append("null");
            return 4;
        }
        return escapeTo(str, JSON_ESCAPE_CHARS, JSON_ESCAPE_STRS, buf);
    }

    public static int escapeXmlTo(CharSequence str, Appendable buf) throws IOException {
        return escapeTo(str, XML_ESCAPE_CHARS, XML_ESCAPE_STRS, buf);
    }

    public static int escapeXmlValueTo(CharSequence str, Appendable buf) throws IOException {
        return escapeTo(str, XML_VALUE_ESCAPE_CHARS, XML_VALUE_ESCAPE_STRS, buf);
    }

    public static int escapeXmlAttrTo(CharSequence str, Appendable buf) throws IOException {
        return escapeTo(str, XML_ATTR_ESCAPE_CHARS, XML_ATTR_ESCAPE_STRS, buf);
    }

    @Deterministic
    public static String unescapeXml(String str) {
        if (isEmpty(str)) {
            return str;
        }
        StringBuilder buf = null;
        int len = str.length();
        int len3 = len - 3;
        int len4 = len - 4;
        int len5 = len - 5;
        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (ch == '&' && i < len3) {
                int j = i;
                char ch1 = str.charAt(i + 1);
                switch (ch1) {
                    case 'l':
                        if (str.charAt(i + 2) == 't' && str.charAt(i + 3) == ';') {
                            i += 3;
                            if (buf == null) {
                                buf = new StringBuilder(len3);
                                if (j > 0) {
                                    buf.append(str.substring(0, j));
                                }
                            }
                            buf.append('<');
                        } else if (buf != null) {
                            buf.append('&');
                        }
                        break;
                    case 'g':
                        if (str.charAt(i + 2) == 't' && str.charAt(i + 3) == ';') {
                            i += 3;
                            if (buf == null) {
                                buf = new StringBuilder(len3);
                                if (j > 0) {
                                    buf.append(str.substring(0, j));
                                }
                            }
                            buf.append('>');
                        } else if (buf != null) {
                            buf.append('&');
                        }
                        break;
                    case 'a':
                        if (i < len4 && str.charAt(i + 2) == 'm' && str.charAt(i + 3) == 'p' && str.charAt(i + 4) == ';') {
                            i += 4;
                            if (buf == null) {
                                buf = new StringBuilder(len4);
                                if (j > 0) {
                                    buf.append(str.substring(0, j));
                                }
                            }
                            buf.append('&');
                        } else if (i < len5 && str.charAt(i + 2) == 'p' && str.charAt(i + 3) == 'o'
                                && str.charAt(i + 4) == 's' && str.charAt(i + 5) == ';') {
                            i += 5;
                            if (buf == null) {
                                buf = new StringBuilder(len5);
                                if (j > 0) {
                                    buf.append(str.substring(0, j));
                                }
                            }
                            buf.append('\'');
                        } else if (buf != null) {
                            buf.append('&');
                        }
                        break;
                    case 'q':
                        if (i < len5 && str.charAt(i + 2) == 'u' && str.charAt(i + 3) == 'o' && str.charAt(i + 4) == 't'
                                && str.charAt(i + 5) == ';') {
                            i += 5;
                            if (buf == null) {
                                buf = new StringBuilder(len5);
                                if (j > 0) {
                                    buf.append(str.substring(0, j));
                                }
                            }
                            buf.append('\"');
                        } else if (buf != null) {
                            buf.append('&');
                        }
                        break;
                    case '#':
                        if (i < len5) {
                            int cc = _unescapeXml(str.charAt(i + 2), str.charAt(i + 3), str.charAt(i + 4),
                                    str.charAt(i + 5));
                            if (cc >= 0) {
                                i += 5;
                                if (buf == null) {
                                    buf = new StringBuilder(len5);
                                    if (j > 0) {
                                        buf.append(str.substring(0, j));
                                    }
                                }
                                buf.append((char) cc);
                            } else if (buf != null) {
                                buf.append('&');
                            }
                        } else if (buf != null) {
                            buf.append('&');
                        }
                        break;
                    default:
                        if (buf != null) {
                            buf.append('&');
                        }
                        break;
                }
            } else if (buf != null) {
                buf.append(ch);
            }
        }
        if (buf != null) {
            return buf.toString();
        }
        return str;
    }

    static int _unescapeXml(char c2, char c3, char c4, char c5) {
        if (c2 >= '0' && c2 <= '9') {
            int c = c2 - '0';
            if (c3 >= '0' && c3 <= '9') {
                c = c * 10 + (c3 - '0');
                if (c4 >= '0' && c4 <= '9') {
                    c = c * 10 + (c4 - '0');
                    if (c5 == ';')
                        return c;
                }
            }
        }
        return -1;
    }

    @Deterministic
    public static String escapeJavadoc(String str) {
        return str.replace("/*", "/ *").replace("*/", "* /").replace("\\u002a/", "\\u002a /")
                .replace("*\\u002f", "* \\u002f").replace("\\u002a\\u002f", "\\u002a \\u002f");
    }

    @Deterministic
    public static String unescapeJava(String str) {
        if (isEmpty(str)) {
            return str;
        }
        StringBuilder buf = null;
        int i, len = str.length() - 1;
        for (i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (ch == '\\') {
                int j = i;
                i++;
                ch = str.charAt(i);
                switch (ch) {
                    case '\\':
                        ch = '\\';
                        break;
                    case '\"':
                        ch = '\"';
                        break;
                    case '\'':
                        ch = '\'';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'u': {
                        // tell cpd to start ignoring code - CPD-OFF
                        // Read the xxxx
                        int sValue = 0;
                        for (int k = 1; k <= 4; k++) {
                            i++;
                            char aChar = str.charAt(i);
                            switch (aChar) {
                                case '0':
                                case '1':
                                case '2':
                                case '3':
                                case '4':
                                case '5':
                                case '6':
                                case '7':
                                case '8':
                                case '9':
                                    sValue = (sValue << 4) + aChar - '0';
                                    break;
                                case 'a':
                                case 'b':
                                case 'c':
                                case 'd':
                                case 'e':
                                case 'f':
                                    sValue = (sValue << 4) + 10 + aChar - 'a';
                                    break;
                                case 'A':
                                case 'B':
                                case 'C':
                                case 'D':
                                case 'E':
                                case 'F':
                                    sValue = (sValue << 4) + 10 + aChar - 'A';
                                    break;
                                default:
                                    throw new NopException(ERR_TEXT_INVALID_UNICODE).param(ARG_STR, limitLen(str, i, 10));
                            }

                        }
                        if (sValue <= 255 && sValue >= 0)
                            throw new NopException(ERR_TEXT_INVALID_UNICODE).param(ARG_STR, limitLen(str, i, 10));
                        ch = (char) sValue;
                        // resume CPD analysis - CPD-ON
                        break;
                    }
                    default:
                        // 不做处理就是跳过\
                }
                if (buf == null) {
                    buf = new StringBuilder(len);
                    if (j > 0) {
                        buf.append(str.substring(0, j));
                    }
                }
                buf.append(ch);
            } else if (buf != null) {
                buf.append(ch);
            }
        }
        if (buf != null) {
            if (i == len)
                buf.append(str.charAt(len));
            return buf.toString();
        }
        return str;
    }

    /**
     * Escapes any values it finds into their String form. So a tab becomes the characters '\\' and 't'.
     *
     * @param str String to escape values in
     * @return String with escaped values
     * @throws NullPointerException if str is null
     */
    @Deterministic
    public static String escapeUnicode(String str) {
        // improved with code from cybertiger@cyberiantiger.org
        // unicode from him, and defaul for < 32's.
        int sz = str.length();
        StringBuilder buffer = new StringBuilder(2 * sz);
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);

            // handle unicode
            if (ch > 0xfff) {
                buffer.append("\\u" + Integer.toHexString(ch));
            } else if (ch > 0xff) {
                buffer.append("\\u0" + Integer.toHexString(ch));
            } else if (ch > 0x7f) {
                buffer.append("\\u00" + Integer.toHexString(ch));
            } else if (ch < 32) {
                switch (ch) {
                    case '\b':
                        buffer.append('\\');
                        buffer.append('b');
                        break;
                    case '\n':
                        buffer.append('\\');
                        buffer.append('n');
                        break;
                    case '\t':
                        buffer.append('\\');
                        buffer.append('t');
                        break;
                    case '\f':
                        buffer.append('\\');
                        buffer.append('f');
                        break;
                    case '\r':
                        buffer.append('\\');
                        buffer.append('r');
                        break;
                    default:
                        if (ch > 0xf) {
                            buffer.append("\\u00" + Integer.toHexString(ch));
                        } else {
                            buffer.append("\\u000" + Integer.toHexString(ch));
                        }
                        break;
                }
            } else {
                switch (ch) {
                    case '\'':
                        buffer.append('\\');
                        buffer.append('\'');
                        break;
                    case '"':
                        buffer.append('\\');
                        buffer.append('"');
                        break;
                    case '\\':
                        buffer.append('\\');
                        buffer.append('\\');
                        break;
                    default:
                        buffer.append(ch);
                        break;
                }
            }
        }
        return buffer.toString();
    }

    static final char[] SQL_ESCAPE_CHARS = new char[]{'\''};
    static final String[] SQL_ESCAPE_STRS = new String[]{"''"};

    static final char[] SQL_ESCAPE_CHARS_SLASH = new char[]{'\'', '\\'};
    static final String[] SQL_ESCAPE_STRS_SLASH = new String[]{"''", "\\\\"};

    /**
     * 对SQL中的不安全的字符进行处理
     *
     * @param str
     * @return
     */
    @Deterministic
    public static String escapeSql(String str, boolean escapeSlash) {
        if (escapeSlash) {
            return escape(str, SQL_ESCAPE_CHARS_SLASH, SQL_ESCAPE_STRS_SLASH);
        } else {
            return escape(str, SQL_ESCAPE_CHARS, SQL_ESCAPE_STRS);
        }
    }

    public static final char[] JSON_ESCAPE_CHARS = new char[]{'\b', '\r', '\n', '\t', '\f', '"', '\\'};
    public static final String[] JSON_ESCAPE_STRS = new String[]{"\\b", "\\r", "\\n", "\\t", "\\f", "\\\"", "\\\\"};

    /**
     * 按照JSON规范，单引号不能被转义
     */
    @Deterministic
    public static String escapeJson(String str) {
        return escape(str, JSON_ESCAPE_CHARS, JSON_ESCAPE_STRS);
    }

    @Deterministic
    public static String unescapeJson(String str) {
        return unescapeJava(str);
    }

    @Deterministic
    public static boolean isSpaceInLine(int ch) {
        // 专门调整了判断顺序, 快速判断为false后不再逐个比较
        return ch <= ' ' && (ch == ' ' || ch == '\t' || ch == '\f' || ch == '\b');
    }

    static final Pattern LINE_PATTERN = Pattern.compile("\\r?\\n");

    @Deterministic
    public static String[] splitToLines(String str) {
        if (str == null || str.isEmpty())
            return EMPTY_STRINGS;

        return LINE_PATTERN.split(str);
    }

    @Deterministic
    public static String[] splitToArray(String str, char sep) {
        List<String> list = split(str, sep);
        if (list == null)
            return null;
        return list.toArray(new String[list.size()]);
    }

    @Deterministic
    public static String joinArray(Object list, String sep) {
        if (list == null)
            return null;
        int len = Array.getLength(list);
        if (len == 0)
            return "";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i != 0)
                sb.append(sep);
            Object o = Array.get(list, i);
            sb.append(o);
        }
        return sb.toString();
    }

    @Deterministic
    public static String bytesToHex(byte[] bytes, boolean upper) {
        if (bytes == null)
            return null;
        char[] hexChars = Base16.encode(bytes, upper);
        return new String(hexChars);
    }

    @Deterministic
    public static String bytesToHex(byte[] bytes) {
        return bytesToHex(bytes, false);
    }

    @Deterministic
    public static byte[] hexToBytes(String str) {
        if (str == null)
            return null;

        if (str.isEmpty())
            return EMPTY_BYTES;

        if (startsWithIgnoreCase(str, "0x"))
            str = str.substring(2);

        if (str.length() % 2 != 0)
            throw new NopException(ERR_TEXT_ILLEGAL_HEX_STRING);

        int length = str.length() / 2;
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = decodeHexByte(str, pos);
        }
        return d;
    }

    @Deterministic
    public static String longToHex(long value, int padLen) {
        return leftPad(Long.toHexString(value), padLen, '0');
    }

    @Deterministic
    public static String intToHex(int value, int padLen) {
        return leftPad(Integer.toHexString(value), padLen, '0');
    }

    @Deterministic
    public static boolean isHexChar(int c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    /**
     * Helper to decode half of a hexadecimal number from a string.
     *
     * @param c The ASCII character of the hexadecimal number to decode. Must be in the range {@code [0-9a-fA-F]}.
     * @return The hexadecimal value represented in the ASCII character given, or {@code -1} if the character is
     * invalid.
     */
    @Deterministic
    public static int decodeHexNibble(final char c) {
        // Character.digit() is not used here, as it addresses a larger
        // set of characters (both ASCII and full-width latin letters).
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 0xA;
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 0xA;
        }
        return -1;
    }

    /**
     * Decode a 2-digit hex byte from within a string.
     */
    @Deterministic
    public static byte decodeHexByte(CharSequence s, int pos) {
        int hi = decodeHexNibble(s.charAt(pos));
        int lo = decodeHexNibble(s.charAt(pos + 1));
        if (hi == -1 || lo == -1) {
            throw new IllegalArgumentException(
                    String.format("invalid hex byte '%s' at index %d of '%s'", s.subSequence(pos, pos + 2), pos, s));
        }
        return (byte) ((hi << 4) + lo);
    }

    @Deterministic
    public static String leftPad(String str, int len, char padChar) {
        Guard.checkArgument(len < Lazy.s_maxPadLen, "pad len is too large", len);

        if (str.length() < len) {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0, n = len - str.length(); i < n; i++) {
                sb.append(padChar);
            }
            sb.append(str);
            return sb.toString();
        }
        return str;
    }

    @Deterministic
    public static String padInt(int num, int len) {
        return leftPad(String.valueOf(num), len, '0');
    }

    @Deterministic
    public static String forceLeftPad(String str, int len, char padChar) {
        if (str.length() >= len)
            return str.substring(0, len);
        return leftPad(str, len, padChar);
    }

    @Deterministic
    public static String forceRightPad(String str, int len, char padChar) {
        if (str.length() >= len)
            return str.substring(0, len);
        return rightPad(str, len, padChar);
    }

    @Deterministic
    public static String rightPad(String str, int len, char padChar) {
        Guard.checkArgument(len < Lazy.s_maxPadLen, "pad len is too large", len);

        if (str.length() < len) {
            StringBuilder sb = new StringBuilder(len);
            sb.append(str);
            for (int i = 0, n = len - str.length(); i < n; i++) {
                sb.append(padChar);
            }
            return sb.toString();
        }
        return str;
    }

    @Deterministic
    public static String replaceChars(String str, String searchChars, String replaceChars) {
        if (isEmpty(str) || isEmpty(searchChars)) {
            return str;
        }
        if (replaceChars == null || searchChars.length() != replaceChars.length())
            throw new IllegalArgumentException("replaceChars and searchChars length not match");

        final int strLength = str.length();
        StringBuilder buf = null;
        for (int i = 0; i < strLength; i++) {
            final char ch = str.charAt(i);
            final int index = searchChars.indexOf(ch);
            if (index >= 0) {
                if (buf == null) {
                    buf = new StringBuilder(strLength);
                    buf.append(str, 0, i);
                }
                buf.append(replaceChars.charAt(index));
            } else if (buf != null) {
                buf.append(ch);
            }
        }
        if (buf != null) {
            return buf.toString();
        }
        return str;
    }

    @Deterministic
    public static String replace(String str, String oldSub, String newSub) {
        if (str == null || oldSub == null || oldSub.length() <= 0)
            return str;

        if (newSub == null)
            newSub = "";

        int pos = str.indexOf(oldSub);
        if (pos < 0)
            return str;

        StringBuilder buf = new StringBuilder(oldSub.length() > newSub.length() ? str.length() : str.length() * 2);
        int len = oldSub.length();
        int pos1 = 0;
        do {
            buf.append(str.substring(pos1, pos));
            buf.append(newSub);
            pos1 = pos + len;
            pos = str.indexOf(oldSub, pos1);
        } while (pos >= 0);
        if (pos1 < str.length())
            buf.append(str.substring(pos1));

        return buf.toString();
    }

    @Deterministic
    public static int indexOfAnyChar(CharSequence str, String chars) {
        if (str == null || str.length() <= 0)
            return -1;
        if (chars == null || chars.length() <= 0)
            return -1;
        for (int i = 0, n = str.length(); i < n; i++) {
            if (chars.indexOf(str.charAt(i)) >= 0)
                return i;
        }
        return -1;
    }

    @Deterministic
    public static boolean containsAnyChar(CharSequence str, String chars) {
        return indexOfAnyChar(str, chars) >= 0;
    }

    @Deterministic
    public static boolean isAsciiVarStart(char c) {
        return isAsciiLetter(c) || c == '_';
    }

    @Deterministic
    public static boolean isAsciiVarPart(char c) {
        return isAsciiLetter(c) || c == '_' || isDigit(c);
    }

    @Deterministic
    public static boolean isAllAsciiLetter(String s) {
        if (s == null || s.length() <= 0)
            return false;
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (isAsciiLetter(c))
                return false;
        }
        return true;
    }

    @Deterministic
    public static boolean isWhitespace(int ch) {
        // 专门调整了判断顺序
        return ch <= ' ' && (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f' || ch == '\b');
    }

    @Deterministic
    public static boolean onlyContainsWhitespace(String s) {
        if (s == null)
            return true;
        for (int i = 0, n = s.length(); i < n; i++) {
            if (!isWhitespace(s.charAt(i)))
                return false;
        }
        return true;
    }

    @Deterministic
    public static boolean containsWhitespace(String s) {
        if (s == null)
            return false;

        for (int i = 0, n = s.length(); i < n; i++) {
            if (isWhitespace(s.charAt(i)))
                return true;
        }
        return false;
    }

    /**
     * 只包含字母，数字以及下划线，并且首字母不是数字
     *
     * @param str
     * @return
     */
    @Deterministic
    public static boolean isSafeAsciiToken(String str) {
        if (str == null || str.length() <= 0)
            return false;
        if (isDigit(str.charAt(0)))
            return false;
        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            if (!isDigit(c) && !isAsciiLetter(c) && c != '_')
                return false;
        }
        return true;
    }

    /**
     * 生成一个随机字符串，长度为n, 字符从可选字符集中选择
     *
     * @param n          指定字符串的长度
     * @param allowChars 可选字符集合
     */
    public static String randomString(int n, String allowChars) {
        IRandom rand = MathHelper.random();
        return randomString(n, allowChars, rand);
    }

    public static String randomString(int n, String allowChars, IRandom rand) {
        Guard.checkArgument(n < Lazy.s_maxRepeatLen, "random n is too large", n);

        char[] chars = new char[n];
        int m = allowChars.length();
        for (int i = 0; i < n; i++) {
            chars[i] = allowChars.charAt(rand.nextInt(m));
        }
        return new String(chars);
    }

    @Deterministic
    public static String lowerCase(String str) {
        if (str == null)
            return null;
        return str.toLowerCase(Locale.ROOT);
    }

    @Deterministic
    public static String upperCase(String str) {
        if (str == null)
            return null;
        return str.toUpperCase(Locale.ROOT);
    }

    public static String randomString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        return randomString(length, str);
    }

    public static String randomDigits(int length) {
        String str = "0123456789";
        return randomString(length, str);
    }

    public static String generateUUID() {
        return StringHelper.replace(UUID.randomUUID().toString(), "-", "");
    }

    public static String generateUUID(int len) {
        if (len > 64 || len <= 0)
            throw new NopException(ERR_TEXT_INVALID_UUID_RANGE).param(ARG_LENGTH, len);

        byte[] bytes = new byte[len];
        MathHelper.secureRandom().nextBytes(bytes);
        return StringHelper.bytesToHex(bytes, false);
    }

    @Deterministic
    public static boolean isEmptyObject(Object o) {
        if (o == null)
            return true;
        if (o instanceof String) {
            return o.toString().isEmpty();
        }
        return false;
    }

    @Deterministic
    public static String emptyAsNull(String str) {
        if (str == null || str.isEmpty())
            return null;
        return str;
    }

    public static void internList(List<String> strs) {
        if (strs == null)
            return;

        for (int i = 0, n = strs.size(); i < n; i++) {
            String str = strs.get(i);
            if (str != null)
                strs.set(i, str);
        }
    }

    @Deterministic
    public static String capitalize(String str) {
        if (str == null || str.length() <= 0)
            return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    @Deterministic
    public static String decapitalize(String str) {
        if (str == null || str.length() <= 0)
            return str;
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    @Deterministic
    public static String camelCaseToUnderscore(String str, boolean lower) {
        if (str == null)
            return null;

        return CaseFormat.LOWER_CAMEL.to(lower ? CaseFormat.LOWER_UNDERSCORE : CaseFormat.UPPER_UNDERSCORE, str);
    }

    @Deterministic
    public static String camelCaseToHyphen(String str) {
        if (str == null)
            return null;
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, str);
    }

    @Deterministic
    public static String camelCase(String str, boolean firstUpper) {
        return camelCase(str, '_', firstUpper);
    }

    /**
     * 将字符串按照分隔符进行拆分，然后按照首字母大小写混排拼接。
     *
     * @param str
     * @param separator 分隔符
     * @parma firstUpper 首字母是否大写
     **/
    @Deterministic
    public static String camelCase(String str, char separator, boolean firstUpper) {
        if (str == null || str.isEmpty())
            return str;


        str = str.toLowerCase();
        if (str.indexOf(separator) < 0) {
            if (firstUpper) {
                return Character.toUpperCase(str.charAt(0)) + str.substring(1);
            }
            return str;
        }

        StringBuilder sb = new StringBuilder();
        boolean nextIsUpper = false;

        char cc = firstUpper ? Character.toUpperCase(str.charAt(0)) : str.charAt(0);
        sb.append(cc);

        for (int i = 1, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            if (c == separator) {
                nextIsUpper = true;
            } else {
                if (nextIsUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextIsUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    @Deterministic
    public static boolean startsWithPath(String str, String path) {
        if (str == null || str.isEmpty())
            return false;
        if (path == null || path.isEmpty())
            return true;

        if (str.length() < path.length() + 1)
            return false;

        if (!str.startsWith(path))
            return false;

        return path.charAt(path.length() - 1) == '/' || str.charAt(path.length()) == '/';
    }

    @Deterministic
    public static boolean startsWithNamespace(String str, String ns) {
        if (str == null || str.isEmpty())
            return false;
        if (ns == null || ns.isEmpty())
            return false;
        if (str.length() < ns.length() + 1)
            return false;

        if (!str.startsWith(ns))
            return false;

        return str.charAt(ns.length()) == ':';
    }

    @Deterministic
    public static boolean startsWithPackage(String str, String packageName) {
        if (str == null || str.isEmpty())
            return false;
        if (packageName == null || packageName.isEmpty())
            return false;
        if (str.length() < packageName.length() + 1)
            return false;

        if (!str.startsWith(packageName))
            return false;

        return str.charAt(packageName.length()) == '.';
    }

    @Deterministic
    public static boolean startsWithIgnoreCase(String str, String subStr) {
        if (str == null || subStr == null)
            return false;
        return str.regionMatches(true, 0, subStr, 0, subStr.length());
    }

    @Deterministic
    public static boolean endsWithIgnoreCase(String str, String subStr) {
        if (str == null || subStr == null)
            return false;
        if (str.length() < subStr.length())
            return false;
        return str.regionMatches(true, str.length() - subStr.length(), subStr, 0, subStr.length());
    }

    public static int indexOfIgnoreCase(String str, String subStr) {
        if (str == null || subStr == null)
            return -1;
        if (str.length() < subStr.length())
            return -1;
        for (int i = 0, n = str.length() - subStr.length(); i < n; i++) {
            if (str.regionMatches(true, i, subStr, 0, subStr.length()))
                return i;
        }
        return -1;
    }

    @Deterministic
    public static boolean startsWithAt(String str, String sub, int pos) {
        if (str == null || sub == null || str.length() < sub.length())
            return false;
        return str.regionMatches(pos, sub, 0, sub.length());
    }

    /**
     * 判断字符串是否以字母开头
     * <p>
     * 如果字符串为Null或空，返回false
     */
    @Deterministic
    public static boolean startWith(CharSequence s, char c) {
        if (isEmpty(s)) {
            return false;
        }
        return s.charAt(0) == c;
    }

    /**
     * 判断字符串是否以字母结尾
     * <p>
     * 如果字符串为Null或空，返回false
     */
    @Deterministic
    public static boolean endWith(CharSequence s, char c) {
        if (isEmpty(s)) {
            return false;
        }
        return s.charAt(s.length() - 1) == c;
    }

    @Deterministic
    public static String repeat(String str, int count) {
        Guard.checkArgument(count < Lazy.s_maxRepeatLen, "repeat count is too large", count);

        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    @Deterministic
    public static int countChar(String str, char c) {
        if (str == null)
            return 0;
        int cnt = 0;
        for (int i = 0, n = str.length(); i < n; i++) {
            if (str.charAt(i) == c)
                cnt++;
        }
        return cnt;
    }

    static final Pattern REGEX_P = Pattern.compile("<p.*?>");
    static final Pattern REGEX_BR = Pattern.compile("<br\\s*/?>");
    static final Pattern REGEX_TAG = Pattern.compile("<.*?>");

    /**
     * 删除字符串中的html标签，仅保留文本。对p和br进行特殊处理，将它们替换为回车换行。
     **/
    @Deterministic
    public static String removeHtmlTag(String str) {
        if (str == null)
            return null;
        // 段落替换为换行
        str = REGEX_P.matcher(str).replaceAll("\r\n");
        // <br><br/>替换为换行
        str = REGEX_BR.matcher(str).replaceAll("\r\n");
        // 去掉其它的<>之间的东西
        str = REGEX_TAG.matcher(str).replaceAll("");

        return str;
    }

    /**
     * 尽量在pos位置两侧取文本，文本总长度不超过len
     */
    @Deterministic
    public static String shortText(final CharSequence str, int pos, int len) {
        int n = str.length();
        if (pos < 0)
            pos = n;
        if (pos > str.length())
            return "";

        int start = Math.max(pos - len / 2, 0);
        int end = Math.min(start + len, n);
        start = Math.min(Math.max(0, n - len), start);

        StringBuilder sb = new StringBuilder(len + 2);
        if (pos < 0) {
            sb.append("[]");
        } else {
            if (start > 0)
                sb.append("...");
            sb.append(str, start, pos);
            sb.append('[');
            if (pos < n) {
                sb.append(str.charAt(pos));
            }
            sb.append(']');
        }
        if (pos < end) {
            sb.append(str, pos + 1, end);
        }
        if (end < n)
            sb.append("...");
        return sb.toString();
    }

    @Deterministic
    public static String limitLen(final CharSequence str, int offset, int maxWidth) {
        if (str == null) {
            return null;
        }
        if (maxWidth < 4) {
            maxWidth = 4;
        }
        if (str.length() <= maxWidth) {
            return str.toString();
        }
        if (offset < 0)
            offset = 0;

        if (offset > str.length()) {
            offset = str.length();
        }
        if (str.length() - offset < maxWidth - 3) {
            offset = str.length() - (maxWidth - 3);
        }
        final String abrevMarker = "...";
        if (offset <= 4) {
            return str.subSequence(0, maxWidth - 3).toString() + abrevMarker;
        }
        if (maxWidth < 7) {
            maxWidth = 7; // throw new
            // IllegalArgumentException("Minimum abbreviation
            // width with offset is 7");
        }
        if (offset + maxWidth - 3 < str.length()) {
            return abrevMarker + limitLen(str.subSequence(offset, str.length()), maxWidth - 3);
        }
        return abrevMarker + str.subSequence(str.length() - (maxWidth - 3), str.length());
    }

    @Deterministic
    public static String limitLen(final CharSequence str, final int maxWidth) {
        return limitLen(str, 0, maxWidth);
    }

    @Deterministic
    public static byte[] utf8Bytes(String str) {
        if (str == null)
            return null;
        if (str.length() <= 0)
            return EMPTY_BYTES;
        return str.getBytes(CHARSET_UTF8);
    }

    @Deterministic
    public static String md5Hash(String str) {
        byte[] bytes = utf8Bytes(str);
        if (bytes == null)
            return null;
        return bytesToHex(HashHelper.md5(bytes));
    }

    @Deterministic
    public static String sha256Hash(String str, String salt) {
        byte[] bytes = utf8Bytes(str);
        if (bytes == null)
            return null;
        return bytesToHex(HashHelper.sha256(bytes, utf8Bytes(salt)));
    }

    @Deterministic
    public static String hmacSha256(String str, String salt) {
        byte[] bytes = utf8Bytes(str);
        if (bytes == null)
            return null;
        return bytesToHex(HashHelper.hmacSha256(bytes, utf8Bytes(salt)));
    }

    @Deterministic
    public static String sha512Hash(String str, String salt) {
        byte[] bytes = utf8Bytes(str);
        if (bytes == null)
            return null;
        return bytesToHex(HashHelper.sha512(bytes, utf8Bytes(salt)));
    }

    /**
     * Base64编码
     *
     * @param bytes
     * @return
     */
    @Deterministic
    public static String encodeBase64(byte[] bytes) {
        if (bytes == null)
            return null;
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    @Deterministic
    public static String encodeBase64Url(byte[] bytes) {
        if (bytes == null)
            return null;
        return java.util.Base64.getUrlEncoder().encodeToString(bytes);
    }

    /**
     * Base64解码
     */
    @Deterministic
    public static byte[] decodeBase64(String str) {
        if (str == null)
            return null;
        if (str.indexOf('\n') >= 0)
            return java.util.Base64.getMimeDecoder().decode(str);
        return java.util.Base64.getDecoder().decode(str);
    }

    @Deterministic
    public static byte[] decodeBase64Url(String str) {
        if (str == null)
            return null;
        if (str.indexOf('\n') >= 0)
            return java.util.Base64.getMimeDecoder().decode(str);
        return java.util.Base64.getUrlDecoder().decode(str);
    }

    /**
     * <p>
     * Checks whether the String a valid Java number. Valid numbers include hexadecimal marked with the "0x" qualifier,
     * scientific notation and numbers marked with a type qualifier (e.g. 123L).
     * </p>
     * <p>
     * Null and blank string will return false.
     * </p>
     *
     * @param str the string to check
     * @return true if the string is a correctly formatted number
     */
    @Deterministic
    public static boolean isNumber(CharSequence str) {
        if ((str == null) || (str.length() == 0)) {
            return false;
        }
        int sz = str.length();
        if (str.charAt(0) == '0' && str.length() > 1) {
            char c = str.charAt(1);
            if (c != 'e' && c != 'E' && c != 'x' && c != '.')
                return false;
        }

        boolean hasExp = false;
        boolean hasDecPoint = false;
        boolean allowSigns = false;
        boolean foundDigit = false;
        // Deal with any possible sign up front
        int start = (str.charAt(0) == '-') ? 1 : 0;
        if (sz > start + 1) {
            if (str.charAt(start) == '0' && str.charAt(start + 1) == 'x') {
                int i = start + 2;
                if (i == sz) {
                    return false; // str == "0x"
                }
                // Checking hex (it can't be anything else)
                for (; i < sz; i++) {
                    char c = str.charAt(i);
                    if ((c < '0' || c > '9') && (c < 'a' || c > 'f') && (c < 'A' || c > 'F')) {
                        return false;
                    }
                }
                return true;
            }
        }
        sz--; // Don't want to loop to the last char, check it afterwords
        // for type qualifiers
        int i = start;
        // Loop to the next to last char or to the last char if we need another
        // digit to
        // make a valid number (e.g. chars[0..5] = "1234E")
        while (i < sz || (i < sz + 1 && allowSigns && !foundDigit)) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                foundDigit = true;
                allowSigns = false;

            } else if (c == '.') {
                if (hasDecPoint || hasExp) {
                    // Two decimal points or dec in exponent
                    return false;
                }
                hasDecPoint = true;
            } else if (c == 'e' || c == 'E') {
                // We've already taken care of hex.
                if (hasExp) {
                    // Two E's
                    return false;
                }
                if (!foundDigit) {
                    return false;
                }
                hasExp = true;
                allowSigns = true;
            } else if (c == '+' || c == '-') {
                if (!allowSigns) {
                    return false;
                }
                allowSigns = false;
                foundDigit = false; // We need a digit after the E
            } else {
                return false;
            }
            i++;
        }
        if (i < str.length()) {
            char c = str.charAt(i);
            if (c >= '0' && c <= '9') {
                // No type qualifier, OK
                return true;
            }
            if (c == 'e' || c == 'E') {
                // Can't have an E at the last byte
                return false;
            }
            if (!allowSigns && (c == 'd' || c == 'D' || c == 'f' || c == 'F')) {
                return foundDigit;
            }
            if (c == 'l' || c == 'L') {
                // Not allowing L with an exponoent
                return foundDigit && !hasExp;
            }

            if (c == '.')
                return !hasDecPoint && foundDigit && !allowSigns;
        }
        // allowSigns is true iff the val ends in 'E'
        // Found digit it to make sure weird stuff like '.' and '1E-' doesn't
        // pass
        return false; // !allowSigns && foundDigit;
    }

    @Deterministic
    public static boolean isNonNegativeInt(String str) {
        return isInt(str, false);
    }

    @Deterministic
    public static boolean isInt(String str) {
        return isInt(str, true);
    }

    @Deterministic
    public static boolean isInt(String str, boolean allowNegative) {
        if ((str == null) || (str.length() == 0)) {
            return false;
        }

        if (allowNegative) {
            if (str.charAt(0) == '-') {
                str = str.substring(1, str.length());
            }
        }

        if (str.charAt(0) == '0' && str.length() > 1) {
            return false;
        }
        return isAllDigit(str);
    }

    /**
     * <p>
     * Turns a string value into a java.lang.Number. First, the value is examined for a type qualifier on the end (
     * <code>'f','F','d','D','l','L'</code>). If it is found, it starts trying to create succissively larger types from
     * the type specified until one is found that can hold the value.
     * </p>
     * <p>
     * If a type specifier is not found, it will check for a decimal point and then try successively larger types from
     * Integer to BigInteger and from Float to BigDecimal.
     * </p>
     * <p>
     * If the string starts with "0x" or "-0x", it will be interpreted as a hexadecimal integer. Values with leading 0's
     * will not be interpreted as octal.
     * </p>
     *
     * @param val String containing a number
     * @return Number created from the string
     * @throws NumberFormatException if the value cannot be converted
     */
    @Deterministic
    public static Number parseNumber(String val) {
        return ConvertHelper.stringToNumber(val, NopException::new);
    }

    @Deterministic
    public static Number tryParseNumber(String val) {
        if (val == null || val.isEmpty())
            return null;

        int pos = 0, n = val.length();
        if (val.charAt(0) == '-')
            pos++;

        for (; pos < n; pos++) {
            char ch = val.charAt(pos);
            if (!isDigit(ch) && ch != '.' && ch != 'e' && ch != 'E')
                break;
        }

        try {
            return parseNumber(val.substring(0, pos));
        } catch (Exception e) {
            return null;
        }
    }

    @Deterministic
    public static boolean isAllChar(String str, char c) {
        if (str == null || str.length() <= 0)
            return false;
        for (int i = 0, n = str.length(); i < n; i++) {
            if (str.charAt(i) != c)
                return false;
        }
        return true;
    }

    enum CRLFKind {
        LF, CRLF, OTHER
    }

    /**
     * 所有独立的\r或者\n或者\r\n都规范化为\r\n或者\n
     *
     * @param crlf 如果为true，表示规范化为\r\n。如果为false，则表示规范化为\n
     */
    @Deterministic
    public static String normalizeCRLF(String str, boolean crlf) {
        return escapeCRLF(str, crlf ? CRLFKind.CRLF : CRLFKind.LF, null);
    }

    @Deterministic
    public static String escapeCRLF(String str, String replaced) {
        return escapeCRLF(str, CRLFKind.OTHER, replaced);
    }

    private static String escapeCRLF(String str, CRLFKind kind, String replaced) {
        if (str == null || str.length() <= 0)
            return str;
        int action = 0; // 1 for \r, 2 for \n, 3 for \r\n
        int sz = str.length();
        StringBuilder buf = null;
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);
            if (i == sz - 1) {
                if (ch == '\r') {
                    action = 1;
                } else if (ch == '\n') {
                    action = 2;
                } else {
                    action = 0;
                }
            } else {
                if (ch == '\r') {
                    char next = str.charAt(i + 1);
                    if (next == '\n') {
                        action = 3;
                    } else {
                        action = 1;
                    }
                } else if (ch == '\n') {
                    action = 2;
                } else {
                    action = 0;
                }
            }

            if (buf != null) {
                if (action == 0) {
                    buf.append(ch);
                } else {
                    if (action == 3)
                        i++;
                    if (kind == CRLFKind.CRLF) {
                        buf.append('\r').append('\n');
                    } else if (kind == CRLFKind.LF) {
                        buf.append('\n');
                    } else {
                        buf.append(replaced);
                    }
                }
            } else {
                if (action == 2) {
                    if (kind == CRLFKind.LF)
                        continue;
                } else if (action == 3) {
                    if (kind == CRLFKind.CRLF) {
                        i++;
                        continue;
                    }
                }

                if (action != 0) {
                    if (buf == null) {
                        buf = new StringBuilder(2 * sz);
                        if (i > 0)
                            buf.append(str.subSequence(0, i));
                    }
                    if (action == 3)
                        i++;

                    if (kind == CRLFKind.CRLF) {
                        buf.append('\r').append('\n');
                    } else if (kind == CRLFKind.LF) {
                        buf.append('\n');
                    } else {
                        buf.append(replaced);
                    }
                }
            }
        }

        if (buf == null)
            return str;
        return buf.toString();
    }

    @Deterministic
    public static boolean isValidClassName(String s) {
        if (s == null || s.length() <= 0)
            return false;
        if (!isAsciiLetter(s.charAt(0))) {
            LOG.trace("nop.commons.util.class-name-must-starts-with-ascii-letter", s);
            return false;
        }
        int state = 0;
        for (int i = 1, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (isAsciiVarPart(c) || c == '$') {
                state = 0;
            } else if (c == '.') {
                if (state == 1)
                    return false;
                state = 1;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 将xml标签名或者属性名按照驼峰规则转换为变量名，生成的变量名有可能与XLang语言的关键字重名。 例如 ioc:start转换为iocStart，default-override转换为defaultOverride，
     * default_override转换为default_override，即采用:和-分隔。
     * <p>
     * 与xmlNameToPropName不同，这里没有对class属性名进行重命名，
     */
    @Deterministic
    public static String xmlNameToVarName(String str) {
        if (StringHelper.isEmpty(str))
            return str;

        str = str.replace(':', '-');
        str = str.replace('.', '-');
        if (str.indexOf('-') < 0) {
            return str;
        }
        return camelCase(str, '-', false);
    }

    /**
     * 将xml标签名或者属性名按照驼峰规则转换为java属性名。同时名称为class时将会被转换为className，从而避免和java的getClass函数重名。 例如
     * ioc:start转换为iocStart，default-override转换为defaultOverride， default_override转换为default_override，即采用:和-分隔。
     */
    @Deterministic
    public static String xmlNameToPropName(String str) {
        String name = xmlNameToVarName(str);
        if ("class".equals(name))
            return CommonConstants.PROP_CLASS_NAME;
        return beanPropName(name);
    }

    /**
     * 将数据库字段名转换为java属性名。对于id和class进行了特殊处理。
     */
    @Deterministic
    public static String colCodeToPropName(String str) {
        if (CommonConstants.PROP_ID.equalsIgnoreCase(str) || CommonConstants.PROP_ID_.equalsIgnoreCase(str))
            return CommonConstants.PROP_ID_;
        if ("class".equalsIgnoreCase(str)) {
            return CommonConstants.PROP_CLASS_NAME;
        }
        return beanPropName(camelCase(str, true));
    }

    @Deterministic
    public static boolean isXmlNameStart(int c) {
        return isJavaIdentifierStart(c) && c != '$';
    }

    @Deterministic
    public static boolean isXmlNamePart(int c) {
        if (c == '$')
            return false;

        if (XMLChar.isName(c)) {
            // 同时也是JavaIdentifierPart
            if (c == 8494 || c == 183 || c == 903 || c == 1758)
                return false;
            return true;
        } else {
            return false;
        }
    }

    @Deterministic
    public static boolean isJavaIdentifierStart(int c) {
        return Character.isJavaIdentifierStart(c);
    }

    @Deterministic
    public static boolean isJavaIdentifierPart(int c) {
        return Character.isJavaIdentifierPart(c);
    }

    @Deterministic
    public static boolean isJavaKeyword(String str) {
        if (str == null)
            return false;
        return Keywords.JAVA.contains(str);
    }

    @Deterministic
    public static boolean isJavaScriptKeyword(String str) {
        if (str == null)
            return false;
        return Keywords.JAVASCRIPT.contains(str);
    }

    @Deterministic
    public static boolean isXLangKeyword(String str) {
        if (str == null)
            return false;
        return Keywords.XLANG.contains(str);
    }

    @Deterministic
    public static boolean isValidJavaVarName(String s) {
        if (s == null || s.length() == 0)
            return false;
        if (!isJavaIdentifierStart(s.charAt(0)))
            return false;
        for (int i = 1, n = s.length(); i < n; i++)
            if (!isJavaIdentifierPart(s.charAt(i)))
                return false;
        return true;
    }

    @Deterministic
    public static boolean isValidSimpleVarName(String s) {
        return isValidJavaVarName(s) && s.indexOf('$') < 0;
    }

    @Deterministic
    public static boolean isQuotedString(String str) {
        if (isEmpty(str))
            return false;

        if (str.length() <= 1)
            return false;

        char c = str.charAt(0);
        if (c != '\'' && c != '"')
            return false;

        if (str.charAt(str.length() - 1) != c)
            return false;

        if (str.indexOf(c, 1) != str.length() - 1)
            return false;

        return true;
    }

    @Deterministic
    public static boolean isValidPropName(String s) {
        return isValidJavaVarName(s);
    }

    @Deterministic
    public static boolean isValidPropPath(String s) {
        if (s == null || s.length() == 0)
            return false;
        if (!isJavaIdentifierStart(s.charAt(0)))
            return false;

        int state = 0;
        for (int i = 1, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (isJavaIdentifierPart(c)) {
                state = 0;
            } else if (c == '.') {
                if (state == 1)
                    return false;
                state = 1;
            } else {
                return false;
            }
        }
        return true;
    }

    @Deterministic
    public static boolean isValidHtmlAttrName(String s) {
        if (s == null || s.length() == 0)
            return false;
        char cur = s.charAt(0);
        if (!XMLChar.isName(cur) && cur != '@')
            return false;
        return _isValidXmlNamePart(s, true, true);
    }

    @Deterministic
    public static boolean isValidXmlName(String s) {
        return isValidXmlName(s, true, true);
    }

    @Deterministic
    public static boolean isValidXmlNamespaceName(String s) {
        return isValidXmlName(s, false, false);
    }

    @Deterministic
    public static boolean isValidXmlName(String s, boolean allowColon, boolean allowDot) {
        if (s == null || s.length() == 0)
            return false;
        char c = s.charAt(0);
        // 与规范不同，这里不允许起始字符为58[：]和8494[℮]，这样一来就同时满足JavaIdentifierStart条件
        if (!isXmlNameStart(c))
            return false;

        return _isValidXmlNamePart(s, allowColon, allowDot);
    }

    static boolean _isValidXmlNamePart(String s, boolean allowColon, boolean allowDot) {
        // 与规范不同，不允许连续的分割符，也不允许以分割符结尾
        int i, n;
        // state =1 表示前一个字符为分隔符
        int state = 0;
        for (i = 1, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c == '-') {
                if (state == 1)
                    return false;
                state = 1;
                continue;
            }

            if (c == ':') {
                if (!allowColon)
                    return false;
                if (state == 1)
                    return false;
                state = 1;
                continue;
            }
            if (c == '.') {
                if (!allowDot)
                    return false;
                if (state == 1)
                    return false;
                state = 1;
                continue;
            }

            if (!isXmlNamePart(c))
                return false;
            state = 0;
        }
        // 不允许分隔符结尾
        if (state == 1)
            return false;
        return true;
    }

    @Deterministic
    public static boolean isValidId(String s) {
        if (s == null || s.length() <= 0)
            return false;

        char start = s.charAt(0);
        if (!isJavaIdentifierStart(start))
            return false;
        int state = 0;
        for (int i = 1, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (isJavaIdentifierPart(c)) {
                state = 0;
            } else if (c == '.' || c == '-' || c == '/') {
                if (state == 1)
                    return false;
                state = 1;
            } else {
                return false;
            }
        }
        return true;
    }

    @Deterministic
    @Description("是否合法的标识名称。如果合法，则通过驼峰变换替换符号-之后可以得到合法的Java变量名")
    public static boolean isValidTokenName(String s) {
        if (s == null || s.length() <= 0)
            return false;

        char start = s.charAt(0);
        if (!isJavaIdentifierStart(start))
            return false;
        int state = 0;
        for (int i = 1, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (isJavaIdentifierPart(c)) {
                state = 0;
            } else if (c == '-') {
                if (state == 1)
                    return false;
                state = 1;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 匹配 *xxx, xxx* 或者*xxx*模式。对于空串或者空的模式，直接返回false
     *
     * @param str     待匹配的字符串。
     * @param pattern 模板字符串，首尾可以用星号来表示任意匹配
     */
    @Deterministic
    public static boolean matchSimplePattern(String str, String pattern) {
        if (str == null || pattern == null)
            return false;
        if (pattern.startsWith("*")) {
            // *匹配所有
            if (pattern.length() == 1 || pattern.equals("**"))
                return true;
            if (pattern.endsWith("*")) {
                String sub = pattern.substring(1, pattern.length() - 1);
                return str.contains(sub);
            } else {
                return str.regionMatches(str.length() - pattern.length() + 1, pattern, 1, pattern.length() - 1);
            }
        } else if (pattern.endsWith("*")) {
            return str.regionMatches(0, pattern, 0, pattern.length() - 1);
        } else {
            return str.equals(pattern);
        }
    }

    @Deterministic
    public static boolean matchSimplePatterns(String str, String[] patterns) {
        if (str == null || patterns == null)
            return false;
        for (String pattern : patterns) {
            if (matchSimplePattern(str, pattern))
                return true;
        }
        return false;
    }

    @Deterministic
    public static boolean matchSimplePatternSet(String str, Collection<String> patterns) {
        if (str == null || patterns == null)
            return false;
        for (String pattern : patterns) {
            if (matchSimplePattern(str, pattern))
                return true;
        }
        return false;
    }

    static String _toString(Object str) {
        return str == null ? null : str.toString();
    }

    @Deterministic
    public static String escapeYaml(String text) {
        return YamlHelper.escapeYaml(text);
    }

    @Deterministic
    public static String unquote(String text) {
        if (text == null || text.length() <= 2)
            return text;
        char beginChar = text.charAt(0);
        char endChar = text.charAt(text.length() - 1);
        if (beginChar == '\'' && endChar == '\'' || beginChar == '"' && endChar == '"')
            return unescapeJava(text.substring(1, text.length() - 1));
        return text;
    }

    /**
     * 通过重复quote字符来对quote字符实现转义。例如 a`b得到 `a``b`
     *
     * @param str   字符串
     * @param quote 引用字符
     * @return 为字符串前后增加quote字符，并对字符串内部的quote字符进行转义。
     */
    @Deterministic
    public static String quoteDupEscapeString(String str, char quote) {
        if (str == null)
            return null;
        if (str.isEmpty())
            return String.valueOf(quote) + quote;

        if (str.indexOf(quote) < 0) {
            return quote + str + quote;
        }
        StringBuilder sb = new StringBuilder(str.length() + 5);
        sb.append(quote);
        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            if (c == quote) {
                sb.append(quote);
                sb.append(quote);
            } else {
                sb.append(c);
            }
        }
        sb.append(quote);
        return sb.toString();
    }

    /**
     * 如果发现指定字符，则把该字符重复一遍。例如 a'b --> a''b
     */
    @Deterministic
    public static String encodeDupEscape(String str, char c) {
        if (str == null)
            return null;
        if (str.isEmpty())
            return str;

        if (str.indexOf(c) < 0) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length() + 5);
        for (int i = 0, n = str.length(); i < n; i++) {
            char cc = str.charAt(i);
            if (cc == c) {
                sb.append(c);
                sb.append(c);
            } else {
                sb.append(cc);
            }
        }
        return sb.toString();
    }

    @Deterministic
    public static String decodeDupEscape(String str, char c) {
        if (str == null)
            return null;
        if (str.isEmpty())
            return str;

        if (str.indexOf(c) < 0) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length() + 5);
        for (int i = 0, n = str.length(); i < n; i++) {
            char cc = str.charAt(i);
            sb.append(cc);
            if (cc == c) {
                if (i < n - 1 && str.charAt(i + 1) == c) {
                    i++;
                }
            }
        }
        return sb.toString();
    }

    /**
     * 假设第一个字符和最后一个字符是quote字符，对字符串进行反转义处理。 例如 `a``b`得到 a`b
     *
     * @param str 字符串
     * @return 反转义得到的字符串。
     */
    @Deterministic
    public static String unquoteDupEscapeString(String str) {
        if (str == null || str.length() < 2) {
            return str;
        }
        StringBuilder buf = null;
        char quote = str.charAt(0);
        if (quote != str.charAt(str.length() - 1)) {
            throw new NopException(ERR_UTILS_INVALID_QUOTED_STRING).param(ARG_STR, str);
        }
        int i, len = str.length() - 2;
        for (i = 1; i < len; i++) {
            char ch = str.charAt(i);
            if (ch == quote) {
                int j = i;
                i++;
                ch = str.charAt(i);
                if (ch != quote)
                    continue;

                if (buf == null) {
                    buf = new StringBuilder(len);
                    if (j > 0) {
                        buf.append(str, 1, j);
                    }
                }
                buf.append(ch);
            } else if (buf != null) {
                buf.append(ch);
            }
        }
        if (buf != null) {
            if (i == len)
                buf.append(str.charAt(len));
            return buf.toString();
        }
        return str.substring(1, str.length() - 1);
    }

    @Deterministic
    public static List<String> splitDupEscaped(String str, char sep) {
        if (str == null)
            return null;
        if (str.isEmpty())
            return Collections.emptyList();

        int pos2 = str.indexOf(sep);
        if (pos2 < 0)
            return Collections.singletonList(str);

        List<String> ret = new ArrayList<>();
        SimpleTextReader tk = new SimpleTextReader(str);
        do {
            String sub = tk.nextDupEscape(sep);
            ret.add(sub);
            if (tk.isEnd())
                break;
            tk.next();
        } while (true);

        return ret;
    }

    @Deterministic
    public static String removeUrlQuery(String path) {
        if (path == null)
            return null;
        int pos = path.indexOf('?');
        if (pos < 0)
            return path;
        return path.substring(0, pos);
    }


    @Deterministic
    public static Map<String, Object> parseQuery(String query, String encoding) {
        if (query == null || query.length() <= 0)
            return null;

        Map<String, Object> ret = new LinkedHashMap<>();
        parseQuery(query, encoding, (key, value) -> {
            Object v = ret.get(key);
            if (v == null) {
                ret.put(key, value);
            } else if (v instanceof List<?>) {
                ((List<String>) v).add(value);
            } else {
                List<String> list = new ArrayList<>();
                list.add((String) v);
                list.add(value);
                ret.put(key, value);
            }
        });
        return ret;
    }

    @SuppressWarnings("CPD")
    @Deterministic
    public static Map<String, String> parseSimpleQuery(String query) {
        Map<String, String> ret = new LinkedHashMap<>();
        parseQuery(query, ENCODING_UTF8, (key, value) -> {
            Object v = ret.get(key);
            if (v == null) {
                ret.put(key, value);
            } else {
                throw new NopException(ERR_UTILS_DUPLICATE_PARAM_NAME_IS_NOT_ALLOWED_IN_SIMPLE_QUERY)
                        .param(ARG_PARAM_NAME, key).param(ARG_QUERY, query);
            }
        });
        return ret;
    }

    private static void parseQuery(String query, String encoding, BiConsumer<String, String> action) {
        if (query == null || query.length() <= 0)
            return;

        int pos1 = 0;
        do {
            int pos2 = query.indexOf('&', pos1);
            if (pos2 < 0) {
                pos2 = query.length();
            }
            if (pos1 >= pos2) {
                if (pos2 >= query.length())
                    break;
                pos1 = pos2 + 1;
                continue;
            }
            String s = query.substring(pos1, pos2);
            int pos3 = s.indexOf('=');
            String key, value;
            if (pos3 < 0) {
                key = s;
                value = "";
            } else {
                key = s.substring(0, pos3);
                value = s.substring(pos3 + 1);
            }
            key = decodeURL(key, encoding);
            if (value != null && !value.isEmpty()) {
                value = decodeURL(value, encoding);
            }

            action.accept(key, value);

            if (pos2 >= query.length())
                break;

            pos1 = pos2 + 1;
        } while (true);
    }

    @Deterministic
    public static String encodeUriPath(String str) {
        return UriEncodeHelper.encodeUriComponent(str, CHARSET_UTF8, UriEncodeHelper.Type.PATH);
    }

    @Deterministic
    public static String beanPropName(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }

        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            if (Character.isUpperCase(name.charAt(0)))
                return name;
            // aUser ==> 对应方法getAUser() ==> java属性名AUser
            char chars[] = name.toCharArray();
            chars[0] = Character.toUpperCase(chars[0]);
            return new String(chars);
        }

        if (Character.isLowerCase(name.charAt(0)))
            return name;

        char chars[] = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    @Deterministic
    public static boolean isCanonicalFilePath(String name) {
        if (name == null)
            return false;

        if (name.indexOf('\\') >= 0)
            return false;

        if (name.contains("../"))
            return false;

        if (name.endsWith("/.."))
            return false;

        if (name.contains("/./"))
            return false;

        if (name.startsWith("./") || name.endsWith("/."))
            return false;

        return isValidFilePath(name);
    }

    static final char[] INVALID_FILE_NAME_CHARS = new char[]{'/', '\\', ':', '*', '?', '"', '<', '>', '|', 0};
    static final String INVALID_FILE_NAME_CHARS_STR = new String(INVALID_FILE_NAME_CHARS);
    static final String[] INVALID_FILE_NAME_REPLS = new String[]{"_", "_", "_", "_", "_", "_", "_", "_", "_", "_"};

    static final char[] INVALID_FILE_PATH_CHARS = new char[]{'\\', ':', '*', '?', '"', '<', '>', '|', 0};
    static final String INVALID_FILE_PATH_CHARS_STR = new String(INVALID_FILE_PATH_CHARS);

    static boolean _containsControlAscii(String s) {
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (c >= 0 && c <= 31)
                return true;
        }
        return false;
    }

    static String _escapeControlAscii(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0, n = s.length(); i < n; i++) {
            int c = s.charAt(i);
            if (c >= 0 && c <= 31) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @Deterministic
    public static boolean isValidFileName(String path) {
        if (isEmpty(path))
            return false;
        if (_containsControlAscii(path))
            return false;
        return !containsAnyChar(path, INVALID_FILE_NAME_CHARS_STR);
    }

    @Deterministic
    public static boolean isValidFilePath(String path) {
        if (isEmpty(path))
            return false;
        if (_containsControlAscii(path))
            return false;
        return !containsAnyChar(path, INVALID_FILE_PATH_CHARS_STR) && !path.contains("//");
    }

    @Deterministic
    public static boolean isValidVPath(String path) {
        if (isEmpty(path))
            return false;

        int pos = path.indexOf(':');
        if (pos < 0)
            return isValidFilePath(path);
        String ns = path.substring(0, pos);
        if (!isValidXmlName(ns))
            return false;
        if (path.length() <= pos + 1)
            return false;
        if (path.charAt(pos + 1) == '/') {
            if (path.length() == pos + 2)
                return true;
            return isValidFilePath(path.substring(pos + 2));
        }
        return isValidFilePath(path.substring(pos + 1));
    }

    @Deterministic
    public static String safeFileName(String fileName) {
        if (isEmpty(fileName))
            return fileName;
        if (_containsControlAscii(fileName)) {
            fileName = _escapeControlAscii(fileName);
        }

        return escape(fileName, INVALID_FILE_NAME_CHARS, INVALID_FILE_NAME_REPLS);
    }

    @Deterministic
    public static String appendPath(String path, String relativePath) {
        if (relativePath == null || relativePath.length() <= 0)
            return path;

        if (path == null || path.isEmpty())
            return relativePath;

        if (!path.endsWith("/")) {
            if (!relativePath.startsWith("/")) {
                return path + "/" + relativePath;
            } else {
                return path + relativePath;
            }
        } else {
            if (!relativePath.startsWith("/")) {
                return path + relativePath;
            } else {
                return path + relativePath.substring(1);
            }
        }
    }

    /**
     * Normalize the path by suppressing sequences like "path/.." and inner simple dots.
     * <p>
     * The result is convenient for path comparison. For other uses, notice that Windows separators ("\") are replaced
     * by simple slashes.
     *
     * @param path the original path
     * @return the normalized path
     */
    @Deterministic
    public static String normalizePath(String path) {
        if (path == null) {
            return null;
        }
        String pathToUse = path.replace('\\', '/');

        // Strip prefix from path to analyze, to not treat it as part of the
        // first path element. This is necessary to correctly of paths like
        // "file:core/../core/io/Resource.class", where the ".." should just
        // strip the first "core" directory while keeping the "file:" prefix.
        int prefixIndex = pathToUse.indexOf(":");
        String prefix = "";
        if (prefixIndex != -1) {
            prefix = pathToUse.substring(0, prefixIndex + 1);
            if (prefix.contains("/")) {
                prefix = "";
            } else {
                pathToUse = pathToUse.substring(prefixIndex + 1);
            }
        }
        if (pathToUse.length() == 0)
            return prefix;

        // 不包含/./或者/../, 就没有必要再做后续处理
        if (pathToUse.indexOf("/.") < 0) {
            return prefix.length() <= 0 ? pathToUse : prefix + pathToUse;
        }

        if (pathToUse.charAt(0) == '/') {
            prefix = prefix + '/';
            pathToUse = pathToUse.substring(1);
        }

        List<String> pathArray = split(pathToUse, '/');
        Deque<String> pathElements = new ArrayDeque<>(pathArray.size());
        int tops = 0;

        for (int i = pathArray.size() - 1; i >= 0; i--) {
            String element = pathArray.get(i);

            if (".".equals(element)) { //NOPMD - suppressed EmptyControlStatement
                // Points to current directory - drop it.
            } else if ("..".equals(element)) {
                // Registering top path found.
                tops++;
            } else {
                if (tops > 0) {
                    // Merging path element with element corresponding to top
                    // path.
                    tops--;
                } else {
                    // Normal path element found.
                    // element = replace(element, "..", "__");
                    pathElements.addFirst(element);
                }
            }
        }

        // ignore all top paths need to be retained.
        return prefix + join(pathElements, "/");
    }

    public static boolean isAbsolutePath(String path) {
        if (path == null)
            return false;

        if (!path.startsWith("/"))
            return false;

        if (path.indexOf('\\') >= 0 || path.indexOf(':') >= 0)
            return false;

        if (path.contains("/./"))
            return false;
        if (path.contains("/../"))
            return false;
        if (path.endsWith("/.") || path.endsWith("/.."))
            return false;
        return true;
    }

    @Deterministic
    public static String relativizePath(String base, String path) {
        if (path == null)
            return null;
        if (base == null)
            return path;

        if (!path.startsWith("/") || !base.startsWith("/"))
            return path;
        base = base.substring(1);
        path = path.substring(1);

        boolean baseIsDir = base.endsWith("/");
        boolean relativeIsDir = path.endsWith("/");
        if (baseIsDir)
            base = base.substring(0, base.length() - 1);
        if (base.length() <= 0)
            return path;

        if (relativeIsDir)
            path = path.substring(0, path.length() - 1);
        List<String> baseParts = split(base, '/');
        List<String> relativeParts = split(path, '/');
        int i, n = Math.min(baseParts.size(), relativeParts.size());
        for (i = 0; i < n; i++) {
            if (!baseParts.get(i).equals(relativeParts.get(i)))
                break;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = baseIsDir ? i : i + 1, K = baseParts.size(); j < K; j++) {
            sb.append("..");
            if (j != K - 1)
                sb.append('/');
        }
        for (int j = i, K = relativeParts.size(); j < K; j++) {
            if (sb.length() > 0)
                sb.append('/');
            sb.append(relativeParts.get(j));
        }
        if (relativeIsDir)
            sb.append('/');
        return sb.toString();
    }

    @Deterministic
    public static String toAbsolutePath(String path, String currentPath) {
        return absolutePath(currentPath, path);
    }

    @Deterministic
    public static String absolutePath(String currentPath, String path) {
        if (currentPath == null)
            return normalizePath(path);
        if (path == null)
            return null;

        int pos0 = path.indexOf(':');
        // 带协议
        if (pos0 >= 0) {
            // 协议相同
            /*
             * int pos1 = currentPath.indexOf(':'); if(pos1 == pos0 && path.regionMatches(0, currentPath, 0, pos0)){
             * if(pos0 == path.length() - 1){ return path + "/"; }else if(path.charAt(pos0+1) == '/'){ // 本身就是绝对路径
             * return path; }else{ return _appendPath(currentPath, path.substring(pos0+1)); } }else{ // 协议不相同，则不合并路径
             * return normalizePath(path); }
             */
            // 带协议就不进行路径合并
            return normalizePath(path);
        }

        // 如果是绝对路径，则直接返回
        if (path.startsWith("/"))
            return normalizePath(path);

        return _appendPath(currentPath, path);
    }

    private static String _appendPath(String currentPath, String path) {
        if (currentPath.endsWith("/"))
            return normalizePath(currentPath + path);

        int pos = currentPath.lastIndexOf('/');
        if (pos < 0) {
            pos = currentPath.indexOf(':');
            if (pos > 0)
                return normalizePath(currentPath.substring(0, pos + 1) + path);
            return normalizePath("/" + path);
        } else {
            return normalizePath(currentPath.substring(0, pos + 1) + path);
        }
    }

    @Deterministic
    public static String fileNameNoExt(String path) {
        String name = fileFullName(path);
        if (name == null || name.length() == 0)
            return name;
        int pos = name.lastIndexOf('.');
        if (pos < 0)
            return name;
        return name.substring(0, pos);
    }

    /**
     * 与fileExt的区别在于，它在fileName中查找最后两个dot, 而不是最后一个dot。 例如 a.orm.xml对应fileType=orm.xml, 而fileExt=xml
     */
    @Deterministic
    public static String fileType(String path) {
        if (path == null)
            return null;
        int pos = path.lastIndexOf('/');
        if (pos < 0)
            pos = 0;
        int pos2 = path.lastIndexOf('.');
        if (pos2 < pos)
            return "";
        if (pos2 == 0)
            return path.substring(pos2 + 1);

        int pos3 = path.lastIndexOf('.', pos2 - 1);
        if (pos3 < pos)
            return path.substring(pos2 + 1);
        return path.substring(pos3 + 1);
    }

    @Deterministic
    public static boolean isValidFileType(String name) {
        if (isEmpty(name))
            return false;

        if (!isValidFileName(name))
            return false;

        if (countChar(name, '.') > 2)
            return false;

        return true;
    }

    @Deterministic
    public static String removeFileType(String path) {
        if (path == null)
            return null;

        String fileType = fileType(path);
        if (fileType.isEmpty())
            return path;
        return path.substring(0, path.length() - fileType.length() - 1);
    }

    @Deterministic
    public static String replaceFileType(String path, String fileType) {
        if (path == null || fileType == null)
            return path;
        return removeFileType(path) + (fileType.startsWith(".") ? "" : ".") + fileType;
    }

    @Deterministic
    public static String fileExt(String path) {
        if (path == null)
            return null;
        int pos2 = path.lastIndexOf('.');
        if (pos2 < 0)
            return "";
        int pos = path.lastIndexOf('/');
        if (pos < 0 || pos2 > pos)
            return path.substring(pos2 + 1);
        return "";
    }

    @Deterministic
    public static String removeFileExt(String path) {
        if (path == null)
            return null;

        String fileExt = fileExt(path);
        if (!fileExt.isEmpty())
            return path.substring(0, path.length() - fileExt.length() - 1);
        return path;
    }

    @Deterministic
    public static String replaceFileExt(String path, String fileExt) {
        if (path == null || fileExt == null)
            return path;
        return removeFileExt(path) + (fileExt.startsWith(".") ? "" : ".") + fileExt;
    }

    @Deterministic
    public static String fileFullName(String path) {
        return lastPart(path, '/', false);
    }

    @Deterministic
    public static String filePath(String path) {
        if (path == null)
            return null;
        int pos = path.lastIndexOf('/');
        if (pos < 0)
            return "";
        return path.substring(0, pos);
    }

    @Deterministic
    public static String firstPart(String str, char c) {
        if (str == null)
            return null;
        int pos = str.indexOf(c);
        if (pos < 0)
            return str;
        return str.substring(0, pos);
    }

    @Deterministic
    public static String lastPart(String str, char c, boolean emptyIfNoSep) {
        if (str == null)
            return null;
        int pos = str.lastIndexOf(c);
        if (pos < 0) {
            return emptyIfNoSep ? "" : str;
        }
        return str.substring(pos + 1);
    }

    @Deterministic
    public static String removeLastPart(String str, char c) {
        if (str == null)
            return null;
        int pos = str.lastIndexOf(c);
        if (pos < 0)
            return "";
        return str.substring(0, pos);
    }

    @Deterministic
    public static String lastPart(String str, char c) {
        return lastPart(str, c, false);
    }

    @Deterministic
    public static String nextPart(String str, char c) {
        if (str == null)
            return null;
        int pos = str.indexOf(c);
        if (pos < 0)
            return "";
        return str.substring(pos + 1);
    }

    @Deterministic
    public static String head(String s, int n) {
        if (s == null)
            return null;
        if (s.length() <= n)
            return s;
        return s.substring(0, n);
    }

    @Deterministic
    public static String tail(String s, int n) {
        if (s == null)
            return null;
        if (s.length() <= n)
            return s;
        return s.substring(s.length() - n);
    }

    @Deterministic
    public static String removeTail(String s, String tail) {
        if (s == null)
            return null;
        if (!s.endsWith(tail))
            return s;
        return s.substring(0, s.length() - tail.length());
    }

    @Deterministic
    public static String removeHead(String s, String head) {
        if (s == null)
            return null;
        if (!s.startsWith(head))
            return s;
        return s.substring(head.length());
    }

    @Deterministic
    public static String skip(String s, int n) {
        if (s == null)
            return null;
        if (s.length() <= n)
            return "";
        return s.substring(n);
    }

    @Deterministic
    public static Double parseDegree(String str) {
        str = strip(str);
        if (str == null)
            return null;

        str = replaceChars(str, "\"'\u00BA‘“", "″′°′″");

        double ret = 0;

        int pos1 = str.indexOf('°');

        if (pos1 >= 0) {
            String s1 = str.substring(0, pos1);
            str = str.substring(pos1 + 1);
            ret = parseNumber(s1).doubleValue();
        }

        int pos2 = str.indexOf('′');
        if (pos2 >= 0) {
            String s2 = str.substring(0, pos2);
            str = str.substring(pos2 + 1);
            ret += parseNumber(s2).doubleValue() / 60.0;
        }
        int pos3 = str.indexOf('″');
        if (pos3 >= 0) {
            String s3 = str.substring(0, pos3);
            // str = str.substring(pos3 + 1);
            ret += parseNumber(s3).doubleValue() / 3600.0;
        }

        return ret;
    }

    @Deterministic
    public static String formatDegree(Number n) {
        if (n == null)
            return null;
        int d = n.intValue();
        double m1 = n.doubleValue() - d;
        int m = (int) (m1 * 60);
        double s1 = (m1 * 60) - m;
        int s = (int) ((s1 + 0.00000001) * 60);
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append('°');
        }
        if (m > 0 || s > 0) {
            sb.append(m).append('′');
        }
        if (s > 0) {
            sb.append(s).append('″');
        }
        return sb.toString();
    }

    /**
     * 获取字符串转化为UTF8编码后的字节长度，等价于str.getBytes("UTF8").length, 但是性能更高
     */
    @Deterministic
    public static int utf8Length(CharSequence str) {
        if (str == null)
            return 0;
        return Utf8.encodedLength(str);
    }

    @Deterministic
    public static String limitUtf8Len(CharSequence str, int utfLen) {
        return limitUtfLen(str, 0, utfLen);
    }

    /**
     * 从指定位置开始截取子字符串，确保子字符串编码为UTF8之后长度小于utf8Len
     */
    @Deterministic
    public static String limitUtfLen(CharSequence str, int from, int utf8Len) {

        StringBuilder sb = new StringBuilder();
        int size = 0;
        for (int i = from, n = str.length(); i < n; i++) {
            if (size >= utf8Len)
                break;
            char c = str.charAt(i);
            // 如果 codePoint 小于 0x0080，则它使用单个字节进行编码。
            if (c < 0x80) {
                size++;
            } else if (c < 0x800) {
                // 如果 codePoint 在 0x0080 到 0x07FF 之间，则它使用两个字节进行编码。
                size += 2;
                if (size > utf8Len)
                    break;
                sb.append(c);
            } else if (!Character.isHighSurrogate(c)) {
                // 如果 codePoint 在 0x0800 到 0xFFFF 之间，则它使用三个字节进行编码。
                size += 3;
                if (size > utf8Len)
                    break;
                sb.append(c);
            } else {
                //  如果 codePoint 在 0x10000 到 0x10FFFF 之间，则它使用四个字节进行编码
                if (i >= n - 1) {
                    break;
                }
                i++;
                char c2 = str.charAt(i);
                size += 4;
                if (size > utf8Len)
                    break;
                sb.append(c);
                sb.append(c2);
            }
        }
        return sb.toString();
    }

    @Deterministic
    public static long parseFileSizeString(String str) {
        if (str == null || str.length() <= 0)
            return -1;
        if (str.endsWith("G")) {
            return (long) parseNumber(str.substring(0, str.length() - 1)).doubleValue() * 1024 * 1024 * 1024;
        } else if (str.endsWith("M")) {
            return (long) parseNumber(str.substring(0, str.length() - 1)).doubleValue() * 1024 * 1024;
        } else if (str.endsWith("K")) {
            return (long) parseNumber(str.substring(0, str.length() - 1)).doubleValue() * 1024;
        } else {
            return parseNumber(str).longValue();
        }
    }

    private static final String PATH_MATCH = "**";

    /**
     * Matches path against pattern using *, ? and ** wildcards. Both path and the pattern are tokenized on path
     * separators (both \ and /). '**' represents deep tree wildcard, as in Ant.
     */
    @Deterministic
    public static boolean matchPath(String path, String pattern) {
        List<String> pathElements = StringHelper.split(path, '/');
        List<String> patternElements = StringHelper.split(pattern, '/');
        return matchTokens(pathElements, patternElements);
    }

    /**
     * Match tokenized string and pattern.
     */
    private static boolean matchTokens(List<String> tokens, List<String> patterns) {
        int patNdxStart = 0;
        int patNdxEnd = patterns.size() - 1;
        int tokNdxStart = 0;
        int tokNdxEnd = tokens.size() - 1;

        while ((patNdxStart <= patNdxEnd) && (tokNdxStart <= tokNdxEnd)) { // find
            // first
            // **
            String patDir = patterns.get(patNdxStart);
            if (patDir.equals(PATH_MATCH)) {
                break;
            }
            if (!matchWildcard(tokens.get(tokNdxStart), patDir)) {
                return false;
            }
            patNdxStart++;
            tokNdxStart++;
        }
        if (tokNdxStart > tokNdxEnd) {
            for (int i = patNdxStart; i <= patNdxEnd; i++) { // string is
                // finished
                if (!patterns.get(i).equals(PATH_MATCH)) {
                    return false;
                }
            }
            return true;
        }
        if (patNdxStart > patNdxEnd) {
            return false; // string is not finished, but pattern is
        }

        while ((patNdxStart <= patNdxEnd) && (tokNdxStart <= tokNdxEnd)) { // to
            // the
            // last
            // **
            String patDir = patterns.get(patNdxEnd);
            if (patDir.equals(PATH_MATCH)) {
                break;
            }
            if (!matchWildcard(tokens.get(tokNdxEnd), patDir)) {
                return false;
            }
            patNdxEnd--;
            tokNdxEnd--;
        }
        if (tokNdxStart > tokNdxEnd) {
            for (int i = patNdxStart; i <= patNdxEnd; i++) { // string is
                // finished
                if (!patterns.get(i).equals(PATH_MATCH)) {
                    return false;
                }
            }
            return true;
        }

        while ((patNdxStart != patNdxEnd) && (tokNdxStart <= tokNdxEnd)) {
            int patIdxTmp = -1;
            for (int i = patNdxStart + 1; i <= patNdxEnd; i++) {
                if (patterns.get(i).equals(PATH_MATCH)) {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp == patNdxStart + 1) {
                patNdxStart++; // skip **/** situation
                continue;
            }
            // find the pattern between padIdxStart & padIdxTmp in str between
            // strIdxStart & strIdxEnd
            int patLength = (patIdxTmp - patNdxStart - 1);
            int strLength = (tokNdxEnd - tokNdxStart + 1);
            int ndx = -1;

            for (int i = 0; i <= strLength - patLength; i++) {
                boolean match = true;
                for (int j = 0; j < patLength; j++) {
                    String subPat = patterns.get(patNdxStart + j + 1);
                    String subStr = tokens.get(tokNdxStart + i + j);
                    if (!matchWildcard(subStr, subPat)) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    ndx = tokNdxStart + i;
                }
            }

            if (ndx == -1) {
                return false;
            }

            patNdxStart = patIdxTmp;
            tokNdxStart = ndx + patLength;
        }

        for (int i = patNdxStart; i <= patNdxEnd; i++) {
            if (!patterns.get(i).equals(PATH_MATCH)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks whether a string matches a given wildcard pattern.
     *
     * @param string  input string
     * @param pattern pattern to match
     * @return <code>true</code> if string matches the pattern, otherwise <code>false</code>
     */
    @Deterministic
    public static boolean matchWildcard(CharSequence string, CharSequence pattern) {
        return matchWildcard(string, pattern, 0, 0);
    }

    /**
     * Internal matching recursive function.
     */
    private static boolean matchWildcard(CharSequence string, CharSequence pattern, int sNdx, int pNdx) {
        int pLen = pattern.length();
        if (pLen == 1) {
            if (pattern.charAt(0) == '*') { // speed-up
                return true;
            }
        }
        int sLen = string.length();
        boolean nextIsNotWildcard = false;

        while (true) {

            // check if end of string and/or pattern occurred
            if ((sNdx >= sLen)) { // end of string still may have pending '*' in
                // pattern
                while ((pNdx < pLen) && (pattern.charAt(pNdx) == '*')) {
                    pNdx++;
                }
                return pNdx >= pLen;
            }
            if (pNdx >= pLen) { // end of pattern, but not end of the string
                return false;
            }
            char p = pattern.charAt(pNdx); // pattern char

            // perform logic
            if (!nextIsNotWildcard) {

                if (p == '\\') {
                    pNdx++;
                    nextIsNotWildcard = true;
                    continue;
                }
                if (p == '?') {
                    sNdx++;
                    pNdx++;
                    continue;
                }
                if (p == '*') {
                    char pNext = 0; // next pattern char
                    if (pNdx + 1 < pLen) {
                        pNext = pattern.charAt(pNdx + 1);
                    }
                    if (pNext == '*') { // double '*' have the same effect as
                        // one '*'
                        pNdx++;
                        continue;
                    }
                    int i;
                    pNdx++;

                    // find recursively if there is any substring from the end
                    // of the
                    // line that matches the rest of the pattern !!!
                    for (i = string.length(); i >= sNdx; i--) {
                        if (matchWildcard(string, pattern, i, pNdx)) {
                            return true;
                        }
                    }
                    return false;
                }
            } else {
                nextIsNotWildcard = false;
            }

            // check if pattern char and string char are equals
            if (p != string.charAt(sNdx)) {
                return false;
            }

            // everything matches for now, continue
            sNdx++;
            pNdx++;
        }
    }

    /**
     * Returns a substring by removing the specified suffix. If the given string does not ends with the suffix, the
     * string is returned without change.
     *
     * @param str
     * @param suffix
     * @return
     */
    @Deterministic
    public static String removeEnd(String str, String suffix) {
        if (str.endsWith(suffix)) {
            return str.substring(0, str.length() - suffix.length());
        } else {
            return str;
        }
    }

    @Deterministic
    public static String removeStart(String str, String prefix) {
        if (str.startsWith(prefix)) {
            return str.substring(prefix.length());
        } else {
            return str;
        }
    }

    @Deterministic
    public static String chompStartChars(String str, String chars) {
        if (str == null || str.isEmpty())
            return str;
        char c = str.charAt(0);
        if (chars.indexOf(c) < 0)
            return str;
        int i = 0;
        for (int n = str.length(); i < n; i++) {
            if (chars.indexOf(str.charAt(i)) < 0)
                break;
        }
        return str.substring(i);
    }

    /**
     * 将复合属性名a.b.c编码为 a$b$c, 便于前台数据绑定框架使用
     */
    @Deterministic
    public static String encodeProp(String name) {
        if (name == null)
            return null;
        return name.replace('.', '$');
    }

    @Deterministic
    public static String decodeProp(String name) {
        if (name == null)
            return null;
        return name.replace('$', '.');
    }

    public static RawText asRawText(String str) {
        if (str == null)
            return null;
        return new RawText(str);
    }

    @Deterministic
    public static Integer parseInt(String s, int radix) {
        if (s == null)
            return null;
        return Integer.parseInt(s, radix);
    }

    @Deterministic
    public static String formatDate(Date date, String pattern) {
        return DateHelper.formatJavaDate(date, pattern);
    }

    @Deterministic
    public static String formatLocalDate(LocalDate date, String pattern) {
        return DateHelper.formatDate(date, pattern);
    }

    @Deterministic
    public static String formatLocalDateTime(LocalDateTime date, String pattern) {
        return DateHelper.formatDateTime(date, pattern);
    }

    @Deterministic
    public static String formatNumber(Number num, String pattern) {
        if (num == null)
            return null;
        DecimalFormat fmt = new DecimalFormat(pattern);
        return fmt.format(num);
    }

    @Deterministic
    public static String formatNumber(Number num) {
        if (num == null)
            return null;

        Class<?> clazz = num.getClass();
        if (clazz == Integer.class || clazz == Long.class || clazz == BigInteger.class)
            return num.toString();

        if (clazz == BigDecimal.class) {
            // 从double直接构造得到的BigDecimal会存在很多小数位数
            BigDecimal d = (BigDecimal) num;
            if (d.scale() < 20)
                return d.toString();
        }

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(20);
        nf.setGroupingUsed(false);
        return nf.format(num.doubleValue());
    }

    @Deterministic
    public static String getXmlProlog(String encoding) {
        if (encoding == null)
            encoding = "UTF-8";
        return "<?xml version=\"1.0\" encoding=\"" + encoding + "\" ?>\n";
    }

    /**
     * Tokenize the given {@code String} into a {@code String} array via a {@link StringTokenizer}.
     * <p>
     * Trims tokens and omits empty tokens.
     * <p>
     * The given {@code delimiters} string can consist of any number of delimiter characters. Each of those characters
     * can be used to separate tokens. A delimiter is always a single character; for multi-character delimiters,
     * consider using {link #delimitedListToStringArray}.
     *
     * @param str        the {@code String} to tokenize
     * @param delimiters the delimiter characters, assembled as a {@code String} (each of the characters is individually
     *                   considered as a delimiter)
     * @return an array of the tokens
     * @see java.util.StringTokenizer
     * @see String#trim()
     */
    @Deterministic
    public static String[] tokenizeToStringArray(String str, String delimiters) {
        return tokenizeToStringArray(str, delimiters, true, true);
    }

    /**
     * Tokenize the given {@code String} into a {@code String} array via a {@link StringTokenizer}.
     * <p>
     * The given {@code delimiters} string can consist of any number of delimiter characters. Each of those characters
     * can be used to separate tokens. A delimiter is always a single character; for multi-character delimiters,
     * consider using {link #delimitedListToStringArray}.
     *
     * @param str               the {@code String} to tokenize
     * @param delimiters        the delimiter characters, assembled as a {@code String} (each of the characters is individually
     *                          considered as a delimiter)
     * @param trimTokens        trim the tokens via {@link String#trim()}
     * @param ignoreEmptyTokens omit empty tokens from the result array (only applies to tokens that are empty after trimming;
     *                          StringTokenizer will not consider subsequent delimiters as token in the first place).
     * @return an array of the tokens ({@code null} if the input {@code String} was {@code null})
     * @see java.util.StringTokenizer
     * @see String#trim()
     */
    @Deterministic
    public static String[] tokenizeToStringArray(String str, String delimiters, boolean trimTokens,
                                                 boolean ignoreEmptyTokens) {

        if (str == null) {
            return null;
        }

        StringTokenizer st = new StringTokenizer(str, delimiters);
        List<String> tokens = new ArrayList<>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (trimTokens) {
                token = token.trim();
            }
            if (!ignoreEmptyTokens || token.length() > 0) {
                tokens.add(token);
            }
        }
        return toStringArray(tokens);
    }

    /**
     * Copy the given {@code Collection} into a {@code String} array.
     *
     * @param collection the {@code Collection} to copy
     * @return the {@code String} array ({@code null} if the supplied {@code Collection} was {@code null})
     */
    @Deterministic
    public static String[] toStringArray(Collection<?> collection) {
        if (collection == null) {
            return null;
        }

        String[] ret = new String[collection.size()];
        int i = 0;
        for (Object item : collection) {
            ret[i] = toString(item, null);
            i++;
        }
        return ret;
    }

    @Deterministic
    public static boolean isValidFormat(String value, String format) {
        return FormatCheckers.instance().isValidFormat(value, format);
    }

    /**
     * Tests if this element has a class. Case insensitive.
     *
     * @param className name of class to check for
     * @return true if it does, false if not
     */
    // performance sensitive
    @Deterministic
    public static boolean hasCssClass(String classAttr, String className) {
        if (classAttr == null)
            return false;

        final int len = classAttr.length();
        final int wantLen = className.length();

        if (len == 0 || len < wantLen) {
            return false;
        }

        // if both lengths are equal, only need compare the className with the attribute
        if (len == wantLen) {
            return className.equalsIgnoreCase(classAttr);
        }

        // otherwise, scan for whitespace and compare regions (with no string or arraylist allocations)
        boolean inClass = false;
        int start = 0;
        for (int i = 0; i < len; i++) {
            if (Character.isWhitespace(classAttr.charAt(i))) {
                if (inClass) {
                    // white space ends a class name, compare it with the requested one, ignore case
                    if (i - start == wantLen && classAttr.regionMatches(true, start, className, 0, wantLen)) {
                        return true;
                    }
                    inClass = false;
                }
            } else {
                if (!inClass) {
                    // we're in a class name : keep the start of the substring
                    inClass = true;
                    start = i;
                }
            }
        }

        // check the last entry
        if (inClass && len - start == wantLen) {
            return classAttr.regionMatches(true, start, className, 0, wantLen);
        }

        return false;
    }

    @Deterministic
    public static boolean classInPackage(String className, String packageName) {
        if (isEmpty(packageName))
            return className.indexOf('.') < 0;
        if (className.length() < packageName.length() + 1)
            return false;
        return className.charAt(packageName.length()) == '.' && className.startsWith(packageName);
    }

    @Deterministic
    public static boolean hasNamespace(String str) {
        if (isEmpty(str))
            return false;
        return str.indexOf(':') > 0;
    }

    @Deterministic
    public static String getNamespace(String str) {
        if (str == null)
            return null;
        int pos = str.indexOf(':');
        if (pos < 0)
            return null;
        return str.substring(0, pos);
    }

    public static void forEachTemplateVar(String message, Consumer<String> action) {
        forEachTemplateVar(message, "{", "}", action);
    }

    public static void forEachTemplateVar(String message, String placeholderStart, String placeholderEnd,
                                          Consumer<String> action) {
        if (message == null)
            return;

        int pos = message.indexOf(placeholderStart);
        if (pos < 0)
            return;

        pos += placeholderStart.length();
        int pos2 = message.indexOf(placeholderEnd, pos);
        if (pos2 < 0)
            return;

        do {
            String name = message.substring(pos, pos2).trim();
            action.accept(name);

            pos2 = pos2 + placeholderEnd.length();
            pos = message.indexOf(placeholderStart, pos2);
            if (pos < 0) {
                break;
            }

            pos += placeholderStart.length();
            pos2 = message.indexOf(placeholderEnd, pos);
            if (pos2 < 0) {
                break;
            }
        } while (true);
    }

    static final char[] ENV_ESCAPE_CHARS = new char[]{'_', '-', '.'};
    static final String[] ENV_ESCAPE_STRS = new String[]{"___", "__", "_"};

    /**
     * 环境变量只允许ascii码和下划线，而配置变量名会使用.和-分隔，因此为了保证双向转换， 约定配置变量名中的_被替换为三个_, 而-被替换为两个_，最后.被替换为_。
     *
     * @param env 环境变量名
     * @return 全小写的配置变量名
     */
    @Deterministic
    public static String envToConfigVar(String env) {
        if (env == null)
            return null;
        String lower = env.toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = lower.length(); i < n; i++) {
            char c = lower.charAt(i);
            if (c == '_') {
                if (isChar(lower, i + 1, '_')) {
                    if (isChar(lower, i + 2, '_')) {
                        sb.append('_');
                        i += 2;
                    } else {
                        sb.append('-');
                        i += 1;
                    }
                } else {
                    sb.append('.');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isChar(String str, int offset, char c) {
        if (str.length() <= offset)
            return false;
        return str.charAt(offset) == c;
    }

    /**
     * 环境变量只允许ascii码和下划线，而配置变量名会使用.和-分隔，因此为了保证双向转换， 约定配置变量名中的_被替换为三个_, 而-被替换为两个_，最后.被替换为_。
     *
     * @param configVar 配置变量名
     * @return 全大写的环境变量名
     */
    @Deterministic
    public static String configVarToEnv(String configVar) {
        if (configVar == null)
            return null;
        String upper = configVar.toUpperCase();
        return escape(upper, ENV_ESCAPE_CHARS, ENV_ESCAPE_STRS);
    }

    /**
     * 是否是有效的配置变量名。配置变量名只能是数字和ASCII码，字符underscore、dot和hyphen组成，数字不能是第一个字符。 而且结尾字符不能是dot或者hyphen。
     *
     * @param str 配置变量名，为空时返回false
     * @return 是否合法
     */
    @Deterministic
    public static boolean isValidConfigVar(String str) {
        if (StringHelper.isEmpty(str))
            return false;

        // 如果首字母是%，则应该是profile
        int pos = 0;
        if (str.charAt(0) == '%') {
            pos = str.indexOf('.');
            if (pos < 0)
                return false;
            String profile = str.substring(1, pos);
            if (!isValidXmlName(profile))
                return false;
            pos++;
        }

        // 0： 初始状态，不能是数字，1：可以是ascii字符、数字、dot、hyphen，2：只能是ascii字符或者数字，3: 只能是数字、dot
        // 4: 不能是hyphen和dot
        int state = 0;
        for (int i = pos, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            switch (state) {
                case 0:
                    if (isAsciiLetter(c) || c == '_') {
                        state = 1;
                    } else {
                        return false;
                    }
                    break;
                case 1:
                    if (c == '.') {
                        state = 2;
                    } else if (c == '-') {
                        state = 4;
                    } else if (c != '_' && !isAsciiLetter(c) && !isDigit(c)) {
                        return false;
                    }
                    break;
                case 2:
                    if (isDigit(c)) {
                        state = 3;
                    } else if (isAsciiLetter(c) || c == '_') {
                        state = 1;
                    } else {
                        return false;
                    }
                    break;
                case 3:
                    if (c == '.') {
                        state = 2;
                    } else if (!isDigit(c)) {
                        return false;
                    }
                    break;
                case 4:
                    if (c == '-' || c == '.') {
                        return false;
                    }
                    if (isAsciiLetter(c) || c == '_' || isDigit(c)) {
                        state = 1;
                    }
                    break;
            }
        }

        if (state == 2)
            return false;

        return true;
    }

    @Deterministic
    public static String normalizeConfigVar(String configVar) {
        if (StringHelper.isEmpty(configVar))
            return configVar;
        return camelCaseToHyphen(configVar);
    }

    /**
     * 如果className是带有包名的全类名，则直接返回，否则拼接包名之后返回
     *
     * @param className   如果包含.则是全包名，否则为简单类名，它与packageName结合在一起构成全类名
     * @param packageName 增加的包名
     */
    @Deterministic
    public static String fullClassName(String className, String packageName) {
        if (className == null || className.isEmpty())
            return className;
        if (packageName == null || packageName.isEmpty())
            return className;
        if (className.indexOf('.') > 0)
            return className;
        return packageName + '.' + className;
    }

    @Deterministic
    public static String normalizeClassName(String className, String packageName, boolean returnValue) {
        if (className == null || className.isEmpty())
            return className;
        if (packageName == null || packageName.isEmpty())
            return className;
        if (className.indexOf('.') > 0)
            return className;

        if (className.equals("void")) {
            if (returnValue)
                return "void";
            return StdDataType.VOID.getClassName();
        }

        StdDataType type = StdDataType.fromJavaClassName(className);
        if (type != null) {
            if (Character.isLowerCase(className.charAt(0)))
                return type.getMandatoryJavaTypeName();
            return type.getJavaTypeName();
        }

        return packageName + '.' + className;
    }

    @Deterministic
    public static boolean isJavaDefaultImportType(String typeName) {
        if (isEmpty(typeName))
            return false;

        StdDataType type = StdDataType.fromJavaClassName(typeName);
        if (type != null) {
            // native type, such as int , float
            if (Character.isLowerCase(typeName.charAt(0)))
                return true;
        }
        if (typeName.startsWith("java.lang.") && countChar(typeName, '.') == 2) {
            return true;
        }
        return false;
    }

    @Deterministic
    public static String classNameToPath(String className) {
        if (className == null)
            return null;
        return className.replace('.', '/');
    }

    @Deterministic
    public static String simpleClassName(String className) {
        return StringHelper.lastPart(className, '.');
    }

    @Deterministic
    public static String packageName(String className) {
        if (className == null)
            return null;
        int pos = className.lastIndexOf('.');
        if (pos < 0)
            return "";
        return className.substring(0, pos);
    }

    @Deterministic
    public static String methodGet(String fieldName) {
        return "get" + StringHelper.capitalize(fieldName);
    }

    @Deterministic
    public static String methodGet(String fieldName, boolean booleanField) {
        if (booleanField)
            return "is" + StringHelper.capitalize(fieldName);
        return "get" + StringHelper.capitalize(fieldName);
    }

    @Deterministic
    public static String methodSet(String fieldName) {
        return "set" + StringHelper.capitalize(fieldName);
    }

    @Deterministic
    public static String methodMake(String fieldName) {
        return "make" + StringHelper.capitalize(fieldName);
    }

    @Deterministic
    public static String methodAdd(String fieldName) {
        return "add" + StringHelper.capitalize(fieldName);
    }

    /**
     * 仅当str不为空时拼接前缀和后缀。例如 wrap(null,"(",")")返回null, wrap("a","(",")")返回 "(a)"
     *
     * @param str     文本对象
     * @param prefix  待拼接的前缀字符串，为空时自动被忽略
     * @param postfix 待拼接的后缀字符串，为空时自动被忽略
     * @return 拼接了前缀和后缀的字符串
     */
    @Deterministic
    public static String wrap(String str, String prefix, String postfix) {
        if (StringHelper.isEmpty(str))
            return str;

        boolean emptyPrefix = StringHelper.isEmpty(prefix);
        boolean emptyPostfix = StringHelper.isEmpty(postfix);
        if (!emptyPrefix && !emptyPostfix)
            return prefix + str + postfix;

        if (!emptyPrefix)
            return prefix + str;

        if (!emptyPostfix)
            return str + postfix;

        return str;
    }

    @Deterministic
    public static List<String> parseCsvList(String str) {
        if (isEmpty(str))
            return null;
        return stripedSplit(str, ',');
    }

    @Deterministic
    public static Set<String> parseCsvSet(String str) {
        if (isEmpty(str))
            return null;
        return new LinkedHashSet<>(stripedSplit(str, ','));
    }

    /**
     * Take a {@code String} that is a delimited list and convert it into a {@code String} array.
     * <p>
     * A single {@code delimiter} may consist of more than one character, but it will still be considered as a single
     * delimiter string, rather than as bunch of potential delimiter characters, in contrast to
     * {@link #tokenizeToStringArray}.
     *
     * @param str       the input {@code String} (potentially {@code null} or empty)
     * @param delimiter the delimiter between elements (this is a single delimiter, rather than a bunch individual delimiter
     *                  characters)
     * @return an array of the tokens in the list
     * @see #tokenizeToStringArray
     */
    @Deterministic
    public static String[] delimitedListToStringArray(@Nullable String str, @Nullable String delimiter) {
        return delimitedListToStringArray(str, delimiter, null);
    }

    /**
     * Take a {@code String} that is a delimited list and convert it into a {@code String} array.
     * <p>
     * A single {@code delimiter} may consist of more than one character, but it will still be considered as a single
     * delimiter string, rather than as bunch of potential delimiter characters, in contrast to
     * {@link #tokenizeToStringArray}.
     *
     * @param str           the input {@code String} (potentially {@code null} or empty)
     * @param delimiter     the delimiter between elements (this is a single delimiter, rather than a bunch individual delimiter
     *                      characters)
     * @param charsToDelete a set of characters to delete; useful for deleting unwanted line breaks: e.g. "\r\n\f" will delete all
     *                      new lines and line feeds in a {@code String}
     * @return an array of the tokens in the list
     * @see #tokenizeToStringArray
     */
    @Deterministic
    public static String[] delimitedListToStringArray(@Nullable String str, @Nullable String delimiter,
                                                      @Nullable String charsToDelete) {

        if (str == null) {
            return EMPTY_STRINGS;
        }
        if (delimiter == null) {
            return new String[]{str};
        }

        List<String> result = new ArrayList<>();
        if (delimiter.isEmpty()) {
            for (int i = 0; i < str.length(); i++) {
                result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
            }
        } else {
            int pos = 0;
            int delPos;
            while ((delPos = str.indexOf(delimiter, pos)) != -1) {
                result.add(deleteAny(str.substring(pos, delPos), charsToDelete));
                pos = delPos + delimiter.length();
            }
            if (str.length() > 0 && pos <= str.length()) {
                // Add rest of String, but not in case of empty input.
                result.add(deleteAny(str.substring(pos), charsToDelete));
            }
        }
        return toStringArray(result);
    }

    /**
     * Convert a comma delimited list (e.g., a row from a CSV file) into an array of strings.
     *
     * @param str the input {@code String} (potentially {@code null} or empty)
     * @return an array of strings, or the empty array in case of empty input
     */
    @Deterministic
    public static String[] commaDelimitedListToStringArray(@Nullable String str) {
        return delimitedListToStringArray(str, ",");
    }

    /**
     * Convert a comma delimited list (e.g., a row from a CSV file) into a set.
     * <p>
     * Note that this will suppress duplicates, and as of 4.2, the elements in the returned set will preserve the
     * original order in a {@link LinkedHashSet}.
     *
     * @param str the input {@code String} (potentially {@code null} or empty)
     * @return a set of {@code String} entries in the list
     */
    @Deterministic
    public static Set<String> commaDelimitedListToSet(@Nullable String str) {
        String[] tokens = commaDelimitedListToStringArray(str);
        return new LinkedHashSet<>(Arrays.asList(tokens));
    }

    /**
     * Delete all occurrences of the given substring.
     *
     * @param inString the original {@code String}
     * @param pattern  the pattern to delete all occurrences of
     * @return the resulting {@code String}
     */
    @Deterministic
    public static String delete(String inString, String pattern) {
        return replace(inString, pattern, "");
    }

    /**
     * Delete any character in a given {@code String}.
     *
     * @param inString      the original {@code String}
     * @param charsToDelete a set of characters to delete. E.g. "az\n" will delete 'a's, 'z's and new lines.
     * @return the resulting {@code String}
     */
    public static String deleteAny(String inString, @Nullable String charsToDelete) {
        if (!hasLength(inString) || !hasLength(charsToDelete)) {
            return inString;
        }

        int lastCharIndex = 0;
        char[] result = new char[inString.length()];
        for (int i = 0; i < inString.length(); i++) {
            char c = inString.charAt(i);
            if (charsToDelete.indexOf(c) == -1) {
                result[lastCharIndex++] = c;
            }
        }
        if (lastCharIndex == inString.length()) {
            return inString;
        }
        return new String(result, 0, lastCharIndex);
    }

    /**
     * Check that the given {@code String} is neither {@code null} nor of length 0.
     * <p>
     * Note: this method returns {@code true} for a {@code String} that purely consists of whitespace.
     *
     * @param str the {@code String} to check (may be {@code null})
     * @return {@code true} if the {@code String} is not {@code null} and has length
     * @see #hasText(String)
     */
    @Deterministic
    public static boolean hasLength(@Nullable String str) {
        return (str != null && !str.isEmpty());
    }

    @Deterministic
    public static String quoteIfNecessary(@Nullable String str) {
        if (str == null)
            return null;

        for (int i = 0, n = str.length(); i < n; i++) {
            if (ArrayHelper.indexOf(JAVA_ESCAPE_CHARS, str.charAt(i)) >= 0)
                return quote(str);
        }
        return str;
    }

    public static boolean xmlValueNeedEscape(String str) {
        if (str == null)
            return false;

        for (int i = 0, n = str.length(); i < n; i++) {
            if (ArrayHelper.indexOf(XML_VALUE_ESCAPE_CHARS, str.charAt(i)) >= 0)
                return true;
        }
        return false;
    }

    /**
     * 按照\n切分为多行，并trim每一行（删除首尾的空格）。并且删除首尾的空行，保留中间的空行。 规范化注释文本。注释中可能包含因为
     *
     * @param comment 注释文本
     * @return 规范化后的注释文本
     */
    @Deterministic
    public static String normalizeComment(String comment, String trimChars) {
        if (StringHelper.isEmpty(comment))
            return comment;

        StringBuilder sb = new StringBuilder();
        String[] parts = StringHelper.splitToArray(comment, '\n');
        for (int i = 0, n = parts.length; i < n; i++) {
            String part = parts[i].trim();
            if (trimChars != null && part.startsWith(trimChars)) {
                part = part.substring(trimChars.length());
            }
            parts[i] = part;
        }

        int start = 0, end = parts.length;
        for (int i = start; i < end; i++) {
            if (parts[i].isEmpty())
                break;
        }
        for (; end > 0; end--) {
            if (!parts[end - 1].isEmpty()) {
                break;
            }
        }

        for (int i = start; i < end; i++) {
            if (i != start)
                sb.append('\n');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    @Deterministic
    public static String trimLeft(String str) {
        if (str == null || str.isEmpty())
            return str;

        for (int i = 0, n = str.length(); i < n; i++) {
            if (!isWhitespace(str.charAt(i))) {
                return str.substring(i);
            }
        }
        return str;
    }

    @Deterministic
    public static String trimLeft(String str, char paddingChar) {
        if (str == null || str.isEmpty())
            return str;

        for (int i = 0, n = str.length(); i < n; i++) {
            if (str.charAt(i) != paddingChar) {
                return str.substring(i);
            }
        }
        return str;
    }

    @Deterministic
    public static String trimRight(String str, char paddingChar) {
        if (str == null || str.isEmpty())
            return str;

        for (int i = str.length() - 1; i >= 0; i--) {
            if (str.charAt(i) != paddingChar) {
                return str.substring(0, i + 1);
            }
        }
        return str;
    }


    @Deterministic
    public static boolean isGraphQLNameStart(int c) {
        return '_' == c || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    @Deterministic
    public static boolean isGraphQLNamePart(int c) {
        return '_' == c || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /**
     * 除了字符串首尾指定位数的字符之外，其他字符都被替换为*。例如电话号码只显示后四位等
     */
    @Deterministic
    public static String maskPattern(String text, String pattern) {
        if (isEmpty(text))
            return text;

        int pos = pattern.indexOf('*');
        if (pos < 0)
            return text;

        int pos2 = pattern.lastIndexOf('*');

        String first = pattern.substring(0, pos);
        String last = pattern.substring(pos2 + 1);

        int firstN = ConvertHelper.toPrimitiveInt(first, NopException::new);
        int lastN = ConvertHelper.toPrimitiveInt(last, NopException::new);

        if (firstN == 0 && lastN == 0)
            return pattern;

        if (firstN + lastN >= text.length())
            return text;

        return text.substring(0, firstN) + repeat("*", text.length() - firstN - lastN)
                + text.substring(text.length() - lastN);
    }

    @Deterministic
    public static String mergeCsvSet(String setA, String setB) {
        if (StringHelper.isEmpty(setA))
            return setB;
        if (StringHelper.isEmpty(setB))
            return setA;

        Set<String> s1 = ConvertHelper.toCsvSet(setA);
        Set<String> s2 = ConvertHelper.toCsvSet(setB);
        s1.addAll(s2);
        return StringHelper.join(s1, ",");
    }

    @Deterministic
    public static boolean isYes(Object value) {
        Byte b = ConvertHelper.toByte(value);
        return b != null && b == 1;
    }

    @Deterministic
    public static boolean isNo(Object value) {
        Byte b = ConvertHelper.toByte(value);
        return b == null || b == 0;
    }

    @Deterministic
    public static String i18n(String key, String defaultValue) {
        if (defaultValue == null)
            return key;
        if (defaultValue.startsWith(CommonConstants.I18N_PREFIX))
            return defaultValue;
        return key + '|' + defaultValue;
    }

    @Description("在文本内容包裹到${和}结构中，成为嵌入式表达式")
    @Deterministic
    public static String wrapExpr(@Name("expr") String expr) {
        if (isEmpty(expr))
            return expr;
        return "${" + expr + "}";
    }

    @Deterministic
    public static String simplifyStdJavaType(String typeName) {
        if (typeName == null)
            return null;

        StdDataType dataType = StdDataType.fromJavaClassName(typeName);
        if (dataType != null) {
            // 原始数据类型保持不变，例如int 与Integer都会返回StdDataType.INT
            if (typeName.indexOf('.') < 0 && Character.isLowerCase(typeName.charAt(0)))
                return typeName;

            // 简单数据类型去除包名
            if (dataType.ordinal() <= StdDataType.DURATION.ordinal())
                return dataType.getSimpleClassName();
        }

        if (dataType == StdDataType.MAP)
            return "Map";

        if (dataType == StdDataType.LIST)
            return "List";

        if (typeName.startsWith("java.util.Map<"))
            return typeName.substring("java.util.".length());

        if (typeName.startsWith("java.util.List<"))
            return typeName.substring("java.util.".length());

        if (typeName.startsWith("java.util.Set<"))
            return typeName.substring("java.util.".length());

        return typeName;
    }

    @Deterministic
    public static String simplifyJavaType(String className) {
        if (className == null)
            return null;
        if (className.startsWith("java.lang.") && countChar(className, '.') == 2) {
            return className.substring("java.lang.".length());
        }
        return className;
    }

    @Deterministic
    public static String simplifyJavaType(String className, String basePackageName) {
        if (StringHelper.startsWithPackage(className, basePackageName)) {
            String name = className.substring(basePackageName.length() + 1);
            if (name.indexOf('.') < 0)
                return name;
        }
        return simplifyJavaType(className);
    }

    public static final String UPPER_CASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LOWER_CASE = UPPER_CASE.toLowerCase();
    public static final String DIGITS = "0123456789";
    public static final String SPECIAL_CHARS = "`~!@#$%^&*()-_=+{[]}|\\:;’“<,>.?/";

    @Deterministic
    public static int countContains(String str, String set) {
        if (str == null)
            return 0;
        int cnt = 0;
        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            if (set.indexOf(c) >= 0) {
                cnt++;
            }
        }
        return cnt;
    }

    @Deterministic
    public static boolean containsUpperCase(String str) {
        return containsAnyChar(str, UPPER_CASE);
    }

    @Deterministic
    public static boolean containsLowerCase(String str) {
        return containsAnyChar(str, LOWER_CASE);
    }

    @Deterministic
    public static boolean containsDigits(String str) {
        return containsAnyChar(str, DIGITS);
    }

    @Deterministic
    public static boolean containsSpecialChar(String str) {
        return containsAnyChar(str, SPECIAL_CHARS);
    }

    @Deterministic
    public static boolean pathStartsWith(String p1, String p2) {
        if (isEmpty(p1))
            return false;
        if (p2 == null)
            return false;

        if (!p1.startsWith(p2))
            return false;
        if (p1.length() == p2.length())
            return true;
        if (p2.endsWith("/"))
            return true;
        int c = p1.charAt(p2.length());
        return c == '/';
    }

    @Deterministic
    public static boolean pathEndsWith(String p1, String p2) {
        if (isEmpty(p1))
            return false;
        if (p2 == null)
            return false;

        if (!p1.endsWith(p2))
            return false;

        if (p1.length() == p2.length())
            return true;
        if (p2.startsWith("/"))
            return true;
        int c = p1.charAt(p1.length() - p2.length());
        return c == '/';
    }

    @Deterministic
    public static long parseSize(String str) {
        if (isEmpty(str))
            return 0;
//        char c = str.charAt(str.length() - 1);
//        if (c == 'G' || c == 'g') {
//            str = str.substring(0, str.length() - 1);
//            long value = (long) (parseNumber(str).doubleValue() * 1024 * 1024 * 1024);
//            return value;
//        } else if (c == 'M' || c == 'm') {
//            str = str.substring(0, str.length() - 1);
//            long value = (long) (parseNumber(str).doubleValue() * 1024 * 1024);
//            return value;
//        } else if (c == 'K' || c == 'k') {
//            str = str.substring(0, str.length() - 1);
//            long value = (long) (parseNumber(str).doubleValue() * 1024);
//            return value;
//        } else {
        // toLong转换自动识别 20.5M这种形式
        return ConvertHelper.toLong(str);
        //}
    }

    @Deterministic
    public static boolean maybeXml(String text) {
        if (isEmpty(text))
            return true;

        int pos = CharSequenceHelper.skipWhitespace(text, 0);
        if (pos >= text.length())
            return false;

        if (text.charAt(pos) == '<' && text.indexOf('>') >= 0) {
            return true;
        }
        return false;
    }

    @Deterministic
    public static String parseGenericComponentType(String text) {
        if (StringHelper.isEmpty(text))
            return null;
        int pos = text.indexOf('<');
        if (pos < 0)
            return null;
        int endPos = text.lastIndexOf('>');
        if (endPos <= pos)
            return null;
        return text.substring(pos + 1, endPos).trim();
    }

    /**
     * Converts the given object into a string representation by calling {@link Object#toString()}
     * and formatting (possibly nested) arrays and {@code null}.
     *
     * <p>See {@link Arrays#deepToString(Object[])} for more information about the used format.
     */
    public static String arrayAwareToString(Object o) {
        final String arrayString = Arrays.deepToString(new Object[]{o});
        return arrayString.substring(1, arrayString.length() - 1);
    }

    public static boolean isSecretVar(String varName) {
        if (varName == null)
            return false;
        return varName.endsWith("secret") || varName.endsWith("password");
    }

    public static String maskSecretVar(String varName, Object value) {
        if (isSecretVar(varName)) {
            return "***";
        }
        return toString(value, null);
    }

    @Deterministic
    public static String nextName(String varName) {
        if (varName == null || varName.isEmpty())
            return "1";

        char c = varName.charAt(varName.length() - 1);
        if (c == ')') {
            int pos = varName.lastIndexOf('(');
            if (pos >= 0) {
                String seq = varName.substring(pos + 1, varName.length() - 1);
                if (StringHelper.isInt(seq)) {
                    int intValue = StringHelper.parseInt(seq, 10);
                    return varName.substring(0, pos + 1) + (intValue + 1) + ")";
                }
            }
        } else if (c == ']') {
            int pos = varName.lastIndexOf('[');
            if (pos >= 0) {
                String seq = varName.substring(pos + 1, varName.length() - 1);
                if (StringHelper.isInt(seq)) {
                    int intValue = StringHelper.parseInt(seq, 10);
                    return varName.substring(0, pos + 1) + (intValue + 1) + "]";
                }
            }
        }

        if (isDigit(c)) {
            if (c < '9') {
                char c2 = (char) (c + 1);
                return varName.substring(0, varName.length() - 1) + c2;
            } else {
                int pos = _searchNotDigit(varName);
                if (pos < 0) {
                    // 全部都是9
                    return "1" + StringHelper.repeat("0", varName.length());
                }
                int num = parseInt(varName.substring(pos + 1), 10) + 1;
                int size = varName.length() - pos;
                return varName.substring(0, pos + 1) + String.format("%0" + size + "d", num);
            }
        }
        return varName + "1";
    }

    static int _searchNotDigit(String varName) {
        for (int i = varName.length() - 1; i >= 0; i--) {
            char c = varName.charAt(i);
            if (!isDigit(c))
                return i;

            if (c < '9')
                return i - 1;
        }
        return -1;
    }

    @Deterministic
    public static boolean isValidNopModuleId(String moduleId) {
        if (moduleId.startsWith("/"))
            return false;
        int pos = moduleId.indexOf('/');
        if (pos < 0)
            return false;
        String provider = moduleId.substring(0, pos);
        if (!StringHelper.isValidSimpleVarName(provider))
            return false;
        String moduleName = moduleId.substring(pos + 1);
        if (!StringHelper.isValidSimpleVarName(moduleName))
            return false;
        return true;
    }

    @Deterministic
    public static boolean isValidNopModuleName(String moduleName) {
        int pos = moduleName.indexOf('-');
        if (pos <= 0)
            return false;
        String provider = moduleName.substring(0, pos);
        if (!StringHelper.isValidSimpleVarName(provider))
            return false;
        String subName = moduleName.substring(pos + 1);
        if (!StringHelper.isValidSimpleVarName(subName))
            return false;
        return true;
    }

    @Description("判断字符串是否是有效的USASCII字符串")
    @Deterministic
    public static boolean isUSASCII(@Name("input") String input) {
        for (int i = 0, n = input.length(); i < n; i++) {
            char c = input.charAt(i);
            if (c < 32 || c > 126) {
                return false;
            }
        }
        return true;
    }

    @Deterministic
    public static String safeXmlComment(String comment) {
        if (isEmpty(comment))
            return comment;

        comment = StringHelper.replace(comment, "--", "- - ");
        return comment;
    }

    static final char[] QUOTE_CN_CHARS = new char[]{'“', '‘'};
    static final String[] QUOTE_EN_STRS = new String[]{"\"", "'"};

    @Deterministic
    public static String normalizeChineseQuote(String str) {
        return escape(str, QUOTE_CN_CHARS, QUOTE_EN_STRS);
    }


    @Deterministic
    public static String format(String format, Object... args) {
        return String.format(format, args);
    }

    @Deterministic
    public static String mergeTagSet(String tagSet, String tagSet2) {
        if (tagSet == null)
            return tagSet2;
        if (tagSet2 == null)
            return tagSet;
        return TagsHelper.toString(TagsHelper.merge(ConvertHelper.toCsvSet(tagSet), ConvertHelper.toCsvSet(tagSet2)));
    }

    /**
     * 配置文本模板时每行的起始空格和结束空格很容易无法正确解析。规范化的形式以|为开始标记，以|为结束标记
     */
    @Deterministic
    public static String normalizeTemplate(String template) {
        if (StringHelper.isEmpty(template))
            return template;

        List<String> lines = stripedSplit(template, '\n', true);
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (String line : lines) {
            if (index > 0) {
                sb.append('\n');
                index++;
            }

            if (line == null) {
                continue;
            }
            if (line.startsWith("|")) {
                line = line.substring(1);
            }
            if (line.endsWith("|"))
                line = line.substring(0, line.length() - 1);
            sb.append(line);
        }
        return sb.toString();
    }

    @Deterministic
    public static String filePathMd5(String filePath) {
        String fileName = StringHelper.fileFullName(filePath);
        // fileName仅仅用于显示。为了避免过长，这里限制了最大长度。唯一性靠md5保证
        if (fileName.length() > 20)
            fileName = fileName.substring(0, 20);
        return md5Hash(filePath) + '-' + fileName;
    }

    @Deterministic
    public static short shortHash(String str) {
        if (str == null)
            str = "";
        int hash = HashHelper.murmur3_32(str);
        return MathHelper.toShortHash(hash);
    }

    @Deterministic
    public static boolean containsChinese(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (isChineseChar(ch)) {
                return true;
            }
        }
        return false;
    }

    @Deterministic
    public static boolean isChineseChar(char c) {
        // 判断基本汉字和扩展 A 区（可自行扩展其他区）
        return (c >= '\u4E00' && c <= '\u9FFF') ||
                (c >= '\u3400' && c <= '\u4DBF');
    }

    @Deterministic
    public static int countChinese(String text) {
        if (text == null || text.isEmpty())
            return 0;

        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 判断基本汉字和扩展 A 区（可自行扩展其他区）
            if (isChineseChar(c)) {
                count++;
            }
        }
        return count;
    }

    // 判断是否是合法的数字编号（如 "2" 或 "3.2.1"）
    @Deterministic
    public static boolean isNumberedPrefix(String s) {
        List<String> parts = StringHelper.split(s, '.');
        for (String part : parts) {
            if (!StringHelper.isAllDigit(part)) { // 每个部分必须是纯数字
                return false;
            }
        }
        return !s.isEmpty();
    }

    @Deterministic
    public static boolean isPrintable(String s) {
        boolean printable = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            printable |= !Character.isISOControl(c) && block != null && block != Character.UnicodeBlock.SPECIALS;
        }
        return printable;
    }

}