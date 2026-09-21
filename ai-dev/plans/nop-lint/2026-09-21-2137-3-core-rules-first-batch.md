---
status: active
mission: nop-lint
work-item: "item-11"
group: "2026-09-21-2137"
verify: [test]
last-reviewed: "2026-09-21"
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

Status: planned

Targets: 本 plan 文件、`ai-dev/design/nop-lint/01-pattern-dsl.md`、`ai-dev/design/nop-lint/02-rule-library.md`、`ai-dev/design/nop-lint/04-ast-grep-alignment.md`

- Item Types: `Decision | Follow-up`

- [ ] 10 规则逐条分类表：每条记录（a）检测语义一句话（b）需求来源（check 脚本/design 条目/ast-grep 规则原文）（c）最小能力（d）v1 形态：pattern-only / pattern+xscript / successor（注明 item 22/23 与不可保真原因）。分类表写入日志并回写 design 02 §1 增注；高误报近似一律不得标注为"可落地"。
- [ ] **承接 plan 03 deferred**：design 01 §3.5 `throw new $$$` 示例改为可解析 pattern（`throw new $$$($$$)` 或等价形态）并修订该示例；design 01 §4/04 §1 提取公式措辞修订（plan 03 follow-up 的"item 11 开始前执行"项）。
- [ ] **承接 plan 1420-1 移交**：消息插值裁定——v1 字面输出 or 引入 `{{VAR}}` 捕获插值，结论（含理由）写入日志并回写 design 对应节（01 §2 消息字段处）；本批规则消息按结论编写。
- [ ] 核对本 plan 执行前 plan 1/2 的 roadmap 状态（item 10/14 已 `done` 或 `planned`），若前置未达成则在执行日志显式记录阻塞。

Exit Criteria:

- [ ] 分类表覆盖 10/10 条规则，每条含来源与能力裁定，无一条缺裁定。
- [ ] design 01 §3.5/§4、04 §1 修订完成；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [ ] 消息插值裁定已记录且 design 回写完成。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（回归，本 Phase 无代码变更时确认基线仍绿）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 规则落地与 RuleTester 夹具

Status: planned

Targets: `nop-lint/nop-lint-nop/src/main/resources`（规则）、`nop-lint/nop-lint-nop/src/test/resources`（夹具）

- Item Types: `Fix | Proof`

- [ ] 按 Phase 1 classification 落地全部"可落地"规则：`.rule.yml` 于 nop-lint-nop 主资源（VFS 路径与加载机制一致）；每条规则至少 1 valid + 2 invalid 夹具（含边界形态，如泛型/嵌套/多分支），经 `RuleTestRunner` 在 JUnit 下全绿。
- [ ] xscript 型规则（预计为 API 4 条中需祖先/超类文本检查者，以 classification 为准）夹具覆盖命中与过滤两路径（命中产诊断；条件不满足零诊断）。
- [ ] 被裁定 successor 的规则：在本 plan `Deferred But Adjudicated` 逐条登记（successor = item 22/23 对应 plan，Why Not Blocking Closure 写明 v1 语义不可保真原因），不落地近似版。
- [ ] 显式执行 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 并记录输出（mission `test` 键不覆盖该模块）。

Exit Criteria:

- [ ] 落地规则 100% 有 RuleTester fixtures 且全绿（roadmap 硬约束 + Minimum Rules #25）。
- [ ] **端到端验证**（Minimum Rules #22）：每条落地规则从 `.rule.yml` 资源经 RuleTestRunner 到诊断断言完整走通。
- [ ] **接线验证**（Minimum Rules #23）：xscript 型规则的过滤路径有断言（脚本真实执行并否决 match），非仅命中路径。
- [ ] **无静默跳过**：被裁定 successor 的规则全部有显式登记，落地清单与 classification 表一一对应（Minimum Rules #24 / Anti-Slacking Rule）。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0 且 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0（后者显式记录）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — ast-grep 行为 cross-check 与收口

Status: planned

