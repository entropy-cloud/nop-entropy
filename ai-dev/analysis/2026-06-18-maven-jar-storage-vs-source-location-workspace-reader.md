# Maven 定制：jar 存放位置 vs 源码所在位置（WorkspaceReader 侧 + 项目发现侧）

> Status: open
> Date: 2026-06-18（2026-06-18 补充 Part C：reactor 子模块源码外置）
> Scope: Maven 4.1.x 源码（`~/sources/maven`）中 jar 存储与**源码位置**相关的定制链路；重点深挖此前被忽略的 `WorkspaceReader` / `ReactorReader` 侧（依赖解析阶段），并补充**reactor 子模块源码外置**的真正拦截点——项目发现阶段（`ModelProcessor`/`ModelLocator`），用 nop Delta 类比给出"最简方式"
> Conclusion: "源码位置"在 Maven 里横跨**两个阶段、两套不同 SPI**，必须分开看：(1) **依赖解析阶段**——`WorkspaceReader`（+ `MavenChainedWorkspaceReader` 链式），让某些**依赖**从源码项目取而非 jar 仓库取；优先级高于 `LocalRepositoryManager`。(2) **项目发现阶段**——`ModelProcessor`/`ModelLocator`（`MultiModuleCollectionStrategy` → `locateExistingPom`），决定 reactor **子模块源码/pom 从哪个目录读**；这一阶段 `WorkspaceReader` 插不进去。jar 落盘则是第三条线 `LocalRepositoryManager`（+ head/tail 分层）。三套 SPI 都带 nop Delta 式"上层覆盖下层"语义且无需 fork。**最简方式**：jar 侧用 `maven.repo.local.head/tail` 两个属性；依赖解析源码侧默认 `ReactorReader` 自动消费、任意目录写 ~50 行 `WorkspaceReader`；**reactor 子模块外置不改 pom** 最简为"软链 + 映射文件"（零 Maven 代码），纯 Maven 配置驱动则写自定义 `ModelProcessor` 扩展读 `.mvn/module-relocations.xml`（~100 行）。本文**修正** `2026-06-15-...md` 把"源码位置"误读为 `classifier=sources` 源码 jar 的偏差，并**自我修正** Part B 一度把所有"源码位置"都归到 WorkspaceReader（对 reactor 子模块不成立，见 Part C）。

## Context

- **触发**：用户要求"深入分析 `~/sources/maven`"，定制"底层 jar 的存放位置"与"源码所在位置"，类比 nop 的 delta 文件系统，且明确要求**jar 包存放位置和源码位置分开考虑**，找最简单的方式。
- **前置分析**：`2026-06-15-maven-local-repo-customization-vs-nop-delta.md` 已详尽覆盖 **jar 存储侧**（`LocalRepositoryManager` / `ChainedLocalRepositoryManager` / head/tail / `LocalPathPrefixComposer`），但其"源码"章节把"源码"理解成了 `*-sources.jar`（classifier），并在被否决方案里写"`WorkspaceReader` 用错地方"。本分析认为**这是对需求的窄化误读**：用户要分开的是"源码项目在哪"与"jar 存在哪"，正是 `WorkspaceReader` 的职责，而非源码 jar 的分桶。
- **涉及源码**：`~/sources/maven`（Maven 4.1.x 主干，master 分支）。

## 核心结论：三个阶段、三套 SPI（"源码位置"横跨其中两个阶段）

> 先给一张总图。注意 Part B（依赖解析）和 Part C（项目发现）都涉及"源码"，但**阶段不同、SPI 不同**，这是本文最容易踩的坑。

Maven（经 Resolver/Aether）把"构建所需的东西从哪来"拆成三个**互不重叠**的阶段，对应三套 SPI：

```
【阶段 0：项目发现 session 启动时执行一次】
┌─────────────────────────────────────────────┐
│ ⓪ ModelProcessor / ModelLocator  ← Part C   │  walking <modules>：按相对路径定位每个
│   locateExistingPom(<module>)               │  reactor 子模块的 pom/源码目录。决定"子模块源码在哪"
└──────────────────┬──────────────────────────┘
                   │  reactor 项目集确定后，进入按 GAV 的依赖解析：
                   ▼
【阶段 1+2：依赖解析（每个 GAV）】
依赖解析请求 (GAV)
        │
        ▼
┌─────────────────────────────────────┐
│ ① WorkspaceReader  ← 源码/项目位置  │   优先级最高，命中即返回，根本不查仓库
│   findArtifact() / findVersions()   │   （返回 target/classes 或打包产物）
│   findModel() ← 返回 POM Model      │
└──────────────┬──────────────────────┘
               │ 没命中
               ▼
┌─────────────────────────────────────┐
│ ② LocalRepositoryManager ← jar 落盘 │   本地仓库（缓存 + install 产物）
│   getPathForLocalArtifact()         │
│   getPathForRemoteArtifact()        │
└──────────────┬──────────────────────┘
               │ 没命中
               ▼
          远程仓库下载
```

