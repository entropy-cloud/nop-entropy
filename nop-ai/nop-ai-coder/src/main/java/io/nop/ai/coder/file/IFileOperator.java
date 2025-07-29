package io.nop.ai.coder.file;

import java.util.List;

public interface IFileOperator {
    FileContents readFiles(List<String> paths);

    void writeFiles(FileContents fileContents, boolean overwrite);

    FileContent readFile(String path);

    List<String> readLines(String path);

    void writeFile(FileContent fileContent, boolean append);

    default void writeFile(FileContent fileContent) {
        writeFile(fileContent, false);
    }

    boolean exists(String path);

    List<String> findFilesByAntPath(String directory, String pattern);

    List<String> listDirectory(String directory);

    //  查找满足模式要求的第一个文件
    String findFileByAntPath(String directory, String pattern);

    default String findFileByName(String directory, String fileName) {
        return findFileByAntPath(directory, "**/" + fileName);
    }

    // 文件管理操作
    void delete(String path);

    void move(String sourcePath, String targetPath, boolean overwrite);

    default void move(String sourcePath, String targetPath) {
        move(sourcePath, targetPath, true);
    }

    void copy(String sourcePath, String targetPath, boolean overwrite);

    default void copy(String sourcePath, String targetPath) {
        copy(sourcePath, targetPath, true);
    }

    default void applyDiff(FileDiff diff) {
        new FileDiffApplier(this).apply(diff);
    }
}