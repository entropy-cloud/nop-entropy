# 2516 CRA Phase 8.22 — R2-13 1.7 batch 批（8 项：RUNNING 死区 fencing + dispatch/参数护栏 + excel 物化 + fetchSize 死配置 + REE 裸逃逸 + 终态写吞没 + sys-event 守卫旁路）

> Design Review: R2 agent_0180df21 @ 2026-10-30（第 1 轮 FAIL[1 Major/3 Minor]：Major-1 restartTime 零写点事实+NULL 语义判据→事实登记[评审亲读推翻：:153 唯一生产写点/tagSet=clock 非自动维护/NULL 判 stale 成立，撰写者勘误正确]；Minor×3 闭合——第 2 轮 probe 揪出 insert-path NULL 活行接管回归[新 Major]→newTask 补租约初始写点一行+三处措辞+myExecCount 载体表述+日志键入表+N1 时序 B 断言——评审明示修复面局部无裁量点，协调方核验落盘后 PASS）
> Plan Status: active
> Draft Review: R1 agent_c8501c74 @ 2026-10-30（FAIL[1 Major/3 Minor]：Major-1 裁定①(b) restartTime 零生产写点+NULL 语义判据缺口→事实登记+NULL 语义定夺子项；Minor×3[cra-index 收敛计数 L385→G21 L405/七行+束行、_vfs 目录歧义消解、前向 warning 预期]——协调方修入后转 active）
> Last Reviewed: 2026-10-30
> Source: roadmap 工作项 8.22（R2-13，`ai-dev/backlog/concurrency-resource-audit-remediation-roadmap.md` L164，Status=todo[本 plan 立项时回写为「todo（plan 2516 drafted，待 draft review）」]）；批次定义 = batch-proposal `ai-dev/audits/2026-10/2026-10-23-1400-cra-phase7-cross-module-adjudication/batch-proposal.md` §二.2 R2-13 行（L87 全文：finding ID 清单 8 项[1.7-C7-001；1.7-C8-001；1.7-C8-002；1.7-C5-001；1.7-C5-002；1.7-C6-002；1.7-C6-009；1.7-C7-011] / 模块与验证命令 nop-batch `./mvnw test -pl nop-batch -am` / Owner Doc=`docs-for-ai/03-modules/nop-batch.md` / PA=无 / 依赖「**依赖 R1-01**（C6-001/C6-010 同 diff 已落地；C7-001 fencing 对齐 R1-01+R2-11）→ **R4-11/R4-12 依赖本批**（C8-001 先于 C8-009）」/ exit criteria 增补：**C7-001 测试 N1 参数化改写前置；C5-002 测试 N2 同步改写；C5-001 C10-011 补回归钉；C8-002 biz 链穿透（C8-010 在 R4-12 同批）；C6-002 REE 裸逃逸封闭（C7-002A 搭车）；C6-009 紧随 RUNNING 死区族、C7-011 A2 注记搭车**）+ §一 通用门禁 E1–E6；成员发现详情 = WI 1.7 审计目录 `ai-dev/audits/2026-10/2026-10-16-1258-deep-audit-nop-batch-concurrency/`（C7-long-running.md C7-001 :48 起 / C7-011 :312 起；C8-backpressure.md C8-001 :51 起（复核修正① :275-284）/ C8-002 :78 起；C5-memory-bounds.md C5-001 :30 起 / C5-002 :61 起；C6-resource-lifecycle.md C6-002 :97 起；C6-round2.md C6-009 :230 起；C10-test-protection.md N1 :196-198 / N2 :200-201 / N3 :203 / C10 映射 :286-290 / 缺口 N1 耦合声明 :133-136；merge-adjudication.md 裁定 5（C7-002 A/B 面拆并入 C6-002/C6-001）:54-63 + 族 A1/A2/A3/A4 :89-115 + 合并台账 :140 起；C10 映射 = 跨模块裁定 `inventory-p2-c10.md` §二 WI 1.7 节 :181 起 + 小结行 :777[**1.7（12）**]；G21 搭车行 = cra-index P2-G21（1.7 终态化/RUNNING 死区搭车束：C6-010→R1-01 已落地、C6-009/C7-011→R2-13 本批））；执行基线 @ `94400444ea`（分支 `concurrency-audit-remediation`，roadmap 8.1–8.21 已 done，plan 2495–2515 均已完成或收口；本 Baseline 全部行号为该基线 live 回读值，2026-10-30 亲读，执行前如再漂移以 live 为准并登记）
> Related: plan 2514（R2-11，completed——**MA-23 fencing 权威 `ai-dev/design/concurrency/wf-task-scanner-fencing-and-task-wf-concurrency.md` 裁定⑥**：holderId+条件 UPDATE 抢占+单飞判据 rowsAffected==1+租约，§3.6「R2-13 引用边界条款」原文钉「R2-13（1.7-C7-001 RUNNING 死区）到达时引用对齐，不重建第二套 fencing」——**本批裁定①为引用方，引用不重建**）；plan 2496（R1-01，completed——1.7-C6-001/C6-010 已随本批前序修复（live `BatchTask.java:262-268` catch Throwable 注释钉 + 守卫首行 :260-263 注释钉在案），owner doc `nop-batch.md` §任务异常终态契约 :220-229 成文，本批裁定⑥零回退其面；另 R1-01 绝缘层 SSOT = `ai-dev/design/concurrency/periodic-task-insulation.md` §3.1）；plan 2502（R1-07，completed——1.7-C7-003 三表保留清理已修（提交 3e59106e6b），owner doc `nop-batch.md` §账本保留策略 :230-247 成文，本批零触碰）；plan 2515（R2-12，completed——PA 批内申报先例与「三联环境裁定」模式引用）；plan 2381（primaryError CAS 收集——`BatchTask` 该已修面零回退钉）；**下游**：R4-11（8.51，G23 前半 11 项）与 R4-12（8.52，G23 后半 11 项）**硬依赖本批**（roadmap L193/L194「硬依赖 8.22」；batch-proposal L126/L127 同）——批末须回写就绪注记，**顺序约束：C8-001 Guard 先于 C8-009 幽灵旋钮参数化（merge-adjudication 族 A4 + cra-index C8-001 行「修复先于 C8-009」）**；G21（P2 搭车束）随本批收敛残余两成员；**并行协同**：R3-04（5.4 report 批）C5-004 与本批 C5-001 双轨联动（G79「同缺陷第二实例修复联动不双计」）——若两批同时在飞，裁定④须与 R3-04 对齐位点清单
> Protected Area 授权：批次 PA=无（batch-proposal §二.2 R2-13 行授权列）。本批八成员站点位于 `nop-batch/nop-batch-dao`（C7-001）、`nop-batch/nop-batch-core`（C7-001/C8-001/C8-002/C6-002/C6-009）、`nop-batch/nop-batch-dsl`（C5-001/C5-002/C7-011）、`nop-batch/nop-batch-jdbc`（C5-002）、`nop-batch/nop-batch-exp`（C8-002）、`nop-batch/nop-batch-sys`（C7-011 被触发配置，非 `_` 前缀资源文件）、旁证 `nop-report/nop-report-core`（C5-001 物化点 `ExcelRecordInput`，只读旁证或裁定④触碰面，见下）——**核心站点均非 Protected Area**（业务模块 store/impl/loader 层）。**批内 PA 申报一项：裁定① C7-001 fencing 若采「新增租约列」形态，须在 `nop-batch/model/nop-batch.orm.xml` 的 `nop_batch_task` 实体新增列（如 `taskLeaseTime`）→ 触发 AGENTS.md「ORM 模型结构 plan-first」——申报载体=本 PA 行+设计文档裁定①+独立 draft review 闭环，沿 2514（裁定⑥两列申报）/2515（裁定⑧索引申报）先例「模型结构变更须先裁定方案并在 plan 登记后方可采」口径；配套迁移脚本为非 `_` 前缀新建（落 `nop-batch/deploy/sql/postgresql/`，载体随裁定①成文）。**预设备选分支 = 零 orm 触碰**：复用既有 `restartTime`（live `nop-batch.orm.xml:72` 在案）+ 租约超时条件构成 stale 判定〔draft review R1 Major-1 事实登记：restartTime 现状**零生产写点**（live 2026-10-30 亲读，全仓仅生成 bean 引用、DaoBatchStateStore 零引用）——(b) 分支须补 `SET restartTime=now` 抢占写点 + **NULL 判 stale 语义**（存量 RUNNING 死区行 restartTime 均 NULL，若 NULL 不判 stale 则本缺陷主体恰恰无法被接管），NULL 语义归 Phase 1 定夺〕（Phase 1 一次定夺，两分支均须在设计文档成文）**。裁定④若采游标式改造则触碰 `nop-report/nop-report-core`（`ExcelRecordInput` 物化点本体）——nop-report 非 Protected Area，但为批行模块外跨模块站点：须在 Scope 显式登记，验证命令增补 `./mvnw test -pl nop-report -am`（E3 增补支路随裁定④成文）；备选分支 = nop-batch 侧包络（规模上限声明+maxCount 接线恢复，零 nop-report 触碰）。其余站点不触 orm/平台层。本批不触碰 R4-11/R4-12 批外成员本体。

## Purpose

