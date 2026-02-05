package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件输出重定向实现
 * <p>
 * 支持：
 * <ul>
 *   <li>覆盖模式 (>): 清空文件后写入</li>
 *   <li>追加模式 (>>): 在文件末尾追加</li>
 * </ul>
 * </p>
 */
public class FileShellOutput implements IShellOutput {

    private final Path filePath;
    private final boolean append;
    private final AtomicInteger writeCount = new AtomicInteger(0);
    private final StringBuilder buffer = new StringBuilder();

    /**
     * 创建文件输出（覆盖模式）
     *
     * @param filePath 文件路径
     */
    public FileShellOutput(String filePath) {
        this(filePath, false);
    }

    /**
     * 创建文件输出
     *
     * @param filePath 文件路径
     * @param append  是否追加模式
     */
    public FileShellOutput(String filePath, boolean append) {
        this.filePath = Paths.get(filePath);
        this.append = append;
    }

    /**
     * 创建文件输出（覆盖模式）
     *
     * @param filePath 文件路径
     */
    public FileShellOutput(Path filePath) {
        this(filePath, false);
    }

    /**
     * 创建文件输出
     *
     * @param filePath 文件路径
     * @param append  是否追加模式
     */
    public FileShellOutput(Path filePath, boolean append) {
        this.filePath = filePath;
        this.append = append;
    }

    @Override
    public void print(String text) {
        synchronized (this) {
            try {
                processText(text, false);
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void println(String text) {
        synchronized (this) {
            try {
                processText(text, true);
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void println() {
        println("");
    }

    @Override
    public void flush() {
        synchronized (this) {
            try {
                flushBuffer();
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            try {
                flushBuffer();
                if (writeCount.get() > 0 || buffer.length() > 0) {
                    // 写入EOF标记
                    Files.writeString(filePath, "", StandardOpenOption.APPEND);
                }
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
    }

    private void processText(String text, boolean addNewline) throws IOException {
        if (text == null) {
            text = "";
        }

        // 如果文本包含换行符，需要拆分
        if (text.contains("\n")) {
            List<String> lines = splitLines(text);

            // 处理所有行
            for (int i = 0; i < lines.size(); i++) {
                // 将当前行添加到缓存
                buffer.append(trimRight(lines.get(i), '\r'));

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
                writeLine("");
            }
            writeCount.incrementAndGet();
        }
    }

    private void flushBuffer() throws IOException {
        if (buffer.length() > 0) {
            writeLine(buffer.toString());
            buffer.setLength(0);
        }
    }

    private void writeLine(String line) throws IOException {
        List<StandardOpenOption> options = new ArrayList<>();
        options.add(StandardOpenOption.CREATE);
        if (append) {
            options.add(StandardOpenOption.APPEND);
        } else {
            options.add(StandardOpenOption.TRUNCATE_EXISTING);
        }

        // 写入行
        Files.writeString(filePath, line, StandardCharsets.UTF_8, options.toArray(new StandardOpenOption[0]));
    }

    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(sb.toString());
                sb.setLength(0);
            } else if (c != '\r') {
                sb.append(c);
            }
        }
        lines.add(sb.toString());

        return lines;
    }

    private String trimRight(String s, char c) {
        int end = s.length() - 1;
        while (end >= 0 && s.charAt(end) == c) {
            end--;
        }
        return end < s.length() ? s.substring(0, end + 1) : s;
    }

    @Override
    public IShellInput asInput() {
        return new FileShellInput(filePath);
    }

    /**
     * 获取文件路径
     *
     * @return 文件路径
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * 是否为追加模式
     *
     * @return true表示追加模式，false表示覆盖模式
     */
    public boolean isAppend() {
        return append;
    }

    /**
     * 获取已写入的行数
     *
     * @return 已写入的行数
     */
    public int getWriteCount() {
        return writeCount.get();
    }
}
