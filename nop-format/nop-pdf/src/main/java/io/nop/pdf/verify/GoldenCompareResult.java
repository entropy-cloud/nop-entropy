package io.nop.pdf.verify;

import java.nio.file.Path;

/**
 * Outcome of a golden snapshot comparison. Status semantics (plan 2463):
 *
 * <ul>
 *     <li>{@link #PASS}: golden existed and matched pixel-identically.</li>
 *     <li>{@link #BASELINE_CREATED}: no golden existed; the actual image was saved as the
 *     new baseline. This is NOT a pass — first run of a new fixture must be reviewed.</li>
 *     <li>{@link #UPDATED}: golden existed but differed and update mode
 *     ({@code nop.pdf.golden.update=true}) overwrote it. NOT a pass.</li>
 *     <li>{@link #FAIL}: golden existed, differed, and update mode was off.</li>
 * </ul>
 */
public final class GoldenCompareResult {

    public enum Status {
        PASS, BASELINE_CREATED, UPDATED, FAIL
    }

    private final Status status;
    private final Path goldenFile;
    private final ImageDiffResult diff;

    GoldenCompareResult(Status status, Path goldenFile, ImageDiffResult diff) {
        this.status = status;
        this.goldenFile = goldenFile;
        this.diff = diff;
    }

    static GoldenCompareResult pass(Path goldenFile) {
        return new GoldenCompareResult(Status.PASS, goldenFile, null);
    }

    static GoldenCompareResult baselineCreated(Path goldenFile) {
        return new GoldenCompareResult(Status.BASELINE_CREATED, goldenFile, null);
    }

    static GoldenCompareResult updated(Path goldenFile) {
        return new GoldenCompareResult(Status.UPDATED, goldenFile, null);
    }

    static GoldenCompareResult fail(Path goldenFile, ImageDiffResult diff) {
        return new GoldenCompareResult(Status.FAIL, goldenFile, diff);
    }

    public Status getStatus() {
        return status;
    }

    public Path getGoldenFile() {
        return goldenFile;
    }

    /**
     * Diff details, only available for {@link Status#FAIL}.
     */
    public ImageDiffResult getDiff() {
        return diff;
    }

    public boolean isPass() {
        return status == Status.PASS;
    }

    @Override
    public String toString() {
        return "GoldenCompareResult{" + status + ", golden=" + goldenFile + (diff == null ? "" : ", " + diff) + '}';
    }

    /**
     * Assert that the golden comparison passed (status {@link Status#PASS} only; a freshly
     * created or updated baseline is intentionally not a pass).
     *
     * @throws AssertionError on any non-PASS status, with regeneration guidance
     */
    public void assertPass() {
        if (status != Status.PASS) {
            String detail = diff == null ? "golden snapshot was " + status
                    : "golden mismatch: " + diff;
            throw new AssertionError("Golden comparison did not pass: " + detail
                    + ". Review the rendered output, then regenerate the baseline by deleting "
                    + goldenFile + " or by rerunning with -D" + PdfGoldenManager.UPDATE_MODE_PROPERTY
                    + "=true");
        }
    }
}
