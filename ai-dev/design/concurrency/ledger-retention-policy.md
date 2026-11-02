# R01 F4 无界账本族级保留策略（ledger retention policy）

> Status: active
> Last Reviewed: 2026-10-30（R2-12 承接：§3.4 增 tcc 两表成员行 + §3.6 声明转 landed，plan 2515 Phase 1；2026-10-25 plan 2502 Phase 1 产出，族级 SSOT 一处定稿）
> Source: plan 2502（`ai-dev/plans/2502-cra-phase8.8-r1-07-f4-ledger-retention.md`）Phase 1；族级纪律 SSOT = family-clustering R01 行（`ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/family-clustering.md` §二.1：「保留期/TTL/行数上限+成功即清理；族级纪律=上限+逐出+fail-fast 包络声明」）；4.5 口径出处 = `ai-dev/audits/2026-10/2026-10-21-1444-deep-audit-nop-search-runner-autotest-concurrency/merge-adjudication.md`（F4 无界账本家族族级修复纪律）；成员发现事实源 = plan 2502 Current Baseline 六成员表（行号以该 plan live 回读值为准）
> 实现锚（分批落地）：nop-retry `RetryRecordStoreImpl`/`RetryEngineImpl`；nop-batch `nop-batch-dao` 清理组件；nop-auth 六族 store + `NopAuthConfigs`；nop-metadata 六表清理载体；nop-duckdb `DuckDbEngine` close 桥接；nop-sys `SysDaoResourceLockManager`/`OrmEntityChangeLogInterceptor` 邻接清理载体
> 传导先例：[pool-facility-hardening.md](pool-facility-hardening.md) §3.1.3（多模块工单同口径传导表范式）

## 一、设计结论

1. **裁定①（统一参数集一处定稿）**：R01 族 DB 账本表的保留策略参数全平台统一为七项（§3.1）：保留期 `retention-days`（缺省 30 天，0=显式禁用）、清理触发周期 `cleanup-interval`（缺省 1h，下限 60s）、单批删除上限 `delete-batch-size`（缺省 1000）、行数上限（DB 账本表**不设**——逐出判据唯一为保留期 TTL，理由见 §3.1 第 4 项）、超限/失败包络行为（配置非法装配期 fail-fast；执行失败 ERROR 显式可见+失败计数，周期链由 R1-01 绝缘层保障不断链）、配置键命名规则（§3.2）、清理谓词时间锚列（逐表显式指定，逐单表落名——禁止隐式选列）。**保留策略参数不六模块各自发明；后续任何参数修订只走本文档一处定稿。**
2. **裁定②（清理载体与生产接线契约）**：机器速率写入的 DB 账本表必须采用**自动触发形态**（周期低频清理，形态 A）；人工 purge action（形态 C）只能作为补充运维通道、不得为唯一形态——人工速率对 cron/自动提交速率差无解（与 5.5-W-02 缺陷逻辑同源）。周期链必须挂接生产可达的生命周期钩子（IoC 容器 `@PostConstruct`/`LifeCycleSupport.doStart` 链或 job 注册链），并以回归测试断言清理体被调度器真实调用——**防 1.6-C7-005「清理 API 存在但零生产装配」空壳先例复刻**（tcc `cleanCompletedTransactions` 本批仅作形态先例引用）。
3. **裁定③（触发形态菜单四态判据）**：形态 A 周期 job 低频接入（机器速率写入面**必须**）；形态 B create/管理路径顺带清扫（仅适用于写入路径天然周期性触达的短 TTL 临时行，credential OAuth state 先例；对键不复访场景有残留面，不得用于永续账本）；形态 C BizModel purge action（人工补充通道）；形态 D close 桥接（进程内生命周期配对专用，非 DB 表）。判据全景见 §3.3。
4. **裁定④（5.5-W-02 第七面机制三选一 → 选③边界声明归属 R3-05）**：C7-101 衍生 qualityBreachApproval 告警工作流实例（落在 nop-wf 侧工作流实例存储，非 nop-metadata 自有表）的清理机制裁定为**显式边界声明归属 R3-05**，本批 nop-wf 侧零产品 diff。理由：(a) 模块所有权——工作流实例表为 nop-wf 所有，nop-metadata 跨模块 DELETE 违反模块边界；(b) 语义不同构——工作流实例非 append-only 账本，存在进行中审批状态，TTL 误删产生「审批中告警实例静默消失」的正确性危害，与「终态即可逐出」的账本行语义不同；(c) 选①需 nop-wf 侧新增完成/取消清理 API，扩批且 nop-wf 非 R1-07 成员模块；(d) R3-05 已是 W-02 同表 retention×reJudge 契约对侧（差异条款②），quality 域 successor 收敛一处。落账：§3.4 成员 4 第七面具名行 + R3-05 对齐义务。
5. **裁定⑤（5.2-MA-04 留存语义条款）**：sys_change_log **不裁定为「永久有意保留」**。缺省保留期 90 天（全平台缺省中最长——审计留痕宁长勿短）；`nop.sys.ledger.changelog.retention-days=0` 为「永久保留」的**显式声明形态**（合规场景 opt-in，禁止三无状态延续：无声明/无清理/无上限）；owner doc（nop-sys.md）必须声明该保留策略，声明落点为 5.2-MA-04 工单验收面。依据：change log 审计行平台未接任何导出/归档设施，「永久」缺省使其成为逐列复制的最大无界表；显式 opt-out 保留合规可能性且使「长期保留」从隐式默认变为显式声明。
6. **裁定⑥（两条差异条款）**：①5.6-W-01 进程内注册表/连接账本（OPEN_FILES+connections）为**生命周期配对破损**，非 DB 表周期增长——保留期/TTL/行数上限参数对其不直接适用，族级纪律「上限+逐出+fail-fast」的投影 = 连接 close 桥接（泄漏载体消除后无「保留」需求）+ `engine.close()` 幂等兜底保留 + 过期指纹误拒面同批闭合（跨 engine 复用回归钉）；②5.5 G2 retention×reJudge 同表契约：quality_result 的保留窗即 reJudge 复用历史窗上界——R3-05 落地 W-28 时若需更长复用视野，参数修订只走本文档（上调 `nop.metadata.ledger.retention-days` 或按 §3.2 引入 face 级键），不得模块内另设语义；W-28 本体不进 R1-07 批。

