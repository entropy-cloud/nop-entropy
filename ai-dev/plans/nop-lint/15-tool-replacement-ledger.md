# 15 工具替代统一账本：防腐门禁 + 骨架修正 + design 12 收口

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: [工具替代 roadmap item 1](../../backlog/nop-lint-tool-replacement-roadmap.md)
> Related: [roadmap](../../backlog/nop-lint-tool-replacement-roadmap.md) · [统一账本](../../../nop-lint/docs/tool-replacement-ledger.md) · [design 12](../../design/nop-lint/12-check-scripts-migration-manifest.md)

## Purpose

把 roadmap item 1 收口：统一工具替代账本 `nop-lint/docs/tool-replacement-ledger.md` 获得防腐门禁脚本（终裁词表 + 分面三轴 enum-set + self-test 正控 + 逐工具终裁行回填机制），账本骨架与 roadmap 的漂移全部修正，design 12 汇总行与逐行状态的不一致修正且其门禁转绿。本计划完成后 roadmap item 1 可标 `done`，后续 wave 的终裁行回填有了机械化纪律。

## Current Baseline

- `nop-lint/docs/tool-replacement-ledger.md` 骨架已存在（随 roadmap commit 388584aec1 建立），与 roadmap 的漂移清单（实测逐项核实）：
  - **词表漂移**：终裁词表行写 `replaced` / `replaced-partial` / `keep-tool` / `out-of-scope`；roadmap §工具级终裁词表定义的是 `core-face-replaced` / `replaced-partial` / `keep-tool` / `out-of-scope`——首值漂移。
  - **item 编号漂移（4 处）**：头注写"终裁汇总出口（roadmap item 17）+ out-of-scope 记录承载（roadmap item 16）"（应 20 / 19）；`## 工具级终裁表` 节标题写"由 roadmap item 17 回填"（应 20）；`## out-of-scope 记录` 节标题写"由 roadmap item 16 落稿"（应 19）。
  - **终裁表 8 行引用漂移**：`roadmap items` 列写 Checkstyle→2、PMD→3、mjs→4、SpotBugs→5–7、Sonar→8–11、EP→12–13、NullAway→14、ArchUnit→15；现行 roadmap 各工具的专属收口 item 为 Checkstyle→3、PMD→4、mjs→5、SpotBugs→9–11、Sonar→12–14、EP→15–16、NullAway→17、ArchUnit→18。
  - **终裁表 5 行 Wave 标注漂移**：终裁列写 `待裁（Wave N …）`，其中 SpotBugs 标 Wave 2（应 3）、SonarQube 标 Wave 3（应 4）、ErrorProne 标 Wave 4（应 5）、NullAway 标 Wave 4（应 5）、ArchUnit 标 Wave 5（应 6）。处置裁定：**终裁列的 Wave 标注整体移除**而非修正——roadmap Hard constraint 7 规定 wave 进度由 roadmap 跟踪、账本管行级状态，禁止双写；本次漂移正是双写的后果。Wave 归属由 roadmap items 列回查。
  - **账本无分面登记节**：roadmap item 2 要求"分面表落统一账本"，账本需先有可被门禁看守的承载结构。
- roadmap item 1 明确要求顺带修正 design 12 汇总行不一致："逐行实况 migrated-pending-switchover 5 / candidate 2 / deferred 3；汇总写 3/5/2——门禁若放行该汇总则门禁同修"。
- 实测（2026-09-28）：`node ai-dev/tools/check-lint-migration-manifest.mjs` **当前 FAIL，5 处违规**：
  - 3 处汇总行：逐行计数为 maintain-mjs 7 / exclude 7 / migrated-pending-switchover 5 / candidate 2 / deferred 3（合计 24），design 12 §逐脚本账本末尾汇总行写 3/5/2——**门禁没有放行该汇总**（判定与 roadmap 括号中"若放行"分支相反：汇总行须修，门禁 summary 判定逻辑本身无需改）。
  - 2 处 enum-set：`check-lint-coverage-manifest.mjs`、`check-lint-tool-migration-mapping.mjs` 两个后建门禁脚本是 live `check-*.mjs` 但在 design 12 无账本行——该门禁只豁免它自己（`check-lint-migration-manifest.mjs:279` 的 `GATE_SCRIPT` 单名排除）。这两个脚本是 lint 迁移工程自建的门禁工具，不是 legacy 迁移目标，与 24 行账本语义不同类。
