package io.nop.ai.shell.io;

import io.nop.api.core.util.Guard;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 List 的线程安全 Shell 输入实现
 * <p>
 * 与 BlockingQueueShellInput 不同，此类使用 List 而非阻塞队列来存储数据。
 * 适用于不需要阻塞等待的场景，数据可以预先加载到 List 中。
 * </p>
 */
public class ListShellInput implements IShellInput {

    private final List<String> list;
    private volatile boolean closed = false;
    private volatile boolean eof = false;
    private final AtomicInteger readIndex = new AtomicInteger(0);
    private final AtomicInteger readCount = new AtomicInteger(0);

    /**
     * 创建具有默认容量（线程安全的 CopyOnWriteArrayList）的 ListShellInput
     */
    public ListShellInput() {
        this.list = new CopyOnWriteArrayList<>();
    }

    /**
     * 创建使用指定 List 的 ListShellInput
     *
     * @param list 用于存储输入数据的列表，应当是线程安全的
     */
    public ListShellInput(List<String> list) {
        Guard.notNull(list, "list");
        this.list = list;
    }

    @Override
    public String readLine() {
        if (closed) {
            return null;
        }

        synchronized (this) {
            if (eof) {
                return null;
            }

            int index = readIndex.get();
            if (index >= list.size()) {
                // 队列为空且已经收到EOF标记
                return null;
            }

            String line = list.get(index);
            if (IShellOutput.EOF_MARKER.equals(line)) {
                eof = true;
                return null;
            }
            if (line != null) {
                readIndex.incrementAndGet();
                readCount.incrementAndGet();
                return line;
            }
            return null;
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

    /**
     * 获取已读取的行数
     *
     * @return 已读取的行数
     */
    public int getReadCount() {
        return readCount.get();
    }

    /**
     * 获取当前读取索引
     *
     * @return 当前读取索引
     */
    public int getReadIndex() {
        return readIndex.get();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }

    /**
     * 添加一行输入数据
     *
     * @param line 输入行，不能为空
     */
    public void put(String line) {
        Guard.notEmpty(line, "line");

        if (closed || eof) {
            throw new IllegalStateException("input closed or EOF reached");
        }

        list.add(line);
    }

    /**
     * 添加多行输入数据
     *
     * @param lines 输入行列表
     */
    public void putAll(List<String> lines) {
        Guard.notNull(lines, "lines");

        if (closed || eof) {
            throw new IllegalStateException("input closed or EOF reached");
        }

        list.addAll(lines);
    }

    /**
     * 标记输入结束（EOF）
     */
    public void eof() {
        if (!eof) {
            eof = true;
            list.add(IShellOutput.EOF_MARKER);
        }
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
