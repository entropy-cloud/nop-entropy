# 2341 full-code-review 4.3 巨型手写文件与复杂度热点审计

> Plan Status: active
> Last Reviewed: 2026-10-06
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 4.3（MA4）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 02/15 + 共享前缀）
> Related: Roadmap Details：「>1500 行手写文件全仓盘点（区分生成物），逐个给出拆分/保留/monitor 裁定建议（**供 R1.0 参考，不强制拆分**）」。
> **机械基线预核**（主 agent 实测，2026-10-06）：全仓 src/main 手写（非 `_` 前缀/非 _gen 路径）Java 中 >1500 行共 **38 处**——其中 5 处位于 `_tmp/snap/`（历史快照副本，**排除**）；其余 33 处中 ANTLR 生成物（XLangParser 11565/EqlParser 10785/EqlLexer 2732/MermaidParser 1843/XLangParserBaseListener 2009/XLangParserListener 1765 等 `parse/antlr/` 路径）需甄别生成 vs 手写；**甄别后真实手写热点 = 24 个**（antlr 7 个均带 `// Nop Generated from X.g4` 头标记 + XLangASTOptimizer/EqlASTOptimizer 带 `__XGEN_FORCE_OVERRIDE__` 为 .xgen 生成物——全部归生成物甄别节排除）。**历次深审已有结构裁定 11 个**（撞车对照）：XNode 2829（1.3 保留+monitor，3500 触发线）/ExecToJavaTranslator 2656（1.5 保留+monitor）/CrudBizModel 2283（2.6 拆分评估交付）/StringHelper 4932（1.4 拆分=P2-1.4-001）/Bytes 1684（1.4 保留+裁剪）/TextScanner 1558（1.4 保留）/ConvertHelper 1531（1.4 保留+monitor）/TypeInferenceProcessor 2096/SimpleStdDomainHandlers 1957/BuildExecutableProcessor 1719/XLangSemantics（1.2 裁定全部不拆）。**待新裁定 13 个**：CodeIndexService 2991/JobCoordinator 2845/GLRParser 2073/WindowOperator 2073/WorkflowEngineImpl 1941〔3.1 仅行为发现无结构裁定〕/ReActAgentExecutor 1935/JqBuiltins 1899/CheckpointCoordinator 1822/ParserCExtractor 1713/EqlTransformVisitor 1673/JavaSymbolResolverAdapter 1619/UserMfaSelfService 1608/GraphModelCheckpointExecutor 1591。
> 只读审计（P0 走异步注入；**本项为盘点+裁定建议型，不强制拆分、不改产品代码**）。**Skill 前缀豁免声明**：共享前缀「禁读 ai-dev/audits/ 历史记录」对本项撞车对照任务豁免——引用历次裁定是任务本体。
> **裁定口径**：每文件三选一裁定（拆分〔附拆分方向与收益评估〕/保留〔附理由〕/monitor〔附监控触发条件〕）；历次深审已有裁定的文件撞车对照引用不重复裁定；判级仅对「拆分建议」类给 P 级（P2 上限——复杂度治理项），「保留/monitor」不占 finding 编号（台账登记）。**附带行为发现通道**：逐文件通读中撞见的疑似行为缺陷按通用约定登记 finding（不受 P2 上限约束）、不展开深挖；P0 走异步注入。
> Draft Review: agent_1a74223c 首轮 3 Major（撞车索引一半错误——实测重建：11 个已有裁定〔补 1.4 四文件 StringHelper 拆分 P2-1.4-001 等 + 1.2 四文件〕+ 13 个待新裁定〔WorkflowEngineImpl 3.1 无结构裁定/JobCoordinator 2.8 零提及移入待裁定〕；1.3 转交 ResourceHelper/ResourceComponentManager watch×2 承接登记；skill 前缀禁读令豁免声明）+ 7 Minor（P2 裁决例外节/附带发现通道/XLangASTOptimizer 归生成物/裁定复核迭代语义/基线漂移指引/scope 排除理由/分批表+输出上限）——本轮修复后 agent_3a4e70d7 聚焦复核全 PASS（3 处 Minor 文本漂移 L18/L55 旧名单与 src/test 计数已顺手清理）——进入执行。

## Purpose

执行 roadmap 工作项 4.3：>1500 行手写文件全仓盘点与拆分/保留/monitor 裁定（供 R1.0 参考），产出报告 `4.3-giant-files-audit.md`。

## Current Baseline

