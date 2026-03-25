package io.nop.ai.toolkit.fs;

import java.util.List;

public interface IToolFileSystem {
    String normalizePath(String path);

    boolean isPathAllowed(String path);

    boolean exists(String path);

    boolean isFile(String path);

    boolean isDirectory(String path);

    TextResult readText(String path, int maxChars);

    LineResult readLines(String path, int fromLine, int toLine, int maxLineLength);

    int countLines(String path, int maxLines);

    void writeText(String path, String content, boolean append);

    List<FileInfo> listDirectory(String dirPath, int depth, int maxCount);

    void mkdirs(String path);

    void delete(String path, boolean recursive, boolean force);

    void move(String fromPath, String toPath, boolean overwrite);

    void copy(String fromPath, String toPath, boolean recursive, boolean overwrite);

    List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults);

    List<SearchMatch> grep(String pattern, String searchDir, boolean recursive, boolean ignoreCase,
                           int maxMatchesPerFile, int maxFiles, int maxDepth);
}
