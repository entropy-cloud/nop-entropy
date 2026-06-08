package io.nop.ai.shell.io;

import java.io.Closeable;

public interface IShellInput extends Closeable {

    ShellChunk read();

    void close();

    boolean isClosed();
}
