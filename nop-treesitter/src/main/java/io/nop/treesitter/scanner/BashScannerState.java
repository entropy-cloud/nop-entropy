package io.nop.treesitter.scanner;

import io.nop.treesitter.scanner.BashCharClass.CString;

import java.util.ArrayList;
import java.util.List;

/**
 * The mutable cross-call state of {@link BashScanner}, mirroring the
 * {@code Scanner} / {@code Heredoc} structs of the upstream {@code scanner.c}.
 *
 * <p>Serialized layout (the C function returns {@code 0} — "give up
 * serializing" — when a heredoc would not fit in
 * {@code TREE_SITTER_SERIALIZATION_BUFFER_SIZE}, i.e. 1024 bytes; see
 * {@link #serialize()} for the Java mapping):</p>
 *
 * <pre>
 *   byte 0        last_glob_paren_depth
 *   byte 1        ext_was_in_double_quote
 *   byte 2        ext_saw_outside_quote
 *   byte 3        heredoc count            (truncated to 8 bits, as in C)
 *   per heredoc:
 *     byte        is_raw
 *     byte        started
 *     byte        allows_indent
 *     4 bytes     delimiter length, little endian (includes the trailing NUL)
 *     n bytes     delimiter contents (includes the trailing NUL)
 * </pre>
 *
 * <p>The C original carries {@code ext_was_in_double_quote} and
 * {@code ext_saw_outside_quote} on the struct and round-trips them through
 * {@code serialize}/{@code deserialize}, but no code path ever reads or writes
 * them. They are kept here so the serialized layout stays byte-identical to
 * upstream; they are always {@code false}.</p>
 */
final class BashScannerState {

    /** {@code TREE_SITTER_SERIALIZATION_BUFFER_SIZE}. */
    static final int SERIALIZATION_BUFFER_SIZE = 1024;

    static final class Heredoc {
        boolean isRaw;
        boolean started;
        boolean allowsIndent;
        final CString delimiter = new CString();
        final CString currentLeadingWord = new CString();

        Heredoc() {
        }

        Heredoc(boolean allowsIndent) {
            this.allowsIndent = allowsIndent;
        }

        /** {@code reset_heredoc}: note it does <em>not</em> touch the leading word. */
        void reset() {
            isRaw = false;
            started = false;
            allowsIndent = false;
            delimiter.clear();
        }
    }

    int lastGlobParenDepth;

    /** Never read or written upstream; serialized for layout fidelity only. */
    boolean extWasInDoubleQuote;

    /** Never read or written upstream; serialized for layout fidelity only. */
    boolean extSawOutsideQuote;

    private final List<Heredoc> heredocs = new ArrayList<>();

    int heredocCount() {
        return heredocs.size();
    }

    Heredoc lastHeredoc() {
        return heredocs.get(heredocs.size() - 1);
    }

    /** {@code array_back} on a possibly empty stack: {@code null} when empty. */
    Heredoc lastHeredocIfAny() {
        return heredocs.isEmpty() ? null : heredocs.get(heredocs.size() - 1);
    }

    void pushHeredoc(boolean allowsIndent) {
        heredocs.add(new Heredoc(allowsIndent));
    }

    void popHeredoc() {
        heredocs.remove(heredocs.size() - 1);
    }

    /** {@code reset}: reset every heredoc, keeping the stack size. */
    void reset() {
        for (Heredoc heredoc : heredocs) {
            heredoc.reset();
        }
    }

    /**
     * The C {@code serialize} gives up (returns {@code 0}) as soon as the next
     * heredoc would not fit in the fixed 1024-byte buffer. Java returns a
     * {@code byte[]} with no length cap, so the faithful mapping of "give up"
     * is an empty array, which {@link #deserialize(byte[])} and the runtime
     * both already read as "no state" — the same mapping the C# scanner uses.
     */
    byte[] serialize() {
        int size = 4;
        for (Heredoc heredoc : heredocs) {
            if (size + 3 + 4 + heredoc.delimiter.size() >= SERIALIZATION_BUFFER_SIZE) {
                return new byte[0];
            }
            size += 3 + 4 + heredoc.delimiter.size();
        }
        byte[] out = new byte[size];
        out[0] = (byte) lastGlobParenDepth;
        out[1] = (byte) (extWasInDoubleQuote ? 1 : 0);
        out[2] = (byte) (extSawOutsideQuote ? 1 : 0);
        out[3] = (byte) heredocs.size();
        int offset = 4;
        for (Heredoc heredoc : heredocs) {
            out[offset++] = (byte) (heredoc.isRaw ? 1 : 0);
            out[offset++] = (byte) (heredoc.started ? 1 : 0);
            out[offset++] = (byte) (heredoc.allowsIndent ? 1 : 0);
            int delimiterSize = heredoc.delimiter.size();
            out[offset++] = (byte) delimiterSize;
            out[offset++] = (byte) (delimiterSize >> 8);
            out[offset++] = (byte) (delimiterSize >> 16);
            out[offset++] = (byte) (delimiterSize >> 24);
            heredoc.delimiter.writeTo(out, offset, delimiterSize);
            offset += delimiterSize;
        }
        return out;
    }

    /** The C {@code deserialize}; note it never shrinks the heredoc stack. */
    void deserialize(byte[] state) {
        if (state == null || state.length == 0) {
            reset();
            return;
        }
        lastGlobParenDepth = state[0];
        extWasInDoubleQuote = state[1] != 0;
        extSawOutsideQuote = state[2] != 0;
        int count = state[3] & 0xFF;
        int size = 4;
        for (int i = 0; i < count; i++) {
            if (i >= heredocs.size()) {
                heredocs.add(new Heredoc());
            }
            Heredoc heredoc = heredocs.get(i);
            heredoc.isRaw = state[size++] != 0;
            heredoc.started = state[size++] != 0;
            heredoc.allowsIndent = state[size++] != 0;
            int delimiterSize = (state[size] & 0xFF)
                    | ((state[size + 1] & 0xFF) << 8)
                    | ((state[size + 2] & 0xFF) << 16)
                    | ((state[size + 3] & 0xFF) << 24);
            size += 4;
            if (delimiterSize > 0) {
                if (size + delimiterSize > state.length) {
                    throw new IllegalStateException(
                            "BashScanner state truncated: heredoc " + i + " delimiter needs "
                                    + delimiterSize + " bytes but only " + (state.length - size) + " remain");
                }
                heredoc.delimiter.readFrom(state, size, delimiterSize);
                size += delimiterSize;
            } else {
                heredoc.delimiter.clear();
            }
        }
    }
}