关闭 R2-13 批的 8 项已确认缺陷（6 项 P1 全部 open + 2 项 P2 搭车[C6-009/C7-011，G21 束]，cra-index 台账行 L182–L188 中批次列=R2-13 的行（live 行号，勘误 3 校正））。核心结果面有六：①**RUNNING 死区族闭合**（C7-001 ←族 A2 + C6-009 紧随 + C7-011 注记搭车）——终态保存失败/进程崩溃后启动闸门无 stale 判定退化为永久死区（cron 每 5s 错误风暴 ≈17,280 条/天）；onTaskComplete 终态写失败被 whenComplete 静默吞没（死区时序 A 的静默化因子）；sys-event 触发链 saveState 未配置使守卫结构性旁路。修复=fencing/stale 判定（**对齐 plan 2514 裁定⑥，引用不重建**）+ 清理段显式化（S4/S5/S6 同段同修）+ 守卫解耦或契约显式化。②**dispatch 容量护栏**（C8-001←C5-005）——loadBatchSize≤0 五层装配链零守卫，orm-reader 全表加载 Semaphore(0) 永久挂死、jdbc/file 族静默空任务丢数、取消无效；修复=Guard fail-fast 三层防线一次定夺（**次序约束：先于 R4-12 C8-009 参数化**）。③**batchSize 守卫对称**（C8-002←CL-008）——ExportDbTool 缺省 0 每行一 chunk 写放大磁盘耗尽（与 Import 守卫不对称）；修复=`>0` 守卫对称补齐 + 钳位可观测，biz 链穿透共享位点（C8-010 本体归 R4-12）。④**内存包络**（C5-001，C10-011 必补回归钉）——excel-reader 全 sheet 物化确定性 OOM 且 maxCountExpr 接线注释死代码；修复=游标式改造或包络声明（双轨联动 R3-04 不双计）。⑤**fetchSize 死配置**（C5-002）——`&&` 门使 DSL fetchSize 永不生效，驱动全量缓冲 GB 级 OOM；修复=门条件独立兜底 + WARN，**N2 测试同步改写前置**。⑥**REE 裸逃逸封闭**（C6-002←C7-002A 面）——executor.execute 同步拒绝绕过 onTaskComplete，setup 资源泄漏+永久 RUNNING；修复=提交点完成兜底 + 已提交幽灵 futures 显式结算（plan 2496 C6-001/C6-010 已修面零回退）。批末 roadmap 8.22 → done，cra-index 台账 L182–L188 七行收敛 + P2-G21 束行注记（勘误 7：C6-009 无独立行，唯一载体=P2-G21 束行 L405），R4-11/R4-12 硬依赖就绪。

## Current Baseline

### 批次与判级基线

- batch-proposal §二.2 R2-13 = 8 项成员（P1×6 + P2 搭车×2）；cra-index 台账独立行（live 行号，勘误 3 校正）：L182 C8-001（←C5-005）、L183 C8-002（←CL-008）、L184 C5-001（C10-011 补回归钉义务）、L185 C5-002（←CL-038 事实，修复须同步改写 N2）、L187 C6-002（←C7-002A）、L188 C7-001（跨单元 fencing 对齐 + N1 前置）；P2 搭车 = C6-009（cra-index P2-G21 行 L405（L385 实为 P2-G01 行，勘误 7 纠正） 邻近行 + P2-G21 行 L405）与 C7-011（P2-G21 行 L405），均「批次随 1.7 P1 批→实际批号 R2-13」（以 cra-index live 为准逐行核对，均为 open、批次列均=R2-13）；roadmap 8.22 行 Status=todo。前置：**R1-01（8.2）+ R2-11（8.20）均已 done**（roadmap 8.22 行 Deps 勘误申报已落：「硬依赖 8.2+8.20」）——基线门禁已满足。
- **同单元已修/批外面登记**：1.7-C6-001 与 1.7-C6-010（终态化阻断族 M1）已随 R1-01（plan 2496）fixed（live `BatchTask.java:260-268` 双注释钉在案：守卫起点上移 + catch Throwable）；1.7-C7-003（三表零保留）已随 R1-07（plan 2502 分片 B，提交 3e59106e6b）fixed（owner doc `nop-batch.md:230-247` 成文）；C7-002 B 面（:243 Error 逃逸）随 C6-001 同批修复——**C7-002 残余仅 A 面，并入本批 C6-002**（merge-adjudication 裁定 5）。1.7 单元合并后台账 51 条中：P1×8 → 6 项入本批（C6-001/C7-003 已修 = 余 6）+ P2 搭车 C6-009/C7-011；其余 P2/P3 全部归 R4-11/R4-12/G21 已收敛部分/dormant（见 Non-Goals）——批次覆盖口径完整。
- **吸收成员（随主条目同 diff，不单列工单）**：C8-001←C5-005（dispatch 容量同位点，C5 复核既定「合并时以 C8-001 P1 为主处置项」；P2-G83 行登记）；C8-002←CL-008（同站点确认线索沿用立案）；C5-002←CL-038（死配置事实沿用不立案口径）；C6-002←C7-002A 面（裁定 5：同一 `executor.execute` 提交点同根因；A 面独有增量「已提交 futures[0..i-1] 幽灵线程继续加载消费数据」并入本批修复验证面；B 面已随 plan 2496 闭合）——吸收明细以 merge-adjudication §一裁定 5 + 台账行 :154-155/:206/:209 为准；cra-index 回填时以主 ID 收敛、吸收成员括注。
- **族登记（merge-adjudication 自主同族扫描）**：**族 A2 RUNNING 死区**（C7-001 ↔ C6-009；C7-011/C7-012 注记关联）——C6-009 为 C7-001 时序 A 的静默化因子，位点与修复独立不合并，C6 复核既定「修复排期紧随 P1 族」→ 本批同批落地（裁定①+⑦ 同一设计文档配套）；C7-012（KILLED 吸收态）归 R4-12 不代做，接缝（stale 判定复用同一状态机写者 DaoBatchStateStore）在裁定①声明。**族 A3 batchSize 护栏**（C8-002 ↔ C5-009 ↔ C8-010）——三缺口共享 adjustBatchSize 钳位子位点；本批裁定③封闭共享位点（Export 守卫+钳位可观测），C5-009（P3）/C8-010（P2）本体归 R4-12。**族 A4 dispatch 参数面**（C8-001 ↔ C8-009）——**次序约束成文：C8-001 Guard 修复必须先于 C8-009 幽灵旋钮参数化**（先堵危险值、再谈暴露旋钮；C8-009 归 R4-12）。
- **C10 验收前置入批集（6 条本体收口门，随本体同 diff 落地[E2]；映射 SSOT = `inventory-p2-c10.md` 小结行 :777「1.7（12）」+ C10-test-protection.md 映射复核 :286-290）**：C10-001（终态化族 M1 收口门——Error/REE 注入 → future 必结算 → 任务行必达终态 → claim 无残留；C6-001/C6-010 已随 plan 2496 修复，本批承接 **C6-002 残余义务 + C7-002A 断言**[幽灵 futures 结算]）、C10-002（RUNNING 死区族收口门——C7-001+C6-009+C7-011 随本批；**C7-012 载体面归 R4-12**，状态存储故障注入形态 = saveTaskState 抛错/stale RUNNING 伪造/重启序列）、C10-003（C8-001 收口门——Guard fail-fast 用例带 loadBatchSize/capacity 配置上下文 + orm 路径断言 fail-fast 非空任务成功[复核修正①口径]；**C8-009 前置参数化面归 R4-12**）、C10-004（C8-002 收口门——缺陷缺省路径断言 + 放大面断言；C6-003/C6-004/C6-007 载体面归 R4-12）、C10-011（**C5-001 必补回归钉**——C10-test-protection 登记完整性缺口补齐：excel-reader 内存/流式行为断言；C5-006 P3 顺带评估随同 diff）。**套件注记义务（三项全在本批）**：N1→C7-001（`TestDaoBatchStateStore` 三用例 `testLoadTaskState_runningInstance_rejectedLoudly`/`testConcurrentStart_freshKey_onlyOneInstanceEntersRunning`/`testConcurrentRestartFromCompleted_onlyOneInstanceGainsRunning` 以「存在 RUNNING 行即无条件拒绝」为期望——裁定①采用 stale 判定即打红，**参数化改写[显式构造 live/stale 两态前置行]为修复 diff 前置义务**）；N2→C5-002（`TestJdbcBatchSupport:180` `testNewJdbcReaderKeepsDefaultsWhenModelEmpty` 断言注释「unset fetchSize stays null so loader uses SQL-level default」为死配置契约的装配层表达——裁定⑤改 provider 层兜底即 wiring 契约变更，**同步改写前置**）；N3→C8-001（`TestPartitionDispatchQueueFinish`/`TestAsyncFetchLoader*` 的 `latch.await()` 无超时——修复 diff 携带补超时同步评估，防护栏回潮时构建悬挂）。**不在本批的 C10 处置**：C10-005（C6-005/C6-011/C7-008/C7-009 载体面归 R4-12；C5-002 半边随本批裁定⑤——映射勘误见勘误 2）、C10-006/007/008/009（载体归 R4-11/R4-12）、C10-010（复核补充登记）、C10-012（C8-005/C8-006 归 R4-12）。
- **nop-batch 域先序批状态（live 形态登记，2026-10-30 亲读）**：plan 2496（R1-01）已 landed——`BatchTask.executeChunkLoop` catch Throwable + 守卫首行（`BatchTask.java:260-285`，owner doc §任务异常终态契约 `nop-batch.md:220-229`）；plan 2502（R1-07）已 landed——三表保留清理（owner doc §账本保留策略 `:230-247`）；plan 2381 已 landed——primaryError CAS 收集（`BatchTask.java:50-51/:209-214/:279-281` 注释钉）。本批为上述已修面之上的残留缺陷修复，全部零回退。

### 对齐纪律显式条款

