# Cycle 2 / I1 — nop-metadata silent-wrong-result 不变式沉淀为可执行门禁

> Plan Status: completed
> Last Reviewed: 2026-08-15
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 2 / I1（silent-wrong-result 不变式评估与沉淀 → 门禁入 CI）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（Follow-up Backlog「静默错算/精度族（建议下轮 Cycle 2 / I1 评估 silent-wrong-result 不变式）」）；plans `2026-08-14-0707-2` / `2026-08-14-1133-2` Non-Blocking Follow-ups（carry-over 建议）；`ai-dev/audits/nop-metadata-invariants/invariant-catalog.md`（现有 4 条 INV + 候选不变式）
> Related: 前置 Cycle 1 plans `2026-08-13-1930-1..5`（已完成）；后继 `2026-08-15-0820-2`（I2'+I3' 审计与裁决）、`2026-08-15-0820-3`（I4'+I5'+I6' 修复与收口）

## Purpose

把 Cycle 2 再审计确认的 **silent-wrong-result（静默错算）失败族**从"逐实例修复"沉淀为**可执行的不变式门禁**，打断 roadmap 目的一节指出的根因——"修实例不修类别"（AR-12 只修了 1 处 locale 站点，live repo 仍有 41 处同族兄弟即是实证）。本计划交付：① 子族可机械化评估裁定；② 评估通过的门禁本体（含自验证 fixture）；③ 初始 red list 快照；④ 聚合入口与 CI 棘轮扩展。**不修复 red list 条目**（后继计划职责）。

## Current Baseline

> 事实为 2026-08-15 live repo 实测（rg 复现命令随条目附注）。

- **Cycle 1 门禁基线**：4 条门禁（silent-swallow / UK / limit / sensitive）当前零命中，经 `ai-dev/tools/run-nop-metadata-invariants.sh` 聚合入口运行，CI `invariant-gate` job（`.github/workflows/maven.yml`）阻断式接入（最近收口证据：plan `2026-08-14-1448-2` / `-1448-3` closure audit，1175 tests / 0 failures）。
- **silent-wrong-result 历史实例族（5 个已确认实例，全部已单点修复）**：
  - AR-01：`(long)amount` 先截断后乘 → SLA 分数错算（plan 2026-08-14-0707-2 修复）
  - AR-03：分隔符拼接 group-key 控制字符碰撞（plan 2026-08-14-0707-2 修复，改结构性 key）
  - AR-05：`contains` 子串匹配误分类列类型（plan 2026-08-14-1133-2 修复，改 exact-match）
  - AR-10：`toBigDecimal` 经 double 丢精度 + String 静默跳过（plan 2026-08-14-1133-2 修复）
  - AR-12：默认 locale `toLowerCase`（Turkish-I 风险）（plan 2026-08-14-1133-3 修复，改 `Locale.ROOT`）
- **类别残留实证（本计划的核心动机）**：
  - **locale 族残留 41 处**：main 代码 11 个文件共 41 处默认 locale `.toLowerCase()` / `.toUpperCase()`（`rg '\.(toLowerCase|toUpperCase)\(\)' nop-metadata --glob '*.java' -g '!*_gen/*' -g '!*Test*'`，分布：SqlColumnLineageExtractor 10 / MetaTableProfiler 7 / MetaDataSourceConnectionProcessor 5 / NopMetaLineageEdgeQueryAction 5 / CheckpointActionDispatcher 4 / MetaQualityRuleExecutor 4 / SqlSourceTableExtractor 2 / MetaCatalogCollector 1 / MetaContractChecker 1 / LocalReconciliationProcessor 1 / ExternalTableStructureReader 1）。其中 lineage 提取器的 CTE/alias registry 键、F9 关键字匹配等属机器比较语义（AR-12 同族）；是否全部为 live defect 由后继 I2'/I3' 逐条裁决。
  - **精度族残留 2 处（已裁定 optimization candidate）**：`MemoryOrderByComparator.java:132` / `MemoryFilterEvaluator.java:356` 的 `BigDecimal.valueOf(((Number) v).doubleValue())`（plan 2026-08-14-1133-2 closure 判定为 ORDER BY/WHERE 比较路径、非聚合、影响较小——本计划将其纳入 red list 由 I3' 重新裁决，不沿用旧裁定）。**检测边界警示**：同型近邻 `AggregationHelper.java:554`（`java.math.BigDecimal.valueOf(n.doubleValue())`）是 AR-10 修复后的**受保护正确形态**（整数类型已先行路由 `longValue()`）——朴素 pattern 扫描会命中 3 处而与基线 2 处矛盾；该族检测规则必须显式排除受保护形态（见 Phase 1 D1）。
  - **narrowing-cast 族**：`(long)(...)` 站点 live 仅剩 2 处且均为修复后的正确形态（先乘后取整，`MetaContractChecker.java:384/:395`）——该子族门禁为**防回退**性质（预期零命中）。
  - **contains-分类族 / 分隔符-key 族**：已知实例已修；兄弟站点数需 Phase 1 扫描后才有精确计数（本计划不预设）。
