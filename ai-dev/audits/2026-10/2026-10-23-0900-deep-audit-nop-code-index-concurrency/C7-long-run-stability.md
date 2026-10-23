# C7 长时运行稳定性 — nop-code-index 全单元初审

- **审计单元**: nop-code-index（nop-code-core 51f + nop-code-flow 12f + nop-code-service 53f，共 116 文件 / 16,898 行）
- **维度**: C7 长时运行稳定性（连续运行天/周级不劣化：无单调增长、挂死可检测、失败可自愈）
- **批次**: 全单元单批·主战场（初审判定=线索，非最终事实；最终结论须经独立复核）
- **盲审声明**: 本初审未读取 ai-dev/audits/、ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/ 下任何历史记录；全部判定基于 live code（@ 7b0ccc89d4）。已知外部输入仅限任务派发面给出的跨单元裁定：5.6 W-04 = LeidenDetector 幽灵线程 P1（本体归 5.6 单元，本单元只审消费侧叠加面）。
- **CL 前缀**: 本维度集外线索以 **CL-301** 起。
- **基线**: `_tmp/cra-baseline-nop-code-index.txt`（长驻站点清单 15 站点 + C7 增量/fingerprint 218 命中）。
- **复核状态**: 全部条目已复核（2026-10-23 C6+C7 维度独立复核，逐条判定见文末「维度复核结论」）。

---

## 一、长时运行风险清单（路径 × 失效形态 × 判定）

### 1.1 单调增长源盘点（C7 步骤 1，对照 F4）

| # | 增长源 | 位置 | 清理机制 | 判定 |
|---|---|---|---|---|
| G1 | nop_code_file 指纹行 | OrmFingerprintStore（全表即指纹） | deleteFileRecords（删文件）/deleteByIndex（删索引）分页清空 | OK（已删文件指纹行随 deleteFileRecords 清理，见 2.4 附注依赖边残留） |
| G2 | nop_code_graph_metric 物化行 | GraphMetricMaterializer.replaceRows/deleteByIndex | 内容变更即 deleteByIndex；deleteIndex 清空；replaceRows 先删后插单事务 | OK |
| G3 | nop_code_shard_ledger 行 | ShardBuildCoordinator | 仅 beginShardBuild 重置；**deleteIndex 不清理**（C7-006） | 缺陷（孤儿行，组件未接线，P3） |
| G4 | rebuildDebounceMap | CodeIndexService:159 | 仅 resetRebuildDebounce（测试用）；**deleteIndex 不清理**（C7-008） | 缺陷（每 indexId 永驻 1 条目，P3） |
| G5 | indexLocks | CodeIndexService:123 | 仅 deleteIndex 移除，且移除存在互斥破坏竞态 | 移除本身是缺陷 → **CL-301**（C1/C2 域）；泄漏面每 indexId 1 把锁，可接受 |
| G6 | incrementalAffectedFilesMap | CodeIndexService:163 | 每次 actual-change 覆盖写；deleteIndex 移除 | OK（快照语义，非累积；单条目可达受影响面大小，驻留至下次增量，可接受） |
| G7 | LlmEdgeCache | LlmEdgeCache:14 | 无上限/无淘汰/无 TTL；注释自declared「进程生命周期」 | 已声明边界（组件未接线）→ 预算语义缺陷见 C7-007 |
| G8 | 集群工作区目录（每 repo×revision 一个完整 clone） | ClusterWorkspaceManager:64 | **无任何 retention/TTL/GC** | 缺陷（C7-002，磁盘形式 F4） |
| G9 | FlowDetector flowCache / symbolFilePathCache | FlowDetector:60-61 | evictOverflow 有界 20 + invalidateCache 挂接索引写路径 | OK |
| G10 | CodeCacheManager analysisCacheMap | CodeCacheManager:71 | MAX_CACHE_ENTRIES=20 LRU + TTL 1h | OK；但单条目内 dependencies 列表无上限 → **CL-303**（C5 域） |
| G11 | incrementalStatusMap | NopCodeIndexBizModel:44-50 | MAX_STATUS_ENTRIES=100 LRU | OK（但失败写入缺口见 C7-011） |
| G12 | 搜索引擎 topic + docs | CodeSearchService / saveFileResultInSession | topic 随 deleteIndex 移除；docs 随文件删除移除 | 机制存在，但副作用非事务化 → C7-005 |
| G13 | 已删文件的被依赖边（nop_code_dependency target 侧） | deleteFileRecords:2404-2406 只删 source 侧 | 无 | 缺陷（C7-004，长期增量单调累积幽灵边） |
| G14 | 每次增量的失败文件 warn 日志 | CodeIndexService:1125/1132/1147/1151 | 每文件每次 1-2 条，有界 | OK（永久失败文件的无限重试见 C7-001 风险联动） |

### 1.2 挂死点清单（C7 步骤 2，对照 F5）

| # | 挂死候选点 | 位置 | 超时/看门狗 | 判定 |
|---|---|---|---|---|
| H1 | git 子进程管道读取阶段 | CodeIndexService.gitOutput:854-861；ClusterWorkspaceManager:145-151/186-193；ChangeAnalyzer:154-157 | waitFor 超时（30s/60s/30s）**不覆盖 readLine/readAllBytes 阶段** | 缺陷（C7-003；ClusterWorkspaceManager 还持 repoLock 级联） |
| H2 | Leiden/Betweenness/PageRank 请求线程同步执行 | GraphMetricMaterializer.materialize:85-107（Leiden 60s 配置超时，执行语义归 5.6 W-04） | Betweenness 有 10000 节点守卫；Leiden 仅 <2 下界无上界 | 消费侧守卫缺口（C7-001） |
| H3 | 分片构建停滞（worker 崩溃后 ledger 永 PENDING） | ShardBuildCoordinator.beginShardBuild | 无租约/超时/watchdog；仅靠人工重begin | 缺陷（C7-006，未接线） |
| H4 | chatService.call（LLM 提取） | LlmSemanticEdgeExtractor:74 | 组件内无显式超时（依赖 IChatService 实现，域外） | 未接线，登记观察（并入 C7-007） |
| H5 | allExtensions VFS 深度遍历 / computeResourceFingerprints 全量哈希 | CodeIndexService:2278-2301；IncrementalDetector:96-102 | 同步一次性执行，规模=受监仓库大小 | 已声明路径（索引构建本身），不立案 |

### 1.3 重试 / 自愈 / 时钟（C7 步骤 3-5）

| # | 检查点 | 位置 | 判定 |
|---|---|---|---|
| R1 | 增量构建中途失败 | triggerIncrementalIndex 全程单事务（DB 面），analyze-before-delete（1110-1153）+ 失败文件保留旧指纹（1202-1209）→ 可重入 | OK（自愈设计良好） |
| R2 | triggerFullIndex 失败 | 单巨事务原子回滚，无半程状态；但失败不写 incrementalStatusMap（C7-011） | 缺陷（P3，观测面） |
| R3 | 读路径自愈物化重试 | CodeGraphService 4 处 self-heal，**无退避、无单飞** | 缺陷（C7-001，P1） |
| R4 | 永久失败文件重试 | 每次增量重试，无退避；后果=每次 actual-change 都 deleteByIndex 物化行 → 与 R3 叠加 | 联动面（并入 C7-001 风险） |
| R5 | 防抖 rebuildDebounce | 30s 窗口；失败不记时间戳（重试不被拦截）=正确；deleteIndex 不清残留（C7-008） | 部分缺陷（P3） |
| R6 | 时钟依赖 | 防抖用 System.currentTimeMillis（30s 窗口，回拨影响可忽略）；computedAt 仅展示 | OK，不立案 |
| R7 | 旧实例复活/fencing | 单进程索引锁语义，无跨进程 fencing（集群构建未接线） | 登记 C7-006 一并观察 |
| R8 | 保存语义一致性 | IFingerprintStore 两实现删除语义相反（C7-009） | 缺陷（P3） |
| R9 | batch 保存路径会话安全 | batchSaveFileRecords 无事务/会话包装，与 batchDeleteFileRecords 不对称（C7-010） | 缺陷（P3） |
| R10 | 搜索副作用回滚补偿 | 无补偿路径（C7-005） | 缺陷（P2） |

---

## 二、发现条目

### [C7-001] 读路径自愈物化无单飞守卫：并发读触发 Leiden 重算风暴，持久失败场景每请求全量重算

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeGraphService.java:98-123, 184-205, 332-340, 425-445`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/GraphMetricMaterializer.java:85-107, 270-295`
- **证据片段**:
  ```java
  // CodeGraphService.java:332-340（三个读路径共用同一触发器，无锁无标记）
  private void ensureMaterializedForGraphMetrics(String indexId) {
      if (metricStore.loadSummary(indexId) == null && metricMaterializer != null) {
          try {
              metricMaterializer.materialize(indexId);
          } catch (Exception e) {
              LOG.warn("Self-heal materialization failed for {}; surprise scoring degrades", indexId, e);
          }
      }
  }
  ```
  ```java
  // GraphMetricMaterializer.java:113-122（Leiden 仅 <2 下界，无节点上界；消费侧同步执行）
  private CommunityResult detectCommunities(CodeCallGraph graph, Set<String> nodes) {
      if (nodes.size() < 2) { ... return null; }
      LeidenConfig config = LeidenConfig.create()
              .setResolution(0.1).setMaxIterations(10).setTimeoutMs(60000);
      return LeidenDetector.detect(graph, nodes, config);
  }
  ```
  ```java
  // CodeGraphService.java:117-122（物化失败即降级内存重算，且下次读再触发物化）
  } catch (Exception e) {
      LOG.warn("Materialized community detection failed ...", indexId, e);
  }
  CommunityResult result = runCommunityDetection(callGraph);
  return convertCommunityResult(result, symbolTable);
  ```
