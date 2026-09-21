package io.nop.lint.core.cli;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit accounting for the files a scan skipped because no registered
 * language binding claimed their extension (design 03 §2.4 增注,
 * 2026-09-22: skipped files are counted, never silently ignored). Counts
 * accumulate per extension label ({@link TargetScanner#NO_EXTENSION} for
 * extension-less names) in first-seen order so the summary line is stable.
 */
public final class SkippedFiles {

    private final Map<String, Integer> byExtension = new LinkedHashMap<>();
    private int total;

    /**
     * Records one skipped file under its extension label.
     */
    void record(Path file, String extension) {
        String label = TargetScanner.extensionLabel(extension);
        byExtension.merge(label, 1, Integer::sum);
        total++;
    }

    /**
     * The number of skipped files the scan classified into this
     * accumulator.
     */
    public int total() {
        return total;
    }

    /**
     * Skipped-file counts keyed by extension label, in first-seen order;
     * empty when nothing was skipped.
     */
    public Map<String, Integer> byExtension() {
        return Map.copyOf(byExtension);
    }

    /**
     * The summary fragment rendered into the console report, e.g.
     * {@code "3 (md=2, (none)=1)"}; a zero total renders as {@code "0"}.
     */
    public String describe() {
        if (total == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(total).append(" (");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : byExtension.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.append(')').toString();
    }
}
