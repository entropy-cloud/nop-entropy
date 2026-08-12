# Cycle 2 / I2 — 不变式驱动审计（输出契约族门禁跨全部实现类与发射点）

> Plan Status: completed
> Last Reviewed: 2026-08-12
> Draft Review: 3 轮独立子 agent 对抗性审查通过（round 1：1 Major（F1 处置二分与 validateOutputContractPins 的 pin 吸收范围冲突）+ 9 Minor，全部修复；round 2：1 轮后 4 Minor（N1-N4）修复；round 3：0 Blocker / 0 Major / 0 Minor，verdict 可转 active）
> Source: roadmap `ai-dev/backlog/nop-stream-invariant-loop-roadmap.md` Cycle 2 / I2 行（① 跑 I1 门禁跨全部 `Output` 实现类与发射点 → 确定性 red list；② 对抗探查聚焦门禁未表达盲区（透传目标敏感分类、嵌套类方法体解析、注册/接线时序）；③ 标注已知族或新族）；前置 I1 产出 `ai-dev/audits/nop-stream-invariants/{cycle2-I1-input.md,output-contract-registry.json,mjs-pins.json}`；`ai-dev/skills/open-ended-adversarial-review-prompt.md`
> Related: 前置 `2026-08-12-1217-8-nop-stream-invariants-cycle2-I1-output-contract-gates.md`（Cycle 2 / I1，硬串行）；后续 `2026-08-12-1217-10-nop-stream-invariants-cycle2-I3-adjudication.md`（I3 裁决，依赖本 plan 的权威 red list）
> Mission: nop-stream-invariant-loop
> Work Item: Cycle 2 / I2. 不变式驱动审计

## Purpose

把 Cycle 2 / I1 沉淀的输出契约族门禁（不变式 #6）跑向全部 main `Output` 实现类与全部发射点，产出**确定性 red list**；对过渡 pin（2 条跨 task 实例）做 live 复核并逐项裁定状态；对门禁未表达的盲区做**聚焦对抗探查**（透传目标敏感分类、嵌套类方法体解析、注册/接线时序）；**每条发现标注已知族（不变式 #6 输出契约族）或新族**。产出权威版 `red-list.md` 作为 I3 裁决的唯一输入。本 plan 只审计，不修复、不做 P0-P3 严重度裁决（属 I3）、不新增门禁（新族门禁属 Cycle 3 / I1）、不派生 Cycle 3 work item（属 I6 收口）。

## Current Baseline

> 已核对 live repo（2026-08-12）：门禁工具、测试类、注册表、pin、CI 配置全部实测存在。

- **Cycle 2 / I1 已完成（前置硬串行）**：不变式 #6 门禁全绿落地（`2026-08-12-1217-8-...` completed；closure audit 记录于 `ai-dev/logs/2026/08-12.md`）。
- **门禁现状（live 实测）**：`node ai-dev/tools/check-nop-stream-invariants.mjs all` 退出码 0（inventory / sync / scan-iterations / scan-output-contract / self-test 全绿）。JUnit 门禁 10 类 / 102 tests / 0 failures（`cycle2-I1-input.md` 落档：core 40 + runtime 34 + cep 28；含新增 `TestOutputContractInvariant` 10 用例）。CI `.github/workflows/maven.yml` 已含 node step 运行 mjs 扫描器。
- **注册表（live）**：`output-contract-registry.json` — 4 个 main 实现类（`ChainingOutput`=forward / `TimestampedCollector`=forward（透传目标敏感）/ `RecordWriterOutput`=pinned-known-violation / `BroadcastingRecordWriterOutput`=pinned-known-violation）+ 6 发射点（`ProcessOperator.java:111/:134`、`WindowOperator.java:1030/:1860`、`CepOperator.java:483/:777`）+ test-only 豁免清单。
- **过渡 pin（live）**：`mjs-pins.json` pinnedViolations = 2——`RWO-cross-task-noop`（`StreamTaskInvokable$RecordWriterOutput` `collect(OutputTag)` :645 空体 no-op，key = V3 违规串精确匹配）/ `BRWO-cross-task-noop`（`StreamTaskInvokable$BroadcastingRecordWriterOutput` :705 空体 no-op）；关联 `HG-01`；removalTrigger = Cycle 2 / I4 interim fail-fast 修复落地 + 注册表分类更新后移除，禁静默移除。**live 复核（本日实测）**：两处 `collect(OutputTag)` 方法体仍为空体（`StreamTaskInvokable.java:645` 仅注释「Side outputs not supported in cross-task exchange」；:705 空体无注释）。
- **门禁盲区（已知）**：门禁只覆盖已沉淀的不变式 #6 表达面；门禁外仍可能存在——a) **透传目标敏感分类**（`TimestampedCollector` 包装 no-op / RWO 时等价跨 task 丢弃，分类以被包装对象语义为准——门禁仅断言包装 recording/no-op output 两种直接场景，未覆盖包装 RWO 的中间链 / 更深层透传链）；b) **嵌套类方法体解析**（`scan-output-contract` 扩展了 brace-depth 解析 private 嵌套类，但新形态嵌套 / 新方法体形态的解析鲁棒性未验证——解析失败显式 fail 路径已实现，未验证覆盖）；c) **注册/接线时序**（`registerSideOutputConsumer` 在 wiring 前/后注册的时序、多消费者、broadcast 与 record-writer 尾接线、`GraphExecutionPlan.java:454-458` fanOutWriters 多 vertex 部署的尾算子路径）；d) 发射点 E2E 覆盖缺口（I1 Non-Blocking Follow-ups 登记：`TestSideOutputChainingE2E` 仅覆盖 WindowOperator late-data 路径，ctx.output / ProcessWindowFunction / PatternProcessFunction 发射点的 E2E 路径未覆盖）。
- **I1 范围边界**：I1 未修复跨 task 缺口任何代码（interim fail-fast = 预授权分派 Cycle 2 / I4；线协议 = `HG-01` 人工确认门）；本 plan 不改变该裁定。
- **范围边界**：本 plan 与 `nop-stream-production` / `nop-stream-independent-audit` / `nop-stream-flink-comparison` roadmap 范围独立（roadmap「范围独立」条款）——探查为聚焦式，非全仓漫游式深度审计。

