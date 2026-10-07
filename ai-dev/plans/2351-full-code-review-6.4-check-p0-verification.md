# 2351 full-code-review 6.4 check 系列 P0×36 逐项复核标定

> Plan Status: completed
> Last Reviewed: 2026-10-07
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 6.4（MA6）；Skill=`ai-dev/skills/audit-remediation-verification-prompt.md`
> Related: Owner=`ai-dev/audits/check/SUMMARY.md`（36 P0 清单，安全类 9 + 数据正确性 16 + 流处理/状态类 4 + 功能完全失效类 7；**非绝对权威——草审 F1 坐实 SUMMARY 自身条目身份缺陷**：其 #6 行文字〔indexDir 任意路径〕在源报告 check/nop-search.md 定级为 P1，该单元真 P0 = 数值/path filter〔:19，批次3 `0b7fc8f903` 修复〕——Phase 1 对账必须逐行核对 SUMMARY↔源报告定级）；Roadmap Details：「check 系列 36 个 P0 逐项标定：still-live / already-fixed / false-positive / duplicated；check/SUMMARY 记录部分 P0 曾在 fix-ai-check 分支处置——标定时以该处置记录为输入、**以 master 当前代码为准**复核 live-ness；仍 live 的全部进入 R1.0 输入」。
> 机械基线（主 agent 实测，2026-10-07，读 check/SUMMARY.md + Draft Review agent_557da897 git 层实测勘正）：36 P0 全量清单在案；**fix-ai-check 分支已实 merge 入 master**（草审实测：四批次 commit `6410a6bfe0`〔#10-12/30/35/36〕/`e6010dc6eb`〔#13-20〕/`0b7fc8f903`〔#1-5/8/9+search filter〕/`2de622fb6a`〔#23-29/32-34〕对 master 均 is-ancestor=true〔commit 可达 master〕，merge commit `7348fbc6a5`/`c69bbf343c` 在案）——36 条默认走向 already-fixed，6.4 核心工作 = 逐条**亲证**修复在位而非信任处置记录。**上游轮内已标定映射（草审 F2 勘正后）**：#1→3.10（:23 P0 双确认 + TestLoginCredentialCheckWhenLockoutDisabled 4 绿）、#4→3.9（check 台账 row1 :23 already-fixed + DefaultAssemblyTest）、#5→3.11（ThoughtStorage guard 防御在位）、#6→**4.4 S-06 保留 P1 live**（checkAllowAccess :135-137 空方法体、setResourceLocator 零 wiring——4.4 终判在先，本项承接其 P1 live 结论）、#14→2.3 旁证（16 类映射在场；注意 2.3 双探针系 check2 条目非 check #14）、#15/16/17→3.2（:11 P0×3 already-fixed）、#18→3.5（:19/:51 isExpired+4 回归测试）、#21→3.8（:27 TestTccRunner 10/10）、#26-29→**无轮内标定，唯一真实输入 = commit `2de622fb6a` + live 代码**（2.8 报告未审计此四位点，虚锚已除）——归净新、#30→**2.3 无标定**（其 JdbcBatcher 实跑系 check2 条目——check #30 为独立净新）、#32/33/34→3.9（NET 族三 P0 already-fixed 实测 :21/:45/:70）、#35/36→4.5（:24/:100-105 P0×2）。#13→**2.1 无标定**（其标定系 check2 游标条目，同域异条——check #13 净新）、#2/#3/#7/#8/#9 净新。**精确粗判：轮内已标定 ≈15 条（#1/#4/#5/#6/#15/#16/#17/#18/#21/#32/#33/#34/#35/#36 + #14 旁证，以 Phase 1 对账落盘为准），净新 ≈20-21（#2/#3/#7-#13/#19/#20/#22-#31 中无轮内标定者）。**
> 只读审计（P0 即时通道语义在此反转：本项发现的 still-live P0 = 直接移交 R1.0 输入清单，不就地修复——roadmap 6.4 原文）。**执行分支口径**：live-ness 以当前工作树（audit/full-code-review）为读、以 master 可达性为锚（草审实测四批次 commit 同时可达两分支）。
> **方法学裁定**（草审 F3/F4 勘正后）：① 标定四态（roadmap）+ **partially-fixed 映射规则**（如 #3 ai-rest 处置自带「设计级变更暂缓」：暂缓残余 → still-live 附 residual 注记进 R1.0）；「修复待验证」仅为台账中间态，Phase 2 必须收敛为四态之一；② 判定证据标准：亲读 master 位点代码 + **处置记录点名测试类实跑**（SECURITY 9 条强制实跑；其余抽验条目强制实跑——3.5 P2-3.5-010「测试在场且绿但保护力为零」反面教训，仅读断言不判 already-fixed）；③ 轮内已标定条目抽验 30% 防漂移（重验 live-ness 不重开判级）；④ SECURITY 类（#1-#9）优先且全量亲读+实跑；⑤ **对账硬步骤**：Phase 1 宇宙表逐行核对 SUMMARY↔源报告 P0 定级（F1 缺陷防范），差异行登记勘误。
> Draft Review: agent_557da897-fbf6-48a2-a044-1184898220f9 首轮 1 Blocker（F1 SUMMARY #6 条目身份错误——indexDir 系 P1、真 P0=数值 filter、4.4 S-06 P1 live 终判在先——对账硬步骤+预登记修复）+ 3 Major（F2 撞车映射三错配四遗漏——#30/#13 剔除、#26-29 虚锚除、#1/#4/#5/#6 补映射、撞车面补 3.1/3.10/3.11/4.4、粗判重算/F3 partially-fixed 无落点——映射规则写死/F4 读断言不足判 already-fixed——SECURITY+抽验强制实跑）+ 5 Minor 全修（F5 agent 均衡规则/F6 still-live ≥9 行歧义/F7 降 P2 台账+终裁归属/F8 finding ID 方案/F9 执行分支口径）——本轮全部修复，待聚焦复核。

