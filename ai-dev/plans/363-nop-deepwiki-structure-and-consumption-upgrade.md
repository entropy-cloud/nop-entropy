# 363 nop-deepwiki 内容质量升级：概念章规划、密度模板与严谨性闭环

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: `ai-dev/analysis/2026-09/2026-09-26-nop-deepwiki-gap-analysis.md`（v3，内容质量轴 + §5.5 源码实证增补；`~/sources/deepwiki/` 12 仓读源取证）
> Related: `ai-dev/plans/361-nop-jq-deadcode-and-jpath-fix.md`、`ai-dev/plans/362-nop-code-index-column-truncation-fix.md`
> Draft Review: 两轮独立子 agent 对抗性审查（2026-09-26，agent_9a413990）：R1 发现 3 Blocker（B1 对照基线已删、B2 松格式指纹静默失效、B3 重写接口契约缺失）+6 Major+8 Minor，全部修复后转 active；修复要点在各 Phase 内标注（出处 [R1-*]）

## Purpose

把 nop-deepwiki 的产出从"目录的百科"升级为"机制的教科书"（对齐 deepwiki.com 的内容质量形态）。收口状态：SKILL.md 三处协议升级 + 两脚本改造完成（fixture 双向验证），并在 nop-jq 上以受限页面集（恒含 5 + 机制章 ≥2 + 代表模块页 2 + 横切主题页 1）端到端跑通新管线，check-wiki 全绿、断言抽检 0 ERROR，与旧版固化记录做对照，结论记入 daily log。

## Current Baseline（2026-09-26 live 核实）

- skill 现状：Phase 2 纯文本检索；Phase 3 PLAN 模块地图**直接映射目录/包**（差距根因，见分析报告 §4）；Phase 4 派发模板只有 ≥1 mermaid + 页首源文件块 + 页尾 Sources，无表格/字数/段末 Sources 约束；生成单轮 GATHER→WRITE 无修订；check-wiki 校验断链/Mermaid/Sources/索引漂移/wiki-state 一致性，无断言真实性抽检。
- nop-jq 旧产出（logs 09-25/09-26 + 分析报告 §3 固化记录）：compact 9 页、flat modules/、表格随机出现（有的页 2 个有的 0 个）、跨切面机制只有 1 张时序图、子代理 33 万-158 万 token/页。**旧产出文件已删除**（deepwiki/ 未入库，随 plan 361 处置删除）——逐页级对照已无对象，对照基线只能是上述固化记录。
- 源码取证（2026-09-26）：survey 12 项目源码在 `~/sources/deepwiki/`，读源产出十大技法增补分析报告 §5.5——6 个 A 清单未覆盖的新技法（N1 THINK 退出 checklist+Phase Gate、N2 机制深度 10 项检查表、N3 宽松引用格式+确定性后处理、N4 Mermaid 防坑清单、N5 规划上下文压缩两段式、N6 结构化输出三级回退）与多个 A 项的源码级参数（聚类兜底链、Claim 实质性标准、大纲轮 1/3 预算、quickstart 后置、路径锁定、relatedPages）。
- 脚本现状（审查实读）：check-wiki.mjs `LINK_RE` 要求非空括号——松格式 `[path:line]()` 对其零匹配（静默跳过不校验）；gen-wiki-meta 指纹提取用同款正则（松格式下 sourceFiles 静默为空）；gen-wiki-meta 指纹契约取"第一个 `## Sources` H2 到文件尾"；gen-wiki-meta `GROUP()` 只认 modules//topics/；check-wiki `MERMAID_HEAD` 已含 mindmap 关键字；check-wiki Sources 归属识别已兼容 `> **Sources:**` 引用行。

## Goals

- Phase 3 规划升级：PLAN 增"概念章规划"步骤——章节由概念聚类产生（机制章 `flows/` ≥2），目录映射降为证据来源；页数不设人为上限。
- Phase 4 派发模板升级：每页 ≥2 表格（豁免面与检查器一致）、模块页 ≥3 mermaid、每个 H2 段末 `> Sources:` 引用行、大页两轮生成、证据 60/20/20 配比、骨架强制注入、Mermaid 防坑清单、机制深度 10 项检查表、THINK 退出 gate、Claim 实质性标准。
- 引用协议：松格式输出 + **gen 阶段确定性重写为真链接**（正确性从模型责任改为代码责任）；check-wiki `--verify-claims` 抽样机检。
- 导航与快照：index.md 顶部 mindmap（PLAN 分组列驱动）+ commit 锚点。
- 端到端：nop-jq 受限页面集跑通新管线并对照固化记录。

## Non-Goals

