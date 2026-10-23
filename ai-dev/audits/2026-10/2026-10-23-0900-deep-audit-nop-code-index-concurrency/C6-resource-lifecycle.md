# C6 资源生命周期与泄漏 — 初审报告（盲审）

- **审计单元**: nop-code-index（nop-code-core 51f + nop-code-flow 12f + nop-code-service 53f，全单元 116f/16,898 行 main 代码）
- **维度**: C6 资源生命周期与泄漏（CRA WI 6.3）
- **批次**: 全单元单批（初审第 1 轮，盲审）
- **盲审声明**: 本报告为初审线索而非最终事实。初审期间未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何历史记录；现状标注（新发现/已知未修复）留待复核阶段。未修改任何产品代码。
- **CL 前缀**: 本维度集外强信号线索以 `CL-2xx` 编号附记于文末（CL-201 起）。
- **方法**: `ai-dev/skills/concurrency-resource-audit-prompt.md` C6 正文 + `ai-dev/skills/deep-audit-prompts.md` 共享前缀；基线 `_tmp/cra-baseline-nop-code-index.txt`。
- **日期**: 2026-10-23

---

## 一、资源获取点配对表

对全单元 main 代码扫描资源获取点（`ProcessBuilder.start` / `getInputStream` / `depthIterator` / ORM session / 缓存与注册表 put / 搜索引擎 doc / 磁盘目录），逐一核对配对释放。计数口径：git 子进程站点 = `ProcessBuilder` + `pb.start()` 两 grep 交叉验证，均为 4 处。

| # | 资源 / 注册点 | 获取位置 | 释放/清理位置 | 配对结论 |
|---|---|---|---|---|
| 1 | 文件 InputStream | `IncrementalDetector.computeFingerprint` L25、`detectResourceChanges` L72 | try-with-resources | 配对（全部路径含异常） |
| 2 | git 子进程 ×4 | `CodeIndexService.gitOutput` L853、`ChangeAnalyzer.parseGitDiff` L152、`ClusterWorkspaceManager.gitWithCredential` L145、`gitOutput` L187 | destroyForcibly：ChangeAnalyzer 在 finally（✅）；CodeIndexService 仅在 waitFor 超时分支（❌ 读取异常路径泄漏进程）；ClusterWorkspaceManager 仅在超时分支（❌） | **不对称** → C6-001 |
| 3 | 进程 stdout BufferedReader | ChangeAnalyzer L155、CodeIndexService L855 | try-with-resources | 配对 |
| 4 | 遍历流 `depthIterator` | `ProjectAnalyzer.analyzeProject/findSourceFiles`、`CodeIndexService.collectResourcesFromVfs` | 无 close——内核实现为 `TreeVisitors.depthFirstIterator` + `File.listFiles()` 数组语义（`IResourceLoader.java:84-86`），不持有 OS 句柄，提前退出无泄漏 | 无需 close，非发现 |
| 5 | ORM session / 事务 | 全部写路径 `transactionTemplate.runInTransaction` + `ormTemplate.runInSession` | 平台托管；嵌套 `runInSession`（`GraphMetricMaterializer.replaceRows` L272 位于 ShardBuildCoordinator 会话内）复用同事务会话 | 配对；`deleteByIndex` 内 `session.clear()` 调用点均置于快照读取之前（L1098 在 L1101/L1168 之前），无 detached 写回 |
| 6 | 指纹行（OrmFingerprintStore） | save: `CodeIndexService.triggerIncrementalIndex` L1212；load: L1071 | deleteFileRecords L1179（deletedFiles）/L1180（successfulChangedFiles）删除 NopCodeFile 行；失败文件指纹保留（L1202-1209 removeIf）为有意设计 | **配对**（生产路径）；ProjectAnalyzer.analyzeIncremental store 路径 save 不删已删文件 → C6-010 |
| 7 | InMemoryFingerprintStore 条目 | `saveFingerprints` put | `deleteByIndex` remove——唯一清理路径；全仓 main 代码零构造点（仅 4 个测试文件引用） | 内存 map 只增不删但使用面=测试，watch-only |
| 8 | CodeCacheManager.analysisCacheMap | getOrCreateEntry put | invalidateAnalysisCache（4 条写路径全部调用）+ LRU(20)+TTL 驱逐 | 配对、有界。注意 `rebuildDependencies` 无 MAX 上界 → CL-202 |
| 9 | FlowDetector flowCache / symbolFilePathCache | detectFlows put L104/L141 | invalidateCache（经 CodeCacheManager.invalidateAnalysisCache L152-154 联动）；evictOverflow 有界 20 | 配对、有界；但双 map 独立驱逐 → CL-206 |
| 10 | NopCodeIndexBizModel.incrementalStatusMap | put L75/100/110/202 | deleteIndex 时 remove L155；LRU 上界 100（removeEldestEntry） | 配对、有界；full-index 失败不记录 → C6-009 |
| 11 | 搜索引擎 topic / docs | addDoc L1813 | removeStaleSymbolDocsForFile L1717（重索引前清旧）、deleteFileRecords→removeDocs L2391、deleteIndex→removeTopic L892 | **失败路径 warn-only + 事务内非原子双写** → C6-005 |
| 12 | nop_code_shard_ledger 行 | beginShardBuild 全量重建（deleteLedger+save） | deleteIndex 的 11 个 deleteEntitiesPaged **不含** NopCodeShardLedger | **不对称** → C6-003 |
| 13 | 符号关联行清理（12 实体类型） | deleteFileRecords L2397-2421 | symbolIds 来源 `findSymbolIdsByFileId` setLimit(10000) 无分页 | **截断→孤儿** → C6-004 |
| 14 | ExecutorService 字段 | `ProjectAnalyzer` L61（预留） | 无关闭路径；生产唯一构造点 `CodeIndexService` L265 传 null，字段恒 null 未使用 | 契约缺口，无活泄漏 → C6-006 |
| 15 | cluster workspace 目录 | ClusterWorkspaceManager.prepare clone/fetch/checkout | 全类无任何删除/淘汰路径 | **无清理 + 失败残留毒化** → C6-002 |
| 16 | indexLocks / repoLocks 注册表 | computeIfAbsent | indexLocks 仅 deleteIndex remove（L917，持锁期间移除）；repoLocks 只增不删 | 有界（index/repo 数量）；移除竞态 → CL-201 |
| 17 | 文件锁/池/信号量 | — | 全单元 grep `LocalFileLock|IPool|ISemaphore|IRateLimiter` 零命中 | 不适用 |
| 18 | LlmEdgeCache | computeIfAbsent | 无淘汰、无上界、进程生命周期 | **F4 无界账本**（生产未接线）→ C6-007 |
| 19 | manifest 文件 | ManifestStore.save writeText | 覆盖写非原子；损坏后 load 返回空→全量重建自愈 | 失败路径可自愈，legacy 路径 → C6-008 |

---

## 二、发现条目

