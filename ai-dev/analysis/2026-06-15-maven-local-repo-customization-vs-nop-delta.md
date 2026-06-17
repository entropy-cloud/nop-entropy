# 定制 Maven 本地仓库 jar 与源码存放位置：能否做成 nop Delta 式分层文件系统？

> Status: open
> Date: 2026-06-15
> Scope: Maven Resolver（maven-resolver 2.0.x / Maven 4）本地仓库路径定制机制；jar 与源码 jar 存放位置分别如何重定向
> Conclusion: **能，而且 Maven Resolver 已经内置了完整的分层机制**。最简方案只需在 `.mvn/maven.config` 里加 `maven.repo.local.head` / `maven.repo.local.tail` 两个系统属性即可达成 nop Delta 式"上层覆盖下层"的分层查找；更细粒度的"按类型/按来源分流"则用 Resolver 自带的 `aether.localRepository.*` split 配置或实现 `LocalPathPrefixComposer` SPI。源码 jar 与普通 jar 在 Resolver 内部是同一个 `Artifact` 体系（仅 `classifier=sources`），**默认共用同一套分层逻辑**，需要分开存放时只要在 `LocalPathPrefixComposer` 里按 classifier 返回不同前缀即可。

## Context

- **需求**：希望像 nop 平台的 `_vfs/_delta/{layerId}/...` 那样，把 Maven 本地仓库 jar 包的存放位置、源码 jar 的存放位置分多层管理（基础层只读 / 定制层可写 / 项目层临时）。
- **类比对象**：nop 的 VFS 解析顺序是 `Tenant → Delta Layers（高→低）→ Base`，每层路径一一对应，找到即返回；Base 层内部又是 `外部覆盖目录 → VFS 索引 → Classpath 扫描 → libPaths → 当前项目源码`。详见 `docs-for-ai/02-core-guides/vfs-and-resource-resolution.md` 与 `docs-for-ai/02-core-guides/delta-customization.md`。
- **目标问题**：
  1. Maven 的 jar 包存储位置是怎么决定的？有多少扩展点？
  2. 源码 jar（`*-sources.jar`）的存储位置能不能和普通 jar 分开配置？
  3. 有没有"最简单的方式"实现分层？写不写代码？

涉及的源码主要在 `~/sources/maven`（Maven 4 主干，Resolver 2.0.x）以及本地仓库里的 `maven-resolver-impl-2.0.14.jar`。

## 关键事实速查

| 关注点 | 答案 | 入口 |
|---|---|---|
| jar 包存放位置由谁决定 | `LocalRepositoryManager`（LRM）接口 | `org.eclipse.aether.repository.LocalRepositoryManager` |
| 默认实现 | `EnhancedLocalRepositoryManager`（带 `_remote.repositories` 追踪） | `maven-resolver-impl` |
| 是否支持分层查找 | ✅ 内置，通过 `ChainedLocalRepositoryManager` | `maven-resolver-util` |
| 是否支持按类型/来源分流 | ✅ 内置，通过 `LocalPathPrefixComposer` 的 split 配置 | `maven-resolver-impl` |
| 用户层最简开关 | `maven.repo.local.head` + `maven.repo.local.tail` | `Constants.java` |
| 是否需要写代码 | 分层 / 分目录：**不需要**；按 classifier 分流：需要 ~30 行 | — |
| 源码 jar 是否独立机制 | ❌ 与普通 jar 同一 `Artifact`，仅 `classifier="sources"` | — |

## Analysis

### 1. jar 包存储位置：三层抽象决定最终路径

Resolver 把"artifact → 磁盘路径"的映射拆成三层可替换组件：

```
Artifact (GAV + classifier + extension)
        │
        ▼
┌──────────────────────────────────────────────────┐
│ LocalRepositoryManager (LRM)                     │   ← 决定"在哪个根目录下"
│   getPathForLocalArtifact(artifact)              │
│   getPathForRemoteArtifact(artifact, repo, ...)  │
└──────────────┬───────────────────────────────────┘
               │ = baseDir.resolve( prefix + relativePath )
               ▼
┌──────────────────────────────────────────────────┐
│ LocalPathPrefixComposer  (可选)                   │  ← 决定"按什么前缀分流"
│   getPathPrefixForLocalArtifact(artifact)        │     （split by released/snapshot/local/remote）
└──────────────┬───────────────────────────────────┘
               ▼
┌──────────────────────────────────────────────────┐
│ LocalPathComposer                                │  ← 决定"GAV → 相对路径"的标准布局
│   getPathForArtifact(artifact, local)            │     （Maven2 布局：group/artifact/version/...）
└──────────────────────────────────────────────────┘
```