**关键点**：`WorkspaceReader` 在 `LocalRepositoryManager` **之前**被查询。也就是说，"源码所在位置"的优先级**高于**"jar 存放位置"。这正是"分开考虑"的天然落点——想让某些依赖从源码项目取而不是从 jar 仓库取，就给它们配一个 `WorkspaceReader`。

> ⚠️ **作用域边界（重要，见 Part C）**：`WorkspaceReader` 只作用于**依赖解析阶段**（GAV → 产物文件）。它**管不了 reactor 子模块的源码/pom 在哪**——那是项目发现阶段（`<module>` 相对路径 → 子模块 pom）的事，由 `ModelProcessor`/`ModelLocator` 负责。换句话说：Part B 全部针对"让**依赖**从源码取"；若你的需求是"把某个 **reactor 子模块**（如 `nop-ai/nop-ai-toolkit`）的源码挪到主仓外且不改 pom"，请直接看 **Part C**，WorkspaceReader 解决不了。

证据（`impl/maven-impl/.../DefaultArtifactDescriptorReader.java:191`、`DefaultVersionResolver.java:132,380`、`DefaultVersionRangeResolver.java:189`）：

```java
final WorkspaceReader workspace = session.getWorkspaceReader();
// 先问 workspace（源码侧），workspace 给了就用它，完全绕过本地仓库
```

### 与 nop Delta 的总体对应

| nop VFS 概念 | Maven 源码侧（WorkspaceReader） | Maven jar 侧（LocalRepositoryManager） |
|---|---|---|
| 分层虚拟 FS | `MavenChainedWorkspaceReader`（链式） | `ChainedLocalRepositoryManager`（head/main/tail） |
| 上层遮蔽下层 | reactor → ide → extensions（`DefaultMaven.setupWorkspaceReader`） | head → main → tail |
| 当前项目源码 | ReactorReader 直接消费 reactor（含 `target/classes`） | （不适用，jar 侧不碰源码） |
| 找到即返回、不回写 | `findArtifact` 命中即用，不落盘 | `find` 命中即用，只单点写入 install/cache 目标 |
| 自定义路径映射 | 实现 `WorkspaceReader` | 实现 `LocalRepositoryManagerFactory` |

---

## Part A：jar 存放位置（速览，详见前置分析）

前置分析 `2026-06-15-...md` 已覆盖，此处只给"最简方式"结论与代码锚点，不重复展开。

### 最简方式（零代码，就是 Delta 分层）

`~/sources/maven/api/.../Constants.java:391-412` 定义，`~/sources/maven/impl/maven-core/.../DefaultRepositorySystemSessionFactory.java:360-382` 装配：

| 属性 | 语义 | nop Delta 对应 |
|---|---|---|
| `maven.repo.local.head` | 前置层，**最先**查、install/cache **写入**此处 | `_delta/{高优先级}` |
| `maven.repo.local`（主） | 中间层（默认 `~/.m2/repository`） | Base |
| `maven.repo.local.tail` | 尾部兜底层 | `_delta/{低优先级}` / 兜底 |
| `maven.repo.local.tail.ignoreAvailability`（默认 true） | tail 是否跳过可用性检查 | — |

配置（`.mvn/maven.config` 或命令行）：

```
-Dmaven.repo.local.head=~/.m2/product-repo,~/.m2/base-repo
-Dmaven.repo.local.tail=/shared/cache
```

需要更细的"按 classifier/release/snapshot 分桶"或完全自定义布局 → 见前置分析方案 C/D（`LocalPathPrefixComposer` / `LocalRepositoryManagerFactory`）。

> jar 侧到此为止。**本分析新增价值集中在 Part B。**

---

## Part B：源码所在位置（WorkspaceReader 侧深挖）

### B1. 内置实现：ReactorReader —— Maven 已默认"从源码取，不经落盘"

`~/sources/maven/impl/maven-core/src/main/java/org/apache/maven/ReactorReader.java`（599 行，`@Named("reactor") @SessionScoped`）。这是回答"源码位置最简方式"的**第一发现**：Maven 内置就有一个把"源码项目"当一等公民来解析依赖的 reader。

它的 `findArtifact()`（`ReactorReader.java:102-127, 161-196`）返回的不是一个 jar，而是**源码项目的产物**，按优先级：

