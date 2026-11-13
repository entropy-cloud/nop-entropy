# Gradle 增量编译机制深度分析

> **层级**：外部框架知识参考层（**不承载本仓决策**）。本文件只回答"Gradle 是怎么做的、为什么这样做、代价与边界在哪"。
> 本仓是否采纳、如何取舍，仍以 `00-vision.md` / `01-architecture-baseline.md` / `03-alternatives-and-roi.md` 的裁定为准；
> 本文件不修改也不要被读成任何 G 决策的替代。

**分析对象**：Gradle 源码仓，本地只读 checkout `~/sources/gradle`，`version.txt` = `9.10.0`，HEAD = `8ce88a611bc`。
**引用约定**：`path:line` 均相对 Gradle 仓根；行号是分析时的快照，随上游变动可能漂移，定位以类/方法名为准。
**标注约定**：【源码】= 有代码锚点的事实；【文档】= Gradle 用户指南的对外契约；【推断】= 由源码结构推出、未在本机实测的结论。
本仓既有文档中的【实测】标注仅指 nop-entropy 自身实验，本文件不冒充实测。

---

## 一、结论摘要

Gradle 的增量编译不是"把 javac 调快"，而是**把"需要做的工作"逐层削掉**。六层能力叠加：

| 层 | 机制 | 削掉的工作 | 关键锚点 |
|---|---|---|---|
| L1 执行流水线 | 任务级 up-to-date 判定 | 整个任务不执行 | `ExecutionBuildServices.java:170-215` |
| L2 输入归一化 | fingerprint / normalizer + ABI hash | "不影响结果的变化"不算变化 | `AbiExtractingClasspathResourceHasher.java:40-66` |
| L3 构建缓存 | 归一化输入 → cache key → 产物复用 | 跨 worktree / 跨机器整任务复用 | `DefaultCachingStateFactory.java:49-84` |
| L4 语言级增量 | 依赖分析 + 选择性重编译 | 同模块里只编译受影响的源文件 | `SelectiveCompiler.java:67-110` |
| L5 VFS + 文件监听 | 增量失效 + 常驻快照 | 把"判定变化"从 O(仓库) 降到 O(改动) | `DefaultFileSystemAccess.java:87-125` |
| L6 常驻守护进程 | daemon + classloader/缓存复用 + 编译 worker | JVM 冷启动与配置期成本 | `ExecutionBuildServices.java`、`DefaultJavaCompilerFactory.java` |

三条对 nop-incremental-build 直接有用的判断：

1. **正确性靠"显式放弃增量"保证，而不是靠"聪明的继续"。** 一切不确定都收敛到 `dependencyToAll` / `full rebuild`，并且"分析失败"本身是被持久化的数据（`ClassSetAnalysisData.fullRebuildCause`），不是异常分支。
2. **ABI（可访问面）是增量效率的主杠杆，且被用在两处**：L1 让任务直接 up-to-date（compile avoidance），L4 在必须编译时收窄重编译闭包。本仓实测的"签名谓词把适用面从 3.8% 提到 63.4%"是同一思想在 Maven 场景下的对应物。
3. **分析结果本身是产物**：`previous-compilation-data.bin` 是 `JavaCompile` 的 `@OutputFile`，因此进构建缓存、可跨机复用、命中后下一次仍是增量。这一点直接对应 `01 §12.4`"不重复造轮子，只补缺口"的边界。

---

## 二、分层与数据流

### 2.1 执行流水线（L1）

缓存友好的普通任务走 `mutablePipeline`【源码 `ExecutionBuildServices.java:188-215`】，顺序即嵌套顺序：

```
AssignMutableWorkspaceStep
 HandleStaleOutputsStep              // 每构建一次清理陈旧输出（不是每任务）
  LoadPreviousExecutionStateStep     // 读取上一轮 history
   MarkSnapshottingInputsStartedStep
    SkipEmptyMutableWorkStep         // 无输入则跳过（含输出清理）
     CaptureMutableStateBeforeExecutionStep   // 快照输入 + 输出（used for up-to-date & overlap）
      ValidateStep.Mutable
       ResolveChangesStep            // 变更检测 → ExecutionStateChanges
        ResolveMutableCachingStateStep      // 计算或复用 cache key
         MarkSnapshottingInputsFinishedStep
          SkipUpToDateStep           // 无 rebuild reason → 直接返回 UP_TO_DATE
           StoreExecutionStateStep   // 先存 history（再执行）——失败也可保留状态
            BuildCacheStep           // 命中则恢复产物并跳过执行
             ResolveInputChangesStep // 生成 InputChanges 交给 task action
              CaptureOutputsAfterExecutionStep
               BroadcastChangingOutputsStep
                RemovePreviousOutputsStep   // 仅非增量执行时清输出
                 PreCreateOutputParentsStep
                  TimeoutStep → CancelExecutionStep → ExecuteStep
```

