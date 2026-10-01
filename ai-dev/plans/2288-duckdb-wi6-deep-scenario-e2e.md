# 2288 DuckDB WI6 — 深度场景端到端测试

> Plan Status: completed
> Last Reviewed: 2026-10-01
> Source: `ai-dev/backlog/duckdb-integration-roadmap.md` WI6；WI4 单写者语义与外存实测；WI5 对拍矩阵与 control 档位知识；对抗审查 agent_89cb75d6（两轮收敛：B1/B2/M1 全部按处方修复，EXECUTABLE）
> Related: 2286（WI4）、2287（WI5）、2284（WI2 xlsx 桥）

## Purpose

把 WI1-WI5 的组件在三条深度场景档位下端到端贯通：大负载外存档（低预算 + 可写 temp 目录下不 OOM 完成）、端到端 pipeline（xlsx/csv 摄取 → parquet → SQL 步骤链 → 结果文件/写回本地库）、并发档（任务级并行与冲突语义）。全部为 Proof 项，不新增生产特性。

## Current Baseline

- **外存档位知识（WI4/WI0 实测）**：64MB 预算对 DuckDB 过紧（~9MB 负载也因 30.5MB 连续块分配失败）；20M 窄行（~180MB）vs 64MB 预算必然触发外存路径；可写 temp 目录 + 充足预算下 spike Q2 已证明溢出完成（_tmp/duckdb-spike Phase2EngineQuestionsTest.q2）。WI4 的 TestSpillEngine（64MB + 不可用 spill 位点）与故障注入形态已固化
- **任务链能力（WI3）**：DuckDbSqlTaskStep 经 `<step type="simple" bean>` 扩展点接入，输入 sql/params/ingestCsvPath/ingestParquetPath/exportPath+exportFormat/resultTable/dbPath，多步经 resultTable + 同 dbPath 共享表；`<retry>`/timeout 一等公民兼容（pipeline.task.xml 先例）
- **xlsx 入口桥（WI2）**：DuckDbFiles.readXlsx（tablesaw XlsxReader → 临时 CSV 中转）——是 API 不是 step 输入，pipeline 的 xlsx 摄取 = 桥转 CSV 后走 task step（裁定，避免 Proof 项夹带生产特性）
- **并发语义（WI4）**：独立文件并行 engine 级全绿；同 JVM 同文件多连接安全（OPEN_FILES 引用计数）；**跨进程才有锁冲突**（file-locked bizFatal 语义 + LockHolderMain 跨 JVM 测试先例）——任务级"同文件冲突"场景 = 外部进程持锁时任务失败语义，同 JVM 双任务同文件是合法并发非冲突
- **SQLite ATTACH 可行性已实测（本 plan 起草时探针）**：duckdb_jdbc 1.5.6.0 捆绑 parquet/json（loaded），sqlite_scanner 未捆绑但**首用自动安装**（本机已完成并缓存于 ~/.duckdb/extensions/v1.5.6，后续离线可用）；ATTACH 'x.db' (TYPE sqlite) 建表/插数/查询/DETACH 全链 OK，产物为真实 SQLite 3.x 文件。测试据此固化（首机需网络自动下载扩展，已在本机预热）
- nop-task 任务级并行：task.execute 在多线程分别用各自 TaskFlowManagerImpl/runtime 即可并行（TaskFlowManagerImpl 字段均 ConcurrentHashMap/AtomicBoolean，共享或各自实例均可）
- **DuckDB 实例生命周期（对抗审查 B1 实证，duckdb_jdbc 1.5.6 字节码）**：DuckDBConnection 每连接独立 startup，实例仅在连接**存活期重叠**时经 native instance cache 共享（testSameJvmMultiReadSingleWrite 的通过机制）——步骤间连接全关则实例销毁，ATTACH 目录态不落 .duckdb 文件，跨步骤不可见；驱动 prepareStatement 走 native 单语句 prepare（多语句必拒），step 无 createStatement 通路——**写回锚定连接方案**：测试持一条裸 JDBC 连接钉住同 dbPath 的共享实例贯穿任务全程，ATTACH 步与 INSERT 步即见 sq；产物 .db 独立复读（新连接内 createStatement 顺序 ATTACH+SELECT）
- **xlsx 桥接线（对抗审查 B2 实证）**：readXlsx 的内部临时 CSV 用后即删且需活连接——pipeline 接法写死：测试经 engine 连接用 readXlsx 把 xlsx 摄入 pipeline 库表 t_xlsx（WI2 桥 API 的正当用法），task 步骤 1 将 t_xlsx exportPath 导出桥 CSV，步骤 2 起 ingestCsvPath 接续（xlsx=入口、桥=API、其后全为真实 step）
- **spill 观测机制（对抗审查 M1 裁定）**：任务同步执行 + DuckDB 即时清理 temp 文件，事后 Files.list 必假阴性——观测改为：task.execute 放工作线程，主线程轮询 spill 目录直到任务结束，断言"出现过条目"标志；预算定为 **128MB**（64MB 过紧会块分配失败、256MB 装得下 160MB sort 不溢出，128MB 高于连续块分配下限且低于 sort 工作集，溢出由数据集数学保证）

