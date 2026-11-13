# 跨维度合并裁定（Phase 6 / 7.1）— nop-batch 并发·异步·资源专项审计

- **审计单元**：nop-batch（WI 1.7，plan 2467）
- **仓库/分支**：`nop-entropy-concurrency-audit` @ `concurrency-audit-remediation`（CRA 绿色基线 b3a9c76bed 起 nop-batch/ 零代码变更，全部发现现状=新发现·未修复）
- **裁定人**：跨维度合并裁定子代理（独立于五维度初审/复核代理，以证据为准；关键位点经 live code 复核：`BatchTask.java` executeChunkLoop :243 `catch (Exception)`、propagateContext/provideTaskContext 在 try 之前、`executor.execute` 无 REE 兜底；`PartitionDispatchQueue` 构造 `new Semaphore(capacity)` 无 `>0` 守卫；`ModelBasedBatchTaskBuilderFactory.buildDispatchConfig:356` 无校验）
- **裁定日期**：2026-10-16

## 〇、输入口径与规则摘要

### 输入口径（采用 C10 复核更正后口径，全程锁定）

| 项 | 值 |
|---|---|
| 五维度最终保留项 | C1×7、C5×9、C6×11、C7×16（含 C7-013 复核降级 P3）、C8×10，合计 **53** |
| 严重度分布 | **P1×8 / P2×28 / P3×17**（C10 复核更正口径，采纳；初审头部 P2×23/P3×22 作废） |
| CL 台账 | 编号 39 条（CL-001~039 无缺号）；驳回/不立案 4（CL-007/009/015/038）；**确认台账 35 条**（C10 复核更正口径，采纳） |
| C10 缺保护登记 | C10-001~012（复核补 C10-011/012）——不参与合并裁定主体，仅作家族归组引用与修复回归面输入 |
| 套件注记 | N1~N3 全部维持（修复 diff 必须携带的同步评估项） |
| P1 集合 | C5-001、C5-002、C6-001、C6-002、C7-001、C7-003、C8-001、C8-002 |

### 三态判定规则

1. **同一缺陷（合并）**：同一代码位点 + 同一根因 + 同一失效后果——合并后保留最高严重度，登记主 ID 与被并 ID（被并 ID 不消失，标注「并入 X」）。
2. **同族相邻（不合并，互列关联）**：同根因模式或同代码区，但不同位点/不同后果。
3. **不同缺陷**：各自独立。

F 族标签沿各发现「F3 失效面/误报排除」节既有引用口径使用（F1 共享状态竞态、F3 守卫/包络缺口、F5 挂死/停摆、F8 清理不对称、F9 异常语义不对称），不新造编号语义。

---

## 一、必裁交叉项逐一裁定（10 项）

### 裁定 1：C5-005 ↔ C8-001（dispatch 容量零护栏同位点）——**同一缺陷，合并**

- **位点同一性**：`PartitionDispatchQueue` 构造 `new Semaphore(capacity)` 无 `>0` 守卫 + `buildDispatchConfig:356-381` 无校验 + 两个 provider 构造传参——C5 复核与 C8 复核各自独立亲证同一位点（本轮 live code 三点复核确认）。
- **两维复核一致建议合并、以 C8-001（P1）为主处置项**，本裁定采纳：**C5-005（P2）并入 nop-batch-C8-001**，其独有增量（multiplyFactor 维度、负值 IAE 语义评估、maxLockQueueCountPerThread 退化登记、xdef 下界建议）并入主条目修复面。
- **ORM 子面事实修正（C8 复核，必须纳入合并条目事实）**：内建 orm-reader 对 `loadBatchSize=0` **非**「LIMIT 0 空列表→静默空任务」——`LongRangeBean(0,0).isEmpty()==true` 使 `buildPagedSql`/两分页 handler 均不加 LIMIT → **全表单页加载 → addBatch(N_all) → Semaphore(0) 永久阻塞 → 挂死（含全表内存尖峰）**；静默空任务面收窄至 jdbc/file/generator/list 族；cancel 无效（`Cancellable.cancel` 不走 afterComplete→finish）。P1 判级因此更稳固，不变。
- **CL-039 关联确认**：同构建函数（buildDispatchConfig）的随机 partitionFn 缺省面为**不同缺陷**（不同参数、不同后果），不并入，互列注记。
- 对 C5 复核表述的同步修正裁定见「五、跨单元关联与待办」第 6 项。

### 裁定 2：CL-026 ↔ C6-001（executeChunkLoop Error 逃逸同位点）——**同一缺陷，CL-026 并入 C6-001**

C1/C6 复核均裁定合并，本裁定确认：`BatchTask.java:243 catch (Exception e)` 为 C6 复核终态化阻断站点表 S1 首行，同一 catch 子句、同一根因（不接 Throwable）、同一后果面（Error→future 永不完成→泄漏/RUNNING/挂死）。CL-026 登记于 CL 台账单列（并入标注），不另立行动项。CL-005 的「Error 级缺口归 CL-026（C6-001）口径」随之并入同一条目修复面。

### 裁定 3：CL-012 = CL-016；CL-013 = CL-017（同站点/同事实跨维重复）——**台账单列去重（确认）**

- CL-012（C6 轮）= CL-016（C7 轮）：`DaoBatchStateStore` 单例 synchronized saveTaskState 锁内 DB I/O，同一站点同一事实 → 台账单列一条（P2 候选），锁内 I/O 族（CL-012/016/020/036）合并修复面。
- CL-013（C6 轮）= CL-017（C7 轮）：async processor 超时幽灵任务，同一事实 → 台账单列一条，判级沿 C6 复核对齐 **P2 候选**（默认装配不可达 + opt-in 零使用），不双记。