1. **POM**：永远从源码文件系统取（`project.getFile()`，即项目里的 `pom.xml`）。
2. **已打包产物**：若本 session 已 `package`，返回 `project.getArtifact().getFile()`（`target/*.jar`）。
3. **project-local-repo**：`target/project-local-repo/{GAV}/...`（上一次构建硬链接/拷贝的产物，见 B2）。
4. **loose class files**：若**尚未打包**，直接返回 `target/classes`（普通 jar）或 `target/test-classes`（test-jar）—— **连 jar 都不打，直接用编译输出目录**。

`findVersions()`（`ReactorReader.java:130-149`）：返回 reactor 内该 GAV 的所有版本。
`findModel()`（`ReactorReader.java:152-155`）：返回该项目的 POM `Model`（供 Resolver 算传递依赖，无需读 jar 里的 pom）。

**含义**：在多模块 reactor 里，模块 A 依赖模块 B 时，B 的产物**根本不进本地仓库**，直接用 B 的 `target/classes`。这就是"源码所在位置"与"jar 存放位置"在 Maven 里的天然分离——B 的源码在 reactor 目录，A 消费的是源码编译输出，二者与 `~/.m2/repository` 完全无关。

### B2. project-local-repo：源码侧自带的"中转层"

`ReactorReader.java:70, 397-522` 引入了一个 `target/project-local-repo`：

- 每个项目成功打包后（`ProjectSucceeded` 事件），把打包产物 + attached artifacts **硬链接**（失败则拷贝）到 `target/project-local-repo/{groupId}/{artifactId}/{version}/...`（`installIntoProjectLocalRepository`，`ReactorReader.java:449-480`）。
- `clean` 阶段会清掉它（`cleanProjectLocalRepository`，`ReactorReader.java:405-428`）。
- 它的作用：让"本次构建没参与（被 `-pl` 过滤掉）但 reactor 里存在"的项目，也能被解析到上一次的产物（`ReactorReader.java:113-124, 141-148`）。

这相当于源码侧的"项目级临时 Delta 层"——存放在源码项目的 `target/` 下，随源码走，不污染全局 `~/.m2`。

### B3. 分层链：MavenChainedWorkspaceReader（≈ nop Delta Layers，但作用在源码侧）

`~/sources/maven/impl/maven-core/src/main/java/org/apache/maven/resolver/MavenChainedWorkspaceReader.java`（169 行）：

- `findArtifact()`：按 readers 顺序问，**第一个命中即返回**（`MavenChainedWorkspaceReader.java:82-94`）——和 `ChainedLocalRepositoryManager.find()` 完全同构。
- `findVersions()`：**聚合**所有 reader 的版本（`LinkedHashSet` 去重，`:97-106`）——比 jar 侧更宽松（jar 侧是短路，源码侧版本是并集）。
- `setReaders()` / `addReader()`：动态重组链（`:108-126`）。

装配点：`~/sources/maven/impl/maven-core/src/main/java/org/apache/maven/DefaultMaven.java:337-352`（`setupWorkspaceReader`）：

```java
Set<WorkspaceReader> workspaceReaders = new LinkedHashSet<>();
// 1) Reactor workspace reader —— 最高优先级（当前 reactor 源码）
workspaceReaders.add( lookup.lookup(WorkspaceReader.class, ReactorReader.HINT) );
// 2) Repository system session-scoped workspace reader (ide + exec request reader)
for (WorkspaceReader r : chainedWorkspaceReader.getReaders()) { ... workspaceReaders.add(r); }
// 3) .. n) Project-scoped workspace readers —— 扩展点
workspaceReaders.addAll( getProjectScopedExtensionComponents(session.getProjects(), WorkspaceReader.class) );
chainedWorkspaceReader.setReaders(workspaceReaders);
```

**解析顺序**：`ReactorReader`（reactor 源码）→ `ideWorkspaceReader` + `request.getWorkspaceReader()`（IDE / 调用方注入）→ 项目级扩展 reader → （都没命中才落到 jar 仓库）。

这正是 nop 的 `Tenant → Delta(高→低) → Base` 在源码侧的镜像：每层"找到即返回"，最底层才是 jar 仓库。

> 注意：`DefaultMaven.java:213-214` 在 session 一开始就用 `new MavenChainedWorkspaceReader(request.getWorkspaceReader(), ideWorkspaceReader)` 建链，`:244` 再把 reactor reader 加进去，`:265` 的 `setupWorkspaceReader` 在 projects 读取后做最终重排——所以**扩展 reader 是在 projects read 之后才并入**的。

