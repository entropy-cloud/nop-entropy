# Discussion Writing Guide

> Status: active workflow guide
> Last Reviewed: 2026-04-14
> Source: adapted from nop-chaos-flux/docs/discussions/README.md

## Purpose

`ai-dev/discussions/` 用于记录**人和 AI 的多轮对话过程**，把模糊的想法逐步澄清为可执行的需求或设计。

核心特征：**包含用户的原始表述和纠正**，过程本身就是产出物。

语言约定：

- `discussion` 默认使用中文记录
- `plan` 默认使用英文编写
- 如果讨论最终沉淀为 `design` 或 `plan`，应在对应文档中使用该文档所属的语言约定

## When To Write A Discussion

- 用户有模糊想法，需要通过对话逐步理清
- 需求边界不明确，需要多轮问答才能确定
- 用户要纠正 AI 此前的理解偏差

**不要写 discussion 的情况：**

- 方案已经确定 → 写 `ai-dev/design/`
- 已经进入执行阶段 → 写 `ai-dev/plans/`
- 记录 bug 及其修复 → 写 `ai-dev/bugs/`
- AI 单方面做调研/对比/评估 → 写 `ai-dev/analysis/`

## File Naming Rule

使用 `YYYY-MM-DD` 日期前缀：

示例名称（如 2026-04-14-ai-agent-core-requirements.md、2026-04-15-tool-calling-design.md）。

同一天多个讨论加序号后缀（如 2026-04-14a-ai-agent-core-requirements.md、2026-04-14b-agent-memory-model.md）。

## Discussion Flow

每份讨论文件按轮次推进，用明确的标题层级分隔各个阶段。

### 第一轮

1. **用户表述需求** — 用户用自然语言描述想法或需求。
2. **AI 整理复述** — AI 将用户的表述重新整理为清晰、结构化的文字，确保准确理解意图。
3. **AI 分析与提问** — AI 给出详细分析，并标注需要用户进一步澄清的问题（使用编号列表）。

### 后续轮次

4. **用户回答** — 用户针对 AI 提出的问题给出补充说明或澄清。
5. **AI 整理回答** — AI 将用户的回答整理后追加到文件中，然后继续提出新的需要澄清的问题。

### 重复 & 纠正

6. 步骤 4–5 重复进行，直到所有问题都被澄清。
7. **纠正规则**：如果 AI 此前的整理有误，用户会指出纠正。AI **不得修改**已有记录，而是在文件末尾追加 `## 纠正` 段落，补充纠正后的描述。

### 结束

8. **总结** — 当所有问题澄清完毕后，AI 写一份 `## 总结`，包含：
   - 需求的最终描述
   - 关键设计决策
   - 遗留的待定事项（如有）
   - 后续行动项（如需新建 doc、需修改的代码路径等）

## Recommended Template

```md
# <<讨论主题>>

> Status: open | resolved | obsolete
> Date: YYYY-MM-DD

## 第 1 轮

### 用户原始表述

（用户的原话或大意）

### AI 复述

（AI 对用户意图的结构化整理）

### AI 分析

（详细分析）

### 待澄清问题

1. ...
2. ...

---

## 第 2 轮

### 用户回答

（用户对上一轮问题的回应）

### AI 整理

（对用户回答的整理）

### 待澄清问题

1. ...

---

## 纠正

> 针对第 X 轮中关于 YYY 的描述，用户纠正如下：
> ...

---

## 总结

### 最终需求

（完整的需求描述）

### 关键决策

- ...

### 待定事项

- ...

### 后续行动

- ...
```

## Writing Rules

1. 讨论文件是需求从模糊到清晰的演进记录，保留全部过程，不做删改。
2. AI 整理时如涉及技术方案，应引用项目中已有的文档或代码路径作为上下文。
3. 用户的原始表述和纠正不得被 AI 修改，只能追加。
4. discussion 文件默认使用中文，保留用户与 AI 的原始澄清语境。
5. 讨论产生的最终结论如果影响到设计，应在完成后同步更新 `ai-dev/design/` 下对应文档，并在 `ai-dev/logs/` 中记录。

## Lifecycle

```
open（对话进行中） → resolved（总结完成） → 结论沉淀到 design/plans
```

## Scope Boundary

- `ai-dev/discussions/` — 人+AI 多轮对话记录（需求澄清过程）
- `ai-dev/analysis/` — AI 单方面的调研/对比/评估报告
- `ai-dev/design/` — 已确认的规范设计（source of truth）
- `ai-dev/plans/` — 已确认方案的执行计划（执行文档，默认英文）
- `ai-dev/bugs/` — bug 历史和修复记录
- `docs-for-ai/` — 从历史 `docs/` 精选的编码规则（AI 编码手册）
- `ai-dev/logs/` — 每日开发记录（短期上下文）