## 二、背景与动机

R01（F4 无界账本/零保留族，19 成员/12 单元）的共同形态：账本表/注册表只写不逐出，行数随业务量/时间单调增长，查询随表深退化，终态数据与在途数据无生命周期分界。family-clustering R01 行给定族级纪律「上限+逐出+fail-fast 包络声明（4.5 口径）」；本设计把该纪律落成**一处定稿的参数集 + 逐成员传导表**，使 R1-07 六成员与后续 13 项族成员（R2-07/R2-08/R2-12/R3-05/R4-03/R4-07/R4-10/R4-29）共享同一套保留语义，防止六模块各自发明保留参数与触发形态。

平台既有先例集（本设计的对照锚）：tcc `TccEngine.cleanCompletedTransactions(retentionTime)`（状态过滤+分支随父级联删除形态）、nop-sys `SysDaoMessageService.cleanupExpiredEvents`（doStart 周期接线+失败可见形态）、nop-credential `NopCredentialOauthStateStore.cleanupExpired()`（create 顺带惰性清扫形态）、nop-metadata `NopMetaLineageEdgeQueryAction` deleteByQuery（单语句批量删除先例）。

## 三、核心设计

### 3.1 统一参数集（七项）

| # | 参数项 | 统一取值/规则 | 理由 |
|---|--------|--------------|------|
| 1 | 保留期 `retention-days` | int，单位天；缺省 **30**；`0`=显式禁用周期清理；负值=装配期 fail-fast | 宁长勿短（plan 风险段既定倾向）：误删危害大于晚删；30 天覆盖常用运维排查窗口 |
| 2 | 清理触发周期 `cleanup-interval` | Duration；缺省 **1h**；下限 60s，低于下限装配期 fail-fast | 低频自动触发；下限防高频清理冲击业务库 |
| 3 | 单批删除上限 `delete-batch-size` | int；缺省 **1000**；每轮每表先按锚列谓词+limit 有界拾取主键集，再按 `IN` 集批删；`<=0` 装配期 fail-fast | 防全表单语句删除的长事务/锁放大；周期调度下有界批删自然收敛 |
| 4 | 行数上限（row cap） | DB 账本表**不设**数值行上限；逐出判据唯一为保留期 TTL。进程内账本（W-01）的「上限」投影为生命周期闭合（裁定⑥①） | 行数上限对 append-only 账本引入「满即删未到期行」的正确性危害；TTL 已闭无界面，双判据徒增误删面 |
| 5 | 超限/失败包络行为（fail-fast 声明，4.5 口径） | 配置非法（负 retention/interval<60s/batch-size≤0）= **装配期 fail-fast**（模块级 `NopException`，不降级继续）；清理执行失败 = ERROR 日志（`nop.<module>.ledger.cleanup-fail`，含表名）+ 失败计数可见；**禁止空 catch**；单轮失败不阻断下轮（R1-01 绝缘层重臂语义） | 「上限+逐出+fail-fast」中 fail-fast 的两层投影：错配置不启动、执行失败不可见即违例；周期链失败显式可见而非静默吞掉（rule 24 同源） |
| 6 | 配置键命名与缺省值归属 | `nop.<module>.ledger[.<face>].retention-days` / `.cleanup-interval` / `.delete-batch-size`；键常量归属清理载体所在模块的类型化 Configs 接口（§3.2）；缺省值以本表为准，Configs 常量缺省值必须与本表一致 | 键名可预测、缺省值单一事实源 |
| 7 | **清理谓词时间锚列** | 逐表显式指定账龄列（逐单表落名），谓词形 = `锚列 < now − retention`（+ 状态/终态谓词限定可删集）；级联删除以父行主键 `IN` 集传递，不独立选锚；**禁止隐式选列/无谓词全表删除** | 清理谓词的账龄语义必须可审计；锚列错选（如用会被业务更新的列）会造成误删/漏删 |