要点：

- **up-to-date 是最外层最便宜的闸门**：命中时连 `InputChanges` 都不产生，直接返回上一轮的 `ExecutionOutputState`（`SkipUpToDateStep.java:66-80`）。
- **history 包在执行之外、结果返回后写入**（`StoreExecutionStateStep` 在外层，调用 delegate 拿到结果后才 `history.store(...)`）；失败时是否保留由"输出是否变化"决定（`StoreExecutionStateStep.java:52-93`）——输出已变化则保留失败状态，这是"编译失败后只重编改动文件"能成立的前提。
- **只有非增量执行才删旧输出**（`RemovePreviousOutputsStep.java:47-64`）。增量执行由语言层精确删除（见 §4.6）。

### 2.2 变更检测（任务级）

`DefaultExecutionStateChangeDetector.detectChanges()`【源码 `DefaultExecutionStateChangeDetector.java:33-122`】收集七类会触发重建的信号：

1. 上一轮是否成功（`PreviousSuccessChanges`）
2. 任务实现类/插件 classloader（`ImplementationChanges`）
3. 输入属性名集合（`PropertyChanges`）
4. 输入属性值（`InputValueChanges`，按值快照比对）
5. 输入文件属性名集合
6. 输出属性名集合
7. **非增量输入文件的变化**（`incrementalInputProperties.nonIncrementalChanges(...)`，第 73-77 行）
8. 输出文件变化（`OutputFileChanges`）

任一非空 → `ExecutionStateChanges.nonIncremental(reasons, ...)`；全空 → 只把标记为增量（`InputBehavior.INCREMENTAL`）的输入属性差异打包成 `incrementalInputFileChanges`（第 117-122 行）。

`InputChanges` 的对外语义【源码 `subprojects/core-api/.../work/InputChanges.java:70-131`】：

- `isIncremental()==false` 时，所有文件都以 `ADDED` 返回；
- `getFileChanges(parameter)` 只对**注册为增量**的输入属性有效（`DefaultIncrementalInputProperties` 通过 `InputBehavior.shouldTrackChanges()` 建映射，`ResolveChangesStep.java:70-88`）；
- 变更项携带 `ChangeType`、`FileType`、**normalized path**（`FileChange.java:28-60`）。

### 2.3 语言层入口

`JavaCompile` 的 action 直接接收 `InputChanges`【源码 `JavaCompile.java:152-171`】；`options.incremental=true` 时走增量编译，装配链为：

```
IncrementalCompilerFactory.makeIncremental()
  = IncrementalResultStoringCompiler( SelectiveCompiler( CleaningJavaCompiler , rebuildAllCompiler ) )
```

【源码 `IncrementalCompilerFactory.java:41-50`】

---

## 三、输入归一化：定义"什么算变化"（L2）

这是最容易被低估的一层：**fingerprint 用什么 normalizer，决定了增量效率，也决定了正确性边界。**

### 3.1 JavaCompile 的输入声明

【源码 `JavaCompile.java:354-371`】

```java
@CompileClasspath @Incremental  getClasspath()
@IgnoreEmptyDirectories @NormalizeLineEndings @PathSensitive(RELATIVE)  getStableSources()
```

- 源码：相对路径 + 行尾归一化 + 忽略空目录 → 换机器/换目录/换行尾不触发重编。
- classpath：`CompileClasspath` 归一化 → **只按 ABI 判变化**。

### 3.2 Classpath 指纹的构造

`ClasspathFingerprintingStrategy`【源码 `ClasspathFingerprintingStrategy.java:75-168`】：

- classpath **顺序敏感**，条目自身路径不敏感（jar 改名/换目录不算变化）【文档 `java_plugin.adoc:802-819`】；
- zip 按"展开后的内容"处理，逐条目归一化后再哈希；
- `compileClasspath` 变体对非 jar 根条目取 `IGNORE`（第 117-120 行）——目录型 classpath 里只有 `.class` 参与，资源变化不算编译输入。

### 3.3 ABI 哈希（compile avoidance 的实体）

`AbiExtractingClasspathResourceHasher`【源码 `AbiExtractingClasspathResourceHasher.java:40-66`】：

- 对每个 `.class` 用 `ApiClassExtractor.extractApiClassFrom(bytes)` 抽取 API 形态，再哈希；
- 抽取失败回落 full hash（`FallbackStrategy.FULL_HASH`，第 123-160 行）——**宁可多算，不可漏算**；
- `ApiClassExtractor.withWriter(...).includePackagePrivateMembers()`：包私有成员也算 ABI（因为同包依赖可见）；
- 结果由 `CachingResourceHasher` 再缓存。