- **计数口径警示**：rg 复现命令不剥离注释——locale 族 41 处计数中含 `LocalReconciliationProcessor.java:124` 的 javadoc 文字伪站点；门禁扫描器口径应剥离注释（预期门禁计数 = 40，Phase 2 P3 快照以扫描器口径为准，rg 计数仅作清扫辅助参照）。同理，既有 `audit-target-set.md` §1.3 的 130 catch 块计数已漂移（live 扫描器实测 125，F14/F16 死码删除所致）——Phase 1 D3 增补时必须 live 重测存量计数，不得照抄旧值。
- **已有 Locale.ROOT 站点 10 处代码 + 1 处 javadoc 引用**（含 AR-12 修复点），证明仓库惯例兼容、修复路径无结构性障碍。
- **候选不变式（watch-only，待本计划 Phase 1 重估）**：类型/方言兼容性族（AR-20、AR-23⑧，单点命中）；并发竞态/UK 幂等族（P2-MA3-03，DB UK 已 fail-loud 兜底）。见 `invariant-catalog.md` §候选不变式。
- **方法论与棘轮规则**：`ai-dev/skills/invariant-loop-audit-prompt.md`（关键机制 #1 门禁=可执行契约、#2 单调棘轮——已沉淀不变式只增不减）。
- **ORM 模型为保护区域**：本计划不改 ORM 模型、不改 `_gen/` 产物、不改产品代码。

## Goals

- 完成silent-wrong-result 5 个子族的**可机械化评估裁定**（逐子族：可静态扫描 / 可 JUnit 穷举 / 仅可 watch-only，附理由），并把裁定通过的不变式写入 `invariant-catalog.md`（含陈述/失败族/历史证据/检测方法四要素）与 `audit-target-set.md`。
- 为裁定为"可机械化"的子族实现可执行门禁（`ai-dev/tools/check-*.mjs` 静态扫描器或 JUnit 参数化穷举，与 Cycle 1 同风格），每个门禁配自验证 fixture（违规样例命中 + 合规样例放行）。
- 交付**初始 red list 快照**（每条命中含 `文件:行` 与可复现证据，遵守 catalog 元规则"以 live 实测为准"），作为 I2'/I3' 输入与 I5' 零命中的棘轮零点。
- 新门禁接入 `run-nop-metadata-invariants.sh` 聚合入口与 CI `invariant-gate` job（阻断式，无 continue-on-error），棘轮从 4 条扩为 4+N 条。

## Non-Goals

- **修复 red list 条目** —— I4'（`2026-08-15-0820-3`）。
- **裁决 red list 条目**（P0/P1/false-positive 分类）—— I3'（`2026-08-15-0820-2`）。
- **把已知 41 处 locale 站点直接改 `Locale.ROOT`** —— 那是修复，不是沉淀；直接修会绕过审计与裁决（且其中可能有 display-only 语义的 false positive）。
- **全仓（nop-metadata 之外）门禁扩展** —— 各模块归各自 mission。
- **首批 4 条既有门禁的规则变更/弱化** —— 棘轮只增不减。