## Goals

- 跨全部 `Output` 实现类与全部发射点跑门禁（mjs all + JUnit 门禁子集），生成**确定性 red list**：unpinned 违规入 red list、stale pin 提示处置、2 条已知过渡 pin 行号与 key 复核。
- 2 条过渡 pin 动态复核并逐项裁定（维持 / 移除 + 依据），注册表一致性复核（V1-V5 自洽）。
- 聚焦对抗探查门禁盲区（透传目标敏感分类、嵌套类方法体解析、注册/接线时序、E2E 覆盖缺口）+ 非族候选评估。
- 每条发现标注族归属：已知族（不变式 #6 输出契约族兄弟实例，可追溯注册表 / finding-ID）或新族（含不变式陈述候选 + 触发证据，供 I6 按 Loop Rule 派生 Cycle 3 / I1）。
- 产出权威版 `red-list.md`（合并门禁结果 + pin 裁定 + 探查发现），零悬挂移交 I3。

## Non-Goals

- **不修复任何 red list 项**（属 I4，经 I3 裁决后执行；跨 task interim fail-fast 已在 I6 预授权分派 Cycle 2 / I4，本 plan 不提前动手）。
- **不做 P0/P1/P2/P3 严重度裁决与派发**（属 I3）。
- **不新增门禁 / 不改门禁代码**（新族门禁属 Cycle 3 / I1；本 plan 只标注新族，不写门禁）。
- **不派生 Cycle 3 work item / 不改 roadmap work item 表**（派生属 I6 收口，按 Loop Rule 预授权执行）。
- **不做全仓漫游式深度审计**（范围 = 输出契约族实现类 + 发射点 + 登记盲区；与 `nop-stream-independent-audit-roadmap.md` 不重叠）。
- **不改被测类代码**（即使发现缺陷，也只入 red list / 探查报告，不移改）。
- **不进入 `HG-01` 线协议设计**（人工确认门未过，属 I6 已登记待办）。

## Scope

### In Scope

- 门禁全量运行与确定性 red list 生成（mjs all + 10 类 JUnit 门禁 + pin 复核 + 注册表复核）。
- 2 条过渡 pin 的 live 复核与状态裁定。
- 聚焦对抗探查（盲区 a-d + 非族候选评估）。
- 发现族标注（已知族兄弟实例 / 新族）与 red-list.md 权威化。
- `ai-dev/logs/` 更新。

### Out Of Scope

- 修复（I4）、裁决（I3）、Cycle 3 派生（I6）、新门禁（Cycle 3 / I1）、`HG-01` 线协议。
- ArchUnit 引入（既有 deferred 裁定：`optimization candidate`，未触发）。
- 任何公共 API / 被测类代码变更。

## Execution Plan

### Phase 1 - 门禁全量运行与确定性 red list 生成

Status: completed
Targets: `ai-dev/tools/check-nop-stream-invariants.mjs`；`ai-dev/audits/nop-stream-invariants/{mjs-pins.json,output-contract-registry.json,red-list.md}`；10 类 JUnit 门禁测试类