## Purpose

执行 roadmap 工作项 6.4：check 系列 36 个 P0 逐项标定（四态），产出报告 `6.4-check-p0-verification.md`，仍 live 者全部进入 R1.0 输入。

## Current Baseline

- 36 P0 清单 + fix-ai-check 处置记载见头部注记（check/SUMMARY.md 为权威输入，各单元报告条目末尾「处置（fix-ai-check 分支）」标注为逐条输入）。
- 轮内撞车先例 ≈15 条（净新 ≈20-21，见头部注记映射），Phase 1 精确对账。
- 绿色基线：编制期全仓 BUILD SUCCESS；本项只读（含测试运行——跑单测验证红测试为只读副作用）；工作树在前序收口 commit 后干净。
- 撞车面（Phase 2 必查，F2 勘正后）：2.3/3.2/3.5/3.8/3.9/4.5（轮内标定引用——注意 2.3/2.1 的 JdbcBatcher/游标标定系 check2 条目，勿混引 check 条目）；**3.1/3.10/3.11/4.4**（#1/#5/#6 轮内标定真实承载面——#6 以 4.4 S-06 P1 live 终判为输入；#2 为 3.1 交叉核对承载面，无轮内标定）；**2.8 不在列**（其未审计 check #26-29；该四条输入=commit `2de622fb6a`+live 代码）；7.2 未执行不等待；6.1/6.2（still-live 位点同型交叉引用）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/6.4-check-p0-verification.md`：摘要 + 36 行标定台账（编号/模块/原问题/四态标定/证据/处置去向）+ 仍 live 清单 + 驳回台账 + 裁决。
- **36 条全量标定**（三分工作流，F5 均衡规则：净新条目按宇宙表 ≈ 均分给三 agent，不打类标签；SECURITY 类另行全量）：
  1. **轮内已标定条目（≈15，映射见头部）**：引用轮报告结论 + 抽验 ≥30% live-ness 防漂移（含处置记录点名测试类实跑）。
  2. **净新复核条目（≈20-21）**：逐条亲读 master 位点 + **处置记录点名测试类强制实跑**（SECURITY 外亦然——3.5 P2-3.5-010 反面教训；测试类不在 master 或跑红 = 降「修复待验证」中间态进 Phase 2 收敛）。
  3. **SECURITY 类 #1-#9 全量亲读+实跑**（优先级最高；#1/#4/#5 已有轮内标定——抽验+实跑；#6 承接 4.4 S-06 P1 live 终判——验证位点现状后按 4.4 结论登记）。
- **finding ID 方案（F8）**：still-live 者新编 `P0-6.4-<序号>` 登记 index「P0 发现追踪」表，同时保留 check 原编号（#NN）溯源；降级 P1 者编 `P1-6.4-<序号>` 进 P1 汇总；降 P2 者开辟 P2 裁决例外登记台账（F7）。判级终裁归属主 agent（复核 agent 提议）。
- still-live 者逐条附 R1.0 输入条目（问题/位点/修复方向/严重级复核——check 时代 P0 按当前共享前缀复核，可能降级 P1〔进 P1 汇总〕或 P2〔开辟 P2 裁决例外，F7〕；判级终裁=主 agent）；index「P0 发现追踪」表登记（**按 roadmap 6.4 定义仅移交 R1.0 不就地修复**，登记表仅记标定结果）。
- index 登记 + 独立 closure audit + roadmap 6.4 翻 done（closure audit 先于翻 done）。

