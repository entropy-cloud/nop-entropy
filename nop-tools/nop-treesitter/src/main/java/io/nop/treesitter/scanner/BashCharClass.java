package io.nop.treesitter.scanner;

/**
 * C-locale character classification for {@link BashScanner}.
 *
 * <p>The upstream {@code scanner.c} uses {@code iswspace} / {@code iswalpha} /
 * {@code iswdigit} / {@code iswalnum} / {@code isdigit} from {@code <wctype.h>}.
 * tree-sitter's C scanner runs under the C locale, where those predicates are
 * exactly the ASCII ranges below. Java's {@code Character.isWhitespace} /
 * {@code Character.isLetter} cover the whole Unicode block and would change
 * which bytes terminate a word, so the ASCII definitions are spelled out here
 * rather than delegating to {@code java.lang.Character}.</p>
 *
 * <p>Also provides the byte-buffer primitive used for the heredoc delimiter and
 * the heredoc-end leading word. Those buffers carry a trailing {@code '\0'} in
 * the C original, and the buffer's {@code size} counts it, because
 * {@code serialize} writes {@code delimiter.size} bytes. The buffer therefore
 * keeps the terminator and reports it as part of the length.</p>
 */
final class BashCharClass {

    private BashCharClass() {
    }

    /** C locale {@code iswspace}. */
    static boolean isSpace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == 0x0B || c == 0x0C || c == '\r';
    }

    /** C locale {@code iswalpha}. */
    static boolean isAlpha(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** C locale {@code iswdigit} / {@code isdigit}. */
    static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    /** C locale {@code iswalnum}. */
    static boolean isAlnum(int c) {
        return isAlpha(c) || isDigit(c);
    }

    /**
     * A growable {@code char} buffer with a C NUL terminator, mirroring the
     * {@code typedef Array(char) String} of {@code scanner.c}.
     */
    static final class CString {
        private byte[] buf = new byte[16];
        private int size;

        void push(int c) {
            if (size == buf.length) {
                byte[] grown = new byte[buf.length * 2];
                System.arraycopy(buf, 0, grown, 0, size);
                buf = grown;
            }
            buf[size++] = (byte) c;
        }

        /**
         * {@code array_clear}: drops the contents but keeps the capacity.
         * The C original also memsets the old contents to zero; that is
         * unobservable here because {@link #size} gates every read.
         */
        void clear() {
            size = 0;
        }

        /** Element count, including the trailing NUL the C code appends. */
        int size() {
            return size;
        }

        byte at(int index) {
            return buf[index];
        }

        void set(int index, byte value) {
            buf[index] = value;
        }

        void writeTo(byte[] out, int offset, int length) {
            System.arraycopy(buf, 0, out, offset, length);
        }

        void readFrom(byte[] in, int offset, int length) {
            if (buf.length < length) {
                buf = new byte[Math.max(length, buf.length * 2)];
            }
            System.arraycopy(in, offset, buf, 0, length);
            size = length;
        }

        void reset() {
            buf = new byte[16];
            size = 0;
        }

        /** {@code strcmp(a, b) == 0} on the NUL-terminated contents. */
        boolean cStringEquals(CString other) {
            int i = 0;
            while (true) {
                boolean aEnd = i >= size;
                boolean bEnd = i >= other.size;
                if (aEnd || bEnd) {
                    // Both strings must terminate exactly here.
                    return aEnd && bEnd;
                }
                byte a = buf[i];
                byte b = other.buf[i];
                if (a == 0 || b == 0) {
                    return a == b;
                }
                if (a != b) {
                    return false;
                }
                i++;
            }
        }
    }
}
