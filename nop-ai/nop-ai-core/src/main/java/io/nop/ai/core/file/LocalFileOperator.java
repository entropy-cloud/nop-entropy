package io.nop.ai.core.file;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.path.AntPathMatcher;
import io.nop.commons.path.IPathMatcher;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
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

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static io.nop.commons.CommonErrors.ARG_FILE;
import static io.nop.commons.CommonErrors.ARG_PATH;
import static io.nop.commons.CommonErrors.ARG_SOURCE_PATH;
import static io.nop.commons.CommonErrors.ARG_SRC_FILE;
import static io.nop.commons.CommonErrors.ARG_TARGET_FILE;
import static io.nop.commons.CommonErrors.ARG_TARGET_PATH;
import static io.nop.commons.CommonErrors.ERR_FILE_ALREADY_EXISTS;
import static io.nop.commons.CommonErrors.ERR_FILE_COPY_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_DELETE_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_INVALID_PATH;
import static io.nop.commons.CommonErrors.ERR_FILE_MOVE_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_NOT_FOUND;
import static io.nop.commons.CommonErrors.ERR_FILE_READ_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_WRITE_FAIL;

public class LocalFileOperator implements IFileOperator {
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
        return path.isEmpty() ? baseDir : new File(baseDir, path);
    }

    @Override
    public FileContent readFileContent(String path, long offset, int limit) {
        if (offset < 0)
            offset = 0;

        File file = resolveFile(path);
        if (!file.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
        }
        try {
            String content = FileHelper.readText(file, "UTF-8");
            if (offset <= 0 && limit <= 0) {
                return new FileContent(path, content);
            } else {
                if (offset >= content.length())
                    return new FileContent(path, "", null);
                if (limit <= 0)
                    return new FileContent(path, content, null, offset, 0, false);

                boolean hasMoreData = false;
                if (offset + limit > content.length()) {
                    limit = (int) (content.length() - offset);
                }
                content = content.substring((int) offset, (int) offset + limit);
                return new FileContent(path, content, null, offset, limit, hasMoreData);
            }
        } catch (Exception e) {
            throw new NopException(ERR_FILE_READ_FAIL, e)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
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
        List<String> allLines = readLines(path);
        startLines = Math.max(0, startLines);
        if (lineCount < 0) {
            if (allLines.size() <= startLines)
                return Collections.emptyList();
            return allLines.subList(startLines, allLines.size());
        }
        lineCount = Math.min(allLines.size() - startLines, lineCount);
        if (lineCount <= 0)
            return Collections.emptyList();

        return allLines.subList(startLines, startLines + lineCount);
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
    public List<String> findFilesByAntPath(String directory, String pattern) {
        return findFilesByFilter(directory, path -> {
            return pathMatcher.match(pattern, path);
        });
    }

    @Override
    public List<String> findFilesByFilter(String directory, Predicate<String> filter) {
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

    private String getRelativePath(File file) {
        return FileHelper.getRelativePath(baseDir, file);
    }
}