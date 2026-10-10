# 2393 full-code-review R1.27 — nop-autotest 数据面修复批（P1-6.8-003/005/006）

> Plan Status: active
> Last Reviewed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.27；findings `P1-6.8-003/005/006`（6.8-check-p1-batch-d.md），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.8-check-p1-batch-d.md`
> Related: R1.26（同批次 d 已收口项）；`unit-test-antipatterns.md`（skill 指定）
> Owner Doc: `docs-for-ai/02-core-guides/testing.md`

## Purpose

收口 autotest 数据面三条 P1：①TestCaseJsonDataSplitter 表产物落 `<dir>/tables/input|output/` 而消费方 AutoTestCaseData 契约为 `<dir>/input|output/tables/`——产物静默不可加载；②AutoTestCaseData variant 双约定（列表访问器 `input/<v>/…` vs 单文件访问器/Saver `variants/<v>/…`）——变体输出表永不校验（框架假绿）；③AutoTestCaseDataSaver.removeInputTable 无 isDefaultVariant 守卫静默删除 default 基础表（删/写不对称）。

## Current Baseline

- **P1-6.8-003**：`TestCaseJsonDataSplitter` :128/:143（savaInputEntity/savaOutputEntity）与 :186 附近（saveInputTableData）以 `getInputFile(new File(dir, "tables"), name)` 落 `<dir>/tables/input/<name>`；消费方 `AutoTestCaseData.getFiles("input/tables")`/`readTableData("input/tables/…")` 契约 `<dir>/input/tables/`。修复：两处表落点改 `getInputFile(dir, "tables/<name>")` → `<dir>/input/tables/`（getOutputFile 同理）——其余 json5/sql 顶层产物 `<dir>/input|output/` 与消费方 getFiles("input", ".sql") 已对齐不动。
- **P1-6.8-005**：`AutoTestCaseData` variant 路径多数派（readInputTableData `variants/<v>/input/tables`、getOutputFileName `variants/<v>/output`、getVariants 列举 `variants/`、Saver 写 `variants/<v>/output/tables`）vs 列表访问器少数派错路径（getInputTableFiles(variant)/getOutputTableFiles(variant) 用 `input/<v>/tables`、getInputSqlFiles(variant) 用 `input/<v>`、getInitSqlFiles(variant) 用 `init/<v>`）→ ResultChecker.checkOutputTables:79 扫描面与 Saver 写入面互斥=变体输出表永不校验。修复：四处错路径统一到 `variants/<v>/…`（mergeFiles fallback 结构保留）。
- **P1-6.8-006**：`AutoTestCaseDataSaver.removeInputTable` `getInputTableFile(tableName, null)`=default 基础表 `input/tables/<t>.csv` 直接 file.delete()；对照 saveInputTable:84 有 `isDefaultVariant` 守卫（仅 default 写）。修复：default（isDefaultVariant(variant)）跳过删除+INFO 记录（基础表是手写测试资产）；非 default 删 variant 表文件 `getInputTableFile(tableName, variant)`。
- **测试基建**：nop-autotest-core 有测试树（bundle/util/spike）；mockito 不在 pom——IEntityModel/IColumnModel/IDaoProvider/IOrmEntityDao 用 JDK 动态代理 stub（getColNames 仅触达 getColumns→getCode）；AutoTestCaseData 构造需核（caseDataDir 单参?）；AutoTestCaseDataSaver.removeInputTable 私有——反射调用判别（构造器 (variant, caseData, ormHook)——ormHook null 可行性实施时核）。

## Goals

- splitter 表产物落 `<dir>/input|output/tables/`（消费方契约）；splitter→AutoTestCaseData 往返测试。
- variant 路径统一 `variants/<v>/…`；变体输出表录制→校验可达（ResultChecker 扫描面=Saver 写入面）。
- removeInputTable default 守卫（基础表不再被录制流程静默删除）；非 default 删 variant 文件。
- 判别测试三腿 stash 实证；nop-autotest-core 测试全绿。

## Non-Goals

- variant 文档化语义扩展（@EnableVariants 行为不变）；init/sql 路径的其他面；录制流程重构；nop-autotest-core 其他零测试面（P1-4.2 系台账行）。

## Scope

### In Scope

- `TestCaseJsonDataSplitter` 两处表落点修正
- `AutoTestCaseData` 四处 variant 路径统一
- `AutoTestCaseDataSaver.removeInputTable` default 守卫
- 测试：splitter→loader 往返（Proxy stub dao）；variant 路径统一判别；removeInputTable 守卫判别（反射）
- owner doc：testing.md 数据目录契约注记

### Out Of Scope

- Non-Goals 全部；TestFixtureBundle 面；spike 测试

## Execution Plan

### Phase 1 - 三修 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] TestCaseJsonDataSplitter：savaInputEntity/savaOutputEntity/saveInputTableData 三处表落点 `getInputFile(dir, "tables/<name>.csv")` / `getOutputFile(dir, "tables/<name>.csv")`（若 saveOutputTableData 存在同型第三处一并修）
- [x] AutoTestCaseData：getInputTableFiles(variant)/getOutputTableFiles(variant)/getInputSqlFiles(variant)/getInitSqlFiles(variant) 的 `input|init/<v>/…` → `variants/<v>/…`
- [x] AutoTestCaseDataSaver.removeInputTable：isDefaultVariant(variant) → INFO 跳过（基础表手写资产不自动删）；非 default → getInputTableFile(tableName, variant) 删 variant 文件
- [x] 测试：①splitter→loader 往返（Proxy stub daoForTable/getEntityModel/columns，silent=true）——splitToDir 产出落 `<dir>/input/tables/T1.csv`+`<dir>/output/tables/T1.csv` 且 AutoTestCaseData.getFiles("input/tables")/getOutputTableFiles() 读到；②variant 统一——手工树 `variants/v1/{input,output}/tables/*.csv`+基础表 → getOutputTableFiles("v1")/getInputTableFiles("v1") 含 variant 文件（合并去重）；③removeInputTable 守卫——反射调用：default（variant=null）时基础表不删；非 default 时 variant 文件被删
- [x] **判别验证（三腿 stash）**：①stash splitter 修正→往返 FAIL（落点错读不到）；②stash variant 统一→getOutputTableFiles("v1") FAIL（读不到 variants 下文件）；③stash removeInputTable 守卫→default 场景 FAIL（基础表被删）——各腿恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 三修全落地（落点/variant 统一/守卫）
- [x] 判别测试三腿绿 + 既有测试零回归
- [x] nop-autotest-core 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/02-core-guides/testing.md`

- Item Types: `Fix`

- [x] testing.md：测试数据目录契约注记（tables 落点 `<dir>/input|output/tables/`；variant 约定单一定义 `variants/<v>/…`；基础表不自动删除）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.27 翻 done；index P1-6.8-003/005/006 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位且与 live 行为一致
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-6.8-003/005/006）
- [x] 行为/契约结果已达成（判别三腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-autotest-core 测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] `./mvnw test -pl nop-autotest/nop-autotest-core` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- AutoTestCaseData 其余 variant 面（getInputFileName 的 fallback 回退语义）核查
- splitter 的 init/sql 产物与消费方 getInitSqlFiles 对齐核查（非本批发现面）

## Closure

Status Note:
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

-
