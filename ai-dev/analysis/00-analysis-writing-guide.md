# Analysis Writing Guide

> Status: active workflow guide
> Last Reviewed: 2026-04-14
> Source: adapted from `nop-chaos-flux/docs/analysis/` conventions

## Purpose

`ai-dev/analysis/` 用于记录 **AI 单方面产出的调研、对比、评估报告**。

核心特征：**AI 独立完成，结论可能被推翻**。不是 source of truth，是决策参考。

## When To Write An Analysis

- 对比多个技术方案的优劣
- 调研某个技术领域或外部项目
- 对现有代码做质量评估或架构审查
- 记录被否决的方案及其否决理由
- 需要跨模块权衡取舍

**不要写 analysis 的情况：**

- 方案已经确定 → 写 `ai-dev/design/`
- 已经进入执行阶段 → 写 `ai-dev/plans/`
- 记录 bug 及其修复 → 写 `ai-dev/bugs/`
- 人和 AI 的多轮对话 → 写 `ai-dev/discussions/`

## File Naming Rule

使用 `YYYY-MM-DD` 日期前缀：

- `2026-04-02-nop-stream-review.md`
- `2026-05-17-snail-job-vs-nop-job-comparison.md`
- `2026-05-18-fault-tolerance-deep-dive.md`

同一天多个分析加序号后缀：

- `2026-04-02-nop-stream-design-review.md`
- `2026-05-18a-powerjob-vs-nop-job-features.md`

## Required Sections

### 1. Title And Metadata

每个 analysis 顶部必须有：

```md
# <<标题>>

> Status: open | resolved | superseded | obsolete
> Date: YYYY-MM-DD
> Scope: <<涉及范围>>
> Conclusion: <<一句话结论，resolved 时必填>>
> Superseded By: <<接替文档路径，superseded 时必填>>
```

`Status` 含义：

- `open` — 分析进行中，结论未定
- `resolved` — 已得出结论，后续工作由 plan 或 design 文档接手
- `superseded` — 被新的 analysis 或 design 文档替代
- `obsolete` — 已不再相关，保留仅作历史参考

### 2. Context / Background

2-5 行，说明为什么需要这个分析：

- 要回答什么问题或决策点
- 涉及哪些模块或子系统
- 约束条件

### 3. Analysis Body

主体内容。根据分析类型选择不同结构：

#### 方案比较型

```md
## Option A: <<名称>>

- 核心思路
- 优点
- 缺点
- 适用场景

## Option B: <<名称>>

- 核心思路
- 优点
- 缺点
- 适用场景

## Comparison

| 维度 | Option A | Option B |
|------|----------|----------|
| ... | ... | ... |
```

#### 设计评估型

```md
## 现状

- 当前实现是什么样的
- 存在什么问题

## 问题分析

- P0 级缺陷
- P1 级缺陷
- 设计层面的结构性问题

## 改进建议

- 建议 1
- 建议 2
```

#### 技术调研型

```md
## 调研目标

- 要回答什么问题

## 调研结果

- 发现 1
- 发现 2

## 与当前项目的关系

- 可借鉴的点
- 不可借鉴的点
```

### 4. Conclusion / Decision

`resolved` 状态时必须填写：

- 最终选择了什么方案
- 为什么选择这个方案
- 被否决的方案及其否决理由
- 后续工作指向哪个 plan 或 design 文档

### 5. Open Questions

分析过程中遗留的未决问题（可选）：

```md
## Open Questions

- [ ] 问题 1
- [ ] 问题 2
```

## Recommended Template

```md
# <<标题>>

> Status: open
> Date: YYYY-MM-DD
> Scope: <<涉及范围>>
> Conclusion: <<resolved 时填写>>

## Context

- 为什么需要这个分析
- 涉及哪些模块

## Analysis

### <<分析主题 1>>

- ...

### <<分析主题 2>>

- ...

## Conclusion

- <<结论，resolved 时必填>>
- 被否决的方案：<<方案>>，原因：<<为什么>>
- 后续工作：指向 `ai-dev/plans/XX-xxx.md` 或 `ai-dev/design/xxx/design.md`

## Open Questions

- [ ] ...

## References

- `docs-for-ai/...`
- `ai-dev/design/...`
- <<外部链接>>
```

## Writing Style

- 简洁直接，用 bullet points 代替长段落
- 列举事实和数据，避免模糊描述
- 明确标注判断依据（代码路径、文档引用、测试结果）
- 对比分析时保持对称结构，方便读者自行判断
- 记录否决理由和否决方案——这些信息在日后重新评估时最有价值

## Lifecycle

```
open → resolved → (plan 或 design 接手)
                   ↑
              superseded / obsolete
```

1. Analysis 的价值在 `open` 和刚 `resolved` 时最高。
2. `resolved` 后，结论应沉淀到 `ai-dev/design/`，执行应拆到 `ai-dev/plans/`。
3. Analysis 本身保留为决策过程的历史记录，不再作为 active doc。
4. 如果结论被推翻，标为 `superseded`，指向新的 analysis 或 design 文档。

## Scope Boundary

- `ai-dev/analysis/` — AI 单方面调研/对比/评估报告（结论可变）
- `ai-dev/discussions/` — 人+AI 多轮对话记录（需求澄清过程）
- `ai-dev/design/` — 已确认的规范设计（source of truth）
- `ai-dev/plans/` — 已确认方案的执行计划（执行文档）
- `ai-dev/bugs/` — bug 历史和修复记录
- `docs-for-ai/` — 从历史 `docs/` 精选的编码规则（AI 编码手册）
- `ai-dev/logs/` — 每日开发记录（短期上下文）