## Goals

- 大负载外存档：低 memory_limit（128MB）+ 可写 temp_directory 下，~160MB sort 工作集（20M 窄行）的聚合/join/sort 任务链全部完成且结果正确（不 OOM、不静默截断，观测到 spill 发生）
- 端到端 pipeline：xlsx 桥摄取 → CSV → task 步骤链（过滤 → 聚合 → parquet 导出 → parquet 回读聚合）→ 结果文件 + 双写回（SQLite ATTACH 直写 + 业务表经 ORM）全部经真实 task XML 步骤链贯通
- 并发档：独立 .duckdb 文件的两个任务多线程并行全绿；外部进程持锁文件的步骤任务按 WI4 语义失败（file-locked bizFatal）
- 连接句柄无泄漏的可观测代理：N 轮 open/query/close 循环后同文件可被新 engine 立即打开（锁已释放）、engine.close 后全部 tracked connection 关闭

## Non-Goals

- 性能指标（WI7 立基线）；真实 ENOSPC 注入（WI4 Deferred 裁定维持）；生产代码/step 输入扩展（xlsx 摄取走 WI2 桥）
- 多进程任务调度器；`memory_limit` 自动调优

## Scope

### In Scope

- 测试资源：`_vfs/nop/duckdb/task/deep-spill.task.xml`（外存档步骤链）、`e2e-pipeline.task.xml`（摄取→过滤→聚合→parquet 导出→parquet 回读→结果导出）、`e2e-pipeline-writeback.task.xml`（SQLite ATTACH 写回）、`locked-file.task.xml`（持锁冲突）、beans：`TestSpillTaskEngine`（128MB + 可写 temp 目录）与对应 step bean、xlsx 测试资源（沿用 WI2 既有 xlsx 资源或生成）
- 测试：TestDuckDbDeepSpill（外存档）、TestDuckDbE2EPipeline（pipeline + 写回）、TestDuckDbTaskConcurrency（并发档 + 句柄循环）
- 当日 ai-dev/logs/ 条目

### Out Of Scope

- 生产代码改动；nop-task-core/ext 改动；CI；性能计时（WI7）

## Execution Plan

### Phase 1 - 大负载外存档

Status: completed
Targets: `nop-duckdb/src/test/`

- Item Types: `Proof`

