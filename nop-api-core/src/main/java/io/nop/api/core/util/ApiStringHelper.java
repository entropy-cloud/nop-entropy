/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import io.nop.api.core.annotations.lang.Deterministic;
import io.nop.api.core.exceptions.NopException;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.nop.api.core.ApiErrors.ARG_MESSAGE;
import static io.nop.api.core.ApiErrors.ARG_VAR_NAME;
import static io.nop.api.core.ApiErrors.ERR_UTILS_TEMPLATE_VAR_NOT_ALLOW_NULL;

public class ApiStringHelper {
    public static LocalDate INVALID_DATE = LocalDate.of(0, 1, 1);
    public static LocalDate FUTURE_DATE = LocalDate.of(3000, 1, 1);

    public static final String ENCODING_UTF8 = "UTF-8";
    public static final Charset CHARSET_UTF8 = StandardCharsets.UTF_8;
    public static final Charset CHARSET_ISO_8859_1 = StandardCharsets.ISO_8859_1;

    @Deterministic
    public static boolean isEmpty(CharSequence o) {
        if (o == null)
            return true;
        return o.length() == 0;
    }

    @Deterministic
    public static boolean isEmptyObject(Object o) {
        if (o == null)
            return true;
        if (o instanceof String)
            return ((String) o).isEmpty();
        return false;
    }