### 裁定 4：CL-024 → C6-001 修复族——**采纳：改列验证断言，不独立立案**

C8 复核已核实：claim（takeBatch:168）常规 interrupt 路径可释放，真正可达的 claim 泄漏面 = Error 逃逸/硬杀，与 C6-001 终态化缺口同一。裁定：**CL-024 并入 C6-001 修复族作验证断言**（completeBatch/claim 释放断言），不独立 P2/P3 立案。台账单列（并入标注）。

### 裁定 5：C6-001 ↔ C6-010 ↔ C7-002（终态化阻断同族）——**逐对三态判定**

| 对 | 判定 | 依据 |
|---|---|---|
| C6-001 ↔ C6-010 | **同族相邻（不合并）** | 同根因模式（终态化守卫覆盖缺口）、同代码区（executeChunkLoop/executeAsync），但**位点不同**：C6-001 = :243 catch 子句过窄（S1）；C6-010 = :229/:231 propagateContext/provideTaskContext 在 try :232 之外（S3）。结构性互依——仅改 catch(Throwable) 无法闭合 S3，两案必须同一 diff（try 上移至 lambda 首行 + catch 放宽 Throwable，C6 复核既定）。互列强关联，终态化阻断族 M1。 |
| C6-001 ↔ C7-002（B 面：:243 Error） | **同一缺陷** | 同一 catch 子句位点、同根因（catch Exception 不接 Throwable）、后果同面（future 永不完成→调用方挂死）。C7-002 的 P2 判级视角（静默永久挂死、batch 层零日志）并入 C6-001 事实描述。 |
| C6-002 ↔ C7-002（A 面：:226 executor 拒绝） | **同一缺陷** | 同一 `executor.execute` 提交点、同根因（提交点无完成兜底、REE 裸逃逸 executeAsync 外层 try 无 catch）、后果同族（onTaskComplete 不执行→泄漏+RUNNING）。C7-002 A 面独有增量（**已提交 futures[0..i-1] 幽灵线程继续加载消费数据**、F3 站点② AsyncFetch Error 假 EOF 变体）并入 C6-002 修复验证面。 |
| C6-010 ↔ C7-002 | 无直接位点交集 | C7-002 不覆盖 :229/:231；经 C6-001 间接关联。 |

**总裁定：C7-002（P2）整体并入终态化阻断族——B 面并入 nop-batch-C6-001、A 面并入 nop-batch-C6-002，不独立立项。** 理由：C7-002 自身无独立位点（其两位点分别与 C6-001/C6-002 完全重合），按三态规则不满足「同族相邻须不同位点」、更非「不同缺陷」；保留独立条目将导致同一修复 diff 双记。C10-001 缺保护登记（C6-001/002/010/C7-002 + CL-024 同组回归测试）与本裁定天然自洽。终态化阻断族修复面扩展为「C6-001 ←CL-026/C7-002(B)/CL-024(断言) + C6-002 ←C7-002(A) + C6-010」，共用「Error 注入 → future 必结算 → 任务行必达终态 → claim 无残留」回归测试（对应 C10-001）。

### 裁定 6：C6-003 ↔ C6-007 ↔ C6-004（exp 工具生命周期族）——**全部同族相邻（不合并）**

- C6-003 ↔ C6-007：**同族相邻**。同根因模式「资源/依赖创建先于 try 保护窗口」（executor :148-153/:230-235 建于 try :161/:242 前 ↔ Hikari 池经 syncConfigWithDb :121-125 建于 try :242 前），不同位点、不同资源、不同后果（executor 对象经 MeterRegistry 滞留 ↔ 整池 OS 级泄漏）；C6 复核已裁定两案同一 diff（exp 工具 try 重排族）。不合并：C6-003 当前 OS 泄漏面为空（P2 理论），C6-007 为现实池泄漏（P2），判级与验证手段不同。
- C6-004 ↔ 前两者：**同族相邻**。同代码区同生命周期主题（exp 两工具 destroy），但根因不同——非窗口错位而是 destroy=shutdown 无 awaitTermination 的优雅关闭缺失，修复面独立（ThreadHelper.shutdownAndAwaitTermination）。三者在 CL-018（导出临时文件重启孤儿）侧另有生命周期邻接注记。

### 裁定 7：C7-004 ↔ C7-013（retry 边界语义族）——**同族相邻（不合并）**

同一 `RetryPolicy` 类的两个独立边界缺口：C7-004 = `isExceedRetryCount` max<0→false 负值无限重试 + 双链计数 off-by-one（位点 :141-146/setter :78-80）；C7-013 = `exponentialDelay` 溢出钳制不拦精确 0（复核修正后仅 rt∈{61,62} 有界突发，P3）。不同方法、不同后果（无限重试风暴 vs 有界零延迟突发）、修复各自独立（Guard.nonNegativeInt / 对 0 值钳制）。互列关联。

### 裁定 8：C8-004 ↔ C8-007 ↔ C8-008（可观测性族）——**同族相邻（不合并），修复绑定同批**

三者为同一失效层（metrics/水位可观测性整体缺位）的三个不同机制位点：C8-004 = BatchTaskMetricsImpl 零生产接线；C8-007 = metrics.retry 零数据源 + getLoadItemCount 错源（Timer 采样=chunk 次数）；C8-008 = 积压/池水位无编程出口（queue 闭包不可达 + enableMetrics 死标志）。C8 复核既定「C8-007 与 C8-004 绑定修复、C8-008 以 C8-004 为前置」——保留三条独立条目、互列强关联、同一修复批实施（对应 C10-010 回归面）。

