# WI14 Closure Audit——18-wi14-dim-lookup-verification.md

- Audit 日期：2026-10-02
- Auditor：独立子 agent（fresh session，与实现者非同一 session；全部结论来自 live repo 实跑/实读，未采信 plan 勾选与日志自述）
- 裁定：**PASS**——process 路径判别性 checkpoint/restore 证据、custom 路径自建 keyed backend 用例（:59-62 先例三步逐一实证）、DSL 级 `.stream.xml` checkpoint 声明的 gate 关键作用（gate/runLocal/wiring 三段 live 代码链核实）、§三 #8 桥接形态（落点/javadoc/pom 零 dao/H2 类型证明）、core 1665 + runtime 1190 全绿（含五个新测试类隔离实跑）、门禁全 0、roadmap 解析 31+7 全部成立。Minor 项（§3）均非阻塞，其中 M-1（日志 runtime 计数 1192 vs 实测 1190）建议收口时更正。

## 1. 逐条审计核验（对应审计指令 1-8）

### 1.1 process 路径判别性证据（审计项 1）——PASS

`TestE2EDimLookupWithCheckpoint`（nop-stream-runtime checkpoint 包）隔离实跑 1/1 绿，且包含于全量 1190 中。判别性机制读码推演（`DimLookupEnrichFunction.VersionedTableLookup` + 测试断言逻辑）：

- **机制**：stub 返回 `rows.get(key) + "@" + generation`（v1/v2 代际戳），共享 `AtomicInteger loadCount` 计数每次真实加载；epoch 1 先钉基线——k1 首读得 `merchant-a@v1` 且 count=1，k1 二读 count 仍 1（缓存命中不重载，:107-109）。
- **快照**：`op1.processBarrier(barrier)` → `getLastSnapshotResult()` 非 null 且 `isEmpty()==false`（:111-115）——keyed 维表缓存确参与 checkpoint 快照（AbstractStreamOperator.snapshotState 内 `keyedStateBackend.snapshotState()` 路径，live 实读）。
- **restore 后判别**（epoch 2，新 operator + **v2 代际 stub**，`open()` 后 `restoreState(snapshot)`）：
  - **若 restore 无效/空**：k1 在 op2 侧 miss → 走 `tableLookup.lookup` → 得 `merchant-a@v2` 且 count=2——`assertEquals("merchant-a@v1")`（:131）与 `assertEquals(1, loadCount)`（:133）双双失败。restore 断言无法被「重装加载」蒙混。
  - **若 restore 后缓存被清/急切重载**：同上失败——v1 值只可能来自 restored keyed state。
  - **若 stub 未真正换代**（v1/v2 相同）：k1 侧 reload 不 bump 计数不成立（count=2≠1 失败），且 k2 断言 `merchant-b@v2`（:138）得 v1 失败——代际戳与计数器联合钉死「换代真实发生 + 重载路径存活」。
  - **miss 重载正向证明**：k2 → `merchant-b@v2` 且 count=2（:136-139）。
- 判别性结论：**restore 缓存命中（v1 + 计数不变）与重新加载（v2 + 计数 +1）在两组正交断言下可区分**，roadmap 完成判定「process 路径的维表 join 有端到端 checkpoint 与 restore 证据」成立。
- 细节核对：lookup key 取自记录字段 `record.get("dimKey")`（DimLookupEnrichFunction :65，javadoc 明文 Context.getCurrentKey 未接线）；快照走 `processBarrier` 形态（与 TestE2EWindowOperatorWithCheckpoint 先例同款）。

### 1.2 custom 路径自建 backend 证据（审计项 2）——PASS

`TestCustomOperatorSelfProvisionedBackend` 隔离实跑 1/1 绿，且包含于全量。核验三步与 `ProcessOperator.open()` 先例的一致性（live 逐行对照）：