- 另两本账本门禁实测绿：`check-lint-tool-migration-mapping.mjs`（26 行）与 `check-lint-coverage-manifest.mjs`（186 entries）均 exit 0（后者依赖 `ai-dev/tools` 下 `pnpm install` 安装 js-yaml——本 worktree 环境已装，属环境前置非代码缺陷）。
- 防腐门禁脚本风格先例：`check-lint-migration-manifest.mjs`（checker 导出 + known-bad fixture 正控 self-test + main 三段）。该先例 parser 对表格行用裸 `split('|')` 切列——单元格内含竖线会错位并触发列数违规（响亮失败，非静默）。
- `node ai-dev/tools/check-doc-links.mjs --strict` 当前 exit 0。
- 仓库无 `check-lint-tool-replacement-ledger.mjs`；新增该名字会命中 migration-manifest 门禁的 enum-set 检查，须与 GATE 排除口径一并处置（Phase 3）。

## 账本目标格式规格（Phase 1 产出 / Phase 2 checker 消费的契约）

为消除"执行者自行发明格式"的风险，两个 Phase 之间的数据契约在此钉死：

1. **工具级终裁表**：固定 8 行，工具名钉死为精确串集合：`Checkstyle 10.21.1`、`PMD 7.26.0`、`check-\*.mjs ×24`、`SpotBugs 4.9.8.3`、`SonarQube`、`ErrorProne（未接线）`、`NullAway / 空类型系统族`、`ArchUnit`（不多不少）。五列：工具 | 终裁 | 残余范围 | 证据 | roadmap items。
   - 终裁列 = 裸 enum 值，无括号注记：`待裁` 或 4 终裁值之一。
   - `roadmap items` 列文法 = 单个数字或连续区间（`3`、`9–11`），regex `^\d+(–\d+)?$`。
   - 回填机制门：终裁 ≠ `待裁` 时，证据列须含至少一个仓内文档锚链接（`./` 或 `../` 开头的 markdown 链接），且残余范围非 `—`（`out-of-scope` 行写划出面清单或 `全工具`）——无证据终裁不可入库（roadmap Hard constraint 3 机械化）。
2. **分面裁定登记节**：节标题固定 `## 分面裁定登记`；节内含词表定义（4 值：`core` / `out-of-purpose` / `out-of-principle` / `out-of-scope`，定义与 roadmap Purpose 分面表一致）+ 逐工具分面表（五列：工具 | 分面标注 | 依据 / 证据 | 重估触发 | 状态；行 = 上述 8 工具精确串）。
   - 分面标注列 = `/` 分隔的 enum token 串或占位 `待裁`；checker 逐 token 校验 enum。第 5 列"状态"scaffold 值同为 `待裁`（回填时记 `landed`，本计划不设 checker 看守该列）。
   - roadmap item 2 措辞中的 "optional" 分面落账本时归 `out-of-purpose` 轴（本计划裁定，避免 item 2 回填撞词表）。
3. **书写纪律**：账本所有表格单元格内禁止 `|` 与 `\|` 字符（分隔用 `/` 或 `；`），由 checker 列数校验天然看守；纪律写进账本头部。

## Goals

