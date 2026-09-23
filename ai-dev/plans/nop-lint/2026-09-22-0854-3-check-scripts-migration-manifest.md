---
status: active
mission: nop-lint
work-item: "item-28"
group: "2026-09-22-0854"
verify: [test]
---

# check-*.mjs 迁移 manifest（roadmap item 28）：逐脚本枚举 + 切换/下线计划 + 防腐门禁

## Current Baseline

以下事实均已对照 live repo（2026-09-22）核实：

- 依赖满足：item 28 deps = item 18（`done`，plan "2026-09-22-0544-1-minimal-cli"——`nop-lint check` + console 输出已交付并 dogfood）+ item 21（`done`，plan "2026-09-22-1045-2-xnode-pattern-engine"——XNode XML 规则引擎 + CLI `xml→xml` 扩展已交付）。manifest 所需的两类执行面（源码 CLI 检查 + XML 模型规则）均已存在。
- 设计权威：design 02 §3 需求追溯表——现有资产盘点（2026-09-20 修订）：`ai-dev/tools/` 下 check-*.mjs 共 **24 个**（2026-09-22 实测仍为 24 个；另有 `check-import-order.sh` 孪生脚本）；`check-silent-wrong-result.mjs` 含 **5 条子规则**须逐条枚举；`check-bean-naming.mjs` 为**当前 CI 唯一活跃的 mjs 门禁**，定向迁移到 XNode 引擎；文档一致性检查 3 条**明确排除**（非代码检查，继续由 doc-link-checker 等工具承担）；ORM 模型检查（orm-icons 等）3 条 → Phase 2 XNode 引擎随 manifest 迁移。roadmap item 28 要求"per-script enumeration replacing the vague '25+'"——本 plan 交付该 manifest 本体。
- 已发生的部分迁移（manifest 须如实记录状态，不得重复立项）：exception/silent-swallow 与 exception/no-log-getmessage 已 faithful 落地（item 23，plan 0544--2），但 **mjs/ast-grep 门禁保持不动**，切换下线归本 manifest 的 decommission 计划；ast-grep 3 条规则已 Phase 1 吸收（item 11，plan 2137-3 cross-check 收口）。
- 承接的 deferred 项（plan `2026-09-21-2137-3-core-rules-first-batch` `Deferred But Adjudicated`，`Successor Required: yes`）：**exception/errorcode-param-consistency** → Successor Path = roadmap item 28 迁移 manifest——其 faithful 语义需要跨文件 ErrorCode 注册表分析（解析 `*Errors.java` 的 ARG_* 注册表 + throw 站点常量解析），超出 v1 与 items 22/23 能力面；该不变式当前仍由 `check-error-param-consistency.mjs` 门禁承担（zero-hit hard gate）。**本 plan 必须裁定**：引擎级支持是否立项（维持 mjs 门禁 vs 立项专用 analyzer），裁定记录回写 design 02 §3 与 roadmap item 28 注记。
- manifest 防腐先例：`ai-dev/tools/check-nop-stream-audit-manifest.mjs`（nop-stream 审计 manifest 一致性门禁）——本 plan 的 manifest 门禁循此先例。
- 归属与边界：本 plan 交付 **manifest 账本 + 每脚本切换/下线计划 + 防腐门禁**，不执行任何脚本的实际迁移或下线（迁移由后续 plan 按 manifest 逐脚本交付；PMD/ErrorProne manifest 归 item 29；checkstyle/pmd.xml 迁移归 item 40）。
- 硬约束适用：已进 `lint`/CI fail-fast 的固定规则不可降级为 advisory（guide Minimum Rules #13 / Non-Degradable Items）——manifest 中任何"下线"动作都必须以"等价门禁已在 nop-lint 落地且行为对照通过"为前置门禁；零平台改动。

## Goals

- manifest 权威账本：`ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md`——逐脚本行项枚举 `ai-dev/tools/check-*.mjs` 全集（24 个，替代笼统"25+"口径），每行含：脚本名 → 子规则数（`check-silent-wrong-result` 拆 5 条逐条列）→ 检查对象类别（Java 源码/XML 模型/文档/流程工具）→ 目标能力映射（pattern 规则 / XNode 规则 / xscript / constraint / L2 / 维持 mjs / 明确排除）→ 依赖 roadmap item → switchover 门禁（切到 nop-lint 前须满足的可验证条件：规则落地 + fixtures + 行为对照）→ decommission 动作（下线/保留/排除）→ 状态。
- errorcode-param-consistency 专项裁定（承接 plan 2137-3 deferred）：维持 mjs 门禁 vs 立项引擎级 analyzer，写明理由与触发条件；结论同步回写 design 02 §3（追溯表行）与 roadmap item 28 注记。
- check-bean-naming 迁移行具体化：XNode 引擎已交付（item 21），manifest 给出其迁移行（beans.xml 命名规则的 XNode 形态、fixtures 与对照口径、下线门禁）——迁移执行本身留给后续 plan。
- manifest 防腐门禁：新增 `ai-dev/tools/check-lint-migration-manifest.mjs`（先例 `check-nop-stream-audit-manifest.mjs`）——校验 (a) manifest 枚举集 == `ai-dev/tools/check-*.mjs` 实际文件集（多出/缺失/改名均 fail），(b) 每行必填字段完整（子规则数/类别/映射/依赖/门禁/动作/状态），(c) 状态值合法。门禁自身跑通并有运行记录。
- roadmap item 28 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）+ design 02 §3 指针更新（"逐脚本规则数以迁移 manifest 记录"指向 12 号文档）。

