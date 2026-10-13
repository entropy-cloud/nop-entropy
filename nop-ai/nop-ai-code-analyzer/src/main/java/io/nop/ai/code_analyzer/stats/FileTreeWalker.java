package io.nop.ai.code_analyzer.stats;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 文件系统遍历器（从 FileLanguageStats 中提取）：跳过隐藏目录/文件与忽略的扩展名，
 * 将命中的文件回调给调用方。
 */
public class FileTreeWalker {

    public interface FileHandler {
        void visitFile(Path file, String extension) throws IOException;
    }

    public void walk(Path root, FileHandler handler) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                // 忽略隐藏目录和指定目录
                if (dirName.startsWith(".") && !dirName.equals(".") ||
                        IgnoredItems.isIgnoredDirectory(dirName)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    String fileName = file.getFileName().toString();

                    // 忽略隐藏文件
                    if (fileName.startsWith(".")) {
                        return FileVisitResult.CONTINUE;
                    }

                    String extension = getFileExtension(fileName).toLowerCase();

                    if (IgnoredItems.isIgnoredExtension(extension)) {
                        return FileVisitResult.CONTINUE;
                    }

                    handler.visitFile(file, extension);
                } catch (IOException e) {
                    // 静默跳过无法读取的文件
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // 静默跳过访问失败的文件
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }
}