- 新门禁脚本 `ai-dev/tools/check-lint-tool-replacement-ledger.mjs` 守护 `nop-lint/docs/tool-replacement-ledger.md`：终裁词表 enum-set（`待裁` 过渡态 + 4 终裁值）、分面 enum-set、逐工具终裁行回填机制（终裁已定时证据/残余范围强制）、known-bad 正控 self-test。
- 统一账本骨架与 roadmap 全面对齐（上节漂移清单逐项修正）+ 按目标格式规格落分面登记节 + 声明门禁接线。
- design 12 收口：汇总行改为逐行实况 7/7/5/2/3；migration-manifest 门禁 GATE 排除口径扩为 `check-lint-*.mjs` 门禁族（含本计划新增的 ledger 门禁），design 12 增注该口径；统一账本中 design 12 不一致注记同步消解。
- 全部门禁（新旧）self-test + 主检查转绿；roadmap item 1 标 `done`。

## Non-Goals

- 不回填任何工具的终裁结论与分面标注（Wave 1–6 各 item 的职责；本计划只建机制，终裁/分面保持"待裁"）。
- 不落 item 2 的 62 条规则分面表内容（只建账本承载结构与 enum 看守）。
- 不改 design 12 任何行级状态（24 行逐行状态与切换计划不在本计划；migrated-pending-switchover 的切换归 item 5）。
- 不动 `checkstyle-pmd-migration.md` 与 coverage manifest 两本账本及其门禁。
- 不改 design 12 的状态词表（无新增状态值；`switched-over` 等 item 5 落地时再加）。

## Scope

### In Scope

- 新增 `ai-dev/tools/check-lint-tool-replacement-ledger.mjs`（含 self-test 正控）。
- 修改 `nop-lint/docs/tool-replacement-ledger.md`（词表/编号/Wave 标注修正 + 分面节 scaffold + 门禁声明 + design 12 注记同步）。
- 修改 `ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md`（汇总行 + 门禁排除口径增注 + Last Reviewed）。
- 修改 `ai-dev/tools/check-lint-migration-manifest.mjs`（GATE 排除口径扩为 `check-lint-*.mjs` 族并抽为可导出判定函数 + self-test 增补）。
- 修改 `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md`（item 1 状态翻转）。
- `ai-dev/logs/2026/09-28.md` 日志更新。

### Out Of Scope

- 任何终裁结论回填、任何 mjs 脚本切换、任何规则落地。
- `docs-for-ai/` 更新——显式裁定：No owner-doc update required（`docs-for-ai/` 不承载工具替代账本，账本属 ai-dev 开发过程资产）。

## Execution Plan

### Phase 1 - 统一账本骨架修正与分面承载结构

Status: completed
Targets: `nop-lint/docs/tool-replacement-ledger.md`、`ai-dev/backlog/nop-lint-tool-replacement-roadmap.md`（仅 item 1 状态行）

- Item Types: `Fix`

