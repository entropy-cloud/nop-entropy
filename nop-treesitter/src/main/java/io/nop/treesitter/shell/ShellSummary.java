package io.nop.treesitter.shell;

import java.util.List;

/**
 * Everything the six capabilities found about a script, in one record.
 *
 * <p>{@link #proofStatus()} is a first-class field, not a fallback. A summary
 * whose proof status is "not proven" is a different thing from "no summary, here
 * is the original text", and a consumer that cannot tell those apart will treat an
 * unanalysed script as if it had been analysed and found benign. Callers are
 * expected to branch on the status, which is why it is a value here rather than
 * an absence.</p>
 */
public final class ShellSummary {

    /** The proof state of the analysed script. */
    public enum ProofStatus {
        /** Every command was proven literal. */
        PROVEN,
        /** The script was refused, with {@link #unprovenReason()} naming the closed reason. */
        NOT_PROVEN,
        /** The tree could not be produced at all (see {@link #errorMessage()}). */
        UNANALYZABLE
    }

    private final ProofStatus proofStatus;
    private final String unprovenReason;
    private final List<String> commandNames;
    private final List<String> connectors;
    private final boolean hasDynamicConstructs;
    private final List<DynamicConstruct.Kind> dynamicConstructKinds;
    private final List<ShellPath> paths;
    private final String errorMessage;

    ShellSummary(ProofStatus proofStatus, String unprovenReason, List<String> commandNames,
                 List<String> connectors, boolean hasDynamicConstructs,
                 List<DynamicConstruct.Kind> dynamicConstructKinds, List<ShellPath> paths,
                 String errorMessage) {
        this.proofStatus = proofStatus;
        this.unprovenReason = unprovenReason == null ? "" : unprovenReason;
        this.commandNames = List.copyOf(commandNames);
        this.connectors = List.copyOf(connectors);
        this.hasDynamicConstructs = hasDynamicConstructs;
        this.dynamicConstructKinds = List.copyOf(dynamicConstructKinds);
        this.paths = List.copyOf(paths);
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public ProofStatus proofStatus() {
        return proofStatus;
    }

    /** The closed-reason name when the status is {@code NOT_PROVEN}, else empty. */
    public String unprovenReason() {
        return unprovenReason;
    }

    /**
     * Command names in source order. For an unproven script these are the names
     * the harvest read, which is exactly why the status must be checked first.
     */
    public List<String> commandNames() {
        return commandNames;
    }

    /** The connectors between consecutive commands ({@code &&}, {@code ;}, {@code |}, ...). */
    public List<String> connectors() {
        return connectors;
    }

    public boolean hasDynamicConstructs() {
        return hasDynamicConstructs;
    }

    public List<DynamicConstruct.Kind> dynamicConstructKinds() {
        return dynamicConstructKinds;
    }

    public List<ShellPath> paths() {
        return paths;
    }

    /** Non-empty only when the status is {@code UNANALYZABLE}. */
    public String errorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "ShellSummary[" + proofStatus + " commands=" + commandNames
                + " dynamic=" + dynamicConstructKinds + "]";
    }
}
