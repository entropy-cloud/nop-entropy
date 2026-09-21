---
status: active
mission: nop-lint
work-item: "item-16"
group: "2026-09-22-0128"
verify: [test]
---

# EditCalculator（diff → TSInputEdit）与增量解析集成（roadmap item 16）

## Current Baseline

以下事实均已对照 live repo（2026-09-22）核实：

- nop-treesitter（M4 done）已提供增量解析底座：`TSParser.parseIncremental(Language, TSTree, List<TSInputEdit>, byte[])`（`TSParser.java:99`）及带 `ParserOptions`/`IncrementalStats` 的重载（`:109`）；`TSInputEdit(startByte, oldEndByte, newEndByte, startPoint, oldEndPoint, newEndPoint)` record 带参数校验（`parser/incremental/TSInputEdit.java:18`）；`TSPoint(row, column)` 为 0 基行列（字节列，`TSPoint.java:13`）且提供 `TSPoint.fromByteOffset(byte[], int)`（`:30`）。
- 正确性上限由底座保证：nop-treesitter 承诺增量产物与全量 parse 逐字节一致（`TSParser.java:25-28` javadoc 明示该不变量）。本 plan 的正确性义务 = **构造合法的编辑序列**（覆盖全部变更区域），不破坏该不变量。
- API 约束（design 03 §1.2）：`TSTree` 上没有 `edit()`；编辑列表由调用方计算——`EditCalculator` 即本 plan 交付项。
- 硬约束（roadmap）：**不得修改既有 nop-treesitter 类**——`EditCalculator` 与集成层全部落 nop-lint-core。
- Lint 侧现状：`LintTree` 包装 `TSTree`（`node/LintTree.java:41 tree()` / `:50 source()`）；`TreeSitterLanguageAdapter.parse` 经 `TSParser.parse` 全量解析（`lang/TreeSitterLanguageAdapter.java:78-85`）；`LintEngine.lint(rules, language, LintTree)` 预解析树入口已存在且 javadoc 明示供增量 run 使用（`engine/LintEngine.java:56-59`）——但**当前无任何调用方增量地产树**；nop-lint 全源码树无 EditCalculator、无增量路径。
- design 03 §1.2 的 `lintFiles(FileFilter, incremental)` 多文件形态与持久缓存是最终消费形态；CLI（item 18）与编辑器（item 41）尚不存在，本 plan 的集成落点限定为**单文件增量解析入口**。
- 性能声称的门槛 = roadmap item 13 benchmark（done）；本 plan 只做正确性声称，不做性能数字（design 11 §8 Phase 4 调优属后续）。
- 现成语料：`nop-lint/bench/corpus/OrderService.java` 与 nop-lint 测试夹具中的真实 Java 源码可作为编辑矩阵与随机编辑的语料。

## Goals

- `EditCalculator.diff(byte[] oldSource, byte[] newSource)` → `List<TSInputEdit>`：字节区间 + 起点/旧终点/新终点三个 `TSPoint`，编辑序列联合覆盖全部变更区域；输入相同时返回空表；非法入参显式抛错（英文消息）。
- 增量解析集成：旧 `LintTree` + 新旧源码 → `TSParser.parseIncremental` → 新 `LintTree`；无旧树时显式回退全量解析（回退路径可断言、不静默）；diff/解析失败 fail-closed。
- 正确性门禁：编辑矩阵 + 种子化随机编辑的语料循环中，增量产物与全量 parse 产物等价（等价 oracle 执行时裁定，如 `TSTreeCursor` 全遍历比较 type/startByte/endByte 序列——`TSNode` 为 record，无内建 equals 语义可用，oracle 需自建且可观测）。
- 接线成立：`EditCalculator` 的输出真实被 `TSParser.parseIncremental` 消费；增量入口真实被 lint 侧消费（端到端测试证明，非死代码）。
- design 03 §1.2 owner-doc 与 live baseline 同步；roadmap item 16 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）。

## Non-Goals

- 多文件并行 `lintFiles` 与跨 run 持久化 tree cache（design 03 §1.2 完整形态——随 item 18 CLI / item 39 输出 / item 41 编辑器集成立项）。
- 性能数字与 JMH 对比（门槛与纪律归 item 13 已建 benchmark 体系；本 plan 无性能声称）。
- nop-treesitter 模块任何修改（roadmap 硬约束）。
- 增量匹配/增量诊断（编辑器 keystroke 场景形态，design 11 §6——本 plan 只做增量 parse + 全量重 lint）。
- Deadline 执行器（item 15）、抑制判定（item 17）。

## Phase 1 — EditCalculator（Fix + Proof）

Status: planned
Targets: `nop-lint/nop-lint-core`（main + test）

- Item Types: `Fix | Proof | Decision`

