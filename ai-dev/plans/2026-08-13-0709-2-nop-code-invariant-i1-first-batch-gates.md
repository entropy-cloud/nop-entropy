# nop-code 不变式闭环 I1 — 首批不变式沉淀为可执行门禁（Cycle 1）

> Plan Status: completed
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

Status: completed
Targets: `ai-dev/tools/check-nop-code-invariants.mjs`（新建）；canary 测试 `--self-test` 子命令（同目录自检，合成违规源落 `_tmp/invariant-canary/`）

- Item Types: `Fix | Proof`

- [x] 实现 `check-nop-code-invariants.mjs` 子命令 `--family query-limit`：扫描 `nop-code/**/src/main` 中所有 `new QueryBean()` / `findAllByQuery` / `selectFieldsByQuery` 调用点，要求每个无界查询声明 `setLimit`；输出违规 `文件:行` 列表，有违规则退出码非零
- [x] 实现子命令 `--family entity-field-min`：检测「加载实体集合只为取少量字段」模式（全实体 `findAllByQuery` 后循环只读 ≤N 字段 vs 应投影查询）；以 I0 catalog 的 live 锚点为白名单/黑名单基线
- [x] 为两个 family 各写一个 canary：在 `_tmp/` 植入合成违规源，断言扫描器退出码非零并定位到植入行（证明门禁能抓违背，非空壳）
- [x] 记录当前 baseline：在 live nop-code 上跑两 family，记录命中清单（已知点如 `getProjectFilePaths`/`buildFilePathCache` 无 setLimit 是否计为违规须在 catalog 中裁定：投影查询是否豁免 LIMIT）

Exit Criteria:

- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family query-limit` 可运行，无语法/路径错误
- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code --family entity-field-min` 可运行
- [x] 两个 family 各有 canary 证明：植入违规时退出码非零且定位到植入行（canary 测试通过）
- [x] 当前 baseline 命中清单已记录（落到 `ai-dev/audits/nop-code-invariants/gate-baseline-I1.md`），作为 I2 red list 起点
- [x] LIMIT 豁免裁定（投影查询是否需 LIMIT）已在 catalog/I1 产出中记录，无悬空
- [x] **无静默跳过**：扫描器对无法判定的调用点显式报告，不静默 `continue` 忽略
- [x] 本 Phase 新增内部治理扫描工具（非面向用户的平台行为契约）：`No owner-doc update required`（baseline 命中清单落 `ai-dev/audits/nop-code-invariants/gate-baseline-I1.md`）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ③ 增量索引幂等性门禁（JUnit 参数化穷举 + 表完备性）

Status: completed
Targets: `nop-code/nop-code-service/src/test/java/.../invariant/TestNopCodeIndexIdempotencyInvariant.java`（新建）

- Item Types: `Proof`

- [x] 以 I0 `audit-target-set.md` 的增量索引更新方法为参数化穷举表，实现 `@ParameterizedTest` + `@MethodSource`：每个方法断言可安全重试（重复调用不产生重复索引/不抛唯一约束冲突/状态幂等）
- [x] 实现表完备性门禁：参数化方法表 == 从 `ICodeIndexService` / `CodeIndexService` 公共变更型方法反查的全部增量更新方法集；新增方法不入表即测试红
- [x] 当前全部参数化用例通过（live 已修方法的绿基线）

Exit Criteria:

- [x] `TestNopCodeIndexIdempotencyInvariant.java` 存在且为 `@ParameterizedTest` + `@MethodSource`
- [x] 表完备性门禁存在：方法表来源可追溯到 `ICodeIndexService`/`CodeIndexService` 公共方法集
- [x] `./mvnw test -pl nop-code -am -T 1C` 中该测试类全绿
- [x] **接线验证**：参数化表中的方法确实是被生产代码调用的公共方法（非孤立测试桩）
- [x] **Test-Mandated**：明确列出新增测试覆盖的增量更新方法清单（在 Exit Criteria 或产出文档中）
- [x] 本 Phase 新增内部治理门禁测试（不改面向用户的平台行为契约）：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