### B4. 自定义"源码所在位置"：三种注册路径（都无需改 Maven 源码）

要让 Maven 从**任意源码目录**（不止当前 reactor）取依赖，写一个 `WorkspaceReader`：

#### 路径 1：核心扩展 / Sisu 组件（最常用，自动发现）

实现接口 + Sisu 注解即可被 DI 自动拾取：

```java
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.repository.WorkspaceRepository;
import org.eclipse.aether.artifact.Artifact;
import javax.inject.Named;       // 或 org.apache.maven.api.di.Named
import javax.inject.Singleton;
import java.io.File;
import java.util.List;

@Named("my-sources")             // hint
@Singleton                       // 或 @SessionScoped（org.eclipse.sisu.plexus.scopes.SessionScoped）
public class MySourcesWorkspaceReader implements WorkspaceReader {
    private final WorkspaceRepository repo = new WorkspaceRepository("my-sources");

    @Override
    public WorkspaceRepository getRepository() { return repo; }

    @Override
    public File findArtifact(Artifact a) {
        // 把 GAV 映射到你的源码目录树，返回 target/classes 或打包 jar；找不到返回 null
        File f = mapToMySourcesTree(a);   // 你自己的映射逻辑
        return (f != null && f.exists()) ? f : null;
    }

    @Override
    public List<String> findVersions(Artifact a) {
        return scanMySourceTreeForVersions(a);   // 你的扫描逻辑
    }
}
```

注册方式（任选其一）：
- 打成 jar 放 `${maven.home}/lib/ext`；
- 作为**核心扩展**：`.mvn/extensions.xml` 声明该 jar 的 GAV（Maven 4 会用它自己的 classpath 引导扩展）。
- 之后它会被 `DefaultMaven.setupWorkspaceReader` 的第 3 档（project-scoped extension components）或 DI 容器直接拾取并入链。

> 若还需要返回 POM Model（让 Resolver 直接用源码 POM 算传递依赖），实现 `MavenWorkspaceReader`（`~/sources/maven/impl/maven-impl/.../MavenWorkspaceReader.java`，含 `findModel(Artifact)`）而非裸 `WorkspaceReader`。

#### 路径 2：调用方注入（嵌入式 / IDE 场景）

`MavenExecutionRequest.setWorkspaceReader()`（`MavenExecutionRequest.java:472`，`DefaultMavenExecutionRequest.java:1055-1066`）：

```java
MavenExecutionRequest req = ...;
req.setWorkspaceReader(new MySourcesWorkspaceReader());
// DefaultMaven.java:214 会把它和 ideWorkspaceReader 一起塞进链
```

适合用 Maven Embedder（`maven-embedder`）做 IDE/工具集成的场景，零 SPI 注册。

#### 路径 3：`AbstractMavenLifecycleParticipant`（projects read 后动态加 reader）

`DefaultMaven.java:265` 的 `setupWorkspaceReader` 在 `afterProjectsRead` 之前调用，而 `afterProjectsRead`（`:270`）会触发所有 `AbstractMavenLifecycleParticipant`。一个 lifecycle participant 可以在 session 里 lookup 并改 `MavenChainedWorkspaceReader` 的 readers 列表（链是可变的，`MavenChainedWorkspaceReader.addReader`）。适合"需要先读 projects 才能决定源码目录"的动态场景。

### B5. 源码侧"最简方式"小结

| 场景 | 方式 | 代码量 |
|---|---|---|
| 源码就是当前 reactor 的兄弟模块 | **零代码**——`ReactorReader` 已自动消费（连 `target/classes` 都直接用） | 0 |
| 从任意外部源码目录树取依赖 | 写 `WorkspaceReader`（~50 行）+ `@Named @Singleton` 注册为扩展 | ~50 行 |
| 嵌入式/IDE 调用 Maven | `request.setWorkspaceReader(...)` | ~50 行 |
| 多套源码树分层（像 Delta Layers） | 写多个 reader，或一个 reader 内部按层穿透；自动被 `MavenChainedWorkspaceReader` 链起来 | ~50 行/层 |

---

## Part C：reactor 子模块源码外置（项目发现阶段，≠ WorkspaceReader）

> 本节为 2026-06-18 追加，澄清一个关键误区：**"源码位置"在 Maven 里跨两个阶段**，Part B 的 `WorkspaceReader` 只覆盖依赖解析阶段；reactor 子模块的源码定位属于项目发现阶段，拦截点完全不同。

### C1. 问题与误区

**典型需求**（用户实例）：`nop-ai-toolkit` 是 `nop-ai` 的内部模块（`<module>nop-ai-toolkit</module>`，reactor 成员）。希望**不改任何 pom**，只给 Maven 一个**外部配置**，让它知道 `nop-ai-toolkit` 的源码在额外位置（如只读共享树），从而从那里读源码/pom。

