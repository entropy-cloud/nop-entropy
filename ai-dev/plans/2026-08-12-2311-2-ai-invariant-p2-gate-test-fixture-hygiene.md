# 2 AI Invariant Loop — P2 Gate-1 负例测试 fixture 位置迁移（AR-8）

> Plan Status: completed
> Mission: nop-ai-invariant-loop
> Work Item: Cycle 2 / P2 — Gate-1 负例 fixture 写入 src/main 的残留污染（AR-8）
> Last Reviewed: 2026-08-12
> Source: `ai-dev/audits/2026-08-12-1119-open-audit-nop-ai-invariant-loop.md` P2 [AR-8]；roadmap `ai-dev/backlog/nop-ai-invariant-loop-roadmap.md` `## Follow-up Backlog`
> Related: `2026-08-12-1120-2-ai-invariant-i1-gate-codification.md`（门禁①落地，本 plan 修复其测试基础设施）；`2026-08-12-2311-1-ai-invariant-p2-engine-split-hygiene.md`（同批 P2 的代码卫生项）
> Review: 四轮独立子 agent 对抗性审查（fresh sessions ses_00975e6c7ffe / ses_0096e9522ffe / ses_00969ad69ffe / ses_009660e41ffe）——R1 Blocker（Maven 选择器路径形式）+ Major（中断残留模拟缺失）；R2 确认 + 新 Major F1（残留模拟空转：clean/测试顺序）；R3 确认 + 新 Major（clean 先删残留，顺序再修）；R4 确认 Blocker/Major 清零（1 Minor 一行补 surefire flag 已吸收），共识 = executable → draft → active

## Purpose

消除 Gate-1（`TestInvariantGate1SecureDefault` / `TestInvariantGate1SecureDefaultShell`）负例测试把临时 fixture 写进模块 `src/main` 的残留风险：测试被中断（kill -9 / surefire 崩溃）时残留文件会进入产物 jar 并让下次 gate-1 表完备性检查变红。修复 = fixture 移到模块 `<module>/target/gate-fixture/`（构建目录）并参数化扫描根，同时**新增中断残留模拟验证**——证明「残留不再导致门禁红/产物污染」这一 AR-8 的核心价值主张。

## Current Baseline

（2026-08-12 23:11 live 核实）

- **缺陷实锤**：`nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/gate/TestInvariantGate1SecureDefault.java` 两个负例测试（`negative_newUndeclaredDefaultClassIsRejected` :247-262、`negative_annotatedDefaultClassStillRequiresTableEntry` :269-284）经 `writeFixture`（:315-322）把 fixture 写到 `moduleRoot().resolve("src/main/java/io/nop/ai/agent/gatefixture")`（:249, :271），finally 中 `deleteFixture`（:324-327，单文件 + 空目录）。`nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/gate/TestInvariantGate1SecureDefaultShell.java` 同构：负例 :170-185、`writeFixture` :187-192、**finally 内联清理**（:182-183，无独立 deleteFixture helper）、`scanSourceTree` :108-122。
- **风险机制**：fixture 写入 `src/main/java`——测试被中断 → finally 不执行 → 残留 Java 文件 → 进入产物 jar（maven-compiler 打包 src/main）→ 下次 `tableCompleteness_matchesSourceTree`（agent :219-235，扫描真实 `src/main/java`）判残留类为 unregistered → 门禁红。正常路径下 finally 清理本就工作（audit 已确认缺陷只在中断路径）——所以**验证必须模拟中断**，不能只验正常路径。
- **扫描逻辑**：`scanSourceTree()`（agent :175-188 / shell :108-122）硬编码扫描 `moduleRoot()/src/main/java`；`hasExcludedSegment`（agent :190-198）排除路径段 `test`/`_gen`/`target`。`writeFixture` 硬编码包名 `io.nop.ai.agent.gatefixture` / `io.nop.ai.shell.gatefixture`，FQCN 断言（agent :256/:278）要求 fixture 保持**原包相对子路径**——迁移只能换根前缀（`src/main/java` → `<module>/target/gate-fixture`），不能拍平目录。
- **target/ 目录安全性（live 核实）**：`.gitignore` 含 `target/`（git status 零污染可验证）；maven-compiler 只编译 `src/main/java`（target 下残留不进产物）；正例完备性只 walk `src/main/java`（target 残留不染门禁）。
- **被裁定的替代方案**（见 Non-Goals/Risks）：`@TempDir`（JUnit 自动清理，kill -9 残留落系统临时目录、同样零污染，但脱离模块目录、无法用 `mvn clean` 统一清除）；`src/test` + 调整扫描范围（会弱化负例语义——负例必须验证扫描逻辑对真实待扫树的拦截）。选 `<module>/target/gate-fixture/`：构建目录、随 `mvn clean` 清除、路径段 `target` 已入排除规则。
- 授权基线：P2 自动修复授权 = plan 级裁定（I3 裁定 2026-08-12）；本项为已确认 live defect（测试基础设施缺陷），归类 `Fix`。