**关键代码定位**（来自 `maven-resolver-impl-2.0.14.jar` 反编译）：

- `EnhancedLocalRepositoryManager.getPathForLocalArtifact()` 字节码：
  ```
  concatPaths(
    localPathPrefixComposer.getPathPrefixForLocalArtifact(artifact),
    super.getPathForLocalArtifact(artifact)   // SimpleLocalRepositoryManager → LocalPathComposer
  )
  ```
  即：**最终路径 = repoBase / prefix / 标准GAV相对路径**。
- `SimpleLocalRepositoryManager` 持有 `LocalRepository repository`（含 basePath）和 `LocalPathComposer`，提供"标准 Maven2 布局"。
- `DefaultLocalPathComposer.getPathForArtifact()` 用 `groupId.replace('.','/')/artifactId/baseVersion/artifactId-version[-classifier].extension` 拼路径（字节码确认）。

这三层都是 SPI 接口，DI（Sisu）里都能替换，但**绝大多数定制场景用不到替换**——下面几节会说明。

### 2. 内置的"分层"机制：ChainedLocalRepositoryManager（≈ nop Delta Layers）

这是回答"最简单方式"的核心发现。Resolver 2.0.x 在 `maven-resolver-util` 里内置了 `ChainedLocalRepositoryManager`，Maven 4 直接通过两个系统属性暴露：

| 属性 | 来源 | 作用 | since |
|---|---|---|---|
| `maven.repo.local.head` | `Constants.MAVEN_REPO_LOCAL_HEAD` | 在主仓库**前**插入若干个仓库（前置层，会"下推"主仓库到 tail） | Maven 4.0.0 |
| `maven.repo.local.tail` | `Constants.MAVEN_REPO_LOCAL_TAIL` | 在主仓库**后**追加若干个仓库（兜底层） | Maven 3.9.0 |
| `maven.repo.local.tail.ignoreAvailability` | `Constants.MAVEN_REPO_LOCAL_TAIL_IGNORE_AVAILABILITY` | tail 层是否跳过可用性检查（默认 true） | Maven 3.9.0 |

`DefaultRepositorySystemSessionFactory.java:360-376` 的关键逻辑：

```java
ArrayList<Path> paths = new ArrayList<>();
String localRepoHead = mergedProps.get(Constants.MAVEN_REPO_LOCAL_HEAD); // 前置层
if (localRepoHead != null) {
    Arrays.stream(localRepoHead.split(","))
          .filter(p -> p != null && !p.trim().isEmpty())
          .map(this::resolve)   // 支持 ~/ 前缀
          .forEach(paths::add);
}
paths.add(Paths.get(request.getLocalRepository().getBasedir()));  // 主仓库
String localRepoTail = mergedProps.get(Constants.MAVEN_REPO_LOCAL_TAIL); // 兜底层
if (localRepoTail != null) {
    Arrays.stream(localRepoTail.split(",")).map(this::resolve).forEach(paths::add);
}
sessionBuilder.withLocalRepositoryBaseDirectories(paths);
```

最终 Resolver 会把这些路径包装成一个 `ChainedLocalRepositoryManager`，其语义（反编译 `find()` 字节码确认）：

```
find(request):
    result = head.find(request)
    if result.available: return result      # ← 上层命中即返回，等价于 nop Delta 高优先级层
    for layer in tail:                       # ← 依次向下兜底，等价于 nop Base 层链
        result = layer.find(request)
        if result.available: return result
    return not-found
```

`add()`（安装/缓存写入）字节码确认：只往 `installTarget`（默认 head）或 `cacheTarget`（远程下载缓存目标）写，**不会**把命中层的文件回写到别处——这一点和 nop 的"Delta 层只读、不污染 Base"思路一致。

#### 对应到 nop Delta 的映射

| nop VFS 概念 | Maven Resolver 对应 |
|---|---|
| `_vfs/_delta/{高优先级layer}/...` | `maven.repo.local.head` 列出的前置仓库 |
| Base 层（VFS 索引 / Classpath / libPaths） | 主 `maven.repo.local` |
| `_vfs/_delta/{低优先级layer}/...` / 兜底 | `maven.repo.local.tail` 列出的尾仓库 |
| 找到即返回、不回写 | `ChainedLocalRepositoryManager.find()` 同样语义 |
| 路径一一对应（`/nop/auth/orm/app.orm.xml`） | `getPathForLocalArtifact()` 用 GAV 唯一确定相对路径，各层目录结构必须一致 |

### 3. 内置的"按类型分流"机制：split prefix（≈ nop 的子目录分流）