**误区**：试图用 Part B 的 `WorkspaceReader` 解决。**行不通**，因为时序：

```
项目发现阶段（Project Discovery）          依赖解析阶段（Dependency Resolution）
        │                                           │
        ▼                                           ▼
MultiModuleCollectionStrategy.collectProjects   DefaultArtifactDescriptorReader
  → modelProcessor.locateExistingPom(<module>)     → session.getWorkspaceReader()  ← Part B 在这里
  → 按 <module> 相对路径找子模块 pom/源码
        │                                           │
        └── reactor 子模块的源码位置在这里就被钉死了
```

`nop-ai-toolkit` 作为 `<module>`，Maven 在**项目发现阶段**就按相对路径 `nop-ai/nop-ai-toolkit/` 去定位它的 pom 和源码目录（`MultiModuleCollectionStrategy.java:64-68` → `modelProcessor.locateExistingPom`，`:105`）。`WorkspaceReader` 在更晚的依赖解析阶段才介入，**够不着** reactor 子模块的源码定位。

**结论**：reactor 子模块源码外置 = 项目发现阶段问题，拦截点是 `ModelProcessor` / `ModelLocator`，不是 `WorkspaceReader`。

### C2. 拦截点定位（源码锚点）

| 组件 | 作用 | 锚点 |
|---|---|---|
| `ModelProcessor.locateExistingPom(Path)` | 定位项目 pom（子模块发现的核心） | `impl/maven-impl/.../api/services/model/ModelProcessor.java:40` |
| `MultiModuleCollectionStrategy.collectProjects` | 从 root 收集所有 reactor 项目，内部调 `locateExistingPom` | `impl/maven-core/.../project/collector/MultiModuleCollectionStrategy.java:64-68,100-119` |
| `DefaultModelBuilder` 的 `mappedSources`（`GAKey → ModelSource`） | 子模块 GAV→源 的注册表，构建时填充 | `impl/maven-impl/.../model/DefaultModelBuilder.java:270,411-433,453-468` |
| `Sources.buildSource(Path)` / `BuildPathSource.resolve(ModelLocator, relative)` | 子模块相对路径 → pom 的解析（`<module>` 落地） | `api/maven-api-core/.../services/Sources.java:75-77,216-247` |

这些都是 Sisu bean / 可替换 SPI。**Maven 没有内置的 "module relocation 配置"**（无 `maven.module.relocations` 之类开关），但可以通过替换/装饰上述 bean 实现。

### C3. 两条落地路（按"最简→最纯 Maven"）

#### 路 1：软链 + 映射文件（最简单，零 Maven 代码）

维护一个外部映射文件（不放项目里、不改 pom），例如 `~/.nop/module-relocations`：
```
nop-ai/nop-ai-toolkit=/external/worktrees/nop-ai-toolkit
```
一个小脚本（或 `mvnw` 包装层 / git hook）读它、建软链：
```bash
ln -sfn /external/worktrees/nop-ai-toolkit nop-ai/nop-ai-toolkit
```
pom 完全不动，Maven 顺着 `<module>` 相对路径找到软链、透明跟到外部。toolkit 仍是 reactor 成员，同一次 `mvn install` 照常编译。"只读"= 软链目标只读。

- **优点**：零 Maven 代码、零风险、所有工具链（IDE、`-pl`、增量）完全无感。
- **缺点**：严格说"配置"驱动的是软链而非 Maven 本身；Windows 软链需提权。
- **适用**：绝大多数"把子模块挪出去还想要最省事"的场景。

#### 路 2：自定义 `ModelProcessor` 扩展（纯 Maven、真正配置驱动，~100 行）

实现一个 `ModelProcessor` 装饰器（Sisu 高优先级覆盖默认 `DefaultModelProcessor`），读 `.mvn/module-relocations.xml`（模块相对路径 → 外部绝对路径映射），在 `locateExistingPom()` 里对命中的模块返回外部路径：

```xml
<!-- .mvn/module-relocations.xml -->
<relocations>
  <relocation module="nop-ai/nop-ai-toolkit" to="/external/worktrees/nop-ai-toolkit"/>
</relocations>
```