## Goals

- 负例 fixture 移出 `src/main`，写入固定常量目录 `<module>/target/gate-fixture/`（写/扫/删三处共享同一常量，防漂移）。
- `scanSourceTree` 参数化：无参重载由正例 `tableCompleteness_matchesSourceTree` 使用（真实 `src/main/java`）；参数化版本由负例传入 fixture 根；排除规则完全复用，门禁语义不变。
- 清理升级为递归删除整个 fixture 目录（不再依赖单文件删除）。
- **中断残留模拟验证**：手工残留（不删除）→ 门禁测试仍绿 + 产物无 gatefixture 类。
- 两个模块（nop-ai-agent + nop-ai-shell）同步修复。

## Non-Goals

- 不改变门禁①判定规则、`SecureDefault` 注解语义、`gate-gaps.yaml` 或 gate 表。
- 不把 fixture 放到 `_tmp/`（AGENTS.md 的 `_tmp/` 约定面向 AI agent 临时文件；Maven 测试运行时产物用模块 `target/` 更符合构建惯例且随 `mvn clean` 清除）。
- 不处理 AR-5/AR-6/AR-7/AR-9（各自 plan）。

## Scope

### In Scope

- `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/gate/TestInvariantGate1SecureDefault.java`：扫描参数化 + fixture 位置迁移 + 目录级清理 + 中断残留模拟验证。
- `nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/gate/TestInvariantGate1SecureDefaultShell.java`：同构修复（含新增递归删除 helper——shell 侧现为 finally 内联）。

### Out Of Scope

- 门禁①机制、判定标准、表内容。
- 其他 P2 / backlog 项。

## Execution Plan

### Phase 1 - nop-ai-agent Gate-1 负例 fixture 迁移

Status: completed
Targets: `nop-ai/nop-ai-agent/src/test/java/io/nop/ai/agent/gate/TestInvariantGate1SecureDefault.java`

- Item Types: `Fix | Proof`

