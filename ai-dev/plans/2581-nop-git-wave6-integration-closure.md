# 2581 nop-git Wave 6: 平台集成与收尾

> Plan Status: active（1 轮对抗审查已吸收 3 Major + 3 Minor）
> Last Reviewed: 2026-11-21
> Source: `ai-dev/backlog/nop-git-roadmap.md` Wave 6 (items 29-31)
> Related: plan 2576-2580

## Purpose

Wave 6 收尾（roadmap items 29–31）：beans.xml IoC 声明、nop-ai-shell git 工具后端切换评估、docs-for-ai 使用文档 + INDEX 路由 + 架构支柱/策略绕过专项审计 + 全模块测试收口。

## Current Baseline

- Wave 1-5 落地（plan 2576-2580）；nop-git-core 零仓库内部依赖（JDK 21 裁定，plan 2576）。
- legacy `nop-utils/nop-git`（IGitRepository，单分支文件管理）原地保留；roadmap item 30 评估其与新 core 的关系。
- gap：beans.xml、docs-for-ai、专项审计、最终收口。

## Goals

- Item 29：`nop-git-core` 增加可选 IoC 集成面——**裁定约束**：core 零内部依赖不可破坏（JDK 21 基线），因此 beans.xml 放在新的 `nop-git-ioc` 薄模块（依赖 nop-core + nop-git-core），声明 `IGitPolicyEngine`/`IGitRepositoryService` 工厂 bean（@InjectValue 配置覆盖 policy 路径/默认分支）；若无 hard 需求可裁定为 Deferred 并记录理由。
- Item 30：nop-ai-shell git 工具后端评估——产出 `ai-dev/analysis/nop-git-ai-shell-integration-assessment.md`（现状拼 shell 的调用点盘点、切 IGitRepositoryService 的收益/成本、结论与建议），**仅评估不实施**（实施属 nop-ai-shell 模块改动，超出本 roadmap 声明范围，按模块规则另立项）。
- Item 31：`docs-for-ai/` 使用文档（03-modules 或新页：安装、CLI 部署、API、策略配置、worktree、HTTP 服务）+ INDEX 路由 + 架构支柱/策略绕过专项审计（复核支柱测试覆盖、绕过面清单）+ 全模块测试收口（全 nop-git + 受影响回归）。
- legacy nop-utils/nop-git 处置裁定（保留/标记 deprecated/迁移建议）写入评估文档。

## Non-Goals

- nop-ai-shell 实际改造（另立项）。
- nop-utils/nop-git 的代码迁移/删除。

## Execution Plan

### Phase 1 - IoC 集成面（item 29）

Status: planned
Targets: `nop-tools/nop-git/nop-git-ioc`（或裁定 Deferred）

- Item Types: `Feature`

- [ ] `nop-git-ioc` 薄模块：**裁定（审查 Major-2）——不继承 nop-git 父 pom 的 release=21**（它属平台集成面，保持平台 25 基线；JDK 21 独立部署走裸 core/cli 不受影响）：pom 覆写 maven.compiler.release=25（或沿用根 pom 默认）；beans.xml 路径钉死 `nop-git-ioc/src/main/resources/_vfs/nop/git/beans/git-ioc.beans.xml`（审查 Minor-1）；policyEngine 工厂 + repositoryService 工厂 bean，@InjectValue 配置；最小装配测试（读 vfs beans、实例化类型正确）

Exit Criteria:

- [ ] 装配测试绿；`ib test` 全绿；`ai-dev/logs/` 已更新
- [ ] 若裁定 Deferred：Deferred But Adjudicated 写明理由 + successor

### Phase 2 - 评估文档 + 专项审计 + 文档收口（items 30/31）

Status: planned
Targets: `ai-dev/analysis/`、`docs-for-ai/`

- Item Types: `Decision` + `Proof`

- [ ] nop-ai-shell 集成评估文档（含 legacy nop-git 处置裁定；**文档带 Status/Date/Scope/Conclusion 元数据头——analysis guide §1 硬性要求，审查 Minor-2**）
- [ ] docs-for-ai 使用文档 + INDEX 路由 + source-anchors 锚点
- [ ] 架构支柱/策略绕过专项审计（独立子 agent：支柱测试覆盖复核 + 绕过面清单核对）
- [ ] roadmap items 29-31 + Milestone 置 done；全模块回归

Exit Criteria:

- [ ] 文档与 INDEX 路由生效（check-doc-links 绿）
- [ ] 专项审计结论入 `ai-dev/audits/` 或 plan Closure
- [ ] 独立 closure audit 证据写入本 plan

## Closure Gates

- [ ] items 29-31 落地或显式裁定
- [ ] docs-for-ai 文档与 live 行为一致（抽查）
- [ ] 专项审计完成且结论登记
- [ ] 独立 closure audit 证据写入
- [ ] 全模块 `ib test` 绿；check-doc-links / check-plan-checklist / scan-hollow 退出码 0

## Deferred But Adjudicated

### nop-ai-shell 实际切换实施

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: roadmap item 30 要求的是「评估」；实施改变 nop-ai-shell 行为，属该模块 scope，需按其模块规则另立项。
- Successor Required: `yes`
- Successor Path: 评估文档结论给出建议后由 nop-ai-shell roadmap 立项

## Non-Blocking Follow-ups

- legacy nop-utils/nop-git 迁移实施（评估文档裁定）。

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）