- Item Types: `Proof | Decision`
- [x] 运行 `node ai-dev/tools/check-nop-stream-invariants.mjs all`（inventory / sync / scan-iterations / scan-output-contract / self-test），逐命令记录输出；**处置二分**：a) 新增 **pre-existing residual**（此前未 pin 的已知行为，无行为漂移）→ 入 red list + 追加 pin 记录 → mjs 恢复 exit 0。**pin 吸收范围以工具为准（关键约束）**：`validateOutputContractPins` 只允许 `[scan-output-contract]` V3 违规（跨 task pinned-known-violation 实例类）被 pin 吸收——**V1 新 `Output` 类 / V4 新发射点 / V5 失效点违规无法用 pin 吸收**，此类新实例 → 入 red list + 注册表更新留痕（I1 补表先例：首次跑红 = 补表属 I1 内职责）+ 说明处置路径，注册表更新后重跑 mjs 验证 exit 0——不允许通过静默忽略恢复绿色；b) **行为漂移**（**既有已 pin / 已登记记录的行为变化**——pin 描述与 live 行为不符，或既有登记行为出现未登记变化）→ 入 red list + 显式升级标记（意味着被测代码在 I1 后被改动，超出 I2 处置权，plan 转 blocked 移交升级）——不允许通过静默忽略恢复绿色。**实测：mjs `all` exit 0（5 命令全 OK）；无新增 pre-existing residual、无行为漂移、无 stale pin——处置二分全部落入「无变化」分支（red-list.md §0 记录）**
- [x] **stale pin 二分（与行为漂移的边界）**：a) 违规消失 = 已真实解决（如有人在 I4 之外修复了空体）→ 移除 pin + red-list 对应条目标记 verified（mjs-pins.json note 语义："the residual was fixed — remove the pin"）+ 注册表分类同步复核；**联动约束**：同时复核 JUnit 反射断言（`TestOutputContractInvariant` 当前断言空体 no-op）——若 JUnit 断言未同步（仍断言 no-op 而行为已变）→ 属行为漂移升级路径（外部改动超出 I2 处置权，plan 转 blocked），不允许"mjs 绿但 JUnit 红"的悬空状态；b) pin key 行号漂移但违规仍在（scanner 报新行号的 unpinned 违规）→ 重 pin 新行号 + 留痕，red list 条目保留——属 pre-existing residual 移动，非行为漂移，不升级 blocked。**容差规则**：±3 行容差仅用于人工复核记录；mjs 层面 pin key 含行号（V3 违规串带 `(文件:行)`），行号任何移动 = pin 失配 = 走重 pin，容差不适用。**实测：2 条过渡 pin 均未 stale（key 精确匹配）；行号零漂移；无重 pin 需求（red-list.md §0 复核表）**
- [x] 运行 10 类 JUnit 门禁 + 表完备性测试（`./mvnw test -pl nop-stream/nop-stream-core,nop-stream/nop-stream-runtime,nop-stream/nop-stream-cep -Dtest='Test*Invariant*'`），记录测试数 / 失败数；任何红项 → 同处置二分（确认是 pin 断言与实际行为不符 → 行为漂移升级路径；新增未覆盖行为 → 入 red list 并说明；**如需恢复 JUnit 绿：按 I1 先例把新行为显式 pin 进断言并在 red list 记录——不允许改断言掩盖漂移**）。（执行前确认本地仓库已 install 上游依赖：`-pl` 不带 `-am` 是为避免 `-Dtest` 误作用于上游模块；若本地仓库缺依赖导致命令失败，回退到 `./mvnw install -pl nop-stream -am -DskipTests -T 1C` 补装，I1/I5 先例）。**实测：BUILD SUCCESS——10 类 / 102 tests / 0 failures / 0 errors / 0 skipped，surefire 逐类计数在案（red-list.md §0），与 cycle2-I1-input.md 一致；无红项，无漂移升级**
- [x] 复核注册表自洽性（V1-V5 语义在 live 代码上的表现）：4 个实现类 live 枚举一致、6 个发射点 live 行号无漂移（±3 行容差仅用于人工复核记录；注册表行号任何移动 → 更新注册表 + 留痕）、test-only 豁免清单无新增 main 代码类混入、**disposition 措辞与 E2E 实际覆盖一致**（注册表中多处 disposition 写 "E2E covered by TestSideOutputChainingE2E"，实测该 E2E 仅覆盖 WindowOperator late-data 路径——措辞过claim 处记录待评估，供 Phase 3-d 与 I3 参考，不静默放过）。**实测：4 实现类 / 6 发射点行号全部零漂移（复核表在案）；豁免 4 条全在 src/test 无 main 混入；发现 E2E 措辞过 claim（5 条 disposition vs 实际仅 WindowOperator late-data 路径）→ 已记录为 C2-RL-3 评估项，供 Phase 3-d / I3**
- [x] 合并生成确定性 red list 初稿（写入 red-list.md：每条含 `文件:行` + 关联不变式 + 关联 finding/注册表条目 + 门禁/pin 来源）。**实测：red-list.md 重构为 Cycle 2 / I2 权威版（§0 门禁结果 + §1 C2-RL-1/2/3 主体，Cycle 1 历史版保留于 §5 附录）**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] mjs `all` 退出码 0 且五命令输出记录在案；unpinned / stale 情况全部显式处置（新增 pre-existing residual：V3 类 → 入 red list + 追加 pin 留痕；V1/V4/V5 类 → 入 red list + 注册表更新留痕，更新后重跑 mjs 验证 exit 0；stale pin → 移除并留痕）。**实测 exit 0（5 命令 OK 记录在案）；无 unpinned / stale 情况需处置（均落入「无变化」分支）**
- [x] 行为漂移处置路径已执行或确认无漂移：漂移项入 red list + 显式升级标记（plan 转 blocked 移交），不存在"为恢复绿色而静默吞掉差异"的路径。**实测确认无行为漂移（RWO/BRWO :645/:705 空体与 pin 描述一致，live 复核在案）；无 blocked 升级**
- [x] 10 类 JUnit 门禁 + 表完备性测试运行记录在案（测试名 + 数量 + 0 failures；如有失败 → 同上二分处置）。**实测 102 tests / 0 failures（逐类计数 red-list.md §0）**
- [x] 注册表自洽性复核表存在（4 实现类 + 6 发射点 live 行号 + 豁免清单）。**red-list.md §0 复核表在案（全部零漂移）**
- [x] 确定性 red list 初稿已写入 red-list.md。**Cycle 2 权威版结构 + C2-RL-1/2/3 初稿在案（§1）**
- [x] **无静默跳过**：任何门禁红项 / 复核不一致都显式进入 red list 或处置记录，无吞掉差异的路径。**E2E 措辞过 claim 已显式记录（C2-RL-3），未静默放过**
- [x] No owner-doc update required（纯审计运行，无行为契约变更）。**已裁定**
- [x] `ai-dev/logs/` 对应日期条目已更新。**`ai-dev/logs/2026/08-12.md` Phase 1 条目在案**