## Scope

### In Scope

- 5 个子族的评估裁定与目录更新（invariant-catalog.md / audit-target-set.md）。
- 2 个 watch-only 候选族的重估（维持 watch-only 或升级为 INV，附理由）。
- 裁定通过的门禁实现 + 自验证 fixture + 初始 red list 快照文档。
- 聚合入口脚本与 CI job 的棘轮扩展。
- 若全部子族裁定为不可机械化：交付**裁定记录文档**（含每族理由与替代防护建议），此时后继计划 2/3 按其前置条件取消——这本身是合法交付形态（Decision）。

### Out Of Scope

- 产品代码修改（含 locale 站点、精度站点）。
- red list 裁决与修复。
- 全仓扩展、ArchUnit 依赖引入（结构性变更，如确需则另立 plan-first 项）。

## Execution Plan

### Phase 1 — 子族评估裁定与目录更新

Status: completed
Targets: `ai-dev/audits/nop-metadata-invariants/invariant-catalog.md`、`ai-dev/audits/nop-metadata-invariants/audit-target-set.md`

- Item Types: `Decision`

- [x] **D1 逐子族评估**（5 子族 × 三选一裁定：静态扫描可机械化 / JUnit 可机械化 / watch-only 不可机械化）：
  - locale 族（默认 locale case-mapping 用于机器比较）——已知 41 站点（扫描器口径 40，剥离注释）；评估扫描器能否区分"机器比较键"与"display-only"语义。**若无法区分，裁定形态仍是静态扫描器 + Phase 3 模式归属按约束 C 由 P3 快照判定（当前证据预期为 b）**——不引入"恒 exit 0 的报告型第四形态"（区分语义是 I3' 裁决的职责，不是门禁的职责；门禁全量报告 + 裁决表消化，正是不引入第四形态的理由）→ **裁定：静态扫描可机械化**（无法区分语义 → 全量报告 + 裁决消化，模式 b）
  - narrowing-cast 族（`(long)` 作用于浮点表达式）——防回退性质（live 0 违规），JUnit 穷举或静态扫描均可，**JUnit 形态仅限此类预期零命中的防回退族**（见 Phase 3 关键约束）→ **裁定：静态扫描可机械化**（规则确定性排除 widening 场景：操作数须有同文件 double/float 声明；live 0 命中 = 防回退零点）
  - contains-分类族（子串匹配用作类型分类）——评估可检测模式边界 → **裁定：静态扫描可机械化**（receiver 声明类型判定：String 声明命中、集合声明放行、不可判定 watch 边界显式声明）
  - 分隔符-key 族（分隔符拼接字符串作复合 map key）——评估可检测模式边界 → **裁定：静态扫描可机械化**（key 位实参跨度内拼接 + *Key 变量赋值双规则；lambda extractor 内联为 watch 边界显式声明）
  - Number→BigDecimal 精度族（`BigDecimal.valueOf(x.doubleValue())` 无损路径旁路）——已知 2 站点；检测规则必须排除 `AggregationHelper.java:554` 一类的受保护正确形态（整数已路由 longValue 的分支内 doubleValue 属合法浮点路径）→ **裁定：静态扫描可机械化**（方法体 `.longValue()` 路由信号显式排除受保护形态，非文件名白名单）
- [x] **D2 watch-only 候选重估**：类型/方言兼容性族、并发竞态族——各给出"维持 watch-only / 升级 INV"裁定与理由（引用 2026-08-13 至今的新证据，无新证据则维持并写明）→ **均维持 watch-only**（理由写入 invariant-catalog.md 候选节：零新复发证据 + 静态不可机械化 + 替代防护）
- [x] **D3 目录更新**：裁定通过的子族写入 invariant-catalog.md（新 INV-* 条目，四要素齐全）；audit-target-set.md 增补新目标集（含 live 计数与 rg 复现命令）并**校正存量计数漂移**（对既有 §1.3 catch 块等计数 live 重测，如 130 → 当前实测值，漂移须记录原因——死码删除等，不照抄旧值）；裁定不通过的子族在候选不变式节登记理由 → INV-LOCALE/INV-NARROW/INV-CONTAINS-CLASSIFY/INV-DELIM-KEY/INV-BIGDEC 5 条新 INV 入 catalog；audit-target-set.md §5 新增（40/0/19/6/2 = 67 站点 + 复现命令）；§1.3 漂移校正 130/46 → 125/45（原因：1448-1/1448-2 死码清扫删除 5 个带 catch 死分支，commit `08b1ef556` 等）

