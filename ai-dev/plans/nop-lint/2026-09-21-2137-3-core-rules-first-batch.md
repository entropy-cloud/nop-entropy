---
status: active
mission: nop-lint
work-item: "item-11"
group: "2026-09-21-2137"
verify: [test]
---

# 首批 10 条核心规则 + fixtures + ast-grep 吸收（roadmap item 11）

## Current Baseline

以下事实均已对照 live repo（2026-09-21）核实：

- 依赖面：items 8/9/12/13 已 done；本组 plan 1（item 10 RuleTester）与 plan 2（item 14 xscript v1）落地后，规则工作具备 (a) RuleTester fixtures 门禁（roadmap 硬约束：每个 Wave-2+ 规则无 fixture 视为未完成），(b) 完整 v1 能力面——pattern/any/kind 匹配 + xscript（`node`/`captures`/`report`、`ancestor/child/children` 遍历、L1 `DeclTypeResolver` 注入）。**执行顺序依赖**：本 plan 在同组 plan 1/2 之后执行（`{N}` 序号已编码）。
- 10 条规则清单（design 02 §1 Phase 1 口径）：exception 5（no-raw-exception / no-empty-catch / silent-swallow / errorcode-param-consistency / no-log-getmessage）+ API 4（ibiz-missing-annotation / ibiz-missing-context / bizmodel-dao-access / bizmodel-safe-api）+ VFS 1（no-vfs-violation）。落点 nop-lint-nop（规则主资源 + `_vfs` 测试夹具）；该模块当前仅含 bootstrap 测试。
- 吸收对象（`ai-dev/tools/rules/` 下 3 条 ast-grep 规则，经 `run-java-lint.sh` + `sgconfig.yml` 运行，两个文件均已实测存在）：bare-runtimeexception（any 3 个 throw pattern——纯 pattern 可等价重写）；empty-catch（`kind: catch_clause` + `has: '{}'`——v1 可用 pattern `catch ($E) { }` 近似，语义差异经 cross-check 记录）；getmessage-only（`has` + `not` 关系算子——faithful 语义需 item 23 的 relational 原语，v1 能否经 xscript 祖先域近似保真由 classification 步裁定）。
- 能力边界（v1 不可用）：constraints（item 22）、relational inside/has/not（item 23）、composite all/not（item 24）、suppression（item 17）。规则若需这些原语才能 faithful 表达，必须显式裁定 successor，**不得以高误报近似冒充落地**（Anti-Slacking Rule）。
- 承接的 deferred / follow-up 项（历史 plan 显式移交）：
  - plan 03 `Deferred But Adjudicated`：design 01 §3.5 `throw new $$$` 示例修订（改用 `throw new $$$($$$)` 或等价可解析 pattern 并修订设计示例）——Successor Path 明确指向 item 11 plan；同 plan follow-up 的 design 01 §4/04 §1 提取公式措辞修订（"item 7 完成、item 11 开始前执行"，item 7 已 done）。
  - plan `2026-09-21-1420-1` Non-Goals 移交：消息模板捕获插值（`{{VAR}}`）是否引入，由 item 11 规则落地时裁定（当前引擎按 `RuleDslModel.message` 字面输出）。
  - plan 05 `Deferred But Adjudicated`：ast-grep 多规则多语料矩阵对比在 item 11 之后才有代表性——本 plan 收口时登记触发，不实施。
- 规则语义需求来源：design 02 §1 仅列名称；各规则的检测契约须在 classification 步锚定到既有 check 脚本（`ai-dev/tools/check-*.mjs`）/设计条目/ast-grep 规则原文，不得凭空发明。
- 验证覆盖缺口：mission `test` 键（`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C`）**不覆盖 nop-lint-nop 模块**——规则夹具测试须显式运行 `-pl nop-lint/nop-lint-nop -am test` 并记录输出（同 plan 1420-1 对 nop-lint-java 的处置）。
- M2 口径：items 8–13 全 done 后里程碑 M2 派生翻 `done`（roadmap 唯一动态状态块，派生值不得提前手写）。

## Goals

