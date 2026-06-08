package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingQueueShellInput extends AbstractShellInput {

    private final BlockingQueue<ShellChunk> queue;
    private volatile boolean eofReceived = false;

    public BlockingQueueShellInput(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public BlockingQueueShellInput(BlockingQueue<ShellChunk> queue) {
        this.queue = queue;
    }

    @Override
    public ShellChunk read() {
        if (eofReceived) return null;

        try {
            ShellChunk chunk = queue.take();
            if (chunk.isEof()) {
                eofReceived = true;
                return null;
            }
            return chunk;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }

    public void put(ShellChunk chunk) throws InterruptedException {
        if (eofReceived || isClosed()) {
            throw new IllegalStateException("input closed or EOF reached");
        }
        queue.put(chunk);
    }

    public void putText(String text) throws InterruptedException {
        put(ShellChunk.text(text));
    }

    public void sendEof() {
        if (!eofReceived) {
            eofReceived = true;
            queue.offer(ShellChunk.eof());
        }
    }

    @Override
    public void close() {
        eofReceived = true;
        super.close();
    }

    @Override
    public boolean isClosed() {
        return eofReceived || super.isClosed();
    }
}
