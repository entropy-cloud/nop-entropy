# Project Context

> **受众**：在 nop-entropy 仓库内进行平台开发的 AI agent。本文件记录平台自身的开发状态快照。如果你在使用 Nop 构建业务应用，可跳过本文件。

本文件是 AI agent 在每次 session 开始时获取项目当前状态的最短快照。原地更新，不创建日期副本。

## Project Identity

- **项目名**：nop-entropy
- **定位**：可替代 Spring 的全栈 Java 框架（Nop 平台），基于可逆计算原理
- **主要用户**：基于 Nop 构建应用的开发者
- **Tech Stack**：Java 21, Maven, JUnit 5, XLang
- **文档新鲜度**：`fresh`

## Active Work

- **活跃计划**：`ai-dev/plans/` 下无活跃计划
- **AI autonomy**：`implement`
- **当前阻塞**：无

### Autonomy Rule

- AI autonomy 为 `implement` 时，可直接实施满足 Verification Checklist 的变更
- AI autonomy 非 `implement` 时，按下方 Autonomy Levels 定义行事
- 文档新鲜度为 `stale` 或 `unknown` 时，AI 仅可 research 和 audit，不可实施产品行为变更

## Current Technical Baseline

- **框架主干**：`nop-core-framework`, `nop-persistence`, `nop-service-framework`
- **代表模块**（理解骨架的最佳入口）：`nop-auth`, `nop-job`, `nop-task`, `nop-wf`, `nop-ai`
- **模型源**：各模块 `model/*.orm.xml`
- **代码生成**：`_gen/`, `_*.java`, `_*.xml` 从模型派生

## Verification Commands

| Purpose | Command |
|---------|---------|
| Full build | `./mvnw clean install -T 1C` |
| Quick build | `./mvnw clean install -DskipTests -T 1C` |
| Run tests | `./mvnw test` |
| Single module tests | `./mvnw test -pl <module> -am` |

## Active ai-dev/ Layers

- [x] `ai-dev/logs/` — 每日开发日志
- [x] `ai-dev/plans/` — 执行计划
- [x] `ai-dev/analysis/` — 技术调研
- [x] `ai-dev/design/` — 架构设计
- [x] `ai-dev/bugs/` — Bug 修复记录
- [x] `ai-dev/discussions/` — 需求澄清
- [x] `ai-dev/lessons/` — 经验教训
- [x] `ai-dev/skills/` — 可复用 AI 提示词
- [x] `ai-dev/audits/` — 代码审计

## AI Block Conditions

AI **必须停止并等待人类确认**：

1. 变更涉及 ORM 模型（`model/*.orm.xml`）结构变更且无 owner doc 描述预期行为
2. 变更涉及跨模块公共 API（`nop-code-api` 等接口模块）且无 plan 描述迁移策略
3. 变更涉及 `nop-auth` 权限模型且无测试覆盖
4. 验证命令全部失败且无法推断原因

## Notes For AI Agents

- 本文件是 `AGENTS.md` 和 `docs-for-ai/` 的运行时补充，不替代它们
- AI 可从实际代码纠正本文件中的事实错误，但不可自行放宽 autonomy 等级、清除 blocker、或将 stale 标记为 fresh
- 不要从对话上下文推断活跃计划——以本文件记录为准