### 裁定 9：C1-002 ↔ C1-006（generator 共享状态族）——**同族相邻（不合并）**

逐对判定：位点不同（BatchGenState 游标 genCount :68/subIndex :96 ↔ SequentialBatchRequestGenerator 实例字段 subIndex/subContext :23-24）；配置前提不同（loader+sequential+concurrency>1 ↔ asyncProcessor=true，xdef 两独立布尔无联动）；后果不同（配额超发/generator-already-finished ISE/同 case 重复消费 ↔ outputVars 确定性错位+HashMap 数据竞争）。同族（generator 层共享可变状态，F1）互列关联；C5-003（同功能面的内存上界缺口）另列注记关联。

### 裁定 10：C7-003 ↔ WI 1.6 C7-001（retry 三表零保留跨单元同族）——**不同缺陷，跨单元关联单列**

不同模块（nop-batch 三表 ↔ nop-retry 三账本表）、不同清理主体与装配面，同族模式（账本零保留无界增长）。不合并，单列「跨单元关联与待办」第 1 项；C7-003 维持新发现 P1。

---

## 二、自主同族扫描（补登相邻组 ≤5 组 + 补充去重裁定）

### A1 读侧全量常驻/内存包络族：C5-001 ↔ C5-004 ↔ C5-006（同族相邻）

同根因模式「读侧无内存包络」（F3/内存上界）：C5-001 = excel-reader `HeaderListRecordOutput` 全量物化（P1，位点具体）；C5-004 = provider loader 返回 List 全量常驻任务全程无上界（P2，通用契约面）；C5-006 = maxCountExpr 包络能力整体缺位——xdef 无特性+接线死代码+恢复亦不构成内存兜底（P3，守卫能力面）。位点/后果各异不合并；修复同批评估（C5-006 若补能力与 C5-001 同 diff，见 C10-011）。

### A2 RUNNING 死区族：C7-001 ↔ C6-009（同族相邻；C7-011/C7-012 注记关联）

同失效后果「任务行永久 RUNNING」的两个互补机制面：C6-009 = onTaskComplete 经 whenComplete 调用时 saveTaskState 终态写失败被静默吞没（机制：吞没+无日志）；C7-001 = 终态写失败/崩溃后启动闸门无 stale 判定退化为永久死区+错误风暴（机制：闸门语义）。C6-009 是 C7-001 时序 A 的静默化因子，位点与修复独立（清理段显式化 vs 闸门 stale 判定），不合并；C6 复核已定 C6-009 修复排期紧随 P1 族。C7-011（守卫对无 saveState 链结构性不覆盖）、C7-012（KILLED 吸收态）为同状态机邻接面，注记关联不并组。

### A3 batchSize 护栏族：C8-002 ↔ C5-009 ↔ C8-010（同族相邻）

同一参数（batchSize）的三个护栏缺口：C8-002 = ExportDbTool 缺省 0 每行一 chunk 写放大（P1，含 adjustBatchSize :89-90 静默钳位子面）；C5-009 = 全链无上界强制（xdef 无范围/Builder 无 Guard/仅 ≤0 归一 1，P3——与 C8-002 共享 adjustBatchSize 钳位子位点）；C8-010 = biz 钳制无下界致 batchSize=0 经 HTTP 穿透**复现 C8-002**（P2，C8 复核原文明示复现关系）。位点/装配面不同不合并；互列强关联，修复宜同一批护栏设计。

### A4 dispatch 参数面族：C8-001（←C5-005）↔ C8-009（同族相邻）

同一 dispatch 配置面的两种互补缺陷：C8-001 = 容量参数零护栏（无守卫，危险值可达）；C8-009 = loadBatchMultiplyFactor/maxLockQueueCountPerThread 幽灵旋钮（内部生效但无任何入口，与 CL-038 死配置互为镜像）。C8 复核既定修复次序约束：**C8-001 Guard 修复必须先于参数化**（先堵危险值、再谈暴露旋钮）。不合并（不同位点、方向相反的缺陷），互列强关联 + 次序约束。

### A5 skip 静默丢数族：C7-005 ↔ C7-016（同族相邻）

同一失效通道（skip 侧静默丢记录）的两个机制面：C7-005 = skipPolicy 缺省 maxSkipCount=0 语义无限跳过 + 跳过不入任何账本（P2）；C7-016 = retry/skip 缺省异常谓词不对称，bizFatal 被重试侧判死后落入跳过侧缺省谓词被静默丢弃（P3）。位点不同（SkipConsumeHelper 计数/账本 vs 双侧谓词缺省值），修复独立；互列关联。C7-015（recordKey 塌缩+同键冲突的独占 chunk 静默误跳）为丢数面第三注记，不并组。

### 补充去重裁定（CL ↔ 发现同站点，非相邻组）

1. **CL-022 → 并入 C5-007（同站点跨维重复登记）**：两者均为 `SplitBatchConsumer` activeConsumers「只 put 不 remove、任务期单调增长」同一位点同一事实（C8 复核 :43-45 亲证 = C5-007 立案面）。裁定：CL-022 台账单列改标「并入 C5-007」，不另立行动项；C8 轮「归 C6/F8」的归属建议被同点重复原则覆盖（归属随 C5-007）。
2. **CL-021 → 并入 CL-009 不立案口径（同站点同事实）**：CL-021（C7 轮确认，Integer.MAX_VALUE 队列 P3 候选）与 CL-009（C5 轮，`ExportDbTool:150`/`ImportDbTool:232` Integer.MAX_VALUE 队列低危维持不立案）为同一位点同一事实。裁定：处置对齐 CL-009（低危维持不立案展开，CL-004 同工具 cachedThreadPool+无界队列组合 P2 不受影响），台账单列改标「并入 CL-009 口径」。