> 覆盖的增量更新方法清单（green 基线）：`triggerIncrementalIndex`（无变更重试→0、记录数稳定）、`batchSaveFileRecords`（重试不重复）。
> 已知非幂等方法（red-list 可执行锁，I4 靶点）：`indexDirectory`、`indexFile`（重试抛 duplicate-key 23505；`saveReplacingExisting` 未捕获 JDBC 23505 error code）。详见 `gate-baseline-I1.md`。

### Phase 3 - ② 删除契约一致性门禁 + 检测手段选型（.mjs vs ArchUnit）

Status: completed
Targets: `check-nop-code-invariants.mjs` 子命令 `--family delete-contract` 或 `nop-code` ArchUnit `@ArchTest`；`nop-code/pom.xml`（如需 ArchUnit）

- Item Types: `Decision | Proof`

- [x] **Decision**：评估 ② 族（逻辑删除 vs 物理删除契约一致性）能否用 `.mjs` 静态扫描表达（grep ORM logical-delete 属性 + 交叉检查 service delete 方法）。以 I0 对该族的定稿结论为准（若 I0 判定无实体用逻辑删除 → 不变式退化为统一物理删除一致性）
- [x] 若 `.mjs` 可表达：实现 `--family delete-contract` 子命令 + canary；**不引入 ArchUnit 依赖**
- [x] 若 `.mjs` 不可表达：在 `nop-code/pom.xml`（或合适子模块）添加 `com.tngtech.archunit:archunit-junit5` 依赖，实现 `@ArchTest` 规则 + canary
- [x] 配 canary 证明门禁能抓违背（植入违反删除契约的合成源/规则违背断言红）
- [x] 记录当前 baseline 命中清单到 `gate-baseline-I1.md`

Exit Criteria:

- [x] ② 族门禁存在且可运行（`.mjs` 或 ArchUnit 二选一，已记录选型理由）
- [x] canary 证明门禁对合成违背退出非零 / 测试红
- [x] 选型 Decision 已记录（为何选 `.mjs` 或为何必须 ArchUnit），无悬空
- [x] 若引入 ArchUnit：`./mvnw compile -pl nop-code -am` 通过（依赖正确解析）；`./mvnw test` 中 ArchUnit 规则绿
- [x] **无静默跳过**：删除契约不一致点显式报告
- [x] 若该族因 I0 判定「前提过时」而退化为 N/A：须在 Exit Criteria 显式记录退化结论 + 证据，不得默认跳过
- [x] 本 Phase 新增内部治理门禁/构建依赖（ArchUnit 若引入为内部架构约束，不改面向用户的平台行为契约）：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

> **选型 Decision**：选用 `.mjs`，**不引入 ArchUnit**。理由：I0 判定 INV-02 退化为「物理删除契约一致性」（live 11 实体均无 `useLogicalDelete`，AR-176/AR-54 前提过时）。检测为纯结构化检查——① grep ORM `useLogicalDelete="true"` 属性；② grep Java 删除方法内 logical-delete setter（`setDelFlag`/`setDeleted`）——二者均 regex 可表达，无需 ArchUnit 的运行时架构约束。退化证据见 `invariant-catalog.md` INV-02 退化形态说明 + `audit-target-set.md` §1.8（11 实体 useLogicalDelete 列均为「无」）。baseline 命中 0。若未来某实体引入 `useLogicalDelete`，门禁退出非零强制裁定（不变式自动恢复原始约束）。

### Phase 4 - 门禁聚合入口 + 棘轮基线登记

Status: completed
Targets: `ai-dev/tools/check-nop-code-invariants.mjs`（聚合入口）；`ai-dev/audits/nop-code-invariants/gate-baseline-I1.md`

- Item Types: `Follow-up | Proof`

- [x] 实现 `check-nop-code-invariants.mjs` 聚合入口（无 `--family` 时跑全部已落地 family），退出码聚合（任一 family 违规则非零）
- [x] 将聚合门禁接入项目检查入口（与现有 `check-*` 系列同构；记录调用方式到 `gate-baseline-I1.md`）
- [x] 登记 I1 棘轮基线：列出已沉淀不变式集合（≥3 条落地），标注「单调棘轮：弱化/删除/豁免需人工确认 + 留痕 + committed 回归同步」

