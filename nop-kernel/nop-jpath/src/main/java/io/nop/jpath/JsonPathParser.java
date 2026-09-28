package io.nop.jpath;

import io.nop.api.core.exceptions.NopException;
import io.nop.jpath.NopJqErrors;
import io.nop.jpath.NopJqException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Recursive descent parser for JsonPath expressions.
 * Compiles a path string into a Segment[] pipeline.
 *
 * Supported syntax:
 * <ul>
 *   <li>$ — root reference</li>
 *   <li>.property — dot notation property access</li>
 *   <li>['property'] — bracket notation property access</li>
 *   <li>[index] — array index access (supports negative indices)</li>
 *   <li>[start:end] — array slice</li>
 *   <li>[*] — wildcard</li>
 *   <li>[?(@.prop op value)] — filter expression</li>
 *   <li>.. — deep scan</li>
 * </ul>
 */
public class JsonPathParser {
    private final String path;
    private int pos;

    public JsonPathParser(String path) {
        this.path = path;
        this.pos = 0;
    }

    public List<Segment> parse() {
        List<Segment> segments = new ArrayList<>();
        expect('$');
        segments.add(new RootSegment());

        while (pos < path.length()) {
            char c = path.charAt(pos);
            if (c == '.') {
                pos++;
                if (pos < path.length() && path.charAt(pos) == '.') {
                    pos++;
                    parseDeepScan(segments);
                } else {
                    parseProperty(segments);
                }
            } else if (c == '[') {
                parseBracket(segments);
            } else {
                throw error("Unexpected character: " + c);
            }
        }
        return segments;
    }

    private void parseProperty(List<Segment> segments) {
        if (pos < path.length() && path.charAt(pos) == '*') {
            pos++;
            segments.add(WildCardSegment.INSTANCE);
            return;
        }
        String name = readPropertyName();
        segments.add(new PropertySegment(name));
    }

    private void parseDeepScan(List<Segment> segments) {
        if (pos < path.length() && path.charAt(pos) == '.') {
            throw error("Unexpected '...' in path");
        }
        if (pos < path.length() && path.charAt(pos) == '[') {
            pos++;
            parseBracketDeepScan(segments);
        } else if (pos < path.length() && path.charAt(pos) != '.' && path.charAt(pos) != '[') {
            String name = readPropertyName();
            segments.add(new DeepScanSegment(name));
        } else {
            segments.add(new DeepScanSegment(null));
        }
    }

    private void parseBracket(List<Segment> segments) {
        pos++; // skip '['
        if (pos >= path.length())
            throw error("Unexpected end of path after '['");

        char c = path.charAt(pos);
        if (c == ']') {
            // Empty brackets: [] means array iterator
            pos++;
            segments.add(WildCardSegment.INSTANCE);
        } else if (c == '*') {
            pos++;
            expect(']');
            segments.add(WildCardSegment.INSTANCE);
        } else if (c == '?') {
            parseFilter(segments);
        } else if (c == '\'' || c == '"') {
            String name = readQuotedString();
            expect(']');
            segments.add(new PropertySegment(name));
        } else if (c == '-' || Character.isDigit(c)) {
            parseBracketIndexOrSlice(segments);
        } else {
            String name = readPropertyName();
            expect(']');
            segments.add(new PropertySegment(name));
        }
    }

    private void parseBracketDeepScan(List<Segment> segments) {
        if (pos >= path.length())
            throw error("Unexpected end of path after '..['");
        char c = path.charAt(pos);
        if (c == '*') {
            pos++;
            expect(']');
            segments.add(new DeepScanSegment(null));
        } else if (c == '\'' || c == '"') {
            String name = readQuotedString();
            expect(']');
            segments.add(new DeepScanSegment(name));
        } else {
            String name = readPropertyName();
            expect(']');
            segments.add(new DeepScanSegment(name));
        }
    }

    private void parseBracketIndexOrSlice(List<Segment> segments) {
        int start = readInt();
        if (pos < path.length() && path.charAt(pos) == ':') {
            pos++; // skip ':'
            int end = pos < path.length() && (Character.isDigit(path.charAt(pos)) || path.charAt(pos) == '-')
                    ? readInt()
                    : Integer.MAX_VALUE;
            expect(']');
            segments.add(new RangeSegment(start, end));
        } else {
            expect(']');
            segments.add(new ArrayAccessSegment(start));
        }
    }

    private void parseFilter(List<Segment> segments) {
        pos++; // skip '?'
        expect('(');
        Filter filter = parseFilterExpression();
        expect(')');
        expect(']');
        segments.add(new FilterSegment(filter));
    }

    private Filter parseFilterExpression() {
        Filter left = parseFilterTerm();
        while (pos < path.length()) {
            skipWhitespace();
            if (pos + 1 < path.length() && path.charAt(pos) == '&' && path.charAt(pos + 1) == '&') {
                pos += 2;
                Filter right = parseFilterTerm();
                left = new LogicFilter(left, LogicFilter.LogicOp.AND, right);
            } else if (pos + 1 < path.length() && path.charAt(pos) == '|' && path.charAt(pos + 1) == '|') {
                pos += 2;
                Filter right = parseFilterTerm();
                left = new LogicFilter(left, LogicFilter.LogicOp.OR, right);
            } else {
                break;
            }
        }
        return left;
    }