- [x] 终裁词表行修正：`replaced` → `core-face-replaced`，词表 4 值与 roadmap §工具级终裁词表逐字一致并逐值附定义摘要
- [x] item 编号漂移修正（4 处）：头注 2 处（17→20、16→19）+ `## 工具级终裁表` 节标题（17→20）+ `## out-of-scope 记录` 节标题（16→19）
- [x] 工具级终裁表按"账本目标格式规格"重构：8 行工具名钉死；终裁列规范化为裸 `待裁`（移除全部 `（Wave …）` 注记——wave 进度归 roadmap，禁止双写）；`roadmap items` 列改为专属收口 item 文法（Checkstyle→3、PMD→4、mjs→5、SpotBugs→9–11、Sonar→12–14、EP→15–16、NullAway→17、ArchUnit→18）
- [x] 新增 `## 分面裁定登记` 节（按"账本目标格式规格"第 2 条：4 值词表 + 五列逐工具分面表，8 行全部 `待裁` 占位）
- [x] 账本头部 blockquote：防腐门禁命令声明（主检查 + self-test 两口径）+ 表格单元格禁 `|` 书写纪律；`Status: scaffold` 行同步更新为"门禁在档、终裁/分面待回填"口径
- [x] 行级账本索引表中 design 12 注记"（**文档分类汇总行写 3/5/2，与逐行不一致——roadmap item 1 修正**）"暂保留，Phase 3 修正 design 12 后同步消解（见 Phase 3 checklist）
- [x] `ai-dev/backlog/nop-lint-tool-replacement-roadmap.md` item 1 状态 todo→planned（本 plan 过 draft review 后立即翻转；`done` 必须等独立 closure audit 之后，见 Closure Gates）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 账本终裁词表 4 值与 roadmap 逐字一致；`replaced` 作为独立 token 在账本中不复存在（`core-face-replaced` / `replaced-partial` 中的子串不算）
- [x] 账本内全部 roadmap item 引用（头注 2 处 + 两个节标题 + 终裁表 8 行）与现行 roadmap 一致，无旧编号残留
- [x] 终裁表 8 行终裁列均为裸 `待裁`，无 Wave 注记；`roadmap items` 列符合文法 `^\d+(–\d+)?$` 且值为专属收口 item
- [x] `## 分面裁定登记` 节存在：词表 4 值与 roadmap Purpose 分面表一致，逐工具分面表 8 行（工具名精确串齐全）且分面标注均为 `待裁`
- [x] 门禁声明与书写纪律存在于账本头部
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0（账本内链接不破）
- [x] No owner-doc update required（`docs-for-ai/` 不承载工具替代账本）
- [x] `ai-dev/logs/2026/09-28.md` 对应条目已更新（随 Phase 3 一并写；实际已于 Phase 3 完成时写入）

### Phase 2 - 新门禁脚本 check-lint-tool-replacement-ledger.mjs

Status: completed
Targets: `ai-dev/tools/check-lint-tool-replacement-ledger.mjs`

- Item Types: `Proof`

- [x] 实现 checker 集（导出函数，风格对齐 `check-lint-migration-manifest.mjs`，parser 按"账本目标格式规格"实现）：
  - 终裁表解析与钉名：8 行工具名精确串集合匹配（多行/少行/改名均 hard error），五列结构完整
  - 终裁词表 enum-set：终裁值 ∈ {`待裁`, `core-face-replaced`, `replaced-partial`, `keep-tool`, `out-of-scope`}
  - 回填机制门：终裁 ≠ `待裁` 时，证据列含 `./` 或 `../` markdown 锚链接且残余范围非 `—`，否则 hard error
  - `roadmap items` 列文法：`^\d+(–\d+)?$`
  - 分面登记节解析与分面 enum-set：`## 分面裁定登记` 节下逐工具分面表的分面标注列逐 token 校验 ∈ {`core`, `out-of-purpose`, `out-of-principle`, `out-of-scope`} 或占位 `待裁`；节/表缺失 = hard error
  - self-test 正控：每个 checker 喂 known-bad fixture 必须拒绝（非法终裁值 / 无证据终裁 / 非法 items 文法 / 非法分面 token / 钉名集合破坏 / 缺列缺节），拒绝不了的视为门禁失效
- [x] main 三段口径：无参 = 全部检查；`self-test` = 仅正控；exit 0/1/2 语义与先例一致

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `node ai-dev/tools/check-lint-tool-replacement-ledger.mjs` exit 0（对 Phase 1 修正后的账本全绿，8 行全部"待裁"合法）
- [x] `node ai-dev/tools/check-lint-tool-replacement-ledger.mjs self-test` exit 0（正控全过）
- [x] 负控人工验证一次：临时把账本某行终裁改为无证据的 `core-face-replaced`，主检查必须 exit 1 且报对应违规（验证后还原，还原后复跑 exit 0）——实测：2 条违规（无证据锚 + 空残余范围）均触发，还原后 exit 0
- [x] 无静默跳过：每个 checker 对解析不到目标节/表头时显式报错退出非 0（Minimum Rules #24）
- [x] 无新增运行时依赖（纯 node:fs/node:path/node:url，先例同）
- [x] No owner-doc update required（工具脚本，无契约面）

