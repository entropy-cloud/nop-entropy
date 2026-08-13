# nop-code 不变式门禁 I1 — 棘轮基线登记

> Status: active
> Last Reviewed: 2026-08-13
> Source: I1 计划 `ai-dev/plans/2026-08-13-0709-2-nop-code-invariant-i1-first-batch-gates.md`
> 依赖: `invariant-catalog.md`（INV-01..INV-04）、`audit-target-set.md`
> 后继: I2 跑门禁产 red list 时以本文件 baseline 命中为起点

## 目的

登记 I1 落地的可执行不变式门禁集合、当前 baseline 命中清单、调用方式、棘轮规则声明。本文件是 I2 的确定性输入：I2 在 baseline 命中点上跑门禁，将「真违规」与「已接受有界查询」分类，前者进入修复队列。

## 门禁工具

`ai-dev/tools/check-nop-code-invariants.mjs`（新建）。

### 调用方式

```bash
# 自检（canary，证明三个 family 各自能抓违背，非空壳）
node ai-dev/tools/check-nop-code-invariants.mjs --self-test

# 单 family，严格模式（任何违规退出非零；canary 用）
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family delete-contract

# 单 family，棘轮模式（仅 NEW 违规退出非零）
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit \
    --baseline ai-dev/audits/nop-code-invariants/baselines/baseline-query-limit.json

# 聚合入口（无 --family，跑全部 family）
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code

# 列出全部命中（不退出非零，仅展示）
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit --list

# 重写 baseline（棘轮前进；diff 须人工审阅后再提交，禁止静默弱化）
node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code \
    --update-baseline ai-dev/audits/nop-code-invariants/baselines/baseline.json
```

## 棘轮规则声明（Monotonic Ratchet）

- 已沉淀的不变式集合**只增不减**。
- baseline JSON（`baselines/baseline-*.json`）记录当前已命中的签名集（`file:line [method(var)]` 形式，按行唯一）。
- **弱化/删除/豁免** baseline 条目，必须：人工确认 + 留痕（在本文件或 plan 记录裁定理由）+ committed 回归同步。
- 修复一个违规后，重跑 `--update-baseline` 收紧 baseline（baseline 条目减少 = 棘轮前进 = 允许且鼓励）。
- 新增违规（不在 baseline 中的签名）→ 棘轮模式下退出非零 → CI/检查入口红。
- 新增方法/调用点未入 baseline → 立即暴露，不会静默通过。

## 已落地不变式清单

| ID | 不变式 | family | 检测手段 | canary | 当前 baseline 命中数 |
|----|--------|--------|---------|--------|---------------------|
| INV-04 | 查询结果上限声明 | `query-limit` | `.mjs` 静态扫描 | ✓ self-test | 33 |
| INV-01 | 实体加载字段最小化 | `entity-field-min` | `.mjs` 静态扫描 | ✓ self-test | 24 |
| INV-02 | 删除路径统一物理删除契约 | `delete-contract` | `.mjs` ORM+service 交叉扫描 | ✓ self-test | 0（退化形态：live 无逻辑删除） |
| INV-03 | 增量索引幂等性 | JUnit `@ParameterizedTest` | 见 `TestNopCodeIndexIdempotencyInvariant` | — | 表完备性门禁 |

---

## LIMIT 豁免裁定（悬空项收口）

**问题**：投影查询（`selectFieldsByQuery`）是否豁免 `setLimit` 要求？

**裁定：不豁免。** 投影查询虽不再加载 CLOB 列，但仍把全部结果行物化进内存。无 `setLimit` 的投影在 10 万+ 行表上仍可 OOM（AR-177 的原始前提：`getProjectFilePaths` 投影已修但无 setLimit 仍是违规）。因此 `query-limit` family 同时扫描 `findAllByQuery` **和** `selectFieldsByQuery`，两者均要求 `setLimit`/`setMaxResults`，或在显式有界白名单中。

**唯一豁免**：可证明结果集有界（如等值过滤到单条记录的唯一键查询，或分页删除循环中的 `setLimit(BATCH_SIZE)`）。这些在 `query-limit` 扫描器中通过「方法作用域内出现 `<var>.setLimit(`」自然放行，无需白名单。

