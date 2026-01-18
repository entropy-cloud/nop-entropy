package io.nop.ai.maven.vfs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 虚拟文件系统实现
 * <p>
 * 虚拟文件系统由base目录和delta目录组成：
 * <ul>
 *   <li>base目录：基础目录，只读，包含原始文件</li>
 *   <li>delta目录：增量目录，可写，覆盖base目录的文件，新增文件也存放在此</li>
 * </ul>
 * <p>
 * 读取文件时，优先从delta目录读取，如果delta目录不存在该文件，则从base目录读取。
 * 写入文件时，总是写入delta目录。
 *
 * @author Nop AI
 */
public class DeltaVirtualFileSystem {

    private static final Logger LOG = LoggerFactory.getLogger(DeltaVirtualFileSystem.class);

    private final File baseDir;
    private final File deltaDir;

    /**
     * 创建虚拟文件系统
     *
     * @param baseDir  基础目录（只读）
     * @param deltaDir 增量目录（可写）
     */
    public DeltaVirtualFileSystem(File baseDir, File deltaDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir cannot be null");
        }
        if (deltaDir == null) {
            throw new IllegalArgumentException("deltaDir cannot be null");
        }

        if (!baseDir.exists()) {
            throw new IllegalArgumentException("baseDir does not exist: " + baseDir.getAbsolutePath());
        }
        if (!baseDir.isDirectory()) {
            throw new IllegalArgumentException("baseDir is not a directory: " + baseDir.getAbsolutePath());
        }

        this.baseDir = baseDir.getAbsoluteFile();
        this.deltaDir = deltaDir.getAbsoluteFile();

        // 确保delta目录存在
        if (!this.deltaDir.exists()) {
            try {
                Files.createDirectories(this.deltaDir.toPath());
                LOG.info("Created delta directory: {}", this.deltaDir.getAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException("Failed to create delta directory: " + this.deltaDir.getAbsolutePath(), e);
            }
        }

        if (!this.deltaDir.isDirectory()) {
            throw new IllegalArgumentException("deltaDir is not a directory: " + this.deltaDir.getAbsolutePath());
        }

        LOG.info("Initialized DeltaVirtualFileSystem with base={}, delta={}",
                this.baseDir.getAbsolutePath(), this.deltaDir.getAbsolutePath());
    }

    /**
     * 创建虚拟文件系统（使用字符串路径）
     *
     * @param basePath  基础目录路径
     * @param deltaPath 增量目录路径
     */
    public DeltaVirtualFileSystem(String basePath, String deltaPath) {
        this(new File(basePath), new File(deltaPath));
    }

    /**
     * 获取虚拟文件
     *
     * @param relativePath 相对路径
     * @return 文件对象（如果存在），否则返回null
     */
    public File getFile(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        // 标准化路径
        String normalizedPath = normalizePath(relativePath);

        // 优先从delta目录查找
        File deltaFile = new File(deltaDir, normalizedPath);
        if (deltaFile.exists() && deltaFile.isFile()) {
            LOG.debug("File found in delta: {}", deltaFile.getAbsolutePath());
            return deltaFile;
        }

        // 从base目录查找
        File baseFile = new File(baseDir, normalizedPath);
        if (baseFile.exists() && baseFile.isFile()) {
            LOG.debug("File found in base: {}", baseFile.getAbsolutePath());
            return baseFile;
        }

        LOG.debug("File not found: {}", normalizedPath);
        return null;
    }

    /**
     * 获取虚拟文件的输入流
     *
     * @param relativePath 相对路径
     * @return 输入流（如果存在），否则返回null
     * @throws IOException 如果读取失败
     */
    public InputStream getInputStream(String relativePath) throws IOException {
        File file = getFile(relativePath);
        if (file == null) {
            return null;
        }
        return Files.newInputStream(file.toPath());
    }

    /**
     * 获取虚拟文件的输出流（总是写入delta目录）
     *
     * @param relativePath 相对路径
     * @return 输出流
     * @throws IOException 如果创建文件失败
     */
    public OutputStream getOutputStream(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("relativePath cannot be null or empty");
        }

