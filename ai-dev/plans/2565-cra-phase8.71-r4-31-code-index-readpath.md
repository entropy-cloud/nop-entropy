# 2565 CRA Phase 8.71 — R4-31 6.3 读路径/C5 族束（14 工单）

> Plan Status: completed
> Last Reviewed: 2026-11-12
> Source: roadmap 工作项 8.71；批次定义 = cra-index.md :473 P2-G88 行；成员发现 = `ai-dev/audits/2026-10/2026-10-23-0900-deep-audit-nop-code-index-concurrency/`；设计文档 = `../design/concurrency/code-index-readpath-batch.md`（裁定 A-N + 三设施 F1/F2/CodeIndexAfterCommit + 勘正 6）
> Related: plan 2532（R3-09 code-index 批——W-17 finder 半边已修防双计、2532 勘误⑤单源义务）、plan 2530（R3-07 行号漂移源）
> **PA**：无（14 工单全 Java 层，nop-code-core/nop-code-flow）
> **特殊约束**：① 三跨工单设施（F1 GitProcessRunner 超时桥/F2 C5 上限+分批纪律/CodeIndexAfterCommit）优先，新增产品类 ≤3；② W-11 36 站点=验证检查单非逐站修（单点收敛），getAll 修后保留 monitor（CHM 弱一致不满足强隔离——冻结条款）；③ W-12 两正确对照站点 :539/:1331 冻结；④ W-17 防双计（finder 半边 2532 已修，本批只接装配面+MAX_ASSEMBLY_SYMBOLS 单源义务）；⑤ 实施顺序 Phase A(W-11→W-12) → B(W-04) → C(W-07→W-09→W-08+W-06) → D(W-13→…→W-19)

## Purpose

关闭 R4-31 的 **14 个 P2 工单**：C1 缓存簇（W-11 SymbolTable 裸读单点收敛、W-12 失效时序 afterCommit 化）、外部进程超时（W-04 四站点统一桥）、删除链/双写/失效面（W-06/W-07/W-08/W-09）、C5 族 7 张上限缺失（W-13~W-19 同根因自并）。C10 缺保护 TP-004~013/015/016/018/019 随本体验收前置（勘正 1 口径）。

## Current Baseline（live 亲读 @ 2026-11-12，取证 agent fresh session；勘正 6 已登记）

（逐条锚点见设计文档 §〇；W-04 :855→:959 等行号漂移已核准，CodeCacheManager/SymbolTable 零漂移；W-06 行号补全 :1013-1023/:77-93/:102-106）

## Goals

- 14 工单全部落地，载体先红后绿或变异论证/结构钉诚实登记（≥3 链/工单）。
- 三设施落地（F1 桥/F2 纪律/AfterCommit 工具），新增产品类 ≤3。
- W-11 36 站点验证检查单全过；2532 勘误⑤单源义务兑现。
- 契约知识落 `source-anchors.md`（owner doc = `docs-for-ai/03-modules/nop-code.md`）。

## Non-Goals

- W-01/W-02/W-03/W-05/W-10（2532 已修或 G89 排除）；W-20/W-35 接线前置工单（登记不实施）；W-08 第 4 副作用点 deleteIndex removeTopic（锁内事务外，登记防误扩）；TP-014/TP-017 排除项（勘正 1）。

## Execution Plan

### Phase 1 - 设计与取证

Status: completed（设计裁定 A-N + 三设施 + fresh-session 取证 + 勘正 6）