以上两条去重不改变 53/35 基线计数（CL 条目本不入 53；CL-021/CL-022 仍各占确认台账一行，仅处置标注变更）。

---

## 三、合并后台账（53 条逐条归属）

> 有效条目 51 + 被并条目 2 = 53。严重度为各维复核终裁值（本裁定未变更任何单条判级）。C10-0xx=缺保护登记引用（家族归组引用，非裁定对象）；N1~N3=套件注记耦合项。

### 3.1 有效条目（51 条）

**C1 共享可变状态（7 条，无合并变动）**

| # | ID | 标题 | 终判 | 并入/关联标注 |
|---|---|---|---|---|
| 1 | nop-batch-C1-001 | BatchTaskGlobals TL provide/remove 无 save-restore，嵌套批任务后外层上下文丢失 | P2 | 裁定 5 邻域外；修复成本低应前置（C1 复核精度修正随条目） |
| 2 | nop-batch-C1-002 | BatchGenState 共享游标计数器多 chunk 线程无锁驱动 | P2 | 裁定 9；generator 共享状态族；关联 C1-006、C5-003（同功能面）；C10-008 |
| 3 | nop-batch-C1-003 | 生命周期回调 swap-on-fire 注册守卫误判，中途注册被静默丢弃 | P2 | 关联 C1-007（回调生命周期面）；C10-009 |
| 4 | nop-batch-C1-004 | onTaskEnds 死载体字段 | P3 | F3 死载体；C10 矩阵「无保护」 |
| 5 | nop-batch-C1-005 | BatchTask 实例复用/重入无运行时守卫（理论） | P3 | 既有部分缓解：plan 2381/2282 闸门，非修复闭环 |
| 6 | nop-batch-C1-006 | SequentialBatchRequestGenerator async 模式实例字段跨线程无同步 | P2 | 裁定 9；generator 共享状态族；关联 C1-002；C10-008 |
| 7 | nop-batch-C1-007 | ExecutionContextImpl complete() 两段窗口 + 失败路径 beforeCompletes 不执行 | P3 | 注记关联 C6-001 回归面 F3⑥（同平台类 ExecutionContextImpl）；C10-009 |

**C5 内存上界（8 条有效 + 1 并出）**

| # | ID | 标题 | 终判 | 并入/关联标注 |
|---|---|---|---|---|
| 8 | nop-batch-C5-001 | excel-reader 全量物化：整个数据表一次性物化为内存 List | **P1** | 族 A1；关联 C5-004/C5-006；**C10-011 必补回归钉**（P1 唯一无修复回归钉登记项；验证不得复用 readAll()） |
| 9 | nop-batch-C5-002 | jdbc-reader fetchSize && 门死配置化，非 MySQL 流式三重不可达 | **P1** | ←CL-038（同点重复立项驳回，事实与 E5 对照并入本条备注）；关联 CL-028（同批修复）；N2 契约耦合 |
| 10 | nop-batch-C5-003 | useBatchRequestGenerator 路径 chunk 输出集无上界 | P2 | 注记关联 C1-002/C1-006（generator 功能面）；C10-008 |
| 11 | nop-batch-C5-004 | provider loader 返回 List 全量常驻，无上限守卫无包络声明 | P2 | 族 A1；关联 C5-001 |
| 12 | nop-batch-C5-006 | excel-reader maxCountExpr 能力整体缺位 | P3 | 族 A1；关联 C5-001（补能力则同 diff，C10-011 顺带覆盖） |
| 13 | nop-batch-C5-007 | SplitBatchConsumer activeConsumers 任务级只增不减（理论） | P3 | **←CL-022（自主去重裁定：同位点跨维登记并入）** |
| 14 | nop-batch-C5-008 | BatchTaskContextImpl 三类任务级容器无容量守卫（理论） | P3 | — |
| 15 | nop-batch-C5-009 | batchSize 全链路无上界强制 | P3 | 族 A3；关联 C8-002（共享 adjustBatchSize 钳位子位点）、C8-010；C10-010 |

**C6 资源生命周期（11 条，无合并变动，族关联 4 条）**

