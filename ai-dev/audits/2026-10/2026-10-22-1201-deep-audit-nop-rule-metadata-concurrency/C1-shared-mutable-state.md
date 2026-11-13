# C1 维度初审：共享可变状态与可见性（盲审批次 1/2：批 A + 批 B）

- **审计单元**: nop-rule（批 A，全部 72f：nop-rule-api 16f 只读 / nop-rule-core 34f / nop-rule-dao 13f / nop-rule-service 8f / nop-rule-app 1f）+ nop-metadata-service 主战场（批 B，22f：quality 8f + reconciliation 3f + event 1f + sync 3f + profiling 3f + catalog 2f + connection 2f）
- **维度**: C1 共享可变状态与可见性
- **盲审声明**: 本文件为初审轮次 1/2 的盲审产出。撰写过程中未读取 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/` 下任何既有记录；全部结论仅基于 live code 与 `_tmp/cra-baseline-nop-rule-metadata.txt`（Phase 1 锚点基线，commit 81a7c228e7）。现状标注（新发现/已知未修复）留待复核阶段。
- **CL 前缀**: `nop-rule-metadata-CL-0NN`（本文件维度集外线索编号沿用此前缀，供跨维度合并裁定）
- **审计面**: 按基线锚点核实补记执行——main 面 static 可变**字段**直接命中≈0（基线再订正已确认），故 C1 审计面为：① 实例字段 × 单例/BizModel 载体；② 注册表/holder 间接形态；③ ThreadLocal（全单元仅 1 处命中，位于 src/test）。`_gen/` 与 `_` 前缀生成物不作发现对象。
- **复核状态**: 全部条目「未复核」（初审轮 1/2）

---

## 一、共享可变状态载体盘点表（五类载体 × 写者 × 读者 × 同步机制 × 判定）

### 载体类 1：static 可变容器（main 面）

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|---------|------|
| S1 | `CrossDbConfigHolder.maxCrossDbRows`（public static **非 final** int，holder 形态） | `nop-metadata-service/.../query/CrossDbConfigHolder.java:5` | main 面无写者（仅测试注释引用）；但为 public 可变全局 API | `MetaJoinExecutor.java:74,404,436`、`CrossDbJoinMerger.java:29`（构造期快照入 final 字段） | 无（非 volatile） | **风险候选——移交批 C**（详见 §三 H1；基线 C1a 按容器类型过滤未覆盖 plain int，属锚点漏检） |
| S2 | `NopRuleApplication.globalArgs`（static 非 final String[]） | `nop-rule-app/.../NopRuleApplication.java:22` | `main()`（L38，单线程启动期） | Quarkus `start()` observer（L25） | Quarkus 启动序列建立 happens-before | 安全（标准 bootstrap 形态；main 先于任何 worker 线程） |
| S3 | `GranularityBucketing.H2_PG_TEMPLATES` / `MYSQL_TEMPLATES`（static final HashMap） | `query/GranularityBucketing.java:30,32` | 仅 static initializer（L35-52） | `templates.get(...)`（L76/79 读路径） | 类初始化锁（JLS 12.4 safe publication） | 安全（初始化后事实不可变；全文件 grep 无运行期 put/remove——已验证） |
| S4 | `MetaQualityScorer.DEFAULT_WEIGHTS`（static final LinkedHashMap） | `quality/MetaQualityScorer.java:70` | 仅 static block（L72-76） | `DEFAULT_WEIGHTS.get(dim)`（L182） | 类初始化锁 | 安全（同 S3，grep 确认无运行期写者） |
| S5 | static final Set/Pattern/DateTimeFormatter/String[][] 群（`ALLOWED_JDBC_PROTOCOLS`、`DANGEROUS_URL_TOKENS`、`ALLOWED_DRIVER_CLASSES`、`CUSTOM_SQL_FORBIDDEN_WORDS/SEQUENCES`、`ALLOWED_WEBHOOK_PROTOCOLS/METHODS`、`SENSITIVE_COLUMN_FALLBACK`、`ALLOWED_STATUSES`、`KEYWORD_BLACKLIST` 族、`IDENTIFIER_PATTERN` 族、`ISO_UTC`、`TABLE_TYPES` 数组等，基线 C1b 31 处） | 各文件（清单见基线 C1b 文件分布） | 仅声明点/静态初始化 | 各校验/判定热路径（只读遍历/contains） | 类初始化锁 + unmodifiableSet/Set.of 包装 | 安全（Pattern/DateTimeFormatter 线程安全；String[] `TABLE_TYPES` 内容无运行期写点——已验证 `.add/.remove/.clear` 不触及） |

### 载体类 2：单例 bean / BizModel 实例字段

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|---------|------|
| B1 | `RuleServiceImpl.ruleManager/saveLogMessage/logMessageSaver`（非 final，setter 注入） | `nop-rule-core/.../RuleServiceImpl.java:41-43` | IoC 容器启动期（beans.xml + `@Inject` setter） | `executeRule`/`getRuleMeta` 请求线程（L62/70/79） | IoC init 完成先于请求服务的线程启动（容器启动 HB） | 安全（write-once；运行期无写点，setter 无 GraphQL/HTTP 暴露面） |
| B2 | `MetaQualityCheckpointScheduler.scheduler/daoProvider/checkpointBizModel`（`@Inject`，scheduler `@Nullable`） | `quality/MetaQualityCheckpointScheduler.java:93-100` | IoC 启动期 | `init()` scanner（`@PostConstruct`）、`registerCheckpoint/unregisterCheckpoint`（BizModel save/delete 请求线程）、`executeScheduledCheckpoint`（job invoker 线程） | IoC init HB；自身无可变编排状态，委托平台 `IJobScheduler` | 安全（本类无可变状态；跨线程一致性由平台调度器承担） |
| B3 | `MetaQualityCheckpointExecutor` / `MetaQualityRuleExecutor` / `QualityResultWriter` / `ReconciliationExecutor` / `MetaModelChangedEventPublisher` / `MetaEntityProfiler` / `MetaCatalogCollector` / `ExternalTableStructureReader` / `LocalReconciliationProcessor` | 各文件（构造器 final 注入或纯静态工具） | 构造期一次 | 请求/job 并发线程 | final 字段安全发布 | 安全（无状态编排器——逐文件字段扫描确认无非 final 实例字段） |
| B4 | `CheckpointActionDispatcher.webhookAllowedHostsCsv/webhookTimeoutSeconds/followRedirectsEnabled`（protected 非 final 配置字段 + public mutator） | `quality/CheckpointActionDispatcher.java:88,91,99` | `configureWebhookSsrf/configureRedirectPolicy`（仅 `NopMetaQualityCheckpointBizModel.ensureActionDispatcher` 创建期调用一次；测试 setter） | `dispatchWebhook` 投递线程（cron + 手动并发） | 无（非 volatile；可见性窗口见 C1-001） | **风险（并入 C1-001 判定）** |
| B5 | BizModel `ensure*` 惰性初始化字段群（`actionDispatcher`/`tableRefExecutor`/`checkpointExecutor`/`scorer` 等 9 处同族） | 见 C1-001 失效面枚举 | 各 BizModel 请求线程（check-then-act） | 同上 | 无（非 volatile、非 synchronized） | **风险 → C1-001** |
| B6 | `MetaDataSourceConnectionProcessor.allowedInternalHostsCsv/h2FileAllowedDirsCsv`（`@InjectValue` protected 非 final）+ `credentialProvider`（`@Inject` setter） | `connection/MetaDataSourceConnectionProcessor.java:158,169,176` | IoC 启动期（配置注入/optional 注入） | `withConnection` 各调用方线程（9+ 处，含 query/sqlview/tableref 包） | IoC init HB | 安全（write-once；但构造器写 JVM 全局状态 → C1-002） |
| B7 | `QualityAlertWorkflowProcessor.daoProvider/connectionService/orm/wfManager`（`@Inject` protected）+ `executor/tableRefResolver`（final 行内初始化）+ `tableRefExecutor`（非 final 惰性） | `quality/QualityAlertWorkflowProcessor.java:41-56` | IoC 启动期 / 首用线程 | 告警处理线程 | 注入 HB / 无（惰性字段 → C1-001 同族） | final 部分；`tableRefExecutor` 并入 C1-001 |
| B8 | `DaoRuleModelLoader.daoProvider`（包级可见，`@Inject` setter） | `nop-rule-dao/.../DaoRuleModelLoader.java:57` | IoC 启动期 | RCM 加载线程 | IoC init HB | 安全 |
| B9 | nop-rule-service 4 个 BizModel（`NopRuleDefinitionBizModel` 等） | `nop-rule-service/.../entity/` | 仅 `@Inject ruleModelLoader`/构造器 setEntityName | 请求线程 | IoC init HB | 安全（无缓存型可变实例字段；`getConditionFields` 用局部 RuleModel，不共享） |

### 载体类 3：ThreadLocal

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|---------|------|
| T1 | `LAST_ORM`（`ThreadLocal<IOrmTemplate>`） | `src/test/.../TestEntityAggregationProcessorOrmDialectWiring.java:166`（set 于 L69，无 remove） | 测试线程 | 同线程 `lastOrm()` | ThreadLocal 语义 | 安全（main 面 **0** 命中——基线 C1c=1 全部落在 src/test；仅存测试线程复用下的 mock 引用滞留，属 P3 测试卫生注记，不立案）。main 生产面无 ThreadLocal；`RuleRuntime` 的上下文经构造参数显式传递（`svcCtx`/`scope`），`ContextProvider.getOrCreateContext()`（`RuleRuntime.java:72`）仅在 svcCtx==null 的离线分支取 ThreadLocal，同线程内使用后不跨线程——无断链 |

### 载体类 4：全局注册表 / 缓存 / holder 间接形态

| # | 载体 | 位置 | 写者 | 读者 | 同步机制 | 判定 |
|---|------|------|------|------|---------|------|
| R1 | `ResourceComponentManager` 全局模型缓存上的 `RuleModel.executableRule` 编译缓存（check-then-act） | `nop-rule-core/.../RuleModel.java:28`（非 volatile 字段）+ `RuleManager.java:55-65`（`synchronized(ruleModel)` 守卫） | `RuleManager.getExecutableRule`（锁内）、`RuleModelCompiler.compileRule:72`（经 L60 在锁内 / 经 `DaoRuleModelLoader.loadObjectFromPath:77` 在 RCM 发布前单线程加载期） | 同锁内 reader（L57）；全单元 grep 确认 `getExecutableRule()` 仅 `RuleManager` 调用 | `synchronized(ruleModel)`（同一缓存实例上的监视器锁）+ RCM 缓存 put 安全发布 | **安全（锁完备）**，附脆弱性注记：字段非 volatile 且 getter/setter 为 public，安全性完全依赖「所有访问方记得先 synchronized(ruleModel)」的隐式约定；未来新增绕锁访问点即成竞态。建议复核阶段评估是否将字段收窄进 RuleManager 或改 volatile+锁双保险（P3 改进候选，不立案） |
| R2 | `RuleModel.inputSchema` 惰性初始化（非 volatile，check-then-act） | `RuleModel.java:30,70-88` | `getInputSchema()` 首调线程 | `NopRuleDefinitionBizModel.getConditionFields:62` | 无 | 安全（当前唯一调用点作用于 `buildRuleModel` **局部**实例——不跨线程共享；注记：若未来对 RCM 缓存模型调用将成竞态，建议在 owner doc 标注约束） |
| R3 | `NopMetaQualityCheckpointBizModel.checkpointRunLocks`（final ConcurrentHashMap 运行标记） | `entity/NopMetaQualityCheckpointBizModel.java:171` | `putIfAbsent`（L203）/ `remove`（L268） | 同 map | ConcurrentHashMap 原子操作；获取-释放配对（putIfAbsent 失败即抛，owner 唯一性保证 remove 不误删他人标记；最外层 finally 覆盖 executor+autoScore+dispatch 全程） | 安全（R4.3 契约实现正确；key=checkpointId 有界受元数据规模约束） |
| R4 | `EXTERNAL_TABLE_UPSERT_LOCKS`（static final ConcurrentHashMap，per-key 锁注册表）+ `EXTERNAL_MODULE_ENSURE_LOCK` | `entity/NopMetaDataSourceBizModel.java:1053,1056`（批 C 范围，此处仅登记） | `computeIfAbsent`（L1070） | `upsertExternalTableGuarded` | ConcurrentHashMap + synchronized(per-key lock) | 安全-已声明边界（类注释明示「key 集合有界（受元数据规模约束），长期持有可接受；单实例 baseline（R4.3 已裁定）」——按校准规则 6，已声明边界不作缺陷；持锁内 DB 事务形态转 CL-001） |
| R5 | `VarCollector.instance()`（平台 unittest 包单例）在规则热路径调用 | `RuleRuntime.java:201` | 平台内部 | 平台内部 | 平台设施（nop-core）自身实现 | 安全（按校准：平台基础设施只审使用方式；采集器未启用时为 no-op，热路径注记见 CL 附言） |

### 载体类 5：经集合/数组/可变对象逃逸的隐式共享

| # | 载体 | 位置 | 判定 |
|---|------|------|------|
| E1 | 编译产物 `ExecutableRule`/`RuleDecider`/`ExecutableMatrixRule`/`RuleOutputAction`/`TraceVarsExecutableRule`/`DecoratedExecutableRule`/`NormalizeInput/OutputExecutableRule`/`MainExecutableRule` | `nop-rule-core/.../execute/`、`model/compile/` | 安全（全 final 字段；构造后无写点；经 `synchronized(ruleModel)` + RCM 缓存 put 安全发布；执行期只读） |
| E2 | `RuleRuntime`（每请求新实例；`logMessages` ArrayList / `outputLists` HashMap / `outputs` LinkedHashMap 均为普通容器） | `nop-rule-core/.../RuleRuntime.java:41-45,68` | 见 C1-003（构造器注释自认「taskRt可能会被多线程访问，所以这里scope线程安全」，scope 用并发容器而输出三容器为普通实现——契约不对称；当前 nop-rule 执行面无可具体化的跨线程写时序 → P2 理论风险） |
| E3 | `RuleServiceImpl.executeRule` 将 `request.getInputs()`（反序列化 Map）置入 ruleRt 并被表达式读取 | `RuleServiceImpl.java:63` | 安全（单请求单线程生命周期，请求结束即弃） |
| E4 | `MetaQualityCheckpointExecutor.execute` 局部 `results/errors/affectedTableIds` 经 summary Map 返回后被 `dispatchActions` 投递（`JsonTool.stringify(summary)` 序列化 + `envelope.put("summary", summary)` 活引用交给 IMessageService） | `MetaQualityCheckpointExecutor.java:201-202`、`CheckpointActionDispatcher.java:500` | 安全-注记（summary 在 dispatch 前已完成填充且 dispatch 后无写者；若未来 IMessageService 异步消费该活引用而调用方又修改 summary，将成 F1 快照撕裂——当前无异步消费实现，登记为观察项不立案） |

安全发布核查（C1 步骤 3）：两单元 main 面无「构造器启动线程」「构造器注册监听器」形态（基线 C4a 8 命中全部位于 src/test，本机复核确认）；long/double 复合写、`++` 共享递增、共享 SimpleDateFormat/Random：main 面 0 命中（`CoreMetrics.currentTimestamp()` 为平台封装，`DateTimeFormatter` 不可变）。

---

## 二、发现条目

### [C1-001] BizModel 单例 `ensure*` 惰性初始化 check-then-act 家族（含 dispatcher 配置后置写入的可见性窗口）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java:531-537`（主证据）；同族失效面见下方枚举
- **证据片段**:
  ```java
  private CheckpointActionDispatcher ensureActionDispatcher() {
      if (actionDispatcher == null) {
          actionDispatcher = new CheckpointActionDispatcher(httpClient, messageService);   // L533 先发布引用
          actionDispatcher.configureWebhookSsrf(webhookAllowedHostsCsv, webhookTimeoutSeconds);  // L534 后置配置
          actionDispatcher.configureRedirectPolicy(httpClientFollowRedirects);              // L535 后置配置
      }
      return actionDispatcher;
  }
  ```