- **Classification Decision**：逐条裁定 10 条规则的检测语义、需求来源、最小能力与 v1 表达式形态（pattern-only / pattern+xscript / 显式 successor→items 22/23），形成可审计的分类记录。
- 按 classification 在 nop-lint-nop 落地规则 `.rule.yml` + RuleTester 夹具（valid/invalid + `.expect`），`./mvnw test` 门禁下全部可验证。
- 吸收 3 条 ast-grep 规则并完成行为 cross-check（同语料 ast-grep CLI vs nop-lint，对齐与差异逐条记录）。
- 承接历史 deferred 项：design 01 §3.5/§4、04 §1 修订；消息插值裁定。
- 完成后 roadmap item 11 具备翻 `done` 条件；M2 进入可派生状态核对。

## Non-Goals

- constraints / relational / composite 原语本体（items 22/23/24）；被裁定 successor 的规则不在本 plan 强行落地。
- 抑制（item 17）、autofix（item 25）、TS 适配（item 19）、check-*.mjs 迁移 manifest（item 28）、PMD/EP 批次（item 29）。
- ast-grep 多规则矩阵 perf 复测的实施（仅登记触发，plan 05 deferred 承接）。
- 规则库 48+ 目标与反模式规则（Wave 5，items 35）。
- 新设 JMH 口径（plan 05 基线体系裁定已记录：规则匹配热路径归既有基线，规则内容编写不引入新基准）。

## Phase 1 — 检测契约分类与 deferred 承接（Decision）

Status: completed

Targets: 本 plan 文件、`ai-dev/design/nop-lint/01-pattern-dsl.md`、`ai-dev/design/nop-lint/02-rule-library.md`、`ai-dev/design/nop-lint/04-ast-grep-alignment.md`

- Item Types: `Decision | Follow-up`

- [x] 10 规则逐条分类表：每条记录（a）检测语义一句话（b）需求来源（check 脚本/design 条目/ast-grep 规则原文）（c）最小能力（d）v1 形态：pattern-only / pattern+xscript / successor（注明 item 22/23 与不可保真原因）。分类表写入日志并回写 design 02 §1 增注；高误报近似一律不得标注为"可落地"。
- [x] **承接 plan 03 deferred**：design 01 §3.5 `throw new $$$` 示例改为可解析 pattern（`throw new $$$($$$)` 或等价形态）并修订该示例；design 01 §4/04 §1 提取公式措辞修订（plan 03 follow-up 的"item 11 开始前执行"项）。
- [x] **承接 plan 1420-1 移交**：消息插值裁定——v1 字面输出 or 引入 `{{VAR}}` 捕获插值，结论（含理由）写入日志并回写 design 对应节（01 §2 消息字段处）；本批规则消息按结论编写。
- [x] 核对本 plan 执行前 plan 1/2 的 roadmap 状态（item 10/14 已 `done` 或 `planned`），若前置未达成则在执行日志显式记录阻塞。

Exit Criteria:

- [x] 分类表覆盖 10/10 条规则，每条含来源与能力裁定，无一条缺裁定。
- [x] design 01 §3.5/§4、04 §1 修订完成；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [x] 消息插值裁定已记录且 design 回写完成。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（回归，本 Phase 无代码变更时确认基线仍绿）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 规则落地与 RuleTester 夹具

Status: completed

Targets: `nop-lint/nop-lint-nop/src/main/resources`（规则）、`nop-lint/nop-lint-nop/src/test/resources`（夹具）

- Item Types: `Fix | Proof`

- [x] 按 Phase 1 classification 落地全部"可落地"规则：`.rule.yml` 于 nop-lint-nop 主资源（VFS 路径与加载机制一致）；每条规则至少 1 valid + 2 invalid 夹具（含边界形态，如泛型/嵌套/多分支），经 `RuleTestRunner` 在 JUnit 下全绿。
- [x] xscript 型规则（预计为 API 4 条中需祖先/超类文本检查者，以 classification 为准）夹具覆盖命中与过滤两路径（命中产诊断；条件不满足零诊断）。
- [x] 被裁定 successor 的规则：在本 plan `Deferred But Adjudicated` 逐条登记（successor = item 22/23 对应 plan，Why Not Blocking Closure 写明 v1 语义不可保真原因），不落地近似版。
- [x] 显式执行 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 并记录输出（mission `test` 键不覆盖该模块）。

Exit Criteria:

