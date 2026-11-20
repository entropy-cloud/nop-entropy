# 2575 nop-rg 编码与字节保真实施

> Plan Status: active
> Last Reviewed: 2026-11-20
> Source: `ai-dev/backlog/nop-rg-encoding-fidelity-roadmap.md`（执行分解）、`ai-dev/design/nop-rg/03-encoding-and-byte-fidelity-design.md`（设计决策）
> Related: `ai-dev/plans/2574-nop-rg-wave5-native-parity.md`（前置，已完成）

## Purpose

消除非 UTF-8 文件上的静默失真与静默错配：JSON 输出改为字节保真（`text` lossy + `bytes` base64 双字段），并提供 `--encoding <charset>` 显式编码支持（模式编码 / regex 解码 / 输出三处一致）。

## Current Baseline

（2026-11-20 live repo 逐条核实）

- literal 匹配 / 行定位 / count 已在字节域：`SearchCommand.patternBytes`（UTF-8 急切编码）、`ByteScan`、`MatchAggregator.advanceLineStart`。
- 命中行文本 lossy 解码（`new String(bytes, UTF_8)`，非法序列静默 `U+FFFD`）：`LineCursor.text`、`MatchAggregator.decode`（submatch 命中文本）。
- regex 整文件与候选行假定 UTF-8：`RegexSearcher.decode` / `processCandidateLine`；候选行 prefilter 的 `requiredLiteral` 也写死 UTF-8 编码。
- JSON 输出仅 `text`：`JsonOutput.writeMatch`（lines 与 submatch 均无 `bytes` 字段）。
- CLI 无 `--encoding`：`NopRgMain` picocli 参数解析；错误退出码现状为 2。
- 系统 rg 15.1.0 实测（2026-11-20）：非法 UTF-8 行输出 `lines:{"bytes":"<base64>"}` 且**不再输出 text**（text/bytes 互斥）；合法 UTF-8 行输出 `lines:{"text":...}`。
- **设计裁定优先**：设计文档决策 2 明确 nop-rg 输出为双字段形态（`text` lossy 恒在 + `bytes` base64 仅非法时出现），是对 rg 互斥形态的**有意超集**（消费方读 bytes 可精确还原，读 text 保可读）。E3.1 对拍以「合法 UTF-8 逐字节一致 + 非法行 bytes base64 值与 rg 一致」为准，不要求输出结构逐字节等同 rg。

## Goals

- 非 UTF-8 文件的 JSON 输出可精确还原原始字节（`bytes` base64 == 文件区间逐字节）。
- 合法 UTF-8 文件的输出与现状逐字节相同（既有对拍测试零变化）。
- `--encoding <charset>` 三处一致（模式编码 / regex 解码 / 输出解码），`--encoding none` 为显式字节保真别名。
- 非法 charset 值显式报错（exit 3），不回退 UTF-8。
- 非法字节场景不再触发 `CharsetDecoder` 回退（基础扫描 + base64，较现状快）；合法 UTF-8 输出路径无可测退化。

## Non-Goals

- 不做自动编码探测（charset 自动识别）。
- 不改变二进制判定策略（`isBinary` NUL 嗅探保持不变）。
- 不引入 `--assume-utf8` 类性能开关。
- 不实现 BOM 自动跳过以外的编码特殊处理。
- 不改变纯文本 / count / files-with-matches 输出模式的格式（仅 JSON 契约扩展与解码口径修正）。

## Scope

### In Scope

- `nop-rg-core`：`LineCursor` / `MatchAggregator` / `LineMatch` / `Submatch` / `SearchCommand` / `RegexSearcher` / `SearchCoordinator`（编码参数传递）。
- `nop-rg-cli`：`JsonOutput`（bytes 字段）、`NopRgMain`（`--encoding` 解析与 exit 3）。
- 新增单元/集成测试 + 既有对拍回归 + 变异探针 + GBK/Latin-1/Shift-JIS 语料对拍。
- owner 文档同步（`ai-dev/design/nop-rg/03-*`、roadmap 状态、`docs-for-ai` 若有路由锚点变化）。

