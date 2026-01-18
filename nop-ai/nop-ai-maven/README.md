# nop-ai-maven

Nop AI Maven Extension - 支持虚拟文件系统的Maven工具

## 概述

`nop-ai-maven` 是一个支持虚拟文件系统的Maven工具，允许通过base目录和delta目录实现文件系统的虚拟化。该工具特别适用于需要隔离和增量修改场景的Maven构建。

**注意**：本工具**不依赖Maven API**，定义了自己的接口以实现最大兼容性。可以作为独立工具使用，也可以集成到自定义Maven插件中。

## 核心功能

### 虚拟文件系统

虚拟文件系统由两部分组成：

- **base目录**：基础目录，只读，包含原始文件和依赖
- **delta目录**：增量目录，可写，覆盖base目录中的文件，新增文件也存放在此

### 工作原理

1. **读取文件**：优先从delta目录读取，如果delta目录不存在该文件，则从base目录读取
2. **写入文件**：总是写入delta目录
3. **删除文件**：只删除delta目录中的文件，不影响base目录

### 虚拟Repository

Maven本地仓库也支持虚拟化，分为：
- **base repository**：基础仓库，只读
- **delta repository**：增量仓库，可写

查找artifact时优先从delta仓库查找，安装artifact时总是安装到delta仓库。

## 安装

### 编译安装

```bash
# 在nop-entropy根目录执行
cd nop-entropy
mvn clean install -DskipTests
```

## 使用方法

### 方式一：通过编程方式使用（推荐）

直接使用核心类进行文件操作和仓库管理：

```java
import io.nop.ai.maven.cli.VfsMavenCli;
import io.nop.ai.maven.vfs.DeltaVirtualFileSystem;
import io.nop.ai.maven.vfs.DeltaWorkspaceReader;
import io.nop.ai.maven.vfs.ArtifactInfo;

// 1. 使用VfsMavenCli构建Maven命令
VfsMavenCli cli = new VfsMavenCli("/path/to/base", "/path/to/delta");
List<String> command = cli.buildMavenCommand("mvn", "compile", "install");
cli.printCommand(command);

// 2. 直接使用DeltaVirtualFileSystem
DeltaVirtualFileSystem vfs = new DeltaVirtualFileSystem("/path/to/base", "/path/to/delta");
File file = vfs.getFile("path/to/file.txt");
InputStream is = vfs.getInputStream("path/to/file.txt");

// 3. 使用DeltaWorkspaceReader管理Maven仓库
DeltaWorkspaceReader reader = new DeltaWorkspaceReader("/path/to/base/repo", "/path/to/delta/repo");
ArtifactInfo artifact = new ArtifactInfo("com.example", "my-artifact", "1.0.0");
File artifactFile = reader.findArtifact(artifact);
```

### 方式二：通过系统属性配置

```bash
# 设置环境变量或系统属性
export VFS_ENABLED=true
export VFS_BASE_DIR=/path/to/base
export VFS_DELTA_DIR=/path/to/delta

# 或者在Java中设置
System.setProperty("vfs.enabled", "true");
System.setProperty("vfs.base.dir", "/path/to/base");
System.setProperty("vfs.delta.dir", "/path/to/delta");

// 创建配置器
DeltaWorkspaceReaderConfigurator configurator = new DeltaWorkspaceReaderConfigurator();
DeltaWorkspaceReader reader = configurator.createWorkspaceReader();
```

### 方式三：集成到自定义Maven插件

```java
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import io.nop.ai.maven.vfs.DeltaWorkspaceReader;
import io.nop.ai.maven.vfs.ArtifactInfo;

/**
 * 自定义Maven插件
 */
@Mojo(name = "vfs-example", defaultPhase = LifecyclePhase.COMPILE)
public class VfsExampleMojo extends AbstractMojo {

    public void execute() throws MojoExecutionException {
        // 创建DeltaWorkspaceReader
        DeltaWorkspaceReader reader = new DeltaWorkspaceReader(
                new File("/path/to/base/repo"),
                new File("/path/to/delta/repo")
        );

        // 查找artifact
        ArtifactInfo artifact = new ArtifactInfo("com.example", "my-artifact", "1.0.0");
        File artifactFile = reader.findArtifact(artifact);

        if (artifactFile != null) {
            getLog().info("Found artifact: " + artifactFile.getAbsolutePath());
        }

        // 安装artifact
        // reader.installArtifact(sourceFile, artifact);
    }
}
```

## 使用场景

### 场景1：AI辅助开发时的代码隔离

在AI辅助开发过程中，将AI生成的代码放在delta目录，原始代码保留在base目录：

```bash
# 设置base目录为项目源码目录
# 设置delta目录为AI生成的代码目录
VfsMavenCli cli = new VfsMavenCli("/project/src", "/project/ai-generated/src");
```

### 场景2：多版本并行开发

对于需要同时维护多个版本的项目，可以使用base目录保存稳定版本，delta目录保存开发版本：

```bash
# base目录保存稳定版本
DeltaVirtualFileSystem vfs = new DeltaVirtualFileSystem(
    "/project/stable/src",
    "/project/development/src"
);
```

### 场景3：依赖隔离测试

隔离测试环境的依赖和生产环境的依赖：

```bash
# base目录为生产依赖，delta目录为测试依赖
DeltaWorkspaceReader reader = new DeltaWorkspaceReader(
    "~/.m2/repository-prod",
    "~/.m2/repository-test"
);
```

## 项目结构

