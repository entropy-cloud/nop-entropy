# rule 热更新失效传播、外部 JDBC 超时与连接治理、懒初始化安全发布、对账/同步/JOIN 包络与告警半程归一（CRA 8.35 R3-05 设计裁定）

> Status: active（final-state 写法——本文只记录当前生效的设计决策与使用契约）
> Last Reviewed: 2026-11-02
> Source: plan 2528（CRA Phase 8.35 R3-05 rule-metadata 批）；审计目录 `ai-dev/audits/2026-10/2026-10-22-1201-deep-audit-nop-rule-metadata-concurrency/`；批次定义 `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.3 R3-05；族级 SSOT [ledger-retention-policy.md](ledger-retention-policy.md)（第七面/G2 承接面）；判据消费方 [cache-invalidation-single-point.md](cache-invalidation-single-point.md)（plan 2500 R1-05 产物）
> Owners: 裁定①④⑤连接域（nop-metadata-service connection + quality 编排）、裁定②（nop-rule-dao model）、裁定③（nop-metadata-service entity 懒初始化族）、裁定⑥⑦⑧（对账/同步/JOIN 包络域）、裁定⑨⑩（告警工作流同表面 nop-wf 消费）

本文是 plan 2528 全部 10 项成员修复的唯一设计裁决权威。每条裁定给出：决策、理由、被否决的备选、零触碰边界。行号以 live 基线 `cbef595fca`（分支 `concurrency-audit-remediation`）亲读值为锚，后续漂移以 live 为准。

## 裁定① W-01：外部 JDBC 超时预算单点收口（P1，B1-10 三登记点统一）

**决策**：

1. 超时预算收口在 `MetaDataSourceConnectionProcessor` 的**连接获取单点**：`withConnection`（:198-212）与 `testConnect`（:214-255）两条建连路径共用「取 Connection → 应用连接预算 → 首次 socket 读」的包内单一打开缝，预算应用严格位于 `dataSource.getConnection()`（:204/:239）之后、`conn.getMetaData()`（:205/:240，第一 socket 读）之前。预包含两层：**建连握手层**经连接属性（裁定④，`nop.metadata.datasource.login-timeout-seconds`，缺省 5）；**建连后 socket 读层**经 `Connection.setNetworkTimeout`（`nop.metadata.datasource.network-timeout-seconds`，缺省 30）。超时命中后 SQLException 沿既有包装面抛 `ERR_DATASOURCE_CONNECT_FAILED`（语句执行路径沿 `ERR_QUALITY_SQL_FAILED`/`ERR_TABLEREF_EXEC_FAILED` 各自既有包装），不再有无限期阻塞。
2. **配置键落点**：新建 nop-metadata-service 类型化 Configs 接口（形态对齐 `NopMetadataLedgerConfigs` 先例），两键均 `nop.metadata.datasource.*` 惯例。`network-timeout-seconds` 缺省 30 = 同单元 webhook 投递既有显式超时惯例（`CheckpointActionDispatcher.DEFAULT_WEBHOOK_TIMEOUT_SECONDS = 30`，:74；配置注入后归一 :109-112；请求侧 `setTimeout` :249）；`login-timeout-seconds` 缺省 5 = 保留 AR-02 防内网探测既有窗口（`DEFAULT_LOGIN_TIMEOUT_SECONDS` :124）。非法值语义：负值显式失败（复用 `ERR_DATASOURCE_CONFIG_INVALID`，不静默缺省）；0 = 显式禁用档（WARN 一次 + owner doc 声明风险，形态对齐 `nop.metadata.ledger.retention-days=0` 禁用档先例）。**缺省 30 的 workload 口径**：大表 COUNT/剖析聚合等 server 端长时间执行、期间不出字节的语句会在 30s 读阻塞处被打断——需要长跑分析的场景经 `network-timeout-seconds=0` 显式关闭档承载（owner doc 声明风险）；该 workload 口径与关闭档语义为 Phase 3 owner doc（`nop-metadata.md`「并发与资源契约」节）落账义务。
3. **驱动兼容论证（三白名单驱动族逐一，白名单 :71-72/:105-109）**：
   - **MySQL Connector/J**：`setNetworkTimeout` 官方实现即动态更新底层 socket SO_TIMEOUT；握手层属性 `connectTimeout`（单位毫秒，驱动缺省 0 = 无超时）以 `login-timeout-seconds × 1000` 注入。
   - **PostgreSQL pgjdbc**：`setNetworkTimeout` 设置 socket SO_TIMEOUT（9.4.1210 起实现），Executor 参数实际被忽略；握手层属性 `connectTimeout`（单位秒，驱动缺省 10）以 `login-timeout-seconds` 注入。
   - **H2**：白名单子集仅 `jdbc:h2:mem:`/`jdbc:h2:file:`（进程内，无网络 socket）；`setNetworkTimeout` 仅对 tcp 会话有意义、嵌入式连接抛不支持异常——H2 族跳过 setNetworkTimeout 与握手属性注入（显式文档化分支，非静默：跳过伴随 DEBUG 日志，owner doc 声明「H2 白名单子集为进程内连接、网络超时语义空置」）。
   - **Executor 形态**：`setNetworkTimeout` 传直接执行器（`Runnable::run`）。JDBC 规范要求 executor 非 null；两个白名单网络驱动（MySQL/PG——H2 白名单子集为进程内连接）均以 SO_TIMEOUT 实现超时、不使用异步中断执行器，直接执行器满足规范且零生命周期管理。否决专用守护线程池（无消费方、新增生命周期面）。
   - **getMetaData 时序注记**：MySQL/PG 下 `getMetaData` 的产品名/版本读为握手期缓存（不产生额外 socket 往返），进程内 H2 无网络读——预算在 `getMetaData` 之前应用的时序要求保持（保守覆盖首个真实 socket 元数据查询/语句读）。
4. **statement 级 setQueryTimeout 采否**：不采。当前语句创建点共 **17 处**（grep 实录，7 文件：MetaEntityProfiler 6、MetaQualityRuleExecutor 5、SqlViewFieldTypeInferrer 2、MetaCatalogCollector 1、AggregationHelper 1、MetaEntityQueryExecutor 1、MetaJoinExecutor 1），全部在处理器提供的 Connection 上执行——连接级 `setNetworkTimeout` 以 SO_TIMEOUT 界定同一等待（服务端不出字节的长时间执行同样被读阻塞超时打断），单点覆盖全部 17 处；逐语句 `setQueryTimeout` 即批次行明文禁形态的散点修复。查询级主动取消（KILL QUERY/cancel 信号）能力不引入——超时语义定为「客户端等待上界」，owner doc 声明。
5. **socketTimeout/connectTimeout URL 参数注入采否**：不采。单位语义驱动分歧（MySQL 毫秒/PG 秒/H2 无此参数）+ 注入发生在 AR-02 URL 校验（:376-432）之后、改变已校验 URL 面（黑名单 contains 校验与注入参数的交互面需重新论证）；连接属性路径经 `SimpleDataSource.properties`（:42-54 合并进 `DriverManager.getConnection` info）到达同一驱动参数，零 URL 变异、零 nop-dao 触碰。
6. **checkpoint 运行标记时限兜底（C7-102 建议第 3 项）采否**：不采。运行标记永久残留的根因是 finally 不执行（线程无限阻塞），预算单点收口后线程必然在有限时间返回、finally（`NopMetaQualityCheckpointBizModel` :266-269）必然释放标记；带时间戳的强制回收引入「标记强制清除后旧执行仍在跑」的双执行窗口，与残余风险不成比例。登记 Non-Blocking Follow-up。
7. **覆盖论证（grep 实录替代继承计数——评审补充条款）**：外部 Connection 获取全仓仅两处入口——`withConnection`（**直接消费点 13 处**/9 文件：NopMetaDataSourceBizModel 2、NopMetaEntityQueryAction 2、NopMetaQualityRuleBizModel 1、TableReferenceExecutor 1、MetaJoinExecutor 2、ExternalExternalJoinAggregationProcessor 1、ExternalAggregationProcessor 1、MixedSameDbJoinAggregationProcessor 2、SqlViewFieldTypeInferrer 1；另 `NopMetaProfilingRuleBizModel`/checkpoint 编排等经 `TableReferenceExecutor` 间接到达同一入口）与 `testConnect`（独立获取路径 :214-255，评审 Minor-3 纳入钉面）——均收敛于同一打开缝自动覆盖。平台事务连接（`TableReferenceExecutor` ENTITY 路径 :79 `IJdbcTransaction.getConnection()`）为平台自有连接池面，不在本裁定域（零触碰）。

**零触碰边界**：`SimpleDataSource`（nop-dao，修复在 processor 使用面——`properties` 注入口 ：169-171 既有）、`LocalJobScheduler`（nop-job 信号面观察载体）、`MetaQualityRuleExecutor` 三个 query helper（:706/:728/:752，连接级收口后零触碰）、`MetaEntityProfiler`/`MetaCatalogCollector`/`ExternalTableStructureReader`（F3 同族消费面，单点自动覆盖）、AR-02 安全链全部既有校验与 `TestMetaDataSourceConnectionSecurity` 其余用例零回退。

## 裁定② W-03：规则热更新失效传播——钉定集扩展至 NopRuleNode 子行（P1，plan 2500 判据消费方）

**决策**：

1. 采**方案 (1) 装载侧钉定扩展**：`DaoRuleModelLoader.loadRule` 在既有定义行钉定（:84 `traceDepends(DaoEntityResource.makeDaoResourcePath(entity))`）之后，对 `entity.getRuleNodes()`（`buildRuleModelNode` :163 消费的同一子行集合）逐节点补 `traceDepends(节点行 DaoEntityResource path)`。失效判据精确到子行：任何写路径（一等 GraphQL/RPC CRUD、daoProvider 直写、管理脚本）对 `NopRuleNode` 行的变更都会改变该行 updateTime，被平台失效判据检出。
2. **验证口径以 plan 2500 新判据为准**（`DefaultResourceChangeChecker` 单点：mtime 相等比较 + 写入年龄宽限双条件 + 装载前后双窗口 + TTL 兜底）：宽限期配置 `nop.core.component.resource-cache.change-grace-period`（缺省 2s，0=关闭档）；TTL 兜底 `nop.core.component.resource-cache.timeout`（缺省 `Duration.ofMinutes(5)`，CoreConfigs）。红② 测试的时间设计与宽限机制兼容：测试经 `@NopTestProperty` 注入 `change-grace-period=0`（关闭档）+ `nop.orm.dao-resource-check-interval`（`OrmConfigs` :74-76，缺省 5000ms）调低至百毫秒档——节点行变更在下一检查周期内被检出，不依赖旧「纯 mtime 相等」口径，也不依赖真实时钟快进。
3. **开销量化（失效风暴风险评估）**：`DaoEntityResource` 自身按 `nop.orm.dao-resource-check-interval`（缺省 5s）自节流（`checkLoad` :95-102 窗口内早退零查询），plan 2500 后 entry 级判据结果记忆化 + refresh-min-interval 门再节流遍历频率。扩展后每条规则模型的失效检查成本 = N 节点 × 每 5s 至多 1 次主键点查；典型规则数十节点、N=50 时单规则 10 次点查/秒上限，主键点查为廉价查询。成本超限的调优旋钮 = 既有 `dao-resource-check-interval`，零新参数。
4. **否决方案 (2)（`NopRuleNodeBizModel` save/delete override 后 touch 父定义行）**：写侧覆盖不完备——override 只拦截经该 BizModel 的写路径，`daoProvider.daoFor(NopRuleNode)` 直写、其他模块级写面均绕过；且把失效语义耦合进 service 层（每次节点编辑附加一次父行 UPDATE，与定义行自身编辑的 updateTime 语义纠缠）。方案 (1) 在装载侧闭合全部写路径，与平台失效机制对齐、改动最小。
5. **方案 (3)（xbiz 裁剪子行写面）禁选**（生成物 PA 硬约束）：`_NopRuleNode.xbiz`（`nop-rule-service/src/main/resources/_vfs/nop/rule/model/NopRuleNode/_NopRuleNode.xbiz`）`DefaultBizGenExtends forEntity="true"` 生成物，触碰即生成管线 plan-first；子行一等 CRUD 语义维持（修复不得裁剪写面）。

**零触碰边界**：`DaoEntityResource`（:90-123）/`DefaultResourceChangeChecker`/`ResourceComponentManager` 平台设施零修改预设（:84 既有使用面先例——条件申报①不触发；如实施中发现必须改本体，按 plan 四件证据链追加申报后方可）；定义级正常失效路径钉 `TestNopRuleDefinitionBizModel` :112 零回退；`NopRuleNodeBizModel`（:29 `extends CrudBizModel` 无 override）零触碰；`nop-rule.md` :296 热更新表述的契约缺口由 E5 owner doc 增补收敛（Phase 3）。

## 裁定③ W-04：ensure\* 懒初始化族统一安全发布（P2，R23 懒初始化守卫族主）

**决策**：

1. 采 **volatile 化 + configure 序修复**（低成本分支），否决生命周期钩子初始化。12 个懒初始化实例字段全部加 `volatile`（9 载体逐一，live 行号）：
   | 载体 | 字段（live 行号） | ensure 点 |
   |---|---|---|
   | NopMetaQualityCheckpointBizModel | tableRefExecutor :154 / checkpointExecutor :157 / actionDispatcher :160 | :531-537 / :541-547 / :549-553 |
   | NopMetaEntityBizModel | tableRefExecutor :108 | :430-436 |
   | NopMetaDataSourceBizModel | tableRefExecutor :155 | :870-876 |
   | NopMetaQualityRuleBizModel | tableRefExecutor :109 | :303-309 |
   | NopMetaProfilingRuleBizModel | tableRefExecutor :76 | :133 |
   | NopMetaQualityScoreBizModel | scorer :27 | :68-74 |
   | NopMetaLineageEdgeBizModel | queryAction :47 | :49-55 |
   | NopMetaEntityQueryAction | sqlFieldTypeInferrer :57 / tableRefExecutor :58 | :163-171 |
   | QualityAlertWorkflowProcessor | tableRefExecutor :56 | :167-173 |
2. **publish-then-configure 必随 volatile 同 diff 修 configure 序**：`ensureActionDispatcher`（:531-537）改为局部变量先构造、先 `configureWebhookSsrf`（SSRF allowlist/timeout，现 :534）+ `configureRedirectPolicy`（现 :535）后、再整体赋值字段——并发窗口内投递不再按缺省保守策略执行。其余 11 字段为构造期一次性装配的不可变依赖组合，volatile 安全发布即闭合可见性缺口。
3. **否决生命周期钩子初始化（`afterPropertiesSet`/构造时 orm() 延迟解析）**：动机面（构造时 `orm()` 不可用，`NopMetaQualityCheckpointBizModel` :153/:156/:159 与 :540 注释自证）仅约束**首次创建时机**，不解决**可见性**——钩子方案仍需 volatile 配合才闭合缺口，却引入 IoC 生命周期耦合与 9 载体逐个改造的不成比例面。volatile + configure 序两案中最小、与 R23 族先例（5.2 MA-06→5.1 I-04 DCL/volatile 同法）对齐。
4. **并发语义声明**：check-then-act 在 volatile 下仍可能重复构造（良性竞态——两线程各建一个实例、后写胜出），闭合的是**可见性缺口 + publish-then-configure 中间态 + 重复构造窗口**；完全串行化的 DCL 不采（加锁收益不抵读路径开销，族先例即 volatile 形态）。触发面 = 两线程不同 checkpointId 首并发；同一 checkpointId 被 R4.3 运行标记（`checkpointRunLocks.putIfAbsent` :203-208）挡在 ensure 之前。**红③ 判据自洽性（M2）**：并发钉**不含 identity 单实例断言**——identity 断言与良性竞态声明自相矛盾、构成构造性 flake；并发钉只承载可见性半边（configure 先于发布、生效配置值正确）。C10 矩阵 #1/#6 原文「断言单实例」半边随修摘除（随红③ 同形登记）。

**零触碰边界**：R4.3 运行标记面（:203-208/:266-269）零触碰；既有并发钉 `TestNopMetaQualityCheckpointBizModel`（:595/:627/:641/:700）与 `TestCheckpointActionDispatcherConcurrency`（:38/:85 直构不经 ensure 路径——邻接保护形态）零回退；`daoProvider()`/`orm()` 访问器调用形态零改动。

## 裁定④ W-05：DriverManager 全局写移除，loginTimeout 改每连接属性路径（P2，反向钉同 diff）

**决策**：

1. **移除构造器全局写**（`MetaDataSourceConnectionProcessor` :140-142/:148-154，写点 :150，全仓 main 面唯一）+ 移除 `DEFAULT_LOGIN_TIMEOUT_SECONDS` 的全局语义（常量保留为缺省值来源）。`loginTimeout` 改为**每连接属性注入**：`buildDataSource`（:269-297）按裁定①的驱动族分派，经 `SimpleDataSource.setProperties`（:169-171）注入族特定握手属性——`SimpleDataSource.getConnection` 将其合并进 `DriverManager.getConnection(url, mergedProps)` 的 info（nop-dao :42-54），全局副作用为零。
2. **族分派（与裁定①共用同一分派表）**：MySQL 族注入 `connectTimeout = login-timeout-seconds × 1000`（毫秒）；PostgreSQL 族注入 `connectTimeout = login-timeout-seconds`（秒）；H2 族（白名单仅 mem/file）不注入——进程内连接无握手悬挂窗口（显式文档化）。
3. **「属性不受支持时回退全局设置并 WARN」采否**：不采。回退分支即重新引入本工单缺陷（JVM 全局态接管宿主）；两个网络驱动族的 `connectTimeout` 属性均为官方文档属性（自证兼容面），H2 白名单子集无握手面——回退分支无真实触发场景，保留即引缺陷入口。
4. **反向钉同 diff 改写（批次行显式义务，禁拆分交付）**：`TestMetaDataSourceConnectionSecurity.testLoginTimeoutSetGlobally`（:776-792，断言体 :787-791 `assertTrue(after == DEFAULT_LOGIN_TIMEOUT_SECONDS)`）改写为双断言： (a) **全局态不变**——处理器构造 + 完整 `testConnect` 执行后 `DriverManager.getLoginTimeout()` 等于进入前值（未修复态必红：构造器写 5）； (b) **每连接属性路径**——经驱动族属性构建的包内缝断言 MySQL 形态 URL 注入 `connectTimeout=5000`（白盒缝断言，H2 形态无属性可断言不构成本钉面）。先红后绿证据在改写与移除同 diff 内按「先加新断言跑红 → 同 diff 移除全局写跑绿」留痕（沿 2525/2527 反向钉改写先例）；随同 diff 修正该测试 ：767-773 滞后 javadoc（「不断言精确秒数」表述与实际断言不一致——勘误 3）。改写后与裁定①的 `setNetworkTimeout` 零交互（连接级 API 不触红本钉——批内次序自由度）。
5. 接口 javadoc 同 diff 修正：`IMetaDataSourceConnectionProcessor` 类注释「建连超时经 DriverManager#setLoginTimeout 在构造函数中设置」段（:19-20 区）改写为连接属性路径声明（In-Scope Minor-5 增补面）。CL-205/CL-501 归并注记随批落 cra-index 收敛注。

**零触碰边界**：`SimpleDataSource`（nop-dao）零触碰（`setLoginTimeout` no-op :81-83 维持现状——修复在 processor 使用面注入 `properties`）；AR-02 黑洞 IP 建连测试面与该测试类其余安全用例零回退；注入的 `connectTimeout` 属性键不在 `DANGEROUS_URL_TOKENS`（:82-102）黑名单域（黑名单校验 URL 子串、属性走独立通道）。

## 裁定⑤ W-07：checkpoint 编排建连复用 + DataSource 实例缓存（P2，G1 批内协同半边）

**决策**：

1. **编排层按数据源分组复用（主修复面）**：`MetaQualityCheckpointExecutor.execute`（:129-190）改两阶段——先对全部规则解析 table-reference（现 `executeSingleRule` :299-331 内联的解析上提），再按 `ref.getDataSource()` 身份分组；每组 external/sql 规则**单次** `connectionService.withConnection`，callback 内循环逐规则 judge——模板 = 同单元既有批量路径 `executeQualityRulesForDataSource`（`NopMetaQualityRuleBizModel` :219-279，:248 withConnection callback 内 ：249-277 循环全部规则，形态已核证正确）。`ENTITY` kind 规则（平台事务路径，`TableReferenceExecutor` :66/:79-95）不参与分组、维持逐规则 `tableRefExecutor.execute`。
2. **失败隔离粒度变更声明**：同组规则共享一次建连——组内单规则的连接级失败（socket 断开等）影响该组 callback 内后续规则的窗口；逐规则 try/catch（批量模板 ：270-276 形态）保持 per-rule 结果隔离（失败进 errors + ERROR 结果行，不拖累其余规则）。owner doc 声明该粒度变化。
3. **DataSource 实例缓存（旁路面，与裁定④同文件 diff 分组防混）**：`buildDataSource`（:289-296 每调用 `new SimpleDataSource`）增实例缓存，载体 = nop-commons `LocalCache.newCache(name, CacheConfig)` 使用面（先例：`SysSequenceGenerator` :74-75、`DashboardPanelQueryCache` :39-40），缓存名 `nop-metadata-datasource`，参数为**固定常量 maxSize=64 / expireAfterWrite=5 分钟**（`CacheConfig.newConfig(64, 300_000L)`，两工厂形态 `CacheConfig` :58/:64；零新配置键——缓存面无运维调参需求，超时面配置收敛于裁定①两键）。
4. **缓存键 = 不可逆摘要 + 超时配置入键（凭证轮换 evict 配对 F8 双层）**：键 = jdbcUrl/username/password/driverClassName **及两超时配置值**（login-timeout/network-timeout）的 **SHA-256 不可逆摘要**（要素在 `mergeCredentialConfig`（:311-357）**之后**解析计算）；摘要值与凭证/键材料**禁入日志**（仅记摘要前缀与命中与否——对齐 P2-08 脱敏纪律）。第一层——凭证库轮换改变解析出的 username/password → 摘要变化 → 自然走新条目（零失效监听器依赖）；超时配置变更同摘要变化 → 即换实例（**无陈旧窗**；否决「显式声明陈旧窗=TTL」分支）。第二层——TTL 5min 上界约束轮换感知延迟与内含凭证的内存驻留；maxSize 有界防连接配置基数无界。
5. **W-01 叠加语义**：缓存的是 `DataSource` 实例而非 Connection——连接仍为每次 `withConnection` 一次性获取/关闭，`setNetworkTimeout` 对每次新连接重新应用（幂等、无陈旧预算残留），握手属性随 DataSource 实例固化（键含要素摘要，配置变更即新实例）。
6. **旁路错误包装漂移声明（m3）**：分组形态绕过 `TableReferenceExecutor.executeOnExternalConnection`，其 SQLException 包装（`ERR_TABLEREF_EXEC_FAILED` + `ARG_META_ENTITY_ID`，:114-126）在组内 callback 内**复刻同型包装**——checkpoint 规则错误面错误码与参数零漂移。规则 ref/目标表解析上提后，target-table-missing（`executeSingleRule` :303-308 `ERR_CHECKPOINT_RULE_TARGET_TABLE_NOT_FOUND`）等解析失败仍落 per-rule errors + ERROR 结果行隔离（同一 catch 面 :155-189），不因分组改变隔离粒度。**分组键 = `dataSourceId`**（数据源实体主键等值——ref 解析为独立实例，禁用对象引用身份比较）。

**否决的备选**：连接池化设施引入（Non-Goal，audit 原文「不引入新池化设施」——DataSource 实例缓存 ≠ 池化，连接获取仍一次性）；按 `dataSourceId` 单键缓存（需在 `NopMetaDataSource` 实体更新路径挂失效钩子，跨实体耦合面大于要素摘要自失效）；`TableReferenceExecutor` 增加「复用既有 Connection」入口（评审补充条款核证：**无需**——编排分组直接以既有 `connectionService.withConnection` + `ruleExecutor.judge` 组合实现，分派层零触碰预设维持、边界变更条款不触发）。

**零触碰边界**：`TableReferenceExecutor`（:66-69/:107-135）零触碰；批量模板 `executeQualityRulesForDataSource` 本体零触碰（仅作形态模板）；`IMetaDataSourceConnectionProcessor` 单点获取语义不变（不新增获取通道）。

## 裁定⑥ W-08：对账候选池 O(1) 复用 + 池加载显式上界（P2，G1 协同半边）

**决策**：

1. **池加载上提**：`ReconciliationExecutor.execute`（:68-115，行循环 :77-115、逐行 `reconcile` 调用点 :90）改执行开头经 processor 批载入入口**一次**加载候选池（`LocalReconciliationProcessor.loadCandidates` 现 :94-105 每行全池 `findAllByQuery` 无 LIMIT——C5-104 同站点），逐行打分复用同一池；`parseProperties`（现每候选每行重复 JSON 解析）随池缓存，单次执行内每候选解析一次。
2. **池加载显式 limit**：新配置键 `nop.metadata.reconciliation.candidate-pool-limit`（与既有 `nop.metadata.reconciliation.fetch-limit` 同 face 段惯例，行侧闸 `TestReconciliationFetchLimitMarker` :50/:63-65 零回退），缺省 1000；超限**显式失败**（模块错误码 fail-fast，对照同模块血缘 `maxEdges` 先例——`NopMetaLineageEdgeBizModel` :41 `configuredMaxEdges` + fail-fast 形态），不静默截断候选池（截断即静默改变匹配语义）。AR-13 `DEFAULT_CANDIDATE_LIMIT=50`（:88-89 既有注释自证）候选序列化有界零回退。
3. **池快照窗一致性语义**：候选池为单次对账执行的执行期快照——执行期间并发候选变更对本轮不可见。此为行为变更声明（现行为逐行重载可观察到执行中期变更，代价是 R 次全池重载）；owner doc 声明「单轮快照一致性」语义。
4. **exact 策略下沉 DB 等值查询采否**：不采。O(1) 池复用已消除 N+1；下沉引入逐行等值查询回 N 次查询形态且分裂 match 策略执行路径。登记优化候选 follow-up。

**否决的备选**：池加载缓存跨执行复用（跨请求缓存候选池引入陈旧候选正确性面——快照窗收敛到单执行内是最小安全边界）；pool limit 采静默截断（违反「不静默截断」语义族）。

**零触碰边界**：`TestReconciliationFetchLimitMarker` 行侧 fetch-limit 闸零回退；AR-13 候选序列化有界零回退；`NopMetaReconciliationBizModel` 既有钉（:66-154）零回退；匹配算法（levenshtein :126 区/exact）零改动。

## 裁定⑦ W-09：syncExternalTables 流式化（P2，R02 无界物化族）

**决策**：

1. 采**流式化**（否决上限旋钮 fail-fast）：`ExternalTableStructureReader.read`（:52-92，:58 累积 List、:79 逐表全列 readColumns、:80 add）改为**惰性逐表产出**形态（迭代器/回调——表行游标驱动，每表读完列即产出），`syncExternalTables` 消费面（`NopMetaDataSourceBizModel` :645-663，:648 reader.read）在既有 withConnection callback 内逐表接收并 upsert——峰值内存从「全 catalog 表×列物化」（万表×40 列≈100MB+ 量级）收敛为单表列集。规模不再受进程堆约束（外部库规模决定）。
2. **消费面触碰登记（预设推翻显式登记——同模块非 PA，无需四件证据链）**：NMDSB :648-663 循环头从遍历 List 改为遍历惰性产出（`reader.read` 返回形态变化波及消费面循环头，逐表 upsert 体与 errors 累计/syncedCount 计数形态不变）。plan 头部「reader 侧流式化则消费面零改」预设按评审 Minor-4 同型条款登记推翻。
3. **三既有语义零回退论证**： (a) **AR-17 部分持久化**（:663 区 scan 级失败已同步表留存）——流式化下 scan 中途 SQLException 时已产出表已各自 upsert，留存语义保持且触发序更自然（先成功先落库）； (b) **AR-23⑤ 故障分类**（scan 级 SQLException 携 productName 原始消息，:85-88 既有）——分类路径零改动：流式形态中 scan 级 SQLException 的 wrap **落点保持在 reader/迭代器内部**（产出循环内捕获并按 :85-88 原样包装 `ERR_EXTERNAL_TABLE_SCAN_FAILED` 整体抛出——M3）——第 k 表读列失败 = scan 级故障，终止本轮同步并沿 AR-17 既有外层 catch 留存 1..k−1 已 upsert 表；per-table errors 通道（:651-660）仅承载 upsert 自身失败； (c) **AR-07 per-key 锁 + REQUIRES_NEW 独立事务**（`upsertExternalTableGuarded`，:613-619 注释锚）——逐表 upsert 调用形态零改动，事务边界不被流式化触碰。
4. **否决上限旋钮 fail-fast**：上限要么低到拒绝合法大 catalog（万表同步是正当场景，fail-fast 即功能性拒绝）、要么高到永不触发（不消除无界物化，仅挪置悬崖）；流式化在零数据损失下消除峰值，无尺度悬崖。

**零触碰边界**：方言识别/拒绝面（`requireSupportedDialect`，`TestExternalTableStructureReader` :37-57 钉）零回退；per-table 事务与锁形态零改动；`schemaPattern` 语义零改动；消费面 errors/syncedCount 摘要契约零改动。

## 裁定⑧ W-10：同库 JOIN 正值 limit 硬闸 + 注释契约修正（P2，R02 族）

**决策**：

1. 采**入口显式拒绝（第三案）**：`normalizeJoinQueryLimit`（`NopMetaEntityBizModel` :461-472，现正值原样透传）正值超过有效上限即抛 `ERR_PAGINATION_LIMIT_TOO_LARGE`（既有错误码，param ARG_LIMIT——`AggregationHelper.truncateCrossDb` :962 同码先例），有效上限 = `configuredMaxQueryLimit`（:76-78，`nop.metadata.query.max-limit|0`）>0 时取配置值、否则 `DEFAULT_MAX_QUERY_LIMIT = 10000`（:74）——与 `normalizeQueryLimit`（:445-455 Math.min 封顶）同一上限来源。单点归一覆盖 `queryJoinData`（:326/:335）与 `queryAggregation`（:349/:361）两个公开 `@BizQuery` 入口（同一消费点覆盖声明）。**「不静默截断」API 语义维持**——闸 = 显式拒绝，非伪造截断；负值拒绝（:464-466）与 null/0 缺省（:467-468）形态零改动。
2. **注释契约修正（同 diff）**：:456-460 javadoc「正值原样透传，超大值由截断层显式拒绝」改写为「正值超上限由入口归一化显式拒绝（`ERR_PAGINATION_LIMIT_TOO_LARGE`）」——截断层（`AggregationHelper.truncateCrossDb` :942-971 ← `CrossDbJoinMerger.truncate` :262-269 委托）实际仅存在于跨库 merge 路径（`MetaJoinExecutor` 同库路径 ：221/:269 `appendLimitOffset` + :648 `executeJdbcQuery` :670 无任何行数闸——缺陷机制事实），修正后指称与实际路径一致，§5.3 发现级 drift 收敛。
3. **否决案 (a)（Math.min 静默封顶，对齐 normalizeQueryLimit）**：join/聚合入口声明语义为「分析入口、超限显式拒绝」（AR-09），静默封顶改变已声明 API 语义且掩盖调用方超配错误；**否决案 (b)（maxCrossDbRows 型新常量闸，`CrossDbJoinMerger` :26/:101/:109 先例）**：与既有 `configuredMaxQueryLimit` 双上限并存 = 参数发明 + 双值漂移面；同库路径与跨库路径的闸来源分离属既有架构（跨库闸管 merge 乘积、同库闸管入口 limit），不强行统一。

**零触碰边界**：负值钉 `TestLimitNegativeValueInvariant`（:67-71）、跨库闸钉 `TestMetaJoinTruncateOverflow`（:24-44）+ `TestMetaJoinCrossDbMergeNullSemantics`（:178-196）、`TestAggregationHelperErrorParam`（truncateCrossDb 入口）零回退；`normalizeQueryLimit`（queryData 入口）封顶形态零改动；跨库 merge 路径零改动。

## 裁定⑨ W-12：告警工作流半个成功归一——分支 (a) 事务内自清理（P2，R10 族）

**决策**：

1. 采 **R10 三选一分支 (a) 补偿（audit 建议 (1) 预设）**：`QualityAlertWorkflowProcessor.createAlertWorkflow`（:86-97）内 try/catch 包裹 `wf.start`（:96）——start 半程失败（`initCreateStatus` :275 已落库 CREATED → `getActors` :282 起始步 actor 解析抛 `ERR_WF_ACTOR_NOT_EXISTS`（`WfActorAssignSupport` :119-120）/saveStarted :288 前任一步抛错）时，经 wf 实例既持 record（`initCreateStatus` :193-208 落库后 wf 侧持有）取实例引用，调用公开 `IWorkflowStore.removeWfRecord`（接口 :49；实现 `DaoWorkflowStore` :172-175 = `wfDao().deleteEntity`）删除半程实例，随后重抛原异常。调用方既有 catch（`NopMetaQualityRuleBizModel` :171-179，:175-178 LOG.error + `ERR_QUALITY_RULE_EXEC_ISOLATED`）语义零变化。
2. **事务语义论证（自清理与 store 事务先后）**：`DaoWorkflowStore` 经 `IEntityDao` 直接操作（:167-175），与规则执行同一 ORM 会话、无 REQUIRES_NEW——自清理 deleteEntity 在同一事务内：外层提交 → 创建+删除同净效应（无孤儿）；外层回滚 → 创建本身消失（天然一致）。不存在「清理在事务外迟到」窗口。清理体自身失败（理论不可达——同会话同实体 dao；record 引用获取失败）时保留原异常并 ERROR 日志显式可见（fail-visible，非吞）。
3. **`IWorkflowStore` 注入形态**：`QualityAlertWorkflowProcessor` 增注公开 `IWorkflowStore`（bean `nopWorkflowStore`，`wf-dao.beans.xml` :11-12 `ioc:default`）；注入缺失（部署裁剪 nop-wf-dao 理论形态）时 WARN + 直接重抛原异常（退化为现状行为、显式可见，非静默）。
4. **否决分支 (b)（异常传播、结果行随事务整体回滚）**：一次告警通道部署漂移（角色未配置）导致已正确判定的质量结果行丢失、单次执行证据消失——违反 G3 告警通道隔离语义（告警侧失败不得拖垮规则执行主链）；且需 owner doc 声明「告警创建失败显式化、单次执行结果丢失」的行为变更 + 既有 `ERR_QUALITY_RULE_EXEC_ISOLATED` 隔离语义钉（`TestQualityAlertWorkflowProcessor` :39-45）回退评估——两案中行为代价显著更大者。**分支 (c)（引擎侧 CREATED 延后持久化）禁选**（nop-wf 平台面，WorkflowEngineImpl :229/:275/:282/:288 窗口属平台；audit 原文不在本单元修复面 + PA 条件申报②）。
5. **G3 三失效层边界声明**：本裁定只闭合第三失效层（创建半程残留）；W-24（投递无重投）/W-25（无去重）= P3 条款不入批，零触碰。
6. **与第七面同表协同（对齐纪律第四条）**：增量孤儿清理（本裁定，失败即清、按实例）与存量/终态 retention（裁定⑩，周期、终态门控 + CREATED 零步残量门控）共享同一终态常量集（`NopWfCoreConstants` 门面，见裁定⑩）与同一删除原语（`IWorkflowStore.removeWfRecord`/同型 dao 删除），禁两套清理机制对同一表各自发明常量/谓词。
7. **零回退面**：MA7.6-02 ctx 三级兜底（:91-95）、MA7.6-03 reJudgeFailClosed（:141-147）、`TestNopMetaQualityResultApprove` :46-67 approve 零副作用钉、`TestQualityAlertWorkflowProcessor` :39-45 错误码钉。

## 裁定⑩ W-02 第七面：qualityBreachApproval 告警工作流实例 retention + G2 契约注记（plan 2502 裁定④/⑥②兑现）

**决策**：

1. **清理载体 = `NopMetaLedgerCleaner` face 扩展**（否决新组件）：既有载体已持有形态 A 周期调度全套（`LifeCycleSupport.doStart` :122-168 绝缘接线、`timer.executeOn(worker)` 池设施契约、`IPeriodicTaskListener` 失败计数/断链观测、`purgeExpiredLedgerRows` :183-203 有界拾取+IN 批删语句形态、启动期 fail-fast 包络 :128-142、retention-days=0 禁用档 :144-147），生产装配在案（`app-service.beans.xml` :48 `ioc:default`）。第七通道复用该骨架，新增第七表清扫体（锚列/谓词按 §3.1 第 7 项逐表落名）。新组件即第二套调度/包络机制，违反「禁两套清理机制」精神。
2. **清理谓词（逐列落名，§3.1 第 7 项纪律）**——目标实体 `NopWfInstance`（nop-wf-dao，PK wfId）：
   - **通道 1（终态实例 TTL）**：`status ∈ {WF_STATUS_COMPLETED=40, WF_STATUS_EXPIRED=50, WF_STATUS_FAILED=60, WF_STATUS_KILLED=70}` **且** `endTime < now − retention`。锚列 `endTime` = 终态迁移时刻（`WorkflowEngineImpl` endWorkflow 写点 :1794，写入后不被业务回改——账龄事实列），终态谓词与锚列并用以排除在途行。
   - **通道 2（W-12 存量/崩溃窗残量孤儿）**：`status = WF_STATUS_CREATED=0` **且** `createTime < now − retention` **且** 零 step 记录（拾取后经 `NopWfStepInstance` 按 wfId 集存在性复核）——覆盖 W-12 修复前存量孤儿与自清理失败（进程崩溃窗）残量；CREATED 且已有 step、SUSPENDED/WAITING/ACTIVATED 等活跃状态永不进删除集（**活跃实例红线**：审批中实例不可 TTL 误删）。
   - **通道 2 显式偏差条款（M1）**：CREATED 残量通道字面越出 [ledger-retention-policy.md](ledger-retention-policy.md) §3.4 第七面行（:72）「实例终态后方可逐出」判据——定性为**该判据的修订性扩展**，论证三重： (a) CREATED 行仅在 start 入口段 `initCreateStatus`（`WorkflowEngineImpl` :193-208，落库点 saveWfRecord :200-201）内落库，落库后立即进入 getActors（:282）/saveStarted（:288）推进——全仓无「先建后启、长期停留 CREATED」的合法消费形态，CREATED 残留唯一来源即 W-12 半程失败（正是本通道清除对象）； (b) 30 天 retention 门 + 零步复核双重门使在途 start（毫秒级）误删不可达； (c) 活跃审批实例（CREATED 已含 step / ACTIVATED / SUSPENDED / WAITING）仍在谓词外——红线实质不受损。该扩展经 plan 2528 Phase 3 在 ledger-retention-policy.md §3.4 第七面行落**注记**（判据修订性扩展登记走 SSOT 一处定稿，不另设第二语义；注记形态不改裁定原文）。
   - 子表（statusHistories/steps/outputs/globalVars 等，`nop-wf.orm.xml` :277-281/:283-287/:289-293/:295-299 `cascadeDelete="true"`）随父行删除经 ORM 级联传递，不独立选锚、不独立 TTL（§3.1 第 7 项「级联以父行主键 IN 集传递」）。
3. **终态常量集落名**：常量经手写门面 `NopWfCoreConstants`（extends 生成接口 `_NopWfCoreConstants` :79-114）引用——`_` 前缀文件只读消费、零触碰；既有 `WF_STATUS_HISTORY_BOUND`（门面同文件）证明「终态集常量消费」为平台既有形态。
4. **nop-wf 零产品 diff 论证（条件申报②不触发）**：拾取查询经 `IDaoProvider.daoFor(NopWfInstance)`（nop-wf-dao 实体经 nop-wf-service→nop-wf-dao 传递依赖在 nop-metadata-service classpath，pom :40-52 亲读）；删除经公开 `IWorkflowStore.removeWfRecord`（`wf-dao.beans.xml` :12 `ioc:default` bean）。查询与删除均为 nop-wf 既有公开能力使用面，零 nop-wf 产品码改动。有界拾取 + IN 批删语句形态与六表通道同构（`purgeByCreateTime` :205-222 同型，锚列参数化为 endTime/createTime 显式传参——禁隐式选列）。
5. **参数集对齐（零参数发明）**：复用 §3.2 nop-metadata 行三键 `nop.metadata.ledger.retention-days`（缺省 30）/`.cleanup-interval`（缺省 1h，下限 60s）/`.delete-batch-size`（缺省 1000）——第七面与六表同窗（SSOT §3.2「七面同窗」注记在案），不新增 face 段。
6. **W-28 两案定夺：预设 = 仅契约注记**。W-28 本体（reJudge 原地改写 `NopMetaQualityRuleBizModel` :130-135 区，P3 登记级）不入批、零触碰 ：130-135 写点；不采「随批搭车最小修复」分支（无正确性损坏——findLatestResult 排序稳定 UK 不受影响，audit 原文；搭车修复将触碰 insert-only 语义面，与 P3 判级不成比例）。
7. **G2 契约注记文案（双落点：本节 + Phase 3 `nop-metadata.md` :347 账本保留策略节扩展）**：「`nop_meta_quality_result` 的保留窗（`nop.metadata.ledger.retention-days`，缺省 30 天）即 W-28 reJudge 可复用的历史窗上界（ledger-retention-policy §3.5 差异条款②）；若 reJudge 复用视野需超出统一保留窗，参数修订只走 SSOT 文档（上调统一键或按 §3.2 引入 face 级键），nop-metadata 模块内禁止另设第二套保留语义。」（该注记已在 `NopMetadataLedgerConfigs` :26-28 javadoc 落一处——本批兑现其余两落点。）

8. **管辖面声明同 diff 修正 + 终态集前向完备注记（m5）**：`NopMetadataLedgerConfigs` 类注释 ：22-24（「第七面……不在本载体管辖面内」）随第七通道落地**同 diff 改写**（管辖声明与实现一致——E5 义务）；注：`EXPIRED=50`/`FAILED=60` 两终态当前引擎无迁移路径（现网不可达终态），入谓词无害且前向完备（引擎后续扩展终态迁移时谓词自动覆盖）。

**零触碰边界**：`TestMetaLedgerRetention_W_02` 六表面（:77/:132/:152/:189/:206 五用例）零回退；ORM 模型零触碰（不新增 cascadeDelete/索引/不改注释——plan 2502 纪律延续）；`IWorkflowManager`/`WorkflowEngineImpl`/`WfActorAssignSupport`（nop-wf 平台面）零触碰；W-28 写点 :130-135 零触碰。

## 裁定⑪ 测试缝总表（红①–红⑩ 驱动器 + 零回退钉清单 + C10 映射）

### 红绿判据（先红后绿或变异锚定，E2）

| 钉 | 成员 | 驱动器形态 | 缺陷态红判据 |
|---|---|---|---|
| 红① | W-01（C7-102+C7-001+C4-R2-001） | 处理器连接打开缝（包内可覆写，对齐 `setGlobalLoginTimeout` :148-154 缝先例）注入记录型 Connection：断言 `setNetworkTimeout` 以缺省值（30s）被调用**且先于** `getMetaData`（时序标志）；**两条建连路径均断言**——withConnection 消费 fixture + `testConnect`（:214-255，评审 Minor-3）；辅助行为钉 = H2 mem 路径 `testConnect` 仍 connected:true（族跳过分支显式可见） | **构造性当前即红**：pre-fix `setNetworkTimeout` 零调用（main 面 grep 0 命中实录），时序标志/调用断言双红 |
| 红② | W-03（C7-R2-001） | 集成钉：`@NopTestProperty` 注入 `change-grace-period=0`（宽限关闭档）+ `nop.orm.dao-resource-check-interval` 百毫秒档；装载并执行规则 → 经 `NopRuleNodeBizModel` 一等 CRUD 改节点谓词 → 再执行同一规则，断言判定行为随新节点变化 | **构造性当前即红**：pre-fix 节点级编辑不触定义行 updateTime，行为恒旧（判别性）；定义级正常失效路径（`TestNopRuleDefinitionBizModel` :112）同套对照零回退 |
| 红③ | W-04（C1-102+C1-001+C4-001） | 结构钉 = 反射扫描**硬编码 9 载体 12 字段清单**（裁定③表逐名），断言全 volatile——**当前即红 0/12**；可见性钉 = 两线程经两个不同 checkpointId 首并发 `executeCheckpoint`，断言 dispatcher 生效 webhook 超时 == 配置值 30 且 configure 先于字段发布（记录型断言——publish-then-configure 中间态闭合；**无 identity 单实例断言**——M2：volatile 下重复构造为良性竞态，identity 断言即构造性 flake，C10 #1/#6 原文「断言单实例」半边随修摘除；同一 checkpointId 被 R4.3 :203-208 挡在 ensure 前——既有钉 :641 面不重叠） | **构造性当前即红**：结构钉确定性红（0/12 volatile）；可见性钉修复后恒绿（中间态不可达），pre-fix 存在可观察竞态窗口（间歇红——非确定性红面如实标注） |
| 红④ | W-05（C1-002，反向钉改写） | 改写 `testLoginTimeoutSetGlobally`（:776-792）为双断言： (a) 构造 + `testConnect` 全程 `DriverManager.getLoginTimeout()` == 进入前值； (b) MySQL 族属性构建缝断言 `connectTimeout=5000`。**与全局写移除同 diff 禁拆分**；先红后绿：同 diff 内先加新断言对未修复态跑红、移除写点后跑绿；随 diff 修正 ：767-773 滞后 javadoc | **构造性当前即红**：pre-fix 构造器写全局态 5（:150），断言 (a) 红；改写为判别性而非弱化的证明 = (a) 对 pre-fix 红 |
| 红⑤ | W-07（C7-003+C4-R2-002） | 记录型 `IMetaDataSourceConnectionProcessor`（计数 withConnection 调用）：同数据源 N 条规则的 checkpoint 执行断言调用数 == 分组数（1）；DataSource 构造计数缝（包内可覆写构造点，同 ：148-154 缝先例）：同配置两次获取断言构造数 == 1（缓存生效） | **构造性当前即红**：pre-fix N 规则 = N 次 withConnection（:129-190 逐规则 ：131 executeSingleRule → TableReferenceExecutor :128）；构造数 == 调用数 |
| 红⑥ | W-08（C7-006+C5-104） | 记录型 daoProvider 计数候选池查询：R 行对账断言池 `findAllByQuery` 次数 == 1；C5-104 面 = 捕获 QueryBean 断言池查询 limit 非空（mutate-fail：摘除 limit → statistics 无界拾取断言红——`TestReconciliationFetchLimitMarker` :63-65 同构形态） | **构造性当前即红**：pre-fix R 次（逐行 ：90 reconcile → :94-105 loadCandidates 全池重载） |
| 红⑦ | W-09（C5-001） | **变异锚定**（C10 原文现状不红）：多表 catalog fixture + 第 k 表读列注入 SQLException → 断言表 1..k−1 已持久化（流式逐表产出即 upsert）**且执行以 `ERR_EXTERNAL_TABLE_SCAN_FAILED` 终止**（scan 级 SQLException 保持 AR-23⑤ 抛出面——wrap 落点在 reader/迭代器内部，M3；**第 k 表不进 per-table errors**——该通道仅承载 upsert 自身失败） | **构造性当前即红**：现状全量物化（:52-92）同注入 0 表持久化 ≠ k−1 → 红；变异 = 摘除流式回退全量物化 → 复红 → 恢复（grep 确认目标行存在——plan guide #8）→ 绿 |
| 红⑧ | W-10（C5-101） | `TestLimitNegativeValueInvariant`（:67-71）同构正值分支：`queryJoinData`/`queryAggregation` 传超上限正值 → 断言抛 `ERR_PAGINATION_LIMIT_TOO_LARGE`；边界对照 = limit == 上限值绿（不误伤）；负值钉与跨库闸钉（:24-44/:178-196）零回退对照 | **构造性当前即红**：pre-fix 正值透传（:461-472 无闸），超限输入不抛 |
| 红⑨ | W-12（C7-R3-001） | fixture = 既有 `qualityBreachApproval/v1.xwf` 起始步 assignment（v1.xwf :30 `actorType="role" actorId="metadata-admin"`）在 autotest 上下文未播种该角色 → `getActors` 抛 `ERR_WF_ACTOR_NOT_EXISTS`（`WfActorAssignSupport` :119-120），时点在 `initCreateStatus` 落库（:275；saveWfRecord 形态 :200-201）之后——构造性已核证（评审 R1，m4）；执行 FAIL+ERROR 规则 → 断言 `NopWfInstance` 无该 wf 实例行（wfName=qualityBreachApproval 计数 == 0）且规则结果仍 ERROR 隔离（`ERR_QUALITY_RULE_EXEC_ISOLATED` 钉 :39-45 绿）。**Phase 2 前置验证义务（m4）**：nop-wf 引擎 bean + ORM 在 nop-metadata-service autotest 上下文装配可用；不可用时登记测试基建启用项（不静默降级红⑨） | **构造性当前即红**：pre-fix catch 吞异常（:171-179），CREATED 零 step 孤儿行持久化（`initCreateStatus` :275 已落库） |
| 红⑩ | W-02 第七面（C7-101 承接） | retention 四件套（模板 = `TestMetaLedgerRetention_W_02` :62-235 五用例形态）： (i) 清理——存量终态超期实例（status=40/50/60/70 + endTime 超窗）被清、子表随级联； (ii) 保留——活跃实例（ACTIVATED/CREATED 含 step/SUSPENDED/WAITING）与未到期终态行零误删 + CREATED 零步超期残量被通道 2 清除； (iii) fail-fast 包络——负 retention/interval<60s/batch≤0 启动期拒绝； (iv) 接线——CapturingTimer 形态断言第七通道经同一周期链真实调度（防 C7-005 空壳先例）；幂等复跑 | **构造性当前即红**：pre-fix 无任何清理通道——终态超期实例行持久留存，清理断言红（2502 承接补钉义务兑现） |

变异探针纪律：恢复路径以 grep/断言确认目标可执行行真实存在（plan guide When Executing #8）。红绿快照落 `_tmp/red-green-2528-summary.md` + 逐 trial 日志（沿 2525/2526/2527 形态）。

### 零回退钉清单

- 双聚合器基线 1421 tests 0F0E **0S**（`_tmp/baseline-2528-decomposed.log`，勘误 8 逐模块计数：nop-rule 59 + nop-metadata 1362）；0 Skipped 签名维持，执行期 Skipped/Failures/Errors 出现即登记归因，禁静默。
- 点名既有钉族零回退：`TestMetaDataSourceConnectionSecurity` 其余安全用例（AR-02 面）、`TestMetaEntityProfilerSecurity`（:395-396 stub 委托）、`TestLimitNegativeValueInvariant`（:67-71）、`TestMetaJoinTruncateOverflow`（:24-44）、`TestMetaJoinCrossDbMergeNullSemantics`（:178-196）、`TestReconciliationFetchLimitMarker`（:50/:63-65）、`TestNopMetaLineageEdgeSizeLimit`（:57-83）、`TestNopRuleDefinitionBizModel`（:112）、`TestRuleDaoStructuralContracts`（:28）、`TestMetaQualityCheckpointScheduler`（:212-239 W-11 已修面）、`TestQualityAlertWorkflowProcessor`（:39-45）、`TestNopMetaQualityResultApprove`（:46-67）、`TestNopMetaReconciliationBizModel`（:66-154）、`TestExternalTableStructureReader`（:37-57）、`TestNopMetaQualityRuleBizModel`（:210/:231）、`TestCheckpointActionDispatcherConcurrency`（:38/:85）、`TestNopMetaEntityConcurrentNullSchemaUpsert`（:89-122 AR-07）、`TestNopMetaQualityCheckpointBizModel`（:595/:627/:641/:700 R4.3）、`TestSqlParseLineageStaleEdgeCleanup`（:28-51）、`TestAggregationHelperErrorParam`、`TestMetaLedgerRetention_W_02`（六表面）。
- 跨 plan 产物面零回退：plan 2502 六表 retention（fc8ed4af2d）、plan 2500 判据面（DefaultResourceChangeChecker 单点 + TTL 兜底）、plan 2496 W-11 Throwable 宽度守卫、MA7.6-02/MA7.6-03、AR-13/AR-14/AR-17/AR-23⑤/AR-07/AR-02/R4.3 运行标记/血缘 maxEdges fail-fast。

### C10 缺保护 15 实体面同 diff 映射 + 反向钉改写专节

红①↔C7-102/C7-001/C4-R2-001、红②↔C7-R2-001、红③↔C1-102/C1-001/C4-001、红④↔C1-002、红⑤↔C7-003/C4-R2-002、红⑥↔C7-006/C5-104、红⑦↔C5-001、红⑧↔C5-101、红⑨↔C7-R3-001、红⑩↔C7-101 第七面——每条与本体同 diff 禁拆分交付（E2）。测试命名 `Test*_<工单>` 惯例（W_01/W_03/W_04/W_05/W_07/W_08/W_09/W_10/W_12/W_02_Face7）。**反向钉改写专节（W-05）**：唯一反向钉定 1 条（#2 C1-002）——改写文案、双断言形态、先红后绿次序、javadoc 修正见裁定④第 4 项；本批不适用 2527「无反向钉死」声明口径（批次行显式义务）；红① 与改写后钉面零交互（setNetworkTimeout 不触红 loginTimeout 断言——批内次序自由度）。

## 评审记录

- 设计评审 R1：独立子 agent agent_00569df7（fresh session，2026-11-02）——**无 Blocker，4 Major + 5 Minor 全部修订闭环**：Major-1 裁定⑩通道 2 补显式偏差条款（ledger-retention-policy §3.4 :72 判据的修订性扩展 + Phase 3 SSOT 注记义务）；Major-2 红③ 摘除 identity 单实例断言（与良性竞态声明自洽，C10 #1/#6 原文同形随修）；Major-3 红⑦ 定死 AR-23⑤ 抛出面（1..k−1 已持久化 + `ERR_EXTERNAL_TABLE_SCAN_FAILED` 终止，wrap 落点 reader/迭代器内部）；Major-4 裁定⑤ 缓存键钉死 SHA-256 不可逆摘要 + 凭证/键禁入日志 + 超时配置入键（无陈旧窗）+ maxSize=64/TTL=5min 固定常量；Minor-1 驱动计数「三个→两个」+ getMetaData 握手缓存注记；Minor-2 30s 缺省 workload 口径 + owner doc 落账义务；Minor-3 旁路错误包装复刻 `ERR_TABLEREF_EXEC_FAILED` + 解析失败 per-rule 隔离 + 分组键 dataSourceId；Minor-4 红⑨ fixture 构造性核证（v1.xwf :30 metadata-admin 未播种）+ Phase 2 前置验证义务；Minor-5 `NopMetadataLedgerConfigs` :22-24 管辖声明同 diff 修正 + EXPIRED/FAILED 前向完备注记。**结论：设计可进入 Phase 2 实现。**