### Out Of Scope

- `nop-rg-vector`（SIMD 字面量路径消费 `patternBytes`，随 SearchCommand 编码自动生效，无独立改动面）、`nop-rg-benchmark`（性能确认复用现有基准形态，不新增 JMH 基准）。

## Adjudications From Draft Review（2026-11-20，独立子 agent 对抗性审查）

- **R1（bytes 区间裁定）**：`lines.bytes` base64 覆盖 `lineStart..lineEnd`（**含行终止符**，与 rg 15.1 实测一致：其 base64 解码后带尾部 `\n`）；submatch `bytes` 覆盖命中的字节区间 `[byteStart, byteEnd)`。
- **R2（bytes 出现条件，指定编码下同样成立）**：`bytes` 字段出现条件恒为「该区间原始字节非法 UTF-8」，与 `--encoding` 无关；`--encoding gbk` 下输出为超集形态（text 为 GBK 解码 + bytes 为原始字节），对拍 rg 转码输出时只比 `text`。此裁定补入设计文档决策 2。
- **R3（E3 对拍矩阵）**：默认编码下三语料对拍 rg（合法行逐字节一致、非法行 bytes base64 值一致）；`--encoding gbk` 下对拍 rg 转码输出的 `text`。非法区间的 `text`（U+FFFD 替换粒度）不要求与 rg 逐字符一致，只比 bytes。
- **R4（exit 3 接线）**：charset 校验在 `NopRgMain.call()` 的 catch-all（IllegalArgumentException → exit 2）**之前显式前置**（`Charset.forName` 前置 try + `return 3`），不经异常兜底；同步更新 NopRgMain 退出码契约 javadoc（有意偏离 rg 的 0/1/2 契约，补 exit 3）。roadmap 与设计文档验收表均钉 exit 3，以此为准。
- **R5（Utf8OffsetMap 通用化，Blocker 修复）**：E2 必须把 `RegexSearcher.Utf8OffsetMap` 的 char→byte 映射按指定 Charset 通用化（`utf8Length(cp)` 写死 UTF-8，GBK 下区间漂移）；GBK regex 测试必须断言 submatch 字节偏移，不能只断言行级命中。
- **R6（对拍 0 skipped 硬门禁）**：E3 对拍测试运行环境须系统 rg 可用（skipped 计数必须为 0），否则该 Exit Criterion 不算达成。
- **R7（合法路径成本口径）**：合法 UTF-8 路径实现为「线性合法性扫描 + 既有 `new String`」（解码本身不变，叠加一次线性扫描）；E3 性能判据按此口径实测裁定，不预设「零开销」。
- **R8（构造器形态）**：`SearchCommand` 新增 Charset 加在全参主构造器，既有 3 个重载保持默认 UTF-8 兼容。
- **R9（roadmap 解锁）**：owner 已通过会话目标显式指示执行本 roadmap（「执行…直到彻底完成」），roadmap「暂不实施」锁定解除；roadmap 状态随执行同步更新。
- **R10（golden 先行）**：E1 动手前先在改动前捕获合法语料 golden 输出落库，改动后与 golden 逐字节 diff。

## Execution Plan

### Phase E1 - 输出字节保真（默认行为，无开关）

Status: completed
Targets: `nop-tools/nop-rg/nop-rg-core`（LineCursor/MatchAggregator/LineMatch/Submatch）、`nop-tools/nop-rg/nop-rg-cli`（JsonOutput）

- Item Types: `Fix`（输出失真 + decoder 慢路径）、`Feature`（bytes 字段契约）、`Proof`（对拍）