- **严重程度**: P1
- **并发场景**: N 个 HTTP/GraphQL worker 线程同时访问同一未物化索引的读 action（getSurprisingConnections / getExplorationQuestions / exportGraphWiki / detectCommunities / getGraphAnalysis / getCriticalNodes），全部在请求线程上同步进入 `materialize()`。
- **触发时序**: 前置：任一 actual-change（增量/单文件/批量写入，CodeIndexService:1096-1098）已 deleteByIndex 物化行；或索引从未物化。T1..Tn 并发读 → 各自 `loadSummary==null` 判定通过（check-then-act，无互斥）→ 各自进入 `materialize()`：重建 100k 符号/500k 边缓存视图 + Leiden + Betweenness(≤10000 守卫) + PageRank(20 迭代) + 逐行写 ~4×N 行（replaceRows 单事务 delete-all + insert-all）→ 并发事务在 `(indexId,metricType,symbolId)` 唯一键上碰撞（GraphMetricMaterializer:141-143 注释确认唯一键），仅一个提交成功，其余回滚 → 回滚的请求各自降级为内存内**再跑一遍** Leiden（runCommunityDetection:744-754）→ 下一批读请求再次发现 summary 缺失，再次物化。物化行缺失窗口随变更频率×读频率持续存在。
- **现状**: 自愈物化没有任何 per-index 单飞/互斥/进行中标记，也没有失败退避；重图算法在请求线程同步执行。
- **风险**: 负载上升必然触发重试风暴：每次尝试 = 全量图重建 + 三个图算法 + 大事务，CPU/DB 双重放大；与 5.6 W-04（LeidenDetector 超时后幽灵线程不退出）叠加时，每次失败物化再泄漏一个持锁/占 CPU 的幽灵线程；永久失败文件（C7-004 无关，见 R4）导致每次增量后物化行必缺，读路径物化频率被变更率放大。大索引单请求可达分钟级，读 QPS 稍高即拖垮 worker 池。
- **建议**: 优先复用平台设施：per-index 物化互斥（对齐 CodeIndexService.withIndexLock 的 ReentrantLock 模式或 `io.nop.commons.concurrent.lock.IResourceLockManager`）；物化进行中标记落库（NopCodeIndex.status=MATERIALIZING，读路径见到即降级返回陈旧/空而不触发）；自愈失败加退避（失败时间戳 + 冷却窗口，防抖模式可复用 rebuildDebounceMap 的形态）；为 Leiden 消费侧补节点上界（对齐既有 MAX_NODES_FOR_COMMUNITY_DETECTION=10000，见失效面枚举中的守卫空洞）。
- **失效面枚举**: 触发点全清单：detectCommunities:114、getGraphAnalysis:199、ensureMaterializedForGraphMetrics:335（getSurprisingConnections:308 / getExplorationQuestions:344 / exportGraphWiki:372 三个入口共用）、getCriticalNodes:439、CodeIndexService.materializeGraphMetrics:727（biz triggerFullIndex:65）、ShardBuildCoordinator:111（未接线）。**同族守卫不一致**：MAX_NODES_FOR_COMMUNITY_DETECTION=10000 仅用于 getCriticalNodes 回退:446 与 diffGraph 基线:506-510；runCommunityDetection:744-754（detectCommunities 回退/exportGraph/diffGraph **target 侧:521**/getKnowledgeGaps:476）与 GraphMetricMaterializer.detectCommunities 均**无上界**——同族兄弟 4 处裸奔、2 处有守卫，逐一登记，不适用豁免。
- **信心水平**: 确定（守卫缺失与触发面为 live code 直读；并发事务碰撞的具体 DB 行为取决于隔离级别，标注为很可能）
- **误报排除**: 不属于"有界声明存在的缓存/低频路径粗锁豁免"：触发点是普通读 action（非管理操作），物化是重计算而非缓存命中；自愈语义本身是注释声明的设计（"one computation, persisted"），缺的是并发单飞与失败退避，不是设计意图被误判。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P1；与 5.6 W-04 裁定为独立缺陷+交叉引用，Leiden 守卫子项归并 W-04 防双计数，见文末「维度复核结论」）

### [C7-002] 集群工作区目录零 retention：每个 (repo,revision) 永久保留一个完整 clone（F4 磁盘形式）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/cluster/ClusterWorkspaceManager.java:61-95`
- **证据片段**:
  ```java
  ReentrantLock lock = repoLocks.computeIfAbsent(repoName, k -> new ReentrantLock());
  lock.lock();
  try {
      Path workspace = Path.of(workspaceRoot, repoName, revision);
      if (Files.isDirectory(workspace.resolve(".git"))) {
          ... // reuse / fetch+checkout
      }
      ...
      gitWithCredential(Path.of(workspaceRoot), credential, "clone",
              repoDir.getAbsolutePath(), workspace.toString());
      git(workspace, "checkout", revision);
  ```
- **严重程度**: P2（组件当前未接线：仅测试引用，无 bean 定义——激活即触发）
- **并发场景**: 集群分片构建调度器（后台/请求线程）按 (repo, revision) 反复调用 prepare。
- **触发时序**: 非竞态，纯累积：revision 为分支名时工作区复用（无害）；revision 为 commit SHA（GIT_REF 模式 256 上限允许十六进制 SHA）时每个新 commit 都 clone 出全新 `workspaceRoot/<repo>/<sha>/` 目录，**全仓库 + 全历史**，无任何删除/归档/TTL 路径（全类无 delete/cleanup 调用，已 grep 确认）。天/周级运行 + 持续提交 → 磁盘单调耗尽。
- **现状**: 只有创建与复用，没有清理机制；workspaceRoot 需显式配置（落点纪律良好），但生命周期只有"生"没有"死"。
- **风险**: 长驻集群构建节点磁盘写满 → clone 失败 → 构建链路整体不可用；多 repo 多 SHA 场景放大倍数为 (仓库大小×提交数)。
- **建议**: 增加 retention 策略（按 repo 的 LRU/N 个最近 revision + TTL 后台清理，或复用引用计数：索引构建完成后标记可回收）；至少提供 deleteWorkspace 显式回收 API 并在 completeShard 发布后调用。
- **失效面枚举**: 同族磁盘落点扫描：ManifestStore（VFS manifest 写入，路径由调用方管理，且 manifestPath 已废弃、analyzeProjectIncremental 无生产调用方）=无失控面；ClusterWorkspaceManager 是本单元唯一自建磁盘累积点，无其他兄弟。
- **信心水平**: 确定（代码事实）；风险等级以激活后计（当前未接线）
- **误报排除**: 不是"有界声明"：javadoc 声明了落点纪律与复用语义，未声明任何生命周期边界；也不是 C6 资源泄漏（无句柄泄漏），是 F4 无界账本的磁盘形态。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P2，见文末「维度复核结论」）

### [C7-003] git 子进程超时不覆盖管道读取阶段：三处 waitFor 超时形同虚设；工作区管理器持锁挂死可级联

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:854-861`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/cluster/ClusterWorkspaceManager.java:145-151, 186-193`；`nop-code/nop-code-flow/src/main/java/io/nop/code/flow/ChangeAnalyzer.java:154-157, 214-228`
- **证据片段**:
  ```java
  // CodeIndexService.java:854-861（先读到 EOF，waitFor 的 30s 只度量 EOF 之后的退出间隙）
  try (var reader = new java.io.BufferedReader(
          new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
  }
  if (!process.waitFor(GIT_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
      process.destroyForcibly();
  ```
  ```java
  // ClusterWorkspaceManager.java:61-63（读管道阶段全程持有 repoLock）
  ReentrantLock lock = repoLocks.computeIfAbsent(repoName, k -> new ReentrantLock());
  lock.lock();
  try {
      ... gitWithCredential(...) // 内部 readAllBytes 无超时
  ```