时间锚列选取规则：锚列必须是**写入后不被业务语义回改**的账龄事实列（创建/终态迁移/过期时刻）；终态谓词与锚列并用以排除在途行。

### 3.2 配置键命名规则与归属

| 模块（清理载体所在） | 配置载体 | 键（缺省值） |
|----------------------|---------|--------------|
| nop-retry-engine | 新建 `RetryEngineConfigs`（`io.nop.retry.engine`，`varRef` 类型化形态，先例 = `BatchConfigs`） | `nop.retry.ledger.retention-days`（30）/ `nop.retry.ledger.cleanup-interval`（1h）/ `nop.retry.ledger.delete-batch-size`（1000） |
| nop-batch-dao | 新建 `NopBatchDaoConfigs`（`io.nop.batch.dao`，先例 = `NopSysDaoConfigs`） | `nop.batch.ledger.retention-days`（30）/ `nop.batch.ledger.cleanup-interval`（1h）/ `nop.batch.ledger.delete-batch-size`（1000）——**与 nop-retry 同参（1.6-C7-001↔1.7-C7-003 批次对齐，batch-proposal §四 L314）** |
| nop-auth-service | 既有 `NopAuthConfigs` | `nop.auth.ledger.retention-days`（30）/ `nop.auth.ledger.cleanup-interval`（1h）/ `nop.auth.ledger.delete-batch-size`（1000）——六族共用一套，处置一致 |
| nop-metadata-service | 新建（`NopSysDaoConfigs` 形态） | `nop.metadata.ledger.retention-days`（30）/ `nop.metadata.ledger.cleanup-interval`（1h）/ `nop.metadata.ledger.delete-batch-size`（1000）——七面同窗（event 表最长保留窗口 = 该统一值） |
| nop-sys-dao | 既有 `NopSysDaoConfigs` | 双 face 段：`nop.sys.ledger.lock.retention-days`（7）/ `nop.sys.ledger.changelog.retention-days`（90，`0`=永久保留显式 opt-in）/ 共用 `nop.sys.ledger.cleanup-interval`（1h）、`nop.sys.ledger.delete-batch-size`（1000） |

face 段规则：单面模块省略 face 段（`nop.<module>.ledger.*`）；多语义 face 模块（nop-sys 的锁行 vs 审计行）face 段必配。retention 缺省值差异（sys lock 7 天 / changelog 90 天）来自行语义：锁行过期后即无效果（grace 仅防御时钟偏移），change log 是审计留痕（宁长勿短全域上限）。

### 3.3 清理触发形态菜单与判据