| 步骤 | ProcessOperator.java（先例） | SelfProvisioningCustomOperator.open()（测试） |
|---|---|---|
| 1 自建 backend | :59-60 `if (keyedStateBackend == null && stateBackend != null) keyedStateBackend = stateBackend.createKeyedStateBackend(Object.class)` | 同款条件 + 同款 `createKeyedStateBackend(Object.class)`（:80-81） |
| 2 恢复挂起态 | :61 `applyPendingRestoreState()` | :82 **直接继承调用**——实存 protected（AbstractStreamOperator.java:260 实读确认），非手写复制品 |
| 3 接线 store | :72-73 `src.setKeyedStateStore(keyedStateBackend)`（StreamingRuntimeContext :69 public 实读确认） | :89-91 `src.setKeyedStateStore(keyedStateBackend)` + `storeWired` 记录 |

断言面（:126-169）：`backendProvisioned()`（自建非空）→ processElement 内 UDF 经 `getRuntimeContext().getKeyedStateStore()` 取 state 成功且 `storeWired=true`（RuntimeContext 注入断言）→ 同 key 二读命中 `loaded:k1`（keyed state 真实持数据）→ `processBarrier` 快照非空（参与 snapshot）→ restore 进新实例后读回 restored 缓存 + `assertSame(backendInstance, udfStoreInstance)`（UDF 所见 store 即 operator 自建 backend 同一实例）。

「不以无自动注入结案」义务：**已达成**——roadmap WI14 完成判定的 custom 半句「按 ProcessOperator.java:59-61 先例在 open 内自建 keyed backend 并有用例」逐项落地（先例三步 + 有用例 + snapshot/restore 参与）。观察项见 §3 M-3（证据为 operator 形态直构，未穿越 `<custom>` DSL 解析——roadmap 措辞不要求该跳）。

### 1.3 DSL 级证据与 checkpoint 声明的作用（审计项 3）——PASS

`TestDimLookupPipelineE2E` 隔离实跑 1/1 绿（含全量）：`.stream.xml`（`<source bean>` → `<keyBy keyExpr="event.dimKey">` → `<process bean="dimLookupFunction">` → `<sink bean>`）经 `DslModelParser` → `StreamModelDslBuilder.of(model).build()` → `env.execute()` → sink 收 [merchant-a, merchant-a, merchant-b]（k1 二次为缓存命中，排序断言 :74-79），独立 init/destroy 照 WI6 处置。

**`<checkpoint>` 声明的关键作用——gate 逻辑 live 代码三段核实**：

1. **gate**（StreamExecutionEnvironment.java :357-359）：`if (checkpointConfig.isCheckpointEnabled() && (checkpointingDeclared || checkpointExecutorFactory != null)) return executeWithCheckpointEngine(...)` 否则 `runLocal(...)`。`CheckpointConfig.checkpointEnabled` **默认 true**（CheckpointConfig.java :40 实读），故判别子实为 `checkpointingDeclared || factory != null`；`checkpointingDeclared` 仅由 `enableCheckpointing(long)`（:157-159）置位，构造器只拷贝 **static setter** 的 `defaultCheckpointExecutorFactory`（:126，ServiceLoader 是惰性 discover 不进构造器）——全新 JVM 且 DSL 未声明 checkpoint 时两者皆 null/false → **runLocal**。
2. **runLocal 无 provisioning**（:453-505 全读）：仅 GraphExecutionPlan + TaskExecutor submit/await，无 TaskCheckpointWiring、无 setStateBackend——KeyedProcessFunction 在 ProcessOperator.open() 内 `stateBackend == null` → keyed backend 不建 → `StreamingRuntimeContext.getKeyedStateStore()` 抛 "Keyed state is only available on a keyed stream"（StreamingRuntimeContext.java :56 实读）——与 plan/log 记录的首跑失败现象一致。
3. **声明后全链**：DSL `<checkpoint enabled="true" interval="50">` → builder `applyCheckpointConfig` `cfg.isEnabled() && interval>0 → env.enableCheckpointing(50)`（StreamModelDslBuilder :194-196 实读）→ gate 走 checkpoint engine → `requireCheckpointExecutorFactory` ServiceLoader 发现 `CheckpointExecutorFactoryImpl`（META-INF/services/io.nop.stream.core.execution.ICheckpointExecutorFactory 实读含该条目）→ GraphModelCheckpointExecutor:717 → `TaskCheckpointWiring.wireTaskCheckpointPipeline` **:167-176** 为链上每个 stateBackend==null 的 AbstractStreamOperator provision backend（行号与 roadmap §3.1 行引用完全吻合）→ ProcessOperator.open() :59-62 自建 keyed backend。

