# 深度审核汇总报告 — nop-batch 批处理引擎并发·异步·资源专项审计（CRA WI 1.7）

## 基本信息

- **审核单元**: `nop-batch`（聚合器 15 pom 模块；11 个含 Java 的 jar 子模块：api/app/biz/core/dao/dsl/exp/gen/jdbc/orm/service，218 files/15,698 lines；手写口径不含 `_` 前缀与 target/；deploy/model/meta 无 Java，web/codegen/sys 仅测试或资源）
- **审核日期**: 2026-10-16
- **执行维度**: C1 共享可变状态、C5 内存有界性、C6 资源生命周期、C7 长时运行、C8 背压流控（roadmap 1.7 裁剪集）+ C10 测试保护力（最后批次）；维度集外线索 overlay C2/C3/C4/C9 四形态（CL 前缀 `nop-batch-CL-0NN`，沿 plan 2464 裁定节口径）
- **目标范围**: `src/main/java` 全部手写代码；基线 commit 4b7c7897de（分支 concurrency-audit-remediation）
- **方法论**: `ai-dev/skills/concurrency-resource-audit-prompt.md`（共享前缀 + 维度正文，盲审初审 → 深挖迭代 → 独立复核+现状标注 → 单元内合并裁定）
- **Plan**: `ai-dev/plans/2467-cra-wi1.7-nop-batch.md`（两轮对抗性审查通过后执行：R1 3 Major + 6 Minor 全修、R2 1 Major（findNext 幻影引用纠正）+ 5 Minor/观察全修）
- **执行说明**: 全部批次串行派发（运营者要求）；owner doc 漂移预注登记不立案（子模块表 7/11 Java 口径 + 漏 api/app/exp/gen、SysEventBatchTrigger 非 main-source Java 类——sys 侧为 job.yaml+batch.xml 配置接线）；声明契约（chunk 独立事务、at-least-once、historyStore 同事务、snapshotBuilder 未配置静默丢弃、orm-reader query-only、processor order 升序、taskName+taskKey 单活跃）按声明边界校准；executor 未配置兜底 `GlobalExecutors.cachedThreadPool()`（BatchTask.java:83-88）为「使用方式在范围内」的裁定边界。

## 执行统计

| 维度 | 深挖轮次 | 初审发现 | 深挖追加 | 复核保留 | 降级 | 驳回 |
|------|---------|---------|---------|---------|------|------|
| C1 | 2 | 5（P2×3/P3×2） | +2（C1-006 P2、C1-007 P3）+CL-026/027 | 7 | 0 | 0 |
| C5 | 2 | 5（**P1×2**/P2×3） | +4（全 P3）+CL-028~032 | 9 | 0 | 0 |
| C6 | 2 | 6（**P1×2**/P2×2/P3×2） | +5（P2×4/P3×1）+CL-033~035 | 11 | 0 | 0 |
| C7 | 2 | 11（**P1×2**/P2×8/P3×1） | +5（P2×2/P3×3）+CL-036 | 16 | 1（**C7-013 P2→P3**：溢出推导忽略 Math.pow 饱和语义，零延迟窗口仅 rt∈{61,62} 两轮有界突发） | 0 |
| C8 | 2 | 6（**P1×2**/P2×2/P3×2） | +4（全 P2）+CL-037~039 | 10 | 0 | 0 |
| C10 | 1 | —（输入=复核后缺陷清单） | — | 12（缺保护登记：P2×11+P3×1） | 0 | 0 |
| **合计** | — | **33（P0×0/P1×8/P2×18/P3×7）** | **+20 = 53** | **53 全保留（含 1 降级）+ 12 缺保护** | 1 | 0 |

全部 5 维度各 2 轮深挖后显式终止（无第 3 轮需求）；P1×8 全部出自初审，深挖零新增 P1。C8 第 2 轮交付参数护栏矩阵（15 数值参数 A1~A8/B1~B6/D1~D6/E1~E8 全枚举——xdef min + Builder Guard + 运行时归一三层护栏完整者为 0）。

## 合并裁定（详见 merge-adjudication.md）