- **C7-001 × MA-23 fencing 对齐（plan 2514 裁定⑥：引用不重建——本批为引用方）**：R12 族 fencing 权威 = `ai-dev/design/concurrency/wf-task-scanner-fencing-and-task-wf-concurrency.md` §3.6 裁定⑥ + 其「R2-13 引用边界条款」（原文：holderId/条件 UPDATE 抢占/单飞判据/租约与 status 守卫层级关系在 2514 定稿，R2-13 到达时引用对齐，不重建第二套 fencing）。本批裁定①只做「按裁定⑥形态菜单落地 nop-batch `nop_batch_task` 实例」：条件 UPDATE 抢占（`tryMarkTaskRunning` WHERE 扩展 stale 谓词）+ 单飞判据 rowsAffected==1 + 租约缺省/键族与 2514 同构对齐（`nop.batch.task.*` 键族，若引入新保留/租约参数键须与 2514 声明同构并登记）；引擎既有 `ERR_BATCH_TASK_NOT_ALLOW_START_WHEN_EXIST_RUNNING_INSTANCE` 快失败守卫为事中兜底零回退（fencing 为事前接管，双层并存不合并——与裁定⑥层级关系条款同构）；**若裁定引入 nop-batch 侧新租约/fencing 语义偏离裁定⑥菜单，须回 2514 设计文档修订登记（附理由），不得模块内发明第二套语义**；平台层上收申报触发条件：跨模块通用扫描抢占原语（nop-kernel 层）若被裁定需要，须彼批申报框架核心 plan-first——本批预设 nop-batch 模块内零平台层触碰。
- **C6-002/C8-002 × R1-01 已修面零回退**：C6-002 修复触碰 `BatchTask.executeAsync`/`executeChunkLoop` 提交面——不得回归 plan 2496 已修面（C6-001 catch Throwable :264-285、C6-010 守卫首行 :260-263、plan 2381 primaryError CAS 收集）与 owner doc §任务异常终态契约（`nop-batch.md:220-229`）；C6-009 清理段显式化不得改变「future 必以原始 err 结算 + afterComplete 关闭链必达」的既有契约，只在终态写失败可见性上补面。
- **C6-009 × RUNNING 死区族关系成文（族 A2）**：C6-009（终态写失败静默吞没）为 C7-001（闸门无 stale 判定）时序 A 的静默化因子，位点与修复独立、不合并；两成员同批落地且共用裁定①的「终态写失败显式可见」语义——C6-009 修复后死区入口面由裁定① stale 接管承接；C7-011 为同状态机邻接面注记搭车（A2 族第三成员），修复不扩为状态机重构。
- **C8-001 × C8-009 次序约束（族 A4）+ C8-002 × biz 链穿透（族 A3 共享面）**：裁定② Guard 落地后向 R4-12（8.52）就绪注记显式声明「C8-009 幽灵旋钮参数化前置已满足」；裁定③封闭 adjustBatchSize 共享钳位位点后，C8-010（biz 钳位无下界穿透复现 C8-002）的复现通道随共享位点收窄，本体归 R4-12 不代做——接缝在设计文档一次写清（R4-12 消费同一 Guard/WARN 面）。
- **C5-001 × G79 双轨联动（R3-04）**：5.4-C5-004 = 1.7-C5-001 同缺陷第二实例（cra-index P2-G79 行「修复联动不双计」）——裁定④若采游标式改造（触碰 `ExcelRecordInput` 本体），位点清单与 R3-04 批对齐登记，R3-04 随其 P1 修复引用同一裁定不重做；若两批同时在飞，以本批裁定④为位点 SSOT、R3-04 为消费方。
- **跨单元同号防混**（inventory-p2-c10 :875）：C7-001 有六处同名异站——本批成员恒带单元前缀 1.7；fencing 对齐面为 1.5-C7-001（plan 2514 裁定⑥）与 1.6-C8-001（plan 2496 绝缘层），非本批本体。

### 8 成员逐条（站点/缺陷/修复方向——修复内容的事实源；行号 = live @ `94400444ea` 2026-10-30 回读值，执行前如再漂移以 live 为准并登记）