结论：**roadmap「DSL→运行时路径证据」缺口的闭合形态正确**——`.stream.xml` 的 `<checkpoint>` 声明是 runLocal/checkpoint-engine 分叉的载荷开关，执行期发现（plan L62 / 日志）与 live 代码行为一致。

### 1.4 §三 #8 合规（审计项 4）——PASS

- **落点**：`ITableLookup` 实存 `nop-stream/nop-stream-core/src/main/java/io/nop/stream/core/connector/lookup/ITableLookup.java`，单方法 `Object lookup(Object key)` + `extends Serializable`。
- **javadoc 桥接表述**：明文 "MUST go through the Nop platform data-access facades rather than self-managed data sources (roadmap §III #8)：application implementations bridge this interface to IJdbcTemplate (nop-dao) or IBatchLoader (nop-batch-core)，nop-stream-core carries no data-access dependency"——与 roadmap §三 #8 行（:66）口径一致；`IBatchLoader` 实存核实（nop-batch/nop-batch-core `IBatchLoaderProvider` 内嵌接口，plan m4 措辞修正准确）。
- **core pom 零 dao**：`git diff --stat HEAD -- pom.xml` 三处 pom 零输出（未改）；live core pom 依赖仅 nop-commons/nop-core/nop-credential-api/junit-jupiter（grep 实读无 nop-dao）。runtime 侧 nop-dao 为既有 provided 依赖（:83-85），未新增。
- **类型证明实跑**：`TestJdbcTemplateTableLookupAdapter` 2/2 绿——真实 H2（D15 MySQL mode）建表/插数 → `IJdbcTemplate.findFirst(SELECT ... where merchant_id = ?)` 经适配器服务 lookup("m1")/lookup("m2")/lookup("missing")=null；序列化往返测试钉住 **IJdbcTemplate 非 Serializable → transient 持有 + 重新解析** 契约（执行期发现与 plan L60 括注一致）。
- **不自建数据源**：流侧代码（ITableLookup/DimLookupEnrichFunction/测试 stub）零 `DataSource`/`Connection` 获取；H2 连接池仅在 JDBC 适配测试内出现且经 `IJdbcTemplate` 门面消费——形态正确。

### 1.5 实跑汇总（审计项 5）——PASS（runtime 计数见 M-1）

| 命令 | 实测结果 |
|---|---|
| `./mvnw test -pl nop-stream/nop-stream-core` | **Tests run: 1665, Failures: 0, Errors: 0, Skipped: 1**，BUILD SUCCESS（1665 = WI8d 收口后 1663 + 本 WI 新增 2，零退化；skip 为既有跳过） |
| `./mvnw test -pl nop-stream/nop-stream-runtime` | **Tests run: 1190, Failures: 0, Errors: 0, Skipped: 10**，BUILD SUCCESS（1190 = WI6 收口后 1185 + 本 WI 新增 5，零退化） |
| `./mvnw test -pl nop-stream/nop-stream-core -Dtest=TestDimTableLookupFunction` | 2/2 绿（隔离） |
| `./mvnw test -pl nop-stream/nop-stream-runtime -Dtest='TestE2EDimLookupWithCheckpoint,TestCustomOperatorSelfProvisionedBackend,TestDimLookupPipelineE2E,TestJdbcTemplateTableLookupAdapter'` | 5/5 绿（四类隔离：1+1+1+2） |
| 五个新测试类在全量中的包含性 | core-full.log / runtime-full.log 逐类 `Tests run` 行 grep 实证全部纳入 |

**计数裁定**：日志声称 "runtime 1192 绿"，live 实测 **1190**（surefire Results 汇总行）；与 WI6 基线 1185 + 新增 5 的算术自洽，判定 1190 为准确值、1192 为实现者计数失准。全绿判定不受影响。core 1665 与声称一致。