- **严重程度**: P2
- **并发场景**: 请求线程（triggerRebuildFromCommit / analyzeChanges）或集群构建线程执行 git；ClusterWorkspaceManager 场景下同 repo 的后续 prepare 全部阻塞在同一 repoLock 上。
- **触发时序**: T1 调用 prepare → git clone 启动 → git 因凭据提示（私有仓库无 askpass helper 时 git 在 /dev/tty 等待）或网络黑洞挂起且不关闭 stdout → T1 在 `readAllBytes()`/`readLine()` 上无限阻塞 → **GIT_TIMEOUT_MILLIS 永不生效**（根本到不了 waitFor）→ repoLock 永久被持 → T2..Tn 对同一 repo 的所有后续构建在 `lock.lock()` 处无限期排队 → 该 repo 构建能力静默死亡且健康检测不可见（无 stall 上报）。
- **现状**: 三处 git 调用的超时都只作用于"EOF 之后进程退出"阶段；读管道阶段无超时。ChangeAnalyzer 的 finally destroyForcibly（:226-228）保证了进程最终被杀，但线程仍要先卡过读取阶段。
- **风险**: 一次 git 环境异常（凭据/网络）即造成请求线程永久占用 + （集群路径）同 repo 构建级联挂死，属"不产出也不报错"的静默挂死，无看门狗可见性。
- **建议**: 超时需作用于全部等待点：读取循环改用带超时的可用性检测（如 `Process.waitFor` 前置于后台读线程，或用平台 `IThreadPoolExecutor` + future.get(timeout) 包装整个 git 调用）；或将读管道放入守护线程、主线程 waitFor(timeout) 统一裁决。三处同族一并修（F3）。
- **失效面枚举**: 全单元 ProcessBuilder 共 3 处 4 个调用点：CodeIndexService.gitOutput:846-874（30s，读阶段裸奔）、ClusterWorkspaceManager.gitWithCredential:131-162（60s，读阶段裸奔+持 repoLock）、ClusterWorkspaceManager.gitOutput:176-205（60s，读阶段裸奔+持锁）、ChangeAnalyzer.parseGitDiff:131-239（30s，读阶段裸奔，无锁故级联面小）。逐一登记：4/4 读阶段无超时。
- **信心水平**: 确定（代码结构直读；git 挂起的具体诱因标注为很可能）
- **误报排除**: 不是"waitFor 已有超时"的误判——超时位于读完成之后；也不是 F6 落盘问题。属 F5（超时未作用于所有等待点）的教科书形态。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：缺陷保留；同站点同缺陷归并 C6-001 主登记，本条关闭为别名，见文末「维度复核结论」）

### [C7-004] 已删文件的被依赖边永久残留：deleteFileRecords 只清 nop_code_dependency 的 source 侧，幽灵边随删除操作单调累积

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2404-2406`
- **证据片段**:
  ```java
  // P1-6.7-011: scope by indexId as well — ...
  deleteEntitiesByFilters(NopCodeDependency.class,
          FilterBeans.and(FilterBeans.eq("sourceFilePath", filePath),
                  FilterBeans.eq("indexId", indexId)));
  ```
- **严重程度**: P2
- **并发场景**: 非竞态，长时运行累积缺陷（失败恢复/清理对称性）：长期增量运行中反复触发文件删除（deleteIndex/batchDeleteFileRecords/triggerIncrementalIndex 的 deletedFiles 分支）。
- **触发时序**: 文件 C import 文件 A → 落库行 (source=C, target=A)。A 被删除 → deleteFileRecords(A) 只删 source=A 的行，(source=C, target=A) 存活 → C 未变更前该行永久指向不存在的文件；每个被删文件的入边都成为永久幽灵行。随删除次数单调累积（F4），且 computeAffectedFiles:2326-2342 沿依赖图**双向** 2-hop 传播 → 幽灵边扩大受影响面；CodeRelationGraphLoader 装载依赖边供 surprise scoring → 图分析结果被幽灵边污染。
- **现状**: 关系表清理在其他表上都是双侧的（Call 清 calleeId+callerId:2418-2419、Inheritance 清 subTypeId+superTypeId:2408/2420、SemanticEdge 清双侧:2409-2410），唯独 Dependency 只清一侧。
- **风险**: 长驻增量服务中幽灵依赖边只增不删：影响面传播过宽（增量放大）、surprise/question 生成引用已删文件、且无任何自愈路径（C 重分析时才会以新行替换，C 不变则永存）。同族清理不对称正是 F3 缺陷族的典型残留。
- **建议**: deleteFileRecords 中对称补 `targetFilePath = filePath AND indexId` 的删除（该行的重新生成由 C 下次重分析天然覆盖）；同事务内完成，无额外一致性代价。
- **失效面枚举**: 依赖边写入点 2 处（saveFileResultInSession:1995-2031 常规路径、synthesizeAndPersistHeuristicEdges 写 Call 不写 Dependency）× 清理点 1 处（deleteFileRecords:2404-2406，仅 source 侧）；全单元删除文件入口 3 个（deleteIndex:898-915 走 deleteEntitiesPaged 全清不受影响、batchDeleteFileRecords:2856、triggerIncrementalIndex:1179、indexDirectory:366-368、indexFileSet:470）——后四者共用 deleteFileRecords，**4/4 受影响**，逐一登记。
- **信心水平**: 确定
- **误报排除**: 不是"残留到 C 下次变更即自愈所以无害"——被删文件 A 的入边在 C 不再变更的前提下永存，长时运行语义下即单调增长；也不是 ORM 级联职责（注释 2412-2417 已明示 bulk 路径绕过级联需显式镜像，本条即该镜像的漏项）。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P2，见文末「维度复核结论」）

### [C7-005] 搜索引擎副作用处于 DB 事务内且无补偿：回滚后搜索索引与 DB 永久发散（F2 半个成功）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2389-2395`（删除侧）、`1786-1817`（写入侧）
- **证据片段**:
  ```java
  // deleteFileRecords（在 transactionTemplate.runInTransaction 体内执行）:
  if (searchEngine != null && !symbolIds.isEmpty()) {
      try {
          searchEngine.removeDocs(topic, symbolIds);   // 外部副作用，先于 DB 删除
      } catch (Exception e) {
          LOG.warn("Failed to remove search docs for file {}", filePath, e);  // 吞掉继续
      }
  }
  ```
  ```java
  // saveFileResultInSession（同一事务内、persist 之前）:
  try {
      searchEngine.addDoc(topic, doc);                 // 外部副作用；DB 随后可能回滚
  } catch (Exception e) {
      LOG.warn("Failed to sync symbol {} to search engine", sym.getId(), e);
  }
  ```
- **严重程度**: P2
- **并发场景**: 索引构建线程在事务内同步调用外部搜索引擎；与并发读搜索请求共享引擎状态。
- **触发时序**: T1 执行 triggerIncrementalIndex/indexFile/indexDirectory → 事务中途 removeDocs 已生效（外部系统，不可回滚）→ 后续 persist 抛异常 → DB 事务回滚，符号行恢复存在，但搜索 docs 已删 → DB 与搜索索引发散（漏结果）。对称地：addDoc 先落引擎 → 事务回滚 → 幽灵搜索结果（查到已不存在的符号）。发散持续到该文件**再次**被变更重索引才消除；期间无检测、无补偿任务。
- **现状**: 外部副作用与 DB 事务混排且双向吞异常（catch 后仅 warn 继续），既不前移出事务，也无回滚后的对账/重放。
- **风险**: 长时运行中每次失败的索引事务都净增一处发散；搜索结果质量静默劣化（ghost/missing），无指标可见。属 F2"半个成功"无恢复语义 + F9 吞异常复合。
- **建议**: 两选一：(a) 将搜索引擎同步移出 DB 事务，改为事务提交后（afterCommit）执行、失败落重试队列；(b) 保留现位置但增加对账路径（重建完成后按 topic 全量 diff 修复）。最小修复是 (a)。
- **失效面枚举**: 副作用点全清单：removeStaleSymbolDocsForFile:2471-2481（saveFileResultInSession 开头）、deleteFileRecords removeDocs:2389-2395、saveFileResultInSession addDoc:1812-1816、deleteIndex removeTopic:890-896（独立于写事务，风险低）。4 处中 3 处处于 DB 事务体内且吞异常；searchEngine 为 null 时的跳过分支不影响本判定。
- **信心水平**: 确定（事务边界与副作用顺序为直读事实；发散持续时间取决于后续变更行为）
- **误报排除**: 不按 error-handling 维度重复立案——本条的实体是"事务回滚无法撤销的外部副作用 + 无恢复语义"（F2），不是错误码/日志规范；也不与 C7-004 重复（那是 DB 内残留行，这是事务外系统发散）。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P2，裁定为搜索双写族主登记并吸并 C6-005，见文末「维度复核结论」）

### [C7-006] 分片构建治理缺口：BUILDING 无停滞检测、deleteIndex 不清 shard ledger、「同事务发布」声明与代码不符（组件未接线）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/ShardBuildCoordinator.java:50-72, 77-93, 95-116`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:898-915`
- **证据片段**:
  ```java
  // ShardBuildCoordinator.java:77-92 —— 外层仅 runInSession，无事务模板
  public ShardBuildStatus completeShard(String indexId, String shardId) {
      return ormTemplate.runInSession(session -> {
          ...
          if (!SHARD_COMPLETED.equals(row.getStatus())) {
              row.setStatus(SHARD_COMPLETED);
              row.setCompletedAt(...);
          }
          IEntityDao<NopCodeShardLedger> ledgerDao = daoProvider.daoFor(NopCodeShardLedger.class);
          return finalizeIfAllCompleted(indexId, ledgerDao, session);
      });
  }
  ```
  ```java
  // ShardBuildCoordinator.java:107-113 —— 注释声称与 status 翻转同事务
  index.setStatus(STATUS_COMPLETED);
  if (metricMaterializer != null) {
      // publish point: global metrics materialized in the same transaction as the
      // status flip, so a published index is always fully scored
      metricMaterializer.materialize(indexId);   // 内部自开独立事务（replaceRows:271）
  }
  ```