- 不做 RAG/embedding（survey [01] 结论：行级 Claim 是确定性方案，已满足溯源）。
- 不做产品形态项：十进制编号对外规范、llms.txt/MCP server、静态站点托管、多语言、交互问答（分析报告 §8 附注归档，需要对外发布时另立）。
- 不改 docs-for-ai 定位边界（产出不入 `docs-for-ai/`）。
- 不改 plan 361/362 已定行为。
- 不在本轮生成全量页面（20+ 页全量移入 Non-Blocking Follow-ups，按需生成）。

## Scope

### In Scope

- `.opencode/skills/nop-deepwiki/SKILL.md`（Phase 0 档位语义化、Phase 3 重写、Phase 4 模板、Phase 5 序列说明、最终检查清单）
- `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`（--verify-claims、密度检查、松格式残余校验、豁免面）
- `.opencode/skills/nop-deepwiki/scripts/gen-wiki-meta.mjs`（PLAN 分组列解析、松格式重写+指纹、mindmap、commit 锚点）
- `.opencode/skills/nop-deepwiki/references/survey-insights.md`（引用协议同步一行）
- `_tmp/deepwiki-fixture/`（扩展 fixture）与 nop-jq 受限页面集验证产出（`nop-kernel/nop-jq/deepwiki-v3/`，验证后删除）

### Out Of Scope

- 分析报告 §8 附注的全部产品形态项
- wiki 全量页面生成（20+ 页）与增量更新实战轮次（Non-Blocking Follow-ups）

## Execution Plan

### Phase 1 - 概念章规划（Q-1）

Status: planned
Targets: `.opencode/skills/nop-deepwiki/SKILL.md`（Phase 0 档位表、Phase 1 预算行、Phase 3 含 PLAN 模板）、`scripts/gen-wiki-meta.mjs`（GROUP 分组）

- Item Types: `Fix | Decision`

- [ ] Decision [R1-§6]：PLAN 增"概念章规划"步骤——主会话基于两段式压缩证据做概念聚类：先产出 ≤1024 token 分析结论（领域概念/分层/关键系统，[N5] planner.rs:348-464），再规划页面；章节轴=概念（机制/流程/不变式）而非目录，组织轴措辞取 openwiki 原文（"Organize around owned systems, runtime domains, and cross-system workflows rather than mirroring the source tree"）；目录映射降为每页证据来源栏。回退：概念聚类证据不足时回退固定页集合
- [ ] Fix: 机制章必选 ≥2（核心数据流章 + 核心子机制章），落点 `flows/<name>.md`（gen-wiki-meta GROUP 同步 flows/→机制）；构建/CI/配置类内容保证有落点页（CodeWiki artifact 兜底思想）
- [ ] Fix [R1-M2/M3]：PLAN 页面契约表增"所属章"列（章→页层级数据来源，供 index 层级与 mindmap 消费）；页面路径锁定（"Page paths are final once submitted"）+ relatedPages 导航字段进契约
- [ ] Fix [R1-§6/m6]：Phase 0 档位语义化（页数栏删除）——compact=恒含 5 页+核心机制章、standard=+主要模块页、deep=+横切主题页；"要多少页"的标准答案=页数由概念章规划自然决定、PLAN 审批时给出确定页面清单；同步 Phase 1 预算行（standard 档 ~2000 文件改绑新语义）与 PLAN 模板 Depth 行注释；gen-wiki-meta config.depth 默认值保持（仅语义注记）
- [ ] Fix: 恒含页保留，quickstart 排序到最后生成（导航/综合页后置——openwiki page-jobs.ts:181 先例）；"新旧 PLAN 模板对照"落入 daily log（旧模板要点 vs 新增：概念章/flows 类型/所属章列/relatedPages/证据来源栏）

Exit Criteria:

- [ ] SKILL.md Phase 3 含概念聚类两段式步骤、机制章必选、回退规则、路径锁定、relatedPages；档位语义化同步三处（Phase 0 表/Phase 1 预算行/PLAN 模板 Depth 注释）
- [ ] SKILL.md 含新版 PLAN 模板样例（含所属章列与 flows 页类型）
- [ ] gen-wiki-meta GROUP 对 `flows/` 前缀正确分组（fixture 验证）
- [ ] No owner-doc update required（skill 自身即交付物；分析报告为 source）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 生成协议升级（Q-2/Q-3）

Status: planned
Targets: `.opencode/skills/nop-deepwiki/SKILL.md`（Phase 4 派发模板与纪律、Phase 5 序列说明、最终检查清单）、`references/survey-insights.md`（引用协议一行）

- Item Types: `Fix`

