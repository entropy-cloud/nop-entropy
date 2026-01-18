package io.nop.ai.maven.config;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.ai.maven.vfs.DeltaWorkspaceReader;

/**
 * Delta Workspace Reader 配置工厂
 * <p>
 * 通过系统属性配置虚拟文件系统，支持base和delta目录。
 * <p>
 * 配置属性：
 * <ul>
 *   <li>vfs.base.dir：基础目录路径（必需）</li>
 *   <li>vfs.delta.dir：增量目录路径（必需）</li>
 *   <li>vfs.enabled：是否启用虚拟文件系统（默认为true）</li>
 * </ul>
 *
 * @author Nop AI
 */
public class DeltaWorkspaceReaderConfigurator {

    private static final Logger LOG = LoggerFactory.getLogger(DeltaWorkspaceReaderConfigurator.class);

    public static final String VFS_ENABLED = "vfs.enabled";
    public static final String VFS_BASE_DIR = "vfs.base.dir";
    public static final String VFS_DELTA_DIR = "vfs.delta.dir";

    /**
     * 创建并配置DeltaWorkspaceReader
     *
     * @return 配置好的DeltaWorkspaceReader，如果未启用则返回null
     */
    public DeltaWorkspaceReader createWorkspaceReader() {
        // 检查是否启用
        boolean enabled = Boolean.parseBoolean(System.getProperty(VFS_ENABLED, "true"));
        if (!enabled) {
            LOG.info("VFS is disabled, skipping DeltaWorkspaceReader creation");
            return null;
        }

        String baseDirPath = System.getProperty(VFS_BASE_DIR);
        String deltaDirPath = System.getProperty(VFS_DELTA_DIR);

        if (baseDirPath == null || baseDirPath.trim().isEmpty()) {
            LOG.warn("VFS base directory not configured (property: {}), skipping", VFS_BASE_DIR);
            return null;
        }

        if (deltaDirPath == null || deltaDirPath.trim().isEmpty()) {
            LOG.warn("VFS delta directory not configured (property: {}), skipping", VFS_DELTA_DIR);
            return null;
        }

        try {
            File baseDir = new File(baseDirPath);
            File deltaDir = new File(deltaDirPath);

            // 创建DeltaWorkspaceReader
            DeltaWorkspaceReader reader = new DeltaWorkspaceReader(baseDir, deltaDir);

            LOG.info("Created DeltaWorkspaceReader: base={}, delta={}",
                    baseDir.getAbsolutePath(), deltaDir.getAbsolutePath());

            return reader;
        } catch (Exception e) {
            LOG.error("Failed to create DeltaWorkspaceReader", e);
            return null;
        }
    }

    /**
     * 检查是否启用了虚拟文件系统
     *
     * @return 如果启用返回true，否则返回false
     */
    public boolean isVfsEnabled() {
        return Boolean.parseBoolean(System.getProperty(VFS_ENABLED, "true"));
    }

    /**
     * 获取基础目录
     *
     * @return 基础目录，如果未配置则返回null
     */
    public File getBaseDir() {
        String baseDirPath = System.getProperty(VFS_BASE_DIR);
        if (baseDirPath == null || baseDirPath.trim().isEmpty()) {
            return null;
        }
        return new File(baseDirPath);
    }

    /**
     * 获取增量目录
     *
     * @return 增量目录，如果未配置则返回null
     */
    public File getDeltaDir() {
        String deltaDirPath = System.getProperty(VFS_DELTA_DIR);
        if (deltaDirPath == null || deltaDirPath.trim().isEmpty()) {
            return null;
        }
        return new File(deltaDirPath);
    }
}