对外契约【文档 `java_plugin.adoc:801-819`】：依赖的 ABI 不变时，依赖方的 `JavaCompile` 直接 up-to-date。被忽略的变化包括方法体、注释、私有成员/内部类、资源、jar 名、参数名。**注解处理器例外**：处理器实现属于编译输入，因此注解处理器 classpath 按 runtime classpath 处理（`caching_java_projects.adoc:25-44`）。

### 3.4 效率含义

- L1 层面上：依赖实现变化 → compile classpath fingerprint 不变 → 任务 up-to-date → 不跑分析、不跑 javac。
- L3 层面上：ABI 相同的不同实现版本可以共享同一条缓存条目（`build_cache_concepts.adoc:113-120`）。
- L4 层面上：ABI-only 的 classpath 快照也被用来限定"哪些本模块类受 classpath 变化影响"（§4.5）。

---

## 四、Java 选择性重编译（L4）

### 4.1 决策顺序

`SelectiveCompiler.execute()`【源码 `SelectiveCompiler.java:67-110`】：

1. `!recompilationSpecProvider.isIncremental()` → 全量（无增量变更信息，通常来自 clean 或编译参数变化）；
2. `previous-compilation-data.bin` 不存在 → 全量；
3. 推导不出 source roots → 全量；
4. 计算 `RecompilationSpec`，若 `isFullRebuildNeeded()` → 全量（日志给出 cause）；
5. 需要编译的类为空 → `RecompilationNotNecessary`（didWork=false，不写输出）；
6. 否则进入 `CompileTransaction`，调用 `CleaningJavaCompiler`（**增量模式下只删除 spec 指定的类**）。

### 4.2 持久化的分析数据

`previous-compilation-data.bin` 位置 = `build/tmp/<compileTaskName>/previous-compilation-data.bin`，声明为 `@OutputFile`【源码 `JavaCompile.java:223-226`】。因此：

- 它是**任务产物**，进构建缓存；
- 缓存命中恢复后，下一次编译仍可增量【文档 `java_plugin.adoc:547-548`】；
- 首次 clean checkout 因分析缓存为空而偏慢【文档同处】。

数据模型（`PreviousCompilationData`，【源码 `recomp/PreviousCompilationData.java:29-43`】）：

| 字段 | 内容 | 用途 |
|---|---|---|
| `outputSnapshot` | 本模块输出目录的 `ClassSetAnalysisData`（**完整分析**） | 反查改动类在本模块内的依赖者 |
| `annotationProcessingData` | origin→generated types/resources、aggregating types、full rebuild cause | 注解处理闭包 |
| `classpathSnapshot` | classpath 的 `ClassSetAnalysisData`（**ABI-only + 最小化**） | 判定 classpath 变化影响 |
| `compilerApiData` | source→classes 映射、常量→依赖者映射 | 源/类反查、常量内联精确追踪 |

`ClassSetAnalysisData` 主体【源码 `deps/ClassSetAnalysisData.java:99-118`】：

- `classHashes`: class → 内容哈希（差异检测的入口）；
- `dependents`: class → `DependentsSet`（private dependents / accessible dependents / dependencyToAll + 原因）；
- `classesToConstants`: class → 可内联常量的哈希集合；
- `fullRebuildCause`: 分析失败或强制全量的**数据化原因**。

### 4.3 依赖抽取（ASM）

`ClassDependenciesVisitor.analyze()`【源码 `asm/ClassDependenciesVisitor.java:89-96`】以 `SKIP_FRAMES` 读字节码，产出 private/accessible 两套依赖与常量集合：

- **可见性二分**（第 176-184、208-223 行）：`ACC_PRIVATE` 缺席者进 accessible，否则进 private；子类可见的 protected/public 都算 accessible，因此"改实现"只影响 private 闭包，"改签名"才扩散到 accessible 闭包。
- **字段/方法描述符与泛型签名**（第 176-213 行）都计入依赖；参数、返回、throws、字段类型全覆盖。
- **方法体依赖用快速扫描**（第 125-131 行）：`ClassAnalysisUtils.getClassDependencies(reader, ...)` 直接扫常量池，避免实现完整 MethodVisitor 的漏网情况；局部变量表（第 262-268 行）归 private。
- **动态调用/常量动态**：`invokedynamic` 的 bootstrap owner 与参数、`ConstantDynamic` 递归展开（第 284-330 行），并特判 `ClassDesc.of(String)` 形式（限定枚举 switch 会以字符串携带类名）。
- **可内联常量**（第 176-184、220-223 行）：`static final` 且可访问且有值 → 记 `hashCode(name + '|' + value)`。用"名字+值"而不是仅值，避免"同名不同值交换"漏检。
- **强制全量的字节码信号**：`module-info`（第 117-120 行）；`RetentionPolicy.SOURCE` 的注解（第 340-360 行，源保留注解不进字节码，只能保守全量）。
- **过滤**：`ClassRelevancyFilter` 丢弃 `java.*`、自身、九种基本类型（`asm/ClassRelevancyFilter.java:41-47`）。

