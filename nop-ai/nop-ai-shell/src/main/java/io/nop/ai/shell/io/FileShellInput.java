package io.nop.ai.shell.io;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * 文件输入重定向实现
 */
public class FileShellInput implements IShellInput {

    private final Path filePath;
    private final List<String> lines;
    private int currentIndex = 0;
    private boolean closed = false;

    /**
     * 创建文件输入
     *
     * @param filePath 文件路径
     */
    public FileShellInput(String filePath) {
        this(Path.of(filePath));
    }

    /**
     * 创建文件输入
     *
     * @param filePath 文件路径
     */
    public FileShellInput(Path filePath) {
        this.filePath = filePath;
        try {
            if (Files.exists(filePath)) {
                this.lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            } else {
                this.lines = List.of();
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public String readLine() {
        if (closed) {
            return null;
        }

        if (currentIndex >= lines.size()) {
            close();
            return null;
        }

        return lines.get(currentIndex++);
    }

    @Override
    public Iterator<String> lines() {
        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return !closed && currentIndex < lines.size();
            }

            @Override
            public String next() {
                if (!hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                return lines.get(currentIndex++);
            }
        };
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * 获取文件路径
     *
     * @return 文件路径
     */
    public Path getFilePath() {
        return filePath;
    }
}
