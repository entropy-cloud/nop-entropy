package io.nop.lint.core.node;

/**
 * Byte range of a node in the parsed source (UTF-8 byte offsets; end exclusive).
 */
public record SourceRange(int startByte, int endByte) {

    public int length() {
        return endByte - startByte;
    }

    public boolean contains(int byteOffset) {
        return byteOffset >= startByte && byteOffset < endByte;
    }
}
