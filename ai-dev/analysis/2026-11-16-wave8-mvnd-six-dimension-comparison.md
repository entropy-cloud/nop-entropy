# Wave 8 — mvnd 六维机制对照 + Gradle 经验裁定

- **Status**: open
- **Date**: 2026-11-16
- **Plan**: [ai-dev/plans/nop-ib/wave8-mvnd-gradle-comparison.md](../plans/nop-ib/wave8-mvnd-gradle-comparison.md) Phase 1 + Phase 3
- **证据源**: `~/sources/mvnd`（本地只读 checkout）；`nop-tools/nop-ib`（branch `feature/nop-ib`）；`ai-dev/design/nop-incremental-build/04`（Gradle 分析）、`05`（引擎设计 §6.5/§7）、`06`（worker 复用设计）
- **裁定约束**: nop-ib 三条硬约束——零假绿（宁可多跑不可漏跑，design 05 §6.5）、不替换构建系统（mvn 是权威模型，ib 只做 fast-path + 兜底回退）、不做成 daemon（"不替换构建系统"的推论，wave8 Non-Goals 显式排除）。

---

## 一、六维对照表

| # | 维度 | mvnd 机制 | nop-ib 机制 | 裁定 | 一句话理由 |
|---|---|---|---|---|---|
| 1 | 常驻进程模型 | daemon + socket 行协议 | 一次性 CLI + worker 池（测试段） | 部分可吸收（仅思想） | daemon 形态违反 Non-Goals；"warm 状态值钱"的思想已由 worker 池承接 |
| 2 | 增量判定 | **无**——逐 reactor 全跑，靠 warm JVM 提速 | git 变更集 + pom re-hash + 模型/索引选择性剪裁 | ib 独有；mvnd 无可吸收物 | mvnd 源码树内 grep `ncremental` 零命中；它不削工作，只摊薄启动成本 |
| 3 | 缓存层 | 进程内 realm/artifact 缓存 + WatchService 失效 | 索引持久化（`.nop-ib/`，跨进程） | 不可吸收 | mvnd 缓存活不过 daemon 死亡；ib 的跨进程索引是同目标更强形态 |
| 4 | 测试调度 | surefire 原样复用（-T 模块级并行） | 自研 worker 池 + 类级派发 + 差分重跑兜底 | 不吸收（保持现状） | 复用 surefire = 放弃类级剪裁这一 ib 核心收益；mvnd 侧无非surefire路径可学 |
| 5 | 失效语义 | 缓存条目按 jar 变更事件失效；构建本身每轮全量 | 过期即全量/回退 Maven（design 05 §6.5 放宽矩阵） | 可吸收（验证了 ib 方向） | mvnd 的教训恰恰是"不做失效判定 → 每轮全量"；ib 用保守矩阵把"不确定"显式数据化 |
| 6 | 并发治理 | Gradle 式 daemon 过期策略 + `-T` 线程数 | repo 级 BuildLock + worker 预算 + 拓扑序 | 部分可吸收 | worker 池的预算/轮换已对齐 mvnd 的"资源约束下公平并行"；daemon 过期策略无对应物 |

### 1.1 常驻进程模型（daemon vs CLI + worker 池）