---

## Phase 1 — query-limit baseline 命中（33，I2 red list 起点）

> 完整清单由 `--list` 生成；下表标注 I0 catalog 已知锚点。I2 须逐条裁定「真违规」vs「已接受有界」。

| 文件:行 | 方法(var) | I0 锚点 / AR-ID | I2 待裁定 |
|---------|-----------|-----------------|-----------|
| `CodeSearchService.java:202` | selectFieldsByQuery(fq) | AR-168 buildFilePathCache（投影已修，无 LIMIT） | 真违规（待加 LIMIT） |
| `CodeIndexService.java:1404` | selectFieldsByQuery(q) | AR-177 getProjectFilePaths（投影已修，无 LIMIT） | 真违规（待加 LIMIT） |
| `CodeIndexService.java:505` | selectFieldsByQuery(kindQuery) | getIndexStats 投影 | 待裁定 |
| `CodeIndexService.java:1471/1511/1521/1531` | findAllByQuery(q) | 删除辅助 / deleteEntitiesByFilter | 待裁定（多为全量删除必要加载） |
| `CodeIndexService.java:1602/1624` | findAllByQuery(query/membershipQuery) | 流程查询 | 待裁定 |
| `CodeIndexService.java:1788/1818/1882` | findAllByQuery(query/fq) | findImplementations / batch | 待裁定 |
| `CodeQueryService.java:*` (14 处) | findAllByQuery(各 var) | 各查询方法 | 待裁定 |
| `CodeSearchService.java:329` | findAllByQuery(fq) | filterByLanguage | 待裁定 |
| `OrmFingerprintStore.java:84/102/137/148` | find/selectFieldsByQuery(query) | 指纹存储 | 待裁定 |
| `CodeCacheManager.java:247` | findAllByQuery(q) | 缓存重建 | 待裁定 |

## Phase 1 — entity-field-min baseline 命中（24，I2 red list 起点）

| 子模式 | 代表命中 | I0 锚点 / AR-ID |
|--------|---------|-----------------|
| `.size()` → countByQuery | `OrmFingerprintStore.java:102`、`CodeIndexService.java:899/1818` | AR-77（getIndexStats 已修正为先例） |
| ≤3 getter → 投影 | `CodeQueryService.java:780/792/853`（findImplementations）、`CodeIndexService.java:1602/1882` | AR-64/86、AR-168/177 |
| ≤3 getter → 投影 | `CodeSearchService.java:129/152/329` | searchBySymbolName/searchFullText |

---

## Phase 2 — INV-03 增量索引幂等性（JUnit 参数化穷举）

测试类：`nop-code/nop-code-service/src/test/java/io/nop/code/service/invariant/TestNopCodeIndexIdempotencyInvariant.java`。

参数化方法表来自 `ICodeIndexService` 公共变更型方法（反射核验）；表完备性门禁确保新增方法不入表即红。共 7 个测试用例（2 参数化 + 2 red-list 锁 + 3 表完备性/接线门禁），`./mvnw test -pl nop-code/nop-code-service` 全绿。

### Green 基线（IDEMPOTENCE_TABLE，已锁定的可重试方法）

| 方法 | 幂等机制 | 验证 |
|------|---------|------|
| `triggerIncrementalIndex` | AR-124 `MappedPathResource` 路径比较；无变更→返回 0、记录数稳定 | 重试两次均 0 changed、file/symbol 数不变 |
| `batchSaveFileRecords` | 确定性 ID + `saveReplacingExisting` | 重试不重复 |

### Red-list 可执行锁（KNOWN_NON_IDEMPOTENT，I4 靶点）

**执行门禁时发现的新违规**（I1 门禁本身证明非空壳——它真实抓到了 live 缺陷）：

| 方法 | 违规 | 根因 | 锁定方式 |
|------|------|------|---------|
| `indexDirectory` | 重试（不清空再索引同一目录）抛 `JdbcException` duplicate-key 23505 | `saveReplacingExisting` 只捕获 `nop.err.orm.save-entity-replace-existing-entity`，未捕获 JDBC 23505 | `testKnownRedList_indexDirectoryRetryFailsIdempotency`（assertThrows） |
| `indexFile` | 重试（再索引同一文件）抛 `JdbcException` duplicate-key 23505 | 同上 | `testKnownRedList_indexFileRetryFailsIdempotency`（assertThrows） |