| # | Finding ID（判级） | 文件（模块） | 站点位置（file:line，live 回读） | 缺陷一句话 | 修复方向 |
|---|---|---|---|---|---|
| 1 | 1.7-C7-001（P1；族 A2 主；C10-002；N1 前置；MA-23 fencing 对齐 + orm PA 批内申报） | `DaoBatchStateStore.java`（nop-batch-dao `store`）+ `BatchTask.java`（nop-batch-core `impl`）；模型 `nop-batch/model/nop-batch.orm.xml`（workerId :64 / restartTime :72 在案，租约列零命中） | 启动闸门快失败 `startExistingTask` :106-150（:123-129 无条件拒绝 RUNNING）；`tryMarkTaskRunning` :187-195（WHERE 仅 `taskStatus > RUNNING`，无 lease/workerId/心跳比较）；`loadExistingTask` :205-222；`saveTaskState` :234-256（终态写经 `updateTask`→`updateEntityDirectly` :230）；`onTaskComplete` 双次保存失败链 `BatchTask.java:221-244` | 任务行永久 RUNNING 死区：终态保存失败/进程崩溃后单活跃闸门无 stale 判定永久拒启，cron 周期触发错误风暴（5s 级 ≈17,280 条/天/任务），无专用运维端点（裸 CrudBizModel，人工改库外无恢复入口） | 裁定①（C10-002 收口门随同 diff；MA-23 对齐见纪律节；orm PA 申报见头部 PA 行）：`tryMarkTaskRunning` WHERE 扩展 stale 谓词——形态一次定夺：(a) 新增租约列条件接管（orm plan-first 批内申报，形态对齐 2514 裁定⑥：条件 UPDATE+rowsAffected==1 单飞+租约键族同构）；(b) 零 orm 触碰——复用 `restartTime` + 租约超时条件（`restartTime + leaseTimeout < now`）；运维 force-kill action 为备选分支；接管成功后行内 workerId/状态原子刷新；N1 参数化改写**前置**（三用例显式构造 live/stale 两态前置行，改写前后语义对照留痕）；C7-012 接缝登记（同一状态机写者，本体归 R4-12）；崩溃/DB 瞬断触发时序 A/B 双路径回归测试 |
| 2 | 1.7-C8-001（P1；←C5-005；C10-003；N3 搭车；族 A4 次序约束） | `PartitionDispatchQueue.java` + `AsyncFetchPartitionDispatchLoaderProvider.java` + `PartitionDispatchLoaderProvider.java` + `BatchTaskBuilder.java` + `BatchDispatchConfig.java`（均 nop-batch-core）；DSL 面 `batch.xdef:80-81`（nop-xdefs，只读旁证——xdef 下界为备选分支） | 队列构造直通 `PartitionDispatchQueue` :78-83（`new Semaphore(capacity)` 无 Guard；消费侧对照 `Guard.checkArgument(batchSize > 0)` :134）；`AsyncFetchPartitionDispatchLoaderProvider` :78（capacity = loadBatchSize × multiplyFactor）；同步派发 `PartitionDispatchLoaderProvider` :61；装配链 `BatchTaskBuilder` :340/:343/:346 直通；`BatchDispatchConfig` :16（Java API 缺省 0）；xdef `loadBatchSize="!int=100"` :81 无 min | dispatch 容量参数（loadBatchSize≤0）五层装配链零护栏：orm-reader 全表单页加载对 Semaphore(0) 永久阻塞挂死（复核修正①——非「静默空任务」）、jdbc/file 族静默空任务丢全量、cancel 不入等待环取消无效 | 裁定②（C10-003 随同 diff；C8-005 同位点吸收面并入）：三层防线一次定夺——预设 = `BatchTaskBuilder.buildLoader:340` 对 `loadBatchSize<=0` 抛 NopException 配置错误（fail-fast 携 loadBatchSize/capacity 公式上下文）；备选 = 队列构造器 Guard / xdef min 约束；orm-reader 修复后期望终态 = fail-fast **非空任务成功**（复核修正①口径断言）；cancel 期间 acquirePermits/takeBatch 可退出断言（finish 之外第二释放入口）；N3 搭车（latch.await 补超时同步评估）；C8-009 次序就绪注记义务（Phase 4） |
| 3 | 1.7-C8-002（P1；←CL-008；C10-004；族 A3 共享面） | `ExportDbTool.java`（nop-batch-exp）；对照 `ImportDbTool.java`（已修兄弟）；`BatchChunkProcessor.java`（nop-batch-core）；schema `export-db.xdef:11` / `import-db.xdef:7`（nop-xdefs，缺省值备选分支面） | `ExportDbTool` :278 无守卫直传 `builder.batchSize(config.getBatchSize())`；`ImportDbTool` :302-303 有 `>0` 守卫（不对称）；`BatchChunkProcessor.adjustBatchSize` :83-90（:89-90 `ret<=0→1` 静默钳位无告警——所有 batchSize≤0 装配入口共同终点）；每 chunk 双 INFO `BatchTask.java:336/:386` + 每 chunk saveTaskState :358/:377 | ExportDbTool 缺省 batchSize=0 静默钳位 1 → 每行一个 chunk：吞吐塌缩 1-3 个数量级 + 日志/状态文件写放大磁盘耗尽（与 Import 守卫 F3 不对称，缺省装配确定性触发） | 裁定③（C10-004 随同 diff）：ExportDbTool 复制 Import `>0` 守卫（最小修复预设，消除不对称）；adjustBatchSize 钳位补一次性 WARN（对齐 `nop.batch.orm-reader-no-query` 静默兜底可观测先例）或上移 builder 装配期 Guard——一次定夺；biz 链穿透面（C8-010 复现通道）随共享钳位位点收窄，C8-010 本体归 R4-12（接缝登记）；xdef 缺省值改 1000 为备选分支（触 nop-xdefs 须登记）；放大面回归断言（chunk 计数/INFO 量） |
| 4 | 1.7-C5-001（P1；C10-011 必补回归钉；G79 双轨联动） | `FileBatchSupport.java`（nop-batch-dsl）；物化点 `ExcelRecordInput.java`（nop-report-core——批行模块外跨模块站点，触碰与否随裁定④分支）；测试资产 `TestBatchTaskDsl`/`TestExportDbTool`（零断言现状） | 接线 `newExcelReader` :140-158（`maxCountExpr` 注释死代码 :150-151）；物化点 `ExcelRecordInput.beforeRead` :87-98（:97 `list = collector.getResult()` 全 sheet List 常驻；`hasNext` :101 仅内存下标推进）；XLSX 单 sheet 上限 1,048,576 行（50 万行 × 20 列 ≈ 1.5-3GB 确定性 OOM） | excel-reader beforeRead 全 sheet 物化为内存 List：违背 owner doc「Chunk 内存友好」声明包络，平凡配置（大数据量 excel 导入）确定性 OOM；maxCountExpr 接线禁用且即便恢复也是先物化后截断无兜底 | 裁定④（C10-011 随同 diff）：两分支一次定夺——(a) 游标式改造 `ExcelRecordInput`（解析层按行回调产出，参照 `CsvRecordInput` 迭代器形态；触 nop-report-core，Scope/E3 增补申报）；(b) 零 nop-report 触碰——nop-batch 侧包络：恢复 `maxCountExpr` 接线 + owner doc 显式声明 excel-reader 规模上限与适用边界（不够格称为内存修复，须与判级陈述一致登记为「包络声明+截断，物化面残留归 R-G 观察项」）；**两分支均须 C10-011 补回归钉**（excel-reader 内存/流式行为断言——缺陷态红：全量物化可断言 collector 路径；现有 `TestBatchTaskDsl#testImportExcel`/`TestExportDbTool#testExportXlsx` 零断言面补齐）；C5-006（P3，maxCountExpr 能力缺位）顺带评估随同 diff；R3-04 双轨联动注记（G79） |
| 5 | 1.7-C5-002（P1；N2 同步改写前置；C10-005 半边） | `JdbcBatchLoaderProvider.java`（nop-batch-jdbc）；DSL 接线 `JdbcBatchSupport.java`（nop-batch-dsl）；xdef 缺省 `batch.xdef:130-133`（只读旁证）；测试 N2 `TestJdbcBatchSupport.java:180` | 坏门 `newState` :243-247（`if (sql.getFetchSize() != -1 && fetchSize != null)`——query/sql 文本路径 SQL 对象 fetchSize 恒 -1 → 门条件恒 false → provider fetchSize 静默丢弃）；DSL 装配 `JdbcBatchSupport` :27-28（loader.setFetchSize 到位）；`streaming="!boolean=false"` xdef :132；变体 B：20 个 `.dialect.xml` 仅 mysql 定义 streamingFetchSize（非 MySQL streaming=true 三重不可达）；autocommit 未关同族事实 | jdbc-reader DSL fetchSize 死配置：显式配置在最常见形态（query/sql + streaming 缺省 false）下永不生效且无 WARN——MySQL/PG 驱动 executeQuery 全量缓冲结果集，GB 级确定性 OOM 在声明包络内 | 裁定⑤（N2 改写**前置**义务 + C10-005 C5-002 半边随同 diff）：门条件改独立兜底 `if (fetchSize != null) ps.setFetchSize(fetchSize)`（\|\| 语义——sql 层已由 JdbcHelper 处理，provider 层只兜底）；streaming=false 且 fetchSize 未生效路径补 WARN；N2 `testNewJdbcReaderKeepsDefaultsWhenModelEmpty` 同步改写（wiring 契约变更：缺省语义由「stay null」改为「provider 兜底缺省」——改写前后语义对照留痕）；运行时生效断言（记录型 Proxy 先例：`TestJdbcBatchConsumerProviderSharedField`/`TestJdbcPageBatchLoaderRetry` 形态）补 C10-005 半边；变体 B/autocommit 面登记不扩批（归 R4-12 C7-009/C6-011 邻接面注记） |
| 6 | 1.7-C6-002（P1；←C7-002A；C10-001；R1-01/2381 已修面零回退） | `BatchTask.java`（nop-batch-core `impl`） | futures 提交循环 :173-176（无包络）；同步抛点 `executeChunkLoop` 内 `executor.execute(` :255（lambda try :264 之外——REE 在 lambda 执行前抛出，catch Throwable 管不到）；外层 try :133-190 仅 finally :189-190 无 catch → REE 裸逃逸 executeAsync；对照站点 `AsyncFetchPartitionDispatchLoaderProvider.setup:116`（fetch 线程提交已有兜底——REE 经 setup 逃逸入 :167-172 catch → onTaskComplete） | executor.execute 同步拒绝（REE）裸逃逸绕过 onTaskComplete：setup 已打开的 reader/output/JDBC 连接泄漏 + 任务行永久 RUNNING；C7-002A 吸收面：已提交 futures[0..i-1] 幽灵线程继续加载消费数据 | 裁定⑥（C10-001 残余义务 + C7-002A 断言随同 diff）：REE 裸逃逸封闭——(a) `executeChunkLoop` 内 `executor.execute` 提交点 try/REE → `future.completeExceptionally` 防御；(b) executeAsync futures 提交循环包入 try/catch：任一提交抛出时已建 futures 逐个 completeExceptionally（**含 C7-002A 已提交幽灵链显式结算/取消**）并直达 `onTaskComplete` 既有终态化+关闭链；默认 cachedThreadPool 不可达/注入有界池饱和与停机竞态触发面声明保留（判级不降）；plan 2496 C6-001/C6-010 与 plan 2381 primaryError 已修面零回退（注释钉 :260-268 零触碰）；C10-001 断言形态 = Error 注入 + REE 注入 → future 必结算 → 任务行必达终态 → claim 无残留 |
| 7 | 1.7-C6-009（P2 搭车；G21 紧随 RUNNING 死区族；C10-002；族 A2 配套裁定①） | `BatchTask.java`（nop-batch-core `impl`，同 #6 站点族）；`FutureHelper`（nop-api-core，只读旁证） | `onTaskComplete` :204-250：err≠null 分支 `saveTaskState(true, err)` :236-241 抛新异常 X 穿透 finally（:242-244 completeExceptionally 必达但 X 无人捕获）→ 经 `allOf().whenComplete` 挂点 :177-180 时 X 仅使无人持有的返回 future 异常完成 → 静默丢失；S5 = 外层 finally `metrics.endTask` :245-246 先抛 → `FutureHelper.complete` :247 被跳过 → future 孤儿调用方挂死；S6 = setup 失败路径 :167-172 进入时 X 替代原始 e 同步逃逸（可见性错乱） | 终态写失败被静默吞没：context/future 已终态、资源已关，但任务行停留 RUNNING 且「终态写失败」事实零日志——运维按 err 重试被闸门永久拒绝（C7-001 时序 A 的静默化因子；触发面 = 普通任务结束瞬间 DB 抖动，比 C6-001/002 更宽） | 裁定⑦（C10-002 随同 diff；与裁定①同一设计文档配套）：清理段显式化族 S4/S5/S6 同段同修——saveTaskState 失败捕获 + ERROR 留痕/计数（终态写失败事实显式可见，不吞不替换原始 err）；S5 防御（metrics.endTask 独立 try 或与 complete 顺序重排，FutureHelper.complete 必达性断言）；S6 语义定夺（X 不替代原始 e，聚合日志）；终态写失败后的行残留由裁定① stale 接管承接（族 A2 闭环声明）；修复排期紧随 P1 族（C6 复核 R5.1 既定，本批同批落地） |
| 8 | 1.7-C7-011（P2 搭车；G21 A2 注记；C10-002） | `ModelBasedBatchTaskBuilderFactory.java` + `BatchTaskRunner.java`（nop-batch-dsl）；`BatchTask.java` initTaskKey（nop-batch-core，同 #6 站点族）；被触发配置 `non-broadcast-consumer.batch.xml` + 触发配置 `sys-event-batch-consumer.job.yaml`（nop-batch-sys，非 `_` 前缀资源） | saveState 门控 `ModelBasedBatchTaskBuilderFactory` :191-192（非 true 即不接 stateStore）；`BatchTaskRunner.executeAsync` :36-49 直通无 JVM 内互斥；UUID 绕过面 `initTaskKey` :194-201（:199-200 无 taskKeyExpr 时 generateUUID）；被触发配置无 saveState（live grep 零命中确认）；job.yaml :2（`enabled|false` 缺省关闭）/:6（cron `0/5`） | sys-event 触发链守卫结构性不覆盖：saveState 未配置 → stateStore 不接线 → (taskName,taskKey) 唯一键与启动闸门全旁路；UUID 键为同根因第二表现（守卫永不冲突 + 每执行一行死数据并入 C7-003 已修增长面）；重叠双执行后果依赖跨单元前提（nop-job 重叠语义/nop-sys claim 原子性，划出不立案） | 裁定⑧（C10-002 随裁定①同 diff；A2 注记搭车）：三分支一次定夺——(a) 最小修复预设 = 被触发配置 `non-broadcast-consumer.batch.xml` 补 saveState+taskKeyExpr（守卫接线闭合，成本最低）；(b) DSL 层 `singleActive` 解耦声明（与 saveState 解耦的单活跃守卫——扩面备选分支，若采须评估与裁定①闸门复用）；(c) owner doc 跨单元契约显式化（「重叠防护依赖 nop-job 调度语义 + nop-sys claim 原子性」声明）；job.yaml 缺省关闭条件面标注保留（判级不因此降级）；nop-job/nop-sys 侧零改码 |

### 平台设施与测试资产盘点（live 实读 @ `94400444ea`）

- **先例范式可引用不重开**：plan 2514 裁定⑥ fencing 形态菜单（条件 UPDATE 抢占/holderId/租约/迁移脚本三行/实体写路径列集约束——裁定①直接投影，`nop_batch_task` 已有 workerId/restartTime 列为 (b) 分支载体）；plan 2502 保留清理 + 配置键族（跨单元 C7-003 同参先例）；plan 2496 终态化阻断族 M1 修复形态（C10-001 断言基型）；plan 2381 primaryError CAS 收集（多线程异常归因先例，裁定⑥⑦复用）；`TestBatchBugVerification` latch+@Timeout(30) 异步防挂起先例（N3 搭车形态）；`ImportDbTool:302` `>0` 守卫（同类守卫既定惯例，裁定②③对称补齐）；`nop.batch.orm-reader-no-query` WARN 先例（静默兜底可观测，裁定③⑤复用）。
- **既有测试资产（回归门禁与改写/补测对象）**：`TestDaoBatchStateStore`（nop-batch-dao，N1 参数化改写对象 + 裁定①补测落点——闸门正确面 7 例钉厚、死区面零钉）；`TestJdbcBatchSupport`（nop-batch-dsl，N2 改写对象 :175/:180 + 裁定⑤运行时断言落点——现全部反射字段读取，运行时生效面零测试）；`TestPartitionDispatchQueueFinish`/`TestAsyncFetchLoaderFailure`/`TestBatchTaskDispatchLoadRetry`（C10-003/N3 落点，全正值参数）；`TestBatchBugVerification`/`TestBatchTaskCheckpoint`（C10-001 相邻钉，Exception 级终态化契约）；`TestExportDbTool#testExportXlsx`（C8-002 缺陷缺省路径被执行零断言）；`TestBatchTaskDsl#testImportExcel`（C5-001 同读取路径零断言）；`TestSysEventBatchTrigger`/`TestSysEventBatchPartitionE2E`（C7-011 相邻钉，被测 batch.xml 无 stateStore）；nop-batch-sys `non-broadcast-consumer.batch.xml`（裁定⑧ (a) 分支触碰面）。
- **owner doc 现状**（2026-10-30 亲读，`docs-for-ai/03-modules/nop-batch.md` 275 行）：§功能概览 :3-16（「Chunk 处理模式……内存友好」声明——裁定④包络对照锚）；§关键字段 :27-40（taskStatus/workerId 等字段表）；§DSL 结构 :55-90；§关键配置项 :204-219；§任务异常终态契约（plan 2496）:220-229（裁定⑥⑦零回退钉 + C6-009 显式化契约句落点）；§账本保留策略（1.7-C7-003，plan 2502）:230-247（零触碰）；§源码锚点 :248-259（补行落点）。**无 RUNNING 死区/fencing 契约节、无 dispatch/batchSize 护栏契约节、无 excel-reader 规模边界声明、无 fetchSize 语义声明、无 sys-event 链守卫覆盖声明**——裁定①②③④⑤⑧ owner doc 补节主落点。

