# Dispatch Log — CRA WI 5.6 nop-graph-duckdb 并发审计

- **Plan**: `ai-dev/plans/2487-cra-wi5.6-nop-graph-duckdb.md`（active；R1 agent_52ade26f 0 Blocker/0 Major、6 Minor 全修——目录路径归位/移交原文全路径/附录 B 全限定/C6-CL-031 三态裁定移 Phase 4/抽查批 CS-0NN 编号口径/派发组成补齐）
- **基线**: commit 6934df7ad3；基线文件 `_tmp/cra-baseline-nop-graph-duckdb.txt`（附录 C 全命令组 + C6-CL-031 站点原文 + DuckDbEngine 单写者约束面清单）
- **盲审纪律**: 初审/深挖子代理禁读 `ai-dev/audits/`、`ai-dev/plans/`、`ai-dev/bugs/`、`ai-dev/lessons/**`、`ai-dev/backlog/`；仅 Phase 4 复核允许读历史审计与 cra-index（**C6 复核必须对照 WI 2.1 移交原文全路径给出 C6-CL-031 三态裁定**）
- **串行约束**: 全部子代理严格串行派发
- **Protected Area**: nop-graph-api 14f/613 plan-first——审计只读不受限；P0 即修仅限 impl 叶

## Phase 1 锚点核实结论（主 agent，2026-10-22）

| 锚点 | 实测 | 结论 |
|------|------|------|
| C1 静态可变字段 | 直接命中 0（algorithm 包命中全为 static 方法——无状态工具类形态，R1 实测确认） | C1 审计面 = 实例字段/单例/逃逸形态 |
| C6-CL-031 站点 | LeidenDetector.java:196-205 runWithTimeout：newSingleThreadExecutor（默认工厂非 daemon）+ submit + finally shutdownNow、无 awaitTermination | 移交项现状与 2.1 记录一致；三态裁定归 Phase 4 |
| DuckDbEngine 单写者 | synchronized(this):105 + LOCK_MARKER:41 + synchronized track(Connection):213 | roadmap 点名约束已具形，审计其完整性 |
| owner doc | nop-duckdb.md 存在；nop-graph 无 owner doc | drift 登记 |
| C2/C3/C4 原语 | C2 少量（DuckDbEngine synchronized）、C4 仅 LeidenDetector executor、C3 零 | 抽查批轻量核查 |

## owner doc 漂移登记

- **nop-graph 无 owner doc**（占单元 63% 行数）——drift 登记，归 owner-doc 治理轨。

## 历史审计对照口径

- cra-index 对 nop-duckdb 0 提及；nop-graph 仅 2.1 移交提及——全部首报（C6-CL-031 闭环归本单元）。

## 维度级派发记录

1. C1 全单元（agent_bfc2e7b6，串行 1/3）：载体 14 项盘点（11 static 字段全 final——无状态声明成立）→ 发现 3（C1-002 P2 DuckDbEngine checkClosed→openConnection→track 与 close 生命周期竞态泄漏连接+虚假 config-conflict、C1-003 P2 Edge.attrs 非 final 非 volatile 可见性缺口[api 契约面]、C1-001 P3 OPEN_FILES 非原子检查）+ CL-001/002。
2. C6 全单元（agent_42958600，串行 2/3）：配对盘点 14 项（11 ✓）→ 发现 3（**C6-001 P1 OPEN_FILES 只注册不释放静态注册表单调泄漏+过期指纹误拒、C6-002 P1 open/close 竞态 track 迟到连接进已关闭引擎→native 句柄+单写者文件锁泄漏**、C6-003 P2 readXlsx 失败路径临时 CSV 不删）+ CL-101~103；LOCK_MARKER 重试逻辑不存在（fail-fast 翻译，无连接需清理）实测澄清。
3. CS 抽查批（agent_dd108686，串行 3/3）：七形态逐文件过完 33/33 → 发现 5（**CS-001 P1 LeidenDetector 超时幽灵线程**[CWTS 1.3.0 零 interrupt 引用反汇编验证，shutdownNow 必然无效，生产消费方 2 处默认 30s 即触发]、CS-002 P2 连接台账 append-only、CS-004 P2 PageRank 每入边整表拷贝、P3×2）+ CL-201~205；显式终止声明。

**Phase 2 汇总**：11 立案 = P1×3 + P2×5 + P3×3；CL 线索 10。已知跨维重复对（去重归 Phase 4/6）：open/close 竞态（C1-002/C6-002/CL-201）、连接台账泄漏（C6-002/CS-002）、LeidenDetector executor（C6 站点核查/CS-001/CL-001/CL-101）、deleteOnExit（C6-003/CS-003）。


## Phase 3 深挖派发记录

