# 2351 full-code-review 6.4 check 系列 P0×36 逐项复核标定

> Plan Status: active
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

Status: planned
Targets: 36 P0 位点（master live code + check/ 处置记录）

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 主 agent 对账落盘：36 行宇宙表（编号/模块/SUMMARY 问题原文/**源报告定级核对**〔F1 硬步骤：SUMMARY↔check/<单元>.md 逐行，差异行登记勘误——预登记 #6 异常〕/轮内标定映射/四批次 commit 锚/分配）——**四批次 commit 锚写入宇宙表**（6410a6bfe0/e6010dc6eb/0b7fc8f903/2de622fb6a 全可达 master 已实测）
- [ ] Agent 1「SECURITY #1-9 全量亲读+实跑 + 净新均分份 A」：#1-9 逐条（含处置记录点名测试类实跑）；净新均分份 A
- [ ] Agent 2「净新均分份 B」：逐条代码亲读 + **点名测试类强制实跑** + commit 可达验证
- [ ] Agent 3「轮内已标定抽验 ≥30%（含实跑）+ 净新均分份 C」：抽验条目 live-ness 重验（防漂移，含测试实跑）+ 净新份 C
- [ ] 主 agent 抽查质量：标定抽验 ≥8 条独立复判（含 ≥2 条 SECURITY）+ 台账对账（36 行全覆盖零遗漏）
- [ ] 产物 `_tmp/fcr-6.4-checkp0.md`（三 agent 分节）+ 36 行标定台账

Exit Criteria:

- [ ] 初稿落盘，每条标定含四态归属 + 双锚证据（代码位点 + 实跑测试/commit）；**每条 still-live 条目含 ≥9 行完整条目**（位点/证据/修复方向/R1.0 条目；若 still-live=0 须在裁决段显式声明）
- [ ] 36 行台账零遗漏；轮内标定抽验 ≥30% 完成
- [ ] No owner-doc update required（只读——still-live 移交 R1.0）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：still-live 条目 100% 亲读复核 + already-fixed 抽验 ≥30%（防「修复不在 master 误判已修」——含测试实跑抽验）+ 撞车核对（修正后映射逐条对 + 3.1/3.10/3.11/4.4 承载面）+ 判级复核（「修复待验证」中间态全部收敛为四态之一；降级提议汇总主 agent 终裁〔F7〕）
- [ ] 主 agent 汇总：四态统一、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-6.4-checkp0-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `6.4-check-p0-verification.md`；index 登记 6.4 行（替换占位行）+ still-live 清单并入「P0 发现追踪」表（标定性质登记）或 P1 汇总（降级者）
- [ ] 定向验证锚点：标定抽验由复核员独立执行；`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应——36 行台账 ↔ 报告 ↔ index 对应）；**通过后** roadmap 6.4 翻 `done`；plan 关闭（双 `--strict`）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 标定口径可复现（复核员抽验一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码（6.1 已覆盖空壳面）

## Closure Gates

> 只读审计计划；live-ness 验证含只读测试运行（SECURITY 9 条 + 抽验/净新条目强制实跑处置记录点名测试类），免除全量构建。

- [ ] 报告落盘且 36 条全量标定——覆盖度量：36/36 行（SECURITY 9 全亲读 + 净新 100% + 轮内抽验 ≥30%）
- [ ] 每条标定双锚证据可复现；四态归属依据 roadmap 定义；误报校准留痕
- [ ] index 计数与报告标定台账一一对应；仍 live 清单完整移交 R1.0
- [ ] 独立 closure audit PASS 并留痕（先于 roadmap 翻 done）
- [ ] roadmap 6.4 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；still-live 修复归 R1.x。）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （收口时填写，或明确写 no remaining plan-owned work）
