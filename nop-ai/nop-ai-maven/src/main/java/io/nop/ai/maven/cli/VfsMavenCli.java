package io.nop.ai.maven.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.nop.ai.maven.config.DeltaWorkspaceReaderConfigurator;
import io.nop.ai.maven.vfs.DeltaVirtualFileSystem;

/**
 * VFS Maven CLI 工具
 * <p>
 * 提供便捷的命令行接口来启动带虚拟文件系统的Maven命令。
 *
 * @author Nop AI
 */
public class VfsMavenCli {

    private final DeltaVirtualFileSystem vfs;

    /**
     * 创建VFS Maven CLI
     *
     * @param baseDir  基础目录
     * @param deltaDir 增量目录
     */
    public VfsMavenCli(File baseDir, File deltaDir) {
        this.vfs = new DeltaVirtualFileSystem(baseDir, deltaDir);
    }

    /**
     * 创建VFS Maven CLI（使用字符串路径）
     *
     * @param basePath  基础目录路径
     * @param deltaPath 增量目录路径
     */
    public VfsMavenCli(String basePath, String deltaPath) {
        this(new File(basePath), new File(deltaPath));
    }

    /**
     * 构建Maven命令，设置虚拟文件系统相关的系统属性
     *
     * @param mavenCommand Maven命令（如 "mvn"）
     * @param goals        Maven目标（如 "compile"）
     * @param extraArgs    额外参数
     * @return 完整的Maven命令列表
     */
    public List<String> buildMavenCommand(String mavenCommand, List<String> goals, List<String> extraArgs) {
        List<String> command = new ArrayList<>();

        // Maven可执行文件
        command.add(mavenCommand);

        // 设置系统属性
        command.add("-D" + DeltaWorkspaceReaderConfigurator.VFS_ENABLED + "=true");
        command.add("-D" + DeltaWorkspaceReaderConfigurator.VFS_BASE_DIR + "=" + vfs.getBaseDir().getAbsolutePath());
        command.add("-D" + DeltaWorkspaceReaderConfigurator.VFS_DELTA_DIR + "=" + vfs.getDeltaDir().getAbsolutePath());

        // 额外参数
        if (extraArgs != null && !extraArgs.isEmpty()) {
            command.addAll(extraArgs);
        }

        // Maven目标
        if (goals != null && !goals.isEmpty()) {
            command.addAll(goals);
        }

        return command;
    }

    /**
     * 构建Maven命令（简化版本）
     *
     * @param mavenCommand Maven命令（如 "mvn"）
     * @param goals        Maven目标（如 "compile"）
     * @return 完整的Maven命令列表
     */
    public List<String> buildMavenCommand(String mavenCommand, String... goals) {
        return buildMavenCommand(mavenCommand, Arrays.asList(goals), null);
    }

    /**
     * 构建Maven命令（带额外参数）
     *
     * @param mavenCommand Maven命令（如 "mvn"）
     * @param goals        Maven目标（如 "compile"）
     * @param extraArgs    额外参数
     * @return 完整的Maven命令列表
     */
    public List<String> buildMavenCommand(String mavenCommand, String[] goals, String... extraArgs) {
        return buildMavenCommand(mavenCommand, Arrays.asList(goals), Arrays.asList(extraArgs));
    }

    /**
     * 打印Maven命令
     *
     * @param command 命令列表
     */
    public void printCommand(List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (String arg : command) {
            if (arg.contains(" ")) {
                sb.append("\"").append(arg).append("\"");
            } else {
                sb.append(arg);
            }
            sb.append(" ");
        }
        System.out.println("Maven Command: " + sb.toString().trim());
    }

    /**
     * 执行Maven命令（仅构建，不执行）
     * <p>
     * 注意：此方法仅构建命令列表，不实际执行命令。
     * 如果需要执行命令，请使用ProcessBuilder或其他方式。
     *
     * @param mavenCommand Maven命令（如 "mvn"）
     * @param goals        Maven目标
     * @param extraArgs    额外参数
     * @return 命令列表
     */
    public List<String> execute(String mavenCommand, List<String> goals, List<String> extraArgs) {
        List<String> command = buildMavenCommand(mavenCommand, goals, extraArgs);
        printCommand(command);

        // 注意：实际执行需要使用ProcessBuilder
        // 示例：
        // ProcessBuilder pb = new ProcessBuilder(command);
        // pb.inheritIO();
        // Process process = pb.start();
        // int exitCode = process.waitFor();
        // return exitCode == 0;

        return command;
    }

    /**
     * 获取虚拟文件系统
     *
     * @return 虚拟文件系统对象
     */
    public DeltaVirtualFileSystem getVfs() {
        return vfs;
    }

    /**
     * 获取基础目录
     *
     * @return 基础目录
     */
    public File getBaseDir() {
        return vfs.getBaseDir();
    }

    /**
     * 获取增量目录
     *
     * @return 增量目录
     */
    public File getDeltaDir() {
        return vfs.getDeltaDir();
    }
}
