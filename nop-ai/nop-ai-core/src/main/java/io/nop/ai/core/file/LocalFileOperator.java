package io.nop.ai.core.file;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.path.AntPathMatcher;
import io.nop.commons.path.IPathMatcher;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.ComponentModelConfig;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.impl.FileResource;
import io.nop.xlang.delta.DeltaMerger;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.XDslValidator;
import io.nop.xlang.xmeta.SchemaLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static io.nop.commons.CommonErrors.ARG_FILE;
import static io.nop.commons.CommonErrors.ARG_PATH;
import static io.nop.commons.CommonErrors.ARG_REGEX;
import static io.nop.commons.CommonErrors.ARG_SOURCE_PATH;
import static io.nop.commons.CommonErrors.ARG_SRC_FILE;
import static io.nop.commons.CommonErrors.ARG_TARGET_FILE;
import static io.nop.commons.CommonErrors.ARG_TARGET_PATH;
import static io.nop.commons.CommonErrors.ERR_FILE_ALREADY_EXISTS;
import static io.nop.commons.CommonErrors.ERR_FILE_COPY_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_DELETE_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_INVALID_PATH;
import static io.nop.commons.CommonErrors.ERR_FILE_INVALID_REGEX_PATTERN;
import static io.nop.commons.CommonErrors.ERR_FILE_MOVE_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_NOT_FOUND;
import static io.nop.commons.CommonErrors.ERR_FILE_READ_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_WRITE_FAIL;

public class LocalFileOperator implements IFileOperator {
    static final Logger LOG = LoggerFactory.getLogger(LocalFileOperator.class);

    // 3.1-MA-20（plan 2555 裁定 H / R1-M3）：globGrep 的 maxFileCount 自弃点（原 :441 传 0
    // = 无界）改为内部缺省上界；grep 每文件读取行数上界。超出即截断 + WARN。
    static final int DEFAULT_GLOB_GREP_MAX_FILES = 1000;
    static final int DEFAULT_GREP_MAX_LINES_PER_FILE = 10_000;

    private final File baseDir;
    private final IPathMatcher pathMatcher = new AntPathMatcher();

    public LocalFileOperator(File baseDir) {
        this.baseDir = FileHelper.getAbsoluteFile(baseDir);
    }

    public LocalFileOperator(String baseDirPath) {
        this(new File(baseDirPath));
    }

    @Override
    public IResource getResource(String path) {
        if (path == null)
            path = "";
        File file = resolveFile(path);
        return new FileResource(file);
    }

    private File resolveFile(String path) {
        if (path.contains("..") || path.contains(":")) {
            throw new NopException(ERR_FILE_INVALID_PATH)
                    .param(ARG_PATH, path);
        }
        File resolved = path.isEmpty() ? baseDir : new File(baseDir, path);
        try {
            String resolvedCanonical = resolved.getCanonicalPath();
            String baseCanonical = baseDir.getCanonicalPath();
            if (!resolvedCanonical.startsWith(baseCanonical)) {
                throw new NopException(ERR_FILE_INVALID_PATH)
                        .param(ARG_PATH, path);
            }
        } catch (IOException e) {
            throw new NopException(ERR_FILE_INVALID_PATH, e)
                    .param(ARG_PATH, path);
        }
        return resolved;
    }

    @Override
    public FileContent readFileContent(String path, long offset, int limit) {
        if (offset < 0)
            offset = 0;

        File file = resolveFile(path);
        if (!file.exists()) {
            LOG.info("nop.ai.file.not-found:path={},file={}", path, file);
            return FileContent.notFound(path);
        }
        try {
            // 3.1-MA-20（plan 2555 裁定 H / R1-M3）：limit 语义下沉到读取面——有界读取
            // 只触碰 [offset, offset+limit) 字符区间，不再整文件装载后 substring
            if (offset <= 0 && limit <= 0) {
                String content = FileHelper.readText(file, "UTF-8");
                return new FileContent(path, content);
            }
            if (offset >= file.length())
                return new FileContent(path, "", null);
            String content = BoundedFileReader.readText(file, offset, limit);
            if (limit <= 0)
                return new FileContent(path, content, null, offset, 0, false);
            int readLimit = (int) Math.min(limit, content.length());
            return new FileContent(path, content, null, offset, readLimit, false);
        } catch (Exception e) {
            LOG.warn("nop.ai.file.read-fail:path={},file={}", path, file);
            return FileContent.readError(path, ErrorMessageManager.instance().getRealCause(e).toString());
        }
    }

    @Override
    public List<String> readLines(String path) {
        File file = resolveFile(path);
        if (!file.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
        }
        try {
            return FileHelper.readLines(file, "UTF-8");
        } catch (Exception e) {
            throw new NopException(ERR_FILE_READ_FAIL, e)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
        }
    }