| # | ID | 标题 | 终判 | 并入/关联标注 |
|---|---|---|---|---|
| 16 | nop-batch-C6-001 | chunk 循环仅 catch Exception，Error 使 future 永不完成→泄漏+RUNNING+挂死 | **P1** | **←CL-026（裁定 2）、←C7-002 B 面（裁定 5）；CL-024 改列本条修复族验证断言（裁定 4）**；终态化阻断族 M1（同 diff：try 上移 + catch Throwable）；F5/F8；关联 C6-009、C7-001（族 A2）；C10-001 |
| 17 | nop-batch-C6-002 | executor.execute 同步 REE 裸逃逸绕过 onTaskComplete，setup 资源泄漏+RUNNING | **P1** | **←C7-002 A 面（裁定 5；幽灵 futures 续跑事实并入验证面）**；终态化阻断族 M1；F5/F8；C10-001 |
| 18 | nop-batch-C6-003 | exp destroy 保护窗口不含 beforeExport/beforeImport（OS 面空；executor 经 MeterRegistry 滞留——复核修正论据） | P2 理论 | 裁定 6；exp 生命周期族（与 C6-007 同 diff try 重排）；F8；C10-004 |
| 19 | nop-batch-C6-004 | exp destroy=shutdown 无 awaitTermination，失败后兄弟表任务后台续跑 | P2 | 裁定 6；exp 生命周期族（修复面独立）；F8；C10-004；注记关联 CL-018 |
| 20 | nop-batch-C6-005 | JdbcBatchLoaderProvider SQLException 分支不关 PreparedStatement | P3 | 注记关联 CL-010/C6-011/C7-009（连接持有族）；C10-005 |
| 21 | nop-batch-C6-006 | PartitionDispatchQueue.removePartition 全仓零调用方（死代码） | P3 | 注记关联 C8-005（discard 面引用同点） |
| 22 | nop-batch-C6-007 | ImportDbTool HikariDataSource 建于 try 窗口外，syncConfigWithDb 失败整池泄漏 | P2 | 裁定 6；exp 生命周期族（与 C6-003 同 diff）；F8；C10-004 |
| 23 | nop-batch-C6-008 | saveState+AsyncFetch：chunk 压实回调挂一次性 ctx 永不触发 | P2 | 注记关联 C7-006（completedIndex 推进面）；C10-009 |
| 24 | nop-batch-C6-009 | onTaskComplete 经 whenComplete 时 saveTaskState 失败被静默吞没 | P2 | 族 A2；关联 C7-001（本条为其时序 A 静默化因子）；修复排期紧随 P1 族（C6 复核 R5.1）；C10-002 |
| 25 | nop-batch-C6-010 | executeChunkLoop try 守卫起点过晚（:229/:231 在 try 外），单改 catch(Throwable) 不能闭合 | P2 | 裁定 5；终态化阻断族 M1（与 C6-001 同 diff）；F5；C10-001 |
| 26 | nop-batch-C6-011 | JdbcBatchLoaderProvider 持事务绑定连接跨事务生命周期（理论） | P3 理论 | 注记关联 CL-010（同族）、C7-009；C10-005 |

**C7 长时运行（15 条有效 + 1 并出）**

| # | ID | 标题 | 终判 | 并入/关联标注 |
|---|---|---|---|---|
| 27 | nop-batch-C7-001 | 任务行永久 RUNNING 死区：闸门无 stale 判定+错误风暴，无专用恢复端点 | **P1** | 族 A2；跨单元关联单列（见五.2）；C10-002；**N1 钉住耦合**（stale 接管型修复必改 3 用例） |
| 28 | nop-batch-C7-003 | 批处理三表零保留，长时运行单调增长无界 | **P1** | 跨单元关联单列（见五.1）；C10-007 |
| 29 | nop-batch-C7-004 | maxRetryCount 负值=无限重试无校验 + 双链计数 off-by-one | P2 | 裁定 7；retry 边界语义族；关联 C7-013；C10-010 |
| 30 | nop-batch-C7-005 | skipPolicy 缺省无限跳过 + 跳过不入任何账本 | P2 | 族 A5；关联 C7-016、C7-015（注记）；C10-010 |
| 31 | nop-batch-C7-006 | completedIndex 断点推进仅 file/excel 生效（契约缺口） | P2 | 注记关联 C6-008；C10-006/C10-007 |
| 32 | nop-batch-C7-007 | orm-reader keyset 锚点持活实体，游标漂移静默跳行 | P2 | 注记关联 C7-014（同游标机制）；C10-006 |
| 33 | nop-batch-C7-008 | jdbc-reader 缺省 offset 翻页：O(N²/2B) 退化+并发写跳行 | P2 | C10-005 |
| 34 | nop-batch-C7-009 | jdbc 流式 loader 整任务持有单条物理连接，瞬断即失败 | P2 | 注记关联 CL-010/C6-011（连接持有族）；C10-005 |
| 35 | nop-batch-C7-010 | 每 chunk 双条 INFO+每轮重试 ERROR 全栈日志线性放大 | P3 | — |
| 36 | nop-batch-C7-011 | sys-event 链 saveState 未配置→单活跃守卫结构性不覆盖 | P2 | 族 A2 注记（守卫覆盖面）；关联 C7-001；C10-002 |
| 37 | nop-batch-C7-012 | KILLED 永久吸收态 + 恢复仅无校验通用 CRUD 且与 version CAS 无 fencing | P2 | 注记关联 C7-001（状态机终态面）；C10-002 |
| 38 | nop-batch-C7-013 | exponentialDelay 溢出钳制不拦精确 0（复核降级：非永久紧循环） | P3 | 裁定 7；retry 边界语义族；关联 C7-004；C10-010 |
| 39 | nop-batch-C7-014 | orm keyset 游标对 NULL 排序值确定性失败墙 | P3 | 注记关联 C7-007；C10-006 |
| 40 | nop-batch-C7-015 | recordKeyExpr 空值塌缩+复合主键冲突：确定性失败/静默误跳 | P3 | 注记关联 C7-005/C7-016（丢数面）；C10-007 |
| 41 | nop-batch-C7-016 | retry/skip 缺省异常谓词不对称：bizFatal 被静默丢弃 | P3 | 族 A5；关联 C7-005；C10-007 |

**C8 背压流控（10 条，含 1 条吸收 C5-005）**

