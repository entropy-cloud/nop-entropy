package io.nop.jq.jsonpath;

import io.nop.jq.JsonAccessor;

import java.util.regex.Pattern;

/**
 * Regex filter that checks if a property value matches a regular expression.
 */
public class RegexFilter implements Filter {
    private final String propertyName;
    private final Pattern pattern;

    public RegexFilter(String propertyName, String regex) {
        this.propertyName = propertyName;
        this.pattern = Pattern.compile(regex);
    }

    @Override
    public boolean apply(JsonAccessor accessor, Object root, Object item) {
        Object actual = propertyName == null ? item : accessor.getProperty(item, propertyName);
        if (actual == null)
            return false;
        return pattern.matcher(actual.toString()).find();
    }

    @Override
    public String toString() {
        return propertyName + " =~ " + pattern.pattern();
    }
}