Exit Criteria:

- [x] invariant-catalog.md 含 0~N 条新 INV-* 条目（每条含陈述/失败族/历史 audit-ID 证据/检测方法四要素），或含"全族裁定不可机械化"的显式记录 → 5 条新 INV（四要素齐全）
- [x] 每个子族裁定有三选一结论 + 书面理由（可追溯，无"未裁定"悬挂）
- [x] audit-target-set.md 新目标集含 live 计数与可复现 rg 命令（复跑计数一致）
- [x] **无静默跳过**：任何子族不得因"难检测"而默默不裁定——不可机械化必须显式写明并给替代防护建议（本批 5 族均可机械化；2 watch-only 候选维持裁定含理由与替代防护）
- [x] No owner-doc update required（目录与目标集属 `ai-dev/audits/` 工件；owner-doc 门禁叙事若需更新，归 Phase 3 一并处理）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 1 小节）

### Phase 2 — 门禁实现与初始 Red List 快照

Status: completed
Targets: `ai-dev/tools/check-*.mjs`（新建，具体文件名由 Phase 1 裁定决定）或 `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/invariant/`（新建测试类）

- Item Types: `Proof`

> 检测语义（算法规格层）：扫描器按 Phase 1 裁定的检测规则运行于 `--module nop-metadata` 过滤范围，输出确定性命中清单（`文件:行` + 规则说明）与退出码（0 = 零命中，非 0 = 有命中），与 `scan-hollow-implementations.mjs` / Cycle 1 三扫描器语义一致。JUnit 形态遵循 Cycle 1 INV-LIMIT 先例（穷举表 + 表完备性自检）。

- [x] **P1 门禁实现**：按 Phase 1 裁定实现全部"可机械化"子族的检测器（静态扫描器或 JUnit 穷举），支持确定性退出码；静态扫描器一律实现两项能力（**即使当前预期零命中也一并预实现，避免后继 Phase 返工**）：① **`--baseline <file>` 对账模式**（模式 b 所需，规格见 Phase 3 关键约束 B）；② **放行注释识别**（行内 `// invariant-ok: <裁决引用>` 形态的命中抑制，供 I3' B1b 方式 (b) FP 处置使用——识别发生在注释剥离之前；放行只是把命中移出"违规集"，审计簿记（red list/裁决表）仍记录该条目及其裁决引用，终态不驻留 baseline（见约束 B），不产生静默盲区）→ `ai-dev/tools/check-silent-wrong-result.mjs`（5 规则单门禁；--baseline 逐键计数对账；--emit-baseline 快照生成；// invariant-ok: 放行 + Allowed 节显式列出；括号失衡 INTERNAL 显式报告；exit 0/1/2，baseline 缺失=2 显式报错）
- [x] **P2 自验证 fixture**：每个门禁配最小违规样例 + 合规样例，违规命中 / 合规样例放行（证明非空壳）；静态扫描器 fixture 参照 `check-silent-swallow.mjs` 的 `--fixture` 模式，**须含三条**：baseline 对账（命中集 ⊆ baseline → exit 0；超出 → 非 0）、放行注释（带 `// invariant-ok:` 的命中不计违规、但不出现在静默盲区报告中）→ 18/18 PASS（16 检测样本双向 + baseline ⊆绿/超计数红/新键红/收缩绿 + 放行注释 violation=0/allowed=1）
- [x] **P3 初始 red list 快照**：首次运行全部新门禁，命中逐条记录至 `initial-red-list-cycle2`（.md，新建于 `ai-dev/audits/nop-metadata-invariants/`）（**定名**，后继 I2'/I3' 引用此路径；与 Cycle 1 的 initial-red-list.md 并列、不覆盖不追加）。快照为**双层**：① 人读层（每条：`文件:行` + 子族 + rg/扫描器复现命令，遵守元规则）；② 机器可读层（baseline 文件，存放于 `baseline-cycle2/`（新建于 `ai-dev/audits/nop-metadata-invariants/`） 目录，**每门禁一个文件**；字段 = 文件路径 + 命中行文本归一化（去首尾空白）+ 子族标签——对账键规格见 Phase 3 关键约束 B）→ `initial-red-list-cycle2.md`（67 条逐条含证据）+ `baseline-cycle2/` 目录下 `silent-wrong-result.json`（61 键 / 67 命中：locale 40 / narrowing 0 / contains 19 / delim 6 / bigdec 2；扫描器实测修正初测 rg 粗筛的 delim 3→6，Phase 1 文档已同步）