### Baseline 勘误（输入不一致登记）

1. **行号核对（审计→live @ `94400444ea`，漂移主因 = plan 2381 primaryError 注释块 + plan 2496 C6-001/C6-010 修复与注释块使 `BatchTask.java` 区域统一 +14~+39 粒度漂移；其余站点 ≤+1 微漂移；机制面零实质漂移——全部缺陷机制 live 复核仍成立）**：BatchTask——executeAsync 审计 :106-152（提交循环 :143-146/:226 抛点）→live :120-191（提交循环 :173-176、`executor.execute` :255、外层 try :133-190 仅 finally）；onTaskComplete 审计 :175-220（C6-009 审计 :204-219）→live :204-250（err≠null 保存 :236-241、finally :245-247、whenComplete 挂点审计 :148-150→live :177-180）；initTaskKey 审计 :165-173（UUID :170-171）→live :194-201（UUID :199-200）；processChunk INFO 审计 :301/:351→live :336/:386、saveTaskState(false) 审计 :322-323→live :358/:377；C6-001/C6-010 plan 2496 注释钉 live :260-268（审计后新增，非漂移项）。DaoBatchStateStore——startExistingTask 审计 :106-150→live :106-150 ✓、tryMarkTaskRunning :187-195 ✓、loadExistingTask :205-222 ✓、saveTaskState 审计（C6-009 引 :253-254 updateEntityDirectly）→live `saveTaskState` :234-256（`updateTask`→`updateEntityDirectly` :230）。PartitionDispatchQueue :78-83/:134 ✓；AsyncFetch :78 ✓、setup:116 ✓；PartitionDispatchLoaderProvider :61 ✓；BatchTaskBuilder :340/:343/:346 ✓；BatchDispatchConfig 审计 :10→live :16（字段区下移 +6）；ExportDbTool 审计 :277→live :278；ImportDbTool 审计 :301-302→live :302-303；export-db.xdef :11 ✓；import-db.xdef :7 ✓；BatchChunkProcessor :83-90（:89-90 钳位）✓；FileBatchSupport newExcelReader :140-158（maxCountExpr 注释 :150-151）✓；ExcelRecordInput beforeRead :87-98（:97）/hasNext :101 ✓；JdbcBatchLoaderProvider :243-247 ✓（审计 :243-248 ±粒度）、fetchSize/streaming 字段 live :63/:65；JdbcBatchSupport :27-28 ✓；batch.xdef dispatcher :80-81 ✓、jdbc-reader :130-133（streaming :132）✓；ModelBasedBatchTaskBuilderFactory saveState 门控 :191-192 ✓；BatchTaskRunner :36-49（审计 :35-50 ±1）；non-broadcast-consumer.batch.xml 无 saveState live grep 零命中确认 ✓；sys-event-batch-consumer.job.yaml :2/:6 ✓；`nop-batch.orm.xml` workerId :64/restartTime :72、租约列零命中 ✓；N1 三用例与 N2 断言（`TestJdbcBatchSupport.java:180`）live 在案 ✓。
2. **C10-005 映射勘误登记**：`inventory-p2-c10.md` :777 映射行「005→C6-005/C6-011/C7-008/C7-009」未列 C5-002，而 C10-test-protection.md :154（缺口 5）与 :290（映射复核行）覆盖面均含 C5-002——以 C10-test-protection 为准登记差异；本批承接 C10-005 的 C5-002 半边（运行时生效断言）随裁定⑤同 diff，其余载体（C6-005/C6-011/C7-008/C7-009）归 R4-12；C5-002 的硬前置义务仍以 batch-proposal R2-13 行「测试 N2 同步改写」为准。
3. **cra-index 行号基线**：本 plan 引用的 L182-L188/L385/L405 为 2026-10-30 live 回读值；cra-index 台账若在执行前被其他批回填致行号漂移，以「批次列=R2-13 + finding ID」定位为准并登记。
4. **已修面勘误**：1.7 单元 P1 族 8 项中 C6-001（plan 2496，终态化阻断族 M1 主条目）与 C7-003（plan 2502 分片 B）已 fixed；C7-002 已按 merge-adjudication 裁定 5 拆解（B 面随 C6-001 闭合、A 面并入本批 C6-002）；G21 束三成员中 C6-010 已随 R1-01 落地——本批 Non-Goal 零触碰；C10-001 对 C6-001/C6-010 的覆盖面已随 plan 2496 闭合，本批仅承接 C6-002 残余义务与 C7-002A 断言。
5. **环境项预登记（批前既有）**：`./mvnw test -pl nop-batch -am`（及备选支路 `./mvnw test -pl nop-report -am`）触达上游模块（nop-kernel/nop-persistence/nop-orm/nop-auth/nop-report 链等）——上游环境抖动（含 debezium fork timeout、nop-auth MFA、负载时序类 flake、fork 启动崩溃类环境项，沿 plan 2512–2515 已知面）按「stash 基线同红 / solo 绿 / 恢复复跑全绿」三联裁定处理并留痕，不得静默重跑或跳过。

## Goals

- Phase 1 设计裁定文档一次裁决八项裁定 + 测试缝（独立 draft review 通过），无临场发明空间：①C7-001 RUNNING 死区 fencing/stale 判定（**MA-23 对齐 2514 裁定⑥引用不重建** + N1 参数化改写前置 + orm PA 申报分支 + C7-012 接缝）；②C8-001 dispatch Guard 三层防线一次定夺（A4 次序约束成文 + N3 搭车）；③C8-002 Export/Import 守卫对称 + 钳位可观测（A3 共享面 + C8-010 接缝）；④C5-001 物化包络两分支定夺（C10-011 必补回归钉 + G79 双轨联动）；⑤C5-002 fetchSize 门独立兜底（N2 同步改写前置 + C10-005 半边）；⑥C6-002 REE 裸逃逸封闭（C7-002A 吸收 + R1-01/2381 零回退钉清单）；⑦C6-009 清理段显式化（S4/S5/S6 同段同修 + 族 A2 配套）；⑧C7-011 守卫覆盖三分支（A2 注记搭车 + 跨单元前提划出）——每条唯一预设默认分支 + 测试缝 + 零回退钉清单。
- 8 项成员全部修复并各带回归测试（rule 25）；C10 五条收口门（C10-001/002/003/004/011）随本体同 diff 落地（E2）；N1/N2/N3 三项套件注记义务全数履行。
- 关键工单先红后绿或变异/回退论证留痕（E2）：红① C7-001 死区红（saveTaskState 抛错/崩溃后重启序列 + stale RUNNING 行注入——缺陷态永久拒启红→修复后 stale 接管绿；N1 三用例参数化前后对照留痕）；红② C8-001 护栏红（loadBatchSize=0/负 注入——缺陷态 orm 挂死/jdbc 静默空任务红→修复后 fail-fast 绿）；红③ C8-002 放大红（export-db 缺省配置——缺陷态每行一 chunk 红→修复后守卫落缺省/WARN 绿）；红④ C5-001 物化红（大 sheet 断言 collector 全量路径——缺陷态全量物化红→修复后游标/包络绿）；红⑤ C5-002 死配置红（记录型 Proxy 断言 setFetchSize 调用——缺陷态永不调用红→修复后独立兜底绿；N2 改写前后对照留痕）；红⑥ C6-002 REE 红（拒绝型 executor 注入——缺陷态资源泄漏+RUNNING 红→修复后终态化+幽灵链结算绿）；红⑦ C6-009 吞没红（saveTaskState 终态写 DB 故障注入——缺陷态零日志静默红→修复后 ERROR 留痕+行由裁定①可接管绿）；红⑧ C7-011 旁路红（sys-event 链重叠触发断言守卫不经过——缺陷态零防护红→修复后守卫接线绿）。
- 联调纪律显式守约（本批六条）：**C7-001×MA-23 fencing 对齐**（2514 裁定⑥引用不重建 + 键族同构 + 修订登记义务）；**C6-002/C6-009×R1-01/2381 已修面零回退**（终态化契约不回归）；**C6-009×族 A2 关系成文**（紧随 P1 族同批）；**C8-001×C8-009 次序约束**（A4 成文 + R4-12 就绪注记）；**C5-001×G79 双轨联动**（R3-04 位点对齐）；**C8-002×biz 链穿透接缝**（共享位点封闭、C8-010 本体归 R4-12）。
- Owner doc 随批更新：`docs-for-ai/03-modules/nop-batch.md`（RUNNING 死区/fencing 契约节[对齐 2514 裁定⑥归属声明]、dispatch/batchSize 护栏契约节、excel-reader 规模边界声明、fetchSize 语义声明、终态写失败可见性契约句、sys-event 链守卫覆盖声明、源码锚点补行）+ `docs-for-ai/04-reference/source-anchors.md`（按实际）。
- 下游就绪义务：批末向 R4-11（8.51）/R4-12（8.52）回写硬依赖就绪注记（R4-12 注记**必须含 C8-001 Guard 先于 C8-009 参数化的前置已满足声明** + 裁定①③⑧与 R4-12 成员 C7-012/C8-010/C5-009 的接缝边界、C10-005/006/007/008/009/012 载体面归属确认）；向 R3-04（若未收口）注记裁定④位点 SSOT 指向。
- E1–E6 通用门禁全过（batch-proposal §一）。

