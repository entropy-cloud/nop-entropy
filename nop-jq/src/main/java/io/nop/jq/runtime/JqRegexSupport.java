package io.nop.jq.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * jq regex builtins (test/match/capture/scan/splits/sub/gsub) on top of
 * java.util.regex. Supports jq flags: g (global), i, x, s, m, n.
 */
public final class JqRegexSupport {
    private JqRegexSupport() {
    }

    /** A compiled pattern plus the jq flags it was compiled with. */
    public record JqRegex(Pattern pattern, boolean global) {
    }

    public static JqRegex compile(String pattern, String flags) {
        int javaFlags = 0;
        boolean global = false;
        if (flags != null) {
            for (char f : flags.toCharArray()) {
                switch (f) {
                    case 'g' -> global = true;
                    case 'i' -> javaFlags |= Pattern.CASE_INSENSITIVE;
                    case 'x' -> javaFlags |= Pattern.COMMENTS;
                    case 's' -> javaFlags |= Pattern.DOTALL;
                    case 'm' -> javaFlags |= Pattern.MULTILINE;
                    default -> throw new JqRuntimeException(
                            flags + " is not a valid modifier string");
                }
            }
        }
        try {
            return new JqRegex(Pattern.compile(pattern, javaFlags), global);
        } catch (PatternSyntaxException e) {
            throw new JqRuntimeException(pattern + " (Invalid regex): " + e.getMessage());
        }
    }

    public static JqRegex compile(String pattern) {
        return compile(pattern, null);
    }

    public static boolean test(String input, JqRegex regex) {
        return regex.pattern().matcher(input).find();
    }

    /** Stream of match objects; honors the g flag. */
    public static void matchAll(String input, JqRegex regex, List<JqValue> outputs) {
        Matcher m = regex.pattern().matcher(input);
        while (m.find()) {
            outputs.add(matchObject(m));
            if (!regex.global())
                return;
            if (m.end() == m.start() && m.end() < input.length()) {
                m.region(m.end() + 1, m.regionEnd() == 0 ? input.length() : m.regionEnd());
            }
        }
    }

    public static JqValue matchObject(Matcher m) {
        Map<String, JqValue> result = new LinkedHashMap<>();
        result.put("offset", JqNumber.of(m.start()));
        result.put("length", JqNumber.of(m.end() - m.start()));
        result.put("string", JqString.of(m.group()));
        result.put("captures", capturesArray(m));
        return new JqObject(result);
    }

    public static JqValue capturesArray(Matcher m) {
        List<JqValue> captures = new ArrayList<>();
        for (int i = 1; i <= m.groupCount(); i++) {
            Map<String, JqValue> cap = new LinkedHashMap<>();
            boolean matched = m.start(i) >= 0;
            cap.put("offset", matched ? JqNumber.of(m.start(i)) : JqNumber.of(-1));
            cap.put("length", JqNumber.of(matched ? m.end(i) - m.start(i) : 0));
            cap.put("string", matched ? JqString.of(m.group(i)) : JqValue.NULL);
            cap.put("name", JqValue.NULL);
            captures.add(new JqObject(cap));
        }
        return new JqArray(captures);
    }

    /** capture: named groups as an object. */
    public static JqValue capture(String input, JqRegex regex) {
        Matcher m = regex.pattern().matcher(input);
        Map<Integer, String> names = groupNameMap(regex.pattern().pattern());
        while (m.find()) {
            Map<String, JqValue> result = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : names.entrySet()) {
                int index = e.getKey();
                result.put(e.getValue(),
                        m.start(index) >= 0 ? JqString.of(m.group(index)) : JqValue.NULL);
            }
            if (!result.isEmpty())
                return new JqObject(result);
            if (m.end() == m.start() && m.end() < input.length())
                m.region(m.end() + 1, input.length());
            else
                break;
        }
        return new JqObject(Map.of());
    }

    /** Extract (?<name>...) group names mapped to their 1-based group index. */
    public static Map<Integer, String> groupNameMap(String regex) {
        Map<Integer, String> names = new LinkedHashMap<>();
        int index = 0;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == '[') {
                while (i + 1 < regex.length() && regex.charAt(i + 1) != ']') {
                    if (regex.charAt(i + 1) == '\\')
                        i++;
                    i++;
                }
                i++;
                continue;
            }
            if (c == '(') {
                if (i + 2 < regex.length() && regex.charAt(i + 1) == '?') {
                    if (regex.charAt(i + 2) == '<' && i + 3 < regex.length()
                            && regex.charAt(i + 3) != '=' && regex.charAt(i + 3) != '!') {
                        int start = i + 3;
                        int end = start;
                        while (end < regex.length() && regex.charAt(end) != '>')
                            end++;
                        index++;
                        names.put(index, regex.substring(start, end));
                        i = end;
                    }
                    // (?...) non-capturing / look-around / flag groups: no index
                    continue;
                }
                index++;
            }
        }
        return names;
    }

    /** scan: stream of matches (named/positional captures if present, else full match). */
    public static void scan(String input, JqRegex regex, List<JqValue> outputs) {
        Matcher m = regex.pattern().matcher(input);
        Map<Integer, String> names = groupNameMap(regex.pattern().pattern());
        while (m.find()) {
            if (m.groupCount() > 0) {
                Map<String, JqValue> capture = new LinkedHashMap<>();
                for (int i = 1; i <= m.groupCount(); i++) {
                    JqValue value = m.start(i) >= 0 ? JqString.of(m.group(i)) : JqValue.NULL;
                    String name = names.get(i);
                    if (name != null)
                        capture.put(name, value);
                    else
                        capture.put(String.valueOf(i - 1), value);
                }
                outputs.add(new JqObject(capture));
            } else {
                outputs.add(JqString.of(m.group()));
            }
            if (m.end() == m.start() && m.end() < input.length())
                m.region(m.end() + 1, input.length());
        }
    }

    /** splits: stream of split strings. */
    public static void splits(String input, JqRegex regex, List<JqValue> outputs) {
        Matcher m = regex.pattern().matcher(input);
        int last = 0;
        while (m.find()) {
            if (m.end() == m.start()) {
                if (m.end() < input.length())
                    m.region(m.end() + 1, input.length());
                else
                    break;
                continue;
            }
            outputs.add(JqString.of(input.substring(last, m.start())));
            last = m.end();
        }
        outputs.add(JqString.of(input.substring(last)));
    }

    /** Replacement using a pre-rendered string; honors the g flag. */
    public static String replace(String input, JqRegex regex, String replacement) {
        Matcher m = regex.pattern().matcher(input);
        boolean found = m.find();
        if (!found)
            return input;
        StringBuffer sb = new StringBuffer();
        m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        if (regex.global()) {
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
