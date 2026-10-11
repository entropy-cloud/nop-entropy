package io.nop.lint.graphql;

/**
 * The rule-library metadata face ({@code Lint__listRules}); severity is the
 * top-level slot, metadata-less rules map to safe defaults.
 */
public record LintRuleView(String id, String severity, String message, String category,
                           boolean autoFixable) {
}