- [x] 落地规则 100% 有 RuleTester fixtures 且全绿（roadmap 硬约束 + Minimum Rules #25）。
- [x] **端到端验证**（Minimum Rules #22）：每条落地规则从 `.rule.yml` 资源经 RuleTestRunner 到诊断断言完整走通。
- [x] **接线验证**（Minimum Rules #23）：xscript 型规则的过滤路径有断言（脚本真实执行并否决 match），非仅命中路径。
- [x] **无静默跳过**：被裁定 successor 的规则全部有显式登记，落地清单与 classification 表一一对应（Minimum Rules #24 / Anti-Slacking Rule）。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0 且 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0（后者显式记录）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — ast-grep 行为 cross-check 与收口

Status: completed

Targets: `ai-dev/tools/`（cross-check 语料与记录）、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Proof | Follow-up`

- [x] 对 3 条吸收规则（bare-runtimeexception / empty-catch / getmessage-only 及其 nop-lint 对应规则）构造同一 Java 语料，分别经 `ai-dev/tools/run-java-lint.sh`（ast-grep CLI）与 nop-lint 引擎运行，逐文件比对命中集合；对齐结果与差异逐条记录（差异要么修正 nop-lint 规则，要么作为已裁定 delta 记入 Phase 1 分类表）。
- [x] **Follow-up 登记（承接 plan 05 deferred）**：ast-grep 多规则多语料矩阵 perf 复测的触发条件（item 11 落地后即具备代表性）写入日志；本 plan 不实施。
- [x] 收口项：roadmap item 11 状态回写（draft review 通过置 `planned`，closure audit 通过按实际落地清单置 `done` 或保持 `planned` 并注明 successor）；核对 M2 派生条件（items 8–13 状态）与 Wave 3 剩余项状态未受扰动。

Exit Criteria:

- [x] cross-check 记录完整（3 条规则 × 语料命中对照表），无未解释的差异。
- [x] perf 复测触发已登记（non-blocking follow-up 形态，含触发条件）。
- [x] roadmap item 11 状态与实际落地清单一致；M2 派生核对记录于日志。
- [x] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（回归）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> 关闭条件记录（01-file-ledger §4.3 消解为 §5.2 完成公式派生）：三个 Phase（分类与 deferred 承接、规则落地与夹具、cross-check 与收口）执行项与 Exit Criteria 全数勾选（27/27 计数域 checklist），文件内无未勾选的 in-scope 项；classification 表覆盖 10/10 条规则并已回写 design 02 §1，落地清单与 classification 表一一对应，无高误报近似被标注为"可落地"；被裁定 successor 的 3 条规则全部登记于下方 `Deferred But Adjudicated`（含 Why Not Blocking Closure 与 Successor Path），无静默跳过；承接项收口完成（design 01 §3.5/§4、04 §1 修订 + 消息插值裁定回写 design 01 §2）；无 in-scope live defect、contract drift 或硬门禁项（roadmap 硬约束"每个 Wave-2+ 规则必须带 RuleTester fixtures"）被静默降级到 deferred / follow-up；cross-check 记录完整（3 条吸收规则 × 语料命中对照表，唯一 delta=嵌套空块已裁定归 item 23）；roadmap item 11 状态由独立 closure audit 通过按实际落地清单（7 落地/3 successor）回写 `done`，M2 派生翻 `done`（items 8–13 全 done）；Anti-Hollow Check 由独立 closure audit 复核 `.rule.yml` VFS 加载 → `RuleTestRunner` → 诊断断言完整走通（`TestNopRuleSuites` 9 用例随 `./mvnw test` 真实执行，suite 发现集精确等于 7 条预期 id，缺 suite 即红 fail-closed；xscript 过滤路径为真断言——调试期 mods 取值 bug 曾使 ibiz valid 夹具真实变红），无空方法体/静默跳过/no-op 作为正常实现（`scan-hollow-implementations.mjs` core+nop 双模块 exit=0）；`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 与 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0；代码规范检查通过（imports 分组等，按仓库 Code Conventions）；独立子 agent closure-audit 证据写入下方 `## Closure` 段落——本 section 不再保留可写 checkbox（计数域纪律，01-file-ledger §2.5），机械验证/审计收口由 `## Verification` pass 行与 `## Closure` 收口记录派生；`ai-dev/logs/2026/09-22.md` 收口条目已更新。

## Deferred But Adjudicated

