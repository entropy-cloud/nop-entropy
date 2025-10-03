package io.nop.ai.core.file;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;
import io.nop.core.resource.IResource;

import java.util.Collections;
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

    List<String> findFilesByAntPath(String directory, String pattern, int maxFileCount);

    List<String> findFilesByFilter(String directory, Predicate<String> filter, int maxFileCount);

    List<String> listDirectory(String directory);

    default FileContents readFileContentsByAntPath(String directory, String pattern, int maxFileCount, int maxLengthPerFile) {
        List<String> paths = findFilesByAntPath(directory, pattern, maxFileCount);
        return readFileContents(paths, maxLengthPerFile);
    }

    default String findFileByRegex(String directory, String regex) {
        Pattern pattern = Pattern.compile(regex);
        return findFileByFilter(directory, path -> pattern.matcher(path).find());
    }

    default List<String> findFilesByRegex(String directory, String regex, int maxFileCount) {
        Pattern pattern = Pattern.compile(regex);
        return findFilesByFilter(directory, path -> pattern.matcher(path).find(), maxFileCount);
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

    void mergeFile(String filePath, String text);

    /**
     * 使用glob模式搜索文件
     *
     * @param directory    搜索目录
     * @param pattern      glob模式
     * @param maxFileCount 最大返回文件数，<=0表示无限制
     * @return 匹配的文件路径列表
     */
    List<String> findFilesByGlob(String directory, String pattern, int maxFileCount);

    /**
     * 在文件中搜索匹配正则表达式的行
     *
     * @param limit 该文件的最大匹配行数，<=0表示无限制
     */
    default List<GrepResult> grep(String filePath, String regex, boolean ignoreCase,
                                  int limit) {
        return grepFiles(Collections.singletonList(filePath), regex, ignoreCase, limit, limit);
    }

    /**
     * 在多个文件中搜索匹配正则表达式的行
     *
     * @param limitPerFile 每个文件的最大匹配行数，<=0表示无限制
     * @param totalLimit   所有文件的总匹配行数限制，<=0表示无限制
     */
    List<GrepResult> grepFiles(List<String> filePaths, String regex, boolean ignoreCase,
                               int limitPerFile, int totalLimit);

    /**
     * 使用glob模式查找文件，并在匹配的文件中搜索符合正则表达式的行
     *
     * @param directory 搜索目录
     * @param globPattern glob文件匹配模式
     * @param regex 正则表达式
     * @param ignoreCase 是否忽略大小写
     * @param limitPerFile 每个文件的最大匹配行数，<=0表示无限制
     * @param totalLimit 所有文件的总匹配行数限制，<=0表示无限制
     * @return 匹配的grep结果列表
     */
    List<GrepResult> globGrep(String directory, String globPattern, String regex,
                              boolean ignoreCase, int limitPerFile, int totalLimit);

    @DataBean
    class GrepResult {
        private final String filePath;
        private final int lineNumber;
        private final String lineContent;

        public GrepResult(String filePath, int lineNumber, String lineContent) {
            this.filePath = Guard.notEmpty(filePath, "filePath");
            this.lineNumber = Guard.positiveInt(lineNumber, "lineNumber");
            this.lineContent = Guard.notEmpty(lineContent, "lineContent");
        }

        // getter方法
        public String getFilePath() {
            return filePath;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getLineContent() {
            return lineContent;
        }

        /**
         * 转换为标准grep格式字符串
         */
        public String toString() {
            return filePath + ":" + lineNumber + ":" + lineContent;
        }
    }
}