- [x] `deep-spill.task.xml` 步骤链：ingestCsvPath（~20M 行窄行 CSV，测试内生成 ~160MB；执行修正：ingest-only 步骤也必须带 sql 输入（WI3 契约 sql 必填），补 `SELECT count(*) FROM t_x`）→ 过滤聚合步（GROUP BY grp）→ join 步（第二张表）→ ORDER BY sort 步 → 导出步（exportPath csv）；每步 timeout=300000
- [x] TestDuckDbDeepSpill：任务在 128MB 预算 + 可写 spill 目录下全链完成（@Timeout(600)；计时探针实测：整套含 20M 行 CSV 生成仅 ~5s，M 系硬件余量巨大；执行修正：join 维表的 g 列与主表类型对齐为数值），导出结果行数/值与 golden 常量一致（防静默截断）；**spill 观测 = task.execute 放工作线程、主线程轮询 spill 目录直到任务结束、断言出现过条目**（事后检查必假阴性——DuckDB 块释放即删 temp 文件）
- [x] 连接句柄代理检查：50 轮 open/query/close 同一文件循环全成功；循环结束后**裸 DriverManager**（避免 config-fingerprint 冲突，审查 m3）立即打开同文件成功（前序连接全部释放、锁无残留）

Exit Criteria:

- [x] 外存档任务全链完成且结果 golden 一致（无 OOM、无静默截断），spill 产物出现被断言
- [x] 50 轮句柄循环全绿 + 锁释放代理断言通过
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0（Phase 1+2 收口门一并复跑，51/51）
- [x] No owner-doc update required（WI9 承接）
- [x] 当日 `ai-dev/logs/` 条目已更新

### Phase 2 - 端到端 pipeline 与写回

Status: completed

（Phase 1 与 Phase 2 产物零交集，相互独立可并行执行；顺序编排只为叙述清晰）
Targets: `nop-duckdb/src/test/resources/_vfs/nop/duckdb/task/`、测试类

- Item Types: `Proof`

- [x] 测试 xlsx 资源：复用 WI2 已入库 columns-with-missing-values.xlsx（tablesaw 数据驱动断言同 WI2 先例）；**接线**：测试经锚定 engine 连接把 xlsx 经 `DuckDbFiles.readXlsx` 摄入 pipeline 库表 t_xlsx，断言摄入行数==tablesaw 读出行数
- [x] `e2e-pipeline.task.xml`：8 步全链（桥导出→CSV 摄取→过滤→parquet 导出→parquet 回读聚合→终结果导出→SQLite ATTACH→SQLite CTAS 写回）；TestDuckDbE2EPipeline 断言每步输出摘要与 golden 一致（**端到端验证**实测通过：xlsx 文件入口（readXlsx 桥摄入）→ … → 终结果 CSV 全链，桥后每步真实经 task 步骤）
- [x] SQLite ATTACH 直写经锚定连接（并入 e2e-pipeline.task.xml 步骤 7/8——独立 task XML 徒增装配无益，执行裁定合并）：**prepareStatement(ATTACH) 实测可用**（裁决点闭合，无需降级）；锚定连接必须在任务执行期间保持打开（首版在任务前关闭导致 sq 不可见——实例生命周期机制的实证修正）；锚定 engine 字段须与容器 bean 指纹一致（@InjectValue 空默认=""，裸 new 为 null，指纹注册表拒绝——已对齐并注释）；**CTAS 经 PreparedStatement 的 updateCount=-1（驱动语义）**——写回验证改经独立复读（新连接顺序 ATTACH+SELECT，断言 count==1 且 MAX(n)==filtered golden），更强于行数输出——测试先持一条裸 JDBC 连接打开 pipeline 同 dbPath（钉住共享实例，机制=testSameJvmMultiReadSingleWrite 的 instance cache），写回步骤 1 `ATTACH '<abs>.db' AS sq (TYPE sqlite)`（**执行期实测裁决：prepareStatement 对 ATTACH 的支持**；若 prepare 失败则降级为单连接 createStatement 顺序 ATTACH+CREATE TABLE sq.result AS SELECT（喂任务链导出的终结果 CSV 摄入数据）——任务链仍产出终结果文件，Anti-Hollow (a) 不受损），步骤 2 `INSERT INTO sq.result SELECT ...`，任务内同实例步骤验证 sq 可见；任务结束后**独立复读**：新裸连接 createStatement 顺序 ATTACH 同 .db + SELECT count 断言行数与值
- [x] **业务表经 ORM 写回**：pipeline 终结果经 ORM save 落 h2 实体 duckdb.PipelineResult（orm/app.orm.xml 新增），EQL 查询断言行数与值——业务库写回走 ORM 的 roadmap 边界实证
- [x] 并发档：两个完整 pipeline 任务各自独立 .duckdb 文件多线程并行执行全绿（各自 golden 断言通过）；`locked-file.task.xml` 指向被 LockHolderMain 子进程持锁的文件 → 任务执行抛 file-locked（bizFatal），finally 销毁子进程（WI4 跨 JVM 先例复用）

