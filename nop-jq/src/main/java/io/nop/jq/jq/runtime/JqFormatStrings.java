package io.nop.jq.jq.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * jq @format string encoders: @text, @json, @html, @uri, @urid, @csv, @tsv,
 * @sh, @base64, @base64d.
 */
public final class JqFormatStrings {
    private JqFormatStrings() {
    }

    /** Apply a format to a scalar value (arrays are errors for @csv/@tsv). */
    public static String apply(String format, JqValue v) {
        return switch (format) {
            case "text" -> JqPrinter.tostring(v);
            case "json" -> JqPrinter.print(v);
            case "html" -> htmlEscape(JqPrinter.tostring(v));
            case "uri" -> uriEncode(JqPrinter.tostring(v));
            case "urid" -> uriDecode(JqPrinter.tostring(v));
            case "sh" -> shQuote(JqPrinter.tostring(v));
            case "base64" -> Base64.getEncoder().encodeToString(
                    JqPrinter.tostring(v).getBytes(StandardCharsets.UTF_8));
            case "base64d" -> new String(Base64.getDecoder().decode(
                    JqPrinter.tostring(v)), StandardCharsets.UTF_8);
            default -> throw new JqRuntimeException("Unknown format: @" + format);
        };
    }

    /** @csv over an array: strings quoted and quote-doubled, nulls empty. */
    public static String csv(JqArray arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append(csvField(arr.get(i)));
        }
        return sb.toString();
    }

    private static String csvField(JqValue v) {
        if (v instanceof JqNull)
            return "";
        if (v instanceof JqString s) {
            return "\"" + s.value().replace("\"", "\"\"") + "\"";
        }
        return JqPrinter.tostring(v);
    }

    /** @tsv over an array: nulls empty, backslash escapes for \t \n \r \\. */
    public static String tsv(JqArray arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0)
                sb.append('\t');
            sb.append(tsvField(arr.get(i)));
        }
        return sb.toString();
    }

    private static String tsvField(JqValue v) {
        if (v instanceof JqNull)
            return "";
        String raw = JqPrinter.tostring(v);
        return raw.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public static String htmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&apos;")
                .replace("\"", "&quot;");
    }

    /** @uri: percent-encode everything except RFC3986 unreserved ASCII characters. */
    public static String uriEncode(String s) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            char c = (char) (b & 0xFF);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            } else {
                sb.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return sb.toString();
    }

    public static String uriDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new JqRuntimeException("Invalid URL-encoded string: " + s);
        }
    }

    /** @sh: single-quote strings; arrays are space-joined quoted elements. */
    public static String shQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    public static String shEncode(JqValue v) {
        if (v instanceof JqArray arr) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0)
                    sb.append(' ');
                sb.append(shQuote(JqPrinter.tostring(arr.get(i))));
            }
            return sb.toString();
        }
        return shQuote(JqPrinter.tostring(v));
    }

    /** Base64 helpers kept for direct use by builtins. */
    public static String base64Encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64Decode(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }
}
