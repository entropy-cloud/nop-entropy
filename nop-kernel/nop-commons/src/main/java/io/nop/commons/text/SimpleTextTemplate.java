package io.nop.commons.text;

import io.nop.api.core.util.Symbol;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.util.StringHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimpleTextTemplate {
    private final List<Object> parts;
    private final String placeholderStart;
    private final String placeholderEnd;

    public SimpleTextTemplate(String template, String placeholderStart, String placeholderEnd) {
        this.parts = parseTemplate(template, placeholderStart, placeholderEnd);
        this.placeholderStart = placeholderStart;
        this.placeholderEnd = placeholderEnd;
    }

    public SimpleTextTemplate(String template) {
        this(template, "{{", "}}");
    }


    public static SimpleTextTemplate of(String template, String placeholderStart, String placeholderEnd) {
        return new SimpleTextTemplate(template, placeholderStart, placeholderEnd);
    }

    public static SimpleTextTemplate of(String template) {
        return of(template, "{{", "}}");
    }

    public static SimpleTextTemplate normalize(String template) {
        return of(StringHelper.normalizeTemplate(template));
    }

    public List<Object> getParts() {
        return parts;
    }

    public void forEachVar(Consumer<String> action) {
        for (Object part : parts) {
            if (part instanceof Symbol)
                action.accept(((Symbol) part).getText());
        }
    }

    public ByteString getPrefix() {
        if (parts.isEmpty())
            return null;
        Object first = parts.get(0);
        if (first instanceof String)
            return ByteString.of(((String) first).getBytes(StandardCharsets.UTF_8));
        return null;
    }

    public String render(Map<String, Object> vars) {
        return renderBy(vars::get);
    }

    public String renderBy(Function<String, ?> fn) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Symbol) {
                Object v = fn.apply(((Symbol) part).getText());
                if (v != null)
                    sb.append(v);
            } else {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof Symbol) {
                sb.append(placeholderStart);
                sb.append(((Symbol) part).getText());
                sb.append(placeholderEnd);
            } else {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    static List<Object> parseTemplate(String message, String placeholderStart, String placeholderEnd) {
        List<Object> list = new ArrayList<>();

        if (StringHelper.isEmpty(message))
            return list;

        int pos = message.indexOf(placeholderStart);
        if (pos < 0) {
            list.add(message);
            return list;
        }

        pos += placeholderStart.length();
        int pos2 = message.indexOf(placeholderEnd, pos);
        if (pos2 < 0) {
            list.add(message);
            return list;
        }

        int pos1 = 0;
        do {
            if (pos1 != pos)
                addToList(list, message.substring(pos1, pos - placeholderStart.length()));

            String name = message.substring(pos, pos2).trim();
            list.add(Symbol.of(name));

            pos2 = pos2 + placeholderEnd.length();
            pos1 = pos2;

            pos = message.indexOf(placeholderStart, pos2);
            if (pos < 0) {
                addToList(list, message.substring(pos2));
                break;
            }

            pos += placeholderStart.length();
            pos2 = message.indexOf(placeholderEnd, pos);
            if (pos2 < 0) {
                addToList(list, message.substring(pos));
                break;
            }

        } while (true);
        return list;
    }

    static void addToList(List<Object> list, String str) {
        if (!str.isEmpty())
            list.add(str);
    }
}