### Phase 3 - design 12 汇总收口 + migration-manifest 门禁排除口径修复

Status: completed
Targets: `ai-dev/design/nop-lint/12-check-scripts-migration-manifest.md`、`ai-dev/tools/check-lint-migration-manifest.mjs`、`nop-lint/docs/tool-replacement-ledger.md`

- Item Types: `Fix`

- [x] design 12 汇总行修正为逐行实况：`maintain-mjs` 7 + `exclude` 7 + `migrated-pending-switchover` 5 + `candidate` 2 + `deferred` 3 = 24
- [x] `check-lint-migration-manifest.mjs` GATE 排除口径：单名排除（`GATE_SCRIPT` 常量比较）改为可导出判定函数（`isLintGateScript`，匹配 `check-lint-*.mjs` 门禁族），`main` 的 liveScripts 过滤改调该函数；self-test 直接调用该函数增补正反用例——执行增注：main 侧 liveScripts 筛选同抽为可导出 `selectMigrationTargetScripts(names)` 一并直测（首轮 self-test 用例误把纯函数 `checkEnumSet` 当排除逻辑测，被 self-test 自身拒绝后修正——排除逻辑测试必须落在真实筛选单元上）
- [x] design 12 数字口径段增注：`check-lint-*.mjs` 门禁族（migration-manifest / tool-migration-mapping / coverage-manifest / tool-replacement-ledger）是 lint 迁移工程自建防腐工具，不入 24 行 legacy 迁移目标枚举
- [x] design 12 头部 `Last Reviewed` 更新为 2026-09-28
- [x] 统一账本行级账本索引表中 design 12 的"汇总行不一致待修正"注记同步消解（改为已修正口径），与 design 12 新汇总行一致
- [x] roadmap item 1 维持 `planned`（done 翻转属 Closure 动作，见 Closure Gates）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `node ai-dev/tools/check-lint-migration-manifest.mjs` exit 0（5 处违规全消，输出 `24 ledger rows == 24 live check-*.mjs scripts`）
- [x] `node ai-dev/tools/check-lint-migration-manifest.mjs self-test` exit 0（含新增正反用例）
- [x] design 12 逐行状态零改动（git diff 证实：仅 Last Reviewed、枚举排除口径增注段、汇总行三处变化，24 行状态列逐字未动）
- [x] `node ai-dev/tools/check-lint-migration-manifest.mjs` 复跑输出中不含 `check-lint-tool-replacement-ledger.mjs`（修复前该名在 enum-set 违规行出现，修复后 24==24 全绿无任何违规行）
- [x] `node ai-dev/tools/check-lint-tool-replacement-ledger.mjs` 主检查 + self-test 仍 exit 0（Phase 2 成果未回破）
- [x] 统一账本中不再有"design 12 汇总行写 3/5/2 不一致"的陈旧宣称
- [x] No owner-doc update required（ai-dev 过程资产修正）
- [x] `ai-dev/logs/2026/09-28.md` 已记录三项修正与门禁转绿证据

## Closure Gates

> 纯文档 + 门禁脚本计划，无产品代码变更：`./mvnw compile/test` 不适用，显式免除（guide Closure Gates 免责条款）。

