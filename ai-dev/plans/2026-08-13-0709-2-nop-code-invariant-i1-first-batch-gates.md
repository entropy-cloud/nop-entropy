# nop-code 不变式闭环 I1 — 首批不变式沉淀为可执行门禁（Cycle 1）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Mission: nop-code-invariant-loop
> Work Item: Cycle 1 / I1. 不变式沉淀（首批门禁）
> Source: `ai-dev/backlog/nop-code-invariant-loop-roadmap.md`（Work Item I1 + Phase Details I1）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`（步骤 1 关键机制 + 门禁技术栈表）
> Related: 前驱 `2026-08-13-0709-1-nop-code-invariant-i0-inventory-and-baseline.md`（I0，本计划依赖其 catalog/matrix/target-set 产出）

## Purpose

把 I0 盘点出的首批失败族**沉淀为可执行不变式门禁**（JUnit 参数化穷举 + `ai-dev/tools/*.mjs` 静态扫描 + ORM/service 交叉检查），入项目检查入口，并配 committed 回归测试证明门禁能抓违背。门禁即契约，使后续 I2 能确定性跑全方法产 red list。

## Current Baseline

> 依赖 I0 产出。本节为 I0 完成后的预期基线 + 已核对的 live 技术事实。

- **I0 产出为本计划输入**（前驱计划须先 completed）：
  - `ai-dev/audits/nop-code-invariants/invariant-catalog.md`（≥4 条不变式，含检测方法标注）
  - `ai-dev/audits/nop-code-invariants/ar-status-matrix.md`（fixed/open/stale-premise 矩阵）
  - `ai-dev/audits/nop-code-invariants/audit-target-set.md`（目标方法清单）
- **当前零 nop-code 代码不变式门禁**（I0 已确认）：无 `check-nop-code-invariants.mjs`、无 nop-code invariant JUnit 测试类。
- **首批候选族（roadmap I1 列出，I0 定稿）的 live 锚点预核**：
  - ① **实体加载字段最小化**：`CodeSearchService.buildFilePathCache`（`CodeSearchService.java:196-212`）与 `CodeIndexService.getProjectFilePaths`（`CodeIndexService.java:1398-1413`）均已改投影查询（`selectFieldsByQuery`）。族内仍需穷举全部「加载实体列表只为取少量字段」的方法。`deleteEntitiesPaged`（`CodeIndexService.java:562-578`）用 `findAllByQuery` 全实体加载后删除——属删除语义必要加载，需在不变式中区分「必要全实体」与「冗余全实体」。
  - ② **增量索引一致性（逻辑 vs 物理删除）**：`useLogicalDelete` 在 live nop-code 零命中（I0 正式核实）。若 I0 判定无实体使用逻辑删除，本不变式退化为「删除路径统一物理删除契约一致性」。`deleteIndex`（`CodeIndexService.java:529-560`）走 `deleteEntitiesPaged` 物理删除。
  - ③ **增量索引幂等性**：增量索引更新操作须可安全重试。需 I0 target-set 列出全部增量更新方法作为参数化穷举表。
  - ④ **查询结果上限（防 OOM）**：`MAX_QUERY_RESULTS=10000`（`CodeIndexService.java:106`）已在 `CodeQueryService` 多处 `setLimit`（`CodeQueryService.java:114/252/270/319/755/762/799`），但 `getProjectFilePaths`/`buildFilePathCache` 的投影查询**无 setLimit**。
- **门禁技术栈**：JUnit 5 `@ParameterizedTest`（已用）；`.mjs` 静态扫描（`ai-dev/tools/` 先例足）；ArchUnit **全仓未引入**（grep `archunit` 零命中），若 ② 族需 ArchUnit 须先在 `nop-code` pom.xml 加 `com.tngtech.archunit:archunit-junit5` 依赖。

## Goals

- 落地 I0 catalog 首批不变式为可执行门禁，每条门禁满足：可运行 + 能抓违背（canary 证明）+ 当前 baseline 通过。
- 建立门禁集合的单调棘轮基线（已沉淀不变式只增不减）。
- 幂等性族用 JUnit 参数化穷举（方法表驱动 + 表完备性门禁：新增方法不入表即红）。
- 静态可 grep 族用 `ai-dev/tools/check-nop-code-invariants.mjs` 扫描器。
- 门禁接入项目检查入口，使 I2 可确定性调用。

## Non-Goals

- 不跑门禁产 red list + 对抗探查 —— 那是 I2。
- 不修复任何门禁命中的违背 —— 那是 I4。
- 不沉淀 I0 之外的新失败族为不变式（新族由 I2/I3 触发 Cycle 2 / I1，Loop Rule 预授权）。
- 不改产品业务逻辑（门禁只读/检测；若发现 live 违背，记录到 I2 red list 而非在本计划修）。

## Scope

### In Scope

- 实现 `ai-dev/tools/check-nop-code-invariants.mjs`（实体加载字段最小化 + 查询结果上限 两族静态扫描）。
- 实现 JUnit `@ParameterizedTest` 幂等性穷举测试 + 表完备性门禁（增量索引幂等性族）。
- 实现 ② 族（删除契约一致性）交叉检查：先评估 `.mjs` 是否可表达，若不可则引入 ArchUnit 依赖并落地 `@ArchTest`。以 I0 对该族的定稿结论为准。
- 为每条门禁配 canary 测试：证明门禁对植入违背退出非零 / 测试红。
- 将门禁接入项目检查入口（聚合调用点）。

### Out Of Scope

- I2 的全量跑门禁 + red list + 对抗探查。
- I4 的修复执行。
- 其他模块（nop-stream/nop-metadata/nop-ai）的不变式门禁（范围独立）。

## Execution Plan

### Phase 1 - ④ 查询结果上限门禁 + ① 实体加载字段最小化门禁（静态扫描）

Status: planned
Targets: `ai-dev/tools/check-nop-code-invariants.mjs`（新建）；canary 测试 `ai-dev/tools/__tests__/` 或同目录自检

- Item Types: `Fix | Proof`

- [ ] 实现 `check-nop-code-invariants.mjs` 子命令 `--family query-limit`：扫描 `nop-code/**/src/main` 中所有 `new QueryBean()` / `findAllByQuery` / `selectFieldsByQuery` 调用点，要求每个无界查询声明 `setLimit`；输出违规 `文件:行` 列表，有违规则退出码非零
- [ ] 实现子命令 `--family entity-field-min`：检测「加载实体集合只为取少量字段」模式（全实体 `findAllByQuery` 后循环只读 ≤N 字段 vs 应投影查询）；以 I0 catalog 的 live 锚点为白名单/黑名单基线
- [ ] 为两个 family 各写一个 canary：在 `_tmp/` 植入合成违规源，断言扫描器退出码非零并定位到植入行（证明门禁能抓违背，非空壳）
- [ ] 记录当前 baseline：在 live nop-code 上跑两 family，记录命中清单（已知点如 `getProjectFilePaths`/`buildFilePathCache` 无 setLimit 是否计为违规须在 catalog 中裁定：投影查询是否豁免 LIMIT）

Exit Criteria:

- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit` 可运行，无语法/路径错误
- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min` 可运行
- [ ] 两个 family 各有 canary 证明：植入违规时退出码非零且定位到植入行（canary 测试通过）
- [ ] 当前 baseline 命中清单已记录（落到 `ai-dev/audits/nop-code-invariants/gate-baseline-I1.md`），作为 I2 red list 起点
- [ ] LIMIT 豁免裁定（投影查询是否需 LIMIT）已在 catalog/I1 产出中记录，无悬空
- [ ] **无静默跳过**：扫描器对无法判定的调用点显式报告，不静默 `continue` 忽略
- [ ] 本 Phase 新增内部治理扫描工具（非面向用户的平台行为契约）：`No owner-doc update required`（baseline 命中清单落 `ai-dev/audits/nop-code-invariants/gate-baseline-I1.md`）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ③ 增量索引幂等性门禁（JUnit 参数化穷举 + 表完备性）

Status: planned
Targets: `nop-code/nop-code-service/src/test/java/.../TestNopCodeIndexIdempotencyInvariant.java`（新建）

- Item Types: `Proof`

- [ ] 以 I0 `audit-target-set.md` 的增量索引更新方法为参数化穷举表，实现 `@ParameterizedTest` + `@MethodSource`：每个方法断言可安全重试（重复调用不产生重复索引/不抛唯一约束冲突/状态幂等）
- [ ] 实现表完备性门禁：参数化方法表 == 从 `ICodeIndexService` / `CodeIndexService` 公共变更型方法反查的全部增量更新方法集；新增方法不入表即测试红
- [ ] 当前全部参数化用例通过（live 已修方法的绿基线）

Exit Criteria:

- [ ] `TestNopCodeIndexIdempotencyInvariant.java` 存在且为 `@ParameterizedTest` + `@MethodSource`
- [ ] 表完备性门禁存在：方法表来源可追溯到 `ICodeIndexService`/`CodeIndexService` 公共方法集
- [ ] `./mvnw test -pl nop-code -am -T 1C` 中该测试类全绿
- [ ] **接线验证**：参数化表中的方法确实是被生产代码调用的公共方法（非孤立测试桩）
- [ ] **Test-Mandated**：明确列出新增测试覆盖的增量更新方法清单（在 Exit Criteria 或产出文档中）
- [ ] 本 Phase 新增内部治理门禁测试（不改面向用户的平台行为契约）：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - ② 删除契约一致性门禁 + 检测手段选型（.mjs vs ArchUnit）

Status: planned
Targets: `check-nop-code-invariants.mjs` 子命令 `--family delete-contract` 或 `nop-code` ArchUnit `@ArchTest`；`nop-code/pom.xml`（如需 ArchUnit）

- Item Types: `Decision | Proof`

- [ ] **Decision**：评估 ② 族（逻辑删除 vs 物理删除契约一致性）能否用 `.mjs` 静态扫描表达（grep ORM logical-delete 属性 + 交叉检查 service delete 方法）。以 I0 对该族的定稿结论为准（若 I0 判定无实体用逻辑删除 → 不变式退化为统一物理删除一致性）
- [ ] 若 `.mjs` 可表达：实现 `--family delete-contract` 子命令 + canary；**不引入 ArchUnit 依赖**
- [ ] 若 `.mjs` 不可表达：在 `nop-code/pom.xml`（或合适子模块）添加 `com.tngtech.archunit:archunit-junit5` 依赖，实现 `@ArchTest` 规则 + canary
- [ ] 配 canary 证明门禁能抓违背（植入违反删除契约的合成源/规则违背断言红）
- [ ] 记录当前 baseline 命中清单到 `gate-baseline-I1.md`

Exit Criteria:

- [ ] ② 族门禁存在且可运行（`.mjs` 或 ArchUnit 二选一，已记录选型理由）
- [ ] canary 证明门禁对合成违背退出非零 / 测试红
- [ ] 选型 Decision 已记录（为何选 `.mjs` 或为何必须 ArchUnit），无悬空
- [ ] 若引入 ArchUnit：`./mvnw compile -pl nop-code -am` 通过（依赖正确解析）；`./mvnw test` 中 ArchUnit 规则绿
- [ ] **无静默跳过**：删除契约不一致点显式报告
- [ ] 若该族因 I0 判定「前提过时」而退化为 N/A：须在 Exit Criteria 显式记录退化结论 + 证据，不得默认跳过
- [ ] 本 Phase 新增内部治理门禁/构建依赖（ArchUnit 若引入为内部架构约束，不改面向用户的平台行为契约）：`No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 门禁聚合入口 + 棘轮基线登记