- mvnd：`Server`（`daemon/src/main/java/org/mvndaemon/mvnd/daemon/Server.java:77`）常驻，socket accept + `Message` 行协议（`common/src/main/java/org/mvndaemon/mvnd/common/Message.java:353` `BuildRequest`、`:409` `BuildFinished`、`:438` `BuildException`）；`noDaemon` 模式退化为一次性进程（`Server.java:83,118,220`）。客户端 `client/src/main/java/org/mvndaemon/mvnd/client/DaemonConnector.java` 负责注册表查找/兼容匹配/拉起。
- nop-ib：`NopIbMain.run()`（`nop-tools/nop-ib/src/main/java/io/nop/ib/NopIbMain.java:41`）单次进程跑完整管线；唯一常驻形态是测试段 `JUnitWorkerPool`（`junit/JUnitWorkerPool.java:29`），且 worker 生命周期被限制在单次 CLI 调用内（设计 06 WR-D1：每模块 W 个 worker，per-class fork 为污染兜底）。
- **裁定：不可吸收 daemon 形态**（wave8 plan Non-Goals："把 nop-ib 改造成 daemon 形态"）。**可吸收思想**：mvnd 的收益全部来自"摊薄 JVM/插件容器冷启动"——这与 ib worker 池的 spawn 前移（`JUnitWorkerPool.java:67-99` 并行 pre-spawn，注释明言"on-demand spawning degenerated into per-class cold JVMs"）是同一杠杆，已吸收，无需变更。

### 1.2 增量判定（mvnd 依赖 mvn4 incremental builder？）

- **实测修正**：wave8 计划与 roadmap 的预置假设"mvnd 依赖 mvn4 incremental builder"在 `~/sources/mvnd` 源码树内**不成立**——`grep -r 'ncremental' --include='*.java'` 全树零命中（含 daemon/agent/helper）。mvnd 的构建执行就是把 Maven 4 cling invoker（`daemon/src/main/java/org/apache/maven/cli/DaemonMavenCling.java`、`DaemonMavenInvoker.java`，后者仅 137 行）搬进常驻 JVM，每个 reactor 每轮按 Maven 生命周期全量跑。mvnd 的提速来自：warm JVM + 进程内缓存（维度 3）+ 默认 `-T` 并行（`common/src/main/java/org/mvndaemon/mvnd/common/Environment.java:196-201` `MVND_MIN_THREADS`/`MVND_THREADS`）。
- nop-ib：`GitChanges.changedPaths()`（`engine/GitChanges.java:21`）取变更集 → `ModelStore.stalenessReason()`（`model/ModelStore.java:75`，pom re-hash，"pom changed"/"pom missing" 即过期）→ `ScopePlanner.plan()`（`scope/ScopePlanner.java:83`）做模块分类与测试剪裁 → `IncrementalCompileExecutor` 做文件级增量编译（`compile/IncrementalCompileExecutor.java:126-167`：删除淘汰类后再编译）。
- **裁定：不可吸收（无物可吸）**。mvnd 在这个维度是零；对照价值在维度 5。

### 1.3 缓存层（事件监听/构建缓存扩展 vs 索引持久化）

- mvnd 的"构建缓存"不是 Gradle 意义上的 content-addressable 输出缓存，而是 **Maven 内部 realm 缓存的进程内替换**：`daemon/src/main/java/org/mvndaemon/mvnd/cache/invalidating/InvalidatingPluginArtifactsCache.java`、`InvalidatingProjectArtifactsCache.java:53`（`@Priority(10)` 覆盖默认实现）、`InvalidatingPluginRealmCache.java` 等，底层 `WatchServiceCacheFactory`（`cache/impl/WatchServiceCacheFactory.java:48`）用 NIO `WatchService` 监听 jar 增删改来失效条目；另有反射式 `Server.clearCache(clazzName, fieldName)`（`Server.java:201`）兜底清空静态 Map。全部**进程内存活物**，daemon 死亡即清零，无磁盘持久层，无跨构建复用。
- nop-ib：索引持久化到 `.nop-ib/`（`index/UnitIndexStore.java:17-22`、`model/ModelStore.java:21-22` `model.json`），跨进程存活；模型过期由 pom re-hash 判定（`ModelStore.java:75-91`）；`IbEngine.refreshIndexesIfModelChanged()`（`engine/IbEngine.java:96`）串起模型→索引失效链。
- **裁定：不可吸收**。mvnd 缓存的正确性依赖 daemon 常驻 + watch 事件即时失效；ib 单次进程模型下拿不到 watch 生命周期，而 ib 的磁盘索引 + re-hash 判定在无 daemon 前提下达成同等"判定便宜"目标，且 mvnd 侧没有"输出级复用"（无 build cache of task outputs）可搬。