- [x] 改动前捕获合法语料 `--json` golden 输出落库（R10）
- [x] 新增字节保真工具（core）：UTF-8 合法性线性扫描 + lossy 解码（替换 `U+FFFD`，与 rg 一致）+ base64 编码；非法序列不依赖 `new String` 的 decoder 行为（合法路径 = 扫描 + 既有 `new String`，R7 口径）
- [x] `LineCursor` / `MatchAggregator`：行文本与命中文本解码改为「合法性扫描 → 合法走 `new String`（现状路径）/ 非法走 lossy + 保留原始字节」；count 口径快路径不受影响
- [x] `LineMatch` / `Submatch`：携带原始字节（仅非法时非 null，合法时零额外开销）
- [x] `JsonOutput.writeMatch`：非法区间输出 `"bytes":"<base64>"`（lines 与 submatch 的 match 对象内），合法区间输出零增长
- [x] 新增测试：非法 UTF-8 语料 bytes base64 解码 == 原始文件区间（逐字节断言）；text 为 lossy 形态
- [x] 新增测试：合法 UTF-8 文件 JSON 输出与改动前逐字节 diff 为空

Exit Criteria:

- [x] 非法 UTF-8 语料（构造语料，含非法首字节/尾部截断多字节序列）：`bytes` base64 解码与文件区间逐字节相等（自动化断言）
- [x] 合法 UTF-8 语料：`--json` 输出与改动前逐字节一致（保存 golden 输出对拍）
- [x] 既有 `RgComparisonTest` 等对拍全绿（合法语料零回归）
- [x] **端到端验证**：`NopRgMain --json` 从 CLI 入口到 stdout 输出 bytes 字段完整跑通
- [x] **无静默跳过**：bytes 字段仅在「区间非合法 UTF-8」这一可观测条件下出现，无 placeholder 路径
- [x] owner 文档：`ai-dev/design/nop-rg/03-encoding-and-byte-fidelity-design.md` 状态与实测形态确认已更新（含 rg 互斥形态 vs 双字段超集裁定）；No docs-for-ai update required（无路由变化）
- [x] `ai-dev/logs/` 当日条目已更新

### Phase E2 - `--encoding <charset>` 显式编码

Status: planned
Targets: `nop-tools/nop-rg/nop-rg-core`（SearchCommand/RegexSearcher/LineCursor/MatchAggregator/SearchCoordinator）、`nop-tools/nop-rg/nop-rg-cli`（NopRgMain）

- Item Types: `Feature`（显式编码能力）、`Fix`（regex 非 UTF-8 静默错配）

- [ ] `SearchCommand` 携带目标 Charset（默认 UTF-8，加在全参主构造器，R8）；`patternBytes` 与 `RegexSearcher.requiredLiteral`（prefilter）按该 Charset 编码（模式侧两处一致）
- [ ] `RegexSearcher` 整文件与候选行解码按指定 Charset（regex 侧）；构造器接收 Charset
- [ ] 输出侧：行文本/命中文本按指定 Charset 解码转 `text`，`bytes` 仍为原始字节 base64（仅非法 UTF-8 时输出）；`SearchCommand` 编码参数贯通 `SearchCoordinator` → `MatchAggregator`/`LineCursor`
- [ ] `NopRgMain` 新增 `--encoding` 选项；`none` 别名 = 默认字节保真；非法 charset 值 stderr 显式报错并 exit 3（catch-all 之前前置校验，R4；更新退出码 javadoc）
- [ ] `RegexSearcher.Utf8OffsetMap` char→byte 映射按指定 Charset 通用化（R5，写死 UTF-8 的 `utf8Length` 在 GBK 下区间漂移）
- [ ] 新增测试：GBK 语料 literal 命中 / regex 命中（含 submatch 字节偏移断言，R5）/ 输出文本全链路正确；`--encoding none` 与默认行为等价；非法 charset exit 3
- [ ] 变异探针（设计第三节强制）：破坏「模式编码」（patternBytes 回退 UTF-8）→ GBK 用例必红；破坏「regex 解码」（RegexSearcher 回退 UTF-8）→ GBK regex 用例必红；破坏「输出解码」→ 输出用例必红；逐一记录红→恢复绿证据