- **严重程度**: P2
- **并发场景**: BizModel 单例的并发请求线程——cron job invoker 线程（`executeScheduledCheckpoint` → `executeCheckpoint` → `dispatchActions`）与 HTTP worker 线程（手动 `executeCheckpoint`）首次并发触发同一检查点编排链时；同族 `ensureTableRefExecutor`/`ensureCheckpointExecutor`/`ensureScorer` 在任意并发首次调用点
- **触发时序**: T1（cron invoker 线程）执行到 L533 完成字段赋值（此时 dispatcher 的 `webhookAllowedHostsCsv=""`、`webhookTimeoutSeconds=30`、`followRedirectsEnabled=false` 均为默认值）→ T1 被调度暂停，尚未执行 L534-535 → T2（HTTP worker）进入 `ensureActionDispatcher`，L532 判 `actionDispatcher != null` 成立，直接返回该实例 → T2 以**未注入部署配置**的默认值执行 `dispatchWebhook`（部署时配置的内网 allowlist host 被拒、配置的超时秒数未生效）。此外 T1/T2 双双通过 L532 null 检查时会产生重复构造（后者胜出，前者配置丢失无告警）。`actionDispatcher` 字段非 volatile（`NopMetaQualityCheckpointBizModel.java:160`），即使时序上 configure 已执行，T2 也无 happens-before 保证看到配置后的字段值
- **现状**: `if (field == null) field = new X(); return field;` 无同步、无 volatile 的惰性初始化；对 `actionDispatcher` 而言还在字段发布**之后**才执行两步 configure（发布-配置倒序）
- **风险**: 首次并发窗口内投递行为偏离部署配置——SSRF allowlist 缺失为 fail-closed（拒绝而非放行，安全面无扩大）、redirect 默认 false 亦 fail-closed、超时回落 30s；故后果限于可用性/配置语义偏差与重复对象，无数据损坏。窗口仅存在于进程首次使用（之后字段恒非 null），概率低但时序可具体化
- **建议**: 对惰性字段加 `volatile` 并将 configure 移入构造路径（`new CheckpointActionDispatcher(...)` 后先 configure 再发布引用，或改用 `synchronized` 方法 / 平台 `GlobalCacheRegistry`-类惯用 holder）；最简修复：`private volatile CheckpointActionDispatcher actionDispatcher;` + 把三步构造+configure 包进局部变量后一次赋值（`final CheckpointActionDispatcher d = new ...; d.configure...; actionDispatcher = d;` 消除发布-配置倒序）
- **失效面枚举**（F3 同族清扫，`ensure*` 字段缓存形态全量清单及逐一状态）:
  1. `NopMetaQualityCheckpointBizModel.ensureActionDispatcher`（L531）— **本条主证据，最重**（发布-配置倒序）
  2. `NopMetaQualityCheckpointBizModel.ensureTableRefExecutor`（L541）— 同模式，重复构造无害（无 post-store configure），随本条一并修复
  3. `NopMetaQualityCheckpointBizModel.ensureCheckpointExecutor`（L549）— 同上
  4. `NopMetaQualityScoreBizModel.ensureScorer`（L69）— 同上（scorer 无配置后置步骤）
  5. `QualityAlertWorkflowProcessor.ensureTableRefExecutor`（L167-171，quality 包、批 B 范围内）— 同上
  6. `NopMetaQualityRuleBizModel.ensureTableRefExecutor`（L303，entity 包，批 C 范围）— 移交合并
  7. `NopMetaDataSourceBizModel.ensureTableRefExecutor`（L870，批 C）— 移交合并
  8. `NopMetaEntityBizModel.ensureTableRefExecutor`（L430，批 C）— 移交合并
  9. `NopMetaProfilingRuleBizModel.ensureTableRefExecutor`（L133，批 C）— 移交合并
  （`CrossDbFieldResolver.ensureLeftCols` 等为每请求局部懒计算，非本族；`ensureExternalSystemModule/ensureOrmModelForModule` 已有锁守卫或 DB 幂等语义，另见 CL-001）
- **信心水平**: 确定（模式与时序为代码事实；实际触发依赖「部署后首次两路并发」这一低频前置）
- **误报排除**: 非平台标准模式——Nop 误报校准针对的是「`@Inject` protected 字段/单例无状态被并发调用」；本条是**运行期可变惰性初始化**（非 IoC 注入、非 write-once），且 `CheckpointActionDispatcher` 的配置写入发生在字段发布之后，构成具体的发布-配置倒序而非「看到共享字段就报不安全」。与「低频路径粗粒度 synchronized 合理」校准亦不冲突：本处恰恰是**零**同步
- **复核状态**: 已复核（2026-10-22 C1 独立复核）：**保留 P2**——证据全部核实（:160 非 volatile、:531-538 发布-配置倒序、configure 生产面唯一调用点 :534-535、dispatcher 缺省 fail-closed 值）；触发时序修正为「两个不同 checkpointId 的首次并发执行」（同一 checkpointId 被 R4.3 运行标记 :203 挡在 ensure 之前，锁持有至 :268 finally），同族无锁载体竞态不受此限；现状未修复。详见文末「维度复核结论」。

