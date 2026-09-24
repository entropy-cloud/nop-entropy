package io.nop.lint.graphql;

/**
 * One diagnostic without its fix carrier (roadmap item 38, plan Decision 4:
 * the fast profile's fix payload does not cross the service boundary), with
 * 1-based line numbers instead of byte ranges. Top-level record so the
 * GraphQL reflection stack derives a clean type name.
 */
public record LintDiagnosticView(String ruleId, String severity, String message,
                                 int line, int endLine) {
}
