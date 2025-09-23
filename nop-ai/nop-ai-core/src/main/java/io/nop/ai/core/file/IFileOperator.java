package io.nop.ai.core.file;

import io.nop.core.resource.IResource;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public interface IFileOperator {
    IResource getResource(String path);

    default FileContents readFileContents(List<String> paths, int maxLengthPerFile) {
        FileContents ret = new FileContents();
        if (paths == null || paths.isEmpty())
            return ret;

        for (String path : paths) {
            ret.addFile(readFileContent(path, 0, maxLengthPerFile));
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

    FileContent readFileContent(String path, long offset, int limit);

    List<String> readLines(String path);

    /**
     * firstLine从1开始，包含lastLine
     */
    List<String> readLines(String path, int startLines, int lineCount);

    void writeFileContent(FileContent fileContent, boolean append);

    default void writeFileContent(FileContent fileContent) {
        writeFileContent(fileContent, false);
    }

    boolean exists(String path);

    List<String> findFilesByAntPath(String directory, String pattern);

    List<String> findFilesByFilter(String directory, Predicate<String> filter);

    List<String> listDirectory(String directory);

    default FileContents readFileContentsByAntPath(String directory, String pattern, int maxFileCount, int maxLengthPerFile) {
        List<String> paths = findFilesByAntPath(directory, pattern);
        if (paths.size() > maxFileCount)
            paths = paths.subList(0, maxFileCount);
        return readFileContents(paths, maxLengthPerFile);
    }

    default String findFileByRegex(String directory, String regex) {
        Pattern pattern = Pattern.compile(regex);
        return findFileByFilter(directory, path -> pattern.matcher(path).find());
    }

    default List<String> findFilesByRegex(String directory, String regex) {
        Pattern pattern = Pattern.compile(regex);
        return findFilesByFilter(directory, path -> pattern.matcher(path).find());
    }

    //  查找满足模式要求的第一个文件
    String findFileByAntPath(String directory, String pattern);

    String findFileByFilter(String directory, Predicate<String> filter);

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