# 2397 full-code-review R1.31 — nop-record readCollection fixed 分支 subStart 错序（P1-7.7-001）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Completed: 2026-10-10
> Source: roadmap `ai-dev/backlog/full-code-review-roadmap.md` R1.31；finding `P1-7.7-001`（7.7-pulsar-record-residual-audit.md 派生，plan-203 R 面 still-live），见 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/7.7-pulsar-record-residual-audit.md`
> Related: plan-203（e530b5c0cc 引入缺陷本体）；P1-4.4-M03（R1.30 已收口）
> Owner Doc: `docs-for-ai/02-core-guides/record-mapping.md`

## Purpose

收口 nop-record readCollection fixed 分支 subStart 捕获错序：`subInput = in.subInput(length)` 之后才捕获 `subStart = in.pos()`——急切 reader（ByteBuffer/ByteBuf）下 subInput 推进父位置，subStart 污染为区域末尾 → remaining=(subStart+length)-pos 计算出多余残留 → readOffset 再跳一个区域长度 = 双重跳过（区域后字段读到错误位置/越界）。

## Current Baseline

- **缺陷本体**：`AbstractModelBasedRecordDeserializer.java:262-264`——`Input subInput = length>0 ? in.subInput(length) : in; long subStart = in.pos();`（subStart 在 subInput 后）。流式同功能路径 `StreamingRecordDeserializer:358-362` 顺序正确（先 `setCollSubStartPos(in.pos())` 再 `subInput(length)`）。
- **触达面**：fixed 全仓 3 文件且唯一使用方 @Disabled 零启用场景（audit 修正 audit-b 记 2）——P1 维持不升 P0（R1.0 从严升级依据已附）。
- **测试基建**：nop-record 测试树成熟——`TestRecordRegionBoundary` 既有惰性 reader 的 fixed 集合用例（testFixedCollectionBoundary，StreamBinaryDataReader 不触发缺陷）+ `testRegionLeftoverWithEagerReader`（急切 readObject 对照）；fixture `test-fixed-collection-boundary.record-file.xml`（items fixed length=15 + c length=5）可直接复用于急切版判别（数据 "111112222233333CCCCC"：旧代码 remaining=15 → c 字段错位/越界）。

## Goals

- subStart 捕获移到 subInput 之前（对齐流式路径形态）；修复后急切 reader 下区域后字段（c）正确读取。
- 新增「急切+非流式+fixed 集合」回归测试（audit 指出的测试矩阵缺口）。
- 判别腿 stash 实证；nop-record 测试全绿。

## Non-Goals

- StreamingRecordDeserializer（正确路径）；@Disabled 唯一使用方的启用；readObject 同公式核对（audit 已确认正确）。

## Scope

### In Scope

- `AbstractModelBasedRecordDeserializer` fixed 分支 subStart 捕获顺序修正
- 测试：TestRecordRegionBoundary 新增急切 fixed 集合用例
- owner doc：record-mapping.md 如有区域对齐描述则同步（实施时核对）

### Out Of Scope

- Non-Goals 全部；S-7.7-C1（R2-4 后半合并裁定通道）

## Execution Plan

### Phase 1 - 修复 + 判别测试

Status: completed
Targets: 上列文件

- Item Types: `Fix`

- [x] fixed 分支：`long subStart = in.pos();` 移到 `subInput = in.subInput(length)` 之前（对齐流式 :358-362 形态）；变量次序重排+注释记录急切 reader 语义
- [x] 测试：TestRecordRegionBoundary 新增 testFixedCollectionWithEagerReaderReadsTrailingField——ByteBufferBinaryDataReader + "111112222233333CCCCC" + 现有 fixture → items=3 且 c="CCCCC"（修复前 c 读失败/数据越界异常）
- [x] **判别验证**：stash 修复 → 新用例 FAIL（c 字段错位或 NO_ENOUGH_DATA）；恢复后绿
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 修复落地（捕获顺序对齐流式形态）
- [x] 判别测试绿 + 既有测试零回归
- [x] nop-record 测试全绿
- [x] owner doc 处置归 Phase 2

### Phase 2 - owner doc + 收口

Status: completed
Targets: `docs-for-ai/02-core-guides/record-mapping.md`

- Item Types: `Fix`

- [x] record-mapping.md 如有区域对齐/deserializer 描述则同步；无则显式记录核对为零
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] roadmap R1.31 翻 done；index P1-7.7-001 → fixed；daily log 条目

Exit Criteria:

- [x] owner doc 条目在位（或核对为零显式记录）
- [x] doc-links 检查退出码 0
- [x] 收口动作落盘

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（P1-7.7-001）
- [x] 行为/契约结果已达成（判别腿 + 零回归）
- [x] 必要 focused verification 已完成（nop-record 测试全绿）
- [x] 不存在被静默降级的 in-scope live defect
- [x] 受影响 owner docs 已同步
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-format/nop-record` 全绿（196，执行轮+审计独立复跑双验证）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- @Disabled 唯一使用方启用时的端到端验证（启用属业务决策）

## Closure

Status Note: 修复语义正确、判别证据精确到异常逐字（IllegalArgumentException newPosition>limit 30>20 双重跳过越界——audit 预言实证）、196/196 审计独立复跑全绿、收口四件套齐备（roadmap/index/daily log/owner doc 核对为零）、Follow-up 分类诚实（audit 确认）。该审计出自 2396+2397 合并 audit agent（报告 B）。

Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，2396+2397 合并审计）agent_34a41001-ec1b-452e-ac7b-4a3bcabb532f
- Evidence:
  - 修复落地 PASS：AbstractModelBasedRecordDeserializer:268-271 subStart 移至 subInput 之前（与 StreamingRecordDeserializer:357-361 形态对齐；Cosmetic：行号快照 3 行内偏差不构成 drift）
  - 判别测试 PASS：testFixedCollectionWithEagerReaderReadsTrailingField:236-252（断言 items=3 且 c=CCCCC，与 plan 处方逐字一致）；判别腿恰 1 ERROR IllegalArgumentException newPosition>limit (30>20)
  - nop-record 196/196 审计独立复跑全绿（1 skipped=@Disabled 既有）
  - 收口完整性：roadmap:162 done；index:193 fixed；daily log 10-10.md:3-11；owner doc 核对为零经 audit 标题面全文核验确认
  - Anti-Hollow：无新组件、行为由新用例端到端证明（ModelBasedBinaryRecordInput.next() → readCollection → 区域后字段 c 正确读出）；hollow 亲跑 0 findings
  - Follow-up 诚实性：@Disabled 使用方启用验证（业务决策+路径已守卫）——合法 non-blocking 确认

Follow-up:

- @Disabled 唯一使用方启用时的端到端验证（业务决策；新回归测试已守卫路径本身）
