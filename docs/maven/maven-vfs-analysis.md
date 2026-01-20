# Maven 虚拟文件系统路径定制分析

## 概述

基于 Maven 4.x 源码分析，可以通过虚拟文件系统(Virtual File System)定制 Maven 编译(`mvn compile`)时的实际访问路径。

## 核心扩展点

### 1. WorkspaceReader - 仓库(依赖)访问定制

**接口定义** (Eclipse Aether 提供):
```java
public interface WorkspaceReader {
    WorkspaceRepository getRepository();
    File findArtifact(Artifact artifact);
    List<String> findVersions(Artifact artifact);
}
```

**Maven 扩展版本** (`org.apache.maven.impl.resolver.MavenWorkspaceReader`):
```java
public interface MavenWorkspaceReader extends WorkspaceReader {
    Model findModel(Artifact artifact);  // 额外方法，返回 POM model
}
```

### 2. MavenChainedWorkspaceReader - 多个 WorkspaceReader 的链式组合

**位置**: `impl/maven-core/src/main/java/org/apache/maven/resolver/MavenChainedWorkspaceReader.java`

**关键代码** (Line 55-57):
```java
public MavenChainedWorkspaceReader(WorkspaceReader... readers) {
    setReaders(Arrays.asList(readers));
}
```

**创建位置** (`DefaultMaven.java` Line 213-214):
```java
MavenChainedWorkspaceReader chainedWorkspaceReader =
        new MavenChainedWorkspaceReader(request.getWorkspaceReader(), ideWorkspaceReader);
```

### 3. WorkspaceReader 优先级顺序

**位置**: `DefaultMaven.java` Line 336-351 `setupWorkspaceReader()` 方法

优先级从高到低:
1. **ReactorReader** - Reactor 多模块项目的 artifacts
2. **Session-scoped readers** - IDE 提供的和请求中设置的 WorkspaceReader
3. **Project-scoped extension components** - 项目级别的扩展组件

## 关键实现类

### ReactorReader - Reactor 多模块项目的文件映射

**位置**: `impl/maven-core/src/main/java/org/apache/maven/ReactorReader.java`

**关键方法**:

#### 1. 确定编译输出目录 (Line 198-225)
```java
private File determineBuildOutputDirectoryForArtifact(final MavenProject project, final Artifact artifact) {
    if (isTestArtifact(artifact)) {
        if (project.hasLifecyclePhase("test-compile")) {
            return new File(project.getBuild().getTestOutputDirectory());
        }
    } else {
        String type = artifact.getProperty("type", "");
        File outputDirectory = new File(project.getBuild().getOutputDirectory());

        // 检查项目是否在当前会话中构建
        boolean projectCompiledDuringThisSession =
                project.hasLifecyclePhase("compile") && COMPILE_PHASE_TYPES.contains(type);

        // 检查项目是否在会话中（未被 -pl, -rf 过滤）
        boolean projectHasOutputFromPreviousSession =
                !session.getProjects().contains(project) && outputDirectory.exists();

        if (projectHasOutputFromPreviousSession || projectCompiledDuringThisSession) {
            return outputDirectory;
        }
    }

    return null;
}
```

#### 2. 安装到项目本地仓库 (Line 456-480)
```java
private void installIntoProjectLocalRepository(Artifact artifact) {
    String extension = artifact.getExtension();
    String classifier = artifact.getClassifier();

    Path target = getArtifactPath(
            artifact.getGroupId(), artifact.getArtifactId(),
            artifact.getVersion(), classifier, extension);

    try {
        // 优先创建软链接，失败则复制
        Path source = artifact.getPath();
        if (!(Files.isRegularFile(target) && Files.isSameFile(source, target))) {
            Files.createDirectories(target.getParent());
            try {
                Files.deleteIfExists(target);
                Files.createLink(target, source);  // 创建软链接
            } catch (UnsupportedOperationException | IOException suppressed) {
                LOGGER.info("Copying {} to project local repository.", artifact);
                try {
                    Files.copy(source, target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException e) {
                    e.addSuppressed(suppressed);
                    throw e;
                }
            }
        }
    } catch (IOException e) {
        LOGGER.error("Error while copying artifact " + artifact + " to project local repository.", e);
    }
}
```