    private Filter parseFilterTerm() {
        skipWhitespace();
        expect('@');
        expect('.');
        String propName = readPropertyName();
        skipWhitespace();

        if (pos + 1 < path.length() && path.charAt(pos) == '=' && path.charAt(pos + 1) == '=') {
            pos += 2;
            Object value = parseFilterValue();
            return new CompareFilter(propName, CompareFilter.Op.EQ, value);
        }
        if (pos + 1 < path.length() && path.charAt(pos) == '!' && path.charAt(pos + 1) == '=') {
            pos += 2;
            Object value = parseFilterValue();
            return new CompareFilter(propName, CompareFilter.Op.NE, value);
        }
        if (pos < path.length() && path.charAt(pos) == '>') {
            pos++;
            if (pos < path.length() && path.charAt(pos) == '=') {
                pos++;
                Object value = parseFilterValue();
                return new CompareFilter(propName, CompareFilter.Op.GE, value);
            }
            Object value = parseFilterValue();
            return new CompareFilter(propName, CompareFilter.Op.GT, value);
        }
        if (pos < path.length() && path.charAt(pos) == '<') {
            pos++;
            if (pos < path.length() && path.charAt(pos) == '=') {
                pos++;
                Object value = parseFilterValue();
                return new CompareFilter(propName, CompareFilter.Op.LE, value);
            }
            Object value = parseFilterValue();
            return new CompareFilter(propName, CompareFilter.Op.LT, value);
        }
        if (pos < path.length() && path.charAt(pos) == '=') {
            pos++;
            if (pos < path.length() && path.charAt(pos) == '~') {
                pos++;
                skipWhitespace();
                String regex = parseFilterString();
                return new RegexFilter(propName, regex);
            }
            // single = is treated as ==
            Object value = parseFilterValue();
            return new CompareFilter(propName, CompareFilter.Op.EQ, value);
        }
        throw error("Unexpected operator in filter at position " + pos);
    }

    private Object parseFilterValue() {
        skipWhitespace();
        if (pos < path.length() && (path.charAt(pos) == '\'' || path.charAt(pos) == '"')) {
            return parseFilterString();
        }
        if (pos < path.length() && (path.charAt(pos) == '-' || Character.isDigit(path.charAt(pos)))) {
            return parseFilterNumber();
        }
        throw error("Unexpected value in filter at position " + pos);
    }

    private String parseFilterString() {
        char quote = path.charAt(pos);
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < path.length() && path.charAt(pos) != quote) {
            if (path.charAt(pos) == '\\' && pos + 1 < path.length()) {
                pos++;
                sb.append(path.charAt(pos));
            } else {
                sb.append(path.charAt(pos));
            }
            pos++;
        }
        if (pos >= path.length())
            throw error("Unterminated string in filter");
        pos++; // skip closing quote
        return sb.toString();
    }

    private Number parseFilterNumber() {
        int start = pos;
        if (pos < path.length() && path.charAt(pos) == '-')
            pos++;
        while (pos < path.length() && Character.isDigit(path.charAt(pos)))
            pos++;
        if (pos < path.length() && path.charAt(pos) == '.') {
            pos++;
            while (pos < path.length() && Character.isDigit(path.charAt(pos)))
                pos++;
            return Double.parseDouble(path.substring(start, pos));
        }
        long val = Long.parseLong(path.substring(start, pos));
        if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE)
            return (int) val;
        return val;
    }

    private String readPropertyName() {
        int start = pos;
        while (pos < path.length()) {
            char c = path.charAt(pos);
            if (c == '.' || c == '[' || c == ')' || c == ' ' || c == '\t')
                break;
            pos++;
        }
        if (pos == start)
            throw error("Expected property name at position " + pos);
        return path.substring(start, pos);
    }

    private String readQuotedString() {
        char quote = path.charAt(pos);
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < path.length() && path.charAt(pos) != quote) {
            if (path.charAt(pos) == '\\' && pos + 1 < path.length()) {
                pos++;
                sb.append(path.charAt(pos));
            } else {
                sb.append(path.charAt(pos));
            }
            pos++;
        }
        if (pos >= path.length())
            throw error("Unterminated string");
        pos++; // skip closing quote
        return sb.toString();
    }

    private int readInt() {
        int start = pos;
        if (pos < path.length() && path.charAt(pos) == '-')
            pos++;
        while (pos < path.length() && Character.isDigit(path.charAt(pos)))
            pos++;
        if (pos == start)
            throw error("Expected integer at position " + pos);
        return Integer.parseInt(path.substring(start, pos));
    }

    private void skipWhitespace() {
        while (pos < path.length() && Character.isWhitespace(path.charAt(pos)))
            pos++;
    }

    private void expect(char expected) {
        if (pos >= path.length() || path.charAt(pos) != expected) {
            throw error("Expected '" + expected + "' at position " + pos
                    + (pos < path.length() ? ", found '" + path.charAt(pos) + "'" : ", end of path"));
        }
        pos++;
    }

    private NopException error(String message) {
        return new NopJqException(NopJqErrors.ERR_JQ_COMPILE_ERROR)
                .param(NopJqErrors.ARG_EXPR, path)
                .param("detail", message);
    }
}