Exit Criteria:

- [x] **端到端验证**（Minimum Rules #22）：xlsx → CSV → 步骤链 → parquet → 回读 → 终结果文件全链有测试贯通且每步摘要 golden 一致
- [x] SQLite ATTACH 直写产物可复读断言；ORM 写回业务表可 EQL 断言（写边界：业务写回只走 ORM，SQLite 仅分析写回面）
- [x] 并发档：并行任务全绿 + 持锁冲突按 file-locked bizFatal 语义失败
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0（51/51 全绿）
- [x] No owner-doc update required（WI9 承接）
- [x] 当日 `ai-dev/logs/` 条目已更新 WI6 收口记录

执行偏差（相对计划文本，已按实测修正落地）：

1. **锚定连接的打开时机**：首版在任务执行前关闭锚定连接 → sqlite_write 步骤报 "Schema sq does not exist"——实证了 baseline 的实例生命周期结论（顺序非重叠连接各自新实例）；按 plan 机制改为锚定连接贯穿任务全程（try 块包住 task.execute）
2. **prepareStatement(ATTACH) 裁决点闭合**：实测可用，降级路径未触发
3. **CTAS updateCount=-1**：duckdb JDBC 对 CTAS 返回 -1（驱动语义）——SQLite 写回的行数输出移除，验证改为独立复读断言（count==1 + MAX(n)==golden），信息量更强
4. **锚定 engine 指纹对齐**：裸 new 的字段为 null、容器 bean 经 @InjectValue 为空串，指纹注册表按 design 拒绝（WI4 config-conflict 语义的正确行为）——锚定 engine 显式置空串并注释
5. **ingest 步骤必须带 sql**（WI3 契约）；**TestDuckDbBeans 按类型取 bean 撞新 engine bean 歧义**——改按 id 取（新测试 bean 注册使类型查找歧义的预期后果）
6. **并发档/句柄循环的类分布**：plan Scope 预设 TestDuckDbTaskConcurrency 单类，实际内容分布在 TestDuckDbDeepSpill.testConnectionCyclesReleaseLocks 与 TestDuckDbE2EPipeline.testParallelIndependentFilesAndLockConflict——覆盖无损（审计确认），归并更贴测试主题
7. **deep-spill.task.xml 步骤序**：实际为 aggregate→sort_export→join_dim（join 后置，导出大表与 join 维表解耦）；golden 断言不受损
8. **deep-spill 两步骤 timeout=120000**（ingest_dim/join_dim 小负载步）而非"每步 300000"——大负载步仍为 300000

执行注意（对抗审查后裁定）：