### Phase 2 - 过渡 pin 复核与注册表一致性裁定

Status: completed
Targets: `StreamTaskInvokable.java:611-660/:660-715`（RWO/BRWO）；`mjs-pins.json`；`output-contract-registry.json`；`TestOutputContractInvariant`（nop-stream-core）

- Item Types: `Proof | Decision`
- [x] **RWO/BRWO live 复核**：确认两处 `collect(OutputTag, X)` 方法体仍为空体 no-op（RWO :645 注释-only、BRWO :705 空体），行为与 pin 描述 / 注册表分类（pinned-known-violation）一致；行号复核按 Phase 1 容差规则（±3 行容差仅用于人工复核记录；**行号任何移动 = pin key 失配 = 重 pin**，容差不适用于 pin 匹配）。**实测：RWO :645-647 空体（:646 注释 only）/ BRWO :705-706 空体（无注释）；行号零漂移（±0），无需重 pin（red-list.md §2 裁定表）**
- [x] **pin key 匹配复核**：两条 pin 的 `key` 与 `scan-output-contract` 实际输出违规串精确匹配（`compareViolationsToPins` 协议）；`removalTrigger` / `HG-01` 关联 / invariant 字段完整。**实测：2 条 key 与 scanner V3 违规串逐字符一致；mjs scan 无 unpinned 无 stale（吸收生效的运行证明）；removalTrigger / HG-01 / invariant: "#6" 字段完整**
- [x] **注册表 ↔ pin ↔ JUnit 断言三方一致**：注册表分类（pinned-known-violation）↔ pin 语义（known-violation 过渡）↔ `TestOutputContractInvariant` 反射实例化断言（空体 no-op = pin 语义）一致；发现不一致 → 按注册表为准修正（I1 定稿语义）并留痕。**实测：三方一致，无差异需修正**
- [x] 2 条 pin 状态裁定：维持（预期：跨 task 实例未修复，pin 维持至 I4 移除）/ 移除（仅在违规消失时，见 Phase 1 stale pin 二分）；裁定结论回写 red-list.md。**实测：均裁定维持（依据 = 跨 task 实例未修复 + interim fail-fast 预授权 Cycle 2 / I4 + `HG-01` 人工确认门未过 + 禁静默移除）——已回写 red-list.md §2**
- [x] **接线路径复核（决策输入）**：跨 task 生产接线链（`GraphExecutionPlan.java:454-458` → `StreamTaskInvokable.wireOperators :239/:245` / `wireTailToRecordWriter :352` → tail 算子 setOutput(RWO/BRWO)）live 行号复核，确认 6 发射点在跨 task 部署下可达的判定依据仍成立（供 I3 确认 interim fail-fast 派发）。**实测：GraphExecutionPlan :453-463（fanOutWriters 分支 :456/:458；单 writer :461）→ wireOperators(List) :200-249（尾算子 :239 RWO 单下游 / :242-245 BRWO 多下游）→ wireTailToRecordWriter :348-354（:352 RWO）全部 live 核对无漂移；6 发射点跨 task 可达性判定依据成立；补充观察：RWO/BRWO 不消费 sideOutputConsumers map（与 HG-01 一致，供 Phase 3-c）**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 2 条 pin 每项存在复核记录（live 行为 + 行号 + key 匹配 + 裁定结论）。**red-list.md §2 裁定表在案（行为 / 行号 / key / 裁定逐项）**
- [x] 注册表 ↔ pin ↔ JUnit 断言三方一致性结论在案（一致或有差异修正记录）。**结论：一致（red-list.md §2）**
- [x] 接线路径 live 复核记录在案（`文件:行` 可复核）。**GraphExecutionPlan:453-463 / StreamTaskInvokable:200-249/:348-354 在案**
- [x] 裁定结论已回写 red-list.md（状态明确：维持 / 移除 + 依据）。**均维持 + 依据（red-list.md §2）**
- [x] **无静默跳过**：每项裁定的理由显式写明（不允许"应该没问题吧"式结论）。**每条裁定附 live 行为 + key 匹配 + 三方一致证据**
- [x] No owner-doc update required（复核不改变行为）。**已裁定**
- [x] `ai-dev/logs/` 对应日期条目已更新。**`ai-dev/logs/2026/08-12.md` Phase 2 条目在案**

### Phase 3 - 聚焦对抗探查（门禁盲区 + 非族候选评估）

Status: completed
Targets: nop-stream 输出契约族 `src/main`（`ChainingOutput` / `TimestampedCollector` / `StreamTaskInvokable` RWO/BRWO + 接线点）；`output-contract-registry.json` 目标集；探查报告新文件 `ai-dev/audits/nop-stream-invariants/cycle2-I2-probing-report.md`（**新建文件，待产出，前向引用**——`I2-probing-report.md` 已存在且为 Cycle 1 产物，不复用不追加，避免历史报告混入）