- [x] `Fix` `scanSourceTree()` 重构为 `scanSourceTree(Path root)` 参数化版本；保留无参重载 = 扫描 `moduleRoot()/src/main/java`，供正例 `tableCompleteness_matchesSourceTree` 使用（负例改传 fixture 根）；排除规则（`hasExcludedSegment`）原样复用。
- [x] `Fix` fixture 目录定义为常量 `FIXTURE_ROOT = moduleRoot().resolve("target/gate-fixture")`（写/扫/删三处共享）；两个负例 fixture 写到 `FIXTURE_ROOT/io/nop/ai/agent/gatefixture/...`——**仅换根前缀，保留包相对子路径**（`writeFixture` 硬编码包名不变，FQCN 断言不破坏）。
- [x] `Fix` 清理升级：`deleteFixture` 改为递归删除整个 `FIXTURE_ROOT`（不依赖单文件删除；finally 中执行）。
- [x] `Proof` 负例语义保持：负例断言仍验证「未登记 Default* 类（无注解 / 有注解但无表项）被扫描标记为 unregistered」——参数化后同一扫描逻辑对 fixture 根运行，断言不变；若实现错误（fixture 未被扫到）断言会**红**而非假绿。
- [x] `Proof` **中断残留模拟（AR-8 核心价值主张的直接验证；固定执行顺序防空转——`mvn clean` 会删整个模块 `target/`，残留必须写在 clean 之后）**：① `./mvnw clean -pl nop-ai/nop-ai-agent -am`（建立干净态）→ ② 手工写入**独立类名**残留 `DefaultKilledResidualFixture.java` 到 `FIXTURE_ROOT/io/nop/ai/agent/gatefixture/`（**不得与测试 fixture 同名**——测试的 `Files.writeString` 会覆盖同名文件，残留必须独立；**不删除**，模拟 kill -9）→ ③ `./mvnw compile -pl nop-ai/nop-ai-agent -am`（**不带 clean**——clean 之后 compile 必为全量，残留真正在场）→ ④ `find nop-ai/nop-ai-agent/target/classes -name "*gatefixture*"` 零命中（残留不进产物）→ ⑤ 跑负例 + 正例全绿（先 `./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest=TestInvariantGate1SecureDefault#tableCompleteness_matchesSourceTree -Dsurefire.failIfNoSpecifiedTests=false` 单跑正例拿硬证据，再跑全类——两个负例的 finally 会递归删除 FIXTURE_ROOT，若正例排在其后会失去残留在场证据；负例在残留在场时断言自身 FQCN 仍成立）→ ⑥ 结论落盘 daily log（记录执行目录与 find 原始输出）。
- [x] `Proof` 正常路径零残留：跑完测试后 `git status --porcelain nop-ai/nop-ai-agent` 零改动；`src/main` 下无 `gatefixture` 目录；`mvn clean` 后 `<module>/target/gate-fixture` 消失。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 两个负例测试 fixture 不再写入 `src/main`（代码可读验证 + 测试后 git status 零残留）
- [x] `negative_newUndeclaredDefaultClassIsRejected` / `negative_annotatedDefaultClassStillRequiresTableEntry` 仍绿且断言语义未弱化（unregistered 判定基于同一扫描逻辑）
- [x] 正例 `tableCompleteness_matchesSourceTree` 仍扫描真实 `src/main/java` 且绿
- [x] **中断残留模拟通过**：顺序 = `mvn clean` → 写独立类名残留（`DefaultKilledResidualFixture.java`）→ `mvn compile`（不带 clean，残留真正在场）→ `find target/classes -name "*gatefixture*"` 零命中 → 门禁测试全绿（正例先行单跑拿硬证据）；daily log 记录命令与原始输出
- [x] 递归删除 fixture 目录实现存在（非单文件删除）
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am -Dtest=TestInvariantGate1SecureDefault -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] No owner-doc update required（测试基础设施修复，不改变门禁契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - nop-ai-shell Gate-1 负例 fixture 迁移（同构）

Status: completed
Targets: `nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/gate/TestInvariantGate1SecureDefaultShell.java`

- Item Types: `Fix | Proof`

- [x] `Fix` 同 Phase 1：扫描参数化 + fixture 根改为 `moduleRoot()/target/gate-fixture`（保留 `io/nop/ai/shell/gatefixture` 包相对子路径）+ 新增递归目录删除 helper（shell 侧现为 finally 内联清理，需升级为递归删目录）。
- [x] `Proof` 负例语义保持（shell 侧同一断言逻辑）+ **中断残留模拟（shell 模块同固定顺序：`mvn clean` → 写独立类名残留 → `mvn compile`（不带 clean）→ `find nop-ai/nop-ai-shell/target/classes -name "*gatefixture*"` 零命中 → 先 `-Dtest=TestInvariantGate1SecureDefaultShell#tableCompleteness_matchesSourceTree -Dsurefire.failIfNoSpecifiedTests=false` 单跑正例再跑全类）**+ 正常路径零残留（`git status --porcelain nop-ai/nop-ai-shell` 零改动）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] shell 负例 fixture 不再写入 `src/main`，测试后零残留
- [x] shell 负例测试仍绿且断言语义未弱化
- [x] 中断残留模拟通过（shell 侧：`mvn clean` → 写残留 → `mvn compile` 产物检查 → 测试全绿）
- [x] `./mvnw test -pl nop-ai/nop-ai-shell -am -Dtest=TestInvariantGate1SecureDefaultShell -Dsurefire.failIfNoSpecifiedTests=false` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] AR-8（负例 fixture 写入 src/main 的残留污染）已修复：两个模块负例测试均不再触碰 `src/main`
- [x] Gate-1 负例/正例断言语义保持（负例仍验证拦截、正例仍扫描真实源树）
- [x] **中断残留验证通过**（手工残留 → 测试绿 + 产物无 gatefixture 类）——AR-8 核心价值主张的直接证据
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [x] 受影响 owner docs 已同步，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证负例断言真实执行（fixture 写入 → 扫描 → unregistered 断言，非空壳），无静默跳过
- [x] `./mvnw compile -pl nop-ai/nop-ai-agent -am && ./mvnw compile -pl nop-ai/nop-ai-shell -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-agent -am && ./mvnw test -pl nop-ai/nop-ai-shell -am`
- [x] checkstyle / 代码规范检查通过（`./mvnw checkstyle:check -pl nop-ai -am`，按 I5/I6 既有口径记录 pre-existing 基线）