### [C6-001] git 子进程输出读取无超时覆盖——waitFor 超时位于读循环之后，静默挂起时超时与 destroy 均不可达

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:846-874`（家族成员：`nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:152-238`、`nop-code/nop-code-service/src/main/java/io/nop/code/service/cluster/ClusterWorkspaceManager.java:131-162` 及 `176-205`）
- **证据片段**:
  ```java
  // CodeIndexService.gitOutput
  Process process = pb.start();
  try (var reader = new java.io.BufferedReader(
          new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));  // ← 无超时，读到 EOF 为止
  }
  if (!process.waitFor(GIT_TIMEOUT_MILLIS, ...)) {   // ← 仅当输出流已 EOF 才可能到达
      process.destroyForcibly();
      throw new NopException(ERR_CODE_REBUILD_NOT_GIT_REPO)...
  }
  ```
- **严重程度**: P2
- **并发场景**: GraphQL worker 线程（`triggerRebuildFromCommit` BizMutation、`analyzeChanges` BizQuery）与 git 子进程；无多线程交错，属单线程挂死（C7 F5"挂死点"在 C6 超时路径核查中的形态）。
- **触发时序**: 不依赖 T1/T2 交错，为线性时序：T1 调用 `triggerRebuildFromCommit` → `gitOutput("diff", ...)` 启动 git → 被分析仓库含挂死的自定义 diff/textconv 驱动（`.gitattributes`/`.gitconfig` 可指定任意外部命令——本产品分析的是任意外部仓库）或 git 在 stale NFS 句柄上阻塞 → git 不产出任何字节也不退出 → `reader.lines()` 永久阻塞在管道 read → L859 的 30s waitFor 与 destroyForcibly 永不可达 → worker 线程与子进程双泄漏。重复触发（debounce 未命中时每次调用都会重新执行 git diff）→ 每次泄漏一个阻塞的 GraphQL worker，直至执行器队列耗尽。
- **现状**: 超时常量存在（30s/60s）但未作用于真正的等待点；属"超时不作用于所有等待点"。
- **风险**: 长时运行服务中一个病理仓库即可持续吞噬请求线程；同时 CodeIndexService.gitOutput 的 catch 分支（L868-873）在读取抛 IOException 时不 destroy，泄漏进程对象。ChangeAnalyzer 是家族中唯一在 finally destroyForcibly 的成员，但其 readLine 同样无超时。
- **建议**: 统一封装一个 git 进程执行工具：读取循环放在 `waitFor(timeout)` 的看护下（如独立读取线程 + `Future.get(timeout)`，或 `Process.toRead` + 定时 `destroyForcibly` 调度）；所有站点 finally 中 `destroyForcibly()` + `waitFor()` 收尸。可参照 `io.nop.commons.concurrent` 的线程池设施托管读取线程。
- **失效面枚举**（F3 同族清扫，grep `pb.start()` 双口径各 4 处）: ① `CodeIndexService.gitOutput` L853 — 读循环无超时+无 finally destroy（最弱）；② `ChangeAnalyzer.parseGitDiff` L152 — 读循环无超时，有 finally destroy（最强）；③ `ClusterWorkspaceManager.gitWithCredential` L145 — readAllBytes 无超时，destroy 仅超时分支；④ `ClusterWorkspaceManager.gitOutput` L187 — 同 ③。四站点均立案，合并为本条。
- **信心水平**: 很可能（挂死需要病理 git 配置/环境，但触发链具体，且产品定位就是索引任意外部仓库）
- **误报排除**: 不是"原语使用本身"误报（ProcessBuilder 使用合法）；也不是低频启动路径——`triggerRebuildFromCommit`/`analyzeChanges` 是暴露给用户的 Biz 入口且 ChangeAnalyzer 已在 `app-service.beans.xml` 装配。30s 超时常量的存在恰恰说明超时是声明语义，而实现未兑现。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P2，裁定为 git 超时族主登记并吸并 C7-003，见文末「维度复核结论」）

### [C6-002] ClusterWorkspaceManager clone 失败残留半克隆工作区，毒化复用分支且无任何工作区清理路径

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/cluster/ClusterWorkspaceManager.java:79-95`（配合 `147-151`）
- **证据片段**:
  ```java
  gitWithCredential(Path.of(workspaceRoot), credential, "clone",
          repoDir.getAbsolutePath(), workspace.toString());      // 超时 → destroyForcibly 杀死 git
  git(workspace, "checkout", revision);
  ...
  // 复用分支（下次调用）：
  if (Files.isDirectory(workspace.resolve(".git"))) {            // 半克隆的 .git 已存在
      String head = gitOutput(workspace, "rev-parse", "HEAD");   // 无提交 → 非零退出 → 永久 IllegalStateException
  ```
- **严重程度**: P2
- **并发场景**: 单线程失败路径（repoLocks 串行化同一 repo），非竞态；属 F2"半个成功"+ F6"落盘无原子屏障"交叉。
- **触发时序**: 线性时序：prepare(repo, rev) → clone 超过 60s → L149 `destroyForcibly()` SIGKILL git（git 无机会执行自身的 target 目录清理）→ 磁盘残留含 `.git/` 的半克隆 → 后续每次 prepare 走复用分支 → `rev-parse HEAD` 失败 → IllegalStateException → 该 (repo, revision) 工作区永久不可用，无自动恢复路径。
- **现状**: clone 直接写入最终目录（无临时目录+rename 原子发布）；失败后无残留清理；且全类没有任何工作区淘汰/TTL（每个 (repo, revision) 一个目录，磁盘只增不减）。
- **风险**: 一次网络抖动/慢镜像即造成该 revision 的分片构建永久失败（F2 无恢复语义）；多 repo×多 revision 长期运行磁盘无界增长。当前该类仅测试引用、未在 beans.xml 装配（dormant），故未定 P1。
- **建议**: clone 到 `workspace-<uuid>.tmp` 后原子 rename；复用分支对 `rev-parse HEAD` 失败做自愈（删除残留目录重新 clone）；为工作区增加淘汰策略（LRU/TTL）或在 beginShardBuild 时清理 stale workspace。
- **失效面枚举**: `ClusterWorkspaceManager.prepare` 的三个写路径：① clone L86-87（本条主体）；② fetch+checkout L71-72（fetch 中断残留 loose objects，但 HEAD 不变，可自愈——watch-only）；③ 无其他工作区写入点。全仓 grep `workspaceRoot` 仅本类 4 处，无其他兄弟清理点漏网。
- **信心水平**: 确定（残留行为由 `destroyForcibly` 语义直接可推）
- **误报排除**: 不是"有界声明存在的缓存"误报——工作区无任何上界声明；也不是测试专用代码（main 源码、显式配置键 `nop.code.cluster.workspace-root` 表明生产意图）。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P2，见文末「维度复核结论」）

