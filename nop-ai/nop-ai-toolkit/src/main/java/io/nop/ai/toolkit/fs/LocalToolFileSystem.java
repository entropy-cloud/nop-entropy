package io.nop.ai.toolkit.fs;

import io.nop.ai.api.exceptions.NopAiException;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.io.stream.SafeLineReader;
import io.nop.commons.path.AntPathMatcher;
import io.nop.commons.path.ICompiledPathMatcher;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.nop.ai.toolkit.NopAiToolkitErrors.ARG_DETAIL;
import static io.nop.ai.toolkit.NopAiToolkitErrors.ERR_AI_TOOLKIT_INVALID_STATE;

public class LocalToolFileSystem implements IToolFileSystem {
    private static final Logger LOG = LoggerFactory.getLogger(LocalToolFileSystem.class);

    private final File workDir;
    private final AntPathMatcher antMatcher = new AntPathMatcher();

    public LocalToolFileSystem(File workDir) {
        this.workDir = workDir;
    }

    @Override
    public String normalizePath(String path) {
        return StringHelper.normalizePath(path);
    }

    @Override
    public boolean isPathAllowed(String path) {
        if (path == null) return false;
        try {
            File resolved = resolveFileInternal(path);
            // Windows 规范路径使用 '\' 分隔符，pathStartsWith 只识别 '/' 边界，需先统一分隔符
            String canonicalWorkDir = StringHelper.normalizePath(workDir.getCanonicalPath());
            String canonicalPath = StringHelper.normalizePath(resolved.getCanonicalPath());
            return StringHelper.pathStartsWith(canonicalPath, canonicalWorkDir);
        } catch (IOException e) {
            return false;
        }
    }

    private File resolveFile(String path) {
        if (!isPathAllowed(path)) {
            throw new NopAiException("Path not allowed: " + path);
        }
        return resolveFileInternal(path);
    }

    private File resolveFileInternal(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        if (path.startsWith("/")) {
            return new File(workDir, path.substring(1));
        }
        return new File(workDir, path);
    }

    @Override
    public boolean exists(String path) {
        File file = resolveFile(path);
        return file.exists();
    }

    @Override
    public boolean isFile(String path) {
        File file = resolveFile(path);
        return file.isFile();
    }

    @Override
    public boolean isDirectory(String path) {
        File file = resolveFile(path);
        return file.isDirectory();
    }

    @Override
    public TextResult readText(String path, int maxChars) {
        File file = resolveFile(path);
        if (!file.exists() || !file.isFile()) {
            throw new NopAiException("File not found: " + path);
        }

        Reader reader = null;
        try {
            reader = java.nio.file.Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
            String content = SafeLineReader.readText(reader, maxChars);
            boolean truncated = maxChars > 0 && content.length() >= maxChars && file.length() > maxChars;
            return new TextResult(path, content, truncated);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(reader);
        }
    }

