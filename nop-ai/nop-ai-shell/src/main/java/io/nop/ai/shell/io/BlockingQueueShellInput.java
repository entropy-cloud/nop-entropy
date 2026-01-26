package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockingQueueShellInput implements IShellInput {

    private final BlockingQueue<String> queue;
    private volatile boolean closed = false;
    private volatile boolean eof = false;
    private final AtomicInteger readCount = new AtomicInteger(0);

    public BlockingQueueShellInput(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public BlockingQueueShellInput(BlockingQueue<String> queue) {
        this.queue = queue;
    }

    @Override
    public String readLine() {
        if (closed) {
            return null;
        }

        try {
            while (!closed) {
                String line = queue.poll(100, TimeUnit.MILLISECONDS);
                
                if (line == IShellOutput.EOF_MARKER) {
                    eof = true;
                    return null;
                } else if (line != null) {
                    readCount.incrementAndGet();
                    return line;
                } else if (eof) {
                    // 队列为空且已经收到EOF标记
                    return null;
                }
                // 队列为空但未收到EOF，继续等待
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }

    @Override
    public Iterator<String> lines() {
        return new Iterator<>() {
            private String nextLine = null;
            
            @Override
            public boolean hasNext() {
                if (nextLine != null) {
                    return true;
                }
                
                if (closed) {
                    return false;
                }
                
                nextLine = readLine();
                return nextLine != null;
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                
                String line = nextLine;
                nextLine = null;
                return line;
            }
        };
    }

    public int getReadCount() {
        return readCount.get();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }

    public void put(String line) throws InterruptedException {
        Guard.notEmpty(line, "line");

        if (closed || eof) {
            throw new IllegalStateException("input closed or EOF reached");
        }
        
        queue.put(line);
    }

    public void eof() {
        if (!eof) {
            eof = true;
            queue.offer(IShellOutput.EOF_MARKER);
        }
    }
}