- **严重程度**: P3（组件当前未接线：无 bean 定义、无生产调用方，仅测试引用；激活前须修）
- **并发场景**: 分片 worker 各自回写 completeShard；构建停滞时无人兜底。
- **触发时序**: (a) 某 shard worker 崩溃 → ledger 永远 PENDING、index 永远 BUILDING，无租约/超时/watchdog（F5），仅人工重 begin 可恢复且无停滞告警；(b) materialize 在 finalize 中失败 → session 关闭时 status 翻转与 shard 状态照常 flush（外层无事务边界），实际形成"已发布但无度量"或反复重入 finalize，与注释"same transaction ... always fully scored"不符；(c) deleteIndex:900-910 的实体清理清单不含 NopCodeShardLedger → 删除索引后 ledger 孤儿行永存，indexId 复用时 getShardBuildStatus 报陈旧账。
- **现状**: 三项缺口：无停滞检测、无外层事务保证声明语义、deleteIndex 清理清单缺项。
- **风险**: 激活集群构建后：停滞构建不可检测（违反挂死可检测性）、发布原子性承诺落空、孤儿 ledger 行单调累积（每索引 shard 数量级，小但真实）。
- **建议**: completeShard 外层补 `transactionTemplate.runInTransaction(REQUIRED)` 使声明成立；ledger 行增加 begin 时间戳 + 停滞判定（超时列出而非自动删除）；deleteIndex 实体清单补 NopCodeShardLedger。接线前完成。
- **失效面枚举**: ledger 写入点 2 处（beginShardBuild:58-68 重置重建、completeShard:86-89 upsert）× 清理点 0 处（deleteIndex 缺项为唯一清理缺口）；状态翻转点 1 处（finalizeIfAllCompleted:107）。无其他兄弟。
- **信心水平**: 确定（代码事实）；风险判定以"接线激活"为前提
- **误报排除**: 不因"未接线"直接不报——该类是 N6.4 已交付的公共组件（javadoc 引用设计文档），缺口属结构性而非风格；也不与 C7-001 的物化问题重复（那是读路径守卫，这是发布原子性声明失真）。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3；归属拆解 (a) 独立/(b) 交叉引用 C1-003+5.6 CS-R2-004/(c) 别名归 C6-003，见文末「维度复核结论」）

### [C7-007] LLM 语义边预算永不重置且缓存命中仍扣预算：首次耗尽后特性静默永久停用（组件未接线）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/semantic/LlmEdgeBudget.java:10-23`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/semantic/LlmSemanticEdgeExtractor.java:64-76`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/semantic/LlmEdgeCache.java:13-19`
- **证据片段**:
  ```java
  // LlmSemanticEdgeExtractor.java:64-71（预算扣减在缓存查询之前——命中也扣）
  for (int[] pair : pairs) {
      if (!budget.tryConsume(estimatedPerPair)) {
          break;                                   // 耗尽后永久 break，无重置路径
      }
      ...
      String responseText = cache.getOrCompute(prompt, () -> { ... chatService.call(request, null); ... });
  ```
  ```java
  // LlmEdgeBudget.java:11 —— consumed 只增不减，无 period/reset 语义
  private final AtomicInteger consumed = new AtomicInteger(0);
  ```
- **严重程度**: P3（组件当前未接线：仅测试引用）
- **并发场景**: 长驻进程内多次索引构建共用同一单例预算/缓存（若按 bean 装配）。
- **触发时序**: 非竞态：首次大索引构建耗尽 maxTokens → 之后每次 extract 在第一个 pair 处 break → LLM 语义边静默消失，无告警、无恢复（进程重启前永久）；缓存全命中场景下同样扣减，重复构建纯烧预算不产生调用。
- **现状**: 预算无周期/重置语义（javadoc"单个项目"暗示 per-project 生命周期，但实例生命周期无人管理）；缓存注释自declared"进程生命周期、无持久化"——缓存无界本身为已声明边界，不另立案。
- **风险**: 特性静默失效（F9 变体：耗尽无对外信号）；接线后运营者难以解释"语义边越来越少"。
- **建议**: 预算增加重置/窗口语义（per-run 新实例，或时间窗配额）；tryConsume 前先查缓存命中（预算只对真实 LLM 调用扣减）；耗尽时记 WARN 一次性告警。接线前完成。
- **失效面枚举**: 预算消费点唯一（extract:65）；缓存消费点唯一（extract:71）；预算与缓存的装配点当前为 0（无 beans 引用，已 grep 确认）。无同族兄弟。
- **信心水平**: 确定（语义直读）；"单例共用"前提标注为很可能（取决于未来装配方式）
- **误报排除**: 缓存无界未立案——注释已显式声明"v1 纯内存（进程生命周期），缓存持久化 deferred"，按纪律 6 属已声明边界；本条立案对象是预算语义（未声明的静默永久停用），非缓存容量。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C7-008] rebuildDebounceMap 生命周期缺口：deleteIndex 不清理 → 重建的同名索引被残留时间戳静默拦截；防抖为非原子 check-then-act

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:159-176, 753-791, 885-919`
- **证据片段**:
  ```java
  // 885-917：deleteIndex 清理了 incrementalAffectedFilesMap（911）与 indexLocks（917），
  // 但没有 rebuildDebounceMap —— 删除后 30s 内同名索引的 rebuild 被残留时间戳拦截
  transactionTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn ->
          ormTemplate.runInSession(session -> {
              ...
              incrementalAffectedFilesMap.remove(indexId);
              daoProvider.daoFor(NopCodeIndex.class).deleteEntityById(indexId);
              return null;
          }));
  indexLocks.remove(indexId);
  ```
  ```java
  // 758-765：check-then-act，无原子性
  long now = System.currentTimeMillis();
  Long last = rebuildDebounceMap.get(indexId);
  if (last != null && now - last < debounceMillis) {
      result.setDebounced(true); ... return result;
  }
  ```
- **严重程度**: P3
- **并发场景**: 管理线程 deleteIndex 与（自动/人工）triggerRebuildFromCommit 并发；或删除后快速重建再触发。
- **触发时序**: T1 rebuild 成功（789 置时间戳）→ T2 deleteIndex（map 条目残留）→ T3 重建同名索引并立即 triggerRebuildFromCommit → 命中残留时间戳被 debounce，返回 debounced=true 且**不执行任何重建**，状态消息甚至暗示"刚重建过"。另一形态：T3/T4 并发触发均通过 check → 双双执行全量增量（靠 withIndexLock 串行兜底，结果正确但双倍开销）。
- **现状**: 清理清单不对称（同函数内清理了两个兄弟 map，漏了第三个）；防抖为非原子检查。
- **风险**: 低频但确定的用户可见误行为（重建被静默跳过）；map 随历史 indexId 数缓慢增长（每条目一字节级，泄漏量可忽略，列 here 仅为清单完备）。时间戳在触发**开始**时捕获（758 vs 789），长重建后窗口已过——该方向对重试友好，判定非缺陷。
- **风险补充**: 无——本条为 P3 局部缺陷。
- **建议**: deleteIndex 清单补 `rebuildDebounceMap.remove(indexId)`；防抖改 `putIfAbsent`/compute 原子语义（非关键，可与上条同批）。
- **失效面枚举**: 写入点 2 处（778、789）+ 清理点 0 处（resetRebuildDebounce:174-176 为测试可见性专用，非生产清理）；同族对照：incrementalAffectedFilesMap 有清理（911）、indexLocks 有清理（917，但本身带竞态 → CL-301）。3 兄弟 1 漏。
- **信心水平**: 确定
- **误报排除**: 不按"每 indexId 一条目"报内存泄漏（量级可忽略）；立案点是 deleteIndex 后同名索引重建被静默拦截这一确定行为分支，与已声明防抖语义（30s 窗口内去重）不同——那是同索引连续触发，这是跨生命周期的残留生效。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C7-009] IFingerprintStore 两实现删除语义相反：接口声明"覆盖"，ORM 实现是 upsert 合并，InMemory 是真替换；公开 API 行为随实现漂移

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/IFingerprintStore.java:9-12`；`nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/InMemoryFingerprintStore.java:13-23`；`nop-code/nop-code-service/src/main/java/io/nop/code/service/incremental/OrmFingerprintStore.java:40-79`；`nop-code/nop-code-core/src/main/java/io/nop/code/core/incremental/IncrementalDetector.java:114-121`
- **证据片段**:
  ```java
  // IFingerprintStore.java:10 —— 接口契约声明覆盖语义
  /** 保存指纹列表，覆盖该 indexId 下已有数据 */
  void saveFingerprints(String indexId, List<FileFingerprint> fingerprints) throws IOException;
  ```
  ```java
  // OrmFingerprintStore.java:47-77 —— 逐行 upsert，列表外旧行全部存活（非覆盖）
  for (FileFingerprint fp : fingerprints) {
      ...
      String existingId = existingPathToId.get(canonicalPath);
      if (existingId != null) { fileEntity = fileDao.getEntityById(existingId); }
      else { ...isNew = true... }
      ...
      if (isNew) { fileDao.saveEntity(fileEntity); ... }
  }
  ```
  ```java
  // InMemoryFingerprintStore.java:14-22 —— 整 map 替换（真覆盖）
  Map<String, FileFingerprint> map = new ConcurrentHashMap<>();
  ...
  store.put(indexId, map);
  ```