### 1.6 门禁（审计项 6）——PASS（实跑记录）

| 命令 | 退出码 | 结果 |
|---|---|---|
| `node ai-dev/tools/check-doc-links.mjs --strict` | 0 | 0 errors / 3 warnings（均为 nop-bytecode 旧 plan 既存，与本 plan 无关） |
| `node ai-dev/tools/check-nop-stream-invariants.mjs sync` | 0 | `sync: OK` |
| `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` | 0 | Critical/High/Medium/Low 全 0——新接口文件 ITableLookup 为单方法契约接口，test/adapter 双侧有真实实现消费，无 hollow 发现 |
| `parseRoadmapMarkdown`（tools/mission-driver/src/roadmap-check.mjs 实调） | — | **items 31 + milestones 7，done 17，progress 0.55，31 名唯一无静默丢弃**；WI14 行仍 `todo`（预期——本 audit 即 Phase 2 第一项，翻转在 audit 之后） |
| `check-plan-checklist --strict`（备案） | 0 | 非 completed plan，Phase 2/Closure Gates 未勾 → warnings only，符合收口前预期态 |

### 1.7 plan 文本一致性（审计项 7）——PASS（两处观察）

- Phase 1 六项勾选全部 live 落地：core 接口+语义单测（§1.4/§1.5）、TestE2EDimLookupWithCheckpoint（§1.1）、TestCustomOperatorSelfProvisionedBackend（§1.2）、TestDimLookupPipelineE2E（§1.3）、IJdbcTemplate 适配用例含 transient 契约（§1.4）、当日日志条目（§1.9）。Exit Criteria 五项成立（判别性、custom 有用例、类型证明 + core 零 dao、core 定向与 runtime 全量绿、日志更新）。
- **执行期发现记录两处**：plan L62（DSL checkpoint 声明修正 + roadmap 义务闭合形态）与日志 10-02.md WI14 条目第 2 点同款记录，逐句口径一致（runLocal 无 provisioning / wiring provision / ProcessOperator.open() 自建）。
- 审查修订记录（plan 头部）行号全部 live 核实：applyPendingRestoreState=AbstractStreamOperator.java:260 ✓、ProcessOperator :59-62/:72-76 ✓、ITableLookup 落点 ✓、runtime test classpath 有 nop-dao ✓。
- 观察见 §3 M-2/M-4（非不符）。

### 1.8 日志与 git 纪律（审计项 8）——PASS（一处计数失准）

- `ai-dev/logs/2026/10-02.md` 顶部 WI14 条目实读：五段（接口落点/执行期发现/四类证据/实测/Doc-sync）与 live 逐项吻合；唯实测段 "runtime 1192" 与实测 1190 不符（§3 M-1）。其余无虚报。
- `git status` 全量实读：**tracked 改动仅 `ai-dev/logs/2026/10-02.md` 一个文件**；三个 pom、全部 `_gen/`、任何 `_` 前缀生成物零改动（porcelain grep `_gen|/_` 零命中）。untracked 全部为本 WI 新增：plan、core `connector/lookup/` 主+测、runtime checkpoint 包 7 个测试文件、`_vfs/nop/stream/test/` 两个手写 DSL/beans 测试资源（`_vfs` 为既存 VFS 内容根惯例，非生成物）。
- **探针已移除**：`wiring-probe` 全仓 grep（java/xml/md，排除 _tmp/node_modules）**零命中**；新增代码 `System.out.print`/`TODO|FIXME|XXX` 零命中。

## 2. 实跑证据汇总

- core 全量：1665 绿（1 skipped 既有）——exit 0
- runtime 全量：1190 绿（10 skipped 既有）——exit 0
- 隔离：TestDimTableLookupFunction 2/2；runtime 四类 5/5
- 门禁：doc-links 0 / invariants sync OK / hollow core 全 0 / roadmap 31+7（done 17，WI14 todo 预期态）/ plan-checklist 0（warnings only）
- 全部原始日志存 `_tmp/audit-wi14/`（core-full.log、runtime-full.log、core-isolated.log、runtime-isolated.log）