    @Override
    public LineResult readLines(String path, int fromLine, int toLine, int maxLineLength) {
        File file = resolveFile(path);
        if (!file.exists() || !file.isFile()) {
            throw new NopAiException("File not found: " + path);
        }

        int maxLen = maxLineLength > 0 ? maxLineLength : Integer.MAX_VALUE;
        List<Line> lines = new ArrayList<>();
        int[] totalLines = {0};

        Reader reader = null;
        try {
            reader = java.nio.file.Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
            List<SafeLineReader.LineRead> lineReads = SafeLineReader.readLines(reader, fromLine, toLine, maxLen);
            for (int i = 0; i < lineReads.size(); i++) {
                SafeLineReader.LineRead lr = lineReads.get(i);
                int lineNum = fromLine + i;
                lines.add(new Line(lineNum, lr.getContent() != null ? lr.getContent() : "", lr.isTruncated()));
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(reader);
        }

        return new LineResult(path, lines.isEmpty() ? 0 : toLine, fromLine, Math.min(toLine, fromLine + lines.size() - 1), lines);
    }

    @Override
    public int countLines(String path, int maxLines) {
        File file = resolveFile(path);
        if (!file.exists() || !file.isFile()) {
            throw new NopAiException("File not found: " + path);
        }

        Reader reader = null;
        try {
            reader = java.nio.file.Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
            if (maxLines <= 0) {
                return SafeLineReader.countLines(reader);
            }
            return SafeLineReader.countLines(reader, maxLines);
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeClose(reader);
        }
    }

    @Override
    public void writeText(String path, String content, boolean append) {
        File file = resolveFile(path);
        if (append) {
            FileHelper.writeText(file, content, StandardCharsets.UTF_8.name(), true);
            return;
        }
        atomicReplace(file, content);
    }

    /**
     * Atomic replace (plan 2026-09-14-1937-1 Phase 2): write to a temp file in
     * the same directory, then move over the target, so a partial write or
     * crash never truncates the previous content. {@code ATOMIC_MOVE} is
     * attempted first; plain {@code Files.move} with {@code REPLACE_EXISTING}
     * is the explicit fallback for filesystems without atomic rename support.
     * The temp file is always created in the target's directory, so no
     * cross-filesystem move can occur. Any failure before the final move
     * leaves the previous target content untouched.
     */
    private void atomicReplace(File target, String content) {
        FileHelper.assureParent(target);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp" + UUID.randomUUID());
        try {
            Files.writeString(temp.toPath(), content, StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException e) {
                LOG.debug("Failed to delete temp file after write: {}", temp, e);
            }
        }
    }

    @Override
    public List<FileInfo> listDirectory(String dirPath, int depth, int maxCount) {
        File dir = resolveFile(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new NopAiException("Directory not found: " + dirPath);
        }

        List<FileInfo> result = new ArrayList<>();
        listDirectoryRecursive(dir, dirPath, depth, maxCount, result);
        return result;
    }

    private void listDirectoryRecursive(File dir, String basePath, int depth, int maxCount, List<FileInfo> result) {
        if (result.size() >= maxCount) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (result.size() >= maxCount) break;

            String relativePath = basePath + "/" + file.getName();
            result.add(new FileInfo(relativePath, file.getName(), file.isDirectory(),
                    file.length(), file.lastModified()));

            if (depth > 0 && file.isDirectory()) {
                listDirectoryRecursive(file, relativePath, depth - 1, maxCount, result);
            }
        }
    }

