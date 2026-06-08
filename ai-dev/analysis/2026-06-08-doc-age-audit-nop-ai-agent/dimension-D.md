# 维度 D: 控制机制有效性（Control Effectiveness）

**审计日期**：2026-06-08
**审计对象**：`nop-ai/nop-ai-agent` 模块
**审计前提**：设计阶段，AGENTS.md 路由和 Protected Areas 标记为 [开发后]

---

## 评级：健康

---

## 检查项与证据

### 1. 路由无歧义

**设计阶段路由**：
- 设计文档自成闭环：README.md → 00-vision.md → roadmap.md。
- README.md 有明确的"必读路径"和"扩展路径"，按设计维度组织。
- 对于设计阶段的任务（设计新 Agent 类型、修改 Plan 模型设计、添加记忆存储设计），路由无歧义。

**[开发后]** AGENTS.md 路由表需要添加 nop-ai-agent 专门条目，覆盖"修改 AI Agent 模块"等任务类型。

### 2. 验证命令真实

- 设计阶段不需要 build/test 验证。
- [开发后] `./mvnw test -pl nop-ai/nop-ai-agent -am` 有效但需补充测试。

### 3. 审计 prompt 适配

- 本次使用 AGE 文档审计 prompt，对设计阶段模块适用。
- 设计文档中的控制点（"必须由人决策"）起到了定制化的审计检查项作用。

### 4. Protected Areas 可执行

**设计阶段的控制机制**：
- 00-vision.md 第 58-66 行定义了"必须由人决策的决策点"（4 项）：这些是有效的控制机制，列出了需要显式确认的决策。
- roadmap.md § 7 "当前最值得固定的设计决策"和 § 8 "当前最值得延期的设计决策"起到了设计阶段的保护区域作用。
- roadmap.md § 9 "拒绝了什么"记录了否决方案，防止重新引入已否决的设计。

**这些控制点是具体可执行的**——新 session 可以对照检查当前设计是否违反了"必须由人决策"的条目。

**[开发后]** AGENTS.md 的 Protected Areas 需要扩展覆盖 nop-ai-agent 代码区域（至少 agent.xdef 和 agent-plan.xdef 的变更应受 plan-first 约束）。

### 5. 去重机制存在

- 日志中的修复记录（06-07.md 的 C1/m2-m8）使用了编号系统，可以跟踪。
- roadmap.md § 9 的否决方案记录可以防止重复讨论已否决的方向。

---

## 问题列表

| # | 问题 | 严重程度 | 阶段 |
|---|------|---------|------|
| D-1 | AGENTS.md 路由表无 nop-ai-agent 条目 | **P2** | [开发后] |
| D-2 | nop-ai-agent 代码区域不在 Protected Areas 中 | **P2** | [开发后] |
| D-3 | 设计文档中的控制点未上升至全局 AGENTS.md 控制层 | **P2** | [开发后] |