### 1.4 测试调度（surefire 复用 vs 自研 fork/worker）

- mvnd：**surefire 原样**。全树 `grep 'urefire'` 仅命中协议消息类一处，无 fork 定制、无类级调度；测试并行度完全继承 Maven 的 `-T` 模块级并行 + surefire 自身 forkCount。
- nop-ib：设计 06（`ai-dev/design/nop-incremental-build/06-junit-worker-reuse-and-generalization-design.md` WR-D1~D8）自研 worker 池：类级派发（WR-D7 派发面=可执行测试类）、worker 轮换 + 差分重跑兜底（WR-D4 零假绿三件套）、surefire 不可镜像配置显式回退 surefire（`model/EffectivePomParser.java:168-191` resolveSurefireExtras；`junit/SurefireClassFilter.java` 镜像 tag 过滤）。
- **裁定：不可吸收（mvnd 无增量价值）**。mvnd 的 surefire 复用等价于 ib 的 L-fallback 路径本身；ib 的类级剪裁是核心收益来源（wave3 已交付），向 mvnd 靠拢=放弃收益。

### 1.5 失效语义（mvnd 全量兜底 vs ib 过期即全量）

- mvnd：失效语义是"**不判定，故全量**"——每轮构建对每个模块重跑完整生命周期（compiler-plugin 自己按时间戳做 target 目录内的类级增量，这是 Maven 4/maven-compiler-plugin 的行为，不是 mvnd 的）；缓存失效只影响解析类条目，不影响构建范围。等价于"全量兜底常开"。
- nop-ib：任何不确定信号显式降级（design 05 §6.5 放宽矩阵："宁可多跑，不可漏跑"）：资源/XLang/L2 处理器/AOP/argLine → 回退 Maven（`compile/MavenFallback.java:34` delegate）；索引不健康 → 全量；`ScopePlanner.java:97` 注释明确"Java/aspect 源变更强制…exact fake-green shape"的保守处理。
- **裁定：可吸收（方向验证）**。mvnd 证明"无判定能力的工具只能全量"，而 ib 的零假绿约束本来就要求"不确定→显式回退"。mvnd 的 lesson 是**反面教材确认**：ib 不应试图在无 watch/无 daemon 条件下模仿 Gradle 式精细失效；现有 re-hash + 放宽矩阵是正确形态。唯一可借鉴点：mvnd `DaemonExpiration`（`daemon/.../DaemonExpiration.java:65-73` `master()`）把每个失效原因结构化为 `DaemonExpirationResult(reason, status)`——ib 的 `stalenessReason` 返回字符串原因已同构，无变更需求。

### 1.6 并发治理

- mvnd：daemon 侧 Gradle 移植的过期策略（gc thrashing / 低内存 / idle 超时 / 重复 daemon 宽限，`DaemonExpiration.java:65-109`）+ `MVND_KEEP_ALIVE`（`Environment.java:185`）+ 构建侧 `-T` 线程（`:198`）。治理对象是**守护进程生命周期**与**模块级并行度**。
- nop-ib：治理对象是**并发正确性 + worker 资源预算**：`BuildLock`（`lock/BuildLock.java:25-42`，repo 级互斥，EXIT_LOCK_TIMEOUT=75）防并发写索引/产物；`JUnitWorkerPool` worker 预算与轮换（设计 06 WR-D6 对齐 `mvn -T 1C × forkCount` 超订轮廓）；编译按 reactor 拓扑序（design 05 §7.1）。
- **裁定：部分可吸收**。mvnd 的 daemon 过期策略无对应物（ib 无 daemon，无需过期）；`-T` 式模块级并行已由设计 06 WR-D6 承接。无需新增吸收项。

---

## 二、Phase 3a — Gradle 经验五项裁定