### [C6-003] deleteIndex 清理清单遗漏 nop_code_shard_ledger，孤儿 ledger 行可击穿 completeShard 的未知 shardId 守卫并提前发布

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:884-919`（配合 `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/ShardBuildCoordinator.java:77-116`）
- **证据片段**:
  ```java
  // CodeIndexService.deleteIndex：11 个实体逐个分页删除，无 NopCodeShardLedger
  deleteEntitiesPaged(session, NopCodeSemanticEdge.class, "indexId", indexId);
  deleteEntitiesPaged(session, NopCodeGraphMetric.class, "indexId", indexId);
  incrementalAffectedFilesMap.remove(indexId);
  daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
  ```
  ```java
  // ShardBuildCoordinator.completeShard：ledger 行存在即放行
  NopCodeShardLedger row = (NopCodeShardLedger) session.get(..., ledgerRowId(indexId, shardId));
  if (row == null) { throw new IllegalArgumentException("unknown shardId ..."); }
  ...
  if (allCompleted) { index.setStatus(STATUS_COMPLETED); metricMaterializer.materialize(indexId); }
  ```
- **严重程度**: P2
- **并发场景**: 单线程调用链（deleteIndex → 后续 recreate → completeShard），非竞态；F8"清理不对称"。
- **触发时序**: T1: beginShardBuild(X, [s1,s2]) → s1 完成 → deleteIndex(X)（**ledger 两行残留**，index 实体已删）→ 重建同名索引 X → 未 beginShardBuild 直接 completeShard(X, s1)（新构建的第一个分片完成回调）→ 旧 ledger 行命中（非 null，守卫失效）→ loadLedger 读回旧行全为 COMPLETED → allCompleted=true → **新索引在仅有 1 个分片数据时被置 COMPLETED 并物化全局度量**。
- **现状**: ledger 的唯一清理点是 beginShardBuild 开头的 deleteLedger；deleteIndex 与 deleteEntitiesPaged 清单（L900-910）均未覆盖 NopCodeShardLedger。
- **风险**: 孤儿 ledger 行不仅持久化泄漏（F4），更把"必须 begin 后才能 complete"的设计守卫击穿，产生状态违约（未构建完即发布）。全仓 grep 确认 `NopCodeShardLedger` 在 main 代码中仅 ShardBuildCoordinator 引用。
- **建议**: deleteIndex 清单补 `deleteEntitiesPaged(session, NopCodeShardLedger.class, "indexId", indexId)`；或 completeShard 校验 index 实体 status==BUILDING 才接受登记。
- **失效面枚举**: ① `ShardBuildCoordinator.beginShardBuild`（唯一 ledger 写入方）；② `CodeIndexService.deleteIndex`（漏删点）；③ `NopCodeShardLedgerBizModel`（CrudBizModel 通用 delete 可手工清理，非程序化路径）；无其他 ledger 写入/删除兄弟点。
- **信心水平**: 确定
- **误报排除**: 不是"ORM cascadeDelete 已覆盖"——本模块批量删除路径明确绕过 ORM 关系导航（deleteFileRecords 注释 WP-4 AR-30/66 自证此模式），ledger 与 index 亦无 ORM 关系声明。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P2，裁定为 ledger 清理缺陷主登记（C7-006(c) 为别名），与 C1-003 同族不并单，见文末「维度复核结论」）

### [C6-004] findSymbolIdsByFileId 单页截断于 10000，超大文件删除时符号关联行清理不完整留下孤儿行

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2461-2469`（调用方 `2380-2433`，上限常量 `L114`）
- **证据片段**:
  ```java
  private List<String> findSymbolIdsByFileId(String fileId) {
      IEntityDao<NopCodeSymbol> dao = daoProvider.daoFor(NopCodeSymbol.class);
      QueryBean q = new QueryBean();
      q.addFilter(FilterBeans.eq("fileId", fileId));
      q.setLimit(MAX_QUERY_RESULTS);              // 10000，无 offset 分页循环
      return dao.findAllByQuery(q).stream()...
  ```
- **严重程度**: P2
- **并发场景**: 无并发，线性删除路径；F8"清理不对称"（其余 12 类关联行均分页清空，唯独 symbolIds 清单本身被截断）。
- **触发时序**: 线性时序：indexFile/增量/批量删除一个含 >10000 符号的文件（机器生成代码/打包产物可达到）→ findSymbolIdsByFileId 返回前 10000 个 → L2407-2421 的 annotationUsage/inheritance/semanticEdge/flowMembership/跨文件 call/usage 六类按 symbolIds 过滤的删除对剩余符号不执行 → 旧 NopCodeSymbol 行已删、关联行残留 → 跨文件 dangling 引用（正是 L2412-2421 注释声称要消灭的形态）。
- **现状**: 同文件中所有 paged 删除（deleteEntitiesPaged/deleteRelationalBySymbolIds）都特意"分页到穷尽"，此处是唯一单页截断点；注释（L2412-2417）声明的 WP-4 不变量在其自身前置条件上不成立。
- **风险**: 数据孤儿随删除累积；被引用方查询（findReferencedBy、图分析）读到指向已消失符号的边。同方法还被 `computeAffectedFiles`（种子集合截断→受影响面静默偏小）与 `removeStaleSymbolDocsForFile`（搜索文档清旧截断→ghost docs）复用，影响面放大到增量失效与搜索清理两条链。
- **建议**: 改为 offset 分页循环收集全部 symbolIds（对齐本类 deleteEntitiesPaged 惯例），或按 fileId 直接过滤删除六类关联行而绕过 symbolIds 清单。
- **失效面枚举**: `findSymbolIdsByFileId` 全部 3 个调用点：① deleteFileRecords L2387（本条主体）；② removeStaleSymbolDocsForFile L2473（搜索残留）；③ computeAffectedFiles L2297 附近（受影响面截断）。三处同根因，一并立案。
- **信心水平**: 很可能（>10k 符号单文件是边界条件，未在常见仓库出现）
- **误报排除**: 不是"有界声明"——MAX_QUERY_RESULTS 是通用查询上限而非清理语义声明；本模块其他清理路径均显式分页穷尽并写注释强调"no silent truncation"（OrmFingerprintStore.deleteByIndex L157-158），本点是同类标准下的例外。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P2，见文末「维度复核结论」）

### [C6-005] 搜索引擎与 DB 事务双写无原子性，且 add/remove 失败一律 warn-only——rollback 产生 ghost docs、删除路径残留不可回收

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:1786-1818`（addDoc 于事务内）、`2389-2395`（removeDocs warn-only）、`890-896`（removeTopic warn-only）
- **证据片段**:
  ```java
  // saveFileResultInSession：DB 实体尚未提交，先写搜索引擎
  try {
      searchEngine.addDoc(topic, doc);            // 事务提交前执行，非事务资源
  } catch (Exception e) {
      LOG.warn("Failed to sync symbol {} to search engine", sym.getId(), e);  // 吞掉
  }
  ```
- **严重程度**: P2
- **并发场景**: 单请求线程；DB 事务与外部搜索引擎两个独立资源管理器，无 2PC。
- **触发时序**: 方向一（ghost）：巨事务 persistInSession 写入数万实体 → 逐符号 addDoc 成功 → 事务后段任一异常（DB 故障/约束/超时）→ rollback → DB 无这些符号而搜索引擎有 → `searchViaEngine` 返回 filePath 为空串的命中（L120 `hit.getPath() != null ? ... : ""`）→ ghost 结果持续直到同文件被重新索引。方向二（残留）：deleteFileRecords L2391 removeDocs 失败仅 warn → 已删符号的搜索文档永久残留；deleteIndex L892 removeTopic 失败同 理，整个 topic 孤儿。
- **现状**: 注释（L1713-1716 WP-7）只解决了"重索引同 ID 先删后写"的顺序问题，未覆盖 rollback 方向与失败补偿。
- **风险**: 搜索结果与索引事实背离（对外可见错误数据）；无对账/重建 topic 的程序化路径（全量重索引只恢复被重索引的文件）。
- **建议**: 搜索写入移到事务提交后（`txn().afterCommit` 或提交点回调），失败入重试队列；或提供按 indexId 全量重建 topic 的对账命令。参照平台 `ITransactionTemplate` 的 afterCommit 钩子。
- **失效面枚举**: 搜索引擎调用全 4 点（grep `searchEngine\.` 双口径核验）：① addDoc L1813（事务内、warn-only）；② removeDocs L2391（warn-only）；③ removeDocs L2477（warn-only，removeStaleSymbolDocsForFile）；④ removeTopic L892（warn-only）。四点失败语义一致地弱，均属同族。
- **信心水平**: 确定（双写顺序与吞异常均为代码直接事实；rollback 场景链具体）
- **误报排除**: 不是 F9"吞异常"误报的过度立案——此处吞掉的不是一个可自愈的旁路操作，而是跨资源一致性的唯一写入机会；也不违反"内部实现可用模块异常"的错误处理口径，问题在补偿语义缺失而非日志级别。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：缺陷保留；同站点同缺陷让位 C7-005 主登记，本条关闭为别名，见文末「维度复核结论」）

### [C6-006] ProjectAnalyzer 预留 ExecutorService 字段无所有权与关闭契约

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:58-78`
- **证据片段**:
  ```java
  /**
   * Reserved for future parallel analysis. Currently unused.
   */
  private final ExecutorService executor;
  ...
  public ProjectAnalyzer(LanguageAdapterRegistry registry, ExecutorService executor, int batchSize) {
      this.registry = registry;
      this.executor = executor;
  ```
