# 2323 full-code-review 2.5 全仓 orm.xml 模型合规扫描

> Plan Status: draft
> Last Reviewed: 2026-10-04
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 2.5（MA2）；Skill=`ai-dev/skills/orm-model-audit-prompt.md`（9 维度机械扫描，severity 以 `deep-audit-prompts.md` 共享前缀 P0–P3 定义为权威判级（roadmap 通用约定），skill 的 blocker/major/minor 仅作对照参考——major 若涉 DDL/生成物契约漂移可构成 P1（如 UK 无 constraint、deploy/sql 物化漂移），个案裁定留痕）
> Related: plan 2314（1.7 已审 orm-model 91 文件边界零泄漏——引擎侧）；本项审模型定义侧；owner doc `docs-for-ai/02-core-guides/orm-model-design.md`
> Skill 适配注记：机械扫描型工作项（非 deep-audit 深挖型）——按 orm-model-audit-prompt 的 9 维度逐文件跑机械检查（grep/解析口径可复现），双 agent 分片并行 + 交叉抽验。severity 映射：blocker→P0/P1、major→P2、minor→P3，报告统一用 P0–P3 并注明映射。范围=21 个 `model/*.orm.xml` 源模型（demo 模型含）；`_delta/`、`_gen/`、test resources 不在扫描面（Delta 合规归 6.x/其他通道）。
> 只读审计（不改模型——roadmap 明确「产出发现清单」；P0 发现走异步注入）。
> Draft Review: agent_0971024b 首轮 2 Blocker（deploy/sql 实存——N/A 判定错误；机械信号 151/106 为全仓噪声、扫描面实为 67/51/16）+ 4 Major+8 Minor 全修；agent_508b7871 聚焦复审 PASS（可执行——deploy/sql 16→18 勘正〔oauth/ddd-demo 嵌套〕、delta 三处除外表述修正；两处 nit 不阻断）。

## Purpose

执行 roadmap 工作项 2.5：对全仓 21 个 `model/*.orm.xml` 源模型做 9 维度机械合规扫描，产出报告 `2.5-orm-model-compliance-scan.md`（发现清单，不改模型）。

## Current Baseline