## Non-Goals

- **不做 R4-11 成员**（G23 前半 11 项：1.7-C1-001/C1-002/C1-003/C1-006/C5-003/C5-004/C6-003/C6-004/C6-007/C6-008/C7-004）——硬依赖本批，本批零承接零顺手修。
- **不做 R4-12 成员**（G23 后半 11 项：1.7-C7-005/C7-006/C7-007/C7-008/C7-009/C7-012/C8-004/C8-007/C8-008/C8-009/C8-010）及其 C10 载体面——特别地 **C8-009**（幽灵旋钮参数化）受裁定②次序约束（本批只落 Guard 与就绪声明）；**C8-010**（biz 钳位下界本体）只随裁定③封闭共享钳位位点并登记接缝；**C7-012**（KILLED 吸收态/恢复入口）只随裁定①登记同一状态机写者接缝；**C5-009**（batchSize 上界 P3）同 A3 族注记不代做。
- 不做已修面：C6-001/C6-010（plan 2496，`BatchTask` catch Throwable/守卫首行/primaryError 零触碰零回退）、C7-003（plan 2502 保留清理，owner doc §账本保留策略面零触碰）、C7-002 B 面（已随 C6-001 闭合）。
- 不做 G21 已收敛部分（C6-010）与 dormant/条件面成员；不做 1.7 单元 P3 成员（C5-006 仅随裁定④顺带评估、C5-007/C5-008/C5-009、C7-013/C7-014/C7-015/C7-016、C8-005 仅随裁定②同位点吸收面、C8-006 等）。
- 不触碰 nop-job/nop-sys 侧改码（C7-011 跨单元前提划出不立案）；不触碰 `batch.xdef`/`export-db.xdef`/`import-db.xdef`（裁定②③ xdef 分支若采须批内登记；预设零 xdef 触碰）；`_` 前缀生成物（裁定①迁移脚本为非 `_` 前缀新建）；R4-11/R4-12/R3-04 批外成员本体；不做 Deferred 节所列项。

## Scope

### In Scope

- `nop-batch/nop-batch-dao/src/main`：`store/DaoBatchStateStore`（裁定① stale 谓词/接管/终态写面）+ `nop-batch/model/nop-batch.orm.xml`（**仅当裁定①采租约列分支，PA 批内申报载体**）+ 配套迁移脚本（非 `_` 新建，仅该分支）+ 测试（N1 参数化改写 + C10-002 裁定①半）。
- `nop-batch/nop-batch-core/src/main`：`impl/BatchTask`（裁定⑥ REE 封闭 + 裁定⑦清理段显式化 + C7-002A 幽灵链结算）+ `core/loader/PartitionDispatchQueue` 等（裁定② Guard）+ `core/BatchTaskBuilder`/`BatchDispatchConfig`（裁定②装配面）+ `processor/BatchChunkProcessor`（裁定③钳位 WARN）+ 测试（C10-001/003 + N3）。
- `nop-batch/nop-batch-dsl/src/main`：`manager/FileBatchSupport`（裁定④ maxCount 接线分支）+ `manager/JdbcBatchSupport`（裁定⑤联动面）+ `manager/ModelBasedBatchTaskBuilderFactory`/`runner/BatchTaskRunner`（裁定⑧ (b) 分支如采）+ 测试（N2 改写 + C10-005 半边）。
- `nop-batch/nop-batch-jdbc/src/main`：`loader/JdbcBatchLoaderProvider`（裁定⑤坏门）；`nop-batch/nop-batch-exp/src/main`：`ExportDbTool`（裁定③守卫对称）；`nop-batch/nop-batch-sys/src/main/resources`：`non-broadcast-consumer.batch.xml`（裁定⑧ (a) 预设分支）。
- 跨模块站点（分支触发制）：`nop-report/nop-report-core` 的 `ExcelRecordInput`（仅裁定④ (a) 游标分支；Scope 申报 + E3 增补 `./mvnw test -pl nop-report -am`）。
- 设计文档：`ai-dev/design/concurrency/batch-running-deadzone-fencing-and-parameter-guards.md`（新建；裁定①–⑧+测试缝+零回退钉，最终状态写法）。Owner doc：`docs-for-ai/03-modules/nop-batch.md`、`docs-for-ai/04-reference/source-anchors.md`（按实际）。

### Out Of Scope

- Non-Goals 所列全部项；R4-11/R4-12/R3-04/dormant/P3 批次工单；nop-kernel/nop-xdefs 平台设施本体（xdef 下界/缺省值修订为备选分支，若裁定采须批内登记）；nop-job/nop-sys 侧改码；`_` 前缀生成物手改。

## Execution Plan

### Phase 1 - batch RUNNING 死区 fencing 与参数护栏设计裁定文档 + 独立评审

Status: done
Targets: `ai-dev/design/concurrency/batch-running-deadzone-fencing-and-parameter-guards.md`（新建）

- Item Types: `Decision`（设计文档，本批唯一设计裁决权威）、`Proof`（独立评审记录）

- [x] 设计文档落 `ai-dev/design/concurrency/`（最终状态写法，rule 14），含以下八项裁定+测试缝，每条唯一预设默认分支：
  - [x] **裁定① C7-001 fencing/stale 判定**：2514 裁定⑥形态菜单投影声明（引用不重建；偏离即回 2514 修订登记）；stale 谓词形态一次定夺——(a) 租约列分支（orm plan-first 申报：列名/类型/迁移脚本三行沿 2514 迁移方案三行模板）vs (b) `restartTime`+租约超时条件（零 orm 触碰，预设默认分支候选）；`tryMarkTaskRunning` WHERE 扩展与 rowsAffected==1 单飞判据；既有快失败守卫（:123-129）事中兜底零回退与双层并存声明；接管后行内状态原子刷新面；实体写路径列集约束（沿 2514 评审⑤增补——防陈旧实体全字段 save 覆写抢占列）；运维 force-kill action 备选分支呈现；N1 三用例参数化改写方案（live/stale 两态构造）**前置**成文；C7-012 接缝（同一状态机写者，本体归 R4-12）；时序 A/B 双路径测试形态
  - [x] **裁定② C8-001 dispatch Guard**：三层防线一次定夺（builder fail-fast[预设] / 队列构造 Guard / xdef min）；错误码与配置上下文形态；orm-reader fail-fast 终态口径（复核修正①）；cancel 第二释放入口断言形态；C8-005 同位点吸收面；A4 次序约束成文（C8-009 前置声明义务）；N3 latch 超时搭车方案
  - [x] **裁定③ C8-002 守卫对称**：Export `>0` 守卫（预设）+ adjustBatchSize 钳位 WARN vs 上移 Guard（一次定夺）；A3 共享位点封闭边界与 C8-010 接缝（本体归 R4-12）；放大面断言形态
  - [x] **裁定④ C5-001 物化包络**：(a) 游标式改造（nop-report 触碰申报 + E3 增补）vs (b) 包络声明+maxCount 接线（物化面残留登记 R-G 观察项）——一次定夺；C10-011 回归钉形态（两分支均强制）；C5-006 顺带评估边界；G79 双轨联动位点清单（R3-04 消费方声明）
  - [x] **裁定⑤ C5-002 fetchSize 门**：门条件独立兜底形态 + WARN 路径；N2 改写方案**前置**成文（缺省语义变更前后对照）；C10-005 半边运行时断言形态（记录型 Proxy）；变体 B/autocommit 登记边界（归 R4-12 注记）
  - [x] **裁定⑥ C6-002 REE 封闭**：提交点防御 + futures 循环包络形态；C7-002A 幽灵 futures 显式结算/取消语义；与裁定⑦共享 onTaskComplete 直达链；零回退钉清单（:260-268 注释钉/primaryError/owner doc §任务异常终态契约）；C10-001 断言形态（Error+REE 双注入）
  - [x] **裁定⑦ C6-009 清理段显式化**：S4/S5/S6 逐点处置表（捕获+ERROR 留痕/complete 必达防御/X 不替换原始 e）；族 A2 闭环声明（终态写失败行残留由裁定① stale 接管承接）；与裁定①同设计文档配套声明
  - [x] **裁定⑧ C7-011 守卫覆盖**：三分支一次定夺（配置补 saveState+taskKeyExpr[预设] / singleActive 解耦声明 / owner doc 契约显式化）；UUID 绕过面处置边界；跨单元前提划出声明；job.yaml 缺省关闭条件面标注
  - [x] **裁定⑨ 测试缝与零回退钉清单**：红①–红⑧ 测试形态逐条（含缺陷态红判据）；零回退钉 = `BatchTask.java:260-268`（plan 2496 C6-001/C6-010 注释钉）、plan 2381 primaryError 注释钉（:50-51/:209-214/:279-281）、owner doc §任务异常终态契约/§账本保留策略 成文契约、`TestDaoBatchStateStore` 闸门正确面 7 例（N1 改写仅限三用例参数化、其余零触碰）、`TestBatchBugVerification`/`TestBatchTaskCheckpoint` 相邻钉；补测类标注「新增断言即新钉、缺陷态红→修复后绿」