如果不想"整层分层"，只想在**同一个仓库根目录下**按条件分桶（例如把 release/snapshot/local-installed/remote-cached 分到不同子目录），Resolver 内置了 `LocalPathPrefixComposerFactorySupport`，提供以下配置（反编译确认常量名）：

| 配置属性（`aether.enhancedLocalRepository.*`） | 默认 | 作用 |
|---|---|---|
| `split` | false | 总开关 |
| `localPrefix` | `installed` | 本地安装件的前缀 |
| `splitLocal` | false | 是否把本地件单独分桶 |
| `remotePrefix` | `cached` | 远程缓存件的前缀 |
| `splitRemote` | false | 是否把远程件按 release/snapshot 分桶 |
| `splitRemoteRepository` | false | 是否按远程仓库 id 分桶 |
| `releasesPrefix` / `snapshotsPrefix` | `releases` / `snapshots` | release/snapshot 子前缀 |

例如开 `split=true&splitLocal=true&splitRemote=true` 后，同一个 artifact 会落到：

```
{repoBase}/installed/{GAV}/...       ← mvn install 装进来的
{repoBase}/cached/releases/{GAV}/... ← 从远程 release 仓库下载的
{repoBase}/cached/snapshots/{GAV}/...← 从远程 snapshot 仓库下载的
```

这正是 nop "在 Base 层内部再分子目录"的对应物。

### 4. 源码 jar（`-sources.jar`）的处理：没有独立机制，但分流很容易

**关键结论**：Resolver 内部**不区分**"源码 jar"和"普通 jar"。源码 jar 就是一个普通 `Artifact`，其 `classifier = "sources"`、`extension = "jar"`。它走完全相同的 `LocalRepositoryManager.getPathForLocalArtifact()` 路径，落盘布局是：

```
{groupId-as-path}/{artifactId}/{version}/{artifactId}-{version}-sources.jar
```

证据：
- `DefaultLocalPathComposer.getPathForArtifact()` 字节码：先拼 `artifactId-version`，再判断 `if (!classifier.isEmpty()) append("-" + classifier)`，最后 `if (!extension.isEmpty()) append("." + extension)`。classifier 对布局没有任何特殊分支。
- `SourceHandlingContext.java`（Maven 4 新文件）只管**当前项目自己** `src/` 目录下的源码根，与"依赖的源码 jar 存储"无关——容易被名字误导，需注意区分。

**因此要让源码 jar 与普通 jar 存到不同目录**，只有两条路：

1. **分层 + 路径前缀分流**（推荐，零代码或极少代码）：在自定义 `LocalPathPrefixComposer` 里按 `artifact.getClassifier()` 返回不同 prefix，例如：
   ```java
   public String getPathPrefixForLocalArtifact(Artifact a) {
       if ("sources".equals(a.getClassifier())) return "sources";
       if ("javadoc".equals(a.getClassifier())) return "javadoc";
       return "";
   }
   ```
   即可让源码 jar 落到 `{repoBase}/sources/{GAV}/...`，普通 jar 仍在 `{repoBase}/{GAV}/...`。
2. **直接分两个仓库**：用 `head`/`tail` 机制把 sources 放到一个独立目录，但 Resolver 的 `find()` 是按 GAV 顺序穿透所有层的，无法"按 classifier 路由到不同层"——所以**源码 jar 分库必须靠方案 1 的 prefix**，单纯 head/tail 做不到按 classifier 路由。

### 5. 扩展点全景（从"不写代码"到"完全自定义"）

| 方案 | 写不写代码 | 配置位置 | 能力上限 | 适合场景 |
|---|---|---|---|---|
| **A. head/tail 分层** | ❌ 纯配置 | `.mvn/maven.config` 或 `MAVEN_OPTS` | 多层只读/可写仓库，按层穿透查找 | **最简单**，直接对应 nop Delta Layers |
| **B. split prefix** | ❌ 纯配置 | `aether.enhancedLocalRepository.split=true` 等 | 同一仓库内按 local/remote/release/snapshot 分桶 | 想要分类整理、不引入多个目录 |
| **C. 自定义 LocalPathPrefixComposer** | ✅ ~30 行 + SPI 注册 | 实现 `LocalPathPrefixComposerFactory` 并用 Sisu `@Named` 注册 | 任意 prefix 规则（含按 classifier 分流源码 jar） | 源码 jar/javadoc 单独存放 |
| **D. 自定义 LocalRepositoryManagerFactory** | ✅ 中等 | 实现 `LocalRepositoryManagerFactory`，DI 替换默认 `EnhancedLocalRepositoryManagerFactory` | 完全自定义路径布局（如非 Maven2 布局） | 需要彻底改布局 |
| **E. 自定义 WorkspaceReader** | ✅ 中等 | 实现 `WorkspaceReader`，在 session 上 `setWorkspaceReader()` | 对"正在构建中的本项目产物"短路，不走本地仓库 | 多模块 reactor 共享、不经落盘 |
| **F. 完全自建 RepositorySystem** | ✅ 大 | 参考 `RepositorySystemSupplier` 自行 wire | 任何东西都能换 | 嵌入式使用 Resolver、脱离 Maven CLI |