```
nop-ai-maven/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/
    │   │   └── io/
    │   │       └── nop/
    │   │           └── ai/
    │   │               └── maven/
    │   │                   ├── cli/
    │   │                   │   └── VfsMavenCli.java
    │   │                   ├── config/
    │   │                   │   └── DeltaWorkspaceReaderConfigurator.java
    │   │                   └── vfs/
    │   │                       ├── DeltaVirtualFileSystem.java
    │   │                       ├── DeltaWorkspaceReader.java
    │   │                       └── ArtifactInfo.java
    │   └── resources/
    └── test/
        └── java/
            └── io/
                └── nop/
                    └── ai/
                        └── maven/
                            ├── examples/
                            │   └── VfsMavenUsageExample.java
                            └── vfs/
                                ├── DeltaVirtualFileSystemTest.java
                                └── DeltaWorkspaceReaderTest.java
```

## 核心类说明

### ArtifactInfo

Artifact信息封装类，避免依赖Maven API。

**主要方法：**
- 构造器（多个重载） - 创建artifact信息
- `getGroupId()` - 获取groupId
- `getArtifactId()` - 获取artifactId
- `getVersion()` - 获取版本
- `getClassifier()` - 获取classifier
- `getExtension()` - 获取扩展名

### DeltaVirtualFileSystem

虚拟文件系统核心类，实现文件读取、写入、删除等操作。

**主要方法：**
- `getFile(String relativePath)` - 获取虚拟文件
- `getInputStream(String relativePath)` - 获取输入流
- `getOutputStream(String relativePath)` - 获取输出流（总是写入delta）
- `copyToVirtual(File sourceFile, String relativePath)` - 复制文件到虚拟文件系统
- `deleteFile(String relativePath)` - 删除文件（仅删除delta目录中的文件）
- `exists(String relativePath)` - 检查文件是否存在
- `listFiles(String relativePath)` - 列出目录下的文件
- `getLastModified(String relativePath)` - 获取文件最后修改时间

### DeltaWorkspaceReader

Maven仓库虚拟化实现，支持虚拟repository。

**主要方法：**
- `findArtifact(ArtifactInfo artifact)` - 查找artifact（优先从delta仓库查找）
- `findVersions(ArtifactInfo artifact)` - 查找artifact的所有版本
- `installArtifact(File sourceFile, ArtifactInfo artifact)` - 安装artifact到delta仓库
- `getRepositoryKey()` - 获取repository key
- `getVirtualFileSystem()` - 获取虚拟文件系统对象
- `getBaseRepoPath()` - 获取基础仓库路径
- `getDeltaRepoPath()` - 获取增量仓库路径

### DeltaWorkspaceReaderConfigurator

配置工厂，根据系统属性创建和配置DeltaWorkspaceReader。

**主要方法：**
- `createWorkspaceReader()` - 创建WorkspaceReader
- `isVfsEnabled()` - 检查是否启用虚拟文件系统
- `getBaseDir()` - 获取基础目录
- `getDeltaDir()` - 获取增量目录

### VfsMavenCli

Maven命令行工具，提供便捷的接口来构建Maven命令。

**主要方法：**
- `buildMavenCommand(String mavenCommand, List<String> goals, List<String> extraArgs)` - 构建Maven命令
- `printCommand(List<String> command)` - 打印命令
- `execute(String mavenCommand, List<String> goals, List<String> extraArgs)` - 执行命令（仅构建，不实际执行）

## 测试

运行单元测试：

```bash
cd nop-ai/nop-ai-maven
mvn test
```

运行使用示例：

```bash
cd nop-ai/nop-ai-maven
mvn test-compile
java -cp target/classes:target/test-classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q) \
  io.nop.ai.maven.examples.VfsMavenUsageExample
```

## 注意事项

1. **目录权限**：确保delta目录有写入权限
2. **路径格式**：建议使用绝对路径，避免相对路径带来的问题
3. **文件冲突**：delta目录中的文件会完全覆盖base目录中的同名文件
4. **磁盘空间**：注意delta目录的磁盘空间使用情况
5. **性能考虑**：频繁的文件读写可能会影响构建性能

## 故障排查

### 虚拟文件系统未生效

检查：
1. 系统属性是否正确设置（`vfs.base.dir` 和 `vfs.delta.dir`）
2. 目录是否存在且有正确的权限

启用调试日志：

```java
// 在代码中设置日志级别
System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
```

### 文件未找到

检查：
1. 文件路径是否正确（相对路径或绝对路径）
2. 文件是否存在于base或delta目录
3. 文件名大小写是否匹配（在某些文件系统上大小写敏感）

## 技术细节

### 依赖

本模块最小化依赖，仅依赖：
- **Nop Core**: `io.github.entropy-cloud:nop-core`
- **Nop API Core**: `io.github.entropy-cloud:nop-api-core`
- **SLF4J**: `org.slf4j:slf4j-api`（日志接口，scope: provided）
- **JUnit 4**: `junit:junit`（测试）

**注意**：本模块**不依赖Maven API**，实现了自己的ArtifactInfo接口，确保最大兼容性。

### 设计原则

1. **零依赖**：不依赖Maven、Spring或其他框架
2. **自包含**：所有接口和实现都包含在模块内部
3. **可测试**：完整的单元测试覆盖
4. **易用性**：提供多种使用方式（编程、配置、命令行）

## 贡献

欢迎提交Issue和Pull Request！

## 许可证

AGPL-3.0（中小企业可免费商用）

## 联系方式

- 项目主页: https://github.com/entropy-cloud/nop-entropy
- 文档: https://nop-platform.github.io/
- 讨论: https://gitcode.com/org/nop-platform/discussion