- **严重程度**: P3
- **并发场景**: 非竞态，语义分歧缺陷：调用方按接口契约（覆盖）推理，实际行为取决于注入的实现。
- **触发时序**: 公开组合 API `IncrementalDetector.computeAndSaveFingerprints(store, indexId, resources)`（main-code 公开方法，当前无生产调用方，已 grep 确认）以**部分**资源列表调用时：InMemory 实现将未列入列表的旧指纹全部抹掉；ORM 实现将它们全部保留（upsert）。同一调用、两个实现、相反的删除语义。若未来生产路径改用 InMemory（或测试通过而生产用 ORM 的"测试-生产等价"假设被打破），将分别产生"指纹全丢→全量重算"或"指纹残留→每次增量都误报 ghost deleted/added"。
- **现状**: 生产主路径（triggerIncrementalIndex:1201-1212）自带 deleteFileRecords 补偿，语义自洽不受此影响——缺陷面限于接口契约与 computeAndSaveFingerprints 组合子的可移植性。
- **风险**: 换实现即静默改变清理行为；按契约写的新调用方在生产上产生指纹残留（单调误报）或数据丢失。
- **建议**: 二选一：修正 OrmFingerprintStore 为真覆盖（save 前按 indexId 删除列表外路径），或修正接口 javadoc 为"upsert 语义，列表外路径不保证删除"并在 computeAndSaveFingerprints 上注明；推荐前者（与声明一致、与 InMemory 对齐）。
- **失效面枚举**: 实现共 2 个（InMemoryFingerprintStore=替换、OrmFingerprintStore=合并）；契约消费方 3 处：IncrementalDetector.computeAndSaveFingerprints:116-121（无生产调用方）、CodeIndexService.triggerIncrementalIndex:1212（带 deleteFileRecords 补偿，自洽）、测试 4 处。逐一登记。
- **信心水平**: 确定（语义分歧直读）；风险以"未来调用方"为前提
- **误报排除**: 不是风格/命名问题——接口 javadoc 是显式契约声明，与实现行为相反属契约漂移；也非 C7-004（那是 DB 内依赖边残留，这是存储抽象层契约）。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C7-010] batchSaveFileRecords 缺事务/会话包装：与已修复的兄弟 batchDeleteFileRecords 不对称（F3），已存在行的字段更新依赖隐式 flush

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2766-2801`（save 侧）、`2843-2860`（delete 侧对照）
- **证据片段**:
  ```java
  // 2766-2800：save 侧 —— 无 runInTransaction/runInSession，已存在实体仅改字段不 save
  public void batchSaveFileRecords(String indexId, List<FileFingerprint> fingerprints) {
      checkWriteAccess(indexId);
      ...
      for (FileFingerprint fp : fingerprints) {
          ...
          List<NopCodeFile> existing = fileDao.findAllByQuery(query);
          NopCodeFile fileEntity;
          if (!existing.isEmpty()) {
              fileEntity = existing.get(0);          // 已存在：改字段后不调用 save
          } else { ... }
          fileEntity.setFileHash(fp.getContentHash());
          fileEntity.setLastModified(fp.getLastModified());
          fileEntity.setFileSize(fp.getFileSize());
          if (existing.isEmpty()) { fileDao.saveEntity(fileEntity); }
      }
  }
  ```
  ```java
  // 2845-2848：delete 侧注释明示包装的必要性 —— 兄弟方法未获得同等待遇
  // Wrap in a transaction+session (under the per-index lock) so the paged load/delete inside
  // deleteFileRecords shares one ORM session — entities loaded by findAllByQuery must belong
  // to the session that later deletes them. ... this public entry point must too, otherwise
  // it is unsafe standalone.
  ```
- **严重程度**: P3（公开 API（ICodeIndexService:155），当前无生产调用方；invariant 测试只覆盖"不重复插入"面，未覆盖"已存在行字段更新是否落库"面）
- **并发场景**: 外部调用方在无事务上下文处独立调用；逐条 findAllByQuery 各自获取会话，已加载实体的脏字段依赖会话关闭时机隐式 flush。
- **触发时序**: T1 调用 batchSaveFileRecords 更新已存在文件的指纹 → 实体由 dao 查询会话加载 → 该 dao 调用结束、会话生命周期若不覆盖后续变更（正是 delete 侧注释承认的失败模式）→ setFileHash/setLastModified/setFileSize 丢失 → 指纹行陈旧 → 下次增量误判 modified（多一次无谓重分析，可自愈）或 fileSize/mtime 漂移。同时无 withIndexLock：与并发增量构建无互斥（delete 侧有）。
- **现状**: 同族两个 batch 入口，delete 侧已按注释修复包装，save 侧未同步——F3"修实例不修类别"的现行残留。
- **风险**: 接线后指纹更新静默丢失/竞态；测试保护力缺口（invariant 测试断言的是 count 不变，不校验字段落库）。
- **建议**: 复制 delete 侧修复形态：withIndexLock + runInTransaction(REQUIRED) + runInSession 包裹；已存在分支显式 saveEntity/updateEntity 或确认会话 flush 契约后加注释。补一条"已存在行字段更新落库"的回归断言。
- **失效面枚举**: batch 入口共 3 个：batchSaveFileRecords:2767（无包装、无锁）、batchLoadFileRecords:2804（只读，无包装合理）、batchDeleteFileRecords:2843（有包装有锁）。3 兄弟 1 缺陷 1 合理 1 已修。
- **信心水平**: 确定（不对称直读）；"更新丢失"具体行为标注为很可能（取决于 Nop ORM 会话 flush 契约，未在本单元内验证）
- **误报排除**: 不按"每次 dao 调用自动开短会话"的假设立案为 P1/P2——语义取决于 IOrmTemplate 契约，故连同测试缺口一起按 P3 登记；不是 error-handling 问题。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

### [C7-011] triggerFullIndex 失败不写增量状态：getIncrementalStatus 轮询看到上一轮的 completed=true（F9 观测缺口）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/entity/NopCodeIndexBizModel.java:56-79, 104-112`
- **证据片段**:
  ```java
  // 56-68：full 路径只有 materialize 有 catch —— indexDirectory 失败直接抛出，不落状态
  public String triggerFullIndex(...) {
      int fileCount = codeIndexService.indexDirectory(indexId, projectPath, null);
      // materialized global metrics are derived data: a failure here must not fail the index
      try {
          codeIndexService.materializeGraphMetrics(indexId);
      } catch (Exception e) { LOG.error(...); }
      ...
      incrementalStatusMap.put(indexId, status);   // 仅成功路径到达
  ```
  ```java
  // 104-112：incremental 路径失败有状态记录（对照组）
  } catch (Exception e) {
      IncrementalStatus status = new IncrementalStatus();
      ...
      status.setCompleted(false);
      status.setErrorMessage(e.getMessage());
      incrementalStatusMap.put(indexId, status);
      throw e;
  }
  ```
- **严重程度**: P3
- **并发场景**: 运维/调度轮询 getIncrementalStatus 与失败的 triggerFullIndex 并发。
- **触发时序**: 第 N 轮 full 成功（status completed=true 入 map）→ 第 N+1 轮 full 在 indexDirectory 抛异常 → 异常传播、无状态写入 → 轮询方查询得到第 N 轮的 completed=true → 失败不可观测，调度方误判"已成功"而跳过重试。
- **现状**: 同类失败在 incremental 路径有 catch+记录+rethrow 的完整形态，full 路径缺失——同文件内两兄弟不对称。
- **风险**: 长时运行调度下失败被吞于观测面（数据本身因单事务回滚未损坏，故 P3）：失败不可见 → 无重试 → 索引停在上轮状态且无人知晓。
- **建议**: 对齐 incremental 形态：triggerFullIndex 全路径 try/catch，失败写 completed=false + errorMessage 后 rethrow。
- **失效面枚举**: 状态写入点 3 处：triggerFullIndex 成功:75（无失败分支）、triggerIncrementalIndex 成功:100 与失败:110（双分支齐全）。同族 1 缺 1 全。
- **信心水平**: 确定
- **误报排除**: 不与 F9"吞异常导致状态不一致"的 P1/P2 形态混判——此处异常正确上抛（调用方 GraphQL 可见），缺口仅在轮询观测面；单事务保证数据面无损，故 P3。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：缺陷保留；同站点同缺陷归并 C6-009 主登记，本条关闭为别名，见文末「维度复核结论」）

---

## 三、维度集外线索（CL-301 起，附记供对应维度复核）

### [CL-301] indexLocks.remove 在持锁状态下执行：删除索引与在途写者的互斥可被打破（C1/C2 域）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:125-143, 885-918`
- **线索**: deleteIndex 在 withIndexLock(indexId) 内部最后执行 `indexLocks.remove(indexId)`。T2 在 remove 前已 computeIfAbsent 拿到旧锁并阻塞在 lock()；T3 在 remove 后 computeIfAbsent 得到**新锁**并进入临界区 → T2（旧锁）与 T3（新锁）并发变更同一 indexId，单写者不变量被打破（deleteIndex 删除完的索引可能被 T2 复活写入残留行）。强信号：具体交错可写出，锁对象替换型竞态。建议 C2 维度立案并核对锁-final 原则。