## Deferred But Adjudicated

无（本 plan 无 deferred 项）。

## Non-Blocking Follow-ups

无。

## Closure

Status Note: AR-8 已修复并经独立 closure audit APPROVED——两模块负例 fixture 全部迁出 `src/main`（写/扫/删共享 `FIXTURE_ROOT = <module>/target/gate-fixture`），扫描参数化（无参重载保留正例语义）、清理升级为递归目录删除；中断残留模拟（mvn clean → 写独立类名残留 → mvn compile 产物零 gatefixture → 门禁全绿）与正常路径零残留均验证通过，两个 Phase 的 Exit Criteria 与全部 Closure Gates 已勾选，无剩余 in-scope 工作。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，review-only）
- Audit Session: `ses_0091ed471ffeBEkzqdPzZ8Zotx`
- Evidence:
  - Phase 1 六条 Exit Criteria 逐条 PASS + Phase 2 六条逐条 PASS（audit 报告逐条附 file:line 证据：agent `TestInvariantGate1SecureDefault.java:63,187-209,272-305,352-365`；shell `TestInvariantGate1SecureDefaultShell.java:47,117-149,188-227`）
  - FIXTURE_ROOT 常量写/扫/删三处共享：PASS；无参/参数化 scanSourceTree 双版本：PASS；负例 fixture 仅落 `target/gate-fixture/io/nop/ai/{agent,shell}/gatefixture/` 包相对子路径、零 `src/main` 引用：PASS；递归目录删除（finally 中）：PASS；负例断言语义未弱化（未被扫到必红非假绿）：PASS
  - 中断残留模拟（执行证据在 `ai-dev/logs/2026/08-13.md`）：agent + shell 两侧均 = `mvn clean` → 写 `DefaultKilledResidualFixture`/`DefaultShellKilledResidualFixture`（不删）→ `mvn compile`（不带 clean）→ `find target/classes -name "*gatefixture*"` 零命中 → 残留在场正例单跑 1/1 绿 → 全类 5/5、3/3 绿 → finally 递归删除后 `target/gate-fixture` 目录整个消失
  - `git status --porcelain` = 仅 5 个预期路径（2 测试文件 + plan + roadmap + daily log），无未跟踪 fixture；`find .../src -name "*gatefixture*"` 零命中
  - `node ai-dev/tools/check-plan-checklist.mjs <plan>` PASS 1 / FAIL 0（audit 时复跑，Closure Evidence 于审计后写入）；`--strict` 复跑 exit 0
  - `./mvnw test -pl nop-ai -am -T 1C` BUILD SUCCESS，surefire 汇总 total=4407 failures=0 errors=0 skipped=15（两次独立全量运行均绿）
  - checkstyle：`-Pqa` profile exit 0；默认-config 直跑 9164 pre-existing violation 全在未触碰上游 nop-api-core（I5/I6 同口径），两受处理测试文件 checkstyle-result.xml 零 violation
  - Anti-Hollow：负例端到端链路（writeFixture 真实写文件 → scanSourceTree(FIXTURE_ROOT) Files.walk 扫描 → unregistered.contains(FQCN) 断言 → finally 递归删除）逐行追踪确认 + audit 独立复跑测试全绿，无 stub/静默跳过
  - Deferred 项分类检查：无 deferred 项；in-scope live defect（AR-8）全部落地，无降级
- 复验工具（closure 后执行）：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` exit 0；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` = 2 条 pre-existing 零新增（I5/I6 同基线）

Follow-up:

- no remaining plan-owned work

## Optional Sections

### Risks And Rollback

- 风险：(1) 参数化重构若误改排除规则，负例测试会变假绿（fixture 未被扫描到却断言通过）——失败方向已论证为红（扫描不到 fixture 时 unregistered 断言必然失败），且 Exit Criteria 要求「断言语义未弱化」+ 中断残留模拟兜底；(2) 执行者若把 fixture 拍平到 fixture 根（丢失包相对子路径），FQCN 断言会红——plan 已显式要求保留子路径；(3) 被裁定的替代方案记录：`@TempDir` 与 `src/test`+扫描范围调整均 considered-and-rejected（理由见 Non-Goals），选 `<module>/target/gate-fixture/`。
- Rollback：Phase 1/2 均为测试文件改动，不触及生产代码，`git revert` 可回滚。
