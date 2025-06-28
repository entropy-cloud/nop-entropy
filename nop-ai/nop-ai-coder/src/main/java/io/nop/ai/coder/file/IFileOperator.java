package io.nop.ai.coder.file;

import java.util.List;

public interface IFileOperator {
    List<FileContent> readFiles(List<String> paths);

    void writeFiles(List<FileContent> fileContents);

    FileContent readFile(String path);

    List<String> readLines(String path);

    void writeFile(FileContent fileContent);

    boolean exists(String path);

    List<String> findFiles(String directoryPath, String pattern);

    List<String> listDirectory(String directoryPath);

    // 文件管理操作
    boolean delete(String path);

    boolean move(String sourcePath, String targetPath);

    boolean copy(String sourcePath, String targetPath);

    default void applyDiff(FileDiff diff) {
        new FileDiffApplier(this).apply(diff);
    }
}