> 所有方案都不需要改 Maven/Resolver 源码，全部走 SPI + DI（Sisu）。

### 6. "最简单方式"——具体配方

#### 需求 1：jar 包存放位置可分层（基础只读 + 定制可写）

**零代码**，在项目根 `.mvn/maven.config` 加一行（`.mvn/maven.config` 里每行是一个或多个参数，会被自动拼到每次 `mvn` 调用前）：

```
-Dmaven.repo.local.head=~/.m2/product-repo,~/.m2/base-repo
-Dmaven.repo.local.tail.ignoreAvailability=true
```

效果：
- 查找顺序：`~/.m2/product-repo` → `~/.m2/base-repo` → `~/.m2/repository`（主） → `tail`（如有）
- 新下载/新 install 的 jar 只写进 **head 第一个**（`installTarget` 默认 = head[0]），等价于 nop 的"Delta 层可写、Base 层只读"。
- 对应 nop：`head` ≈ `_delta/{高优先级}`，主 repo ≈ Base，`tail` ≈ 兜底 Delta。

#### 需求 2：源码 jar 单独存到一个目录

**需要一点代码**（方案 C）。最小骨架：

```java
@Named("sources-split")
@Singleton
public class SourcesSplitPrefixFactory extends LocalPathPrefixComposerFactorySupport {
    @Override
    public LocalPathPrefixComposer createComposer(RepositorySystemSession session) {
        return new LocalPathPrefixComposer() {
            @Override public String getPathPrefixForLocalArtifact(Artifact a) {
                return "sources".equals(a.getClassifier()) ? "sources" : "";
            }
            @Override public String getPathPrefixForRemoteArtifact(Artifact a, RemoteRepository r) {
                return "sources".equals(a.getClassifier()) ? "sources" : "";
            }
            @Override public String getPathPrefixForLocalMetadata(Metadata m) { return ""; }
            @Override public String getPathPrefixForRemoteMetadata(Metadata m, RemoteRepository r) { return ""; }
        };
    }
}
```

把它打成 jar 放到 `${maven.home}/lib/ext` 或作为 Maven 核心扩展（`pom.xml` 的 `<extensions>` / `.mvn/extensions.xml`）注册即可。之后：

```
{repoBase}/sources/{GAV}/{aid}-{ver}-sources.jar   ← 源码 jar
{repoBase}/{GAV}/{aid}-{ver}.jar                    ← 普通 jar
```

> 注意：`LocalPathPrefixComposerFactorySupport` 自身也读了 `split` 等配置，如果你的工厂只是覆盖 `createComposer`，请确保**不要**再 `super` 调用那些 `isSplit` 方法，否则会和系统属性配置冲突。

#### 需求 3：jar 与源码完全分到两个根目录

把方案 A + 方案 C 组合：用 head/tail 做分层，再用 prefix 把 sources 路由到 `sources/` 子目录。或者更激进——**自定义 `LocalRepositoryManagerFactory`**（方案 D），对 classifier=sources 的 artifact 返回一个完全不同 `basePath` 的 LRM。但通常没必要，方案 C 的子目录已经够清晰。

### 7. 与 nop Delta 的本质差异（避免误用）

| 维度 | nop Delta | Maven Resolver |
|---|---|---|
| 路径标识 | 虚拟路径 `/nop/auth/orm/app.orm.xml` | GAV 坐标 → 标准布局相对路径 |
| 合并语义 | **内容合并**（`x:extends="super"` 做 XML 差量合并） | **命中即返回**（无合并，第一个找到的文件就是最终答案） |
| 层来源 | classpath 上各模块的 `_vfs/_delta/{layer}/` | 显式 `head`/`tail` 路径列表 |
| 写回 | Delta 层不写回 Base | `installTarget`/`cacheTarget` 单点写入，不回写命中层 |
| 是否需要代码 | 零代码（约定即配置） | **分层零代码；按 classifier 分流需 ~30 行** |

