package io.nop.ai.coder.file;

import java.util.List;

public interface IFileOperator {
    default FileContents readFileContents(List<String> paths) {
        FileContents ret = new FileContents();
        if (paths == null || paths.isEmpty())
            return ret;

        for (String path : paths) {
            ret.addFile(readFileContent(path));
        }
        return ret;
    }

    default void writeFileContents(FileContents fileContents, boolean overwrite) {
        if (fileContents.getFiles() == null)
            return;

        for (FileContent fileContent : fileContents.getFiles()) {
            writeFileContent(fileContent, overwrite);
        }
    }

    FileContent readFileContent(String path);

    List<String> readLines(String path);

    List<String> readLines(String path, int fromLine, int toLine);

    void writeFileContent(FileContent fileContent, boolean append);

    default void writeFileContent(FileContent fileContent) {
        writeFileContent(fileContent, false);
    }

    boolean exists(String path);

    List<String> findFilesByAntPath(String directory, String pattern);

    List<String> listDirectory(String directory);

    default FileContents readFileContentsByAntPath(String directory, String pattern, int maxFileCount) {
        List<String> paths = findFilesByAntPath(directory, pattern);
        if (paths.size() > maxFileCount)
            paths = paths.subList(0, maxFileCount);
        return readFileContents(paths);
    }

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