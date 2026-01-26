package io.nop.ai.shell.io;

import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 List 的线程安全 Shell 输出实现
 * <p>
 * 与 BlockingQueueShellOutput 不同，此类使用 List 而非阻塞队列来存储数据。
 * 适用于不需要阻塞等待的场景，所有输出都直接存储在 List 中。
 * </p>
 */
public class ListShellOutput implements IShellOutput {

    private final List<String> list;
    private final AtomicInteger writeCount = new AtomicInteger(0);
    private final StringBuilder buffer = new StringBuilder();

    /**
     * 创建具有默认容量（线程安全的 CopyOnWriteArrayList）的 ListShellOutput
     */
    public ListShellOutput() {
        this.list = new CopyOnWriteArrayList<>();
    }

    /**
     * 创建使用指定 List 的 ListShellOutput
     *
     * @param list 用于存储输出数据的列表，应当是线程安全的
     */
    public ListShellOutput(List<String> list) {
        Guard.notNull(list, "list");
        this.list = list;
    }

    @Override
    public void print(String str) {
        synchronized (this) {
            processText(str, false);
        }
    }

    @Override
    public void println(String text) {
        synchronized (this) {
            // 优化：如果缓存为空且文本不包含换行符，直接放入列表
            if (buffer.length() == 0 && text != null && text.indexOf('\n') < 0) {
                list.add(text);
                writeCount.incrementAndGet();
                return;
            }

            processText(text, true);
        }
    }

    @Override
    public void flush() {
        // No-op for list based impl
    }

    @Override
    public void close() {
        synchronized (this) {
            flushBuffer();
            list.add(IShellOutput.EOF_MARKER);
        }
    }

    private void processText(String text, boolean addNewline) {
        if (text == null) {
            text = "";
        }

        // 如果文本包含换行符，需要拆分
        if (text.contains("\n")) {
            List<String> lines = StringHelper.split(text, '\n');

            // 处理所有行
            for (int i = 0; i < lines.size(); i++) {
                // 将当前行添加到缓存
                buffer.append(StringHelper.trimRight(lines.get(i), '\r'));

                // 如果是中间行，则立即刷新缓存
                if (i < lines.size() - 1) {
                    flushBuffer();
                    writeCount.incrementAndGet();
                }
            }
        } else {
            // 没有换行符，直接添加到缓存
            buffer.append(text);
        }

        // 如果需要添加换行符，则刷新缓存
        if (addNewline) {
            if (buffer.length() > 0) {
                flushBuffer();
            } else {
                list.add("");
            }
            writeCount.incrementAndGet();
        }
    }

    private void flushBuffer() {
        if (buffer.length() > 0) {
            list.add(buffer.toString());
            writeCount.incrementAndGet();
            buffer.setLength(0);
        }
    }

    /**
     * 获取已写入的行数
     *
     * @return 已写入的行数
     */
    public int getWriteCount() {
        return writeCount.get();
    }

    /**
     * 获取当前缓冲区的长度
     *
     * @return 缓冲区长度
     */
    public int getBufferLength() {
        synchronized (this) {
            return buffer.length();
        }
    }

    @Override
    public IShellInput asInput() {
        // 创建一个简单的输入包装器
        return new IShellInput() {
            private volatile boolean closed = false;
            private final AtomicInteger readIndex = new AtomicInteger(0);

            @Override
            public String readLine() {
                if (closed) {
                    return null;
                }

                synchronized (ListShellOutput.this) {
                    int index = readIndex.get();
                    if (index >= list.size()) {
                        return null;
                    }

                    String line = list.get(index);
                    if (IShellOutput.EOF_MARKER.equals(line)) {
                        closed = true;
                        return null;
                    }
                    readIndex.incrementAndGet();
                    return line;
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

    /**
     * 获取底层数据列表
     *
     * @return 底层数据列表
     */
    public List<String> getList() {
        return list;
    }
}
