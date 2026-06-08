package io.nop.ai.shell.io;

import java.io.Closeable;

public interface IShellOutput extends Closeable {

    void write(ShellChunk chunk);

    default void print(String text) {
        write(ShellChunk.text(text));
    }

    default void writeLine(String line) {
        write(ShellChunk.text(line + "\n"));
    }

    default void println(String text) {
        writeLine(text);
    }

    default void println() {
        write(ShellChunk.text("\n"));
    }

    default void flush() {
    }

    IShellInput asInput();

    @Override
    void close();
}