Targets: `ai-dev/tools/`（cross-check 语料与记录）、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Proof | Follow-up`

- [ ] 对 3 条吸收规则（bare-runtimeexception / empty-catch / getmessage-only 及其 nop-lint 对应规则）构造同一 Java 语料，分别经 `ai-dev/tools/run-java-lint.sh`（ast-grep CLI）与 nop-lint 引擎运行，逐文件比对命中集合；对齐结果与差异逐条记录（差异要么修正 nop-lint 规则，要么作为已裁定 delta 记入 Phase 1 分类表）。
- [ ] **Follow-up 登记（承接 plan 05 deferred）**：ast-grep 多规则多语料矩阵 perf 复测的触发条件（item 11 落地后即具备代表性）写入日志；本 plan 不实施。
- [ ] 收口项：roadmap item 11 状态回写（draft review 通过置 `planned`，closure audit 通过按实际落地清单置 `done` 或保持 `planned` 并注明 successor）；核对 M2 派生条件（items 8–13 状态）与 Wave 3 剩余项状态未受扰动。

Exit Criteria:

- [ ] cross-check 记录完整（3 条规则 × 语料命中对照表），无未解释的差异。
- [ ] perf 复测触发已登记（non-blocking follow-up 形态，含触发条件）。
- [ ] roadmap item 11 状态与实际落地清单一致；M2 派生核对记录于日志。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（回归）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 plan status 改为 `completed`。关闭流程详见 plan guide 的 `When Closing The Plan` 与 `Closure Audit Rule`。

- [ ] 所有 Phase（1–3）`Status` 均为 `completed`，文件内无未勾选的 in-scope checklist 项。
- [ ] classification 表覆盖 10/10 条规则并已回写 design 02 §1；落地清单与 classification 表一一对应，无高误报近似被标注为"可落地"。
- [ ] 被裁定 successor 的规则全部登记于下方 `Deferred But Adjudicated`（含 Why Not Blocking Closure 与 Successor Path），无静默跳过。
- [ ] 承接项收口：design 01 §3.5/§4、04 §1 修订完成；消息插值裁定已记录并回写 design。
- [ ] **Anti-Hollow Check**：closure audit 已验证（a）每条落地规则从 `.rule.yml` VFS 加载 → `RuleTestRunner` → 诊断断言完整走通（不只是文件存在），（b）xscript 型规则的过滤路径有真实断言，（c）无空方法体/静默跳过/no-op 作为正常实现。
- [ ] 无 in-scope live defect、contract drift 或硬门禁项（roadmap 硬约束"每个 Wave-2+ 规则必须带 RuleTester fixtures"）被静默降级到 deferred / follow-up。
- [ ] cross-check 记录完整（3 条吸收规则 × 语料命中对照表），无未解释差异；差异要么已修正，要么作为已裁定 delta 记入 Phase 1 classification 表。
- [ ] roadmap item 11 状态与实际落地清单一致（closure audit 通过置 `done`，或保持 `planned` 并注明 successor）；M2 为派生状态且未被提前手写。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0 且 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0。
- [ ] 代码规范检查通过（imports 分组等，按仓库 Code Conventions）。
- [ ] 独立子 agent closure audit 已完成，证据（Reviewer/Agent 标识、session、逐条 Exit Criterion 与 Closure Gate 的 PASS/FAIL 结果）写入下方 `## Closure` 段落。
- [ ] `ai-dev/logs/` 收口条目已更新。

## Deferred But Adjudicated

- (执行期登记：Phase 2 中被裁定 successor 的规则逐条登记于此，每条含 Classification / Why Not Blocking Closure / Successor Required / Successor Path)

## Draft Review Record

- dispatch review #review-2026-09-21-142035-mission-driver-2026-09-21-2137-3-core-rules-first-batch-1-df97ff14 to 2026-09-21-142035-mission-driver/REVIEW_PLANS
- 2026-09-21：iteration 1，共识 approved #review-2026-09-21-142035-mission-driver-2026-09-21-2137-3-core-rules-first-batch-1-df97ff14

## Verification

- (pending)

## Closure

- (pending)
