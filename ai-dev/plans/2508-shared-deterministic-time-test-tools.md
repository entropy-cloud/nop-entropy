# 2508 共享确定性时间测试工具归属与归并（roadmap WI-2）

> Plan Status: completed
> Last Reviewed: 2026-11-07
> Source: `ai-dev/backlog/test-timing-determinism-roadmap.md` WI-2；工具的原始实现来自 plan 2507 Phase 3
> Related: `ai-dev/plans/2507-debug-subsystem-test-time-determinism.md`（completed）
> 编号说明：沿用仓库当前 4 位序列（最近为 2507）。
> Review: R1 对抗审查（独立子 agent，含 Maven ReactorReader 语义验证）2 Major + 4 Minor，本稿逐条修订（F1 脏工作树记录 + 选择性暂存纪律；F2 install 步骤补 test-jar 解析；F3 jar tf 折入 install；F4 test-jar 先例数修正为 4 处；F5 testing.md 指引移至 Phase 2；F6 import 两种形态枚举）。R2 复审确认全部解决，新增 1 Minor（Targets 行不一致）已修订，判定 READY 可执行（同一独立子 agent 两轮）。
> 验证环境：所有 mvn 命令带 `-Dmaven.repo.local=$PWD/.m2-repo`（与 plan 2507 / `ai-dev/logs/2026/11-06.md` 基线一致），下文以 `<repo>` 代指该参数。
> 跨平台约束（用户 2026-11-07 指令）：本 plan 全部产出（测试工具、自测、pom）必须是 Windows/Linux/macOS 可复现的纯 JDK / JUnit / Maven 构造，不得引入 OS 专有 API、路径分隔符拼接、shell 外部进程或信号依赖。
> 提交纪律（用户 2026-11-07 指令）：每份 plan 收口后自动提交一次；本 plan 的收口提交按选择性暂存执行——只含本 plan 触碰的文件（`ai-dev/plans/2508-*.md`、roadmap、`ai-dev/logs/2026/11-07.md`、testing.md、四模块 pom 与测试源、`nop-commons` 的 pom + `src/test/java/io/nop/commons/test/time/**`），工作树中其他在途改动（如 TextScanner）不得卷入。

## Purpose

裁定「可推进时钟 / 手动调度器」共享确定性时间测试工具的归属模块，并把现存重复实现归并到单一 owner，使 roadmap WI-4 ~ WI-8 的各模块测试改造有一个统一、低耦合的工具来源。

## Current Baseline

- plan 2507 Phase 3 在两个调试模块测试源各落地一份 `ManualClock`（逐字相同，仅包名不同：`io.nop.xlang.debugger` / `io.nop.xlang.debug.web`）；`nop-xlang-debug-web` 另有 `ManualScheduler`。自测共 3 个类：debugger 侧 `TestManualClock`、debug-web 侧 `TestManualClock` + `TestManualScheduler`。
- `nop-sys/nop-sys-dao`（`io.nop.sys.dao.lock.MockClock`）与 `nop-service-framework/nop-biz-auth-core`（`io.nop.auth.core.mfa.store.MockClock`）各有一个模块内 `MockClock implements IClock`（可推进、无按线程读取计数），分别被 `TestSysDaoResourceLockManager` 与 `TestLocalEmailCodeStore` / `TestLocalSmsCodeStore` / `TestLocalMfaChallengeStore` 使用。
- 测试规范已入册 `docs-for-ai/02-core-guides/testing.md`「时间可控性：冻结时钟与禁用裸 now()」「禁止依赖敏感时间等待」；规范未指明共享工具位置。
- 仓库已有 test-jar 导出先例 4 处（nop-stream-core、nop-stream-runtime、nop-xlang、nop-message-core；根 pom pluginManagement 已 pin maven-jar-plugin 3.3.0，导出方无需再 pin 版本），消费方显式声明 `<type>test-jar</type><scope>test</scope>` + `${project.version}`（先例：nop-stream-runtime 对 nop-stream-core）。
- **脏工作树记录**：`nop-kernel/nop-commons` 存在与本 plan 无关的在途未提交改动（`src/main/java/io/nop/commons/text/tokenizer/TextScanner.java` + `src/test/java/io/nop/commons/text/TestTextScanner.java`，关联在途 analysis `2026-10-24-textscanner-api-design-review.md`）。本 plan 不触碰这两个文件；若全量门因其在途状态变红，属 out-of-scope，仅记录、不在本 plan 内修复。
- `nop-kernel/nop-commons` 依赖 `nop-api-core`（`io.nop.api.core.time.IClock` 所在模块），测试源已用 junit-jupiter（53 个测试类）；它被 36 个 pom 直接引用，是全平台传递依赖面最广的底层模块（全 reactor 456 模块）。
- 四个候选消费模块（两调试模块、nop-sys-dao、nop-biz-auth-core）pom 均未直接声明 nop-commons（传递获得）；test-jar 类型依赖不被普通 jar 依赖传递，消费方必须显式声明。
- 工具类与自测均为纯 JDK / 纯 JUnit 构造（ConcurrentHashMap、PriorityQueue、AbstractExecutorService、org.junit.jupiter）。
- 全 reactor 基线：26467 用例 0 失败 0 错误、456 模块 SUCCESS（2026-11-06，`full-test-2026-11-06-run5.log`）。