    /**
     * @param s the String to check
     * @return if it is all zeros or null
     */
    @Deterministic
    public static boolean isAllZero(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) != '0') {
                return false;
            }
        }
        return s.length() > 0;
    }

    @Deterministic
    public static boolean isAllDigit(String str) {
        if (str == null || str.isEmpty())
            return false;

        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            if (c > '9' || c < '0')
                return false;
        }
        return true;
    }

    @Deterministic
    public static String strip(String str) {
        if (str == null)
            return null;
        int n = str.length();
        int i, j;
        for (i = 0; i < n; i++) {
            if (!isSpace(str.charAt(i))) {
                break;
            }
        }

        for (j = n - 1; j > i; j--) {
            if (!isSpace(str.charAt(j))) {
                break;
            }
        }
        if (i > j)
            return null;
        return str.substring(i, j + 1);
    }

    @Deterministic
    public static boolean isSpace(int ch) {
        // 专门调整了判断顺序
        return ch <= ' ' && (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f' || ch == '\b');
    }

    /**
     * Check whether the given {@code String} contains actual <em>text</em>.
     * <p>More specifically, this method returns {@code true} if the
     * {@code String} is not {@code null}, its length is greater than 0,
     * and it contains at least one non-whitespace character.
     *
     * @param str the {@code String} to check (may be {@code null})
     * @return {@code true} if the {@code String} is not {@code null}, its
     * length is greater than 0, and it does not contain whitespace only
     */
    @Deterministic
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    @Deterministic
    public static boolean isNotEmpty(String obj) {
        return !isEmpty(obj);
    }

    @Deterministic
    public static boolean isBlank(String str) {
        return !hasText(str);
    }

    @Deterministic
    public static boolean hasText(String str) {
        return str != null && !str.isEmpty() && containsText(str);
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

    @Deterministic
    public static int indexOfInRange(CharSequence str, int ch, int beginIndex, int endIndex) {
        for (int i = beginIndex; i < endIndex; i++) {
            if (str.charAt(i) == ch)
                return i;
        }
        return -1;
    }

    @Deterministic
    public static List<String> split(String str, char sep) {
        if (str == null)
            return null;
        if (str.length() == 0)
            return Collections.emptyList();
        int pos2 = str.indexOf(sep);
        if (pos2 < 0)
            return Collections.singletonList(str);
        List<String> ret = new ArrayList<>();
        ret.add(str.substring(0, pos2));

        int len = 1;
        int pos1 = pos2 + len;
        do {
            pos2 = str.indexOf(sep, pos1);
            if (pos2 < 0)
                break;
            ret.add(str.substring(pos1, pos2));
            pos1 = pos2 + len;
        } while (true);
        ret.add(str.substring(pos1));

        return ret;
    }

    @Deterministic
    public static List<String> stripedSplit(String str, char sep) {
        return stripedSplit(str, sep, false);
    }

    @Deterministic
    public static List<String> stripedSplit(String str, char sep, boolean includeNull) {
        if (str == null)
            return null;
        if (str.length() == 0)
            return Collections.emptyList();
        int pos2 = str.indexOf(sep);
        if (pos2 < 0) {
            str = strip(str);
            if (str == null)
                return Collections.emptyList();
            return Collections.singletonList(str);
        }
        List<String> ret = new ArrayList<String>();
        String s = strip(str.substring(0, pos2));
        if (includeNull || s != null)
            ret.add(s);

        int len = 1;
        int pos1 = pos2 + len;
        do {
            pos2 = str.indexOf(sep, pos1);
            if (pos2 < 0)
                break;
            s = strip(str.substring(pos1, pos2));
            if (includeNull || s != null)
                ret.add(s);
            pos1 = pos2 + len;
        } while (true);

        s = strip(str.substring(pos1));
        if (includeNull || s != null)
            ret.add(s);

        return ret;
    }

    public static String renderTemplate(String message, Function<String, Object> transformer) {
        return renderTemplate(message, "{", "}", transformer);
    }

    public static String renderTemplateForScope(String message, String placeholderStart,
                                                String placeHolderEnd, Map<String, Object> scope) {
        Guard.notNull(scope, "scope");
        return renderTemplate(message, placeholderStart, placeHolderEnd, scope::get);
    }

    public static String renderTemplate(String message, String placeholderStart,
                                        String placeHolderEnd, Function<String, Object> transformer) {
        return renderTemplate2(message, placeholderStart, placeHolderEnd, (name, sb) -> transformer.apply(name));
    }

    public static String renderTemplate2(String message, String placeholderStart,
                                         String placeholderEnd, BiFunction<String, StringBuilder, Object> transformer) {
        if (message == null)
            return null;

        int pos = message.indexOf(placeholderStart);
        if (pos < 0)
            return message;

        pos += placeholderStart.length();
        int pos2 = message.indexOf(placeholderEnd, pos);
        if (pos2 < 0)
            return message;

        StringBuilder sb = new StringBuilder(message.length() + 32);
        sb.append(message, 0, pos - placeholderStart.length());
        do {
            String name = message.substring(pos, pos2).trim();
            // 增加非空判断，可用于调试诊断
            if (name.endsWith("!")) {
                name = name.substring(0, name.length() - 1);
                Object result = transformer.apply(name, sb);
                if (result == null)
                    throw new NopException(ERR_UTILS_TEMPLATE_VAR_NOT_ALLOW_NULL)
                            .param(ARG_MESSAGE, message)
                            .param(ARG_VAR_NAME, name);
                sb.append(result);
            } else {
                Object result = transformer.apply(name, sb);
                if (result != null) {
                    sb.append(result);
                }
            }
            pos2 = pos2 + placeholderEnd.length();
            pos = message.indexOf(placeholderStart, pos2);
            if (pos < 0) {
                sb.append(message.substring(pos2));
                break;
            }
            sb.append(message, pos2, pos);

            pos += placeholderStart.length();
            pos2 = message.indexOf(placeholderEnd, pos);
            if (pos2 < 0) {
                sb.append(message.substring(pos - placeholderStart.length()));
                break;
            }
        } while (true);

        return sb.toString();
    }

    @Deterministic
    public static Map<String, String> parseSlotScope(String str) {
        if (isEmpty(str))
            return Collections.emptyMap();

        if (str.startsWith("{") && str.endsWith("}"))
            str = str.substring(1, str.length() - 1);
        return parseStringMap(str, ':', ',');
    }

    @Deterministic
    public static Map<String, String> parseStringMap(String str, char keySepChar, char itemSepChar) {
        if (isEmpty(str))
            return Collections.emptyMap();

        List<String> list = split(str, itemSepChar);
        if (list == null || list.isEmpty())
            return Collections.emptyMap();

        Map<String, String> ret = new LinkedHashMap<>();
        for (String item : list) {
            if (isBlank(item))
                continue;
            int pos = item.indexOf(keySepChar);
            if (pos < 0) {
                String key = item.trim();
                ret.put(key, key);
                continue;
            }
            String key = item.substring(0, pos).trim();
            String value = item.substring(pos + 1).trim();
            ret.put(key, value);
        }
        return ret;
    }

    @Deterministic
    public static String encodeStringMap(Map<String, String> map, char keySepChar, char itemSepChar) {
        if (map == null)
            return null;
        if (map.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey());
            sb.append(keySepChar);
            sb.append(entry.getValue());
            sb.append(itemSepChar);
        }
        return sb.toString();
    }


    @Deterministic
    public static String encodeURL(String str) {
        return encodeURL(str, ENCODING_UTF8);
    }

    @Deterministic
    public static String decodeURL(String str) {
        return decodeURL(str, ENCODING_UTF8);
    }

    @Deterministic
    public static String encodeURL(String str, String encoding) {
        if (str == null || str.isEmpty())
            return str;

        try {
            return URLEncoder.encode(str, encoding == null ? ENCODING_UTF8 : encoding);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Deterministic
    public static String decodeURL(String str, String encoding) {
        if (str == null || str.isEmpty())
            return str;

        try {
            return URLDecoder.decode(str, encoding == null ? ENCODING_UTF8 : encoding);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Deterministic
    public static String appendQuery(String url, String query) {
        if (url == null)
            return null;

        if (query == null || query.length() == 0)
            return url;

        int pos = url.indexOf('?');
        if (pos < 0)
            return url + "?" + query;
        if (url.endsWith("?"))
            return url + query;
        return url + "&" + query;
    }


    @Deterministic
    public static String join(Iterable<?> list, String sep) {
        return join(list, sep, false);
    }

    @Deterministic
    public static String join(Iterable<?> list, String sep, boolean ignoreEmpty) {
        if (list == null)
            return null;

        if (list instanceof Collection) {
            if (((Collection<?>) list).isEmpty()) {
                return "";
            }
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : list) {
            if (ignoreEmpty && isEmptyObject(o))
                continue;
            if (!first) {
                sb.append(sep);
            } else {
                first = false;
            }
            sb.append(o);
        }
        return sb.toString();
    }

    @Deterministic
    public static boolean onlyChars(String str, boolean allowAscii, boolean allowDigit, String specialChars) {
        if (isEmpty(str))
            return false;

        for (int i = 0, n = str.length(); i < n; i++) {
            char c = str.charAt(i);
            if (!isAllowChar(c, allowAscii, allowDigit, specialChars))
                return false;
        }

        return true;
    }

    @Deterministic
    public static boolean isAsciiLetter(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
    }

    @Deterministic
    public static boolean isDigit(int c) {
        return c <= '9' && c >= '0';
    }

    private static boolean isAllowChar(char c, boolean allowAscii, boolean allowDigit, String specialChars) {
        if (allowAscii && isAsciiLetter(c))
            return true;

        if (allowDigit && isDigit(c)) {
            return true;
        }
        if (specialChars != null && specialChars.indexOf(c) >= 0)
            return true;
        return false;
    }
}