- [x] 设计文档（`ai-dev/design/concurrency/code-index-readpath-batch.md`）
- [x] 成员取证（锚点全核 + 行号漂移核准 + 防双计确认 + 勘正 6）
- [x] 独立 draft review R2（fresh session 换 task_id：0B+3M+3m 全修——R2-1 §三/§二 W-16 链2 与载体行未同步改写→file-wins 变异方向勘正/R2-2 批内累积语义缺口新发现→裁定 K 补快照+增量累积/R2-3 裁定 L bullet1 与 §三链2 未同步→仅 :263 钉面/m-A W-12 防回退结构钉/m-B 设计 §四命令聚合器勘正/m-C 旧不变量句+裁定 B 标题措辞勘正；W-08/W-04 复核结论=无需改；R1 全部落位核验 PASS+死锁/over-invalidation 想象性分析通过）
- [x] 独立 draft review（R1 must-fix-first：3B+3M+5m 全修——B1 W-12 afterCommit 在四站点无事务上下文=no-op 空壳→改移入事务体首行+锁影响复核登记/B2 W-16 胜出不变量与 live 相反→勘正 global-wins 保持语义/B3 W-17 :148/:864 截断改公开输出→逐站点重裁仅 :263 收口+如实引用既有 finder cap/M1 F1 桥 extraEnv 承载凭据/M2 超时语义定形结果对象/M3 E3 补 nop-code-service+命令勘正/m1-m5 随批）

### Phase 2 - 14 工单实现 + 回归测试

Status: implemented（14/14 落地+红绿证+门禁完成，待 closure audit）
Targets: nop-code/nop-code-core + nop-code/nop-code-flow
Item Types: `Fix`（14）

**实施顺序**（串行，按设计 §一）

- Phase A：W-11（SymbolTable CHM×2+truncated volatile+putIfAbsent 原子化单点收敛，null 键守卫保留[R1-m1]）→ W-12（**四失效点 invalidate 移入 runInTransaction 事务体首行**[R1-B1：live 核实四站点均无事务上下文，无事务直行=no-op 空壳——原 afterCommit 包裹方案作废；:458/:551 锁外挪锁内登记锁影响复核]；CodeIndexAfterCommit 工具仍用于回调封装；两对照站点 :539/:1331 冻结）
- Phase B：W-04（F1 GitProcessRunner 超时桥：run/runLines 返回结果对象 output+exitCode+timedOut[R1-M2]；桥 API 带 extraEnv 承载凭据[R1-M1]；四站点：CodeIndexService.gitOutput[timedOut→ERR_CODE_REBUILD_NOT_GIT_REPO]/ClusterWorkspaceManager×2 持 repoLock/ChangeAnalyzer[timedOut→truncated 不抛]）
- Phase C：W-07（findSymbolIdsByFileId 分页穷尽+pageSize 缝）→ W-09（deleteFileRecords 补 Dependency targetFilePath 对称腿，同方法同 diff）→ W-08（三站点经 CodeIndexAfterCommit 移 afterCommit+best-effort 边界声明）→ W-06（deleteIndex 补 NopCodeShardLedger 同事务清理）
- Phase D：W-13（MAX_EDGES+BatchQueue 分批）→ W-14（MAX_CACHE_DEPENDENCIES+截断旗标）→ W-15（replaceRows 插入侧对称分批）→ W-16（mergedTable 批级共享快照，**不变量勘正=global-wins**[R1-B2：live 实为 global 胜出 file 仅补缺，保持公开解析语义]）→ W-17（**逐站点重裁**[R1-B3]：仅 :263 entryPoints 收口；:148/:864 保持全量输入[finder cap 10⁴+本体硬帽双保护，截断即改公开输出]；MAX_ASSEMBLY_SYMBOLS=loadSymbolsByIds 本体硬帽兑现 2532 勘误⑤单源）→ W-18（symQuery setLimit+warnIfCapped）→ W-19（getFileTree 投影变体）

Exit Criteria:

- [x] 14 工单全部实施
- [x] 载体表逐条存在且断言目标行为（设计 §二；载体文件清单见红绿总册——W-11 TestSymbolTableConcurrency / W-12 TestCacheInvalidationAlignment 扩 / W-04 TestGitProcessTimeout+TestGitCallSitesTimeout+TestGitCallSitesTimeoutFlow / W-06 TestShardBuildCoordinator 扩 / W-07+W-09 TestDeleteFileRecordsExhaustiveW07W09 / W-08 TestSearchSyncAfterCommit / W-13 TestExtractorEdgeCap / W-14 TestCodeCacheManagerDepsCap / W-15 TestGraphMetricInsertBatching / W-16 TestIncrementalMergedTableBatching / W-17 TestAssemblySymbolsCapping / W-18+W-19 TestCodeQueryServiceCapAndFileTree）
- [x] 红证按设计 §三 逐工单留证（总册 `_tmp/red-green-ledger-2565.md`；结构钉诚实登记处逐条标注：W-11 链4/W-04 链4/W-12 链2/W-13 链2 链4/W-14 链3/W-16 链3/W-17 链3/W-18 链2 链3/W-19 链2 链3）
- [x] W-11 36 站点验证检查单全过（grep 全量核对登记总册）+ getAll monitor 冻结条款维持（快照免疫腿常驻）
- [x] W-13 分批可观测缝落点留实施评审登记[R1-m4]（结构化 DEBUG 缝案+session 代理行为级由 W-15 承载）；W-06 夹具不调 beginShardBuild[R1-m3]；W-08 回调携提交前物化 POJO[R1-m2]
- [x] W-12 两对照站点 :539/:1331 零触碰（grep 断言登记）；W-17 防双计登记（:148/:864 全量输入冻结+2530 单飞门零耦合声明）
- [x] E3：nop-code-core / nop-code-service / nop-code-flow 逐模块绿（service 342 中 1 失败=TestIndexNopXlangModule **HEAD 预存回归**——worktree 干净 HEAD bca482cab8 复跑同败，与本批零交集如实登记；排除该测试三叶全绿）[-am 全量被上游既有 flake TestResourceCacheInvalidationChain 阻断（单测复跑 4/4 绿）归因登记]
- [x] E1 全量编译门禁 BUILD SUCCESS（`./mvnw clean install -DskipTests -T 1C`）
- [x] owner doc `docs-for-ai/03-modules/nop-code.md` 契约落账（新「读路径/删除链/外部进程并发契约」节）+ `source-anchors.md` 新锚点行（CODEINDEX-RT-001/002/003）
- [x] 工具门禁：check-doc-links --strict EXIT=0；scan-hollow `--severity high` 三叶 Critical/High=0
- [x] `ai-dev/logs/` 已更新（2026/11-12.md 顶部条目）

Status 注记（2026-11-12 实施完成）：Phase 2 全部 Exit Criteria 勾选兑现；**Plan Status 保持 active 待独立 closure audit**（Closure Gates 未勾选——由独立子 agent 审核）。

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（14/14）
- [x] 所有 in-scope confirmed contract drifts 已收敛
- [x] 行为/契约结果已达成
- [x] 必要 focused verification 已完成
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] Anti-Hollow Check：closure audit 验证调用链连通与无空壳
- [x] `./mvnw compile`（触达模块）
- [x] `./mvnw test`（逐模块）
- [x] 代码规范自查 + scan-hollow 通过（仓库无 checkstyle 插件）

## Deferred But Adjudicated

### W-20/W-35 接线前置工单 / W-08 第 4 副作用点 / TP-014/TP-017
- Classification: `watch-only residual`（裁定 D/勘正 1/勘正 4 明示边界）
- Why Not Blocking Closure: 接线前置归 N6.4 工单；removeTopic 在事务外不属本批三站点口径；TP-014/TP-017 为 G89 排除项。
- Successor Required: `no`

## Review 记录

**Draft review R1（fresh session，2026-11-12，must-fix-first）**：3B+3M+5m 全修落 plan/design。B1 W-12「afterCommit 四行包裹」在四站点全部无事务上下文（:458/:551 锁外/:1001 事务前/:2969 事务前），CodeIndexAfterCommit 无事务直行=行为 no-op 空壳修复，红证腿 1 无法变绿→改结构性方案：invalidate 移入 runInTransaction 事务体首行（:458/:551 锁外挪锁内、锁影响复核随实施留证）；B2 W-16 裁定「file 后 add 覆盖」与 live :1702-1711 相反（实为 global 胜出 file 仅补缺）→不变量勘正 global-wins 保持公开解析语义；B3 W-17 :148 symbolsById 消费方=findDominantPackageByNames 遍历全部成员算 label（公开 DTO）——截断即改输出，:864 bridge 依赖 hubs 集外 id 同理→逐站点重裁：仅 :263 收口、:148/:864 保持全量（2532 finder cap 10⁴ 已封口+本体硬帽双保护，「装配面无界」误判勘正）；M1 F1 桥「凭据 env 在桥外组装」不可行（pb 在桥内 start）→extraEnv 参数缺省零 diff；M2 超时语义三处矛盾（抛异常/标志置位/truncated 不抛）→定形结果对象 output+exitCode+timedOut、异常翻译留站点；M3 E3 漏 nop-code-service+`-pl` 聚合器命令空转→补全；m1 putIfAbsent null 键 NPE 守卫/m2 W-08 回调携提交前 POJO 禁触 session/m3 W-06 夹具不调 beginShardBuild（自带 deleteLedger）/m4 W-13 分批缝留实施评审/m5 cra-index :473 行文字勘正随收敛注兑现。行号引用与测试基面全部核实吻合。修正后待复审。

