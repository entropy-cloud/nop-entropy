package io.nop.ai.toolkit.fs;

import io.nop.commons.path.AntPathMatcher;
import io.nop.commons.path.IPathMatcher;
import io.nop.commons.util.IoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LocalToolFileSystem implements IToolFileSystem {
    private static final Logger LOG = LoggerFactory.getLogger(LocalToolFileSystem.class);

    private final File workDir;
    private final IPathMatcher pathMatcher = new AntPathMatcher();

    public LocalToolFileSystem(File workDir) {
        this.workDir = workDir;
    }

    @Override
    public String normalizePath(String path) {
        if (path == null) return null;
        path = path.replace('\\', '/');
        while (path.contains("/./") || path.contains("/..")) {
            path = path.replace("/./", "/");
            path = path.replaceAll("/[^/]+/\\.\\./", "");
        }
        return path;
    }

    @Override
    public boolean isPathAllowed(String path) {
        if (path == null) return false;
        try {
            File resolved = resolveFile(path);
            String canonicalWorkDir = workDir.getCanonicalPath();
            String canonicalPath = resolved.getCanonicalPath();
            return canonicalPath.startsWith(canonicalWorkDir);
        } catch (IOException e) {
            return false;
        }
    }

    private File resolveFile(String path) {
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
            throw new IllegalArgumentException("File not found: " + path);
        }

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            boolean truncated = false;
            if (maxChars > 0 && content.length() > maxChars) {
                content = content.substring(0, maxChars);
                truncated = true;
            }
            return new TextResult(path, content, truncated);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }
    }

    @Override
    public LineResult readLines(String path, int fromLine, int toLine, int maxLineLength) {
        File file = resolveFile(path);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File not found: " + path);
        }

        List<Line> lines = new ArrayList<>();
        int totalLines = 0;
        int currentLine = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                totalLines++;
                currentLine++;
                if (currentLine >= fromLine && currentLine <= toLine) {
                    boolean truncated = false;
                    String content = line;
                    if (maxLineLength > 0 && content.length() > maxLineLength) {
                        content = content.substring(0, maxLineLength);
                        truncated = true;
                    }
                    lines.add(new Line(currentLine, content, truncated));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + path, e);
        }

        return new LineResult(path, totalLines, fromLine, Math.min(toLine, totalLines), lines);
    }

    @Override
    public int countLines(String path, int maxLines) {
        File file = resolveFile(path);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File not found: " + path);
        }

        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                count++;
                if (maxLines > 0 && count >= maxLines) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to count lines: " + path, e);
        }
        return count;
    }

    @Override
    public void writeText(String path, String content, boolean append) {
        File file = resolveFile(path);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        try {
            if (append) {
                Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write file: " + path, e);
        }
    }

    @Override
    public List<FileInfo> listDirectory(String dirPath, int depth, int maxCount) {
        File dir = resolveFile(dirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("Directory not found: " + dirPath);
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
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public void delete(String path, boolean recursive, boolean force) {
        File file = resolveFile(path);
        if (!file.exists()) {
            return;
        }

        if (file.isDirectory() && recursive) {
            deleteRecursive(file);
        } else {
            if (force && file.isFile()) {
                file.setWritable(true);
            }
            file.delete();
        }
    }

    private void deleteRecursive(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteRecursive(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    @Override
    public void move(String fromPath, String toPath, boolean overwrite) {
        File fromFile = resolveFile(fromPath);
        File toFile = resolveFile(toPath);
        if (!fromFile.exists()) {
            throw new IllegalArgumentException("File not found: " + fromPath);
        }

        if (toFile.exists() && !overwrite) {
            throw new IllegalArgumentException("Target file already exists: " + toPath);
        }

        if (!toFile.getParentFile().exists()) {
            toFile.getParentFile().mkdirs();
        }

        if (!fromFile.renameTo(toFile)) {
            throw new RuntimeException("Failed to move file: " + fromPath + " to " + toPath);
        }
    }

    @Override
    public void copy(String fromPath, String toPath, boolean recursive, boolean overwrite) {
        File fromFile = resolveFile(fromPath);
        File toFile = resolveFile(toPath);
        if (!fromFile.exists()) {
            throw new IllegalArgumentException("File not found: " + fromPath);
        }

        if (toFile.exists() && !overwrite) {
            throw new IllegalArgumentException("Target file already exists: " + toPath);
        }

        if (!toFile.getParentFile().exists()) {
            toFile.getParentFile().mkdirs();
        }

        try {
            if (fromFile.isDirectory() && recursive) {
                copyDirectory(fromFile, toFile);
            } else {
                Files.copy(fromFile.toPath(), toFile.toPath());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy file: " + fromPath + " to " + toPath, e);
        }
    }

    private void copyDirectory(File fromDir, File toDir) throws IOException {
        if (!toDir.exists()) {
            toDir.mkdirs();
        }
        File[] files = fromDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(toDir, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, targetFile);
                } else {
                    Files.copy(file.toPath(), targetFile.toPath());
                }
            }
        }
    }

    @Override
    public List<FileInfo> glob(String pattern, String directory, boolean recursive, int maxDepth, int maxResults) {
        List<FileInfo> result = new ArrayList<>();
        try {
            PathMatcher pathMatcher = new PathMatcher(pattern);
            Path basePath = directory == null || directory.isEmpty() 
                ? workDir.toPath() 
                : resolveFile(directory).toPath();
            globRecursive(basePath, "", pathMatcher, recursive, maxDepth, 0, maxResults, result);
        } catch (IOException e) {
            throw new RuntimeException("Failed to glob: " + pattern, e);
        }
        return result;
    }

    private void globRecursive(Path baseDir, String relativePath, PathMatcher matcher, 
                               boolean recursive, int maxDepth, int currentDepth,
                               int maxCount, List<FileInfo> result) throws IOException {
        if (result.size() >= maxCount) return;
        if (maxDepth > 0 && currentDepth >= maxDepth) return;

        try (Stream<Path> paths = Files.list(baseDir)) {
            for (Path path : paths.collect(Collectors.toList())) {
                if (result.size() >= maxCount) break;

                String relPath = relativePath.isEmpty() ? path.getFileName().toString()
                        : relativePath + "/" + path.getFileName().toString();
                if (Files.isDirectory(path) && recursive) {
                    globRecursive(path, relPath, matcher, recursive, maxDepth, currentDepth + 1, maxCount, result);
                } else if (matcher.matches(relPath)) {
                    File file = path.toFile();
                    result.add(new FileInfo(relPath, file.getName(), false, file.length(), file.lastModified()));
                }
            }
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            int matchesInFile = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (matchesInFile >= maxMatches) return;

                java.util.regex.Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String matchedText = matcher.group();
                    result.add(new SearchMatch(filePath, lineNumber, line, matchedText, false));
                    matchesInFile++;
                }
            }
        } catch (IOException e) {
            LOG.debug("Failed to read file for grep: {}", filePath);
        }
    }

    private static class PathMatcher {
        private final String pattern;

        PathMatcher(String pattern) {
            this.pattern = pattern.replace("*", ".*").replace("?", ".");
        }

        boolean matches(String path) {
            return path.matches(pattern);
        }
    }
}
