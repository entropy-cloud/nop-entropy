package io.nop.autotest.bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Extensible sensitive-column rule set for bundle validation (plan 2026-10-01-2049-1
 * Decision A, per Approver B condition C2). The built-in default flags unmasked
 * PASSWORD/SALT-shaped columns in any table; consumers may register additional rules.
 * <p>
 * This is code-level discipline only — the repo-level gate keeping plaintext
 * credential columns out of git is a separate duty (handed over at M1.1 closure to
 * the plan that first commits a real bundle).
 */
public class FixtureBundleSensitiveRules {

    public static class Rule {
        final Pattern columnPattern;
        final Pattern tablePattern; // null = any table

        public Rule(Pattern tablePattern, Pattern columnPattern) {
            this.tablePattern = tablePattern;
            this.columnPattern = columnPattern;
        }

        boolean matches(String tableName, String columnName) {
            return (tablePattern == null || tablePattern.matcher(tableName).matches())
                    && columnPattern.matcher(columnName).matches();
        }
    }

    private final List<Rule> rules = new ArrayList<>();

    public FixtureBundleSensitiveRules() {
        // built-in defaults: PASSWORD / SALT shaped columns anywhere must be masked
        rules.add(new Rule(null, Pattern.compile("(?i)^(password|passwd|pwd|salt)$")));
    }

    public void addRule(Pattern tablePattern, Pattern columnPattern) {
        rules.add(new Rule(tablePattern, columnPattern));
    }

    /** Returns the first sensitive-but-unmasked column, or null when the row image complies. */
    public String findViolation(String tableName, List<String> csvHeaders, List<String> maskedColumns) {
        for (String header : csvHeaders) {
            for (Rule rule : rules) {
                if (rule.matches(tableName, header) && (maskedColumns == null || !maskedColumns.contains(header))) {
                    return header;
                }
            }
        }
        return null;
    }
}
