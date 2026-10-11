# 2315 full-code-review 1.8 集成与外围结构审计 + 漏网模块归属裁定

> Plan Status: draft
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.8（MA1）；Skill=`ai-dev/skills/deep-audit-prompts.md`（维度 01/02 + 共享前缀）
> Related: owner doc `docs-for-ai/01-repo-map/module-groups.md`；EmptyMain 线索 `nop-runner/nop-cli/.../EmptyMain.java`；e2e 与根 scripts/ 归属裁定为本项交付物之一
> Skill 适配注记：集成与外围 7 组按 **reactor 口径**审计（以各组 pom `<modules>` 为准，停用模块单列）：spring 29/4（另有 nop-all-for-spring、nop-report-for-spring 两目录**非 reactor**、0 Java）；quarkus 活模块 test 实为 **0**（「4 test」全部来自被 pom:23 注释停用的 nop-quarkus-grpc，单列）；network 216/61；integration 62/23；runner 37/13（test 含 1 资源夹具）；autotest 54/13；demo 102/57。维度 01 集成层模块归属与依赖方向；维度 02 阈值 1500（500–1500 watch 移交 4.3）+ 漏网模块归属裁定（nop-entropy-e2e Playwright 资产、根 scripts/、**根级非 Maven 资产全集逐项**）。
> Draft Review: agent_9bec77f0 首轮（3 Major + 4 Minor 全修）；agent_fd48a28a 复审 2 必改（状态 draft 回退、全集传导）已修——通过后本行与 Plan Status 一同翻 active

## Purpose

执行 roadmap 工作项 1.8：审计集成层模块归属与依赖方向、EmptyMain 双入口、demo/autotest 基建定位，裁定 `nop-entropy-e2e/`（Playwright 测试资产）与根 `scripts/` 的审计归属，产出报告 `1.8-integration-periphery-audit.md`。**只读审计**（P0 走异步注入）。

## Current Baseline

- 集成外围 7 组 main/test：spring 29/4、quarkus 21/4、network 216/61、integration 62/23、runner 37/13、autotest 54/13、demo 102/57。
- EmptyMain 位于 `nop-runner/nop-cli/src/main/java/io/nop/cli_app/EmptyMain.java`（「双入口」指 CLI 双入口腐化风险——runner 与 scripts/nop-cli.cmd）。
- nop-entropy-e2e：Node/Playwright 测试资产（~44 TS，非 Maven 模块）；根 `scripts/` 仅 nop-cli.cmd + test.xrun 两个文件。
- 矩阵 ⑧ 列「EmptyMain 占位」反模式线索（runner/autotest/demo 行）。
- 1.1 已确认组间零环；network→kernel 19 条边为最大跨组边（向下合规）。
- 绿色基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS。

## Goals