## Goals

- 归属裁定：可推进时钟与手动调度器在仓库内有唯一 canonical owner 模块。
- 归并：调试模块两份 `ManualClock` + 一份 `ManualScheduler`（含 3 个自测类）与两个模块内 `MockClock` 全部收敛到 owner 单一实现；模块内副本删除，行为语义不变。
- 消费路径成立：owner 模块以 test-jar 导出；消费方加一条 test-jar 测试依赖即可使用（WI-4 ~ WI-8 直接复用，不再模块内自造）。

## Non-Goals

- 不改任何生产代码行为（含 CoreMetrics 默认时钟语义与任何模块 main 源）。
- 不改工具对外行为语义（API 与 2507 Phase 3 落地版本一致）。
- 不新增违规守卫（roadmap WI-3），不改各模块测试的时间等待写法（WI-4 ~ WI-8 承接）——本 plan 只归并工具本身及其既有使用者。
- 不动 nop-stream-* / nop-xlang 等既有 test-jar 的导出面。

## Scope

### In Scope

- `nop-kernel/nop-commons`：test-jar 导出 + 工具类 + 自测迁入。
- `nop-dev-tools/nop-xlang-debugger`、`nop-dev-tools/nop-xlang-debug-web`：删除本地副本，切换为 owner test-jar 消费。
- `nop-sys/nop-sys-dao`、`nop-service-framework/nop-biz-auth-core`：删除模块内 `MockClock`，改用 owner `ManualClock`。
- `docs-for-ai/02-core-guides/testing.md`：共享工具位置与消费方式指引。

### Out Of Scope

- 其余模块的时间敏感测试改造（roadmap WI-4 ~ WI-8）。
- 违规守卫脚本（roadmap WI-3）。
- `nop-autotest` 框架的时钟能力（`TestClock`）——它是 autotest 录制/回放管线的一部分，语义不同，不归并。

## Execution Plan

### Phase 1 - 归属裁定与规则指引

Status: completed
Targets: 本 plan（决策记录）

- Item Types: `Decision`

- [x] Decision：owner = `nop-kernel/nop-commons` 测试源，包 `io.nop.commons.test.time`，经 test-jar 导出。依据：全平台被依赖面最广的底层模块、`IClock` 依赖已具备、测试源已具备 junit-jupiter、test-jar 先例成熟、不为 2 个类新增 reactor 模块。
- [x] Decision：拒绝方案记录——(a) `nop-autotest-core`：测试基建但依赖面重，plain-JUnit 模块（两调试模块、nop-stream 测试）被迫拉入 autotest 框架；(b) 新增独立模块（如 nop-test-time）：为 2 个类扩 reactor 与文档维护面；(c) `nop-api-core`：protected `nop-*-api` 面，契约 jar 保持纯净，不承载测试工具。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] No new test required：纯决策，无行为面。
- [x] `ai-dev/logs/2026/11-07.md` 记录裁定。（testing.md 指引移至 Phase 2 工具类实际存在后写入，避免 owner doc 引用尚不存在的代码。）

### Phase 2 - owner 落地（nop-commons test-jar）

Status: completed
Targets: `nop-kernel/nop-commons/src/test/java/io/nop/commons/test/time/`、`nop-kernel/nop-commons/pom.xml`、`docs-for-ai/02-core-guides/testing.md`

- Item Types: `Feature`

