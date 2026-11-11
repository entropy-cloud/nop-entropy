# 2563 CRA Phase 8.69 — R4-29 4.5 Lucene/watch/测试基建束（5 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-12
> Source: roadmap 工作项 8.69；批次定义 = batch-proposal/cra-index.md :456 P2-G71 行；成员发现 = `ai-dev/audits/2026-10/2026-10-21-1444-deep-audit-nop-search-runner-autotest-concurrency/`；设计文档 = `../design/concurrency/search-watch-testinfra-batch.md`（裁定 A-E + 勘正 3 + 冻结面）
> Related: plan 2496（CliRunCommand 周期任务——I-C4-002 第三腿已修，范围收窄依据）、plan 2547（RateLimitExecutorImpl debounce——MA-03 平台侧冻结面）、plan 2481（审计立项）
> **PA**：无（五工单全 Java 层；MA-03 越界单行申报 = 不触碰 nop-core IZipTool，平台侧改造留档 backlog）
> **特殊约束**：① MA-01 四成员同设施同 diff 禁拆分（cra-index G71 约束）；② MA-02 禁 watcher.stop() 统一（CL-011 footgun）+ C10-004 腿 1「未调用共享服务 stop」断言随 diff；③ MA-04 两案择一已裁定案 A（整体重写），实现评审仅裁周期复排二选一，不重开工单；④ 批内顺序：MA-04 → MA-03（重叠腿依赖重写后替身）；⑤ S-04 正向约束（rollback 先移除再关闭 + dir 保留）为 MA-01 冻结面

## Purpose

关闭 R4-29 的 **5 个 P2 工单**：Lucene 三 CHM 生命周期与并发协调族（MA-01 四成员单设施）、watch 注册/注销残留族（MA-02）、watch-zip 产物并发损坏族（MA-03）、MockScheduledExecutor 替身契约族（MA-04 重写）、watch 脚本异常静默（I-C4-002 单站对齐）。C10 成员面测试义务（C10-001/003/004/005/007）随本体同 diff 兑现。

## Current Baseline（live 亲读 @ 2026-11-12，取证 agent fresh session）

（逐条锚点见设计文档 §〇；勘正 3 处已登记）
- MA-01：`LuceneSearchEngine.java:134-136` 三 CHM 零协调；`:264/:277/:289` computeIfAbsent 无守卫；`:1118-1150` removeTopic 三段关闭；`:1158-1173` rollback S-04 形态在位；destroy `:179-222`
- MA-02：`CliWatchCommand.java:124-126` ICancellable 丢弃；`:94-105` finally 仅 locallyOwned 清理
- MA-03：`CliWatchZipCommand.java:127-137` 无 in-flight 守卫无原子写；zipDirTo 在 nop-core `IZipTool.java:75-85`（越界不触碰）
- MA-04：`MockScheduledExecutor.java` + `MockTask.java` 时间/生命周期/取消/复排四面缺陷在位；全仓零消费方零测试引用
- I-C4-002：`CliWatchCommand.java:138-151` processEvents 无 catch；对照基准 `CliWatchZipCommand:133-135`
- 基线命令：`./mvnw -o test -pl nop-autotest/nop-autotest-core,nop-search/nop-search-lucene,nop-runner/nop-cli-core -am`

## Goals

- 5 工单全部落地，载体先红后绿或变异论证（≥3 链）。
- MA-01 整族单设施（评审整族一次裁定设施选型）；MA-04 替身整体重写单一 diff。
- C10-001/003/004/005/007 成员面随本体同 diff 兑现。
- 契约知识落 `source-anchors.md` 新锚点行（nop-search 无 owner doc→缺口声明沿用）。

## Non-Goals

- I-C6-001 幂等腿（G72 批）；MA-05/I-C1-007 等 P3 工单（不在 G71 成员）；CL-005/CL-012 平台回流（增强非前置）；nop-core FileWatcher/NioFileWatchService/IZipTool 平台侧改造；CL-011 移交轨。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-E + fresh-session 取证 + 勘正 3）

- [x] 设计文档（`ai-dev/design/concurrency/search-watch-testinfra-batch.md`）
- [x] 成员取证（fresh session：全在位 + 勘正 3 处[I-C4-002 第三腿漂移/MA-03 debounce 描述漂移/MA-04 锚点修正+两处新发现；R1-M9 追勘：MockTask getDelay 实为 :26-29]）
- [x] 独立 draft review（R1 must-fix-first：2B+4M+3m 全修——B1 MA-03 注入接缝显式声明/B2 MA-04 周期裁定补契约对照→定边界声明案/M3 MA-01 锁纪律+五站点清单/M4 maxTopics 配置来源/M5 对照腿不启真执行器/M6 MA-02·E 接缝/M7 同包+字段可见性/M8 错误码/M9 锚点追勘）

### Phase 2 - 5 工单实现 + 回归测试