Exit Criteria:

- [ ] GBK 语料：literal、regex、`--json` 输出文本三路断言全绿；漏改任一处对应变异用例确实变红（探针证据写入日志）
- [ ] `--encoding none` 输出与未指定 `--encoding` 逐字节一致
- [ ] 非法 charset 值：exit 3 + stderr 显式报错（自动化断言）
- [ ] 二进制判定不受 `--encoding` 影响（决策 4 回归断言）
- [ ] **接线验证**：`SearchCommand.charset` 在运行时确实被 patternBytes 编码 / RegexSearcher 解码 / 输出解码三条链消费（测试断言或代码追踪记录）
- [ ] owner 文档：设计文档 03 状态更新为已实施 + 契约（`--encoding`）写入；roadmap E2 状态更新
- [ ] `ai-dev/logs/` 当日条目已更新

### Phase E3 - 验收与保护

Status: planned
Targets: `nop-tools/nop-rg/nop-rg-cli`（测试）、`ai-dev/backlog`（roadmap 状态）

- Item Types: `Proof`（验收）、`Proof`（变异探针归档）、`Proof`（性能）

- [ ] GBK / Latin-1 / Shift-JIS 语料与系统 rg `--json` 对拍（R3 矩阵：默认编码比 bytes/合法行；`--encoding gbk` 比 rg 转码 text；非法区间 text 不要求逐字符一致），对拍脚本/测试落库
- [ ] 变异探针汇总归档（E2 探针 + 「只改输出不改模式」破坏探针），证据写入日志
- [ ] 性能确认：合法 UTF-8 语料输出路径无可测退化；非法语料较现状（decoder 回退路径）有提升（复用 CoordinatorEndToEndBenchmark 形态或 ib bench 计时对比，结论写入日志）

Exit Criteria:

- [ ] 三语料对拍测试全绿且落库为回归测试，且 skipped 计数为 0（系统 rg 可用，R6 硬门禁）
- [ ] 变异探针红→绿证据完整（无假绿：恢复后 grep 确认目标行存在）
- [ ] 性能结论有数据支撑（合法零退化、非法提升）写入日志；如基线噪声过大，允许记录为「无可测退化」级证据
- [ ] roadmap Work Items 状态全部更新（done）；`ai-dev/logs/` 收口条目已更新

## Closure Gates

- [ ] 所有 in-scope 缺陷（输出失真 / regex 静默错配 / decoder 慢路径）已修复
- [ ] 行为契约达成：非法 UTF-8 可还原、合法 UTF-8 零变化、GBK 全链路正确、非法 charset exit 3
- [ ] 必要 focused verification 已完成（E1/E2/E3 全部 Exit Criteria 勾选）
- [ ] 无 in-scope live defect 被降级到 deferred / follow-up
- [ ] owner docs 已同步（设计文档 03、roadmap 状态）
- [ ] 独立子 agent closure-audit 已完成并记录证据（plan Closure 段）
- [ ] Anti-Hollow Check：closure audit 已验证 `--encoding` 三处一致链路运行时连通、bytes 字段真实输出、无空方法体/静默跳过
- [ ] `ib check`（nop-rg-core + nop-rg-cli 编译与受影响测试）通过
- [ ] 代码规范检查通过（imports 分组、4 空格缩进）

## Deferred But Adjudicated

（无——本计划所有 in-scope 项均须完成）

## Non-Blocking Follow-ups

- `nop-rg-benchmark` 新增专门编码语料 JMH 基准（优化候选；本计划用计时对比级证据即可）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent 收口审计时填写）
- Evidence: （每条 Exit Criterion / Closure Gate 验证结果 + 工具退出码）

Follow-up:

- （收口时填写或写 no remaining plan-owned work）
