# 2311 full-code-review 1.4 nop-commons + nop-api-core + nop-codegen + nop-xdefs 审计

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.4（MA1）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 02/05/17 + 共享前缀）
> Related: plan 2309/2310（1.2/1.3 同构管线）；owner doc `docs-for-ai/02-core-guides/xdef-and-xdsl.md`
> Skill 适配注记：四模块为一组审计对象（commons/api-core=纯工具与 API 层、codegen=生成引擎、xdefs=纯资源模块 0 Java）。维度 02 阈值 1500（与 4.3 一致；500–1500 watch 移交）；维度 05 正文「model→codegen→…→web 业务生成链」适配为**生成引擎自身**的模板链完整性 + 114 xdef schema 一致性。**死模板判定两级口径**（消费单位是模板根目录而非单文件）：(a) 目录级消费点映射（renderModel/CodeGenTask tplPath/CLI/pom exec 全仓清单）；(b) 活目录内文件级可达性（XCodeGenerator 遍历规则 + `{var}` 占位实例化 + `@init.xrun` 约定钩子 + xlib include）；(c) 静态拷贝资产（.gitignore 等）与约定钩子显式豁免出死模板候选；维度 17「代码风格与规范」按三个 Java 模块抽样执行（import 分组/命名/注释规范；xdefs 0 Java 无对象，其命名/域名规范归维度 05）。codegen 的 41 个 Java 引擎面由维度 05 接线 + 维度 17 抽样覆盖（最大文件仅 265 行，维度 02 不适用）。
> Draft Review: agent_59594085 首轮（2 Major：.xgen 计数口径 128→120+26+16、死模板两级判定口径；6 Minor 全部修复）

## Purpose

执行 roadmap 工作项 1.4：审计 nop-commons/nop-api-core/nop-codegen/nop-xdefs 四模块（工具巨类、codegen 162 模板文件链、114 xdef schema 一致性、代码规范），产出报告 `1.4-kernel-utils-codegen-xdefs-audit.md`。**只读审计**（P0 走异步注入）。

## Current Baseline

- `nop-kernel/nop-commons`：main 403 / test 49；`StringHelper.java` 4932 行（`io/nop/commons/util/`）。
- `nop-kernel/nop-api-core`：main 322 / test 40；`ConvertHelper.java` 1531 行（`io/nop/api/core/convert/`）。
- `nop-kernel/nop-codegen`：main 41 / test 14；模板文件（`src/main/resources/_vfs/nop/templates` 路径下）162 个 = `.xgen` 120 + `.xrun` 26 + 静态/约定文件 16（.gitignore/_module/yaml/xpl 等）；另有 src/test 下 8 个 `.xgen` fixture（全模块 `.xgen`=128）；包：antlr/common/graalvm/initialize/java/js/maven/task/utils/vue + 3 顶层类（CodeGenConfigs/Constants/Errors）+ XCodeGenerator。
- `nop-kernel/nop-xdefs`：0 Java（纯资源模块）；`*.xdef` schema **114 个**。
- 1.1 已确认组间零出边/零循环；1.2/1.3 已覆盖 nop-xlang/nop-core（本项不重复）。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.4-kernel-utils-codegen-xdefs-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-1.4-<序号>`）+ 驳回台账（正文）+ 裁决。
- 维度 02（commons/api-core）：工具巨类解剖（StringHelper 4932/ConvertHelper 1531：职责块/内聚/拆分-保留-monitor 裁定）、包级职责漂移、>1500 清单与 500–1500 watch。
- 维度 05（codegen/xdefs）：模板链完整性——162 模板的消费点（哪些模板从未被引用=死模板）、模板↔全仓 `_` 生成物对应抽样、xdef 114 schema 一致性（schema 间 x:extends/引用闭合、命名/域名规范、无主 schema）；CodeGenTask 生成链入口连通。
- 维度 17（四模块抽样）：import 分组规范（AGENTS.md 约定 io.nop→三方→java）、命名/注释规范、错误处理面（CodeGenErrors/api-core 错误码使用一致性——浅层，全仓面归 4.1）。
- 涉及 commons/api-core/codegen 的发现按各自模块的 owner doc 路由（roadmap 1.4 Owner Doc 列为 xdef-and-xdsl.md，owner doc 面窄于审计面与 1.2/1.3 先例同构——发现进报告不改文档）。
- index 登记 + 独立 closure audit + roadmap 1.4 翻 done。

## Non-Goals

- nop-xlang/nop-core 内部（1.2/1.3 已完成）；nop-record-mapping 等其他 kernel 子模块（无独立工作项，归 6.x 扫描兜底）。
- 生成物内容审计（`_` 文件是生成结果，问题归属模板/模型层——1.2 已登记口径）。
- 全仓错误处理一致性（4.1）、全仓巨型文件（4.3）——本项只产出四模块子集。

## Scope

### In Scope

- `nop-kernel/nop-commons/**`、`nop-kernel/nop-api-core/**`、`nop-kernel/nop-codegen/**`、`nop-kernel/nop-xdefs/**`

### Out Of Scope

- 上述之外的 kernel 兄弟模块；模板的下游生成物内容（除抽样验证模板↔产物对应）

## Execution Plan

### Phase 1 - 三维度深挖（deep-audit 迭代轮次，子 agent 并行）

Status: planned
Targets: 四模块

- Item Types: `Proof`

- [ ] 动手前基线确认：`git status` 干净后开始
- [ ] 机械基线：四模块行数榜、模板清单（162 逐个文件名）、xdef 清单（114 逐个）、`_` 生成物→模板反向抽样对（注明口径）
- [ ] 维度 02 子 agent（commons/api-core）：StringHelper/ConvertHelper 解剖（职责块行数占比/内聚/拆缝/裁定）、两模块包级职责漂移、>1500 与 watch
- [ ] 维度 05 子 agent（codegen/xdefs）：死模板清单（162 中全仓零引用的）；模板↔生成物抽样验证（≥10 对：模板→消费任务→产物文件存在且结构吻合）；CodeGenTask/XCodeGenerator 生成链入口连通；xdef 114 一致性（互引闭合/无主 schema/命名规范；口径：另有 68 个 .xdef 在 nop-xdefs 之外——src/main 真实 schema、docs 副本、测试 fixture 分层计数，闭合判定以 src/main 全仓为引用面，副本/fixture 不计入无主候选）
- [ ] 维度 17 子 agent（四模块抽样）：import 分组/命名/注释规范抽验（每模块 ≥20 文件）、CodeGenErrors/api-core 错误码浅层一致性
- [ ] 深挖迭代：有发现的维度派第 2 轮，至无新发现或 10 轮上限
- [ ] 产物 `_tmp/fcr-1.4-dim{02,05,17}.md`

Exit Criteria:

- [ ] 三份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 每维度派独立维度复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [ ] 统一 severity 判级；跨维度/跨工作项合并（1.2/1.3 已登记项交叉引用）
- [ ] StringHelper/ConvertHelper 裁定收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-1.4-dim{02,05,17}-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `1.4-kernel-utils-codegen-xdefs-audit.md`；index 登记 1.4 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 独立子 agent closure audit；通过后 roadmap 1.4 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除构建验证；引用 2026-09-22 绿色基线。

- [ ] 报告落盘且三维度全覆盖
- [ ] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 1.4 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- 巨类若裁定「拆分」，具体拆分 plan 归 R1.x；死模板清理若成立，归 R1.x（codegen 属 Protected Area，plan-first）。

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