### [C1-002] `MetaDataSourceConnectionProcessor` 构造器写 JVM 全局状态 `DriverManager.setLoginTimeout`（跨模块全局副作用，无协调者与恢复路径）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/connection/MetaDataSourceConnectionProcessor.java:140-152,124`
- **证据片段**:
  ```java
  public MetaDataSourceConnectionProcessor() {
      setGlobalLoginTimeout();
  }
  void setGlobalLoginTimeout() {
      try {
          DriverManager.setLoginTimeout(DEFAULT_LOGIN_TIMEOUT_SECONDS);   // DEFAULT_LOGIN_TIMEOUT_SECONDS = 5
      } catch (SecurityException se) { ... }
  }
  ```
- **严重程度**: P2
- **并发场景**: IoC 容器初始化线程（写 `DriverManager` 类级 static 字段 `loginTimeout`）× 宿主 app 内任何其他使用 `DriverManager.getConnection` 的组件线程（nop-metadata 之外，如宿主自有工具、三方库的直连路径）跨模块共享 JVM 全局可变状态
- **触发时序**: T1（IoC init）执行构造器将全局 loginTimeout 置 5s（本单元内为唯一写者——已 grep 复核 nop-rule/nop-metadata/nop-dao/nop-orm/nop-commons main 面无其他 `setLoginTimeout` 调用点）→ 宿主 app 中任一其他组件 T2 调用 `DriverManager.setLoginTimeout(X)`（仓库外组件，存在性依赖部署生态）→ 此后 T3 经 DriverManager 建连的元数据连接以 X 秒为登录超时——本模块的 SSRF/挂死防护假设（内网探测连接 5s 失败）被静默放宽至 X；反向时序（T2 先 X，T1 后置 5）则宿主组件的连接超时被静默收窄为 5s。两个方向均无告警、无恢复
- **现状**: 以受控常量（5s）+ 可覆写抽象方法（测试可 mock）刻意设置，属有意的加固（`DEFAULT_LOGIN_TIMEOUT_SECONDS` 注释链与 AR-02 语境一致）；但副作用目标是**进程全局**静态字段，模块边界外不可见、不可协商、卸载/停机时不还原
- **风险**: 仓库内当前无其他写者，故无可成立的仓内交错序列——风险成立依赖宿主 app 生态中存在其他 DriverManager 使用者；一旦存在，双方互不知晓地覆盖对方的超时假设，元数据模块的连接安全边界（防内网探测挂死）可能被静默旁路。按纪律标注：**理论风险（跨模块前置未在本仓库内具体化）**
- **建议**: 不回退加固本身；将影响面显式化——(a) 在 owner doc 声明「本模块启动即接管 JVM 全局 JDBC login timeout=5s」；(b) 评估改用每连接 `DriverManager.getConnection(url, info)` 的 `loginTimeout` 连接属性（局部化，无全局副作用），仅在属性不受驱动支持时回退全局设置并 WARN
- **失效面枚举**: 全单元 grep `setLoginTimeout`——main 面**仅此 1 处**写者、0 处其他读者/写者（`MetaDataSourceConnectionProcessor` 内唯一）；无同族兄弟字段。全局状态的**读者**为 JDK DriverManager 内部（所有 `getConnection` 调用方，包括本模块 `withConnection` 链与宿主任意组件），无法枚举穷尽——这正是立条理由
- **信心水平**: 很可能（机制确定；触发依赖部署生态）
- **误报排除**: 非「原语使用不是问题」校准——问题不在 `setLoginTimeout` 原语，而在**单例 bean 构造期对进程级全局可变状态的独占写入**，属 C1 载体类 1（static 全局）的本单元唯一 main 面直接命中（基线 C1a 容器过滤同样未覆盖此类 JDK 全局 API 形态）。也非「已声明边界」：代码注释仅声明了测试可 mock 的意图，未声明跨模块全局影响面
- **复核状态**: 已复核（2026-10-22 C1 独立复核）：**保留 P2**——:150 为全仓 main 面唯一 `DriverManager.setLoginTimeout` 写点（nop-dao 三个同名方法均为 DataSource 接口实例覆写，非全局写）、单例 bean 注册（app-service.beans.xml:12-13，`ioc:default`）均核实；全局写每次启动无条件发生，与 C1-101 的「零写者、机制不存在」有本质区别，P2 判级与维度内口径一致；现状未修复。

### [C1-003] `RuleRuntime` 输出容器线程安全与自身并发声明不对称（scope 并发化而 outputs/logMessages 普通容器）

- **文件**: `nop-rule/nop-rule-core/src/main/java/io/nop/rule/core/execute/RuleRuntime.java:41-45,60-75,153-158`
- **证据片段**:
  ```java
  // L68 构造器内注释：
  // taskRt可能会被多线程访问，所以这里scope线程安全
  this.scope = scope;                       // scope 经 newChildScope(true,true) / newConcurrentMap 并发化
  private final List<RuleLogMessageBean> logMessages = new ArrayList<>();      // L41 普通容器
  private final Map<String, List<Object>> outputLists = new HashMap<>();       // L43 普通容器
  private final Map<String, Object> outputs = new LinkedHashMap<>();           // L45 普通容器
  ...
  public void addOutput(String name, Object value) {                           // L153 RuleOutputAction 热路径写入点
      List<Object> list = outputLists.computeIfAbsent(name, k -> new ArrayList<>());
      list.add(value);
      outputs.put(name, value);
  }
  ```
- **严重程度**: P2（理论风险，未具体化）
- **并发场景**: 规则执行线程与「构造器注释预想的多线程访问方（taskRt/任务步并行执行）」——注释表明作者预期 `IRuleRuntime` 关联的求值上下文可能被多线程触及，并为 scope 选择了并发容器，但 `addOutput`/`logMessage`/`setOutput` 写入的三个容器保持普通实现
- **触发时序**: 无法在本单元内具体化——`RuleServiceImpl.executeRule` → `IRuleManager.executeRule` → `ExecutableRule.execute` 全链为单请求单线程同步执行；唯一可想象的并发写方（规则表达式内触发并行任务步并回写 ruleRt 输出）在 nop-rule 现有执行面中不存在调用路径。故标注：**理论风险，未具体化**（若未来 xpl/task 生态的规则内并行求值成立，`HashMap.computeIfAbsent`+`ArrayList.add` 的并发写将产生容器结构损坏与输出静默丢失）
- **现状**: 同一构造器内对「同一运行时可达状态」作出两种不一致的线程安全决策（scope 并发、输出容器非并发），并发契约依赖注释而非结构
- **风险**: 当前无功能影响；契约不对称会误导后续开发者在并行上下文中复用 `IRuleRuntime`（注释背书 scope 安全，开发者合理外推 outputs 亦安全），届时损坏形态为 HashMap 结构破坏 + `logMessages` 丢消息，难以复现定位
- **建议**: 二选一对齐契约：(a) 若执行模型确为单线程，修正注释并去掉并发 scope 的额外成本（或注明 scope 并发仅为防御）；(b) 若多线程访问是真实预期，将 `outputLists`/`outputs`/`logMessages` 升级为并发容器或文档化「输出写入仅限执行线程」的契约并加 fail-fast 守卫
- **失效面枚举**: 同族写点全清单——`addOutput`（L153，RuleOutputAction.invoke 唯一调用方 `RuleOutputAction.java:22`）、`setOutput`（L180）、`logMessage`（L195→L210 `logMessages.add`）、`clearOutputs`（L160）；全部经由 `IExecutableRule.execute(ruleRt)` 单线程链到达，逐一确认无并行调用方；nop-rule 全单元无其他持有 RuleRuntime 引用并跨线程分发的代码（grep `ruleRt`/`IRuleRuntime` 复核）
- **信心水平**: 有趣的猜测（契约不对称是事实；并发写路径是否存在取决于平台任务生态未来演进）
- **误报排除**: 非「看到共享字段就报线程不安全」——该字段是**每请求新实例**的普通形态（本应直接判安全）；立案理由是类内注释的并发声明与容器选型的自相矛盾构成真实契约缺陷，且已按规则对不可具体化时序降级 P2 并显式标注，而非按数据竞争 P1 报告
- **复核状态**: 已复核（2026-10-22 C1 独立复核）：**保留 P2（理论风险）**——三容器（:41/:43/:45）、:68 注释、addOutput（:153-158）与单线程链（RuleServiceImpl:61-68、RuleOutputAction:22 唯一调用方）逐行核实；契约不对称是现存代码事实、并发写时序不可具体化（含跨单元唯一消费者 per-call runtime 核实），P2 理论级恰当；现状未修复。

---

## 三、跨批次移交（C1 维度内、批 C/D 文件范围；本文件仅登记不展开）

- **H1 `CrossDbConfigHolder.maxCrossDbRows`**（`query/CrossDbConfigHolder.java:5`，`public static int` 非 final）：holder 形态直接命中，基线 C1a 按容器类型过滤漏检。main 面读者 3 处（`MetaJoinExecutor.java:74,404,436`、`CrossDbJoinMerger.java:29`——后者构造期快照入 final 字段是正确形态），main 面**无写者**（当前事实只读全局），无同步机制。批 C 审计建议按「public 可变全局 API 形态缺陷」P3 立项（改 final 常量或 config 注入），并核查测试对它的写点是否与生产断言耦合。
- **H2 `GranularityBucketing.H2_PG_TEMPLATES/MYSQL_TEMPLATES`**（`query/GranularityBucketing.java:30,32`）：本机已验证仅静态初始化写入、运行期只读 → 安全；批 C 无需重复立案，但建议在 static final 化（`Map.copyOf`）上顺手收口。
- **H3 `EXTERNAL_TABLE_UPSERT_LOCKS` / `EXTERNAL_MODULE_ENSURE_LOCK`**（`entity/NopMetaDataSourceBizModel.java:1053-1096`）：已声明有界（类注释 R4.3 裁定）→ 按「已声明边界」不作缺陷；批 C 复核时仅需确认声明与实现未漂移。
- **H4 entity 包 `ensure*` 同族 4 处**（见 C1-001 失效面枚举第 6-9 项）：批 C 复核时与 C1-001 合并裁定（同根因，按「根因 + 失效层」合并计数，避免重复立项）。

## 四、维度集外线索（CL）

### [nop-rule-metadata-CL-001]（C2 形态）Java 锁内执行 DB 事务：per-key upsert 锁与全局 ensure 锁

- **线索**: `entity/NopMetaDataSourceBizModel.java:1070-1079`（`synchronized(lock)` 内 `runInTransaction(REQUIRES_NEW)` + `flushSession`）；`:1094-1097`（`synchronized(EXTERNAL_MODULE_ENSURE_LOCK)` 内 DB find+insert）。锁内获取 DB 连接并等待远端提交——连接池耗尽/远端变慢时锁持有时长不可控，全局单键锁还会串行化全部外部模块 sync。有注释裁定背书（跨 commit 可见性论证），但属 C2 锁扩展/持锁重 I/O 的定向核对对象。批 C 范围，信号强。
- **置信**: 强信号，未展开。

### [nop-rule-metadata-CL-002]（C2 形态）`RuleManager.getExecutableRule` 首编译路径在 `synchronized(ruleModel)` 内做 XLang 编译

- **线索**: `nop-rule-core/.../RuleManager.java:55-65`——首次访问某规则模型的线程在模型监视器锁内执行 `RuleModelCompiler` 全量编译（含表达式编译、可能触发模板解析 I/O）；并发首访线程全部阻塞在同一把模型锁上。锁正确性无问题（C1 已判安全），属锁粒度/持锁重操作的性能核对点（P3 候选）。nop-rule 范围（批 A）。
- **置信**: 中信号（冷路径，首访一次性），未展开。

### [nop-rule-metadata-CL-003]（C4/C7 形态）cron 链路异常语义已被处置——登记既有裁定索引，建议 C4 复核 LocalJobScheduler 侧

- **线索**: `quality/MetaQualityCheckpointScheduler.java:204-234` 的 catch-all + `isConcurrentRunRejection` 分级（注释引 MA7.5-01/MA7.5-03/R4.3 裁定：异常外泄会使 job 永久 FAILED 不可复活）。本文件按盲审纪律未读裁定原文；C4/C7 审计者应核对：WARN/ERROR 分级与 job 永久 FAILED 语义在平台 LocalJobScheduler 侧的现行行为是否仍与注释声明一致（钉定测试三方一致性口径）。
- **置信**: 中信号（注释自证已处置，仅需对账），未展开。

## 五、计数口径备忘

- 批 A 文件口径：`find nop-rule -name "*.java" -path "*/main/*"` = 72f（含 `_gen` 12f；非生成 60f，全部走过字段扫描或定向阅读）。
- 批 B 文件口径：本文件审定的 22f = quality 8 + reconciliation 3 + event 1 + sync 3 + profiling 3 + catalog 2 + connection 2。
- `ensure*` 同族计数口径：`grep -rn "private [A-Za-z<>, ]* ensure" nop-metadata/nop-metadata-service/src/main` + 人工排除每请求局部形态（`CrossDbFieldResolver.ensure*Cols` 3 处已排除）；= 9 处字段缓存形态。
- static 可变容器计数口径：`grep -rnE "static final (Map|List|Set|...)"`（基线 C1b 31 处）逐文件验证写点；`public static` 非 final 形态独立 grep `grep -rn "public static [^(]*=" .../main` 复核——本批次范围内命中 `CrossDbConfigHolder.maxCrossDbRows` 1 处（query 包，移交）、`NopRuleApplication.globalArgs` 1 处（package-private，bootstrap，安全）。

---

# C1 初审追加（批 C+批 D）

> **审计单元**: nop-metadata（CRA WI 5.5 深度并发审计）
> **维度**: C1 共享可变状态与可见性（盲审批次 2/2：批 C + 批 D）
> **盲审声明**: 本批次为盲审初审——未读取 `ai-dev/audits/` 本目录及任何既有审计产出，未读 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`；一切以 live code 为准（基线 commit 81a7c228e7，见 `_tmp/cra-baseline-nop-rule-metadata.txt`）。`_gen/` 与 `_` 前缀生成物不作发现对象；只读审计，未修改任何代码。
> **CL 前缀**: `nop-rule-metadata-CL-0NN`；本批次发现编号自 **C1-101** 起（101 段为批 C+D 保留段）。
> **批次范围**: 批 C = `nop-metadata-service` entity 包 42f；批 D = query 包 27f + lineage/field/search/sqlview/tableref/contract/datasource/manifest/security 包 + service 根散文件 + `nop-metadata-api`(30f, plan-first 只读)/`nop-metadata-core`(2f)/`nop-metadata-dao`(81f)/`nop-metadata-app`(1f)。
> **基线口径衔接**: Phase 1 补记已裁定 main 面 static 可变**字段**直接命中≈0（1253 为 static 方法声明的粗口径噪声）；本批次 C1 审计面按裁定执行：实例字段×单例/BizModel 载体 + 注册表/holder 间接形态。本批实际命中 1 处 static 可变基元字段（`CrossDbConfigHolder.maxCrossDbRows`，int 基元不在 Phase 1 容器类 grep 模式内，属模式外命中，见 C1-101）。

## 五类载体清单表（批 C + 批 D）

| # | 载体类 | 载体（字段 × 位置） | 写者 | 读者 | 同步机制 | 判定 |
|---|---|---|---|---|---|---|
| 1 | singleton bean 实例字段——`@Inject` 协作对象 | entity 包全部 BizModel 的 `@Inject protected` 字段（connectionService/eventPublisher/searchService/entityFieldBiz/moduleBiz/profilingResultBiz/tagLabelBiz 等，~40 处）+ service 根/批 D 包 helper（MetaContractChecker.daoProvider、NopMetaIndexBuilder.searchEngine/daoProvider 等） | IoC 容器启动期单次注入 | 请求线程（并发读） | IoC 容器发布（启动期写完成后只读） | 安全（平台标准模式；启动后无运行时写者，已逐字段核对无 setter 运行时变更路径） |
| 2 | singleton bean 实例字段——`@InjectValue` 配置 | `configuredMaxQueryLimit`(NopMetaEntityBizModel:77)、`configuredMaxEdges`/`configuredMaxTables`(NopMetaLineageEdgeBizModel:42-45)、`configuredReconFetchLimit`(NopMetaReconciliationConfigBizModel:79)、`credentialAdminRolesCsv`(NopMetaDataSourceBizModel:129)、`platformVersion`(NopMetaModuleBizModel)、`webhookAllowedHostsCsv`/`webhookTimeoutSeconds`/`httpClientFollowRedirects`(NopMetaQualityCheckpointBizModel:127-141) | IoC 启动期单次 | 请求线程只读 | 同上 | 安全（配置值经 `final` helper 构造参数间接快照的除外，见载体 4） |
| 3 | singleton bean 实例字段——`final` helper 对象 | `NopMetaEntityBizModel:103-106`（profiler/joinExecutor/aggregationExecutor/queryAction）、`NopMetaDataSourceBizModel:145-151`（structureReader/catalogCollector/tableRefResolver）、各 BizModel 的 fieldResolver/filterTranslator/dataSourceResolver/sqlFieldExtractor/ruleExecutor/resultWriter/tableRefResolver | 启动期构造 | 请求线程 | final 字段 JMM 冻结语义 | 安全（所持对象本体均无状态或 per-request 传参，已逐一核对 MetaJoinExecutor/MetaAggregationExecutor/MetaEntityProfiler 等实例字段） |
| 4 | singleton bean 实例字段——**非 final 懒初始化** | ①NopMetaEntityBizModel.tableRefExecutor:108 ②NopMetaDataSourceBizModel.tableRefExecutor:155 ③NopMetaQualityRuleBizModel.tableRefExecutor:109 ④NopMetaQualityCheckpointBizModel.tableRefExecutor:154/checkpointExecutor:157/actionDispatcher:160 ⑤NopMetaProfilingRuleBizModel.tableRefExecutor:76 ⑥NopMetaQualityScoreBizModel.scorer:66 ⑦NopMetaLineageEdgeBizModel.queryAction:47 ⑧NopMetaEntityQueryAction.tableRefExecutor:58/sqlFieldTypeInferrer:57 | 首个到达的请求线程（check-then-act） | 全部后续请求线程 | **无**（非 volatile、非锁内） | **C1-102 家族（P2）**，10 字段 × 8 载体类 |
| 5 | setter 注入字段 | `NopMetaSearchProcessor.searchIndexFailOpen`(:32,setter:52)、AutoClassificationProcessor/LineageTagPropagationProcessor 的 daoProvider/bizObjectManager（setter:52-67）、`NopMetaDataSourceBizModel` credentialProvider/credentialMigrationSupport（setter:132-137） | IoC 启动期（beans.xml property）/ 测试 | 请求线程 | 启动期写后只读 | 安全（平台 setter 注入标准模式；未发现运行时写者） |
| 6 | static 可变字段 | `CrossDbConfigHolder.maxCrossDbRows:5`（public static int，无 final 无 volatile） | 当前生产面无写者（仅初始化器）；未来代码/测试可写 | MetaJoinExecutor 构造器(:74 快照)+MetaJoinExecutor:404/436 每次调用活读 | **无** | **C1-101（P2）** |
| 7 | static 可变字段（平台惯用法） | `NopMetadataApplication.globalArgs:16`（QuarkusMain） | main 线程（Quarkus.run 前） | StartupEvent observer | Quarkus 启动链 happens-before（同一调用链内） | 安全（全平台统一 Nop 应用引导惯用法，不立案） |
| 8 | 全局注册表/缓存（间接形态） | `NopMetaDataSourceBizModel.EXTERNAL_TABLE_UPSERT_LOCKS:1053`（static ConcurrentHashMap per-key 锁注册表）+ `EXTERNAL_MODULE_ENSURE_LOCK:1060`（static 单键锁） | syncExternalTables 并发请求线程（computeIfAbsent 写入） | 同上 | ConcurrentHashMap + synchronized(per-key) | 锁机制本身正确（computeIfAbsent 原子）；无淘汰 = C7 线索，锁内覆盖 REQUIRES_NEW commit = C2 线索——**代码注释已裁定有界+单实例基线，登记 watch-only CL-101，不立案** |
| 9 | ThreadLocal | main 面 **0**（唯一命中 `TestEntityAggregationProcessorOrmDialectWiring:166` 位于 src/test，测试自持，不入生产载体清单） | — | — | — | 无发现 |
| 10 | 隐式共享（逃逸） | `NopMetaLineageEdgeQueryAction.LineageGraph/LineageExtractResult`(:521-549) public final 可变 Map/List 字段 | per-request 构建（buildLineageGraph:417 每 call 新建） | GraphQL 响应序列化 | 无跨线程驻留（无缓存） | 安全（局部对象直出序列化，无第二读者；不立案） |
| 11 | 隐式共享（per-request 机制核对） | AggregationContext/MemAggAccumulator(SumAcc 等 6 种含可变累积字段)/CrossDbField(lookupKey 非 final)/MemoryFilterEvaluator/MemoryOrderByComparator.SortKey | MetaAggregationExecutor:78/97-145 与 AggregationHelper:921 每 call new | 同请求内 | 栈封闭 | 安全（全部 per-request 实例化，无单例缓存累积器路径，已 grep 全部实例化点核对） |
| 12 | static final 集合（不可变核查） | MetaManifestBuilder.ISO_UTC(DateTimeFormatter 线程安全)、ExpressionMeasureValidator 3 组 unmodifiableSet、NopMetaDataProductBizModel.LINKABLE_ASSET_TYPES(Set.of)、FilterToSqlTranslator.IDENTIFIER_PATTERN(Pattern)、各 SUPPORTED_DIALECTS | 无写者 | 并发读 | 不可变 | 安全（本批 C1b 粗口径命中的 31 处经逐文件核对均为不可变类型/内部类声明，无 SimpleDateFormat/Random/可变容器误用） |

**main 面 static 可变字段直接命中计数口径复核**：`grep -rn "static [A-Za-z]" --include="*.java" <batch 范围> | grep -v "static final"` 逐条人工归类后，**真命中 1**（CrossDbConfigHolder.maxCrossDbRows）+ **平台惯用法 1**（globalArgs，不立案）；其余均为 static 方法声明。与 Phase 1 补记"≈0"结论一致（差异为 int 基元在 Phase 1 容器类模式外）。

---

## 发现条目

### [C1-101] CrossDbConfigHolder 静态可变配置字段：绕过平台配置注入 + 快照/活读双口径不一致

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/query/CrossDbConfigHolder.java:3-9`；连带 `query/MetaJoinExecutor.java:74,404,436`
- **证据片段**:
  ```java
  // CrossDbConfigHolder.java:5
  public static int maxCrossDbRows = 10000;

  // MetaJoinExecutor.java:74（构造期快照 → final 字段）
  public MetaJoinExecutor() {
      this.crossDbMerger = new CrossDbJoinMerger(CrossDbConfigHolder.maxCrossDbRows);
  }
  // MetaJoinExecutor.java:404 / 436（每次调用活读）
  q.setLimit(CrossDbConfigHolder.maxCrossDbRows + 1);
  Long fetchLimit = (long) CrossDbConfigHolder.maxCrossDbRows + 1;
  ```
- **严重程度**: P2
- **并发场景**: 单例 `NopMetaEntityBizModel`（`private final MetaJoinExecutor joinExecutor = new MetaJoinExecutor()`，:104）在 IoC 启动线程构造时快照；HTTP worker 线程每次 join/聚合调用活读。潜在写者为任意持有 classpath 的代码（扩展、测试、未来运维钩子）。
- **触发时序**: T1（启动线程）构造 MetaJoinExecutor，将 10000 快照进 `CrossDbJoinMerger.maxCrossDbRows`（final）→ T2（假设的运维/扩展代码）执行 `CrossDbConfigHolder.maxCrossDbRows = 5000`（int 非原子可见性依赖 JMM，无 volatile 则 T1 后续请求线程可能无限期读旧值）→ 后续请求线程活读 5001 作为 fetch limit（:404），但合并上限仍是启动期快照 10000（CrossDbJoinMerger:101-112）→ **单侧行数上限与合并产物上限的防 OOM 双闸门取值分叉**，规模护栏整体语义不再对应同一配置值。当前生产面 grep 确认无任何运行时写者（仅初始化器赋值），故为结构性隐患而非现行错误行为。
- **现状**: 防 OOM 上限以 public static 非 final 基元字段承载，未走平台 `@InjectValue` 配置通道；且消费端一处构造期快照、两处调用期活读，读取口径不统一。
- **风险**: 任一后续改动引入运行时写者（如管理端动态调参、测试并行污染全局静态）即触发双闸门分叉与跨线程可见性缺失；static 可变全局配置同时构成并行测试干扰源（测试甲改值未复原会泄漏到同 JVM 后续测试/生产路径）。
- **建议**: 改为 `@InjectValue("@cfg:nop.metadata.query.cross-db-max-rows|10000")` 注入 `MetaQueryContext`（或注入 MetaJoinExecutor 构造参数），与 `configuredMaxQueryLimit`（NopMetaEntityBizModel:76-77）同模式；字段本身如需保留静态入口则加 final 并以常量语义声明（注释已自称"常量先例"，宜名实相符）。
- **失效面枚举**（F3 同族清扫）: static 配置/常量族全清单——①`CrossDbConfigHolder.maxCrossDbRows`（本案，非 final 可变）；②`MetaEntityProfiler.MAX_IN_APP_SORT_ROWS`（static final，已核对为常量，安全）；③`NopMetaIndexBuilder.PURGE_ENUMERATION_LIMIT`（static final，安全）；④`NopMetaEntityBizModel.DEFAULT_QUERY_LIMIT/DEFAULT_MAX_QUERY_LIMIT`（static final，安全）；⑤`NopMetaSearchProcessor.TOPIC`（static final，安全）；⑥`@InjectValue` 配置字段 9 处（载体 2，走平台通道，安全）。**仅①偏离**。
- **信心水平**: 确定（字段形态、读写点、无生产写者均经 grep 全量核实）
- **误报排除**: 不属于"有界声明的缓存/队列"误报项——这不是容器而是配置基元；不属于平台标准模式——平台配置标准通道是 `@InjectValue`（同模块 9 处先例），本案明确绕过；亦非低频路径粗锁类合理取舍，因其是防 OOM 双闸门的唯一取值源。
- **复核状态**: 已复核（2026-10-22 C1 独立复核）：**保留发现、降级 P2→P3**——全仓（生产+测试）grep 零写者，静态初始化器经类初始化锁安全发布使现行读值恒为 10000，快照/活读分叉与可见性缺失在当前代码中均不可发生（无可具体化交错）；实质为配置通道/形态缺陷，危害兑现需未来引入写者；与批 A 移交建议 H1 的 P3 一致（批 C 立项 P2 未对齐维度内先例）；现状未修复。详见文末「维度复核结论」。

### [C1-102] 单例 BizModel 非 final 懒初始化字段族：无同步 check-then-act（10 字段 × 8 载体），含 publish-then-configure 中间态发布

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityCheckpointBizModel.java:160,531-538`（主证，publish-then-configure）；同族见失效面枚举
- **证据片段**:
  ```java
  // NopMetaQualityCheckpointBizModel.java:531-538
  private CheckpointActionDispatcher ensureActionDispatcher() {
      if (actionDispatcher == null) {
          actionDispatcher = new CheckpointActionDispatcher(httpClient, messageService);  // 先发布
          actionDispatcher.configureWebhookSsrf(webhookAllowedHostsCsv, webhookTimeoutSeconds); // 后配置
          actionDispatcher.configureRedirectPolicy(httpClientFollowRedirects);
      }
      return actionDispatcher;
  }
  // 其余同族形态（仅构造，无后置配置），如 NopMetaEntityBizModel.java:430-435
  private TableReferenceExecutor ensureTableRefExecutor() {
      if (tableRefExecutor == null) {
          tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
      }
      return tableRefExecutor;
  }
  ```
- **严重程度**: P2
- **并发场景**: `@BizModel` 单例 bean 的实例字段被 HTTP worker / 调度（cron checkpoint 执行路径 ensureCheckpointExecutor:210）多线程并发触达；懒初始化动机为"构造时 orm() 不可用"（:302、:869 注释明示）。
- **触发时序**: （子例 A，publish-then-configure）T1、T2 并发进入 executeCheckpoint → 双方读 `actionDispatcher == null` → 双方各自 new 并配置（两个 dispatcher 实例并存）→ T1 在 :533 完成字段赋值、尚未执行 :534-535 configure 时，T3（第三个并发请求或调度线程）读取字段得非 null 引用，**使用了 SSRF allowlist/timeout/redirect 策略尚未注入的 dispatcher**。中间态后果边界经核实为 fail-closed 方向（CheckpointActionDispatcher 字段内联默认 `webhookAllowedHostsCsv=""` 拒内网、`webhookTimeoutSeconds=30`、`followRedirectsEnabled=false` 拒跟随，quality/CheckpointActionDispatcher.java:90-99），且窗口为首次并发调用的纳秒级——故现行后果限于一次 webhook 投递按缺省保守策略执行/重复构造浪费；（子例 B，其余 9 字段）目标类字段全 final（TableReferenceExecutor:50-52、NopMetaLineageEdgeQueryAction:43-48、SqlViewFieldTypeInferrer:83-84、MetaQualityScorer 经构造器、MetaQualityCheckpointExecutor:63-68），JMM final 冻结保证不出现撕裂对象，后果限于重复构造与每线程可能长期各持不同实例。
- **现状**: 单例 bean 上 10 个非 final 非 volatile 字段以无同步 check-then-act 惰性初始化；其中 1 处在字段赋值后、对象配置完成前即已发布（publish-then-configure 反模式）。
- **风险**: 现时无数据损坏面；但该惯用法把正确性押在"目标对象恰好全 final + configure 值恰好缺省即安全"两个隐含前提上——未来任一 configure 值变为放宽方向（如 allowlist 扩面、redirect 开关语义反转）或新懒初始化字段目标含非 final 状态，中间态即转化为守卫绕过/状态撕裂。P2 定级依据：竞态可具体化但现行后果有界（首次并发 + fail-closed 中间态 + 重复构造浪费）。
- **建议**: 统一收敛为安全发布形态：优先在 bean `afterPropertiesSet`/`start()` 生命周期钩子完成初始化（orm 依赖经 `IOrmTemplate` 延迟解析代理或注入 `DaoProvider` 于启动期取得）；或将 10 字段改 volatile（低成本方案，check-then-act 竞态仍在但中间态与重复构造可见性解决）；ensureActionDispatcher 至少应调整为"先配置局部变量、后整体赋值字段"（`var d = new ...; d.configure...; actionDispatcher = d;`）消除 publish-then-configure。
- **失效面枚举**（F3 同族清扫，grep `== null` + `= new` 全量核对）: ①NopMetaEntityBizModel.tableRefExecutor(:108/:430)——ctor-only，目标全 final；②NopMetaDataSourceBizModel.tableRefExecutor(:155/:870)——同①；③NopMetaQualityRuleBizModel.tableRefExecutor(:109/:303)——同①；④NopMetaQualityCheckpointBizModel.tableRefExecutor(:154/:542)——同①；⑤同文件 checkpointExecutor(:157/:549)——同①；⑥同文件 actionDispatcher(:160/:531)——**publish-then-configure，本案主证**；⑦NopMetaProfilingRuleBizModel.tableRefExecutor(:76/:133)——同①；⑧NopMetaQualityScoreBizModel.scorer(:66/:69)——同①；⑨NopMetaLineageEdgeBizModel.queryAction(:47/:49)——同①；⑩NopMetaEntityQueryAction.tableRefExecutor(:58/:163)+sqlFieldTypeInferrer(:57/:171)——同①。批外兄弟：quality/QualityAlertWorkflowProcessor.tableRefExecutor(:169，quality 包属批 A/B 审计面，此处仅登记)。全部 10 处均无 volatile/锁/holder。
- **信心水平**: 确定（字段形态与竞态窗口为代码事实）；后果评估为"很可能"（中间态 fail-closed 边界经 CheckpointActionDispatcher 默认值核实）
- **误报排除**: 不属于"无状态单例被并发调用是设计意图"校准项——本案是**字段写**竞态而非无状态读并发；不属于"低频路径粗锁合理"项——executeCheckpoint/executeJoin 均为请求与 cron 热路径；publish-then-configure 子例有具体对象中间态，非"看到共享字段就报线程不安全"的泛化指控。
- **复核状态**: 已复核（2026-10-22 C1 独立复核）：**保留 P2**——8 载体全部核实且字段均非 final 非 volatile 无锁；簿记修正：标题「10 字段」实为 **11 字段×8 载体**（⑩ EntityQueryAction 含 2 字段）；行号漂移修正（scorer 字段实为 :27、TableReferenceExecutor final 字段实为 :39-40），子例 B「目标类全 final」经抽查成立（TableReferenceExecutor:39-40、NopMetaLineageEdgeQueryAction:43-48、SqlViewFieldTypeInferrer:83-84）；QCP 载体 3 字段的 ensure 调用在 R4.3 运行标记之后（同 checkpointId 并发被挡），其余载体（如 NopMetaEntityBizModel:375 每请求调用）无锁、竞态直接可具体化——定级维持 P2；现状未修复。

---

## 维度集外线索（CL 附记，供 C2/C3/C7 维度取证）

### [nop-rule-metadata-CL-101] 静态 per-key 锁注册表无淘汰 + 锁内覆盖 REQUIRES_NEW 事务提交（C2/C7，watch-only，已裁定边界）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaDataSourceBizModel.java:1040-1079,1092-1098`
- **线索**: `EXTERNAL_TABLE_UPSERT_LOCKS`（static ConcurrentHashMap，computeIfAbsent 建锁）无任何 evict/remove 路径——增长上界 = 元数据表数（key=(metaModuleId,tableName,schema)），javadoc(:1050-1051) 明示"key 集合有界（受元数据规模约束），单实例 baseline（R4.3 已裁定），多实例残余面见 plan Deferred"；`synchronized(lock)` 块内执行 REQUIRES_NEW 事务的 find→upsert→flush→commit（:1071-1078），属锁内持锁跨越 DB I/O（C2 锁内重操作观察项）；`ensureExternalSystemModule` 的模块级单键锁(:1093)与 per-key 锁无嵌套调用关系（:638 在进入 per-key 循环前调用），无锁序环。
- **移交建议**: 已按"声明边界非缺陷"口径不立案；C7/C6 维度若发现多实例部署形态落地或 key 空间引入用户可控维度（如 schema 透传），应升级重审。