Exit Criteria:

- [x] 每个新门禁可被独立运行且结果可复跑一致（同输入同输出）→ 两次运行 JSON hits 完全一致；--rule 逐规则过滤计数 40/0/19/6/2 与 audit-target-set §5 一致
- [x] 自验证 fixture 双向验证通过（违规样例命中、合规样例放行；静态扫描器另含 baseline 对账 fixture：⊆ 绿 / 超出红）→ 18/18 PASS；另实测抽键 baseline → exit 1、baseline 缺失 → exit 2
- [x] `initial-red-list-cycle2` 存在且含双层结构：人读层每条含可复现证据（元规则：不以"声称已修"抹除违规）；机器可读 baseline 文件存在且对账键为文件路径+命中内容归一化文本（非行号）
- [x] **接线验证**：门禁目标集口径与 Phase 1 更新后的 audit-target-set.md 一致（不漏扫、不凭空发明目标）→ 扫描器逐规则计数 = §5.1-§5.5 权威计数（40/0/19/6/2）
- [x] **无静默跳过**：门禁内部不得用"未实现分支返回空数组"当正常结果；未覆盖子模式显式报告或抛错 → 括号失衡 = INTERNAL 显式命中；baseline 缺失/损坏 = exit 2；不可判定 receiver/lambda extractor = catalog 显式 watch 边界声明（非静默跳过——文档化边界）
- [x] **新功能测试规则**：新增扫描器代码的"功能测试"即 P2 fixture（显式列出）→ 18 项 fixture（--fixture 可复跑）；本计划无 JUnit 形态交付物（Phase 1 裁定全部静态扫描）
- [x] 产品代码零改动（`git diff` 证实 nop-metadata 主代码无变更）→ `git diff --stat -- nop-metadata/` 为空
- [x] No owner-doc update required（同 Phase 1 裁定）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 2 小节）

### Phase 3 — 聚合入口与 CI 棘轮扩展

Status: completed
Targets: `ai-dev/tools/run-nop-metadata-invariants.sh`、`.github/workflows/maven.yml`（invariant-gate job）、`docs-for-ai/`（门禁叙事如有变更）

- Item Types: `Proof`

