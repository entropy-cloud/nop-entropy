package io.nop.treesitter.shell;

import java.nio.charset.StandardCharsets;

/**
 * The analysed script, addressable by the runtime's coordinate system.
 *
 * <p><b>This class exists because tree-sitter's coordinates are byte offsets and
 * Java's strings are not.</b> Slicing a decoded {@code String} with
 * {@code startByte()}/{@code endByte()} is wrong for any script containing a
 * multi-byte character, and wrong in the worst possible direction: the offsets
 * overshoot, so a node's text runs past its end and swallows whatever follows. A
 * script like {@code echo é; rm -rf /data} then reports the destructive command as
 * a command named {@code m} with an empty target — while still claiming to be
 * <em>proven</em>. Everything that needs a node's text goes through here.</p>
 */
final class ShellSource {

    private final byte[] bytes;
    private final String text;

    ShellSource(byte[] bytes) {
        this.bytes = bytes == null ? new byte[0] : bytes;
        this.text = new String(this.bytes, StandardCharsets.UTF_8);
    }

    /**
     * The script's text, for whole-script operations (whitespace-only checks).
     * Never sliced by byte offset.
     */
    String text() {
        return text;
    }

    /**
     * The text of a byte range, decoded as UTF-8.
     *
     * <p>An out-of-range or inverted range yields the empty string rather than an
     * exception: a node's range is data, and a nonsensical one means "no text here",
     * not a crash. A range that starts or ends mid-character decodes to the
     * replacement character, which is visible rather than silently wrong.</p>
     */
    String slice(int startByte, int endByte) {
        if (startByte < 0 || endByte > bytes.length || startByte >= endByte) {
            return "";
        }
        return new String(bytes, startByte, endByte - startByte, StandardCharsets.UTF_8);
    }

    /** The text of a node. */
    String textOf(io.nop.treesitter.TSNode node) {
        return slice(node.startByte(), node.endByte());
    }

    /** Whether the script contains nothing but whitespace. */
    boolean isBlank() {
        return text.isBlank();
    }

    int length() {
        return bytes.length;
    }
}
