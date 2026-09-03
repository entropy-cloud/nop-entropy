# 测试代码通配符导入清理（跨模块统一 sweep）（roadmap item 22）

> Plan Status: completed
> Mission: nop-stream-productization
> Work Item: item 22（[Follow-up，来源 item 7 plan `2026-09-01-0938-2`（core 审计报告 §1.2 #6）；item 10 closure 事实补全 connector 16 文件；item 11 closure 事实补全 rocksdb 4 / fraud-example 4）
> Last Reviewed: 2026-09-03
> Source: `ai-dev/analysis/2026-09/2026-09-01-nop-stream-core-module-audit.md` §1.2 #6（main 已清零、test 残留 317 文件 + checkstyle AvoidStarImport 未强制的事实记录）
> Related: item 23（core execution 根包重组）——**本 plan 先于 item 23 执行**：显式导入使 item 23 类迁移的爆炸半径在 import 面可见（通配符导入会静默吸收包移动、掩盖隐藏耦合）

## Purpose

把 nop-stream 全部 10 个模块测试代码中的通配符导入（`import x.y.*;` 与 `import static x.y.*;`）清理为显式导入，收敛到仓库既定导入分组约定，并落一个防回潮门禁，使「test 通配符导入 = 0」成为可持续成立的事实而非一次性清扫。

## Current Baseline

（2026-09-03 live 复算，口径 = `rg -l '^import .*\\*;' <module>/src/test/java --glob '*.java'`）

| 模块 | 通配符导入测试文件数 |
|---|---|
| nop-stream-core | 169 |
| nop-stream-runtime | 108 |
| nop-stream-cep | 15 |
| nop-stream-flow | 1 |
| nop-stream-connector | 5 |
| nop-stream-connector-batch | 5 |
| nop-stream-connector-jdbc | 2 |
| nop-stream-connector-debezium | 4 |
| nop-stream-rocksdb | 4 |
| nop-stream-fraud-example | 4 |
| **合计** | **317** |

- main 侧全部 10 模块已清零（`rg -l 'import .*\\*;' */src/main` 0 命中，core 审计 §1.2 #6 已核验，本轮起草复核一致）。
- **形态分布（2026-09-03 live 复算，本 plan 工作面的事实基础）**：通配符的主体是**静态成员通配**——317 个命中文件全部含 `import static org.junit.jupiter.api.Assertions.*;` 形态；**非静态类型通配仅存在于三个模块**：core 23 / runtime 55 / connector 2 个文件（其余 7 模块为 0）。非静态通配的包分布以 JUnit/mock/平台工具包为主（`io.nop.stream.core.execution.*` 全仓测试树仅出现 1 次：runtime `TestCheckpointPlanBuilderParticipants.java:4`）。
- 静态展开的真实工作细节（live 抽查）：①~30 个 JUnit 断言/注解简单名按「实际使用才导入」枚举展开；②限定调用 `Assertions.x(...)` 存在于 10 个文件（其中 4 个在本 plan 清扫面内）——**按调用点粒度**无需为限定调用新增静态导入（这些文件通常同时有裸调用仍需按引用展开，故按文件级表述「无需任何静态导入」不成立，以调用点为准）；③少数常量族通配：`static NopStreamErrors.*` ×2 文件、`static ProcessingGuarantee.*` ×1 文件——常量枚举型展开需按实际引用的常量成员处理。
- 简单名冲突现实核查：对全部被通配的包做两两同简单名扫描（含 junit-api、`io.nop.api.core.message`、java.util 族），**当前零冲突**——类型通配展开的冲突消解是防御性预案而非主工作面。
- 导入分组约定（AGENTS.md + `ai-dev/tools/check-import-order.mjs`）：`io.nop.*` → 第三方（含 `jakarta.*`/`javax.*`）→ `java.*`，组间空行，静态导入最后。
- 门禁现状：root pom `maven-checkstyle-plugin` 配置整体被注释（AvoidStarImport 未在构建期强制）；`check-import-order.mjs` 为 advisory（不检查通配符、非 CI 硬门禁）；`check-nop-stream-invariants.mjs` 是 nop-stream 范围的既有硬门禁（hollow/invariants 族）。
- 本项为代码风格治理项，**非 live defect**（core 审计已定性）——但按 roadmap 排程属当前第一个 `todo`，且是 item 23 的前置依赖（见 Related）。

## Goals

- 10 个模块 `src/test/java` 下通配符导入文件数 **317 → 0**（同一口径 closure 复算）。
- 展开后的显式导入遵循仓库分组约定（nop → third → java，静态最后）；类型通配只导入文件内实际引用的简单名、静态通配只导入实际引用的成员（不得展开成「整包/整类全量导入」造成新的噪音；限定调用 `Assertions.x(...)` 的文件不新增导入）。
- 零行为变更：不改任何测试逻辑、断言、注解；全量测试绿。失败分诊规则：展开后任何编译/测试失败 = 该文件展开缺陷（修展开，禁止改测试逻辑迁就）。
- 防回潮门禁落地：通配符导入检查进入 nop-stream 范围既有硬门禁工具（含正向自证 fixture），CI 可执行、exit code 语义明确。

## Non-Goals

- main 代码任何改动（已清零，无工作）。
- 导入顺序全量重排（只处理命中通配符的文件内的导入块；既有非通配符导入顺序如恰好不符约定，仅在顺手可见的同一导入块内整理，不做跨文件全局重排项目）。
- item 23（包重组）——本 plan 只清导入，不动任何类位置。
- checkstyle 插件在 root pom 的启用/修复（root pom 影响全仓库非 nop-stream 模块，超出本 mission 范围；门禁以 ai-dev 工具承载）。

## Scope

### In Scope

- 10 模块 317 个测试文件的通配符导入展开（静态成员通配为主体 + 三模块的非静态类型通配）。
- 静态展开细节处理：实际引用成员枚举、限定调用不加导入（10 个 `Assertions.x(...)` 文件）、常量族通配（`NopStreamErrors.*`/`ProcessingGuarantee.*`）按引用成员展开。
- 类型通配简单名冲突的防御性预案：同一文件内两个通配符包都提供某被引用简单名时，以完全限定名引用消解（live 现状零冲突，预案仅备扩展后新面）。
- 防回潮门禁：在 `check-nop-stream-invariants.mjs` 内新增通配符导入子命令（含 committed 正向自证 fixture），接入方式见 Phase 1 裁定。
- 各 phase 全量测试验证。

### Out Of Scope

- 上列 Non-Goals 全部。
- 测试文件的其他风格问题（行宽、命名等）。

## Execution Plan

### Phase 1 - 展开方法裁定 + 工具化展开 core（169 文件）

Status: completed
Targets: `nop-stream/nop-stream-core/src/test/java/`、`ai-dev/tools/`（门禁规则）

- Item Types: `Decision | Fix`

- [x] 展开方法裁定：机械展开（IDE/脚本按实际引用简单名/成员生成显式导入）vs 手工逐文件——core 169 文件必须工具化或半工具化（纯手工不可审计）；裁定内容包括：展开工具选型（临时脚本放 `_tmp/`、或分批小心手工）、静态成员「实际引用」判定方式（限定调用不加导入、常量族按引用成员）、冲突简单名清单的产生方式（编译器报错驱动）、每批次的验证命令
- [x] core 169 文件通配符导入展开（静态与非静态），展开后导入块符合分组约定
- [x] 防回潮门禁落地（**接入时序裁定如下，避免共享门禁中期变红**）：规则实现为 `check-nop-stream-invariants.mjs` 新子命令 + committed 正向自证 fixture（对故意注入通配符导入的样例文件必须报错——沿既有 `BadSinkFixture` 自测先例，不接受临时验证）；子命令自带模块 scope 参数（现有工具无 `--module` 选项，**scope 支持属本子命令新工作**）；Phase 1—2 期间以显式 scope 验证已清理模块（core 先过），**不改变 no-arg 默认运行面**；Phase 3 全模块归零后规则加入默认运行面（此时全绿，无中期红窗）
- [x] core 模块全量测试绿：`./mvnw test -pl nop-stream/nop-stream-core -am`（core 依赖平台模块，-am 覆盖）

Exit Criteria:

- [x] 展开方法与工具裁定记录（含拒绝替代方案）落日志；core 模块通配符导入文件数 169 → 0（同口径 rg 复算记录）
- [x] 门禁子命令存在且可执行：committed fixture 正向自证（注入通配符样例必报错）+ 以子命令 scope 参数对展开后 core 通过 exit 0
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 全绿（零行为变更的证明 = 原有测试原样通过；本 phase 无新功能，No new test required: 纯测试代码导入清理，行为面由全量既有测试覆盖——显式声明）
- [x] **无静默跳过**：不适用（纯导入清理，无新方法/分支——显式声明）
- [x] owner-doc 裁定：`No owner-doc update required`（测试代码风格治理，无契约/行为变更）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - runtime + cep + flow 展开（124 文件）

Status: completed
Targets: `nop-stream/nop-stream-runtime|nop-stream-cep|nop-stream-flow/src/test/java/`

- Item Types: `Fix`

- [x] runtime 108 文件展开（Phase 1 方法复用）
- [x] cep 15 文件展开
- [x] flow 1 文件展开
- [x] 三模块全量测试绿

Exit Criteria:

- [x] 三模块通配符导入文件数 108/15/1 → 0/0/0（同口径复算记录）
- [x] `./mvnw test -pl nop-stream/nop-stream-runtime -am`、`./mvnw test -pl nop-stream/nop-stream-cep -am`、`./mvnw test -pl nop-stream/nop-stream-flow -am` 全绿（No new test required: 同 Phase 1 理由——显式声明）
- [x] owner-doc 裁定：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - connector 四模块 + rocksdb + fraud-example 展开（24 文件）+ 门禁收口

Status: completed
Targets: `nop-stream/nop-stream-connector{,-batch,-jdbc,-debezium}|nop-stream-rocksdb|nop-stream-fraud-example/src/test/java/`、`ai-dev/tools/`

- Item Types: `Fix`

- [x] connector 5 / batch 5 / jdbc 2 / debezium 4 / rocksdb 4 / fraud-example 4 文件展开（24 文件）
- [x] 全 nop-stream 聚合验证：10 模块同口径复算全 0；通配符门禁子命令加入 `check-nop-stream-invariants.mjs` 默认运行面后全量跑 exit 0
- [x] 全量回归：`./mvnw test -pl nop-stream -am -T 1C` 全绿

Exit Criteria:

- [x] 10 模块通配符导入文件计数表（317 → 全 0）落日志，与 roadmap item 22 基线逐模块对账
- [x] 防回潮门禁全量 exit 0（已入默认运行面），且工具在 mission 门禁执行面可被后续 plan/audit 直接引用（工具名 + 用法记录）
- [x] `./mvnw test -pl nop-stream -am -T 1C` 全绿（No new test required: 纯导入清理——显式声明）
- [x] owner-doc 裁定：`No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] 10 模块通配符导入 test 文件数全部为 0（closure 复算，口径与 Current Baseline 相同）
- [x] 防回潮门禁存在、可执行、全量 exit 0（「test 通配符导入 = 0」可持续成立）
- [x] 全量测试绿：`./mvnw test -pl nop-stream -am -T 1C`
- [x] 独立子 agent closure-audit 已完成并记录证据（含抽查：随机抽取若干文件验证展开正确性——导入的简单名确实被使用、无逻辑改动）
- [x] `./mvnw compile`（nop-stream 聚合）通过
- [x] 门禁工具按名核验：`node ai-dev/tools/check-nop-stream-invariants.mjs` exit 0（含本 plan 新增通配符规则默认面）+ `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` exit 0；advisory 的 `check-import-order.mjs`（存量顺序违规基线不在本 plan 范围）**明确不计入** closure 门禁

## Deferred But Adjudicated

（无——本 plan 为纯机械清理，无优化项或 residual 需要延期裁定。）

## Non-Blocking Follow-ups

- root pom `maven-checkstyle-plugin` 被注释配置的修复（影响全仓库非 nop-stream 模块，out-of-scope improvement；nop-stream 范围防回潮已由本 plan 门禁覆盖）

## Closure

Status Note: 317 个测试文件的通配符导入全部展开为实际引用的显式导入（10 模块同口径复算全 0，与 Current Baseline 逐模块对账一致）；防回潮门禁 `check-wildcard-imports`（含 committed 正向自证 fixture）已收口进 `check-nop-stream-invariants.mjs` 默认运行面且全量 exit 0；全量回归 3370/0/0 与 roadmap 基线一致。两处偏差（TestSubtaskExecution RuntimeException→StreamException、TestDrainableSourceSupport 空 catch 加意图注释）均为 pre-commit ast-grep 硬门禁要求的单行修改，已在提交说明与日志披露，断言面/行为面不变。无剩余 plan-owned work。
Completed: 2026-09-03

Closure Audit Evidence:

- Reviewer / Agent: 独立 general subagent closure audit（fresh session `ses_f993019beffeDMQgXnWhj8o47u`，非执行 session）
- Evidence:
  - CLOSURE-AUDIT: **APPROVED**（0 Blocker / 0 Major / 1 Minor / 2 Info）
  - Gate 1（10 模块归零）：PASS——审计者独立 rg 复算 10 模块 test 树全 0、main 树全 0；变更文件普查 879f72bfbe..HEAD 共 317 个 test 文件，逐模块 169/108/15/1/5/5/2/4/4/4 与基线表完全一致
  - 展开正确性抽查：PASS——审计者对全部 317 文件做 diff 纯度扫描（非 import/空行仅 2 处已披露偏差行）；5 文件 30 条新增导入逐一验证「实际被使用」（_tmp/lint-probe2 脚本 ALL-USED）；2 文件分组约定（nop → third → java → static 最后）核验
  - Gate 2（防回潮门禁）：PASS——`check-wildcard-imports` / no-arg 默认面（含通配符规则，工具源码 ~2102-2104）/ `self-test` 三跑 exit 0；fixture 双形态违规断言在源码 ~1898-1909 核验；负控 `--module bogus` exit 1；live red 证明：向 core test 树注入未跟踪探针文件 → exit 1 报 file:line，移除后恢复 exit 0
  - Gate 3（全量测试）：PASS——审计者发现磁盘无遗留 surefire 报告后**自行复跑** `./mvnw test -pl nop-stream -am -T 1C`：BUILD SUCCESS，3370/0/0/25 skipped，逐模块与日志声明一致
  - Gate 5（compile）：PASS——同一 reactor 内 compile 先于 test 成功（被 test 覆盖）
  - Gate 6（工具按名核验）：PASS——`scan-hollow-implementations --module nop-stream --severity high` exit 0（0 findings）
  - 诚实性：PASS——Deferred 无条目；Non-Blocking Follow-ups 仅 root pom checkstyle（预先裁定 out-of-scope）；无 in-scope 缺陷/硬门禁失败被降级
  - Minor（已处置）：日志纯度措辞与 2 处偏差行自相矛盾——已修正 09-03.md Phase 1/Phase 3 条目措辞为「仅 N 处已披露偏差」
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（见下方收口核验）
- Anti-Hollow 检查：n/a（纯导入清理 plan，无新组件/接线；审计确认无逻辑改动面）
- Deferred 项分类检查：无 deferred 项；唯一 follow-up 为 out-of-scope improvement（root pom checkstyle），分类诚实

Follow-up:

- root pom `maven-checkstyle-plugin` 注释配置修复（out-of-scope improvement，归属全仓库治理而非本 mission）
- no remaining plan-owned work
