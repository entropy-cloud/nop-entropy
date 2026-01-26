package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockingQueueShellOutput implements IShellOutput {

    private final BlockingQueue<String> queue;
    private final AtomicInteger writeCount = new AtomicInteger(0);
    private final StringBuilder buffer = new StringBuilder();

    public BlockingQueueShellOutput() {
        this.queue = new LinkedBlockingQueue<>();
    }

    public BlockingQueueShellOutput(BlockingQueue<String> queue) {
        this.queue = queue;
    }

    @Override
    public void print(String str) {
        try {
            synchronized (this) {
                processText(str, false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }

    @Override
    public void println(String text) {
        try {
            synchronized (this) {
                // 优化：如果缓存为空且文本不包含换行符，直接放入队列
                if (buffer.length() == 0 && text != null && text.indexOf('\n') < 0) {
                    queue.put(text);
                    writeCount.incrementAndGet();
                    return;
                }
                
                processText(text, true);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }

    @Override
    public void flush() {
        // No-op for queue-based output
    }

    @Override
    public void close() {
        try {
            synchronized (this) {
                flushBuffer();
                queue.offer(IShellOutput.EOF_MARKER);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        }
    }

    private void processText(String text, boolean addNewline) throws InterruptedException {
        if (text == null) {
            text = "";
        }

        // 如果文本包含换行符，需要拆分
        if (text.contains("\n")) {
            List<String> lines = StringHelper.split(text, '\n');

            // 处理所有行
            for (int i = 0; i < lines.size(); i++) {
                // 将当前行添加到缓存
                buffer.append(StringHelper.trimRight(lines.get(i),'\r'));
                
                // 如果是中间行，则立即刷新缓存并添加换行符
                if (i < lines.size() - 1) {
                    flushBuffer();
                    writeCount.incrementAndGet();
                }
            }
        } else {
            // 没有换行符，直接添加到缓存
            buffer.append(text);
        }

        // 如果需要添加换行符，则刷新缓存并添加换行符
        if (addNewline) {
            if(buffer.length() > 0) {
                flushBuffer();
            }else{
                queue.put("");
            }
            writeCount.incrementAndGet();
        }
    }

    private void flushBuffer() throws InterruptedException {
        if (buffer.length() > 0) {
            queue.put(buffer.toString());
            writeCount.incrementAndGet();
            buffer.setLength(0);
        }
    }

    @Override
    public IShellInput asInput() {
        // Create a simple input wrapper
        return new IShellInput() {
            private volatile boolean closed = false;

            @Override
            public String readLine() {
                if (closed) return null;

                try {
                    String line = queue.take();
                    if (IShellOutput.EOF_MARKER.equals(line)) {
                        closed = true;
                        return null;
                    }
                    return line;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }

            @Override
            public java.util.Iterator<String> lines() {
                return new java.util.Iterator<String>() {
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
                            throw new java.util.NoSuchElementException();
                        }
                        
                        String line = nextLine;
                        nextLine = null;
                        return line;
                    }
                };
            }

            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public void close() {
                closed = true;
            }
        };
    }

    public BlockingQueue<String> getQueue() {
        return queue;
    }
}