（对照 `ai-dev/design/nop-incremental-build/04-gradle-incremental-compilation-analysis.md`；roadmap Wave 8 checklist 160-166 行的五项）

### G-1 事务化任务输出（CompileTransaction stash/rollback） — **不吸收（暂缓），登记差距**

- Gradle：`CompileTransaction.execute()` 把将被删除的输出**先 move 到 stash**，编译失败再回滚（04 §4.6，`transaction/CompileTransaction.java:101-170`）。
- ib 现状：design 05 §7.1（:196 行）**规格里写了**"失败时事务化回滚（stash/restore）"，但 `IncrementalCompileExecutor` 代码内无 stash/rollback 实现（grep 零命中）；当前实际防线是 javac 预验证（prevalidation）先于删除（`IncrementalCompileExecutor.java:137` 附近：先收集 files/deletedSources、预验证通过才删除 `:164-167`），缩小但未消除半写窗口。
- **裁定：不吸收为独立 Feature**。理由：失败后的半写产物会被下一轮 mvn 全量重建覆盖（不替换构建系统约束兜底了正确性），风险面仅剩"失败后立即跑 ib fast-path 且索引仍判健康"一条路径；现有缓解（预验证后才删除 + 失败原因落日志）已覆盖主流路径。**登记 Follow-up**：把 §7.1 的 stash/restore 规格与代码对齐（实现它，或把规格降级为"预验证+删除后编译"并记录残余风险），二选一，归 wave 后续 plan，不在 wave8 实施。

### G-2 content-addressable build cache（输入哈希→输出复用） — **不吸收**

- Gradle：归一化输入 → cache key → 产物跨 worktree/跨机复用（04 §5，`DefaultCachingStateFactory.java:49-84`）。
- 裁定依据（Deferred 26 维持，不翻案）：(a) ib 的优化顺序是"不启动 Maven > 不编译 > 少编译"（design 05 :299 行），剪裁已把"要编的模块"压到很小，跨机缓存收益边际小；(b) 可迁移缓存 key 需要输入归一化（路径/行尾/JDK 无关），而 ib 的编译输入含被物化的绝对路径 classpath 与注解处理器面，做归一化即重造 Gradle 的 normalization 层；(c) 零假绿约束下，缓存命中产物无法本地验证等价性，一旦上游（JDK/compiler 版本）漂移，命中即假绿风险——与 §6.5"不确定→全量"方向冲突。**维持 Deferred 26**。

### G-3 文件系统监听（VFS/watcher 前半） — **不吸收**

- Gradle：watcher + 跨构建 VFS 把变化判定从 O(仓库) 降到 O(改动)（04 §6.3），但其收益**以常驻进程为前提**——事件在 daemon 存活期内积压并被消费。
- 裁定依据（Deferred 26 前半维持）：ib 是一次性 CLI（维度 1 裁定），单次进程内注册 WatchService 拿不到"上一次构建之后"的事件，只能覆盖本次进程生命周期内自己写的文件——而那些路径 ib 本来就知道（编译器写产物、runner 写日志），watch 是纯冗余。跨调用间隙的变更检测由 `GitChanges`（`engine/GitChanges.java:21`）以 git 为准，比 watch 的丢失窗口（daemon 死亡期间事件不可回放）更完备。**不吸收**。

### G-4 configuration cache / 模型过期（Gradle configuration cache vs model.json） — **已吸收（确认现状即对应物），补一处显式化**

- 对应关系：Gradle configuration cache 跳过"配置阶段重算"；ib 的 `.nop-ib/model.json`（`ModelStore.java:21-22`）跳过"effective-pom 重算"，两者都是"派生自权威源的可缓存配置快照"。
- 过期策略对照：Gradle 的 configuration cache 在任何配置输入变化时失效并回退正常配置；ib 的 model.json 在**任何 pom re-hash 变化或 pom 缺失**时失效（`ModelStore.java:75-91`），过期后不猜、不修补，全量 refresh（`ModelRefresher.refresh()`，`model/ModelRefresher.java:52`）。这与零假绿同构，**确认现状，无需翻案**。
- 唯一差距与 mvnd 对照一致（维度 5）：ib 的 re-hash 是 O(pom 数) 全量比对（465 个 pom），无增量指纹。规模实测可接受（wave7 bench 未把 refresh 列为瓶颈），**不吸收 Gradle 的 fingerprint 机制**；若未来 refresh 进入热点，Follow-up 走"文件 mtime 短路 + hash 校验"（Gradle `CachingFileHasher` 的 (hash,length,timestamp) 三元组，04 §6.2）而非完整 fingerprint 层。

### G-5 worker API 的隔离与复用（Gradle ProcessIsolatedCompilerWorkerExecutor vs 设计 06 worker 池） — **已吸收（design 06 即本地化实现）**

- Gradle：编译器可在进程隔离 worker 中执行，避免污染 daemon（04 §七表，`DefaultJavaCompilerFactory.java:19,75-90`）；worker 是常驻、可复用、按 lease 分配。
- ib：设计 06 WR-D1/D2/D4/D6 即同一模型在"无 daemon"前提下的本地化：持久 worker、启动 classpath 定死不换 loader、污染用差分重跑+轮换兜底、模块槽位并行对齐 mvn 双层超订。实现已落地（`JUnitWorkerPool.java:29`，wave6 交付）。
- **裁定：已吸收，无新增变更**。Gradle 侧唯一未搬的是"worker 内编译器隔离"——ib 的 javac 走进程内 facade（`javac/JavaCompilerFacade.java`），因 ib 每次 CLI 调用本身就是进程边界，污染面与 Gradle daemon 不同，无需隔离层。

---

## 三、mvnd 侧意外发现（影响后续 wave 论证口径）

1. **mvnd 源码树内没有任何增量构建代码**（`grep 'ncremental'` 零命中）——roadmap :162 行与 wave8 plan Current Baseline 中"mvnd 依赖 mvn4 incremental builder"的表述需要修正：mvnd 的全部收益 = warm JVM + 进程内解析缓存 + 默认并行。这让 Phase 2 的 wall-clock 对照预期清晰：mvnd 只在"冷启动段"有优势，"编译/测试段"与 mvn 相同。
2. mvnd 的缓存失效走 NIO WatchService（`WatchServiceCacheFactory.java:48`），失效粒度是 **jar 文件**而非源文件——即 mvnd 对"源码变更"完全无感知，与维度 2 结论一致。
3. mvnd 的 daemon 过期策略文件头自注移植自 Gradle 5.6.2 `MasterExpirationStrategy`（`DaemonExpiration.java:52-56`）——mvnd 与 Gradle 在"daemon 生命周期治理"上同源，进一步支持 G-5"不搬 daemon 治理"的裁定。

---

## 四、结论

- 六维中 mvnd 对 ib 的**净新增吸收为零**；两处"部分可吸收"确认现有实现已承接（worker 池 warm 思想、模块级并行轮廓），两处"可吸收"均为方向验证（反面教材 / 同构确认）。
- Gradle 五项：G-1 不吸收但登记 §7.1 规格与代码差距（Follow-up）；G-2/G-3 维持 Deferred 26 不翻案；G-4/G-5 确认已吸收无变更。
- Phase 2 实验预期更新：mvnd 优势段 = JVM/插件冷启动；ib 优势段 = 编译剪裁 + 测试类级派发；"mvnd 失效即全量兜底 vs ib 索引选择性"的实测体现应在**增量编译段**与**测试段**，而非启动段。

---

*Citations: mvnd paths relative to `~/sources/mvnd`; nop-ib paths relative to `nop-tools/nop-ib/src/main/java/io/nop/ib/`（行号为 2026-11-16 快照）。*
