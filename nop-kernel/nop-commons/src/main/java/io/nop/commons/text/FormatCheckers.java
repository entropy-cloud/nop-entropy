/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text;

import com.google.common.base.CharMatcher;
import com.google.common.net.InternetDomainName;
import io.nop.commons.util.NetHelper;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class FormatCheckers {
    static final FormatCheckers _INSTANCE = new FormatCheckers();

    public static FormatCheckers instance() {
        return _INSTANCE;
    }

    Map<String, Predicate<String>> checkers = new HashMap<>();

    public static final String FORMAT_URI = "uri";
    public static final String FORMAT_IPV4 = "ipv4";
    public static final String FORMAT_IPV6 = "ipv6";
    public static final String FORMAT_IP = "ip";
    public static final String FORMAT_PHONE = "phone";
    public static final String FORMAT_EMAIL = "email";

    public static final String FORMAT_BASE64 = "base64";
    public static final String FORMAT_MD5 = "md5";
    public static final String FORMAT_SHA1 = "sha1";
    public static final String FORMAT_SHA256 = "sha256";
    public static final String FORMAT_SHA512 = "sha512";
    public static final String FORMAT_UUID = "uuid";
    public static final String FORMAT_HOST_NAME = "hostname";

    public static final String FORMAT_DATE_TIME = "date-time";
    public static final String FORMAT_REGEX = "regex";

    public static final List<String> JSON_SCHEMA_FORMATS = Arrays.asList(FORMAT_URI, FORMAT_IPV4, FORMAT_PHONE,
            FORMAT_EMAIL, FORMAT_HOST_NAME, FORMAT_DATE_TIME, FORMAT_REGEX);

    public FormatCheckers() {
        registerFormatChecker(FORMAT_IP, NetHelper::isInetAddress);
        registerFormatChecker(FORMAT_IPV4, NetHelper::isIpV4Address);
        registerFormatChecker(FORMAT_IPV6, NetHelper::isIpV6Address);

        registerFormatChecker(FORMAT_URI, FormatCheckers::isURI);
        registerFormatChecker(FORMAT_HOST_NAME, FormatCheckers::isHostName);

        registerFormatChecker(FORMAT_MD5, FormatCheckers::isMD5);
        registerFormatChecker(FORMAT_SHA1, FormatCheckers::isSha1);
        registerFormatChecker(FORMAT_SHA256, FormatCheckers::isSha256);
        registerFormatChecker(FORMAT_UUID, FormatCheckers::isUUID);
        registerFormatChecker(FORMAT_BASE64, FormatCheckers::isBase64);
    }

    public static boolean isURI(String value) {
        try {
            new URI(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Yep, a regex...
    private static final Pattern MACADDR = Pattern.compile("[A-Za-z0-9]{2}(?::[A-Za-z0-9]{2}){5}");

    public static boolean isMac(String value) {
        return MACADDR.matcher(value).matches();
    }

    private static final CharMatcher HEX_CHARS = CharMatcher.anyOf("0123456789abcdefABCDEF").precomputed();

    public static boolean isHex(String value) {
        return HEX_CHARS.matchesAllOf(value);
    }

    /*
     * Regex to accurately remove _at most two_ '=' characters from the end of the input.
     */
    private static final Pattern BASE64_PATTERN = Pattern.compile("==?$");

    /*
     * Negation of the Base64 alphabet. We try and find one character, if any, matching this "negated" character
     * matcher.
     *
     * FIXME: use .precomputed()?
     */
    private static final CharMatcher NOT_BASE64 = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'))
            .or(CharMatcher.inRange('0', '9')).or(CharMatcher.anyOf("+/")).negate();

    public static boolean isBase64(String value) {
        /*
         * The string length must be a multiple of 4. FIXME though: can it be 0? Here, it is assumed that it can, even
         * though that does not really make sense.
         */
        if (value.length() % 4 != 0) {
            return false;
        }

        final int index = NOT_BASE64.indexIn(BASE64_PATTERN.matcher(value).replaceFirst(""));

        return index == -1;
    }

    public static boolean isMD5(String value) {
        return isHex(value) && value.length() == 32;
    }

    public static boolean isSha1(String value) {
        return isHex(value) && value.length() == 40;
    }

    public static boolean isSha256(String value) {
        return isHex(value) && value.length() == 64;
    }

    public static boolean isSha512(String value) {
        return isHex(value) && value.length() == 128;
    }

    public static boolean isUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isHostName(String value) {
        try {
            InternetDomainName.from(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void registerFormatChecker(String format, Predicate<String> checker) {
        checkers.put(format, checker);
    }

    public boolean isValidFormat(String value, String format) {
        Predicate<String> predicate = checkers.get(format);
        if (predicate == null)
            return true;
        return predicate.test(value);
    }

    public boolean isFormatDefined(String format) {
        return checkers.containsKey(format);
    }

    public Predicate<String> getFormatChecker(String format) {
        return checkers.get(format);
    }
}