## Non-Goals

- 任何 check-*.mjs 脚本的实际迁移、切换或下线执行（后续 plan 按 manifest 逐脚本交付；本 plan 只定账本、门禁与切换条件）。
- PMD/ErrorProne coverage manifest v1 与质量/安全/XNode 规则 9 条（item 29，design 06 §7）；checkstyle.xml/pmd-ruleset.xml 迁移与并行期（item 40，design 06 §8）。
- ast-grep 规则的进一步吸收（3 条已于 item 11 收口；若 manifest 盘点发现新增 ast-grep 规则则只登记不迁移）。
- 文档一致性检查 3 脚本与 plan/mission 工具脚本的功能改造（manifest 中记录排除/归属裁定即可，不改其行为）。
- nop-lint 引擎内核能力的变更（manifest 映射若发现能力缺口，只登记"依赖 item"指向 roadmap，不在本 plan 内补能力）。

## Phase 1 — 逐脚本盘点与裁定（Decision + Proof）

Status: planned
Targets: `ai-dev/tools/check-*.mjs`（只读盘点）、`ai-dev/logs/`

- Item Types: `Decision | Proof`

- [ ] 逐脚本枚举盘点：24 个 check-*.mjs 逐个读源码，记录子规则数（`check-silent-wrong-result.mjs` 拆 5 条子规则逐条列）、检查对象、门禁接线点（何处调用：CI/crontab/手工）、当前 zero-hit/活跃状态；`check-import-order.sh` 孪生关系记录在案。
- [ ] **Decision（errorcode-param-consistency 裁定）**：跨文件 ErrorCode 注册表分析的引擎级支持是否立项——裁定 + 理由 + 触发条件记录，并同步回写 design 02 §3 与 roadmap item 28 注记（消解 plan 2137-3 deferred 的 successor 义务）。
- [ ] **Decision（排除与归属裁定）**：文档一致性 3 脚本（design 02 §3 已排除）核对维持排除；plan/mission 工具脚本（`check-plan-checklist`/`check-plan-status`）与流程类脚本（`check-fix-commit-diff`/`check-nop-stream-*` 等）逐个裁定"非代码检查、排除"或"保留 mjs"，不把 in-scope 代码检查伪装成排除（Anti-Slacking：每条排除必须写明确理由）。
- [ ] **Decision（能力映射）**：每条子规则 → nop-lint 目标能力（pattern/XNode/xscript/constraint/L2/维持 mjs）+ 依赖的 roadmap item（含依赖未交付项的挂起标注）；已迁移两项（silent-swallow、no-log-getmessage）记录为"已落地、待切换"，门禁现状如实登记。
- [ ] 盘点结论记录：分类汇总（各类别脚本数/子规则总数）写入日志，供 Phase 2 manifest 数字一致性核对。

Exit Criteria:

- [ ] 24/24 脚本 + 每条子规则均有 (a) 子规则数 (b) 类别 (c) 能力映射 (d) 依赖 item 的裁定记录，无未裁定行。
- [ ] errorcode-param-consistency 裁定完成并回写 design 02 §3 + roadmap 注记（plan 2137-3 deferred 消解）。
- [ ] 每条"排除/维持 mjs"裁定均有明确理由，无 in-scope 代码检查被伪装排除（Anti-Slacking 自查记录在案）。
- [ ] `ai-dev/logs/` 对应日期条目已更新（盘点表与裁定记录）。

## Phase 2 — manifest 落盘 + 防腐门禁（Fix + Proof）

