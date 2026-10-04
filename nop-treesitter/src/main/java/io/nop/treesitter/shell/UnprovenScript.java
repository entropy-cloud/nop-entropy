package io.nop.treesitter.shell;

import java.util.List;

/**
 * A script the analysis layer <b>refused</b> to describe as a command sequence,
 * together with the closed set of reasons why.
 *
 * <p>This type deliberately has no accessor that yields commands. The failure it
 * records — "here are the commands, maybe none" — is the failure mode that turns a
 * parser into a security hole, because an empty command list reads as "this
 * script does nothing". A caller must handle the refusal explicitly.</p>
 */
public final class UnprovenScript implements ProvenSplit {

    /** The closed reason set. */
    public enum Reason {
        /** The subtree contains an ERROR or MISSING node: a partially-understood tree is never promoted to proven. */
        SYNTAX_ERROR,
        /** A node kind outside the closed whitelist was found. */
        CONSTRUCT_OUTSIDE_WHITELIST,
        /** An expansion, substitution or redirection was found. */
        DYNAMIC_CONSTRUCT,
        /** A whitelisted node whose text is not a literal (glob, tilde, brace, quote-affecting escape, mixed concatenation). */
        NON_LITERAL_LEXICAL_FORM,
        /**
         * The script has no command in it — empty, or nothing but whitespace. A
         * proven sequence of zero commands would read as "this script does nothing",
         * which is the same dangerous misreading as an empty result for a script
         * that was never understood; both are refusals, not proofs.
         */
        NO_COMMAND,
        /** The grammar the tree came from is not the bash grammar this layer is defined against. */
        UNSUPPORTED_LANGUAGE
    }

    private final Reason reason;
    private final int byteOffset;
    private final String nodeType;
    private final String detail;

    UnprovenScript(Reason reason, int byteOffset, String nodeType, String detail) {
        this.reason = reason;
        this.byteOffset = byteOffset;
        this.nodeType = nodeType == null ? "" : nodeType;
        this.detail = detail == null ? "" : detail;
    }

    static UnprovenScript at(Reason reason, int byteOffset, String nodeType, String detail) {
        return new UnprovenScript(reason, byteOffset, nodeType, detail);
    }

    public Reason reason() {
        return reason;
    }

    /** Byte offset in the analysed script at which the refusal was decided. */
    public int byteOffset() {
        return byteOffset;
    }

    /** The node type that caused the refusal, or the empty string. */
    public String nodeType() {
        return nodeType;
    }

    /** Human-readable specifics; never required for correct handling. */
    public String detail() {
        return detail;
    }

    /** The reasons, as a list, so the refusal can cross a GraphQL boundary unchanged. */
    public List<Reason> reasons() {
        return List.of(reason);
    }

    @Override
    public String toString() {
        return "Unproven[" + reason + " @" + byteOffset
                + (nodeType.isEmpty() ? "" : " " + nodeType)
                + (detail.isEmpty() ? "" : ": " + detail) + "]";
    }
}
