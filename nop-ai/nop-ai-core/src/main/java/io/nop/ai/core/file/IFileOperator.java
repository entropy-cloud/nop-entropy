package io.nop.ai.core.file;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.util.Guard;
import io.nop.core.resource.IResource;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Legacy core file operator used by the {@code FileTool} BizModel and DSL
 * tooling.
 * <p>
 * <b>Boundary contract (P2-MA1-012 ruling, 2026-07-31):</b> kept alongside
 * {@code io.nop.ai.toolkit.fs.IToolFileSystem} — the two abstractions serve
 * different contracts: IFileOperator = base-dir-scoped resource access
 * ({@code IResource} results, FileContent offset/limit reads,
 * AntPath/Regex/Glob finders, xdef-aware {@code mergeFile},
 * {@code applyDiff}); IToolFileSystem = sandboxed executor file access
 * (path permission checks, char-count limited DTO results, depth/max
 * bounded glob/grep). Their method surfaces do not map 1:1, so migration
 * must first reconcile the abstractions; scheduled as future major-version
 * work. Boundary contract and migration preconditions:
 * {@code ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md}.
 *
 * @deprecated 使用 io.nop.ai.toolkit.fs.IToolFileSystem 替代
 * （forRemoval=true，移除属 future major 版本工作）
 */
@Deprecated(forRemoval = true)
public interface IFileOperator {
    /**
     * Resolves a path (relative to this operator's base directory) to a resource handle.
     *
     * @param path the path relative to the base directory; null or empty resolves the base directory itself
     * @return the resource handle (never null; the underlying resource need not exist)
     * @throws io.nop.api.core.exceptions.NopException if the path escapes the base directory
     */
    IResource getResource(String path);

    /**
     * Reads the contents of multiple files, each truncated to {@code maxLengthPerFile} characters.
     *
     * @param paths            the file paths to read; null or empty yields an empty result
     * @param maxLengthPerFile the maximum number of characters read per file (<=0 means no limit)
     * @return a container aggregating one {@link FileContent} per path
     */
    default FileContents readFileContents(List<String> paths, int maxLengthPerFile) {
        FileContents ret = new FileContents();
        if (paths == null || paths.isEmpty())
            return ret;

        for (String path : paths) {
            ret.addFile(readFileContent(path, 0, maxLengthPerFile));
        }
        return ret;
    }

    /**
     * Writes all files contained in {@code fileContents}.
     *
     * @param fileContents the files to write; null content is a no-op
     * @param overwrite    whether an existing target file should be replaced
     */
    default void writeFileContents(FileContents fileContents, boolean overwrite) {
        if (fileContents.getFiles() == null)
            return;

        for (FileContent fileContent : fileContents.getFiles()) {
            writeFileContent(fileContent, overwrite);
        }
    }

    /**
     * Reads a file with optional character offset/limit slicing.
     *
     * @param path   the file path relative to the base directory
     * @param offset the character offset to start reading from (negative values are treated as 0)
     * @param limit  the maximum number of characters to read (<=0 means read to the end)
     * @return the file content; a not-found marker is returned when the file does not exist, and a
     *         read-error marker when the file cannot be read
     */
    FileContent readFileContent(String path, long offset, int limit);

    /**
     * Reads all lines of a file.
     *
     * @param path the file path relative to the base directory
     * @return the lines of the file
     * @throws io.nop.api.core.exceptions.NopException if the file does not exist or cannot be read
     */
    List<String> readLines(String path);

    /**
     * firstLine从1开始，包含lastLine
     */
    List<String> readLines(String path, int startLines, int lineCount);

    void writeFileContent(FileContent fileContent, boolean append);

    /**
     * Writes a file, replacing any existing content.
     */
    default void writeFileContent(FileContent fileContent) {
        writeFileContent(fileContent, false);
    }

    /**
     * Checks whether a path exists (file or directory) under the base directory.
     *
     * @param path the path relative to the base directory
     * @return true if the resource exists
     */
    boolean exists(String path);

    /**
     * Finds files under a directory whose paths match an Ant-style pattern.
     *
     * @param directory    the directory to search, relative to the base directory
     * @param pattern      the Ant-style glob pattern (e.g. double-star globs like {@code *.java})
     * @param maxFileCount the maximum number of results (<=0 means unlimited)
     * @return matching file paths relative to the base directory
     */
    List<String> findFilesByAntPath(String directory, String pattern, int maxFileCount);

    /**
     * Finds files under a directory whose relative paths satisfy the given filter.
     *
     * @param directory    the directory to search, relative to the base directory
     * @param filter       predicate applied to each file path relative to the base directory
     * @param maxFileCount the maximum number of results (<=0 means unlimited)
     * @return matching file paths relative to the base directory
     */
    List<String> findFilesByFilter(String directory, Predicate<String> filter, int maxFileCount);

