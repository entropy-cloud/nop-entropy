package io.nop.ai.shell.io;

import static io.nop.ai.shell.NopAiShellErrors.ERR_AI_SHELL_INVALID_STATE;
import io.nop.api.core.exceptions.NopException;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingQueueShellOutput implements IShellOutput {

    /**
     * plan 2557 C8-012 (ruling J): entry cap for text chunks. An oversized
     * chunk is split into successive bounded chunks (concatenation restores
     * the original text) instead of propagating a single giant chunk into the
     * queue — every consumer downstream can rely on per-chunk boundedness.
     */
    public static final int MAX_CHUNK_CHARS = 64 * 1024;

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
        if (closed) throw new NopException(ERR_AI_SHELL_INVALID_STATE).param("detail", "output closed");
        if (chunk.isText() && chunk.asText().length() > MAX_CHUNK_CHARS) {
            // plan 2557 C8-012 (ruling J): split giant text chunks into
            // successive bounded chunks at the entry
            String text = chunk.asText();
            for (int offset = 0; offset < text.length(); offset += MAX_CHUNK_CHARS) {
                putChunk(ShellChunk.text(text.substring(offset, Math.min(offset + MAX_CHUNK_CHARS, text.length()))));
            }
            return;
        }
        putChunk(chunk);
    }

    private void putChunk(ShellChunk chunk) {
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

    /**
     * C4-003 (MA-06, plan 2501 Phase 3): exposes the close flag so an executor-side
     * collector can use {@code closed && queue.isEmpty()} as a terminal backstop even
     * when the EOF chunk was dropped by a full queue (executor-side mitigation; the
     * {@code close()} offer semantics themselves are MA-05 scope and untouched).
     */
    public boolean isClosed() {
        return closed;
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
