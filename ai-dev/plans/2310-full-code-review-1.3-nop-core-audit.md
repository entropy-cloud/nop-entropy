# 2310 full-code-review 1.3 nop-core 结构审计

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.3（MA1）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 01/02/15 + 共享前缀）
> Related: plan 2309（1.2 同构管线）；owner doc `docs-for-ai/02-core-guides/xlang-and-xpl-basics.md`
> Skill 适配注记：nop-core 为单 Maven 模块——维度 01 子模块 pom 口径替换为**组内包级依赖方向**（core 对 commons/api-core/dataset/javac 四条兄弟依赖边的合规面 + core 18 包互向 DAG）；维度 02 阈值 1500（与 4.3 全仓口径一致；500–1500 职责混杂候选登记 watch 移交 4.3）；维度 15 正文「类型安全与泛型使用」按 roadmap Details 聚焦**类型转换体系**（`type`/`reflect` 包转换器族 + raw type/unchecked 面）；正文中「I*Biz<T> 泛型精度」「DTO/RequestBean/DataBean 类型定义」两步对 nop-core 无对象，**显式丢弃**。CoreInitialization 启动顺序复核**不属于任何维度正文**——按 2309 先例设独立第 4 个点名复核子 agent（产物 `_tmp/fcr-1.3-named-recheck.md`）。
> Draft Review: agent_323cd9b2 首轮（2 Major + 5 Minor 全部修复）；agent_9c381233 复核 PASS

## Purpose

执行 roadmap 工作项 1.3：对 `nop-kernel/nop-core` 做维度 01（包级依赖边界）、02（模块职责与文件边界，重点 XNode 2829 行巨类与资源/VFS 组件模型）、15（类型转换体系与泛型安全）三维度审计，另设独立点名复核（CoreInitialization 启动顺序），产出报告 `1.3-nop-core-structure-audit.md`。**只读审计**（P0 走异步注入）。

## Current Baseline