### exception/silent-swallow（不落地近似版）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: faithful 语义（catch 块内七信号缺失判定）需要后代模式搜索（`has`）+ 否定（`not`）+ 注释/字符串掩码；v1 xscript 的 text.contains 近似存在注释/字符串旁路——`check-silent-swallow.mjs` 的 comment-bypass fixture 正是防这类假阴性/假阳性，落地它等于以高误报近似冒充（Anti-Slacking Rule 禁止）。当前仓库该不变式仍由 `check-silent-swallow.mjs` 门禁承担，supported baseline 不受影响。
- Successor Required: `yes`
- Successor Path: roadmap item 23（relational rules：inside/has/follows/precedes + not 组合）落地后的对应 plan

### exception/no-log-getmessage（不落地近似版）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: ast-grep 原规则语义 = `has: $E.getMessage()` 且 `not: has: throw $$$`，faithful 表达需要 `has`+`not` 关系算子；v1 xscript 无法枚举后代（`descendant` 仅返回首个）且无否定原语，text.contains 近似同样存在注释/字符串旁路。当前仓库该行为仍由 `ai-dev/tools/rules/java-lint-getmessage-only.yml`（ast-grep CLI）承担。
- Successor Required: `yes`
- Successor Path: roadmap item 23（relational rules）落地后的对应 plan

### exception/errorcode-param-consistency（不落地近似版）

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: `check-error-param-consistency.mjs` 的 faithful 语义需要跨文件 ErrorCode 注册表分析（解析 `*Errors.java` 的 ARG_* 注册表 + define-face 校验 + throw 站点常量解析）——超出 v1 能力面，items 22/23 的单文件约束/关系算子也不足以表达；文本近似版会引入大量误报。该不变式当前仍由 mjs 脚本门禁承担（zero-hit hard gate）。
- Successor Required: `yes`
- Successor Path: roadmap item 28（check-*.mjs 迁移 manifest，逐脚本枚举 + 切换计划）；若届时需要引擎级支持，再由该 plan 裁定是否立项专用 analyzer

## Draft Review Record

- dispatch review #review-2026-09-21-142035-mission-driver-2026-09-21-2137-3-core-rules-first-batch-1-df97ff14 to 2026-09-21-142035-mission-driver/REVIEW_PLANS
- 2026-09-21：iteration 1，共识 approved #review-2026-09-21-142035-mission-driver-2026-09-21-2137-3-core-rules-first-batch-1-df97ff14

## Verification

- pass test 2026-09-21-142035-mission-driver exit=0

- closure-visit 复核（2026-09-22，独立 auditor opencode-pid-96490）：`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` BUILD SUCCESS，**242 tests，0 failures**，即 frontmatter `verify` 键 `test` 对应 mission `commands.test`。
- typecheck：`./mvnw -pl nop-lint/nop-lint-core -am test-compile -T 1C` exit=0。
- build：`./mvnw -pl nop-lint/nop-lint-core -am clean package -DskipTests -T 1C` exit=0。
- 规则模块显式验证（mission `test` 键不覆盖 nop-lint-nop，按 plan Phase 2 Exit Criteria 显式运行）：`./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` exit=0（**10 tests，0 failures**，含 `TestNopRuleSuites` 9 用例：suite 发现集断言 + 主资源管线加载 + x:extends 一致性）。
- lint 裁定：mission `commands.lint`（`./mvnw -pl nop-lint/nop-lint-core -am checkstyle:check -q 2>/dev/null || echo 'lint not configured'`）按配置 exit=0；裸 `checkstyle:check` 为全仓遗留基线不通过（nop-api-core 9226 处 style-only 违规，与本 plan 无关且 roadmap 终态即以 nop-lint 替代 checkstyle），mission 已显式将其降级为非阻塞，不计入本 plan 完成公式（与 plan 1420-1 / 2137-1 / 2137-2 同一裁定）。
- 辅助门禁：`scan-hollow-implementations.mjs --module nop-lint/nop-lint-core --severity high` exit=0、`--module nop-lint/nop-lint-nop --severity high` exit=0（0 findings）；`check-doc-links.mjs --strict` 退出码 0（0 errors）。

## Closure