- [ ] Fix: 派发模板密度硬约束——每页 ≥2 表格（实体/常量/阶段对照任选；**豁免 quickstart/reading-guide**，与检查器豁免面一致）、模块页（modules/*.md）≥3 mermaid、每个 H2 段末附 `> Sources:` 引用行（**页尾 `## Sources` 保持唯一聚合区**——段末用引用行避免污染指纹提取契约 [R1-M6]）
- [ ] Fix: 引用松格式约定进模板——子代理只写 `Sources: [path:line]()`（仓库相对路径，不写链接目标）；重写为真链接由 Phase 5 gen-wiki-meta 承担（deepwiki-open content.py:84-151 模式：正确性从模型责任改为代码责任）[R1-B3/M1]
- [ ] Fix: GATHER/THINK 实质化——GATHER 退出 checklist（≥4-6 可用片段、完整端到端数据流理解、失败/并发/边界/扩展点已核查；有不确定即回读源码——OpenDeepWiki content-generator.md:353-366,980-994）；机制深度 10 项检查表进模板（openwiki repository-prompts.ts:143-148）+ "Do not turn the page into a source-file inventory" + "seedPaths are starting points, not research boundaries"
- [ ] Fix: Claim 实质性标准进模板（openwiki guidance.ts:7-24：断言须物质性改变读者理解而非符号存在性）；反幻觉闭环句（Do not infer, invent, or use external knowledge...）与 Every page must earn its place 进模板
- [ ] Fix: Mermaid 防坑清单进模板（deepwiki-open prompts.py:65-95：graph TD 禁 LR、节点 3-4 词、箭头语义表、ID 冲突、标签引号，每条带反例）
- [ ] Fix: 父页派发 prompt 必须附"已产出子页清单+各自一句话结论"，要求正文显式引用（leaf-first 引实产）；大页两轮生成规范化——源材料 >30 文件或 >3000 行先大纲后填节（大纲轮 1/3 上下文预算、每节独立预算，generator.rs:434-505）
- [ ] Fix: 派发证据清单按 60/20/20 配比组织（相关证据/结构上下文/多样性补充）；>500 行文件签名骨架强制注入派发 prompt，记录各页 token 消耗用于对比
- [ ] Fix: 同批并发 ≤5 纪律保留；页面集增大后按章分批（叶子页先行、总览/quickstart 殿后）

Exit Criteria:

- [ ] SKILL.md Phase 4 模板含全部上述约束（密度/豁免面/松格式/10 项检查表/退出 gate/Claim 标准/Mermaid 防坑/两轮/配比/骨架），模板样例可见松格式 Sources 与段末引用行的实例形态
- [ ] survey-insights.md §3 引用格式行同步松格式协议
- [ ] No new test required: 模板类变更，Phase 4 端到端验证覆盖
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 严谨性闭环与导航（Q-4/Q-5）

Status: planned
Targets: `.opencode/skills/nop-deepwiki/scripts/check-wiki.mjs`、`.opencode/skills/nop-deepwiki/scripts/gen-wiki-meta.mjs`、`.opencode/skills/nop-deepwiki/SKILL.md`（Phase 5 序列说明与最终检查清单同步 [R1-M1]）

- Item Types: `Fix`

- [ ] Fix [R1-B3]：**重写接口契约定死**——gen-wiki-meta 增 `--repo <目标仓库根>` 参数（缺省从 PLAN.md `> Target:` 行解析）；Phase 5 顺序=gen 先行（松格式→重写为相对真链接+按重写后内容提取指纹）→check 后行（校验残余松格式报 ERROR、沿用现有 LINK_RE 断链逻辑）。两个脚本的既有机制零废弃
- [ ] Fix [R1-B2]：gen-wiki-meta 指纹提取支持松格式（重写后提取，或直接解析松格式 path）——杜绝 sourceFiles 静默为空的 Silent No-Op
- [ ] Fix: check-wiki 增 `--verify-claims N [--seed S] [--repo R]`：抽样池=正文断言（`path:起-止行`）+松格式 Sources 条目合并；关键词=断言句中反引号 code token 优先、其次 ≥4 字符 ASCII 标识符；**行号越界（>文件总行数）恒 ERROR**、区间 ±3 行容错窗内零关键词命中 ERROR、提取不到关键词 SKIP 不计；`--seed` 固定抽样保证 closure audit 可复现 [R1-M4]
- [ ] Fix: check-wiki 增密度检查（连续竖线行 ≥2 聚为一表计数；默认每页 ≥2 表、modules/ 页 ≥3 mermaid，CLI `--min-tables/--min-mermaid-module` 可调；**豁免 quickstart/reading-guide**；模块页判定=modules/ 路径前缀，flows 页不按模块页阈值）[R1-m3]
- [ ] Fix: check-wiki 校验残余松格式（Sources 区空括号条目）报 ERROR
- [ ] Fix: gen-wiki-meta 在 index.md 顶部生成全站 mermaid mindmap（从 PLAN"所属章"列分组拼接，零 LLM；**节点文本确定性清洗**——剥离/替换 ASCII 括号/引号/反引号，固定缩进步进；不承诺渲染级正确，check-wiki 只做围栏+块首检查）+ 页脚 commit/日期快照行 [R1-M2/m4]
- [ ] Fix: fixture 扩展 [R1-m7]：`_tmp/deepwiki-fixture/` 增错误行号断言页、合规断言页、缺表格页、松格式 Sources 页 + 迷你源码目录（PLAN Target 指向它，供松格式解析与 verify-claims）

Exit Criteria:

- [ ] 对违规 fixture（错误行号断言、残余松格式、缺表格）相应检查报 ERROR/WARN，对合规 fixture 放行（双向验证，`--seed` 复跑结果一致）
- [ ] 松格式 fixture 经 gen-wiki-meta 后：指纹非空、页面 Sources 已重写为真链接、check-wiki 断链检查对其生效
- [ ] mindmap/快照在 fixture 上生成且通过 check-wiki 围栏/块首检查
- [ ] `node --check` 两脚本通过
- [ ] No new test required: fixture 双向验证证据代替（项目无 JS 测试基建）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 端到端验证（Q 收口，受限页面集）

Status: planned
Targets: `nop-kernel/nop-jq/deepwiki-v3/`（目标仓库内一层——相对链接穿越与 git 锚点均正确 [R1-m5]；验证后删除）

- Item Types: `Proof`

- [ ] Proof: 在 nop-jq 上以升级管线跑通全流程（Phase 0→5），页面集=恒含 5 页 + 机制章 ≥2（flows/）+ 代表模块页 2 + 横切主题页 1；`check-wiki.mjs --strict` 退出码 0（含 0 WARN [R1-m2]）；`--verify-claims 20 --seed 42` 0 ERROR；其余页面按需生成（Non-Blocking Follow-ups）
- [ ] Proof: 新旧对照基于**固化记录**（logs 09-25/09-26 与分析报告 §3——旧产出文件已删 [R1-B1]），对照维度限记录中存在的轴：组织轴（目录投影 vs 概念章）、机制章有无、表格密度区间、引用机制（全链接 vs 松格式+重写）、token 消耗对比；对照表记入 daily log
- [ ] Proof: 与 deepwiki.com react 2.1 页逐要素对照，基线=分析报告 §2 固化的一手拆解（不重抓站点），对照对象限 2 个机制章页，结论记入 daily log
- [ ] No owner-doc update required（分析报告 §6 执行注记由 closure 补）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 行为/契约结果已达成（概念章规划落地 + 密度达标 + 松格式重写闭环 + 断言抽检 + mindmap/快照）
- [ ] 必要 focused verification 已完成（fixture 双向验证 + nop-jq 受限页面集端到端 + 新旧固化记录对照）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [ ] 受影响的 owner docs 已同步（分析报告加执行注记）
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 已验证新检查项真实生效（对违规 fixture 报错、合规 fixture 放行），概念章在真实产出中存在且非目录投影，松格式重写与指纹在真实产出中非空
- [ ] `node --check` 全部脚本
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0

## Deferred But Adjudicated

### 产品形态项（十进制对外编号/llms.txt/MCP/静态托管/多语言）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 属对外发布形态，与内容质量轴正交；分析报告 §8 已归档，需要时另立
- Successor Required: `no`

### nop-jq wiki 全量页面生成（standard 全量 15-20+ 页）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本轮交付物是 skill 升级，受限页面集已证明管线全链路连通（guide Rule 22）；全量生成的价值在产出物本身，按用户需要时执行
- Successor Required: `no`

## Non-Blocking Follow-ups

- nop-jq wiki 全量页面生成（见 Deferred）
- wiki 增量更新实战轮次验证（update 模式实测）
- `~/sources/deepwiki/openwiki/src/claims/` 读源取证 Claim 重定位算法细节（A3 的强化参考）

## Closure

Status Note: （关闭时填写）
Completed: （关闭时填写）

Closure Audit Evidence:

- Reviewer / Agent: （独立子 agent）
- Evidence: （每条 Exit Criterion / Closure Gate 的 PASS/FAIL + live 证据）

Follow-up:

- 见 Non-Blocking Follow-ups