        String normalizedPath = normalizePath(relativePath);
        File deltaFile = new File(deltaDir, normalizedPath);

        // 确保父目录存在
        File parentDir = deltaFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            Files.createDirectories(parentDir.toPath());
        }

        LOG.debug("Writing file to delta: {}", deltaFile.getAbsolutePath());
        return Files.newOutputStream(deltaFile.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 复制文件到虚拟文件系统（总是复制到delta目录）
     *
     * @param sourceFile  源文件
     * @param relativePath 目标相对路径
     * @throws IOException 如果复制失败
     */
    public void copyToVirtual(File sourceFile, String relativePath) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IllegalArgumentException("sourceFile does not exist: " + sourceFile);
        }

        String normalizedPath = normalizePath(relativePath);
        File deltaFile = new File(deltaDir, normalizedPath);

        // 确保父目录存在
        File parentDir = deltaFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            Files.createDirectories(parentDir.toPath());
        }

        Files.copy(sourceFile.toPath(), deltaFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        LOG.info("Copied file to virtual FS: {} -> {}",
                sourceFile.getAbsolutePath(), deltaFile.getAbsolutePath());
    }

    /**
     * 删除虚拟文件（只删除delta目录中的文件）
     *
     * @param relativePath 相对路径
     * @throws IOException 如果删除失败
     */
    public void deleteFile(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("relativePath cannot be null or empty");
        }

        String normalizedPath = normalizePath(relativePath);
        File deltaFile = new File(deltaDir, normalizedPath);

        if (deltaFile.exists()) {
            Files.delete(deltaFile.toPath());
            LOG.info("Deleted file from delta: {}", deltaFile.getAbsolutePath());
        } else {
            LOG.warn("File not found in delta, cannot delete: {}", normalizedPath);
        }
    }

    /**
     * 检查虚拟文件是否存在
     *
     * @param relativePath 相对路径
     * @return 如果文件存在返回true，否则返回false
     */
    public boolean exists(String relativePath) {
        return getFile(relativePath) != null;
    }

    /**
     * 获取虚拟文件的最后修改时间
     *
     * @param relativePath 相对路径
     * @return 最后修改时间（毫秒），如果文件不存在返回0
     */
    public long getLastModified(String relativePath) {
        File file = getFile(relativePath);
        return file != null ? file.lastModified() : 0;
    }

    /**
     * 列出虚拟目录下的所有文件
     *
     * @param relativePath 相对路径
     * @return 文件列表
     */
    public List<File> listFiles(String relativePath) {
        List<File> result = new ArrayList<>();

        String normalizedPath = normalizePath(relativePath);

        // 收集delta目录的文件
        File deltaPathDir = new File(deltaDir, normalizedPath);
        if (deltaPathDir.exists() && deltaPathDir.isDirectory()) {
            File[] deltaFiles = deltaPathDir.listFiles();
            if (deltaFiles != null) {
                for (File file : deltaFiles) {
                    result.add(file);
                }
            }
        }

        // 收集base目录的文件（排除已存在于delta的）
        File basePathDir = new File(baseDir, normalizedPath);
        if (basePathDir.exists() && basePathDir.isDirectory()) {
            File[] baseFiles = basePathDir.listFiles();
            if (baseFiles != null) {
                for (File baseFile : baseFiles) {
                    String fileName = baseFile.getName();
                    // 检查delta目录中是否已存在同文件名
                    File deltaFile = new File(deltaPathDir, fileName);
                    if (!deltaFile.exists()) {
                        result.add(baseFile);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 获取基础目录
     *
     * @return 基础目录
     */
    public File getBaseDir() {
        return baseDir;
    }

    /**
     * 获取增量目录
     *
     * @return 增量目录
     */
    public File getDeltaDir() {
        return deltaDir;
    }

    /**
     * 标准化路径（处理路径分隔符，去除前导斜杠）
     *
     * @param path 原始路径
     * @return 标准化后的路径
     */
    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        // 统一路径分隔符为 /
        String normalized = path.replace('\\', '/');

        // 去除前导斜杠
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        // 去除尾部斜杠（除非是根路径）
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}
