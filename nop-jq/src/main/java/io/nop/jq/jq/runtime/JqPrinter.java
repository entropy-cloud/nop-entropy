package io.nop.jq.jq.runtime;

import java.util.List;
import java.util.Map;

/**
 * Canonical jq output formatting: compact JSON with jq number formatting
 * (integral doubles print without a fractional part, NaN prints as null)
 * and minimal string escaping. Used by tostring/tojson/interpolation and
 * by error messages.
 */
public final class JqPrinter {
    private JqPrinter() {
    }

    public static String print(JqValue v) {
        StringBuilder sb = new StringBuilder();
        append(sb, v);
        return sb.toString();
    }

    public static void append(StringBuilder sb, JqValue v) {
        if (v instanceof JqNull) {
            sb.append("null");
        } else if (v instanceof JqBoolean b) {
            sb.append(b.value());
        } else if (v instanceof JqNumber n) {
            appendNumber(sb, n);
        } else if (v instanceof JqString s) {
            appendQuoted(sb, s.value());
        } else if (v instanceof JqArray arr) {
            sb.append('[');
            for (int i = 0; i < arr.size(); i++) {
                if (i > 0)
                    sb.append(',');
                append(sb, arr.get(i));
            }
            sb.append(']');
        } else if (v instanceof JqObject obj) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, JqValue> e : obj.properties().entrySet()) {
                if (!first)
                    sb.append(',');
                first = false;
                appendQuoted(sb, e.getKey());
                sb.append(':');
                append(sb, e.getValue());
            }
            sb.append('}');
        } else {
            sb.append(v);
        }
    }

    public static void appendNumber(StringBuilder sb, JqNumber n) {
        // exact rendering for values that were parsed as integers
        if (n.value() instanceof Integer || n.value() instanceof Long) {
            sb.append(n.value());
            return;
        }
        double d = n.doubleValue();
        if (Double.isNaN(d)) {
            sb.append("null");
            return;
        }
        if (Double.isInfinite(d)) {
            sb.append(d > 0 ? "1.7976931348623157e+308" : "-1.7976931348623157e+308");
            return;
        }
        if (d == Math.floor(d) && Math.abs(d) < 9.2e18) {
            sb.append((long) d);
            return;
        }
        sb.append(formatDouble(d));
    }

    /** jq error-message rendering: values longer than 11 chars get "..." appended. */
    public static String printTruncated(JqValue v) {
        String s = print(v);
        return s.length() > 11 ? s.substring(0, 11) + "..." : s;
    }

    /** Render a double the way jq does: shortest repr, lowercase exponent with explicit sign. */
    public static String formatDouble(double d) {
        String s = Double.toString(d);
        int e = s.indexOf('E');
        if (e < 0)
            return s;
        String mantissa = s.substring(0, e);
        int exp = Integer.parseInt(s.substring(e + 1));
        // strip a trailing ".0" mantissa: 1.0E17 -> 1e+17
        if (mantissa.endsWith(".0"))
            mantissa = mantissa.substring(0, mantissa.length() - 2);
        return mantissa + "e" + (exp >= 0 ? "+" : "-") + Math.abs(exp);
    }

    public static void appendQuoted(StringBuilder sb, String s) {
        sb.append('"');
        int runStart = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != '"' && c != '\\')
                continue;
            // flush the safe run before the escape
            sb.append(s, runStart, i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> sb.append(String.format("\\u%04x", (int) c));
            }
            runStart = i + 1;
        }
        sb.append(s, runStart, s.length());
        sb.append('"');
    }

    /** tostring semantics: strings stay raw, everything else is printed. */
    public static String tostring(JqValue v) {
        if (v instanceof JqString s)
            return s.value();
        return print(v);
    }

    /** jq value ordering helper used when printing needs sort keys. */
    public static int compareLists(List<JqValue> a, List<JqValue> b) {
        int n = Math.min(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            int cmp = JqOrdering.compare(a.get(i), b.get(i));
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(a.size(), b.size());
    }
}