### [CL-302] detectFlows/persistFlows 不持 per-index 锁：并发调用 delete-all + plain session.save 竞态（C1/C3 域）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java:2560-2578, 2679-2727`
- **线索**: persistFlows 先 deleteExistingFlowsByIndex 再逐条 `session.save(flowEntity)`（未用 saveReplacingExisting，与索引写入路径的 upsert 纪律不一致），且 detectFlows 入口无 withIndexLock。两个并发 detectFlows：T1 删旧插 F → T2 删（含 T1 刚插的 F）插 F，交叠窗口内可出现同 id 双写或约束冲突 → 一方 detectFlows 失败。流程行有 MAX_FLOWS_PER_INDEX=5000 上界（有界性 OK），问题仅在并发互斥缺失。

### [CL-303] CodeCacheManager.rebuildDependencies 无条目上限：与 symbolTable/callGraph 的 MAX_CACHE_* 守卫不同族（C5 域）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:45-46, 254-270`
- **线索**: rebuildSymbolTable/rebuildCallGraph 各有 MAX_CACHE_SYMBOLS=100000 / MAX_CACHE_EDGES=500000 截断守卫（含 truncated 标记与消费侧 warnIfCacheTruncated），rebuildDependencies 把 indexId 下全部 nop_code_dependency 行载入 List 无任何上限——单 CacheEntry 内存峰值由依赖行总量决定，20 条目 × 大仓库依赖行数。建议 C5 按同族守卫缺失立案。

### [CL-304] SymbolTable 同步写/裸读不对称：getByQualifiedName/getById/size 无锁读 synchronized add 后的 HashMap（C1 域，基线 G11-15-02 站点）

- **文件**: `nop-code/nop-code-core/src/main/java/io/nop/code/core/graph/SymbolTable.java:26-51`
- **线索**: add 与 getAll/findAllByQualifiedNamePrefix 是 synchronized，但 getByQualifiedName/getById/size 裸读。缓存条目生命周期内存在并发写者（persistSingleFileInSession → cacheManager.addToSymbolTableCache:1604 在持 CodeCacheManager 锁下 add，而图分析读路径 getOrRebuildSymbolTable 返回表后在锁外遍历/点查）。 getAll 已按 WP-5 AR-155/158 做快照，点查仍暴露于并发 put 的 HashMap 读。写不出稳定复现时序（HashMap 裸读并发 put 的后果从丢读到死循环不等），按 C1 口径需补具体交错或降 P2。

### [CL-305] OrmFingerprintStore.saveFingerprints 全量逐行 getEntityById：每次增量 O(N) 单行加载（C9 域）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/incremental/OrmFingerprintStore.java:45-78`
- **线索**: triggerIncrementalIndex 以**全量**当前文件指纹列表调用 saveFingerprints（CodeIndexService:1201-1212），ORM 实现对每行先 loadFileIdMapByIndex（全表分页扫一遍）再逐行 `fileDao.getEntityById(existingId)`——10 万文件级仓库的每次增量 = 10 万次单行 SELECT + 全表扫描，即使 0 文件变更后的实际变更触发也一样。量化口径：N 次 PK 点查/增量。建议 C9 立案（批量化：existingPathToId 已含 id，行值比较后仅对真变更行加载）。

---

## 四、自查口径备注

