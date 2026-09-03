/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregate outcome of one conf-validate / dry-run run (item 20 / P-REQ-14): the
 * structured per-item issue list plus the CLI exit-code contract — 0 = passed
 * (explicit {@code SKIP} items are allowed and do not fail the run), 1 = at least one
 * {@code FAIL} issue.
 *
 * <p>Usage errors (missing arguments etc.) are exit code 2 and are decided by the
 * command entry, not by this report.
 */
public final class StreamConfValidationReport {

    private final List<ValidationIssue> issues = new ArrayList<>();
    private final List<String> probeLog = new ArrayList<>();

    public StreamConfValidationReport add(ValidationIssue issue) {
        issues.add(issue);
        return this;
    }

    /** Per-endpoint probe summary lines (dry-run layer 3): pass/skip/fail per family. */
    public StreamConfValidationReport addProbeLine(String line) {
        probeLog.add(line);
        return this;
    }

    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public List<String> getProbeLog() {
        return Collections.unmodifiableList(probeLog);
    }

    /** Passed = no FAIL issue (explicit skip items are allowed). */
    public boolean isPassed() {
        return issues.stream().noneMatch(i -> i.getSeverity() == ValidationIssue.Severity.FAIL);
    }

    /** CLI exit code: 0 when every check passed (skips allowed), 1 otherwise. */
    public int getExitCode() {
        return isPassed() ? 0 : 1;
    }

    /** Renders the full report: verdict, per-issue lines, then the dry-run probe log. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        long failCount = issues.stream().filter(i -> i.getSeverity() == ValidationIssue.Severity.FAIL).count();
        long skipCount = issues.size() - failCount;
        if (failCount == 0) {
            sb.append("conf-validate: OK (0 failed");
            if (skipCount > 0) {
                sb.append(", ").append(skipCount).append(" explicit skip item(s)");
            }
            sb.append(")\n");
        } else {
            sb.append("conf-validate: FAILED (").append(failCount).append(" failed, ")
                    .append(skipCount).append(" skipped)\n");
        }
        for (ValidationIssue issue : issues) {
            sb.append("  ").append(issue.describe()).append('\n');
        }
        if (!probeLog.isEmpty()) {
            sb.append("dry-run probes:\n");
            for (String line : probeLog) {
                sb.append("  ").append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