| # | ID | 标题 | 终判 | 并入/关联标注 |
|---|---|---|---|---|
| 42 | nop-batch-C8-001 | dispatch 链 loadBatchSize≤0 五层无护栏：**orm-reader 全表单页加载后挂死（含内存尖峰）**；jdbc/file/generator/list 静默空任务丢全量；自定义 loader 挂死且 cancel 无效 | **P1** | **←C5-005（裁定 1，守卫建议与本条增量并入）**；ORM 子面事实修正已纳入条目事实（见五.6）；族 A4；关联 C8-009（修复次序：本条 Guard 先行）、CL-025、CL-039（不同缺陷注记）；C10-003；**N3 套件耦合**（无超时 await 使回归表现为悬挂） |
| 43 | nop-batch-C8-002 | ExportDbTool 缺省 batchSize=0 每行一 chunk（双 INFO+状态文件写）+ Import 不对称 + 钳位静默 | **P1** | **←CL-008（C5 轮深化立案并入，沿用）**；族 A3；关联 C5-009/C8-010；**P1 边界裁定：维持**（见六.2）；C10-004 |
| 44 | nop-batch-C8-003 | 兜底全局 cached 池无上界 + batch.xdef:2 声明失真（schema 工件随修） | P2 | 跨单元关联单列（见五.3）；注记关联 C8-010/CL-004（无界池面）；C10-010 |
| 45 | nop-batch-C8-004 | BatchTaskMetricsImpl 零生产接线 | P2 | 裁定 8；可观测性族（绑定修复 C8-007/C8-008）；C10-010 |
| 46 | nop-batch-C8-005 | discard 路径 at-least-once 成立；丢弃不可审计+takeBatch 静默 null 残余 | P3 | 注记关联 C6-006；C10-012 |
| 47 | nop-batch-C8-006 | batchSize×rateLimit 必死组合（>rateLimit×1200）装配期不可见 + rateLimit≤0 静默禁用 | P3 | C10-012 |
| 48 | nop-batch-C8-007 | metrics.retry 零数据源 + getLoadItemCount 错源 | P2 | 裁定 8；可观测性族（与 C8-004 绑定修复）；C10-010 |
| 49 | nop-batch-C8-008 | 积压/池水位无编程出口（queue 闭包不可达+enableMetrics 死标志） | P2 | 裁定 8；可观测性族（C8-004 前置）；C10-010 |
| 50 | nop-batch-C8-009 | loadBatchMultiplyFactor/maxLockQueueCountPerThread 幽灵旋钮 | P2 | 族 A4；关联 C8-001（修复次序后置）；C10-003 |
| 51 | nop-batch-C8-010 | core/exp 无界 vs biz 有界+钳制护栏双标；biz 钳制无下界致 batchSize=0 经 HTTP 穿透复现 C8-002 | P2 | 族 A3；关联 C8-002、C8-003/CL-004（注记）；C10-010 |

### 3.2 被并条目（2 条，不消失）

| ID | 原判 | 并入 | 吸收的增量事实/建议 |
|---|---|---|---|
| nop-batch-C5-005 | P2 | **nop-batch-C8-001**（裁定 1） | multiplyFactor 维度、负值 IAE「无配置上下文」语义评估、maxLockQueueCountPerThread≤0 退化登记（复核方向修正：恒真→≈值取 1）、xdef 下界 Guard 建议 |
| nop-batch-C7-002 | P2 | **nop-batch-C6-001（B 面：:243 Error）+ nop-batch-C6-002（A 面：:226 拒绝）**（裁定 5） | 幽灵 futures[0..i-1] 提交后续跑事实、AsyncFetch fetch 提交 Error→假 EOF 同族变体（F3②）、F3 站点③④ CLI 可见不命中清单、「拒绝时 completeExceptionally(rej)」修复建议 |

### 3.3 C10 缺保护登记引用映射（家族归组引用，非裁定对象）

C10-001~012 的「涉及缺陷 ID」在合并后全部自洽（两组合并的两侧本就登记在同一 C10 条目内），无需改写：C10-001（终态化族=C6-001/002/010+C7-002+CL-024，与本裁定 M1 完全重合）、C10-003（C8-001/C5-005+C8-009 前置）、其余照旧。C10-011（C5-001 漏登记补充）为 P1 修复批必补回归位。N1→C7-001、N2→C5-002、N3→C8-001 族修复 diff 必须携带同步评估。

---

## 四、CL 线索台账（单列，不计入 53）

**编号 39 条；驳回/不立案 4（CL-007/009/015/038）；确认台账 35 条**（口径沿 C10 复核更正，采纳）。合并关系标注如下（★=本裁定新增/变更标注）：