> **关键约束 A：JUnit 形态限制与 F3 历史先例。** JUnit 穷举形态**仅限预期零命中的防回退族**（如 narrowing-cast）；预期非空 red list 的族一律以静态扫描器 + 模式 b 落地。理由：(1) JUnit 形态无 baseline 对账机制，非空 FAIL 项要么阻断 CI 要么被迫排除；(2) "从默认 surefire 排除恒红测试"的先例已被 F3（plan `2026-08-14-0707-3`）以"陈旧前提失效"显式废除并确立 defense-in-depth 方向——本计划**不复活该模式**；若 JUnit 防回退族在运行中出现 FAIL（发现新违规），正确行为就是 CI 红（这正是防回退语义），无需任何排除配置。**回退路径**：若"预期零命中"在 P3 快照时被推翻（防回退族出现非零命中），回 Phase 1 把该族改裁定为静态扫描器 + 模式 b，违规入 red list 交 I3' 裁决——仍零产品代码变更。
>
> **关键约束 B：模式 b（快照对账）规格。** 若新门禁初始 red list 非空，直接阻断式接入会立即打红 CI。确定解：
> - **机器可读 baseline 文件**：由 P3 产出（每门禁一个，存于 `baseline-cycle2/`（新建于 `ai-dev/audits/nop-metadata-invariants/`））。
> - **对账键 = 文件路径 + 命中行文本归一化（去首尾空白）**；**逐键比较出现次数（current ≤ baseline）**——不用行号（无关编辑导致的行号漂移不得误伤对账），不用模式粒度（同文件同类增殖必须可见），同键计数比较保证"同文件同文本增殖"可检出。键粒度与 red list 条目一一对应，支撑 I3' 逐条裁决 → I4' 逐笔修复 → baseline 逐键收缩的簿记。
> - **对账语义 = 子集（⊆，计数版）**：对每个键，当前出现次数 ≤ baseline 记录次数 → exit 0（绿）；任何键超次数或出现 baseline 外新键 → 非 0（红）。**允许命中数少于 baseline**（I4' 渐进修复期间 CI 保持绿，无需每笔修复同步收缩 baseline）。
> - **baseline 收缩规则**：baseline 只能随 I3' 裁决终态或 I4' 修复/裁定落地而收缩（每笔收缩须可追溯到裁决表或修复 commit；禁止无依据扩张——扩张 = 新命中被"合法化"，属棘轮倒退）。
> - **对账逻辑归属**：扫描器自带 `--baseline <file>` 旗标（P1 交付），聚合入口与 CI 调用带旗标形式；不在聚合脚本里另做文本比对。
> - **放行注释与 baseline 的关系**：带 `// invariant-ok: <裁决引用>` 的命中不计入对账输入（B1b 方式 (b) 的终态处置 = 放行注释，不驻留 baseline；方式 (a) 的终态处置 = 驻留 baseline。两种方式由 I3' 逐条裁定，机制均由 P1 预实现）。
> - **终态衔接（预声明，I5' 执行）**：I4' 完成后 baseline 重写为**"已批准豁免清单"**（= B1b 方式 a 的 FP 条目 + I3' 维持的优化候选条目）或清空；baseline 清空且无放行注释 → 该门禁升级为模式 a（零命中阻断式）；baseline 仅剩已批准豁免条目 → 保持模式 b 且红线语义不变（任何新增命中即红）。**经裁定的豁免条目（FP / 优化候选维持）不是终态异常，而是 baseline 的合法驻留项或放行注释条目**——这消解了"豁免残留与零命中互斥"的矛盾。
>
> **关键约束 C：模式归属表。** 每个新门禁的接入模式（a 零命中阻断式 / b 快照对账式）由 Phase 3 依据 P3 实际快照**按确定性规则逐门禁判定**（快照空 → a；快照非空 → b），不依赖 Phase 1 预判；归属表（门禁 → 模式 → baseline 状态）作为 Phase 3 checklist 交付物记录，closure audit 按表核验 CI 接线一致性。

- [x] **P4 聚合入口扩展**：`run-nop-metadata-invariants.sh` 增补新门禁序列（含退出码语义注释；模式 b 门禁以 `--baseline` 形式调用）；顺带校正脚本头部陈旧计数注释（如 "(130 catch blocks)" → 当前实测值，与 D3 口径一致）→ step [5/5] 以 `--baseline` 调用；头部注释 130 → 125 catch blocks / 45 files
- [x] **P5 CI 接线**：`invariant-gate` job 按模式归属表接入新门禁（快照空 → 模式 a 阻断式；非空 → 模式 b `--baseline` 对账式），无 `continue-on-error` / `|| true` → job 调用聚合入口（既有），gate 5 经聚合入口接入（模式 b）；workflow 零变更且核实无静默放行配置；注入 proof 证实聚合入口 exit 1 可传播至 CI
- [x] **P5b 模式归属表**：产出"门禁 → 接入模式 → baseline 状态"三列归属表（记入本 plan 或 `initial-red-list-cycle2`（.md）头部），作为 closure audit 核验 CI 接线一致性的依据 → 记入 `initial-red-list-cycle2.md` 头部（gate 5 / 模式 b / baseline 61 键 67 命中 + 确定性判定依据）
- [x] **P6 owner-doc 同步**：若门禁集合/语义有用户可见变化，更新 `docs-for-ai/` 中 invariant-guards 相关文档（含新门禁条目、模式 b 语义、运行方式）→ `invariant-guards.md`（5 门禁表 + 模式 b 专节 + 棘轮/复触发更新）+ `INDEX.md` 行同步