| 形态 | 判据（满足方可选） | 平台先例 | 约束 |
|------|-------------------|---------|------|
| A 周期 job 低频接入（自动） | 机器速率写入面（cron/自动提交/每请求落账）——**此类账本必须选 A** | `SysDaoMessageService.cleanupExpiredEvents` doStart 接线 | 挂生产可达生命周期钩子（`@PostConstruct`/`LifeCycleSupport.doStart` 或 job 注册链）；周期载体遵守池设施契约（timer 只触发、任务体 hop worker，[pool-facility-hardening.md](pool-facility-hardening.md) §3.1.1）+ R1-01 绝缘覆盖；回归测试必须断言调度器真实调用清理体（防 C7-005 空壳） |
| B create/管理路径顺带清扫（惰性） | 写路径天然周期性触达 + 短 TTL 临时行 | `NopCredentialOauthStateStore.cleanupExpired()` create 前调用 | 键不复访即残留——不得用于永续账本主形态；可与 A 组合 |
| C BizModel purge action（人工） | 补充运维通道 | tcc `cleanCompletedTransactions`（API 形态先例；其零生产装配 = C7-005 反例） | **不得为机器速率写入面唯一形态**（人工速率差无解）；若落地须附真实调用链测试 + 生产可达性核对 |
| D close 桥接（生命周期配对） | 进程内注册表/连接账本（非 DB 表） | 5.6-W-01 修复形态 | 保留期参数不适用（裁定⑥①） |

### 3.4 R01 六成员逐单传导表

本批六成员与后续族成员按本表落地，不得模块内发明参数/形态。回归钉均要求：清理断言 + 保留断言（未到期/非终态/活行不误伤）+ fail-fast 包络断言（非法配置拒绝）+ 接线断言（清理体被生产链真实调用）。