Status: planned
Targets: `ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md`、`ai-dev/tools/check-lint-migration-manifest.mjs`、`ai-dev/design/nop-lint/02-rule-library.md`、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Fix | Proof`

- [ ] 写入 manifest：`ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md`——逐脚本行项（Phase 1 裁定为准）：子规则数/类别/目标映射/依赖 item/switchover 门禁（可验证条件：规则落地 + fixtures + 行为对照通过）/decommission 动作/状态；头部写明账本定位（design 02 §3 的展开权威）与数字口径（替代"25+"）。
- [ ] 每脚本 switchover/decommission 计划完整：至少覆盖已迁移两项的下线门禁（等价 nop-lint 规则 + fixtures + 行为对照 + 切换执行 plan 触发点）与 check-bean-naming 的 XNode 迁移行（形态、fixtures 口径、下线门禁）；已进 CI fail-fast 的规则不出现无门禁的"直接删除"动作（Minimum Rules #13）。
- [ ] 防腐门禁脚本：`ai-dev/tools/check-lint-migration-manifest.mjs`——校验枚举集与 live 脚本集一致（多/缺/改名 fail）、必填字段完整、状态值合法；对 manifest 自身 fail-closed（输出缺失行明细，退出码非 0）。
- [ ] 门禁运行记录：跑通 `node ai-dev/tools/check-lint-migration-manifest.mjs`（exit=0），并做一次故意破坏试验（临时移除一行 → 门禁 fail → 恢复）证明门禁真的在防（Proof）。
- [ ] 指针回写：design 02 §3"逐脚本规则数以迁移 manifest 记录"追加指向 12 号 manifest；roadmap item 28 注记补充 manifest 路径与 errorcode 裁定结论；`docs-for-ai` 侧如受影响则同步核对（预计无）。

Exit Criteria:

- [ ] manifest 24 行（+子规则拆解）与 Phase 1 裁定一一对应；每行必填字段完整；分类汇总数与日志盘点一致。
- [ ] `node ai-dev/tools/check-lint-migration-manifest.mjs` 退出码 0，且破坏试验证明 fail 路径真实生效（Proof 记录在案）。
- [ ] 无静默跳过（Minimum Rules #24）：门禁对缺失行/非法状态显式报错。
- [ ] owner-doc：design 02 §3 指针与 roadmap item 28 注记已更新且与 live 一致；check-doc-links --strict 退出码 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — 收口（Proof）

Status: planned
Targets: `ai-dev/backlog/nop-lint-roadmap.md`、`ai-dev/plans/nop-lint/`

- Item Types: `Proof`

- [ ] 一致性核对：manifest 数字口径 vs design 02 §3（"24 个"）vs roadmap item 28 描述 vs 防腐门禁输出——四处一致；后续迁移 plan 的触发关系（哪些 item/Wave 消费 manifest 行）在 manifest 尾部登记。
- [ ] 收口项：roadmap item 28 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 items 20/22/24/25/26/27/29 与 M4 未受扰动（M4 需 19–29 全 done，本项单项完成不翻转）；plan 2137-3 deferred 消解关系记入日志。
- [ ] Exit Criteria 汇总：`./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（本 plan 无生产代码变更，mission `test` 键作为回归基线照跑）；`node ai-dev/tools/check-lint-migration-manifest.mjs` 退出码 0；owner-doc 与 live 一致；`ai-dev/logs/` 对应日期条目已更新。

Exit Criteria:

- [ ] 四处数字口径一致（manifest / design 02 §3 / roadmap / 门禁输出），manifest 消费关系登记完整。
- [ ] roadmap item 28 状态回写正确，plan 2137-3 deferred 消解已记录，周边 item 与 M4 未受扰动。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0 且 `node ai-dev/tools/check-lint-migration-manifest.mjs` 退出码 0。
- [ ] owner-doc 与 live 一致；`check-doc-links.mjs --strict` 退出码 0。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 frontmatter `status` 改为 `completed`。本 plan 零生产代码变更（仅 `ai-dev/` 下文档与工具脚本），构建验证按 mission `verify: [test]` 以 nop-lint-core 回归为基线照跑。

- [ ] 三个 Phase 的 Exit Criteria 全部勾选，无未勾选 in-scope item 残留（未做项必须显式移入 Deferred But Adjudicated 并写明理由）。
- [ ] manifest 账本 24 行 + 子规则拆解完整；防腐门禁 `node ai-dev/tools/check-lint-migration-manifest.mjs` 退出码 0，破坏试验 Proof 记录在案。
- [ ] errorcode-param-consistency 裁定已回写 design 02 §3 与 roadmap item 28 注记，plan 2137-3 deferred 消解已记录。
- [ ] 无 in-scope live defect / contract drift / owner-doc drift 被静默降级为 deferred 或 follow-up（Anti-Slacking 复核）。
- [ ] owner docs（design 02 §3、roadmap item 28）与 live 一致；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（回归基线，本 plan 零生产代码变更）。
- [ ] `ai-dev/logs/` 收口记录已更新。
- [ ] 独立子 agent closure audit 已完成，证据写入 `## Closure`（含每条 Exit Criterion 与 Closure Gate 的验证结果、`check-plan-checklist.mjs --strict` 退出码 0）。

## Draft Review Record

- dispatch review #review-2026-09-22-085436-mission-driver-2026-09-22-0854-3-check-scripts-migration-manifest-1-e18667f8 to 2026-09-22-085436-mission-driver/REVIEW_PLANS
- 2026-09-22：iteration 1，共识 approved #review-2026-09-22-085436-mission-driver-2026-09-22-0854-3-check-scripts-migration-manifest-1-e18667f8

## Verification

- `node ai-dev/tools/check-lint-migration-manifest.mjs`（Phase 2 交付后）退出码 0
- `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0（回归基线）
- `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure
