package io.nop.ai.shell.io;

public abstract class ShellChunk {

    private ShellChunk() {
    }

    public static ShellChunk text(String text) {
        return new TextChunk(text);
    }

    public static ShellChunk binary(byte[] data) {
        return new BinaryChunk(data);
    }

    public static ShellChunk eof() {
        return EofChunk.INSTANCE;
    }

    public boolean isText() {
        return this instanceof TextChunk;
    }

    public boolean isBinary() {
        return this instanceof BinaryChunk;
    }

    public boolean isEof() {
        return this instanceof EofChunk;
    }

    public String asText() {
        throw new UnsupportedOperationException("Not a text chunk");
    }

    public static final class TextChunk extends ShellChunk {
        private final String text;

        private TextChunk(String text) {
            this.text = text != null ? text : "";
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean isText() {
            return true;
        }

        @Override
        public String asText() {
            return text;
        }
    }

    public static final class BinaryChunk extends ShellChunk {
        private final byte[] data;

        private BinaryChunk(byte[] data) {
            this.data = data != null ? data : new byte[0];
        }

        public byte[] getData() {
            return data;
        }

        @Override
        public boolean isBinary() {
            return true;
        }
    }

    public static final class EofChunk extends ShellChunk {
        public static final EofChunk INSTANCE = new EofChunk();

        private EofChunk() {
        }

        @Override
        public boolean isEof() {
            return true;
        }
    }
}