- `nop-kernel/nop-core`：main 757 / test 92 Java；18 个包目录（command/context/dataset/dict/exceptions/execution/git/i18n/initialize/lang/model/module/reflect/resource/stat/type/unittest/utils）+ 3 个顶层类（CoreConfigs/CoreConstants/CoreErrors）；reflect 包 137 文件。
- 点名热点：`XNode.java`（`io/nop/core/lang/xml/`）实测 **2829 行**；`CoreInitialization.java`（`io/nop/core/initialize/`）343 行。
- 1.1 已确认 nop-kernel 组间出边为零、reactor 零循环——本项聚焦组内。
- 1.2 已发现 nop-core 两项相邻事实（本项顺带复核现状，不重复展开）：nop-core 主代码 0 处使用 io.nop.javac（unused-declared，归 R1.x）；core.type.impl/core.reflect.impl 被 xlang 大量消费（impl 白名单建议）。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.3-nop-core-structure-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-1.3-<序号>`，file:line + 修复方向）+ 驳回台账（正文留痕）+ 裁决。
- 维度 01：core 18 包互向 DAG（SCC/环甄别）；对 commons/api-core/dataset/javac 四条兄弟依赖边的合规面（core 不应依赖 api-core 之上层次）。
- 维度 02：XNode 2829 行解剖（职责块/拆分面/裁定建议 拆分/保留/monitor）；资源/VFS 组件模型（resource 包：ResourceComponentManager、注册/发现链）；>1500 行手写清单。
- 维度 15：类型转换体系（type 包转换器、reflect 的 ClassModel/字段元模型泛型安全、raw type/unchecked 面）。点名复核：CoreInitialization 启动顺序（初始化步骤间的顺序依赖与幂等性）。
- index 登记 + 独立 closure audit + roadmap 1.3 翻 done。

## Non-Goals

- XNode 的 XML 行为级正确性证明（解析器实现归 antlr4 模块）；不拆分任何文件。
- nop-xlang/nop-jq（1.2/1.9 已覆盖或归属他项）；nop-commons 工具巨类（1.4）。
- ConvertHelper（nop-commons，1.4 范围）——本项只审 core 自己的转换面。
- `nop-kernel/nop-jpath` 的 jq 桥接面（roadmap 注记「属 1.1/1.3/6.x 既有审计项」）：本项 Out of Scope 明排兄弟模块内部，不承接；其归属在 6.x（反模式/接线扫描）兜底，若 6.x 亦未覆盖，作为流程缺陷上报 R1.0 对账。

## Scope

### In Scope

- `nop-kernel/nop-core/src/main/**`、`src/test/**`、`src/main/resources/_vfs/**`

### Out Of Scope

- nop-kernel 其他兄弟模块内部；生成模板（除 XNode 类生成物定性所需）

## Execution Plan

### Phase 1 - 三维度深挖（deep-audit 迭代轮次，子 agent 并行）

Status: completed
Targets: `nop-kernel/nop-core/**`

- Item Types: `Proof`

- [x] 动手前基线确认：`git status` 干净后开始扫描
- [x] 机械基线：包级 import 边表（注明 grep 模式与排除项）、行数榜、`_` 生成物计数（口径注明）
- [x] 维度 01 子 agent：18 包互向 DAG（SCC/环甄别，参照 1.2 的假环/协同环豁免方法学）、resource 模型对外依赖面、兄弟依赖四边（api-core/commons/dataset/javac）合规——dataset 边对照 core 自身 `io/nop/core/dataset` 包（仅 BeanRowMapper）核查职责是否重叠
- [x] 维度 02 子 agent：XNode 2829 行解剖（职责块/内聚性/生成物定性/拆分-保留-monitor 裁定依据）、resource 包 VFS 组件模型注册链（以 `RegisterModelDiscovery`（位于 nop-xlang，**scope 外**，仅作消费方视角锚点，不展开审计）为消费端参照）、>1500 手写清单、500–1500 watch 登记
- [x] 维度 15 子 agent：type/reflect 包类型转换体系（转换器注册与查找链、缺失转换的行为——抛错 vs 静默）、ClassModel 反射元模型泛型安全（raw type/unchecked cast 抽样）
- [x] 点名复核子 agent（独立第 4 个）：CoreInitialization 启动顺序（初始化步骤清单与顺序依赖、重复初始化防护/幂等性、与 `_vfs` 注册面的耦合）——产物 `_tmp/fcr-1.3-named-recheck.md`
- [x] 深挖迭代：有发现的维度派第 2 轮追加深挖（携带已有发现扩盲区），至无新发现或 10 轮上限
- [x] 产物 `_tmp/fcr-1.3-dim{01,02,15}.md` + `_tmp/fcr-1.3-named-recheck.md`

Exit Criteria:

- [x] 三份维度初稿 + 点名复核结论落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] No owner-doc update required（只读）
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 每维度派独立维度复核子 agent（fresh session）：重新读代码，逐条 保留/降级/驳回；高风险条目子项复核
- [x] 主 agent 汇总各维度复核结论（对照 deep-audit 误报校准：`_` 生成物、I*Biz-in-dao、jakarta-in-core 等），**只有复核通过的条目进入最终汇总**
- [x] 统一 severity 判级；跨维度/跨工作项重复发现合并（尤其与 1.1/1.2 已登记项）
- [x] XNode 裁定建议（拆分/保留/monitor）收敛

Exit Criteria:

- [x] 每维度独立复核结论落盘 `_tmp/fcr-1.3-dim{01,02,15}-review.md`；保留发现判级理由 + 证据可复现
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `1.3-nop-core-structure-audit.md`；index 登记 1.3 行（P0–P3 计数；P1 进「P1 发现汇总」表）
- [ ] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [x] 独立子 agent closure audit；通过后 roadmap 1.3 翻 `done`；plan 关闭（双 --strict）；commit
- [x] No owner-doc update required（文档欠账以 finding 进报告）

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除构建验证；引用 2026-09-22 绿色基线。

- [x] 报告落盘且三维度 + 点名复核（CoreInitialization）全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应（P1=0 无需汇总表行）
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 1.3 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

- XNode 若裁定「拆分」，具体拆分 plan 归 R1.x；VFS 组件模型的行为级测试缺口若成立，修复归 R1.x。

## Closure

Status Note: 1.3 三维度 + CoreInitialization 点名复核全部完成（P0=0/P1=0/P2=3/P3=29 + watch×5，降级 6 全部正文留痕）；closure audit 一次 PASS（2 Minor 顺手修复）；P2×3 进 R1.0；未改产品代码，无 P0。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: agent_2bd8941e-3dcd-4f52-914d-8173207fe11d（独立 fresh-session closure auditor，只读）
- Evidence:
  - 发现清单完整性：P2 表 3 行/P3 表 29 行连续无缺、watch×5、降级 6（机械复核）；抽验 10 组 file:line 全部命中
  - severity 标定：SCC(10) 判 P2 vs 1.2 SCC(21) 判 P1 的区分论证经独立裁定站得住；死配置降 P2 四点理由留痕充分；漏升 P1 排查（reinitialize 生产零调用独立复证）通过
  - 4 项独立复现全过：SCC(10)+63 边 E5 脚本 fresh 复跑、dataset 边双向源码核验、VFS 链三层证据、order 阶梯逐行
  - index 对账 0/0/3/29 一致；roadmap 翻 done 前置成立
  - 2 个 Minor（过程记录 dim15 归位数字、P2-1.3-003 危害链措辞）已顺手修复
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs` 退出码 0
  - Anti-Hollow（审计版）：三条正结论均有真实三层证据

Follow-up:

- no remaining plan-owned work（XNode 拆分裁定=保留+monitor；P2×3 与 watch×2/×5 已移交 R1.0/4.3）
