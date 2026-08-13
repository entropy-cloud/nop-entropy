# I1 — nop-metadata 首批不变式沉淀为可执行门禁（First-Batch Invariant Guards）

> Plan Status: active
> Last Reviewed: 2026-08-13
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 1 / I1（不变式沉淀 → 首批门禁入 CI）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I1）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`
> Related: 前置 `2026-08-13-1930-1-...`（I0 目录与目标集）；后继 `2026-08-13-1930-3-...`（I2+I3 跑门禁产 red list + 裁决）

## Purpose

把 I0 盘点出的首批 4 条不变式**从文档沉淀为可执行门禁**（门禁即契约，不是 lessons 文档）。每条不变式落地为 `ai-dev/tools/*.mjs` 静态扫描器、JUnit 5 `@ParameterizedTest`（方法表驱动穷举）、或 ast-grep 规则之一，全部可运行、可复跑。本计划交付**门禁本身**与**首次运行的初始 red list 快照**（作为 I2 的输入与 I5"零命中"的对比基线）。

## Current Baseline

> 事实为 2026-08-13 live repo 实测；I0 完成后此节将引用 I0 产出的目标集精确计数。

- **I0 产出依赖**：本计划依赖 `2026-08-13-1930-1`（I0）产出的 `invariant-catalog.md`（4 条不变式陈述 + 检测方法）与 `audit-target-set.md`（方法全集 + ORM 全集）。I0 完成前，下列计数为本次实测估计值：
  - catch 块面 ≈ **130 个 catch 块（分布在 46 个文件）**（`nop-metadata-service/src/main/java`）
  - limit 入口面 ≈ 29 文件引用 `limit`
  - ORM：39 entity / 37 unique-key（36 带 constraint，1 缺）
- **门禁基线 = 零**：`ai-dev/tools/` 下无 `check-silent-swallow.mjs` / `check-orm-unique-key-constraint.mjs` / `check-sensitive-literal-leak.mjs`；无 invariant 相关 JUnit 参数化穷举测试。
- **可复用模式**：
  - Node 脚本：`ai-dev/tools/scan-hollow-implementations.mjs`（含模块过滤、severity、退出码语义）是新扫描器的结构先例。
  - ast-grep 规则：`ai-dev/tools/rules/` 已有 3 条 Java lint YAML 规则，新规则按同格式追加。
  - JUnit：`nop-metadata-service/src/test` 已有 JUnit 5 测试，参数化穷举按 `@ParameterizedTest` + `@MethodSource` 落地。
- **ArchUnit 未配置**：当前 `nop-metadata` pom.xml 未含 `archunit-junit5` 依赖。若某条不变式选用 ArchUnit，需先在对应模块 pom.xml 添加依赖（结构性变更，需记录）。
- **ORM 模型为保护区域**：`nop-metadata/model/nop-metadata.orm.xml` 变更需人工确认。本计划**不改 ORM 模型**（只读取并校验）；缺失 constraint 的 1 个 unique-key 留作 I2 red-list 条目、I4 修复。
- **已知预期违规（初始 red list 锚点）**：unique-key guard 预期命中 ≥1（那 1 个缺 constraint 的）；silent-swallow / limit / 敏感字面量 guard 的实际命中数由 I2 跑出，本计划只交付"可跑出确定性 red list"的门禁。

## Goals

- 实现 4 条不变式对应的可执行门禁（检测器），每条可独立运行并产出确定性、可复跑的结果（命中清单 + 退出码）。
- 每条门禁附带**表完备性**约束：扫描/穷举的目标集 == I0 目标集全集（新增目标不入集即门禁能感知）。
- 捕获并记录**初始 red list 快照**（每条门禁首次运行的全部命中），作为 I2 正式 red list 的基础、I5"零命中"的对比零点。
- 门禁代码 committed 入仓，可被后续 Phase / CI 调用。

## Non-Goals

- **裁决 red list / 修复违规** —— 那是 I3 裁决、I4 修复。本计划交付门禁 + 初始 red list 快照，不动产品代码。
- **把门禁提升为阻断式 hard CI gate（强制零命中才绿）** —— 那是 I5（违规清零后）的收口动作。本计划交付"可运行 + 可复跑 + 初始 red list 已记录"的门禁。
- **修改 ORM 模型 / DDL / `_gen/` 产物** —— 保护区域，留待 I4（且需人工确认）。
- **Cycle 2 新族门禁** —— 留待 I6 触发。

## Scope

### In Scope

- 4 条门禁的实现（检测器本体 + 各自的运行入口）。
- 每条门禁的**自验证测试**：给定一个已知违规样例与一个合规样例，门禁分别正确命中 / 放行（证明门禁非空壳、能真正检测）。
- 初始 red list 快照文档（汇总 4 条门禁首次运行的全部命中）。

### Out Of Scope

- red list 条目的裁决（P0/P1/defer）—— I3。
- 违规修复 —— I4。
- hard CI gate 提升（零命中阻断）—— I5。
- ORM 模型 / DDL 修改 —— I4。
- 首批 4 族之外的新不变式 —— Cycle 2 / I1。

## Execution Plan

### Workstream A — 静态扫描器族（silent-swallow / unique-key / sensitive-literal）

Status: planned
Targets: `ai-dev/tools/check-silent-swallow.mjs`、`ai-dev/tools/check-orm-unique-key-constraint.mjs`、`ai-dev/tools/check-sensitive-literal-leak.mjs`（新建）

- Item Types: `Proof`

> 这三条不变式适合用 Node 静态扫描器（参考 `scan-hollow-implementations.mjs` 结构：模块过滤、命中清单、退出码语义）。扫描目标集来自 I0 的 `audit-target-set.md`。
>
> **检测语义（算法规格层，非代码细节）**——使自验证 fixture 与"确定性命中清单"无歧义：
> - **A1 silent-swallow 检测规则**：一个 catch 子句判为命中，iff 在该 catch 块的花括号跨度内，**不**出现以下任一信号：`throw`、`NopMetadataException(`（或其它带 ErrorCode 的异常构造）、`.errorCode(`、`ErrorCode.`、`BizException`、`Biz.fatal(`。即"捕获后既不 rethrow 也不附加 ErrorCode 传播"。（注：`ai-dev/tools/rules/java-lint-empty-catch.yml` 与 `java-lint-getmessage-only.yml` 已覆盖空 catch 与仅 `getMessage()` 的窄子模式；本扫描器是它们的**语义超集**——还覆盖"log.warn 后继续"等非空但未传播 ErrorCode 的情形。新增扫描器而非扩 ast-grep 规则，因前者可做花括号跨度内的多信号判定。）
> - **A3 sensitive-literal 检测规则**：判为命中，iff 一个字符串字面量满足下列任一，且出现在 logger / error-message-builder 的实参位置：① 匹配 JDBC-URL 模式 `/jdbc:[a-z]+:\/\//`；② 长度 > 12 且匹配 SQL-literal 模式 `/\b(SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|JOIN)\b/i`（排除已脱敏的 hash 标识符如 `sqlHash`，与 R8.2 AR-16 脱敏先例一致）。（实现近似：行级扫描器按**行级共现**判定"实参位置"——同一行同时出现 logger/error-builder 调用 token 与匹配字面量即判命中；自验证 fixture 用单行样例消除歧义。）

- [ ] **A1 silent-swallow 扫描器**（不变式①）：按上述检测规则扫描 `nop-metadata-service` service-tier 的 catch 块，命中"catch 后既不 rethrow 也不把异常包装为带 ErrorCode 的异常"的实例；产出命中清单（`文件:行` + 规则说明）
- [ ] **A2 unique-key constraint 扫描器**（不变式②）：扫描 `nop-metadata/model/*.orm.xml` 全部 `<unique-key name=` 元素，命中缺 `constraint=` 属性的条目（预期初始命中 ≥1）；同时校验 `constraint` 值非空
- [ ] **A3 sensitive-literal 扫描器**（不变式④）：按上述检测规则扫描 error/log message 构造点，命中出现 raw JDBC URL 字面量 / 内联 SQL literal 的实例（与 R6.2 P2-12 / R8.2 AR-16 脱敏先例对齐）
- [ ] 每个扫描器支持 `--module nop-metadata` 过滤与确定性退出码（0 = 零命中，非 0 = 有命中），与 `scan-hollow-implementations.mjs` 语义一致
- [ ] 每个扫描器配一个**自验证 fixture**（最小违规样例 + 合规样例），证明扫描器能区分（非空壳）

Exit Criteria:

- [ ] 三个 `.mjs` 文件存在于 `ai-dev/tools/`，各自可被 `node ai-dev/tools/check-X.mjs --module nop-metadata` 运行
- [ ] 每个扫描器在当前 codebase 上产出确定性命中清单（结果可复跑一致）
- [ ] A2 在当前 ORM 模型上命中数 = live 缺 constraint 的 unique-key 数（与 I0 实测一致，预期 ≥1）
- [ ] 每个扫描器的自验证 fixture：违规样例被命中、合规样例被放行（证明检测逻辑有效）
- [ ] **接线验证**：扫描器读取的目标集口径与 I0 `audit-target-set.md` 一致（不漏扫、不凭空发明目标路径）
- [ ] **无静默跳过**：扫描器内部不得用"未实现分支返回空数组"当作正常结果；未覆盖的子模式必须显式报告或抛错
- [ ] No owner-doc update required（工具脚本；owner-doc 同步留待 I5 门禁提升时统一处理）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Workstream B — JUnit 参数化穷举族（limit 负值校验）

Status: planned
Targets: `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/invariant/TestLimitNegativeValueInvariant.java`（新建；包 `io.nop.metadata.service.invariant`）

- Item Types: `Proof`

> 这条不变式适合用 JUnit 5 `@ParameterizedTest` + `@MethodSource`（方法表驱动穷举）。方法表 == I0 枚举的全部"接受 limit 参数的 public 入口方法"全集。
>
> **构建绿 vs red list 矛盾的确定解**（非"实现时裁定"）：limit 守卫测试类 `TestLimitNegativeValueInvariant` **默认从 surefire 排除**（在 `nop-metadata-service` pom.xml 的 surefire `<excludes>` 加 `**/invariant/TestLimitNegativeValueInvariant.java`，此为持久配置、确保默认构建恒绿；备选的 per-invocation `-Dtest=!...` 仅为一次性手段，不等效于持久 `<excludes>`，不单独使用），因此 `./mvnw test -pl nop-metadata -am -T 1C` 保持绿。该类由**单独的文档化命令**调用（如 `./mvnw test -pl nop-metadata-service -Dtest=TestLimitNegativeValueInvariant`，或一个 `node` 包装器），其非零退出码 = 存在未 reject 负值的方法 = red-list 的 limit 分量；退出码与每条 FAIL 的方法名被 Phase C 收入 `initial-red-list.md`。这样默认构建不被污染，同时 red list 确定性暴露。**表完备性自检**（见下）作为一个**单独的、默认运行的、必然 PASS** 的测试（`TestLimitTargetSetCompleteness`），放在默认 surefire 集合中 —— 它只断言"方法表 == 反查全集"，不触发任何 limit 调用，因此恒绿且仍能在新增 limit 方法未入表时变红（防新方法静默成盲区）。

- [ ] **B1 limit 负值穷举测试**（不变式③）：建立方法表（来自 I0 目标集的全部 limit-taking public 方法），对每个方法断言"传入负值 limit 时抛出带 ErrorCode 的异常（reject）而非静默接受"；该类默认从 surefire 排除，由单独命令调用，FAIL 项 = red list 的 limit 分量
- [ ] **B2 表完备性自检**（默认运行、恒绿、但防漏）：`TestLimitTargetSetCompleteness` 断言"B1 方法表 == 从公共接口反查的全部 limit-taking 方法集"，新增方法不入表即此测试红（防新方法静默成盲区）；该测试不调用任何 limit，故恒绿
- [ ] 对当前已知 reject 的方法（如已实现 `ERR_PAGINATION_LIMIT_INVALID` / `ERR_SEARCH_LIMIT_INVALID` 的入口）标记为 PASS 基线；未 reject 的进入初始 red list

Exit Criteria:

- [ ] `TestLimitNegativeValueInvariant` 存在；默认 `./mvnw test -pl nop-metadata -am -T 1C` 保持绿（该类从 surefire 排除）；由单独命令调用时，已 reject 的方法 PASS、未 reject 的方法 FAIL（FAIL 即初始 red list 的 limit 分量）
- [ ] `TestLimitTargetSetCompleteness`（表完备性自检）在默认 surefire 集合中运行且 PASS（方法表 == I0 目标集 limit-taking 方法数；双向核对计数）；新增 limit 方法不入表时此测试会红
- [ ] 测试对"负值 limit 被静默接受"的方法确实判 FAIL（不是空壳断言）
- [ ] **无静默跳过**：穷举测试不得用 `@Disabled` 跳过未通过项；未 reject 的方法以 FAIL 暴露（只是不污染默认构建，因该类从默认 surefire 排除）
- [ ] **接线验证**：方法表来源与 I0 目标集一致（双向核对计数）
- [ ] No owner-doc update required（测试代码；owner-doc 同步留待 I5）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase C — 初始 Red List 快照与棘轮零点

Status: planned
Targets: `ai-dev/audits/nop-metadata-invariants/initial-red-list.md`

- Item Types: `Proof | Decision`

- [ ] 汇总 Workstream A/B 四条门禁首次运行的**全部命中**为一份快照文档，每条命中含：门禁名 / `文件:行` / 对应不变式编号 / 对应历史 audit-finding-ID（如可回溯）
- [ ] 记录"棘轮零点"：此快照的命中总数 = I5"零命中"收口的对比基准；后续 I4 每修复一项，快照对应条目标记 resolved，直至 I5 全清
- [ ] 记录门禁运行命令清单（4 条），使 I2/I5 可一键复跑

Exit Criteria:

- [ ] `initial-red-list.md` 存在，含 4 条门禁各自的命中分节，每条命中可被独立 `rg`/扫描器复跑定位
- [ ] 命中总数与各门禁首次运行输出一致（无遗漏、无主观筛选）
- [ ] 棘轮零点声明存在（指向 I5 收口对比）
- [ ] 门禁运行命令清单可被复制执行并复现相同结果
- [ ] **端到端验证**：从"运行 4 条门禁命令"到"汇总为 red list 快照"路径完整跑通，结果确定可复现
- [ ] **无静默跳过**：red list 不得人为删除"看起来不重要"的命中；所有命中原样记录，裁决留待 I3
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划新增工具脚本与测试代码（产品行为不变、不改 ORM 模型），故保留编译/测试/规范验证。

- [ ] 4 条不变式均有对应可运行门禁（3 扫描器 + 1 参数化测试），各自有自验证 fixture/断言证明非空壳
- [ ] 表完备性约束已落地（limit 方法表 == I0 全集；扫描器目标集口径 == I0 目标集）
- [ ] 初始 red list 快照已记录，命中数与门禁首次运行一致
- [ ] 门禁未把任何已知违规静默降级（全部进入 red list，裁决归 I3）
- [ ] `./mvnw compile -pl nop-metadata -am` 通过（新增测试类编译通过）
- [ ] `./mvnw test -pl nop-metadata -am -T 1C` 通过（默认绿）：limit 守卫类 `TestLimitNegativeValueInvariant` 默认从 surefire 排除，由单独命令调用产出 limit red list；`TestLimitTargetSetCompleteness`（表完备性）在默认集合并 PASS
- [ ] 单独调用 `TestLimitNegativeValueInvariant` 的命令可复跑，退出码与 FAIL 方法名被收入 `initial-red-list.md`
- [ ] checkstyle / 代码规范检查通过（新脚本/测试符合 import 顺序、命名规范）
- [ ] 受影响 owner docs：No owner-doc update required（门禁提升为 hard gate 时在 I5 统一同步 `docs-for-ai/`）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 门禁
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）每个扫描器/测试对"已知违规样例"确实命中、对"合规样例"确实放行（非空壳）；（b）门禁可被外部一键调用并产出确定性结果
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（如产出的 .md 含链接）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 前必跑，见 Minimum Rules #26）

## Deferred But Adjudicated

### 门禁提升为阻断式 hard CI gate

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划交付"可运行 + 可复跑 + 初始 red list"的门禁；阻断式提升需违规先清零（I4），否则会立即破坏 CI。I5 在违规清零后把门禁提升为"零命中才绿"的 hard gate。这是 roadmap 既定的 7 步分工，非降级。
- Successor Required: yes
- Successor Path: `ai-dev/plans/` 下 I5 对应计划（待 I4 完成后起草）

### ArchUnit 路线（如某族更适合架构约束）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 首批 4 族均可由 Node 扫描器 + JUnit 参数化覆盖；ArchUnit 需新增 pom 依赖（结构性变更）。若 I2 对抗探查发现某族用 ArchUnit 更优，在 Cycle 2 / I1 引入。
- Successor Required: no

## Non-Blocking Follow-ups

- 门禁聚合入口（统一 `ai-dev/tools/` 下聚合脚本调用 4 条门禁）可在 I5 hard-gate 提升时一并搭建。

## Closure

Status Note: （完成时填写）
Completed: （完成时填写）

Closure Audit Evidence:

- Reviewer / Agent: （完成时填写）
- Evidence: （完成时填写）

Follow-up:

- （完成时填写）