```java
@Named
@Singleton
public class RelocatingModelProcessor implements ModelProcessor {
    @Inject @Named("default") ModelProcessor delegate;   // 包装默认实现
    private Map<String, Path> relocations;                // 启动时读上面的 xml

    @Override
    public Path locateExistingPom(Path project) {
        Path hit = relocations.get(rootRelative(project));
        if (hit != null) {
            Path external = delegate.locateExistingPom(hit);   // 去外部树定位 pom
            if (external != null) return external;
        }
        return delegate.locateExistingPom(project);            // 其余模块走默认
    }
    // 其余 ModelProcessor 方法 delegate 转发
}
```
注册成核心扩展（`.mvn/extensions.xml`）即可。pom 不动，纯靠 Maven 扩展 + 配置文件实现 relocation。

- **优点**：字面意义的"给 Maven 补一个配置信息，它就知道哪些模块在额外位置"；无脚本、无软链，跨平台。
- **缺点**：需写 + 维护 ~100 行扩展；要正确处理 `project` 路径相对 root 的归一化、与 `mappedSources`/`BuildPathSource.resolve` 的交互（子模块的 `<module>` 相对解析也走 `ModelLocator`，需一并装饰，见 Open Questions）。
- **适用**：明确要"纯 Maven 配置驱动、不依赖外部脚本"的团队规范场景。

### C4. 路选型

| 需求 | 路 |
|---|---|
| 只想子模块文件物理上在别处、构建不变、最省事 | **路 1（软链 + 映射）** |
| 要纯 Maven 原生、配置文件驱动、不依赖脚本/软链 | 路 2（`ModelProcessor` 扩展） |
| 子模块要彻底从 reactor 摘掉、当外部依赖消费 | 回 Part B（WorkspaceReader）或 jar 侧（`install`）——但这要改 pom 摘 `<module>` |

---

## Delta 映射总表（jar 侧 + 依赖解析源码侧 + 项目发现源码侧，三条并列）

| nop Delta 概念 | jar 存放侧（Part A） | 源码·依赖解析侧（Part B） | 源码·项目发现侧（Part C） |
|---|---|---|---|
| Maven 阶段 | （依赖解析，落盘） | 依赖解析，**在 jar 仓库之前** | **项目发现**（早于依赖解析） |
| 分层机制类名 | `ChainedLocalRepositoryManager` | `MavenChainedWorkspaceReader` | （无内置分层；靠 `ModelProcessor` 装饰重定向） |
| 层顺序（高→低） | head → main → tail | reactor → ide/request → extensions | 软链/扩展重定向 → 默认相对路径 |
| 配置入口 | `maven.repo.local.head/tail` 属性 | DI 自动发现 / `setWorkspaceReader` | 软链映射文件 / `.mvn/module-relocations.xml` |
| "当前项目源码" | 不涉及 | `ReactorReader`（`target/classes` 直消费） | `<module>` 相对路径（默认） |
| 项目级临时层 | （无直接对应） | `target/project-local-repo` | （不适用） |
| 命中语义 | 短路（第一命中即用） | artifact 短路；**versions 取并集** | 重定向即整体换目录（非逐层穿透） |
| 写回 | 仅写 install/cache 目标 | 不落盘（源码产物直接用） | 源码目录只读 |
| 完全自定义路径 | `LocalRepositoryManagerFactory` | `WorkspaceReader.findArtifact` | `ModelProcessor.locateExistingPom` |
| 是否需改 Maven 源码 | 否（SPI + DI） | 否（SPI + DI） | 否（SPI + DI） |
| 是否需写代码 | 分层否；分桶是 | reactor 场景否；任意目录是 | 软链否；纯配置驱动是（~100 行） |

**最重要的三条**：
1. 三套机制各管一段、**互相独立**。改 jar 位置 → Part A；让**依赖**从源码取 → Part B；挪 **reactor 子模块**源码 → Part C。
2. **时序决定归属**：项目发现（C）→ 依赖解析的 workspace（B）→ 依赖解析的 jar 仓库（A）。需求落在哪个阶段就用哪套 SPI，张冠李戴会"看着对其实不生效"（典型：用 WorkspaceReader 去挪 reactor 子模块）。
3. **不改 pom 的子模块外置**最简 = 软链 + 映射文件（Part C 路 1）。

---

## 与前置分析的关系（修正与互补）

`2026-06-15-maven-local-repo-customization-vs-nop-delta.md` 的偏差与本分析的修正：

| 点 | 前置分析（2026-06-15） | 本分析（2026-06-18） |
|---|---|---|
| "源码"的解读 | 源码 **jar**（`classifier=sources`） | 源码 **项目/位置**（依赖解析侧 WorkspaceReader + 项目发现侧 ModelProcessor） |
| 是否分开考虑 jar 与源码 | 否，都在 jar 仓库内用 prefix 分桶 | 是，三套独立 SPI 分三个阶段 |
| `WorkspaceReader` 的评价 | 列为"被否决方案，用错地方" | 对**依赖解析**侧是正解；但对 reactor 子模块外置**确实用错地方**（那是项目发现阶段，见 Part C） |
| 覆盖面 | jar 侧极详尽（LocalPathPrefixComposer 等） | jar 侧速览 + 源码两阶段深挖 |

两者**不冲突而是互补**：
- 想把 `*-sources.jar`（源码 jar）和普通 jar 存到不同**磁盘目录** → 用前置分析的 `LocalPathPrefixComposer` 按 classifier 分 prefix（仍在 jar 仓库体系内）。
- 想让**依赖**从源码项目取而不是从 jar 仓库取 → 用本分析 Part B 的 `WorkspaceReader`。
- 想把 **reactor 子模块**源码挪到主仓外且不改 pom → 用本分析 Part C 的软链 / `ModelProcessor` 扩展（**不是** WorkspaceReader）。

用户本次明确要"jar 包存放位置**和源码位置**分开考虑"且聚焦"单个子模块外置、不改 pom、靠外部配置告知 Maven"，对应本分析的 **Part A + Part C**（Part B 作为对比与误区澄清保留）。

---

## Conclusion

- **能实现**，且 Maven 4.1.x 用**三套独立 SPI** 分管三个阶段：jar 落盘（`LocalRepositoryManager`）、依赖解析的源码取（`WorkspaceReader`）、项目发现的子模块定位（`ModelProcessor`/`ModelLocator`）。三者都带 nop Delta 式分层/重定向语义，**不需要 fork Maven**。
- **最简方式（jar 侧）**：`.mvn/maven.config` 加 `maven.repo.local.head` / `tail`，零代码（详见前置分析）。
- **最简方式（依赖解析源码侧，Part B）**：
  - reactor 兄弟模块：**零代码**，`ReactorReader` 已自动消费源码（甚至直接用 `target/classes`）。
  - 任意源码目录：写一个 ~50 行 `WorkspaceReader`，`@Named @Singleton` 注册为核心扩展，自动被 `MavenChainedWorkspaceReader` 链入；嵌入式场景用 `request.setWorkspaceReader()`。
- **最简方式（reactor 子模块外置、不改 pom，Part C）**：
  - **路 1（首选）**：软链 + 外部映射文件，零 Maven 代码，pom 不动。
  - 路 2：自定义 `ModelProcessor` 扩展读 `.mvn/module-relocations.xml`，~100 行，纯 Maven 配置驱动。
- **被否决的方案**：
  - 改 Maven/Resolver 源码——三套 SPI 已足够，维护成本不值。
  - 用 `WorkspaceReader` 去挪 reactor 子模块源码——时序错位，项目发现阶段它还没介入（见 Part C1）。
  - 用 head/tail 分层去路由"源码项目"——head/tail 只作用于 jar 本地仓库，碰不到源码侧。
  - 把"源码位置"需求硬塞进 `LocalPathPrefixComposer`（前置分析的方向）——那只能分桶源码 **jar**，改变不了"依赖从源码取"或"子模块从外部读"的行为。
- **后续工作**：本文是纯调研。若要落地"不改 pom 的子模块外置"，可在 `ai-dev/plans/` 起一个 plan，首选交付路 1 的映射脚本，或路 2 的 `ModelProcessor` 扩展 + `.mvn/extensions.xml` 注册。

## Open Questions

- [ ] 自定义 `WorkspaceReader` 返回 `target/classes`（loose classes）时，Resolver 对非 jar 路径的 classpath 处理是否与 `ReactorReader` 完全一致？需在 `DefaultArtifactDescriptorReader` 里确认 workspace 返回的 file 如何被当作 classpath（`ReactorReader` 依赖 `COMPILE_PHASE_TYPES` 白名单，自定义 reader 需注意 war/ear 等类型不应返回目录）。
- [ ] `.mvn/extensions.xml` 注册的扩展 `WorkspaceReader`，其加载时机是否早于 `DefaultMaven.setupWorkspaceReader`？若扩展在 projects read 之后才就绪，可能需要走 `AbstractMavenLifecycleParticipant` 路径（路径 3）。
- [ ] `MavenChainedWorkspaceReader` 的 versions 取并集，而 jar 侧 `ChainedLocalRepositoryManager` 是短路——若同一 GAV 在多个源码层都有，版本集合会膨胀，是否影响依赖调解？需结合 `DefaultVersionResolver.java:132-145` 验证。
- [ ] Maven 3.9.x 是否支持同样的 `WorkspaceReader` 扩展点？`ReactorReader`/`MavenChainedWorkspaceReader` 在 3.x 已存在，但 DI 注解体系（`@Named @SessionScoped`）和 `.mvn/extensions.xml` 引导流程有差异。
- [ ] **Part C 路 2 的正确性**：装饰 `ModelProcessor.locateExistingPom` 是否足够？子模块 `<module>` 相对解析也经过 `BuildPathSource.resolve(ModelLocator, relative)`（`Sources.java:239-247`）和 `DefaultModelBuilder.mappedSources`，是否需同时装饰 `ModelLocator` 才能覆盖所有路径？需实测验证。
- [ ] **Part C 与 reactor 图**：被重定向到外部的子模块，其 `rootDirectory` / `maven.multiModuleProjectDirectory` 仍属主仓；跨目录子模块是否影响 `DefaultGraphBuilder` 的拓扑、`-pl`/`-rf` 选择、增量编译？需结合 `MultiModuleCollectionStrategy` + `DefaultGraphBuilder` 验证。

## References

### 源码锚点（`~/sources/maven`）
- `impl/maven-core/src/main/java/org/apache/maven/ReactorReader.java` — 内置源码侧 reader（`target/classes` 直消费、project-local-repo、`findArtifact/findVersions/findModel`）
- `impl/maven-core/src/main/java/org/apache/maven/resolver/MavenChainedWorkspaceReader.java` — 源码侧分层链（artifact 短路、versions 并集）
- `impl/maven-core/src/main/java/org/apache/maven/DefaultMaven.java:213-214,244,265,337-352` — workspace reader 装配顺序（reactor→ide/request→extensions）
- `impl/maven-core/src/main/java/org/apache/maven/execution/MavenExecutionRequest.java:470-472` — `setWorkspaceReader` 嵌入式注入入口
- `impl/maven-impl/src/main/java/org/apache/maven/impl/resolver/DefaultArtifactDescriptorReader.java:191`（及 `DefaultVersionResolver.java:132,380`、`DefaultVersionRangeResolver.java:189`）— Resolver 先查 workspace 后查 local repo 的证据
- `impl/maven-core/src/main/java/org/apache/maven/internal/aether/DefaultRepositorySystemSessionFactory.java:360-382` — jar 侧 head/main/tail 装配
- `api/maven-api-core/src/main/java/org/apache/maven/api/Constants.java:109-114,382-412` — `MAVEN_REPO_LOCAL` / `MAVEN_REPO_LOCAL_HEAD/TAIL` 常量
- `api/maven-api-core/src/main/java/org/apache/maven/api/services/LocalRepositoryManager.java` — jar 侧 SPI 接口（`getPathForLocalArtifact` / `getPathForRemoteArtifact`）
- `impl/maven-cli/src/main/java/org/apache/maven/cling/invoker/LookupInvoker.java:745-771,777-778` — 主本地仓库路径决定（`maven.repo.local` / settings / 默认）
- **Part C（项目发现阶段）**：
  - `impl/maven-impl/src/main/java/org/apache/maven/api/services/model/ModelProcessor.java:40` — `locateExistingPom(Path)`，子模块 pom 定位 SPI（路 2 拦截点）
  - `impl/maven-core/src/main/java/org/apache/maven/project/collector/MultiModuleCollectionStrategy.java:64-68,100-119` — reactor 项目收集，内部调 `modelProcessor.locateExistingPom`
  - `impl/maven-impl/src/main/java/org/apache/maven/impl/model/DefaultModelBuilder.java:270,411-433,453-468` — `mappedSources`（`GAKey → ModelSource`），子模块 GAV→源 注册表
  - `api/maven-api-core/src/main/java/org/apache/maven/api/services/Sources.java:75-77,216-247` — `buildSource` / `BuildPathSource.resolve(ModelLocator, relative)`，`<module>` 相对路径解析落地

### 外部依赖（Resolver，源码不在本仓库）
- `~/.m2/repository/org/apache/maven/resolver/maven-resolver-spi/` — `WorkspaceReader`、`LocalRepositoryManagerFactory` 等 SPI 定义
- `~/.m2/repository/org/apache/maven/resolver/maven-resolver-util/2.0.14/` — `ChainedLocalRepositoryManager`（jar 侧分层实现）

### 本仓库对照
- `docs-for-ai/02-core-guides/delta-customization.md` — nop Delta 写法
- `docs-for-ai/02-core-guides/vfs-and-resource-resolution.md` — nop VFS 解析顺序（Tenant → Delta → Base）
- `ai-dev/analysis/2026-06-15-maven-local-repo-customization-vs-nop-delta.md` — jar 侧详尽分析（本文件的 Part A 基础与"被修正对象"）