    /**
     * Lists the immediate children of a directory.
     *
     * @param directory the directory path relative to the base directory
     * @return the child paths relative to the base directory
     */
    List<String> listDirectory(String directory);

    /**
     * Finds files by Ant pattern and reads their contents.
     *
     * @param directory        the directory to search
     * @param pattern          the Ant-style glob pattern
     * @param maxFileCount     the maximum number of files to read (<=0 means unlimited)
     * @param maxLengthPerFile the maximum number of characters read per file (<=0 means no limit)
     * @return a container aggregating one {@link FileContent} per matched path
     */
    default FileContents readFileContentsByAntPath(String directory, String pattern, int maxFileCount, int maxLengthPerFile) {
        List<String> paths = findFilesByAntPath(directory, pattern, maxFileCount);
        return readFileContents(paths, maxLengthPerFile);
    }

    /**
     * Finds the first file whose path matches the given regular expression.
     *
     * @param directory the directory to search
     * @param regex     the regular expression matched against file paths relative to the base directory
     * @return the first matching path relative to the base directory, or null if none matches
     */
    default String findFileByRegex(String directory, String regex) {
        Pattern pattern = Pattern.compile(regex);
        return findFileByFilter(directory, path -> pattern.matcher(path).find());
    }

    /**
     * Finds files whose paths match the given regular expression.
     *
     * @param directory    the directory to search
     * @param regex        the regular expression matched against file paths relative to the base directory
     * @param maxFileCount the maximum number of results (<=0 means unlimited)
     * @return matching file paths relative to the base directory
     */
    default List<String> findFilesByRegex(String directory, String regex, int maxFileCount) {
        Pattern pattern = Pattern.compile(regex);
        return findFilesByFilter(directory, path -> pattern.matcher(path).find(), maxFileCount);
    }

    //  查找满足模式要求的第一个文件
    /**
     * Finds the first file whose path matches an Ant-style pattern.
     *
     * @param directory the directory to search
     * @param pattern   the Ant-style glob pattern
     * @return the first matching path relative to the base directory, or null if none matches
     */
    String findFileByAntPath(String directory, String pattern);

    /**
     * Finds the first file whose relative path satisfies the given filter.
     *
     * @param directory the directory to search
     * @param filter    predicate applied to each file path relative to the base directory
     * @return the first matching path relative to the base directory, or null if none matches
     */
    String findFileByFilter(String directory, Predicate<String> filter);

    /**
     * Finds the first file with the given name anywhere under a directory.
     *
     * @param directory the directory to search
     * @param fileName  the file name to search for (matched as a recursive glob, i.e. anywhere under the directory)
     * @return the first matching path relative to the base directory, or null if none matches
     */
    default String findFileByName(String directory, String fileName) {
        return findFileByAntPath(directory, "**/" + fileName);
    }

    // 文件管理操作
    /**
     * Deletes a file or directory (recursively). A missing path is silently ignored.
     *
     * @param path the path relative to the base directory
     */
    void delete(String path);

    /**
     * Moves a file or directory.
     *
     * @param sourcePath the source path relative to the base directory
     * @param targetPath the target path relative to the base directory
     * @param overwrite  whether to replace an existing target; if false and the target exists, an error is thrown
     */
    void move(String sourcePath, String targetPath, boolean overwrite);

    /**
     * Moves a file or directory, overwriting any existing target.
     */
    default void move(String sourcePath, String targetPath) {
        move(sourcePath, targetPath, true);
    }

    /**
     * Copies a file or directory.
     *
     * @param sourcePath the source path relative to the base directory
     * @param targetPath the target path relative to the base directory
     * @param overwrite  whether to replace an existing target; if false and the target exists, an error is thrown
     */
    void copy(String sourcePath, String targetPath, boolean overwrite);

    /**
     * Copies a file or directory, overwriting any existing target.
     */
    default void copy(String sourcePath, String targetPath) {
        copy(sourcePath, targetPath, true);
    }

    /**
     * Applies the operations described by a file diff (add/delete/rename/modify sections).
     *
     * @param diff the diff describing the file changes to apply
     */
    default void applyDiff(FileDiff diff) {
        new FileDiffApplier(this).apply(diff);
    }

    /**
     * Merges the given text into a file, respecting its xdef model when one is registered for the file type.
     * <p>
     * For file types backed by an xdef schema, {@code text} is parsed as a delta document and merged with the
     * existing document (validated afterwards); otherwise the file is simply written (or overwritten) with the text.
     *
     * @param filePath the file path relative to the base directory
     * @param text     the (delta) document text to merge or write
     */
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