累积与索引由 `ClassDependentsAccumulator` 完成【源码 `deps/ClassDependentsAccumulator.java:39-130`】：同名类跨 jar 只保留首个（classpath 遮蔽语义）；`dependencyToAll` 的类会移除其普通 dependents 记录。

### 4.4 受影响闭包

`ClassSetAnalysis.findTransitiveDependents()`【源码 `deps/ClassSetAnalysis.java:98-146`】是传播核心：

```
BFS(改动类 ∪ 注解生成的 "依赖全体" 类型)
  for each current:
    if 已访问: continue
    加入 accessible 闭包
    direct = bytecode dependents + 常量 dependents + 注解生成 dependents
    if direct.isDependencyToAll: return dependencyToAll
    private 闭包 += direct.private
    待访问 += direct.accessible
```

配套规则：

- 常量变化且编译器不支持常量映射 → 全量（第 106-113 行 `supportsConstantsMapping`）；
- 注解处理上一轮已报 full rebuild cause → 全量（第 101-104 行）；
- `getTypesToReprocess()`（第 164-183 行）把 aggregating 注解元素、被重编生成类的 origin 补进重处理集合；
- `package-info`/`module-info` 由编译器无法以"类"形式重处理，改为"每次带源文件重编"（`AbstractRecompilationSpecProvider.java:201-225`）。

### 4.5 源变化与 classpath 变化

`AbstractRecompilationSpecProvider.provideRecompilationSpec()`【源码 `recomp/AbstractRecompilationSpecProvider.java:71-89`】顺序：

1. **classpath 先判**（第 91-100 行）：`CurrentCompilation` 对 `compileClasspath + modulePath` 现场做 ABI-only 分析，与上次快照 `findChangesSince()` 求差，再把差集映射回**本模块输出**的依赖者（`recomp/CurrentCompilation.java:36-58` + `recomp/PreviousCompilation.java:33-46`）。差集若 `dependencyToAll`（如 classpath 无法分析、module-info 变化）→ 全量。
2. **再判源变化**（第 102-121 行）：每个 `FileChange` 经 `SourceFileClassNameConverter` 反查类名；`SourceFileChangeProcessor`（`recomp/SourceFileChangeProcessor.java:28-42`）加"改动类 + 传递依赖者"。若改了非源文件而注解处理器存在（`isIncrementalOnResourceChanges=false`，`JavaRecompilationSpecProvider.java:51-54`）→ 全量。
3. **补齐源文件与"独立类"**（第 151-190 行）：一个 `.java` 文件可能含多个类；只重编其中一部分会把同文件其他类的新输出"漏删"，导致 Groovy/Spock 等产生错误类（上游 issue #21644）。因此把文件内其余类也纳入删除/重编，并对它们只追踪注解处理依赖，不再扩传递闭包。
4. 加 `module-info` 源文件（存在即重编，第 215-225 行）。

`SourceFileClassNameConverter` 优先用 `CompilerApiData` 的 source→classes 映射（精确），拿不到时按文件名推导（`FileNameDerivingClassNameConverter`）。

### 4.6 编译执行：事务化输出

`initCompilationSpecAndTransaction()`【源码 `AbstractRecompilationSpecProvider.java:226-278`】构造：

- `sourceToCompile` PatternSet → `sourceTree.matching(...)` 得到本次真正传给编译器的源文件（不是全量源树）；
- `classesToDelete`：`a/b/C.class` 与 `a_b_C.h`（JNI 头）；
- 把 `destinationDir` **插到 compile classpath 最前**（第 277-283 行）——未重编的旧类仍可被解析；
- 资源删除集合按 `GeneratedResource.Location`（CLASS_OUTPUT / SOURCE_OUTPUT / NATIVE_HEADER_OUTPUT）分别构造。

`CompileTransaction.execute()`【源码 `transaction/CompileTransaction.java:101-170`】：

1. 建/清空 `stash-dir`、`backup-dir`；
2. 把"将被删除的输出"**移动**到 stash（不是直接删）——因此失败可回滚；
3. 编译；成功则清理空目录；失败则回滚：删除本轮新生成类/资源（来自 `ApiCompilerResult`），恢复被覆盖文件（`backupClassFiles`），再 unstash；
4. `supportsIncrementalCompilationAfterFailure()`（fork/CLI 编译器下为 false）决定要不要走崩溃后增量路径。