**自更新契约**：当 I4 修复某方法使其重试安全时，对应 `assertThrows` 锁会变红（不再抛异常）→ 强制把该方法从 `KNOWN_NON_IDEMPOTENT` 移入 `IDEMPOTENCE_TABLE` 并补 verify 分支。这是 JUnit 层的棘轮：弱化（删锁）或忽略都会暴露。

### 表完备性分类（testTableCompletenessGate）

`ICodeIndexService` 全部公共方法被反射核验，归入四类之一：`IDEMPOTENCE_TABLE`（green）｜`KNOWN_NON_IDEMPOTENT`（red-list 锁）｜`DELETE_METHODS`（INV-02）｜`QUERY_METHODS`（只读）。新增未分类方法 → 测试红。

## Phase 3 — INV-02 删除契约一致性

`.mjs` 选型（不引入 ArchUnit）。live ORM 11 实体均无 `useLogicalDelete`，service 删除路径均用物理删除 API（`batchDeleteEntities`/`deleteEntityById`）。baseline 命中 0。若未来某实体引入 `useLogicalDelete` 或删除方法改用逻辑删除 setter，门禁退出非零（强制裁定）。

---

## No-Silent-Skip 行为

扫描器对无法判定查询变量的调用点（如内联 `new QueryBean()...`）显式报告为 `[UNDETERMINED]`，不静默 `continue` 忽略（满足 plan guide Rule #24）。canary（`--self-test`）证明三个 family 各自对植入违背退出非零并定位到植入行。

---

## Phase 4 — 聚合入口 + 端到端验证

### 聚合入口

`check-nop-code-invariants.mjs` 无 `--family` 时跑全部已落地 family（query-limit / entity-field-min / delete-contract），退出码聚合（任一 family 有 NEW 违规 → 非零）。与现有 `check-*` 系列同构——直接 `node ai-dev/tools/...` 调用，门禁调用方式记录在本文件顶部「调用方式」段。

### 端到端验证（Anti-Hollow）

1. **聚合 strict 模式**：`--module nop-code`（无 baseline）→ 报告 57 条违规（33 query-limit + 24 entity-field-min + 0 delete-contract），退出码 1。证明聚合入口确实调用每个 family（非孤立）。
2. **canary 端到端**：在源码树植入 `_InvariantCanaryE2E.java`（含 `findAllByQuery(q)` 无 `setLimit`），跑 `--family query-limit --baseline` → 精确定位到植入文件第 5 行，标记 `[NEW]`，退出码 1。移除植入后恢复 baseline 退出码 0。证明「聚合入口 → family 执行 → baseline diff → 退出码反映真实违规」完整路径连通。
3. **self-test canary**：`--self-test` 在 `_tmp/` 合成三 family 各自的违规源，断言退出非零且定位到植入行（query-limit / entity-field-min / delete-contract 三 family 均通过）。

### 棘轮基线登记（I1 收口）

本 Cycle 1 / I1 落地的可执行不变式集合（单调棘轮：弱化/删除/豁免需人工确认 + 留痕 + committed 回归同步）：

| 不变式 | family / 测试 | baseline 文件 | 当前命中 | 状态 |
|--------|--------------|--------------|---------|------|
| INV-04 查询结果上限 | `query-limit` | `baselines/baseline-query-limit.json` | 33 | 已登记（I2 red list 起点） |
| INV-01 实体加载字段最小化 | `entity-field-min` | `baselines/baseline-entity-field-min.json` | 24 | 已登记（I2 red list 起点） |
| INV-02 删除路径物理删除契约 | `delete-contract` | `baselines/baseline-delete-contract.json` | 0 | 已登记（退化形态：live 无逻辑删除） |
| INV-03 增量索引幂等性 | JUnit 参数化 + 表完备性 | `TestNopCodeIndexIdempotencyInvariant` | 2 green / 2 red-list | 已登记（green 锁 + red-list 可执行锁） |

≥3 条不变式已落地为可执行门禁（满足 Closure Gate）。本 baseline 为 I2 的确定性输入。