- Item Types: `Proof | Decision`
- [x] 按 `open-ended-adversarial-review-prompt.md` 对输出契约族做**聚焦**对抗探查（范围 = 注册表目标集 + 盲区清单，不做全仓漫游），盲区清单：
  - a) **透传目标敏感分类**：**实测（C2-PR-1）**——全部 6 发射点 side-output 直连算子 `output` 字段（`Collector<T>` 不继承 `Output`，用户函数无法经 collector 发 side-output）；`new TimestampedCollector(` 生产调用点 3 处（ProcessOperator:39 / WindowOperator:397 / CepOperator:338）均包装算子 output；main 无更深层 Output 包装链（无 Output 子类 / 无匿名实现）；JUnit 包装 RWO 断言 = 合成场景但分类语义有效（forward 语义 + 被包装对象语义为准仍成立）；注册表 / JUnit 表达完整——**检查后无问题**
  - b) **嵌套类方法体解析**：**实测（C2-PR-2）**——显式 fail 五路径全部实现（mjs :673/:715/:727/:763/:783）+ self-test 覆盖（未识别形态 hard error）；RWO/BRWO 深两层嵌套 + 泛型形态解析正确；静默跳过形态（匿名类 `new Output<>(){}` / `record implements Output` / raw `OutputTag tag`）当前 0 实例（grep 实测）→ 登记为门禁表达扩展候选供 I6（非 live defect）
  - c) **注册/接线时序**：**实测（C2-PR-3）**——共享 map（StreamTaskInvokable:99）wiring 传引用（:182/:220）→ wiring 前后注册均可达（JUnit 接线断言实测）；同一 OutputTag 重复注册 = put last-wins 静默覆盖（观察项，不违反 #6，生产无重复注册调用面）；broadcast（BRWO :242-245）vs record-writer（RWO :239/:352）尾接线差异 = C2-RL-1/2 已 pin；GraphExecutionPlan fanOutWriters 多 vertex（:453-463）Phase 2 已复核——**检查后无问题**
  - d) **E2E 覆盖缺口评估**：**实测（C2-PR-4）**——6 发射点仅 WindowOperator:1030（late-data）有 `TestSideOutputChainingE2E`；ProcessOperator:111/:134、WindowOperator:1860、CepOperator:483/:777 共 5 个零 E2E 覆盖（单元层亦无 OutputTag 发射断言）→ 缺口确认，登记为优化级候选，不升格 red list（与 C2-RL-3 联动供 I3 参考）
- [x] 非族候选评估（每条裁定：升格为新不变式族候选 / 不升格 + 理由）：门禁未表达的输出相关残余——R16-AR-14（OperatorChain.processElement 广播）**不升格**（live 复核 OperatorChain 无该方法，随类重构消失，I2 判定无变化）；R16-AR-19/20（BatchConsumerSinkFunction buffer）**不升格**（文档化 unsynchronized by design + flush fail-fast `ERR_STREAM_STATE_ERROR` + batchSize 阈值 flush，复核无变化）；其他输出路径（emitWatermarkStatus :640-642/:650-652 / emitLatencyMarker :701-702/:709-710 跨 task 空体）→ **C2-PR-5：不变式 #6 陈述扩展候选**（同根因 / 同 `HG-01` 门 / 控制面遥测非数据丢失），不升格独立新族，供 I6 Loop Rule 评估
- [x] 每条探查发现标注族归属：已知族兄弟实例（不变式 #6，标注注册表条目 / finding-ID 或"新发现"）/ 新族（给出不变式陈述候选 + 触发证据 `文件:行`）。**C2-PR-1..5 全部带族标注（已知族 #6 观察 / 门禁扩展候选 / 陈述扩展候选）；无新独立族；候选均含触发证据 `文件:行`**
- [x] 探查报告写入 `ai-dev/audits/nop-stream-invariants/cycle2-I2-probing-report.md`（**新建文件，待产出**——I1 先例同款前向引用标注（`check-doc-links` 对其报 BROKEN_LINK 属预期）；I2-probing-report.md 同款格式，finding 含 位置 / 场景 / 影响 / 族标注）。**新建文件已产出（2026-08-12），同款格式（范围声明 / 发现含位置场景影响族标注 / 盲区处置 / 非族评估 / 盲区自评）**

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 探查报告存在，每条发现含 `文件:行` + 场景 + 族标注（repo-observable）。**`cycle2-I2-probing-report.md` 在案（C2-PR-1..5，位置 / 场景 / 影响 / 族标注齐全）**
- [x] 盲区 a-d 每项至少一句结论（含"检查后无问题"），不允许空洞的零发现报告。**a/b/c 结论 = 检查后无问题 + 观察项；d = 缺口确认登记（报告 §2）**
- [x] 非族候选评估结论在案（升格 / 不升格 + 理由）。**3 候选全裁定（R16-AR-14 / R16-AR-19/20 不升格 + 理由；控制面方法 → C2-PR-5 扩展候选，报告 §3）**
- [x] 已知族新实例与注册表 / 历史 finding 的对应关系可追溯；新族发现（如有）显式标注不变式陈述候选 + 触发证据。**报告 §4 对应表在案；C2-PR-5 含陈述候选 + 触发证据（StreamTaskInvokable.java:640-642/:650-652/:701-702/:709-710）**
- [x] 探查范围声明记录（覆盖类清单），证明是聚焦探查而非全仓漫游。**报告 §0 覆盖类清单在案（4 实现类 + 6 发射点 + 接线点 + 扫描器 + 测试面）**
- [x] **无静默跳过**：扫描器 / 探查遇无法分类形态时显式 fail 的记录在案（非静默归类）。**mjs 显式 fail 五路径实测 + self-test 覆盖在案；静默跳过三形态 0 实例 + 显式登记（C2-PR-2）**
- [x] No owner-doc update required（探查为审计产出，不改行为）。**已裁定**
- [x] `ai-dev/logs/` 对应日期条目已更新。**`ai-dev/logs/2026/08-12.md` Phase 3 条目在案**
### Phase 4 - red list 权威化与移交 I3