### [nop-rule-metadata-CL-102] 调度路径 null context 就地补造 ServiceContextImpl（C3 上下文传播取证线索）

- **文件**: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/entity/NopMetaQualityScoreBizModel.java:38-44`；同形态 `quality/QualityAlertWorkflowProcessor.java:92-95`（批 A/B 审计面，仅登记）
- **线索**: `computeQualityScore` 注释明示"Cron/scheduler path may pass null context; create a minimal one for pipeline compatibility"——调度线程上下文为 null 时就地 new 空 ServiceContext 续跑 pipeline。C1 视角无共享可变状态问题（局部变量），但 C3 维度应核查：该空 context 丢失哪些 ThreadLocal 依赖（租户/用户 trace）、save 链路对空 context 的行为契约是否处处已定义、与 checkpoint 调度路径（NopMetaQualityCheckpointBizModel:210 无同类兜底）口径是否一致（同族不对称）。

---

# C1 深挖第 2 轮

> **审计单元**: nop-rule 全部 72f/5,221 + nop-metadata 全部 242f/29,131（本轮合并批覆盖全单元，不限于第一轮批次边界）
> **盲审声明**: 本轮为深挖追加轮（2/2），仅允许读本维度文件（第一轮初审产出）；未读取其他维度文件及 `ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/`、`ai-dev/backlog/`。全部结论基于 live code 独立复核。
> **本轮性质**: 对第一轮未覆盖/浅覆盖角落的扩大面核查 + 对第一轮安全判定的独立复核。**本轮无新增发现（0 条）**，扩大面逐项清扫结论见文末清扫表（终止信号载体）。

## 本轮扩大面逐项核查记录（证据锚点）

### 扩大面 1：nop-rule-core 执行上下文字段面 + 规则集加载器实例态

- RuleRuntime 非 final 字段全量清单（`inputs`/`ruleName`/`ruleVersion`/`ruleMatch`/`collectLogMessage`/`traceContext`/`exception`，`nop-rule-core/.../execute/RuleRuntime.java:47-58`）逐字段核对：全部为每请求生命周期 setter 字段（`RuleServiceImpl.executeRule:62-68` 单线程填充、同请求消费），与 C1-003 已立案的输出三容器同生命周期，无新的独立失效面；`traceContext` 在读取点做了防御性拷贝（`RuleRuntime.java:207` `new HashMap<>(traceContext)`），拷贝不对称属 C1-003 建议(b)契约文档化范围，不重复立案。
- `RuleRuntime.cache`（`:74`）：svcCtx 非 null 时复用请求作用域 `ICache`，null 时每请求 `new MapCache`——两分支均无跨请求驻留。
- 规则集加载器：`DaoRuleModelLoader`（唯一实例字段 `daoProvider`，`@Inject` setter 启动期写，`DaoRuleModelLoader.java:57-66`）全方法无状态；`DaoRuleModelSaver` 每调用 `new`（`NopRuleDefinitionBizModel.java:140`）无状态；`RuleDslModelLoader` 每次解析新建 `XLangCompileTool`（`:20`）无状态。→ 全部安全。
- 编译面独立复核：`RuleModelCompiler.compileRule` 全部 3 个调用路径核实——`RuleManager.java:60`（`synchronized(ruleModel)` 锁内，作用于 **RCM 缓存模型**）、`RuleDslModelLoader.java:22` 与 `DaoRuleModelLoader.java:77`（均为 **RCM 发布前**对新鲜解析模型编译，单线程加载期）——R1「锁完备」判定成立，无绕锁编译路径；`RuleModel.getExecutableRule()` 调用方 grep 仅 `RuleManager`，R1 附注的脆弱性维持 P3 不立案口径。
- 可执行规则类字段面（E1 独立复核）：TraceVars/Decorated/NormalizeInput/NormalizeOutput/Main/ExecutableRule/RuleDecider/ExecutableMatrixRule/RuleOutputAction 逐类 grep 字段声明，全部 `final`，执行期只读（`ExecutableMatrixRule.execute` 的 `MutableIntArray`/`MutableInt` 为方法局部变量，栈封闭）。E1 判定成立。

### 扩大面 2：nop-metadata-dao 81f 手写实现

- `dao/entity` 非 `_gen` 手写保留类（NopMetaEntity/NopMetaDataSource/NopMetaTagLabel 等）逐文件核对：全部为继承 `_gen` 基类 + 无状态判定助手方法（如 `isPhysical`/`isSqlView`/`isDisabled`，仅读自身列值），0 新增实例字段、0 static 可变字段。
- `dao/biz` 38f 全部为生成的 `INopMeta*Biz` 接口，无手写实现混入。
- `dao/model/OrmModelImporter`：纯 new+填充的无状态构建器（仅 static 工具方法 + 局部变量）；`dao/dto` 2f 为 per-call DTO。→ 全部安全。

### 扩大面 3：catalog/connection/sync 包处理器实例字段生命周期

- `MetaCatalogCollector`：0 实例字段（仅 static final Logger + `IDENTIFIER_PATTERN`，`MetaCatalogCollector.java:42,49`）；`CatalogTableStats`/`ExternalTableInfo`/`ExternalColumnInfo` 为 per-call DTO，消费点（`NopMetaDataSourceBizModel.java:897-901,932`、`NopMetaProfilingRuleBizModel.java:173`）均为同请求内"收集→序列化→写库"单线程链，无跨请求驻留。
- `ExternalTableStructureReader`：0 实例字段；`TABLE_TYPES` static final String[]（`:41`）全文件无写点。
- `MetaDataSourceConnectionProcessor`：3 个实例字段（`allowedInternalHostsCsv`/`h2FileAllowedDirsCsv`/`credentialProvider`，`:158,169,176`）已在第一轮 B6（write-once 安全）+ C1-002（构造器全局副作用）立案，本轮复核无新增字段。
- `MetaDataSourceResolver`（datasource 包）：0 实例字段；`TableReferenceExecutor`（tableref 包）：2 final 字段（`:39-40`）。`TableReference` 全 final 字段（`:32-51`）不可变。→ 全部安全。

### 扩大面 4：MetaModelChangedEventPublisher 订阅者可见性

- 发布机制核实：主路径为 **DB 事件行写入**（`MetaModelChangedEventPublisher.java:127-139` `eventDao.saveEntity`），类注释明示「首版无订阅者、不依赖 IMessageService 订阅者注册机制」（`:37`）——**无内存订阅者注册表，不存在订阅者可见性/安全发布问题**。全单元 grep 消费面：仅 `NopMetaModelChangedEventBizModel`（DB 行查询 BizModel）。
- 快照别名撕裂（F1 定向核对）：全部 UPDATE 路径核实为先 `buildSnapshot`（**即时物化为 String**，`:160-173`）再变更实体——`NopMetaModuleBizModel:512-519`（releaseModule：before 捕获于 setStatus 前）、`NopMetaDataSourceBizModel:323/359-380/640-677`、`NopMetaEntityBizModel:126-129/144-145`；直接 `publishEvent` 仅用于 CREATED（before=null：`NopMetaEntityBizModel:234`、`NopMetaModuleBizModel:285`）。无 before=活动实体别名导致的撕裂。
- `SENSITIVE_COLUMN_FALLBACK`（`:75`）static final unmodifiableSet，无运行期写点。→ 安全。

### 扩大面 5：逃逸面（getter 返回内部可变集合）

- 双单元 main 面全量扫描（单行/多行 getter 直接返回 Map/List/Set 字段引用）：命中 `AggregationContext` 3 处（`:55,59,95`）+ `CatalogTableStats.getExtras/getUnavailable` + `ExternalTableInfo.getColumns` + `RuleRuntime` 输出三容器 getter + `QualityRuleJudgment.details`。逐项核对生命周期：前四者全部为 per-call/per-request 栈封闭对象（第一轮载体 10/11 已核，本轮复核成立）；`RuleRuntime` getter 是唯一跨方法共享面，已由 C1-003 立案（契约不对称），消费端 `RuleServiceImpl:70-80` 同请求线程序列化后即弃，无第二读者。→ 无新增立案。

### 扩大面 6：ClassLoader / 静态工具类隐式共享态

- `ClassLoader`/`loadClass`/`forName`：双单元 main 面 **0 命中**。
- static 非 final 字段独立重扫（本轮新 grep，不依赖第一轮基线）：真命中仅 `CrossDbConfigHolder.maxCrossDbRows`（已立案 C1-101）+ 两个 `globalArgs`（`NopRuleApplication.java:22`/`NopMetadataApplication.java:16`，平台 bootstrap 惯用法，第一轮 S2/载体 7 已判安全）；其余命中均为 static 方法声明（粗口径噪声）。
- 静态工具类逐个核查：`NopMetadataHelper`/`RuleServiceHelper`/`MetaQualityRuleExecutor`（全 static 方法，0 实例字段）/`MetaCatalogCollector`/`EntityAggregationProcessor`/`MetaEntityProfiler`（仅 static final 常量）/`QualityResultWriter`/`LocalReconciliationProcessor`/`GranularityBucketing`（static init 写模板后运行期只读，`translate` 为 `tpl.replace` 无突变，`GranularityBucketing.java:31-53,97`）/`SeedGlossaryData`（无状态 seeder）——全部无状态。
- 特殊形态新增核查：`SqlSelectFieldExtractor.parser`（**final `EqlASTParser` 实例跨请求共享**，`SqlSelectFieldExtractor.java:55`）——追踪平台源码 `nop-persistence/nop-orm-eql/.../EqlASTParser.java:10-33`：0 实例字段，每次 parse `newParser()` + `new EqlASTBuildVisitor()`，无状态对象共享安全。`VarCollector._instance`（static 非 final 单例，`RuleRuntime.java:201` 热路径读）——平台 nop-core unittest 设施，main 面无 `registerInstance` 写者，默认 no-op 实例经类初始化安全发布（第一轮 R5 判定成立，平台设施本体不重审）。
- 缓存/注册表间接形态补扫：`ICache`/`MapCache`/`GlobalCacheRegistry`/`CacheConfig` 双单元 grep——唯一命中为 RuleRuntime 每请求 cache；无单元级全局缓存注册表。

### 扩大面（附）：跨单元边界核查

- `IRuleRuntime`/`IRuleManager`/`executeRule` 单元外消费者全量 grep：仅 `nop-ai/nop-ai-gateway/.../routing/RuleBasedSelectionStrategy.java`（1 文件）。核实其用法：无状态单例、per-call `ruleManager.newRuleRuntime()`（`:140`）、setter 注入 write-once（`:91-93`；类注释 `:72-73` 明示单例 stateless 契约、runtime 不得缓存为字段）——无跨线程消费 ruleRt 的路径，**C1-003 的 P2「理论风险」定级维持不升级**；亦无跨单元共享可变状态新失效面。

## 清扫表（本轮扩大面逐项核查结论——终止信号载体）

| # | 扩大面项 | 核查对象（证据锚点） | 判定 | 理由 |
|---|---------|--------------------|------|------|
| 1 | nop-rule-core 执行上下文字段面 | `RuleRuntime.java:47-58` 全部非 final 字段、`:74` cache 双分支 | 安全 | 均为每请求生命周期字段，单线程填充/消费；容器契约不对称已由 C1-003 立案，无独立新失效面 |
| 2 | 规则集加载器实例态 | `DaoRuleModelLoader.java:57`/`DaoRuleModelSaver`/`RuleDslModelLoader.java:20` | 安全 | 唯一实例字段为 IoC 启动期 write-once；无解析缓存、无共享 compile tool |
| 3 | 编译缓存锁完备性复核（R1-R1 判定独立复核） | `RuleModelCompiler` 3 调用路径（`RuleManager.java:60` 锁内；`RuleDslModelLoader.java:22`、`DaoRuleModelLoader.java:77` 发布前） | 安全（R1 判定成立） | 无绕锁编译路径；`getExecutableRule()` 调用方 grep 仅 RuleManager |
| 4 | nop-metadata-dao 手写实现 | `dao/entity` 手写保留类 + `dao/biz` 38 生成接口 + `OrmModelImporter` + dto 2f | 安全 | 手写类 0 实例字段 0 static 可变；biz 全生成接口；导入器无状态 |
| 5 | catalog/connection/sync 处理器实例字段 | `MetaCatalogCollector`/`ExternalTableStructureReader`/`MetaDataSourceConnectionProcessor:158-176`/`MetaDataSourceResolver` | 安全 | 处理器 0-3 实例字段（已立案或 write-once）；`TableReference` 不可变；DTO 全部 per-call 栈封闭 |
| 6 | 事件发布面订阅者可见性 | `MetaModelChangedEventPublisher.java:29-47,127-139`；UPDATE 快照捕获序（Module:512-519、DataSource:640-677、Entity:126-129） | 安全 | 主路径为 DB 行持久化，无内存订阅者注册表；UPDATE 快照全部先物化后变更，无 F1 别名撕裂 |
| 7 | 逃逸面 getter | 双单元 getter 返回内部集合扫描（AggregationContext:55-95/CatalogTableStats/ExternalTableInfo/QualityRuleJudgment/RuleRuntime） | 安全 | 命中项全部 per-call 栈封闭；RuleRuntime getter 已由 C1-003 覆盖，消费端单线程序列化 |
| 8 | ClassLoader/静态工具类 | ClassLoader grep 0 命中；static 非 final 重扫仅 C1-101 + globalArgs；静态工具类 10 个逐个核对；`EqlASTParser` 源码级核查 | 安全 | 无类加载共享态；工具类全部无状态；跨请求共享的 EqlASTParser 实例本体无状态（per-parse newParser） |
| 9 | 跨单元边界（rule 引擎外部消费者） | `RuleBasedSelectionStrategy.java:81-143`（nop-ai-gateway，单元外唯一消费者） | 安全（C1-003 维持 P2 理论级不升级） | 无状态单例 per-call runtime 用法，无可具体化跨线程写时序 |
| 10 | 缓存/注册表间接形态补扫 | `ICache`/`MapCache`/`GlobalCacheRegistry`/`CacheConfig` 双单元 grep | 安全 | 唯一命中为 RuleRuntime 每请求 cache（`:74`）；无单元级全局缓存注册表 |

## 终止信号

**本轮终止：无新增发现。**

深挖第 2 轮对任务书列出的全部 6 项扩大面 + 跨单元边界完成逐项核查（清扫表 10 行），全部判定「安全」或已由第一轮条目覆盖（C1-001/002/003/101/102 + CL-001~003/101/102），无符合立案标准（可定位 + 可具体化风险 + 非误报校准项）的新缺陷。按 `ai-dev/skills/deep-audit-prompts.md` 深挖终止规则：C1 维度深挖结束，不应再派发后续轮次。

---

# C1 维度复核结论（独立复核，2026-10-22）

- **复核人**: CRA WI 5.5 C1 维度独立复核子代理（独立 task_id，未参与初审/深挖撰写）
- **复核口径**: 逐条重读 5 条发现引用的全部 file:line；对关键声明（零写者、configure 调用点唯一性、目标类 final 字段、锁注册表无淘汰、单例 bean 注册）独立 grep 验证。git HEAD=81a7c228e7（与初审基线一致）且 4 个主证据文件工作区无漂移——复核对象与初审所见代码逐行相同。
- **总结论**: **5/5 保留（驳回 0），其中 1 条降级（C1-101 P2→P3）**；2 处证据语义修正（不影响定级）、2 处行号引用漂移（不影响实体认定）、1 处计数簿记修正。深挖第 2 轮「0 新增」结论经抽查维持。

## 逐条裁定

### C1-001：保留 P2

- 证据全部核实：`NopMetaQualityCheckpointBizModel.java:160`（非 final 非 volatile）、`:531-538`（L533 先发布引用、L534-535 后 configure——发布-配置倒序属实）；全仓 grep 生产面 configure 调用点仅 `:534-535` 一处（载体 B4「创建期调用一次」成立）；中间态缺省值 fail-closed 方向核实（`CheckpointActionDispatcher.java:90-99`：`webhookAllowedHostsCsv=""`、`webhookTimeoutSeconds=DEFAULT(30)`、`followRedirectsEnabled=false`）。
- **语义修正（不影响定级）**：触发时序原文「cron×HTTP 首次并发触发**同一检查点**编排链」在 R4.3 运行标记下不可发生——`executeCheckpoint` 于 `:203` putIfAbsent 之后才到达 `:210`（ensureCheckpointExecutor）与 `:264→:510`（ensureActionDispatcher），标记持有至 `:268` finally，同 checkpointId 的第二并发请求被挡在 ensure 之前。**可具体化窗口 = 两个不同 checkpointId 的首次并发执行**（运行标记按 checkpointId 分键）+ 同族其余无锁载体。Phase 6 合并时触发时序以 C1-102 的表述为准。

### C1-002：保留 P2

- 证据全部核实：构造器 `:139-141` → `setGlobalLoginTimeout`（`:143-153`）→ `:150 DriverManager.setLoginTimeout(5)`；单例 bean 注册核实（`_vfs/nop/metadata/beans/app-service.beans.xml:12-13`，`ioc:default`）；全仓 main 面 `DriverManager.setLoginTimeout` 唯一写点即 `:150`（nop-dao `SimpleDataSource/DynamicDataSource/ReadWriteRoutingDataSource` 同名方法均为 DataSource 接口实例覆写，非全局写）——「唯一写者」声明成立。
- 判级理由：与 C1-101 本质不同，该全局写**每次启动无条件发生**（机制真实存在），跨模块后果依赖宿主生态存在其他 DriverManager 使用者，按维度内「机制确定 + 触发依赖部署生态」P2 口径（同 C1-003 先例）恰当。

### C1-003：保留 P2（理论风险）

- 证据逐行核实：三容器 `RuleRuntime.java:41/43/45`、`:68` 注释、`addOutput :153-158`、`setOutput :180-182`、`logMessages.add :210`、`clearOutputs :160-163`；单线程链核实（`RuleServiceImpl.java:61-68` 每请求填充、`RuleOutputAction.java:22` 唯一 addOutput 调用方）；深挖轮跨单元唯一消费者（nop-ai-gateway per-call runtime）判定成立。
- 契约不对称（`:68` 注释声明并发容忍 vs 三容器非并发实现）是现存代码事实；并发写时序在现有执行面不可具体化 → P2 理论级恰当。

### C1-101：保留发现，降级 P2 → P3

- 证据全部核实：`CrossDbConfigHolder.java:5`；快照点 `MetaJoinExecutor.java:74`、`CrossDbJoinMerger.java:29`（入 final 字段）；活读点 `MetaJoinExecutor.java:404/436`；双闸门消费 `CrossDbJoinMerger.java:101-112`。全仓 grep（生产+测试）**零写者**——仅注释引用（TestMetaEntityProfilerResourceLimits:29、TestMetaJoinCrossDbMergeNullSemantics:173-174、NopMetaIndexBuilder:34、MetaEntityProfiler:74）。
- **降级理由**：无写者即无交错——静态初始化器经类初始化锁安全发布，现行所有读恒为 10000，快照/活读分叉、可见性缺失、双闸门取值分叉当前**均不可发生**；缺陷实质是「public 可变全局配置 API 形态缺陷（绕过 `@InjectValue` 平台通道）+ 快照/活读双口径的潜在不一致面」，危害兑现需未来引入运行时写者（该写者本身即新缺陷）。对比同批 P2：C1-002 的全局写真实发生、C1-003 的契约矛盾是现存事实，本条三者皆无，为最弱形态。批 A 移交建议 H1 本就定 P3，批 C 立项 P2 未对齐维度内先例——收敛为 P3。修复建议（改 `@InjectValue` 注入或 final 常量）维持不变。

### C1-102：保留 P2

- 8 载体全部核实，字段均非 final 非 volatile 无锁：`NopMetaEntityBizModel.java:108`（ensure :430-436）、`NopMetaDataSourceBizModel.java:155`（:870-876）、`NopMetaQualityRuleBizModel.java:109`（:303）、`NopMetaQualityCheckpointBizModel.java:154/157/160`（:541/:549/:531）、`NopMetaProfilingRuleBizModel.java:76`（:133）、`NopMetaQualityScoreBizModel.java:27`（:69-73）、`NopMetaLineageEdgeBizModel.java:47`（:49-56）、`NopMetaEntityQueryAction.java:57-58`（:163-169/:171-177）。
- **簿记修正**：标题「10 字段 × 8 载体」实为 **11 字段 × 8 载体**（枚举⑩含 2 字段）。**行号漂移**：scorer 字段声明实为 `:27`（非 :66）；`TableReferenceExecutor` final 字段实为 `:39-40`（非 :50-52；扩大面 3 的 :39-40 正确）。子例 B「目标类全 final」经抽查成立（TableReferenceExecutor:39-40、NopMetaLineageEdgeQueryAction:43-48、SqlViewFieldTypeInferrer:83-84）——JMM final 冻结论证成立。
- **触发面修正（不影响定级）**：QCP 载体 3 字段的 ensure 调用均在 R4.3 运行标记之后（同 checkpointId 并发被挡）；其余载体无任何锁、竞态直接可具体化（如 `NopMetaEntityBizModel.java:375` buildMetaQueryContext 每请求调用、`NopMetaQualityRuleBizModel.java:162/372` 直呼点）。定级维持 P2。

## 跨批重复标注（本维度登记建议；与 C4-001 的合并裁定归 Phase 6）

- **C1-001（批 A/B）× C1-102（批 C/D）**：8 站点重叠（QCP 3 + Entity/DataSource/QualityRule/Profiling/Score 各 1），主证据站点相同（ensureActionDispatcher），且与 C4 维度 C4-001 同族。**建议：本维度主登记 = C1-102**（失效面最全 11 字段×8 载体、子例 A/B 后果边界分析更精细）；**次登记 = C1-001**（独有贡献须并入主登记：①批外站点 `QualityAlertWorkflowProcessor.java:56/167-172`；②消除发布-配置倒序的最具体修复写法——局部变量先 configure 后整体赋值）。两条款修复方向一致（volatile / 生命周期钩子），Phase 6 可无损合并。
- **C1-101 × CL-601**：本维度 static 可变字段唯一站点为 C1-101；CL-601（CL 维度编号，本目录无 CL 维度文件，未读其内容）若指向同站点（CrossDbConfigHolder），建议主登记归 C1 维度（C1-101，降级后 P3），CL 侧作线索引用。

## CL 线索复核（5/5 确认）

- **CL-001 确认**：`NopMetaDataSourceBizModel.java:1067-1080` per-key 锁内 REQUIRES_NEW 事务 + flushSession（跨 commit 裁定 javadoc :1040-1052 在案）；`:1092-1098` 全局单键锁内 DB find/insert。站点与语义属实，属 C2 锁内重 I/O 定向核对对象，C1 不立案正确。
- **CL-002 确认**：`RuleManager.java:54-65` `synchronized(ruleModel)` 内 `RuleModelCompiler.compileRule`（:60）；首访冷路径，P3 候选口径合理。
- **CL-003 确认**：`MetaQualityCheckpointScheduler.java:204-240` catch-all + `isConcurrentRunRejection`（:224-235）WARN/ERROR 分级（注释引 MA7.5-01/R4.3）。补充锚点：`:219` 注释「null context 安全：computeQualityScore 内部从不解引用 context」可作 C3/CL-102 对账输入。
- **CL-101 确认**：`EXTERNAL_TABLE_UPSERT_LOCKS` 全文件仅 `:1053` 声明 + `:1070` computeIfAbsent，无任何 remove/evict 路径；`:1050-1052` javadoc 声明有界 + 单实例（R4.3）。watch-only 口径成立。
- **CL-102 确认**：`NopMetaQualityScoreBizModel.java:38-44`（null context → `new ServiceContextImpl()`）+ `QualityAlertWorkflowProcessor.java:92-95`（三级兜底）；同族不对称（checkpoint 调度路径传 null 无兜底、仅 Scheduler:219 注释声明安全）——C3 取证线索成立。

## 批次覆盖核验

- **批 A（nop-rule 全部 72f）**：有痕迹 ✓（S2/S3、B1/B8/B9、R1/R2/R5、E1/E2、T1、C1-003、CL-002 + 深挖轮扩大面 1 与跨单元边界）。
- **批 B（quality/reconciliation/event/sync/profiling/catalog/connection）**：7 子组全有痕迹 ✓（quality：B2/B4/C1-001/CL-003；reconciliation：B3+扩大面 6；event：B3+扩大面 4；sync：扩大面 3 三文件全部具名（ExternalTableStructureReader:41 TABLE_TYPES + ExternalTableInfo/ExternalColumnInfo DTO）；profiling：B3+载体 12+扩大面 6；catalog：B3+扩大面 3/6；connection：B6+C1-002+扩大面 3）。
- **批 C（entity 42f）**：有痕迹 ✓（载体表 1-5、R3 checkpointRunLocks、H3 锁注册表、H4 四站点移交、C1-102 族、NopMetaModuleBizModel/NopMetaDataProductBizModel）。
- **批 D**：query ✓（C1-101/S1/H1/H2/载体 6）、lineage ✓（载体 2/4/10）、search ✓（载体 5/12 + C1-101 失效面③）、sqlview ✓（C1-102⑩）、tableref ✓（扩大面 3）、contract ✓（载体 1 MetaContractChecker）、datasource ✓（扩大面 3 MetaDataSourceResolver）、manifest ✓（载体 12 MetaManifestBuilder）、根散文件 ✓（扩大面 6 NopMetadataHelper/SeedGlossaryData）、dao ✓（扩大面 2，81f）、app ✓（载体 7 NopMetadataApplication:16）。
- **登记缺口（按任务书不补审，移交 Phase 6 决定是否点名补核）**：
  1. **security 包零具名痕迹**：唯一文件 `HostSecurityUtil.java` 未在任何载体表/扩大面/清扫表具名（「security」全文唯一命中为批次范围声明行）。
  2. **field 包部分痕迹**：仅 `ExpressionMeasureValidator` 具名（载体 12）；`MetaEntityFieldResolver`/`ResolvedTableField` 未具名核对（前者仅作为 tableRefResolver 构造参数间接出现）。
  3. **nop-metadata-api(30f)/nop-metadata-core(2f) 无具名文件级痕迹**：仅批次范围声明 + 深挖轮「全单元 static 非 final 字段重扫」的隐含覆盖（api 为 plan-first 只读 DTO/接口面；core 2 文件中 1 个为 `_` 前缀生成物，按口径不作发现对象）。

---

## 最终保留项

| # | 条目 | 判级（复核后） | 判定 | 登记 | 现状 |
|---|------|------------|------|------|------|
| 1 | C1-001 BizModel ensure* check-then-act 族（含 publish-then-configure） | P2 | 保留 | 次登记（主=C1-102；与 C4-001 同族合并归 Phase 6） | 未修复 |
| 2 | C1-002 构造器写 JVM 全局 DriverManager.loginTimeout | P2 | 保留 | 本维度主登记 | 未修复 |
| 3 | C1-003 RuleRuntime 输出容器与 scope 并发声明不对称 | P2（理论风险） | 保留 | 本维度主登记 | 未修复 |
| 4 | C1-101 CrossDbConfigHolder.maxCrossDbRows 静态可变配置字段 | **P3（由 P2 降级）** | 保留 + 降级 | 本维度主登记（CL-601 若同站点则为线索引用） | 未修复 |
| 5 | C1-102 单例 BizModel 懒初始化字段族（11 字段 × 8 载体） | P2 | 保留 | 本维度主登记 | 未修复 |

- **最终保留数：5 条 = P2×4 + P3×1（P0×0 / P1×0 / 驳回 0 / 降级 1）**。
- 深挖第 2 轮清扫结论经抽查维持（编译缓存三调用路径、事件发布 DB 行形态、EqlASTParser 无状态、外部消费者 per-call runtime 等与独立重读一致），终止信号有效，不再派发后续轮次。
- 覆盖缺口 3 项（security 包 / field 包 2 文件 / api-core 具名痕迹）已登记待 Phase 6 裁定；跨批合并（C1-001×C1-102×C4-001、C1-101×CL-601）待 Phase 6。