- [x] Phase 1–3 全部 Exit Criteria 勾选完毕
- [x] 四本 lint 账本/manifest 门禁全绿：tool-replacement-ledger（新）、migration-manifest、tool-migration-mapping、coverage-manifest 的主检查 + self-test（先例三本均有 self-test 口径）全 exit 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] roadmap item 1 状态 `done`——**仅在独立 closure audit 完成并写入下方 Closure 证据后翻转**（roadmap 状态词表语义：done = 通过独立 closure audit）；且 roadmap 中无与本计划产出矛盾的状态文本（audit Major-1 要求的 L37/L120 两处陈旧宣称已随收口一并修正）
- [x] 无被静默降级的 in-scope live defect（design 12 汇总不一致与门禁红均已修复，非 deferred）
- [x] No owner-doc update required（`docs-for-ai/` 不承载工具替代账本；显式裁定）
- [x] 独立子 agent closure audit 完成且证据写入本计划 Closure 段
- [x] Anti-Hollow Check：closure audit 确认（a）新门禁对账本的真实违规能拒绝（负控验证记录在案），（b）无 stub/no-op checker（self-test 正控全过 + 抽查 checker 实现非空）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/15-tool-replacement-ledger.md --strict` exit 0

## Deferred But Adjudicated

（无——本计划无延期项）

## Non-Blocking Follow-ups

- `check-lint-coverage-manifest.mjs` 依赖 `ai-dev/tools` 下 `pnpm install`（js-yaml/tree-sitter 等声明于该目录 package.json）：worktree 环境前置，非代码缺陷，不入本计划。
- design 12 §已迁移待切换项的下线计划等叙述段与 Wave 1 item 5 执行时的账本消费，归 item 5 plan 处置。

## Closure

Status Note: 三个 Phase 全部完成且经独立 fresh-session 子 agent closure audit 逐条 live 复核通过（含审计员自行复做的负控验证），四本 lint 门禁全绿，design 12 汇总不一致与门禁红两个 in-scope live defect 已修复，roadmap item 1 已翻 `done`。本计划关闭。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_28e0e96c-7372-449a-963c-94ede2504fba（fresh session，与本实现会话无关）
- Audit Session: agent_28e0e96c-7372-449a-963c-94ede2504fba
- Evidence:
  - Phase 1 Exit Criteria 8/8 PASS：账本词表与 roadmap 逐字一致（grep 独立 token `replaced` 零命中）、item 编号漂移 4 处清零、终裁表 8 行裸 `待裁` + items 列 3/4/5/9–11/12–14/15–16/17/18、分面节 4 值 + 8 行待裁、头部门禁声明与书写纪律在档、doc-links strict 0 errors
  - Phase 2 Exit Criteria 6/6 PASS：新门禁主检查 + self-test exit 0；审计员自行复做负控（`待裁`→无证据 `keep-tool`）→ exit 1 报 2 违规（无证据锚 + 空残余范围），还原复跑 exit 0；解析失败路径显式 exit 非 0；零新增运行时依赖
  - Phase 3 Exit Criteria 8/8 PASS：migration-manifest 门禁 24==24 全绿（输出不含新门禁名违规行）+ self-test exit 0；git diff 证实 design 12 恰 3 hunk（Last Reviewed / 枚举排除口径增注 / 汇总行 3/5/2→7/7/5/2/3），24 行状态列零改动；`isLintGateScript` + `selectMigrationTargetScripts` 真实导出且 main 调用、self-test 正反用例直测；族排除无误伤（28 − 4 = 24）
  - 四门禁联跑全 exit 0（ledger 新门禁、migration-manifest、tool-migration-mapping 26 行、coverage-manifest 186 entries）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/15-tool-replacement-ledger.md --strict` 退出码 0（收口后无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查：新门禁 359 行实质实现（parser + 双 enum-set + 回填机制门 + items 文法 + 钉名集合），13 个 known-bad fixture 正控全拒 + 4 正控全收；负控实测证明对真实违规能拒绝
  - Deferred 项分类检查：Deferred But Adjudicated 为空；两条 follow-up（pnpm install 环境前置、design 12 下线叙述归 item 5）经审计确认均 non-blocking，无 in-scope live defect 被降级
  - 审计发现的 Major-1（roadmap L37/L120 两处"汇总行不一致待 item 1 修正"陈旧宣称在收口时失真）已随 roadmap item 1→done 同一编辑修正，Closure Gate 对应条款满足

Follow-up:

- `check-lint-coverage-manifest.mjs` 的 worktree 环境前置（`ai-dev/tools` 下 pnpm install），见 Non-Blocking Follow-ups 首条。
- design 12 下线计划与 switchover 消费归 roadmap item 5 plan。