- 合并前 53 条保留缺陷 → 合并后 **51 条**（P1×8 / P2×26 / P3×17；并入 2 条：**C5-005→C8-001**（dispatch 容量零护栏同位点，必裁 1）、**C7-002→C6-001/C6-002**（终态化阻断族裁定 5：两位点分别完全重合，A 面并入 C6-002、B 面+CL-026 并入 C6-001））；逐条台账 53 条归属经 live code 复核。
- 关键事实修正（C8 复核发现、裁定 1 采纳）：**C8-001 的 ORM 子面「LIMIT 0 静默空任务」被证伪**——实测 `LongRangeBean(0,0).isEmpty()` 使 buildPagedSql 双路径均不加 LIMIT → 内建 orm-reader **全表单页加载 → Semaphore(0) 永久挂死（附全表内存尖峰）**，挂死面扩大至最常用 reader，P1 更稳固；C5 复核「内建 loader 均空页」表述同步修正（静默空任务面收窄至 jdbc/file/generator/list 族）。
- C8-002 P1 边界显式裁定：维持（默认装配确定性触发 + 日志磁盘耗尽面 + 钳位静默；与 C5-002/WI 1.1–1.6「默认装配确定性放大列 P1」先例口径一致）。
- CL 线索台账：编号 39 条 / **驳回 4**（CL-007 live 已闭合（修复 commit 7defae0e69）、CL-009 低危不立案、CL-015 误读控制流、CL-038 与 C5-002 重复）/ **确认 35**（含合并标注：CL-026→C6-001、CL-024→C6-001 修复族验证断言、CL-012=CL-016、CL-013=CL-017 去重单列、CL-022→C5-007、CL-021→CL-009 口径）。
- 跨单元对账：与 cra-index WI 1.1–1.6 台账（P1 累计 47 条）**零重复立项**（nop-batch 与已审单元零代码交集，53 条现状全部「新发现·未修复」，git 核实 nop-batch 最后修复批次 2026-08-24 bcac549f47）；跨单元关联 5 处单列（见下）。

## 按严重程度分布（合并后）

| 严重程度 | 数量 | 主要类别 |
|---------|------|---------|
| P0 | 0 | — |
| P1 | 8 | **C8-001**（←C5-005）dispatch 容量全链零护栏：loadBatchSize≤0 → 内建 orm-reader 全表加载 Semaphore(0) 永久挂死 / jdbc-file 族静默空任务全量丢数、**C8-002** ExportDbTool 缺省 batchSize=0 静默钳位 1 每行一 chunk（日志/状态文件写放大磁盘耗尽；与 Import `>0` 守卫 F3 不对称）、**C5-001** excel-reader beforeRead 全 sheet 物化 List（50 万行 1.5–3GB，maxCountExpr 接线被注释禁用且先物化后截断无兜底）、**C5-002** jdbc-reader fetchSize 死配置（门条件恒 false + 非 MySQL dialect 无 streamingFetchSize + autocommit 未关三重不可达 → GB 级全量缓冲）、**C6-001**（←C7-002B/CL-026）executeChunkLoop 仅 catch Exception → Error 逃逸 future 永不完成 → reader/writer/连接永不关闭+任务行永久 RUNNING+调用方挂死（修复必须与 C6-010 同 diff：try 上移）、**C6-002**（←C7-002A）executor.execute REE 裸逃逸 → 句柄泄漏+永久 RUNNING、**C7-001** 任务行永久 RUNNING 死区（tryMarkTaskRunning 无 stale 判定无 fencing，无专用运维端点，cron 每 5s 错误风暴 17,280 次/天）、**C7-003** 三张账本表零保留（清理代码完全不存在——非零装配；UUID taskKey 每触发 +1 行单调增长） |
| P2 | 26 | 终态化族（C6-009/010、C6-008 saveState×AsyncFetch 互斥缺失、C7-012 KILLED 吸收态无逃生门）、内存面（C5-003/004）、护栏族（C8-009 幽灵旋钮、C8-010 护栏双标、C8-003 concurrency 无上界+xdef 声明与实现相反）、可观测性族（C8-004 metrics 零接线、C8-007 retry() 零调用+计数语义错、C8-008 水位不可观测）、翻页退化（C7-007/008）、策略边界（C7-004/005/011、C1-001/002/003/006）、exp 工具族（C6-004/007、C6-003 理论）等 |
| P3 | 17 | C7-013（降级：溢出残余面）、C7-010/014/015/016、C5-006~009、C6-005/006/011、C1-004/005/007、C8-005/006 |

C10 另有缺保护登记 12 条（P2×11+P3×1，映射见 merge-adjudication.md §3.3）：保护力矩阵 **有钉 0 / 部分 32 / 无保护 21**（结构性：53 条全未修复且套件绿）；P1 8 条全部「部分」——缺陷路径被执行但零断言 2 条（C5-001、C8-002）、缺陷面完全未触及 6 条（C5-002、C6-001、C6-002、C7-001、C7-003、C8-001）。测试资产 56 测试 java/135 @Test；故障注入面系统性缺失（0 Error 注入/0 拒绝 executor/0 抛错 store/0 负值参数）。测试套件自身缺陷注记 N1~N3（N1/N2 条件性钉住缺陷行为——C7-001/C5-002 修复须带参数化改写，否则打红；N3 无超时 latch 挂死回归表现为构建悬挂）。

## P0 记录（即时通道）

本单元 **P0 = 0**（即时通道显式空执行；初审/深挖/复核全程无 P0 立案）。

## 现状标注摘要