Status: completed
Targets: `ai-dev/audits/nop-stream-invariants/red-list.md`

- Item Types: `Proof | Follow-up`
- [x] 合并 Phase 1（门禁 red list）+ Phase 2（pin 裁定）+ Phase 3（探查发现）全部结论，red-list.md 更新为 **I2 权威版（Cycle 2）**（每条：位置 / 关联不变式 / 关联 finding 或注册表条目 / 族标注 / 验证或探查结论 / 裁决输入就绪）。**red-list.md 定稿：§0 门禁结果 + §1 C2-RL-1/2/3 + §2 pin 裁定 + §3 C2-PR-1..5 + §4 移交声明 + §5 Cycle 1 历史版存档；每条含裁决输入**
- [x] 与 cycle2-I1-input.md 对照复核：2 条过渡 pin、4 实现类、6 发射点逐项有处置（在表 / 已裁定移除并说明），**零悬挂**；顺带同步注册表行号引用（live 行号以本次复核为准）。**零悬挂核对表在案（§4）：2 pin → C2-RL-1/2、4 实现类 → §0 复核表、6 发射点 → §0 复核表 + C2-RL-3/C2-PR-4、门禁 102 tests / mjs / E2E 实测在案；注册表行号零漂移无需同步（0 处移动）**
- [x] **Anti-Hollow 证据实跑**：`./mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestSideOutputChainingE2E` 实跑 3/3 绿（既有 in-task 端到端基线的真实运行记录，作为本 plan 审计基线的 Anti-Hollow 证据）。**实测 3/3 绿（2026-08-12 21:20，surefire 记录在案）**
- [x] 移交声明写入 red-list.md：I3 裁决输入就绪（每条含足够裁决信息：位置、族、严重度参考、验证/探查结论）。**§4 移交声明在案（每条含位置 / 族 / 严重度参考 / 验证结论 / 修复方向参考）**
- [x] roadmap Work Item Cycle 2 / I2 状态流转（本 plan 转 active 时 `todo`→`planned`；closure audit 通过后 `planned`→`done`，由本 plan Closure 流程记录）。**closure audit 通过后执行（见 Closure 段）**

Exit Criteria:

- [x] red-list.md 为 Cycle 2 / I2 权威版（Phase 1-3 结论合并齐全、零悬挂：cycle2-I1-input.md 登记项全部有处置）。**定稿在案（§0-§4 齐全 + §4 零悬挂核对表）**
- [x] 每条含裁决输入（位置 / 族 / 结论），I3 可直接逐条裁决。**C2-RL-1/2/3 + C2-PR-1..5 均含位置 / 族标注 / 结论 / 修复方向参考**
- [x] 门禁保持全绿（pin-and-record 语义下；本 plan 未引入新的 CI 红）。**mjs `all` exit 0 + JUnit 门禁 10 类 102 tests 0 failures + E2E 3/3 + 全量 `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（2832 tests 0 failures，2026-08-12 21:22 实测）**
- [x] `ai-dev/logs/` 对应日期条目已更新。**`ai-dev/logs/2026/08-12.md` Phase 4 条目在案**
## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 确定性 red list 已生成且与 cycle2-I1-input.md 登记项零悬挂（2 pin + 4 实现类 + 6 发射点全部有处置）。**red-list.md §4 零悬挂核对表在案（2 pin → C2-RL-1/2、4 实现类 → §0 复核表、6 发射点 → §0 + C2-RL-3/C2-PR-4）**
- [x] 2 条过渡 pin 复核完成并回写 red-list.md（维持 / 移除均有依据）。**§2 裁定表：均维持 + 依据（live 行为 / key 匹配 / 三方一致 / 接线路径）**
- [x] 聚焦对抗探查完成（盲区 a-d + 非族候选评估），全部发现带族标注。**cycle2-I2-probing-report.md 在案（C2-PR-1..5 全带族标注 + 盲区 a-d 逐项结论 + 非族评估表）**
- [x] 新族发现（如有）显式标注（不变式陈述候选 + 触发证据），供 I6 按 Loop Rule 派生 Cycle 3 / I1——未被静默遗漏。**无新独立族；2 个扩展候选显式登记（C2-PR-2 门禁形态覆盖 / C2-PR-5 控制面陈述扩展，均含触发证据 文件:行）——未被静默遗漏**
- [x] 无 in-scope confirmed live defect 被静默降级或遗漏（全部红项 / 发现都在 red-list.md 或探查报告中）。**C2-RL-1/2/3 + C2-PR-1..5 全部在案（red-list.md §1/§3 + 探查报告）**
- [x] 门禁全绿复验（mjs `all` 退出码 0 + JUnit 门禁 0 failures）；本 plan 未改动被测代码；**若 Phase 1 发现行为漂移：plan 以 blocked 状态移交升级，不执行正常 closure**。**实测：mjs all exit 0（5 命令）+ JUnit 门禁 10 类 102 tests 0 failures + E2E 3/3 + 全量 2832 tests 0 failures；本 plan 零被测代码改动；无行为漂移（未触发 blocked）**
- [x] **Anti-Hollow Check**：closure audit 验证门禁 / 验证测试真实运行（surefire 或 mjs 输出记录），无空方法体 / 静默跳过作为“验证完成”的证据。**mjs 5 命令逐条 OK 输出 + JUnit surefire 逐类计数 + E2E 3/3 实测（21:20）+ hollow 扫描 14 项与基线一致；closure audit 复核见 Closure 段**
- [x] 独立子 agent closure-audit 已完成并记录证据（`ai-dev/logs/`）。**fresh session `ses_009db2c6affepbH0zjAkdzqggG`（2026-08-12）11/11 PASS，verdict ready for completed；证据见本 plan Closure 段 + `ai-dev/logs/2026/08-12.md`**
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan> --strict` 退出码 0。**实测 exit 0（Passed: 1；completed 状态复核见 Closure 收口处复跑）**
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0。**实测 exit 0（3 条 BROKEN_LINK 为 credential plan 既有基线，非本 plan 引入；cycle2-I2-probing-report.md 新建后链接可解析）**
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream --severity high` 运行记录在案：findings 集与执行前基线（I1 closure 钉定的 `34aed42c1` 基线 = 既有 14 项 high findings，全部为既有 fail-fast 类型——`UnsupportedOperationException` / not-yet-implemented 显式抛错，或注释说明的有意 no-op（非空实现），Rule #24 合规）一致，**无新增 finding**（该工具无豁免/pin 机制、既有 findings 非零属基线事实，判据 = findings 集与基线一致而非退出码 0；执行时先实跑记录 findings 集作为对比基线）。**实测（2026-08-12）：14 项 findings（P1 UnsupportedOperationException 11 + P6b not-yet-implemented 注释 3）与基线 commit 34aed42c1 一致，无新增 finding**
- [x] 新增验证测试（如有）随 `./mvnw test -pl nop-stream -am -T 1C` 全绿；若本 plan 未新增测试，此项显式写明 `No new test required: <reason>`。**No new test required: 本 plan 为纯审计运行（门禁 + pin 复核 + 探查 + 文档产出），未新增任何代码或测试；全量 `./mvnw test -pl nop-stream -am -T 1C` BUILD SUCCESS（2832 tests 0 failures，2026-08-12 21:22 实测）**

## Deferred But Adjudicated

### 跨 task interim fail-fast 修复（RWO/BRWO 空体 → fail-fast）

- Classification: `Fix`（已确认契约缺口 P1，I6 §6.2 双层裁决）——已确认 live defect，**必须修**，不属 deferral
- Why Not Blocking Closure: 本项不延期——I6 预裁决其处于 P1 自动修复预授权信封内（private 嵌套类行为修复，`Output` 接口零变更，同 RL-7 先例），分派 Cycle 2 / I3 确认 → I4 执行；I2 只复核 pin 状态与接线路径证据（Phase 2），不提前修复。I4 修复 + 注册表分类更新后移除过渡 pin。
- Successor Required: `yes`
- Successor Path: Cycle 2 / I3（裁决确认）→ Cycle 2 / I4（修复 + 注册表分类更新 + pin 移除）

### 跨 task side-output 线协议结构性变更（人工确认待办 `HG-01`）

- Classification: `Fix`（I6 裁定延续：已确认契约缺口 P1，执行门 = 人工确认）——非 watch-only residual
- Why Not Blocking Closure: 修复需 RecordWriter 线协议结构性重构，人工确认门未过（I6 登记在案：`HG-01`）；本 plan 不进入线协议设计；过渡 pin + 类级枚举 + 发射点注册表三重留痕继续生效。
- Successor Required: `yes`
- Successor Path: 人工确认后另立 plan；触发条件 = 人工批准 + 跨 task side-output 需求出现（或 CI 门禁红暴露新实例）

### 探查发现的 E2E 覆盖缺口（如 6 发射点中仅 late-data 路径有 E2E）

- Classification: `watch-only residual`（待 I3 裁决 / I4 或后续评估，非本 plan 处置项）
- Why Not Blocking Closure: I1 Non-Blocking Follow-ups 已登记该候选（`TestSideOutputChainingE2E` 多输出路径扩展）；本 plan Phase 3-d 只评估缺口现状并登记结论，扩展属优化项，不阻塞 I2 移交。
- Successor Required: `no`（复触发 / I4 类别清扫时评估）

## Non-Blocking Follow-ups

- 探查报告中的优化级发现（非 defect）交由 I3 裁决后按其处置（P2/P3 → Follow-up Backlog）。
- 若探查发现需要更长周期验证的场景（如极端并发压力、需要专门 harness），记录为后续候选，不阻塞 I2 移交。
- 6 发射点 E2E 覆盖扩展（ctx.output / ProcessWindowFunction / PatternProcessFunction 路径）：如 I2 评估确认缺口且非 defect，由后续 work item 或 I4 类别清扫时评估，不阻塞本 plan。

## Closure

Status Note: Cycle 2 / I2 不变式驱动审计全部 4 Phase 完成——门禁全量运行（mjs 5 命令 exit 0 + JUnit 10 类 102 tests 0 failures）产出确定性 red list（C2-RL-1/2/3）；2 条过渡 pin live 复核 + 裁定维持；聚焦对抗探查（盲区 a-d + 非族候选评估）产出 5 条带族标注发现（C2-PR-1..5，无新独立族）；red-list.md 权威化定稿并零悬挂移交 I3；独立 fresh-session closure-audit 11/11 PASS。本 plan 为纯审计运行，零被测代码改动。
Completed: 2026-08-12

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（general，fresh session）
- Audit Session: `ses_009db2c6affepbH0zjAkdzqggG`
- Evidence:
  - 每条 Exit Criterion / Closure Gate 验证结果（11/11 PASS）：
    1. mjs gates 实跑：`all` exit 0；5 子命令逐条 OK（inventory / sync / scan-iterations / scan-output-contract / self-test）；exit 0 ⟹ 零 unpinned 零 stale（mjs:552-554 + 1354-1363）
    2. JUnit 门禁实跑：`-Dtest='Test*Invariant*'` BUILD SUCCESS，surefire 逐类 8/12/10/10/9/10/4/11/21/7 = 102 tests 0 failures（与 plan 表精确一致）
    3. Anti-Hollow：`TestSideOutputChainingE2E` 复跑 3/3 绿；`TestOutputContractInvariant`（427 行）含真实断言（forward assertEquals / fail-fast assertThrows ERR_STREAM_SIDE_OUTPUT_NO_CONSUMER / pinned no-op partition.size()==0 / fail() no-silent-skip 分支）
    4. Live 代码状态：RWO :645-647 注释-only 空体 / BRWO :705-706 空体；pin key :645/:705 精确匹配；注册表 collectOutputTagLine 645/705 + pinned-known-violation 一致
    5. 注册表一致性：live `implements Output` = 恰好 4 类；6 发射点行号全部核对；E2E 措辞过 claim 诚实记录（red-list §0 + C2-RL-3）
    6. red-list 质量：§4 零悬挂表（2 pin / 4 类 / 6 点全有处置）；C2-RL 每条含位置/不变式/族/裁决输入；§2 pin 均维持+依据；C2-PR-1..5 族标注齐全；§5 Cycle 1 历史版存档保留
    7. 探查报告：§0 范围声明 / §2 盲区 a-d 逐项结论 / §3 非族评估 / 族标注 / §5 盲区自评；新文件（untracked），Cycle 1 `I2-probing-report.md` 未被覆写
    8. 零被测代码改动：git status 仅 ai-dev 文档 + 4 个新 ai-dev 文件；nop-stream src/main 与 src/test 零改动
    9. Plan 文本一致性：4 Phase completed + 全 [x]；Closure Gates 仅余独立审计项（本次已勾）；`check-plan-checklist --strict` exit 0
    10. Hollow 扫描：14 项 findings（11 UOE + 3 not-yet-implemented 注释）与基线 commit `34aed42c1` 一致，无新增
    11. Doc links：`check-doc-links --strict` exit 0（3 条 BROKEN_LINK 为 credential plan 既有基线，非本 plan 引入）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-08-12-1217-09-nop-stream-invariants-cycle2-I2-invariant-driven-audit.md --strict` 退出码 0（Passed: 1）
  - Anti-Hollow 检查结果：mjs 5 命令真实执行（逐条 OK 输出）+ JUnit surefire 逐类计数 + E2E 3/3 实跑 + hollow 扫描 14 项与基线一致；`scan-hollow-implementations.mjs` findings 集与基线一致（无新增）
  - Deferred 项分类检查：跨 task interim fail-fast（已确认契约缺口，非 deferral，预授权 Cycle 2 / I4）／`HG-01` 线协议（人工确认门）／E2E 覆盖缺口（watch-only residual）——分类诚实，无 in-scope live defect 被降级
  - 额外复核：全量 `./mvnw test -pl nop-stream -am -T 1C` 复跑 BUILD SUCCESS，2832 tests / 0 failures（与 plan 声称精确一致）；探查声明的代码位置（TimestampedCollector 调用点 / OperatorChain 无 processElement / BatchConsumerSinkFunction 文档化 unsynchronized / mjs 显式 fail 五路径）全部 live 核对属实

Follow-up:

- no remaining plan-owned work（C2-PR-3 重复注册观察项与 C2-PR-4 E2E 扩展候选已随 red-list.md §3 / 探查报告移交 I3 裁决；C2-PR-2 / C2-PR-5 不变式扩展候选供 I6 Loop Rule 评估）

## Optional Sections

## Outdated Note

无（Cycle 2 / I1 为唯一前置，未失效）
