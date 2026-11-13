# C6 资源生命周期与泄漏 — nop-ai-rest（WI 3.4）

## 审计范围与口径

- **单批次 8 源码根覆盖声明**：本初审由 C6 维度单批次逐文件审读完成，覆盖 `nop-ai/nop-ai-api/src/main/java`（29f/3,917）、`nop-ai/nop-ai-coder/src/main/java`（21f/2,050）、`nop-ai/nop-ai-dsl-orm/src/main/java`（4f/226）、`nop-ai/nop-ai-rag/src/main/java`（20f/1,810）、`nop-ai/nop-ai-code-analyzer/src/main/java`（33f/3,450）、`nop-ai/nop-ai-dao/src/main/java`（46f/614）、`nop-ai/nop-ai-service/src/main/java`（27f/863）、`nop-ai/nop-ai-app/src/main/java`（1f/32），合计 181 个手写 Java 文件（不含 `_` 前缀/`_gen/` 生成文件）。
- **Phase 2 收官说明**：本维度为 nop-ai-rest 审计 Phase 2 收官维。审计方法论依据 `ai-dev/skills/concurrency-resource-audit-prompt.md` 共享前缀（严重度判级、误报校准、口径纪律）+ C6 正文（资源获取点配对表）+ 附录 A（13 字段发现格式）。
- **盲审纪律声明**：初审期间未阅读 `ai-dev/audits/**`、`ai-dev/plans/**`、`ai-dev/bugs/**`、`ai-dev/lessons/**`、`ai-dev/backlog/**`，未使用 git log 查修复历史；仅使用 live code、javadoc 与 `_tmp/cra-baseline-nop-ai-rest.txt` 基线（C6a/C6b 节仅用于圈定重点）。
- **口径纪律复现**：基线 C6a/C6b 的数量结论已人工逐点复现——8 根内 JDBC 获取点共 3 处（`PgVectorStore` L115/L152/L188 `dataSource.getConnection()`，全部 try-with-resources 配对）；`FileInputStream` 获取点共 1 处（`JavaCodeFileInfoParser` L76，finally + `IoHelper.safeCloseObject` 配对）；`Files.walkFileTree` 1 处（自管理）；`Files.walk/Files.list/Files.lines/DirectoryStream/newBufferedReader/newInputStream` 全根 **0 处**（grep 复现）；直接文件写点 1 处（`JavaMethodReplacer` L50 `Files.write`），其余写面经平台 `FileHelper/ResourceHelper/XNode.saveToResource` 封装。8 根内无 `Closeable/AutoCloseable` 实现（nop-ai-shell 的 `ShellCommandExecutor` 不在本批次范围）。
- **嵌入/向量库客户端生命周期口径**：`IEmbeddingModel` 生产实现位于 nop-ai-core（另一批次范围）；本批次内嵌入面（`AiModelTextEmbedding` 桥接、`EmbeddingModelBasedClassifier` reserved 参考实现）均不持有 OS 资源。`PgVectorStore` 的连接池由集成方注入 `DataSource` 所有，javadoc 明文声明该边界——按「已声明边界不是缺陷」口径不计发现。
- **降档口径声明**：本维度泄漏/残留类发现一律不适用启动期单线程降档；本轮发现全部位于请求/任务运行路径，无需援引该降档。

## 发现