## 3. Minor 发现（均非阻塞）

- **M-1（建议收口时更正）**：日志实测段 "runtime 1192 绿" 与 live 实测 **1190**（0 F/0 E/10 skip）不符；1185（WI6 审计基线）+ 5（新增）= 1190 自洽，1192 为计数失准。零退化判定不受影响，但 Phase 2 收口翻转 roadmap 时应顺手把日志数字改为 1190，避免审计链数字漂移。
- **M-2（建议顺手删）**：`TestDimTableLookupFunction.interfaceIsSerializableContract` 内嵌一行恒真探针——`assertThrows(IllegalArgumentException.class, () -> { throw new IllegalArgumentException("probe"); })`（自抛自捕，不考核任何行为），并有私有 `assertTrue` 助手遮蔽。真实断言（`Serializable.class.isAssignableFrom(ITableLookup.class)`）有效、2/2 绿，仅属测试噪音；收口翻转 Status 时可删探针行。
- **M-3（记录）**：custom 路径证据为 operator 形态直构（`new SelfProvisioningCustomOperator(...)`），未穿越 `<custom>` 元素 → `AdvancedTransforms.buildCustom` 的 DSL 解析跳。roadmap 完成判定措辞（「按先例在 open 内自建 keyed backend 并有用例」）与 plan M1 修订（钉 runtime 单一位置）均不要求该跳——**非缺口**；`<custom>` 元素解析本身是既有能力（WI 前先例），本 WI 义务在「自建可行且有用例」，已达成。
- **M-4（记录，归 WI18 记账）**：`ITableLookup.lookup` 契约以返回 null 表达 miss，`DimLookupEnrichFunction` 以 `cache.value()==null` 判 miss——若真实维表命中行但列值为 NULL，将每次 miss 重载（缓存永不命中）。stub 语义下未构成问题（测试行均非 null），但应用层桥接真实数据库时需注意；建议 WI18 用户文档在 ITableLookup 契约处记账（plan Deferred 段已列 WI18 承接用户文档）。

## 4. 无静默跳过检查

新增主代码仅 1 文件（ITableLookup，单方法接口 + Serializable 契约，javadoc 记录 §三 #8 桥接义务）；其余 12 个新文件全为测试与测试资源。grep TODO/FIXME/XXX/System.out 零命中；scan-hollow core 全 0；无静默返回路径（lookup 缺 key 返回 null 为接口契约明文行为，非静默跳过）；runtime 门禁链（gate→engine→wiring→open）三段 live 代码无空洞环节。

## 5. 结论

WI14 的 process 路径判别性 checkpoint/restore 证据（restore 缓存命中 vs 重载在代际戳+计数器下可区分）、custom 路径自建 keyed backend 用例（:59-62/:260/:69 三锚点先例一致）、DSL 级 checkpoint 声明的 gate 关键作用（三段 live 代码链核实，坐实 roadmap「DSL→运行时路径证据」缺口闭合形态）、§三 #8 桥接合规（落点/javadoc/pom 零 dao/H2 类型证明）全部在 live repo 成立；core 1665 + runtime 1190 实跑全绿（含五类隔离）；门禁全 0；roadmap 31+7 解析无丢弃；_gen 纪律与探针移除干净。

**裁定 PASS**：本 audit 构成 plan Phase 2 第一项证据。剩余收口动作（实现者执行，非本 audit 范围）：① 更正日志 runtime 计数 1192 → 1190（M-1），可选删 M-2 探针行；② roadmap WI14 `todo` → `done`（括注单层无嵌套，建议括注「两路径证据齐备；维表访问面=ITableLookup 桥接（应用层接 IJdbcTemplate/IBatchLoader），不自建数据源」）+ `parseRoadmapMarkdown` 复核 31+7；③ plan Phase 2 勾选、Status → `completed`、Closure 段落与本 audit 证据回填；④ `check-plan-checklist --strict` 与 `check-doc-links --strict` 复核退出码 0。M-3/M-4 仅为记录，无本 plan 动作义务（M-4 建议 WI18 记账）。