| 成员 | 账本清单（清理谓词时间锚列显式落名） | 参数取值（§3.2 键） | 清理载体与形态 | 回归钉要求（特有面） |
|------|--------------------------------------|---------------------|----------------|----------------------|
| 1.6-C7-001（nop-retry 三账本表） | `nop_retry_record`：锚列 `updateTime`，谓词 `status=2`（COMPLETED 终态）；`nop_retry_attempt`：锚列 `startTime`（孤儿扫描/级联随父）；`nop_retry_dead_letter`：锚列 `createTime` | §3.2 nop-retry 行 | `RetryRecordStoreImpl` 清理方法 + `RetryEngineImpl` 生命周期链周期调度，形态 A。清理序：①COMPLETED record 有界拾取 → 级联删 attempt（`recordId IN` 父 sid 集）→ 删 record；②死信有界拾取 → 级联删孤儿 attempt（`recordId IN` 死信 recordId 集——sid 为 seq 生成不复用，删除安全；同时覆盖 OVERWRITE 重建与死信迁移两类孤儿源）→ 删死信；③幂等（重复触发无害） | COMPLETED+attempt 超期清理；孤儿 attempt 随死信保留期一并清理（不留 `deadLetter.record` 悬空后的永久孤儿）；未到期保留（PENDING/RETRYING/SUSPENDED 及未到期 COMPLETED 不误伤）；幂等；引擎调度链接线断言。保留语义注记：死信超期清除后 `retryFromDeadLetter` 重放通道随之关闭（owner doc 声明） |
| 1.7-C7-003（nop-batch 三表，**与成员 1 同设计同参**） | `nop_batch_task`：锚列 `endTime`，谓词 `taskStatus∈{30,40,50,60}`（COMPLETED/FAILED/CANCELLED/KILLED 终态）；`nop_batch_task_var`：级联随任务行（`batchTaskId IN`），无独立 TTL；`nop_batch_record_result`：锚列 `createTime`（独立 TTL 通道）+ 终态级联（`batchTaskId IN`）双通道；`nop_batch_file`：级联随任务行（`batchTaskId IN`），显式声明无独立 TTL | §3.2 nop-batch 行（与成员 1 同参同形） | nop-batch-dao 清理组件（`LifeCycleSupport.doStart` + `timer.executeOn(worker).scheduleWithFixedDelay` + `doStop` 取消，照抄 sys 先例形态），`app-batch-dao.beans.xml` 装配，形态 A。清理序：终态任务有界拾取 → record_result/task_var/file 按 `batchTaskId IN` 级联 → 删任务行；record_result 独立 createTime TTL 通道覆盖「任务行非终态期」的增长面 | UUID taskKey 终态任务行超期清理；record_result 按 TTL 批删；task_var/file 随任务行级联；未到期+非终态（CREATED/RUNNING/SUSPENDED）保留；幂等；装配链接线断言。owner doc 声明与 nop-retry 互引（批次对齐声明 L314） |
| 5.1-MA-01（nop-auth 六族，ask-first 已授权[边界=六族]） | `nop_auth_session`：锚列 `lastAccessTime`（主锚）+ `logoutTime` 判别（已终结行）；mfa_challenge / sms_code / email_code / rate_limit_counter / login_attempt 五族：锚列 `expireAt`，谓词 `expireAt < now − retention` | §3.2 nop-auth 行 | 六族统一批量清扫（credential `cleanupExpired` 语句形态），载体=周期低频清理（形态 A——登录机器速率写入面）；session 行删除即令牌载荷（accessToken/refreshToken/cacheData）清除 | C10-001/004/005 回归钉同 diff（session 过期行清理+未过期保留[可注入时钟]、五族批量清扫参数化逐一、`countUnexpired` 口径不变钉）；trusted-device 文件零产品 diff 下既有测试仍绿 |
| ↳ 边界注记行（非本批成员面） | trusted-device 过期行清扫第七面（`MfaTrustedDeviceManager`）——**R1 修订摘除**：授权边界=六族，本面归 Deferred But Adjudicated，successor=R3-01；落地时按本表形态 A + `nop.auth.ledger` 键对齐 | — | — | — |
| 5.5-W-02（nop-metadata 六表+第七面） | 六表锚列均 `createTime`：`nop_meta_quality_result` / `nop_meta_quality_score` / `nop_meta_catalog` / `nop_meta_profiling_result` / `nop_meta_reconciliation_result` / `nop_meta_model_changed_event`（mediumtext 前后双快照） | §3.2 nop-metadata 行（七面同窗；event 表最长保留窗口=统一保留期 30 天） | 形态 A 周期清理（deleteByQuery 先例语句形态），机器速率写入面（cron+auto-score）禁止形态 C 为唯一载体；ORM 模型零触碰（不新增 cascadeDelete/索引，不改「历史事实语义」注释——保留窗内该语义不破坏） | 六表逐面超期清理；窗口内行保留（历史事实语义窗内不破坏）；幂等；G2 契约核对（差异条款②） |
| ↳ 第七面具名行（qualityBreachApproval 告警工作流实例——**已落地**） | 载体 = nop-wf 侧 `NopWfInstance`（PK `wfId`，非 nop-metadata 自有表）。**通道 1 终态 TTL**：`status ∈ {COMPLETED=40, EXPIRED=50, FAILED=60, KILLED=70}` 且锚列 `endTime < now − retention`（endTime = 终态迁移时刻，写入后不被业务回改的账龄事实列）。**通道 2 CREATED 零步残量**：`status = CREATED(0)` 且 `createTime < now − retention` 且零 step 记录（拾取后经 `NopWfStepInstance` 按 wfId 集存在性复核）。子表（statusHistories/steps/outputs/globalVars，ORM `cascadeDelete`）随父行 wfId IN 集传递清扫，不独立选锚、不独立 TTL；`works`（无 cascadeDelete）不在锚集内 | §3.2 nop-metadata 行三键（与六表同窗，零新键） | 形态 A 周期清理——`NopMetaLedgerCleaner` face 扩展第七通道（与六表同一调度链、同一失败包络、同一参数集；有界拾取 + IN 批删，谓词幂等）；拾取与删除均为 nop-wf 既有 dao 使用面（与告警创建半程自清理共用同一删除原语族），nop-wf 零产品 diff | 四件套：终态超期实例清理 + 子表随父行 wfId 集级联；活跃实例保留（ACTIVATED/SUSPENDED/WAITING/CREATED 已含 step 与未到期终态行零误删——**活跃审批红线：审批中告警实例不可 TTL 误删**）+ CREATED 零步超期残量被通道 2 清除；fail-fast 包络（共享三键校验）；接线断言（第七通道经生产生命周期链真实调度，防 C7-005 空壳）+ 幂等复跑。**通道 2 判据修订性扩展声明（M1）**：CREATED 零步残量通道字面越出本表「实例终态后方可逐出」判据，定性为该判据的**修订性扩展**（判据原文不改，扩展论证三重）：(a) CREATED 行仅在 start 入口段落库后立即进入推进（全仓无「先建后启、长期停留 CREATED」的合法消费形态；创建半程失败已由告警创建入口的事务内自清理先行闭合，通道 2 只承载其失败残留与进程崩溃窗存量）；(b) 保留期 TTL 门 + 零步复核双重门使在途 start（毫秒级）误删不可达；(c) 活跃审批实例（CREATED 已含 step / ACTIVATED / SUSPENDED / WAITING）仍在谓词外——活跃实例红线实质不受损 |
| 5.6-W-01（nop-duckdb 双载体，差异条款①） | 进程内：`OPEN_FILES` 注册表 + connections 账本——无 DB 锚列（生命周期配对面） | 保留期/TTL/行数上限**不适用**（裁定⑥①投影） | 形态 D close 桥接：连接 close 时锁内 untracked + `releaseOpen`；`track` 保留原对象使 `engine.close()` 幂等兜底零回退 | refs 随连接 close 归零；connections 账本随包装 close 清理；**过期指纹误拒面同批闭合**（同路径跨 engine 重开不再 100% 假阳性 bizFatal——端到端回归钉，组件级单测不替代）；既有 close 语义零回退 |
| 5.2-MA-04（nop-sys 双面） | `nop_sys_lock`：锚列 `expireAt`，谓词 `expireAt < now − retention`（覆盖崩溃持有者+无人争用僵尸行）；`nop_sys_change_log`：锚列 `createTime`，留存语义见裁定⑤ | §3.2 nop-sys 行（lock 7 天 / changelog 90 天） | 照抄 `cleanupExpiredEvents` 形态：retention 配置 + 周期清理（doStart 接线真实生效），形态 A；ChangeLog `saveDirectly` 批量化（写放大面随批，族内联动面） | 僵尸锁行清理；持有中/未到期活锁保留（不误伤）；change log 按留存语义清理（`0` 配置下零删除）；批量化后列行数与内容等价；接线断言 |
| R2-07 ⑦ 1.1-C1-006（nop-stream 分配账本，`ClusterRegistry`——与 `SideOutputRegistry`/Debezium REGISTRY 同名异实体） | `nop_stream_task_assignment`：锚列 `assigned_at`（写入后不被业务回改）；谓词 = **组合谓词**——主谓词 `job_id ∈ 进程内 OpsJobManager terminalAt 终态白名单`（终态集 CANCEL/DRAIN/SUSPEND，锚 `OpsJobManager:302` 单点；平台无耐久终态 job 表）+ `assigned_at` 账龄 TTL 兜底谓词（覆盖崩溃重启后白名单丢失的孤儿行）；均 anti 排除当前活跃 job 集；零 DDL（`CREATE TABLE IF NOT EXISTS` 不加列）；进程内 `taskAssignmentHistory` 随终态 job 按 jobId 定界摘除（非 TTL 面） | §3.2 通用键投影 face token=`assignment`（`nop.stream.ledger.assignment.*`，face 段惯例同 `nop.sys.ledger.lock/changelog` 行），缺省值以 SSOT §3.1 为准，零参数发明 | 终态迁移路径接线 `removeTaskAssignment`（InMemory+JDBC 双实现）+ JDBC 形态 A 周期清理；活跃 job 追加语义零改动（attempt 历史单调保留）；anti-join 活协调器表定界被拒（failover 重注册窗口误删面） | 终态清理 + 活跃追加零改动对照（+1 pin 与单调 pin 保持绿，防 naive 全清回退）；崩溃孤儿行 TTL 兜底清理；幂等；形态 A 调度真实调用断言（裁定②防空壳） |
| R2-07 ⑧ 1.1-C8-003（nop-stream `nop_stream_msg_queue`，INSERT-only 队列账本） | 锚列 = 自增主键 `id`（INSERT 序即账龄序）；谓词 = **已派发位点定界主谓词**（`id ≤ 进程内 per-topic 已派发最大 id`——仅认派发循环真实推进的 lastSeenId，订阅启动 MAX(id) bootstrap 声明跳过语义不作清理位点）+ retention TTL 双谓词取交——queue 语义为消费后可删，TTL 纯账龄独立删除被拒（消费停滞期误删未消费行 = 数据丢失）；重启后位点归零重建前清理保守暂停为可接受语义（滞后清理不误删）；未消费积压增长面归 WI6 生产者门控（backpressure），不归 retention | §3.2 通用键投影 face token=`msgqueue`（`nop.stream.ledger.msgqueue.*`），缺省值以 SSOT §3.1 为准，零参数发明 | 形态 A 周期清理（既有 test-only 清理升格为生产周期载体）；随批补后端 `ChannelBacklogProbe`（backlog 口径=未消费行数，与保留位点同源） | 已派发行清理 + 未派发行零误删（消费停滞注入对照 + 重启暂停窗口断言）；幂等；形态 A 调度真实调用断言；probe 门控真实可达断言 |
| R2-08 ④ 1.2-C3-002（nop-stream 2PC epoch 提交台账，双载体：JDBC `stream_epoch_ledger_v2` + file manifest，裁定详情 = [stream-connectors-concurrency-and-envelope.md](stream-connectors-concurrency-and-envelope.md) §3.4） | JDBC 面：锚列 `committed_at`（commit 成功写入的账龄事实列，写入即成功终态无在途谓词），谓词 = `committed_at < now − retention`；**保留窗 ≥ 可重提交 epoch 窗口**（防误删未到期行踩坏恢复幂等守卫，§3.1 第 4 项同源）；manifest 面：账龄谓词 = 键编码 epoch 号账龄（`epoch(键) < 当前提交 epoch − 保留 epoch 窗口`），保留 epoch 窗口 = `ceil(retention-days × 86400000ms ÷ checkpointIntervalMs)`，向上取整防少留（checkpointInterval 为既有 CheckpointConfig 值经装配传入，非第八参数） | §3.2 通用键投影 face token=`epochledger`（`nop.stream.ledger.epochledger.*`），缺省值以 SSOT §3.1 为准，零参数发明；键常量按「归属清理载体所在模块」分落两兄弟模块（jdbc 三键 / file 零键直读） | JDBC = 形态 A 周期清理（挂模块生命周期链，delete-batch-size 有界批删）；manifest = 形态 B commit 顺带裁剪（每 epoch commit 已在 AR-14 锁内全量 rewrite，裁剪并入该 rewrite；非 DB 表形态 A 无宿主） | 多 epoch 写入后台账行数/manifest 键数有界；未到期保留（保留窗 ≥ 可重提交窗口）；幂等；JDBC 形态 A 调度真实调用断言（防空壳）；abort 不触台账语义零回退；恢复幂等钉（`TestJdbcTwoPhaseCommitSinkDeep`/restoredKeys）零回退 |
| R2-12 ⑫ 1.6-C7-005（nop-tcc 两表，§3.6 承接条款落地；裁定详情 = [retry-tcc-cas-compensation-and-ledger-hygiene.md](retry-tcc-cas-compensation-and-ledger-hygiene.md) §3.8） | `nop_tcc_record`：锚列 `beginTime`（写入后不被业务回改），谓词 = 既有 finished 终态集（`getFinishedStatus`）+ `beginTime < now − retention`；`nop_tcc_branch_record`：级联随父（`txnId IN`），无独立 TTL；**耗尽行前置终态化**——每轮清理先 `UPDATE status=KILLED WHERE status<CONFIRM_SUCCESS AND retryTimes>=maxRetryCount AND expireTime<now` 再入 finished 谓词（终态字段=复用 status 列零新列；C7-006 观测/干预本体归 R4-10 消费同一语义） | §3.2 通用键投影 face 缺省单面（`nop.tcc.ledger.retention-days|30` / `cleanup-interval|1h` / `delete-batch-size|1000`），缺省值以 SSOT §3.1 为准，零参数发明 | 形态 A 周期清理（绝缘载体 + 生命周期钩子，对齐 nop-retry `startLedgerCleanup` 同构；既有 `removeCompletedRecords(retention, true)` 清理体 + 分支随父级联；`cleanCompletedTransactions` 零生产装配 = C7-005 反例由本行接线闭合） | 耗尽行 KILLED 终态化 + 超期清理；未到期/非终态/在途行零误伤；幂等；形态 A 调度真实调用断言（防 C7-005 空壳先例回归）；索引生效断言（expireTime+status+retryTimes 复合 + beginTime，orm PA 批内申报，详见裁定⑧ §3.8） |