- 21 个源模型：nop-ai、nop-ai-coder/demo、nop-auth、nop-oauth、nop-batch、nop-code、nop-credential、nop-datav、nop-ddd-demo、nop-dyn、nop-file、nop-job、nop-kernel-cli/demo、nop-metadata、nop-report、nop-retry、nop-rule、nop-sys、nop-task、nop-tcc、nop-wf。**nop-stream 无 orm.xml（实体模型零个）**——登记为事实。
- **扫描面机械信号（实测，唯一判级锚）**：21 源模型中 `<unique-key` 标签 67 个，带 `constraint=` 51 个，**缺失 16 个**（nop-code 9 处：225/559/633/690/751/804/1013/1050/1071；nop-job 3 处：208/322/451；nop-ddd-demo〔demo〕3 处：124/272/284；nop-batch 1 处：131）——维度 8 红线候选（DDL 零发射）。全仓口径数字（151/106 等，含 _vfs 生成物/test resources/模板/fixture 噪声）仅作信息性注记，**不用于判级**。
- **deploy/sql 物化链存在**：18 个模块存在 `deploy/sql/{mysql,oracle,postgresql}/`（16 顶层：nop-auth/batch/credential/datav/dyn/file/job/metadata/report/retry/rule/sys/task/tcc/wf/ai + 2 嵌套：nop-auth/nop-oauth、nop-demo/nop-ddd-demo）——维度 8 的三方言 DDL 物化核对可执行（如 `UK_NOP_AUTH_USER_NAME` 在 nop-auth 三方言 `_create_nop-auth.sql` 可检索）。
- roadmap 行「业务 13 模块 + ai + code + stream」与 live 盘点（21 文件=16 顶层业务+ai+code+3 demo，stream 零模型）的差异调和：以 live 盘点 21 文件清单为锚，roadmap 行为 v6 时点的近似表述。
- entity.xdef 权威定义：`nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/orm/entity.xdef`（只读输入）。
- 平台参考实现：`nop-auth/model/nop-auth.orm.xml`（skill 指定）。
- 绿色基线：2026-09-22 全仓 BUILD SUCCESS；2.1 已证 orm 模型装载校验矩阵全绿（环引用/重复列/propId 不匹配响亮失败——引擎侧校验兜底了部分机械维度，扫描重点在校验不覆盖的规范维度）。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/2.5-orm-model-compliance-scan.md`：摘要 + 9 维度逐项结论 + P0–P3 发现（`P<级别>-2.5-<序号>`，注明 skill severity 映射）+ 裁决。
- 9 维度逐文件扫描（每个维度给出可复现 grep/解析口径）：
  1. 类型规范（code UPPER_SNAKE/propId 连续/stdSqlType∈StdSqlType/stdDataType 配套/`stdSqlType="INT"`、`dictName=` 残留）
  2. 长度与精度（VARCHAR precision/金额 DECIMAL precision=20 scale=4·8/BIGINT 主键无 precision/CLOB vs 超长 VARCHAR）
  3. 字典设计（`<dict>` 命名/option code·value·label/跨域同名字典与平台字典复用【全仓定义索引】/ext:dict 引用闭合【name 属性位置无关解析】）
  4. 标准字段完整性（id/delVersion/version/createdBy/createTime/updatedBy/updateTime/remark + useLogicalDelete 配置 + 审计四件套 domain 复用【重复定义列=发现】；demo 模型按同口径扫描但单独分桶）
  5. 关系设计（to-one+join+tagSet=pub/头-行 cascade-delete/无声明跨模块 refEntityName；机制 D【纯外键列+I*Biz】属代码面——显式裁定不在 XML 扫描面）
  6. 跨模块引用一致性（机制 B：refEntityName ↔ notGenCode 外部实体声明闭合；DAG 单向）
  7. 命名一致性（className↔包名/tableName↔域前缀/列 name↔code）
  8. UK constraint 物化（**口径钉死：多行感知逐 tag 块解析** `/<unique-key\b[^>]*>/gs` 内查 `constraint=`，禁止行级 grep 等式判定——nop-metadata.orm.xml:2028 的 constraint= 在第 3 行，单行 grep 漏计）；逐文件计数相等性 + 无 constraint 的 UK 逐个列出 + **deploy/sql 三方言物化核对**（16 模块：每表 UNIQUE 约束数 == 模型带 constraint UK 数，抽 nop-auth/nop-metadata 三方言比对）
  9. model-first 落地（**手改痕迹口径=git 溯源**：提交改动生成路径（`_gen/**`、`_*.java`、`_app.orm.xml`、`_service.beans.xml`、`_*.xmeta`）但同一提交未动对应 `model/*.orm.xml` 或模板→手改嫌疑；`__XGEN_FORCE_OVERRIDE__` 是生成器所有权标记（仅 17/128 模板产出）不作手改判据；**时间戳口径=git log -1 --format=%ci**（FS mtime 在 worktree 全同秒不可用），产物清单=`_gen/*.java`、`_*.xmeta`、`deploy/sql/_create_*.sql`，排除手维护的 _vfs app.orm.xml；「重新生成 diff 应为空」子项豁免——只读扫描不跑 codegen，以 git 溯源+物化差异兜底）
- index 登记 + 独立 closure audit + roadmap 2.5 翻 done。

## Non-Goals

- 模型修复（发现清单产出即可，修复归 R1.0/R1.x）。
- Delta 定制文件合规（`_delta/**` 不在扫描面）；生成产物内容审计（_gen 豁免，仅手改痕迹检查）。
- 业务字段完整性深度评审（skill 维度中依赖业务知识的部分——只做机械可判定项）。
- nop-stream 实体模型（零个，无对象）。

## Scope

### In Scope

- 21 个 `model/*.orm.xml` 源模型（每模块恰 1 个）+ `_delta/**/orm/app.orm.xml` 共 5 处（roadmap Details 明文含 delta 合规；main 1 处 nop-quarkus-demo + test 4 处 nop-auth/dyn/sys/wf service）
- 只读输入：entity.xdef、`docs-for-ai/02-core-guides/orm-model-design.md`、nop-auth 参考实现

### Out Of Scope

- `_gen/`、`nop-entropy-e2e/**`、`_delta/`（除上述 5 处 app.orm.xml 外）；ORM 引擎（2.1 已审）；业务语义评审。

## Execution Plan

### Phase 1 - 机械扫描（双 agent 分片并行）

Status: planned
Targets: 21 个 orm.xml

- Item Types: `Proof`

- [ ] 动手前基线确认：本 plan 已提交、`git status` 干净后开始
- [ ] 机械基线：21 文件清单+实体计数、全仓 grep 口径基线（unique-key/constraint、stdSqlType="INT"、dictName=、notGenCode、ext:dict）、demo 与业务模型分桶
- [ ] 分片 A 子 agent（12 文件：auth/oauth/credential/sys/job/wf/task/tcc/retry/batch/file/metadata）：9 维度逐文件扫描，每维度给可复现口径
- [ ] 分片 B 子 agent（9 文件：ai/ai-coder-demo/code/dyn/datav/report/rule/ddd-demo/kernel-cli-demo）：同上（metadata 调入 A 平衡实体数）
- [ ] 交叉抽验：A/B 各抽对方 2 文件复核维度 1/4/8 计数（口径一致性验证）
- [ ] 产物 `_tmp/fcr-2.5-{a,b}.md`

Exit Criteria:

- [ ] 两份分片扫描报告落盘，每条发现 ≥9 行含 file:line + 修复方向
- [ ] 9 维度每维度有全仓可复现汇总口径（grep 模式+计数）
- [ ] No owner-doc update required（只读）
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 1 事实

### Phase 2 - 汇总复核与判级收敛

Status: planned
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [ ] 独立复核子 agent（fresh session）：两分片逐条 保留/降级/驳回 + 口径复算（维度 8 计数独立复算为必查项）
- [ ] 主 agent 汇总：按共享前缀 P0–P3 独立判级（skill severity 仅对照；DDL/生成物契约漂移类 major 候选 P1）、demo 与业务模型分桶口径（**demo 模型发现默认 ≤P2 且单独清单不自动进 R1.0**）、与 1.7/2.1 既有发现的撞车对照
- [ ] 裁决收敛

Exit Criteria:

- [ ] 复核结论落盘 `_tmp/fcr-2.5-review.md`；保留发现判级理由 + 证据可复现
- [ ] `ai-dev/logs/` 对应执行日 log 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: planned
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [ ] 编制 `2.5-orm-model-compliance-scan.md`；index 登记 2.5 行
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [ ] 验证锚点：纯 XML 只读扫描+grep 口径——**免除构建/测试验证**（无代码变更、无模型变更），以扫描口径可复现性为锚（复核 agent 独立复算计数）
- [ ] 独立子 agent closure audit；通过后 roadmap 2.5 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [ ] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读 XML 扫描计划，免除构建/测试验证（引用 2026-09-22 绿色基线；复核 agent 独立复算计数为锚）。

- [ ] 报告落盘且 21 文件 × 9 维度全覆盖（含 demo 分桶与 stream 零模型登记）
- [ ] 每条发现 file:line 可复现；grep 口径可复现
- [ ] index 计数与报告发现清单一一对应
- [ ] 独立 closure audit PASS 并留痕
- [ ] roadmap 2.5 回填 `done`；log 收口记录已写
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；视发现而定。模型修复工单归 R1.0/R1.x。）

## Closure

Status Note: （待 closure audit 后填写）
Completed: （待填）

Closure Audit Evidence:

- Reviewer / Agent: （待独立子 agent 填写）
- Evidence: （待填）

Follow-up:

- （待填）