### [C6-001] PgVectorStore 的 schemaReady 为门店级标志，误控按 collection 的 DDL——第二个 collection 起全部操作永久失败

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/vector/PgVectorStore.java:52,82-103`（消费点 L116/L153/L189；collectionName 注入点 `RagIngestService.java:79-81`、`RagSearchService.java:46-48`）
- **证据片段**:
  ```java
  private volatile boolean schemaReady;              // L52：全店一个标志
  ...
  private void ensureSchema(Connection connection, String collection) throws SQLException {
      if (schemaReady) {                             // L83：与 collection 无关地提前返回
          return;
      }
      ... // CREATE EXTENSION IF NOT EXISTS vector
      try (PreparedStatement stmt = connection.prepareStatement(
              "CREATE TABLE IF NOT EXISTS " + collection + " (id text NOT NULL, ...")) {
          stmt.execute();
      }
      schemaReady = true;                            // L102：建好 A 表即宣告全店就绪
  }
  ```
- **严重程度**: P1
- **并发场景**: HTTP worker 线程池并发调用 `NopAiRagBizModel`（singleton）→ `RagIngestService`/`RagSearchService`/`PgVectorStore`；`schemaReady` 是跨请求共享的可变生命周期状态，DDL 惰性初始化的完成标志被错误地作用域到整个 store 而非单个 collection（表）。
- **触发时序**: （a）确定性时序——T1 ingest(indexId="A") 执行 ensureSchema(conn,"A") L86-102，CREATE TABLE A 成功后 L102 置 schemaReady=true → T2 ingest(indexId="B")（不同请求线程或同线程顺序执行均可）进入 ensureSchema，L83-85 因 schemaReady=true 提前 return → 继续执行 `INSERT INTO B ...`（L117-132）→ PostgreSQL 抛 relation "b" does not exist → L134-137 包装为 `ERR_AI_CORE_INVALID_ARGUMENT`。此后对 B 的 store/delete/search 全部失败，schemaReady 永不复位，直至进程重启（且重启后首个访问的 collection 仍独占 DDL）。（b）并发首建时序——T1、T2 同时首次访问同一 collection：两者都在 L83 通过检查，各自在独立连接上执行 CREATE TABLE IF NOT EXISTS；PostgreSQL 并发同名 DDL 存在 `duplicate key value violates unique constraint pg_type_...`/`relation already exists` 竞态异常面，任一失败即该请求报错（幂等语句不保证并发下无异常）。
- **现状**: 惰性 DDL 初始化状态机的就绪标志作用域错配：`resolveCollection` 支持每请求不同 collection（L217-223），RAG 管线把 `indexId` 直接作为 collectionName（`RagIngestService` L79-81），多索引是设计内用法，但 DDL 门禁只认第一个访问的 collection。
- **风险**: 任何多索引部署（设计主路径）从第二个 indexId 起读写全部永久失败并伪装成客户端参数错误；并发首建窗口偶发初始化失败。数据未损坏（INSERT 失败即回滚），但可用性语义违约，且错误码误导调用方按"参数错"处理而不重试。
- **建议**: 将就绪状态按 collection 键控：`ConcurrentHashMap.newKeySet<String>() schemaReadyCollections`，以 `add(collection)` 的 CAS 语义替代单一布尔；并发首建窗口用平台 `io.nop.commons.concurrent.lock.IResourceLockManager`（或容忍 IF NOT EXISTS 幂等 + 捕获并发 DDL 异常后重试一次）。同类可参考平台 `computeIfAbsent` 惰性初始化惯例。
- **失效面枚举**（8 根内惰性初始化/资源就绪标志全清单，grep 复现）: `PgVectorStore.schemaReady`（L52，缺陷本体）；`AiModelTextEmbedding.cachedDimension`（L28，volatile int 咨询性缓存、无门禁语义——无影响）；`MavenProject.javaFileFinder`（L33）/`moduleStructure`（L48，无同步惰性初始化——登记为 CL-014，非 DDL 门禁）；`JavaSourceFileFinder.classNameCache`（L36，ConcurrentHashMap——正确）。其余 5 根无惰性资源初始化门禁。
- **信心水平**: 确定
- **误报排除**: 不是"有界声明/平台实现重审"豁免对象——schemaReady 是本模块手写生命周期状态机；不是无状态单例误报——存在可变门禁标志且能写出确定性与并发两类具体时序。
- **复核状态**: 未复核
- 复核状态: 保留(P1)·**三登记主登记**｜新发现·已知未修复——经裁定为 PgVectorStore.schemaReady 三登记（C1-002=C3-004=C6-001）的唯一工单登记：根因层 = 生命周期/初始化守卫的作用域错配（确定性面主导，非并发竞态主导），C6 视角为最准确分层；C1-002（并发 CTA 子面）与 C3-004（javadoc 契约锚点 + 5 入口失效面枚举 + 并发双跑幂等校准）吸收为视角登记，不重复计数；确定性时序(a)回码亲证（L52/83/102 + 调用点 L116/153/189 + indexId→collectionName RagIngestService L79-80/RagSearchService L46-47 + beans.xml「生产通过 Delta 替换」注释）；并发时序(b)经 C3-004 校准降为瞬态子面（pg_type duplicate key 竞态实存但重试可过），随主登记修复一并消除；severity 校准：设计内多索引部署确定性功能不可用 + 错误码误导，无数据损坏——P1 成立、不升 P0

### [C6-002] JavaMethodReplacer 以非原子原地覆写改写源文件——失败/中断路径损毁目标文件，无临时文件替换、无备份、无文件锁

- **文件**: `nop-ai/nop-ai-coder/src/main/java/io/nop/ai/coder/code/JavaMethodReplacer.java:19-51`
- **证据片段**:
  ```java
  String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8); // L22 读
  ...
  String newContent = content.substring(0, braceStart)
                      + newMethodBody
                      + content.substring(braceEnd + 1);                          // L45-47 内存拼装
  // 写回文件
  Files.write(path, newContent.getBytes(StandardCharsets.UTF_8));                 // L50 截断式原地覆写
  ```
- **严重程度**: P2
- **并发场景**: （1）单线程失败路径即可触发（无需并发）：L50 `Files.write` 默认 CREATE+TRUNCATE_EXISTING，先截断后写入；（2）并发场景为多工具线程对同一源文件的 read-modify-write——L22 读与 L50 写之间无任何文件锁或版本校验。
- **触发时序**: （a）T1 执行到 L50 `Files.write` 中途发生磁盘满/IO 错误/进程被 kill -9 → 目标文件已截断且新内容未完整落盘 → 原文件内容不可恢复（无备份）。（b）T1 执行到 L22 读取 content（状态 S1）→ T2 对同一文件完成完整 applyMethodDiff（文件已变）→ T1 执行 L50 用基于 S1 拼装的内容整文件覆写 → T2 的修改静默丢失（丢失更新），且若 T1 的正则匹配的是 S1 中另一处相似签名，还会把方法体写入错误位置。
- **现状**: 8 根内唯一的直接 `java.nio` 文件覆写点；无 temp+atomic-move、无 .bak、无 `LocalFileLock`。当前仓库 main 代码零消费者（仅 `nop-ai-coder/src/test/.../TestCoderErrorCodeConversion.java` 引用），属工具面能力预留，但类本身无 Reserved 声明、随时可被 AI codegen 工具链接线。
- **风险**: 一旦被接线到代码改写链路，失败/中断即破坏用户源文件（P1 级后果）；并发改写静默丢更新。按当前零 main 消费者口径暂记 P2。
- **建议**: 改为「写临时文件（同目录 `Files.createTempFile`）→ `Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE)`」；失败时保留原文件；跨线程共享目标文件时用平台 `io.nop.commons.concurrent.lock.LocalFileLock`。大文件场景同时改用 `Files.readString`（见 CL-011）。
- **失效面枚举**（8 根内文件写点全清单，grep 复现）: `JavaMethodReplacer` L50 `Files.write`（缺陷本体，唯一 read-modify-write 覆写面）；`JavaCodeFileInfoGenerator` L82 `FileHelper.writeText`（平台封装，目标 `.info.json` 存在即跳过——幂等可恢复，无破坏性覆写）；`AiCoderHelper` L126 `XNode.saveToResource`、L147/L151 `ResourceHelper.writeText`（平台封装，生成物写入）；`MavenProject` L22 ShellRunner 生成 `target/nop-dependency-tree.txt`（外部 mvn 写入 target/，Maven 惯例可清理）。其余 6 根无直接写点（grep: NO_IO_MATCHES）。
- **信心水平**: 确定（机制确定；影响面受限于当前零 main 消费者）
- **误报排除**: 非测试专用代码的简化并发模型豁免——类位于 main 源码根且无 Reserved 裁定标注；`Files.write` 原语本身不算问题，缺陷是复合操作缺原子替换与锁，属 F2/F6/F8 同族形态。
- **复核状态**: 未复核
- 复核状态: 保留(P2·条件化)｜新发现·已知未修复（潜伏：全仓仅 TestCoderErrorCodeConversion 引用，零 main 消费者复核属实；失败/中断路径损毁面 + 接线即回升 P1 的自身条件标注成立）——**分歧裁定**：与 C6-004 为同族（F2/F6 非原子写）不同站点不同根因的两个独立缺陷，双保留不合并；本条失效面枚举中对 JavaCodeFileInfoGenerator 站点的「幂等可恢复，无破坏性覆写」clearing 结论经复核**被 C6-004 推翻成立**（exists()-skip 使半写产物永不可由重跑自愈），更正为「write-once + skip-if-exists 放大面，独立立案见 C6-004」；跨单元查重：WI 3.2 单元为 toolkit/tools/shell 不含 nop-ai-coder，JavaMethodReplacer 首审于此（2026-07 ARM MA4.2-11 仅删 demo main、AI-19/20 为风格域，非并发域，无重复立项）

### [C6-003] JarResolverCollection/JavaParserBuilder 注册 JarTypeSolver（持有打开的 jar 句柄）后无任何释放生命周期——F8 清理不对称

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/JarResolverCollection.java:20,34-46`；`nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/code/JavaParserBuilder.java:17-19,60-65`
- **证据片段**:
  ```java
  public class JarResolverCollection {                       // 无 Closeable/AutoCloseable、无 close 方法
      private final Map<String, TypeSolver> jarSolvers = new HashMap<>();
      public TypeSolver addJar(File jarFile) {
          String path = FileHelper.getAbsolutePath(jarFile);
          if (jarSolvers.containsKey(path))
              return jarSolvers.get(path);
          try {
              TypeSolver jarSolver = new JarTypeSolver(jarFile);  // L40：打开 jar/zip 句柄 + 符号表
              jarSolvers.put(path, jarSolver);                    // L41：强引用持有，永不移除
              return jarSolver;
  ```