- [ ] **Decision（编辑粒度）**：裁定 diff 输出形态——单一合并 hunk vs 最小化多 hunk（含选择理由与对增量复用率的影响权衡）；结论记日志并回写 design 03 §1.2 增注。
- [ ] `EditCalculator.diff` 落地：产出合法 `TSInputEdit` 序列（字节区间约束 + 三点换算，复用 `TSPoint.fromByteOffset` 语义）。
- [ ] 测试矩阵：相同输入 → 空表；空→非空 / 非空→空；纯插入 / 纯删除 / 纯替换；多 hunk；保留前后缀的大段重写；CRLF 与多字节字符边界；相邻 hunk 边界——逐例断言 `TSInputEdit` 各字段正确。（Minimum Rules #25）
- [ ] 失败路径显式化：null 入参等非法输入抛 `NopLintException`（英文消息），无静默返回。（Minimum Rules #24）

Exit Criteria:

- [ ] 矩阵全格有断言（Minimum Rules #25）。
- [ ] 非法输入路径全部显式抛错（Minimum Rules #24）。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [ ] owner-doc：design 03 §1.2 粒度裁定增注完成。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Phase 2 — 增量解析集成（Decision + Fix + Proof）

Status: planned
Targets: `nop-lint/nop-lint-core`（main + test）

- Item Types: `Decision | Fix | Proof`

- [ ] **Decision（集成落点）**：裁定增量入口落点——`LintLanguage` 增量重载 / engine 级 reparse 入口 / `LintTree` 装配层的取舍；结论记日志并回写 design 03 §1.2 增注。
- [ ] 增量入口落地：旧树 + 新旧源码 → `parseIncremental` → `LintTree`；无旧树 → 显式全量回退（回退行为有断言）；失败 fail-closed。
- [ ] **Decision（等价 oracle）**：裁定增量 ≡ 全量的可观测等价判定（如 TSTreeCursor 全遍历比较 type/startByte/endByte 序列），记日志；oracle 本身有焦点测试。
- [ ] 单元测试：命中增量路径（有旧树）、回退路径（无旧树）、失败路径显式抛错。（Minimum Rules #25 / #24）

Exit Criteria:

- [ ] 三条路径（增量/回退/失败）各有断言（Minimum Rules #25）。
- [ ] **接线验证**（Minimum Rules #23）：`TSParser.parseIncremental` 真实消费 `EditCalculator` 输出（编辑序列经真实 diff 产出，非测试手工构造旁路）。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [ ] owner-doc：design 03 §1.2 集成落点增注完成。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Phase 3 — 语料正确性与端到端收口（Proof）

Status: planned
Targets: `nop-lint/nop-lint-core`（test）、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Proof`

- [ ] 正确性门禁：真实 Java 语料（`nop-lint/bench/corpus` + 测试夹具源码）× 编辑矩阵 + 种子化随机编辑循环（≥ 100 实例，种子固定可复现）：增量产物 ≡ 全量产物（oracle 断言），随 `./mvnw test` 执行。
- [ ] 端到端测试：parse → lint（产出诊断）→ 源码编辑 → 增量 parse → 重 lint → 新诊断集正确反映编辑（旧诊断消失 / 新诊断出现、range 正确）。
- [ ] 收口项：roadmap item 16 状态回写（draft review 通过置 `planned`，closure audit 通过置 `done`）；核对 items 15/17/18 状态未受扰动。

Exit Criteria:

- [ ] **端到端验证**（Minimum Rules #22）：从 parse 到编辑后再 lint 的完整路径走通并断言诊断变化。
- [ ] 正确性门禁可复现：实例数/种子/通过标准写入测试，退出码随 mvn test 生效。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。
- [ ] roadmap item 16 状态回写正确。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [ ] 所有 Phase 的执行项与 Exit Criteria 全部勾选，无未勾选的 in-scope 项残留。
- [ ] 无 in-scope confirmed live defect / contract drift 被静默降级到 deferred / follow-up。
- [ ] 行为契约达成：`EditCalculator.diff` 输出联合覆盖全部变更区域的合法编辑序列、增量产物 ≡ 全量产物等价门禁通过、无旧树显式回退、失败路径 fail-closed（Goals 逐条落地）。
- [ ] 受影响 owner docs 已同步：design 03 §1.2 增注完成（粒度裁定 + 集成落点）；roadmap item 16 状态回写正确。
- [ ] **Anti-Hollow Check**：独立 closure audit 验证 `EditCalculator` 输出在运行时真实被 `TSParser.parseIncremental` 消费、增量入口真实被 lint 侧调用（接线证据 + 端到端测试），无空方法体/静默跳过/no-op 作为正常实现。
- [ ] 独立子 agent closure-audit 已完成并将证据写入 `## Closure` 段落。
- [ ] `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 退出码 0。

## Draft Review Record

- dispatch review #review-2026-09-21-142035-mission-driver-2026-09-22-0128-2-edit-calculator-incremental-parse-1-a3c85e17 to opencode-2026-09-21-142035
- 2026-09-22：iteration 1，共识 approved #review-2026-09-21-142035-mission-driver-2026-09-22-0128-2-edit-calculator-incremental-parse-1-a3c85e17

## Verification

## Closure