#### 3. 项目本地仓库路径计算 (Line 505-522)
```java
private Path getProjectLocalRepo() {
    if (projectLocalRepository == null) {
        Path root = session.getRequest().getRootDirectory();
        List<MavenProject> projects = session.getProjects();

        if (projects != null) {
            projectLocalRepository = projects.stream()
                    .filter(project -> Objects.equals(root.toFile(), project.getBasedir()))
                    .findFirst()
                    .map(project -> project.getBuild().getDirectory())
                    .map(Paths::get)
                    .orElseGet(() -> root.resolve("target"))
                    .resolve(PROJECT_LOCAL_REPO);  // "project-local-repo"
        } else {
            return root.resolve("target").resolve(PROJECT_LOCAL_REPO);
        }
    }
    return projectLocalRepository;
}
```

### RepositorySystemSessionFactory - Repository 系统会话工厂

**位置**: `impl/maven-core/src/main/java/org/apache/maven/internal/aether/DefaultRepositorySystemSessionFactory.java`

**关键注释** (Line 31-34):
```java
/**
 * Creates "ready to use" session builder instance. The factory does not set up one thing: the
 * {@link org.eclipse.aether.repository.WorkspaceReader}s, that is caller duty to figure out.
 * Workspace readers should be set up as very last thing before using resolver session, that is built
 * by invoking {@link SessionBuilder#build()} method.
 */
```

### DefaultMaven - 主执行入口

**位置**: `impl/maven-core/src/main/java/org/apache/maven/DefaultMaven.java`

**WorkspaceReader 设置流程** (Line 429-434):
```java
private CloseableSession newCloseableSession(
        MavenExecutionRequest request,
        WorkspaceReader workspaceReader) {
    return repositorySessionFactory
            .newRepositorySessionBuilder(request)
            .setWorkspaceReader(workspaceReader)  // 关键：设置 workspace reader
            .build();
}
```

## 路径定制方法

### 方法一: 实现自定义 WorkspaceReader

**最直接的方式**，完全控制 artifact 查找逻辑:

```java
package com.example.maven.vfs;

import org.apache.maven.api.model.Model;
import org.apache.maven.impl.resolver.MavenWorkspaceReader;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;

@Named("virtual-fs-reader")
@Singleton
public class VirtualFileSystemWorkspaceReader implements MavenWorkspaceReader {

    private final String virtualBasePath;

    public VirtualFileSystemWorkspaceReader(String virtualBasePath) {
        this.virtualBasePath = virtualBasePath;
    }

    @Override
    public WorkspaceRepository getRepository() {
        return new WorkspaceRepository("virtual-fs", null);
    }

    @Override
    public File findArtifact(Artifact artifact) {
        // 从虚拟文件系统返回 artifact
        String virtualPath = resolveVirtualPath(artifact);
        File file = new File(virtualPath);

        return file.exists() ? file : null;
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        // 返回虚拟文件系统中可用的版本
        return List.of("1.0.0-VIRTUAL", "2.0.0-VIRTUAL");
    }

    @Override
    public Model findModel(Artifact artifact) {
        // 从虚拟文件系统返回 POM model
        File pomFile = new File(resolveVirtualPath(artifact) + ".pom");
        return loadVirtualModel(pomFile);
    }

    private String resolveVirtualPath(Artifact artifact) {
        return virtualBasePath + "/"
                + artifact.getGroupId().replace('.', '/')
                + "/"
                + artifact.getArtifactId()
                + "/"
                + artifact.getVersion()
                + "/"
                + artifact.getArtifactId()
                + "-"
                + artifact.getVersion()
                + (artifact.getClassifier() != null ? "-" + artifact.getClassifier() : "")
                + "."
                + artifact.getExtension();
    }

    private Model loadVirtualModel(File pomFile) {
        // 实现：加载并解析 POM 文件
        // 使用 Maven 的 ModelReader 或手动解析
        return null;  // 简化示例
    }
}
```

### 方法二: 通过 MavenExecutionRequest 设置

**编程方式设置**，适用于运行时动态配置:

```java
import org.apache.maven.DefaultMaven;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;

public class MavenWithCustomVFS {
    public static void main(String[] args) {
        // 创建自定义 WorkspaceReader
        WorkspaceReader vfsReader = new VirtualFileSystemWorkspaceReader("/path/to/virtual/fs");

        // 创建 Maven 执行请求
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(new File("/actual/project/path"));
        request.setPom(new File("/actual/project/path/pom.xml"));
        request.setGoals(List.of("compile"));
        request.setWorkspaceReader(vfsReader);  // 关键：设置自定义 WorkspaceReader

        // 执行 Maven
        DefaultMaven maven = new DefaultMaven(/* dependencies */);
        MavenExecutionResult result = maven.execute(request);

        // 处理结果
        if (result.hasExceptions()) {
            result.getExceptions().forEach(e -> e.printStackTrace());
        }
    }
}
```

### 方法三: 使用 Maven Extension (DI 方式)

**生产环境推荐方式**，通过依赖注入注册:

**1. 创建扩展项目结构**:
```
maven-vfs-extension/
├── pom.xml
└── src/
    └── main/
        └── resources/
            └── META-INF/
                └── maven/
                    └── extension.xml
```

**2. pom.xml**:
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>maven-vfs-extension</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>Maven VFS Extension</name>
    <description>Virtual File System Workspace Reader Extension</description>

    <dependencies>
        <dependency>
            <groupId>org.apache.maven</groupId>
            <artifactId>maven-core</artifactId>
            <version>${maven.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.eclipse.aether</groupId>
            <artifactId>aether-api</artifactId>
            <version>1.1.0</version>
        </dependency>
    </dependencies>
</project>
```

**3. META-INF/maven/extension.xml**:
```xml
<extension>
  <components>
    <component>
      <role>org.eclipse.aether.repository.WorkspaceReader</role>
      <role-hint>virtual-fs</role-hint>
      <implementation>com.example.maven.vfs.VirtualFileSystemWorkspaceReader</implementation>
      <description>Virtual File System Workspace Reader</description>
    </component>
  </components>
</extension>
```

**4. 安装扩展**:
```bash
mvn clean install
```

**5. 使用扩展**:
在项目的 `~/.m2/extensions.xml` 中注册:
```xml
<extensions>
  <extension>
    <groupId>com.example</groupId>
    <artifactId>maven-vfs-extension</artifactId>
    <version>1.0.0</version>
  </extension>
</extensions>
```

或者在项目的 `pom.xml` 中:
```xml
<build>
  <extensions>
    <extension>
      <groupId>com.example</groupId>
      <artifactId>maven-vfs-extension</artifactId>
      <version>1.0.0</version>
    </extension>
  </extensions>
</build>
```

## 源码文件访问路径定制

### MavenProject 中的源码路径管理

**位置**: `impl/maven-core/src/main/java/org/apache/maven/project/MavenProject.java`

#### 1. 基础目录 (Line 290-302)
```java
@Deprecated
public File getBasedir() {
    return basedir;
}

public Path getBaseDirectory() {
    return getBasedir().toPath();
}
```

#### 2. 添加源码根目录 (Line 347-351)
```java
public void addSourceRoot(@Nonnull ProjectScope scope,
                         @Nonnull Language language,
                         @Nonnull Path directory) {
    directory = getBaseDirectory()
            .resolve(Objects.requireNonNull(directory, "directory cannot be null"))
            .normalize();
    addSourceRoot(new DefaultSourceRoot(scope, language, directory));
}
```

### 定制源码路径的方法

#### 方法一: POM 配置

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
  ...
  <build>
    <!-- 使用绝对路径指向虚拟文件系统 -->
    <sourceDirectory>/virtual/path/src/main/java</sourceDirectory>

    <!-- 或相对于项目根目录 -->
    <sourceDirectory>${project.basedir}/virtual-src</sourceDirectory>

    <!-- 测试源码目录 -->
    <testSourceDirectory>/virtual/path/src/test/java</testSourceDirectory>

    <!-- 资源目录 -->
    <resources>
      <resource>
        <directory>/virtual/path/src/main/resources</directory>
      </resource>
    </resources>

    <!-- 编译输出目录 -->
    <outputDirectory>/virtual/output/classes</outputDirectory>
    <testOutputDirectory>/virtual/output/test-classes</testOutputDirectory>
  </build>
</project>
```

#### 方法二: 编程方式动态修改

```java
import org.apache.maven.project.MavenProject;

// 在 Maven 插件或扩展中
public class SourcePathCustomizer {
    public void customizeSourcePaths(MavenProject project) {
        // 动态设置源码目录
        String virtualSourcePath = "/virtual/fs/src/main/java";

        // 方式 1: 直接设置 build 属性
        project.getBuild().setSourceDirectory(virtualSourcePath);

        // 方式 2: 使用 addSourceRoot API
        project.addSourceRoot(
            ProjectScope.MAIN,
            Language.JAVA_FAMILY,
            java.nio.file.Path.of(virtualSourcePath)
        );

        // 设置输出目录
        project.getBuild().setOutputDirectory("/virtual/fs/target/classes");
        project.getBuild().setTestOutputDirectory("/virtual/fs/target/test-classes");
    }
}
```

#### 方法三: 通过系统属性

```bash
# 运行时指定
mvn compile -Dproject.build.sourceDirectory=/virtual/path/src/main/java \
              -Dproject.build.outputDirectory=/virtual/output/classes

# 或使用自定义属性
mvn compile -Dvirtual.source.root=/virtual/fs/src/main/java
```

然后在 POM 中引用:
```xml
<build>
  <sourceDirectory>${virtual.source.root}</sourceDirectory>
</build>
```

## 本地仓库路径定制

### 方法一: MavenExecutionRequest 设置

```java
MavenExecutionRequest request = new DefaultMavenExecutionRequest();
request.setLocalRepositoryPath(new File("/path/to/virtual/local/repo"));
```

### 方法二: settings.xml 配置

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <localRepository>/path/to/virtual/local/repo</localRepository>

  <!-- 或使用多个本地仓库 (Maven 4.x 支持本地仓库链) -->
  <mirrors>
    <mirror>
      <id>virtual-repo</id>
      <mirrorOf>*</mirrorOf>
      <name>Virtual Repository</name>
      <url>file:/path/to/virtual/repo</url>
    </mirror>
  </mirrors>
</settings>
```

### 方法三: 系统属性

```bash
# 设置本地仓库
export MAVEN_OPTS="-Dmaven.repo.local=/path/to/virtual/repo"
mvn compile

# 或使用命令行
mvn compile -Dmaven.repo.local=/path/to/virtual/repo
```

### Maven 4.x 本地仓库链

**位置**: `DefaultRepositorySystemSessionFactory.java` Line 366-382

```java
ArrayList<Path> paths = new ArrayList<>();
String localRepoHead = mergedProps.get(Constants.MAVEN_REPO_LOCAL_HEAD);
if (localRepoHead != null) {
    Arrays.stream(localRepoHead.split(","))
            .filter(p -> p != null && !p.trim().isEmpty())
            .map(this::resolve)
            .forEach(paths::add);
}
paths.add(Paths.get(request.getLocalRepository().getBasedir()));
String localRepoTail = mergedProps.get(Constants.MAVEN_REPO_LOCAL_TAIL);
if (localRepoTail != null) {
    Arrays.stream(localRepoTail.split(","))
            .filter(p -> p != null && !p.trim().isEmpty())
            .map(this::resolve)
            .forEach(paths::add);
}
sessionBuilder.withLocalRepositoryBaseDirectories(paths);
```

**使用方式**:
```bash
mvn compile \
  -Dmaven.repo.local.head=/path/to/repo1,/path/to/repo2 \
  -Dmaven.repo.local.tail=/path/to/repo3
```

## 完整示例: 虚拟文件系统实现

### 场景: 从内存数据库加载 artifact

```java
package com.example.maven.vfs.memory;

import org.apache.maven.api.model.Model;
import org.apache.maven.impl.resolver.MavenWorkspaceReader;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named("memory-db-reader")
public class MemoryDatabaseWorkspaceReader implements MavenWorkspaceReader {

    // 模拟内存数据库
    private final Map<String, byte[]> artifactCache = new HashMap<>();
    private final Map<String, Model> modelCache = new HashMap<>();

    public MemoryDatabaseWorkspaceReader() {
        // 初始化：从内存数据库加载 artifacts
        initializeArtifacts();
    }

    private void initializeArtifacts() {
        // 示例：将 artifact 存储到内存
        artifactCache.put("com.example:app:1.0.0", loadArtifact("app-1.0.0.jar"));
        modelCache.put("com.example:app:1.0.0", loadModel("app-1.0.0.pom"));
    }

    @Override
    public WorkspaceRepository getRepository() {
        return new WorkspaceRepository("memory-db", null);
    }

    @Override
    public File findArtifact(Artifact artifact) {
        String key = artifact.getGroupId() + ":"
                    + artifact.getArtifactId() + ":"
                    + artifact.getVersion();

        byte[] content = artifactCache.get(key);
        if (content == null) {
            return null;
        }

        // 创建临时文件供 Maven 使用
        try {
            Path tempFile = Files.createTempFile("maven-vfs-", ".jar");
            Files.write(tempFile, content);
            return tempFile.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp file", e);
        }
    }

    @Override
    public List<String> findVersions(Artifact artifact) {
        String prefix = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":";
        return artifactCache.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> key.substring(prefix.lastIndexOf(':') + 1))
                .toList();
    }

    @Override
    public Model findModel(Artifact artifact) {
        String key = artifact.getGroupId() + ":"
                    + artifact.getArtifactId() + ":"
                    + artifact.getVersion();
        return modelCache.get(key);
    }

    private byte[] loadArtifact(String name) {
        // 从内存数据库加载二进制数据
        return new byte[0];  // 简化示例
    }

    private Model loadModel(String name) {
        // 从内存数据库加载 POM
        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("app");
        model.setVersion("1.0.0");
        return model;
    }
}
```

### 使用示例

```java
public class MavenWithMemoryDB {
    public static void main(String[] args) {
        // 创建内存数据库 WorkspaceReader
        WorkspaceReader memoryReader = new MemoryDatabaseWorkspaceReader();

        // 设置 Maven 执行请求
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setBaseDirectory(new File("/actual/project"));
        request.setPom(new File("/actual/project/pom.xml"));
        request.setGoals(List.of("compile"));
        request.setWorkspaceReader(memoryReader);

        // 执行
        DefaultMaven maven = createMavenInstance();
        MavenExecutionResult result = maven.execute(request);

        if (result.hasExceptions()) {
            System.err.println("Build failed:");
            result.getExceptions().forEach(e -> e.printStackTrace());
        } else {
            System.out.println("Build successful!");
        }
    }