**最大区别**：nop Delta 是"内容级合并"（同一路径多份文件做 XML merge），而 Maven Chained LRM 是"路径级短路"（同 GAV 第一份命中即用，不合并）。所以**不能**指望 Maven 分层去做"基础 jar + Delta jar 合并内容"——jar 是二进制，本就不该合并；分层只用于"基础只读层 + 可写覆盖层"的隔离管理，这点和 nop 的"Delta 覆盖 Base"语义一致。

## Conclusion

- **能实现**，且 Resolver 2.0.x / Maven 4 已经内置了几乎所有需要的机制，**不需要 fork Maven**。
- **最简单方式**（分层管理 jar 存放位置）：`.mvn/maven.config` 加 `maven.repo.local.head` 即可，零代码，等价于 nop 的 Delta Layers。
- **源码 jar 单独存放**：因为 Resolver 把它视为普通 classifier，必须用自定义 `LocalPathPrefixComposer`（约 30 行）按 classifier 分 prefix；纯 head/tail 做不到。
- **被否决的方案**：
  - 直接改 `maven-resolver-impl` 源码——维护成本高，且 SPI 已经足够，没必要。
  - 用 `WorkspaceReader` 解决依赖存储——`WorkspaceReader` 只对"当前 reactor 内的项目"短路，不影响外部依赖的落盘位置，用错地方。
- **后续工作**：本文是纯调研，暂无对应 plan。若要落地"源码 jar 单独存放"，可在 `ai-dev/plans/` 起一个 plan，核心交付物就是上面方案 C 的 SPI 实现 + `.mvn/extensions.xml` 注册。

## Open Questions

- [ ] Maven 3.9.x 下 `maven.repo.local.head` 不支持（仅 `tail` since 3.9.0，`head` since 4.0.0）。本项目 `mvnw` 用的是 4.0.0-rc-5，可用；但若要兼容 3.9.x，只能用 `tail` 或自定义 `LocalRepositoryManager`。
- [ ] `ChainedLocalRepositoryManager` 的 `installTarget`/`cacheTarget` 索引由构造参数 `int installTarget, int cacheTarget` 控制，Maven CLI 默认 `installTarget=0`（head[0]）。需要"install 落到主仓库、cache 落到 head"时，得在 session 构造期介入（写 `RepositorySystemSessionExtender`）——这点需要进一步验证。
- [ ] sources jar 的下载需要 `maven-source-plugin` 或 IDE 主动请求；不开启下载 sources 时，prefix 分流对构建无影响，仅影响"主动下载 sources"的场景。

## References

- 源码：`~/sources/maven/impl/maven-core/src/main/java/org/apache/maven/internal/aether/DefaultRepositorySystemSessionFactory.java`（360-399 行，head/tail 装配）
- 源码：`~/sources/maven/api/maven-api-core/src/main/java/org/apache/maven/api/Constants.java`（382-423 行，`MAVEN_REPO_LOCAL_HEAD/TAIL` 常量与文档）
- 源码：`~/sources/maven/impl/maven-impl/src/main/java/org/apache/maven/impl/standalone/RepositorySystemSupplier.java`（DI 装配全景，含 `EnhancedLocalRepositoryManagerFactory`、`LocalPathPrefixComposerFactory`）
- 反编译：`~/.m2/repository/org/apache/maven/resolver/maven-resolver-impl/2.0.14/maven-resolver-impl-2.0.14.jar`
  - `org/eclipse/aether/internal/impl/EnhancedLocalRepositoryManager.class`（`getPathForLocalArtifact` = prefix + standard path）
  - `org/eclipse/aether/internal/impl/DefaultLocalPathComposer.class`（Maven2 布局，classifier 拼接逻辑）
  - `org/eclipse/aether/internal/impl/LocalPathPrefixComposerFactorySupport.class`（split 配置常量）
- 反编译：`~/.m2/repository/org/apache/maven/resolver/maven-resolver-util/2.0.14/maven-resolver-util-2.0.14.jar`
  - `org/eclipse/aether/util/repository/ChainedLocalRepositoryManager.class`（head/tail 链式查找 + 单点写入）
- 外部文档：
  - [Local Repository – Artifact Resolver](https://maven.apache.org/resolver/local-repository.html)
  - [Resolver Configuration](https://maven.apache.org/resolver/configuration.html)
  - [MNG-8167: Enable "split repo" in settings.xml](https://github.com/apache/maven/issues/9685)
- 本仓库对照：
  - `docs-for-ai/02-core-guides/delta-customization.md`（nop Delta 写法）
  - `docs-for-ai/02-core-guides/vfs-and-resource-resolution.md`（nop VFS 解析顺序）