Status: completed
Targets: nop-search/nop-search-lucene + nop-runner/nop-cli-core + nop-autotest/nop-autotest-core
Item Types: `Fix`（5）

**实施顺序**（串行；MA-04 先于 MA-03）

1. MA-04+D：MockScheduledExecutor 整体重写（nanoTime 锚定 + TimeUnit 忠实换算 + started 守卫 reject + 源 promise 返回 + whenComplete 清理 + destroy 排空 + expireTasks 驱动 API（推进内部虚拟时钟偏移——R2-M3 语义钉）+ **周期复排裁定=边界声明案**[「仅手动驱动单轮、不承担 IScheduledExecutor 周期绝缘/退避契约」javadoc+删死 period 字段——R1-B2，对照 IScheduledExecutor:30-52 契约] + scheduleWithFixedDelay synchronized 补齐）；载体 TestMockScheduledExecutorContract 五腿（对照腿=DefaultScheduledExecutor 语义基准，不启真执行器[R1-M5]）
2. MA-01+A：LuceneSearchEngine 三 CHM 协调设施（选型留评审但整族一次裁定；**评审必查**[R1-M3+R2-M1/M2]：rollback 在 addDocs/removeDocs catch 内——RW 锁读升级写自死锁陷阱→同锁域或 running 旗标（旗标案须伴随在途引用保护或显式声明腿 2 通过语义）；全部公共读路径站点（search 五处 acquire/release :320-333/:451-474/:510-534/:548-613/:698-748 + refreshBlocking :296-301）全纳入读锁 span）+ topic 上限/LRU 逐出（LuceneConfig 新增 maxTopics，容量腿 setter 注入小值[R1-M4]；destroy 后二次校验失败抛 NopException 复用 ERR_LUCENE_OPEN_INDEX_FAIL[R1-M8]）+ ISearchEngine javadoc 契约；载体 TestLuceneSearchEngineCoordination 三腿+容量腿+三 CHM 尺寸断言（**包 io.nop.search.lucene 同包，字段降 package-private 为显式生产变更**[R1-M7]）；S-04 钉保持绿
3. MA-02+B：doWatch 接住 ICancellable + finally 无条件 cancel（null 守卫[R2-m4]）+ locallyOwned stop 保留；载体 TestCliWatchCommandLifecycle（含未调 stop 断言腿；接缝[R1-M6/R2-m2]：stdin EOF 腿 System.setIn 空流 try/finally 恢复、ListAppender 挂 LOG）
4. MA-03+C：in-flight AtomicBoolean + dirty 补偿 + tmp+ATOMIC_MOVE；**注入接缝生产变更[R1-B1]**：executor 工厂方法可覆写或 scheduleZipUpdate 包私有（实现评审二选一，不触 testParameterDefaults 冻结面）；载体 TestCliWatchZipCommand 扩展（原子腿+重叠腿，用 MA-04 重写后替身经 RateLimitExecutorImpl 包装驱动[R2-m3]）
5. I-C4-002+E：processEvents catch+LOG 对齐（processEvents 改包私有[R1-M6]，异常脚本=.xpl 经 XLang.parseXpl）；载体 TestCliDiffAndWatchFixes 扩展异常腿

Exit Criteria:

- [x] 5 工单全部实施
- [x] 载体表逐条存在且断言目标行为（设计文档 §二）
- [x] 关键项先红后绿或变异论证（≥3 链，设计文档 §三 计划逐链留证——16 链留证 `_tmp/red-green-ledger-2563.md`）
- [x] **MA-01 禁拆分履行**：四成员同设施同 diff；S-04 正向约束保持绿
- [x] **MA-02 footgun 拦截腿**：未调用共享服务 stop 断言随 diff
- [x] **MA-04 周期复排**：已裁定边界声明案（R1-B2，javadoc 声明不承担周期契约+删 period 死字段）
- [x] C10-001/003/004/005/007 成员面同 diff 兑现
- [x] E3：三叶逐模块绿（nop-search-lucene 27 / nop-cli-core 76 / nop-autotest-core 36）
- [x] E1 全量编译门禁 BUILD SUCCESS（`./mvnw clean install -DskipTests -T 1C` 全 reactor BUILD SUCCESS，nop-cli 未再成为已知面——stub artifact 安装后全绿）
- [x] 契约知识落 `source-anchors.md` 新锚点行（R4-29-A~D 四行，缺口声明沿用）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 三叶 EXIT=0
- [x] `ai-dev/logs/` 已更新（ai-dev/logs/2026/11-12.md）

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（5/5）
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步（缺口声明+新锚点）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（触达模块）
- [x] `./mvnw test`（逐模块）
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### I-C6-001 幂等腿 / C4-005 watch 观察项 / 平台 zipDirTo temp+rename / F9 globalState 脏态观察
- Classification: `watch-only residual`（裁定 C/E 明示移交边界）
- Why Not Blocking Closure: I-C6-001 归 G72 批；C4-005 随 MA-01 治理观察；平台侧留档 backlog；F9 属脚本契约不属本批。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-12，must-fix-first）**：2B+4M+3m 全部修正落 plan/design。B1 裁定 C executor 硬编码 :100-101+scheduleZipUpdate private 无注入缝 → 显式声明生产接缝变更（工厂方法可覆写或包私有，二选一评审定）；B2 裁定 D re-arm 案与 IScheduledExecutor:30-52 已裁定周期契约（fixedDelay 指数退避+绝缘）冲突 → 周期复排定为边界声明案（替身无真实线程不能履约退避；零消费方可接受）；M3 MA-01 rollback 在 addDocs catch 内→RW 锁读升级写自死锁必查+search 五 acquire/release 站点全纳入读锁 span；M4 topic 上限配置来源=LuceneConfig.maxTopics 新字段；M5 五腿对照=语义基准不启真执行器（避真实线程 flaky）；M6 MA-02 stdin 腿 System.setIn/ListAppender、E 腿 processEvents 包私有+.xpl 注入；M7 载体测试须放 io.nop.search.lucene 同包+字段降 package-private 显式声明；M8 destroy 二次校验失败抛 NopException 复用 ERR_LUCENE_OPEN_INDEX_FAIL；M9 设计勘正 3 自漂 4 行追勘（getDelay :26-29）。修正后 ready-to-execute。