## Non-Goals

- P1 抽样标定（6.5-6.8）；P2/P3 归档（6.9）；still-live 修复执行（R1.x）；stream 域等待 7.2。

## Scope

### In Scope

- check/SUMMARY.md 36 P0 全量四态标定 + 仍 live 清单
- 只读输入：check/ 各单元报告（fix-ai-check 处置标注）、轮内深审报告标定、git 历史（fix-ai-check 分支处置的 merge 状态——`git log`/`git branch --contains` 验证修复 commit 是否可达 master）

### Out Of Scope

- P1/P2/P3 标定；修复执行；7.2 域等待。

## Execution Plan

### Phase 1 - 对账与三分标定（3 agent 并行）

Status: completed
Targets: 36 P0 位点（master live code + check/ 处置记录）

- Item Types: `Proof`

- [x] 动手前基线确认：本 plan 已提交（12e4072bec 前序）、`git status` 干净后开始
- [x] 主 agent 对账落盘：36 行宇宙表 `_tmp/fcr-6.4-universe.md`（F1 列=源报告定级核对待 agent 填；#6 异常预登记；四批次 commit 锚在列；分配=Agent 1 SECURITY 9 / Agent 2 净新 9 / Agent 3 净新 7+抽验 5+#14 旁证）
- [x] Agent 1「SECURITY #1-9 全量亲读+实跑 + 净新均分份 A」：9 行 → already-fixed 8 目 / still-live 2 目（#3 residual P0→P2 降档〔主向量闭合 ticket fail-closed + 设计级暂缓残余〕/#6a 拆目 P1 承接 4.4 S-06——真 P0 #6b filter already-fixed 9/9 绿）/ false-positive-as-P0 1 目（#6a SUMMARY 定级错位证实）；实跑 12 次全成功 58 tests 全绿零降级 → `_tmp/fcr-6.4-checkp0-a1.md`
- [x] Agent 2「净新均分份 B」：#10-13/19/20/22-24 九条全部 already-fixed（位点全为修复后形态；实跑 9 组全绿零降级；F1 九条源报告定级全一致）；勘误 3 条（#22 锚 B4?→46575c4278/#13 相邻未决 findPrevSql 空 filter 已在 SUMMARY 超审计节/#22 consumer 10→11）→ `_tmp/fcr-6.4-checkp0-a2.md`
- [x] Agent 3「轮内已标定抽验 ≥30%（含实跑）+ 净新均分份 C」：净新 7 条（#25-31）全部 already-fixed（11 组测试实跑全绿；#26-29 严格以 2de622fb6a 为唯一输入）；抽验 5/5（#15/#18/#21/#33/#35）与轮内标定全一致（#18 生产谓词亲读正确 + P2-3.5-010 收窄成立）；#14 旁证成立；交叉发现 nop-sys-dao 测试编译断裂系 3.5 已归因 R1.x 已知项非新发现 → `_tmp/fcr-6.4-checkp0-a3.md`
- [x] 主 agent 抽查质量：7 条亲验全证实〔closure audit R3 裁定注记：7<8 缺口由 Phase 2 独立复核 11 抽验+6 补盲（含 SECURITY #1/#4/#7）合并计满足〕+ 台账对账 36 行零遗漏
- [x] 产物 `_tmp/fcr-6.4-checkp0-a1/a2/a3.md`（三 agent 分报告）+ 36 行标定台账（宇宙表 `fcr-6.4-universe.md`）

Exit Criteria:

- [x] 初稿落盘，每条标定含四态归属 + 双锚证据（代码位点 + 实跑测试/commit）；still-live 2 目均含 ≥9 行完整条目（非零，无需 =0 声明）
- [x] 36 行台账零遗漏（SECURITY 9 + 净新 21 全亲读；轮内标定抽验 5/15≈33% 完成）
- [x] No owner-doc update required（只读——still-live 移交 R1.0）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 独立复核子 agent（fresh session）：**VERDICT PASS 零判级改判**——still-live 2 目 100% 亲读（#6a 与 4.4 S-06 行号零漂移）；already-fixed 抽验 11 目（≥30%）+ 补盲 6 目 → **实跑覆盖 36/36 闭环**（17 次实跑 95 tests 全绿）；F1 对账 6 行抽验全一致；撞车 9 组全对；「修复待验证」中间态清零。**核心发现：#3 残余收窄**（初判 OAuth code 服务端换取缺失被证伪——commit 93d47833f0 已实施；真实残余=签名验证/回调限流/javadoc 陈旧）；P2-6.4-001 独立判 P2 维持；#6a 处置=纯引用 4.4 S-06 不新编编号（防双登记）；引用勘误 3 项（checkAllowAccess 行号/调用点/#22 证实）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-6.4-checkp0-review.md`
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.4-check-p0-verification.md`；index 登记 6.4 行（替换占位行）+ still-live 清单并入「P0 发现追踪」表（标定性质登记）或 P1 汇总（降级者）
- [x] 定向验证锚点：标定抽验由复核员独立执行（11+补盲 6）；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 独立子 agent closure audit（六项：计数勾稽六方闭合/关键判定亲读 5 处/引用 14 处全命中/覆盖算术 95 tests 精确/文本一致/流程合规——首轮 FAIL 附处方 R1-R3 纯文本级，落实后轻量复核 PASS）；**通过后** roadmap 6.4 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] 标定口径可复现（复核员抽验一致）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [x] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码（6.1 已覆盖空壳面）

## Closure Gates

> 只读审计计划；live-ness 验证含只读测试运行（SECURITY 9 条 + 抽验/净新条目强制实跑处置记录点名测试类），免除全量构建。

- [x] 报告落盘且 36 条全量标定——覆盖度量：37 单元（36+#6 拆目）；实跑覆盖 36/36 闭环（17 次实跑 95 tests 全绿）
- [x] 每条标定双锚证据可复现；四态归属依据 roadmap 定义；误报校准留痕（E1 tests 笔误勘误/FP-as-P0 拆目/复核收窄透明记载）
- [x] index 计数与报告标定台账一一对应（P2-6.4-001 台账行字段完整）；仍 live 清单完整移交（P2 台账+4.4 承载）
- [x] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done——FAIL→R1-R3→轻量复核 PASS）
- [x] roadmap 6.4 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；still-live 修复归 R1.x。）

## Closure

Status Note: 6.4 check 系列 P0×36 逐项标定收口。**无 P0 级仍 live**——fix-ai-check 四批次修复全部 master 实证（already-fixed 35 单元，实跑覆盖 36/36 闭环 95 tests 全绿）；唯一 still-live 实体 P2-6.4-001（#3 ai-rest residual，P0→P2 降档且残余复核收窄）；#6a FP-as-P0 勘误拆目（缺陷本体 P1 live 纯引用 4.4 S-06）；#6b SUMMARY 遗漏真 P0 补录 already-fixed。closure audit（agent_a51bf7e4）首轮 FAIL 附处方 R1-R3（纯文本级实体零返工）：R1=实跑 tests 合计笔误 58→68 勘误；R2=Phase 2 三箱补勾；R3=抽查 7<8 缺口裁定注记（Phase 2 合并计满足）——落实后轻量复核 PASS。
Completed: 2026-10-07

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_a51bf7e4-295c-45dd-b682-b8bef5218e90（fresh session，只读 closure auditor；VERDICT: FAIL（处方 R1-R3 纯文本级；实体判定与报告结论全部维持）→ 处方落实后轻量复核 PASS）
- Evidence:
  - 计数勾稽：37 单元六方闭合（报告 §二 36 行逐行清点/index 0-0-1-0/复核 §五.3/初审映射）；P2-6.4-001 台账行五字段完整（F7 首用归属正确）；P0 发现追踪零新增正确；#6a 不双登记核实
  - 关键判定亲读 5 处：P2-6.4-001 三残余逐项实证（javadoc :65-67 原文/93d47833f0 ancestor 双分支/无限流 grep）/#6a checkAllowAccess :135-137 + 4.4 :80 零漂移/#6b 测试实存/#30 safeClose :257/:328/#22 11 consumer 勘误实证
  - 引用路径 14 处抽验全命中零悬空（六 commit is-ancestor 实测）
  - 覆盖度量：37=36+1；实跑覆盖 36/36（复核 17 次实跑表求和=95 精确）；SECURITY 9 全量/净新 16 全量/抽验 5/15
  - 验证：doc-links exit 0；check-plan-checklist --strict exit 0
  - Anti-Hollow：只读审计无新增组件；验证锚 = 36/36 实跑闭环 + closure auditor 亲读
  - Deferred 项分类检查：无 deferred；P2-6.4-001 经 P2 裁决例外台账（显式通道非静默降级）；#6a 归 4.4 承载（防双登记）
  - 流程合规：audit 先行于 roadmap 翻 done

Follow-up:

- no remaining plan-owned work（P2-6.4-001 归 R1.0 裁决；#6a 归 4.4 S-06 承载；相邻未决 2 项已在案〔SUMMARY 超审计节/3.5 R1.x〕）