    @Override
    public List<String> readLines(String path, int startLines, int lineCount) {
        // 3.1-MA-20（plan 2555 裁定 H / R1-M3）：分页读取行数上界下沉——流式跳过
        // startLines 后至多读 lineCount 行，不再全量 readLines 后 subList
        File file = resolveFile(path);
        if (!file.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
        }
        int start = Math.max(0, startLines);
        try {
            if (lineCount == 0)
                return Collections.emptyList();
            return BoundedFileReader.readLines(file, start, lineCount);
        } catch (Exception e) {
            throw new NopException(ERR_FILE_READ_FAIL, e)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
        }
    }

    @Override
    public void writeFileContent(FileContent fileContent, boolean append) {
        File file = resolveFile(fileContent.getPath());
        try {
            FileHelper.writeText(file, fileContent.getContent(), "UTF-8", append);
        } catch (Exception e) {
            throw new NopException(ERR_FILE_WRITE_FAIL, e)
                    .param(ARG_PATH, fileContent.getPath())
                    .param(ARG_FILE, file);
        }
    }

    @Override
    public boolean exists(String path) {
        return resolveFile(path).exists();
    }

    @Override
    public List<String> findFilesByAntPath(String directory, String pattern, int maxFileCount) {
        return findFilesByFilter(directory, path -> {
            return pathMatcher.match(pattern, path);
        }, maxFileCount);
    }

    @Override
    public List<String> findFilesByFilter(String directory, Predicate<String> filter, int maxFileCount) {
        File dir = resolveFile(directory);
        if (!dir.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, directory)
                    .param(ARG_FILE, dir);
        }

        List<String> result = new ArrayList<>();

        FileHelper.walk(dir, file -> {
            if (file.isFile()) {
                String relativePath = getRelativePath(file);
                if (filter.test(relativePath)) {
                    if (maxFileCount > 0 && result.size() >= maxFileCount) {
                        // 3.1-MA-20（plan 2555 裁定 H）：命中上界截断 + WARN，不再返回 max+1 条
                        LOG.warn("nop.ai.file.find-truncated:maxFileCount={},dir={}", maxFileCount, dir);
                        return FileVisitResult.TERMINATE;
                    }
                    result.add(relativePath);
                }
            }
            return FileVisitResult.CONTINUE;
        });

