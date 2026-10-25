# 2502 CRA Phase 8.8 — R1-07 F4 账本保留统一批（R01 族行：统一保留期/TTL/上限设计一处定稿，6 项）

> Plan Status: active
> Draft Review: R1 agent_a834f6e3（0 Blocker、4 Major[M1 trusted-device 第七面授权边界/M2 第七面机制与测试义务低于六面/M3 Phase 2 接线措辞与裁定菜单不一致/M4 缺 doc-links 门禁]+7 Minor——修订 agent_585e2639 全量应用；M1 采保守裁决：授权边界维持六族、trusted-device 移出本批 Deferred 登记 successor=R3-01[扩面询问未获回复，不静默扩面]，active）
> Last Reviewed: 2026-10-25
> Source: roadmap 工作项 8.8（R1-07，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L150，Status=todo）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.1 R1-07 行（L68：finding ID 清单 6 项 / PA=nop-auth ask-first / 依赖=无前置（可并行）+「保留策略设计=族级 SSOT（上限+逐出+fail-fast 包络声明，4.5 口径）」/ exit criteria 增补四条）；**族级纪律 SSOT = family-clustering.md R01 行（L45：保留期/TTL/行数上限+成功即清理；族级纪律=上限+逐出+fail-fast 包络声明[4.5 口径]；5.1 MA-01/1.6 C7-001/1.7 C7-003/5.5 W-02/5.6 W-01 统一批次[ledger §二#3]；账本表批次对齐 1.6 C7-001↔1.7 C7-003）+ 4.5 口径出处 `ai-dev/audits/2026-10/2026-10-21-1444-deep-audit-nop-search-runner-autotest-concurrency/merge-adjudication.md:125`（F4 无界账本家族族级修复纪律）**；5.2-MA-04 留存语义条款 = family-clustering §三.5#5（L564：维持 P2 入 R01 族行，留存语义按族级纪律统一设计[保留期/TTL/上限+owner doc 补 retention 声明后落地]）；成员发现详情 = 六个审计目录（见 Current Baseline 逐条回读路径）；执行基线 @ `6aeb7d43e9`（分支 `concurrency-audit-remediation`，工作树 clean；roadmap 8.1–8.7 已 done，plan 2495–2501 均 completed；本 Baseline 全部行号为该基线 live 回读值，执行前如再漂移以 live 为准并登记）
> Related: plan 2498（8.4 R1-03 平台池消费方批，completed——多模块批范式与 E1–E6 门禁先例）、plan 2499（8.5 R1-04，completed）/ plan 2500（8.6 R1-05，completed）/ plan 2501（8.7 R1-06，completed）——R1 轮前序批；**下游设计对齐批（本批 Phase 1 设计文档为其族级 SSOT，参数修订只走该文档一处定稿）**：R2-07（1.1-C1-006/C8-003 保留纪律对齐 R1-07，batch-proposal L81）、R2-08（1.2-MA-05 保留策略与 R1-07 同设计；MA-16 对齐，L82/L118）、R2-12（1.6-C7-005 tcc 两表清理接线——平台方案在 R1-07，L86）、R3-05（G2 retention×reJudge 同表契约=**R1-07（W-02）+ R3-05 注记（W-28=P3 条款）**，L318）、R4-03（MA-13 与 MA-05 保留策略配套，L118）、R4-07（1.4-C7-004=R01 三表保留族，对齐 R1-07 设计，L122）、R4-10（硬依赖 8.8[roadmap L192]）、R4-29（4.5-MA-01 R01 族成员，L144——非本批成员，设计对齐）
> Protected Area 与授权：**nop-auth ask-first——已获用户授权（2026-10-25 AskUserQuestion 用户询问记录：授权修复——Session/Token 六族 DB 清理配对纳入本批，统一保留期/TTL/上限设计参数化 + focused 回归测试）**。Dependencies 声明（E4）：①AGENTS.md「Protected Areas」表「权限/认证模型（nop-auth）| ask-first」行——本批成员 3（5.1-MA-01）触碰 nop-auth-service 认证域数据清理面，授权范围以本条记录为准；授权边界=六族清理配对面，**不含** MA-04 可信设备并发纪律锁本体（→R3-01），**亦不含 trusted-device 过期行清扫第七面（R1 修订摘除：2026-10-25 扩面询问[维持六族 vs 扩至第七面]未获用户回复，保守采 R1 建议②维持六族授权边界不外推；第七面清扫面归 Deferred But Adjudicated 已登记项，Classification=`moved to explicit successor ownership`，successor=R3-01/nop-auth 域后续确认批次，回询补授权后方可实施——不静默扩面为本 plan 自设红线；5.1 merge-adjudication L38 的「过期清扫搭车 MA-01」搭车面本批不承接）**；②ORM plan-first 面**零触碰**（零触碰声明见下）；③nop-graph-api plan-first 叶零触碰（W-06 归 R3-06，不在本批）。零触碰声明（E4）：产品 diff 不含 nop-api-core（io.nop.api.core.*）、`model/*.orm.xml`（nop-retry/nop-batch/nop-metadata/nop-sys 等 ORM 模型只读引用——六成员修复均为纯清理/生命周期配对，5.1 裁定明文「orm 不变（纯清理）」；retention 走代码+配置与既有实体 deleteByQuery，不声明新级联/新索引；执行中若暴露必须改 ORM 的面，停下按 plan-first 立项，不扩批）、`_` 前缀生成物。

## Dependencies

- 批次依赖：**无前置（可并行）**（batch-proposal R1-07 行 Deps 列原文）；R1 轮前序批 8.1–8.7 已 done 不构成本批排序前置。
- PA 授权依赖：nop-auth ask-first——授权来源与用户询问记录见头部「Protected Area 与授权」（2026-10-25）。
- 设计对齐依赖：本批 Phase 1 产出的族级保留策略设计文档为 R01 全族 SSOT；本批六成员落地不得偏离该文档逐单传导表。

## Purpose

关闭 R01 F4 无界账本/零保留族的 6 项成员发现（batch-proposal §二.1 R1-07 行：P1×5——1.6-C7-001、1.7-C7-003、5.1-MA-01、5.5-W-02、5.6-W-01；P2×1 提前搭车——5.2-MA-04[P2-G75，裁定「修→提前（R01 族行）」，batch-proposal L230/G75]）：①**统一保留期/TTL/行数上限设计一处定稿**——族级保留策略设计文档（4.5 口径：上限+逐出+fail-fast 包络声明）+ 六成员逐单传导表 + 参数化配置键，保留策略参数不六模块各自发明；②五（六）模块落地：nop-retry 三账本表清理入口、nop-batch 三表清理、nop-auth 六族清理配对（ask-first 已授权）、nop-metadata 七面 retention、nop-duckdb 双载体账本生命周期配对+过期指纹误拒闭合、nop-sys sys_lock/sys_change_log retention+留存语义条款；③1.6-C7-001↔1.7-C7-003 账本表批次对齐（同设计同参）；④每工单 focused 回归测试（5.1 携 C10-001/004/005 回归钉同 diff）。批末 roadmap 8.8 → done，cra-index 六行收敛。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.1 R1-07 = 6 项成员（P1×5 + P2 提前搭车×1）；cra-index P1 台账 5 行（L177 `C7-001（←C6-004）| 1.6 | nop-retry-nop-tcc | … | R1-07 | open`、L189 `C7-003 | 1.7 | nop-batch | … | R1-07 | open`、L285 `nop-auth-credential-MA-01（六族清理配对族主）| … | R1-07 | open`、L302 `nop-rule-metadata-W-02（retention 族主 C7-004+C7-101）| … | R1-07 | open`、L304 `nop-graph-duckdb-W-01（账本泄漏族主 C6-001 吸 3 载体）| … | R1-07 | open`，裁定批次均=R1-07、状态均=open）+ P2 裁决台账 G75 行（cra-index L449：`P2-G75（5.2-MA-04 零保留账本，成员 1 条：5.2-MA-04）… 修→提前（R01 F4 族行）… 批次随 R01→实际批号 R1-07`）；roadmap 8.8 行 Status=todo（L150）。
- **族级纪律 SSOT 在位**：family-clustering R01 行（L45）明文族级纪律=「上限+逐出+fail-fast 包络声明（4.5 口径）」+「保留期/TTL/行数上限+成功即清理」；R01 族全量 19 成员（P1×11/P2×8，12 单元）中本批承接 6 项，其余 13 项归各自批次并以本批 Phase 1 设计文档为对齐 SSOT（对齐批清单见头部 Related）。跨批契约三处（batch-proposal §四 重复消解表）：「1.7 C7-003 与 1.6 C7-001 账本保留族批次对齐 | 同批 R1-07」（L314）、「5.5 G2 retention×reJudge 同表契约 | R1-07（W-02）+ R3-05 注记（W-28=P3 条款）」（L318——本批 W-02 的 retention 设计须与 R3-05 的 W-28 reJudge 同表面语义协调，设计文档显式登记该契约，W-28 本体不进本批）、「5.2-MA-04 维持 P2 入 R01 族行 | R1-07」（L330）。
- **平台清理先例集在位**（Phase 1 设计文档的对照先例，live @ `6aeb7d43e9` 核实）：tcc `TccEngine.cleanCompletedTransactions(long retentionTime)`（`nop-tcc/nop-tcc-core/src/main/java/io/nop/tcc/core/impl/TccEngine.java:351`，API 面 `ITccEngine`——零生产装配属 C7-005→R2-12，本批仅作形态先例引用）；nop-sys `SysDaoMessageService.cleanupExpiredEvents`（`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/message/SysDaoMessageService.java:253-282`，周期接线 `:228` executeOn）；nop-credential `NopCredentialOauthStateStore.cleanupExpired()`（`nop-credential/nop-credential-service/src/main/java/io/nop/credential/service/oauth/NopCredentialOauthStateStore.java:67` create 前调用、`:112` 方法定义——六族唯一有批量清扫形态的对照组）；nop-sys `SysDaoNamingService.cleanup`（:89-101）；nop-metadata `NopMetaLineageEdgeQueryAction` deleteByQuery 单语句删除先例（审计载 :486-488）。
- 前序批次完成：roadmap 8.1–8.7 已 done（plan 2495–2501 completed @ `6aeb7d43e9`）；本批与 8.9 起各批无排序依赖。

### 6 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `6aeb7d43e9` 回读值，执行前如再漂移以 live 为准并登记；漂移成因见勘误 3）

| # | Finding ID（判级） | 单元 / 模块 | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向（Phase 1 设计文档逐单传导表口径） |
|---|---|---|---|---|---|
| 1 | 1.6-C7-001（P1，←C6-004；cra-index L177） | 1.6 / nop-retry（nop-retry-engine 本体 + nop-retry-service BizModel 面） | `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java`（全文件仅 :248/:309 两处 `deleteEntityDirectly(record)`——OVERWRITE 重建/死信迁移，均非保留期清理；attempt/dead_letter 零删除）；`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java:499-507`（`handleExecutionSuccess` 成功置 COMPLETED 不删除；:171 `findRecordByIdempotentId` 每次提交必经查询随表深退化）；BizModel `nop-retry/nop-retry-service/src/main/java/io/nop/retry/service/entity/NopRetryRecordBizModel.java:11`（纯 `CrudBizModel` 无 purge action；DeadLetter BizModel 同构）；表锚 `nop-retry/model/nop-retry.orm.xml:182/:287/:381`（nop_retry_record / nop_retry_attempt / nop_retry_dead_letter 三账本表——只读引用） | retry 三张账本表零保留机制：每成功任务沉淀 ≥2 行（record COMPLETED + attempt）、失败周期额外 1 死信 + N attempt；tcc 侧尚有清理 API（未接线），retry 连 API 都不存在；死信化删除 record 后 attempt 孤儿化（无 FK/cascade）随死信化单调累积 | 仿 tcc `cleanCompletedTransactions(retentionTime)` 先例提供 COMPLETED/attempt/死信的保留期清理入口（attempt 孤儿随死信保留期一并清理）+ retention 参数化配置键（键名/缺省值按 Phase 1 设计文档逐单表）+ owner doc 声明默认保留期；清理入口形态（BizModel purge action / 周期 job / 提交路径顺带清扫）以设计文档定稿为准，不模块内发明 |
| 2 | 1.7-C7-003（P1；cra-index L189） | 1.7 / nop-batch（nop-batch-dao） | `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java:83-104`（`tryInsertNewTask` saveState=true 每执行插一行）、`:276-291`（`newTask`——taskKeyExpr 缺省 UUID 每触发新行）；`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/history/DaoBatchRecordHistoryStore.java:78-111`（`saveProcessed` 每成功记录插一行，无删除/TTL；审计载 :77-111，live `saveProcessed` 签名 :78）；表锚 `nop-batch/model/nop-batch.orm.xml:42/:145/:192/:243`（nop_batch_task / nop_batch_task_var / nop_batch_record_result / nop_batch_file——前三表主体[record_result 复合主键 (BATCH_TASK_ID, RECORD_KEY) 已在模型层，C7-round2 修正采信]、file 附带登记面） | 三张账本表零保留：清理代码完全不存在（第 2 轮二态判定「连死代码都没有」成立），UUID taskKey 每触发 +1 行单调增长（周期任务×记录量年化十亿行级） | retention 清理入口（按完成时间/终态时间 TTL 批删）+ 终态任务行（COMPLETED/FAILED/KILLED 超期）清理 job 或运维 action（形态按设计文档逐单表定稿）+ owner doc 声明保留策略；**与成员 1 同设计同参（批次对齐声明 L314）**；审计建议「saveProcessed upsert 评估」不属本批（其真实动机=C7-015 键语义场景，归 C7-015 所属批次——勘误 6） |
| 3 | 5.1-MA-01（P1，六族清理配对族主，吸 C6-002/C6-003；cra-index L285） | 5.1 / nop-auth（nop-auth-service 六族）+ nop-credential（对照组先例面） | 主证 `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java:164`（`removeUserContextAsync`「不需要删除」——审计载 :162-166）、`:99-103`（`checkExpired` 过期只置 LOGOUT_TYPE_EXPIRE——审计载 :100-103）、`:139-141`（session 行持久化 accessToken/refreshToken/cacheData 令牌载荷）；`login/DaoLoginSessionStore.java:70`（`saveEntity` INSERT）、`:75-84`（`logoutSession` 仅置 logoutType/Time/By）；五族同键惰性删除：`mfa/store/DbMfaChallengeStore.java:96`（惰性 TTL 清理）、`mfa/store/DbSmsCodeStore.java:115,:130`、`mfa/store/DbEmailCodeStore.java:108,:123`、`ratelimit/DbSendCodeRateLimiter.java:123`（`deleteExpired(key, now)` 同键限定）、`login/DbLoginAttemptStore.java:70,:102`；trusted-device 搭车面 `mfa/MfaTrustedDeviceManager.java:199,:215,:230,:253,:260`（fixedWindowEnd/removeBySid×2/listForUser/countUnexpired——仅人工删除，C6-003）；对照组先例 `nop-credential/.../oauth/NopCredentialOauthStateStore.java:67,:112` | 六族 DB 状态存储零/弱清理配对：NopAuthSession 行（含令牌载荷）无限期驻留（连惰性删除都没有）、五族仅同键访问时惰性删除（键不复访即永久驻留，RateLimitCounter/LoginAttempt 键空间攻击者可低成本扩充）、trusted-device 仅人工 removeBySid | 复用 `NopCredentialOauthStateStore.cleanupExpired()` 语句形态统一批量清扫（`delete where expireAt < now - retention`）+ 清理载体（job 低频接入或 create/管理路径顺带——按设计文档逐单表）+ `NopAuthConfigs` 保留期配置参数化；六族一处实现、统一接入、处置一致不静默分叉；trusted-device 过期清扫**本批摘除（R1 修订：授权边界=六族，第七面清扫面归 Deferred→R3-01——勘误 5；MA-04 本体并发纪律锁同归 R3-01）**；orm 不变（纯清理）；C10-001/004/005 回归钉同 diff（5.1 C10-test-protection L64/L67/L68：session 清理行删除+令牌载荷清除+未过期保留[可注入时钟]、五族批量清扫参数化、countUnexpired 口径不变） |
| 4 | 5.5-W-02（P1，retention 族主 C7-004+C7-101；cra-index L302） | 5.5 / nop-metadata（站点在 nop-metadata-service；单元=nop-rule-metadata——勘误 1/2） | 主锚 `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityResultWriter.java:26,:67`（insert-only append，审计载 :16-19/:57-68）；`service/event/MetaModelChangedEventPublisher.java:108-112`（publishEvent 24 处发布点入口，审计载 :123-141——live 方法签名段前移，勘误 3）；catalog 写点 `entity/NopMetaDataSourceBizModel.java:881-882`（appendCatalogRow，审计载 :878-891）；ORM 语义注释 `nop-metadata/model/nop-metadata.orm.xml:2095-2100`（「无 cascadeDelete=历史事实语义」——**只读引用，零触碰**）；七面全清单（C7-004 F3 枚举）：①nop_meta_quality_result ②nop_meta_quality_score ③nop_meta_catalog ④nop_meta_profiling_result ⑤nop_meta_reconciliation_result ⑥nop_meta_model_changed_event（mediumtext 前后双快照，无消费者）⑦衍生 qualityBreachApproval 工作流实例（C7-101 三写点并入） | 七面 append-only 时序表零保留路径：cron+auto-score 机器速率写入、人工清理人速进行——速率差即无界增长（event 表 mediumtext 双快照最重；「无 retention」未被任何声明覆盖） | retention 机制按设计文档逐单表落地（审计三选一或组合：nop-job 注册定期清理 job[复用 `NopMetaLineageEdgeQueryAction` deleteByQuery 先例形态] / BizModel `purgeBefore(timestamp)` 运维 action / event 表最长保留窗口）+ owner doc 声明保留策略默认值；ORM 模型零触碰（retention 走既有实体 deleteByQuery）；G2 retention×reJudge 同表契约在 Phase 1 设计文档显式登记（W-28 本体归 R3-05） |
| 5 | 5.6-W-01（P1，账本泄漏族主 C6-001，吸 C6-R2-001/CS-002/C1-R2-001 四方同证；cra-index L304） | 5.6 / nop-duckdb | `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java:48`（`OPEN_FILES` 静态注册表）、`:82-92`（过期指纹检查段——审计载 L83 误拒点）、`:98`（`registerOpen` 唯一注册点）、`:116`（`releaseOpen` 唯一调用点，engine.close() 内）、`:213-216`（`track`）、`:218-226`（registerOpen 方法）、`:228-238`（releaseOpen 方法）；成因侧 `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbSqlTaskStep.java:85`（TWRS 直接关连接旁路——refs 不释放）；契约 `nop-duckdb/src/main/java/io/nop/duckdb/IDuckDbEngine.java:16`（「close() releases every connection」——W-02 竞态族引用，本批零改动）；装配 `nop-duckdb/src/main/resources/_vfs/nop/duckdb/beans/app-duckdb.beans.xml:13-15`（单例 bean，仅容器停机 close） | OPEN_FILES+connections 双载体账本仅 engine.close() 释放：单例长驻期每任务净增永不回收（refs 滞留止于 engine 存活期=单例 bean 下应用存活期）+ 过期指纹误拒同路径异配置重开（registry 自述场景 100% 假阳性 bizFatal，无自愈路径） | 连接 close 桥接 releaseOpen/untracked 单点修复收口双载体（审计建议 (a) 优先：openFile/openMemory 返回 close 时回调的 Connection 包装，包装 close 内锁内移除 TrackedConnection + filePath != null 时 releaseOpen；`track` 记录原对象保 engine.close 幂等兜底不变）——C6-001 与 C6-R2-001 两载体一处修改同时闭合；**过期指纹误拒面同批闭合**（exit criteria 增补条）：step 消费链回归后 refs 归零，同路径跨 engine 复用不再 100% 假阳性；双载体同测 + 跨 engine 复用回归 + 既有 close 语义零回退（W-02/W-03/W-04 归 R3-06 不触碰）；与 Phase 1 设计文档形态差异条款（勘误 7） |
| 6 | 5.2-MA-04（P2 提前搭车，族主 C6-001 吸 C6-003；cra-index L449 P2-G75 行） | 5.2 / nop-sys（nop-sys-dao） | `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java:107-111`（forceUnlock）、`:146-157`（过期争用删除——争用触发式惰性清理，审计载 :146-154）、`:260-273`（releaseLock；saveNew 方法定义审计载 :204-224，live 调用点 :123/:136/:164）；`log/OrmEntityChangeLogInterceptor.java:22-48`（postSave 逐列 INSERT——逐列循环段 :34-48）、`:52-77`（postUpdate 逐脏列，审计载 :64-77）、`:95`（postDelete 单行）、`:108-111`（saveDirectly 每列一次 runInNewSession——写放大面）；同模块清理先例 `message/SysDaoMessageService.java:228,:253-282`（cleanupExpiredEvents） | sys_lock 行（崩溃持有者+无人争用资源）与 sys_change_log 审计行零保留（全模块零删除点）+ ChangeLog 每实体每保存逐列写放大（F4+F7 同族）；同模块 event/broadcast/serviceInstance/naming 均有清理而锁行与审计行没有（F3 资源面漏项） | 照抄 `cleanupExpiredEvents` 形态：retention 配置 + 周期清理（`delete where expireAt < now - grace`）+ ChangeLog saveDirectly 批量化（单会话批量插入，随批）；**留存语义按族级纪律落地**（exit criteria 增补条；family-clustering §三.5#5）：NopSysChangeLog 审计留痕「有意长期保留」的可能性未排除——retention 默认值与审计合规要求对照后定清理策略，owner doc 补 retention 声明后落地（声明落点是本工单验收面的一部分，非豁免通道） |

### Baseline 勘误（输入不一致登记）

1. **模块列与实际 diff 面偏差**：roadmap 8.8 / batch-proposal R1-07 模块列 = nop-retry；nop-tcc；nop-batch；nop-credential；nop-rule；nop-duckdb；nop-sys。实测成员缺陷面实际触达 **nop-auth**（MA-01 六族主体全在 nop-auth-service——PA=nop-auth ask-first 的触碰对象本身不在模块列）与 **nop-metadata**（W-02 七面站点全在 nop-metadata-service，5.5 审计目录即 nop-rule+metadata 双模块单元；同提案 R3-05 行验证命令即并列 `nop-rule`+`nop-metadata` 两通道可证）。本 plan 验证通道按实际 diff 触达模块列全（`./mvnw test -pl nop-auth -am`、`./mvnw test -pl nop-metadata -am` 为成员 3/4 必跑通道），不默认扩批；nop-rule 本批预期零产品 diff（5.5 单元名义模块），`./mvnw test -pl nop-rule -am` 若零 diff 以实际为准登记。
2. **owner doc 锚点缺口**：roadmap 8.8 Owner Doc 列六份（nop-retry/nop-batch/nop-credential/nop-rule/nop-duckdb/nop-sys.md）无 nop-auth.md 与 nop-metadata.md，而成员 3 主体与成员 4 全部站点分别在这两模块（两份 owner doc 均已存在：`docs-for-ai/03-modules/nop-auth.md`、`docs-for-ai/03-modules/nop-metadata.md`）。本批 owner doc 更新面按勘误 1 扩为**六份既定锚点 + 两份实际触达锚点核对**（nop-auth.md 更新 MA-01 清理契约；nop-metadata.md 更新 W-02 retention 契约；nop-rule.md 若零 diff 则核对后登记 `No owner-doc update required` + 理由）。缺口登记随 Phase 8 落账。
3. **行号漂移登记（成因=审计基线后无相关提交，漂移均为±数行级）**：RetryRecordStoreImpl 审计载 :247-249/:285-310/:309 → live :248/:309（moveToDeadLetter 声明 :287）；RetryEngineImpl :499-507 零漂移；DaoBatchRecordHistoryStore saveProcessed 审计载 :77-111 → live :78-111；DaoBatchStateStore :83-104/:276-291 零漂移；DaoUserContextCache removeUserContextAsync 审计载 :162-166 → live :164、checkExpired 标记段审计载 :100-103 → live :99-103、:139-141 令牌载荷零漂移；QualityResultWriter 审计载 :16-19/:57-68 → live 类声明 :26/append 体 saveEntity :67（窗口内）；MetaModelChangedEventPublisher 审计载 :123-141 → live publishEvent 入口 :108-112（执行期精读后以 live 为准）；NopMetaDataSourceBizModel appendCatalogRow 审计载 :878-891 → live :881-882；DuckDbEngine :48/:98/:116/:213-238 零漂移、指纹检查段审计载 L83 → live :82-92；DuckDbSqlTaskStep :85 零漂移；SysDaoResourceLockManager forceUnlock :107-111/releaseLock :260-273 零漂移、过期争用删除审计载 :146-154 → live :146-157；OrmEntityChangeLogInterceptor postSave 审计载 :34-48 → live 方法声明 :22/逐列循环 :34-48、saveDirectly :108-111 零漂移；MfaTrustedDeviceManager 审计载 :198-202/:214-238/:252-265 → live :199/:215/:230/:253/:260。本 Baseline 已按 live 回读值书写；执行时再以 live 为准。
4. **nop-tcc 零成员站点边界**：R1-07 finding 清单无一条站点在 nop-tcc（1.6 单元审计覆盖 nop-retry+nop-tcc，但成员 1 本体=retry 三表；tcc 两表 nop_tcc_record/nop_tcc_branch_record 零保留=**C7-005 → R2-12**[batch-proposal L86，「C7-001/C8-001 平台方案在 R1-07/R1-01」]；tcc `cleanCompletedTransactions` 仅作形态先例引用且其零生产装配缺陷属 C7-005）。本批 **nop-tcc 预期零产品 diff**，验证通道 `./mvnw test -pl nop-tcc -am` 若零 diff 以实际为准登记；Phase 1 设计文档为 R2-12 落地时的对齐 SSOT，防两批各发明一套保留参数。
5. **5.1-MA-04 搭车边界（R1 修订：第七面摘除）**：MA-04（可信设备并发纪律族）本体=check-then-write 无串行化点，归 **R3-01**（batch-proposal L101，Dep 列「5.1-MA-01 在 R1-07」）；其合并裁定行明文「过期清扫搭车 MA-01」（5.1 merge-adjudication L38）——R1 修订裁决（2026-10-25 扩面询问未获回复，保守维持六族授权边界）：**trusted-device 过期行清扫面本批摘除**（MfaTrustedDeviceManager 过期行批量清理原拟搭车——现归 Deferred But Adjudicated 已登记项，Classification=`moved to explicit successor ownership`，successor=R3-01/nop-auth 域后续确认批次，回询补授权后方可实施）；C10-005（countUnexpired 口径不变）保留为只读零改动面回归钉（测试③），trusted-device 产品面本批零 diff。边界注记随 Phase 8 落 cra-index 5.1-MA-01 行与 R3-01 行对照。
6. **1.7-C7-003 审计建议 (3)「saveProcessed upsert 评估」不属本批**：C7-round2 修正已裁定其真实动机=「重复记录场景的任务可用性（见 C7-015）」且复合主键已在模型层（重复插入必然响亮失败，非静默累积）——本批只承接零保留增长面（retention 清理+声明），upsert/键语义场景归 C7-015 所属批次，不扩批。
7. **5.6-W-01 与族级保留参数的形态差异**：W-01 为进程内注册表/连接账本的生命周期配对破损（close 权与 release 权无桥接），非 DB 表周期性增长——「保留期/TTL/行数上限」参数对其不直接适用，其落地形态=连接 close 桥接（生命周期配对，泄漏载体消除后无「保留」需求）。Phase 1 设计文档须显式写明该差异条款（族级纪律「上限+逐出+fail-fast 包络声明」对 W-01 的投影=修复后双载体账本随连接生命周期闭合、engine.close 幂等兜底保留），防执行期把 DB 表清理形态错误套用到 nop-duckdb。exit criteria 增补条「过期指纹误拒面同批闭合」为 W-01 的独立验收面。
8. **5.5-W-02 的 G2 跨批契约**：batch-proposal L318「5.5 G2 retention×reJudge 同表契约 | R1-07（W-02）+ R3-05 注记（W-28=P3 条款）」——W-02 的 retention 设计若覆盖 reJudge 复用面（quality result 同表语义），须在 Phase 1 设计文档登记与 R3-05 W-28 的契约注记；W-28 本体不进本批，防 R3-05 到达时与已落地 retention 语义冲突。

## Goals

- **族级保留策略设计文档一处定稿**（Phase 1，`ai-dev/design/concurrency/ledger-retention-policy.md`，对标 `pool-facility-hardening.md` §3.1.3 多模块传导先例）：统一保留期/TTL/行数上限参数集+平台配置键命名规则+缺省值+清理触发形态（周期 job / 路径顺带清扫 / BizModel purge action / close 桥接）+fail-fast 包络声明（4.5 口径「上限+逐出+fail-fast」）+六成员逐单传导表+两条差异条款（勘误 7/8）——**保留策略参数不六模块各自发明**，后续批修订只走该文档。
- 6 项成员全部修复并各带 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单：`_C7_001`/`_C7_003`/`_MA_01`/`_W_02`/`_W_01`/`_MA_04`）；可复现项先红后绿或变异/回退论证（E2 允许支路）；成员 3 携 C10-001/004/005 回归钉同 diff。
- 1.6-C7-001↔1.7-C7-003 账本表批次对齐（L314 契约）：nop-retry 与 nop-batch 的清理参数/触发形态/owner doc 声明同源同批。
- 5.2-MA-04 留存语义按族级纪律落地：sys_change_log 的 retention 默认值与审计合规对照结论 + owner doc retention 声明，作为工单验收面。
- 5.6-W-01 过期指纹误拒面同批闭合：refs 随连接关闭归零后，同路径跨 engine 重开不再假阳性 bizFatal（跨 engine 复用回归钉）。
- Owner doc 随批更新：六份既定锚点（nop-retry/nop-batch/nop-credential/nop-rule/nop-duckdb/nop-sys.md）+ 两份实际触达锚点（nop-auth.md/nop-metadata.md，勘误 2）核对与更新。
- E1–E6 通用门禁全过（batch-proposal §一）：全量构建绿 / 每工单强制回归测试（先红后绿或变异/回退论证）/ 验证通道留证 / PA 授权声明与零触碰声明 / owner doc 清单 / 批级独立 closure audit。

## Non-Goals

- **不承接 R01 族其他 13 项成员**：1.1-C1-006/C8-003（→R2-07）、1.2-MA-05（→R2-08）、1.2-MA-16（→R4-03）、1.4-MA-01（→R2-10）、1.4-C7-004（→R4-07）、1.5-MA-08（→R2-11）、3.1-MA-03、3.2-MA-07、4.1-MA-03、4.3-C6-011、4.5-MA-01（→R4-29）、6.5-W-06——Phase 1 设计文档为其对齐 SSOT，本批不改其代码。
- **不承接 tcc 两表清理接线**（1.6-C7-005 → R2-12；本批不改 nop-tcc 代码，勘误 4）。
- **不承接 5.1-MA-04 本体与 trusted-device 过期清扫第七面**（并发纪律锁/豁免判定时点语义及过期行清扫面均 → R3-01；R1 修订后本批对 trusted-device 零承接——授权边界=六族，勘误 5）。
- **不承接 5.5 其余工单**：W-01 外部 JDBC 无超时族（→R3-05）、W-03 热更新失效族（→R3-05）、C7-101 三写点仅随 W-02 retention 一并覆盖（其独立登记面不扩批）；W-28 reJudge 本体（→R3-05，G2 契约注记归设计文档，勘误 8）。
- **不承接 5.6 其余工单**：W-02 竞态族/W-03 CTAS 必败/W-04 不可中断族（→R3-06）；IDuckDbEngine 契约文本与 close 语义零改动（W-01 修复不得触碰 :16 契约面——该契约是 W-02 的修复面之一）。
- **不承接 1.6 单元其他成员**：C7-003 僵尸记录/C7-005/C7-012/C8-001（→R2-12/R2-13 等各自批次）。
- **不承接 1.7-C7-015 upsert/键语义场景**（勘误 6）。
- **不做 ORM 模型变更**（零触碰声明见头部）：不新增 cascadeDelete、不新增索引声明、不改 nop-metadata.orm.xml 的「历史事实语义」注释——retention 全部走代码+配置+既有实体删除语句。
- 不触碰 nop-api-core（io.nop.api.core.*）、nop-graph-api、`_` 前缀生成物。

## Scope

### In Scope

- `ai-dev/design/concurrency/ledger-retention-policy.md`（Phase 1 新建，族级 SSOT 设计文档）。
- `nop-retry`（nop-retry-engine 清理入口 + nop-retry-service BizModel 面 + retention 配置键 + nop-retry-dao 回归面）。
- `nop-batch`（nop-batch-dao 清理入口/job 或 action + retention 配置键）。
- `nop-auth`（nop-auth-service 六族批量清扫 + `NopAuthConfigs` retention 配置；**PA ask-first 已授权——授权边界=六族**，trusted-device 过期清扫第七面 R1 修订摘除归 Deferred→R3-01）+ `nop-credential`（对照组先例复用；预期零产品 diff 或仅先例形态对齐，以实际 diff 为准登记）。
- `nop-metadata`（nop-metadata-service 七面 retention 清理）。
- `nop-duckdb`（DuckDbEngine close 桥接包装 + DuckDbSqlTaskStep 消费链回归面）。
- `nop-sys`（nop-sys-dao sys_lock/sys_change_log 周期清理 + ChangeLog saveDirectly 批量化 + retention 配置）。
- Owner doc：八份核对与更新（六份既定锚点 + nop-auth.md/nop-metadata.md，勘误 2）；cra-index 台账回填（6 成员行 + 勘误 4/5 边界注记 + G2 契约注记）；roadmap 8.8 → done。

### Out Of Scope

- Non-Goals 所列全部项；R2-07/R2-08/R2-12/R3-05/R3-06/R4-03/R4-07/R4-10/R4-29 等后续批工单；ORM 模型变更；R01 族其余 13 项成员。

## Execution Plan

> 族级设计一处定稿（Phase 1）→ 六模块组分片实现（Phase 2–7，每工单回归测试+可复现项先红后绿或变异/回退论证）→ 全量验证 + owner doc 八份 + 台账回填（Phase 8）→ closure audit + 收口（Phase 9）。Phase 2–7 分片间无硬依赖（均消费 Phase 1 设计文档逐单表），可按序执行；每分片独立提交（提交纪律：每模块一提交，commit message 声明工单 ID 与 plan 号）。分片 C（nop-auth）开始前须确认头部 ask-first 授权记录仍然有效（授权日期 2026-10-25），且触碰面限于六族边界（trusted-device 第七面清扫已 R1 修订摘除）。

### Phase 1 - 族级保留策略设计文档（一处定稿）

Status: completed（2026-10-25，提交 `5dd46531ed`；配额中断接续收尾回填）
Targets: `ai-dev/design/concurrency/ledger-retention-policy.md`（新建）

- Item Types: `Decision`（族级纪律 SSOT 定稿）

- [x] 统一参数集一处定稿：保留期（retention period）、清理触发周期、单批删除上限（批删 cap）、行数上限（row cap，适用成员）、超限/失败包络行为（fail-fast 声明——清理失败显式可见非静默吞掉，rule 24 同源）、**清理谓词时间锚列（第七项）**（每表账龄列显式指定：如 nop_batch_task startTime/updateTime 之一；retry attempt 孤儿清理 join 键——逐单表逐列落名，清理谓词不得隐式选列）；形态对齐 family-clustering R01 行与 4.5 口径原文（「上限+逐出+fail-fast 包络声明」）——§3.1 七项表落定（retention-days 缺省 30、0=显式禁用；cleanup-interval 1h、下限 60s；delete-batch-size 1000；DB 账本表不设 row cap[TTL 唯一逐出判据]；fail-fast 两层投影[错配置装配期拒绝+执行失败 ERROR+计数]；键命名 `nop.<module>.ledger[.<face>].*`；锚列逐表显式+选取规则）
- [x] 平台配置键命名规则与缺省值定稿（各成员 retention 配置键归属各自模块 Configs——如 `NopAuthConfigs`/模块自有 Configs 形态，键命名与缺省值在本文档逐单表定稿，模块落地照抄不发明）——§3.2 五模块键表（含 nop-sys 双 face 段 lock 7d/changelog 90d）
- [x] 清理触发形态菜单定稿（周期 job 低频接入 / create·管理路径顺带清扫 / BizModel purge action / close 桥接——含各形态适用判据：**机器速率写入的账本表优先自动触发形态**（周期 job/路径顺带——人工 purge action 对 cron/机器速率写入面的速率差无解，与 W-02 缺陷逻辑同源），对照先例集：`TccEngine.cleanCompletedTransactions`/`SysDaoMessageService.cleanupExpiredEvents`/`NopCredentialOauthStateStore.cleanupExpired`/`NopMetaLineageEdgeQueryAction` deleteByQuery）——§3.3 四态菜单（A 必须自动/B 短 TTL 临时行限定/C 仅补充通道/D 生命周期配对专用）+ 裁定②生产接线契约（防 C7-005 空壳）
- [x] 六成员逐单传导表（对标 pool-facility-hardening §3.1.3 先例）：每成员=账本清单（成员 4 七面[含第七面具名行，见下]/成员 3 六族——trusted-device 第七面 R1 修订摘除，逐单表只留边界注记行→Deferred/R3-01/成员 6 双载体差异条款）+参数取值（含清理谓词时间锚列）+清理载体+回归钉要求——§3.4 全表在位（六成员+边界注记行+第七面具名行，锚列逐表落名）
- [x] 差异条款两条：①5.6-W-01 进程内生命周期配对形态（保留期参数不适用→投影声明，勘误 7）；②5.5 G2 retention×reJudge 同表契约注记（勘误 8）——§3.5 两则在案（+裁定⑥§一.6）
- [x] 5.2-MA-04 留存语义条款：sys_change_log retention 默认值与审计合规要求的对照结论落文档（「有意长期保留」可能性的裁定结论）——裁定⑤（§一.5）：不裁定永久保留，缺省 90 天，`0`=永久保留显式 opt-in，禁止三无状态延续
- [x] 家族 SSOT 声明：R01 全族后续批（R2-07/R2-08/R2-12/R3-05/R4-03/R4-07/R4-10/R4-29）对齐本设计；参数修订只走本文档一处定稿，禁止模块内私改参数语义——§3.6 在案（含 R2-12/R4-29/R4-07 对齐路径与周期链设施契约[periodic-task-insulation/pool-facility-hardening §3.1.1]引用）
- [x] 第七面具名行（C7-101 衍生告警工作流实例）：该面落在 nop-wf 侧工作流实例存储（非 nop-metadata 自有表），逐单表为其设具名行；机制候选显式化三选一——①工作流侧完成/取消 API 清理；②按族级纪律「实例随 quality_result 保留窗同步清理」路径；③显式边界声明归属 R3-05——三选一由设计裁定④定夺并落账理由与依据——裁定④=选③（§一.4 + §3.4 具名行：nop-wf 所有权/活跃审批不可 TTL 误删/扩批禁止/R3-05 契约对侧收敛四理由落账）
- [x] 索引登记义务：`ai-dev/design/concurrency/README.md` 索引登记本文档条目 + `ai-dev/design/README.md` 子系统 Status 行同步（设计文档落盘即登记，不留孤儿文档）——两处均已随 `5dd46531ed` 登记（README 条目+阅读顺序第 5 项；design/README.md concurrency 行 Status 追加）

> **执行注记（plan 2502 Phase 1——配额中断接续交付）**：设计文档 `ai-dev/design/concurrency/ledger-retention-policy.md` 落盘并提交 @ `5dd46531ed` 后实现会话配额中断（本回填与日志未及完成）；续作子代理按上列九项逐项审计该提交 diff——九项产出全在位、无内容缺口。四条增补 exit criteria 对应关系：统一设计=§3.1、1.6↔1.7 批次对齐=§3.4 成员 2 行（同设计同参声明+§3.2 nop-batch 行）、5.2-MA-04 留存语义=裁定⑤、5.6-W-01 过期指纹误拒面=§3.4 W-01 行（端到端回归钉要求）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 设计文档落盘且上列九项齐备（含第七面具名行与索引登记）；六成员逐单表覆盖 batch-proposal R1-07 exit criteria 四条增补（统一设计一处定稿 / 1.6↔1.7 批次对齐 / 5.2-MA-04 留存语义 / 5.6-W-01 过期指纹误拒面）
- [x] No owner-doc update required（本 Phase 纯设计决策文档，模块 owner doc 更新归各分片 Phase 显式登记去向、Phase 8 统一落账）——实际落账超前：nop-retry.md/nop-batch.md 保留策略节已随分片 A/B 提交先行落账
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-10-25 条目由续作子代理补记，含接续说明）

### Phase 2 - 分片 A：1.6-C7-001 retry 三账本表保留期清理入口（nop-retry）

Status: completed（2026-10-25，提交 `873365710c`；配额中断接续收尾回填）
Targets: `nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/store/RetryRecordStoreImpl.java`、`nop-retry/nop-retry-engine/src/main/java/io/nop/retry/engine/impl/RetryEngineImpl.java`（成功路径面）、`nop-retry/nop-retry-service/src/main/java/io/nop/retry/service/entity/`（BizModel purge 面）、retention 配置键载体、`nop-retry` 测试目录

- Item Types: `Fix`（1.6-C7-001 已确认 live defect）、`Proof`（回归测试）

- [x] 按 Phase 1 逐单表实现保留期清理入口：COMPLETED record / attempt / 死信三类账本按保留期清理；attempt 孤儿随死信保留期一并清理（不留 `deadLetter.record` 悬空后的永久孤儿——审计机制⑥面）；清理入口幂等（重复触发无害）——`RetryRecordStoreImpl.purgeExpiredLedgerRows` 三通道（①COMPLETED record 锚列 updateTime+status 谓词→级联删 attempt→删 record；②死信锚列 createTime→级联删孤儿 attempt[recordId IN 死信集]→删死信；③孤儿 attempt 锚列 startTime 兜底扫描——统一覆盖死信迁移/OVERWRITE 重建/历史遗留孤儿）；有界拾取+IN 批删、谓词幂等
- [x] retention 参数化：配置键/缺省值照抄 Phase 1 逐单表（不模块内发明）；owner doc 声明默认保留期（去向登记归 Phase 8，本 Phase 显式登记）——新建 `RetryEngineConfigs`（`nop.retry.ledger.retention-days=30`/`cleanup-interval=1h`/`delete-batch-size=1000`，缺省值对齐设计 §3.2 nop-retry 行）；owner doc 已随提交落账（见 Exit）
- [x] 回归测试（rule 25，测试名带 `_C7_001` 标识）：①COMPLETED record+attempt 超期清理断言；②死信清理时孤儿 attempt 一并清理断言；③未到期行保留断言（清理不误伤在途/未到期记录）；④清理入口幂等断言；⑤既有 retry 测试零回归——`TestRetryLedgerRetention_C7_001` 7 例（①completedRecordAndAttemptsPurged ②orphanAttemptsPurgedWithExpiredDeadLetter ②'overwriteOrphanAttemptsPurgedBySweep ③未到期/PENDING/SUSPENDED 保留断言在①内 ④purgeIdempotent ⑤engineLifecycleChain ⑥zeroRetentionDisables ⑦invalidConfigFailsFast；既有 TestRetryEngineImpl 30+TestRetryScannerImpl 6+TestRetryScannerInsulation 3+TestRetryRecordStoreImpl 8 零回归）
- [x] **无静默跳过**（rule 24）：清理执行失败路径显式日志与可见性，不吞异常（fail-fast 包络按设计文档）——`IPeriodicTaskListener.onRunFailure/onChainRejected` ERROR+失败计数；错配置启动期 fail-fast（`nop.err.retry.ledger-config-invalid`：负保留期/非正批删上限/周期<60s 三态断言）
- [x] **提交纪律**：nop-retry 一提交，commit message 带 `1.6-C7-001` 与 plan 号——`873365710c`
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-10-25 条目由续作子代理补记，含接续说明）

> **执行注记（1.6-C7-001 面，plan 2502 Phase 2——配额中断接续交付）**：
> - 接续说明：实现会话提交 `873365710c` 后配额中断（本回填/门禁复跑/日志未完成）；续作子代理按本 Phase 清单逐项审计前任 diff——清理实现/参数化/回归测试/接线/owner doc 五面全在位、无内容缺口；变异探针恢复核验通过（purge 体/兜底扫描可执行行在位，探针标记零残留）。
> - 接口面登记：`IRetryRecordStore.purgeExpiredLedgerRows(retentionTimeMillis, batchSize)` 新增方法（javadoc 载明三通道与锚列语义）；既有两测试载体的实现类同步补实现（`TestRetryEngineImpl` 委托透传/`TestRetryScannerImpl` UnsupportedOperationException 快速失败——非语义弱化）。
> - 必红证据（E2，变异探针双段，修复后恢复即绿）：purge 体置空 → 3 红（`_tmp/phase2-c7-001-red.log`：①②④三清理断言 deleted=0）；兜底扫描置空 → 1 红（`_tmp/phase2-c7-001-red-orphan.log`：②' OVERWRITE 孤儿断言）；恢复后全绿（`_tmp/phase2-c7-001-green.log` 6 success=true / `_tmp/phase2-c7-001-green-orphan.log` 7 success=true）。
> - rule 23 接线验证：`testC7_001_engineLifecycleChainInvokesLedgerCleanup`——doStart 生产生命周期链真实调度（断言 taskName=`nop-retry-ledger-cleanup`/interval=1h 传参）+ 被调度命令在真实 DB 链上执行清理（过期行经 scheduled command 删除——非仅 API 存在）+ doStop 取消断言；形态 A 载体=引擎既有生命周期钩子（`LifeCycleSupport.doStart`，与扫描链同构），零生产装配空壳形态不复刻。
> - 门禁：`./mvnw test -pl nop-retry -am` 全聚合通道三度受阻于传递依赖批前既有 flake（①run1/`_tmp/phase13-nop-retry-gate.log` nop-stream-connector-debezium surefire fork 超时——根 pom 已载 known issue 注记[forkedProcessExitTimeoutInSeconds 放宽 120s 即为此]，solo 复跑 70 tests 0F BUILD SUCCESS `_tmp/phase13-debezium-solo.log`；②run2/`_tmp/phase13-nop-retry-gate2.log`+前任会话门禁 `_tmp/gate-nop-retry-am.log` nop-auth-service `TestOperationMfaE2E` mfa-fail——**batch 基线 `b88a130a8f` checkout 后 solo 复跑同族 6 错**（`_tmp/phase13-mfa-solo-baseline.log`；HEAD solo 5 错 `_tmp/phase13-mfa-solo.log`；错误基数逐轮波动=时钟/TOTP 窗口敏感型；nop-auth 自 2026-10-14 绿色基线零提交、`dependency:list` 核实 nop-retry 不在 auth-service 依赖树）；③gate3 `-fae` 分解通道（-app 装配模块除外）nop-retry engine/api/dao/meta 全 SUCCESS+debezium 唯一 FAILURE `_tmp/phase13-nop-retry-gate3.log`，nop-retry-service 经传递链 skip 后 solo 复跑 4/4 BUILD SUCCESS `_tmp/phase13-nop-retry-service-solo.log`[前任会话同口径先例 `_tmp/phase2-nop-retry-full.log`]。nop-retry-app/web 为 demo 汇装模块（无自有测试、唯引入 auth-service/消息栈传递 flake 面）。
> - Owner doc 去向：`docs-for-ai/03-modules/nop-retry.md`「账本保留策略」节已随本提交落账（配置键表/三通道清理谓词/不清理面/死信重放通道随保留期关闭声明/失败可见性）——Phase 8 核对即可。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 1.6-C7-001 landed：三账本表保留期清理入口在位且参数来自 Phase 1 逐单表，带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：清理断言先红后绿或变异/回退论证留痕（修复前基线无清理入口——回退论证下断言全红；快照 `_tmp/phase2-c7-001-*.log` 含 RED 与 GREEN 两段）
- [x] **接线验证**（rule 23）：断言清理入口在真实生产链上被调用（job 装配链/提交路径/**逐单表裁定的其他形态**真实触发清理，非仅 API 存在；若逐单表裁定为 purge action，接线验证=真实 action 调用链测试 + 生产可达性核对[注册入口/运维面]——本族自身先例 C7-005 即「清理 API 存在但零生产装配」的空壳形态，本工单不得复刻）
- [x] `./mvnw test -pl nop-retry -am` BUILD SUCCESS（以分解模块集等价通道承载：engine/api/dao/meta 门禁 in-reactor SUCCESS + service solo BUILD SUCCESS；全聚合通道被传递依赖批前既有 flake 阻断的三轮 triage 见执行注记——debezium fork 超时与 auth-service TestOperationMfaE2E 均经基线 checkout/solo 复跑证明非本批回归）
- [x] Owner doc 裁定：`nop-retry.md` 保留期契约核对（「死信多次失败周期可产生多条」已声明、保留策略未声明的缺口随修复收敛为显式保留期声明）——受影响面随批更新或显式写 `No owner-doc update required` + 理由；最终落账可归 Phase 8 但须在本 Phase 显式登记去向
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 分片 B：1.7-C7-003 batch 三表保留清理（nop-batch）——与分片 A 同设计同参

Status: completed（2026-10-25，提交 `3e59106e6b`；配额中断接续收尾回填）
Targets: `nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/store/DaoBatchStateStore.java`（对照面）、`nop-batch/nop-batch-dao/src/main/java/io/nop/batch/dao/history/DaoBatchRecordHistoryStore.java`（对照面）、清理载体（job/action，按 Phase 1 逐单表）、retention 配置键载体、`nop-batch` 测试目录

- Item Types: `Fix`（1.7-C7-003 已确认 live defect）、`Proof`（回归测试）

- [x] 按 Phase 1 逐单表实现三表清理入口：nop_batch_task 终态行（COMPLETED/FAILED/KILLED 超期）、nop_batch_record_result（按完成时间 TTL 批删）、nop_batch_task_var 随任务行清理；nop_batch_file 附带登记面处置按逐单表结论落账（清或显式声明，处置一致不静默分叉）——`DaoBatchLedgerCleaner`（nop-batch-dao/cleanup）：①终态任务行锚列 endTime+taskStatus 终态谓词（`>=TASK_STATUS_COMPLETED`，覆盖 COMPLETED/FAILED/CANCELLED/KILLED）→ record_result/task_var/file 按 `batchTaskId IN` 级联→删任务行；②record_result 锚列 createTime 独立 TTL 通道（覆盖任务行非终态期增长面）；nop_batch_file=级联随任务行、无独立 TTL（设计 §3.4 成员 2 行显式声明，处置一致）
- [x] retention 参数化：配置键/缺省值照抄 Phase 1 逐单表；**与成员 1 同参同形（批次对齐声明 L314——两分片的清理形态与参数语义在 owner doc 声明中互引）**；owner doc 声明保留策略（去向登记归 Phase 8）——新建 `NopBatchDaoConfigs`（`nop.batch.ledger.retention-days=30`/`cleanup-interval=1h`/`delete-batch-size=1000`，与 nop-retry 同参；缺省值唯一事实源=设计 §3.2 nop-batch 行）；owner doc 已随提交落账（见 Exit）
- [x] 回归测试（rule 25，测试名带 `_C7_003` 标识）：①UUID taskKey 任务行超期清理断言；②record_result 按 TTL 批删断言；③task_var 随任务行清理断言；④未到期/非终态行保留断言；⑤既有 batch 测试零回归——`TestDaoBatchLedgerCleanup_C7_003` 6 例（①expiredTerminalTaskCascadeCleanup[task+var+file+record_result 级联删除+未到期/非终态保留④] ②recordResultTtlSweepIndependentOfTaskState ③purgeIdempotent ④lifecycleChainInvokesLedgerCleanup ⑤zeroRetentionDisables ⑥invalidLedgerConfigFailsFast；既有 batch 测试零回归由模块门禁承载）
- [x] **无静默跳过**（rule 24）：清理失败路径显式可见，不吞异常——`IPeriodicTaskListener.onRunFailure/onChainRejected` ERROR+失败计数（`nop.batch.ledger.cleanup-fail`）；错配置启动期 fail-fast（`nop.err.batch.ledger-config-invalid` 三态断言）；清理体异常直接抛出由绝缘 listener 观测
- [x] **提交纪律**：nop-batch 一提交，commit message 带 `1.7-C7-003` 与 plan 号——`3e59106e6b`
- [x] `ai-dev/logs/` 对应日期条目已更新（2026-10-25 条目由续作子代理补记，含接续说明）

> **执行注记（1.7-C7-003 面，plan 2502 Phase 3——配额中断接续交付）**：
> - 接续说明：实现会话提交 `3e59106e6b` 后配额中断；续作子代理逐项审计前任 diff——三表清理/同参对齐/回归测试/装配接线/owner doc 五面全在位、无内容缺口。
> - 生命周期接线（防 C7-005 空壳）：`app-batch-dao.beans.xml` 装配单例 bean `nopDaoBatchLedgerCleaner` → IoC 容器创建 → `LifeCycleSupport` `@PostConstruct start()` → `doStart` 周期调度（`timer.executeOn(worker).scheduleWithFixedDelay` 绝缘六参入口，taskName=`nop-batch-ledger-cleanup`，与 nop-sys `cleanupExpiredEvents` 同构）→ `doStop` 取消；`retention-days=0` 显式禁用调度。
> - 必红证据（E2，变异探针）：4 红（`_tmp/phase3-c7-003-red.log`：①级联清理/接线端到端/幂等/TTL 通道四断言 deleted=0 或任务行残留）→ 恢复全绿（`_tmp/phase3-c7-003-green.log` 6 success=true）。
> - rule 23 接线验证：`testC7_003_lifecycleChainInvokesLedgerCleanup`——doStart→CapturingTimer 捕获调度参数→被调度命令运行时在真实 DB 链上删除过期任务行（端到端接线断言——非仅 API/bean 存在）。
> - 门禁：`./mvnw test -pl nop-batch -am` 全聚合通道以分解模块集承载（-app 装配模块除外——唯引入 auth-service 传递 flake 面，同 Phase 2 注记口径）：`_tmp/phase13-nop-batch-gate.log` BUILD SUCCESS（nop-batch-core/dao/dsl 等 14 模块 -am 通道全绿，`TestDaoBatchLedgerCleanup_C7_003` 6/6 in-reactor 绿）。
> - Owner doc 去向：`docs-for-ai/03-modules/nop-batch.md`「账本保留策略」节已随本提交落账（与 nop-retry.md 互引批次对齐声明 L314；幂等语义窗口=保留窗声明[filterProcessed 记录级去重依赖 record_result 行存在，超期清除后同 taskKey 已处理记录可能被重新处理——更长幂等窗口部署应调大 retention-days]）+ 源码锚点表补 `DaoBatchLedgerCleaner` 行。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 1.7-C7-003 landed：三表清理入口在位且参数来自 Phase 1 逐单表（与成员 1 同源），带 ≥1 条 focused 回归测试且测试名可辨识工单
- [x] 必红证据（E2）：清理断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase3-c7-003-*.log`）
- [x] **接线验证**（rule 23）：断言三表清理入口在真实生产链上被调用（与成员 1 同防 C7-005 空壳形态——job/action 装配链真实触发）
- [x] `./mvnw test -pl nop-batch -am` BUILD SUCCESS（分解模块集承载[-app 装配模块除外，理由同 Phase 2 注记]：`_tmp/phase13-nop-batch-gate.log` BUILD SUCCESS 全绿）
- [x] Owner doc 裁定：`nop-batch.md` 保留策略声明核对（审计建议 (4)「history 表保留策略显式声明」面）——受影响面随批更新或显式写 `No owner-doc update required` + 理由；去向登记
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 分片 C：5.1-MA-01 六族清理配对（nop-auth，ask-first 已授权[边界=六族] + nop-credential 先例面；trusted-device 第七面 R1 修订摘除）

Status: planned
Targets: `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/login/DaoUserContextCache.java`（:164 removeUserContextAsync 面）、`login/DaoLoginSessionStore.java`、`mfa/store/DbMfaChallengeStore.java`、`mfa/store/DbSmsCodeStore.java`、`mfa/store/DbEmailCodeStore.java`、`ratelimit/DbSendCodeRateLimiter.java`、`login/DbLoginAttemptStore.java`、`mfa/MfaTrustedDeviceManager.java`（R1 修订摘除——零触碰，仅作零 diff 核验对照）、`NopAuthConfigs`（retention 配置）、`nop-auth`/`nop-credential` 测试目录

- Item Types: `Fix`（5.1-MA-01 已确认 live defect[六族 P1]）、`Proof`（回归测试，C10-001/004/005 回归钉）

> 续作纪律（R1 修订增补）：分片 C 允许分会话续作——续作会话开工前必须重读本 Phase 全文与头部授权声明；每面独立提交（六族清扫/retention 参数化/回归测试可分 commit，commit message 均带工单 ID 与 plan 号）；本 Phase 勾选状态为进度事实源，续作以勾选状态为准、不凭记忆或会话外状态推进。

- [ ] **授权前置核对**：确认头部 ask-first 授权记录（2026-10-25）在案且本分片触碰面限于授权范围（**六族清理配对**——trusted-device 过期清扫第七面 R1 修订摘除：本批摘除，归 Deferred But Adjudicated 已登记项，successor=R3-01，本分片不得触碰 `MfaTrustedDeviceManager`）；不触碰认证模型判定逻辑/MFA 并发纪律（MA-04 本体 → R3-01）
- [ ] 六族统一批量清扫（复用 `NopCredentialOauthStateStore.cleanupExpired()` 语句形态）：NopAuthSession 过期行清理（含令牌载荷行——行删除即载荷清除）+ 五族跨键批量清扫（`delete where expireAt < now - retention`）；清理载体（job 低频接入或 create/管理路径顺带）按 Phase 1 逐单表定稿，六族处置一致不静默分叉。trusted-device 过期行清扫不在本项（R1 修订摘除→Deferred/R3-01）
- [ ] retention 参数化：`NopAuthConfigs` 配置键/缺省值照抄 Phase 1 逐单表；orm 不变（纯清理——零触碰声明）
- [ ] 回归测试（rule 25，测试名带 `_MA_01` 标识；C10-001/004/005 回归钉同 diff）：①session 过期行清理+未过期行保留断言（可注入时钟）；②五族批量清扫参数化断言（仅过期行删除，五族逐一）；③countUnexpired 口径不变断言（仅计未过期，C10-005 钉——零改动面只读口径钉，trusted-device 文件本批零产品 diff 下仍须绿）；④trusted-device 过期行清扫断言——**本批摘除（R1 修订：授权边界=六族，第七面归 Deferred→R3-01），不实施该断言，改核验 `git diff` 中 MfaTrustedDeviceManager 零产品 diff**；⑤既有 nop-auth 测试零回归
- [ ] **无静默跳过**（rule 24）：清扫失败/部分失败路径显式可见
- [ ] **提交纪律**：nop-auth 一提交（nop-credential 若产生先例面对齐 diff 单独提交并声明关系），commit message 带 `5.1-MA-01` 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 5.1-MA-01 landed：六族批量清扫在位（session 令牌载荷行随过期清理、五族不再仅同键惰性；trusted-device 第七面清扫已 R1 修订摘除、不在本验收面），参数来自 Phase 1 逐单表；ask-first 授权记录引用在 commit/日志留痕
- [ ] C10-001/004/005 回归钉同 diff 在位（测试 ①②③ 即三钉落地）
- [ ] 必红证据（E2）：清扫断言先红后绿或变异/回退论证留痕（修复前基线无批量清扫入口——回退论证断言红；快照 `_tmp/phase4-ma01-*.log`）
- [ ] **接线验证**（rule 23）：断言六族清扫在真实生产链上被调用（job 装配链或 create/管理路径真实触发——非仅 store 方法存在；防 C7-005 空壳形态）
- [ ] `./mvnw test -pl nop-auth -am` BUILD SUCCESS；nop-credential 若有 diff 则 `./mvnw test -pl nop-credential -am` BUILD SUCCESS（零 diff 以实际为准登记）
- [ ] Owner doc 裁定：`nop-auth.md` 六族保留期契约核对与更新（PA 授权面文档同步）；`nop-credential.md` 核对（对照组先例语义零回退）——受影响面随批更新或显式写 `No owner-doc update required` + 理由；去向登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 分片 D：5.5-W-02 七面 retention（nop-metadata）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/main/java/io/nop/metadata/service/quality/QualityResultWriter.java`（对照面）、清理载体（nop-job 定期 job 或 BizModel purge action，按 Phase 1 逐单表）、`nop-metadata` 测试目录；ORM 模型零触碰（只读引用）

- Item Types: `Fix`（5.5-W-02 已确认 live defect）、`Decision`（七面清理载体与 event 表最长保留窗口结论）、`Proof`（回归测试）

- [ ] 按 Phase 1 逐单表实现七面 retention：六表（quality_result/quality_score/catalog/profiling_result/reconciliation_result/model_changed_event[mediumtext 双快照]）+ 衍生告警工作流实例面（C7-101 三写点随族覆盖；第七面落在 nop-wf 侧工作流实例存储非自有表——处置按 Phase 1 设计裁定④三选一结论执行，见 Phase 1 第七面具名行）——六表清理载体按逐单表（deleteByQuery 先例形态），event 表最长保留窗口结论落账
- [ ] G2 契约核对：retention 设计与 reJudge 同表面语义在 Phase 1 设计文档注记闭合（勘误 8），本分片零 W-28 本体改动
- [ ] retention 参数化：配置键/缺省值照抄 Phase 1 逐单表；owner doc 声明保留策略默认值（`nop-metadata.md`，去向登记归 Phase 8）
- [ ] 回归测试（rule 25，测试名带 `_W_02` 标识）：①六表逐面超期清理断言（mediumtext event 表含内）；②未到期行保留断言（「历史事实语义」在保留窗口内不破坏）；③清理幂等断言；④既有 nop-metadata 测试零回归；⑤第七面（C7-101 工作流实例）验证形态按设计裁定④落账——裁定为可测清理路径时补对应断言（测试名仍带 `_W_02`）；裁定为边界声明归属 R3-05 时在本 Phase Exit Criteria 显式登记 rule 25 No-new-test 声明 + 理由（nop-wf 侧非自有表，本批零产品 diff）
- [ ] **无静默跳过**（rule 24）：清理失败路径显式可见
- [ ] **提交纪律**：nop-metadata 一提交，commit message 带 `5.5-W-02` 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 5.5-W-02 landed：七面 retention 在位且参数来自 Phase 1 逐单表（机械核验：六表清理入口经 git diff/测试可见；第七面按设计裁定④结论二选一在案——清理接线在位，或「边界声明归属 R3-05 + rule 25 No-new-test 声明」落账），带 ≥1 条 focused 回归测试且测试名可辨识工单
- [ ] 必红证据（E2）：清理断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase5-w02-*.log`）
- [ ] **接线验证**（rule 23）：断言七面清理在真实生产链上被调用（job 注册链或 BizModel action 真实可达——防 C7-005 空壳形态）
- [ ] `./mvnw test -pl nop-metadata -am` BUILD SUCCESS（勘误 1 补通道）；`./mvnw test -pl nop-rule -am` 若零 diff 以实际为准登记
- [ ] Owner doc 裁定：`nop-metadata.md` retention 契约更新（勘误 2 补锚）；`nop-rule.md` 核对（预期 `No owner-doc update required`——站点零在 nop-rule，以核对结论为准）；G2 注记核对；去向登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 6 - 分片 E：5.6-W-01 双载体账本 close 桥接 + 过期指纹误拒闭合（nop-duckdb）

Status: planned
Targets: `nop-duckdb/src/main/java/io/nop/duckdb/DuckDbEngine.java`（openFile/openMemory 返回值包装 + track/untracked）、`nop-duckdb/src/main/java/io/nop/duckdb/DuckDbSqlTaskStep.java`（消费链回归面）、`nop-duckdb` 测试目录；`IDuckDbEngine.java` 契约文本零改动（Non-Goals）

- Item Types: `Fix`（5.6-W-01 已确认 live defect——四方案并证族主）、`Proof`（回归测试）

- [ ] 按 Phase 1 逐单表（含形态差异条款，勘误 7）实现连接 close 桥接：openFile/openMemory 返回 close 时回调的 Connection 包装——包装 close 内锁内移除对应 TrackedConnection（untracked）+ filePath != null 时 releaseOpen；`track` 记录原对象保 engine.close() 幂等兜底语义零回退；`DuckDbSqlTaskStep` TWRS 消费链经包装后 refs 自动归零（生产消费者零改动或最小适配，以逐单表结论为准）
- [ ] 过期指纹误拒面闭合：step 消费链执行后 OPEN_FILES 条目随 refs 归零释放，同路径异配置 engine 重开不再命中残留指纹（exit criteria 增补条独立验收面）
- [ ] 回归测试（rule 25，测试名带 `_W_01` 标识）：①step 执行后 OPEN_FILES 条目释放断言（双载体之一：refs 归零）；②connections 账本随包装 close 清理断言（载体 B：C6-R2-001 面）；③同路径跨 engine 重开不再误拒断言（`ERR_DUCKDB_CONFIG_CONFLICT` 假阳性消除——100% 假阳性场景回归钉；**rule 22 端到端**：step 消费链执行→refs 归零→跨 engine 重开不误拒全链一次跑通，组件级单测不替代）；④engine.close() 兜底幂等断言（既有 close 语义零回退）；⑤既有 duckdb 测试零回归
- [ ] **无静默跳过**（rule 24）：包装 close 失败路径显式可见，不吞异常
- [ ] **提交纪律**：nop-duckdb 一提交，commit message 带 `5.6-W-01` 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 5.6-W-01 landed：双载体随连接生命周期闭合（close 桥接在位）+ 过期指纹误拒面闭合，带 ≥1 条 focused 回归测试且测试名可辨识工单
- [ ] 必红证据（E2）：先红后绿（修复前基线跑 ①③——TWRS 旁路下 refs 残留 + 指纹误拒断言红，测试仅用修复前既有 API 面保证可编译）或变异/回退论证留痕（快照 `_tmp/phase6-w01-*.log`）
- [ ] **接线验证**（rule 23）：断言 close 桥接在真实 `DuckDbSqlTaskStep` 消费链上运行时生效（包装连接被 step 真实持有并关闭，refs 归零可观测——非仅包装类型存在）
- [ ] `./mvnw test -pl nop-duckdb -am` BUILD SUCCESS
- [ ] Owner doc 裁定：`nop-duckdb.md` 连接生命周期契约核对（「refs 随连接 close 归零」语义随修复收敛为显式契约）——受影响面随批更新或显式写 `No owner-doc update required` + 理由；去向登记
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 7 - 分片 F：5.2-MA-04 sys_lock/sys_change_log retention + 留存语义（nop-sys）

Status: planned
Targets: `nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/lock/SysDaoResourceLockManager.java`（周期清理面）、`nop-sys/nop-sys-dao/src/main/java/io/nop/sys/dao/log/OrmEntityChangeLogInterceptor.java`（清理对面 + saveDirectly 批量化）、retention 配置载体、`nop-sys` 测试目录

- Item Types: `Fix`（5.2-MA-04 已确认 live defect[P2 族]）、`Decision`（sys_change_log 留存语义条款结论）、`Proof`（回归测试）

- [ ] 按 Phase 1 逐单表实现周期清理（照抄 `cleanupExpiredEvents` 形态）：sys_lock `delete where expireAt < now - grace`（覆盖「崩溃持有者+无人争用」僵尸行——争用触发式惰性清理的零覆盖面）+ sys_change_log 按留存语义结论清理
- [ ] 留存语义条款落地（exit criteria 增补条）：sys_change_log retention 默认值与审计合规对照结论取自 Phase 1 设计文档，owner doc 补 retention 声明（声明落点=本工单验收面；若结论为「有意长期保留」则声明为显式边界并登记上限/导出策略——不允许三无状态延续）
- [ ] ChangeLog saveDirectly 批量化（单会话批量插入同实体列行——写放大面随批，MA-04 族成员面）
- [ ] retention 参数化：配置键/缺省值照抄 Phase 1 逐单表
- [ ] 回归测试（rule 25，测试名带 `_MA_04` 标识）：①sys_lock 僵尸行（expireAt 过期+无争用）周期清理断言；②在持有/未到期锁行保留断言（清理不误伤活锁）；③sys_change_log 按留存语义清理断言；④批量化后列行数与内容等价断言（postSave/postUpdate 面）；⑤既有 nop-sys 测试零回归
- [ ] **无静默跳过**（rule 24）：清理失败路径显式可见；批量化失败回退语义显式
- [ ] **提交纪律**：nop-sys 一提交，commit message 带 `5.2-MA-04` 与 plan 号
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 5.2-MA-04 landed：sys_lock/sys_change_log 周期清理在位 + saveDirectly 批量化 + 留存语义声明落 owner doc，参数来自 Phase 1 逐单表，带 ≥1 条 focused 回归测试且测试名可辨识工单
- [ ] 必红证据（E2）：清理断言先红后绿或变异/回退论证留痕（快照 `_tmp/phase7-ma04-*.log`）
- [ ] **接线验证**（rule 23）：断言周期清理在真实装配链上被调度执行（照抄 cleanupExpiredEvents 的 doStart 接线形态真实生效——防 C7-005 空壳形态）
- [ ] `./mvnw test -pl nop-sys -am` BUILD SUCCESS
- [ ] Owner doc 裁定：`nop-sys.md` retention 声明与留存语义结论落账（5.2-MA-04 exit criteria 增补条的落点）——受影响面随批更新；`No owner-doc update required` 不适用于本 Phase（留存语义声明为既定验收面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 8 - 全量验证 + owner doc 八份落账 + 台账回填（E1/E3/E5）

Status: planned
Targets: 受影响聚合模块树（nop-retry/nop-batch/nop-auth/nop-credential/nop-metadata/nop-rule/nop-duckdb/nop-sys）、`docs-for-ai/03-modules/`（nop-retry.md、nop-batch.md、nop-credential.md、nop-rule.md、nop-duckdb.md、nop-sys.md、nop-auth.md、nop-metadata.md）、cra-index 六成员行、roadmap 8.8 行

- Item Types: `Proof`（全量验证留证）、`Fix`（owner doc/台账同步义务）

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（最低限度：受影响聚合模块树 `-am` 全绿——nop-retry/nop-batch/nop-auth/nop-credential/nop-metadata/nop-duckdb/nop-sys 各通道；nop-tcc/nop-rule 若零 diff 以实际为准登记，勘误 1/4）
- [ ] E3 验证通道留证：各分片 `./mvnw test -pl <模块> -am` 结果快照 `_tmp/phase*-gate-*.log`
- [ ] 零触碰复核（E4）：`git diff` 核对产品 diff 不含 nop-api-core/`model/*.orm.xml`/nop-graph-api/`_` 前缀生成物；ask-first 授权记录与 commit 留痕核对
- [ ] E5 owner doc 八份落账：六份既定锚点（nop-retry/nop-batch/nop-credential/nop-rule/nop-duckdb/nop-sys.md）+ 两份实际触达锚点（nop-auth.md/nop-metadata.md，勘误 2）——各分片登记的去向统一落账；nop-rule.md 若核对后零更新则登记 `No owner-doc update required` + 理由
- [ ] cra-index 回填：6 成员行收敛（1.6-C7-001/1.7-C7-003/5.1-MA-01/5.5-W-02/5.6-W-01 P1 台账行 + P2-G75 行）+ 勘误 4/5 边界注记（C7-005→R2-12 对齐 SSOT、MA-04 本体+trusted-device 清扫第七面→R3-01[R1 修订摘除]）+ G2 契约注记（W-02 行）
- [ ] roadmap 8.8 行 → done（在 Phase 9 closure audit PASS 之后执行翻转；本 Phase 仅准备）
- [ ] 文档链接门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（覆盖本批新建 `ai-dev/design/concurrency/ledger-retention-policy.md`、`ai-dev/design/concurrency/README.md` 索引登记与八份 owner doc 修改）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] E1 全量构建绿（快照留证）
- [ ] E3 各模块验证通道全绿且快照在 `_tmp/`
- [ ] E4 零触碰复核通过（git diff 核对记录留痕）
- [ ] E5 owner doc 八份更新或显式 `No owner-doc update required` + 理由；cra-index 六行收敛
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 9 - closure audit + 收口（E6）

Status: planned
Targets: 本 plan 全部 Phase、`ai-dev/logs/` 收口条目、roadmap 8.8 行翻转

- Item Types: `Proof`（独立 closure audit）

- [ ] 从头重读整份 plan，逐条核对每个 Phase Exit Criteria 与 Closure Gates（guide「When Closing The Plan」9 件事）
- [ ] 独立子 agent closure audit（fresh session，不复用实现会话）：每条 Exit Criterion/Closure Gate 的 PASS/FAIL + live 证据；Anti-Hollow 检查（清理入口真实被调用链连通——非仅 API 存在；W-01 桥接在真实 step 消费链上生效；无空方法体/静默跳过）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2502-cra-phase8.8-r1-07-f4-ledger-retention.md --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module <受影响模块> --severity high` 退出码 0
- [ ] 文本一致性核对：Plan Status / 各 Phase Status / 各 Exit Criteria / Closure Gates / `ai-dev/logs/` 收口记录五处彼此一致
- [ ] roadmap 8.8 → done 翻转；Closure Audit Evidence 写入本 plan Closure 段

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] 独立 closure audit PASS 且证据写入 Closure 段（Reviewer/Agent 标识 + 每条 Gate 验证结果）
- [ ] check-plan-checklist `--strict` 退出码 0；scan-hollow-implementations 退出码 0
- [ ] roadmap 8.8 行 Status=done
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 6 项 in-scope confirmed live defects 已修复（1.6-C7-001/1.7-C7-003/5.1-MA-01/5.5-W-02/5.6-W-01 P1 + 5.2-MA-04 P2 提前搭车）
- [ ] 批特有 exit criteria 四条增补全收敛：①统一保留期/TTL/行数上限设计一处定稿（Phase 1 设计文档）且五（六）模块落地无分叉（各分片参数可追溯到逐单表）；②1.6-C7-001↔1.7-C7-003 账本表批次对齐（同设计同参声明在两分片与 owner doc 中互引）；③5.2-MA-04 留存语义按族级纪律落地（owner doc retention 声明在位）；④5.6-W-01 过期指纹误拒面同批闭合（跨 engine 复用回归钉在位）
- [ ] 每工单 ≥1 条 focused 回归测试（rule 25，测试名可辨识工单）；成员 3 携 C10-001/004/005 回归钉
- [ ] 必红证据（E2）各分片留痕（先红后绿或变异/回退论证，`_tmp/` 快照）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响 owner docs（八份）已同步到 live baseline，或显式写明 `No owner-doc update required` + 理由；PA ask-first 授权记录与零触碰声明经 E4 复核
- [ ] 独立子 agent closure-audit 已完成并记录证据（Closure 段）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）清理入口/桥接在真实调用链上运行时生效（retry 清理入口被装配链调用、duckdb 包装在真实 step 链生效等——不只是类型系统存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw clean install -T 1C` BUILD SUCCESS（或受影响聚合模块树 `-am` 全绿，快照留证）
- [ ] checkstyle / 代码规范检查通过（pre-commit lint 0 violations 留痕）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（本批修改 `ai-dev/design/`、`docs-for-ai/`、`ai-dev/plans/` 后强制）

## Deferred But Adjudicated

### 5.1-MA-04 trusted-device 过期行清扫第七面（R1 修订摘除）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: nop-auth ask-first 授权文本（2026-10-25）仅覆盖六族清理配对面；第七面（trusted-device 过期行清扫）须回询补授权后方可实施——不静默扩面为本 plan 自设红线（头部授权声明、Phase 4 授权前置核对项与 Risks 段同源），故不在本批 contract closure 面内；2026-10-25 扩面询问未获用户回复，保守采 R1 建议②维持六族边界。
- Successor Required: `yes`
- Successor Path: R3-01/nop-auth 域后续确认批次（与 MA-04 本体并发纪律锁一并承接；落地时对齐 Phase 1 设计文档逐单表边界注记行）

（其余相邻面归属均为「moved to explicit successor ownership」而非本 plan deferred，见 Non-Goals 与 Baseline 勘误 4/5/6/8：C7-005→R2-12、MA-04 本体→R3-01、W-28→R3-05、upsert/键语义→C7-015 所属批次、R01 族其余 13 项→各自批次。）

## Non-Blocking Follow-ups

- R01 全族后续批到达时的设计对齐注记落账（R2-07/R2-08/R2-12/R3-05/R4-03/R4-07/R4-10/R4-29 各自执行；Phase 1 设计文档已载 SSOT 声明）——不影响本批 contract closure。
- roadmap 8.8 / batch-proposal R1-07 行的模块列与 owner doc 锚点缺口（勘误 1/2）向批次提案维护面登记勘误（本批 Phase 8 已按实际触达面执行，登记不阻塞 closure）。

## Closure

Status Note: （收口时填写）
Completed: （未完成）

Closure Audit Evidence:

- Reviewer / Agent: （待 Phase 9 独立子 agent closure audit 填写）
- Evidence: （待填写：每条 Exit Criterion / Closure Gate 验证结果 + checklist/hollow 扫描退出码 + Anti-Hollow 调用链追踪结论）

Follow-up:

- （待收口时填写，或明确写 no remaining plan-owned work）

## Risks And Rollback

- **分片 C（nop-auth）为唯一 PA 触碰面**：授权记录（2026-10-25）必须先于动码在案，授权边界=六族（trusted-device 第七面清扫已 R1 修订摘除→Deferred/R3-01）；若执行中发现任何第七面外推需求或授权范围分歧，停止分片并回询用户，不静默扩面。
- **清理误伤风险**：各分片回归测试均含「未到期/非终态/活锁行保留」断言（Phase 2②/3④/5②/7②）；清理谓词实现错误由该断言族兜底。
- **回滚形态**：各分片独立提交，单分片回滚不影响他分片；retention 缺省值保守（宁长勿短——误删危害大于晚删，缺省值在 Phase 1 定稿时按此倾向取值并记录理由）。