- [x] 设计文档独立 draft review（独立子 agent，含想象性分析——按 plan guide「迭代对抗性审查」模板）通过后，Phase 2 方可启动——记录于本 plan 头部 Draft Review 行或 daily log
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 设计文档存在于 `ai-dev/design/concurrency/`、为最终状态写法、含裁定①–⑨全部项且每条有唯一预设默认分支，独立 draft review 记录在案
- [x] 裁定①含 MA-23 引用不重建条款与 orm PA 申报分支（或零 orm 声明）；裁定②含 A4 次序约束；裁定③含 C8-010 接缝；裁定④含 C10-011 强制回归钉与 G79 联动；裁定⑤含 N2 前置改写方案；裁定⑥含 C7-002A 吸收与零回退钉清单；裁定⑦含族 A2 闭环声明；裁定⑧含跨单元划出声明
- [x] `No owner-doc update required`（本 Phase 仅设计文档；owner doc 更新归 Phase 3 统一落账——显式登记该去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 8 成员实现 + 回归测试

Status: done
Targets: `nop-batch/nop-batch-{dao,core,dsl,jdbc,exp,sys}/src/`（+分支触发制 `nop-report/nop-report-core/src/`）——各自 main + test 树

- Item Types: `Fix`（6 项 P1 + 2 项 P2 搭车均为已确认 live defect/批前裁定搭车，不得写成 Feature）、`Proof`（单元测试 + C10 收口门）

- [x] 组 core/dao：C7-001（裁定① fencing/stale + N1 参数化改写前置）+ C6-009（裁定⑦ 同变更集——族 A2 配套）+ C10-002；C6-002（裁定⑥ REE 封闭 + C7-002A 结算）+ C10-001
- [x] 组 core 护栏：C8-001（裁定② Guard）+ C8-005 吸收面 + C10-003 + N3 搭车；C8-002（裁定③，core 侧钳位 WARN 半）+ C10-004
- [x] 组 dsl/jdbc/exp/sys：C5-001（裁定④）+ C10-011 + C5-006 顺带评估；C5-002（裁定⑤ + N2 改写前置）+ C10-005 半边；C8-002 exp 侧守卫（裁定③ exp 半，与 core 半同变更集）；C7-011（裁定⑧）随 C10-002 同 diff
- [x] 回归测试（rule 25 显式覆盖 + C10 收口门同 diff，先红后绿或变异/回退论证留痕）：
  - [x] 红①+红⑦+红⑧（组 core/dao+C10-002）：死区接管 + 终态写失败留痕 + sys-event 守卫接线——缺陷态各自红 → 修复后绿
  - [x] 红⑥（组 core/C10-001）：REE 注入资源泄漏/RUNNING + Error 注入并存——缺陷态红 → 修复后终态化+幽灵链结算绿
  - [x] 红②+红③（组 core/exp/C10-003+C10-004）：dispatch 护栏 fail-fast + export 守卫对称——缺陷态红 → 修复后绿
  - [x] 红④+红⑤（组 dsl/jdbc/C10-011+C10-005 半边）：excel 物化断言 + fetchSize 运行时生效——缺陷态红 → 修复后绿（N1/N2 改写前后对照留痕随红①红⑤）
- [x] **端到端验证**（rule 22）：≥1 条批任务全链（DSL 装配→setup→chunk 循环[含 dispatch 派发]→终态保存→[失败路径]终态写失败留痕→[崩溃模拟]重启 stale 接管，覆盖裁定①⑥⑦；saveState=true 持久化形态）；≥1 条读侧链（excel/jdbc reader→物化/fetchSize 断言→chunk 消费，覆盖裁定④⑤；含 guard 用例 dispatch/exp 面）
- [x] **接线验证**（rule 23）：裁定① stale 谓词真实被 `tryMarkTaskRunning` 条件 UPDATE 消费且接管分支 rowsAffected 断言（非仅 SQL 存在）；裁定② Guard 真实被 buildLoader 装配链调用断言；裁定③ WARN/守卫真实被缺省配置路径触发断言；裁定⑥ futures 包络真实在 REE 注入下结算全部已提交链断言；裁定⑧ saveState 接线后守卫真实经过断言
- [x] **无静默跳过**（rule 24）：裁定①接管失败显式（保留既有快失败异常语义，非静默放行）；裁定⑥REE 路径显式结算（非吞）；裁定⑦终态写失败显式 ERROR+计数（非空 catch）；裁定③钳位 WARN 非静默；新增公共分支无空方法体/TODO
- [x] **提交纪律**：设计文档先行单独提交；按组提交——core/dao / 护栏 / dsl/jdbc/exp/sys，C10 测试与对应本体同一 commit（裁定①⑦同变更集、裁定③两半同变更集）；组间无耦合时可独立提交序列
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 8 项成员全部 landed，各带 ≥1 条 focused 回归测试且测试名可辨识对应工单；C10 五条收口门同 diff 落地；N1/N2/N3 三项义务履行并留对照痕
- [x] 必红证据（E2）：红①–红⑧均先红后绿或变异/回退论证，快照落 `_tmp/` 并登记
- [x] 零回退实证：零回退钉清单全部保持绿（钉零回改逐条 git diff 为空核对；唯一允许的钉改写 = N1 三用例参数化 + N2 缺省语义断言，改写前后语义对照留痕登记在案）
- [x] **端到端验证 / 接线验证 / 无静默跳过**（rule 22/23/24）：本 Phase checklist 内对应三项已逐条核验并勾选
- [x] `./mvnw test -pl nop-batch -am` BUILD SUCCESS（裁定④ (a) 分支时增补 `./mvnw test -pl nop-report -am`）；上游环境抖动按 Baseline 勘误 5 三联裁定处理并留痕；Owner doc 内容定稿归 Phase 3 统一落账（显式登记去向）
- [x] `ai-dev/logs/` 对应日期条目已更新

**Phase 2 偏差登记（2026-10-30，TestBatchTaskDsl 快照失败裁定）**：裁定①落地后 `TestBatchTaskDsl#testBatchTask` 在 nop-batch 六模块 reactor 门禁红（solo `-pl nop-batch-dsl` 假绿——离线解析到本地仓库旧版 nop-batch-dao 构件；以 reactor 内 dao 为准）。根因判定 = **fencing 语义正确、非产品缺陷**：saveTaskState 条件 UPDATE 的 exec_count fencing 与 insert-path（DB=实体=1）一致，DSL 流状态写全部落盘（END_TIME/RESTART_TIME 在 DB 非空即条件 UPDATE matched 的直接证据；六模块门禁日志唯一一次 `task-state-write-lost-fencing` 来自新增钉 testSaveTaskState_zombieWriterWriteLost 的预期 zombie 分支）。失败三症状均为 AutoTest 用例表未随裁定①新行为同步：(1) RESTART_TIME 期望空 = 旧契约，新契约 insert 即持租约 + checkpoint 续租 → 恒非空；(2) END_TIME 的 `@var:NopBatchTask@endTime` 变量依赖 ORM 实体 hook（AutoTestOrmHook clock 列 dirty 属性）注册，条件 UPDATE 绕过该 hook → 变量不再注册；(3) VERSION=11 为旧实体写路径（每 checkpoint ORM 乐观锁递增）产物，条件 UPDATE 绕过 VERSION。修复落点 = 用例表同步（裁定①套件义务，路径 (b)）：`nop-batch/nop-batch-dsl/_cases/io/nop/batch/dsl/TestBatchTaskDsl/testBatchTask/output/tables/nop_batch_task.csv` END_TIME/RESTART_TIME 改 `@notNull:` 非空断言、VERSION 11→0；模式名大小写敏感（`@notNull:`，FilterOp 构造名，非枚举名 NOT_NULL）。设计文档裁定①补「观测面影响」句（同日勘误）；owner doc 契约句归 Phase 3。红绿留痕 `_tmp/red-green-2516-fencing-fix-{red,green}.log`；验证：TestBatchTaskDsl solo（dao reactor 在环）绿×3、六模块门禁 `./mvnw test -pl nop-batch-{core,dao,dsl,exp,jdbc,sys} -Dsurefire.rerunFailingTestsCount=2` EXIT=0（`_tmp/2516-gate-nop-batch-modules-r2.log`）、TestDaoBatchStateStore（nop-batch-dao，N1+四新钉）EXIT=0。


- 偏差登记（实施期）：① TestBatchTaskDsl AutoTest 用例表随裁定①新行为同步（RESTART_TIME 恒非空/END_TIME 条件 UPDATE 绕 ORM hook 变量注册改 @notNull/VERSION 钉插入态 0——fencing 经证正确[DB 行 END_TIME/RESTART_TIME 非空即条件 UPDATE matched 直接证据]，六模块门禁 EXIT=0；solo -pl 单模块假绿陷阱登记[离线解析旧版 dao 构件]）；② task 组与续作代理两轮接力完成（站点 28 文件）；③ -am 门禁三次：r1 中止于 nop-task-core 2514 C5-003 逐出测试负载 flake（solo 绿 `_tmp/2516-eviction-solo.log`，该钉间歇误红登记 watch 归 2514 owner）、r2 debezium fork timeout、r3 EXIT=0（MFA flake rerun 自愈）；④ E3 快照：模块级六模块 EXIT=0（`_tmp/2516-gate-nop-batch-modules-r2.log`）+ -am r3 EXIT=0（`_tmp/2516-gate-nop-batch-am-r3.log`）。

### Phase 3 - 全量验证 + owner doc 更新

Status: planned
Targets: nop-batch 聚合全量测试；`docs-for-ai/03-modules/nop-batch.md`；`docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Proof`