### 3.5 差异条款两则

1. **5.6-W-01 生命周期配对投影（勘误 7 落设计）**：W-01 是 close 权与 release 权无桥接的生命周期配对破损，非 DB 表周期增长。「保留期/TTL/行数上限」对其不直接适用；族级纪律「上限+逐出+fail-fast 包络声明」的投影 = 修复后双载体账本随连接生命周期闭合（泄漏载体消除，无「保留」需求）+ `engine.close()` 幂等兜底保留 + 包装 close 失败路径显式可见。**禁止把 DB 表清理形态（deleteByQuery/retention 配置）套用到 nop-duckdb。**
2. **5.5 G2 retention×reJudge 同表契约注记（勘误 8 落设计）**：quality_result 的保留窗（`nop.metadata.ledger.retention-days`）即 reJudge（W-28，R3-05）可复用的历史窗上界。R3-05 到达时：若 reJudge 复用视野 ≤ 统一保留窗，零参数改动；若需更长视野，按本文档修订参数（上调统一键或引入 face 级键），**不得在 nop-metadata 内另设第二套保留语义**。W-28 本体不进 R1-07 批。

### 3.6 家族 SSOT 声明与参数修订纪律

- 本文档为 R01 全族（19 成员/12 单元）保留策略的**唯一事实源**。非本批 13 项族成员所属批次（R2-07、R2-08、R2-12、R3-05、R4-03、R4-07、R4-10、R4-29）落地时按 §3.4 对应行/同类行对齐，禁止模块内发明保留参数、触发形态或键名。
- R2-12（1.6-C7-005 tcc 两表清理接线）已按 §3.4 成员行落地（plan 2515，裁定⑧承接本节声明——成员行见 [retry-tcc-cas-compensation-and-ledger-hygiene.md](retry-tcc-cas-compensation-and-ledger-hygiene.md) §3.8）；R4-29（4.5-MA-01）与 R4-07（1.4-C7-004）按同构行对齐。
- **参数修订只走本文档一处定稿**：任何模块调整保留期/周期/批删上限/锚列，先修订本文档对应行（附理由），再改模块配置缺省值；Configs 常量缺省值与本文档不一致即为缺陷。
- 本设计与 [periodic-task-insulation.md](periodic-task-insulation.md)（周期链绝缘）、[pool-facility-hardening.md](pool-facility-hardening.md) §3.1.1（timer/worker 契约）共享周期清理链的设施契约：形态 A 的周期载体必须同时满足两者的接线约束。