- **严重程度**: P2
- **并发场景**: 代码分析任务线程调用 `addJar`/`addModuleJars` 注册依赖 jar；`JavaParserBuilder` 侧 L60-65 再把同一 `TypeSolver` 二次登记进自身 `jarSolvers` Set 与 `CombinedTypeSolver`——两层账本都只有 add、都没有 remove/close。
- **触发时序**: 无需并发交错（生命周期缺失型）：T1 分析任务对 N 个依赖 jar 逐个 `addJar` → 每个 `JarTypeSolver` 内部打开 `java.util.jar.JarFile`（文件描述符 + central directory 解析 + 符号缓存）→ 任务结束后集合若仍被持有（builder/service 长驻引用），全部句柄与符号表驻留至进程结束；仅当集合整体不可达时才由 JDK ZipFile 的 Cleaner 兜底延迟释放。T2（若未来多任务并发共享同一 collection）各任务注册的 jar 句柄相互叠加，无任何按任务回收路径。
- **现状**: register/open 与 unregister/close 完全不成对（F8）：`JarResolverCollection` 与 `JavaParserBuilder` 均未实现 `AutoCloseable`，无 close/unregister/clear 生命周期方法（`clearCache` 只存在于 `JavaSourceFileFinder`）。当前全仓 main/test 零外部消费者（grep 复现），模块未接线，属潜伏缺陷。
- **风险**: 一旦接入长驻分析服务，每分析一批新 jar 就累积一批 fd 与内存符号表——长时运行 fd 耗尽/内存增长；Cleaner 兜底依赖对象不可达，与「集合长驻」的使用方式矛盾。
- **建议**: 为 `JarResolverCollection` 实现 `AutoCloseable`，close 时遍历 `jarSolvers.values()`，对实现 `Closeable` 的 solver（JavaParser `JarTypeSolver` 依赖 `CloseableSource` 体系）逐个关闭；`JavaParserBuilder` 侧持有 collection 引用即可，删除重复的 `jarSolvers` 双重记账；长驻服务按分析任务作用域持有并在任务终态统一 close。
- **失效面枚举**（8 根内资源持有型注册表全清单）: `JarResolverCollection.jarSolvers`（L20，缺陷本体，无释放）；`JavaParserBuilder.solver + jarSolvers`（L17/L19，同一批实例的二次登记，同样无释放，与本体同根同修）；`JavaSourceFileFinder.classNameCache`（L36，持 File 引用非 OS 句柄，无泄漏）；`InMemoryVectorStore.buckets`（数据持有非句柄，归 CL-012）；`PgVectorStore.dataSource`（集成方所有，javadoc 已声明边界，非缺陷）。
- **信心水平**: 确定（机制确定；当前零消费者，未造成实际泄漏事件）
- **误报排除**: 不是"原语使用不是问题"豁免——缺陷不是某个 API 误用，而是整个释放生命周期缺席；也不是已声明边界——类 javadoc 无 Reserved 裁定，无任何"短期进程退出即释放"的声明。
- **复核状态**: 未复核
- 复核状态: 保留(P2·条件化)｜新发现·已知未修复（潜伏：JarResolverCollection/JavaParserBuilder 全仓仅 TestJavaCodeFileInfoParser 引用，零 main 消费者复核属实）——泄漏/残留形态不适用启动期降档的口径声明核对正确（P2 由零消费者条件化支撑，非启动期降档）；L20/34-46 与 JavaParserBuilder L17-19/60-65 双层记账无释放亲证