- [ ] E1 全量构建：`./mvnw clean install -T 1C` BUILD SUCCESS（或授权支路 `-DskipTests` 快速通道 + 聚焦测试组合覆盖，支路理由随执行记录声明——沿 plan 2497–2515 Phase 3 先例）
- [ ] 批行验证命令（E3）：`./mvnw test -pl nop-batch -am` BUILD SUCCESS 且登记（模块/测试数/退出码/快照路径；裁定④ (a) 分支增补 nop-report 支路）；环境抖动按三联裁定留痕；实际触达模块以 git diff 为准逐条登记；零触碰确认：产品 diff 不含 `_` 前缀生成物手改（裁定①租约列分支的 orm 源模型+迁移脚本+codegen 重产物除外，须逐项登记为申报面落地）、R4-11/R4-12/R3-04 批外成员本体、plan 2496/2502/2381 已修面零 diff 核对登记；`nop-batch.orm.xml` 变更限于裁定①申报分支（或零 diff）〔`src/main/resources/_vfs/` 下 xdsl 资源为源模型非生成物，不受 `_` 前缀生成物禁令约束（draft review R1 Minor-3）〕
- [ ] Owner doc 更新：`nop-batch.md`——新增「RUNNING 死区与 fencing 契约（plan 2516 R2-13）」节（stale 判定/接管语义/运维边界，「以设计文档与 plan 2514 裁定⑥为准」归属声明）、dispatch/batchSize 护栏契约句（Guard fail-fast + 钳位 WARN 可观测）、excel-reader 规模边界声明（裁定④）、fetchSize 语义声明（裁定⑤）、终态写失败可见性契约句（裁定⑦，并入 §任务异常终态契约节）、sys-event 链守卫覆盖声明（裁定⑧）、§源码锚点补行；`source-anchors.md` 新增锚点（按实际）
- [ ] 工具门禁：`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-batch --severity high` 退出码 0（批前既有发现逐条裁定登记）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [ ] E1 + 批行验证命令 BUILD SUCCESS 且逐条登记（模块/测试数/退出码/快照路径）；支路（如有）已声明理由
- [ ] nop-batch.md 落点 + source-anchors.md 锚点；check-doc-links --strict 退出码 0；scan-hollow 逐条登记（触达面 high=0 或批前既有已裁定）
- [ ] 零触碰确认登记（生成物/R4-11/R4-12/R3-04 批外成员本体/2496·2502·2381 已修面/orm 申报面边界）
- [ ] `ai-dev/logs/` 对应日期条目已更新


### Phase 4 - closure audit + 收口

Status: planned
Targets: 本 plan、roadmap 8.22 行、cra-index 台账、R4-11/R4-12 就绪注记

- Item Types: `Proof`

- [ ] 独立子 agent closure audit（fresh session，不复用实现 session）：逐条核对全部 Phase Exit Criteria 与 Closure Gates 对 live repo 的成立性，含 Anti-Hollow 检查（stale 接管/Guard fail-fast/守卫对称/物化包络/fetchSize 兜底/REE 结算/清理段留痕/sys-event 接线运行时真实生效、无空方法体/静默吞异常）
- [ ] cra-index 台账回填：R2-13 八行状态收敛（6 项 P1 open→fixed + 2 项 P2 搭车消号，注记 plan 2516；吸收成员[C5-005/CL-008/CL-038/C7-002A]随各主行括注；P2-G21 束残余两成员收敛注记）；C10 五条收敛注记；roadmap 8.22 行 Status → done；R4-11/R4-12（8.51/8.52）硬依赖就绪注记（**R4-12 注记含 C8-009 参数化前置已满足声明 + C7-012/C8-010/C5-009 接缝边界 + C10 载体面归属确认**）；R3-04 裁定④位点 SSOT 注记（按需）
- [ ] 双门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0 + scan-hollow 全绿复核；Closure Evidence 写入本 plan `## Closure` 段（rule 27）；`ai-dev/logs/` 收口条目已更新

Exit Criteria:

- [ ] closure audit 证据（Reviewer/Agent 标识、每条 Exit Criterion 与 Closure Gate 的 PASS/FAIL + 证据来源）已写入 `## Closure` 段
- [ ] cra-index 七行收敛+P2-G21 束行注记 + C10 注记、roadmap 8.22 = done、R4-11/R4-12 就绪注记完成（R3-04 按需）
- [ ] 双门禁退出码 0 登记在案
- [ ] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [ ] 8 项成员全部修复：C7-001（RUNNING 死区 stale 接管）、C6-009（终态写失败显式可见）、C6-002（REE 裸逃逸封闭 + 幽灵链结算）、C8-001（dispatch 容量 Guard fail-fast）、C8-002（Export/Import 守卫对称 + 钳位可观测）、C5-001（excel 物化包络 + C10-011 回归钉）、C5-002（fetchSize 门兜底 + N2 改写）、C7-011（sys-event 链守卫接线）
- [ ] batch-proposal R2-13 行 exit criteria 增补六段逐条达成并留证：①**C7-001 测试 N1 参数化改写前置**；②**C5-002 测试 N2 同步改写**；③**C5-001 C10-011 补回归钉**；④**C8-002 biz 链穿透共享位点封闭（C8-010 归 R4-12 接缝登记）**；⑤**C6-002 REE 裸逃逸封闭（C7-002A 搭车）**；⑥**C6-009 紧随 RUNNING 死区族、C7-011 A2 注记搭车（同批落地）**
- [ ] 对齐纪律守约：C7-001×MA-23 fencing 对齐（2514 裁定⑥引用不重建 + 键族同构 + 修订登记义务落地）；C6-002/C6-009×R1-01/2381 已修面零回退；C6-009×族 A2 关系成文；C8-001×C8-009 次序约束成文；C5-001×G79 双轨联动；orm PA 批内申报闭环（裁定①若采租约列分支）
- [ ] 每成员 ≥1 条 focused 回归测试（rule 25）；红①–红⑧均有先红后绿或变异/回退论证（E2）；C10 五条同 diff 落地；N1/N2/N3 义务履行
- [ ] 设计文档裁定①–⑨成文且经独立 draft review；既有钉（零回退钉清单全列）零回退实证（唯一钉改写 N1/N2 登记在案）
- [ ] 全量构建绿：`./mvnw clean install -T 1C`（或授权支路已声明）+ 批行验证命令 `./mvnw test -pl nop-batch -am` BUILD SUCCESS（E1/E3；上游环境抖动三联裁定留痕；裁定④ (a) 分支增补 nop-report 支路）
- [ ] Owner doc 更新完成：nop-batch.md（+source-anchors.md 按实际）（E5）
- [ ] 受影响聚合模块 `scan-hollow-implementations.mjs --severity high` 触达面 high=0（或批前既有发现逐条裁定登记）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（Deferred 节条目均为入批前已裁定的批外项，successor 清晰）
- [ ] 独立子 agent closure-audit 已完成并记录证据（rule 12/27）
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）stale 接管/Guard/守卫对称/包络/兜底/REE 结算/留痕/接线运行时真实生效（不只类型系统），（b）无空方法体/静默跳过/no-op 作为正常实现
- [ ] `./mvnw compile` + `./mvnw test`（nop-batch 聚合）；checkstyle / 代码规范检查通过（含 pre-commit lint gate 如触发）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] cra-index 台账 L182–L188 七行收敛 + P2-G21 束行注记（勘误 7：C6-009 无独立行，唯一载体=P2-G21 束行 L405） + roadmap 8.22 → done + R4-11/R4-12 就绪注记（E6）

## Deferred But Adjudicated

### R4-11/R4-12 成员（G23 前后各 11 项，含 C7-012/C8-009/C8-010/C5-009 及其 C10 载体面）

- Classification: `out-of-scope improvement`（batch-proposal L126/L127 显式安排，非本批降级）；Successor Required: `yes`；Successor Path: R4-11（roadmap 8.51）/R4-12（roadmap 8.52），均硬依赖 8.22——本批 done 后就绪
- Why Not Blocking Closure: 判级对齐零重复——cra-index 台账 G23 行批次列=R4-11/R4-12；本批已收敛 1.7 单元全部 6 项 open P1 + P2 搭车 C6-009/C7-011，下游实施以本批后的 live 形态为基线，且可引用本批裁定①②③⑧接缝与 A4 次序声明为既有裁定不重开体。

### P3 成员与 R-G 观察项（1.7-C5-006 顺带评估外的 P3 族、裁定④ (b) 分支下的物化面残留）

- Classification: `out-of-scope improvement`（P3 成员批次列=R4-11/R4-12/治理批）+ `watch-only residual`（物化面残留，仅裁定④采 (b) 分支时成立）；Successor Required: `yes`；Successor Path: R4-11/R4-12/R-G
- Why Not Blocking Closure: 批次列为批前裁定；物化面残留若发生，其包络声明与规模边界已在 owner doc 成文且 C10-011 回归钉在场，不影响声明包络成立。

## Non-Blocking Follow-ups

- **R4-11/R4-12/R3-04 对接注记**：本批落地后向 roadmap 8.51/8.52 行作就绪注记（硬依赖解除落点；含 A4 次序声明与裁定①③⑧接缝引用指向）；R3-04 按需位点注记；三批实施引用本设计文档为既有裁定。
- **fencing 平台化上收**：若后续批需要跨模块通用扫描抢占原语（nop-kernel/nop-api-core 层），属后续平台批独立申报事项（2514 裁定⑥申报触发条件同款），不阻塞本批 closure。
- （占位）执行期发现的小额治理项（如命名/注释/测试基建微整）在此登记，均不得承载 in-scope live defect。

## Closure

Status Note: （closure 时填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent closure audit 填写）
- Evidence: （待填写）

Follow-up:

- （待 closure 时填写；confirmed live defect 不得出现在这里）

## Risks And Rollback

- **裁定① stale 接管误伤风险**：stale 谓词过宽可能接管仍在执行的活实例（长任务 > 租约期）——租约缺省值与续租语义（是否续租、由谁续）随裁定①成文并给包络声明；回滚 = revert 裁定①变更集（N1 三用例改写随 revert 一并回退，对照痕保留）。
- **裁定⑥⑦ 清理段重排风险**：onTaskComplete 清理段为 plan 2496/2381 双重已修面，重排 finally 顺序时须保「future 必结算 + afterComplete 必达 + primaryError 归因」三契约不回归——变异探针纪律适用（注释/删除目标行→跑钉子→恢复→再跑，恢复以断言方式确认）。
- **裁定④ nop-report 跨模块风险**：游标式改造触碰 `ExcelRecordInput` 后，`ExcelHelper.readAllSheets` 等全量 API 消费方（C5-001 F3 维度集外兄弟，登记为同族观察项）行为面须回归——E3 增补 nop-report 支路覆盖；(b) 分支则零风险但物化面残留须在 owner doc 显式声明，不得写成「已修复内存问题」。
