# 2382 full-code-review R1.16 — nop-batch 记录面修复批 b（P1-3.4-001/002）

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.16；findings `P1-3.4-001`（JDBC-01）+ `P1-3.4-002`（RL-01），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/3.4-batch-deep-audit-b.md`
> Related: plan 2381（R1.15 同 audit 3.3 面）；P1-4.2-003 upsert 归 R1.41 勿双立；P2-3.4-007（失败向列写入——审计移交注记滑号勘正）为台账行非本 plan 交付物
> Owner Doc: `docs-for-ai/03-modules/nop-batch.md`

## Purpose

收口 nop-batch 记录面两条 P1：①`OrmBatchRecordSnapshotBuilder.restore` 补 `ret.add(item)`（当前返回恒空列表→retry 集静默丢弃整 chunk 记录）+ `"orm-entity"` snapshotBuilder 魔法值文档随修；②`DaoBatchRecordHistoryStore.saveProcessed` 补 mandatory 三列（retryCount/batchSize/handleStatus）赋值（当前 dead-on-arrival——首个成功 chunk 即整任务失败）+钉定测试升级真 IEntityDao flush 校验。

## Current Baseline

- **P1-3.4-001**：`OrmBatchRecordSnapshotBuilder.restore`（nop-batch-orm）循环内 `item.orm_restoreValues(values); session.attach(item, cascadeAttach);` 但 **`ret` 从未 add**（:36-43 自初版如此）→ 返回恒空列表。`BatchConstants.SNAPSHOT_BUILDER_ORM_ENTITY = "orm-entity"` 魔法值经 `ModelBasedBatchTaskBuilderFactory.getSnapshotBuilder:351` 映射到本类，全仓零文档。
- **P1-3.4-002**：`DaoBatchRecordHistoryStore.saveProcessed`（nop-batch-dao :78-110）只赋 batchTaskId/recordKey/resultStatus/recordInfo 四项；`nop-batch.orm.xml` 的 retryCount/batchSize/handleStatus 三列 `mandatory="true"`（:212-218）+ 平台校验默认 true + 三方言 NOT NULL → 按文档配置 historyStore 的任务**首个成功 chunk 即校验失败整任务失败**。既有钉定测试 `TestDaoBatchRecordHistoryStore` 用 Proxy IEntityDao（saveEntity 仅收集，不触发校验）=审计所指「Proxy 盲区」。
- **测试基建**：nop-batch-dao 已有 nop-autotest-junit test 依赖（pom :34）+ `TestDaoBatchStateStore` 完整 autotest 先例（localDb+真 IEntityDao+new BatchTaskContextImpl）；`DaoBatchRecordHistoryStore(IEntityDao, IBatchHistoryStoreModel)` 公共构造器可直接以真 dao 构造；`BatchTaskContextImpl` 提供 taskId/evalScope。**nop-batch-orm 无 src/test 与任何 test 依赖（审查 B1）**——需 pom 补 test-scope junit-jupiter（先例 nop-batch-core :21-24）+新建 src/test。

## Goals

- restore 返回「恢复后的完整记录列表」（retry 消费方语义修复）。
- saveProcessed 落库行补三列默认值：retryCount=0（首执行）、batchSize=filtered.size()、handleStatus=0（成功态，与 resultStatus=0 同语义）；真 IEntityDao flush 校验测试锁定 NOT NULL 可通过。
- `"orm-entity"` 魔法值登记 owner doc（snapshotBuilder 用法说明）。
- 受影响模块（nop-batch-orm/nop-batch-dao）测试全绿。

## Non-Goals

- P2-3.4-007（失败向列写入——saveProcessed 失败分支落库；编号勘正见审计报告移交注记）为台账行，不改 saveProcessed 的「仅成功写历史」语义。
- P2-3.4-006/008/009（死列/死契约）与分页语义族（台账行）。
- snapshotBuilder 魔法值重构为注册表机制（仅文档随修，行为不变）。

## Scope

### In Scope

- nop-batch-orm：`OrmBatchRecordSnapshotBuilder.restore` 补 ret.add
- nop-batch-dao：`DaoBatchRecordHistoryStore.saveProcessed` 三列赋值；`TestDaoBatchRecordHistoryStore` 扩展三列断言（Proxy 测试保留）+ 新增真 IEntityDao flush 校验 autotest
- 测试：restore 判别测试（修复前空列表 FAIL）；saveProcessed 三列断言；真 flush 校验
- owner doc：nop-batch.md snapshotBuilder=`orm-entity` 用法说明

### Out Of Scope

- Non-Goals 全部；demo 模块

## Execution Plan

### Phase 1 - 两修复 + 三层测试

Status: completed
Targets: `OrmBatchRecordSnapshotBuilder`、`DaoBatchRecordHistoryStore`、nop-batch-orm/dao 测试

- Item Types: `Fix`

- [x] nop-batch-orm pom 补 test-scope junit-jupiter（审查 B1，先例 nop-batch-core :21-24）+新建 src/test 树
- [x] `OrmBatchRecordSnapshotBuilder.restore`：循环内补 `ret.add(item);`（恢复后的记录列表语义）
- [x] `DaoBatchRecordHistoryStore.saveProcessed`：`result.setRetryCount(0)` + `result.setBatchSize(filtered.size())`（显式约定注释在位）+ `result.setHandleStatus(0)`（显式约定取值援引 resultStatus）——注释援引 P1-3.4-002
- [x] 测试①（nop-batch-orm 判别）：TestOrmBatchRecordSnapshotRestore——Proxy 布景（IOrmEntity 需 hashCode/equals 真值供 HashMap 键用——首跑 NPE 勘正），断言恢复列表=items 全量 1/1
- [x] 测试②（nop-batch-dao）：TestDaoBatchRecordHistoryStore.testSaveProcessedOnSuccess 扩展三列断言（retryCount=0/batchSize=3/handleStatus=0）——3/3（family 4 含 RealFlush）
- [x] 测试③（nop-batch-dao 真 flush 校验——钉定升级）：TestDaoBatchRecordHistoryStoreRealFlush（JunitAutoTestCase localDb+真 IEntityDao+flush+回读断言 3 行 mandatory 列非空）1/1——Proxy 盲区封堵
- [x] **判别验证**：测试①判别逻辑=断言 restored.size()==items.size()（修复前恒空必 FAIL）；真 flush 测试③即②的判别面（缺三列赋值时 flush 抛 ERR_ORM_MANDATORY_PROP_IS_NULL）

Exit Criteria:

- [x] restore 返回完整恢复列表（判别测试绿）
- [x] saveProcessed 三列赋值后真 flush 校验通过（③绿）
- [x] nop-batch-orm 1/dao 11 全绿（含既有 4 用例零回归；core 61 不受扰）
- [x] owner doc 处置归 Phase 2（已落地）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-batch.md`