## 四、拒绝了什么

1. **拒绝各模块自定保留参数/触发形态**——R01 族级纪律一处定稿（family-clustering R01 行 + 4.5 口径），分叉即第二事实源。
2. **拒绝 DB 账本表数值行数上限（row cap）**——「满即删」会删除未到期行（正确性危害）；保留期 TTL 已闭合无界面（§3.1 第 4 项）。
3. **拒绝人工 purge action 作为机器速率写入面唯一形态**——人工清理速率对 cron/自动提交写入速率差无解，正是 5.5-W-02 的缺陷逻辑。
4. **拒绝 ORM 模型变更**（新增 cascadeDelete/索引/修改注释）——R1-07 零触碰声明；retention 全部走代码+配置+既有实体删除语句。
5. **拒绝第七面搭车**——trusted-device 过期清扫未获授权不静默扩面（→R3-01）；工作流实例 TTL 误删违反 nop-wf 所有权与活跃状态正确性（裁定④）。
6. **拒绝 `saveProcessed` upsert 评估入批**——其真实动机是 C7-015 键语义场景；复合主键已在模型层，重复插入响亮失败非静默累积（plan 2502 勘误 6）。

## 五、与已有设计的关系

- [periodic-task-insulation.md](periodic-task-insulation.md)：形态 A 周期链的绝缘契约（失败可见、重臂、拒绝终态）——清理链失败包络（§3.1 第 5 项）直接消费该语义。
- [pool-facility-hardening.md](pool-facility-hardening.md)：§3.1.1 调度槽/执行体契约——形态 A 的 `timer.executeOn(worker)` 载体约束。
- [cache-invalidation-single-point.md](cache-invalidation-single-point.md)：零共享代码位（无关联）。
- owner doc 落点：`docs-for-ai/03-modules/nop-retry.md`、`nop-batch.md`（保留期声明，二者互引批次对齐）、`nop-auth.md`、`nop-metadata.md`、`nop-duckdb.md`、`nop-sys.md`（留存语义声明）——各分片落地时随批更新。