- 本报告数量结论口径：wiring 判定来自 `grep -rln "<类名>" nop-code --include="*.java" --include="*.xml"`（排除 target/），双口径交叉（beans.xml 全文目检 + java 引用清单）；失效面枚举逐点给出文件:行号，可复现。
- 已知跨单元裁定引用：5.6 W-04（LeidenDetector 幽灵线程本体）——本单元 C7-001 仅审消费侧触发/叠加面，未重审 detector 内部。
- 未立案但已核对为 OK 的面：FlowDetector 缓存有界（evictOverflow）、incrementalStatusMap 有界（100 LRU）、CodeCacheManager 容量+TTL、IncrementalDetector analyze-before-delete 与失败文件指纹保留（自愈设计良好）、persistFlows 5000 上界、搜索降级路径 limit*2/*3 有界、ChangeAnalyzer finally destroyForcibly。

---

# C7 深挖第 2 轮

- **轮次焦点**: C7-001 同族放大——「hasMaterialized→materialize」之外的全部读触发写/重建站点穷举 + 各站点并发触发防护面全查（synchronized/单飞标志/DB 锁）
- **盲审声明**: 本轮仅追加，不修改第 1 轮内容；未读取本维度集外其他维度文件及 ai-dev/plans/、ai-dev/bugs/、ai-dev/lessons/、ai-dev/backlog/。未修改任何产品代码。
- **日期**: 2026-10-22（审计执行）/ 基线 commit 7b0ccc89d4

## 一、读触发写/重建站点防护面清扫表（C7-001 同族放大）

| # | 站点 | 触发动作 | 并发防护 | 判定 |
|---|---|---|---|---|
| 1 | CodeGraphService.detectCommunities:114 / getGraphAnalysis:199 / ensureMaterializedForGraphMetrics:335（getSurprisingConnections:308、getExplorationQuestions:344、exportGraphWiki:372 三入口共用）/ getCriticalNodes:439 | 读路径触发 DB 写（materialize：图算法重算 + 逐行落库） | **无任何防护**（无锁/无进行中标记/无退避；check-then-act 于 loadSummary） | 第 1 轮 C7-001 已立，枚举经本轮复核完整 |
| 2 | CodeCacheManager.getOrRebuildSymbolTable:113 / getOrRebuildCallGraph:129 / getOrRebuildDependencies:173 | 读路径触发内存重建（DB 分页加载 100k 符号/500k 边/全量依赖） | 全局单例 ReentrantLock（单飞成立）；但重建 I/O 在锁内执行，且该锁同时守护全部 indexId 的缓存命中快速路径 | **本轮立案 C7-R2-001**（防护面反问题：单飞粒度全局过粗） |
| 3 | CodeSearchService.searchViaEngine:89-141 | 读路径降级（引擎失败→DB 查询） | 只读，无写触发 | 无缺陷（无自愈重建站点，搜索面不存在 materialize 同族） |
| 4 | GraphMetricStore / GraphQuestionGenerator / CodeRelationGraphLoader | 纯读 | 类注释声明 read-only，逐方法核实无写 | OK |
| 5 | FlowDetector 读路径（getFlow/getAffectedFlows/listFlows） | 读缓存 | detectFlows 为 @BizMutation（声明写动作，非读触发）；构建后整体 `flowCache.put` + unmodifiable view 安全发布（WP-5 AR-147） | OK；双缓存独立驱逐=第 1 轮 CL-206 |
| 6 | NopCodeSymbolBizModel / NopCodeFileBizModel 全部 @BizQuery/@BizLoader | 纯读 | 逐方法核实无 save/delete/persist | OK |
| 7 | CodeIndexService.getIndexStats/getIndexIds/getLastIncrementalAffectedFiles | 纯读 | — | OK |
| 8 | analyzeChanges（@BizQuery） | 读路径执行 git 子进程（ChangeAnalyzer.parseGitDiff） | 无写触发；挂死面归 C6-001/C7-003 | 已覆盖 |

**结论：读触发 DB 写站点全单元仅 materialize 一族（#1，C7-001 枚举完整）；读触发内存重建仅 CodeCacheManager（#2）。ensureSubServices:178 为 synchronized 惰性初始化（防护成立；对照 getFingerprintStore:248 无同步 = 第 1 轮 CL-205）。**

## 二、发现条目

### [C7-R2-001] 读触发缓存重建的全局单飞粒度失控：DB 分页 I/O 在唯一全局锁内执行，一个索引的慢重建冻结全部索引的全部图读（C7-001 同一读路径的防护面反面）

- **文件**: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java:71-73, 113-127, 192-221`
- **证据片段**:
  ```java
  // CodeCacheManager.java:71-73 —— 唯一全局锁，无 per-index 粒度
  private final Map<String, CacheEntry> analysisCacheMap = new LinkedHashMap<>(16, 0.75f, true);
  private final ReentrantLock lock = new ReentrantLock();
  ```
  ```java
  // 113-127 —— 缓存命中快速路径与重建慢路径共用同一把全局锁
  SymbolTable getOrRebuildSymbolTable(String indexId, IDaoProvider daoProvider, ...) {
      lock.lock();                                    // 所有 indexId 的所有读请求在此串行
      try {
          CacheEntry entry = getOrCreateEntry(indexId);
          if (entry.cache.symbolTable != null) { entry.touch(); return entry.cache.symbolTable; }
          entry.cache.symbolTable = rebuildSymbolTable(indexId, daoProvider, converter);  // 锁内 DB I/O
  ```
  ```java
  // 192-221 —— 锁内分页加载，最大 100k 行 / 20 次批量查询（CallGraph 500k、Dependencies 无上界=CL-303）
  while (true) {
      query.setOffset(offset); query.setLimit(BATCH_SIZE);   // BATCH_SIZE=5000
      List<NopCodeSymbol> batch = symbolDao.findPageByQuery(query);
      ...
      if (totalLoaded >= MAX_CACHE_SYMBOLS) { ... return table; }
  ```
- **严重程度**: P3（无正确性问题，纯串行化热点；C2/C9 邻域，供合并裁定阶段归属）
- **并发场景**: N 个 HTTP/GraphQL worker 并发访问图读 action（getSurprisingConnections/getGraphAnalysis/detectCommunities/exportGraphWiki/getCriticalNodes/getImpactAnalysis/diffGraph 等 15+ 调用点）+ 任一索引的写路径 invalidateAnalysisCache 使缓存失效。
- **触发时序**: T1 读索引 A，缓存未命中（写后失效/TTL 1h 到期/LRU 20 条目驱逐）→ T1 持全局锁执行 rebuildSymbolTable（10 万符号 = 20 次 5000 行分页查询，秒级）→ T2..Tn 读**无关索引 B/C** 的图 action → 全部阻塞在 `lock.lock()`（缓存命中快速路径也必须拿锁）→ 直至 T1 三结构重建完毕。与 C7-001 叠加：物化风暴 N worker 进入 materialize 后先在此串行重建缓存，随后释放锁并发跑 Leiden——重建段被串行放大一次，算法段照旧并发。
- **现状**: 单飞本身成立（重建不重复执行），但锁粒度为进程级全局，覆盖「命中快速路径 + 全部索引的重建」两个量级相差 5 个数量级的操作（微秒 touch vs 秒级 DB 分页）。
- **风险**: 多索引共享服务实例时，任一大索引的缓存失效都会造成全局图读停顿；长时运行下随索引数量增加，失效-重建事件频率上升，停顿概率与时长同步增长。无数据损坏、无泄漏，故 P3。
- **建议**: 锁粒度降为 per-indexId（`ConcurrentHashMap<String, ReentrantLock>` + computeIfAbsent，对齐 CodeIndexService.indexLocks 形态；注意规避 CL-301/201 的持锁移除竞态——本缓存无 remove 需求，天然规避）；或命中路径改无锁 ConcurrentHashMap 读、仅重建段持 per-index 锁（double-check + volatile entry）。平台设施对照：`io.nop.commons.concurrent.lock.IResourceLockManager`。
- **失效面枚举**: 锁内重建站点 3 处（getOrRebuildSymbolTable:113 / getOrRebuildCallGraph:129 / getOrRebuildDependencies:173，同根因一并立案）+ 锁内写维护 2 处（addToSymbolTableCache:157——写路径持 indexLock 再拿本锁，嵌套方向单一无死锁环；invalidateAnalysisCache:145——轻量 remove，无害）；命中快速路径 3 处即上述 3 个 getOrRebuild 的命中分支。同族对照：NopCodeIndexBizModel.incrementalStatusMap 用 synchronizedMap（粒度正确）、FlowDetector 双缓存用 ConcurrentHashMap（无锁读，粒度正确）——CodeCacheManager 是本单元唯一全局粗锁读路径。
- **信心水平**: 确定（锁范围与调用面为 live code 直读；重建时长量级为按 MAX_CACHE_SYMBOLS/BATCH_SIZE 推算）
- **误报排除**: 不落入"低频管理路径粗锁合理"豁免——触发点是普通读 action（C7-001 已同口径裁定）；也不落入"原语使用不是问题"——立案点是锁内重 I/O 与全局粒度的复合（C2 定向检查项"热路径锁内重操作"），非 synchronized 原语本身。
- **复核状态**: 已复核（2026-10-23 C6+C7 维度独立复核，判定：保留 P3，见文末「维度复核结论」）

## 三、本轮小结

- 新发现 1 条：C7-R2-001（P3，C7-001 同一读路径的防护面反面：materialize 段无单飞 → 风暴；缓存重建段单飞过粗 → 全局停顿）。
- C7-001 同族放大清扫：**闭合**——读触发写/重建站点全单元穷举完毕（上表 8 行），materialize 之外无第二个读触发 DB 写站点。
- 结合第 1 轮：C7 维度的单调增长源（G1-G14）、挂死点（H1-H5）、重试/自愈/时钟（R1-R10）三张清单在第 2 轮逐面复核，除 C7-R2-001 外无新缺口。**若无新输入，本轮终止。**

---

# C7 维度复核结论（独立复核）

- **日期**: 2026-10-23（CRA WI 6.3 Phase 4，C6+C7 维度独立复核子代理）
- **复核方法**: 逐条重读 live 源码 file:line（基线 @ 7b0ccc89d4，与初审同版无漂移）；C7-001 对照 5.6 merge-adjudication（`ai-dev/audits/2026-10/2026-10-22-1300-deep-audit-nop-graph-duckdb-concurrency/merge-adjudication.md`）与 CS-sweep 复核结论逐面核对边界。派单列举 C7-001~011/R2-001，实际超集复核本文件全部 12 条。不修改初审既有内容，仅回填各条复核状态行并追加本节。

## 逐条裁定

### C7-001（P1）→ 保留，维持 P1；与 5.6 W-04 裁定为「独立缺陷 + 交叉引用」，守卫子项归并防双计数

- 证据全部重演属实：6 个读触发点（detectCommunities:114 / getGraphAnalysis:199 / ensureMaterializedForGraphMetrics:335〔getSurprisingConnections:308、getExplorationQuestions:344、exportGraphWiki:372 三入口共用〕/ getCriticalNodes:439）+ CodeIndexService.materializeGraphMetrics:727 + ShardBuildCoordinator:111 逐一核准；ensureMaterializedForGraphMetrics check-then-act（loadSummary==null 判定 L333，无互斥/无标记/无退避）属实；变更后物化行删除窗口（CodeIndexService L1096-1098 deleteByIndex）属实；物化失败降级内存重算再触发（detectCommunities L117-122）属实；replaceRows 单事务 delete-all+insert-all 与 (indexId,metricType,symbolId) 唯一键注释（GraphMetricMaterializer L140-143、L271）属实。
- **5.6 W-04 边界结论（任务指定）**：
  - 5.6 merge-adjudication 的 W-04（CS-001，P1）失效面 = ① detector 内部不可中断（CWTS 反汇编钉死，本体归 5.6）；② 经 CS-R2-001 并入的**调用面守卫不对称**——Leiden 5 调用点零上界（materializer:122、CodeGraphService:121/476/495/521）+ Betweenness 侧守卫齐全（:446/:841/materializer:185），已由 5.6 逐点登记并复核（与本条失效面枚举的守卫空洞为同一清单）；③ C6-CL-031 别名。
  - **未覆盖面**：W-04 不含「自愈物化的并发编排缺陷」——per-index 无单飞/无进行中标记/无失败退避、请求线程同步执行 materialize、并发事务唯一键碰撞回滚后再重算的循环。5.6 R2 调用面清扫按派单口径**仅限"算法调用点占住请求线程"**（CS-sweep R2 覆盖声明原文），ensureMaterializedForGraphMetrics:335 在 5.6 仅作为守卫可达性放大器提及（CS-R2-001 失效面尾注），从未作为并发控制缺陷立案。**本条核心（无单飞重算风暴）是 5.6 未覆盖的本单元独立缺陷，归并裁定=独立，非 W-04 从生面。**
  - **处置**：C7-001 独立立案维持 P1；其「Leiden 无上界守卫」子项**归并 W-04**（CS-R2-001 同站点已登记，修复随 CS-001 建议(3) 落地，不另计工单）；风暴叠加幽灵线程泄漏的后果与 W-04 **交叉引用**。**W-04 修复（可中断/有界）不消灭本条**：无单飞下全量图重建 + Betweenness/PageRank + ~4×N 行大事务的重复执行与碰撞回滚照旧发生，P1 独立成立。
  - 一处枚举勘误（不影响结论）：「MAX_NODES...仅用于 :446 与 :506-510」漏计 L841（assembleCriticalNodesFromMetrics 物化路径守卫，同为 Betweenness 侧）——守卫侧实为 3 处，Leiden 侧零守卫的结论不变。
- 处置：P1 保留；修复面 = per-index 物化互斥 + 进行中标记 + 失败退避；守卫子项交叉引用 W-04。

### C7-002（P2）→ 保留，维持 P2

- 全类逐行复读证实零 retention（无 delete/cleanup/TTL 调用点）；workspace 路径 L64 = workspaceRoot/repoName/revision；revision 白名单 L100（≤256，允许十六进制 SHA）→ 每 SHA commit 一 clone 的累积链成立；dormant（仅 2 测试类引用、无 beans）复核成立，P2 以激活计恰当。

### C7-003（P2）→ 缺陷保留；同站点同缺陷归并 C6-001 主登记，本条关闭为别名

- 4/4 站点「读阶段无超时」复核一致：CodeIndexService L855-857（read lines）先于 waitFor L859；ClusterWorkspaceManager L146/L188（readAllBytes）先于 waitFor L147/L189；ChangeAnalyzer readLine L162 先于 waitFor L214；ChangeAnalyzer finally destroyForcibly L226-228 对照成立。ClusterWorkspaceManager 持 repoLock 跨 git 调用（L61-93 包裹 L86-89）级联面属实。
- **归并裁定（任务指定）**：与 C6-001 **同站点同缺陷**（waitFor 超时位于读循环之后的同一结构性根因）。主登记 = **C6-001**（修复建议为超集：读循环超时看护 + finally 收尸 + 统一 git 执行工具）；本条关闭为别名，其独有后果（repoLock 级联挂死、无 stall 可见性）并入主登记风险段与验收检查单；C5 维 CL-101 一并归并该族。

### C7-004（P2）→ 保留，维持 P2

- deleteFileRecords L2404-2406 仅清 sourceFilePath 侧属实；双侧清理对照组核准（Call calleeId+callerId L2418-2419、Inheritance subTypeId L2408+superTypeId L2420、SemanticEdge 双侧 L2409-2410）——「唯独 Dependency 只清一侧」成立；Dependency 写点唯一（saveFileResultInSession L2016-2017，heuristic 路径写 Call 不写 Dependency）核实；删除入口 4/4 受影响（L367/L402/L470/L1179-1180/L2856 共用 deleteFileRecords，deleteIndex 走 deleteEntitiesPaged 全清豁免）核准；computeAffectedFiles 双向 2-hop 放大（L2330-2342）属实。

### C7-005（P2）→ 保留，维持 P2；裁定为搜索双写族主登记（C6-005 为别名）

- removeDocs L2389-2395 处于 deleteFileRecords（triggerIncrementalIndex/batchDeleteFileRecords 事务体内）且吞异常属实；addDoc L1812-1816 处于 saveFileResultInSession（事务体内）warn-only 属实；removeStaleSymbolDocsForFile L2471-2481 同族；removeTopic L890-896 确在 withIndexLock 内、runInTransaction 之前（独立于写事务）——初审的区分准确。
- **归并裁定**：本条事务边界时序分析更完整，定为**主登记**；C6-005 关闭为别名，其 removeTopic/topic 孤儿对账子注并入本条修复范围（afterCommit 移出 / 对账命令）。

### C7-006（P3）→ 保留（捆绑条目）；归属拆解：(a) 独立 / (b) 交叉引用 C1-003 + 5.6 CS-R2-004 / (c) 别名归 C6-003

- (a) 停滞检测缺失：beginShardBuild L46-72 无时间戳/租约、completeShard 无 watchdog，全类核读无兜底——独立保留。
- (b) 「同事务发布」声明失真：completeShard 外层仅 ormTemplate.runInSession（L78）无事务模板，materialize 内部自开事务（GraphMetricMaterializer replaceRows L271 transactionTemplate.runInTransaction）——注释承诺落空属实。**边界**：与 C1-003 复核修正后的「附加契约缺口」同源（C1 复核已随 C1-003 登记）；5.6 CS-R2-004（P3）亦已登记「ShardBuildCoordinator 事务内物化」——本子项交叉引用两案，不单独计工单。
- (c) deleteIndex 漏删 ledger：与 C6-003 同站点同缺陷，主登记 = **C6-003**，本子项关闭为别名。

### C7-007（P3）→ 保留，维持 P3

- tryConsume 先于 cache.getOrCompute（L64-71，缓存命中仍扣预算）属实；LlmEdgeBudget.consumed 只增无重置/无窗口（L11-23）属实；wiring 复核：预算/缓存 main 装配 0、extractor 未注册——dormant 成立。缓存无界不另立案（已声明边界）与 C6-007（容量口径立案方）不冲突：两案立案对象不同（预算语义 vs 容量口径），注记互引即可。

### C7-008（P3）→ 保留，维持 P3

- deleteIndex 清理漏 rebuildDebounceMap（L898-917 仅清 incrementalAffectedFilesMap L911 + indexLocks L917）、残留时间戳拦截同名索引重建的时序（L758-765 判定 → L778/L789 put）、resetRebuildDebounce 测试专用（L173-176 注释自证）逐一属实；「跨生命周期残留 vs 同索引 30s 去重」的语义区分成立。

### C7-009（P3）→ 保留，维持 P3

- 接口 javadoc「覆盖该 indexId 下已有数据」（IFingerprintStore L10）vs OrmFingerprintStore L40-79 逐行 upsert（无列表外删除分支）vs InMemoryFingerprintStore L13-23 整 map 替换——三方对照属实；computeAndSaveFingerprints（IncrementalDetector L114-121）main 零调用方 grep 核实；生产主路径自洽（triggerIncrementalIndex L1179-1180 deleteFileRecords 补偿 + L1212）核准。

### C7-010（P3）→ 保留，维持 P3

- batchSaveFileRecords L2766-2801：无 withIndexLock/runInTransaction/runInSession，已存在分支仅 set 字段不 save（L2784-2799）属实；delete 侧对照 L2843-2860（withIndexLock + 双重包装 + L2845-2848 注释自证必要性）属实——「3 兄弟 1 缺陷 1 合理 1 已修」枚举准确；公开 API 声明点 ICodeIndexService:155 核实。

### C7-011（P3）→ 缺陷保留；同站点同缺陷归并 C6-009 主登记，本条关闭为别名

- 证据与 C6-009 复核一致（triggerFullIndex L56-79 仅成功写 / triggerIncrementalIndex L104-112 双分支齐全）。**归并裁定**：主登记 = **C6-009**（失效面超集，含 triggerRebuildFromCommit 第三缺口），本条关闭为别名。

### C7-R2-001（P3）→ 保留，维持 P3

- 全局单锁 L73 + 命中快速路径与重建慢路径共用（getOrRebuildSymbolTable L115-122 / getOrRebuildCallGraph L131-139 / getOrRebuildDependencies L174-182）+ 锁内分页 I/O（rebuildSymbolTable L192-221，BATCH 5000 / MAX_CACHE_SYMBOLS 100k，L42-46）逐一属实；invalidateAnalysisCache L145-155 轻量 remove、addToSymbolTableCache L157-171 嵌套方向单一无死锁环核准；对照 NopCodeIndexBizModel synchronizedMap（L44-50）与 FlowDetector ConcurrentHashMap（粒度正确）——「本单元唯一全局粗锁读路径」成立。C2/C9 邻域归属标注合理。

## CL 线索裁定（CL-301~305）

| CL | 裁定 | 复核要点 |
|---|---|---|
| CL-301 | **确认** | 与 C6 维 CL-201 同站点同缺陷双登记（withIndexLock L125-143 + deleteIndex 持锁 remove L917）；归 C1/C2 域单一立案 |
| CL-302 | **确认** | detectFlows L2561-2577 无 withIndexLock；persistFlows L2681-2727 deleteExistingFlowsByIndex + 裸 session.save（非 saveReplacingExisting）属实；MAX_FLOWS_PER_INDEX=5000（L2679）有界性不影响互斥缺口立案；C1/C3 域 |
| CL-303 | **确认** | = CL-202 重复（rebuildDependencies L254-270 无上界），C5 域，防双计 |
| CL-304 | **确认** | SymbolTable 点读裸读（getByQualifiedName L35-37 / getById L39-41 / size L49-51）vs synchronized add L26-33；= C1-001 主登记（C1 复核已吸并 G11-15-02） |
| CL-305 | **确认** | OrmFingerprintStore.saveFingerprints L45 全表 loadFileIdMapByIndex + L58 逐行 getEntityById 属实；C9 域 |

## 批次覆盖核验（三源码根痕迹）

- **nop-code-core**：ProjectAnalyzer（H5、CL-204 关联）、IFingerprintStore/InMemoryFingerprintStore/IncrementalDetector（C7-009/R8）、ManifestStore（G8 对照面）——痕迹在案。
- **nop-code-flow**：FlowDetector（G9/R2 清扫表 #5、CL-206）、ChangeAnalyzer（H1 站点④）——痕迹在案。
- **nop-code-service**：impl 7 类（CodeIndexService 主战场/CodeGraphService/GraphMetricMaterializer/CodeCacheManager/CodeSearchService/CodeQueryService/ShardBuildCoordinator）+ cluster（ClusterWorkspaceManager G8/C7-002）+ semantic（LlmEdgeCache/LlmSemanticEdgeExtractor/LlmEdgeBudget G7/H4/C7-007）+ entity（NopCodeIndexBizModel G11/C7-011）+ incremental（OrmFingerprintStore C7-009/CL-305）+ graph（GraphMetricStore G2 载体）——痕迹在案。
- **单元计数核准**：core 51f/3,865 行 + flow 12f/1,840 行 + service 53f/11,193 行 = **116f/16,898 行**，与审计单元声明精确吻合；基线 `_tmp/cra-baseline-nop-code-index.txt` 三 roots @ 7b0ccc89d4 一致。**覆盖核验通过。**

## 最终保留项

- **立案保留 12/12，无驳回、无降级、无升级**：C7-001（P1，独立缺陷，守卫子项归并 5.6 W-04/CS-R2-001，与 W-04 交叉引用）、C7-002（P2）、C7-003（P2，缺陷保留，归并 C6-001 主登记为别名）、C7-004（P2）、C7-005（P2，搜索双写族**主登记**，吸并 C6-005）、C7-006（P3，(a) 独立 /(b) 交叉引用 C1-003+5.6 CS-R2-004 /(c) 别名归 C6-003）、C7-007（P3）、C7-008（P3）、C7-009（P3）、C7-010（P3）、C7-011（P3，归并 C6-009 主登记为别名）、C7-R2-001（P3）。
- **CL 确认 5/5**：CL-301~305 全部确认（CL-301=CL-201、CL-303=CL-202 双登记注记；CL-304=C1-001 主登记；CL-302/305 留 C1/C3/C9 域）。
- 跨维归并总表（防双计数）：C7-003→C6-001（别名）；C7-005←C6-005（主登记）；C7-011→C6-009（别名）；C7-006(c)→C6-003（别名）；C7-001 守卫子项→5.6 W-04。
- 判级分布：P1×1 + P2×4 + P3×7（与初审一致；归并仅改登记归属，不改判级）。