| CL | 归属/严重度 | 处置 |
|---|---|---|
| CL-001 | C2/P2 | 独立行动项 |
| CL-002 | C2/P3 | 独立行动项 |
| CL-003 | C3/P3 | 独立行动项（自 P2 降档已采纳） |
| CL-004 | C5/C4/P2 | 独立行动项；跨单元关联 WI 1.6 CL-004（见五.4） |
| CL-005 | C3/F9/P3 | 独立行动项；Error 级缺口归 C6-001 口径（裁定 2 随动） |
| CL-006 | C9/P3 | 独立行动项；←CL-027（7 站点串行点合并口径）；跨单元待办见五.5 |
| CL-008 | C8/P1 | **并入 nop-batch-C8-002** |
| CL-010 | C6/P2 | 独立行动项；与 C6-011 同族（C5/C6 单条）；C10-005 相邻 |
| CL-011 | C9/P3 | 独立行动项；与 CL-030 同族合并评估 |
| CL-012 | C2/P2 候选 | 独立行动项；**=CL-016 单列**；锁内 I/O 族（CL-012/016/020/036）合并修复面 |
| CL-013 | C6/P2 候选 | 独立行动项；**=CL-017 单列**（判级对齐 P2） |
| CL-014 | C6/P2 候选 | 独立行动项（与 S10 阻塞型不结算同根） |
| CL-016 | C7/P2 候选 | **并入 CL-012（单列不双记）** |
| CL-017 | C7/P2 候选 | **并入 CL-013（单列不双记）** |
| CL-018 | C6/P3 | 独立行动项；注记关联 C6-004（exp 生命周期族） |
| CL-019 | C7/P3 | 独立行动项（声明故障模型=进程 kill 内成立） |
| CL-020 | C7/P3 | 独立行动项；锁内 I/O 族 |
| CL-021 | C7/P3 候选 | ★**并入 CL-009 不立案口径**（自主去重：同位点 ExportDbTool:150/ImportDbTool:232 无界队列） |
| CL-022 | C6/F8/P3 | ★**并入 nop-batch-C5-007**（自主去重：同位点 SplitBatchConsumer.activeConsumers） |
| CL-023 | C9/P3 | 独立行动项；注记关联 C6-011（共享连接串行化设计面） |
| CL-024 | C8→C6 | **并入 nop-batch-C6-001 修复族验证断言**（裁定 4），不独立立案 |
| CL-025 | C3/P3 | 独立行动项；注记关联 C8-001（同类只查 finished 不查 cancelled） |
| CL-026 | C6/P1 | **并入 nop-batch-C6-001**（裁定 2） |
| CL-027 | C9/P3 | **并入 CL-006** |
| CL-028 | C5 邻接/P3 | 独立行动项（与 C5-002 修复同批） |
| CL-029 | C7/P3 | 独立行动项 |
| CL-030 | C9/P3 | 与 CL-011 同族合并口径 |
| CL-031 | C2/C8/P2~P3 | 独立行动项（归属维度留协调方裁定） |
| CL-032 | C9/P3 | 独立行动项；注记关联 C8-002（同写路径不同根因：单次全量 JSON 成本 vs 频率放大） |
| CL-033 | C8/P2 候选 | 独立行动项（游标快照或文档声明） |
| CL-034 | C6/P3 候选 | 独立行动项（fire 语义统一） |
| CL-035 | C6/P3 候选 | 独立行动项（per-callback 防护同族化；Error 级并入 C6-001 族口径） |
| CL-036 | C7/P3 候选 | 独立行动项；锁内 I/O 族 |
| CL-037 | C9/P3 | 独立行动项 |
| CL-039 | C5/C9/P3 | 独立行动项；注记关联 C8-001（同构建函数不同参数面——裁定 1 确认不同缺陷） |
| （CL-007） | — | **驳回**：live 已闭合（历史 commit 7defae0e69，CRA 基线祖先），不入台账 |
| （CL-009） | C4/低危 | **沿用不立案**（←CL-021 并入口径） |
| （CL-015） | — | **驳回**：初审误读控制流（闸门拒绝沿外层 try 同步传播，saveTaskState 前提不成立） |
| （CL-038） | — | **驳回独立立案**：与 C5-002 同点重复，事实与 E5 活性对照并入 C5-002 备注 |

---

## 五、跨单元关联与待办（单列，不合并）

1. **C7-003（nop-batch 三表零保留，P1）↔ WI 1.6 C7-001（nop-retry 三账本表零保留，P1）**：账本零保留族跨单元同型（同族不同模块，均新发现）。修复应共用保留策略设计（TTL/行数上限/成功即清理语义），但各自独立立项——列入 Phase 8 批次对齐项。
2. **C7-001（nop-batch RUNNING 死区，P1）↔ WI 1.5 C7-001（WfTaskScanner 扫描无 fencing）/ WI 1.6 C8-001（扫描循环被拒/Error 静默永久停摆，P1）**：扫描守卫/停摆族对照；WI 1.6 C8-001 已标注「修复须平台级统一」。nop-batch 侧修复（stale 判定+恢复端点）须与该平台级统一方案对齐，避免两套 fencing 语义。
3. **C8-003（nop-batch 兜底全局 cached 池无上界，P2）↔ WI 1.5 C4-001（globalTimer 共享 4 线程）/ WI 1.6 CL-002（doScan 实占 globalWorker）**：平台池占用族。batch 引擎缺省走 GlobalExecutors.cachedThreadPool 与平台共享池治理属同一主题，修复（专用池/池隔离）须平台级统一评估。
4. **CL-004（nop-batch exp 线程池 cachedThreadPool+无界队列，P2）↔ WI 1.6 CL-004（tcc 过期扫描/清理零生产装配，P1 建议）**：零生产装配不同形态对照（一为无界兜底、一为能力未交付）。单列对照，不合并；WI 1.6 侧终裁留协调方。
5. **CL-006（nop-rpc 线程模型，含 CL-027 第 7 站点）**：留 Wave 2/4（rpc/资源单元）处置，本单元不展开。
6. **C8-001 ORM 子面事实修正 → C5 复核表述同步修正裁定（必裁 1 附带义务）**：C5 复核结论四.1「标准内建 loader 对 batchSize=0 均返回空页（BatchGenLoaderProvider/ListBatchLoader/IRecordInput.readBatch）→ 实际表现是空页→静默空任务」中 **orm-reader 部分被 C8 复核完整证据链推翻**（引证仅覆盖 generator/list/record-input 三族，遗漏 OrmQueryBatchLoaderProvider→dao.findNext→findPage(0,0)→LongRangeBean(0,0).isEmpty→buildPagedSql/JdbcHelper 不加 LIMIT 链）。**修正后口径**：batchSize=0 下内建 loader 表现分两族——orm-reader **全表单页加载后挂死（含内存尖峰）**；jdbc/file/generator/list 族空页→静默空任务。该修正随合并条目（C8-001）生效：C5-005 触发时序 T2 精化与 C10 矩阵 B1 行按此修正；**修复验证 orm 路径时期望终态为「挂死+内存尖峰」，不得以「空任务成功」为断言**（C10-003 回归设计输入）。
7. **零重复立项声明**：五维复核现状标注一致——`git log b3a9c76bed..HEAD -- nop-batch/` 为空，cra-index WI 1.1–1.6 台账 grep 零 `nop-batch` 交集；本单元 53 条与既有三张台账（P0/P1/CL）零同点重复，全部为新发现。