- [x] `ManualClock`、`ManualScheduler` 迁入 owner 包（类名、API、行为语义与 2507 版本逐字一致，仅改包名；javadoc 注明 canonical owner 与消费方式）。
- [x] `TestManualClock`、`TestManualScheduler` 自测迁入 owner 包，用例集不变。
- [x] `nop-kernel/nop-commons/pom.xml` 加 maven-jar-plugin test-jar execution（对齐 nop-stream-core 先例，无需 pin 版本——根 pom pluginManagement 已 pin 3.3.0）。
- [x] testing.md「禁止依赖敏感时间等待」的替代写法节补共享工具位置与消费方式（≤3 行摘要；状态与归属以本 plan 为准，SSOT 不复制）——此刻 owner 包已存在，指引与代码同批落地。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw <repo> test -pl nop-kernel/nop-commons -am` 全绿，且报告中含迁入的 2 个自测类。
- [x] `./mvnw <repo> install -pl nop-kernel/nop-commons -DskipTests` 成功（install 走 package 生命周期，test-jar 随之装进 `<repo>` 本地仓），`jar tf` 验证 `.m2-repo` 中 nop-commons test-jar 含 `io/nop/commons/test/time/ManualClock.class` 与 `ManualScheduler.class`。此步骤保证仓库既有实践 `./mvnw -o <repo> test -pl <模块>`（不带 `-am`）与 IDE 导入在收口后仍可解析。
- [x] testing.md 指引与实际类一一对应，`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors（初版指引含 ai-dev 计划链接触发 BOUNDARY 错误，改为无 ai-dev 引用后复跑 0 errors）。
- [x] 无静默跳过（Minimum Rules #24）：工具类为既有实现平移，不支持方法仍显式抛 `UnsupportedOperationException`（由迁入自测覆盖）。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 3 - 消费方归并（4 模块 6 测试类）

Status: completed
Targets: `nop-dev-tools/nop-xlang-debugger`、`nop-dev-tools/nop-xlang-debug-web`、`nop-sys/nop-sys-dao`、`nop-service-framework/nop-biz-auth-core`

- Item Types: `Fix`

- [x] 两调试模块：删除本地 `ManualClock`（×2）与本地 `TestManualClock`（×2）、`ManualScheduler` + `TestManualScheduler`（debug-web），import 改 owner 包；两模块 pom 各加 nop-commons test-jar 测试依赖。import 形态两种都要处理：debugger 侧 `TestReplDeadline` / `TestLeaseLifecycle`（子包 `debugger.session`）改既有 import；debug-web 侧同包无 import 的 `TestReplService` / `TestDebugGatewayE2E` / `AbstractDebugGatewayTest` / `TestDebugLogPipe` 需新增 `import io.nop.commons.test.time.ManualClock;`（按各自实际用到的类）。
- [x] `nop-sys-dao`：删除 `MockClock`，`TestSysDaoResourceLockManager` 改用 `ManualClock`（`MockClock.now()` → `ManualClock.startingNow()`、`advanceMillis(d)` → `advance(d)`）；pom 加 test-jar 依赖。
- [x] `nop-biz-auth-core`：删除 `MockClock`，`TestLocalEmailCodeStore` / `TestLocalSmsCodeStore` / `TestLocalMfaChallengeStore` 同法改用 `ManualClock`（`advanceSeconds(s)` → `advance(s * 1000L)`）；pom 加 test-jar 依赖。
- [x] 四模块测试全绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `grep -rn "class ManualClock\|class ManualScheduler\|class MockClock" <四模块>/src/test` 0 命中（副本删除完毕）。
- [x] `./mvnw <repo> test -pl <四模块> -am` 全绿。
- [x] 接线验证（Minimum Rules #23）：消费模块编译产物对 owner 工具类的解析来源为 nop-commons test-jar（`./mvnw <repo> dependency:tree -pl <消费模块> -am` 中 nop-commons:tests 条目证据，写入 logs）。
- [x] No new test required：行为等价替换，由 owner 侧迁入自测与消费模块既有测试覆盖。
- [x] No owner-doc update required：测试类内部改造，不改变文档化行为。
- [x] `ai-dev/logs/2026/11-07.md` 已更新。

### Phase 4 - 全量门与收口

Status: completed
Targets: 全 reactor、roadmap

- Item Types: `Proof`

- [x] 全 reactor `./mvnw <repo> test -T 1C -fae` 0 失败 0 错误。
- [x] roadmap `test-timing-determinism-roadmap.md` WI-2 → done（Plan 列填本 plan 编号）；`ai-dev/logs/2026/11-07.md` 收口条目。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 全量测试证据（命令、用例数、结果）写入 `ai-dev/logs/2026/11-07.md`。
- [x] roadmap 状态块已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 工具类唯一 canonical 实现：全仓 `ManualClock` / `ManualScheduler` / `MockClock` 类定义仅存 owner 一处（grep 证据）
- [x] 四个消费模块全部经 owner test-jar 消费（依赖证据）
- [x] 工具行为语义与 2507 版本一致：自测用例集不变、owner 模块测试全绿
- [x] testing.md 指引入册、`check-doc-links.mjs --strict` 0 errors
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：本 plan 无新增生产组件；test-jar 接线经依赖解析证据验证（Minimum Rules #23）
- [x] `./mvnw <repo> compile`（由 test 生命周期传递满足）
- [x] `./mvnw <repo> test`（全 reactor）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2508-shared-deterministic-time-test-tools.md --strict` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-commons --severity high` 退出 0
- [x] **跨平台核查**：本 plan 产出为纯 JDK / JUnit / Maven 构造（无 OS 专有 API、无路径分隔符拼接、无外部进程/信号依赖），核查结论记录于 logs

