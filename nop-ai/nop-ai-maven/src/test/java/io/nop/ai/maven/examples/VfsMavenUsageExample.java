package io.nop.ai.maven.examples;

import io.nop.ai.maven.cli.VfsMavenCli;
import io.nop.ai.maven.vfs.DeltaVirtualFileSystem;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * VFS Maven 使用示例
 *
 * @author Nop AI
 */
public class VfsMavenUsageExample {

    public static void main(String[] args) throws Exception {
        // 示例1：基本使用
        basicUsageExample();

        // 示例2：Maven命令构建
        mavenCommandExample();

        // 示例3：文件操作
        fileOperationsExample();

        System.out.println("\n=== 所有示例运行完成 ===");
    }

    /**
     * 示例1：基本使用
     */
    public static void basicUsageExample() {
        System.out.println("\n=== 示例1：基本使用 ===");

        // 创建临时目录
        try {
            Path baseTemp = Files.createTempDirectory("vfs-base-example-");
            Path deltaTemp = Files.createTempDirectory("vfs-delta-example-");

            File baseDir = baseTemp.toFile();
            File deltaDir = deltaTemp.toFile();

            // 创建虚拟文件系统
            DeltaVirtualFileSystem vfs = new DeltaVirtualFileSystem(baseDir, deltaDir);

            System.out.println("Base目录: " + baseDir.getAbsolutePath());
            System.out.println("Delta目录: " + deltaDir.getAbsolutePath());

            // 在base目录中创建一个文件
            File baseFile = new File(baseDir, "example.txt");
            Files.write(baseFile.toPath(), "Base content".getBytes());
            System.out.println("在base目录创建文件: " + baseFile.getAbsolutePath());

            // 读取文件
            File file = vfs.getFile("example.txt");
            if (file != null) {
                System.out.println("从虚拟文件系统读取文件: " + file.getAbsolutePath());
                String content = new String(Files.readAllBytes(file.toPath()));
                System.out.println("文件内容: " + content);
            }

            // 在delta目录中创建同名文件
            File deltaFile = new File(deltaDir, "example.txt");
            Files.write(deltaFile.toPath(), "Delta content".getBytes());
            System.out.println("在delta目录创建同名文件: " + deltaFile.getAbsolutePath());

            // 再次读取，应该返回delta目录中的文件
            file = vfs.getFile("example.txt");
            if (file != null) {
                System.out.println("再次读取文件（应该来自delta）: " + file.getAbsolutePath());
                String content = new String(Files.readAllBytes(file.toPath()));
                System.out.println("文件内容: " + content);
            }

            // 清理
            deleteDirectory(baseDir);
            deleteDirectory(deltaDir);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 示例2：Maven命令构建
     */
    public static void mavenCommandExample() {
        System.out.println("\n=== 示例2：Maven命令构建 ===");

        try {
            // 创建临时目录
            Path baseTemp = Files.createTempDirectory("vfs-base-maven-");
            Path deltaTemp = Files.createTempDirectory("vfs-delta-maven-");

            // 创建VFS Maven CLI
            VfsMavenCli cli = new VfsMavenCli(baseTemp.toFile(), deltaTemp.toFile());

            System.out.println("Base目录: " + cli.getBaseDir().getAbsolutePath());
            System.out.println("Delta目录: " + cli.getDeltaDir().getAbsolutePath());

            // 构建Maven编译命令
            List<String> command = cli.buildMavenCommand("mvn", "compile");
            System.out.println("\nMaven编译命令:");
            cli.printCommand(command);

            // 构建Maven测试命令（带额外参数）
            List<String> testCommand = cli.buildMavenCommand(
                    "mvn",
                    new String[]{"test"},
                    "-DskipTests=false", "-Dtest=MyTest"
            );
            System.out.println("\nMaven测试命令:");
            cli.printCommand(testCommand);

            // 构建Maven安装命令
            List<String> installCommand = cli.buildMavenCommand(
                    "mvn",
                    "clean", "install", "-DskipTests"
            );
            System.out.println("\nMaven安装命令:");
            cli.printCommand(installCommand);

            // 清理
            deleteDirectory(baseTemp.toFile());
            deleteDirectory(deltaTemp.toFile());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 示例3：文件操作
     */
    public static void fileOperationsExample() {
        System.out.println("\n=== 示例3：文件操作 ===");

        try {
            // 创建临时目录
            Path baseTemp = Files.createTempDirectory("vfs-base-fileops-");
            Path deltaTemp = Files.createTempDirectory("vfs-delta-fileops-");

            File baseDir = baseTemp.toFile();
            File deltaDir = deltaTemp.toFile();

            // 创建虚拟文件系统
            DeltaVirtualFileSystem vfs = new DeltaVirtualFileSystem(baseDir, deltaDir);

            // 1. 检查文件是否存在
            System.out.println("1. 检查文件是否存在:");
            System.out.println("   example.txt 存在: " + vfs.exists("example.txt"));

            // 2. 写入新文件（总是写入delta目录）
            System.out.println("\n2. 写入新文件:");
            java.io.OutputStream os = vfs.getOutputStream("new/newfile.txt");
            os.write("This is a new file in delta".getBytes());
            os.close();
            System.out.println("   写入文件: new/newfile.txt");
            System.out.println("   文件存在: " + vfs.exists("new/newfile.txt"));

            // 3. 读取文件
            System.out.println("\n3. 读取文件:");
            try (InputStream is = vfs.getInputStream("new/newfile.txt")) {
                byte[] content = new byte[is.available()];
                is.read(content);
                System.out.println("   文件内容: " + new String(content));
            }

            // 4. 复制文件
            System.out.println("\n4. 复制文件:");
            File sourceFile = Files.createTempFile("source-", ".txt").toFile();
            Files.write(sourceFile.toPath(), "Copied content".getBytes());
            vfs.copyToVirtual(sourceFile, "copied/copied.txt");
            System.out.println("   复制文件到: copied/copied.txt");
            System.out.println("   文件存在: " + vfs.exists("copied/copied.txt"));

            // 5. 删除文件（只删除delta目录中的文件）
            System.out.println("\n5. 删除文件:");
            vfs.deleteFile("new/newfile.txt");
            System.out.println("   删除文件: new/newfile.txt");
            System.out.println("   文件存在: " + vfs.exists("new/newfile.txt"));

            // 6. 列出文件
            System.out.println("\n6. 列出目录下的文件:");
            List<File> files = vfs.listFiles("copied");
            System.out.println("   找到文件数: " + files.size());
            for (File file : files) {
                System.out.println("   - " + file.getName());
            }

            // 7. 获取最后修改时间
            System.out.println("\n7. 获取最后修改时间:");
            long lastModified = vfs.getLastModified("copied/copied.txt");
            System.out.println("   copied/copied.txt 最后修改时间: " + lastModified);

            // 清理
            deleteDirectory(baseDir);
            deleteDirectory(deltaDir);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 递归删除目录
     */
    private static void deleteDirectory(File directory) throws Exception {
        if (directory == null || !directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    Files.deleteIfExists(file.toPath());
                }
            }
        }

        Files.deleteIfExists(directory.toPath());
    }
}
