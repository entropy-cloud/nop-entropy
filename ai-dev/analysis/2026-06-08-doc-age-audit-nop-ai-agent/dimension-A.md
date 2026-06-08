# 维度 A: 吸引子可发现性（Attractor Discoverability）

**审计日期**：2026-06-08
**审计对象**：`nop-ai/nop-ai-agent` 模块
**审计前提**：设计阶段，AGENTS.md 全局路由和 docs-for-ai 索引标记为 [开发后]

---

## 评级：健康

---

## 检查项与证据

### 1. 入口文件存在且有效

**设计文档体系自闭环**：
- `ai-dev/design/nop-ai-agent/README.md` 是设计阶段的入口文件，包含：阅读顺序、必读路径、扩展路径、与其他文档的关系。
- `00-vision.md` 标注 `状态：active`，定义了 8 条约束和 5 条 Non-Goals。
- `nop-ai-agent-roadmap.md` 标注 `日期：2026-06-07`、`状态：active`。

**[开发后]** AGENTS.md 路由表和 docs-for-ai/INDEX.md 中目前没有 nop-ai-agent 专门条目。进入开发阶段后需要补充。

### 2. 路由可达（设计阶段）

从 README.md 出发：

```
README.md → "必读路径" → 00-vision.md → roadmap.md
README.md → "扩展路径" → 01-architecture-baseline.md, 02-execution-model.md 等
```

**3 步闭环**：README.md → 00-vision.md → roadmap.md，覆盖了核心吸引子（设计约束 + 分层架构 + 实施路线图）。

**[开发后]** 从 AGENTS.md 到设计文档需要 4-5 步搜索，超出 3 步限制。需要添加专门路由条目。

### 3. 吸引子载体可识别

- **吸引子载体（stable owner docs）**：00-vision.md（约束层）、roadmap.md（实施层）、agent.xdef / agent-plan.xdef（schema 层）。可识别。
- **轨迹记录（dated records）**：ai-dev/logs/ 中的日志文件。可识别。
- **控制工具（prompts, checklists）**：00-vision.md 第 58-66 行的"必须由人决策"控制点。可识别。

### 4. 优先级声明存在

- `README.md` 有明确的"必读路径"（高优先级）和"扩展路径"（按需）。
- `00-vision.md` 的 8 条约束是最高优先级，5 条 Non-Goals 是边界声明。
- `roadmap.md` § 7 区分了"最值得固定的决策"和"最值得延期的决策"。
- **覆盖充分**。

### 5. 活跃状态标注

- `00-vision.md` 标注 `状态：active`、`日期：2026-06-06`。
- `nop-ai-agent-roadmap.md` 标注 `日期：2026-06-07`。
- **[开发后]** `project-context.md` 未提及 nop-ai-agent 设计活动（P2），需更新。

---

## 问题列表

| # | 问题 | 严重程度 | 阶段 |
|---|------|---------|------|
| A-1 | project-context.md 未提及 nop-ai-agent 设计活动 | **P2** | 当前 |
| A-2 | 旧日志引用 `nop-ai/nop-ai-agent/docs/` 旧路径，已被清空 | **P3** | 当前 |
| A-3 | AGENTS.md 路由表无 nop-ai-agent 条目 | **P2** | [开发后] |
| A-4 | docs-for-ai/ 中无 nop-ai-agent 索引 | **P2** | [开发后] |
| A-5 | nop-ai/nop-ai-agent/ 代码目录无 README.md | **P2** | [开发后] |
