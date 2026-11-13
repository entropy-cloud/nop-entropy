package io.nop.ib.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser for nop-ib's own state documents (model.json, unit index).
 * Counterpart of {@link JsonOut}: handles exactly the value space we write —
 * objects, arrays, strings, numbers, booleans, null. No duplicate-key tolerance:
 * duplicate keys throw (state documents are machine-written).
 */
public final class JsonIn {
    private final String text;
    private int pos;

    private JsonIn(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        JsonIn p = new JsonIn(text);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (p.pos != p.text.length())
            throw new NopIbJsonException("trailing content at offset " + p.pos);
        return v;
    }

    /** Convenience: parse and require a top-level object. */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> asMapOrEmpty(Object parsed) {
        if (parsed == null)
            return new java.util.LinkedHashMap<>();
        if (!(parsed instanceof java.util.Map))
            throw new NopIbJsonException("expected JSON object at top level");
        return (java.util.Map<String, Object>) parsed;
    }

    /** Parse an object, throwing on malformed input. */
    public static java.util.Map<String, Object> readAsMap(String text) {
        return asMapOrEmpty(parse(text));
    }

    /** Parse or return null on any malformed input (best-effort payload extraction). */
    public static Object silentParse(String text) {
        try {
            return parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    public static class NopIbJsonException extends RuntimeException {
        public NopIbJsonException(String message) {
            super(message);
        }
    }

    private Object readValue() {
        if (pos >= text.length())
            throw fail("unexpected end of input");
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return readObject();
            case '[':
                return readArray();
            case '"':
                return readString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWs();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWs();
            if (peek() != '"')
                throw fail("expected string key");
            String key = readString();
            skipWs();
            if (peek() != ':')
                throw fail("expected ':'");
            pos++;
            skipWs();
            Object value = readValue();
            if (map.putIfAbsent(key, value) != null)
                throw fail("duplicate key: " + key);
            skipWs();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                return map;
            } else {
                throw fail("expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWs();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWs();
            list.add(readValue());
            skipWs();
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                return list;
            } else {
                throw fail("expected ',' or ']'");
            }
        }
    }

    private String readString() {
        StringBuilder sb = new StringBuilder();
        pos++; // "
        while (true) {
            if (pos >= text.length())
                throw fail("unterminated string");
            char c = text.charAt(pos++);
            if (c == '"')
                return sb.toString();
            if (c == '\\') {
                if (pos >= text.length())
                    throw fail("unterminated escape");
                char e = text.charAt(pos++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'u':
                        if (pos + 4 > text.length())
                            throw fail("bad unicode escape");
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw fail("bad escape: \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Object readNumber() {
        int start = pos;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E')
                pos++;
            else
                break;
        }
        String num = text.substring(start, pos);
        if (num.isEmpty())
            throw fail("invalid value at offset " + start);
        try {
            if (num.indexOf('.') < 0 && num.indexOf('e') < 0 && num.indexOf('E') < 0)
                return Long.valueOf(num);
            return Double.valueOf(num);
        } catch (NumberFormatException ex) {
            throw (NopIbJsonException) fail("invalid number: " + num);
        }
    }

    private char peek() {
        if (pos >= text.length())
            throw fail("unexpected end of input");
        return text.charAt(pos);
    }

    private void expect(String word) {
        if (!text.startsWith(word, pos))
            throw fail("expected '" + word + "'");
        pos += word.length();
    }

    private void skipWs() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r')
                pos++;
            else
                break;
        }
    }

    private NopIbJsonException fail(String message) {
        return new NopIbJsonException(message + " (offset " + pos + ")");
    }
}