Status: planned
Targets: `ai-dev/tools/check-nop-code-invariants.mjs`（聚合入口）；`ai-dev/audits/nop-code-invariants/gate-baseline-I1.md`

- Item Types: `Follow-up | Proof`

- [ ] 实现 `check-nop-code-invariants.mjs` 聚合入口（无 `--family` 时跑全部已落地 family），退出码聚合（任一 family 违规则非零）
- [ ] 将聚合门禁接入项目检查入口（与现有 `check-*` 系列同构；记录调用方式到 `gate-baseline-I1.md`）
- [ ] 登记 I1 棘轮基线：列出已沉淀不变式集合（≥3 条落地），标注「单调棘轮：弱化/删除/豁免需人工确认 + 留痕 + committed 回归同步」

Exit Criteria:

- [ ] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code`（无 family）跑全部 family，聚合退出码可观察
- [ ] `gate-baseline-I1.md` 记录：已落地不变式清单 + 各 family baseline 命中 + 调用方式 + 棘轮规则声明
- [ ] **端到端验证**：从「调用聚合入口」到「各 family 执行」到「退出码反映真实违规」完整路径已验证（含一次植入违背触发非零的端到端 canary）
- [ ] **接线验证**：聚合入口确实调用了每个 family 子命令（非孤立）
- [ ] 本 Phase 改 live baseline（新增门禁工具）：相关说明已写入 `ai-dev/audits/nop-code-invariants/`（非 `docs-for-ai/` 产品契约）：`No owner-doc update required`（门禁为内部治理工具，不改面向用户的平台行为契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] I0（前驱）已 `completed`（catalog/matrix/target-set 可作为输入引用）
- [ ] ≥3 条首批不变式已落地为可执行门禁（query-limit / entity-field-min / idempotency，+ delete-contract 视选型）
- [ ] 每条门禁有 canary 证明能抓违背（非空壳）
- [ ] JUnit 幂等性穷举测试在 `./mvnw test -pl nop-code -am -T 1C` 全绿
- [ ] `./mvnw compile -pl nop-code -am` 通过（含可能的 ArchUnit 依赖）
- [ ] checkstyle / 代码规范检查通过
- [ ] 聚合门禁入口可运行且退出码反映真实违规
- [ ] `gate-baseline-I1.md` 记录已落地不变式集合 + baseline 命中 + 棘轮声明
- [ ] LIMIT 豁免裁定与删除契约选型 Decision 均无悬空
- [ ] **Anti-Hollow Check**：closure audit 已验证门禁非空壳（canary 抓违背成功 + 聚合入口确实调用各 family + JUnit 表完备性门禁存在）
- [ ] 不存在被静默降级到 deferred 的 in-scope 门禁（② 族若退化为 N/A 必须有 I0 证据，不得默认跳过）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码为 0（无 high/critical 空壳发现；门禁自身不得空壳）

## Deferred But Adjudicated

### 新失败族沉淀为 Cycle 2 不变式

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: I1 只沉淀 I0 catalog 首批族；I2/I3 发现的新族由 Loop Rule 预授权派生 Cycle 2 / I1（见 roadmap Cross-Cutting 授权），不在本 Cycle 1 / I1 范围。
- Successor Required: yes
- Successor Path: Cycle 2 / I1（由 I6 收口时按 Loop Rule 派生）

## Non-Blocking Follow-ups

- I2 跑门禁产 red list + 对抗探查盲区（属 I2）
- ArchUnit 若本 Cycle 未引入，后续族如需架构约束规则可在 Cycle 2 / I1 引入

## Closure

Status Note: <<完成或关闭时填写>>
Completed: <<YYYY-MM-DD>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<待填>>

Follow-up:

- <<明确写：本计划产出（首批门禁 + gate-baseline-I1）为 I2 的确定性输入；no remaining plan-owned work>>