    @Override
    public void mkdirs(String path) {
        File dir = resolveFile(path);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new NopException(ERR_AI_TOOLKIT_INVALID_STATE)
                    .param(ARG_DETAIL, "Failed to create directory: " + path);
        }
    }

    @Override
    public void delete(String path, boolean recursive, boolean force) {
        File file = resolveFile(path);
        if (!file.exists()) {
            return;
        }

        boolean deleted;
        if (file.isDirectory() && recursive) {
            deleted = FileHelper.deleteAll(file);
        } else {
            if (force && file.isFile()) {
                file.setWritable(true);
            }
            deleted = file.delete();
        }
        if (!deleted && file.exists()) {
            throw new NopException(ERR_AI_TOOLKIT_INVALID_STATE)
                    .param(ARG_DETAIL, "Failed to delete: " + path);
        }
    }

    @Override
    public void move(String fromPath, String toPath, boolean overwrite) {
        File fromFile = resolveFile(fromPath);
        File toFile = resolveFile(toPath);
        if (!fromFile.exists()) {
            throw new NopAiException("File not found: " + fromPath);
        }

        if (toFile.exists() && !overwrite) {
            throw new NopAiException("Target file already exists: " + toPath);
        }

        FileHelper.assureParent(toFile);

        if (!FileHelper.moveFile(fromFile, toFile)) {
            throw NopException.adapt(new IOException("Failed to move file: " + fromPath + " to " + toPath));
        }
    }

    @Override
    public void copy(String fromPath, String toPath, boolean recursive, boolean overwrite) {
        File fromFile = resolveFile(fromPath);
        File toFile = resolveFile(toPath);
        if (!fromFile.exists()) {
            throw new NopAiException("File not found: " + fromPath);
        }

        if (toFile.exists() && !overwrite) {
            throw new NopAiException("Target file already exists: " + toPath);
        }

        FileHelper.assureParent(toFile);

        if (fromFile.isDirectory() && recursive) {
            FileHelper.copyWithFilter(fromFile, toFile, null);
        } else {
            FileHelper.copyFile(fromFile, toFile);
        }
    }

    @Override
    public List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults) {
        List<FileInfo> result = new ArrayList<>();
        ICompiledPathMatcher matcher = antMatcher.compile(pattern);
        Path basePath = StringHelper.isEmpty(directory)
                ? workDir.toPath()
                : resolveFile(directory).toPath();
        globRecursive(basePath, "", matcher, recursive, maxDepth, 0, maxResults, result);
        return result;
    }

    private void globRecursive(Path baseDir, String relativePath, ICompiledPathMatcher matcher,
                               boolean recursive, int maxDepth, int currentDepth,
                               int maxCount, List<FileInfo> result) {
        if (result.size() >= maxCount) return;
        if (maxDepth > 0 && currentDepth >= maxDepth) return;

        try (Stream<Path> paths = java.nio.file.Files.list(baseDir)) {
            for (Path path : paths.collect(Collectors.toList())) {
                if (result.size() >= maxCount) break;

                String relPath = relativePath.isEmpty() ? path.getFileName().toString()
                        : relativePath + "/" + path.getFileName().toString();
                if (java.nio.file.Files.isDirectory(path) && recursive) {
                    globRecursive(path, relPath, matcher, recursive, maxDepth, currentDepth + 1, maxCount, result);
                } else if (matcher.match(relPath)) {
                    File file = path.toFile();
                    result.add(new FileInfo(relPath, file.getName(), false, file.length(), file.lastModified()));
                }
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public List<SearchMatch> grep(String pattern, String searchDir, boolean recursive, boolean ignoreCase,
                                  int maxMatchesPerFile, int maxFiles, int maxDepth) {
        List<SearchMatch> result = new ArrayList<>();
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
        Pattern regex = Pattern.compile(pattern, flags);
        File dir = resolveFile(searchDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return result;
        }

        grepRecursive(dir, searchDir, regex, recursive, maxMatchesPerFile, maxFiles, maxDepth, 0, result);
        return result;
    }

    private void grepRecursive(File dir, String basePath, Pattern pattern, boolean recursive,
                               int maxMatchesPerFile, int maxFiles, int maxDepth, int currentDepth,
                               List<SearchMatch> result) {
        if (result.size() >= maxFiles * maxMatchesPerFile) return;
        if (maxDepth > 0 && currentDepth >= maxDepth) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        int filesWithMatches = 0;
        for (File file : files) {
            if (filesWithMatches >= maxFiles) break;

            if (file.isDirectory() && recursive) {
                grepRecursive(file, basePath + "/" + file.getName(), pattern, recursive,
                        maxMatchesPerFile, maxFiles, maxDepth, currentDepth + 1, result);
            } else if (file.isFile()) {
                int matchesBefore = result.size();
                grepInFile(file, basePath + "/" + file.getName(), pattern, maxMatchesPerFile, result);
                if (result.size() > matchesBefore) {
                    filesWithMatches++;
                }
            }
        }
    }

    private void grepInFile(File file, String filePath, Pattern pattern, int maxMatches, List<SearchMatch> result) {
        SafeLineReader sr = null;
        try {
            Reader reader = java.nio.file.Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
            sr = new SafeLineReader(reader);
            int matchesInFile = 0;
            while (sr.hasNext() && matchesInFile < maxMatches) {
                SafeLineReader.LineRead lr = sr.readLine(65536);
                if (lr.getContent() == null) break;
                java.util.regex.Matcher matcher = pattern.matcher(lr.getContent());
                if (matcher.find()) {
                    String matchedText = matcher.group();
                    result.add(new SearchMatch(filePath, (int) sr.getLineIndex(), lr.getContent(), matchedText, lr.isTruncated()));
                    matchesInFile++;
                }
            }
        } catch (IOException e) {
            LOG.debug("Failed to read file for grep: {}", filePath);
        } finally {
            IoHelper.safeCloseObject(sr);
        }
    }
}