1. **ATTACH 跨步骤不可见是预期形态非缺陷**（实例生命周期见 baseline）——锚定连接是本 plan 的机制设计，不是 workaround；若 prepareStatement(ATTACH) 实测失败，按写回项内的降级顺序执行并记录偏差
2. **spill 观测**：只断言执行期间"出现过条目"标志，文件名/数量/事后残留一概不锚定
3. **sqlite_scanner 离线行为**：本机已缓存（~/.duckdb/extensions/v1.5.6/osx_arm64/）；换平台/版本首跑需网络自动下载（Deferred 段裁定维持）；测试不 skip——扩展缺失时显式失败并提示预热路径
4. **baseline 证据指针说明**：spike Q2（_tmp/duckdb-spike，瞬态不入 git）为本 plan Phase 1 的来源依据；Phase 1 落地后持久测试即替代其为长期证据

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复或登记（本 WI 为 Proof，预期无新 defect）
- [x] 行为/契约结果已达成：三档位场景测试全绿且断言业务不变量
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步，或明确 No owner-doc update required（显式归 WI9）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）pipeline 从 xlsx 文件入口点（readXlsx 桥摄入）到终结果文件全链真实经 task 步骤执行（桥后每步是真实 step，非测试代码直连拼凑），（b）spill 档真走了外存路径（执行期轮询观测标志），（c）SQLite 写回产物经独立连接复读断言、ORM 写回经 EQL 断言
- [x] `./mvnw test -pl nop-duckdb -am` 退出码 0
- [x] 代码规范核对通过（import 分组/命名/缩进人工核对 + scan-hollow 0；构建无 checkstyle 工具）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2288-duckdb-wi6-deep-scenario-e2e.md --strict` 退出码 0
- [x] scan-hollow-implementations --module nop-duckdb --severity high 退出码 0

## Deferred But Adjudicated

### sqlite_scanner 扩展的离线预热自动化

- Classification: `watch-only residual`
- Why Not Blocking Closure: 扩展已在本机缓存（~/.duckdb/extensions/v1.5.6），后续运行离线可用；首次运行需网络自动下载属 duckdb 发行形态（非本项目可注入），且"SQLite ATTACH 直写"是 roadmap 明示场景而非可选优化
- Successor Required: `no`
- Successor Path: 无（若未来出现完全离线的强制需求，另行裁定随包分发扩展或降级该场景）

## Non-Blocking Follow-ups

- pipeline 的 xlsx 摄取当前走 WI2 桥（API 转 CSV）；若未来出现"步骤输入直读 xlsx"的真实需求，另行立项扩展 step 输入（生产特性，不入 Proof 项）

## Closure

Status Note: WI6 收口。独立 closure audit（fresh subagent）裁决 CAN CLOSE、0 blocker：外存档 spill 观测非恒真（专属 temp 目录 + 执行期轮询）、导出 20M 行精确相等 + 首行 k=976 排序验证；E2E 全链 8 步均为真实生产 step bean、每步摘要 golden 断言；SQLite 写回独立复读（任务外新连接）；ORM 写回经 EQL；并行/锁冲突语义复用 WI4 先例。5 条 Minor 均为文本/收尾层（结构性偏差已补记 6-8 条、路径引用已修、closure commit 随本收口执行）。
Completed: 2026-10-01

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子代理（fresh session，read-only audit）
- Audit Session: agent_fa2d5eee-f677-4c10-b009-ce5ff410323c
- Evidence:
  - Phase 1 Exit Criteria 5/5 PASS（spillSeen 断言非恒真——专属 temp 目录无恒真通路；exportedRows==20_000_000 精确 long 相等；50 轮循环 + 裸 DriverManager 重开）
  - Phase 2 Exit Criteria 6/6 PASS（8 步链每步摘要 golden；SQLite 读回断言 MAX(n)==filteredGolden；ORM EQL 验证；并行两 pipeline 全绿 + file-locked bizFatal）
  - Anti-Hollow 三项亲验 PASS：(a) 8 步全为 `<simple bean="nopDuckDbSqlTaskStep">` 生产 step、测试仅 setInput；(b) 128MB < 160MB 工作集数学必然 + 轮询实证；(c) sqlite 读回/ORM 验证均任务外独立连接
  - 验证门：./mvnw test -pl nop-duckdb 51/51 EXIT=0（审计员复跑）、check-doc-links --strict 0 errors、scan-hollow --severity high 0 findings、check-plan-checklist --strict EXIT=0（均审计员复跑）
  - 执行偏差 5 条逐项与 live 代码一致；审计 Minor 1-3 已补记为偏差 6-8，Minor 4 路径引用已修，Minor 5 随本收口提交
  - 审计 Minor 修复记录：结构性偏差补记、plan L40 路径引用改为 check-doc-links 可解析前缀

Follow-up:

- sqlite_scanner 离线预热自动化（Deferred 既有条目维持 watch-only）
- no remaining plan-owned work