## Deferred But Adjudicated

（本 plan 无 deferred 项。）

## Non-Blocking Follow-ups

- 违规守卫脚本与「存量基线只减不增」机制（roadmap WI-3，独立 plan）。
- 各模块时间敏感测试改造（roadmap WI-4 ~ WI-8，逐 WI 独立 plan）。

## Closure

Status Note: 共享确定性时间测试工具（ManualClock / ManualScheduler）归属裁定为 nop-kernel/nop-commons 测试源（包 io.nop.commons.test.time，test-jar 导出）；两调试模块的 3 份工具类副本与 2 个模块内 MockClock 全部归并为 owner 单一实现（行为语义逐字平移），4 个消费模块经 test-jar 消费且测试全绿；testing.md 已入册工具位置指引。独立 closure audit 复核全部 Exit Criteria 与 Closure Gate 后 APPROVED（3 项 Minor 为日志/文档措辞问题，已同批修正 2 项），roadmap WI-2 → done。

Completed: 2026-11-07

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，未参与实现；同日 plan draft R1/R2 审查者为另一独立子 agent）
- Evidence:
  - Phase 1：Decision 与拒绝方案记录于 plan 与 `ai-dev/logs/2026/11-07.md`；PASS。
  - Phase 2：owner 4 文件与 `git show HEAD:` 旧副本 diff 仅包名 + javadoc 差异（行为语义逐字一致）；owner 模块 TestManualClock 4 + TestManualScheduler 12 全绿（`_tmp/wi2-full-test.log`）；tests.jar 实测含 `io/nop/commons/test/time/` 全部 class；testing.md:520 指引与实际类一致且无 ai-dev 边界引用；`check-doc-links.mjs --strict` 复跑 0 errors。
  - Phase 3：repo 全域 grep `class ManualClock|class ManualScheduler|class MockClock` 仅 owner 一处；8 个旧副本文件（6 调试模块 + 2 MockClock）确认删除；4 消费模块 pom 均含 nop-commons test-jar 依赖且 dependency:tree 全部解析 `nop-commons:test-jar:tests`（1 处日志 + 审计现场 3 处复验）；4 模块 `-am` 测试 BUILD SUCCESS 0 失败；MockClock→ManualClock 迁移 9 处 `now()`→`startingNow()`、4 处 `advanceMillis`、5 处 `advanceSeconds`（含 `(SHORT_TTL + 1) * 1000L` 正确括号）。
  - Phase 4：`./mvnw <repo> test -T 1C -fae` BUILD SUCCESS、456 模块 SUCCESS、0 失败 0 错误（`_tmp/wi2-full-test.log`）；roadmap WI-2 → done + plan 编号。
  - Anti-Hollow：src/main 无任何 ManualClock/ManualScheduler 引用（无生产代码新增）；wiring 为 test-jar 解析 + 消费测试注册/推进真实驱动；`scan-hollow-implementations.mjs --module nop-kernel/nop-commons --severity high` 复跑 Critical 0 / High 0 exit 0。
  - 文本一致性（Guide #19）：Plan Status=completed、4 Phase 全 completed、Closure Gates 仅审计门（本审计即为该步，已勾）之外全勾、`check-plan-checklist.mjs --strict` 审计复跑 exit 0、日志条目与 plan 一致。
  - 跨平台核查：产出为纯 JDK / JUnit / Maven 构造（无 File.separator / os.name / Runtime / ProcessBuilder / 路径分隔符拼接）；审计 PASS。
  - 审计发现处理：Minor#1 日志「5 个测试类」实为 4 个（已修正）；Minor#2 roadmap Last updated 已改 2026-11-07（已修正）；Minor#3 工作树另有其他在途改动（信息性——选择性暂存纪律已排除，全绿 run 覆盖当前树）。
  - 审计判定：**APPROVED**。

Follow-up:

- 违规守卫脚本与「存量基线只减不增」（roadmap WI-3，独立 plan）。
- 各模块时间敏感测试改造（roadmap WI-4 ~ WI-8，逐 WI 独立 plan）。
- 无剩余 plan-owned work。