4. C1 R2（agent_4a797b64，串行）：新增 1（**C1-R2-001 P1**——OPEN_FILES/connections 账本仅 engine.close 释放，单例装配下每任务净增永不回收[与 C6-001/CS-002 同站点异维度表述]）；扩大面 ①②③④ 全闭 + 11 处 static final 内容可变性逐核；清扫表终止。**继续条件豁免注记**：R2 新增 P1 按机械口径触发必派，但该 P1 为已登记 C6-001/CS-002 的跨维度重述（无新站点）且清扫表全闭——豁免第 3 轮，依据本条留痕，交 closure audit 复核。
5. C6 R2（agent_d27cb609，串行）：新增 2（**C6-R2-002 P1** materialize CTAS 无 table-exists 守卫→孤儿表+可重试分类→持久 dbPath 重试永久必败；C6-R2-001 P2 connections 账本增长同根因异载体）+ C6-001 表述修正建议（refs 释放限定引擎存活期内，P1 定级不变）；5 扩大面闭合。
6. C6 R3（agent_740a86f1，串行）：R2-002 同族放大闭合（写持久 SQL 面 3 处穷举：ingest 有守卫/materialize 无[已案]/COPY 自愈）+ 恢复语义闭合（6 个 IO_FAILED 点逐一裁定）+ 新增 1（C6-R3-001 P3 重放契约未声明 caller-SQL 幂等性——声明面修复）；**本轮终止**。
7. CS R2（agent_8d945722，串行）：新增 4（CS-R2-001 P2 Leiden 5 调用点零规模守卫 vs Betweenness 3 处 10⁴ 守卫同族不一致+请求线程触发面放大、CS-R2-002 P2 Betweenness 无超时/中断[JGraphT Brandes 零 interrupt 反汇编取证]、P3×2）+ CL-206~210；显式终止；归并建议 CS-001/R2-001/R2-002 同缺陷族。

**Phase 3 汇总**：19 立案 = P1×5（C6-001/C6-002/C6-R2-002/CS-001/C1-R2-001）+ P2×8 + P3×6；C6 三轮、CS 两轮、C1 两轮（豁免注记 1 条）；各维度均有显式终止信号。

## Phase 4 复核派发记录

8. C1 复核（agent_d523b0d9，串行 1/3）：4/4 保留 0 驳回 0 降级——C1-002 并入 C6-002 主登记（C1 侧证据不独立）、C1-R2-001 并入 C6 账本族（三方同缺陷面核实）；C1-001/C1-003 独立主登记（后者触 api 叶修复须 plan-first）；R2 豁免正当性复核通过；CL-001 确认（别名归并 CS-001）、CL-002 确认+计数更正（9 个 synchronized 方法）。
9. C6 复核（agent_7df27b35，串行 2/3）：6/6 保留 0 驳回 0 降级（初审 3 处升降档动议均不采信并附理由）；C6-002 确认主登记（「可降 P2」不采信——IDuckDbEngine L16 契约明文违约）；**C6-CL-031 三态裁定=边界变化**：非 daemon+无 awaitTermination 站点事实仍成立但 awaitTermination 属负修复（CWTS 无中断检查点）——并入 CS-001 关闭，2.1 回填注记已写入 C6 文件；归并结构：账本族主 C6-001（吸 R2-001+CS-002+C1-R2-001）/竞态族主 C6-002（吸 C1-002）/deleteOnExit 族主 C6-003（吸 CS-003）；CL-101~103 全确认。
10. CS 复核（agent_7fe86b52，串行 3/3）：9/9 保留 0 驳回 0 降级；CS-001 主登记（两消费方显式 60000 而非默认 30000——表述修正判级不变）；CS-002/003 遵 C6 归并；CS-R2-001 并入 CS-001、CS-R2-003 并入 CS-004、CS-R2-002 同族相邻独立、CS-R2-004 升 P2 动议成立（潜伏维持 P3）；CL-201~210 确认 10/10。

**Phase 4 汇总**：19 实体全保留 0 驳回 0 降级；归并 7 条（C1-002→C6-002、C1-R2-001→C6-001 族、CS-002→C6-001、CS-003→C6-003、CS-R2-001→CS-001、CS-R2-003→CS-004、C6-R2-001→C6-001 载体 B）→ 独立工单 12 = **P1×4**（账本泄漏族 C6-001/竞态族 C6-002/CTAS 必败 C6-R2-002/不可中断族 CS-001）+ P2×4（deleteOnExit 族 C6-003[吸 CS-003]/C1-003[api 叶 plan-first]/邻接分配放大族 CS-004[吸 CS-R2-003]/CS-R2-002 Betweenness 超时）+ P3×4——工单级精确口径由 Phase 6 裁定；C6-CL-031 闭环=边界变化并入 CS-001；CL 15/15 确认 0 驳回。

## Phase 5 C10 派发记录

11. C10（agent_028d1f43）：19 行矩阵——**会红 0/19**；P1×4 全零红保护（账本/竞态族有相邻契约锚但只防回归侧）；共性根因=duckdb 测试全为顺序形态（单例长驻/交错/失败重放零用例）+ graph 测试 0 并发/0 超时断言；缺保护 12 项（P1×4/P2×4/P3 聚合 4 面）。
12. C10 复核（agent_fe668da9）：19 行全抽样重演（P1×4 逐一）——**PASS with corrections**（3 处修正：并入计数 6→7/Thread 用例计数/方法节漏列）。

**Phase 5 汇总**：会红 0/19；缺保护 12 项；优先补测 P10-P1-1（单例账本泄漏）与 P10-P1-3（materialize 重放）——确定性触发最易回退必红。