### 4.7 结果存储

`IncrementalResultStoringCompiler.storeResult()`【源码 `IncrementalResultStoringCompiler.java:69-83`】在每次真实编译后：

1. **重新完整分析输出目录**（`analyzeOutputFolder`）；
2. 重新做 classpath 的 ABI-only 分析；
3. 用 `ClassSetAnalysisData.reduceToTypesAffecting(outputSnapshot, compilerApiData)` 把 classpath 快照**收缩到只含可能影响本模块的类型**（`deps/ClassSetAnalysisData.java:120-180`）——正向闭包 + 常量来源 + package-info + dependencyToAll；
4. 合并注解处理数据与常量映射：以本轮 `changedClasses` 为界，删旧加新（第 170-186 行）；
5. 写回 `previous-compilation-data.bin`。

`RecompilationNotNecessary` 时**不重写**分析文件（`IncrementalResultStoringCompiler.java:67-71`）——零编译不产生额外 IO。

### 4.8 缓存的位置与规模

`UserHomeScopedCompileCaches`【源码 `cache/UserHomeScopedCompileCaches.java:33-66`】在 `~/.gradle/caches/<version>/javaCompile/` 下开两个 indexed cache：

- `jarAnalysis`：classpath 条目 → `ClassSetAnalysisData`，内存装饰 20,000 条；
- `classAnalysis`：class 文件哈希 → `ClassAnalysis`，内存装饰 400,000 条。

即"逐字节码文件分析"是跨构建、跨项目复用的（同一 jar 只分析一次），这是 L4 在大型仓库上仍然便宜的原因。

---

## 五、构建缓存（L3）

### 5.1 cache key 组成