- Item Types: `Fix`

- [x] nop-batch.md：snapshotBuilder 用法说明（`snapshotBuilder="orm-entity"` 魔法值→OrmBatchRecordSnapshotBuilder〔恢复快照/重试集语义〕+ restore 返回完整恢复列表契约；bean 形态自定义）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap/index/daily log 收口动作落盘（roadmap R1.16 翻 done、index P1-3.4-001/002 → fixed、MR 表 R1.16 行）

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] `ai-dev/logs/` 对应日期条目已更新
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-3.4-001/002）
- [x] 行为/契约结果已达成（restore 判别+三列断言+真 flush 校验）
- [x] 必要 focused verification 已完成（orm 1/dao 11 全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步（nop-batch.md 两处）
- [x] 独立子 agent closure-audit 已完成并记录证据（见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 restore 返回列表真实含恢复项（测试①断言 restored.size==items.size）+saveProcessed 三列在真 flush 下通过校验（测试③回读断言非空——非仅 Proxy 收集）
- [x] `./mvnw test -pl nop-batch/nop-batch-orm,nop-batch/nop-batch-dao -am -Dtest='!TestBatchTaskCheckpoint' -Dsurefire.failIfNoSpecifiedTests=false` 全绿
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-batch-orm --severity high` 与 `--module nop-batch-dao` 退出码均 0

## Deferred But Adjudicated

（无——P2-3.4 属台账行非本 plan 域）

## Non-Blocking Follow-ups

- P2-3.4-001..009 台账行（R1.0 P2 裁决通道）

## Closure

Status Note: P1-3.4-001/002 按审计处方交付：restore 补 ret.add（retry 集替换语义修复）+saveProcessed mandatory 三列显式赋值（取值约定注释钉死）+钉定测试升级真 IEntityDao flush（Proxy 盲区封堵）+orm-entity 魔法值 doc 登记。persist 缺省零改动、saveProcessed 仅成功写历史语义保持。两 Phase 全 completed，独立 closure audit Can Close（bookkeeping 已随本段补齐）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: closure-audit agent_013be377（fresh read-only session）
- Evidence:
  - Req1 Phase/勾选：Phase 1 全勾；Phase 2 勾选与 Closure 段随本审计补齐，PASS
  - Req2 live-code：restore :43 ret.add（P1-3.4-001 注释在位）；saveProcessed :101-103 三 setter（P1-3.4-002 注释在位）；orm pom junit test-scope+注释；orm.xml 三列 mandatory :212-217 零改动，PASS
  - Req3 测试：TestOrmBatchRecordSnapshotRestore 1/1（判别：restored.size==items.size，修复前恒空必 FAIL）；TestDaoBatchRecordHistoryStore 3/3（三列断言扩展）；TestDaoBatchRecordHistoryStoreRealFlush 1/1（真 flush+回读断言）——surefire 2026-10-10 04:33 orm 1/dao 11/core 61 全零失败，PASS
  - Req4 一致性：roadmap R1.16 done（:147）+index :127-128 fixed+MR 行（audit 指出缺失——已补）+daily log（audit 指出缺失——已补）+owner doc 两处在位+doc-links 0，PASS（补齐后）
  - Req5 处方忠实：orm.xml 零改动（persist 缺省保持）/仅成功写历史语义保持（failure 分支测试仍绿）/编号勘误（005→007）在审计报告留痕，PASS
  - Req6 hollow/no-op：变更 hunk 全真实逻辑，双模块 hollow 扫 0，PASS
  - 工具退出码：check-plan-checklist --strict=0；scan-hollow-implementations（orm+dao）=0；check-doc-links --strict=0
  - Advisory（不阻塞）：plan 「4/4」计数勘正为「3/3（family 4 含 RealFlush）」随本段落盘

Follow-up:

- P2-3.4 台账行（R1.0 P2 裁决通道）
- 无其余 plan-owned 剩余工作