Exit Criteria:

- [x] `run-nop-metadata-invariants.sh` 运行覆盖 4+N 门禁，当前 codebase 上退出码 0（模式 b 门禁 = 命中集 ⊆ baseline 对账通过）→ 5 门禁全绿 exit 0（1-4 零命中；5 对账 61 键内 0 超出）
- [x] CI `invariant-gate` job 调用聚合入口且聚合入口覆盖 4+N 门禁（以接线 proof 佐证；workflow 文件如需变更则与模式归属表一致，无静默放行配置）→ workflow 调用聚合入口（零变更继承）；rg 证实无 continue-on-error/|| true；注入违规聚合入口 exit 1（set -e 传播）
- [x] 模式归属表已产出且与 CI/聚合入口实际接线一致 → `initial-red-list-cycle2.md` 头部归属表 = 聚合入口 step [5/5] `--baseline` 形式一致
- [x] **接线验证**：proof 证明新门禁在聚合入口中被真实调用（聚合入口输出含新门禁的运行记录），且注入一个 baseline 外违规样例可令对应门禁非零退出、注入 baseline 内样例不误红（注入样例 proof 后还原，最终 `git diff` 净零——模式 b 双向 proof，Cycle 1 hard-gate proof 先例）→ 聚合输出含 [5/5] 运行记录；注入 `s.toLowerCase()` → guard exit 1 + 聚合 exit 1；还原后 exit 0；既有 67 baseline 内命中不误红（对账 0 超出）；`git status --porcelain -- nop-metadata/` 空
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 保持全绿（本计划不改产品代码；JUnit 形态仅限预期零命中防回退族，无排除配置——见关键约束 A）→ BUILD SUCCESS exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 前后差分：本次修改文件 0 新增 broken link（工具为全仓运行且存在既有基线错误，故以修改前后输出差分判定，不要求全仓退出码 0）→ issue 清单与基线完全一致（16 errors / 3 warnings 不变；期间自查修复 3 处新引入引用——docs-for-ai BOUNDARY ×1、相对路径 ×1、plan 内路径反引号 ×1）
- [x] `ai-dev/logs/` 对应日期条目已更新（Phase 3 小节）

## Closure Gates

- [x] Phase 1~3 全部 Exit Criteria 勾选，各 Phase Status = completed
- [x] 5 子族 + 2 watch-only 候选全部有显式裁定，零悬挂
- [x] 新门禁（如裁定存在）已在聚合入口与 CI 中生效且可复跑
- [x] 初始 red list 快照已记录且每条含可复现证据
- [x] 不存在被静默降级的子族裁定（不可机械化 ≠ 不裁定）
- [x] 产品代码零变更（本计划性质：沉淀门禁，非修复）
- [x] 独立子 agent closure-audit 已完成并记录证据（session `ses_ffcd67978ffeKU1RIPzNfQ3gyJ`，verdict **approved**，见 Closure 段）
- [x] **Anti-Hollow Check**：closure audit 验证 (a) 新门禁在聚合入口/CI 被真实调用（非仅文件存在），(b) fixture 证明检测逻辑双向有效（含 baseline 对账与放行注释 fixture），(c) 无空方法体/静默跳过
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（本计划预期 JUnit 防回退族零命中故无排除配置；若红即防回退语义成立，按关键约束 A 回退路径处置，不得加排除配置）→ BUILD SUCCESS exit 0（无新增 JUnit、无排除配置）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0

## Deferred But Adjudicated

（本计划起草时无。Phase 1 裁定若产生 watch-only 结论，按"已裁定"记录在 invariant-catalog.md 候选节，不属于 plan-level deferred。）