        return result;
    }

    @Override
    public List<String> listDirectory(String directory) {
        File dir = resolveFile(directory);
        if (!dir.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, directory)
                    .param(ARG_FILE, dir);
        }

        List<String> result = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                result.add(getRelativePath(file));
            }
        }
        return result;
    }

    @Override
    public String findFileByAntPath(String directory, String pattern) {
        return findFileByFilter(directory, path -> {
            return pathMatcher.match(pattern, path);
        });
    }

    @Override
    public String findFileByFilter(String directory, Predicate<String> filter) {
        File dir = resolveFile(directory);
        if (!dir.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, directory)
                    .param(ARG_FILE, dir);
        }

        final String[] found = {null};
        FileHelper.walk(dir, file -> {
            if (file.isFile()) {
                String relativePath = getRelativePath(file);
                if (filter.test(relativePath)) {
                    found[0] = relativePath;
                    return FileVisitResult.TERMINATE;
                }
            }
            return FileVisitResult.CONTINUE;
        });

        return found[0];
    }

    @Override
    public void delete(String path) {
        File file = resolveFile(path);
        if (!file.exists()) {
            return;
        }

        boolean b;
        if (file.isDirectory()) {
            b = FileHelper.deleteAll(file);
        } else {
            b = file.delete();
        }
        if (!b)
            throw new NopException(ERR_FILE_DELETE_FAIL)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
    }

    @Override
    public void move(String sourcePath, String targetPath, boolean overwrite) {
        File src = resolveFile(sourcePath);
        File dest = resolveFile(targetPath);

        if (!src.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, sourcePath)
                    .param(ARG_FILE, src);
        }

        if (src.equals(dest))
            return;

        if (dest.exists()) {
            if (overwrite) {
                delete(targetPath);
            } else {
                // 添加这个检查
                throw new NopException(ERR_FILE_ALREADY_EXISTS)
                        .param(ARG_PATH, targetPath)
                        .param(ARG_TARGET_FILE, dest);
            }
        }

        boolean b = FileHelper.moveFile(src, dest);
        if (!b) {
            throw new NopException(ERR_FILE_MOVE_FAIL)
                    .param(ARG_SOURCE_PATH, sourcePath)
                    .param(ARG_TARGET_PATH, targetPath)
                    .param(ARG_SRC_FILE, src)
                    .param(ARG_TARGET_FILE, dest);
        }
    }

    @Override
    public void copy(String sourcePath, String targetPath, boolean overwrite) {
        File src = resolveFile(sourcePath);
        File dest = resolveFile(targetPath);

        if (!src.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, sourcePath)
                    .param(ARG_FILE, src);
        }

        if (src.equals(dest))
            return;

        if (dest.exists()) {
            if (overwrite) {
                delete(targetPath);
            } else {
                // 添加这个检查
                throw new NopException(ERR_FILE_ALREADY_EXISTS)
                        .param(ARG_PATH, targetPath)
                        .param(ARG_TARGET_FILE, dest);
            }
        }

        try {
            FileHelper.copyFile(src, dest);
        } catch (Exception e) {
            throw new NopException(ERR_FILE_COPY_FAIL, e)
                    .param(ARG_SOURCE_PATH, sourcePath)
                    .param(ARG_TARGET_PATH, targetPath)
                    .param(ARG_SRC_FILE, src)
                    .param(ARG_TARGET_FILE, dest);
        }
    }

    @Override
    public void mergeFile(String filePath, String text) {
        String fileType = StringHelper.fileType(filePath);
        ComponentModelConfig config = ResourceComponentManager.instance().getModelConfigByFileType(fileType);
        if (config == null || config.getXdefPath() == null) {
            // no xdef
            writeFileContent(new FileContent(filePath, text));
        } else {
            IXDefinition xdef = SchemaLoader.loadXDefinition(config.getXdefPath());
            XNode node = XNode.parse(text);

            IResource file = getResource(filePath);
            if (!file.exists()) {
                new XDslValidator(XDslKeys.DEFAULT).validate(node, xdef.getRootNode(), true);
                writeFileContent(new FileContent(filePath, node.xml()));
            } else {
                XNode baseNode = XNodeParser.instance().parseFromResource(file);
                new DeltaMerger(XDslKeys.DEFAULT).merge(baseNode, node, xdef.getRootNode(), false);
                new XDslValidator(XDslKeys.DEFAULT).validate(node, xdef.getRootNode(), true);
                writeFileContent(new FileContent(filePath, baseNode.xml()));
            }
        }
    }

    @Override
    public List<String> findFilesByGlob(String directory, String pattern, int maxFileCount) {
        return this.findFilesByAntPath(directory, pattern, maxFileCount);
    }

    @Override
    public List<GrepResult> grepFiles(List<String> filePaths, String regex, boolean ignoreCase, int limitPerFile, int totalLimit) {
        List<GrepResult> results = new ArrayList<>();
        if (filePaths == null || filePaths.isEmpty() || regex == null || regex.isEmpty()) {
            return results;
        }

        // 编译正则表达式
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex, flags);
        } catch (Exception e) {
            throw new NopException(ERR_FILE_INVALID_REGEX_PATTERN, e)
                    .param(ARG_REGEX, regex);
        }

        int totalMatches = 0;

        for (String filePath : filePaths) {
            if (totalLimit > 0 && totalMatches >= totalLimit) {
                break;
            }

            File file = resolveFile(filePath);
            if (!file.exists() || !file.isFile()) {
                continue;
            }

            try {
                // 3.1-MA-20（plan 2555 裁定 H）：grep 每文件读取行数上界——逐行流式，
                // 至多扫描 DEFAULT_GREP_MAX_LINES_PER_FILE 行，不再整文件 readLines
                List<String> lines = BoundedFileReader.readLines(file, 0, DEFAULT_GREP_MAX_LINES_PER_FILE);
                int fileMatches = 0;

                for (int i = 0; i < lines.size(); i++) {
                    if (limitPerFile > 0 && fileMatches >= limitPerFile) {
                        break;
                    }
                    if (totalLimit > 0 && totalMatches >= totalLimit) {
                        break;
                    }

                    String line = lines.get(i);
                    java.util.regex.Matcher matcher = pattern.matcher(line);

                    if (matcher.find()) {
                        // 创建GrepResult对象，每个匹配行一个结果
                        results.add(new GrepResult(filePath, i + 1, line));
                        fileMatches++;
                        totalMatches++;
                    }
                }

            } catch (Exception e) {
                LOG.warn("nop.ai.file.grep-fail:path={},file={}", filePath, file, e);
                // 继续处理其他文件，不抛出异常
            }
        }

        return results;
    }

    @Override
    public List<GrepResult> globGrep(String directory, String globPattern, String regex,
                                     boolean ignoreCase, int limitPerFile, int totalLimit) {
        // 先通过glob模式查找文件
        // 3.1-MA-20（plan 2555 裁定 H）：原先传 0（无界自弃），改为内部缺省上界
        // DEFAULT_GLOB_GREP_MAX_FILES，命中即截断 + WARN（findFilesByFilter 内）
        List<String> filePaths = findFilesByGlob(directory, globPattern, DEFAULT_GLOB_GREP_MAX_FILES);

        // 然后在匹配的文件中执行grep搜索
        return grepFiles(filePaths, regex, ignoreCase, limitPerFile, totalLimit);
    }

    private String getRelativePath(File file) {
        return FileHelper.getRelativePath(baseDir, file);
    }
}