- **严重程度**: P3
- **并发场景**: 无当前并发（字段未使用）；未来接入并行分析时的生命周期归属缺口。
- **触发时序**: 理论风险，未具体化（当前无执行路径触及该字段）。
- **现状**: 字段仅存储、从不读取、从不 shutdown；生产唯一构造点 `CodeIndexService.setRegistry` L265 使用单参构造（executor=null），带 executor 构造器无 main 调用点（grep 核验）。关闭责任未在任何注释/接口中声明（调用方持有？ProjectAnalyzer 持有？）。
- **风险**: 未来并行化接入时，若默认"持有方负责关闭"的认知缺失，将产生线程池永不关闭的长驻泄漏；现阶段无行为影响。
- **建议**: 接入并行分析时明确所有权（建议：executor 由 IoC 容器管理生命周期，ProjectAnalyzer 不 shutdown，注释写明"调用方持有"）；或删除预留字段待需要时再加。
- **失效面枚举**: `ProjectAnalyzer` 三个构造器（L64/70/74）均不走关闭路径；全仓 main 代码 `new ProjectAnalyzer(` 仅 CodeIndexService L265 一处（null 参）。无兄弟泄漏点。
- **信心水平**: 确定（字段事实）/ 风险为推测
- **误报排除**: 不是"主路径资源泄漏"（恒 null，无资源可泄）；按 P3 契约缺口立案而非缺陷。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C6-007] LlmEdgeCache 无界进程生命周期缓存（F4 无界账本族）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/semantic/LlmEdgeCache.java:13-23`
- **证据片段**:
  ```java
  /**
   * N7.1 LLM 结果缓存：SHA256(prompt) 为键，ConcurrentHashMap 存储。
   * v1 纯内存（进程生命周期），缓存持久化 deferred。
   */
  public class LlmEdgeCache {
      private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
  ```
- **严重程度**: P3
- **并发场景**: 多请求线程并发调用 `computeIfAbsent`（原语本身安全）；风险是跨请求的单调增长。
- **触发时序**: 长时运行线性时序：每次语义边提取按 (symbolA, symbolB, 内容) 生成 prompt → sha256 键 → 新键永远追加 → 无淘汰/无 TTL/无容量 → 条目数随索引内容变化单调增长（内容变→prompt 变→旧键不失效也不复用）。
- **现状**: 类注释自我声明"v1 纯内存（进程生命周期）"；但全仓 main 代码无任何构造点与 beans 装配（仅测试 4 处 `new LlmEdgeCache()`），LlmSemanticEdgeExtractor 同样未接线——生产当前不可达。
- **风险**: 一旦接线（其设计目标），长时运行服务内存无界增长，与同模块其他缓存全部有界（CodeCacheManager 20 上界、FlowDetector 20 上界、incrementalStatusMap 100 上界）的口径不一致。
- **建议**: 接线前套用 `io.nop.commons.cache.ICache`/CacheConfig（容量+淘汰），或至少加 MAX_ENTRIES LRU 上界。
- **失效面枚举**: 本模块 4 个进程级容器对照：CodeCacheManager（有界 20 ✅）、FlowDetector 双缓存（有界 20 ✅）、incrementalStatusMap（有界 100 ✅）、LlmEdgeCache（无界 ❌——唯一例外）。
- **信心水平**: 确定（无界事实）；风险以"未来接线"为前提
- **误报排除**: 不是"有界声明存在的缓存"误报——注释只是陈述现状，未声明安全包络；亦未在 owner doc（docs-for-ai/03-modules/nop-code.md）声明为边界。dormant 故降 P3 而非 P2。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C6-008] ManifestStore 非原子覆盖写，损坏后静默退化为全量重建

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/ManifestStore.java:21-25`
- **证据片段**:
  ```java
  public void save(String manifestPath, List<FileFingerprint> fingerprints) {
      String json = JsonTool.stringify(fingerprints);
      IResource resource = VirtualFileSystem.instance().getResource(manifestPath);
      resource.writeText(json, null);        // 直接覆盖目标，无 temp+rename
  }
  ```
- **严重程度**: P3
- **并发场景**: 单线程；无并发写保护（两调用方并发 save 同一 manifest 会交错损坏，但当前无生产调用方）。
- **触发时序**: 线性时序：save 写一半进程被 kill/磁盘满 → manifest 截断 → 下次 load 解析失败 → catch 返回空列表（L43-46）→ 全部文件判为 added → 全量重分析+全量覆盖保存（自愈，代价为一次全量索引）。
- **现状**: 无原子写屏障（对照 F6）；失败后果是性能损失而非数据损坏，且 load 端 catch 保证了不崩溃。
- **风险**: 低——仅冗余工作量；该路径在生产入口已被绕过（`triggerIncrementalIndex` 注释 L782-784 明言 manifestPath deprecated，改走 OrmFingerprintStore；Orm 存储的 save 在事务内，回滚安全）。
- **建议**: 若保留 manifest 路径，写临时文件后 `Files.move(ATOMIC_MOVE)`；否则标记 @Deprecated 防止新调用方接入。
- **失效面枚举**: 指纹持久化三点对照：① OrmFingerprintStore（事务内，回滚安全 ✅）；② ManifestStore（非原子 ❌ 本条）；③ InMemoryFingerprintStore（内存，N/A）。无其他兄弟写点。
- **信心水平**: 确定
- **误报排除**: 不是"落盘无持久化屏障"F6 的 P1 立案对象——manifest 不声明 durable 语义，损坏后果有界且自愈；生产已弃用该路径。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3（一处引用勘误见复核结论），见文末「维度复核结论」）

### [C6-009] triggerFullIndex 失败不记录状态，getIncrementalStatus 返回上一次成功运行的状态（与增量路径失败记录不对称）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:56-79`（对照 `81-113`）
- **证据片段**:
  ```java
  public String triggerFullIndex(...) {
      int fileCount = codeIndexService.indexDirectory(indexId, projectPath, null);  // 抛异常直接穿透
      ...
      incrementalStatusMap.put(indexId, status);   // 只有成功才写
  ```
- **严重程度**: P3
- **并发场景**: 单请求；状态注册表的写路径不对称（F9 变体：失败对状态机不可见）。
- **触发时序**: 线性时序：某次 triggerIncrementalIndex 成功 → status{completed=true} → 其后 triggerFullIndex 中途失败抛出 → 无失败状态写入 → 运维调 getIncrementalStatus 看到 completed=true（旧增量）→ 误判全量索引健康。
- **现状**: triggerIncrementalIndex 有 catch→写失败状态→rethrow 的完整记录；triggerFullIndex 与 triggerRebuildFromCommit 均只有成功写。
- **风险**: 状态可观测性失真（无数据损坏）；一次失败的批量重建在监控面上不可区分于未发生。
- **建议**: 对齐增量路径：catch→写 completed=false+errorMessage→rethrow。
- **失效面枚举**: incrementalStatusMap 的 4 个写入口：① triggerFullIndex（仅成功 ❌）；② triggerIncrementalIndex（成功+失败 ✅）；③ triggerRebuildFromCommit（仅成功 ❌——但其失败由 GraphQL 错误直接可见，且无旧成功态覆盖场景少）；④ deleteIndex remove（✅）。同族两处缺口，本条立案较重的一处。
- **信心水平**: 确定
- **误报排除**: 不是"静默吞异常"（异常确实向上抛出，GraphQL 层可见）——立案点是状态注册表的清理/记录不对称，不是异常消失。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，裁定为状态观测缺口主登记并吸并 C7-011，见文末「维度复核结论」）

### [C6-010] ProjectAnalyzer.analyzeIncremental(store 路径) 保存指纹不清理已删除文件的指纹行——IFingerprintStore.deleteByPaths 全仓零调用

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:491-524`
- **证据片段**:
  ```java
  ChangeSet changes = detector.detectResourceChanges(previousFingerprints, sourceResources);
  ...
  List<String> changedFiles = changes.getAddedAndModified();   // deletedFiles 被计算
  ProjectAnalysisResult result = analyzeFiles(projectRoot, changedFiles);
  List<FileFingerprint> newFingerprints = detector.computeResourceFingerprints(sourceResources);
  fingerprintStore.saveFingerprints(indexId, newFingerprints);  // 只 upsert，不删 deletedFiles 行
  ```
- **严重程度**: P3
- **并发场景**: 无并发，孤儿积累为线性时序：文件删除 → detectResourceChanges 报告 deletedFiles → 保存的新指纹不含该文件但存储旧行仍在（OrmFingerprintStore.saveFingerprints 为纯 upsert）→ 孤儿指纹行永久残留并继续出现在下次 previousFingerprints 中。
- **现状**: `IFingerprintStore.deleteByPaths` 接口存在、两实现均实现，但全仓 main 代码零调用（grep 核验）；生产路径（CodeIndexService.triggerIncrementalIndex）通过 deleteFileRecords L1179 删除 NopCodeFile 行完成了等价配对，故该孤儿仅存在于本 API 被直接使用时（当前仅测试引用）。
- **风险**: 公共 API 契约陷阱：调用方按直觉使用 analyzeIncremental(store) 会静默积累孤儿指纹，增量检测的 deletedFiles 集合永久含幽灵路径。
- **建议**: 在 saveFingerprints 前调用 `fingerprintStore.deleteByPaths(indexId, changes.getDeletedFiles())`；或在该方法 javadoc 显式声明"调用方负责清理已删除文件指纹"。
- **失效面枚举**: analyzeIncremental 三个重载对照：① (projectRoot, previousResult) 纯内存 ✅；② (projectRoot, indexId, store) ❌ 本条；③ (projectRoot, manifestPath) 全量覆盖写 manifest，删除自然消失 ✅。生产入口 triggerIncrementalIndex ✅ 已配对。
- **信心水平**: 确定（配对缺失为直接代码事实）
- **误报排除**: 不是"生产主路径缺陷"——生产路径已配对，故 P3；也不是误读 saveFingerprints 语义（OrmFingerprintStore L40-79 证实为纯 upsert 无删除分支）。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

---

## 三、维度集外强信号线索（CL-2xx，供对应维度子代理独立核实）

### [CL-201] deleteIndex 持锁期间从 indexLocks 移除锁对象——锁映射移除竞态可致同 indexId 双临界区（C1/F10 域）
`CodeIndexService.java:917` 在 `withIndexLock` 回调内执行 `indexLocks.remove(indexId)`，而 `withIndexLock` L126 用 `computeIfAbsent` 取锁：T1 持锁 L1 并 remove 映射项 → T2 computeIfAbsent 新建 L2 并 lock 成功 → T1 尚未 unlock → 同一 indexId 两个临界区并发执行（deleteIndex 与 rebuild 并发破坏互斥）。证据充分、时序具体，属 C1 立案对象，本维度不展开。

### [CL-202] CodeCacheManager.rebuildDependencies 无容量上界（C5/F7 域）
`CodeCacheManager.java:254-270` 分页加载全部 NopCodeDependency 无 MAX 检查，对照同类 `MAX_CACHE_SYMBOLS`(100k)/`MAX_CACHE_EDGES`(500k) 截断守卫（L210/L241 均有 + setTruncated）；20 个缓存条目 × 无界依赖列表 = 声明边界不一致，C5 维度应核。

### [CL-203] GraphMetricStore.findByMetric 无 limit 全量加载（C5 域）
`GraphMetricStore.java:158-165` 每次查询加载该 metricType 全部行（COMMUNITY 可达节点数级），请求级物化无分页；C5 维度应核（非泄漏：结果不驻留）。

### [CL-204] ProjectAnalyzer.MAX_FILE_COUNT/MAX_FILE_SIZE_BYTES 常量声明未生效（C5 域）
`ProjectAnalyzer.java:54-55` 声明 50000 文件/1MB 文件上界常量，但两个 analyzeProject 循环均未引用——声明的规模包络与实现不符（分析全量物化 fileResults），C5 维度应核是否按"声明包络"立案。

### [CL-205] getFingerprintStore() 惰性初始化为非同步 check-then-act（C1 域）
`CodeIndexService.java:248-253` protected 可变字段无同步惰性创建，多请求并发可双创建 OrmFingerprintStore（后果良性：两个等价实例），C1 维度裁定是否登记。

### [CL-206] FlowDetector 双缓存独立驱逐导致同 indexId 缓存视图不一致（C1 域）
`FlowDetector.java:141-143` flowCache 与 symbolFilePathCache 各自 `evictOverflow`（L506-512 移除任意首键）：同一 indexId 可能从 flowCache 被逐、从 symbolFilePathCache 留存（或相反），`getAffectedFlows` 退化为启发式路径映射。后果为降级非损坏，C1 维度裁定。

### [CL-207] rebuildDebounceMap 与 deleteIndex 不联动 + debounce check-then-put 非原子（C7/C1 域）
`CodeIndexService.java:159-160` 的 debounce 表在 deleteIndex（L911 仅清 incrementalAffectedFilesMap）不清除：删除后 30s 内同 ID 重建会被误 debounce（L759-765）；且 L759 get→L789 put 两步非原子，并发触发可双双通过。影响为功能语义（防抖误伤）而非资源，C7/C1 维度应核。

---

## 四、小结

- 资源获取点配对盘点 19 类（见配对表），其中 13 类配对完好、6 类存在不对称（#2/#6/#11/#12/#13/#18）。
- 立案 10 条：P2×5（C6-001~005）、P3×5（C6-006~010）；无 P0/P1（已知的 CL-101 git 子进程方向经独立核实成立为读超时缺口，但触发需病理环境且主流程有 destroy，定 P2）。
- CL 线索 7 条（CL-201~207），均附 file:line 证据。
- 本报告为盲审初审输出，全部条目待独立复核。

---

# C6 深挖第 2 轮

- **轮次焦点**: C6-001 同族放大（git 子进程之外的外部进程/网络调用超时面，穷举第 5 处）+ C6-002/C6-003 同族（半工作区/孤儿行清理面第 3 处排查）
- **盲审声明**: 本轮仅追加，不修改第 1 轮内容；未读取本维度集外其他维度文件及 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/。未修改任何产品代码。
- **日期**: 2026-10-22（审计执行）/ 基线 commit 7b0ccc89d4

## 一、超时面穷举清扫表（C6-001 同族，第 5 处排查）

外部进程/网络调用点全单元 grep 口径：`ProcessBuilder|Runtime\.getRuntime|\.exec\(|HttpClient|URLConnection|openConnection|openStream|Socket|chatService\.` （nop-code-{core,flow,service,api} main 代码）。命中 5 处，逐一登记：

| # | 站点 | 类型 | 超时状态 | 判定 |
|---|---|---|---|---|
| 1-4 | CodeIndexService.gitOutput:848 / ChangeAnalyzer.parseGitDiff:146 / ClusterWorkspaceManager.gitWithCredential:133 / gitOutput:178 | git 子进程 | 读阶段无超时（C6-001/C7-003 已立案） | 第 1 轮已立 |
| 5 | LlmSemanticEdgeExtractor.extract:74 `chatService.call(request, null)` | 网络 RPC（IChatService） | 组件内无超时 + null cancelToken | **本轮立案 C6-R2-001** |
| — | CodeSearchService → LuceneSearchEngine（io.nop.search-lucene） | 进程内 Lucene，无网络 | 不适用 | 非超时面 |
| — | ORM/JDBC 全部 DB 访问 | 平台托管连接池 | 域外（nop-dao） | 不在本单元重审 |

阻塞等待原语补查（`.get()|.join(|await(|syncGet|CountDownLatch`）：CodeIndexService:129 `action.get()` 为 withIndexLock 闭包直调（非 future），无其他无超时等待点。**超时面穷举至此闭合：4 个活站点 + 1 个双重休眠 RPC 站点，无第 6 处。**

## 二、清理面清扫表（C6-002/C6-003 同族，第 3 处排查）

| 面 | 清单对照 | 结论 |
|---|---|---|
| 实体行孤儿 | 全单元 13 个实体 × deleteIndex 清理清单（CodeIndexService:900-913）逐一对表：Usage/FlowMembership/Flow/AnnotationUsage/Inheritance/Call/Symbol/File/Dependency/SemanticEdge/GraphMetric/Index 全覆盖；**唯一缺席 = NopCodeShardLedger（= C6-003）** | 无第 3 处，闭合 |
| 磁盘残留 | 全单元 `Files.write|writeText|createTemp*` 命中仅 ManifestStore:24（= C6-008，legacy 弃用路径）+ ClusterWorkspaceManager git clone/fetch/checkout 落盘（= C6-002）；无临时文件创建点 | 无第 3 处，闭合 |
| 内存 map 清理 | incrementalAffectedFilesMap（deleteIndex:911 ✅）、indexLocks（:917 ✅，移除竞态=CL-201/301）、rebuildDebounceMap（漏清 = C7-008）、repoLocks（ClusterWorkspaceManager 只增不删，每 repo 1 把锁，有界 watch-only）、incrementalStatusMap（BizModel:155 ✅） | 无新缺口，闭合 |

**清理面第 3 处：不存在。本轮对该子问题终止。**

## 三、发现条目

### [C6-R2-001] LlmSemanticEdgeExtractor 的 LLM RPC 无组件级超时且取消令牌恒为 null——单元内唯一网络调用面的超时完全外置（组件双重休眠）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/semantic/LlmSemanticEdgeExtractor.java:64-76`（配合 `LlmEdgeCache.java:16-19`、`CodeIndexService.java:285-291`、`nop-code/nop-code-core/src/main/java/io/nop/code/core/analyzer/ProjectAnalyzer.java:289-293`）
- **证据片段**:
  ```java
  // LlmSemanticEdgeExtractor.java:71-76 —— 网络调用无超时包装、cancelToken=null
  String responseText = cache.getOrCompute(prompt, () -> {
      ChatRequest request = new ChatRequest();
      request.withUserPrompt(prompt);
      ChatResponse response = chatService.call(request, null);   // 同步 RPC，无超时、无取消
      return response != null ? response.outputText() : null;
  });
  ```
  ```java
  // CodeIndexService.java:285-291 —— 发现机制不注册 LLM 提取器（休眠第一层）
  private List<ISemanticEdgeExtractor> discoverSemanticExtractors() {
      List<ISemanticEdgeExtractor> extractors = new ArrayList<>();
      extractors.add(new NameSimilarityExtractor());
      extractors.add(new DocKeywordExtractor());
      extractors.add(new AnnotationPatternExtractor());   // 无 LlmSemanticEdgeExtractor
  ```
  ```java
  // ProjectAnalyzer.java:290 —— requiresLlm 过滤器（休眠第二层：即使注册也不会执行）
  for (ISemanticEdgeExtractor extractor : semanticExtractors) {
      if (!extractor.requiresLlm()) { ... extractor.extract(...) }
  ```
- **严重程度**: P3
- **并发场景**: 索引构建线程（indexFile/triggerIncrementalIndex/indexDirectory 的分析管线）与 LLM 服务端；单线程阻塞形态（F5 挂死候选），无多线程交错。
- **触发时序**: 线性时序（以接线后为前提）：extract 循环对每对候选符号调用 `chatService.call(request, null)` → `IChatService.call` 默认实现为 `FutureHelper.syncGet(callAsync)`（无超时重载）→ 若注入的实现内部无读超时（接口契约不要求），LLM 端点挂起即索引构建线程永久阻塞；`LlmEdgeBudget` 只约束调用次数（tryConsume）不约束单次时长，一个挂起调用即冻结整个 extract。null cancelToken 使上层即使有取消语义也无法传导。
- **现状**: 消费站点无超时、无取消；超时责任完全外置于 IChatService 实现。平台参考实现 ChatServiceImpl 有 HTTP 读超时（`CFG_AI_SERVICE_READ_TIMEOUT`，nop-ai-core:272），但 IChatService 接口契约（nop-ai-api `IChatService.java`）未声明超时保证——注入任何无超时实现即破防。
- **风险**: 组件当前双重休眠（未注册 + requiresLlm 过滤），零生产影响；接线激活后一次 LLM 端点异常即冻结索引构建管线（与 C6-001 git 挂死同构，F5"超时未作用于所有等待点"的网络版）。
- **建议**: 接线门禁三选一：(a) extract 内用 `FutureHelper.waitResult(future, timeoutMs)` 替代裸 `call`，超时配置化；(b) 传入 ICancelToken 并关联索引构建的取消令牌；(c) 在 IChatService 契约 javadoc 显式声明"实现必须自带读超时"，并在装配校验。同时把本条与 C6-001 的 git 工具封装合并为"外部调用统一超时执行器"。
- **失效面枚举**: 全单元外部调用 5 站点：4 个 git 子进程站点（C6-001 已立，finally destroy 覆盖度各异）+ 本站点（唯一网络面、唯一无任何 destroy/超时兜底的站点）；进程内 Lucene 搜索（非网络）与平台托管 JDBC 不属超时面。同族对照：本站点是 5 站点中唯一"无超时且无进程可杀"的形态——进程模型有 destroyForcibly 兜底，RPC 模型连兜底都没有。
- **信心水平**: 确定（调用链与休眠隔离为 live code 直读；"实现可能无超时"以契约为准，参考实现有超时标注为很可能）
- **误报排除**: 不是"依赖实现自带超时即可"的过度立案——第 1 轮 C6 口径下 git 站点立案理由是"超时常量存在但未作用于等待点"；本站点同构（预算/缓存设施齐备，唯独无时长兜底），且 IChatService 契约确实未声明超时。不按 P2 立案因组件双重休眠、零当前影响。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

## 四、本轮小结

- 新发现 1 条：C6-R2-001（P3，组件休眠，接线门禁）。
- 超时面穷举（C6-001 同族）：**闭合**——4 git 站点 + 1 dormant RPC 站点，无第 6 处。
- 清理面第 3 处排查（C6-002/C6-003 同族）：**闭合，不存在**——13 实体对表、磁盘写入面、内存 map 面三口径清扫完毕。
- 除上述两点外本轮未发现其他 C6 域新问题；C6 维度深挖第 2 轮的新增面已清扫完毕，**若无新输入，本轮终止**。

---

# C6 维度复核结论（独立复核）

- **日期**: 2026-10-23（CRA WI 6.3 Phase 4，C6+C7 维度独立复核子代理）
- **复核方法**: 逐条重读 live 源码 file:line（基线 @ 7b0ccc89d4，与初审同版无漂移）；专项裁定对照 5.6 merge-adjudication（`ai-dev/audits/2026-10/2026-10-22-1300-deep-audit-nop-graph-duckdb-concurrency/merge-adjudication.md`）与同目录 C1 复核结论。派单列举 C6-001~005/R2-001，实际超集复核本文件全部 11 条（C6-001~010 + R2-001）。不修改初审既有内容，仅回填各条复核状态行并追加本节。

## 逐条裁定

### C6-001（P2）→ 保留，维持 P2；裁定为本单元 git 超时族主登记

- 证据全部重演属实：gitOutput L846-874 读循环 L855-857 先于 waitFor L859（30s 常量 L812），catch L868-873 无 destroy；`ProcessBuilder`/`pb.start()` 双口径各 4 处复核一致（CodeIndexService:853、ChangeAnalyzer:152、ClusterWorkspaceManager:145/187）；ChangeAnalyzer finally destroyForcibly L226-228 与另三站无 finally 的不对称成立；ChangeAnalyzer 经 `app-service.beans.xml` L38-39 装配（活站点）核实。
- **git 超时族主登记裁定（任务指定）**：C6-001 与 C7-003 为**同站点同缺陷**（waitFor 超时位于读循环之后，超时不作用于管道读取等待点——同一结构性根因、同一 4 站点失效面、同为 P2）。建议 **C6-001 为主登记**：其修复建议（统一 git 进程执行工具：读取循环超时看护 + 全站点 finally destroyForcibly/waitFor 收尸）为两案修复的超集，且额外含 CodeIndexService/ClusterWorkspaceManager 异常路径进程对象泄漏子面；C7-003 关闭为同站点别名，其独有后果面（ClusterWorkspaceManager 持 repoLock 级联挂死、无 stall 可见性）并入主登记风险段与验收检查单；C5 维 CL-101（派单日志已列同族）一并归并。单工单闭合防双计数。

### C6-002（P2）→ 保留，维持 P2

- 全类逐行复读证实：clone 直接写入最终目录 L86-87；复用分支仅判 `.git` 目录存在 L65 + `rev-parse HEAD` L66（半克隆残留时 rev-parse 非零退出 → IllegalStateException L194-196，永久毒化成立）；全类无任何 delete/淘汰/TTL 路径；wiring 复核：仅 2 个测试类引用、无 beans 装配，dormant 判定成立。

### C6-003（P2）→ 保留，维持 P2；裁定为 ledger 清理缺陷主登记（C7-006(c) 为别名）

- deleteIndex 清理清单 L900-910 逐行对表：11 实体无 NopCodeShardLedger；ledger 唯一程序化清理点 = beginShardBuild 的 deleteLedger（L59）——漏删成立。completeShard 守卫仅 `session.get` null 判（L81-85），孤儿行命中后走 finalizeIfAllCompleted L97-114 置 COMPLETED + materialize（L111）——击穿链成立。触发时序需 index 实体先行重建（L104 null 守卫），初审场景已含该步，成立。
- **与 C1-003 的同族边界（任务指定）**：两案同属 ShardBuildCoordinator 发布语义违反族、同为未接线组件，但失效层不同——C1-003 = 同生命周期内并发 completeShard 的跨事务 check-then-act（主失效模式=发布丢失，竞态缺陷，修复=条件 UPDATE 发布权裁决）；C6-003 = 跨生命周期清理不对称击穿 unknown-shardId 守卫（提前发布，单线程时序，修复=deleteIndex 清单补删 + 可选 status==BUILDING 校验）。**裁定：同族相邻、互不吸收、独立立案**；两者可并列挂接同一「接线前置」工单，修复互不覆盖。
- **C7-006(c) 与本条同站点同缺陷**：主登记 = C6-003（聚焦条目），C7-006 保留 (a)(b) 子项并将 (c) 标注别名交叉引用。

### C6-004（P2）→ 保留，维持 P2

- findSymbolIdsByFileId L2461-2469 `setLimit(MAX_QUERY_RESULTS=10000)` 无 offset 循环属实（常量 L114）；3 调用点核准（deleteFileRecords L2387 / removeStaleSymbolDocsForFile L2473 / computeAffectedFiles L2320）；对照本类 deleteEntitiesPaged 分页穷尽惯例（L921-937）与 OrmFingerprintStore.deleteByIndex "no silent truncation" 注释（L157-158），「同类标准下的例外」定性成立。

### C6-005（P2）→ 缺陷保留；同站点同缺陷让位 C7-005 主登记，本条关闭为别名

- 4 个 `searchEngine.` 调用点双口径复核一致（L892/L1813/L2391/L2477）；addDoc L1813 处于 runInTransaction 体内、warn-only L1814-1816；ghost 命中路径 `hit.getPath()` 空串回退（CodeSearchService L120）属实。
- **归并裁定**：与 C7-005 为同一缺陷（DB 事务内外部副作用无原子性 + 双向吞异常无补偿，F2）。C7-005 的事务边界时序分析更完整（removeDocs 先于 DB 删除、回滚双方向发散），建议 **C7-005 为主登记**；本条关闭为别名，其独有子注（removeTopic 失败 → 整 topic 孤儿、无程序化对账命令）并入主登记修复范围（afterCommit 移出 / topic 级对账二选一已含）。

### C6-006（P3）→ 保留，维持 P3

- executor 字段 L60-62（注释自证 Reserved/unused）、三构造器 L64/70/74、生产唯一构造点 CodeIndexService L265 均属实。一处表述勘误（不影响结论）：L265 走单参构造器、由构造器内置 `this.executor = null`（L66），非调用方「传 null 参」。契约缺口 P3 定性恰当。

### C6-007（P3）→ 保留，维持 P3

- 无界 ConcurrentHashMap（L14-23）与「进程生命周期」自declared 注释属实；wiring 复核：main 仅 LlmSemanticEdgeExtractor 字段引用 + 自身，测试 1 文件，无 beans——dormant 成立；与同模块三个有界容器（CodeCacheManager 20 / FlowDetector 20 / incrementalStatusMap 100）口径不一致的对照成立。与 C7-007 的分工注记：C7-007 立案预算语义，本条立案容量口径，互引不双计。

### C6-008（P3）→ 保留，维持 P3（一处引用勘误）

- save 非原子覆盖写 L23-25、load catch 返回空列表自愈（L43-46）属实；ManifestStore main 唯一使用点 = ProjectAnalyzer L541（manifestPath 重载，`analyzeIncremental` 全仓 main 调用 0 命中）——生产绕过成立。**勘误**：「manifestPath deprecated」注释实际位于 triggerRebuildFromCommit L782-784，非 triggerIncrementalIndex 内；deprecated 事实与结论不变。

### C6-009（P3）→ 保留，维持 P3；裁定为状态观测缺口主登记（C7-011 为别名）

- triggerFullIndex L56-79 仅成功写状态（L70-75）、indexDirectory L61 异常直接穿透；对照组 triggerIncrementalIndex catch→写失败→rethrow（L104-112）；triggerRebuildFromCommit 仅成功写（L197-202）；deleteIndex biz remove（L153-156）。4 写入口枚举完整属实。
- **归并裁定**：与 C7-011 同站点同缺陷；本条失效面为超集（含 triggerRebuildFromCommit 第三缺口），建议 **C6-009 为主登记**，C7-011 关闭为别名。

### C6-010（P3）→ 保留，维持 P3

- analyzeIncremental(store) L490-524：saveFingerprints（L523）前无 deleteByPaths 调用属实；deleteByPaths 全仓 main 零调用（仅接口 L22 + 两实现 + 测试）grep 核实；OrmFingerprintStore.saveFingerprints L40-79 纯 upsert（无列表外删除分支）直读证实；生产主路径（triggerIncrementalIndex L1179-1180 + L1212）已配对的自洽论证成立。

### C6-R2-001（P3）→ 保留，维持 P3

- `chatService.call(request, null)` L74 属实；IChatService.call 默认实现 = `FutureHelper.syncGet(callAsync)` 无超时重载（nop-ai-api IChatService.java L19-20 直读），接口契约无超时声明；参考实现 ChatServiceImpl 有读超时（nop-ai-core ChatServiceImpl L272 `CFG_AI_SERVICE_READ_TIMEOUT`）——与初审引用逐点吻合。双重休眠两道闸复核成立（discoverSemanticExtractors L285-291 无 LLM 提取器 + ProjectAnalyzer requiresLlm 过滤 L290-293）。接线门禁 P3 定性恰当。

## CL 线索裁定（CL-201~207）

| CL | 裁定 | 复核要点 |
|---|---|---|
| CL-201 | **确认** | withIndexLock computeIfAbsent（L125-143）+ deleteIndex 持锁内 remove（L917）属实，双临界区交错可写出；与 C7 维 CL-301 同站点同缺陷双登记，归 C1/C2 域单一立案 |
| CL-202 | **确认** | rebuildDependencies（L254-270）无 MAX 守卫，对照 L210/L241（100k/500k 截断）成立；与 CL-303 重复，C5 域 |
| CL-203 | **确认** | GraphMetricStore.findByMetric（graph/GraphMetricStore.java L159-168）无 setLimit 属实；C5 域（C5-001 已覆盖，防双计） |
| CL-204 | **确认** | MAX_FILE_SIZE_BYTES/MAX_FILE_COUNT（ProjectAnalyzer L54-55）声明后全文件零引用（private 可证）；C5 域（C5-002 已覆盖） |
| CL-205 | **确认** | getFingerprintStore（L248-253）无同步惰性创建；= C1-004 主登记（良性，C1 复核已裁定保留） |
| CL-206 | **确认** | detectFlows 双缓存各自 evictOverflow（L142-143，移除任意首键 L505-511）；invalidateCache 双清（L501-504）仅显式失效路径，不构成反驳 |
| CL-207 | **确认** | deleteIndex 清理漏 rebuildDebounceMap（L898-917）+ check-then-put 非原子（L758-765 vs L778/L789）；= C7-008 主登记（C1-005 覆盖非原子面） |

## 批次覆盖核验（三源码根痕迹）

- **nop-code-core**：ProjectAnalyzer（C6-006/C6-010/CL-204）、ManifestStore（C6-008）、IFingerprintStore/InMemoryFingerprintStore（配对表 #6/#7、C6-010）、IncrementalDetector（配对表 #1/#4）——痕迹在案。
- **nop-code-flow**：ChangeAnalyzer（C6-001 站点②、配对表 #2/#3）、FlowDetector（配对表 #9、CL-206）——痕迹在案。
- **nop-code-service**：impl 7 类（CodeIndexService 主战场/GraphMetricMaterializer/ShardBuildCoordinator/CodeCacheManager/CodeGraphService/CodeSearchService/CodeQueryService）+ cluster（ClusterWorkspaceManager C6-002）+ semantic（LlmEdgeCache/LlmSemanticEdgeExtractor C6-007/R2-001）+ entity（NopCodeIndexBizModel C6-009）+ incremental（OrmFingerprintStore C6-010）+ graph（GraphMetricStore CL-203）——痕迹在案。
- **单元计数核准**：core 51f/3,865 行 + flow 12f/1,840 行 + service 53f/11,193 行 = **116f/16,898 行**，与审计单元声明精确吻合；基线 `_tmp/cra-baseline-nop-code-index.txt`（2026-10-23T01:37:54Z @ 7b0ccc89d4）三 roots 声明一致。**覆盖核验通过。**

## 最终保留项

- **立案保留 11/11，无驳回、无降级、无升级**：C6-001（P2，git 超时族**主登记**，吸并 C7-003 别名）、C6-002（P2）、C6-003（P2，ledger 清理**主登记**，C7-006(c) 别名；与 C1-003 同族不并单）、C6-004（P2）、C6-005（P2，缺陷保留，**让位 C7-005 主登记**，本条为别名）、C6-006（P3）、C6-007（P3）、C6-008（P3，一处引用勘误）、C6-009（P3，状态观测**主登记**，吸并 C7-011 别名）、C6-010（P3）、C6-R2-001（P3）。
- **CL 确认 7/7**：CL-201~207 全部确认（CL-201=CL-301、CL-202=CL-303 双登记注记；CL-203/204/205/207 归属对应主登记条目）。
- 跨维归并总表（防双计数）：C6-001←C7-003；C6-005→C7-005（让位）；C6-009←C7-011；C6-003←C7-006(c)。
- 判级分布：P2×5 + P3×6（与初审一致；归并仅改登记归属，不改判级）。