Status Note: 三个 Phase 全部落地并经独立 closure audit 复核通过：Phase 1 分类裁定（10/10 规则逐条 (a)语义/(b)来源/(c)最小能力/(d)v1 形态分类表记录于 `ai-dev/logs/2026/09-22.md` 并回写 design 02 §1；承接项三项全部收口——design 01 §3.5 `throw new $$$($$$)` 可解析形态、design 01 §4/04 §1 提取公式措辞、消息插值裁定"v1 不引入 `{{VAR}}`，动态消息经 xscript report 拼接"回写 design 01 §2）、Phase 2 规则落地（7 条 `.rule.yml` 于 nop-lint-nop 主资源 `_vfs/nop/lint/rules/`，每条 1 valid + 2 invalid + `.expect` 夹具，suite 为 `x:extends` 指针零重复；3 条 successor 显式裁定不落地近似版）、Phase 3 cross-check 与收口（3 条吸收规则 × 语料对照表，唯一 delta=嵌套空块（relational `has`，item 23）已裁定登记；perf 复测触发已登记；roadmap item 11 按 closure audit 通过回写 `done`，M2 派生翻 `done`）。27/27 计数域 checklist 全勾，无 in-scope live defect 被降级（3 条 successor 规则均需 item 23/28 能力面，faithful 语义由既有 mjs/ast-grep 门禁继续承担，登记于 `Deferred But Adjudicated`）。
Completed: 2026-09-22

Closure Audit Evidence:

- dispatch audit #audit-2026-09-21-142035-mission-driver-2026-09-21-2137-3-core-rules-first-batch-1-dc6bf921 to opencode-pid-96490 models={exec:zhipuai-coding-plan/glm-5.3-flash,aud:zhipuai-coding-plan/glm-5.3-flash}
- accepted #audit-2026-09-21-142035-mission-driver-2026-09-21-2137-3-core-rules-first-batch-1-dc6bf921：独立 auditor（fresh closure visit）复核通过——亲测复跑全套验证命令（test-compile / clean package -DskipTests / test 均 exit=0，**242 tests 全绿**；规则模块 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` exit=0 **10 tests 全绿**；mission `lint` 键按配置 exit=0，裸 checkstyle 遗留基线裁定见 `## Verification`）；Anti-Hollow 通过：主资源 `.rule.yml` → VFS 加载（xdef 校验 + `lint.register-model.xml` 管线）→ suite `x:extends` 指针 → `RuleTestRunner.runSuites` → `LintEngine.lint` → `.expect` 逐条断言端到端真实连通，`TestNopRuleSuites` 9 用例在本 visit 随 `./mvnw test` 真实执行且 suite 发现集精确等于 7 条预期 id（缺 suite 即红，fail-closed 防静默丢弃）；xscript 过滤路径为真断言（no-empty-catch 注释空体豁免依赖 xscript 具名子节点判定、ibiz 注解过滤在 valid 夹具上生效——调试期 mods bug 曾使其真实变红）；`scan-hollow-implementations.mjs` core+nop 双模块 exit=0，新增规则/夹具/测试无空方法体/静默跳过/no-op；机械修复消解 CLOSURE_SCRIPT_CHECK 4 项（`## Closure Gates` 12 个计数域外 checkbox 按 01-file-ledger §4.3 消解为纯 prose、frontmatter 移除子集外 `last-reviewed` 字段、`## Verification` 补 pass 行、`## Closure` 补 dispatch/accepted 收口对），`plan-check.mjs --strict` 重跑派生完成公式全绿；语义复核 27/27 计数域 checklist 与 live repo 一致（7 条规则文件+21 夹具+9 测试实测、design 01/02/04 增注在案、cross-check 对照表+承接项裁定记录于日志）；deferred 分类诚实（3 条 successor 均有 v1 不可保真原因与 successor path，无 in-scope defect 降级）。
- Reviewer / Agent: mission-driver closure auditor（独立 visit，opencode-pid-96490，model zhipuai-coding-plan/glm-5.3-flash）
- Evidence: 本文件 `## Verification` pass 行（2026-09-21-142035-mission-driver）；`ai-dev/logs/2026/09-22.md` closure audit 条目；roadmap item 11 → `done`、M2 → `done`

Follow-up:

- no remaining plan-owned work（silent-swallow / no-log-getmessage 归 item 23 relational 原语、errorcode-param-consistency 归 item 28 迁移 manifest，均有 successor path；ast-grep 矩阵 perf 复测为已登记触发条件，非本 plan 责任面；嵌套空块 delta 归 item 23）