**Draft review R2（fresh session，换 task_id，2026-11-12）**：0B+3M+4m 全部修正落 design/plan——M-1 旗标案能力不对等（不排斥在途 acquire searcher→腿 2 目标面，旗标案须伴随引用保护或显式声明通过语义）；M-2 读锁 span 清单漏 refreshBlocking :296-301；M-3 expireTasks 与 nanoTime 锚定组合隐含内部虚拟时钟，钉死语义（推进偏移，非仅强制触发）；m-1 design §五.3 旧锚点同步；m-2 System.setIn try/finally 恢复纪律；m-3 scheduleZipUpdate 收 IRateLimitExecutor→替身须 RateLimitExecutorImpl 包装；m-4 finally cancel null 守卫。R1 九项落位核验全 PASS。两轮收敛，无 Blocker，进入实施。

## Closure

Status Note: 8.69/R4-29 全 5 工单落地（MA-04 替身整体重写单一 diff + MA-01 per-topic RW 锁四成员单设施单 diff + MA-02 ICancellable 接住 + MA-03 in-flight/zipDirty 双重补偿 + tmp+ATOMIC_MOVE + I-C4-002 catch+LOG 单站对齐），16 链变异红证（`_tmp/red-green-ledger-2563.md`），C10-001/003/004/005/007 成员面同 diff 兑现，S-04/参数默认/本地路径/RateLimitExecutorImpl/nop-core 平台面冻结面零回退，E3 三叶逐模块绿（autotest-core 36 / search-lucene 27 / cli-core 76）+ E1 全量 BUILD SUCCESS + scan-hollow 三叶 EXIT=0，source-anchors R4-29-A~D 四锚点落账（owner doc 缺口声明沿用）。closure audit APPROVED-with-corrections（0B/0M/4m 文档级全闭环）。
Completed: 2026-11-12

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit（0 Blocker / 0 Major / 4 Minor 文档级，全部闭环）
- Evidence:
  - 五工单机制 live 取证逐条 PASS（RW 锁六读站点/rollback 同写锁域/destroy 双重校验/maxTopics LRU/替身重写八面/processEvents catch 等，锚点见 audit 报告）
  - 载体测试存在且断言目标行为（TestMockScheduledExecutorContract 8 钉/TestLuceneSearchEngineCoordination 5 腿/TestCliWatchCommandLifecycle 含 footgun 腿/TestCliWatchZipCommand overlap+atomic 腿/TestCliDiffAndWatchFixes 异常腿）
  - 红绿账本抽验 4 链：全部恢复 grep 在 live 命中，全仓 MUTATION 残留 ×0
  - 冻结面 git diff 零触碰；三叶 fresh 实跑全绿与 plan E3 一致
  - Anti-Hollow 三链 PASS（生产 zip 链连通/MA-04 替身被 MA-03 重叠腿真实消费/逐出复用 removeTopic）
  - `check-plan-checklist.mjs --strict` EXIT=0；`check-doc-links.mjs --strict` EXIT=0；scan-hollow 三叶 EXIT=0
  - Deferred 四项分类诚实性 PASS（无 in-scope live defect 降级）
  - 工作树混载登记：`nop-cli-errors.i18n.yaml`（生成物）65 行新增含本批 executor-destroyed 词条及此前批次累积词条，E1 install 后 codegen 再生成，历史惯例随 chore 同步提交

Follow-up:

- cli-core 对已移出模块树的 nop-ai-translate stale 依赖清理（m2 stub 环境处置维持）
- 平台 IZipTool.zipDirTo temp+rename 改造留档 backlog（本批使用方层已闭合写-读窗口）
- I-C6-001 幂等腿归 G72 批

