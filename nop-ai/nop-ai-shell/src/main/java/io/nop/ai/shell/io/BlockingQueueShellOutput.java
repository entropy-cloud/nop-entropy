package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingQueueShellOutput implements IShellOutput {

    private final BlockingQueue<ShellChunk> queue;
    private volatile boolean closed = false;

    public BlockingQueueShellOutput() {
        this.queue = new LinkedBlockingQueue<>(1024);
    }

    public BlockingQueueShellOutput(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public BlockingQueueShellOutput(BlockingQueue<ShellChunk> queue) {
        this.queue = queue;
    }

    @Override
    public void write(ShellChunk chunk) {
        if (closed) throw new IllegalStateException("output closed");
        try {
            queue.put(chunk);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            queue.offer(ShellChunk.eof());
        }
    }

    @Override
    public IShellInput asInput() {
        return new BlockingQueueShellInputAdapter(queue);
    }

    public BlockingQueue<ShellChunk> getQueue() {
        return queue;
    }

    private static class BlockingQueueShellInputAdapter extends AbstractShellInput {
        private final BlockingQueue<ShellChunk> queue;
        private volatile boolean eofReceived = false;

        BlockingQueueShellInputAdapter(BlockingQueue<ShellChunk> queue) {
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
                return null;
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
}
