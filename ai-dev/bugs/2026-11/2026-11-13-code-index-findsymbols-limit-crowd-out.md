# 2026-11-13 Code Index findSymbols Limit Crowd-Out Fix

## Problem

- `TestIndexNopXlangModule`（nop-code-service，9.1 已登记 HEAD 预存面）确定性失败：
  `verifySymbolLookup:211 按名称模糊查找应命中 io.nop.xlang.scope.XLangCompileScope ==> expected: <true> but was: <false>`
- 索引本身完好：`findSymbolByQualifiedName` 能精确命中该类（kind=CLASS）；仅带 limit 的模糊查找 miss。

## Diagnostic Method

- 先区分「索引内容缺失」vs「查找匹配问题」：在测试内加临时诊断（跑后移除）——
  精确 qn 查询命中（exactKind=CLASS, exactName=XLangCompileScope）；无 limit 模糊查询返回
  129 行且类在第 52 位；limit=50 的前 50 行全是 `IXLangCompileScope` 接口及其方法。
- 排除假设：符号未入索引（否，精确查询命中）；大小写/形态不匹配（否，contains 直接命中）。
- 决定性证据：类的 52 个更弱匹配先于它被持久化，无序 LIMIT 把精确命中挤出窗口。
- 用合成小样（1 接口 + 5 方法 + 1 同名类，limit=3）在 `TestFindSymbolsRelevanceRanking` 中
  复现同形红，证明与 nop-xlang 源规模无关。

## Root Cause

- `CodeQueryService.findSymbols` 的模糊过滤为 `name contains q OR qualifiedName contains q`，
  以单条无 ORDER BY 的 LIMIT 查询执行。
- 任何 QN 恰好包含查询串的类型成员（方法/字段）都经 qualifiedName 腿匹配，且按存储顺序
  （先索引的文件先落库）先于同名类返回 → limit 内弱匹配挤掉精确匹配。
- 391b…379bfbcbcc 当时的 222 绿是源码/存储顺序巧合（接口成员当时未越过 50 行边界），
  该契约从未保证精确命中在窗口内。

## Fix

- `CodeQueryService.findSymbols`（limit>0 且有查询串时）改为三层分级：
  exact name → name contains → qualifiedName contains，逐层查询并按 id 去重合并，
  每层 fetch limit 行（后续层仅在先前层耗尽后才查询，重复数上界 = 已收集数，足以填满窗口）。
- 层内按 `qualifiedName asc, id asc` 排序，保证确定性。
- 无查询串 / limit<=0 路径行为不变。`findSymbolsPage` 保持原样（同族残余：分页无 ORDER BY，
  另行登记，不在本修扩散）。

## Tests

- `nop-code/nop-code-service/src/test/java/io/nop/code/service/impl/TestFindSymbolsRelevanceRanking.java`
  — 回归钉：修复前红（exact-name 命中被 QN-only 匹配挤出窗口），修复后绿；
  钉「limit 内必须含精确命中且排首位 / name-contains 先于 QN-only / 去重 / limit 尊重 / kinds 过滤逐层生效」。
- `TestIndexNopXlangModule` 原断言未动（真实模块端到端钉）。

## Affected Files

- `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeQueryService.java`
- `nop-code/nop-code-service/src/test/java/io/nop/code/service/impl/TestFindSymbolsRelevanceRanking.java`（新增）

## Notes For Future Refactors

- findSymbols 的「精确优先」契约由 TestFindSymbolsRelevanceRanking 钉住；若改为单条 SQL
  排序表达式实现，必须保留三层序与去重。
- findSymbolsPage 仍是无排序分页（跨页可能重叠/漏行），若修需独立工单（count 逐层拆分）。