    private static DefaultMaven createMavenInstance() {
        // 使用依赖注入容器创建 Maven 实例
        // 实际项目中应使用 Plexus 或 CDI
        return new DefaultMaven(/* 注入依赖 */);
    }
}
```

## 关键类文件位置总结

| 类 | 文件路径 | 作用 |
|-----|---------|-----|
| `WorkspaceReader` | Eclipse Aether API | artifact 查找接口 |
| `MavenWorkspaceReader` | `impl/maven-impl/src/main/java/.../resolver/MavenWorkspaceReader.java` | Maven 扩展接口，增加 findModel 方法 |
| `MavenChainedWorkspaceReader` | `impl/maven-core/src/main/java/.../resolver/MavenChainedWorkspaceReader.java` | 链式 workspace reader，支持多个 reader 组合 |
| `ReactorReader` | `impl/maven-core/src/main/java/.../ReactorReader.java` | Reactor 多模块项目实现，处理模块间依赖 |
| `DefaultMaven` | `impl/maven-core/src/main/java/.../DefaultMaven.java` | 主执行入口，负责设置和配置 workspace reader |
| `DefaultRepositorySystemSessionFactory` | `impl/maven-core/src/main/java/.../aether/DefaultRepositorySystemSessionFactory.java` | Repository 系统会话工厂，注释说明 WorkspaceReader 应在最后设置 |
| `RepositorySystemSessionFactory` | `impl/maven-core/src/main/java/.../resolver/RepositorySystemSessionFactory.java` | 工厂接口，定义 newRepositorySessionBuilder 方法 |
| `MavenExecutionRequest` | `impl/maven-core/src/main/java/.../execution/MavenExecutionRequest.java` | Maven 执行请求接口，包含 setWorkspaceReader 方法 |
| `MavenProject` | `impl/maven-core/src/main/java/.../project/MavenProject.java` | Maven 项目模型，管理源码和输出目录 |
| `MavenSession` | `impl/maven-core/src/main/java/.../execution/MavenSession.java` | Maven 执行会话，封装请求和系统会话 |

## 关键源码行号索引

### DefaultMaven.java

| 行号 | 内容 |
|------|------|
| 213-214 | 创建 MavenChainedWorkspaceReader |
| 336-351 | setupWorkspaceReader - 配置 workspace reader 优先级 |
| 429-434 | newCloseableSession - 通过 SessionBuilder.setWorkspaceReader() 设置 |
| 455-478 | getProjectScopedExtensionComponents - 获取项目级别扩展 |

### ReactorReader.java

| 行号 | 内容 |
|------|------|
| 198-225 | determineBuildOutputDirectoryForArtifact - 确定输出目录 |
| 281-298 | hasBeenPackagedDuringThisSession - 判断是否已打包 |
| 456-480 | installIntoProjectLocalRepository - 安装到项目本地仓库 |
| 505-522 | getProjectLocalRepo - 计算项目本地仓库路径 |

### DefaultRepositorySystemSessionFactory.java

| 行号 | 内容 |
|------|------|
| 31-34 | 类注释：说明 WorkspaceReader 应在最后设置 |
| 366-382 | 本地仓库链配置 (head + main + tail) |

### MavenProject.java

| 行号 | 内容 |
|------|------|
| 290-302 | getBaseDirectory - 获取项目基础目录 |
| 347-351 | addSourceRoot - 添加源码根目录 |

## 注意事项

### 1. 生命周期感知

WorkspaceReader 的 `findArtifact()` 返回结果时，Maven 会根据当前生命周期阶段选择合适的实现:

- **compile 阶段**: 返回 `target/classes` (如果可用)
- **package 阶段后**: 返回打包好的 JAR 文件
- **Reactor 多模块**: 优先从其他模块获取

### 2. 文件系统限制

虚拟文件系统实现需要注意:
- 临时文件处理：可能需要创建临时文件供 Maven 读取
- 文件属性：确保文件的可读性、修改时间等属性正确设置
- 软链接支持：如使用软链接，需要处理不支持软链接的文件系统

### 3. 线程安全

WorkspaceReader 可能被多线程并发调用，确保实现是线程安全的。

### 4. 性能考虑

- 缓存：频繁访问的 artifact 应缓存
- 懒加载：只在需要时加载虚拟文件
- 资源释放：临时文件应及时清理

### 5. 调试技巧

启用详细日志查看 WorkspaceReader 调用:

```bash
mvn compile -X -Dorg.slf4j.simpleLogger.log.org.apache.maven.resolver=DEBUG
```

## 总结

Maven 4.x 通过 **WorkspaceReader** 接口提供了灵活的虚拟文件系统扩展能力。关键点包括:

1. ✅ **Repository/依赖路径定制**: 实现 `WorkspaceReader` 接口
2. ✅ **源码路径定制**: POM 配置 `<sourceDirectory>` 或编程方式修改
3. ✅ **编译输出路径定制**: POM 配置 `<outputDirectory>` 或 API 设置
4. ✅ **本地仓库路径定制**: `MavenExecutionRequest.setLocalRepositoryPath()` 或属性 `maven.repo.local`
5. ✅ **本地仓库链**: Maven 4.x 支持多个本地仓库 (head + main + tail)

**架构设计亮点**:
- WorkspaceReader 通过 `SessionBuilder.setWorkspaceReader()` 设置，是"设置解析会话前最后一步"
- `MavenChainedWorkspaceReader` 支持多个 reader 组合，优先级可控
- Reactor 多模块项目自动集成，无需额外配置
- 支持项目级别扩展，实现更细粒度的控制

这种设计使得 Maven 可以轻松集成虚拟文件系统、内存数据库、分布式存储等非传统文件系统的 artifact 存储方案。