`DefaultCachingStateFactory.calculateCacheKey()`【源码 `caching/impl/DefaultCachingStateFactory.java:49-84】依次 append：

1. 任务实现（`ImplementationSnapshot`，含 classloader 哈希）；
2. 附加实现（action、闭包等）；
3. 每个**输入属性名 + 值快照哈希**；
4. 每个**输入文件属性名 + fingerprint 哈希**（已归一化）；
5. 每个**输出属性名**（不含内容）。

不含：绝对路径、时间戳、任务名、机器、输出内容。因此 key 天然可迁移，产物可跨 worktree/跨机复用。

### 5.2 命中与未命中

`BuildCacheStep.executeWithCache()`【源码 `steps/BuildCacheStep.java:74-160`】：

- 命中：先 `fileSystemAccess.invalidate(outputLocations)`，再 load 解包到工作区，清理 local state，然后把恢复后的输出快照 `record` 进 VFS，返回 `FROM_CACHE`（`Execution.skipped`）；
- 未命中：正常执行，成功后打包存储；
- 缓存禁用：`work.shouldDisableCaching(overlappingOutputs)`（重叠输出无法安全复用）。

打包：单文件 GZip、目录 Tar（`build-cache-packaging/.../GZipBuildCacheEntryPacker`、`TarBuildCacheEntryPacker`）；路径按相对路径打包，配合相对路径归一化实现可迁移。本地缓存目录默认 `<GRADLE_USER_HOME>/caches/build-cache-1`【文档 `directory_layout.adoc:129,224`】。

### 5.3 与增量编译的交互

- 缓存命中时**没有** `InputChanges`（任务未执行）；
- 但 `previous-compilation-data.bin` 作为输出被一起恢复，因此**下一次**编译可增量；
- 若任务输出重叠或 local state 存在，缓存被禁用而不降级为"部分复用"。

---

## 六、VFS、文件监听与哈希缓存（L5）

### 6.1 VFS 模型

- `VirtualFileSystem` 保存 `SnapshotHierarchy`：路径 → `FileSystemLocationSnapshot`（regular file 的内容哈希 + metadata、目录快照树、missing）。
- `DefaultFileSystemAccess.readRegularFileContentHash()`【源码 `snapshots/.../DefaultFileSystemAccess.java:87-125`】先查 VFS；未命中才 `stat` → `FileHasher.hash(file, length, lastModified)` → 存入 VFS。同一路径的并发快照用 `StripedProducerGuard` 加锁去重。
- 目录快照用 `DirectorySnapshotter`，可复用 `previouslyKnownSnapshots`，并按 filter 决定是否缓存"过滤后"快照（第 160-200 行）。
- 写入路径必须显式 `invalidate(locations)`（第 209-214 行）；Gradle 自己产出的文件通过 `record(snapshot)` 直接进 VFS（第 221-223 行）。

### 6.2 哈希缓存

`CachingFileHasher`【源码 `hashing-services/.../CachingFileHasher.java:83-99`】：

- 键 = 绝对路径；值 = `(hash, length, timestamp)`；
- 仅当 `timestampInspector.timestampCanBeUsedToDetectFileChange(path, timestamp)` 为真且 `(length, mtime)` 未变时复用；
- 否则真实哈希并回填；
- 存储是跨构建的 user-home 级 indexed cache（`Kind.FILE_HASHES("fileHashes", ...)`，`CrossBuildFileHashCache.java:55-56`），内存 + 磁盘两层。

### 6.3 文件监听

`WatchingVirtualFileSystem`【源码 `file-watching/.../WatchingVirtualFileSystem.java:74-260`】：

- 默认在支持平台开启（Gradle 7+）【文档 `file_system_watching.adoc:22-27`】；
- 监听事件只做**增量失效**：`SnapshotCollectingDiffListener` 把差异发布给 `FileWatcherRegistry.virtualFileSystemContentsChanged(...)`；
- VFS 状态跨构建保留（daemon 内），构建开始时 `updateVfsOnBuildStarted` 处理"上次构建后的事件积压"；
- 不支持监听的挂载（网络盘等）会被检测并剔除，状态按需失效而非静默错用；
- 平台实现：macOS 走 FSEvents（`DarwinFileWatcherRegistryFactory`，20µs latency），Linux 走 inotify（带实例/探针上限处理），Windows 走 ReadDirectoryChanges；
- Gradle 自己管理的目录（`build/`、`.gradle/`、全局缓存）不监听，因为它们只由 Gradle 写【文档 `file_system_watching.adoc:37-45`】。

**效率含义**：重复构建的 up-to-date 判定从"遍历并哈希整个仓库"变成"读内存快照 + 少量失效路径"。这是 Gradle 增量"看起来近乎免费"的真正来源，而不是编译算法本身。

---

## 七、执行侧其他效率工程

| 手段 | 作用 | 锚点 |
|---|---|---|
| Gradle daemon | 跨构建复用 JVM、classloader、VFS、编译分析缓存 | `WatchingVirtualFileSystem` 生命周期 |
| configuration cache | 跳过配置阶段（本文件不展开） | `optimizing-builds/configuration-cache/` |
| 编译 worker 隔离 | 编译器可在进程隔离的 worker 中执行（`ProcessIsolatedCompilerWorkerExecutor`），避免污染 daemon | `DefaultJavaCompilerFactory.java:19,75-90` |
| 并行执行 | `--parallel` + worker lease；重叠输出检测阻止错误复用 | `CaptureMutableStateBeforeExecutionStep` 的 `OverlappingOutputDetector` |
| 陈旧输出清理 | `HandleStaleOutputsStep` 每构建一次，而非每任务 | `ExecutionBuildServices.java:190` |
| 空输入短路 | `SkipEmptyMutableWorkStep` 直接清理输出并跳过 | `ExecutionBuildServices.java:193` |
| 持久化 class 分析 | user-home 级 `jarAnalysis` / `classAnalysis` | `UserHomeScopedCompileCaches.java:33-66` |

---

## 八、正确性：什么时候显式放弃增量

Gradle 的立场是"**全量永远正确，增量必须可证**"。放弃增量的触发条件（合并自上述源码）：

| 场景 | 判定结果 | 锚点 |
|---|---|---|
| 无增量信息 / clean / 编译参数变化 | 全量 | `SelectiveCompiler.java:68-70` |
| 无 `previous-compilation-data.bin` | 全量 | 同上 71-74 |
| 推导不出 source roots | 全量 | 同上 75-78 |
| 字节码/jar 分析失败 | 全量（原因持久化） | `DefaultClassSetAnalyzer.java:65-80` |
| classpath 分析数据缺失 | 全量 | `CurrentCompilation.java:44-50` |
| `module-info` 变化 | 全量 | `ClassSetAnalysisData.java:243-245` |
| `RetentionPolicy.SOURCE` 注解变化 | 全量 | `ClassDependenciesVisitor.java:340-360` |
| 可内联常量变化且无常量映射 | 全量 | `ClassSetAnalysis.java:106-113` |
| 注解处理器非增量 / 上一轮报错 | 全量 | `ClassSetAnalysis.java:101-104` |
| 改了资源且注解处理器在场 | 全量 | `JavaRecompilationSpecProvider.java:51-54` |
| 使用 CLI 编译器 | 关闭 compiler API、常量分析、失败后增量 | `JavaCompile.java:163-168` |
| 自定义 `executable`/`javaHome` | 关闭部分优化 | 【文档 `java_plugin.adoc:550-559`】 |
| 验证失败（配置/属性） | 关闭增量执行 | `ResolveChangesStep.java:41,68` |

设计要点：**"原因"是数据不是异常**（`fullRebuildCause` 会写入快照并在下一轮继续生效），因此"分析失败"不会退化成"每轮静默重试增量"。

---

## 九、对外契约与已知限制【文档】

`java_plugin.adoc:526-560`：

- 重编"受影响的类"闭包：改动类 + 直接/传递依赖类，跨项目、跨外部库都适用；依赖来自字节码类型引用或编译器的符号分析。
- 源保留注解不可见 → 改动即全量。
- 类分析缓存在项目目录；首次 clean checkout 偏慢，CI 可考虑关闭增量。
- 类分析是构建缓存产物 → 缓存命中后下一次仍增量。
- 已知问题：注解处理器读资源需自行声明为输入；资源变化触发全量；自定义 `executable`/`javaHome` 使部分优化失效（编译失败后、常量变化后不能立即增量）；源码目录与包名不匹配在注解处理/缓存场景可能出问题。

增量注解处理【文档 `java_plugin.adoc:561-640`】：

- 必须显式注册 `META-INF/gradle/incremental.annotation.processors`，类别 `isolating` / `aggregating`（可 `dynamic` 运行时决定）；
- 只能读 `CLASS`/`RUNTIME` 保留注解；参数名需 `-parameters`；必须用 `Filer` 生成；不得依赖编译器私有 API（`com.sun.source.util.Trees`）；`createResource` 的 location 限于 `CLASS_OUTPUT`/`SOURCE_OUTPUT`/`NATIVE_HEADER_OUTPUT`，否则关闭增量；
- isolating 处理器必须只依据单个被注解元素的 AST 决策，且每个生成文件恰好一个 originating element；否则保守全量。

---

## 十、效率结论：为什么这套组合快

1. **削工作的层级化**：任务级（不跑）＞ 输出级（不写）＞ 文件级（少编）＞ 字节码级（少分析，靠 user-home 缓存）。每一层都能独立命中。
2. **变化判定被归一化两次**：L1 用 ABI/相对路径判定"是否要跑"；L4 用 ABI-only 快照判定"跑了要编多少"。两者共享同一套 ABI 语义，不会出现"任务说没变、编译器说变了"的口径分裂。
3. **状态全部可持久化**：执行 history、输出快照、class 分析、classpath 分析、常量映射、注解处理映射，都是可序列化/可缓存的产物；因此"快"不依赖进程内状态存活（进程内状态只是额外加速）。
4. **代价是状态体积与失效复杂度**：Gradle 用 `reduceToTypesAffecting` 收缩 classpath 快照，用"只保留首个同名类"处理遮蔽，用 `dependencyToAll` 兜底不可分析输入。这些正是它能在保持正确性的同时不爆内存的原因。

---

## 十一、对本仓 nop-incremental-build 的可迁移要点（仅事实清单，不作决策）

> 本节只把 Gradle 的做法翻译成对本仓设计有信息量的问题；采纳与否仍由 `01` / `03` 的裁定决定。

| # | Gradle 做法 | 对应本仓概念 | 迁移时要回答的问题 |
|---|---|---|---|
| 1 | 任务级粗判 + 语言级细判双层 | Maven 骨架成本 vs javac 边际成本（`00-vision.md §2`） | 是否两层都做，还是只做"委托 Maven + 测试选择"（03 的收缩退路） |
| 2 | ABI/签名谓词同时用于"是否跑"和"编多少" | I1 跨模块污染（含签名谓词与方向） | 谓词口径是否单一事实源，避免两层分裂 |
| 3 | 分析结果作为任务输出持久化 | `01 §12.4` 与构建侧增量的关系 | 分析文件放哪、是否进缓存、如何随 `clean` 失效（I2） |
| 4 | 反依赖闭包传播（BFS，非文件清单） | 编译作用域算法（`01 §九`） | 闭包边界是否包含注解生成与常量依赖 |
| 5 | `dependencyToAll` 数据化 | 回退矩阵（`01 §十五`） | 回退原因是否可观测、可持久、可复现 |
| 6 | 删除残留按 class→source 反查 + PatternSet | I4 删除残留须由 AST 计算 | 反查表从哪来、何时更新 |
| 7 | 事务化输出（stash/rollback） | 编译失败后的状态 | 失败后是否允许继续增量（对应"编译错误后不立即增量"的取舍） |
| 8 | 输出目录置于 classpath 首位 | 增量编译的 classpath 物化 | 与 `01 §11.3` 的 classpath 物化是否冲突 |
| 9 | classpath 快照最小化 | 状态体积控制 | 是否需要对 465 个 pom 的依赖面做收缩 |
| 10 | 常量内联变化保守全量 | I5 数据与模型驱动测试的相邻风险 | 本仓代码生成/AOP 是否引入同类"隐式内联" |
| 11 | 失败/缺失一律全量，不"继续但不校验" | I6 索引召回闸门的方向性不等式 | 索引/分析不可信时的降级路径 |
| 12 | VFS+watcher+daemon 才使判定近乎免费 | Phase 0 成本基线 | 本仓没有 watcher/daemon 时，判定成本必须实测，不能引用 Gradle 的"免费"结论 |

**明确不可直接搬运的差异**：

- Gradle 的"判定便宜"依赖常驻 daemon + 文件监听 + 跨构建 VFS；Maven 单次进程模型下没有等价物。本仓的对应结论是 Phase 0 实测骨架成本（`00-vision.md §2.3`），不能用 Gradle 的数字替换。
- Gradle 对"换构建系统"的评估与本仓 `03 §五` 一致：迁移成本极高；本文件不构成重新评估"换 Gradle"的理由。
- Gradle 的 ABI 语义服务于 JVM 字节码；本仓若涉及 XLang/AOP 生成物，签名谓词的"可访问面"定义需要单独裁定，不能照抄 `ACC_PRIVATE` 二分。

---

## 十二、关键源码索引

> 路径中的 `...` 表示省略的中间包路径（按类名定位，不随上游目录微调而失效）。

| 主题 | 入口 |
|---|---|
| 任务 action 与增量装配 | `platforms/jvm/language-java/.../api/tasks/compile/JavaCompile.java` |
| 选择性编译决策 | `platforms/jvm/language-java/.../compile/incremental/SelectiveCompiler.java` |
| 重编译规格 | `platforms/jvm/language-java/.../incremental/recomp/AbstractRecompilationSpecProvider.java`、`.../recomp/RecompilationSpec.java` |
| 依赖分析 | `platforms/jvm/language-java/.../incremental/asm/ClassDependenciesVisitor.java`、`.../asm/ClassRelevancyFilter.java` |
| 闭包与差异 | `platforms/jvm/language-java/.../incremental/deps/ClassSetAnalysis.java`、`.../deps/ClassSetAnalysisData.java`、`.../deps/ClassDependentsAccumulator.java` |
| classpath 快照 | `platforms/jvm/language-java/.../incremental/classpath/DefaultClassSetAnalyzer.java` |
| 结果持久化 | `platforms/jvm/language-java/.../incremental/IncrementalResultStoringCompiler.java`、`.../recomp/PreviousCompilationData.java` |
| 输出事务 | `platforms/jvm/language-java/.../incremental/transaction/CompileTransaction.java` |
| 编译缓存 | `platforms/jvm/language-java/.../incremental/cache/UserHomeScopedCompileCaches.java` |
| 执行流水线 | `platforms/core-execution/execution/.../execution/steps/`；装配见 `subprojects/core/.../service/scopes/ExecutionBuildServices.java` |
| 变更检测 | `platforms/core-execution/execution/.../history/changes/DefaultExecutionStateChangeDetector.java` |
| cache key | `platforms/core-execution/execution/.../caching/impl/DefaultCachingStateFactory.java` |
| ABI 哈希 | `platforms/core-execution/normalization-java/.../changedetection/state/AbiExtractingClasspathResourceHasher.java` |
| classpath 指纹 | `platforms/core-execution/normalization-java/.../fingerprint/classpath/impl/ClasspathFingerprintingStrategy.java` |
| VFS / 文件访问 | `platforms/core-execution/snapshots/.../vfs/impl/DefaultFileSystemAccess.java` |
| 文件监听 | `platforms/core-execution/file-watching/.../watch/` |
| 哈希缓存 | `platforms/core-execution/hashing-services/.../changedetection/state/CachingFileHasher.java` |
| 行为验证测试 | `platforms/jvm/language-java/src/integTest/groovy/org/gradle/java/compile/incremental/`（如 `JavaSourceIncrementalCompilationIntegrationTest`、`JavaClassChangeIncrementalCompilationIntegrationTest`、`JavaCompileAvoidanceWithIncrementalCompilationIntegrationTest`、`BaseIncrementalCompilationAfterFailureIntegrationTest`、`CrossTaskIncrementalJavaCompilationIntegrationTest`） |
| 用户契约 | `platforms/documentation/docs/src/docs/userguide/reference/core-plugins/java_plugin.adoc:526-640`、`.../optimizing-builds/build-cache/build_cache_concepts.adoc:113-120`、`.../reference/runtime-configuration/file_system_watching.adoc:15-70` |