- 报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.8-integration-periphery-audit.md`：摘要 + 维度结论 + P0–P3 发现（`P<级别>-1.8-<序号>`）+ 驳回台账（正文）+ 裁决。
- 维度 01：集成层模块归属与依赖方向（spring/quarkus 对 core-framework 的宿主集成面；network/integration 的边界——network 是 HTTP/MQ 基建还是业务网关；runner/autotest/demo 定位）；依赖方向倒置甄别（集成组被内核依赖=红旗）。
- 维度 02：EmptyMain 双入口裁定（EmptyMain 是什么/谁用/与 NopCli 的关系——占位即 P3 登记）；demo/autotest 基建定位（demo 102 main 是模板还是活代码）；漏网模块归属裁定（nop-entropy-e2e → 建议归属独立 e2e 审计域或并入 4.2；根 scripts/ → 归属 runner 域）；巨型文件/watch 登记。
- index 登记 + 独立 closure audit + roadmap 1.8 翻 done。

## Non-Goals

- network 的解压/MQTT P0 复核（3.9 归属）；AI 鉴权拦截器语义（3.9）；**CLI 工具链质量与双入口腐化治理深审（4.5 归属——本项只做入口清单结构面与 EmptyMain 定性）**。
- e2e Playwright 资产的内容级审计（本项只裁定归属，不审 TS 代码）。
- 全仓测试有效性（4.2）。

## Scope

### In Scope

- `nop-spring/**`、`nop-quarkus/**`、`nop-network/**`、`nop-integration/**`、`nop-runner/**`、`nop-autotest/**`、`nop-demo/**`
- `nop-entropy-e2e/`（仅归属裁定）、根 `scripts/`（仅归属裁定）、**根级非 Maven 资产全集**（逐项归属裁定）

### Out Of Scope

- network 行为级深审（3.9）；spring/quarkus 宿主集成的运行时行为（结构面为主）

## Execution Plan

### Phase 1 - 两维度深挖（deep-audit 迭代轮次，子 agent 并行）

Status: completed
Targets: 7 组 + 2 归属裁定对象

- Item Types: `Proof`

- [x] 动手前基线确认：`git status` 干净后开始
- [x] 机械基线：7 组模块边表（pom + import 双口径，以组 pom modules 为准、停用模块单列）、行数榜、**全部 main 入口枚举（manifest/mainClass/脚本包装：NopCliApplication、NopCliMainForJdk11、scripts/nop-cli.cmd、EmptyMain）**
- [x] 维度 01 子 agent（**规则适配**：deep-audit 维度 01 的业务骨架分层规则在本组显式替换——rule 10「框架特定依赖只出现在 app 模块」不适用（整组即框架集成层，starter 含 spring/quarkus 依赖非违规）；rule 2/3 dao/core 层规则无对象丢弃；保留归属/方向/纯净性骨架）：
  - 检查点 ①：starter 注册/装配面结构（AutoConfiguration.imports、`_vfs` beans、桥接类分布——spring vs quarkus 两组）
  - 检查点 ②：spring 与 quarkus starter 集合对等缺口（一方有一方无的 starter 清单）
  - 检查点 ③：红旗法——内核/业务组 pom 与 import 中出现 spring/quarkus/network/integration 工件的反向边扫描
  - 检查点 ④：疑似错位模块名单（如 nop-demo 内的 nop-spring-gateway/nop-spring-gateway-demo-service）
  - 检查点 ⑤：network/integration 边界（职责重叠甄别）；7 组归属与依赖方向（各组 pom 边 + import 实际）
- [x] 维度 02 子 agent：漏网模块归属裁定（根级非 Maven 资产全集逐项枚举归属——build.sh/export-*.sh/gen-dependency-tree.sh/tools/config/deploy/deepwiki/ai-tools/build-tools/tests/demo/missions/package.json/pmd-ruleset.xml/.githooks 等，其中 e2e/scripts 给具体归属建议）；EmptyMain 裁定（内容/引用/与 nop-cli 真入口关系）；CLI 双入口腐化评估（runner NopCli vs scripts/nop-cli.cmd 的分工与漂移风险）；demo 102 main 定性（模板/示例/活代码——被谁消费）；autotest 基建定位；漏网模块归属裁定（e2e/scripts 各给归属建议）；500–1500 watch 登记
- [x] 深挖迭代：有发现的维度派第 2 轮，至无新发现或 10 轮上限
- [x] 产物 `_tmp/fcr-1.8-dim{01,02}.md`

Exit Criteria:

- [x] 两份维度初稿落盘（含深挖追加节），每条发现 ≥9 行含 file:line
- [x] No owner-doc update required（只读；e2e/scripts 归属裁定以 finding 形式进报告）
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 独立维度复核与判级收敛

Status: completed
Targets: Phase 1 产物

- Item Types: `Proof` + `Decision`

- [x] 每维度派独立维度复核子 agent（fresh session）：逐条 保留/降级/驳回；主 agent 汇总（误报校准对照，只有复核通过条目进汇总）
- [x] 统一 severity 判级；与 3.9/4.2/**4.5** 边界协调（network 行为深审/全仓测试面/CLI 工具链治理——本项结构性裁定与 4.5 深审的分界）
- [x] e2e/scripts 归属裁定收敛（进报告移交节，供 roadmap 侧参考）

Exit Criteria:

- [x] 复核结论落盘 `_tmp/fcr-1.8-dim{01,02}-review.md`；保留发现判级理由 + 证据可复现
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制、index、closure audit 与收口

Status: completed
Targets: 轮次目录报告 + index + roadmap + 本 plan

- Item Types: `Proof`

- [x] 编制 `1.8-integration-periphery-audit.md`；index 登记 1.8 行
- [x] 如有 P0：异步注入修复 plan + 「P0 发现追踪」登记
- [x] 独立子 agent closure audit；通过后 roadmap 1.8 翻 `done`；plan 关闭（双 --strict）；commit

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划，免除构建验证；引用 2026-09-22 绿色基线。

- [x] 报告落盘且两维度 + 归属裁定全覆盖
- [x] 每条发现 file:line 可复现；判级依据共享前缀；误报校准留痕
- [x] index 计数与报告发现清单一一对应
- [x] 独立 closure audit PASS 并留痕
- [x] roadmap 1.8 回填 `done`；log 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无）

## Non-Blocking Follow-ups

（无预设；e2e/scripts 归属裁定若被采纳，落地动作归相应工作项）

## Closure

Status Note: 1.8 两维度 + 归属裁定全部完成（P0=0/P1=1/P2=6/P3=20，降级 1 正文留痕）；closure audit 一次 PASS（1 Major 表级别名错位 + 4 Minor 全部顺手修复）；P1-1.8-001 进 R1.0；未改产品代码，无 P0。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: agent_bd769bb3-dec1-4eef-9c73-8606bf1c3c74（独立 fresh-session closure auditor，只读）
- Evidence:
  - 发现清单完整性：P1 表 1 行/P2 表 6 行/P3 表 20 行/降级 1（机械复核）；抽验 8 条 file:line 全实锤（含 c7ea7b6836/1d12af9dcb git 实证）
  - severity 标定：P1-1.8-001 维持 P1 独立裁定成立（不以生产消费为前提+降 P2 触发条件在案）；P2×6 无漏升降；01-01 降级验证成立
  - 4 项关键结论复测 + 附加 3 项：红旗法 0 反向边独立重采样、integration/network 正交、根级资产 5/5、e2e 五包、@QuarkusMain 18/16、81 pom 扇入精确
  - index 对账 0/1/6/20 一致（P1 汇总表已回填）；roadmap 翻 done 前置成立
  - 1 Major（P3 表别名错位+方向括注）+ 4 Minor 全部已修复
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs` 退出码 0
  - Anti-Hollow（审计版）：红旗法正结论独立重采样证实

Follow-up:

- no remaining plan-owned work（P1-1.8-001 命运裁决归 R1.0；e2e 独立审计域建议归 R1.0 对账登记；CLI 治理移交 4.5；watch×6 移交 4.3）