---

## 六、统计

### 6.1 计数自洽

| 项 | 值 |
|---|---|
| 合并前（C10 复核口径） | 53 = P1×8 + P2×28 + P3×17 |
| 并入（同一缺陷合并） | 2 条：C5-005→C8-001；C7-002→C6-001+C6-002 |
| **合并后有效发现** | **51 = P1×8 + P2×26 + P3×17** |
| 自洽校验 | 51 + 2（被并）= 53 ✓；8+26+17=51 ✓ |
| 同族相邻（不合并，互列关联） | 必裁产出 5 族（终态化阻断族、exp 生命周期族、retry 边界语义族、可观测性族、generator 共享状态族）+ 自主补登 5 组（A1~A5） |
| CL 台账 | 39 编号 / 4 驳回 / 35 确认（单列，含 6 项并入标注 + 2 项本裁定新增去重标注）；独立行动项净计数：35 − 并入标注 8（CL-008/016/017/021/022/024/026/027）− 同族合并口径 2（CL-027→CL-006 已含、CL-030→CL-011）≈ 25~27 条（按族合并修复面实施） |

### 6.2 P1 合并变动清单（逐条，8 条）

| P1 | 合并变动 |
|---|---|
| C5-001 | 无并入。族 A1 关联登记；C10-011 补回归钉义务。P1 维持。 |
| C5-002 | 无发现级并入（CL-038 同点重复立项驳回、事实并入备注；CL-028 同批）。P1 维持。 |
| C6-001 | **吸收增量**：←CL-026（裁定 2）、←C7-002 B 面（裁定 5）、CL-024 改列验证断言（裁定 4）；修复族同 diff 扩至 C6-002/C6-010。P1 维持。 |
| C6-002 | **吸收增量**：←C7-002 A 面（幽灵 futures 续跑+AsyncFetch 假 EOF 变体并入验证面）。P1 维持。 |
| C7-001 | 无并入。族 A2 关联 C6-009；跨单元关联单列；N1 耦合。P1 维持。 |
| C7-003 | 无并入。跨单元关联 WI 1.6 C7-001 单列。P1 维持。 |
| C8-001 | **吸收从项**：←C5-005（裁定 1，主处置项）；ORM 子面事实修正纳入条目（挂死面扩大至最常用 reader，P1 更稳固）；修复次序约束先于 C8-009。P1 维持。 |
| C8-002 | 无发现级并入（←CL-008 沿用）。**P1 边界显式裁定：维持 P1**——(a) C10 复核更正口径基线已固定 8 条 P1，本裁定以口径一致性为最高准则；(b) 实质依据与 C5-002 先例同构：默认装配确定性触发（test.export-db-xlsx.xml 无 batchSize 属性亲证）+ 每行双 INFO×每 chunk 状态文件写磁盘耗尽面 + 钳位静默不可感知；(c) WI 1.1–1.6「默认装配确定性放大列 P1」先例一致。 |

### 6.3 严重度分布（合并前后）

| 维度 | 合并前 | 合并后 | 变动 |
|---|---|---|---|
| C1 | P2×4 / P3×3 | P2×4 / P3×3 | 无 |
| C5 | P1×2 / P2×3 / P3×4 | P1×2 / P2×2 / P3×4 | C5-005 并出 |
| C6 | P1×2 / P2×6 / P3×3 | P1×2 / P2×6 / P3×3 | 无（吸收增量不计条数） |
| C7 | P1×2 / P2×9 / P3×5 | P1×2 / P2×8 / P3×5 | C7-002 并出 |
| C8 | P1×2 / P2×6 / P3×2 | P1×2 / P2×6 / P3×2 | 无（C8-001 吸收从项不计条数） |
| **合计** | **53（P1×8/P2×28/P3×17）** | **51（P1×8/P2×26/P3×17）** | P2 −2 |

### 6.4 后续输入

- 本台账为 `summary.md` 与 `cra-index.md` WI 1.7 行的直接输入：登记 51 条有效（P1×8/P2×26/P3×17）+ C10 缺保护 12 条单列 + CL 确认台账 35 条单列。
- 修复批次建议（按族聚合）：① 终态化阻断族 M1（C6-001/002/010 同 diff + 回归 C10-001，最高优先）；② 内存包络批（C5-001/002 + C5-006 同批评估 + 回归 C10-011）；③ dispatch 护栏批（C8-001 Guard → C8-009 参数化次序约束 + 回归 C10-003/N3）；④ 状态机批（C7-001 stale 判定 + C6-009 + N1 耦合评估）；⑤ 三表保留策略批（C7-003，与 WI 1.6 对齐）；⑥ 可观测性批（C8-004/007/008）；⑦ 护栏与语义批（C8-002/010、C5-009、C7-004/013、C7-005/016）；⑧ 其余独立条目按判级排序。