## 维度集外线索（overlay C2/C4/C5/C7/C8/C9 + 非 rag C3）

### [nop-ai-rest-CL-010] （C2/C1）JavaSourceFileFinder.getAllJavaSourceFiles 用 parallelStream 向普通 ArrayList 并发写入——数据竞争

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/maven/JavaSourceFileFinder.java:165-173`
- **证据**: `moduleStructure.getModules().values().parallelStream().map(...).forEach(moduleDir -> findJavaFilesInModule(moduleDir, results))`，`results` 为方法内 `new ArrayList<>()`（L166），多 FJ worker 经 `findJavaFiles`（L228-241）并发 `results.add(file)`。
- **线索说明**: T1（FJ-worker-1）与 T2（FJ-worker-2）同时执行 `ArrayList.add` → size 丢失更新（丢文件）或扩容交叠抛 `ArrayIndexOutOfBoundsException`。建议 `collect`/`Stream.of(...).flatMap` 收敛或换平台线程设施；当前全仓零消费者，潜伏。C6 维度内按口径不展开，移交 C2 定审。

### [nop-ai-rest-CL-011] （C5）仓库遍历工具面三处整文件读入内存，无单文件大小上限

- **文件**: `nop-ai/nop-ai-coder/.../code/JavaMethodReplacer.java:22`（`Files.readAllBytes`）；`nop-ai/nop-ai-code-analyzer/.../stats/CodeLineAnalyzer.java:33`（`Files.readAllLines`）；`nop-ai/nop-ai-code-analyzer/.../maven/MavenDependencyTreeParser.java:14`（`FileHelper.readLines`）
- **线索说明**: 三者均服务于全仓遍历场景（code-analyzer 走遍目标仓库全部文件），单个误入的大文件（如误带 .java/.txt 扩展的巨型生成物）即造成无上界内存尖峰；`CodeLineAnalyzer` 由 `FileTreeWalker` 逐文件回调调用，遍历面无大小闸门。F3 同族已逐一登记（此三处即全清单，grep C5b 复现）。移交 C5 定审（OOM 族 F7）。

### [nop-ai-rest-CL-012] （C5/C7，F4）InMemoryVectorStore 为 IoC 缺省 bean 且 biz 面无 delete 入口——内存账本只增不删

- **文件**: `nop-ai/nop-ai-rag/src/main/resources/_vfs/nop/ai/rag/beans/rag-defaults.beans.xml`（`InMemoryVectorStore ioc:default="true"`）；`nop-ai/nop-ai-rag/.../vector/InMemoryVectorStore.java:41-56`；`nop-ai/nop-ai-rag/.../biz/NopAiRagBizModel.java:46-76`
- **线索说明**: 类 javadoc 声称「不注册 IoC default bean」已被 K3 装配推翻——beans 文件将其注册为 `IVectorStore` 缺省实现；`buckets`（tenant/collection → 全量 VectorData，含 embedding 数组与 content 文本）无淘汰/TTL；`NopAiRagBizModel` 仅暴露 ingestDocument/search/synthesize，SPI 的 `delete` 无 biz 入口 → 未 Delta 替换为 PgVectorStore 的部署中，摄取数据只增不删直至 OOM。javadoc 与装配真相不一致本身也需 owner doc 修订。移交 C5/C7 定审。

### [nop-ai-rest-CL-013] （C7，F2）PgVectorStore store/delete 批量写无事务——autocommit 下批量部分成功无恢复语义

- **文件**: `nop-ai/nop-ai-rag/src/main/java/io/nop/ai/rag/vector/PgVectorStore.java:115-137,152-167`
- **线索说明**: `store`/`delete` 在 autocommit 连接上 `addBatch`+`executeBatch`，无 `setAutoCommit(false)`/commit/rollback——executeBatch 中途失败时已执行的行已各自提交（"半个成功"），调用方只收到异常、无重试/幂等恢复约定（store 的 ON CONFLICT upsert 使整体重放幂等，但 delete 部分失败后重放语义未声明）。附注：SQLException 一律包装为 `ERR_AI_CORE_INVALID_ARGUMENT`（客户端参数错误族），瞬时 DB 故障会被按错误码分类的调用方误判。移交 C7 定审。

### [nop-ai-rest-CL-014] （C2/C9）MavenProject 惰性初始化无同步 + 零模块路径不缓存——重复全树遍历

- **文件**: `nop-ai/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/maven/MavenProject.java:32-57`
- **线索说明**: `getJavaFileFinder`/`loadModuleStructure` 均为 check-then-act 惰性初始化（L33/L48），并发调用重复构建 `JavaSourceFileFinder`/`MavenModuleStructure`（各自触发全项目目录树递归 `listFiles` 遍历）；且 `loadModuleStructure` 在 moduleCount==0 时故意不缓存（L54-56），每次调用都重扫整树。后果限于重复劳动与浪费（无句柄泄漏），低频工具路径。移交 C2/C9 定审。

---
（本批次 C6 维度：发现 3 条 = P0×0 / P1×1 / P2×2 / P3×0；维度集外线索 5 条 = CL-010…CL-014。基线 C6a/C6b 全部获取点已逐点核对配对状态并复现计数，见「审计范围与口径」。）

---

## 维度复核结论（WI 3.4 独立复核，2026-10-20）

- **复核员**: WI 3.4 独立复核员（合并批 C1+C3+C6 三维度）
- **复核范围**: 本文件 C6-001~C6-003 + C6-round2.md C6-004，共 4 条；CL-010~015 线索同批核对
- **台账查重结论**: 与 cra-index WI 1.1–3.3 零同站点重复。C6-002（JavaMethodReplacer）为 nop-ai-coder 首审（WI 3.2 单元为 toolkit/tools/shell 不含 coder；2026-07 ARM MA4.2-11 与 conformance AI-19/20 均为风格域非并发域）；C6-001 即 schemaReady 三登记的 C6 面；CL-012 与 WI 3.1 MA-03（F4 引擎终态清理族）同族不同模块，无重复立项。

### schemaReady 三登记裁定（C1-002 = C3-004 = C6-001）

**关系认定：同一缺陷的三维视角，非三个独立缺陷。** 三登记的核心确定性面完全同一——实例级 `volatile boolean schemaReady`（L52）与 per-collection DDL 作用域错配（L83-85 提前 return / L102 单表置位），第二个 collection 起确定性失败；三者的调用点（store L116 / delete L153 / search L189）、触发链（indexId→collectionName）与行号经逐项回码复核一致。差异仅在视角与增量子面：

| 登记 | 视角 | 独立增量 | 复核裁定 |
|---|---|---|---|
| **C6-001** | 生命周期守卫作用域错配 | 并发首建时序(b) + 错误码误导 + 重启后仍独占 DDL | **主登记（P1）**——根因层是初始化状态机的守卫粒度错误，确定性面主导严重度，C6 分层最准确；修复归属唯一 |
| C1-002 | 共享可变状态 CTA | volatile 仅保可见性、check-then-act 无互斥的首访竞态子面 | 吸收；其时序 2（并发 DDL 竞态）经 C3-004 校准约束为**瞬态面**（pg_type duplicate key 竞态实存、重试可过、非永久） |
| C3-004 | 初始化时序假设违约 | javadoc 契约锚点 + 失效面 5 入口枚举 + 「并发双跑 DDL 幂等无害」校准排除 | 吸收；javadoc L37-38 原文为模糊的「首次访问执行 CREATE TABLE IF NOT EXISTS」，「per-collection 声明」降为佐证；幂等校准被主登记采纳 |

**修复结构**：`schemaReady` 改 per-collection ready set（`ConcurrentHashMap.newKeySet` + `add` CAS 语义或 `computeIfAbsent`）一处修复同时闭合三登记的确定性面与并发子面。severity 校准：设计内多索引部署（生产 Delta 装配 + indexId 即 collectionName）确定性功能不可用 + INVALID_ARGUMENT 错误码误导，无数据损坏——P1 成立、不升 P0（非数据竞争、非主路径数据损坏，单 collection 部署不受影响）。

### C6-002 / C6-004 分歧裁定

**裁定：两案均成立、均保留，不合并。** 失效面结构：

| | C6-002（JavaMethodReplacer） | C6-004（JavaCodeFileInfoGenerator） |
|---|---|---|
| 站点 | `JavaMethodReplacer.java:50`（源文件覆写） | `JavaCodeFileInfoGenerator.java:82`（.info.json 产物写） |
| 根因 | read-modify-write 无原子替换、无锁、无备份 | write-once × `exists()`-skip（:57-58）放大：半写产物永久豁免重生成 |
| 后果 | 失败/中断损毁**用户源文件**（不可恢复） | 崩溃残留半写 JSON **永久保留**（重跑不可自愈、无检测信号） |
| 消费者 | 零 main（仅 1 测试类） | 零 main（仅 2 测试类） |

C6-004 对 C6-002 失效面 clearing 结论的分歧**经复核成立**：C6-002 所称「幂等可恢复」不成立——`destFile.exists()` 即跳过（:57-58）使半写产物存在后重生成永不发生；「无破坏性覆写」单线程正常路径成立但与放大面无关。C6-002 失效面枚举中该站点描述按 C6-004 更正。两案同属 F2/F6 非原子写家族，建议 Phase 8 同批次修（同用 temp + `ATOMIC_MOVE` 范式），但保持两个独立工单（站点/根因/后果三者不同，合并会丢失「源文件破坏」与「产物永久损坏」两个不同失效语义）。

### 逐条判定表

| 发现 | 初审判级 | 判定 | 最终严重度 | 现状标注 | 复核要点 |
|---|---|---|---|---|---|
| C6-001 | P1 | 保留·**三登记主登记** | P1 | 新发现·已知未修复 | 见上节；吸收 C1-002/C3-004 |
| C6-002 | P2 | 保留 | P2（零消费者条件化，接线回升 P1） | 新发现·已知未修复（潜伏） | 机制亲证；分歧裁定如上 |
| C6-003 | P2 | 保留 | P2（零消费者条件化） | 新发现·已知未修复（潜伏） | 双层记账无释放亲证；泄漏形态不适用启动期降档口径声明核对正确 |
| C6-004（round2） | P2 | 保留 | P2（零消费者条件化，接入管线升 P1） | 新发现·已知未修复（潜伏） | FileHelper.writeText 截断写实现亲证（L147-158）；writeTextWithLock/writeTextIfNotMatch 存在未用亲证；分歧声明成立 |

### 复核裁定说明（线索层）

- **CL-010 = C1-001 同站点**：吸收关闭，以 C1-001 为唯一立案。
- **CL-012 与 CL-004 同站点同面重复**（InMemoryVectorStore 无界账本，均 C5/C7·F4）：建议以 CL-012 为主登记（事实面更全：beans.xml 缺省装配推翻 javadoc「不注册 default bean」声明 + biz 无 delete 入口，装配真相经亲证成立），CL-004 吸收标注；移交 C5/C7 定审时合并。与 C1-003 仅同站点不同缺陷面（账本无界 vs upsert 竞态），不互吸。
- **CL-013 = C1 维度 CL-002 同站点**（PgVectorStore 批量写无事务半个成功，两维度各登记一次）：事实面一致，移交 C7/C2 定审时合并为单一线索。
- **CL-015（round2，归 C7）**：ShellRunner timeout=0 → `process.waitFor()` 无限期挂死机制与平台设施超时能力俱在未用的形态登记成立，移交 C7 维持。

## 最终保留项（复核后）

| # | 发现 | 最终严重度 | 工单结构 |
|---|---|---|---|
| 1 | C6-001 schemaReady 作用域错配 | P1 | **主登记**（吸收 C1-002/C3-004） |
| 2 | C6-002 JavaMethodReplacer 非原子覆写 | P2（条件化） | 独立工单（与 C6-004 同批修、不合并） |
| 3 | C6-003 JarResolverCollection 无释放生命周期 | P2（条件化） | 独立工单 |
| 4 | C6-004 exists-skip × 截断写 | P2（条件化） | 独立工单（与 C6-002 同批修、不合并） |

C6 维度复核后：4 条全部保留、0 降级、0 驳回、0 升格；C6-001 定为三登记主登记（吸收 2 条）；独立工单 4 条（P1×1 / P2×3）。
