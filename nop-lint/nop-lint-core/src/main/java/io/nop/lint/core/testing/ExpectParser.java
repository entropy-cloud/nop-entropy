package io.nop.lint.core.testing;

import io.nop.core.lang.json.JsonTool;
import io.nop.lint.core.NopLintException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses a {@code *.expect} fixture file (design 03 §4.2, v1 subset:
 * {@code line / endLine / ruleId / messageContains}) into an
 * {@link ExpectModel}, enforcing the format fail-closed:
 *
 * <ul>
 *   <li>the file declares a {@code diagnostics} list with at least one
 *       entry (an empty expectation list would make an invalid fixture's
 *       assertion vacuous)</li>
 *   <li>every entry declares {@code line} (1-based integer), an optional
 *       {@code endLine} (defaulting to {@code line}), and non-blank
 *       {@code ruleId} and {@code messageContains}; {@code endLine} never
 *       precedes {@code line}</li>
 *   <li>unknown fields are rejected; the {@code fix} expectation field is
 *       rejected with a dedicated message because fix expectations need the
 *       autofix engine (roadmap item 25) — a fixture must not appear to
 *       pass while its fix expectations are silently ignored</li>
 * </ul>
 *
 * <p>Suppression fixtures (inline comments, {@code @SuppressWarnings}) are a
 * separate concern and do not belong to this runner's contract (roadmap
 * item 17).</p>
 */
public final class ExpectParser {

    private static final String DIAGNOSTICS_KEY = "diagnostics";
    private static final Set<String> TOP_LEVEL_KEYS = Set.of(DIAGNOSTICS_KEY);
    private static final Set<String> ENTRY_KEYS = Set.of("line", "endLine", "ruleId", "messageContains");

    /**
     * Parses {@code text} as the content of the {@code *.expect} file at
     * {@code resourcePath}.
     *
     * @param resourcePath the fixture path carried into error messages
     * @param text         the YAML text of the expectation file
     * @return the validated expectation model
     * @throws NopLintException on any structural violation; no failure is
     *                          silently dropped
     */
    public ExpectModel parse(String resourcePath, String text) {
        Map<String, Object> root;
        try {
            root = JsonTool.parseBeanFromYaml(text, Map.class);
        } catch (Exception e) {
            throw new NopLintException("Failed to parse lint expectation file '" + resourcePath
                    + "' as YAML: " + e.getMessage(), e);
        }
        if (root == null)
            throw new NopLintException("Expectation file '" + resourcePath + "' is empty; a '"
                    + DIAGNOSTICS_KEY + "' list with at least one entry is required");

        rejectUnknownKeys(root.keySet(), TOP_LEVEL_KEYS, resourcePath, resourcePath);

        Object value = root.get(DIAGNOSTICS_KEY);
        if (value == null)
            throw new NopLintException("Expectation file '" + resourcePath + "' is missing the '"
                    + DIAGNOSTICS_KEY + "' list (invalid fixtures must declare their expected "
                    + "diagnostics; no silent no-op)");
        if (!(value instanceof Collection<?> items))
            throw new NopLintException("Expectation file '" + resourcePath + "' declares '"
                    + DIAGNOSTICS_KEY + "' as " + value.getClass().getSimpleName()
                    + "; a list of diagnostic expectations is required");
        if (items.isEmpty())
            throw new NopLintException("Expectation file '" + resourcePath + "' declares an empty '"
                    + DIAGNOSTICS_KEY + "' list; an invalid fixture with zero expectations would "
                    + "make the assertion vacuous (no silent no-op)");

        List<ExpectDiagnostic> diagnostics = new ArrayList<>(items.size());
        int index = 0;
        for (Object item : items) {
            index++;
            diagnostics.add(parseDiagnostic(resourcePath, index, item));
        }
        return new ExpectModel(resourcePath, diagnostics);
    }

    private ExpectDiagnostic parseDiagnostic(String resourcePath, int index, Object item) {
        String at = resourcePath + " entry #" + index;
        if (!(item instanceof Map<?, ?> props))
            throw new NopLintException("Expectation " + at + " must be a mapping (got "
                    + (item == null ? "null" : item.getClass().getSimpleName()) + ")");

        rejectUnknownKeys(props.keySet(), ENTRY_KEYS, resourcePath, at);

        int line = toLine(at, "line", props.get("line"), true);
        Integer endLineValue = props.get("endLine") == null ? null
                : toLine(at, "endLine", props.get("endLine"), false);
        int endLine = endLineValue == null ? line : endLineValue;
        if (endLine < line)
            throw new NopLintException("Expectation " + at + " declares endLine=" + endLine
                    + " before line=" + line + "; line numbers are 1-based and endLine must not "
                    + "precede line");

        String ruleId = requiredText(at, "ruleId", props.get("ruleId"));
        String messageContains = requiredText(at, "messageContains", props.get("messageContains"));
        return new ExpectDiagnostic(line, endLine, ruleId, messageContains);
    }

    private void rejectUnknownKeys(Set<?> keys, Set<String> allowed, String resourcePath, String at) {
        for (Object key : keys) {
            String name = String.valueOf(key);
            if ("fix".equals(name))
                throw new NopLintException("Expectation " + at + " declares a 'fix' expectation, which "
                        + "this RuleTester version does not execute: fix expectations require the "
                        + "autofix engine (roadmap item 25). Remove the field or defer this fixture "
                        + "until item 25 lands (no silent skip of unsupported expectations)");
            if (!allowed.contains(name))
                throw new NopLintException("Expectation " + at + " declares unknown field '" + name
                        + "'; supported fields are " + sorted(allowed));
        }
    }

    private int toLine(String at, String field, Object value, boolean mandatory) {
        if (value == null) {
            if (mandatory)
                throw new NopLintException("Expectation " + at + " is missing the '" + field
                        + "' field (1-based source line)");
            return -1;
        }
        if (!(value instanceof Number number))
            throw new NopLintException("Expectation " + at + " declares '" + field + "' as "
                    + typeName(value) + "; an integer >= 1 is required");
        long raw = number.longValue();
        if (number.doubleValue() != raw)
            throw new NopLintException("Expectation " + at + " declares '" + field + "' as " + value
                    + "; a whole-number line is required (line numbers are 1-based)");
        if (raw < 1 || raw > Integer.MAX_VALUE)
            throw new NopLintException("Expectation " + at + " declares '" + field + "'=" + raw
                    + "; line numbers are 1-based and must be >= 1");
        return (int) raw;
    }

    private String requiredText(String at, String field, Object value) {
        if (value == null || String.valueOf(value).isBlank())
            throw new NopLintException("Expectation " + at + " is missing a non-blank '" + field
                    + "' field");
        if (!(value instanceof String text))
            throw new NopLintException("Expectation " + at + " declares '" + field + "' as "
                    + typeName(value) + "; a non-blank string is required");
        return text;
    }

    private String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private List<String> sorted(Set<String> names) {
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        return sorted;
    }
}
