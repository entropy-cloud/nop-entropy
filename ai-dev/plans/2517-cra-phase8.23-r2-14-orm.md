# 2517 CRA Phase 8.23 — R2-14 2.4 ORM 并发批（13 项：会话注册表原子化 + 模型发布可见性 + session 互斥 + 事务注册协议原子性 + 连接生命周期锁纪律 + 实体缓存 read-repair 世代化 + 抢占 NULL 退化 + drainTo 出事务）

> Design Review: R2 agent_4d71d66b @ 2026-10-30（第 1 轮 FAIL[1 Blocker/2 Major/6 Minor]：Blocker=裁定⑥预设 ICache 原子 compute 能力不存在[插入-only]且补齐触未申报 nop-commons→改判 nop-orm 模块内世代 fence[CHM<String,AtomicLong>+版本化 value+fence CAS，生命周期弱引用+cap 100k]；Major-1 世代公式二义→三具名变量 readGen/fenceGen/valueGen+唯一公式 readGen==fenceGen；Major-2 裁定⑤投递机制与 BaseContext 降级路径不符→具名 context.execute+三句降级/吞弃/vert.x 语义；Minor×6 闭合[restore 原子 put/混合态单向化/⑬包络含 flushSession+跨裁定依赖/⑧synchronization 回退/§4.6 同对象注/⑫]——协调方核验后 PASS）
> Design Review: R2 终轮 agent_4d71d66b @ 2026-10-30（第 2 轮 FAIL[1 Major/2 Minor]：Major=fence 生命周期 gen-0 起点使「fence 身份重置×gen-0 快照碰撞」可放行旧世代回填→per-persister 单调分配器非零起点[协调方修入]；Minor-2′ 降级/吞弃两处机制精确化、Minor-3′ compute 体异常 safeClose 语义——评审明示修复面局部无裁量点，协调方逐处断言落盘后核验 PASS））
> Plan Status: active
> Draft Review: R1 agent_8db17b00 @ 2026-10-30（FAIL[1 Major/3 Minor]：Major-1 C2-001 写路径引用区间误指含 checkContext 方法族→勘误 6 订正 :495-644；Minor×3[cra-index 行号 :213-225 订正/C2-006 P2 并入标注/log 行数精度]——协调方修入后转 active；20+ 组基线亲读全吻合、结构完备、PA 判定 13/13 零 orm 核验成立）
> Draft Review: 待独立 draft review（本 plan 为起草产出，Status=draft；独立子 agent draft review 通过后转 active）
> Last Reviewed: 2026-10-30
> Source: roadmap 工作项 8.23（R2-14，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L165，Status=todo[本 plan 立项时回写为「todo（plan 2517 drafted，待 draft review）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-14 行（L88 全文：finding ID 清单 13 项[2.4-C1-001；2.4-C1-003；2.4-C1-015；2.4-C2-001；2.4-C2-002；2.4-C2-004；2.4-C2-005；2.4-C2-013；2.4-C2-015；2.4-C2-017；2.4-C3-001；2.4-C3-005；2.4-C6-001] / 模块与验证命令 nop-persistence `./mvnw test -pl nop-persistence -am` / Owner Doc=`docs-for-ai/02-core-guides/model-first-development.md`；`docs-for-ai/04-reference/source-anchors.md` / PA=**ORM 模型结构 plan-first**（若修复触 `model/*.orm.xml` 须 plan-first 声明；纯代码修复作零 orm 触碰声明） / 依赖「无前置（可并行）；C2-013/C2-015 自并单点（R10）；C2-004 与 C1-027（R4-19）共享设计注记」/ exit criteria 增补：**IContext 注册表 get-or-create 单修复（ContextAttrs）；ormModel volatile 化；session 全生命周期互斥；事务注册协议原子性（半个成功消除）；实体缓存 read-repair 世代化；drainTo 等待窗口出事务**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 2.4 审计目录 `ai-dev/audits/2026-10/2026-10-18-1521-deep-audit-nop-persistence-rw-concurrency/`（C1-shared-mutable-state.md C1-001 :66 起 / C1-003 :132 起 / C1-015 :536 起；C2-lock-sync.md C2-001 :54 / C2-002 :84 / C2-004 :139 / C2-005 :168 / C2-013 :562 / C2-015 :626 / C2-017 :686；C3-async-chains.md C3-001 :13 / C3-005 :132；C6-resource-lifecycle.md C6-001 :42；C1-round2.md / C2-round2.md / C3-round2.md / C6-round2.md 复核维持行；merge-adjudication.md §一 M1[C1-023→C1-001]/M3[C3-006→C6-001] :35-37 + 组 1[C2-003→C1-001] :71-76 + 组 2[C2-006→C3-005] :79-84 + 组 3[C1-026→C2-017] :87-92 + N4[C2-005 独立+实现约束] :55 + N5[N6 切片注记] :56-57 + N8[C2-013↔C1-023 两层不混淆] :59 + 台账 :116-155；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` §二 WI 2.4 节 :350 起 + 小结行 :785[**2.4（17）**]；P1 台账 = cra-index `ai-dev/audits/cra-index.md` :213–225（勘误 6 订正）（live 2026-10-30 回读）13 行批次列=R2-14 全 open）；执行基线 @ `fb4c40fb5e`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.22 已 done，plan 2495–2516 均已完成或收口；本 Baseline 全部行号为该基线 live 回读值，2026-10-30 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2499（R1-04，completed——api-core 上下文传播/bindCancel 批：`thenOnContext`/`executeWithContext` 平台语义为本批 C2-001/C2-002/C3-001 触发前提（CL-401/CL-402 可达性根因），平台侧已收口，本批为 ORM 层守卫修复不代做平台层）；plan 2504（R2-01，completed——`ResourceRetirementQueue`/`LoadWaitGraph` 平台退役代际机制先例可引用）；plan 2507（R2-04，completed——2.5 写门租约与 `shardSelector` volatile 发布先例（source-anchors `ORM-MIG-001` 在案），C1-003 volatile 化同型先例）；plan 2514/2515/2516（completed——R2 批结构先例：PA 批内申报、三联环境裁定、N 前置改写、偏差三件套模式）；**下游**：R4-18（8.58，G45 前半 15 项，**软依赖本批**）与 R4-19（8.59，G45 后半 15 项，软依赖 8.58）——批末须回写就绪注记；**C1-016（R4-18）= 全单元唯一真并发回归钉，修复不得弱化**（本批零触碰其钉）；**C2-004 × C1-027（批次列=R4-18，勘误 3）共享「失效世代号」设计注记**：本批裁定为世代化 SSOT，R4-18 消费同一裁定不重建、两个 work item 不合并执行（merge-adjudication 台账 :142 复核确认）；**并行协同**：R2-15（8.24，2.5 peri 批）同模块 nop-persistence 并行在飞时共享验证通道（E3 快照分别留证，互不代跑）
> Protected Area 授权：批次 PA=**ORM 模型结构 plan-first（条件触发制）**。**本批预设全部 13 成员均为纯 Java 代码修复，零 `model/*.orm.xml` 触碰**——13 成员 PA 判定表逐一核实（见 Baseline「PA 判定表」节）：站点分布于 `nop-persistence/nop-orm`（C1-001/C1-003/C2-001/C2-002/C2-004/C3-001/C3-005）、`nop-persistence/nop-orm-model`（C1-015）、`nop-persistence/nop-dao`（C2-013/C2-015/C2-017）、`nop-persistence/nop-orm-data`（C2-005/C6-001），全部为 `src/main/java` 手写代码；nop-persistence 域内受影响五模块（nop-orm/nop-orm-model/nop-dao/nop-orm-data/nop-orm-eql[仅测试基线]）**无产品 orm 结构模型**（live find 核实：仅 `_vfs/` 下 defaults 模板与 `nop-orm-pdm` 测试 fixture，均非本批触碰面）。**头部零 orm 触碰声明成立**；**触发条件条款**：任一裁定的修复方案若演进为触碰 orm 源模型（如 C2-004 世代化若采「实体表新增世代列」形态），须先在本 plan PA 行 + 设计文档 + draft review 中完成 plan-first 申报（沿 2514/2515/2516 先例「模型结构变更须先裁定方案并在 plan 登记后方可采」口径）方可实施；预设分支不触。**批内第二 PA 申报条款（条件触发制）**：C1-001/C2-013 的「ContextAttrs 原子 get-or-create」若裁定为**平台级工具方法落 `nop-api-core`（`io.nop.api.core.*`）**，触发 AGENTS.md「nop-api-core plan-first」——预设 = 不新建平台工具类，直接在 `OrmSessionRegistry`/`TransactionRegistry` 各自模块内用 `context.getAttrs().computeIfAbsent(...)`（`BaseContext.getAttrs()` :191 已暴露 ConcurrentHashMap，live 在案）实现，零 nop-api-core 触碰；若 Phase 1 裁定确需 nop-api-core 落点，须在设计文档申报并列入 draft review 审查面。

## Purpose

关闭 R2-14 批的 13 项已确认缺陷（**全部 P1**，cra-index P1 台账 :213–225（勘误 6 订正） 批次列=R2-14 的 13 行，live 2026-10-30 回读全 open；本批 = WI 2.4 单元 P1 有效工单全集——17 条 P1 中 4 条为并入锚点[C1-023/C2-003→C1-001、C1-026→C2-017、C3-006→C6-001、C2-006→C3-005]，有效工单恰 13）。核心结果面有七：①**IContext 注册表原子化**（C1-001←C1-023/C2-003，exit criteria 增补第一段）——session/事务两个 context 注册表 get-or-create 三步竞态逐字节同构，单一平台级修复项：注册表双实例→双一级缓存/僵尸会话/事务表覆盖；修复=ContextAttrs 原子 get-or-create + 单槽 put/restore 原子化 + finally 无条件 close 兜底。②**模型发布可见性**（C1-003 + C1-015，增补第二段）——`ormModel` 非 volatile 使 reloadModel 公开运维 API 后旧模型无限期可见 + clearCache 两步混合态 + 每请求 setOrmInterceptor 热写；`OrmComponentModel` 先发布引用后填充使组件列映射热路径竞态（实体状态按错误 propId 错位写入）；修复=volatile 化 + 顺序修正 +（备选）解析期预计算。③**session 全生命周期互斥**（C2-001 + C2-002 + C3-001，增补第三段）——flush 重入守卫为静默跳过而非锁、`runInNewSessionAsync` 在异步完成线程 flush/close、上游永不完成时会话永不关闭：修复=互斥守卫（owner-thread/锁）+ 收尾回 context 队列 + 兜底关闭路径。④**事务注册协议原子性**（C2-013 + C2-015，R10 自并单点，增补第四段）——查-建-放三步协议 + runWithoutTransaction/NOT_SUPPORTED 注销窗口 + subTransactions 裸 LinkedHashMap 组提交迭代竞态：提交成功误报 ERR_TXN_NOT_REGISTERED、REQUIRED 组语义退化、组提交丢写无感知回滚；修复=注册协议原子化（putIfAbsent/compute 语义）+ 组事务集合同规范（快照+守卫），半个成功消除。⑤**连接生命周期锁纪律**（C2-017←C1-026）——双取池连接泄漏 + eagerRelease 僵尸连接静默回滚丢写；修复=事务对象为 monitor 串行化五触点或 CAS 状态机，null 早退显式化。⑥**实体缓存 read-repair 世代化**（C2-004，增补第五段）——迟到 put 覆盖并发 evict，陈旧值无界驻留；修复=世代号/版本围栏（与 C1-027 共享设计，R4-18 消费）。⑦**抢占与 drainTo**（C2-005 + C6-001←C3-006，增补第六段）——tryClaim CAS 谓词 NULL 退化无条件 UPDATE 任务重复投递；drainTo REQUIRES_NEW 横跨无限等待窗口（K≥8 消费者池饥饿）；修复=NULL 谓词显式化（或 fail-fast 前置校验）+ 事务边界内移单轮询短事务（tryClaim+loadItems 同事务实现约束）。批末 roadmap 8.23 → done，cra-index P1 台账 13 行收敛 + C10 收敛注记，R4-18/R4-19 软依赖就绪注记（含 C2-004 世代化裁定指向与 C1-016 钉零弱化义务转达）。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-14 = 13 项成员（**全 P1**，无 P2 搭车；WI 2.4 单元 P1 终局 17 条 = 本批 13 主 ID + 4 并入锚点，`C10-test-protection.md` :27 口径）；cra-index P1 台账 :213–225（勘误 6 订正）（live 2026-10-30 回读）13 行批次列均=R2-14、状态均 open；并入锚点 C1-023/C2-003/C1-026/C3-006/C2-006 无独立台账行（cra-index grep 零命中确认，合并裁定台账双锚点在 merge-adjudication.md :35-37/:71-92）；roadmap 8.23 行 Status=todo。前置：**无前置（可并行）**——roadmap 8.1–8.22 已 done，基线门禁满足。
- **同单元批外面登记（Non-Goal 零触碰）**：WI 2.4 P2×31/P3×23 全部归 R4-18/R4-19（G45 前后各 15 项，cra-index P2-G45 行 L429）与 P2-G46（C1-021 条化，L430 deferred）；P2-G45 行注记「C1-016 全单元唯一有钉条目」「C2-016=C10-017 现状容忍钉（修复须先改 testMultipleCommit 行为钉）」——均在本批范围外，本批修复 diff 不得弱化 C1-016 钉（`LazyLoadOrmModel` topo 面，与本批 C1-015 同族不同位点，零触碰）。CL-401/CL-402 平台可达性根因已随 R1-04（plan 2499）收口，本批为 ORM 层守卫承接。
- **吸收成员（随主条目同 diff，不单列工单；merge-adjudication §一+组裁定）**：C1-001←C1-023（M1：TransactionRegistry get-or-create 逐字节同构）+ C2-003（组 1：单槽 put/restore 原子性与栈序——工单含两个修复子项 (a) ContextAttrs 原子 get-or-create (b) 槽交换原子化+finally 无条件 close 兜底）；C2-017←C1-026（组 3：connection 字段可见性/双取原子性面为超集，误标勘误「C1-026=check-then-act 原子性非单纯可见性」登记在案）；C3-005←C2-006（组 2：dirty 先复位后执行同一事实的 C2 维度投影，P1 超集含 flushAsync finally actionMap.clear 失败清队列面）；C6-001←C3-006（M3：同方法 drainTo、同根因 REQUIRES_NEW 跨等待窗口，重叠 >80% 单工单；queryTimeout 子项挂同一工单验收清单）。**C2-005 与 C6-001/C3-006 为不同缺陷独立立案**（N4 终裁），但「事务边界内移」实现必须保证 tryClaim+loadItems 同一短事务——该实现约束写入 C6-001 工单验收项。cra-index 回填时以主 ID 收敛、吸收成员括注。
- **族登记（family-clustering R10 = F2 半个成功族 chain14/chain37 对偶，:54 行）**：本批成员 C2-013/C2-015 入 R10 族（「自并：组提交丢写与事务注册同协议原子性根因」——族行原文），批内**自并单点**：两成员共享 AbstractTransaction/TransactionTemplateImpl/TransactionRegistry 设施的「协议原子性」根因，Phase 1 收敛为单一裁定（§裁定⑤）+ 同一变更集承载，台账各 ID 独立收敛不双计；族修复口径「归一/补偿/声明三选一」对齐（族内 1.6-C6-003 已随 plan 2515 落地、5.5-W-12/5.6-W-03 归各自批，零重复立项）。C1-003/C1-015 入「写冻结模型/惰性初始化」邻接族（C1-016/C1-020 同族归 R4-18，位点不重叠）。
- **C10 验收前置入批集（映射 SSOT = `inventory-p2-c10.md` 小结行 :785「2.4（17）」+ `C10-test-protection.md` P1 小结 :110 与逐行 :92-108；随本体同 diff 落地[E2]）**：
  - **全量收口门（本批承全）**：C10-001（C1-001←C1-023/C2-003——真/替身 context 双线程并发首触 instance() 同实例断言 + put/restore 交错序列测试形态，C10-test-protection :176 建议）、C10-002（C2-002/C2-003/C3-001 异步入口族）、C10-006（C2-005——邻接钉 `TestDaoEntityBlockingSourceClaim#testClaimConflictSkipsItem` 只护非 NULL 冲突面，NULL 退化面结构性不可见[session mock 屏蔽 WHERE 谓词生成]，回归钉须穿透谓词生成层）、C10-007（C2-013/C2-015——并发注册交错/REQUIRED 组语义崩塌/提交后误报三后果面全无钉）、C10-008（C2-017/C1-026——双取泄漏与 doCommit null 静默 no-op 无钉）、C10-010（C6-001/C3-006——等待窗口内活跃连接数不增长 + 每 tick 短事务边界均无钉）。
  - **半边收口门（本批承本批成员面，其余成员归 R4-18/R4-19 随其本体）**：C10-003（C1-003 面——reloadModel/clearCache 路径 G3 零覆盖族，本批补 C1-003 回归钉；C1-004/C2-007/C1-008(c)/C6-003 面归 R4-18/R4-19）、C10-004（C2-001 面——跨线程并发 flush 静默跳过→事务带未刷写提交无钉，本批补；C1-002 面归 R4-18）、C10-005（C2-004 面——G4 全局缓存失效面零覆盖族，本批补迟到回填 vs 并发 evict 交错钉；C1-027/C1-010 面归 R4-18）。
  - **钉保护注记**：C10-009（C3-005/C2-006——**钉错相位**：现有 `TestFlushFailure#testFlushFailureClearsFlushVisiting` 注入相位在装载队列（dirty=false 之前）恰好绕开缺陷窗口，制造「重试语义已覆盖」错觉（C10-test-protection :106 亲验）——本批修复测试**必须钉 batchActionQueue.flush 失败相位**（dirty=false 之后的 DML 批失败→重试 flush），且不得删除/弱化既有装载相位钉；新钉与旧钉并存，语义对照留痕）。C1-016 唯一真并发钉（R4-18）零触碰；C10-017 现状容忍钉 testMultipleCommit（C2-016 面，R4-19）零触碰。
- **nop-persistence 域先序批状态（live 形态登记）**：R2-01（plan 2504）已 landed——2.1 kernel 退役代际/配置发布（含 2.5-C6-005 双轨联动面）；R1-04（plan 2499）已 landed——api-core bindCancel/上下文传播；2.5 peri 批（R2-15，plan 未立项）与本批并行不冲突（站点不同：peri 审计目录 `2026-10-19-0624-deep-audit-nop-persistence-peri-concurrency/`，finding 前缀 2.5 与本批 2.4 零交叉）。本批为上述面上的残留 P1 修复，全部零回退。
- **平台设施与测试资产盘点（live 实读 @ `fb4c40fb5e`）**：
  - **先例范式可引用不重开**：`BaseContext.getAttrs()`（:191，ConcurrentHashMap 暴露）为 ContextAttrs 原子 get-or-create 载体；`ContextualizedRegistry`（nop-commons，map 为 CHM）单步原子安全、缺陷在复合操作层；plan 2507 `shardSelector` volatile 发布 + 读回确认先例（source-anchors ORM-MIG-001）；`TestLazyLoadOrmModel`（nop-orm-model，**全单元唯一真并发测试**，C1-016 钉所在——本批 C1-015 回归钉形态先例）；`TestFlushFailure#testFlushFailureClearsFlushVisiting`（装载相位钉，C10-009 对照面）；`TestDaoEntityBlockingSourceClaim`×2 / `TestTransactionTemplateAsync`×4 / `TestJdbcTransactionAutoCommitRestore`×2 / `TestDefaultTransactionManager`×8（各邻接钉，C10-test-protection §2 :52-59 全表）；`TestGlobalCache#testGlobalCache`/`testCollectionGlobalCacheEvictedAfterChange`（单线程 evict 正确性钉，C10-005 半边扩展落点）；plan 2499 `thenOnContext` 出口泵语义（C3-001 收尾兜底消费面）。
  - **owner doc 现状**（2026-10-30 亲读）：`docs-for-ai/02-core-guides/model-first-development.md`（603 行）——相关节：「直接 SQL 的使用边界」:545-575（REQUIRES_NEW 独立事务/绕过会话缓存契约句 :564-565，与 C6-001 事务边界内移契约句邻接）、「detach 与 evict 的会话契约（R1.5 / P1-2.1-001）」:576-585（**既有会话契约节先例，本批 session 生命周期契约句落点邻接**）；**无 session/事务并发互斥契约节、无全局缓存世代语义声明、无模型热刷新可见性声明、无 IBlockingSource 抢占契约节**——裁定①②③④⑤⑥⑦ owner doc 补节主落点。`docs-for-ai/04-reference/source-anchors.md`（427 行）——已有 `ORM-MIG-001`（含 shardSelector volatile 先例）、`TNT-004`（EntityPersisterImpl）、`DQL-002`（IOrmTemplate）锚点；本批补锚点落点。
- **受影响模块聚合形态**：`nop-persistence` 为 Maven 聚合器，E3 命令 `./mvnw test -pl nop-persistence -am` 触达 nop-orm/nop-orm-model/nop-dao/nop-orm-data/nop-orm-eql 及上游链（nop-kernel/nop-api-core/nop-core/nop-commons/nop-auth 等）。

### 对齐纪律显式条款

- **C2-013/C2-015 × R10 自并单点**：两成员同协议原子性根因（family-clustering :54 族行原文「自并」），Phase 1 收敛为单一裁定 + 同一变更集（TransactionRegistry 协议层 + AbstractTransaction 组事务集合层同规范落地：注册原子化 putIfAbsent/compute 语义 + 组集合快照/守卫 + cleanup 身份比对消除伪 ERR_TXN_NOT_REGISTERED）；台账两 ID 独立收敛、cra-index 各行括注「同变更集（R10 自并）」。**锁序约束（merge-adjudication N8 + C2-013 建议(2)）**：若引入 per-context 事务门闩，锁只保护注册表结构变更，不得覆盖 task 执行与 commit（listener 外部回调），遵守锁序图补锁裁定。
- **C2-004 × C1-027 共享设计注记（世代号 SSOT 单侧声明）**：C1-027（缓存失效竞态世代号面）批次列=**R4-18**（batch-proposal L133 成员清单第 15 项 + 其 exit criteria 列「C1-027 与 R2-14 C2-004 共享设计」）——R2-14 行括注「（R4-19）」为笔误（勘误 3）。本批 Phase 1 裁定⑥为「失效世代号」设计 SSOT：世代化形态（版本化 value/ICache.compute CAS/时间戳围栏，一次定夺）成文于设计文档，R4-18 到达时**消费同一裁定不重建**；两 work item 不合并执行（merge-adjudication :142 复核确认——C2-004=read-repair 顺序竞态、C1-027=失效判据世代面，修复互不替代）。
- **C2-001/C2-002/C3-001 × 平台触发前提（R1-04 已收口面零代做）**：同上下文并发可达性根因（`executeBlocking(ordered=false)` javadoc、`executeWithContext` 仅 warn、`thenOnContext` 完成线程内联）为平台语义（CL-401/CL-402），平台侧统一已随 plan 2499 落地；本批在 ORM 层落地 session 级守卫（互斥/收尾队列化/兜底关闭），不修改 nop-api-core 平台语义，平台上收申报触发条件 = 若裁定需要改 `BaseContext`/`ContextProvider` 语义则须另行申报（本批预设零触碰）。
- **C6-001 × C2-005 同文件实现约束（N4）**：drainTo 事务边界内移后，`loadItems`（含 `tryClaim`）必须仍在**同一短事务**内执行（逐轮 runInTransaction 包络），否则抢占 CAS 失去事务串行化前提——该约束为裁定⑦验收项；C2-005 的 NULL 谓词修复与事务边界内移互不替代、可同变更集落地。
- **跨单元同号防混**（inventory-p2-c10 :875）：C6-001（4.3 P0 已修 / 4.4 P1 / 5.4 P2 / 5.6 P1 同号异站）——本批成员恒带单元前缀 2.4；C1-001 与其他单元同名异站（3.1-MA-13/MA-14 等）无关；2.5 peri 批成员（2.5-C1-001 等）与 2.4 零交叉。

### 13 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `fb4c40fb5e` 2026-10-30 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级；吸收成员） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 2.4-C1-001（P1；←C1-023/C2-003 双并入；C10-001；增补段①） | `OrmSessionRegistry.java` + `OrmTemplateImpl.java`（nop-orm `impl`）；`TransactionRegistry.java`（nop-dao `txn.impl`）；`OrmSessionImpl.java`（close 兜底面） | `instance()` get→new→setAttribute 三步无同步 `OrmSessionRegistry.java:20-27`；同构 `TransactionRegistry.java:23-30`；单槽 put/restore + `s == session` 关闭门 `OrmTemplateImpl.java:213-232`（runInNewSession）/:244-271（runInNewSessionAsync，:262-269 whenComplete 恢复）；`registerSession/unregisterSession` :273-280；`OrmSessionImpl.close` 幂等守卫 :768-770 | context 注册表 get-or-create 三步竞态 + 单槽 put/restore 交错：注册表双实例→同请求双一级缓存/僵尸会话复用、单槽互踩→会话泄漏（refCount 失衡）/身份交换跨会话混写；TransactionRegistry 逐字节同构（事务表覆盖） | 裁定①（C10-001 随同 diff；增补段①「IContext 注册表 get-or-create 单修复（ContextAttrs）」）：两个修复子项一次定夺——(a) `instance()` 改 `context.getAttrs().computeIfAbsent(CONTEXT_KEY, k -> new ...)`（预设，零 nop-api-core 触碰；若裁定抽平台工具落 nop-api-core 须批内 PA 申报）；(b) put/restore 原子化：恢复用 `remove(key, value)` 两参 + 不匹配仍无条件 `close()`（close 幂等 :768-770 在案，消除跳过-关闭分支）；`skip-session-close-when-not-current-session` info 日志升级告警可观测；runInNewSessionAsync 恢复面与 #4 C3-001 收尾兜底同变更集对齐 |
| 2 | 2.4-C1-003（P1；C10-003 半边；增补段②前半） | `DefaultOrmModelProvider.java` + `TenantAwareOrmModelProvider.java` + `LoadedOrmModel.java`（nop-orm `factory`） | `ormModel` 非 volatile `DefaultOrmModelProvider.java:17`；`getOrmModel` 每请求 `setOrmInterceptor` 热写 :36-43；`clearCache` 两步（赋值 :47 + interceptorCache.clear :48）混合态；`TenantAwareOrmModelProvider` 同族 :16/:40-42（clearCacheForTenant :46 邻接）；`LoadedOrmModel.ormInterceptor` 非 volatile :33（写点 :58-60） | 模型提供器 ormModel 非 volatile：reloadModel（公开运维 API）后旧模型无限期可见（无 HB 边）、clearCache 两步间读者可见混合态（旧 interceptor 写入新模型）、每请求对共享 ormInterceptor 读改写竞态 | 裁定②（C10-003 半边随同 diff；增补段②「ormModel volatile 化」）：两 provider `ormModel` 加 `volatile`（`StaticOrmModelProvider` final 为同族正确反证）；clearCache 顺序一次定夺（interceptorCache.clear() 先于赋值，或整体同步块）；`setOrmInterceptor` 改引用变化时才写（`if (ormModel.getOrmInterceptor() != interceptor)`，热写降偶发写）；`LoadedOrmModel.ormInterceptor` volatile 连带；TenantAware 同 diff 同修；模型代发布协议切片（C2-007/C1-004，R4-18/R4-19）只登记接缝不代做 |
| 3 | 2.4-C1-015（P1；写冻结模型族关联[C1-016/C1-020 归 R4-18 不代做]；增补段②后半） | `OrmComponentModel.java`（nop-orm-model `model`） | 双字段非同步惰性初始化 `colPropIds`/`propIds` :26-27；`getColumnPropIdMap` 先发布后填充（:45 引用赋值先于填充循环）:43-50；`getColumnPropIds` :106-117（:115 引用最后赋值但无 volatile/final，JMM 元素写可见性缺口）；读侧热路径 `DynamicOrmEntity.java:246`/`OrmBatchLoadQueueImpl.java:140,475`/`OrmEntityHelper.java:101,197`（旁证） | OrmComponentModel 先发布引用后填充：并发首触组件列映射返回空/部分 Map 或 int[] 元素 stale——组件属性未绑定/实体状态按错误 propId 错位写入；每次模型重装载即重置窗口 | 裁定③（增补段②配套；C10-003 半边关联钉随同 diff）：两分支一次定夺——(a) 预设=解析期预计算（`OrmEntityModelInitializer`/`initProps()` 完成后一次性赋值，与 `colsByPropId` 同法，运行期只读）；(b) 备选=保留惰性但 `volatile` + 局部填充最后赋引用（消除先发布后填充）；修复不得弱化 `TestOrmComponentModel#testGetColumnPropIdsFollowsDeclarationOrder` 列序钉（单线程列序契约保留）；C1-016 同族邻接零触碰 |
| 4 | 2.4-C2-001（P1；C10-004 半边；增补段③） | `OrmSessionImpl.java`（nop-orm `session`） | 全字段零互斥 `readOnly/closed/dirty` :102-105 + `flusher` :108；flush 重入门静默跳过 `:183-185`（`flusher != null → return`，非锁不等待不报错）；`this.flusher = new CascadeFlusher` :208 两线程可同越 null 检查；写路径无 checkContext（save/update/delete/lock 族 :273-322 各入口仅 checkValid+checkNotReadOnly）；`flushImmediately` :1008-1030 同型；`SessionBatchActionQueue.queues` :33-41 惰性创建覆盖丢弃；`OrmSessionEntityCache` 裸 HashMap/visiting 门 :42,114,253-284（同族守卫清单 C2-001 失效面枚举） | session 全生命周期零互斥：同上下文并发下 (a) T2 静默跳过 flush→事务带未刷写提交丢更新，(b) 双 CascadeFlusher 并发遍历同一裸 HashMap→CME/重复 DML；唯一守卫为非原子 boolean，跨线程误用无 fail-fast | 裁定④（C10-004 半边随同 diff；增补段③「session 全生命周期互斥」）：一次定夺——(a) 预设=flush 入口 `ReentrantLock`（可重入承载 CascadeFlusher 回调重入语义）替代静默跳过，或 owner-thread 记录 + 异线程访问 fail-fast（复用 `ERR_ORM_SESSION_NOT_RUN_ON_CONTEXT` 错误码族）；(b) 写路径入口补 checkContext 对齐读路径（守卫对称）；(c) 同族守卫清单（`SessionBatchActionQueue.queues` computeIfAbsent 化等）按失效面枚举逐项落位或登记边界；与 #5/#7（C2-002/C3-001）共享「会话互斥」同一设计文档裁定 |
| 5 | 2.4-C2-002（P1；C10-002；增补段③） | `OrmTemplateImpl.java`（nop-orm `impl`）；`OrmSessionImpl.java`（close 面） | `runInNewSessionAsync` whenComplete 在完成线程执行 flush/registry 恢复/close `OrmTemplateImpl.java:255-270`（thenOnContext 经 context.execute 可在任意完成线程内联）；`OrmSessionImpl.close` `if (closed) return` 非原子 :768-791 | 异步会话收尾（flush/close/registry 恢复）在任意完成线程执行，与同 context 在用线程无同步：close 与在用并发→对已关闭 session 静默半操作/CME/动作丢失 | 裁定⑤（C10-002 随同 diff；与裁定④⑦同一设计文档配套）：收尾线程纪律一次定夺——(a) 预设=whenComplete 仅投递「会话收尾」任务回 context 队列（对齐 thenOnContext 语义）+ 收尾与裁定④互斥守卫协同；(b) 备选=session 引用计数/owner-thread 校验后再 flush/close；`skip-session-close` 升级告警；`registerSession/unregisterSession` 公共 API 任意线程覆盖面随裁定① registry 原子化承接 |
| 6 | 2.4-C2-004（P1；C1-027（R4-18）共享设计注记；C10-005 半边；增补段⑤） | `EntityPersisterImpl.java` + `CollectionPersisterImpl.java`（nop-orm `persister`） | read-repair：miss→读库→回填 `EntityPersisterImpl.java:228-243`（loadAsync thenApply :239-242）、:175-183（batchLoadAsync）、:595-624（getToLoad 同构）；回填点 `updateGlobalCache` :538-545（`putAsync`/`removeAsync`）；evict 点 :466/:487/:499→`evictGlobalCache` :553-569（onAfterCommit removeAsync）；集合路径 `CollectionPersisterImpl.java:96-100`（同步 put :229）/`_batchLoad` :148-154/`flushCollectionChange` :172-183 evict；`lock()` :259-261 持行锁相对安全（排除项） | 全局实体/集合缓存 read-repair 复合竞态：miss 回填（跨多 await 点 check-then-act）迟到 put 覆盖并发 evict——缓存=v1 DB=v2 永不自愈（写侧不触发则无界驻留）；useGlobalCache 实体级 opt-in + CFG 默认 true 上可达 | 裁定⑥（C10-005 半边随同 diff；增补段⑤「实体缓存 read-repair 世代化」；C1-027 共享设计 SSOT 见对齐纪律节）：世代化形态一次定夺——(a) 预设=世代号/版本围栏（回填 put 携带读库世代，低于当前世代放弃；`ICache` compute 原子能力或版本化 value CAS）；(b) 备选=evict 侧提交后延迟双删；`lock()` 路径排除项声明保留；集合路径同构同 diff；世代号设计成文后 R4-18（C1-027）消费注记义务（Phase 4） |
| 7 | 2.4-C2-005（P1；独立缺陷[N4 终裁]；C10-006；增补段⑥前半） | `DaoEntityBlockingSource.java`（nop-orm-data `source`）；`GenSqlHelper.java`（nop-orm `sql`，只读旁证——appendExampleFilter 按已 init 属性生成 WHERE） | `tryClaim` `DaoEntityBlockingSource.java:246-263`（注释 :224-226 声明原子抢占契约；example 谓词 :257-259 仅收非 null 旧值）；`currentAcquireValues` :265-285（三字段逐个跳过 null）；`loadItems` :216-237（claim-conflict 日志 :233 退化场景永不出现）；消费入口族 take :122-126/poll :129-144/drainTo :147-154/takeMulti :157-159 全汇聚 | 抢占 CAS 谓词 NULL 退化：新任务行抢占字段初始 NULL 不进 WHERE→UPDATE 退化为按 id 无条件更新→两消费者均匹配成功，同一任务重复投递（声明契约失效，静默无日志） | 裁定⑦前半（C10-006 随同 diff；增补段⑥「抢占 NULL 退化消除」）：一次定夺——(a) 预设=NULL 旧值显式生成 `col IS NULL` 谓词（SqlBuilder isNull 支持，使 NULL 初始态同样参与 CAS）；(b) 备选=`oldValues.isEmpty()` 时拒绝抢占+告警（fail-fast）；或声明契约改强制业务非 NULL 初始化 + `init()` fail-fast 校验实体模型 default——三形态 Phase 1 定夺；回归钉穿透谓词生成层（session mock 屏蔽面是缺陷结构性不可见根因，C10-006 注记）；与 #13（C6-001）同变更集实现约束见对齐纪律节 |
| 8 | 2.4-C2-013（P1；R10 自并单点与 #9 同变更集；C10-007；增补段④） | `TransactionTemplateImpl.java` + `DefaultTransactionManager.java`（nop-dao `txn.impl`）；`TransactionRegistry.java`（裁定①同位点外层） | `getTxnState` 查-建-放三步 `:125-167`（:157-164 主分支无互斥）；`runWithoutTransaction` 注销-执行-重挂窗口 :170-181（:175/:179）；`NOT_SUPPORTED` :291-299（:295 unregister）；`REQUIRES_NEW` :285-290 + `createNewTransaction` :338/:352（put 覆盖 prevTxn）；`cleanupTransaction` :406-438（:415 remove 失败→:416 伪 ERR_TXN_NOT_REGISTERED）；`DefaultTransactionManager.getRegisteredTransaction/registerTransaction/unregisterTransaction` :158-182 | 事务注册协议无原子性：同 context 并行异步分支下 REQUIRED 组语义静默退化为独立事务、提交成功后误报 ERR_TXN_NOT_REGISTERED（触发上游重试与数据重复）、已打开事务对象被并发 close——全平台 @Transactional 公共底座 | 裁定⑧（C10-007 随同 diff；增补段④「事务注册协议原子性（半个成功消除）」；R10 自并单点与裁定⑨同变更集）：(a) 注册协议原子化：get+new+register 收敛为 registry `compute`/`putIfAbsent` 语义（失败方 close 自己新建事务采用胜者）；(b) runWithoutTransaction/NOT_SUPPORTED 注销窗口改挂起标记或 per-context 事务门闩（**锁只护注册表结构变更，不覆盖 task 执行与 commit**——锁序约束）；(c) cleanup remove 失败先身份比对，不符降级告警非抛错（伪 ERR_TXN_NOT_REGISTERED 消除）；与裁定①共享 ContextAttrs 外层原子化（两层不混淆：N8 确认实例创建层 vs 协议层独立） |
| 9 | 2.4-C2-015（P1；R10 自并单点与 #8 同变更集；C10-007；增补段④） | `AbstractTransaction.java`（nop-dao `txn.impl`） | `subTransactions` 裸 LinkedHashMap 惰性初始化 :60/:105-111（addSubTransaction）；`removeSubTransaction` :114-118；组提交/回滚迭代无快照 `commitSubTransactions` :365-372（async :351-363）/`rollbackSubTransactions` :194-210（async :212-221）/`closeSubTransactions` :437-443/`openSubTransactions` :445-451；`isSubTxnRollbackOnly` :146-154；对照 listeners 已 COW（:54-56 注释自证作者认知并发）——同一状态机两集合部件同步纪律不一致 | subTransactions 组提交迭代期并发注册静默不提交：写数据在 close 时无感知回滚丢失（多数据源 txnGroupMap 设计场景 + 并行分支，丢写最重后果） | 裁定⑨（C10-007 随同 diff；与裁定⑧同变更集[R10]）：一次定夺——(a) 预设=惰性 init `synchronized(this)` + 全部迭代点改持锁快照 `new ArrayList<>(subTransactions.values())` + 快照外迟到子事务显式拒绝（fail-fast 守卫，杜绝静默丢弃）；(b) 备选=类契约显式声明「注册期/提交期单线程」+ 提交开始后 addSubTransaction 抛 fail-fast；全消费点族（a-g 失效面枚举）单点修复闭合 |
| 10 | 2.4-C2-017（P1；←C1-026 并入[组 3]；C10-008；增补段④配套） | `JdbcTransaction.java`（nop-dao `jdbc.txn`） | `connection` 单字段无同步 :35；`getConnection` 双取 `:61-75`（:62 null 检查 + :64 dataSource.get() 竞态）；`doRollback` :78-89（null 早退）；`doCommit` :92-100（:93 null 早退静默 no-op）；`restoreAutoCommit` :102-111；`releaseConnection` check-then-close-then-null 三步 :113-120；`doClose` :122-125；`getConnection0` :53-57（单线程安全排除项） | JdbcTransaction 连接生命周期无互斥：并行分支双取池连接其一脱离管辖泄漏至池超时；eagerRelease 提交后并发重取僵尸连接其写数据 close 时静默回滚；doCommit null 早退完全无痕 | 裁定⑩（C10-008 随同 diff）：一次定夺——(a) 预设=事务对象为 monitor 串行化 `getConnection/doCommit/doRollback/releaseConnection/doClose` 五触点（临界区内含池获取；**禁止锁内再取 registry/CHM 全局锁**，锁序裁定）；(b) 备选=并入 CAS 状态机（COMMITTING/CLOSED 态 getConnection 抛 ERR_TXN_NOT_IN_TRANSACTION 显式失败，替代 :93 静默 no-op）；最少 `doCommit` null 早退补 warn；C1-026 吸收面（check-then-act 原子性）随同一修复闭合 |
| 11 | 2.4-C3-001（P1；C10-002；增补段③） | `OrmTemplateImpl.java`（nop-orm `impl`，同 #1/#5 站点族） | `runInNewSessionAsync` 清理唯一挂在 whenComplete `:255-270`（:261 registry.put 恢复、:263-266 close 判定）——上游 future 永不完成（模块内 5 根无 orTimeout）→ session 永不 flush/close + context 注册表滞留新会话顶替 oldSession；context 关闭钩子仅清 attributes 不补关（BaseContext.close :315-327 旁证） | runInNewSessionAsync 上游永不完成时 session 永不关闭且注册表滞留：长生命周期 context 泄漏无限期、后续 runInSession 复用脏会话读到陈旧一级缓存 | 裁定⑪（C10-002 随同 diff；与裁定⑤同一设计文档配套）：兜底关闭路径一次定夺——(a) 预设=注册表 put 时在 context 生命周期（onClose 钩子/context 属性伴生登记）挂 session 反向兜底登记，future 完成路径保持现状；(b) 备选=返回 future 超时兜底（平台设施复用优先）；单一修复点覆盖全部异步会话入口族（runInSessionAsync 委托分支等 F3 枚举 7 类）；泄漏量守卫指标（每挂起一次一泄漏）随裁定成文 |
| 12 | 2.4-C3-005（P1；←C2-006 并入[组 2]；C10-009[钉错相位注记]；增补段④配套半个成功族） | `OrmSessionImpl.java`（nop-orm `session`）；`BatchActionQueueImpl.java`（nop-orm `persister`） | flush 顺序错误：`dirty=false` :211 + `cache.clearDirty()` :212 先于 `batchActionQueue.flush()` :214——DML 批失败后重试 flush 在 :189 `if (!dirty)` 短路 no-op，修改永不落库（非事务路径半个成功残留）；`BatchActionQueueImpl.flushAsync` finally `actionMap.clear()` :234 失败路径同样清空动作队列；对照 `flushImmediately` :1008-1030 oldDirty 保守恢复正确写法（修复模板） | flush 先声明干净后执行 SQL：批 DML 失败（约束冲突）后捕获-重试编排静默丢数据（脏标记已清、动作队列已清、丧失重写能力）；事务路径受 rollback 保护故 P1 非 P0 | 裁定⑫（C10-009 随同 diff，**钉错相位注记**：新钉必须注入 batchActionQueue.flush 失败相位[dirty=false 之后]，既有装载相位钉保留不弱化，语义对照留痕）：dirty/clearDirty 移至 `batchActionQueue.flush()` 成功之后（对齐 flushImmediately oldDirty 模板，预设）或失败保守恢复 `dirty=true`（一次定夺）；`actionMap.clear()` 失败路径面（C2-006 吸收增量）同 diff 核对（notifyUnsubmittedFailure 已覆盖未提交部分，「已提交失败」部分回调面一并核对登记）；saveDirectly/updateDirectly/deleteDirectly 承继面登记不扩批 |
| 13 | 2.4-C6-001（P1；←C3-006 并入[M3 单工单，queryTimeout 子项挂验收]；C10-010；增补段⑥后半；N4 实现约束） | `DaoEntityBlockingSource.java`（nop-orm-data `source`，同 #7 站点文件）；`JdbcTransaction.java`（doOpen 即取连接面，旁证） | `take()` :122-126→`takeMulti` :157-159（maxWait=-1 无限等待）→`drainTo` **`@Transactional(REQUIRES_NEW)`** :172 横跨整个等待窗口：`waitUntil` :196 轮询体内 loadItems 每 ~100ms 一次 SELECT（:178 interval 防御在案）；事务 open 即取连接（JdbcTransaction.doOpen→getConnection0）+ setAutoCommit(false) + 抢占行锁保持至有数据/中断；poll(timeout) :139-144 有界（同病随参数归族）；poll() :129-136 与 drainTo(c,max) :147-154 窗口纳秒级安全（排除项） | drainTo 无限等待路径在 REQUIRES_NEW 事务内轮询：连接/事务快照/抢占行锁持锁窗口无上界且出现在「队列空」正常稳态——K≥8 消费者先于生产者启动即全应用 DB 饥饿（maxSize 默认 8）；PG 长事务阻断 vacuum | 裁定⑬（C10-010 随同 diff；增补段⑥「drainTo 等待窗口出事务」）：事务边界内移一次定夺——(a) 预设=方法级 REQUIRES_NEW 摘除，`waitUntil` 循环体外置（`runWithoutTransaction` 形态），循环体内逐轮 `runInTransaction` 短事务包络 loadItems+tryClaim（**N4 实现约束：抢占 CAS 与轮询读必须同一短事务**）；(b) 备选=每轮独立短事务+数据到达合并返回；「最大空闲持连时长」守卫指标（IPoolMetrics 复用）随裁定成文；queryTimeout 子项（C3-006 吸收面）挂本工单验收清单；@Transactional 拦截器装配前提声明保留（未装配则降级 P3 口径不改判级） |

### PA 判定表（13 成员逐一，orm 触碰与否；live 2026-10-30 核实）

| # | Finding | 站点模块 | 站点形态 | 触 `model/*.orm.xml`？ | 判定依据 |
|---|---|---|---|---|---|
| 1 | C1-001 | nop-orm/nop-dao main java | 注册表/模板类 | 否 | 纯 Java 复合操作修复 |
| 2 | C1-003 | nop-orm main java | provider 字段 | 否 | volatile + 顺序修正 |
| 3 | C1-015 | nop-orm-model main java | 模型类派生字段 | 否 | 预计算或 volatile（不改模型 XML） |
| 4 | C2-001 | nop-orm main java | session 内部 | 否 | 锁/守卫 |
| 5 | C2-002 | nop-orm main java | 模板收尾 | 否 | 线程纪律 |
| 6 | C2-004 | nop-orm main java | persister 缓存围栏 | 否 | **预设世代号在 value/ICache 层实现；若演进为「实体表新增世代列」须 PA plan-first 批内申报（触发条件条款）** |
| 7 | C2-005 | nop-orm-data main java | example 谓词构造 | 否 | 谓词/fail-fast |
| 8 | C2-013 | nop-dao main java | 事务注册协议 | 否 | 协议原子化 |
| 9 | C2-015 | nop-dao main java | 组事务集合 | 否 | 快照+守卫 |
| 10 | C2-017 | nop-dao main java | 连接生命周期 | 否 | monitor/CAS 状态机 |
| 11 | C3-001 | nop-orm main java | 异步收尾兜底 | 否 | context 钩子登记 |
| 12 | C3-005 | nop-orm main java | flush 顺序 | 否 | 顺序修正 |
| 13 | C6-001 | nop-orm-data main java | 事务边界 | 否 | 边界内移 |

**结论：头部零 orm 触碰声明成立**（全部 13 成员 = `src/main/java` 纯代码修复；nop-persistence 受影响五模块无产品 orm 结构模型，live find 核实）。触发条件条款见头部 PA 行（C2-004 世代列形态 / ContextAttrs 平台工具落 nop-api-core 形态两处条件申报点）。

### Baseline 勘误（输入不一致登记）

1. **行号核对（审计→live @ `fb4c40fb5e`，漂移主因 = 审计基线 abccc80dde 后 8.1–8.22 修复批对 nop-persistence 零产品触碰[除 R2-01/R2-04 的 2.5 面]，漂移均为审计自引粒度差 ±1~3；机制面零实质漂移——全部 13 缺陷机制 live 复核仍成立）**：OrmSessionRegistry :20-27（审计 :19-27，类声明行差 1）；TransactionRegistry :23-30（审计 :22-30）；OrmTemplateImpl runInNewSession live :213-232（审计 :213-231）、runInNewSessionAsync live :244-271（审计 :244-270，whenComplete 恢复体 :262-269）、runInSession :204-212、registerSession/unregisterSession :273-280 ✓；OrmSessionImpl 字段 :102-108 ✓、flush 门 :183-185（审计 :182-186 ±粒度）、flusher 赋值 :208 ✓、dirty=false :211/clearDirty :212/batchActionQueue.flush :214 ✓、checkContext :229-232（审计 :229-233）、close :768-770 ✓、flushImmediately :1008-1030（审计 :1013-1031 ±粒度）；DefaultOrmModelProvider :17/:36-43/:45-49 ✓（审计 :36-49/:44-49 ±粒度）；TenantAwareOrmModelProvider :16/:40-42 ✓（clearCacheForTenant :46 审计未单列，非漂移）；LoadedOrmModel :33/:58-60 ✓；OrmComponentModel :26-27/:43-50/:106-117 ✓（审计 :43-51/:106-118 ±1）；EntityPersisterImpl loadFromGlobalCache live :526（审计 :537 引的是 updateGlobalCache :538，粒度差）、updateGlobalCache :538-545、evictGlobalCache :553-569、loadAsync 回填 :228-243 ✓、batchLoad :175-183 ✓、lock :259-261 ✓；CollectionPersisterImpl :96-100/:148-154/:172-183/:217-229 ✓；DaoEntityBlockingSource loadItems :216-237、tryClaim :246-263（审计 :216-261 合并粒度）、currentAcquireValues :265-285（审计 :263-281 ±2）、drainTo :172-214 ✓（REQUIRES_NEW :172、waitUntil :196）、take :122-126/takeMulti :157-159 ✓；TransactionTemplateImpl getTxnState :125-167 ✓、runWithoutTransaction :170-181 ✓、NOT_SUPPORTED :291-299 ✓、REQUIRES_NEW :285-290/:338/:352 ✓、cleanup :406-438 ✓；DefaultTransactionManager :158-182 ✓；AbstractTransaction :60/:105-111/:114-118/:194-210/:212-221/:351-372/:437-451 ✓；JdbcTransaction :35/:53-75/:78-100/:102-111/:113-125 ✓；BatchActionQueueImpl flushAsync :145/actionMap.clear :234 ✓；`BaseContext.getAttrs()` :191 在案（审计引 :296-308 为 executeBlocking javadoc，非漂移）。
2. **cra-index 行号基线**：本 plan 引用的 P1 台账 :213–225（勘误 6 订正） 为 2026-10-30 live 回读值（awk 相对定位 82-94 + 表头偏移推算）；cra-index 台账若在执行前被其他批回填致行号漂移，以「批次列=R2-14 + finding ID」定位为准并登记。并入锚点（C1-023/C2-003/C1-026/C3-006/C2-006）无独立 P1 台账行（grep 零命中确认）——cra-index 回填时仅 13 主行收敛 + 吸收成员括注，无并入行消号动作。
3. **C1-027 批次归属勘误**：batch-proposal R2-14 行括注「C2-004 与 C1-027（**R4-19**）共享设计注记」，但 C1-027 在 R4-18 成员清单（L133 第 15 项）且 R4-18 exit criteria 列明「C1-027 与 R2-14 C2-004 共享设计」、cra-index P2-G45 行 G45 拆分「前半 R4-18」含 C1-027——**以 R4-18 为准**；共享设计注记义务本身不变（世代号 SSOT 本批定、R4-18 消费）。
4. **C10 映射半边口径**：`inventory-p2-c10.md` :785 小结行 2.4（17）为全量映射；本批仅承载 13 主 ID 对应的 C10 面（C10-001/002/006/007/008/010 全量 + 003/004/005 半边），R4-18/R4-19 成员面（C1-004/C2-007/C1-008(c)/C1-002/C1-027/C1-010 等）随其本体批落地——半边拆分以本节清单为准，Phase 4 台账收敛注记按半边口径书写。
5. **环境项预登记（批前既有）**：`./mvnw test -pl nop-persistence -am` 触达上游模块链（nop-kernel/nop-api-core/nop-auth/nop-stream 等）——上游环境抖动（含 nop-auth MFA flake、debezium fork timeout、nop-task-core 逐出测试负载间歇误红[watch 归 2514 owner]、surefire fork 启动崩溃类，沿 plan 2512–2516 已知面）按「stash 基线同红 / solo 绿 / 恢复复跑全绿」三联裁定处理并留痕，不得静默重跑或跳过。
6. **勘误补登（draft review R1 Major-1/Minor-2，2026-10-30）**：① C2-001 写路径区间——初稿误引 :273-322（该区间 getBatchLoadQueue/flushBatchLoadQueue/contains/evict/evictAll/clear 全部含 checkContext），真实写入口族（仅 checkNotReadOnly）live :495-644，机制结论「写路径无 checkContext」成立但引用区间订正；② cra-index P1 台账 13 行 live 实为 **:213-225**（勘误 2 初稿 :211-L224 为 awk 推算值，标签改为「推算+live 复核订正」）。

## Goals

- Phase 1 设计裁定文档一次裁决 13 项裁定（裁定①–⑬，含三个吸收面与两组同变更集绑定）+ 测试缝（独立 draft review 通过），无临场发明空间：①ContextAttrs 注册表原子化（C1-001，增补段①）②ormModel volatile 化（C1-003，增补段②）③OrmComponentModel 预计算（C1-015）④session 互斥（C2-001，增补段③）⑤异步收尾线程纪律（C2-002）⑥read-repair 世代化（C2-004，增补段⑤，C1-027 SSOT）⑦抢占 NULL 谓词（C2-005，增补段⑥前半）⑧事务注册协议原子化（C2-013，增补段④）⑨组事务集合同规范（C2-015，与⑧同变更集[R10]）⑩连接生命周期锁纪律（C2-017）⑪异步会话兜底关闭（C3-001）⑫flush 顺序修正（C3-005，钉错相位注记）⑬drainTo 出事务（C6-001，增补段⑥后半，N4 约束）——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。
- 13 项成员全部修复并各带回归测试（rule 25）；C10 收口门（C10-001/002/006/007/008/010 全量 + 003/004/005 半边 + 009 钉错相位注记）随本体同 diff 落地（E2）。
- 关键工单先红后绿或变异/回退论证留痕（E2），红①–红⑬逐成员登记：红① 注册表双实例/单槽互踩红（双线程并发首触 + 交错序列测试——缺陷态双实例/漏 close 红→修复后同实例+无条件 close 绿）；红② reloadModel 旧模型可见红（写后并发读断言——缺陷态可读旧引用红→volatile 后新模型可见绿）；红③ 组件列映射空/部分 Map 红（并发首触——缺陷态部分填充红→修复后完整绿）；红④ 并发 flush 丢刷写红（T2 跳过 flush 后事务提交断言——缺陷态静默跳过红→修复后互斥绿）；红⑤ 完成线程收尾交错红（close 与在用并发断言——缺陷态半操作红→收尾队列化绿）；红⑥ 迟到回填覆盖 evict 红（miss 回填 vs 提交 evict 交错断言——缺陷态缓存=v1 红→世代围栏后放弃回填绿）；红⑦ NULL 抢占双投红（两消费者 NULL 初始行断言——缺陷态双双匹配红→IS NULL 谓词后单方匹配绿；穿透谓词生成层）；红⑧ 并发注册协议红（提交成功误报/组语义退化断言——缺陷态伪 ERR_TXN_NOT_REGISTERED 红→身份比对告警绿）；红⑨ 组提交迭代丢子事务红（迭代期并发注册断言——缺陷态静默不提交红→快照+显式拒绝绿）；红⑩ 双取连接泄漏红（并行 getConnection 连接身份断言——缺陷态双取红→单锁串行绿；doCommit null 早退显式化）；红⑪ 挂起泄漏红（上游永不完成 + context 关闭钩子断言——缺陷态 session 永不关红→兜底关闭绿）；红⑫ flush 失败重试 no-op 红（batchActionQueue.flush 相位失败注入——缺陷态重试短路红→顺序修正后重试生效绿；装载相位旧钉保持绿）；红⑬ 空队列持连红（take 空队列活跃连接数断言——缺陷态恒 1 持连红→空闲零持有绿）。
- 联调纪律显式守约（本批五条）：**R10 自并单点**（裁定⑧⑨同变更集）；**C2-004×C1-027 世代号 SSOT 单侧声明**（R4-18 消费不重建）；**C2-001/C2-002/C3-001×平台前提零代做**（R1-04 已收口面）；**C6-001×C2-005 N4 实现约束**（tryClaim+loadItems 同短事务）；**C1-016 钉零弱化**（R4-18 唯一真并发钉零触碰）。
- Owner doc 随批更新：`docs-for-ai/02-core-guides/model-first-development.md`（session/事务并发互斥契约节[邻接 :576 既有会话契约节]、模型热刷新可见性与组件预计算声明、全局缓存世代语义声明、IBlockingSource 抢占契约节[NULL 谓词与短事务约束]、drainTo 事务边界契约句[邻接 :564-565 既有 REQUIRES_NEW 契约]、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（ORM 并发锚点新增，按实际）。
- 下游就绪义务：批末向 R4-18（8.58）/R4-19（8.59）回写软依赖就绪注记（**R4-18 注记含 C2-004 世代号裁定指向[消费不重建] + C1-016 钉零弱化义务确认**；R4-19 注记含 C2-016 现状容忍钉边界确认）。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-18 成员**（G45 前半 15 项：2.4-C1-002/C1-004/C1-005/C1-006/C1-007/C1-008/C1-012/C1-013/C1-016/C1-017/C1-018/C1-019/C1-024/C1-025/C1-027）——特别地 **C1-027** 只随裁定⑥登记世代号 SSOT 消费接缝不代做；**C1-016** 唯一真并发钉零触碰；模型代发布协议切片（C2-007/C1-004 面级注记）只登记不代做。
- **不做 R4-19 成员**（G45 后半 15 项：2.4-C2-007/C2-009/C2-014/C2-016/C2-018/C3-002/C3-003/C3-004/C4-001/C4-002/C4-003/C5-001/C5-002/C5-005/C9-001）——特别地 C2-016 现状容忍钉 testMultipleCommit 零触碰；C2-009 补钉义务归 R4-19。
- 不做 2.5 peri 批成员（R2-15，11 项）——同模块不同审计单元，站点零交叉；本批 diff 不触碰 peri 审计目录登记的任何站点。
- 不做平台层改码：`nop-api-core`（`BaseContext`/`ContextProvider`/`thenOnContext` 语义）零触碰（R1-04 已收口面；ContextAttrs 平台工具申报条款仅条件触发）；nop-commons `ContextualizedRegistry` 原语零触碰（单步原子安全，缺陷在复合操作层）。
- 不做 CL 线索立案面（CL-401/402 平台根因已收口、CL-410~415 不立案展开）；不做 C5 内存边界族（findAll 物化族归 R4-19）；不做 C4 线程池族（归 R4-19）。
- 不触碰 `_` 前缀生成物；R4-18/R4-19/R2-15 批外成员本体；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-persistence/nop-orm/src/main/java`：`impl/OrmSessionRegistry`（裁定①）+ `impl/OrmTemplateImpl`（裁定①⑤⑪）+ `factory/DefaultOrmModelProvider`/`TenantAwareOrmModelProvider`/`LoadedOrmModel`（裁定②）+ `session/OrmSessionImpl`（裁定④⑫ + close 兜底面）+ `session/SessionBatchActionQueue`（裁定④同族守卫面）+ `persister/EntityPersisterImpl`/`CollectionPersisterImpl`（裁定⑥）+ `persister/BatchActionQueueImpl`（裁定⑫失败清队列面）+ 测试（C10-001/002/003 半/004 半/005 半/009）。
- `nop-persistence/nop-orm-model/src/main/java`：`model/OrmComponentModel`（裁定③）+ 测试（列序钉保持 + 并发钉新增）。
- `nop-persistence/nop-dao/src/main/java`：`txn/impl/TransactionRegistry`（裁定①( 同构面）+ `txn/impl/TransactionTemplateImpl`/`DefaultTransactionManager`（裁定⑧）+ `txn/impl/AbstractTransaction`（裁定⑨）+ `jdbc/txn/JdbcTransaction`（裁定⑩）+ 测试（C10-007/008）。
- `nop-persistence/nop-orm-data/src/main/java`：`source/DaoEntityBlockingSource`（裁定⑦⑬）+ 测试（C10-006/010）。
- 设计文档：`ai-dev/design/concurrency/orm-session-txn-atomicity-and-model-cache-generation.md`（新建；裁定①–⑬+测试缝+零回退钉+C1-027 SSOT 条款，最终状态写法）。Owner doc：`docs-for-ai/02-core-guides/model-first-development.md`、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；R4-18/R4-19/R2-15/dormant/P3 批次工单；nop-api-core/nop-commons 平台设施本体（条件申报条款除外）；`model/*.orm.xml`（预设零触碰，触发条件条款见头部 PA 行）；`_` 前缀生成物手改。

## Execution Plan

### Phase 1 - ORM 并发设施设计裁定文档 + 独立评审

Status: done
Targets: `ai-dev/design/concurrency/orm-session-txn-atomicity-and-model-cache-generation.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① C1-001 注册表原子化**：ContextAttrs get-or-create 形态（computeIfAbsent 于两 registry 内联[预设] vs 平台工具落 nop-api-core[须批内 PA 申报]）一次定夺；put/restore 原子化（remove(k,v) 两参 + 不匹配无条件 close 兜底）+ close 幂等依赖声明（:768-770）；skip-session-close 升级告警；C1-023/C2-003 吸收面双子项闭环声明；N8 两层不混淆注记（实例创建层 vs 协议层）
  - [x] **裁定② C1-003 模型发布可见性**：volatile 化点位（两 provider + LoadedOrmModel.ormInterceptor）；clearCache 顺序定夺（clear 先于赋值 vs 同步块）；setOrmInterceptor 变化时才写；TenantAware 同 diff；模型代发布协议切片接缝登记（C2-007/C1-004 归 R4-18/R4-19 不代做）
  - [x] **裁定③ C1-015 组件预计算**：解析期预计算（预设）vs volatile+最后赋引用（备选）一次定夺；列序钉零弱化声明；C1-016 同族零触碰声明
  - [x] **裁定④ C2-001 session 互斥**：ReentrantLock（预设，可重入承载 CascadeFlusher 回调）vs owner-thread fail-fast 一次定夺；写路径 checkContext 对称面；同族守卫清单逐项落位边界（SessionBatchActionQueue/OrmSessionEntityCache/getSessionRevVersion）；与裁定⑤⑪共享设计
  - [x] **裁定⑤+⑪ C2-002/C3-001 异步收尾**：收尾回 context 队列（预设）vs 引用计数（备选）；兜底关闭路径（context 生命周期登记[预设] vs future 超时）；异步入口族 F3 七类收敛声明；泄漏守卫指标
  - [x] **裁定⑥ C2-004 read-repair 世代化**：世代号形态（版本化 value/ICache.compute CAS/时间戳围栏）一次定夺；lock() 排除项声明；集合路径同 diff；**C1-027 SSOT 条款**（R4-18 消费不重建 + 两 work item 不合并）；世代列形态触发条件则 PA 申报声明
  - [x] **裁定⑦ C2-005 NULL 谓词**：IS NULL 谓词（预设）vs oldValues.isEmpty() fail-fast vs 契约强制非 NULL 初始化三形态定夺；回归钉穿透谓词生成层方案（mock 屏蔽面消解）
  - [x] **裁定⑧⑨ C2-013/C2-015 事务协议原子化（R10 自并单点，同变更集）**：registry compute/putIfAbsent 语义；runWithoutTransaction/NOT_SUPPORTED 挂起标记 vs per-context 门闩（锁序约束成文）；cleanup 身份比对消伪 ERR_TXN_NOT_REGISTERED；subTransactions synchronized+快照+迟到显式拒绝（预设）vs 单线程契约声明+fail-fast 守卫；半个成功消除验收口径
  - [x] **裁定⑩ C2-017 连接锁纪律**：五触点 monitor 串行化（预设）vs CAS 状态机；锁序裁定（禁锁内取全局锁）；doCommit null 早退 warn/显式失败；C1-026 吸收面闭合声明
  - [x] **裁定⑫ C3-005 flush 顺序**：dirty 移至成功后（预设，flushImmediately 模板）vs 失败保守恢复；C2-006 吸收面（actionMap.clear 失败路径 + 已提交失败回调面核对）；**C10-009 钉错相位注记**（新钉相位=batchActionQueue.flush 失败，旧装载钉保留）
  - [x] **裁定⑬ C6-001 drainTo 出事务**：REQUIRES_NEW 摘除+循环体 runInTransaction 短事务包络（预设，N4 约束成文）vs 备选；空闲持连守卫指标；queryTimeout 子项挂验收清单；@Transactional 装配前提声明
  - [x] **裁定⑭ 测试缝与零回退钉清单**：红①–红⑬ 测试形态逐条（含缺陷态红判据）；零回退钉 = `TestOrmComponentModel#testGetColumnPropIdsFollowsDeclarationOrder`（列序钉）、`TestFlushFailure#testFlushFailureClearsFlushVisiting`（装载相位钉，仅并存不弱化）、`TestDaoEntityBlockingSourceClaim` 两钉、`TestTransactionTemplate`×12/`TestTransactionTemplateAsync`×4/`TestDefaultTransactionManager`×8/`TestJdbcTransactionAutoCommitRestore`×2 邻接钉族、`TestLazyLoadOrmModel`（C1-016 唯一真并发钉，零触碰）、`TestGlobalCache` 单线程 evict 钉、`LazyLoadOrmModel` 锁纪律既有正确面（putEntityModel/checkTopoEntryReady 同锁约束零触碰）；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑭全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定①含 ContextAttrs 双形态与 nop-api-core 申报条款；裁定④⑤⑪含共享设计声明；裁定⑥含 C1-027 SSOT 条款与世代列 PA 触发声明；裁定⑧⑨含 R10 同变更集绑定与锁序约束；裁定⑫含钉错相位注记；裁定⑬含 N4 实现约束成文
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 13 成员实现 + 回归测试（按子域分组）

Status: done（2026-10-31，红绿证据 `_tmp/red-green-2517-*`，四模块 reactor 全绿 707 测试 0 失败，偏差登记见 Phase 2 尾部附录）
Targets: `nop-persistence/nop-{orm,orm-model,dao,orm-data}/src/`（各自 main + test 树）

- Item Types: `Fix`（13 项 P1 均为已确认 live defect，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [x] 组 A 会话注册与生命周期（nop-orm impl）：C1-001（裁定①，含 C1-023/C2-003 吸收面）+ C2-002（裁定⑤）+ C3-001（裁定⑪，与⑤同变更集）+ C10-001/C10-002
- [x] 组 B 会话互斥与 flush 状态机（nop-orm session/persister）：C2-001（裁定④）+ C3-005（裁定⑫，含 C2-006 吸收面）+ C10-004 半边/C10-009（钉错相位注记履行）
- [x] 组 C 模型发布（nop-orm factory + nop-orm-model）：C1-003（裁定②）+ C1-015（裁定③）+ C10-003 半边
- [x] 组 D 实体缓存（nop-orm persister）：C2-004（裁定⑥）+ C10-005 半边
- [x] 组 E 事务协议与连接（nop-dao txn）：C2-013（裁定⑧）+ C2-015（裁定⑨，与⑧同变更集[R10]）+ C2-017（裁定⑩，含 C1-026 吸收面）+ C10-007/C10-008
- [x] 组 F 抢占与 drainTo（nop-orm-data）：C2-005（裁定⑦）+ C6-001（裁定⑬，含 C3-006 吸收面，N4 同变更集约束）+ C10-006/C10-010
- [x] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：红①–红⑬ 按组 A–F 落位（Goals 节逐条判据）；红绿快照落 `_tmp/` 并登记
- [x] **端到端验证**（rule 22）：≥1 条全链并发场景（真/替身 context 上：并发首触 ORM 访问→session 注册→并发 flush 互斥→事务注册协议并发→组事务提交/回滚→连接释放，覆盖裁定①④⑧⑨⑩）；≥1 条异步/资源链（runInNewSessionAsync 挂起与完成两路径→收尾队列化/兜底关闭→drainTo 空闲零持连→有数据短事务抢占单方匹配，覆盖裁定⑤⑦⑪⑬；read-repair 世代交错场景覆盖裁定⑥）
- [x] **接线验证**（rule 23）：裁定① computeIfAbsent 真实被 OrmSessionRegistry/TransactionRegistry 双入口消费断言（非仅工具存在）；裁定④互斥真实在并发 flush 用例中生效断言（非仅锁字段存在）；裁定⑥世代围栏真实拦截迟到回填断言；裁定⑬短事务包络真实包裹 tryClaim 断言（事务计数/连接计数证据）
- [x] **无静默跳过**（rule 24）：裁定①不匹配分支显式 close（非跳过）；裁定④竞争分支显式等待/报错（非静默 return——保留语义处显式声明）；裁定⑧cleanup 身份不符降级告警留痕（非静默）；裁定⑨迟到子事务显式拒绝（非静默丢弃）；裁定⑩doCommit null 显式 warn/失败（非无痕 no-op）；新增公共分支无空方法体/TODO
- [x] **提交纪律**：设计文档先行单独提交；按组提交（A/B/C/D/E/F 六组，裁定⑧⑨同变更集、裁定⑤⑪同变更集、裁定⑦⑬同变更集[N4]）；C10 测试与对应本体同一 commit；组间无耦合时可独立提交序列
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 13 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 收口门（全量 6 + 半边 3 + 009 注记）同 diff 落地
- [x] 必红证据（E2）：红①–红⑬均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [x] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；唯一允许的钉改写 = C10-009 语义对照新增[旧钉保留]，登记在案）
- [ ] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [ ] `./mvnw test -pl nop-persistence -am` BUILD SUCCESS；上游环境抖动按 Baseline 勘误 5 三联裁定处理并留痕；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 偏差登记（2026-10-31，续作代理完成）

1. **裁定⑪落点形态**：设计写「context 关闭钩子（onClose 属性伴生）」，实现为静态 WeakHashMap(context→sessions) + `instance()`/收尾路径惰性清扫（阈值 100 WARN）——同样零 BaseContext 触碰、生命周期弱引用绑定，语义等效。
2. **裁定⑦双路径**：非 NULL 旧值维持既有 `updateByExample` 等值路径（零回退钉保护）；仅含 NULL 旧值走显式 `IS NULL` 条件 UPDATE；方言/表名不可解析的替身环境回落等值路径——两路径均条件 UPDATE，绝不退化为无条件 UPDATE（代码注释显式声明）。
3. **测试环境 flake**：reactor r1 上游 nop-core `TestResourceCacheInvalidationChain` 3 红（本批零触碰 nop-core）→ solo 复跑绿 → r2 恢复复跑 BUILD SUCCESS EXIT=0（三联裁定留痕 `_tmp/2517-phase2-final-test-r2.log`）。
4. **钉族唯一 diff** = `TestFlushFailure.java`（C10-009 允许的语义适配：DROP→RENAME 保数据 + 重试 flush，旧断言不变）。
5. **续作中断恢复**：前序子代理已落组 A–F main 全部代码与红①–④⑥⑪⑫/红⑦–⑩⑬留痕；缺口（红⑤测试+留痕、lint 空 catch、rule 22/23 E2E 证据）由续作代理补齐。

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-persistence 聚合全量测试；`docs-for-ai/02-core-guides/model-first-development.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2516 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-persistence -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径）；环境抖动按三联裁定留痕；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `_` 前缀生成物手改、不含 `model/*.orm.xml`（PA 零触碰声明兑现，或触发条件条款下已申报面逐项登记）、不含 nop-api-core/nop-commons 平台设施（条件申报面除外）、不含 R4-18/R4-19/R2-15 批外成员本体、C1-016 钉（TestLazyLoadOrmModel）与 C2-016 现状容忍钉（testMultipleCommit）零 diff 核对登记
- [ ] Owner doc 更新：`model-first-development.md`——新增「ORM 会话与事务并发契约（plan 2517 R2-14）」节（session 互斥语义/异步收尾线程纪律/注册表原子化、「以设计文档为准」归属声明）、模型热刷新可见性与组件预计算声明（裁定②③）、全局缓存世代语义声明（裁定⑥，含 C1-027 SSOT 指向）、IBlockingSource 抢占契约节（NULL 谓词 + 短事务约束，裁定⑦⑬）、drainTo 事务边界契约句（并入 :564-565 邻接节或独立小节）、事务注册协议原子性契约句（裁定⑧⑨，半个成功消除）、连接生命周期锁纪律句（裁定⑩）、flush 失败重试语义句（裁定⑫）、§源码锚点补行；`source-anchors.md` 新增锚点（按实际）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-persistence --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] model-first-development.md 落点 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（生成物/orm 模型/平台设施/R4-18/R4-19/R2-15 批外成员本体/C1-016 与 C2-016 钉面）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.23 行、cra-index P1 台账、R4-18/R4-19 就绪注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（注册表原子化/互斥/收尾队列化/世代围栏/协议原子化/锁纪律/NULL 谓词/短事务包络运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：R2-14 十三行状态收敛（open→fixed，注记 plan 2517；吸收成员[C1-023/C2-003/C1-026/C3-006/C2-006]随各主行括注）；C10 收敛注记（全量 6 + 半边 3 按勘误 4 口径 + 009 注记）；roadmap 8.23 行 Status → done；R4-18/R4-19（8.58/8.59）软依赖就绪注记（**R4-18 注记含 C2-004 世代号裁定指向 + C1-016 钉零弱化义务确认**；R4-19 注记含 C2-016 钉边界确认）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 十三行收敛 + 吸收成员括注 + C10 注记、roadmap 8.23 = done、R4-18/R4-19 就绪注记完成
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 13 项成员全部修复：C1-001（注册表原子化+槽兜底）、C1-003（ormModel volatile 化）、C1-015（组件列映射预计算/安全发布）、C2-001（session 全生命周期互斥）、C2-002（异步收尾线程纪律）、C2-004（read-repair 世代化）、C2-005（抢占 NULL 退化消除）、C2-013（事务注册协议原子化）、C2-015（组事务集合同规范）、C2-017（连接生命周期锁纪律）、C3-001（异步会话兜底关闭）、C3-005（flush 顺序修正）、C6-001（drainTo 等待窗口出事务）
- [ ] batch-proposal R2-14 行 exit criteria 增补六段逐条达成并留证：①**IContext 注册表 get-or-create 单修复（ContextAttrs）**；②**ormModel volatile 化**；③**session 全生命周期互斥**；④**事务注册协议原子性（半个成功消除）**；⑤**实体缓存 read-repair 世代化**；⑥**drainTo 等待窗口出事务**
- [ ] 对齐纪律守约：R10 自并单点（裁定⑧⑨同变更集）；C2-004×C1-027 世代号 SSOT 单侧声明（R4-18 消费注记落地）；平台前提零代做（nop-api-core 零触碰）；N4 实现约束（tryClaim+loadItems 同短事务）兑现；C1-016 钉零弱化；PA 条款闭环（零 orm 触碰声明兑现或条件申报面落地）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑬均有先红后绿或变异/回退论证（E2）；C10 收口门同 diff 落地（全量 6 + 半边 3 + 009 注记）
- [ ] 设计文档裁定①–⑭成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-persistence -am` BUILD SUCCESS（E1/E3；上游环境抖动三联裁定留痕）
- [ ] Owner doc 更新完成：model-first-development.md（+source-anchors.md 按实际）（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）注册表原子化/互斥/收尾/围栏/协议/锁纪律/谓词/包络运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-persistence 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index P1 台账 R2-14 十三行收敛 + roadmap 8.23 → done + R4-18/R4-19 就绪注记（E6）

## Deferred But Adjudicated

### R4-18/R4-19 成员（G45 前后各 15 项，含 C1-027/C1-016/C2-016 及其 C10 载体面）

- Classification: `out-of-scope improvement`（batch-proposal L133/L134 显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-18（roadmap 8.58，软依赖 8.23）/R4-19（roadmap 8.59，软依赖 8.58）
- Why Not Blocking Closure: 判级对齐零重复——cra-index P2-G45 行批次列=R4-18/R4-19；本批已收敛 2.4 单元全部 13 项 open P1（有效工单全集），下游实施以本批后的 live 形态为基线，且可引用本批裁定①⑥⑧⑨接缝（ContextAttrs/世代号 SSOT/协议原子化）为既有裁定不重开体。

### P2-G46 条化成员（2.4-C1-021）与 CL 线索面（CL-410~415）

- Classification: `out-of-scope improvement`（C1-021 条化激活前提未满足，G46 deferred；CL 面 watch/不立案已在 cra-index 落账）；Successor Required: `yes`（C1-021 触发条款=运行期租户模型切换启用）；Successor Path: 触发条款所在批（G46 行声明）
- Why Not Blocking Closure: 批次列为批前裁定；条化激活前提与 CL 线索均不构成本批 13 成员的 in-scope 缺陷面。

## Non-Blocking Follow-ups

- **R4-18/R4-19 对接注记**：本批落地后向 roadmap 8.58/8.59 行作就绪注记（软依赖解除落点；含裁定①⑥⑧⑨接缝引用指向与 C1-016/C2-016 钉义务转达）；R2-15 并行批若同模块在飞，E3 快照各自留证。
- **平台级 ContextAttrs 工具上收**：若后续批需要跨模块通用 context 属性原子 get-or-create 工具（nop-api-core 层），属后续平台批独立申报事项（本批预设模块内联实现不阻网），不阻塞本批 closure。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （收口时填写）
Completed: —

Closure Audit Evidence:

- Reviewer / Agent: （待独立 closure audit）
- Evidence: （待填）

Follow-up:

- （待填；或明确写 no remaining plan-owned work）

## Risks And Rollback

- **裁定④ session 互斥的性能面风险**：flush 为每事务热路径，引入 ReentrantLock 或 owner-thread 校验有吞吐成本——设计文档须给出临界区边界（仅覆盖 flush 与生命周期变更，不覆盖读路径）与基线对照说明；回滚 = revert 裁定④变更集。
- **裁定⑥世代化与 C1-027 接缝风险**：世代号形态若在本批定夺后与 R4-18 的失效判据面（C1-027）出现语义分叉，R4-18 按引用边界条款回本设计文档修订登记，不得模块内发明第二套世代语义（对齐 2514 裁定⑥引用纪律先例）。
- **裁定⑬事务边界内移的语义面风险**：drainTo 摘除方法级 REQUIRES_NEW 改变既有装配契约（若部署层拦截器未装配则行为面不同——C6-001 判级前提声明）；变异探针纪律适用（注释/删除目标行→跑钉子→恢复→再跑，恢复以断言方式确认）；N4 约束（tryClaim+loadItems 同短事务）破坏即抢占 CAS 失效，回归钉红⑦/红⑬必须同变更集验证。
- **裁定⑧⑨锁序风险**：per-context 门闩若误覆盖 task 执行/commit（listener 外部回调）将串行化整个请求并制造锁内 I/O——锁序约束在设计文档成文为硬边界，closure audit 抽查临界区边界。