- 53 条全部「新发现·未修复」（cra-index WI 1.1–1.6 台账与 nop-batch 零代码交集；git log 核实无本域 CRA 修复提交；plan 2381/2282 为 C1-005 同点位部分缓解已备注）。
- 已知未修复：0 条（外部）；已修复：0 条（本域）。CL-007 为「live 已闭合」形态（历史 commit 7defae0e69 已修，非本审计修复）。
- 维度集外线索：确认台账 35 条（CL-012 全局锁内 DB I/O 族——DaoBatchStateStore 单例 synchronized 锁内 updateTask 全平台串行化、CL-013/017 幽灵 async processor、CL-014 cancel 与 future 解耦、CL-018 timer 孤儿、CL-033 load 侧重试丢数 at-least-once 张力等）；驳回 4 条。

## 关键发现摘要（P1 全清单 8 条，详见 merge-adjudication.md §3.1/§6.2）

| # | ID | 标题 | 失效层 |
|---|----|------|--------|
| 1 | C8-001（←C5-005） | dispatch 容量全链零护栏：loadBatchSize≤0 → orm-reader 全表加载 Semaphore(0) 永久挂死 / 静默空任务丢数 | nop-batch-core 派发装配 |
| 2 | C8-002 | ExportDbTool 缺省 batchSize=0 静默钳位 1：每行一 chunk 磁盘耗尽写放大 | nop-batch-exp 工具装配 |
| 3 | C5-001 | excel-reader 全 sheet 物化：50 万行 1.5–3GB，maxCountExpr 禁用无兜底 | nop-batch-core 文件读取 |
| 4 | C5-002 | jdbc-reader fetchSize 死配置：DSL 显式配置静默吞掉，缺省 GB 级全量缓冲 | nop-batch-jdbc 装配 |
| 5 | C6-001（←C7-002B/CL-026） | Error 逃逸 future 永不完成：资源永不关闭+任务行永久 RUNNING+调用方挂死 | nop-batch-core 终态化 |
| 6 | C6-002（←C7-002A） | REE 裸逃逸：句柄泄漏+永久 RUNNING（注入有界池饱和/停机竞态） | nop-batch-core 提交路径 |
| 7 | C7-001 | 任务行永久 RUNNING 死区：无 stale 判定无 fencing 无运维端点，cron 错误风暴 | nop-batch-dao 状态机 |
| 8 | C7-003 | 三张账本表零保留：清理代码完全不存在，UUID taskKey 单调增长 | nop-batch-dao 长时运行 |

## 盲区自评

- `_gen/` 与 target/ 按纪律排除（`_gen/` 下 3 个非 `_` 前缀 PkBuilder 生成文件已计数注记）。
- C2/C3/C4/C9 为维度集外形态：仅以 CL overlay 登记线索（确认台账 35 条），未做全维度审计——CL-012 锁内 DB I/O 族与 CL-006（rpc 线程模型，留 Wave 2/4）受影响最大，若 7.1 需要完整口径需补审计。
- C7-001/C7-003 的 P1 未做并发复现实验（时序推演到行级 + 双向独立复核维持）；C8-002 的 P1 为边界裁定（合并裁定已显式记录争议与维持理由）。
- sys-event 触发链的 lease/claim 原子性在 nop-sys（跨单元不立案）；nop-job 侧 cron 驱动的停摆面（WI 1.6 C8-001 同族）已跨单元关联单列。
- DSL xpl 脚本内用户代码的任意行为按「用户代码抛异常/Error 是现实触发面」计入（C6-001 判级依据），未审计具体用户脚本。
- 修复耦合与顺序约束（合并裁定产出）：C6-001+C6-010 同 diff（try 上移+catch Throwable）；C8-001 Guard 先于/同时于 C8-009 参数化；C8-004 接线绑定 C8-007 并先落地 C8-008 水位 API；C7-001 修复前必须先参数化改写测试 N1；C5-002 修复须同步改写 N2；C8-002 与 C8-010（biz 穿透面）同批。

## 与其他 roadmap 的重叠

- 与 WI 1.1–1.6 台账（P1 累计 47 条）逐条对账**零重复立项**；跨单元关联 5 处单列：C7-003↔WI 1.6 C7-001（三表零保留族——账本保留建议 platform 级统一方案）、C7-001↔WI 1.5 C7-001/WI 1.6 C8-001（扫描守卫/停摆族——平台级 fencing 对齐）、C8-003↔WI 1.5 C4-001/WI 1.6 CL-002（平台池占用族）、CL-004↔WI 1.6 CL-004（零生产装配对照）、CL-006 留 Wave 2/4 rpc 单元终裁。
- security-audit-roadmap / full-code-review-roadmap：plan 2330/2331（质量域 batch 审计）与 P1-3.3/P1-3.4 修复为声明契约来源，无本域重叠面。