**Draft review R2（fresh session，2026-11-12）**：0B+3M+3m——R2-1 §三 W-16 链2/§二载体行未随 global-wins 勘正同步（原变异方向在勘正后是正确行为不会红）→改写为 file-wins 变异；R2-2 新发现批内累积语义缺口（live 同批前序文件符号持续入 globalTable，批首冻结快照会丢符号违反语义保持承诺）→裁定 K 补「快照+随每文件增量扩入」；R2-3 裁定 L bullet1 与 §三 W-17 链2 未随逐站点重裁同步→仅 :263 钉面；m-A W-12 补防回退结构钉；m-B 设计 §四命令聚合器勘正；m-C 旧不变量句+裁定 B 标题措辞勘正。B1 死锁/over-invalidation 分析通过（失效执行点与 :539/:1331 同锁序、无反向锁路径）。两轮收敛 0 Blocker，进入实施。


## Closure

Status Note: 8.71/R4-31 全 14 工单落地（Phase A W-11 SymbolTable 单点收敛+W-12 四失效点移事务体/Phase B W-04 GitProcessRunner 超时桥四站点/Phase C W-07+W-09+W-08+W-06 删除链与双写/Phase D W-13~W-19 C5 族上限+分批+global-wins 批级快照），三设施（GitProcessRunner/CodeIndexAfterCommit/F2 纪律）新增产品类 ≤3，红绿+结构钉逐链落 `_tmp/red-green-ledger-2565.md`，C10 缺保护 TP 成员面随本体，冻结面零回退（含 getAll monitor/:539/:1331/GIT_REF_PATTERN/凭据契约/getFiles/GraphMetricStore/单飞门），owner doc nop-code.md 新契约节+source-anchors CODEINDEX-RT-001/002/003 落账。closure audit APPROVED-with-corrections（0B/1M/2m 全闭环）。
Completed: 2026-11-12

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session closure audit（14/14 面 live 取证全 PASS，超出抽查要求）
- Evidence:
  - 十四工单机制 live 取证逐条 PASS（锚点见审计报告表）
  - 载体测试 10 文件腿数与账本一致且断言目标行为
  - 红绿账本抽验 4 链吻合；结构钉诚实登记处逐条与 live grep 一致
  - 冻结面 git diff 零回退；Anti-Hollow 三设施消费链亲证连通
  - **Major-1 闭环**：TestIndexNopXlangModule HEAD 归因证据原被批次编译产物污染——coordinator 真干净 worktree（独立目录 @ HEAD bca482cab8 + `-am` reactor 源码重建）复跑同败（:211 AssertionFailedError），HEAD 预存回归坐实，排除后三叶绿（core 158/0F、flow 46/0F、service 341/0F 14S）；Minor-1 账本行号勘正三处；Minor-2 255MB 日志已清理
  - `check-plan-checklist --strict` EXIT=0；`check-doc-links --strict` EXIT=0；scan-hollow 三叶全 0
  - Deferred 分类诚实性 PASS（W-20/W-35/removeTopic/TP-014/017 watch-only+触发条款）

Follow-up:

- TestIndexNopXlangModule HEAD 预存回归（findSymbols limit-50 无序查询）——非本批缺陷，登记 9.1 对账议题
- `-am` 上游 TestResourceCacheInvalidationChain 既有 flake（2564 同源）