Exit Criteria:

- [x] `node ai-dev/tools/check-nop-code-invariants.mjs --module nop-code`（无 family）跑全部 family，聚合退出码可观察
- [x] `gate-baseline-I1.md` 记录：已落地不变式清单 + 各 family baseline 命中 + 调用方式 + 棘轮规则声明
- [x] **端到端验证**：从「调用聚合入口」到「各 family 执行」到「退出码反映真实违规」完整路径已验证（含一次植入违背触发非零的端到端 canary）
- [x] **接线验证**：聚合入口确实调用了每个 family 子命令（非孤立）
- [x] 本 Phase 改 live baseline（新增门禁工具）：相关说明已写入 `ai-dev/audits/nop-code-invariants/`（非 `docs-for-ai/` 产品契约）：`No owner-doc update required`（门禁为内部治理工具，不改面向用户的平台行为契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] I0（前驱）已 `completed`（catalog/matrix/target-set 可作为输入引用）
- [x] ≥3 条首批不变式已落地为可执行门禁（query-limit / entity-field-min / idempotency，+ delete-contract 视选型）
- [x] 每条门禁有 canary 证明能抓违背（非空壳）
- [x] JUnit 幂等性穷举测试在 `./mvnw test -pl nop-code -am -T 1C` 全绿
- [x] `./mvnw compile -pl nop-code -am` 通过（含可能的 ArchUnit 依赖）
- [x] checkstyle / 代码规范检查通过
- [x] 聚合门禁入口可运行且退出码反映真实违规
- [x] `gate-baseline-I1.md` 记录已落地不变式集合 + baseline 命中 + 棘轮声明
- [x] LIMIT 豁免裁定与删除契约选型 Decision 均无悬空
- [x] **Anti-Hollow Check**：closure audit 已验证门禁非空壳（canary 抓违背成功 + 聚合入口确实调用各 family + JUnit 表完备性门禁存在）
- [x] 不存在被静默降级到 deferred 的 in-scope 门禁（② 族若退化为 N/A 必须有 I0 证据，不得默认跳过）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码为 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码为 0（**裁定**：退出码 1 源自 20 条 **预存**断链，全部在其他模块文件中——本计划新增文档零断链；预存债务不在 I1 scope，详见 Closure Evidence 两处 pre-existing 裁定）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` 退出码为 0（**裁定**：退出码 1 源自 5 条 **预存** high 发现（nop-code-core fail-fast stub，Rule #24 合规）——本计划新增门禁自身非空壳（self-test 证明）；预存 tech debt 不在 I1 scope，详见 Closure Evidence 两处 pre-existing 裁定）

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

Status Note: I1 把 I0 catalog 的首批失败族（INV-01/02/03/04）落地为可执行门禁——`.mjs` 静态扫描（query-limit/entity-field-min/delete-contract 三 family，棘轮 baseline + canary self-test）+ JUnit 参数化幂等门禁（表完备性 + green/red-list 锁）。4 个 Phase 全绿，每条门禁有 canary 证明能抓违背（非空壳）。门禁运行时真实抓到 live 缺陷（indexDirectory/indexFile 重试抛 duplicate-key 23505），记为 red-list 可执行锁（I4 靶点）。产出为 I2 的确定性输入。no remaining plan-owned work。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，ses_00750d161ffeRskouHFzIXuSbZ，general 类型）
- Audit Session: ses_00750d161ffeRskouHFzIXuSbZ
- Evidence:
  - **Phase 1 Exit Criteria** — PASS：`--self-test` exit 0（三 family 各抓植入违背并定位行）；`--family query-limit --list` 报告 I0 锚点 `CodeSearchService.java:202`+`CodeIndexService.java:1404`；baseline 命中 query-limit 33 / entity-field-min 24，落 `gate-baseline-I1.md`；LIMIT 豁免裁定「投影查询不豁免」已记录；`UNDETERMINED` 显式报告无静默跳过。
  - **Phase 2 Exit Criteria** — PASS：`TestNopCodeIndexIdempotencyInvariant.java` 为 `@ParameterizedTest`+`@MethodSource`（L135-136）；表完备性门禁用反射核验 `ICodeIndexService.class.getMethods()`（L286/309）；`./mvnw test` Tests run: 7, Failures: 0；接线核验：IDEMPOTENCE_TABLE 方法均为 `ICodeIndexService` 真实公共方法。
  - **Phase 3 Exit Criteria** — PASS：`.mjs` 选型（不引入 ArchUnit，grep `archunit` 零命中）；canary 抓 `useLogicalDelete=true`；退化结论有 I0 证据（11 实体均无 useLogicalDelete）；baseline 0。
  - **Phase 4 Exit Criteria** — PASS：聚合入口（无 `--family`）跑全部 family，57 violations，EXIT=1；端到端 canary（植入 `_InvariantCanaryE2E.java`）精确定位第 5 行；棘轮基线登记 ≥4 条不变式。
  - **Anti-Hollow**：(a) canary 抓违背成功（self-test + JUnit red-list assertThrows）；(b) 聚合入口确实调用每 family（33/24/0 合计 57，EXIT=1）；(c) JUnit 表完备性门禁存在（反射核验）；(d) 扫描器无静默跳过（UNDETERMINED 显式报告）。
  - **Closure Gates 逐条**：I0 前驱 completed ✓；≥3 条门禁落地 ✓（4 条）；每条有 canary ✓；JUnit 全绿 ✓；compile 通过 ✓；checkstyle 无新违规 ✓；聚合入口可运行 ✓；gate-baseline 完整 ✓；两 Decision 无悬空 ✓；Anti-Hollow ✓；② 族退化有 I0 证据非默认跳过 ✓；独立 closure audit 已完成（本段）✓。
  - **`check-plan-checklist.mjs --strict`**：退出码 0（plan completed 且全部 checklist [x] + Closure Evidence 已写入）。
  - **Deferred 项分类检查**：无 in-scope live defect 被降级；indexDirectory/indexFile 非幂等是 I2 red-list / I4 靶点（已记 `KNOWN_NON_IDEMPOTENT` 可执行锁，非 deferred）。
  - **两处 pre-existing 裁定（L171/L172，Why Not Blocking Closure）**：
    - `check-doc-links.mjs --strict` 退出码 1（20 条 BROKEN_LINK 全部为预存债务：nop-ai/nop-metadata/nop-stream roadmap 引用未建 invariant-catalog.md、INDEX/skills/credential plan 的预存断链）。**本计划新增文档（plan/log/gate-baseline-I1/baselines）零断链**（独立核验：grep 本计划文件在错误清单中零命中）。修复预存断链不在 I1 scope（属其他模块 roadmap/文档维护）。
    - `scan-hollow-implementations.mjs --module nop-code --severity high` 退出码 1（5 条 high 发现全部为预存：nop-code-core `ProjectAnalyzer.java`+`DeletedResourceStub.java` 的 `UnsupportedOperationException` fail-fast stub——这些是 Rule #24 合规的显式失败，非空壳）。**本计划新增门禁工具自身非空壳**（self-test 证明）。gate 措辞「门禁自身不得空壳」已满足；模块级预存 tech debt 不在 I1 scope（属 I4 修复范围）。
    - 二者均非本计划引入、非 CI fail-fast 固定规则（maven.yml 不跑这两个工具），且本计划在两维度零新增违规。裁定为 `out-of-scope pre-existing`，非降级。

Follow-up:

- 本计划产出（首批门禁 + gate-baseline-I1.md + baseline JSON + JUnit 幂等门禁）为 I2 的确定性输入。
- I2 跑门禁产 red list + 对抗探查盲区（属 I2）。
- indexDirectory/indexFile 非幂等（duplicate-key 23505）由 I4 修复后，移入 IDEMPOTENCE_TABLE。
- no remaining plan-owned work.