## Non-Blocking Follow-ups

- 全仓门禁扩展（其他模块）—— 归各自 mission。
- 若 Phase 1 裁定某子族需 ArchUnit / pom 新依赖才能机械化 —— 结构性变更，登记后由 successor plan 评估（本计划不引入新依赖）。

## Closure

Status Note: silent-wrong-result 5 子族全部裁定为静态扫描可机械化并沉淀为单门禁 `check-silent-wrong-result.mjs`（1 扫描器 × 5 规则），初始 red list 67 命中/61 键快照双层记录（`initial-red-list-cycle2.md` + `baseline-cycle2/silent-wrong-result.json`），聚合入口 4→5 门禁（模式 b baseline 对账），CI invariant-gate 经聚合入口阻断式继承，产品代码零变更（修复/裁决归后继 plans 0820-2/0820-3）。独立 closure audit verdict = approved（0 Blocker / 0 Major；4 Minor 已在收口同 commit 修复：red-list/target-set 键数与行数口径 3 处 + scanner bigdec 未平衡括号 INTERNAL 对齐）。
Completed: 2026-08-15

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure auditor 子 agent（fresh session，review-only 零文件修改），session `ses_ffcd67978ffeKU1RIPzNfQ3gyJ`
- Evidence:
  - Phase 1 Exit Criteria 6/6 PASS（5 条新 INV 四要素齐全 catalog:135/152/168/185/202；目标集 live 复跑 rg locale 41 原始 = 40+javadoc 伪站点、catch 126 = 125+1，与 §5/§1.3 一致；watch-only 候选 2/2 显式裁定）
  - Phase 2 Exit Criteria 9/9 PASS（fixture 18/18 exit 0；hits=67 双跑一致；逐规则 40/0/19/6/2 = 目标集权威计数；对账键 = path+family+text 非行号；产品 `git status --porcelain -- nop-metadata/` 空）
  - Phase 3 Exit Criteria 7/7 PASS（聚合入口 5 门禁 live 运行 exit 0、gate5 对账 61 键内 0 超出；CI maven.yml:45-73 无 continue-on-error/|| true；模式归属表与接线一致；注入 proof 双向——临时收缩 baseline → exit 1 + EXCESS，全量 baseline → exit 0（等价复现，未触产品代码）；mvn test BUILD SUCCESS；doc-links 16 errors 与基线一致）
  - Closure Gates 11/11 PASS（含本 audit 自身；checklist 工具复跑 exit 0；hollow 扫描 0 findings exit 0）
  - Anti-Hollow：(a) 聚合入口 script:67 真实调用 `--baseline` 形态、CI 调用聚合入口、端到端运行含 gate5 输出；(b) fixture 双向 18/18 含 baseline ⊆绿/超红/新键红/收缩绿 + 放行注释 violation=0/allowed=1 显式列出；(c) 无空方法体/静默跳过（baseline 缺失 exit 2、括号失衡 INTERNAL、watch 边界文档化）
  - Spot-check：red-list 抽查 3 条（SqlColumnLineageExtractor:157 / LineageTagPropagationProcessor:85 / MemoryOrderByComparator:132）与 live 代码逐一吻合；受保护形态 AggregationHelper:551-554 longValue 路由确认排除；MetaContractChecker:384/:395 括号形态确认不命中
  - Deferred 项分类检查：Non-Blocking Follow-ups 仅含全仓扩展（归各 mission）与 ArchUnit 依赖评估（结构性变更 successor plan）——无 in-scope live defect 被降级

Follow-up:

- 全仓门禁扩展（nop-metadata 之外）—— 归各自 mission。
- 若后续子族需 ArchUnit / pom 新依赖机械化 —— successor plan 评估（本计划零新依赖）。
- 后继：`2026-08-15-0820-2`（I2'+I3' 正式 red list + 裁决，消费本计划 `initial-red-list-cycle2.md` / `baseline-cycle2/`）、`2026-08-15-0820-3`（I4'~I6' 修复与收口）。