- 机械基线：>1500 行 38 处（_tmp/snap 5 处排除后 33 处 = 11 已裁定 + 9 生成物 + 13 待新裁定）；antlr/XGEN 生成物甄别（9 个排除）；手写热点与撞车索引见上方预核行。
- 绿色基线：编制期（2026-09-22）全仓 BUILD SUCCESS；本项为盘点型（验证以复核员独立重放为锚）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/4.3-giant-files-audit.md`：摘要 + 生成物甄别节 + 全量盘点台账 + 裁定清单（拆分建议 finding/保留/monitor 台账）+ 驳回台账 + 裁决。
- **生成物甄别**（roadmap 点名「区分生成物」）：antlr 路径文件逐一判定（`.xgen` 源/构建生成标记/手写覆盖）；其余文件核对 `_` 前缀/_gen 排除完整性。
- **全量盘点台账**：>1500 行手写文件逐个一行台账（模块 + LOC + 职责一句话 + 裁定）。
- **拆分/保留/monitor 裁定**（roadmap 点名）：无历次裁定的文件逐个给出三选一裁定；拆分建议展开 ≥9 行条目（职责簇分析 + 拆分方向 + 收益/成本评估）；已有裁定文件撞车对照（引用原 finding ID + 现状核对）。
- **承接 1.3 转交登记**：1.3 报告 :98 转 4.3 三项——ResourceHelper 1157/ResourceComponentManager 576 watch×2（**均 <1500 不在本项机械清单**，登记转交确认与现状）+ XNode monitor 项（撞车对照引用）。
- index 登记 + 独立 closure audit + roadmap 4.3 翻 done。

## Non-Goals

- 实际拆分执行（R1.0 裁定后才立工单）；<1500 行文件；已深审域的行为缺陷（历次深审已立案）；生成物内容审计。

## Scope

### In Scope

- 全仓 src/main Java >1500 行手写文件（排除 _tmp/snap、_ 前缀、_gen；antlr 生成物甄别后排除）
- 只读输入：历次深审报告（1.2/1.3/1.5/2.6/2.8/3.1 等的裁定撞车对照）

### Out Of Scope

- 拆分执行；<1500 行（含 1.3 转交的 ResourceHelper/ResourceComponentManager——登记不盘点）；**src/test（4 个 >1500，4.2 域）与 tools/mission-driver JS（工具链非产品代码）显式排除**；行为缺陷深审；生成物内容。

## Execution Plan

### Phase 1 - 盘点与裁定（单 agent 分批）

Status: planned
Targets: 全仓 src/main >1500 行文件

- Item Types: `Proof` + `Decision`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始（不干净：登记在途变更，触及扫描域则等待，未触及登记豁免）；**基线漂移处理：执行日重放 38 处清单，与快照差异逐个归因（新过线文件补入台账/monitor 触发线越过登记）**
- [ ] 机械基线：38 处清单（LOC 排序）、antlr 生成物甄别（构建标记/源模板）、历次深审裁定撞车索引（XNode/ExecToJavaTranslator/CrudBizModel/WorkflowEngineImpl/XLangASTOptimizer/JobCoordinator 等的 finding ID）
- [ ] 盘点裁定 agent（**分批表：批 1 = kernel/persistence 组〔13 个待裁定的 5 个〕+ 生成物甄别；批 2 = stream/ai/wf/jq 组〔6 个〕；批 3 = 其余〔2 个〕+ 撞车对照 11 个；单批输出上限：拆分建议条目每批 ≤3 条防压缩**）：生成物甄别节 + 全量台账 + 13 个待裁定文件逐个三选一裁定（拆分建议 ≥9 行条目；保留/monitor 台账行）+ 撞车对照 11 个核对
- [ ] 裁定复核迭代：初稿完成后由主 agent 抽查裁定质量（非发现型深挖——10 轮上限不适用；以「13 个待裁定文件全部有裁定且拆分建议有职责簇分析」为完成判据）
- [ ] 产物 `_tmp/fcr-4.3-inventory.md`

Exit Criteria:

- [ ] 初稿落盘，拆分建议条目 ≥9 行含修复方向
- [ ] 全量台账（33 处排除后逐个一行）落盘
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 独立复核与裁定收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：逐条 保留/降级/驳回 + 清单口径重放
- [ ] 主 agent 汇总：判级统一（拆分建议 P2 上限）、撞车去重终裁
- [ ] 台账收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-4.3-inventory-review.md`
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `4.3-giant-files-audit.md`；index 登记 4.3 行（拆分建议如有 P2 走「P2 裁决台账」通道）
- [ ] 如有 P0：**发现即异步注入** + 「P0 发现追踪」登记
- [ ] 定向验证锚点：盘点清单口径重放由复核员独立执行；doc-links exit 0
- [ ] 独立子 agent closure audit（四项：证据完整性/严重性标定/引用路径真实性/计数对应）；通过后 roadmap 4.3 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] 盘点口径可复现（复核员重放一致）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0
- [ ] `scan-hollow-implementations.mjs` 豁免声明：只读审计无新增代码，以 roadmap 6.1 全仓扫描覆盖

## Closure Gates

> 只读审计计划，免除全量构建验证；以复核员独立重放为验证锚点。

- [ ] 报告落盘且生成物甄别/全量台账/裁定清单全覆盖
- [ ] 每条拆分建议 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 4.3 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；拆分工单归 R1.0 裁定。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
