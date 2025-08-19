package io.nop.ai.coder.file;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.path.AntPathMatcher;
import io.nop.commons.path.IPathMatcher;
import io.nop.commons.util.FileHelper;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.nop.commons.CommonErrors.ARG_FILE;
import static io.nop.commons.CommonErrors.ARG_PATH;
import static io.nop.commons.CommonErrors.ARG_SOURCE_PATH;
import static io.nop.commons.CommonErrors.ARG_SRC_FILE;
import static io.nop.commons.CommonErrors.ARG_TARGET_FILE;
import static io.nop.commons.CommonErrors.ARG_TARGET_PATH;
import static io.nop.commons.CommonErrors.ERR_FILE_COPY_FAIL;
import static io.nop.commons.CommonErrors.ERR_FILE_DELETE_FAIL;
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

    private File resolveFile(String path) {
        return path.isEmpty() ? baseDir : new File(baseDir, path);
    }

    @Override
    public FileContent readFileContent(String path) {
        File file = resolveFile(path);
        if (!file.exists()) {
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
        }
        try {
            String content = FileHelper.readText(file, "UTF-8");
            return new FileContent(path, content);
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
    public List<String> readLines(String path, int fromLine, int toLine) {
        List<String> allLines = readLines(path);
        fromLine = Math.max(0, fromLine);
        toLine = Math.min(allLines.size(), toLine);
        if (fromLine >= allLines.size() || fromLine >= toLine)
            return Collections.emptyList();

        return allLines.subList(fromLine, toLine);
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
                if (pathMatcher.match(pattern, relativePath)) {
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
                if (pathMatcher.match(pattern, relativePath)) {
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
            throw new NopException(ERR_FILE_NOT_FOUND)
                    .param(ARG_PATH, path)
                    .param(ARG_FILE, file);
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

        if (dest.exists()) {
            if (overwrite) {
                delete(targetPath);
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

        if (dest.exists()) {
            if (overwrite) {
                delete(targetPath);
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

    private String getRelativePath(File file) {
        String basePath = baseDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (filePath.startsWith(basePath)) {
            return filePath.substring(basePath.length() + 1);
        }
        return filePath;
    }
}