package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

public class PrintStreamShellOutput implements IShellOutput {

    private final PrintStream printStream;
    private volatile boolean closed = false;

    public PrintStreamShellOutput(PrintStream printStream) {
        this.printStream = printStream;
    }

    public static PrintStreamShellOutput fromFile(File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            return new PrintStreamShellOutput(new PrintStream(out));
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public void write(ShellChunk chunk) {
        if (closed) throw new IllegalStateException("output closed");
        if (chunk.isText()) {
            printStream.print(chunk.asText());
        } else if (chunk.isBinary()) {
            printStream.write(((ShellChunk.BinaryChunk) chunk).getData(), 0, ((ShellChunk.BinaryChunk) chunk).getData().length);
        }
    }

    @Override
    public void flush() {
        printStream.flush();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            printStream.close();
        }
    }

    @Override
    public IShellInput asInput() {
        throw new UnsupportedOperationException("PrintStreamOutputAdapter cannot be used as input");
    }

    protected PrintStream getPrintStream() {
        return printStream;
    }
}
